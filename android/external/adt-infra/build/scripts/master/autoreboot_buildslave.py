# Copyright (c) 2009 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A slave that reboots after each job.

Yeah, we trust our unit tests *that* much.
"""

import os

from buildbot.buildslave import BuildSlave


class AutoRebootBuildSlave(BuildSlave):
  def __init__(self, *args, **kwargs):
    """Enforces max_builds == 1 for obvious reasons."""
    kwargs['max_builds'] = 1
    BuildSlave.__init__(self, *args, **kwargs)

  def buildFinished(self, sb):
    """This is called when a build on this slave is finished."""
    flag_path = os.path.join(self.parent.master.basedir,
                             '.enable_perspective_shutdown')
    if os.path.exists(flag_path):
      # TODO(nodir): remove check when ready and deploy everywhere
      # Mark the build slave is to be shut down, so it does not accept jobs.
      self.perspective_shutdown()

    # Actually shutdown the slave.
    return self.shutdown()
