# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api

class SwarmingHeartbeatApi(recipe_api.RecipeApi):
  """Defines support to run automated heartbeat."""

  def run(self):
    """Runs job_runs_fine.py to run an heartbeat check."""
    args = []
    if self.m.properties.get('target_environment') == 'staging':
      args = ['--staging']
    self.m.python(
        'job_runs_fine.py', script=self.resource('job_runs_fine.py'), args=args)
