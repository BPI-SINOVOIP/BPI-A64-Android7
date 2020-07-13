# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
    'properties',
    'trigger'
]


def RunSteps(api):

  def normalize_specs(specs):
    specs = dict(specs)
    if 'buildbot_changes' in specs:
      specs['buildbot_changes'] = map(dict, specs['buildbot_changes'])
    return specs

  specs = map(normalize_specs, api.properties['trigger_specs'])
  api.trigger(*specs)


def GenTests(api):
  yield (
      api.test('trigger_one_build') +
      api.properties(trigger_specs=[{
          'builder_name': 'cross-compiler',
          'properties': {'a': 1},
        }])
      )

  yield (
      api.test('trigger_two_builds') +
      api.properties(trigger_specs=[{
          'builder_name': 'cross-compiler',
          'properties': {'a': 1},
        }, {
          'builder_name': 'cross-compiler',
          'properties': {'a': 2},
        }])
      )

  yield (
      api.test('buildbot_changes') +
      api.properties(trigger_specs=[{
          'builder_name': 'cross-compiler',
          'buildbot_changes': [{
              'author': 'someone@chromium.org',
              'revision': 'deadbeef',
              'comments': 'hello world!',
          }],
        }])
      )

  yield (
      api.test('backward_compatibility') +
      api.properties(trigger_specs=[{
          'buildername': 'cross-compiler',
          'a': 1,
        }])
      )
