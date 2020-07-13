# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'chromite',
  'gitiles',
]


_TEST_CONFIG = {
  '_default': {
    'foo': 'bar',
    'baz': 'qux',
  },
  '_templates': {
    'woot': {
      'baz': 'templated',
    }
  },
  'myconfig': {
    '_template': 'woot',
    'local': 'variable',
  },
}

def RunSteps(api):
  api.chromite.set_config('base')

  # Basic checkout exercise.
  api.chromite.checkout()
  api.chromite.setup_board('amd64-generic', args=['--cache-dir', '.cache'])
  api.chromite.build_packages('amd64-generic')
  api.chromite.cros_sdk('cros_sdk', ['echo', 'hello'],
                        environ={ 'var1': 'value' })
  api.chromite.cbuildbot('cbuildbot', 'amd64-generic-full',
                         args=['--clobber', '--build-dir', '/here/there'])

  # Exercise valid configuraiton.
  config = api.chromite.load_config('myconfig')
  assert config is not None
  assert config['foo'] == 'bar'
  assert config['baz'] == 'templated'
  assert config['local'] == 'variable'
  assert config.get('invalid') is None

  raised = False
  try:
    config['invalid']
  except KeyError:
    raised = True
  assert raised, 'Did not raise KeyError!'

  # Exercise invalid configuration.
  assert api.chromite.load_config('nonexistent') is None


def GenTests(api):
  yield (api.test('basic') +
      api.chromite.seed_chromite_config(_TEST_CONFIG)
  )

