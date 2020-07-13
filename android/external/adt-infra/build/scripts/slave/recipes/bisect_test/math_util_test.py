# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Code coverage tests for the math_utils recipe module."""

import math

DEPS = [
    'math_utils',
    'test_utils',
]

def RunSteps(api):
  assert(0 == api.math_utils.relative_change(0, 0))
  assert(0 == api.math_utils.relative_change(1, 1))
  sample_a = list(range(1, 10))
  sample_b = list(range(11, 21))
  assert(99.9 == api.math_utils.confidence_score(sample_a, sample_b))
  assert(0 == api.math_utils.confidence_score([1],[1, 2]))
  assert(0 == api.math_utils.confidence_score([1, 1],[1, 2]))
  assert(99.9 == api.math_utils.confidence_score([1, 1, 1],[2, 2, 2]))
  assert(0 == api.math_utils.confidence_score([],[], True))
  assert(0 ==  api.math_utils.relative_change(1, 1))
  assert(math.isnan(api.math_utils.relative_change(0, 1)))
  assert(1 == api.math_utils.relative_change(1, 2))
  assert(0 == api.math_utils.variance([0]))
  assert(1.33 <  api.math_utils.pooled_standard_error([sample_a, sample_b])
         < 1.34)
  assert(0 == api.math_utils.pooled_standard_error([[0]]))
  assert(0 == api.math_utils.standard_error([1]))
  assert(110 == api.math_utils.mean([10, 20, 300]))
  try:
    api.math_utils.mean([])
  except ValueError:
    pass
  else:  # pragma: no cover
    raise AssertionError('`mean` doesn\'t raise ValueError for empty list.')

def GenTests(api):
  yield api.test('math_utils_test')
