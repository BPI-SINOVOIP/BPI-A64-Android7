#!/usr/bin/python
#
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Gets list of revisions between two commits and their commit positions."""

import json
import os
import re
import subprocess
import sys


RE_COMMIT_POSITION = re.compile('(?P<branch>.+)@{#(?P<revision>\d+)}')

RANGE_COMMAND="""
git rev-list %(from)s..%(to)s|xargs -L 1 sh -c '
  REV=$0;
  CP=`git footers --key Cr-Commit-Position $0`;
  printf "%%s:%%s\n" $REV $CP;
'"""


def print_usage(argv):
  usage = '%s <from_rev> <to_rev>' % argv[0]
  usage += '\n where from_rev and to_rev are full commit hashes.'
  print usage


def valid_args(argv):
  if len(argv) < 3 or len(argv[1]) != 40 or len(argv[2]) != 40:
    return False
  try:
    int(argv[1], 16)
    int(argv[2], 16)
  except ValueError:
    return False
  return True


def main(argv):
  if not valid_args(argv):
    print_usage(argv)
    return 1
  command = RANGE_COMMAND % {'from': argv[1], 'to': argv[2]}
  output = subprocess.check_output(command,shell=True)
  rev_list = []
  for line in output.splitlines():
    parts = line.split(':', 1)
    if parts[1]:
      parts[1] = RE_COMMIT_POSITION.match(parts[1]).group('revision')
    rev_list.append(parts)
  print json.dumps(rev_list)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
