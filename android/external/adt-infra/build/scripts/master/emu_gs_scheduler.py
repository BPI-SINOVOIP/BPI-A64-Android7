# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This SingleBranchScheduler download necessary emulator package and include it in build properties

"""

import os, sys
import gcs_oauth2_boto_plugin
import StringIO
from boto import boto
from twisted.python import log
from twisted.internet import defer, utils
from buildbot.schedulers.timed import Periodic
from buildbot.schedulers.basic import SingleBranchScheduler

google_storage = 'gs'

class EmulatorSingleBranchScheduler(SingleBranchScheduler):
  """Augmented 'SingleBranchScheduler' that adds emu_image properties"""

  # Overrides 'SingleBranchScheduler.addBuildsetForChanges'
  @defer.inlineCallbacks
  def addBuildsetForChanges(self, *args, **kwargs):
    for x in ['windows', 'linux', 'mac']:
      if x in self.name:
        emu_cache_file = 'emulator_%s_poller.cache' % x
    try:
        with open(emu_cache_file, 'r') as f:
            content = f.read().splitlines()
            emu_revision = content[0]
            emu_file = ','.join(content[1:])
    except:
        log.msg("%s: Error - emulator cache file not available, cancel build" % self.name)
        cancel_build = True
    try:
        with open('sys_image_lmp_mr1_poller.cache', 'r') as f:
            content = f.read().splitlines()
            lmp_mr1_revision = content[0]
            lmp_mr1_file = ','.join(content[1:])
    except:
        lmp_mr1_revision = 'None'
        lmp_mr1_file = ''
    try:
        with open('sys_image_mnc_poller.cache', 'r') as f:
            content = f.read().splitlines()
            mnc_revision = content[0]
            mnc_file = ','.join(content[1:])
    except:
        mnc_revision = 'None'
        mnc_file = ''
    try:
        with open('sys_image_nyc_poller.cache', 'r') as f:
            content = f.read().splitlines()
            nyc_revision = content[0]
            nyc_file = ','.join(content[1:])
    except:
        nyc_revision = 'None'
        nyc_file = ''
    try:
        with open('sys_image_lmp_poller.cache', 'r') as f:
            content = f.read().splitlines()
            lmp_revision = content[0]
            lmp_file = ','.join(content[1:])
    except:
        lmp_revision = 'None'
        lmp_file = ''
    try:
        with open('sys_image_klp_poller.cache', 'r') as f:
            content = f.read().splitlines()
            klp_revision = content[0]
            klp_file = ','.join(content[1:])
    except:
        klp_revision = 'None'
        klp_file = ''
    self.properties.setProperty('mnc_revision', mnc_revision, 'Scheduler')
    self.properties.setProperty('mnc_system_image', mnc_file, 'Scheduler')
    self.properties.setProperty('lmp_mr1_revision', lmp_mr1_revision, 'Scheduler')
    self.properties.setProperty('lmp_mr1_system_image', lmp_mr1_file, 'Scheduler')
    self.properties.setProperty('nyc_revision', nyc_revision, 'Scheduler')
    self.properties.setProperty('nyc_system_image', nyc_file, 'Scheduler')
    self.properties.setProperty('klp_revision', klp_revision, 'Scheduler')
    self.properties.setProperty('klp_system_image', klp_file, 'Scheduler')
    self.properties.setProperty('lmp_revision', lmp_revision, 'Scheduler')
    self.properties.setProperty('lmp_system_image', lmp_file, 'Scheduler')
    self.properties.setProperty('emu_revision', emu_revision, 'Scheduler')
    self.properties.setProperty('emulator_image', emu_file, 'Scheduler')
    self.properties.setProperty('got_revision', '%s-%s-%s-%s-%s' % (emu_revision, mnc_revision, lmp_mr1_revision, nyc_revision.split('-')[0], lmp_revision), 'Scheduler')
    self.properties.setProperty('logs_dir', os.path.join(os.getcwd(), 'slave_logs', ''), 'Scheduler')

    rv = yield SingleBranchScheduler.addBuildsetForChanges(
        self,
        *args,
        **kwargs)
    defer.returnValue(rv)
