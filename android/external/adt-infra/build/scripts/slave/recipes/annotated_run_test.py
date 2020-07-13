# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Checks that properties get to recipes from annotated_run properly"""

from recipe_engine.recipe_api import Property

DEPS = [
    'recipe_engine/step',
    'recipe_engine/properties',
]

PROPERTIES = {
  'true_prop': Property(),
  'num_prop': Property(),
  'string_prop': Property(),
  'dict_prop': Property(),
}

def RunSteps(api, true_prop, num_prop, string_prop, dict_prop):
  assert true_prop == True
  assert num_prop == 123
  assert string_prop == '321'
  assert dict_prop == {'foo': 'bar'}

def GenTests(api):
  yield api.test('basic') + api.properties(
    true_prop=True,
    num_prop=123,
    string_prop='321',
    dict_prop={'foo': 'bar'})

  yield (api.test('type_error') + api.properties(
      true_prop=True,
      num_prop=123,
      string_prop=321,
      dict_prop={'foo': 'bar'}) +
    api.expect_exception('AssertionError'))

