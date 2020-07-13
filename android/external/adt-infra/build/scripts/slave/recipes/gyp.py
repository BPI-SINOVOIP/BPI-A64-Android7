# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'gclient',
  'path',
  'properties',
  'python',
]


def RunSteps(api):
  api.gclient.set_config('gyp')

  api.bot_update.ensure_checkout(force=True)

  api.python('run tests',
             api.path['checkout'].join('gyptest.py'), ['-a'],
             cwd=api.path['checkout'])


def GenTests(api):
  yield(api.test('fake_test') +
        api.properties.generic(mastername='fake_mastername',
                               buildername='fake_buildername'))
