#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Merge output from several runs of the v8 performance runner into one json
file.

Expects paths to result output file listed as arguments. Prints json with the
merged results.
"""


import json
import os
import sys


def main(argv):
  errors = []
  traces = []
  for path in argv:
    assert os.path.exists(path)
    with open(path) as f:
      results = json.loads(f.read())
    for error in results['errors']:
      errors.append(error)
    for trace in results['traces']:
      traces.append(trace)
  print json.dumps({'errors': errors, 'traces': traces}, indent=2)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
