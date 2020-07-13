# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


DEPS = [
  'step',
]


def RunSteps(api):
  with api.step.nest('dinosaur'):
    with api.step.nest('bird'):
      api.step('sparrow', ['sleep', '10'])
    with api.step.nest('song'):
      api.step('blackbird', ['echo', 'deadofnight'])
    api.step('roar', ['sleep', '10'])
  api.step('not_a_dinosaur', ['sleep', '10'])


def GenTests(api):
  yield api.test('basic')
