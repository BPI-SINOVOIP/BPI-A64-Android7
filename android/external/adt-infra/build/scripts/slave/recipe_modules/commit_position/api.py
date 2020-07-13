# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import re

from recipe_engine import recipe_api


class CommitPositionApi(recipe_api.RecipeApi):
  """Recipe module providing commit position parsing and manipulation."""
  RE_COMMIT_POSITION = re.compile('(?P<branch>.+)@{#(?P<revision>\d+)}')
  COMMIT_POS_STR = '^Cr-Commit-Position: refs/heads/master@{#%d}'

  @classmethod
  def parse(cls, value):
    match = cls.RE_COMMIT_POSITION.match(value)
    if not match:
      raise ValueError("Invalid commit position (%s)" % (value,))
    return match.group('branch'), int(match.group('revision'))

  @classmethod
  def parse_branch(cls, value):
    branch, _ = cls.parse(value)
    return branch

  @classmethod
  def parse_revision(cls, value):
    _, revision = cls.parse(value)
    return revision

  @classmethod
  def construct(cls, branch, value):
    value = int(value)
    return '%(branch)s@{#%(value)d}' % {
        'branch': branch,
        'value': value,
    }

  def chromium_hash_from_commit_position(self, commit_pos):
    """Resolve a commit position in the chromium repo to its commit hash."""
    try:
      int_pos = int(commit_pos)
    except ValueError:
      raise self.m.step.StepFailure('Invalid commit position (%s).'
                                    % (commit_pos,))
    step_result = self.m.git('log', '--format=hash:%H', '--grep',
                             self.COMMIT_POS_STR % int_pos, '-1',
                             'origin/master',
                             stdout=self.m.raw_io.output(),
                             name='resolving commit_pos ' + str(commit_pos))
    try:
      result_line = [line for line in step_result.stdout.splitlines()
                     if line.startswith('hash:')][0]
      result = result_line.split(':')[1]
      int(result, 16)
      return result
    except (IndexError, ValueError):
      raise self.m.step.StepFailure(
          'Could not parse commit hash from git log output' + step_result.stdout)

  def chromium_commit_position_from_hash(self, sha):
    """Resolve a chromium commit hash to its commit position."""
    try:
      assert int(sha, 16)
      sha = str(sha)  # Unicode would break the step when passed in the name
    except (AssertionError, ValueError):
      raise self.m.step.StepFailure('Invalid commit hash: ' + sha)
    step_result = self.m.git('footers', '--position', sha,
                             stdout=self.m.raw_io.output(),
                             name='resolving hash ' + sha)
    try:
      result = int(self.parse_revision(str(step_result.stdout)))
    except ValueError:
      raise self.m.step.StepFailure(
          'Could not parse commit position from git output: ' +
          (step_result.stdout or ''))
    return result

