# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import mock
import webapp2
import webtest

from dashboard import bisect_fyi
from dashboard import start_try_job
from dashboard import stored_object
from dashboard import testing_common

TEST_FYI_CONFIGS = {
    'positive_culprit': {
        'bisect_config': {
            'bad_revision': '357672',
            'bug_id': 111,
            'command': ('python src/tools/perf/run_benchmark -v '
                        '--browser=release_x64 --output-format=chartjson '
                        '--also-run-disabled-tests blink_perf.bindings'),
            'good_revision': '357643',
            'gs_bucket': 'chrome-perf',
            'max_time_minutes': '20',
            'metric': 'create-element/create-element',
            'recipe_tester_name': 'win_x64_perf_bisect',
            'repeat_count': '10',
            'test_type': 'perf'
        },
        'expected_results': {
            'status': ['completed'],
            'culprit_data': {'cl': ['2a1781d64d']},
        }
    },
    'early_abort': {
        'bisect_config': {
            'bad_revision': '257672',
            'bug_id': 222,
            'command': ('python src/tools/perf/run_benchmark -v '
                        '--browser=release_x64 --output-format=chartjson '
                        '--also-run-disabled-tests blink_perf.bindings'),
            'good_revision': '257643',
            'gs_bucket': 'chrome-perf',
            'max_time_minutes': '20',
            'metric': 'create-element/create-element',
            'recipe_tester_name': 'win_x64_perf_bisect',
            'repeat_count': '10',
            'test_type': 'perf'
        },
        'expected_results': {
            'status': ['aborted'],
        }
    },
}


class BisectFYITest(testing_common.TestCase):

  def setUp(self):
    super(BisectFYITest, self).setUp()
    stored_object.Set(
        bisect_fyi._BISECT_FYI_CONFIGS_KEY, TEST_FYI_CONFIGS)
    stored_object.Set(
        start_try_job._TESTER_DIRECTOR_MAP_KEY,
        {
            'linux_perf_bisect': 'linux_perf_bisector',
            'win_x64_perf_bisect': 'linux_perf_bisector',
        })
    app = webapp2.WSGIApplication(
        [('/bisect_fyi', bisect_fyi.BisectFYIHandler)])
    self.testapp = webtest.TestApp(app)

  @mock.patch.object(bisect_fyi.start_try_job, '_PerformBuildbucketBisect')
  def testPost_FailedJobs_BisectFYI(self, mock_perform_bisect):
    mock_perform_bisect.return_value = {'error': 'PerformBisect Failed'}
    self.testapp.post('/bisect_fyi')
    messages = self.mail_stub.get_sent_messages()
    self.assertEqual(1, len(messages))

  @mock.patch.object(bisect_fyi.start_try_job, '_PerformBuildbucketBisect')
  def testPost_SuccessJobs_BisectFYI(self, mock_perform_bisect):
    mock_perform_bisect.return_value = {'issue_id': 'http://fake'}
    self.testapp.post('/bisect_fyi')
    messages = self.mail_stub.get_sent_messages()
    self.assertEqual(0, len(messages))


if __name__ == '__main__':
  unittest.main()
