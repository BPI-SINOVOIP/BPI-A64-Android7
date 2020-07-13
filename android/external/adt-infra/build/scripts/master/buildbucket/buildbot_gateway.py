# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This file encapsulates most of buildbot API for BuildBucketIntegrator."""

from buildbot.changes.changes import Change
from buildbot.interfaces import IControl
from buildbot.process.buildrequest import BuildRequest
from buildbot.status import builder as build_results
from twisted.internet.defer import inlineCallbacks, returnValue
import sqlalchemy as sa


class BuildRequestGateway(object):
  """Simplifies BuildRequest API for BuildBucketIntegrator."""

  def __init__(self, master, build_request=None, brid=None):
    assert master
    assert build_request is not None or brid is not None
    self.master = master
    self.build_request = build_request
    self.brid = brid
    if not self.brid and self.build_request:
      self.brid = self.build_request.id

  @inlineCallbacks
  def _ensure_build_request(self):
    if self.build_request:
      return
    assert self.brid
    brdict = yield self.master.db.buildrequests.getBuildRequest(self.brid)
    self.build_request = yield BuildRequest.fromBrdict(self.master, brdict)

  @inlineCallbacks
  def get_property(self, name):
    yield self._ensure_build_request()
    value = self.build_request.properties.getProperty(name)
    returnValue(value)

  def __str__(self):
    return 'Build request %s' % self.brid

  def __repr__(self):
    return 'BuildRequestGateway(brid=%s)' % self.brid

  @inlineCallbacks
  def cancel(self):
    yield self._ensure_build_request()
    yield self.build_request.cancelBuildRequest()

  @inlineCallbacks
  def is_failed(self):
    """Returns True if build request is marked failed in the database.

    Performs a database query, does not a cached value.
    """
    brdict = yield self.master.db.buildrequests.getBuildRequest(self.brid)
    returnValue(
        brdict.get('complete', False) and
        brdict.get('results') == build_results.FAILURE
    )


class BuildbotGateway(object):
  """All buildbot APIs needed by BuildBucketIntegrator to function.

  Handy to mock.
  """

  def __init__(self, master):
    """Creates a BuildbotGateway.

    Args:
      master (buildbot.master.BuildMaster): the buildbot master.
    """
    assert master, 'master not specified'
    self.master = master

  def find_changes_by_revision(self, revision):
    """Searches for Changes in database by |revision| and returns change ids."""
    def find(conn):
      table = self.master.db.model.changes
      q = sa.select([table.c.changeid]).where(table.c.revision == revision)
      return [row.changeid for row in conn.execute(q)]
    return self.master.db.pool.do(find)

  @inlineCallbacks
  def get_change_by_id(self, change_id):
    """Returns buildot.changes.changes.Change as Deferred for |change_id|."""
    chdict = yield self.master.db.changes.getChange(change_id)
    change = yield Change.fromChdict(self.master, chdict)
    returnValue(change)

  def get_cache(self, name, miss_fn):
    """Returns a buildbot.util.lru.AsyncLRUCache by |name|.

    Args:
      name (str): cache name. If called twice with the same name, returns the
        same object.
      miss_fn (func): function cache_key -> value. Used on cache miss.
    """
    return self.master.caches.get_cache(name, miss_fn)

  def add_change_to_db(self, **kwargs):
    """Adds a change to buildbot database.

    See buildbot.db.changes.ChangesConnectorComponent.addChange for arguments.
    """
    return self.master.db.changes.addChange(**kwargs)

  def insert_source_stamp_to_db(self, **kwargs):
    """Inserts a SourceStamp to buildbot database.

    See buildbot.db.sourcestamps.SourceStampsConnectorComponent.addSourceStamp
    for arguments.
    """
    return self.master.db.sourcestamps.addSourceStamp(**kwargs)

  def get_builders(self):
    """Returns a map of builderName -> buildbot.status.builder.BuilderStatus."""
    status = self.master.getStatus()
    names = status.getBuilderNames()
    return {name:status.getBuilder(name) for name in names}

  def get_slaves(self):
    """Returns a list of all slaves.

    Returns:
      A list of buildbot.status.slave.SlaveStatus.
    """
    status = self.master.getStatus()
    return map(status.getSlave, status.getSlaveNames())

  def get_connected_slaves(self):
    """Returns a list of all connected slaves.

    Returns:
      A list of buildbot.status.slave.SlaveStatus.
    """
    return filter(lambda s: s.isConnected(), self.get_slaves())

  @inlineCallbacks
  def add_build_request(
      self, ssid, reason, builder_name, properties_with_source,
      external_idstring):
    """Adds a build request to buildbot database."""
    _, brids = yield self.master.addBuildset(
        ssid=ssid,
        reason=reason,
        builderNames=[builder_name],
        properties=properties_with_source,
        external_idstring=external_idstring,
    )
    assert len(brids) == 1
    returnValue(BuildRequestGateway(self.master, brid=brids[builder_name]))

  @inlineCallbacks
  def get_incomplete_build_requests(self):
    """Returns not yet completed build requests from the database as Deferred.
    """
    build_request_dicts = yield self.master.db.buildrequests.getBuildRequests(
        complete=False)
    requests = []
    for brdict in build_request_dicts:
      # TODO(nodir): optimize: run these queries in parallel.
      req = yield BuildRequest.fromBrdict(self.master, brdict)
      requests.append(BuildRequestGateway(self.master, build_request=req))
    returnValue(requests)

  def get_build_url(self, build):
    """Returns a URL for the |build|."""
    return self.master.getStatus().getURLForThing(build)

  def stop_build(self, build, reason):
    """Stops the |build|."""
    control = IControl(self.master)  # request IControl from self.master.
    builder_control = control.getBuilder(build.getBuilder().getName())
    assert builder_control
    build_control = builder_control.getBuild(build.getNumber())
    assert build_control
    build_control.stopBuild(reason)
