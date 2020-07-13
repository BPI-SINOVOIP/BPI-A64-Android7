# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Example of using the Syzygy recipe module."""

# Recipe module dependencies.
DEPS = [
  'chromium',
  'gclient',
  'platform',
  'properties',
  'syzygy',
]


from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  # Configure the build environment.
  if buildername == 'Syzygy Debug':
    api.syzygy.set_config('syzygy_msvs', BUILD_CONFIG='Debug')
    api.chromium.set_config('syzygy_msvs', BUILD_CONFIG='Debug')
    api.gclient.set_config('syzygy_msvs', BUILD_CONFIG='Debug')
  elif buildername == 'Syzygy Coverage':
    api.syzygy.set_config('syzygy', BUILD_CONFIG='Coverage')
    api.chromium.set_config('syzygy', BUILD_CONFIG='Coverage')
    api.gclient.set_config('syzygy', BUILD_CONFIG='Coverage')
  else:
    assert buildername == 'Syzygy Official'
    api.syzygy.set_config('syzygy_official')
    api.chromium.set_config('syzygy_official')
    api.gclient.set_config('syzygy_official')

  # Clean up any running processes on the slave.
  api.syzygy.taskkill()

  # Checkout and compile the project.
  api.syzygy.checkout()
  api.syzygy.runhooks()
  api.syzygy.compile()

  # Run every possible test. Most builders also run a subset of these.
  api.syzygy.clobber_metrics()
  unittests = api.syzygy.read_unittests_gypi()
  api.syzygy.run_unittests(unittests)
  api.syzygy.randomly_reorder_chrome()
  api.syzygy.benchmark_chrome()
  api.syzygy.archive_metrics()

  if buildername == 'Syzygy Coverage':
    # These can only run in coverage builds and have asserts to enforce it.
    api.syzygy.capture_unittest_coverage()
    api.syzygy.archive_coverage()

  if buildername == 'Syzygy Official':
    # These can only run in coverage builds and have asserts to enforce it.
    api.syzygy.archive_binaries()
    api.syzygy.upload_symbols()

  # These are smoke test specific, but will run on any builder.
  api.syzygy.download_binaries()
  api.syzygy.smoke_test()


def GenTests(api):
  """Generates an end-to-end successful test for this builder."""
  yield api.syzygy.generate_test(api, 'Syzygy Debug')

  # Use 'fake_slave' as a slave name to ensure that we get coverage of the
  # alternate code paths in Coverage and Official specific commands builds.
  yield api.syzygy.generate_test(api, 'Syzygy Coverage', slavename='fake_slave')
  yield api.syzygy.generate_test(api, 'Syzygy Official', slavename='fake_slave')
