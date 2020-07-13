# Copyright (c) 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Specifies how to launch chromoting integration test on build_internal."""

DEPS = [
    'bot_update',
    'gclient',
    'legion',
    'properties',
]


def RunSteps(api):
  api.gclient.set_config('chromium')
  api.bot_update.ensure_checkout(force=True)

  controller = api.legion.create_controller(
      name='Test Controller',
      path='controller_path.isolate',
      os='Linux',
      config_vars={'cfg_name': 'cfg_value'},
      controller_vars={'cont_name': 'cont_value'},
      dimensions={'dim_name': 'dim_value'},
  )

  api.legion.add_task_to_controller(
      controller=controller,
      name='Task',
      path='task_path.isolated',
      config_vars={'cfg_name': 'cfg_value'}
  )

  api.legion.execute(controller)


def GenTests(api):
  yield api.test('basic') + api.properties.generic()

