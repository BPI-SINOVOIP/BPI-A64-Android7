#!/usr/bin/python
#
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Wrapper to the legacy zip function, which stages files in a directory.
"""

import json
import os
import stat
import sys

from common import chromium_utils

def main(argv):
  with open(sys.argv[3], 'r') as f:
    zip_file_list = json.load(f)
  (zip_dir, zip_file) = chromium_utils.MakeZip(sys.argv[1],
                                               sys.argv[2],
                                               zip_file_list,
                                               sys.argv[4],
                                               raise_error=True)
  chromium_utils.RemoveDirectory(zip_dir)
  if not os.path.exists(zip_file):
    raise Exception('Failed to make zip package %s' % zip_file)

  # Report the size of the zip file to help catch when it gets too big.
  zip_size = os.stat(zip_file)[stat.ST_SIZE]
  print 'Zip file is %ld bytes' % zip_size

if __name__ == '__main__':
  sys.exit(main(sys.argv))
