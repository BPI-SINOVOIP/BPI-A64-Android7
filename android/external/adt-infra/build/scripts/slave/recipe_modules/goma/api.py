# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api

class GomaApi(recipe_api.RecipeApi):
  """GomaApi contains helper functions for using goma."""

  def update_goma_canary(self, buildername):
    """Returns a step for updating goma canary."""
    # for git checkout, should use @refs/heads/master to use head.
    head = 'refs/heads/master'
    # svn checkout works with @HEAD.
    # As of Sep 29, Linux goma canaries are svn checkout, others are not.
    if 'Linux' in buildername:
      head = 'HEAD'
    self.m.gclient('update goma canary',
                   ['sync', '--verbose', '--force',
                    '--revision', 'build/goma@%s' % head],
                   cwd=self.m.path['build'])
