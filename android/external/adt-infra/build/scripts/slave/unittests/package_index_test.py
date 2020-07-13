#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Tests for package_index.py."""

import gzip
import hashlib
import json
import os
import shutil
import tempfile
import unittest

import test_env  # pylint: disable=W0403,W0611

from slave.chromium import package_index


TEST_CC_FILE_CONTENT = '#include "test.h"\nint main() {\nreturn 0;\n}\n'
TEST_H_FILE_CONTENT = ('#ifndef TEST_H\n#define TEST_H\n#include <stdio.h>\n'
    '#endif\n')
COMPILE_ARGUMENTS = 'clang++ -fsyntax-only -std=c++11 -c test.cc -o test.o'
INCLUDE_PATH = '/usr/include'


class PackageIndexTest(unittest.TestCase):

  def setUp(self):
    # Create the test.cc and test.h files (not necessarily named like that).
    with tempfile.NamedTemporaryFile(
        suffix='.cc', prefix='test', delete=False) as self.test_cc_file:
      self.test_cc_file.write(TEST_CC_FILE_CONTENT)
    with tempfile.NamedTemporaryFile(
        suffix='.h', prefix='test', delete=False) as self.test_h_file:
      self.test_h_file.write(TEST_H_FILE_CONTENT)
    compdb_dictionary = {
        'directory': '.',
        'command': COMPILE_ARGUMENTS,
        'file': self.test_cc_file.name,
    }
    # Write a compilation database to a file.
    with tempfile.NamedTemporaryFile(
        suffix='.json', delete=False) as self.compdb_file:
      self.compdb_file.write(json.dumps([compdb_dictionary]))

    # Create the test.cc.filepaths file referenced through the compilation
    # database
    with open(self.test_cc_file.name + '.filepaths', 'wb') as filepaths_file:
      filepaths_file.write('\n'.join([self.test_cc_file.name,
                                      self.test_h_file.name,
                                      '%s//stdio.h' % INCLUDE_PATH]))

    self.index_pack = package_index.IndexPack(
        os.path.realpath(self.compdb_file.name))
    self.assertTrue(os.path.exists(self.index_pack.index_directory))
    self.assertTrue(os.path.exists(
        os.path.join(self.index_pack.index_directory, 'files')))
    self.assertTrue(os.path.exists(
        os.path.join(self.index_pack.index_directory, 'units')))

  def tearDown(self):
    if os.path.exists(self.index_pack.index_directory):
      shutil.rmtree(self.index_pack.index_directory)
    os.remove(self.compdb_file.name)
    os.remove(self.test_cc_file.name)
    os.remove(self.test_h_file.name)
    os.remove(self.test_cc_file.name + '.filepaths')

  def _CheckDataFile(self, filename, content):
    filepath = os.path.join(self.index_pack.index_directory, 'files', filename)
    self.assertTrue(os.path.exists(filepath))
    with gzip.open(filepath, 'rb') as data_file:
      actual_content = data_file.read()
    self.assertEquals(content, actual_content)

  def _CheckRequiredInput(self, required_input, filename, content):
    self.assertEquals(required_input['digest'],
                      hashlib.sha256(content).hexdigest())
    self.assertEquals(required_input['size'], len(content))
    self.assertEquals(required_input['path'], filename)

  def testGenerateDataFiles(self):
    self.index_pack._GenerateDataFiles()
    test_cc_file = hashlib.sha256(TEST_CC_FILE_CONTENT).hexdigest() + '.data'
    test_h_file = hashlib.sha256(TEST_H_FILE_CONTENT).hexdigest() + '.data'
    self._CheckDataFile(test_cc_file, TEST_CC_FILE_CONTENT)
    self._CheckDataFile(test_h_file, TEST_H_FILE_CONTENT)

  def testGenerateUnitFiles(self):
    # Setup some dictionaries which are usually filled by _GenerateDataFiles()
    test_cc_file_fullpath = os.path.join('.', self.test_cc_file.name)
    test_h_file_fullpath = os.path.join('.', self.test_h_file.name)
    stdio_fullpath = '%s/stdio.h' % INCLUDE_PATH
    self.index_pack.filehashes = {
        test_cc_file_fullpath: hashlib.sha256(TEST_CC_FILE_CONTENT).hexdigest(),
        test_h_file_fullpath: hashlib.sha256(TEST_H_FILE_CONTENT).hexdigest(),
        stdio_fullpath: hashlib.sha256('').hexdigest()
    }
    self.index_pack.filesizes = {
        test_cc_file_fullpath: len(TEST_CC_FILE_CONTENT),
        test_h_file_fullpath: len(TEST_H_FILE_CONTENT),
        stdio_fullpath: 0
    }
    # Now _GenerateUnitFiles() can be called.
    self.index_pack._GenerateUnitFiles()

    # Because we only called _GenerateUnitFiles(), the index pack directory
    # should only contain the one unit file for the one compilation unit in our
    # test compilation database.
    for root, _, files in os.walk(self.index_pack.index_directory):
      for unit_file_name in files:
        with gzip.open(os.path.join(root, unit_file_name), 'rb') as unit_file:
          unit_file_content = unit_file.read()

        # Assert that the name of the unit file is correct.
        unit_file_hash = hashlib.sha256(unit_file_content).hexdigest()
        self.assertEquals(unit_file_name, unit_file_hash + '.unit')

        # Assert that the json content encodes valid dictionaries.
        compilation_unit_wrapper = json.loads(unit_file_content)
        self.assertEquals(compilation_unit_wrapper['format'], 'grok')
        compilation_unit_dictionary = compilation_unit_wrapper['content']
        self.assertEquals(compilation_unit_dictionary['analysis_target'],
                          self.test_cc_file.name)
        self.assertEquals(compilation_unit_dictionary['cxx_arguments'], {})
        self.assertEquals(compilation_unit_dictionary['output_path'], 'test.o')

        self.assertEquals(len(compilation_unit_dictionary['required_input']),
                          len(self.index_pack.filesizes))
        self._CheckRequiredInput(
            compilation_unit_dictionary['required_input'][0],
            self.test_cc_file.name, TEST_CC_FILE_CONTENT)
        self._CheckRequiredInput(
            compilation_unit_dictionary['required_input'][1],
            self.test_h_file.name, TEST_H_FILE_CONTENT)

        real_compile_arguments = COMPILE_ARGUMENTS.split()[1:]
        self.assertEquals(
            compilation_unit_dictionary['argument'],
            (
                ['-isystem%s' % INCLUDE_PATH] + real_compile_arguments +
                ['-w', '-nostdinc++']
            ))

if __name__ == '__main__':
  unittest.main()
