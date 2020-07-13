# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'step',
  'example',
]

from recipe_engine import recipe_api

@recipe_api.composite_step
def deferrer(api):
  with api.step.defer_results():
    api.example('aggregated start')
    api.example('aggregated finish')

def normal(api):
  api.example('normal start')
  api.example('normal finish')

@recipe_api.composite_step
def composite_step(api):
  api.example('composite start')
  api.example('composite finish')
  return "jane"

def RunSteps(api):
  with api.step.defer_results():
    api.example('prelude')
    deferrer(api)
    normal(api)
    rslt = composite_step(api)
    api.example.explicit_non_composite_step()  # will count as 2 steps
    api.example(rslt.get_result())
    api.example('clean up')


def GenTests(api):
  yield api.test('basic')

  yield (
      api.test('one_fail') +
      api.step_data('prelude', retcode=1)
    )

  yield (
      api.test('nested_aggregate_fail') +
      api.step_data('aggregated start', retcode=1)
    )

  yield (
      api.test('nested_normal_fail') +
      api.step_data('normal start', retcode=1)
    )

  yield (
      api.test('nested_comp_fail') +
      api.step_data('composite start', retcode=1)
    )
