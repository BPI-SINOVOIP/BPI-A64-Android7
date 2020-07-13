# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import sys
import unittest

# TODO(robertocn): Use abspath for these, to prevent relative path errors.
# For the importing of mock.
_ROOT_DIR = os.path.join(os.path.dirname(__file__), os.path.pardir,
                         os.path.pardir, os.path.pardir, os.path.pardir)

sys.path.append(os.path.join(_ROOT_DIR, 'third_party', 'mock-1.0.1'))

import mock

from .bisector import Bisector


class MockRevisionClass(object):  # pragma: no cover

  def __init__(self, rev_string, bisector):
    self.commit_pos = int(rev_string)
    self.revision_string = rev_string
    self.bisector = bisector
    self.previous_revision = None
    self.next_revision = None
    self.values = []

  def get_next_url(self):
    if self.in_progress:
      return 'mockurl'
    return None


class BisectorTest(unittest.TestCase):  # pragma: no cover

  def setUp(self):
    self.bisect_config = {
        'test_type': 'perf',
        'command': ('tools/perf/run_benchmark -v '
                    '--browser=release page_cycler.intl_ar_fa_he'),
        'good_revision': '306475',
        'bad_revision': '306478',
        'metric': 'warm_times/page_load_time',
        'repeat_count': '2',
        'max_time_minutes': '5',
        'bug_id': '425582',
        'gs_bucket': 'chrome-perf',
        'builder_host': 'master4.golo.chromium.org',
        'builder_port': '8341',
        'dummy_builds': True,
    }
    self.dummy_api = mock.Mock()

  def test_create_bisector(self):
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    # Check the proper revision range is initialized
    self.assertEqual(4, len(bisector.revisions))
    a, b, c, d = bisector.revisions  # pylint: disable=unbalanced-tuple-unpacking
    # Check that revisions are properly chained
    self.assertEqual(a, b.previous_revision)
    self.assertEqual(b, c.previous_revision)
    self.assertEqual(c, d.previous_revision)
    self.assertEqual(d, c.next_revision)
    self.assertEqual(c, b.next_revision)
    self.assertEqual(b, a.next_revision)

    # Check the ends are grounded
    self.assertIsNone(a.previous_revision)
    self.assertIsNone(d.next_revision)

    # Check the reference range is set with correct 'goodness' values
    self.assertTrue(a.good)
    self.assertTrue(d.bad)

  def test_improvement_direction_default(self):
    # By default, no improvement direction should be set
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertIsNone(bisector.improvement_direction)

  def test_improvement_direction_greater_is_better(self):
    # Improvement up, bad > good: should fail
    self.bisect_config['improvement_direction'] = 1
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    bisector.good_rev.mean_value = 10
    bisector.bad_rev.mean_value = 100
    self.assertFalse(bisector.check_improvement_direction())
    self.assertIn('direction of improvement', ''.join(bisector.warnings))

    # Improvement up, bad < good: should not fail
    self.bisect_config['improvement_direction'] = 1
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    bisector.good_rev.mean_value = 100
    bisector.bad_rev.mean_value = 10
    self.assertTrue(bisector.check_improvement_direction())
    self.assertNotIn('direction of improvement', ''.join(bisector.warnings))

  def test_improvement_direction_lower_is_better(self):
    # Improvement down, bad < good: should fail
    self.bisect_config['improvement_direction'] = -1
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    bisector.good_rev.mean_value = 100
    bisector.bad_rev.mean_value = 10
    self.assertFalse(bisector.check_improvement_direction())
    self.assertIn('direction of improvement', ''.join(bisector.warnings))

    # Improvement down, bad > good: should not fail
    self.bisect_config['improvement_direction'] = -1
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    bisector.good_rev.mean_value = 10
    bisector.bad_rev.mean_value = 100
    self.assertTrue(bisector.check_improvement_direction())
    self.assertNotIn('direction of improvement', ''.join(bisector.warnings))

  def test_check_initial_confidence_default(self):
    # Test default required confidence (default may change)
    mock_score = self.dummy_api.m.math_utils.confidence_score
    # A confidence score of 0 should not satisfy any default
    mock_score.return_value = 0
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertFalse(bisector.check_initial_confidence())
    self.assertTrue(bisector.failed_initial_confidence)

    # A confidence score of 100 should satisfy any default
    mock_score.return_value = 100
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertTrue(bisector.check_initial_confidence())
    self.assertFalse(bisector.failed_initial_confidence)

  def test_check_initial_confidence_not_required(self):
    # When confidence is not required, confidence_score should not be called
    mock_score = self.dummy_api.m.math_utils.confidence_score
    self.bisect_config['required_initial_confidence'] = None
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertTrue(bisector.check_initial_confidence())
    self.assertFalse(mock_score.called)

  def test_check_initial_confidence_arbitrary(self):
    mock_score = self.dummy_api.m.math_utils.confidence_score
    self.bisect_config['required_initial_confidence'] = 99
    # A confidence score of 98.5 should not satisfy the required 99
    mock_score.return_value = 98.5
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertFalse(bisector.check_initial_confidence())
    self.assertTrue(bisector.failed_initial_confidence)

    # A confidence score of 99.5 should satisfy the required 99
    mock_score.return_value = 99.5
    bisector = Bisector(self.dummy_api, self.bisect_config, MockRevisionClass)
    self.assertTrue(bisector.check_initial_confidence())
    self.assertFalse(bisector.failed_initial_confidence)

  def test_wait_for_all(self):
    def mock_update_status(s):
      if getattr(s, 'mock_verified', False):
        s.in_progress = False
        return
      s.mock_verified = True
      s.tested = True

    # Plug in mock update_status method
    with mock.patch(
        'bisector_test.MockRevisionClass.update_status',
        mock_update_status):
      bisector = Bisector(
          self.dummy_api, self.bisect_config, MockRevisionClass)
      for r in bisector.revisions:
        r.in_progress = True
      bisector.wait_for_all(bisector.revisions)
      # Verify that all revisions in list where verified by mock_update_status
      self.assertTrue(all([r.mock_verified for r in bisector.revisions]))

  def test_wait_for_any(self):
    # Creating placeholder for the patch
    MockRevisionClass.update_status = None
    with mock.patch(
        'bisector_test.MockRevisionClass.update_status'):
      bisector = Bisector(
          self.dummy_api, self.bisect_config, MockRevisionClass)
      for r in bisector.revisions:
        r.tested = False
        r.in_progress = True
      bisector.revisions[0].tested = True
      finished_revision = bisector.wait_for_any(bisector.revisions)
      self.assertEqual(bisector.revisions[0], finished_revision)


