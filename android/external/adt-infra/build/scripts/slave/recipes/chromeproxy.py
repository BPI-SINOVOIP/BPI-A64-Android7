# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Recipe for running ChromeProxy integration tests on desktop VM's."""

DEPS = [
    'properties',
    'step',
]


def RunSteps(api):
  # TODO(bustamante): Add ChromeProxy tests.
  api.step('Placeholder Step', ['echo', 'Placeholder Step'])


def GenTests(api):
  yield (
      api.properties.generic()
  )
