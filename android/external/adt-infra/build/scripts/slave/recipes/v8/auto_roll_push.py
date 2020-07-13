# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'gclient',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'v8',
]

def RunSteps(api):
  api.chromium.cleanup_temp()
  api.gclient.set_config('v8')
  api.bot_update.ensure_checkout(force=True, no_shallow=True)

  step_result = api.python(
      'check roll status',
      api.path['build'].join('scripts', 'tools', 'runit.py'),
      [api.path['build'].join('scripts', 'tools', 'pycurl.py'),
       'https://v8-roll.appspot.com/status'],
      stdout=api.raw_io.output(),
      step_test_data=lambda: api.raw_io.test_api.stream_output(
          '1', stream='stdout')
    )
  step_result.presentation.logs['stdout'] = step_result.stdout.splitlines()
  if step_result.stdout.strip() != '1':
    step_result.presentation.step_text = "Pushing deactivated"
    return
  else:
    step_result.presentation.step_text = "Pushing activated"

  api.python(
      'push candidate',
      api.path['checkout'].join(
          'tools', 'release', 'auto_push.py'),
      ['--author', 'v8-autoroll@chromium.org',
       '--reviewer', 'v8-autoroll@chromium.org',
       '--push',
       '--work-dir', api.path['slave_build'].join('workdir')],
      cwd=api.path['checkout'],
    )


def GenTests(api):
  yield api.test('standard') + api.properties.generic(
      mastername='client.v8.fyi')
  yield (api.test('rolling_deactivated') +
      api.properties.generic(mastername='client.v8.fyi') +
      api.override_step_data(
          'check roll status', api.raw_io.stream_output('0', stream='stdout'))
    )