class BisectorAbortTest(unittest.TestCase):  # pragma: no cover

  def setUp(self):
    self.bisect_config = {
        'test_type': 'perf',
        'command': 'tools/perf/run_benchmark -v '
                    '--browser=release page_cycler.intl_ar_fa_he',
        'good_revision': '306475',
        'bad_revision': '306478',
        'metric': 'warm_times/page_load_time',
        'repeat_count': '2',
        'max_time_minutes': '5',
        'bug_id': '425582',
        'gs_bucket': 'chrome-perf',
        'builder_host': 'master4.golo.chromium.org',
        'builder_port': '8341',
        'dummy_builds': True,
    }
    self.dummy_api = mock.Mock()
    self.called_abort = False
    self.aborted_once = False

  def test_abort_unnecessary_jobs(self):
    def mock_abort(_):
      self.called_abort = True
      if self.aborted_once:
        raise RuntimeError('Only one abort expected')
      self.aborted_once = True

    MockRevisionClass.abort = None
    MockRevisionClass.update_status = None
    with mock.patch(
        'bisector_test.MockRevisionClass.update_status'):
      with mock.patch('bisector_test.MockRevisionClass.abort',
                      mock_abort):
        bisector = Bisector(self.dummy_api, self.bisect_config,
                                MockRevisionClass)
        r = bisector.revisions
        r[0].good = True
        r[0].bad = False
        r[0].tested = True
        r[0].in_progress = False

        r[1].in_progress = True
        r[1].tested = False

        r[2].good = True
        r[2].bad = False
        r[2].tested = True
        r[2].in_progress = False

        r[3].bad = True
        r[3].good = False
        r[3].tested = True
        r[3].in_progress = False

        try:
          bisector.abort_unnecessary_jobs()
        except RuntimeError:
          self.fail('Expected to call abort only once')
        self.assertTrue(self.called_abort)

        # Verifying the side effects of updating the candidate range
        self.assertEqual(r[2], bisector.lkgr)
        self.assertEqual(r[3], bisector.fkbr)


# TODO(robertocn): Add test for bisector.check_bisect_finished.
if __name__ == '__main__':
  unittest.main()  # pragma: no cover
