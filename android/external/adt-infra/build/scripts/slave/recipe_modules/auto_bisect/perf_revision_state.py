# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import tempfile
import os
import uuid

from . import revision_state

if 'CACHE_TEST_RESULTS' in os.environ:  # pragma: no cover
  from . import test_results_cache


class PerfRevisionState(revision_state.RevisionState):

  """Contains the state and results for one revision in a perf bisect job."""

  def __init__(self, *args, **kwargs):
    super(PerfRevisionState, self).__init__(*args, **kwargs)
    self.values = []
    self.mean_value = None
    self.std_err = None
    self._test_config = None

  def _read_test_results(self):
    """Gets the test results from GS and checks if the rev is good or bad."""
    results = self._get_test_results()
    # Results will contain the keys 'results' and 'output' where output is the
    # stdout of the command, and 'results' is itself a dict with the keys:
    # 'mean', 'values', 'std_err' unless the test failed, in which case
    # 'results' will contain the 'error' key explaining the type of error.
    results = results['results']
    if results.get('error'):
      self.status = PerfRevisionState.FAILED
      return
    self.mean_value = results['mean']
    self.values = results['values']
    self.std_err = results['std_err']
    # We cannot test the goodness of the initial rev range.
    if self.bisector.good_rev != self and self.bisector.bad_rev != self:
      if self._check_revision_good():
        self.good = True
      else:
        self.bad = True

  def _write_deps_patch_file(self, build_name):
    api = self.bisector.api
    file_name = str(api.m.path['tmp_base'].join(build_name + '.diff'))
    api.m.file.write('Saving diff patch for ' + str(self.revision_string),
                     file_name, self.deps_patch + self.deps_sha_patch)
    return file_name

  def _request_build(self):
    """Posts a request to buildbot to build this revision and archive it."""
    # TODO: Rewrite using the trigger module.
    api = self.bisector.api
    bot_name = self.bisector.get_builder_bot_for_this_platform()
    if self.bisector.dummy_builds:
      self.job_name = self.commit_hash + '-build'
    else:  # pragma: no cover
      self.job_name = uuid.uuid4().hex
    if self.needs_patch:
      self.patch_file = self._write_deps_patch_file(
          self.job_name)
    else:
      self.patch_file = '/dev/null'
    try_cmd = [
        'try',
        '--bot', bot_name,
        '--revision', self.commit_hash,
        '--name', self.job_name,
        '--svn_repo', api.SVN_REPO_URL,
        '--diff', self.patch_file,
    ]
    try:
      if not self.bisector.bisect_config.get('skip_gclient_ops'):
        self.bisector.ensure_sync_master_branch()
      api.m.git(
          *try_cmd, name='Requesting build for %s via git try.'
          % str(self.commit_hash), git_config_options={
              'user.email': 'FAKE_PERF_PUMPKIN@chromium.org',
          })
    finally:
      if (self.patch_file != '/dev/null' and
          'TESTING_SLAVENAME' not in os.environ):
        try:
          api.m.step('cleaning up patch', ['rm', self.patch_file])
        except api.m.step.StepFailure:  # pragma: no cover
          print 'Could not clean up ' + self.patch_file

  def _get_bisect_config_for_tester(self):
    """Copies the key-value pairs required by a tester bot to a new dict."""
    result = {}
    required_test_properties = {
        'truncate_percent',
        'metric',
        'max_time_minutes',
        'command',
        'repeat_count',
        'test_type'
    }
    for k, v in self.bisector.bisect_config.iteritems():
      if k in required_test_properties:
        result[k] = v
    self._test_config = result
    return result

  def _do_test(self):
    """Posts a request to buildbot to download and perf-test this build."""
    if self.bisector.dummy_builds:
      self.job_name = self.commit_hash + '-test'
    elif 'CACHE_TEST_RESULTS' in os.environ:  # pragma: no cover
      self.job_name = test_results_cache.make_id(
          self.revision_string, self._get_bisect_config_for_tester())
    else:  # pragma: no cover
      self.job_name = uuid.uuid4().hex
    api = self.bisector.api
    perf_test_properties = {
        'builder_name': self.bisector.get_perf_tester_name(),
        'properties': {
            'revision': self.commit_hash,
            'parent_got_revision': self.commit_hash,
            'parent_build_archive_url': self.build_url,
            'bisect_config': self._get_bisect_config_for_tester(),
            'job_name': self.job_name,
        },
    }
    if 'CACHE_TEST_RESULTS' in os.environ and test_results_cache.has_results(
        self.job_name):  # pragma: no cover
      return
    step_name = 'Triggering test job for ' + str(self.revision_string)
    self.test_results_url = (self.bisector.api.GS_RESULTS_URL +
                             self.job_name + '.results')
    api.m.trigger(perf_test_properties, name=step_name)

  def get_next_url(self):
    if self.status == PerfRevisionState.BUILDING:
      return self.build_url
    if self.status == PerfRevisionState.TESTING:
      return self.test_results_url

  def get_buildbot_locator(self):
    if self.status not in (PerfRevisionState.BUILDING,
                           PerfRevisionState.TESTING):  # pragma: no cover
      return None
    # TODO(robertocn): Remove hardcoded master.
    master = 'tryserver.chromium.perf'
    if self.status == PerfRevisionState.BUILDING:
      builder = self.bisector.get_builder_bot_for_this_platform()
    if self.status == PerfRevisionState.TESTING:
      builder = self.bisector.get_perf_tester_name()
    return {
        'type': 'buildbot',
        'master': master,
        'builder': builder,
        'job_name': self.job_name,
    }

  def _get_test_results(self):
    """Tries to get the results of a test job from cloud storage."""
    api = self.bisector.api
    try:
      stdout = api.m.raw_io.output()
      name = 'Get test results for build ' + self.commit_hash
      step_result = api.m.gsutil.cat(self.test_results_url, stdout=stdout,
                                     name=name)
    except api.m.step.StepFailure:  # pragma: no cover
      return None
    else:
      return json.loads(step_result.stdout)

  def _check_revision_good(self):
    """Determines if a revision is good or bad.

    Note that our current approach is to determine whether it is closer to
    either the 'good' and 'bad' revisions given for the bisect job.

    Returns:
      True if this revision is closer to the initial good revision's value than
      to the initial bad revision's value. False otherwise.
    """
    # TODO: Reevaluate this approach
    bisector = self.bisector
    distance_to_good = abs(self.mean_value - bisector.good_rev.mean_value)
    distance_to_bad = abs(self.mean_value - bisector.bad_rev.mean_value)
    if distance_to_good < distance_to_bad:
      return True
    return False

  def __repr__(self):
    return ('PerfRevisionState(values=%r, mean_value=%r, std_err=%r)' %
            (self.values, self.mean_value, self.std_err))
