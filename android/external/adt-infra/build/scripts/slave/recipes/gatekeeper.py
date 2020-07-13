# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Launches the gatekeeper."""


DEPS = [
  'gatekeeper',
  'path',
]


def RunSteps(api):
  api.gatekeeper(
    api.path['build'].join('scripts', 'slave', 'gatekeeper.json'),
    api.path['build'].join('scripts', 'slave', 'gatekeeper_trees.json'),
  )


def GenTests(api):
  yield (
    api.test('basic')
    + api.step_data(
      'reading gatekeeper_trees.json',
      api.gatekeeper.fake_test_data(),
    )
  )

  yield (
    api.test('keep_going')
    + api.step_data(
      'reading gatekeeper_trees.json',
      api.gatekeeper.fake_test_data(),
    )
    + api.step_data('gatekeeper: chromium', retcode=1)
  )
