#!/usr/bin/python
#
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# TODO(robertocn): Remove this file once the calls to it have been refactored in
# favor of calling existing modules (E.g. api.m.json)

"""Saves the text read from stdin to a new temp file, prints its file name.

   Optionally saves the text to an arbitrary file instead, if given as a CLI
   parameter.

   E.g.
     echo "This is a test."|put_temp.py
     echo "This is a test, too."|put_temp.py /tmp/SomeName
"""

import fileinput
import tempfile
import sys


def main(argv):
  if len(argv) < 2:
    file_name = tempfile.mkstemp()[1]
  else:
    file_name = argv[1]
  try:
    out_file = open(file_name, 'w')
  except:
    raise Exception('Could not open file for output.')
  for line in fileinput.input():
    out_file.write(line)
  out_file.close()
  print file_name
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv))
