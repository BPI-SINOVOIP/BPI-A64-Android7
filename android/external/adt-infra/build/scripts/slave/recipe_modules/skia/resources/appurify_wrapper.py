#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import argparse
import json
import os
import subprocess
import sys
import tempfile
import urllib2
import zipfile


"""Wrapper for appurify-client.py which avoids hard-coding credentials."""


DEVICE_TYPE_ID_MAP = {
  'Nexus5': 536,
}


def get_cred_from_file(key):
  """Read credential information from a file."""
  filepath = os.path.join(os.path.expanduser('~'), '.%s' % key)
  with open(filepath) as f:
    return f.read().rstrip()


def run_no_except(cmd):
  """Run the given command and don't let any exceptions propagate."""
  cmd = [str(c) for c in cmd]
  try:
    return subprocess.check_output(cmd)
  except:
    raise Exception('Command failed')


def get_creds_and_run(cmd):
  """Obtain credentials and run the given command on Appurify."""
  # Find credentials.
  key = get_cred_from_file('appurify_key')
  secret = get_cred_from_file('appurify_secret')

  # Obtain an access token.
  creds_output = run_no_except([
      'appurify-client.py', '--api-key', key, '--api-secret', secret,
      '--action', 'access_token_generate'])
  token = eval(creds_output)['response']['access_token']

  # Run the given command.
  run_no_except(['appurify-client.py', '--access-token', token] + cmd)


def get_device_type_id(device):
  """Find and return the ID for the given device."""
  device_type_id = DEVICE_TYPE_ID_MAP.get(device)
  if not device_type_id:
    raise Exception('Unknown device type: %s' % device)
  return device_type_id


def write_to_zip_file(zip_file, path, arc_path):
  """Recursively write |path| to |zip_file| as |arc_path|.

  zip_file: An open instance of zipfile.ZipFile.
  path: An absolute path to the file or directory to be zipped.
  arc_path: A relative path within the zip file to which the file or directory
    located at |path| should be written.

  This is copied and modified from:
  https://chromium.googlesource.com/chromium/src/build/+/1791a46a29d531e7b2f6409756d7586d7d2fbcfb/android/pylib/utils/zip_utils.py
  """
  if os.path.isdir(path):
    for dir_path, _, file_names in os.walk(path):
      dir_arc_path = os.path.join(arc_path, os.path.relpath(dir_path, path))
      print 'dir:  %s -> %s' % (dir_path, dir_arc_path)
      zip_file.write(dir_path, dir_arc_path, zipfile.ZIP_STORED)
      for f in file_names:
        file_path = os.path.join(dir_path, f)
        file_arc_path = os.path.join(dir_arc_path, f)
        print 'file: %s -> %s' % (file_path, file_arc_path)
        zip_file.write(file_path, file_arc_path, zipfile.ZIP_DEFLATED)
  else:
    print 'file: %s -> %s' % (path, arc_path)
    zip_file.write(path, arc_path, zipfile.ZIP_DEFLATED)


def package_tests(zip_file, robotium_cfg_file, test_apk, skp_dir=None,
                  resource_dir=None):
  """Package all tests into a zip file."""
  sdcard_files = []
  with zipfile.ZipFile(zip_file, 'w') as zip_file:
    zip_file.write(test_apk, os.path.basename(test_apk), zipfile.ZIP_DEFLATED)

    if skp_dir:
      skps_prefix = 'skps'
      write_to_zip_file(zip_file, skp_dir, skps_prefix)
      sdcard_files.extend(
        ['/'.join((skps_prefix, f)) for f in os.listdir(skp_dir)])

    if resource_dir:
      resources_prefix = 'resources'
      write_to_zip_file(zip_file, resource_dir, resources_prefix)
      sdcard_files.extend(
        ['/'.join((resources_prefix, f)) for f in os.listdir(resource_dir)])

  robotium_cfg = '''[robotium]
dumpsys=1
dumpstate=1
collect_artifacts=/sdcard/skia_results
host_test=%s
sdcard_files=%s
''' % (os.path.basename(test_apk), ','.join(sdcard_files))
  with open(robotium_cfg_file, 'w') as f:
    f.write(robotium_cfg)


def run_on_appurify(apk, test_apk, device, result_dir, skp_dir=None,
                    resource_dir=None):
  """Test the APK on Appurify."""
  with tempfile.NamedTemporaryFile(suffix='.zip') as test_src:
    with tempfile.NamedTemporaryFile(suffix='.cfg') as config_src:
      package_tests(test_src.name, config_src.name, test_apk, skp_dir,
                    resource_dir)
      args = [
          '--app-src', apk,
          '--test-src', test_src.name,
          '--result-dir', result_dir,
          '--test-type', 'robotium',
          '--config-src', config_src.name,
          '--device-type-id', get_device_type_id(device),
      ]
      get_creds_and_run(args)


def main():
  parser = argparse.ArgumentParser(description='Run in Appurify')
  parser.add_argument('--apk', required=True)
  parser.add_argument('--test-apk', required=True)
  parser.add_argument('--device', required=True)
  parser.add_argument('--result-dir', required=True)
  parser.add_argument('--skp-dir')
  parser.add_argument('--resource-dir')
  args = parser.parse_args()
  run_on_appurify(args.apk, args.test_apk, args.device, args.result_dir,
                  args.skp_dir, args.resource_dir)


if __name__ == '__main__':
  main()
