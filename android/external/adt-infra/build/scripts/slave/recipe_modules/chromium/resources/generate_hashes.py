#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import hashlib
import os
import sys


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument('input_file', type=argparse.FileType('r'))
  parser.add_argument('output_file', type=argparse.FileType('w'))
  args = parser.parse_args(argv)

  file_contents = args.input_file.read()

  hashes = []
  for alg in hashlib.algorithms:
    hashes.append('%s  %s  %s' % (
        alg,
        getattr(hashlib, alg)(file_contents).hexdigest(),
        os.path.basename(args.input_file.name)))

  args.output_file.write('\n'.join(hashes))

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
