# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from buildbot.status import builder as build_results
from buildbot.status.base import StatusReceiverMultiService
from twisted.internet.defer import inlineCallbacks, returnValue

from . import common
from .buildbot_gateway import BuildbotGateway


BUILD_STATUS_NAMES = {
    build_results.EXCEPTION: 'EXCEPTION',
    build_results.FAILURE: 'FAILURE',
    build_results.RETRY: 'RETRY',
    build_results.SKIPPED: 'SKIPPED',
    build_results.SUCCESS: 'SUCCESS',
    build_results.WARNINGS: 'SUCCESS',  # Treat warnings as SUCCESS.
}


class BuildBucketStatus(StatusReceiverMultiService):
  """Updates build status on buildbucket."""

  def __init__(self, integrator, buildbucket_service_factory, dry_run):
    """Creates a new BuildBucketStatus.

    Args:
      integrator (BuildBucketIntegrator): integrator to notify about status
        changes.
      buildbucket_service_factory (function): returns a DeferredResource as
        Deferred that will be used to access buildbucket service API.
      dry_run (bool): if True, do not start integrator.
    """
    StatusReceiverMultiService.__init__(self)
    self.integrator = integrator
    self.buildbucket_service_factory = buildbucket_service_factory
    self.dry_run = dry_run
    self.integrator_starting = None

  @inlineCallbacks
  def _start_integrator(self):
    buildbucket_service = yield self.buildbucket_service_factory()
    buildbot = BuildbotGateway(self.parent)
    self.integrator.start(buildbot, buildbucket_service)

  def _run_when_started(self, fn, *args):
    assert self.integrator_starting
    d = self.integrator_starting.addCallback(lambda _: fn(*args))
    common.log_on_error(d)

  def startService(self):
    StatusReceiverMultiService.startService(self)
    if self.dry_run:
      return
    self.integrator_starting = self._start_integrator()
    common.log_on_error(self.integrator_starting, 'Could not start integrator')
    self._run_when_started(self.integrator.poll_builds)
    self.parent.getStatus().subscribe(self)

  def stopService(self):
    self.integrator.stop()
    StatusReceiverMultiService.stopService(self)

  # pylint: disable=W0613
  def builderAdded(self, name, builder):
    # Subscribe to this builder.
    return self

  def buildStarted(self, builder_name, build):
    if self.dry_run:
      return
    self._run_when_started(self.integrator.on_build_started, build)

  def buildFinished(self, builder_name, build, result):
    if self.dry_run:
      return
    assert result in BUILD_STATUS_NAMES
    status = BUILD_STATUS_NAMES[result]
    self._run_when_started(self.integrator.on_build_finished, build, status)
