# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'gclient',
  'json',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'url',
]


def ContainsChromiumRoll(changes):
  for change in changes:
    if change['subject'].startswith('Update V8 to'):
      return True
  return False


def RunSteps(api):
  api.chromium.cleanup_temp()
  api.gclient.set_config('chromium')
  api.gclient.apply_config('v8_bleeding_edge_git')

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
    step_result.presentation.step_text = 'Rolling deactivated'
    return
  else:
    step_result.presentation.step_text = 'Rolling activated'

  params = {
    'closed': 3,
    'owner': 'v8-autoroll@chromium.org',
    'limit': 30,
    'format': 'json',
  }

  params = api.url.urlencode(params)
  search_url = 'https://codereview.chromium.org/search?' + params

  result = api.url.fetch(
      search_url,
      'check active roll',
      step_test_data=lambda: api.raw_io.test_api.output('{"results": []}')
  )
  if ContainsChromiumRoll(api.json.loads(result)['results']):
    api.step.active_result.presentation.step_text = 'Active rolls found.'
    return

  # Prevent race with gnumbd by waiting.
  api.python.inline(
      'wait for gnumbd',
      'import time; time.sleep(20)',
  )

  api.bot_update.ensure_checkout(force=True, no_shallow=True)

  api.python(
      'roll deps',
      api.path['checkout'].join(
          'v8', 'tools', 'release', 'auto_roll.py'),
      ['--chromium', api.path['checkout'],
       '--author', 'v8-autoroll@chromium.org',
       '--reviewer',
       'hablich@chromium.org,machenbach@chromium.org,'
       'yangguo@chromium.org,vogelheim@chromium.org',
       '--roll',
       '--work-dir', api.path['slave_build'].join('workdir')],
      cwd=api.path['checkout'].join('v8'),
    )


def GenTests(api):
  yield api.test('standard') + api.properties.generic(
      mastername='client.v8.fyi')
  yield (api.test('rolling_deactivated') +
      api.properties.generic(mastername='client.v8') +
      api.override_step_data(
          'check roll status', api.raw_io.stream_output('0', stream='stdout'))
    )
  yield (api.test('active_roll') +
      api.properties.generic(mastername='client.v8') +
      api.override_step_data(
          'check active roll', api.raw_io.output(
              '{"results": [{"subject": "Update V8 to foo"}]}'))
    )

