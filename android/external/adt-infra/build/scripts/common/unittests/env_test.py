#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections
import os
import StringIO
import sys
import unittest

# Path to <build>/scripts
sys.path.insert(0, os.path.abspath(os.path.join(
    os.path.dirname(__file__), os.pardir, os.pardir)))
from common import env


class _CommonSystemTestCase(unittest.TestCase):
  MOCK_ABSPATH_PREFIX = '<ABSPATH>@'

  def setUp(self):
    self._orig = {
        'abspath': os.path.abspath,
        'sep': os.sep,
        'pathsep': os.pathsep,
    }

    def mock_os_path_abspath(value):
      if not value.startswith(self.MOCK_ABSPATH_PREFIX):
        value = self.MOCK_ABSPATH_PREFIX + value
      return value
    os.path.abspath = mock_os_path_abspath
    os.pathsep = ':'
    os.sep = '/'

  def tearDown(self):
    os.path.abspath = self._orig['abspath']
    os.sep = self._orig['sep']
    os.pathsep = self._orig['pathsep']


class SplitPathTestCase(_CommonSystemTestCase):

  def testAbsolute(self):
    # NOTE: '/' is enforced as 'os.sep' through '_CommonSystemTestCase'.
    self.assertEqual(
        env.SplitPath('/a/b/c'),
        ['/', 'a', 'b', 'c'])

  def testRelative(self):
    # NOTE: '/' is enforced as 'os.sep' through '_CommonSystemTestCase'.
    self.assertEqual(
        env.SplitPath('a/b/c'),
        ['a', 'b', 'c'])

  def testEmpty(self):
    self.assertEqual(env.SplitPath(''), [])


class PythonPathTestCase(_CommonSystemTestCase):
  def testForcesAbsolutePaths(self):
    path = env.PythonPath([os.path.abspath('a'), 'b'])
    self.assertEqual(list(path), [os.path.abspath(x) for x in ('a', 'b')])

  def testWithDuplicatePaths_OnlyListsEachOnce(self):
    path = env.PythonPath(['a', os.path.abspath('b'), 'a', 'b', 'c'])
    self.assertEqual(list(path), [os.path.abspath(x) for x in ('a', 'b', 'c')])

  def testFlatten(self):
    self.assertEqual(
        env.PythonPath.Flatten('a', ['b', ['c', 'd']],
            env.PythonPath(['e', 'f'])),
        ['a', 'b', 'c', 'd'] + [os.path.abspath(p) for p in ('e', 'f')])

  def testFromPaths(self):
    self.assertEqual(
        env.PythonPath.FromPaths('a', 'b', 'c'),
        env.PythonPath(['a', 'b', 'c']))

  def testFromPathStr(self):
    self.assertEqual(
        env.PythonPath.FromPaths('a', 'b').pathstr,
        '<ABSPATH>@a:<ABSPATH>@b')

  def testAppend(self):
    self.assertEqual(
        env.PythonPath.FromPaths('a', 'b').Append(
            env.PythonPath.FromPaths('c', 'b')),
        env.PythonPath.FromPaths('a', 'b', 'c'))

  def testOverride(self):
    self.assertEqual(
        env.PythonPath.FromPaths('a', 'b').Override(
            env.PythonPath.FromPaths('c', 'b')),
        env.PythonPath.FromPaths('c', 'b', 'a'))

  def testHermetic(self):
    base_path = env.PythonPath.FromPaths('a', 'b')
    self.assertTrue(base_path.IsHermetic())

    path = base_path.Append(os.path.join('python', 'dist-packages'))
    self.assertFalse(path.IsHermetic())
    self.assertEqual(path.GetHermetic(), env.PythonPath.FromPaths('a', 'b'))

  def testInstall(self):
    orig_path = sys.path
    orig_pythonpath = os.environ.get('PYTHONPATH')
    path = env.PythonPath.FromPaths('a', 'b')
    try:
      path.Install()

      self.assertEqual(sys.path, [os.path.abspath(p) for p in ('a', 'b')])
      self.assertEqual(os.environ.get('PYTHONPATH'), '%s:%s' % (
          os.path.abspath('a'), os.path.abspath('b')))
    finally:
      sys.path = orig_path
      env.SetPythonPathEnv(orig_pythonpath)

  def testEnter(self):
    orig_path = sys.path
    orig_pythonpath = os.environ.get('PYTHONPATH')
    path = env.PythonPath.FromPaths('a', 'b')
    with path.Enter():
      self.assertEqual(sys.path, [os.path.abspath(p) for p in ('a', 'b')])
      self.assertEqual(os.environ.get('PYTHONPATH'), '%s:%s' % (
          os.path.abspath('a'), os.path.abspath('b')))

    self.assertEqual(sys.path, orig_path)
    self.assertEqual(os.environ.get('PYTHONPATH'), orig_pythonpath)


class NoEnvPythonPathTestCase(PythonPathTestCase):
  """Run the PythonPathTestCase with no 'PYTHONPATH' set.

  'os.environ' is a pseudo-dictionary that will error if one of its values is
  set to None. This runs the PythonPathTestCase to exercise the library when
  there is no 'PYTHONPATH' variable set.
  """

  def setUp(self):
    self._orig_python_path = os.environ.get('PYTHONPATH')
    env.SetPythonPathEnv(None)
    super(NoEnvPythonPathTestCase, self).setUp()

  def tearDown(self):
    super(NoEnvPythonPathTestCase, self).tearDown()
    env.SetPythonPathEnv(self._orig_python_path)

  def testNoPythonPath(self):
    self.assertIsNone(os.environ.get('PYTHONPATH'))


