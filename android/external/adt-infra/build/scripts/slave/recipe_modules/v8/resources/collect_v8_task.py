#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import optparse
import os
import shutil
import subprocess
import sys
import tempfile
import traceback

from slave import slave_utils


MISSING_SHARDS_MSG = r"""Missing results from the following shard(s): %s

It can happen in following cases:
  * Test failed to start (missing *.dll/*.so dependency for example)
  * Test crashed or hung
  * Swarming service experiences problems

Please examine logs to figure out what happened.
"""


def emit_warning(title, log=None):
  print '@@@STEP_WARNINGS@@@'
  print title
  if log:
    slave_utils.WriteLogLines(title, log.split('\n'))


def merge_shard_results(output_dir):
  """Reads JSON test output from all shards and combines them into one.

  Returns dict with merged test output on success or None on failure. Emits
  annotations.
  """
  # summary.json is produced by swarming.py itself. We are mostly interested
  # in the number of shards.
  try:
    with open(os.path.join(output_dir, 'summary.json')) as f:
      summary = json.load(f)
  except (IOError, ValueError):
    emit_warning(
        'summary.json is missing or can not be read',
        'Something is seriously wrong with swarming_client/ or the bot.')
    return None

  # Merge all JSON files together.
  archs = []
  modes = []
  slowest_tests = []
  results = []
  missing_shards = []
  for index, result in enumerate(summary['shards']):
    if result is not None:
      json_data = load_shard_json(output_dir, index)
      if json_data:
        # On continuous bots, the test driver outputs exactly one item in the
        # test results list for one architecture.
        assert len(json_data) == 1
        archs.append(json_data[0]['arch'])
        modes.append(json_data[0]['mode'])

        slowest_tests.extend(json_data[0]['slowest_tests'])
        results.extend(json_data[0]['results'])
        continue
    missing_shards.append(index)

  # Each shard must have used the same arch and mode configuration.
  assert len(set(archs)) == 1
  assert len(set(modes)) == 1

  # If some shards are missing, make it known. Continue parsing anyway. Step
  # should be red anyway, since swarming.py return non-zero exit code in that
  # case.
  if missing_shards:
    as_str = ', '.join(map(str, missing_shards))
    emit_warning(
        'some shards did not complete: %s' % as_str,
        MISSING_SHARDS_MSG % as_str)
    # Not all tests run, combined JSON summary can not be trusted.
    # TODO(machenbach): Implement using a tag in the test results that makes
    # the step know they're incomplete.

  return [{
    'arch': archs[0],
    'mode': modes[0],
    'slowest_tests': sorted(
        slowest_tests, key=lambda t: t['duration'], reverse=True),
    'results': results,
  }]


def load_shard_json(output_dir, index):
  """Reads JSON output of a single shard."""
  # 'output.json' is set in v8/testing.py, V8SwarmingTest.
  path = os.path.join(output_dir, str(index), 'output.json')
  try:
    with open(path) as f:
      return json.load(f)
  except (IOError, ValueError):
    print >> sys.stderr, 'Missing or invalid v8 JSON file: %s' % path
    return None


def main(args):
  # Split |args| into options for shim and options for swarming.py script.
  if '--' in args:
    index = args.index('--')
    shim_args, swarming_args = args[:index], args[index+1:]
  else:
    shim_args, swarming_args = args, []

  # Parse shim own's options.
  parser = optparse.OptionParser()
  parser.add_option('--swarming-client-dir')
  parser.add_option('--temp-root-dir', default=tempfile.gettempdir())
  parser.add_option('--merged-test-output')
  options, extra_args = parser.parse_args(shim_args)

  # Validate options.
  if extra_args:
    parser.error('Unexpected command line arguments')
  if not options.swarming_client_dir:
    parser.error('--swarming-client-dir is required')

  # Prepare a directory to store JSON files fetched from isolate.
  task_output_dir = tempfile.mkdtemp(
      suffix='_swarming', dir=options.temp_root_dir)

  # Start building the command line for swarming.py.
  args = [
    sys.executable,
    '-u',
    os.path.join(options.swarming_client_dir, 'swarming.py'),
  ]

  args.extend(swarming_args)
  args.extend(['--task-output-dir', task_output_dir])

  exit_code = 1
  try:
    # Run the real script, regardless of an exit code try to find and parse
    # JSON output files, since exit code may indicate that the isolated task
    # failed, not the swarming.py invocation itself.
    exit_code = subprocess.call(args)

    # Output parsing should not change exit code no matter what, so catch any
    # exceptions and just log them.
    try:
      with open(options.merged_test_output, 'wb') as f:
        json.dump(merge_shard_results(task_output_dir),
                  f, separators=(',', ':'))
    except Exception:
      emit_warning(
          'failed to process v8 output JSON', traceback.format_exc())

  finally:
    shutil.rmtree(task_output_dir, ignore_errors=True)

  return exit_code


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
