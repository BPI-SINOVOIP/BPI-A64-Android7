# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'properties',
  'swarming_heartbeat',
]


def RunSteps(api):
  api.swarming_heartbeat.run()


def GenTests(api):
  yield api.test('prod')

  yield (
      api.test('staging') +
      api.properties.scheduled(target_environment='staging'))