class SysPythonPathTestCase(_CommonSystemTestCase):

  def setUp(self):
    super(SysPythonPathTestCase, self).setUp()
    self._orig_sys_path = sys.path[:]

  def tearDown(self):
    super(SysPythonPathTestCase, self).tearDown()
    sys.path = self._orig_sys_path

  def testEmptyPath(self):
    sys.path = []
    self.assertEqual(len(env.GetSysPythonPath()), 0)

  def testDuplicatePythonPathElements_OnlyOccurOnce(self):
    sys.path = [os.path.abspath(p) for p in ('a', 'b', 'a', 'c')]
    self.assertEqual(
        list(env.GetSysPythonPath()),
        [os.path.abspath(p) for p in ('a', 'b', 'c')])

  def testPythonPathElements_BecomeAbsolute(self):
    sys.path = ['a', 'b']
    self.assertEqual(
        list(env.GetSysPythonPath()),
        [os.path.abspath(p) for p in ('a', 'b')])

  def testHermetic(self):
    sys.path = ['a', 'b', os.path.join('python', 'site-packages', 'mytool')]
    self.assertEqual(
        env.GetSysPythonPath(hermetic=True),
        env.PythonPath.FromPaths('a', 'b'))


class GetEnvPythonPathTestCase(_CommonSystemTestCase):

  def setUp(self):
    super(GetEnvPythonPathTestCase, self).setUp()
    self._orig_pythonpath = os.environ.get('PYTHONPATH')

  def tearDown(self):
    super(GetEnvPythonPathTestCase, self).tearDown()
    env.SetPythonPathEnv(self._orig_pythonpath)

  def testEmptyPythonPath(self):
    env.SetPythonPathEnv(None)
    self.assertEqual(len(env.GetEnvPythonPath()), 0)

  def testDuplicatePythonPathElements_OnlyOccurOnce(self):
    os.environ['PYTHONPATH'] = 'a:b:a:c'
    self.assertEqual(
        list(env.GetEnvPythonPath()),
        [os.path.abspath(p) for p in ('a', 'b', 'c')])

  def testPythonPathElements_BecomeAbsolute(self):
    os.environ['PYTHONPATH'] = 'a:%s' % (os.path.abspath('b'))
    self.assertEqual(
        list(env.GetEnvPythonPath()),
        [os.path.abspath(p) for p in ('a', 'b')])


class BuildPythonPathIsValidTestCase(unittest.TestCase):

  def testAllBuildPythonPathsExist(self):
    """Checks the real local checkout to assert all paths actually exist."""
    for path in env.GetBuildPythonPath():
      self.assertTrue(os.path.exists(path),
          "Build path does not exist: %s" % (path,))


class InfraPythonPathTestCase(unittest.TestCase):

  def setUp(self):
    self._orig_modules = dict(sys.modules)

  def tearDown(self):
    sys.modules = self._orig_modules

  def testGenerate(self):
    self.assertIsNotNone(env.GetInfraPythonPath(hermetic=True))
    self.assertIsNotNone(env.GetInfraPythonPath(hermetic=False))
    self.assertIsNotNone(env.GetInfraPythonPath(
        master_dir=os.path.join(env.Build, 'masters', 'master.chromium')))

  def testRequestsLoads(self):
    # NOTE: Update this if 'requests' version changes.
    with env.GetInfraPythonPath().Enter():
      import requests
      self.assertEqual(
          env.SplitPath(requests.__file__)[-4:-1],
          ['third_party', 'requests_1_2_3', 'requests'])

  def testTwistedLoads(self):
    # NOTE: Update this if 'twisted' version changes.
    with env.GetInfraPythonPath().Enter():
      import twisted
      self.assertEqual(
          env.SplitPath(twisted.__file__)[-4:-1],
          ['third_party', 'twisted_10_2', 'twisted'])


class InfraPythonPathWithNoBuildInternalTestCase(InfraPythonPathTestCase):

  def setUp(self):
    self._orig_build_internal = env.BuildInternal
    env.BuildInternal = None

  def tearDown(self):
    env.BuildInternal = self._orig_build_internal

  def testNoBuildInternal(self):
    self.assertIsNone(env.BuildInternal)


class TestCommandEcho(_CommonSystemTestCase):

  def testEcho(self):
    args = collections.namedtuple('args', ['output'])(
        output=StringIO.StringIO())
    path = env.PythonPath.FromPaths('a', 'b')
    self.assertEqual(env._Command_Echo(args, path), 0)
    self.assertEqual(args.output.getvalue(), path.pathstr)


class TestCommandPrint(_CommonSystemTestCase):

  def testEcho(self):
    args = collections.namedtuple('args', ['output'])(
        output=StringIO.StringIO())
    path = env.PythonPath.FromPaths('a', 'b')
    self.assertEqual(env._Command_Print(args, path), 0)
    self.assertEqual(args.output.getvalue(), '\n'.join(list(path) + ['']))


if __name__ == '__main__':
  unittest.main()
