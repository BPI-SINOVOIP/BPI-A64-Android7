# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'gclient',
  'git',
  'gsutil',
  'json',
  'path',
  'properties',
  'step',
  'v8',
]

def RunSteps(api):
  api.chromium.cleanup_temp()
  api.gclient.set_config('chromium')
  api.gclient.apply_config('v8')
  api.bot_update.ensure_checkout(
      force=True, no_shallow=True, with_branch_heads=True)
  api.step(
      'V8Releases',
      [api.path['slave_build'].join(
           'v8', 'tools', 'release', 'releases.py'),
       '-c', api.path['checkout'],
       '--json', api.path['slave_build'].join('v8-releases-update.json'),
       '--branch', 'recent',
       '--work-dir', api.path['slave_build'].join('workdir')],
      cwd=api.path['slave_build'].join('v8'),
    )
  api.gsutil.upload(api.path['slave_build'].join('v8-releases-update.json'),
                    'chromium-v8-auto-roll',
                    api.path.join('v8rel', 'v8-releases-update.json'))


def GenTests(api):
  yield api.test('standard') + api.properties.generic(
      mastername='client.v8.fyi')

