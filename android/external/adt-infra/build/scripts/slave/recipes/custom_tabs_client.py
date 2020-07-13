# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'gclient',
  'properties',
]


def _CheckoutSteps(api):
  # Checkout custom-tabs-client. The config is implemented at:
  #   scripts/slave/recipe_modules/gclient/config.py
  api.gclient.set_config('custom_tabs_client')
  api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()


def RunSteps(api):
  _CheckoutSteps(api)


def GenTests(api):
  yield (
    api.test('basic') +
    api.properties(mastername='master.tryserver.client.custom_tabs_client',
                   buildername='linux',
                   slavename='linux_slave')
  )
