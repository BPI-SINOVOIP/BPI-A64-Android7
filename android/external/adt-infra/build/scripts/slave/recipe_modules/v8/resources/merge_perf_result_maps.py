#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Merge output from several runs of the v8 performance runner into one json
file.

Expects a json file with a mapping of test names to paths to result output
files. Prints json with the merged results.
"""


import json
import os
import sys


def main(argv):
  results_map = {}
  with open(argv[0]) as f:
    results_file_map = json.loads(f.read())
  for test, path in results_file_map.iteritems():
    assert os.path.exists(path)
    with open(path) as f:
      results_map[test] = json.loads(f.read())
  print json.dumps(results_map, indent=2)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
