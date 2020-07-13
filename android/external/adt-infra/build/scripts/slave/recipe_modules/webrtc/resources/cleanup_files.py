#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script that deletes all files (but not directories) in a given directory."""

import os
import sys


def main(args):
  if not args or len(args) != 1:
    print >> sys.stderr, 'Please specify a single directory as an argument.'
    return 1

  clean_dir = args[0]
  if not os.path.isdir(clean_dir):
    print 'Cannot find any directory at %s. Skipping cleaning.' % clean_dir
    return 0

  for filename in os.listdir(clean_dir):
    file_path = os.path.join(clean_dir, filename)
    if os.path.isfile(file_path):
      try:
        os.remove(file_path)
        print 'Removed %s' % file_path
      except OSError as e:
        # Don't fail if we cannot delete a file.
        print e
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
