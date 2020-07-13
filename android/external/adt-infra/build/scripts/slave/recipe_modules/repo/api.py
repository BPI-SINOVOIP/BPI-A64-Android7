# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Common steps for recipes that use repo for source control."""

import re

from recipe_engine import recipe_api

class RepoApi(recipe_api.RecipeApi):
  """Provides methods to encapsulate repo operations."""

  _REPO_LIST_RE = re.compile(r'^(.+) : (.+)$')

  # WARNING: The version of repo checked into depot_tools doesn't support
  # switching between branches correctly due to
  # https://code.google.com/p/git-repo/issues/detail?id=46

  def __init__(self, **kwargs):
    super(RepoApi, self).__init__(**kwargs)
    self._repo_path = None

  @property
  def repo_path(self):
    if not self._repo_path:
      self._repo_path = self.m.path['depot_tools'].join('repo')
    return self._repo_path

  @repo_path.setter
  def repo_path(self, path):
    self._repo_path = path

  def __call__(self, args, name=None, **kwargs):
    """Executes 'repo' with the supplied arguments.

    Args:
      args: (list): A list of arguments to supply to 'repo'.
      name (str): The name of the step. If None, generate a name from the args.
      kwargs: Keyword arguments to pass to the 'step' call.
    Returns:
      See 'step.__call__'.
    """
    name = name or ' '.join(args)
    return self.m.step(name, [self.repo_path] + args, **kwargs)

  def init(self, url, *args, **kwargs):
    """Perform a 'repo init' step with the given manifest url."""
    kwargs.setdefault('infra_step', True)
    kwargs.setdefault('name', 'repo init')
    return self(['init', '-u', url] + list(args), **kwargs)

  def sync(self, *args, **kwargs):
    """Sync an already-init'd repo."""
    # NOTE: This does not set self.m.path['checkout']
    kwargs.setdefault('infra_step', True)
    suffix = kwargs.pop('suffix', None)
    if not 'name' in kwargs:
      name = 'repo sync'
      if suffix:
        name += ' - ' + suffix
      kwargs['name'] = name
    return self(['sync'] + list(args), **kwargs)

  def clean(self, *args, **kwargs):
    """Clean an already-init'd repo."""
    kwargs.setdefault('infra_step', True)
    kwargs.setdefault('name', 'repo forall git clean')
    return self(['forall', '-c', 'git', 'clean', '-f', '-d'] + list(args),
                **kwargs)

  def reset(self, **kwargs):
    """Reset to HEAD an already-init'd repo."""
    kwargs.setdefault('name', 'repo forall git reset')
    return self(['forall', '-c', 'git', 'reset', '--hard', 'HEAD'], **kwargs)

  def list(self, **kwargs):
    """Return (list): A list of (path, name) project tuples.
        path (str): The relative path to the project's checkout.
        name (str): The name of the project in the manifst.
    """
    kwargs['stdout'] = self.m.raw_io.output()
    kwargs.setdefault('name', 'repo list')
    step_result = self(['list'], **kwargs)

    result = []
    for line in step_result.stdout.splitlines():
      match = self._REPO_LIST_RE.match(line)
      if match:
        result.append(match.groups())

    # Display the result in the step text.
    if result:
      step_result.presentation.step_text = '</br></br>'
      step_result.presentation.step_text += '</br>'.join(
          '%s : %s' % (path, name) for path, name in result)
    return result
