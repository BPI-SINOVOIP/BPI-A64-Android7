#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for functions in runtest.py."""

import unittest

import test_env  # pylint: disable=W0403,W0611

import mock
from slave import runtest


# Note: The git-svn id / cr pos is intentionally modified.
# Also commit messages modified to be < 80 char.
CHROMIUM_LOG = """
Update GPU rasterization device whitelist

This replaces the whitelisting of all Qualcomm GPUs on
Android 4.4 with whitelisting all Android 4.4 devices
with GL ES version >= 3.0.

BUG=405646

Review URL: https://codereview.chromium.org/468103003

Cr-Commit-Position: refs/heads/master@{#291141}
git-svn-id: svn://svn.chromium.org/chrome/trunk/src@291140 0039d316-1c4b-4281
"""

BLINK_LOG = """
[Sheriff-o-matic] Remove race condition on the commit list.

By always modifying the same list of commits, we ensure that data binding

As well, renamed "revisionLog" to "commitLog" everywhere, to better reflect

BUG=405327
NOTRY=true

Review URL: https://codereview.chromium.org/485253004

git-svn-id: svn://svn.chromium.org/blink/trunk@180728 bbb929c8-8fbe-4397-9dbb-9
"""


class FakeLogProcessor(object):
  """A fake log processor to use in the test below."""

  def __init__(self, output):
    self._output = output

  def PerformanceLogs(self):
    return self._output


class GetDataFromLogProcessorTest(unittest.TestCase):
  """Tests related to functions which convert data format."""

  def setUp(self):
    super(GetDataFromLogProcessorTest, self).setUp()

  # Testing private method _GetDataFromLogProcessor.
  # pylint: disable=W0212
  def test_GetDataFromLogProcessor_BasicCase(self):
    """Tests getting of result data from a LogProcessor object."""
    log_processor = FakeLogProcessor({
        'graphs.dat': ['[{"name": "my_graph"}]'],
        'my_graph-summary.dat': ['{"traces": {"x": [1, 0]}, "rev": 123}'],
    })

    # Note that the 'graphs.dat' entry is ignored.
    self.assertEqual(
        {'my_graph': {'traces': {'x': [1, 0]}, 'rev': 123}},
        runtest._GetDataFromLogProcessor(log_processor))

  def test_GetDataFromLogProcessor_OneGraphMultipleLines(self):
    log_processor = FakeLogProcessor({
        'graph-summary.dat': [
            '{"traces": {"x": [1, 0]}, "rev": 123}',
            '{"traces": {"y": [1, 0]}, "rev": 123}',
        ]
    })

    # We always expect the length of the lines list for each graph to be 1.
    # If it doesn't meet this expectation, ignore that graph.
    self.assertEqual({}, runtest._GetDataFromLogProcessor(log_processor))

  def test_GetDataFromLogProcessor_InvalidJson(self):
    log_processor = FakeLogProcessor({
        'graph-summary.dat': ['this string is not valid json']
    })
    self.assertEqual({}, runtest._GetDataFromLogProcessor(log_processor))


class GetGitRevisionTest(unittest.TestCase):
  """Tests related to getting revisions from a directory."""
  def test_GitSvnCase(self):
    # pylint: disable=W0212
    self.assertEqual(runtest._GetGitCommitPositionFromLog(CHROMIUM_LOG),
                     '291141')
    # pylint: disable=W0212
    self.assertEqual(runtest._GetGitCommitPositionFromLog(BLINK_LOG),
                     '180728')

  def test_GetCommitPosFromBuildPropTest(self):
    """Tests related to getting a commit position from build properties."""
    # pylint: disable=W0212
    self.assertEqual(runtest._GetCommitPos(
        {'got_revision_cp': 'refs/heads/master@{#12345}'}), 12345)
    # pylint: disable=W0212
    self.assertIsNone(runtest._GetCommitPos({'got_revision': 12345}))


