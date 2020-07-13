# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""BuildBucketIntegrator integrates Buildbot and Buildbucket."""

import datetime
import itertools
import json
import logging
import traceback

from buildbot.util import deferredLocked
from master.buildbucket import common, changestore
from master.buildbucket.common import log
from twisted.internet import defer, reactor
from twisted.internet.defer import inlineCallbacks, returnValue

from . import common

# buildbucket API-related constants.
# time enough to schedule and start a build.
LEASE_DURATION = datetime.timedelta(hours=1)
DEFAULT_HEARTBEAT_INTERVAL = datetime.timedelta(minutes=5)
# Maximum integer that max_builds buildbucket parameter can take.
MAX_MAX_BUILDS = (1 << 31) - 1

# Buildbot-related constants.
BUILDSET_REASON = 'buildbucket'
PROPERTY_SOURCE = 'buildbucket'
LEASE_CLEANUP_INTERVAL = datetime.timedelta(minutes=1)


class BuildBucketIntegrator(object):
  """Integrates Buildbot with buildbucket.

  The |buildbot| and |buildbucket_service| parameters of the start() method
  represent the two systems BuildBucketIntegrator integrates. Here
  |buildbot| is BuildbotGateway, which encapsulates Buildbot API, and
  |buildbucket_service| is a DeferredResource for buildbucket API.

  BuildBucketIntegrator has to be explicitly started and stopped. Normally
  BuildbucketPoller does that in startService/stopService.
  """

  # _leases attribute stores currently held leases.
  # It is a dict build_id -> lease, where lease is a dict with keys:
  # - key: lease key
  # - build_request: BuildRequestGateway
  # - build: if build_started with same build_id was called,
  #          the associated build.
  # _lease is None on creation, but it gets loaded from the database in
  # _ensure_leases_loaded().

  def __init__(
      self, buckets, build_params_hook=None, max_lease_count=None,
      heartbeat_interval=None):
    """Creates a BuildBucketIntegrator.

    Args:
      buckets (list of str): poll only builds in any of |buckets|.
      build_params_hook (func): If not None, a callable with arguments
        (params, build) that can modify the supplied parameters during
        validation.

        If a ValueError is raised, the build will be marked as an
        INVALID_BUILD_DEFINITION failure and the error message will be
        propagated to the BuildBucket status.
      max_lease_count (int): maximum number of builds that can be leased at a
        time. Defaults to the number of connected slaves.
      heartbeat_interval (datetime.timedelta): frequency of build heartbeats.
        Defaults to 1 minute.
    """
    assert buckets, 'Buckets not specified'
    assert max_lease_count is None or isinstance(max_lease_count, int)
    if max_lease_count is not None:
      assert max_lease_count >= 1
    self.buckets = buckets[:]
    self.build_params_hook = build_params_hook
    self.buildbot = None
    self.buildbucket_service = None
    self._find_change_cache = None
    self.started = False
    self.changes = None
    self.poll_lock = defer.DeferredLock()
    self._max_lease_count = max_lease_count
    self._leases = None
    self.heartbeat_interval = heartbeat_interval or DEFAULT_HEARTBEAT_INTERVAL

  def get_max_lease_count(self):
    if self._max_lease_count:
      return self._max_lease_count
    if not self.buildbot:
      return 0
    return len(self.buildbot.get_connected_slaves())

  def log(self, message, level=None):
    common.log(message, level)

  def start(self, buildbot, buildbucket_service, change_store_factory=None):
    assert not self.started, 'BuildBucketIntegrator is already started'
    assert buildbot
    assert buildbucket_service
    change_store_factory = change_store_factory or changestore.ChangeStore
    self.buildbot = buildbot
    self.buildbucket_service = buildbucket_service
    self.buildbucket_service.start()
    self.changes = change_store_factory(buildbot)
    self.started = True
    self.log('integrator started')
    self._do_until_stopped(self.heartbeat_interval, self.send_heartbeats)
    self._do_until_stopped(
        LEASE_CLEANUP_INTERVAL, self.clean_completed_build_requests)

  @inlineCallbacks
  def _get_leases_from_db(self):
    """Returns currently held leases from the database.

    Queries for not-completed-yet build requests. This may take a long time.

    Returns:
      A dict of the same structure, as self._leases. See its description.
    """
    assert self.started
    self.log(
        ('Requesting for all not yet completed build requests. '
          'This may take a long time...'),
        level=logging.DEBUG)
    build_requests = yield self.buildbot.get_incomplete_build_requests()
    self.log(
        'Received %d build requests' % len(build_requests),
        level=logging.DEBUG)

    leases = {}
    for build_request in build_requests:
      info = yield build_request.get_property(common.INFO_PROPERTY)
      if not info:
        # Not a buildbucket build request.
        continue
      try:
        info = common.parse_info_property(info)
      except ValueError as e:
        self.log('invalid buildbucket property: %s' % e, level=logging.ERROR)
        continue
      build = info.get('build', {})
      build_id = build.get('id')
      lease_key = build.get('lease_key')
      if not (build_id and lease_key):
        self.log(
            'build_id or lease_key are not found in %r' % build_request,
            level=logging.WARNING)
        continue
      if build_id in leases:
        self.log(
          'more than one non-completed build request for build %s' % build_id,
          level=logging.WARNING)
        continue
      leases[build_id] = {
          'key': lease_key,
          'build_request': build_request,
      }
    returnValue(leases)

  @inlineCallbacks
  def _ensure_leases_loaded(self):
    if self._leases is None:
      self._leases = yield self._get_leases_from_db()
      self.log(
          'Loaded current leases from the database: %r' % self._leases,
          level=logging.DEBUG)
      self.send_heartbeats()

  def stop(self):
    if not self.started:
      return
    self.buildbot = None
    self.buildbucket_service.stop()
    self.buildbucket_service = None
    self.started = False
    self.log('integrator stopped')

  @staticmethod
  def _validate_change(change):
    """Raises ValueError if change dict is invalid."""
    if not isinstance(change, dict):
      raise ValueError('change is not a dict')
    if 'id' in change:
      change_id = change['id']
      if not isinstance(change_id, basestring):
        raise ValueError('Id is not a string: "%s"' % change_id)
      if not change_id:
        raise ValueError('Invalid id: "%s"' % change_id)

    author = change.get('author')
    if author is None:
      raise ValueError('Author is not specified')
    if not isinstance(author, dict):
      raise ValueError('Author is not a dict')
    if not author.get('email'):
      raise ValueError('Author email is not specified')

  def _validate_build(self, build):
    """Validates the supplied build dictionary.

    Returns (dict): The validated parameters dictionary extracted from the
        build's `parameters_json` field. If a `build_params_hook` is
        configured, the returned parameters may differ from those specified
        in the build.

    Raises ValueError if the build dict is invalid.
    """
    if not build:
      raise ValueError('build is not specified')
    if build.get('id') is None:
      raise ValueError('Build id is not set')
    parameters_json = build.get('parameters_json')
    if parameters_json is None:
      raise ValueError('Build parameters (parameters_json) are not set')
    if not isinstance(parameters_json, basestring):
      raise ValueError('Build parameters_json is not a string')
    try:
      params = json.loads(parameters_json)
    except ValueError as ex:
      raise ValueError(
          'Could not parse parameters_json: %s.\nJSON: %s' %
          (ex, parameters_json))
    assert isinstance(params, dict)

    def validate_inner(params):
      builder_name = params.get('builder_name')
      if not builder_name:
        raise ValueError('builder_name parameter is not set')
      builder = self.buildbot.get_builders().get(builder_name)
      if builder is None:
        raise ValueError('Builder %s not found' % builder_name)

      properties = params.get('properties')
      if properties is not None and not isinstance(properties, dict):
        raise ValueError('properties parameter is not a JSON object')

      changes = params.get('changes')
      if changes is not None:
        if not isinstance(changes, list):
          raise ValueError('changes parameter is not a list')
        for change in changes:
          try:
            self._validate_change(change)
          except ValueError as ex:
              raise ValueError(
                  'A change is invalid: %s\nChange:%s' % (ex, change))

    validate_inner(params)
    if callable(self.build_params_hook):
      self.build_params_hook(params, build.copy())
      validate_inner(params)
    return params

  def _check_error(self, res):
    """If |res| contains an error, logs it and returns True.

    Args:
      res (dict): buildbucket response.

    Returns:
      True if the response contains an error. Otherwise False.
    """
    error = res.get('error')
    if not error:
      return False
    self.log('buildbucket response contains an error: "%s" (reason %s)' % (
        error.get('message', '<no message>'),
        error.get('reason', '<no reason>'),
    ))
    return True

  @inlineCallbacks
  def _try_lease_build(self, build):
    lease_expiration_ts = common.datetime_to_timestamp(
        datetime.datetime.utcnow() + LEASE_DURATION)
    lease_resp = yield self.buildbucket_service.api.lease(
        id=build['id'],
        body=dict(lease_expiration_ts=lease_expiration_ts))
    lease_error = lease_resp.get('error')
    if lease_error and lease_error['reason'] == 'CANNOT_LEASE_BUILD':
      self.log('Could not lease build %s' % build['id'])
      return
    if self._check_error(lease_resp):
      return
    lease_key = lease_resp.get('build', {}).get('lease_key')
    if not lease_key:
      self.log(
          'A build has been leased, but lease_key is not provided',
          level=logging.WARNING)
      return
    returnValue(lease_key)

  @staticmethod
  def _strip_build_def(build):
    property_whitelist = set([
        'bucket',
        'build_id',
        'created_by',
        'created_ts',
        'id',
        'lease_key',
        'tags',
    ])
    return {k: v for k, v in build.iteritems() if k in property_whitelist}

  @inlineCallbacks
  def _schedule(self, builder_name, properties, build, ssid):
    """Schedules a build and returns (bsid, brid) tuple as Deferred."""
    assert self._leases is not None
    properties = (properties or {}).copy()
    properties[common.INFO_PROPERTY] = json.dumps({
        common.BUILD_PROPERTY: self._strip_build_def(build),
    }, sort_keys=True)
    properties_with_source = {
        k:(v, PROPERTY_SOURCE) for k, v in properties.iteritems()
    }
    build_request = yield self.buildbot.add_build_request(
        ssid=ssid,
        reason=BUILDSET_REASON,
        builder_name=builder_name,
        properties_with_source=properties_with_source,
        external_idstring=build['id'],
    )
    self._leases[build['id']] = {
        'key': build['lease_key'],
        'build_request': build_request,
    }
    self.log('Scheduled a build for buildbucket build %s' % build['id'])
    self.log(
        'Lease count: %d/%d' % (len(self._leases), self.get_max_lease_count()),
        level=logging.DEBUG)

  @inlineCallbacks
  def _try_schedule_build(self, build, ssid_cache):
    """Tries to schedule a build if it is valid and there is capacity.

    Args:
      build (dict): a build received from buildbucket.peek api.
    """
    build_id = build.get('id')
    if build_id is None:
      self.log(
          'Received a build without an id: %r' % build,
          level=logging.WARNING)
      return

    self.log(
        'Will try to schedule buildbucket build %s' % build_id,
        level=logging.DEBUG)

    lease_key = yield self._try_lease_build(build)
    if not lease_key:
      self.log('Could not lease build %s' % build_id)
      return
    build['lease_key'] = lease_key

    try:
      params = self._validate_build(build)
    except ValueError as ex:
      self.log('Definition of build %s is invalid: %s.' % (build_id, ex))
      self.buildbucket_service.api.fail(
          id=build_id,
          body={
              'lease_key': build['lease_key'],
              'failure_reason': 'INVALID_BUILD_DEFINITION',
              'result_details_json': json.dumps({
                  'error': {
                      'message': str(ex),
                  },
              }, sort_keys=True),
          })
      return

    builder_name = params['builder_name']
    self.log('Scheduling build %s (%s)...' % (build_id, builder_name))

    changes = params.get('changes') or []
    ssid = yield self.changes.get_source_stamp(changes)

    properties = params.get('properties')
    yield self._schedule(builder_name, properties, build, ssid)

  @deferredLocked('poll_lock')
  @inlineCallbacks
  def poll_builds(self):
    """Polls buildbucket and schedules builds."""
    assert self.started
    start_cursor = None
    ssid_cache = {}

    # Check current leases before polling.
    yield self._ensure_leases_loaded()
    max_lease_count = self.get_max_lease_count()
    if len(self._leases) >= max_lease_count:
      self.log(
          ('Not polling because reached lease count limit: %d/%d' %
           (len(self._leases), max_lease_count)),
          level=logging.DEBUG)
      return

    # Assume in the worst case 2 builds out of 3 will not be scheduled.
    # max_builds is computed only once, before the loop, because
    # query parameters must not be changed between pages.
    max_builds = min(MAX_MAX_BUILDS, (max_lease_count - len(self._leases)) * 3)

    self.log('polling builds...')
    while len(self._leases) < self.get_max_lease_count():
      peek_resp = yield self.buildbucket_service.api.peek(
          bucket=self.buckets,
          max_builds=max_builds,
          start_cursor=start_cursor,
      )
      if self._check_error(peek_resp):
        break
      start_cursor = peek_resp.get('next_cursor')

      builds = peek_resp.get('builds', [])
      self.log('got %d builds' % len(builds))

      for build in builds:
        if not build:
          continue
        yield self._try_schedule_build(build, ssid_cache)
        if len(self._leases) >= self.get_max_lease_count():
          self.log('Reached the maximum number of leases', level=logging.DEBUG)
          break
      if not start_cursor:
        break

  @staticmethod
  def is_buildbucket_build(build):
    """Returns True if |build|'s origin is buildbucket."""
    info = build.properties.getProperty(common.INFO_PROPERTY)
    return info is not None

  @staticmethod
  def _get_build_def(build):
    """Returns buildbucket build def a buildbot build."""
    info = build.properties.getProperty(common.INFO_PROPERTY)
    if info is None:
      return None
    try:
      info = common.parse_info_property(info)
    except ValueError:
      return None
    return info.get(common.BUILD_PROPERTY)

  @inlineCallbacks
  def send_heartbeats(self):
    if not self._leases:
      return
    leases = self._leases.items()
    self.log(
        'Sending heartbeats for %d leases' % len(leases),
        level=logging.DEBUG)
    lease_expiration_ts = common.datetime_to_timestamp(
        datetime.datetime.utcnow() + LEASE_DURATION)

    @inlineCallbacks
    def send(leases):
      heartbeats = [{
          'build_id': build_id,
          'lease_key': lease['key'],
          'lease_expiration_ts': lease_expiration_ts,
      } for build_id, lease in leases]
      resp = yield self.buildbucket_service.api.heartbeat_batch(
          body={'heartbeats': heartbeats},
      )
      results = resp.get('results') or []

      for result in results:
        build_id = result.get('build_id')
        lease = self._leases.get(build_id)
        if not lease:
          continue
        if self._check_error(result):
          self.log('Canceling build request for build "%s"' % build_id)
          del self._leases[build_id]
          build = lease.get('build')
          if build:
            stop_reason = (
                'Heartbeat failed with error "%s" (reason "%s")'
                % (
                    result['error'].get('message'),
                    result['error'].get('reason')))
            yield self._stop_build(build, stop_reason)
          else:
            yield lease['build_request'].cancel()

    batches = []
    BATCH_SIZE = 25
    while leases:
      batches.append(send(leases[:BATCH_SIZE]))
      leases = leases[BATCH_SIZE:]
    yield defer.gatherResults(batches)

  @inlineCallbacks
  def clean_completed_build_requests(self):
    """Deletes leases that point to cancelled build requests.

    Since Buildbot's requestCancelled notifcation does not work,
    we have to periodically check for canceled build requests.
    """
    if not self._leases:
      return
    for build_id, lease in self._leases.items():
      request = lease['build_request']
      # Buildbot does not distinguish failed and cancelled build requests,
      # but a cancelled build request doesn't have a started build.
      is_cancelled = (yield request.is_failed()) and not lease.get('build')
      if not is_cancelled:
        continue

      # Check that build_id is still in self._leases because this loop iteration
      # is async and self._leases may be modified in the meantime.
      if build_id not in self._leases:
        continue

      self.log(('Build request %r is complete. Deleting lease for build %s' %
                (request, build_id)),
               level=logging.DEBUG)
      del self._leases[build_id]
      yield self.buildbucket_service.api.cancel(id=build_id)

  def _do_until_stopped(self, interval, fn):
    def loop_iteration():
      if not self.started:
        return
      try:
        fn()
      finally:
        if self.started:
          reactor.callLater(interval.total_seconds(), loop_iteration)
    loop_iteration()

  def _stop_build(self, build, reason):
    if build.isFinished():
      return
    self.log(
        'Stopping the build %s/%s. Reason: %s' % (
            build.getBuilder().getName(), build.getNumber(), reason))
    self.buildbot.stop_build(build, reason=reason)

  @inlineCallbacks
  def _leased_build_call(self, method_name, build, body):
    build_def = self._get_build_def(build)
    if not build_def:
      return

    method = getattr(self.buildbucket_service.api, method_name)
    body = body.copy()
    body['lease_key'] = build_def['lease_key']
    resp = yield method(id=build_def['id'], body=body)
    returnValue(resp)

  @inlineCallbacks
  def on_build_started(self, build):
    assert self.started
    build_def = self._get_build_def(build)
    if not build_def:
      return
    build_id = build_def['id']
    builder = build.getBuilder()
    self.log(
        ('Build %s started as %s/%d' %
         (build_id, builder.getName(), build.getNumber())))
    yield self._ensure_leases_loaded()
    if build_id not in self._leases:
      self._stop_build(build, 'Build started, but it is not among leases.')
    if self._leases[build_id]['key'] != build_def['lease_key']:
      self._stop_build(
          build, 'Build started, but it is lease key is not current.')

    assert not self._leases[build_id].get('build')
    self._leases[build_id]['build'] = build
    resp = yield self._leased_build_call('start', build, {
        'url': self.buildbot.get_build_url(build),
    })
    if 'error' in resp:
      stop_reason = (
          'Build has started, but an attempt to notify buildbucket about it '
          'has failed with error "%s" (reason: %s).' % (
              resp['error'].get('message'),
              resp['error'].get('reason')))
      self._stop_build(build, stop_reason)

  @inlineCallbacks
  def on_build_finished(self, build, status):
    assert status in ('SUCCESS', 'FAILURE', 'EXCEPTION', 'RETRY', 'SKIPPED')
    build_def = self._get_build_def(build)
    if not build_def:
      return
    build_id = build_def['id']
    assert self.is_buildbucket_build(build)
    self.log(
        'Build %s finished with status "%s"' % (build_id, status),
        level=logging.DEBUG)

    # Update leases.
    yield self._ensure_leases_loaded()
    if build_id not in self._leases:
      self.log(
          'build %s is not among current leases' % build_id,
          level=logging.WARNING)
    elif status != 'RETRY':
      del self._leases[build_id]

    if status == 'RETRY':
      # Do not mark this build as failed. Either it will be retried when master
      # starts again and the build lease is still held, or the build lease will
      # expire.
      return
    if status == 'SKIPPED':
      # Build lease will expire on its own.
      # TODO(nodir): implement unlease API http://crbug.com/448984 and call it
      # here.
      return

    if not self.started:
      self.log(
          'BuildBucketIntegrator.on_build_finished: cannot proceed because '
          'the integrator is stopped', level=logging.WARNING)
      return

    properties_to_send = build.getProperties().asDict()
    # Strip property source.
    properties_to_send = {k:v for k, (v, s) in properties_to_send.iteritems()}
    del properties_to_send[common.INFO_PROPERTY]
    body = {
        'result_details_json': json.dumps({
            'properties': properties_to_send,
        }, sort_keys=True),
    }
    if status == 'SUCCESS':
      yield self._leased_build_call('succeed', build, body)
    else:
      body['failure_reason'] = (
          'BUILD_FAILURE' if status == 'FAILURE'
          else 'INFRA_FAILURE')
      yield self._leased_build_call('fail', build, body)
