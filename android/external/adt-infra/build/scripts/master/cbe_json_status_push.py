# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ConfigParser
import collections
import datetime
import functools
import json
import os

from buildbot.status.base import StatusReceiverMultiService
from master import auth
from master.deferred_resource import DeferredResource
from twisted.internet import defer, reactor
from twisted.python import log


CBE_URL = 'https://chrome-build-extract.appspot.com'
CBE_DISCOVERY_SERVICE_URL = (
    '%s/_ah/api/discovery/v1/apis/{api}/{apiVersion}/rest' % CBE_URL
)


# Annotation that wraps an event handler.
def event_handler(func):
  """Annotation to simplify 'StatusReceiver' event callback methods.

  This annotation uses the wrapped function's name as the event name and
  logs the event if the 'StatusPush' is configured to be verbose.
  """
  status = func.__name__
  @functools.wraps(func)
  def wrapper(self, *args, **kwargs):
    if self.verbose:
      log.msg('Status update (%s): %s %s' % (
          status, args, ' '.join(['%s=%s' % (k, kwargs[k])
                                  for k in sorted(kwargs.keys())])))
    return func(self, *args, **kwargs)
  return wrapper


def api_call(api, **kwargs):
  """Allows keyword-style calls to a DeferredResource.Api object."""
  return api(body=kwargs)


class ConfigError(ValueError):
  pass


_BuildBase = collections.namedtuple(
    '_BuildBase', ('builder_name', 'build_number'))
class _Build(_BuildBase):
  # Disable "no __init__ method" warning | pylint: disable=W0232
  def __repr__(self):
    return '%s/%s' % (self.builder_name, self.build_number)


