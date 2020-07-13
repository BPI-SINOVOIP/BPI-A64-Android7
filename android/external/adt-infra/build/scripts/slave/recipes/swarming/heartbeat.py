# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Swarming heart beat recipe: runs a dummy job on the prod Swarming instance to
ensure it is working properly.

Waterfall page: https://build.chromium.org/p/chromium.swarm/waterfall
"""

DEPS = [
  'properties',
  'swarming',
  'swarming_client',
  'swarming_heartbeat',
]


def RunSteps(api):
  branch = 'stable'
  if api.properties.get('target_environment') == 'staging':
    branch = 'master'
  api.swarming_client.checkout(branch, can_fail_build=False)
  api.swarming.check_client_version()
  api.swarming_heartbeat.run()


def GenTests(api):
  yield (
    api.test('heartbeat') +
    api.properties.scheduled()
  )
  yield (
    api.test('heartbeat_staging') +
    api.properties.scheduled(target_environment='staging')
  )
