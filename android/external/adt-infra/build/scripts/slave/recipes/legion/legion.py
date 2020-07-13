# Copyright (c) 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Launches Legion tests."""

from recipe_engine.types import freeze

DEPS = [
    'bot_update',
    'gclient',
    'isolate',
    'legion',
    'path',
    'properties',
    'swarming',
]

CONFIG_VARS = {'multi_machine': '1'}
CONTROLLER_VARS = {'verbosity': 'INFO'}
EXAMPLE_DIR = ['testing', 'legion', 'examples']
SUBPROCESS_DIR = EXAMPLE_DIR + ['subprocess']
HELLO_WORLD_DIR = EXAMPLE_DIR + ['hello_world']
HTTP_TEST_DIR = EXAMPLE_DIR + ['http_example']

CONFIGS = freeze({
  'subprocess_test': {
    'controller_path': SUBPROCESS_DIR + ['subprocess_test.isolate'],
    'task_paths': {
      'task-hash': SUBPROCESS_DIR + ['task.isolate'],
    },
    'config_vars': CONFIG_VARS,
    'controller_vars': CONTROLLER_VARS,
  },
  'hello_world_test': {
    'controller_path': HELLO_WORLD_DIR + ['controller_test.isolate'],
    'task_paths': {
      'task-hash': HELLO_WORLD_DIR + ['task_test.isolate'],
    },
    'config_vars': CONFIG_VARS,
    'controller_vars': CONTROLLER_VARS,
  },
  'http_test': {
    'controller_path': HTTP_TEST_DIR + ['http_test.isolate'],
    'task_paths': {
      'http-server': HTTP_TEST_DIR + ['http_server.isolate'],
      'http-client': HTTP_TEST_DIR + ['http_client.isolate']
    },
    'config_vars': CONFIG_VARS,
    'controller_vars': CONTROLLER_VARS,
  },
})


def RunSteps(api):
  api.isolate.isolate_server = (
      'https://omnibot-legion-isolate-server.appspot.com')
  api.swarming.swarming_server = (
      'https://omnibot-legion-swarming-server.appspot.com')
  api.gclient.set_config('chromium')
  api.bot_update.ensure_checkout(force=True)

  for name, config in CONFIGS.iteritems():
    controller = api.legion.create_controller(
        name=name,
        path=api.path['checkout'].join(*config['controller_path']),
        os='Linux',
        config_vars=config['config_vars'],
        controller_vars=config['controller_vars']
    )

    for name, path in config['task_paths'].iteritems():
      api.legion.add_task_to_controller(
          controller=controller,
          name=name,
          path=api.path['checkout'].join(*path),
          config_vars=config['config_vars']
      )

    api.legion.execute(controller)


def GenTests(api):
  yield api.test('basic') + api.properties.generic()
