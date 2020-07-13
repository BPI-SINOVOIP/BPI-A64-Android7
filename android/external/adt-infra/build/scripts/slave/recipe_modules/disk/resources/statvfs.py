# Copyright (c) 2014 The Chromium Authors. All Rights Reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Generates json output of current disk usage.

Argument 1: path mapped to the disk.

Standard output: JSON object with keys:
  capacity (float): disk capacity, in MiB.
  used (float): disk usage, in MiB.
"""

import json
import os
import sys

stats = os.statvfs(sys.argv[1])
data = {
    'capacity': stats.f_blocks * stats.f_frsize,
    'used': (stats.f_blocks - stats.f_bavail) * stats.f_frsize,
}
json.dump(data, sys.stdout)
print ''  # put \n
