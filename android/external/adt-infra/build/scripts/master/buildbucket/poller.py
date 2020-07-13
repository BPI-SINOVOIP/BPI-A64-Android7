# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from datetime import timedelta

from buildbot.changes.base import PollingChangeSource
from twisted.internet.defer import inlineCallbacks, returnValue


class BuildBucketPoller(PollingChangeSource):
  """Polls builds scheduled by buildbucket service.

  Besides polling, BuildBucketPoller is responsible for starting/stopping
  BuildBucketIntegrator.
  """
  # Is it polling right now?
  _polling = False

  def __init__(self, integrator, poll_interval, dry_run):
    """Creates a new BuildBucketPoller.

    Args:
      integrator (BuildBucketIntegrator): integrator to use for build
        scheduling.
      poll_interval (int): frequency of polling, in seconds.
      dry_run (bool): if True, do not poll.
    """
    assert integrator
    if isinstance(poll_interval, timedelta):
      poll_interval = poll_interval.total_seconds()

    self.integrator = integrator
    if poll_interval:
      self.pollInterval = poll_interval
    self.dry_run = dry_run

  @inlineCallbacks
  def poll(self):
    # Do not schedule multiple polling processes at a time.
    if not self._polling and self.integrator.started and not self.dry_run:
      self._polling = True
      try:
        yield self.integrator.poll_builds()
      finally:
        self._polling = False
