# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import urllib

from recipe_engine import recipe_api

class PerfDashboardApi(recipe_api.RecipeApi):
  """Provides steps to take a list of perf points and post them to the
  Chromium Perf Dashboard.  Can also use the test url for testing purposes."""

  def get_skeleton_point(self, test, revision, value):
    # TODO: masterid is really mastername
    assert(test != '')
    assert(revision != '')
    assert(value != '')
    return {
      "master": self.m.properties['mastername'],
      "bot" : self.m.properties['slavename'],
      "test" : test,
      "revision" : revision,
      "value" : value,
      "masterid" : self.m.properties['mastername'],
      "buildername" : self.m.properties['buildername'],
      "buildnumber" : self.m.properties['buildnumber']
    }

  def add_dashboard_link(self, presentation, test, revision, bot=None):
    """Adds a results-dashboard link to the step presentation.

    Must be called from a follow-up function of the step, to which the link
    should be added. For a working link, the parameters test, revision and bot
    must match to the parameters used to upload points to the dashboard.

    Args:
      presentation: A step presentation object. Can be obtained by
                    step_result.presentation from a followup_fn of a step.
      test: Slash-separated test path.
      revision: The build revision, e.g. got_revision from the update step.
      bot: The slave name.
    """
    assert presentation
    assert test
    assert revision
    params = urllib.urlencode({
      'masters' : self.m.properties['mastername'],
      'bots': bot or self.m.properties['slavename'],
      'tests': test,
      'rev': revision,
    })
    presentation.links['Results Dashboard'] = ('%s/report?%s'
                                               % (self.c.url, params))

  def set_default_config(self):
    """If in golo, use real perf server, otherwise use testing perf server."""
    if self.m.properties.get('use_mirror', True):  # We're on a bot
      self.set_config('production')
    else:
      self.set_config('testing')

  def post(self, data):
    """Takes a data object which can be jsonified and posts it to url."""
    self.m.python(
      name="perf dashboard post",
      script=self.resource('post_json.py'),
      stdin=self.m.json.input({
        'url' : "%s/add_point" % self.c.url,
        'data' : data
      }))
