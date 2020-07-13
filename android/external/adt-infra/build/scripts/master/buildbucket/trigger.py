# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Build triggering via buildbucket."""

import json

from twisted.internet import defer

from . import client
from . import common


class TriggeringService(object):
  """Schedules new builds on buildbucket."""

  def __init__(self, active_master, buildbucket_service):
    self.active_master = active_master
    self.buildbucket_service = buildbucket_service

  @classmethod
  @defer.inlineCallbacks
  def create(cls, active_master):
    buildbucket_service = yield client.create_buildbucket_service(active_master)
    defer.returnValue(cls(active_master, buildbucket_service))

  def start(self):
    self.buildbucket_service.start()

  def stop(self):
    self.buildbucket_service.stop()

  @staticmethod
  def get_buildset(build_def):
    tags = build_def.get('tags', [])
    PREFIX = 'buildset:'
    for t in tags:
      if t.startswith(PREFIX):
        return t[len(PREFIX):]
    return None

  def get_build_url(self, build_id):
    return 'https://%s/b/%s' % (
        client.get_default_buildbucket_hostname(self.active_master), build_id)

  @defer.inlineCallbacks
  def trigger(
      self, source_build, bucket_name, builder_name, properties, changes=None):
    """Schedules a build on buildbucket."""
    info = source_build.getProperties().getProperty(common.INFO_PROPERTY) or {}
    try:
      info = common.parse_info_property(info)
    except ValueError:
      info = {}
    build_def = info.get(common.BUILD_PROPERTY) or {}
    bucket_name = bucket_name or build_def.get('bucket')
    if not bucket_name:
      # This function should not be called in this case.
      raise common.Error(
          'Neither bucket_name is specified, nor the build is from buildbucket')

    tags = []
    if build_def:
      tags.append('parent_build_id:%s' % build_def['id'])
      buildset = self.get_buildset(build_def)
      if buildset:
        tags.append('buildset:%s' % buildset)

    response = yield self.buildbucket_service.api.put(body={
        'bucket': bucket_name,
        'tags': tags,
        'parameters_json': json.dumps({
            'builder_name': builder_name,
            'properties': properties,
            'changes': changes or [],
        }),
    })
    result = {
        'response': response,
    }
    build_id = response.get('build', {}).get('id', {})
    if build_id:
      result['build_url'] = self.get_build_url(build_id)
    defer.returnValue(result)


_master_triggering_service_map = {}


def get_triggering_service(active_master):
  """Returns a TriggeringService instance for active_master as Deferred."""
  d = _master_triggering_service_map.get(active_master)
  if not d:
    d = TriggeringService.create(active_master)
    def start(service):
      service.start()
      return service
    d.addCallback(start)
    _master_triggering_service_map[active_master] = d

  result = defer.Deferred()
  def udpate_result(service):
    result.callback(service)
    return service
  d.addCallback(udpate_result)
  d.addErrback(result.errback)
  return result


def change_from_change_spec(self, change):
  """Converts a change in change_spec format to buildbucket format.

  For more info on change_spec format, see
  master.chromium_step.AnnotationObserver.insertSourceStamp.

  Buildbucket change format is described in README.md.
  """
  create_ts = None
  if 'when_timestamp' in change:
    # Convert from seconds to milliseconds since Unix Epoch.
    assert isinstance(change['when_timestamp'], (int, float))
    create_ts = change['when_timestamp'] * 1000

  return {
      'revision': change.get('revision'),
      'author': {
          # Assuming author is email.
          'email': change.get('author'),
      },
      'create_ts': create_ts,
      'files': [{'path': f} for f in change.get('files', [])],
      'message': change.get('comments'),
      'branch': change.get('branch'),
      'url': change.get('revlink'),
      'project': change.get('project'),
  }

