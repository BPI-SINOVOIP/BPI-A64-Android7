#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Copied from ('https://chromium.googlesource.com/infra/infra.git/+/master/'
# 'bootstrap/remove_orphaned_pycs.py')

import logging
import os
import sys


def main(argv):
  logging.basicConfig(level=logging.DEBUG)
  if len(argv) == 0:
    bootstrap_dir = os.path.dirname(os.path.abspath(__file__))
    build_dir = os.path.dirname(os.path.dirname(bootstrap_dir))
    argv = [build_dir]

  for root in argv:
    # This could take an argument, except gclient DEPS has no good way to pass
    # us an argument, and gclient getcwd() is ../ from our .gclient file. :(
    logging.debug("Cleaning orphaned *.pyc files from: %s" % root)

    for (dirpath, _, filenames) in os.walk(root):
      fnset = set(filenames)
      for filename in filenames:
        if filename.endswith(".pyc") and filename[:-1] not in fnset:
          path = os.path.join(dirpath, filename)
          logging.info("Deleting orphan *.pyc file: %s" % path)
          os.remove(path)


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
