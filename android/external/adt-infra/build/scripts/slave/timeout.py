#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import datetime
import os
import subprocess
import sys


BUILD_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__))))
sys.path.append(os.path.join(BUILD_ROOT, 'third_party'))


from luci_py import subprocess42


def round_timedelta(timedelta):
  return datetime.timedelta(seconds=int(round(timedelta.total_seconds())))


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument(
      '--soft-timeout', type=int, default=15*60,
      help='Timeout (in seconds) after which a warning message will be printed '
           'to stdout.')
  parser.add_argument(
      '--hard-timeout', type=int,
      help='Timeout (in seconds) after which the command will be killed.')
  parser.add_argument(
      '--output-limit', type=int,
      help='Output limit (both stdout and stderr) in bytes after which '
           'the command will be killed.')
  parser.add_argument('command', nargs='+')
  args = parser.parse_args(argv)

  start_timestamp = datetime.datetime.now()

  proc = subprocess42.Popen(
    args.command,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE)

  # Use a dict because python2 doesn't have nonlocal keyword
  # and we'd get UnboundLocalError otherwise.
  output_status = {'length': 0, 'limit_exceeded': False}
  def stream_output(stream, data):
    output_status['length'] += len(data)
    if args.output_limit and output_status['length'] > args.output_limit:
      output_status['limit_exceeded'] = True
      kill_result = proc.kill()
      sys.stderr.write('timeout.py: output limit exceeded (kill %s)\n' % (
          'successful' if kill_result else 'failed'))
      sys.stderr.flush()
      return False

    stream.write(data)
    stream.flush()
    return True

  for stream_name, data in proc.yield_any(
      soft_timeout=args.soft_timeout, hard_timeout=args.hard_timeout,
      maxsize=512):
    # Print a message on soft timeout (only when the process is still running).
    if (stream_name, data) == (None, None):
      proc.poll()
      if proc.returncode is None:
        if not stream_output(
            sys.stderr,
            'timeout.py: still running %r (%s)\n' % (
                args.command,
                round_timedelta(datetime.datetime.now() - start_timestamp))):
          break
      continue

    stream = sys.stdout if stream_name == 'stdout' else sys.stderr
    if not stream_output(stream, data):
      break

  sys.stdout.write('timeout.py: waiting for the process to finish...\n')
  sys.stdout.flush()

  # Ensure we know the return code.
  proc.wait()

  sys.stdout.write(
      'timeout.py: command finished; exit code: %d; run time %s; '
      'output size (bytes): %d\n' % (
          proc.returncode,
          round_timedelta(datetime.datetime.now() - start_timestamp),
          output_status['length']))
  sys.stdout.flush()

  # Make sure we always exit with non-zero code on failure.
  if output_status['limit_exceeded'] and proc.returncode == 0:
    return 1

  return proc.returncode


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
