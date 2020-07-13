# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64

from recipe_engine import recipe_api


class Gitiles(recipe_api.RecipeApi):
  """Module for polling a git repository using the Gitiles web interface."""

  def _fetch(self, url, step_name, attempts=None, add_json_log=True):
    args = [
      '--json-file', self.m.json.output(add_json_log=add_json_log),
      '--url', url,
    ]
    if attempts:
      args.extend(['--attempts', attempts])
    a = self.m.python(step_name,
      self.resource('gerrit_client.py'), args)
    return a

  def refs(self, url, step_name='refs', attempts=None):
    """Returns a list of refs in the remote repository."""
    step_result = self._fetch(
      self.m.url.join(url, '+refs?format=json'),
      step_name, attempts=attempts,
    )

    refs = sorted(str(ref) for ref in step_result.json.output)
    step_result.presentation.logs['refs'] = refs
    return refs

  def log(self, url, ref, num='all', step_name=None, attempts=None):
    """Returns the most recent commits under the given ref.

    Args:
      url: URL of the remote repository.
      ref: Name of the desired ref (see Gitiles.refs).
      num: Number of commits to limit the results to. Defaults to all.
      step_name: Custom name for this step. Will use the default if unspecified.

    Returns:
      A list of (commit hash, author) in reverse chronological order.
    """
    step_name = step_name or 'log: %s' % ref

    step_result = self._fetch(
      self.m.url.join(url, '+log/%s?format=json&n=%s' % (ref, num)),
      step_name, attempts=attempts,
    )

    # The output is formatted as a JSON dict with a "log" key. The "log" key
    # is a list of commit dicts, which contain information about the commit.
    commits = [
      (str(commit['commit']), str(commit['author']['email']))
      for commit in step_result.json.output['log']
    ]

    step_result.presentation.logs['log'] = [commit[0] for commit in commits]
    step_result.presentation.step_text = '<br />%d new commits' % len(commits)
    return commits

  def commit_log(self, url, commit, step_name=None, attempts=None):
    """Returns: (dict) the Gitiles commit log structure for a given commit.

    Args:
      url (str): The base repository URL.
      commit (str): The commit hash.
      step_name (str): If not None, override the step name.
    """
    step_name = step_name or 'commit log: %s' % commit

    commit_url = '%s/+/%s?format=json' % (url, commit)
    step_result = self._fetch(commit_url, step_name, attempts=attempts)
    return step_result.json.output

  def download_file(self, repository_url, file_path, branch='master',
                    step_name=None, attempts=None):
    """Downloads raw file content from a Gitiles repository.

    Args:
      repository_url: Full URL to the repository.
      file_path: Relative path to the file from the repository root.
      step_name: (str) If not None, override the step name.

    Returns:
      Raw file content.
    """
    fetch_url = self.m.url.join(repository_url, '+/%s/%s?format=text' % (
        branch, file_path,))
    step_result = self._fetch(
        fetch_url,
        step_name or 'fetch %s:%s' % (branch, file_path,),
        attempts=attempts,
        add_json_log=False,
        )
    return base64.b64decode(step_result.json.output['value'])
