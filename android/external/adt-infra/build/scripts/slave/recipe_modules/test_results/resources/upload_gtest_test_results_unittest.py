#!/usr/bin/env python
# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for upload_gtest_test_results.py."""

import json
import unittest

import upload_gtest_test_results


class UploadGtestTestResultsTest(unittest.TestCase):

  def setUp(self):
    pass

  def test_no_test_data(self):
    results = upload_gtest_test_results.get_results_map_from_json(
        json.dumps({}))
    self.assertEquals({}, results)

  def test_multiple_results(self):
    contents = {
        'per_iteration_data': [{
            'Fake.Test': [
                {'status': 'FAILURE', 'elapsed_time_ms': 1000},
                {'status': 'SUCCESS', 'elapsed_time_ms': 0},
            ],
        }],
    }
    results = upload_gtest_test_results.get_results_map_from_json(
        json.dumps(contents))
    self.assertEquals('FAIL', results['Fake.Test'][0].status)
    self.assertEquals(1, results['Fake.Test'][0].test_run_time)
    self.assertEquals('PASS', results['Fake.Test'][1].status)
    self.assertEquals(0, results['Fake.Test'][1].test_run_time)

  def test_bad_status(self):
    contents = {
        'per_iteration_data': [{
            'Fake.Test': [
                {'status': 'XXX', 'elapsed_time_ms': 1000},
            ],
        }],
    }
    results = upload_gtest_test_results.get_results_map_from_json(
        json.dumps(contents))
    self.assertEquals('UNKNOWN', results['Fake.Test'][0].status)
    self.assertEquals(1, results['Fake.Test'][0].test_run_time)

  def test_skipped(self):
    contents = {
        'disabled_tests': [
            'Disabled.Test',
        ],
        'per_iteration_data': [{
            'Skipped.Test': [
                {'status': 'SKIPPED', 'elapsed_time_ms': 0},
            ],
        }],
    }
    results = upload_gtest_test_results.get_results_map_from_json(
        json.dumps(contents))
    self.assertEquals(results['Disabled.Test'][0].DISABLED,
                      results['Disabled.Test'][0].modifier)
    self.assertEquals(results['Disabled.Test'][0].DISABLED,
                      results['Skipped.Test'][0].modifier)


if __name__ == '__main__':
  unittest.main()
