# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Local testing cache for test results.

The purpose of these functions is for the bisector to save some state between
runs. Specifically, it is meant to save the results of running a specific test
on a specific revision so that further bisections requiring them do not trigger
a new test job every time.
"""

import hashlib
import json
import os
import sys

result_set_file = '/tmp/bisect/test_results_cache'


def make_id(*params):  # pragma: no cover
  id_string = json.dumps(params)
  return hashlib.sha1(id_string).hexdigest()


def has_results(name):  # pragma: no cover
  return name in _get_result_set()


def save_results(name, value):  # pragma: no cover
  rs = _get_result_set()
  rs[name] = value
  _write_result_set(rs)


def _get_result_set():  # pragma: no cover
  if os.path.isfile(result_set_file):
    contents = open(result_set_file).read()
    result_set = json.loads(contents)
    return result_set
  else:
    return {}


def _write_result_set(result_set):  # pragma: no cover
  _dir = os.path.dirname(result_set_file)
  if not os.path.exists(_dir):
    os.mkdir(_dir)
  with open(result_set_file, 'w') as of:
    contents = json.dumps(result_set)
    of.write(contents)


def main():  # pragma: no cover
  some_id = make_id('dummy_string')
  if not has_results(some_id):
    save_results(some_id, 'dummy_value')

if __name__ == '__main__':
  sys.exit(main())   # pragma: no cover
