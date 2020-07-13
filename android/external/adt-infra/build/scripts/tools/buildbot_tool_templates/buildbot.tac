# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file is used by scripts/tools/buildbot-tool to generate master configs.

import os

from twisted.application import service
from buildbot.master import BuildMaster

basedir = os.path.dirname(os.path.abspath(__file__))
configfile = 'master.cfg'

application = service.Application('buildmaster')
BuildMaster(basedir, configfile).setServiceParent(application)
