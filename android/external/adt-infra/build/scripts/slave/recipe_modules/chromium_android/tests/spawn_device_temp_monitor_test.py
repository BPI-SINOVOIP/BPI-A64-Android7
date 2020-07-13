#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import unittest

THIS_DIR = os.path.dirname(os.path.abspath(__file__))

# For 'test_env'.
sys.path.insert(
    0, os.path.abspath(os.path.join(THIS_DIR, '..', '..', '..', 'unittests')))
# For 'spawn_device_temp_monitor.py'.
sys.path.insert(
    0, os.path.abspath(os.path.join(THIS_DIR, '..', 'resources')))

# Imported for side effects on sys.path.
import test_env
import mock

# In depot_tools/
from testing_support import auto_stub
import spawn_device_temp_monitor


class SimulatedSigterm(Exception):
  pass


class mocked_Popen(object):
  def __init__(self, **kwargs):
    self.pid = None
    self.stdout = kwargs.get('stdout', None)
    self.stderr = kwargs.get('stderr', None)

  def communicate(self):
    if not self.stderr:
      return [self.stdout, self.stderr]
    else:
      raise self.stderr


class MainFuncTest(auto_stub.TestCase):
  def setUp(self):
    # Make sleep throw an exception to simulate a sigterm
    # and break out of loop.
    def mocked_sleep_call(duration):
      self.assertEquals(60, duration)
      raise SimulatedSigterm('simulated sigterm')
    self.mock(
        spawn_device_temp_monitor.time,
        'sleep',
        mocked_sleep_call)

  def test_main_responsive_device(self):
    # Collect calls to 'subprocess.Popen' and simulate a responsive device.
    def mocked_popen_calls(args, **kwargs):
      if (args[0] == spawn_device_temp_monitor._RUN_PY):
        # ts_mon was called, so collect args and return
        send_ts_mon_call.extend(args)
        return None
      elif (args[0] == '/some/adb/path'):
        # adb was called, so collect args and define the output
        adb_calls.append(args)

        if (args[2] == 'device_serial_1'):
          if args[4].startswith('grep'):
            return mocked_Popen(stdout='some_thermal_file_name', stderr=None)
          elif args[4].startswith('cat'):
            return mocked_Popen(stdout='12', stderr=None)
          elif args[4].startswith('dumpsys'):
            return mocked_Popen(stdout='temperature: 456\nlevel: 96',
                                stderr=None)
          else:
            self.fail('Unexpected adb command: %s' % (' '.join(args)))
        elif (args[2] == 'device_serial_2'):
          if args[4].startswith('grep'):
            return mocked_Popen(stdout='some_thermal_file_name', stderr=None)
          elif args[4].startswith('cat'):
            return mocked_Popen(stdout='56789', stderr=None)
          elif args[4].startswith('dumpsys'):
            return mocked_Popen(stdout='level: 11\ntemperature: 987',
                                stderr=None)
          else:
            self.fail('Unexpected adb command: %s' % (' '.join(args)))
        else:
          self.fail('Unexpected device serial: %s' % (' '.join(args)))
      else:
        self.fail('Unexpected Popen call: %s' % (' '.join(args)))

    self.mock(
        spawn_device_temp_monitor.subprocess,
        'Popen',
        mocked_popen_calls)
    try:
      send_ts_mon_call = []
      adb_calls = []
      spawn_device_temp_monitor.main([
          '/some/adb/path',
          '["device_serial_1", "device_serial_2"]',
          'some_master_name',
          'some_builder_name'])
    except SimulatedSigterm:
      pass

    # Should build args to send_ts_mon_values correctly.
    expected_cmd = [spawn_device_temp_monitor._RUN_PY,
        'infra.tools.send_ts_mon_values',
        '--ts-mon-device-role',
        'temperature_monitor',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/cpu/temperature", '
        '"value": 12, "device_id": "device_serial_1"}',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/battery/temperature", '
        '"value": 456, "device_id": "device_serial_1"}',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/battery/charge", '
        '"value": 96, "device_id": "device_serial_1"}',
        '--float',
        '{"builder": "some_builder_name", '
        '"master": "some_master_name", "name": "dev/cpu/temperature", '
        '"value": 56, "device_id": "device_serial_2"}',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/battery/temperature", '
        '"value": 987, "device_id": "device_serial_2"}',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/battery/charge", '
        '"value": 11, "device_id": "device_serial_2"}',
    ]
    self.assertEquals(expected_cmd, send_ts_mon_call)
  
  def test_main_dead_device(self):
    # Collect calls to 'subprocess.Popen' and simulate an unresponsive device.
    def mocked_popen_calls(args, **kwargs):
      if (args[0] == spawn_device_temp_monitor._RUN_PY):
        # ts_mon was called, so collect args and return
        send_ts_mon_call.extend(args)
        return None
      elif (args[0] == '/some/adb/path'):
        # adb was called, so collect args and throw exception
        adb_calls.extend(args)
        raise subprocess.CalledProcessError(1, None, None)
      else:
        self.fail('Unexpected Popen call: %s' % (' '.join(args)))

    self.mock(
        spawn_device_temp_monitor.subprocess,
        'Popen',
        mocked_popen_calls)
    try:
      send_ts_mon_call = []
      adb_calls = []
      spawn_device_temp_monitor.main([
          '/some/adb/path',
          '["device_serial_1"]',
          'some_master_name',
          'some_builder_name'])
    except SimulatedSigterm:
      pass

    # Should build args to send_ts_mon_values without any metrics.
    expected_cmd = [spawn_device_temp_monitor._RUN_PY,
        'infra.tools.send_ts_mon_values',
        '--ts-mon-device-role',
        'temperature_monitor',
    ]
    self.assertEquals(expected_cmd, send_ts_mon_call)

  def test_main_hung_device(self):
    # Collect calls to 'subprocess.Popen' and
    # simulate a device that hangs on dumpsys.
    def mocked_popen_calls(args, **kwargs):
      if (args[0] == spawn_device_temp_monitor._RUN_PY):
        # ts_mon was called, so collect args and return
        send_ts_mon_call.extend(args)
        return None
      elif (args[0] == '/some/adb/path'):
        # adb was called, so collect args and define the output,
        # but simulate a timeout when dumpsys is called
        adb_calls.append(args)
        if args[4].startswith('grep'):
          return mocked_Popen(stdout='some_thermal_file_name', stderr=None)
        elif args[4].startswith('cat'):
          return mocked_Popen(stdout='12', stderr=None)
        elif args[4].startswith('dumpsys'):
          return mocked_Popen(
              stdout=None,
              stderr=spawn_device_temp_monitor.AdbDeviceTimeout
          )
        else:
          self.fail('Unexpected adb command: %s' % (' '.join(args)))
      else:
        self.fail('Unexpected Popen call: %s' % (' '.join(args)))

    self.mock(
        spawn_device_temp_monitor.subprocess,
        'Popen',
        mocked_popen_calls)

    # Collect calls to os.kill so that no process is actually killed
    def mocked_kill(*args):
      pass

    self.mock(
        spawn_device_temp_monitor.os,
        'kill',
        mocked_kill)

    try:
      send_ts_mon_call = []
      adb_calls = []
      spawn_device_temp_monitor.main([
          '/some/adb/path',
          '["device_serial_1"]',
          'some_master_name',
          'some_builder_name'])
    except SimulatedSigterm:
      pass


    expected_cmd = [spawn_device_temp_monitor._RUN_PY,
        'infra.tools.send_ts_mon_values',
        '--ts-mon-device-role',
        'temperature_monitor',
        '--float',
        '{"builder": "some_builder_name", "master": "some_master_name", '
        '"name": "dev/cpu/temperature", '
        '"value": 12, "device_id": "device_serial_1"}'
    ]
    self.assertEquals(expected_cmd, send_ts_mon_call)

if __name__ == '__main__':
  unittest.main()
