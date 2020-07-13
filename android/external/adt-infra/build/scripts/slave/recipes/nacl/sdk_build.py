# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'gclient',
  'path',
  'platform',
  'properties',
  'python',
  'tryserver',
]


def RunSteps(api):
  api.chromium.set_config('chromium')
  api.gclient.set_config('chromium')
  api.bot_update.ensure_checkout(force=True)
  api.tryserver.maybe_apply_issue()
  api.chromium.runhooks()
  api.chromium.cleanup_temp()

  api.python(
      'annotated_steps',
      api.path['checkout'].join(
          'native_client_sdk', 'src', 'build_tools', 'buildbot_run.py'),
      allow_subannotations=True)


def GenTests(api):
  yield (
    api.test('basic') +
    api.properties.generic() +
    api.platform('linux', 64)
  )
