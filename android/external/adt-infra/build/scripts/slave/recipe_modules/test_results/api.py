# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


class TestResultsApi(recipe_api.RecipeApi):
  """Recipe module to upload gtest json results to test-results server."""

  # TODO(estaab): Make test_results_server a configuration value.
  def upload(self, gtest_results_file, test_type, chrome_revision,
             test_results_server, downgrade_error_to_warning=True):
    """Upload gtest results json to test-results.

    Args:
      gtest_results_file: Path to file containing gtest json.
      test_type: Test type string, e.g. layout-tests.
      test_results_server: Server where results should be uploaded.
      downgrade_error_to_warning: If True, treat a failure to upload as a
          warning.

    Returns:
      The step result.
    """
    try:
      self.m.python(
          name='Upload to test-results [%s]' % test_type,
          script=self.resource('upload_gtest_test_results.py'),
          args=['--input-gtest-json', gtest_results_file,
                '--master-name', self.m.properties['mastername'],
                '--builder-name', self.m.properties['buildername'],
                '--build-number', self.m.properties['buildnumber'],
                '--test-type', test_type,
                '--test-results-server', test_results_server,
                '--chrome-revision', chrome_revision])
    finally:
      step_result = self.m.step.active_result
      if (downgrade_error_to_warning and
          step_result.presentation.status == self.m.step.FAILURE):
        step_result.presentation.status = self.m.step.WARNING
      return step_result

