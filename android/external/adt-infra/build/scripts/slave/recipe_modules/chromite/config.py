# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import types

from recipe_engine.config import config_item_context, ConfigGroup
from recipe_engine.config import Dict, Single, Set

import DEPS
path_api = DEPS['path'].api


def BaseConfig(CBB_CONFIG=None, CBB_BRANCH=None, CBB_BUILD_NUMBER=None,
               CBB_DEBUG=False, CBB_CLOBBER=False, **_kwargs):
  return ConfigGroup(
    # Base mapping of repository key to repository name.
    repositories = Dict(value_type=Set(basestring)),

    # Checkout Chromite at this branch. "origin/" will be prepended.
    chromite_branch = Single(basestring, empty_val=CBB_BRANCH or 'master'),

    # Should the Chrome version be supplied to cbuildbot?
    use_chrome_version = Single(bool),

    # Should the CrOS manifest commit message be parsed and added to 'cbuildbot'
    # flags?
    read_cros_manifest = Single(bool),

    cbb = ConfigGroup(
      # The Chromite configuration to use.
      config = Single(basestring, empty_val=CBB_CONFIG),

      # The buildroot directory name to use.
      builddir = Single(basestring),

      # If supplied, forward to cbuildbot as '--master-build-id'.
      build_id = Single(basestring),

      # If supplied, forward to cbuildbot as '--buildnumber'.
      build_number = Single(int, empty_val=CBB_BUILD_NUMBER),

      # If supplied, forward to cbuildbot as '--chrome-rev'.
      chrome_rev = Single(basestring),

      # If supplied, forward to cbuildbot as '--chrome_version'.
      chrome_version = Single(basestring),

      # If True, add cbuildbot flag: '--debug'.
      debug = Single(bool, empty_val=CBB_DEBUG),

      # If True, add cbuildbot flag: '--clobber'.
      clobber = Single(bool, empty_val=CBB_CLOBBER),

      # The (optional) configuration repository to use.
      config_repo = Single(basestring),

      # This disables Chromite bootstrapping by omitting the explicit "--branch"
      # argument.
      disable_bootstrap = Single(bool),
    ),

    # A list of branches whose Chromite version is "old". Old Chromite
    # buildbot commands reside in the "buildbot" subdirectory of the Chromite
    # repository instead of the "bin".
    old_chromite_branches = Set(basestring),

    # A list of branches whose builders should not use a shared buildroot.
    non_shared_root_branches = Set(basestring),

    # A list of branches whose builders checkout Chrome from SVN instead of Git.
    chrome_svn_branches = Set(basestring),
  )

config_ctx = config_item_context(BaseConfig)


@config_ctx()
def base(c):
  c.repositories['tryjob'] = []
  c.repositories['chromium'] = []
  c.repositories['cros_manifest'] = []

  c.old_chromite_branches.update((
    'firmware-uboot_v2-1299.B',
    'factory-1412.B',
  ))
  c.non_shared_root_branches.update(c.old_chromite_branches)
  c.non_shared_root_branches.update((
    'factory-2305.B',
  ))
  c.chrome_svn_branches.update((
    'factory-4455.B',
    'factory-zako-5220.B',
    'factory-rambi-5517.B',
    'factory-nyan-5772.B',
  ))


  # If running on a testing slave, enable "--debug" so Chromite doesn't cause
  # actual production effects.
  if 'TESTING_MASTER_HOST' in os.environ:  # pragma: no cover
    c.cbb.debug = True


@config_ctx(includes=['base'])
def external(c):
  c.repositories['tryjob'].extend([
      'https://chromium.googlesource.com/chromiumos/tryjobs',
      'https://chrome-internal.googlesource.com/chromeos/tryjobs',
      ])
  c.repositories['chromium'].append(
      'https://chromium.googlesource.com/chromium/src')
  c.repositories['cros_manifest'].append(
      'https://chromium.googlesource.com/chromiumos/manifest-versions')


@config_ctx(group='master', includes=['external'])
def master_chromiumos_chromium(c):
  c.use_chrome_version = True
  c.cbb.builddir = 'shared_external'


@config_ctx(group='master', includes=['external'])
def master_chromiumos(c):
  c.cbb.builddir = 'external_master'

@config_ctx()
def chromiumos_paladin(c):
  c.read_cros_manifest = True

@config_ctx(group='master', includes=['external'])
def chromiumos_tryserver(c):
  c.cbb.disable_bootstrap = True

@config_ctx()
def chromiumos_coverage_test(c):
  c.use_chrome_version = True
  c.read_cros_manifest = True
  c.cbb.chrome_rev = 'stable'
