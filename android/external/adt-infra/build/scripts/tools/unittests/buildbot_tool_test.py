#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for scripts/tools/buildbot_tool.py"""

import StringIO
import sys
import textwrap
import unittest


# This adjusts sys.path, so it must be imported before the other modules.
import test_env

from common import fake_filesystem
from tools import buildbot_tool


FAKE_MASTER_CFG_TEMPLATE = """\
# master_classname
%(master_classname)s

# buildbot_url
%(buildbot_url)s
"""

# This is a fake file; a real builders.pyl contains 'builders'
# and 'slave_pools' entries, but buildbot_tool doesn't care about those.
FAKE_BUILDERS_PYL = """\
{
  "git_repo_url": "git://example.com/example.git",
  "master_base_class": "Master1",
  "master_port": 10999,
  "master_port_alt": 20999,
  "master_type": "waterfall",
  "slave_port": 30999,
  "templates": ["templates"],
}
"""


def _trap_output():
  orig_output = (sys.stdout, sys.stderr)
  sys.stdout = StringIO.StringIO()
  sys.stderr = StringIO.StringIO()
  return orig_output


def _restore_output(orig_output):
  out, err = sys.stdout.getvalue(), sys.stderr.getvalue()
  sys.stdout, sys.stderr = orig_output
  return out, err


def _stub_constants(new_values):
  orig = {}
  for k, v in new_values.items():
    orig[k] = getattr(buildbot_tool, k)
    setattr(buildbot_tool, k, v)
  return orig


def _restore_constants(orig_values):
  for k, v in orig_values.items():
    setattr(buildbot_tool, k, v)


class GenTest(unittest.TestCase):
  def _run_gen(self, builders_pyl, master_cfg=FAKE_MASTER_CFG_TEMPLATE):
    files = {
      '/build/templates/master.cfg': master_cfg,
      '/build/masters/master.test/builders.pyl': builders_pyl,
    }
    fs = fake_filesystem.FakeFilesystem(files=files.copy())

    orig_output = _trap_output()
    orig_constants = _stub_constants({
      'BASE_DIR': '/build',
      'TEMPLATE_SUBPATH': 'templates',
      'TEMPLATE_DIR': '/build/templates',
    })

    try:
      ret = buildbot_tool.main(['gen', '/build/masters/master.test'], fs)
    finally:
      out, err = _restore_output(orig_output)
      _restore_constants(orig_constants)

    return ret, out, err, files, fs


  def test_normal(self):
    ret, out, err, files, fs = self._run_gen(FAKE_BUILDERS_PYL)
    self.assertEqual(ret, 0)
    self.assertEqual(err, '')
    self.assertNotEqual(out, '')
    self.assertEqual(set(fs.files.keys()),
                     set(files.keys() +
                         ['/build/masters/master.test/master.cfg']))

    self.assertMultiLineEqual(
        fs.read_text_file('/build/masters/master.test/master.cfg'),
        textwrap.dedent("""\
            # master_classname
            Test

            # buildbot_url
            https://build.chromium.org/p/test/
            """))

  def test_not_found(self):
    ret, out, err, _, _ = self._run_gen(None)
    self.assertEqual(ret, 1)
    self.assertEqual(out, '')
    self.assertEqual(err,
                     '/build/masters/master.test/builders.pyl not found\n')

  def test_bad_template(self):
    files = {
      '/build/templates/master.cfg': '%(unknown_key)s',
      '/build/masters/master.test/builders.pyl': FAKE_BUILDERS_PYL,
    }
    fs = fake_filesystem.FakeFilesystem(files=files.copy())

    orig_output = _trap_output()
    orig_constants = _stub_constants({
      'BASE_DIR': '/build',
      'TEMPLATE_SUBPATH': 'templates',
      'TEMPLATE_DIR': '/build/templates',
    })

    try:
      self.assertRaises(KeyError,
                        buildbot_tool.main,
                        ['gen', '/build/masters/master.test'],
                        fs)
    finally:
      _restore_output(orig_output)
      _restore_constants(orig_constants)


class HelpTest(unittest.TestCase):
  def test_help(self):
    orig_output = _trap_output()
    fs = fake_filesystem.FakeFilesystem()
    try:
      # We do not care what the output is, just that the commands run.
      self.assertRaises(SystemExit, buildbot_tool.main, ['--help'], fs)
      self.assertRaises(SystemExit, buildbot_tool.main, ['help'], fs)
      self.assertRaises(SystemExit, buildbot_tool.main, ['help', 'gen'], fs)
    finally:
      _restore_output(orig_output)


if __name__ == '__main__':
  unittest.TestCase.maxDiff = None
  unittest.main()