class StatusPush(StatusReceiverMultiService):
  """
  Periodically push builder status updates to appengine.
  """

  # Path of the status push configuration file to load.
  CONFIG = 'cbe_json_status_push.config'
  # The section for the status push in the config file.
  CONFIG_SECTION = 'cbe_json_status_push'
  # The default push interval, in seconds.
  DEFAULT_PUSH_INTERVAL_SEC = 30

  # Perform verbose logging.
  verbose = False

  def __init__(self, activeMaster, server=None, master=None,
               discoveryUrlTemplate=None,
               pushInterval=None):
    """
    Instantiates a new StatusPush service.

    The server and master values are used to form the BuildBot URL that a given
    build references. For example:
      - server: http://build.chromium.org/p
      - master: chromium

    Args:
      activeMaster: The current Master instance.
      server: (str) The server URL value for the status pushes.
      master: (str) The master name.
      discoveryUrlTemplate: (str) If not None, the discovery URL template to use
          for 'chrome-build-extract' cloud endpoint API service discovery.
      pushInterval: (number/timedelta) The data push interval. If a number is
          supplied, it is the number of seconds.
    """
    assert activeMaster, 'An active master must be supplied.'
    StatusReceiverMultiService.__init__(self)

    # Infer server/master from 'buildbot_url' master configuration property,
    # if possible.
    if hasattr(activeMaster, 'buildbot_url') and not (server and master):
      inf_server, inf_master = self.inferServerMaster(activeMaster.buildbot_url)
      server = server or inf_server
      master = master or inf_master
    assert server and master, 'A server and master value must be supplied.'

    # Parameters.
    self.activeMaster = activeMaster
    self.server = server
    self.master = master
    self.discoveryUrlTemplate = (discoveryUrlTemplate or
                                 CBE_DISCOVERY_SERVICE_URL)
    self.pushInterval = self._getTimeDelta(pushInterval or
                                           self.DEFAULT_PUSH_INTERVAL_SEC)

    self._status = None
    self._res = None
    self._updated_builds = set()
    self._pushTimer = None
    self._sequence = 0

  @classmethod
  def load(cls, activeMaster, config=None, **kwargs):
    """Returns: (StatusPush) A configured StatusPush instance, or None.

    This method loads a StatusPush from a configuration file, returning the
    configured isntance or None if a status push instance isn't configured.

    In order to define a StatusPush instance, the configuration file must:
    - Exist.
    - Contain a 'cbe_json_status_push' section.
    This section overrides values in 'kwargs', can contain:

    [cbe_json_status_push]
    # An alternative discovery URL template to use for the
    # 'chrome-build-extract' service. If omitted, defaults to
    # CBE_DISCOVERY_SERVICE_URL.
    discovery_url = <URL>

    # The server URL. Defaults to inferring from the master's URL.
    server = <URL>

    # The master name. Defaults to inferring from the master's URL.
    master = <NAME>

    # The number of seconds in between status pushes. Defaults to
    # DEFAULT_PUSH_INTERVAL_SEC.
    push_interval_sec = <TIME_SEC>

    # The path to the service account JSON to use. No authentication will be
    # attempted if missing.
    service_account_json_path = <PATH>

    Args:
      activeMaster: The active master instance.
      config: (str/None) The path of the configuration file. If None, the
          default configuration file path will be used.
      kwargs: Keyword arguments to forward to the StatusPush constructor.

    Raises:
      ValueError: if there was an error loading the configuration.
    """
    config = os.path.abspath(config or cls.CONFIG)
    if not os.path.isfile(config):
      log.msg('CBEStatusPush: No configuration file at [%s]' % (config,))
      return None

    cp = ConfigParser.SafeConfigParser()
    cp.read(config)
    if not cp.has_section(cls.CONFIG_SECTION):
      log.msg('CBEStatusPush: Configuration [%s] missing [%s] section.' % (
              config, cls.CONFIG_SECTION))
      return None

    def getprop(c, p, typ=None):
      if cp.has_option(cls.CONFIG_SECTION, c):
        kwargs[p] = (typ or str)(cp.get(cls.CONFIG_SECTION, c))

    getprop('discovery_url', 'discoveryUrlTemplate')
    getprop('server', 'server')
    getprop('master', 'master')
    getprop('push_interval_sec', 'pushInterval',
            typ=lambda v: datetime.timedelta(seconds=int(v)))

    return cls(activeMaster, **kwargs)

  @classmethod
  def inferServerMaster(cls, url):
    """Returns: (server, master) tuple inferred from 'url'."""
    # Assume the master is the last component of the URL.
    return url.rstrip('/').rsplit('/', 1)

  @staticmethod
  def _getTimeDelta(value):
    """Returns: A 'datetime.timedelta' representation of 'value'."""
    if isinstance(value, datetime.timedelta):
      return value
    elif isinstance(value, (int, long)):
      return datetime.timedelta(seconds=value)
    raise TypeError('Unknown time delta type; must be timedelta or number.')

  def startService(self):
    """Twisted service is starting up."""
    StatusReceiverMultiService.startService(self)

    # Subscribe to get status updates.
    self._status = self.parent.getStatus()
    self._status.subscribe(self)

    @defer.inlineCallbacks
    def start_loop():
      # Load and start our master push resource.
      self._res = yield self._loadResource()
      self._res.start()

      # Schedule our first push.
      self._schedulePush()
    reactor.callWhenRunning(start_loop)

  @defer.inlineCallbacks
  def stopService(self):
    """Twisted service is shutting down."""
    self._clearPushTimer()

    # Do one last status push.
    yield self._doStatusPush(self._updated_builds)

    # Stop our resource.
    if self._res:
      self._res.stop()
      self._res = None

  @defer.inlineCallbacks
  def _loadResource(self):
    """Loads and instantiates a cloud endpoints resource to CBE master push."""
    # Construct our DeferredResource.
    service = yield DeferredResource.build(
        'master_push',
        'v0',
        credentials=auth.create_credentials_for_master(self.activeMaster),
        discoveryServiceUrl=self.discoveryUrlTemplate,
        verbose=self.verbose,
        log_prefix='CBEStatusPush')
    defer.returnValue(service)

  @defer.inlineCallbacks
  def _doStatusPush(self, updated_builds):
    """Pushes the current state of the builds in 'updated_builds'.

    Args:
      updated_builds: (collection) A collection of _Build instances to push.
    """
    assert self._res, 'CBE Resource is not instantiated.'

    # If there are no updated builds, we're done.
    if not updated_builds:
      return

    # Load all build information for builds that we're pushing.
    builds = sorted(updated_builds)
    if self.verbose:
      log.msg('Pushing status for builds: %s' % (builds,))
    loaded_builds = yield defer.DeferredList([self._loadBuild(b)
                                              for b in builds])

    send_builds = []
    for i, build in enumerate(builds):
      success, result = loaded_builds[i]
      if not success:
        log.msg('Failed to load build for [%s]: %s' % (build, result))
        continue

      # result is a (build, build_dict) tuple.
      send_builds.append(result[1])

    # If there are no builds to send, do nothing.
    if not send_builds:
      return

    # Increment our sequence.
    sequence = self._sequence
    self._sequence += 1

    # Construct our packet.
    yield api_call(
        self._res.api.pushBuilds,
        server=self.server,
        master=self.master,
        seq=sequence,
        build_json=[json.dumps(build) for build in send_builds])

  def _pushTimerExpired(self):
    """Callback invoked when the push timer has expired.

    This function takes a snapshot of updated builds and begins a push.
    """
    self._clearPushTimer()

    # Collect this round of updated builds. We clear our updated builds in case
    # more accumulate during the send interval. If the send fails, we will
    # re-add them back in the errback.
    updates = self._updated_builds.copy()
    self._updated_builds.clear()

    if self.verbose:
      log.msg('Status push timer expired. Pushing updates for: %s' % (
              sorted(updates)))

    # Upload them. Reschedule our send timer after this push completes. If it
    # fails, add the builds back to the 'updated_builds' list so we don't lose
    # them.
    d = self._doStatusPush(updates)

    def eb_status_push(failure, updates):
      # Re-add these builds to our 'updated_builds' list.
      log.err('Failed to do status push for %s: %s' % (
          sorted(updates), failure))
      self._updated_builds.update(updates)
    d.addErrback(eb_status_push, updates)

    def cb_schedule_next_push(ignored):
      self._schedulePush()
    d.addBoth(cb_schedule_next_push)

  def _schedulePush(self):
    """Schedules the push timer to perform a push."""
    if self._pushTimer:
      return
    if self.verbose:
      log.msg('Scheduling push timer in: %s' % (self.pushInterval,))
    self._pushTimer = reactor.callLater(self.pushInterval.total_seconds(),
        self._pushTimerExpired)

  def _clearPushTimer(self):
    """Cancels any current push timer and clears its state."""
    if self._pushTimer:
      if self._pushTimer.active():
        self._pushTimer.cancel()
      self._pushTimer = None

  def _loadBuild(self, b):
    """Loads the build dictionary associated with a '_Build' object.

    Returns: (build, build_data), via Deferred.
      build: (_Build) The build object that was loaded.
      build_data: (dict) The build data for 'build'.
    """
    builder = self._status.getBuilder(b.builder_name)
    build = builder.getBuild(b.build_number)
    return defer.succeed((b, build.asDict()))

  def _recordBuild(self, build):
    """Records an update to a 'buildbot.status.build.Build' object.

    Args:
      build: (Build) The BuildBot Build object that was updated.
    """
    build = _Build(
        builder_name=build.builder.name,
        build_number=build.number,
    )
    self._updated_builds.add(build)

  #### Events

  @event_handler
  def builderAdded(self, _builderName, _builder):
    return self

  @event_handler
  def buildStarted(self, _builderName, _build):
    return self

  @event_handler
  def stepStarted(self, _build, _step):
    return self

  @event_handler
  def buildFinished(self, _builderName, build, _results):
    self._recordBuild(build)
