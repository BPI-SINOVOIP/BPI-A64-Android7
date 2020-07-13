# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


class LegionApi(recipe_api.RecipeApi):
  """Provides a recipes interface for the Legion framework."""

  @property
  def legion_path(self):
    """Returns the path to legion.py."""
    return self.m.path['checkout'].join('testing', 'legion', 'tools',
                                        'legion.py')

  def create_controller(self, name, path, os, config_vars=None,
                        controller_vars=None, dimensions=None):
    """Returns a controller config dictionary.

    Args:
      name: The name of the controller.
      path: The path to the .isolate or .isolated file for the controller.
      os: The os to run the controller on.
      config_vars: A dictionary of config vars to pass when isolating the
          controller .isolate file. This is ignored if passing a .isolated file.
      controller_vars: A dictionary of command line vars passed to the
          controller.
      dimensions: A dictionary of dimensions to pass when isolating the
          controller .isolate file. This is ignored if passing a .isolated file.
    """
    return {
        'name': name,
        'path': path,
        'os': os,
        'config_vars': config_vars or {},
        'controller_vars': controller_vars or {},
        'dimensions': dimensions or {},
        'tasks': []
        }

  def add_task_to_controller(self, controller, name, path, config_vars=None):
    """Adds a task config to a controller config.

    Args:
      controller: A controller config returnd by create_controller.
      name: The name of the task. This corresponds to the command line flag
          defined in the controller code.
      path: The path to the .isolate or .isolated file for the task.
      config_vars: Config variables passed when isolating a task .isolate file.
          This is ignored if passing a .isolated file.
    """
    controller['tasks'].append({
        'name': name,
        'path': path,
        'config_vars': config_vars or {}
        })

  def _archive_if_needed(self, config):
    """Archives an isolate file if needed.

    This method is a no-op if the path is already an .isolated file. If not the
    file is archived and the path is set to the .isolated file.
    """
    for item in [config] + config['tasks']:
      if self.m.path.splitext(item['path'])[-1] == '.isolated':
        continue
      isolated_path = str(item['path']) + 'd'
      cmd = [
          'archive',
          '--isolate', item['path'],
          '--isolated', isolated_path,
          '--isolate-server', self.m.isolate.isolate_server,
      ]
      for name, value in item['config_vars'].iteritems():
        cmd.extend(['--config-variable', name, value])

      self.m.python(
          'archive for %s' % self.m.path.split(str(item['path']))[-1],
          self.m.swarming_client.path.join('isolate.py'),
          cmd)
      item['path'] = isolated_path

  def execute(self, config):
    """Executes a Legion-based swarming test.

    config: The configuration returned by create_controller.
    """
    self._archive_if_needed(config)

    cmd = [
        'run',
        '--controller-isolated', config['path'],
        '--task-name', config['name'],
        '--isolate-server', self.m.isolate.isolate_server,
        '--swarming-server', self.m.swarming.swarming_server,
        '--dimension', 'os', config['os']
    ]

    for name, value in config['dimensions'].iteritems():
      cmd.extend(['--dimension', name, value])
    for name, value in config['controller_vars'].iteritems():
      cmd.extend(['--controller-var', name, value])
    for task in config['tasks']:
      cmd.extend(['--task', task['name'], task['path']])

    step_result = self.m.python(
        'Running test for %s' % config['name'],
        self.legion_path,
        cmd)
    return step_result.stdout
