# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'json',
  'properties',
  'test_results',
]


def RunSteps(api):
  gtest_results = {
      'disabled_tests': [
          'Disabled.Test',
      ],
      'per_iteration_data': [{
          'Skipped.Test': [
              {'status': 'SKIPPED', 'elapsed_time_ms': 0},
          ],
      }],
  }
  api.test_results.upload(
      api.json.input(gtest_results),
      chrome_revision=2,
      test_type='example-test-type',
      test_results_server='localhost',
      downgrade_error_to_warning=api.properties.get('warning'))


def GenTests(api):
  yield (
      api.test('upload_success') +
      api.properties(
          mastername='example.master',
          buildername='ExampleBuilder',
          buildnumber=123))

  yield (
      api.test('upload_and_degrade_to_warning') +
      api.step_data('Upload to test-results [example-test-type]', retcode=1) +
      api.properties(
          mastername='example.master',
          buildername='ExampleBuilder',
          buildnumber=123,
          warning=True))

  yield (
      api.test('upload_without_degrading_failures') +
      api.step_data('Upload to test-results [example-test-type]', retcode=1) +
      api.properties(
          mastername='example.master',
          buildername='ExampleBuilder',
          buildnumber=123,
          warning=False))
