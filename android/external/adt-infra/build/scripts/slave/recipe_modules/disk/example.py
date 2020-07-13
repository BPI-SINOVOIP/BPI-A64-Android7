# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'disk',
  'json',
  'platform',
  'properties',
]

GIB = 1 << 30


def RunSteps(api):
  default_usage1 = {
    'capacity': 100 * GIB,
    'used': 50 * GIB,
  }
  default_usage2 = {
    'capacity': 100 * GIB,
    'used': 51 * GIB,
  }

  usage1_kwargs = {}
  if not api.properties.get('no_usage1_data'):
    usage1_data = api.properties.get('usage1_data') or default_usage1
    usage1_data = dict(usage1_data)
    usage1_kwargs.update({
      'step_test_data': lambda: api.json.test_api.output_stream(usage1_data),
    })
  usage1 = api.disk.space_usage(
      name='usage1',
      can_fail_build=api.properties.get('usage1_fails_build'),
      **usage1_kwargs)

  api.disk.space_usage(
      name='usage2',
      step_test_data=lambda: api.json.test_api.output_stream(default_usage2),
      previous_result=usage1)

def GenTests(api):
  yield api.test('basic')
  yield (
      api.test('high_usage') +
      api.properties(usage1_data={
          'capacity': 100 * GIB,
          'used': 90 * GIB,
      }))
  yield api.test('windows') + api.platform.name('win')
  yield api.test('no_test_data') + api.properties(no_usage1_data=True)

  yield (
      api.test('space_usage_doesnt_fail_build') +
      api.properties(usage1_data={'x': 1}))

  yield (
      api.test('space_usage_fails_build') +
      api.properties(usage1_data={'x': 1}, usage1_fails_build=True))