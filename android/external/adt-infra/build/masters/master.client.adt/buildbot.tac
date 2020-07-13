# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file was generated from
# scripts/tools/buildbot_tool_templates/buildbot.tac
# by "../../build/scripts/tools/buildbot-tool gen .".
# DO NOT EDIT BY HAND!


import os

from twisted.application import service
from buildbot.master import BuildMaster

basedir = os.path.dirname(os.path.abspath(__file__))
configfile = 'master.cfg'

application = service.Application('buildmaster')
BuildMaster(basedir, configfile).setServiceParent(application)
