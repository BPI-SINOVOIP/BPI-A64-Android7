#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Tests for filter_compilations.py."""

import json
import os
import shutil
import sys
import tempfile
import unittest

import test_env  # pylint: disable=W0403,W0611

from slave.chromium import filter_compilations


def _GetCompilationUnitDictionary(filename):
  compdb_dictionary = {
    'directory': '.',
    'command': 'clang++ -std=c++11 -c %s' % filename,
    'file': filename,
  }
  return compdb_dictionary

def _CreateCompdbFile(compdb_data):
  with tempfile.NamedTemporaryFile(
      suffix='.json', prefix='test', delete=False) as compdb_file:
    compdb_file.write(json.dumps(compdb_data))
  return compdb_file

class FilterCompilationsTest(unittest.TestCase):

  def setUp(self):
    self.compilation_list = []
    for i in xrange(0, 100):
      self.compilation_list.append(
          _GetCompilationUnitDictionary('test_%s.cc' % i))
    self.test_compdb = _CreateCompdbFile(self.compilation_list)

  def tearDown(self):
    os.remove(self.test_compdb.name)

  def _CallFilterCompilations(self, compdb_filter):
    # For simplicity, we will always use the same input and output file.
    # The filter_compilations.py script supports that (and should continue to
    # support it).
    argv = ['--compdb-input=%s' % self.test_compdb.name,
            '--compdb-filter=%s' % compdb_filter,
            '--compdb-output=%s' % self.test_compdb.name]
    filter_compilations.main(argv)

  def _CheckOutputFile(self, expected_output):
    with open(self.test_compdb.name, 'rb') as output_file:
      compdb_output = json.loads(output_file.read())
    self.assertEqual(compdb_output, expected_output)

  def testFilterAll(self):
    self._CallFilterCompilations(self.test_compdb.name)
    self._CheckOutputFile([])

  def testFilterNothing(self):
    empty_filter_file = _CreateCompdbFile([])
    self._CallFilterCompilations(empty_filter_file.name)
    os.remove(empty_filter_file.name)
    self._CheckOutputFile(self.compilation_list)

  def testFilterEvenIndices(self):
    compilation_list_even = []
    for i in xrange(0, 100, 2):
      compilation_list_even.append(
          _GetCompilationUnitDictionary('test_%s.cc' % i))
    compilation_list_odd = []
    for i in xrange(1, 100, 2):
      compilation_list_odd.append(
          _GetCompilationUnitDictionary('test_%s.cc' % i))
    filter_file = _CreateCompdbFile(compilation_list_even)
    self._CallFilterCompilations(filter_file.name)
    os.remove(filter_file.name)
    self._CheckOutputFile(compilation_list_odd)


if __name__ == '__main__':
  unittest.main()
