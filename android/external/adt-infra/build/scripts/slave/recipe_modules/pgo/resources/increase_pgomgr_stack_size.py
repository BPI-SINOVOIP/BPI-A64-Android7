#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
Increase the stack size of pgomgr.exe.

This is a temporary workaround for a bug in pgomgr.exe, it has been reported
to Microsoft and this should be fixed in the next update of the toolchain,
we're currently using VS 2013 update 4.
"""

import os
import subprocess
import sys


def IncreasePgomgrStackSize(bin_dir):
  """Increase the stack size of pgomgr.exe."""
  editbin_path = os.path.join(bin_dir, 'editbin.exe')
  pgomgr_path = os.path.join(bin_dir, 'pgomgr.exe')
  # 64 MB of stack should be more than enough!
  stack_size = 64 * 1024 * 1024
  subprocess.check_call([editbin_path, '/STACK:%d' % stack_size, pgomgr_path])
  return 0


def main():
  if len(sys.argv) != 2:
    print sys.stderr, ('The directory containing editbin.exe and the pgomgr.exe'
        ' to patch hasn\'t been specified.')
    return 0

  return IncreasePgomgrStackSize(sys.argv[1])


if __name__ == '__main__':
  sys.exit(main())
