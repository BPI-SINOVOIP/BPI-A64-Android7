#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Launches a daemon to monitor android device temperaures.

This script will repeatedly poll the given devices for their
temperatures every 60 seconds via adb and uploads them for monitoring
through infra's ts_mon.
"""

import argparse
import json
import logging
import os
import optparse
import re
import signal
import subprocess
import sys
import time

# Various names of sensors used to measure cpu temp
_CPU_TEMP_SENSORS = [
  # most nexus devices
  'tsens_tz_sensor0',
  # android one
  'mtktscpu',
  # nexus 9
  'CPU-therm',
]

# TODO(bpastene): change the following if infra.git becomes a checked
# out repo on slaves instead of a cipd managed package.

# Location of the infra-python package's run script.
_RUN_PY = '/opt/infra-python/run.py'
 
_TEMP_REGEX = re.compile('^\s*temperature: ([0-9]+)\s*$')
_CHARGE_REGEX = re.compile('^\s*level: ([0-9]+)\s*$')


class AdbDeviceTimeout(Exception):
  pass


def alarm_handler(signum, frame):
  raise AdbDeviceTimeout


def run_adb_command(cmd, timeout=None):
  """Wrapper function around adb commands that supports timeouts."""
  if timeout:
    try:
      signal.alarm(timeout)
      proc = subprocess.Popen(cmd, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE)
      stdout, stderr = proc.communicate()
      signal.alarm(0)
      return stdout
    except AdbDeviceTimeout:
      os.kill(proc.pid, signal.SIGTERM)
      raise subprocess.CalledProcessError(1, None, None)
  else:
    return subprocess.check_output(cmd)


def get_device_args(adb_path, master_name, builder_name, device):
  bat_charge = None
  bat_temp = None
  cpu_temp = None
  # Search for the file that the _CPU_TEMP_SENSOR dumps to and cat it.
  cmd = [adb_path, '-s', device, 'shell',
         'grep -lE "%s" /sys/class/thermal/thermal_zone*/type'
           % ('|'.join(_CPU_TEMP_SENSORS))]
  try:
    cpu_temp_files = run_adb_command(cmd, timeout=5)
    if (len(cpu_temp_files.splitlines()) == 1):
        cpu_temp_file = re.sub('type$', 'temp', cpu_temp_files.strip())
        cmd = [adb_path, '-s', device, 'shell',
               'cat %s' % (cpu_temp_file)]
        file_contents = run_adb_command(cmd, timeout=5).strip()
        # Most devices report cpu temp in degrees (C), but a few
        # can report it in thousandths of a degree. If this is in thousandths,
        # chop off the trailing three digits to convert to degrees
        if (len(file_contents) == 5):
          file_contents = file_contents[:2]
        cpu_temp = int(file_contents)
  except (subprocess.CalledProcessError, TypeError, ValueError):
    cpu_temp = None

  # Dump system battery info and grab the temp.
  cmd = [adb_path, '-s', device, 'shell', 'dumpsys battery']
  try:
    battery_info = run_adb_command(cmd, timeout=5)
    for line in battery_info.splitlines():
      temp_match = _TEMP_REGEX.match(line)
      charge_match = _CHARGE_REGEX.match(line)
      if temp_match:
        bat_temp = int(temp_match.group(1))
      elif charge_match:
        bat_charge = int(charge_match.group(1))

  except (subprocess.CalledProcessError, TypeError, ValueError):
    bat_temp = None
    bat_charge = None

  cpu_dict = {'name': "dev/cpu/temperature",
              'value': cpu_temp,
              'device_id': device,
              'master': master_name,
              'builder': builder_name}
  cpu_temp_args = ['--float', json.dumps(cpu_dict)] if cpu_temp else []
  battery_temp_dict = {'name': 'dev/battery/temperature',
                       'value': bat_temp,
                       'device_id': device,
                       'master': master_name,
                       'builder': builder_name}
  bat_temp_args = ['--float', 
                   json.dumps(battery_temp_dict)] if bat_temp else []
  battery_charge_dict = {'name': 'dev/battery/charge',
                         'value': bat_charge,
                         'device_id': device,
                         'master': master_name,
                         'builder': builder_name}
  bat_charge_args = ['--float',
                     json.dumps(battery_charge_dict)] if bat_charge else []
  return cpu_temp_args + bat_temp_args + bat_charge_args


def main(argv):
  """Launches the device temperature monitor.

  Polls the devices for their battery and cpu temperatures
  every 60 seconds and uploads them for monitoring through infra's
  ts_mon. Fully qualified, the metric names would be
  /chrome/infra/dev/(cpu|battery)/temperature &
  /chrome/infra/dev/battrery/charge
  """

  parser = argparse.ArgumentParser(
      description='Launches the device temperature monitor.')
  parser.add_argument('adb_path', help='Path to adb binary.')
  parser.add_argument('devices_json',
                      help='Json list of device serials to poll.')
  parser.add_argument('master_name', help='Name of the buildbot master.')
  parser.add_argument('builder_name', help='Name of the buildbot builder.')
  args = parser.parse_args(argv)

  signal.signal(signal.SIGALRM, alarm_handler)

  devices = json.loads(args.devices_json)
  while True:
    upload_cmd_args = []
    for device in devices:
      upload_cmd_args += get_device_args(args.adb_path, args.master_name,
                                         args.builder_name, device)

    cmd = [_RUN_PY, 'infra.tools.send_ts_mon_values', '--ts-mon-device-role',
           'temperature_monitor'] + upload_cmd_args
    try:
      subprocess.Popen(cmd)
    except OSError:
      logging.exception('Unable to call %s', _RUN_PY)

    time.sleep(60)


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
