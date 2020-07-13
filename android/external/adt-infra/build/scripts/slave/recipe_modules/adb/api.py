# Copyright (c) 2014 ThE Chromium Authors. All Rights Reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api

class AdbApi(recipe_api.RecipeApi):
  def __init__(self, **kwargs):
    super(AdbApi, self).__init__(**kwargs)
    self._custom_adb_path = None
    self._devices = None

  def __call__(self, cmd, serial=None, **kwargs):
    """Run an ADB command."""
    cmd_prefix = [self.adb_path()]
    if serial:
      cmd_prefix.extend(['-s', serial])
    return self.m.step(cmd=cmd_prefix + cmd, **kwargs)

  def set_adb_path(self, adb_path):
    self._custom_adb_path = adb_path

  def adb_path(self):
    if self._custom_adb_path:
      return self._custom_adb_path
    return self.m.path['slave_build'].join(
        'src', 'third_party', 'android_tools', 'sdk', 'platform-tools', 'adb')

  def list_devices(self, step_test_data=None, **kwargs):
    cmd = [
        str(self.adb_path()),
        'devices',
    ]

    result = self.m.python(
        'List adb devices',
        self.resource('list_devices.py'),
        args=[ repr(cmd), self.m.json.output() ],
        step_test_data=step_test_data or self.test_api.device_list,
        **kwargs)

    self._devices = result.json.output

  @property
  def devices(self):
    assert self._devices is not None, (
        "devices is only available after yielding list_devices()")
    return self._devices

  def root_devices(self, **kwargs):
    self.list_devices(**kwargs)
    self.m.python.inline(
        'Root devices',
        """
        import subprocess
        import sys
        adb_path = sys.argv[1]
        for device in sys.argv[2:]:
          print 'Attempting to root device %s ...' % (device)
          subprocess.check_call([adb_path, '-s', device, 'root'])
          subprocess.check_call([adb_path, '-s', device, 'wait-for-device'])
          print 'Finished rooting device %s' % (device)
        """,
        args=[self.adb_path()] + self.devices,
        **kwargs)
