# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


DEPS = [
  'step',
]


def RunSteps(api):
  with api.step.context({
      'env': {'mood': 'excellent', 'climate': 'sunny'},
      'name': 'grandparent'}):
    with api.step.context({'env': {'climate': 'rainy'}, 'name': 'mom'}):
      api.step("child", ["echo", "billy"])
    with api.step.context({'env': {'climate': 'cloudy'}, 'name': 'dad'}):
      api.step("child", ["echo", "sam"])
    api.step("aunt", ["echo", "testb"])


def GenTests(api):
  yield api.test('basic')
