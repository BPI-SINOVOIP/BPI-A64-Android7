# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import bisect

def keyed_bisect(range, is_bad):
  """Wrapper for using python's bisection with a generic key function.

  Args:
    range: List with the keys for bisection. Sorted in the order
           good -> bad. It's assumed that range[-1] is a "bad" revision and
           that the revision before range[0] is "good".
    is_bad: Callable that takes a key and returns a boolean indicating
            whether it's good or bad.
  """

  class LazyMap(object):
    def __getitem__(self, i):
      # The function is assumed to return False for good keys and True for bad
      # ones. By initializing bisect with True below, bisection handles the two
      # cases (1) False < True for good keys and (2) True >= True for bad keys.
      return bool(is_bad(range[i]))

  # Initialize with len(range) - 1 to omit retesting range[-1] as we assume
  # it's bad.
  return range[bisect.bisect_left(LazyMap(), True, 0, len(range) - 1)]
