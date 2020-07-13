# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'chromite',
  'gitiles',
  'properties',
]


# Map master name to 'chromite' configuration name.
_MASTER_CONFIG_MAP = {
  'chromiumos': {
    'master_config': 'master_chromiumos',
    'variants': {
      'paladin': ['chromiumos_paladin'],
    },
  },
  'chromiumos.chromium': {
    'master_config': 'master_chromiumos_chromium',
  },

  # Fake waterfall for Coverage
  'chromiumos.coverage': {
    'master_config': 'master_chromiumos',
    'variants': {
      'test': ['chromiumos_coverage_test'],
    },
  },
}

def RunSteps(api):
  # Load the appropriate configuration based on the master.
  api.chromite.configure(
      api.properties,
      _MASTER_CONFIG_MAP)

  # Run 'cbuildbot' common recipe.
  api.chromite.run_cbuildbot()

def GenTests(api):
  #
  # master.chromiumos.chromium
  #

  # Test a standard CrOS build triggered by a Chromium commit.
  yield (
      api.test('chromiumos_chromium_builder')
      + api.properties(
          mastername='chromiumos.chromium',
          buildername='Test',
          slavename='test',
          buildnumber='12345',
          repository='https://chromium.googlesource.com/chromium/src',
          revision='b8819267417da248aa4fe829c5fcf0965e17b0c3',
          branch='master',
          cbb_config='x86-generic-tot-chrome-pfq-informational',
      )
  )

  #
  # master.chromiumos
  #

  # Test a ChromiumOS clobber Paladin build.
  yield (
      api.test('chromiumos_paladin')
      + api.properties(
          mastername='chromiumos',
          buildername='Test',
          slavename='test',
          buildnumber='12345',
          repository='https://chromium.googlesource.com/chromiumos/'
                     'manifest-versions',
          branch='master',
          revision=api.gitiles.make_hash('test'),
          clobber=None,
          cbb_config='x86-generic-paladin',
          cbb_variant='paladin',
      )
      + api.step_data(
          'Fetch manifest config',
          api.gitiles.make_commit_test_data(
              'test',
              '\n'.join([
                  'Commit message!',
                  'Automatic: Start master-paladin master 6952.0.0-rc4',
                  'CrOS-Build-Id: 1337',
              ]),
          ),
      )
  )

  # Test a ChromiumOS Paladin build whose manifest is not parsable.
  yield (
      api.test('chromiumos_paladin_manifest_failure')
      + api.properties(
          mastername='chromiumos',
          buildername='Test',
          slavename='test',
          buildnumber='12345',
          repository='https://chromium.googlesource.com/chromiumos/'
                     'manifest-versions',
          branch='master',
          revision=api.gitiles.make_hash('test'),
          cbb_config='x86-generic-paladin',
          cbb_variant='paladin',
      )
      + api.step_data(
          'Fetch manifest config',
          api.gitiles.make_commit_test_data(
              'test',
              None
          )
      )
  )

  #
  # [Coverage]
  #

  # Test a standard CrOS build triggered by a Chromium commit.
  yield (
      api.test('chromiumos_coverage')
      + api.properties(
          mastername='chromiumos.coverage',
          buildername='Test',
          slavename='test',
          buildnumber=0,
          repository='https://chromium.googlesource.com/chromiumos/'
                     'chromite.git',
          revision='fdea0dde664e229976ddb2224328da152fba15b1',
          branch='master',
          cbb_config='x86-generic-full',
          cbb_branch='factory-1412.B',
          cbb_variant='test',
          cbb_debug=True,
          cbb_disable_branch=True,
          config_repo='https://fake.googlesource.com/myconfig/repo.git',
      )
  )
