# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


DEPS = [
  'bot_update',
  'gclient',
  'path',
  'properties',
  'python',
  'step',
]


@recipe_api.composite_step
def cq_stats_uploader(api, project, date_range):
  api.python(
      'cq_stats_uploader (%s-%s)' % (project, date_range),
      api.path['slave_build'].join('infra', 'run.py'),
      [
        'infra.tools.cq_stats_uploader',
        '--project', project,
        '--range', date_range,
        '--logs-verbose',
      ])


def RunSteps(api):
  # Checkout infra/infra solution.
  api.gclient.set_config('infra')
  api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()

  with api.step.defer_results():
    for project in ['chromium', 'blink']:
      for date_range in ['day', 'week']:
        cq_stats_uploader(api, project, date_range)


def GenTests(api):
  server_props = (
    api.properties(mastername='chromium.infra.cron') +
    api.properties(buildername='cq_stats_uploader') +
    api.properties(slavename='slavename')
  )

  yield server_props + api.test('basic')

  yield server_props + (
    api.test('failure') +
    api.override_step_data('cq_stats_uploader (blink-day)', retcode=1)
  )
