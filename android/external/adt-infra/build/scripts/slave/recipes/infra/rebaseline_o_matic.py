# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Generates new baselines for Blink layout tests that need rebaselining.

Intended to be called periodically. Syncs to the Blink repo and runs
'webkit-patch auto-rebaseline', which processes entries in
LayoutTests/TestExpectations that are marked with 'NeedsRebaseline'.

Slaves running this recipe will require SVN access credentials for submitting
patches with the new baselines.
"""

DEPS = [
  'bot_update',
  'gclient',
  'git',
  'path',
  'properties',
  'python',
]


def RunSteps(api):
  api.gclient.set_config('blink')
  api.bot_update.ensure_checkout(force=True)

  cwd = api.path['checkout'].join('third_party', 'WebKit')

  api.python('webkit-patch auto-rebaseline',
             cwd.join('Tools', 'Scripts', 'webkit-patch'),
             ['auto-rebaseline', '--verbose'],
             cwd=cwd)


def GenTests(api):
  yield (api.test('rebaseline_o_matic') +
         api.properties(mastername='chromium.infra.cron',
                        buildername='rebaseline-o-matic',
                        slavename='fake-slave'))

