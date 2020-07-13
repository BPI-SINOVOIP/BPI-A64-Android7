# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""An interface for holding state and result of revisions in a bisect job.

When implementing support for tests other than perf, one should extend this
class so that the bisect module and recipe can use it.

See perf_revision_state for an example.
"""

import hashlib
import os
import re

from . import depot_config


class RevisionState(object):
  """Abstracts the state of a single revision on a bisect job."""

  # Possible values for the status attribute of RevisionState:
  (
      NEW,  # A revision_state object that has just been initialized.
      BUILDING,  # Requested a build for this revision, waiting for it.
      TESTING,  # A test job for this revision was triggered, waiting for it.
      TESTED,  # The test job completed with non-failing results.
      FAILED,  # Either the build or the test jobs failed or timed out.
      ABORTED,  # The build or test job was aborted. (For use in multi-secting).
      SKIPPED,  # A revision that was not built or tested for a special reason,
                # such as those ranges that we know are broken, or when nudging
                # revisions.
  ) = xrange(7)

  def __init__(self, revision_string, bisector,
               dependency_depot_name=None, base_revision=None,
               deps_revision=None, cp=None):
    """Creates a new instance to track the state of a revision.

    There are two use cases for this constructor:
      - Creating a revision state for a chromium revision, OR
      - Creating a revision state for a chromium revision plus an explicitly
        specified revision of a dependency repository (a DEPS change).
    In the first case a revision_string and a bisector are needed.
    In the second case, revision_string must be None, and all of depot,
    base_revision and deps_revision must be provided.

    Args:
      revision_string (str): A git hash or a commit position in the chromium
        repository. If None, all kwargs must be given.
      bisector (Bisector): The object performing the bisection.
      depot_name (str): The name of the depot as specified in DEPS. Must be a
        key in depot_config.DEPOT_DEPS_NAME .
      base_revision (RevisionState): The revision state to patch with the deps
        change.
      depot_revision: The commit hash of the dependency repo to put in place of
        the one set for the base_revision.
      cp: A pre-resolved commit position.
    """
    #  TODO(robertocn): Evaluate if the logic of this constructor should be
    #    split into separate methods.
    super(RevisionState, self).__init__()
    self.bisector = bisector
    self._good = None
    self.deps = None
    self.build_status_url = None
    self.test_results_url = None
    self.build_archived = False
    self.status = RevisionState.NEW
    self.next_revision = None
    self.previous_revision = None
    self.revision_string = revision_string
    self.job_name = None
    self.patch_file = None
    self.deps_revision = None
    self.depot_name = dependency_depot_name or 'chromium'
    self.depot = depot_config.DEPOT_DEPS_NAME[self.depot_name]
    if not self.revision_string:
      assert base_revision
      assert base_revision.deps_file_contents
      assert self.depot
      assert self.depot_name != 'chromium'
      self.needs_patch = True
      self.revision_string = (base_revision.revision_string + ',' +
                              self.depot_name)
      self.revision_string += '@' + deps_revision
      self.deps_patch, self.deps_file_contents = self.bisector.make_deps_patch(
          base_revision, base_revision.deps_file_contents,
          self.depot, deps_revision)
      self.commit_hash = base_revision.commit_hash
      self.commit_pos = base_revision.commit_pos
      self.deps_sha = hashlib.sha1(self.deps_patch).hexdigest()
      self.deps_sha_patch = self.bisector.make_deps_sha_file(self.deps_sha)
      self.deps = dict(base_revision.deps)
      self.deps[dependency_depot_name] = deps_revision
      self.deps_revision = deps_revision
    else:
      self.needs_patch = False
      if cp is not None:
        # If cp is given, assume revision_string IS the commit_hash.
        self.commit_hash = revision_string
        if cp:
          self.commit_pos = cp
        else:  # pragma: no cover
          # If there is no commit position, use an abbreviated hash instead.
          self.commit_pos = self.commit_hash[:8]
      else:
        self.commit_hash, self.commit_pos = self._commit_from_rev_string()
    self.build_url = self.bisector.get_platform_gs_prefix() + self._gs_suffix()

  @property
  def tested(self):
    return self.status in [RevisionState.TESTED]

  @property
  def in_progress(self):
    return self.status in (RevisionState.BUILDING, RevisionState.TESTING)

  @property
  def failed(self):
    return self.status == RevisionState.FAILED

  @property
  def aborted(self):
    return self.status == RevisionState.ABORTED

  @property
  def good(self):
    return self._good == True

  @property
  def bad(self):
    return self._good == False

  @good.setter
  def good(self, value):
    self._good = value

  @bad.setter
  def bad(self, value):
    self._good = not value

  def start_job(self):
    """Starts a build, or a test job if the build is available."""
    if self.status == RevisionState.NEW and not self._is_build_archived():
      self._request_build()
      self.status = RevisionState.BUILDING
      return

    if self._is_build_archived() and self.status in [RevisionState.NEW,
                                                     RevisionState.BUILDING]:
      self._do_test()
      self.status = RevisionState.TESTING

  def abort(self):  # pragma: no cover
    """Aborts the job.

    This method is typically called when the bisect no longer requires it. Such
    as when a later good revision or an earlier bad revision have been found in
    parallel.
    """
    assert self.in_progress
    # TODO: actually kill buildbot job if it's the test step.
    self.status = RevisionState.ABORTED

  def deps_change(self):
    """Uses `git show` to see if a given commit contains a DEPS change."""
    api = self.bisector.api
    name = 'Checking DEPS for ' + self.commit_hash
    step_result = api.m.git(
        'show', '--name-only', '--pretty=format:',
        self.commit_hash, stdout=api.m.raw_io.output(), name=name)
    if self.bisector.dummy_builds and not self.commit_hash.startswith('dcdc'):
      return False
    if 'DEPS' in step_result.stdout.splitlines():  # pragma: no cover
      return True
    return False  # pragma: no cover

  def _gen_deps_local_scope(self):
    """Defines the Var and From functions in a dict for calling exec.

    This is needed for executing the DEPS file.
    """
    deps_data = {
        'Var': lambda _: deps_data['vars'][_],
        'From': lambda *args: None,
    }
    return deps_data

  def read_deps(self):
    """Sets the dependencies for this revision from the contents of DEPS."""
    api = self.bisector.api
    if self.deps:
      return
    step_result = api.m.git.cat_file_at_commit(depot_config.DEPS_FILENAME,
                                               self.commit_hash,
                                               stdout=api.m.raw_io.output())
    self.deps_file_contents = step_result.stdout
    try:
      deps_data = self._gen_deps_local_scope()
      exec(self.deps_file_contents or 'deps = {}', {}, deps_data)
      deps_data = deps_data['deps']
    except ImportError:  # pragma: no cover
      # TODO(robertocn): Implement manual parsing of DEPS when exec fails.
      raise NotImplementedError('Path not implemented to manually parse DEPS')

    revision_regex = re.compile('.git@(?P<revision>[a-fA-F0-9]+)')
    results = {}
    for depot_name, depot_data in depot_config.DEPOT_DEPS_NAME.iteritems():
      if (depot_data.get('platform') and
          depot_data.get('platform') != os.name):
        # TODO(robertocn) we shouldn't be checking the os of the bot running the
        # bisector, but the os the tester would be running on.
        continue

      if (depot_data.get('recurse') and
          self.depot_name in depot_data.get('from')):
        depot_data_src = depot_data.get('src') or depot_data.get('src_old')
        src_dir = deps_data.get(depot_data_src)
        if src_dir:
          re_results = revision_regex.search(src_dir)
          if re_results:
            results[depot_name] = re_results.group('revision')
          else:
            warning_text = ('Could not parse revision for %s while bisecting '
                            '%s' % (depot_name, self.depot))
            if warning_text not in self.bisector.warnings:
              self.bisector.warnings.append(warning_text)
        else:
          results[depot_name] = None
    self.deps = results
    return

  def update_status(self):
    """Checks on the pending jobs and updates status accordingly.

    This method will check for the build to complete and then trigger the test,
    or will wait for the test as appropriate.

    To wait for the test we try to get the buildbot job url from GS, and if
    available, we query the status of such job.
    """
    if self.status == RevisionState.BUILDING and self._is_build_archived():
      self.start_job()
    elif self.status == RevisionState.TESTING and self._results_available():
      self._read_test_results()
      # We assume _read_test_results may have changed the status to a broken
      # state such as FAILED or ABORTED.
      if self.status == RevisionState.TESTING:
        self.status = RevisionState.TESTED

  def _is_build_archived(self):
    """Checks if the revision is already built and archived."""
    if not self.build_archived:
      api = self.bisector.api
      self.build_archived = api.gsutil_file_exists(self.build_url)

    if self.bisector.dummy_builds:
      self.build_archived = self.in_progress

    return self.build_archived

  def _results_available(self):
    """Checks if the results for the test job have been uploaded."""
    api = self.bisector.api
    result = api.gsutil_file_exists(self.test_results_url)
    if self.bisector.dummy_builds:
      return self.in_progress
    return result  # pragma: no cover

  def _gs_suffix(self):
    """Provides the expected right half of the build filename.

    This takes into account whether the build has a deps patch.
    """
    name_parts = [self.commit_hash]
    if self.needs_patch:
      name_parts.append(self.deps_sha)
    return '%s.zip' % '_'.join(name_parts)

  def _commit_from_rev_string(self):
    """Gets the chromium repo commit hash and position for this revision."""
    pieces = self.revision_string.split(',')
    if (pieces[0].startswith('chromium@') or
        pieces[0].startswith('src@') or
        '@' not in pieces[0]):
      hash_or_pos = pieces[0].split('@')[-1]
      if self._check_if_hash(hash_or_pos):
        commit_pos = self._get_pos_from_hash(hash_or_pos)
        commit_hash = self._get_hash_from_pos(commit_pos)
      else:
        commit_hash = self._get_hash_from_pos(hash_or_pos)
        commit_pos = self._get_pos_from_hash(commit_hash)
      return commit_hash, commit_pos

  def _check_if_hash(self, s):
    if len(s) <= 8:
      # We expect commit positions to be 8 digits or less
      try:
        int(s)
        # If the cast did not raise an error, assume s is a commit position.
        return False
      except ValueError:  # pragma: no cover
        pass
    # If the following cast raises an error then s does not represent a valid
    # SHA hash, therefore, we let the error bubble up.
    int(s, 16)
    return True

  def _get_pos_from_hash(self, sha):
    api = self.bisector.api
    try:
      result = api.m.commit_position.chromium_commit_position_from_hash(sha)
    except api.m.step.StepFailure as sf:
      api.m.halt('Failed to resolve commit position - ' + sf.reason)
      raise
    return result

  def _get_hash_from_pos(self, pos):
    api = self.bisector.api
    try:
      result = api.m.commit_position.chromium_hash_from_commit_position(pos)
    except api.m.step.StepFailure as sf:
      api.m.halt('Failed to resolve commit position - ' + sf.reason)
      raise
    return result
