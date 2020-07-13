# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Generates BoringSSL documentation and uploads it to Cloud Storage."""


DEPS = [
  'bot_update',
  'gclient',
  'gsutil',
  'path',
  'properties',
  'python',
]


def RunSteps(api):
  # Sync and pull in everything.
  api.gclient.set_config('boringssl')
  api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()

  # Set up paths.
  util = api.path['checkout'].join('util')
  go_env = util.join('bot', 'go', 'env.py')
  output = api.path.mkdtemp('boringssl-docs')

  # Generate and upload documentation.
  api.python('generate', go_env, ['go', 'run', 'doc.go', '-out', output],
             cwd=util)
  api.gsutil(['-m', 'cp', '-a', 'public-read', api.path.join(output, '**'),
              'gs://chromium-boringssl-docs/'])


def GenTests(api):
  yield api.test('boringssl-docs') + \
        api.properties.generic(mastername='client.boringssl',
                               buildername='docs',
                               slavename='slavename')
