# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


class DiskApi(recipe_api.RecipeApi):
  """DiskApi contains helper functions for reading disk info."""

  def space_usage(
      self, path=None, warning_level=None, previous_result=None,
      can_fail_build=False, name=None, **kwargs):
    """Displays disk space usage.

    Does not support Windows yet, does not emit steps.

    Prints disk space usage in step log and step text.

    Args:
      path (str): path mapped to the disk. Defaults to [SLAVE_BUILD].
      warning_level (float): a value from 0 to 1.0. If usage reaches this level,
        mark the step as WARNING. Defaults to 0.9.
      previous_result (dict): previous result of space_usage call. If passed,
        delta is displayed in step text.
      name (str): step name. Defaults to "disk space usage".

    Returns:
      A dict with disk usage info or None if step fails. Dict keys:
        * capacity (float): disk capacity, in MiB.
        * used (float): disk usage, in MiB.
    """
    path = path or self.m.path['slave_build']
    name = name or 'disk space usage'
    warning_level = warning_level or 0.9
    kwargs.setdefault(
        'step_test_data',
        lambda: self.m.json.test_api.output_stream(
            self.test_api.space_usage_result()))

    if self.m.platform.is_win:
      # Not supported. Feel free to implement.
      return

    step = None
    try:
      step = self.m.python(
          name,
          self.resource('statvfs.py'),
          stdout=self.m.json.output(),
          args=[path],
          **kwargs)
      capacity_mb = step.stdout['capacity'] / 1024.0 / 1024.0
      used_mb = step.stdout['used'] / 1024.0 / 1024.0
      percent = used_mb / capacity_mb
      step.presentation.step_text = '%.2f/%.2f GiB (%d%%) used' % (
          used_mb / 1024.0, capacity_mb / 1024.0, percent * 100)
      if percent >= warning_level:
        step.presentation.status = self.m.step.WARNING
      if previous_result:
        step.presentation.step_text += '. Delta: %+.2f MiB' % (
            used_mb - previous_result['used'])
      return {
          'capacity': capacity_mb,
          'used': used_mb,
      }
    except Exception as ex:
      # Do not fail entire build because of a disk space step failure.
      if step:
        step.presentation.logs['exception'] = ['%r' % ex]
        step.presentation.status = self.m.step.WARNING
      if can_fail_build:
        raise recipe_api.StepFailure('Could not get disk info: %s' % ex)
      return

