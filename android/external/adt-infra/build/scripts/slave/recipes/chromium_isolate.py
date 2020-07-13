# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This recipe runs all isolated tests specified in swarm_hashes property.
Isolating a test is required in order to run it using this recipe.
"""

from recipe_engine.types import freeze

DEPS = [
    'chromium',
    'isolate',
    'properties',
    'swarming_client',
]

# TODO(nodir): pass these arguments from builder to tester once triggering from
# recipes lands. This is needed for ARM testers http://crbug.com/359338
TEST_ARGS = freeze({
    'browser_tests': ['--gtest_filter=*NaCl*.*'],
    'sandbox_linux_unittests': ['--test-launcher-print-test-stdio=always'],
})


def RunSteps(api):
  config_name = api.properties.get('chromium_config') or 'chromium'
  api.chromium.set_config(config_name)

  api.swarming_client.checkout()

  revision = api.properties['parent_got_revision']
  webkit_revision = api.properties['parent_got_webkit_revision']
  for test in sorted(api.isolate.isolated_tests):
    api.isolate.runtest(test, revision, webkit_revision,
                        args=TEST_ARGS.get(test))


def GenTests(api):
  props = api.properties.generic(
      parent_got_revision=123,
      parent_got_webkit_revision=321,
      parent_got_swarming_client_revision=
          'ae8085b09e6162b4ec869e430d7d09c16b32b433',
      swarm_hashes={
          "browser_tests": "23f4ed98b3616e695602920b8d6c679091e8d8ce"}
  )

  yield api.test('run_isolated_tests') + props
