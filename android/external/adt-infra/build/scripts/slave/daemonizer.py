#!/usr/bin/python
# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This script can daemonize another script. This is usefull in running
background processes from recipes. Lookat chromium_android module for
example.

USAGE:

  daemonizer.py [options] -- <script> [args]

  - options are options to this script.
  - script is the script to daemonize or run in the background
  - args are the arguments that one might want to pass the <script>
"""

# TODO(sivachandra): Enhance this script by enforcing a protocol of
# communication between the parent (this script) and the daemon script.

# TODO(bpastene): Improve file handling by adding flocks over file io
# as implemented in infra/libs/service_utils/_daemon_nix.py

import argparse
import logging
import os
import signal
import subprocess
import sys


def restart(cmd, pid_file_path):
  # Check for the pid_file to see if the daemon's already running
  # and restart it if it is.
  if pid_file_path == None:
    logging.error('pid_file_path arg must be specified when '
                  'restarting a daemon')
    return 1
  try:
    with open(pid_file_path, 'r') as pid_file:
      pid = int(pid_file.readline())
  except (IOError, ValueError):
    pid = None

  if pid:
    logging.info(
        "%s pid file already exists, attempting to kill process %d",
        pid_file_path,
        pid)
    try:
      os.kill(pid, signal.SIGTERM)
    except OSError:
      logging.exception("Unable to kill old daemon process")

  return daemonize(cmd, pid_file_path)


def daemonize(cmd, pid_file_path):
  """This function is based on the Python recipe provided here:
  http://www.jejik.com/articles/2007/02/a_simple_unix_linux_daemon_in_python/
  """
  # Spawn a detached child process.
  try:
    pid = os.fork()
    if pid > 0:
      # exit first parent
      sys.exit(0)
  except OSError, e:
    sys.stderr.write("fork #1 failed, unable to daemonize: %d (%s)\n" %
                     (e.errno, e.strerror))
    sys.exit(1)

  # decouple from parent environment
  os.chdir("/")
  os.setsid()
  os.umask(0)

  # do second fork
  try:
    pid = os.fork()
    if pid > 0:
      # exit from second parent
      sys.exit(0)
  except OSError, e:
    sys.stderr.write("fork #2 failed, unable to daemonize: %d (%s)\n" %
                     (e.errno, e.strerror))
    sys.exit(1)

  # redirect standard file descriptors
  sys.stdout.flush()
  sys.stderr.flush()
  si = file('/dev/null', 'r')
  so = file('/dev/null', 'a+')
  se = file('/dev/null', 'a+', 0)
  os.dup2(si.fileno(), sys.stdin.fileno())
  os.dup2(so.fileno(), sys.stdout.fileno())
  os.dup2(se.fileno(), sys.stderr.fileno())

  proc = subprocess.Popen(cmd)

  # Write pid to file if applicable.
  if pid_file_path:
    try:
      with open(pid_file_path, 'w') as pid_file:
        pid_file.write('%s' % str(proc.pid))
    except (IOError):
      logging.exception("Unable to write pid to file")

  proc.communicate()
  return proc.returncode


def stop(pid_file_path):
  if pid_file_path == None:
    logging.error("pid_file_path arg must be specified when stopping a daemon")
    return 1
  try:
    with open(pid_file_path) as pid_file:
      pid = int(pid_file.readline())
    logging.info('Sending SIGTERM to %d', pid)
    os.kill(pid, signal.SIGTERM)
    os.remove(pid_file_path)
  except (IOError, OSError):
    logging.exception('Error terminating daemon process')


def main():
  parser = argparse.ArgumentParser(
      description='Launch, or shutdown, a daemon process.')
  parser.add_argument(
      '--action',
      default='daemonize',
      choices=['restart','stop','daemonize'],
      help='What action to take. Both restart and stop attempt to write & read '
      'the pid to a file so it can kill or restart it, while daemonize simply '
      'fires and forgets.')
  parser.add_argument(
      '--pid-file-path',
      type=str,
      default=None,
      help='Path of tmp file to store the daemon\'s pid.')
  parser.add_argument(
      '--', dest='',
      required=False,
      help='Optional delimiter dividing daemonizer options with the command. '
      'This is here to ensure it\'s backwards compatible with the previous '
      'version of daemonizer.')
  parser.add_argument('cmd', help='Command (+ args) to daemonize', nargs='*')
  args = parser.parse_args()

  if args.action == 'restart':
    return restart(args.cmd, args.pid_file_path)
  elif args.action == 'daemonize':
    return daemonize(args.cmd, None)
  elif args.action == 'stop':
    return stop(args.pid_file_path)


if __name__ == '__main__':
  sys.exit(main())