class SendResultsToDashboardTest(unittest.TestCase):
  """Tests related to sending requests and saving data from failed requests."""

  def setUp(self):
    super(SendResultsToDashboardTest, self).setUp()

  # Testing private method _GetDataFromLogProcessor.
  # Also, this test method doesn't reference self.
  # pylint: disable=W0212,R0201
  @mock.patch('slave.runtest._GetDataFromLogProcessor')
  @mock.patch('slave.results_dashboard.MakeListOfPoints')
  @mock.patch('slave.results_dashboard.SendResults')
  def test_SendResultsToDashboard_SimpleCase(
      self, SendResults, MakeListOfPoints, GetDataFromLogProcessor):
    """Tests that the right methods get called in _SendResultsToDashboard."""
    # Since this method just tests that certain methods get called when
    # a call to _SendResultsDashboard is made, the data used below is arbitrary.
    fake_charts_data = {'chart': {'traces': {'x': [1, 0]}, 'rev': 1000}}
    fake_points_data = [{'test': 'master/bot/chart/x', 'revision': 1000}]
    fake_results_tracker = mock.Mock()
    fake_results_tracker.IsChartJson = mock.MagicMock(return_value=False)
    GetDataFromLogProcessor.return_value = fake_charts_data
    MakeListOfPoints.return_value = fake_points_data

    result = runtest._SendResultsToDashboard(
        fake_results_tracker, {
            'system': 'linux',
            'test': 'sunspider',
            'url': 'http://x.com',
            'build_dir': 'builddir',
            'mastername': 'my.master',
            'buildername': 'Builder',
            'buildnumber': 123,
            'supplemental_columns': {}})

    # First a function is called to get data from the log processor.
    GetDataFromLogProcessor.assert_called_with(fake_results_tracker)

    # Then the data is re-formatted to a format that the dashboard accepts.
    MakeListOfPoints.assert_called_with(
        fake_charts_data, 'linux', 'sunspider', 'Builder', 123, {})

    # Then a function is called to send the data (and any cached data).
    SendResults.assert_called_with(
        fake_points_data, 'http://x.com', 'builddir')

    # No errors, should return True.
    self.assertTrue(result)

  @mock.patch('slave.results_dashboard.MakeDashboardJsonV1')
  @mock.patch('slave.results_dashboard.SendResults')
  def test_SendResultsToDashboard_Telemetry(
      self, SendResults, MakeDashboardJsonV1):
    """Tests that the right methods get called in _SendResultsToDashboard."""
    # Since this method just tests that certain methods get called when
    # a call to _SendResultsDashboard is made, the data used below is arbitrary.
    fake_json_data = {'chart': {'traces': {'x': [1, 0]}, 'rev': 1000}}
    fake_results_tracker = mock.Mock()
    fake_results_tracker.IsChartJson = mock.MagicMock(return_value=True)
    fake_results_tracker.ChartJson = mock.MagicMock(return_value=fake_json_data)
    fake_results_tracker.IsReferenceBuild = mock.MagicMock(return_value=False)
    fake_results_tracker.Cleanup = mock.MagicMock()
    MakeDashboardJsonV1.return_value = {'doesnt': 'matter'}

    result = runtest._SendResultsToDashboard(
        fake_results_tracker, {
            'system': 'linux',
            'test': 'sunspider',
            'url': 'http://x.com',
            'build_dir': 'builddir',
            'mastername': 'my.master',
            'buildername': 'Builder',
            'buildnumber': 123,
            'revisions': {'rev': 343},
            'supplemental_columns': {}})

    # Then the data is re-formatted to a format that the dashboard accepts.
    MakeDashboardJsonV1.assert_called_with(
        fake_json_data, {'rev': 343}, 'sunspider', 'linux',
        'Builder', 123, {}, False)

    # Then a function is called to send the data (and any cached data).
    SendResults.assert_called_with(
        {'doesnt': 'matter'}, 'http://x.com', 'builddir')
    fake_results_tracker.Cleanup.assert_called_with()

    # No errors, should return True.
    self.assertTrue(result)

  @mock.patch('slave.results_dashboard.MakeDashboardJsonV1')
  @mock.patch('slave.results_dashboard.SendResults')
  def test_SendResultsToDashboard_NoTelemetryOutput(
      self, SendResults, MakeDashboardJsonV1):
    """Tests that the right methods get called in _SendResultsToDashboard."""
    fake_results_tracker = mock.Mock()
    fake_results_tracker.IsChartJson = mock.MagicMock(return_value=True)
    fake_results_tracker.ChartJson = mock.MagicMock(return_value=None)
    fake_results_tracker.IsReferenceBuild = mock.MagicMock(return_value=False)
    fake_results_tracker.Cleanup = mock.MagicMock()

    runtest._SendResultsToDashboard(
        fake_results_tracker, {
            'system': 'linux',
            'test': 'sunspider',
            'url': 'http://x.com',
            'build_dir': 'builddir',
            'mastername': 'my.master',
            'buildername': 'Builder',
            'buildnumber': 123,
            'revisions': {'rev': 343},
            'supplemental_columns': {}})

    # Should not call functions to generate JSON and send to JSON if Telemetry
    # did not return results.
    self.assertFalse(MakeDashboardJsonV1.called)
    self.assertFalse(SendResults.called)
    fake_results_tracker.Cleanup.assert_called_with()

  def test_GetTelemetryRevisions(self):
    options = mock.MagicMock()
    options.revision = '294850'
    options.webkit_revision = '34f9d01'
    options.build_properties = {
        'got_webrtc_revision': None,
        'got_v8_revision': 'undefined',
        'git_revision': '9a7b354',
    }
    versions = runtest._GetTelemetryRevisions(options)
    self.assertEqual(
        {'rev': '294850', 'webkit_rev': '34f9d01', 'git_revision': '9a7b354'},
        versions)

if __name__ == '__main__':
  unittest.main()
