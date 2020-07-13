# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


DEPS = [
  'bot_update',
  'gclient',
  'git',
  'path',
  'properties',
  'python',
  'webrtc',
]


def RunSteps(api):
  api.gclient.set_config('webrtc')
  api.gclient.runhooks()
  api.bot_update.ensure_checkout(force=True)

  # Enforce a clean state.
  api.git(
      'checkout', '-f', 'master',
      cwd=api.path['checkout'],
  )
  api.git(
      'clean', '-ffd',
      cwd=api.path['checkout'],
  )

  # Run the roll script. It will take care of branch creation, modifying DEPS,
  # uploading etc. It will also delete any previous roll branch.
  api.python(
      'autoroll chromium_revision',
      api.path['checkout'].join('tools', 'autoroller',
                                'roll_chromium_revision.py'),
      ['--clean', '--verbose'],
      cwd=api.path['checkout'],
  )


def GenTests(api):
  yield (
      api.test('roll') +
      api.properties.generic(mastername='client.webrtc.fyi',
                             buildername='Auto-roll - WebRTC DEPS')
  )
