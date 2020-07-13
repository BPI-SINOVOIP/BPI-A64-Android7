# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'chromium_tests',
  'filter',
  'gclient',
  'json',
  'properties',
  'tryserver',
]


def RunSteps(api):
  api.chromium.set_config('chromium')
  api.gclient.set_config('chromium')
  api.bot_update.ensure_checkout(force=True)

def GenTests(api):
  yield (
    api.test('basic') +
    api.properties.tryserver()
  )
