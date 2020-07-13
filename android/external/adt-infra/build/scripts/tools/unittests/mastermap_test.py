#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for scripts/tools/mastermap.py"""


import json
import unittest

import test_env  # pylint: disable=W0611

from tools import mastermap


class FakeOutput(object):
  def __init__(self):
    self.spec = None
    self.data = None

  def __call__(self, spec, data):
    self.spec = spec
    self.data = data


class FakeOpts(object):
  verbose = None
  full_host_names = False


class HelperTest(unittest.TestCase):

  def test_getint_succeeds(self):
    res = mastermap.getint('10')
    self.assertEquals(res, 10)

  def test_getint_fails(self):
    res = mastermap.getint('foo')
    self.assertEquals(res, 0)

  def test_format_host_name_chromium(self):
    res = mastermap.format_host_name('master1.golo.chromium.org')
    self.assertEquals(res, 'master1.golo')

  def test_format_host_name_corp(self):
    res = mastermap.format_host_name('master.chrome.corp.google.com')
    self.assertEquals(res, 'master.chrome')

  def test_format_host_name_neither(self):
    res = mastermap.format_host_name('mymachine.tld')
    self.assertEquals(res, 'mymachine.tld')


class MapTest(unittest.TestCase):

  def test_column_names(self):
    output = FakeOutput()
    mastermap.master_map([], output, FakeOpts())
    self.assertEqual([ s[0] for s in output.spec ],
        ['Master', 'Config Dir', 'Host', 'Web port', 'Slave port',
          'Alt port', 'URL'])

  def test_exact_output(self):
    output = FakeOutput()
    master = {
      'name': 'Chromium',
      'dirname': 'master.chromium',
      'host': 'master1.golo',
      'fullhost': 'master1.golo.chromium.org',
      'port': 30101,
      'slave_port': 40101,
      'alt_port': 50101,
      'buildbot_url': 'https://build.chromium.org/p/chromium',
    }
    mastermap.master_map([master], output, FakeOpts())
    self.assertEqual(output.data, [ master ])


class FindPortTest(unittest.TestCase):

  @staticmethod
  def _gen_masters(num):
    return [{
        'name': 'Master%d' % i,
        'dirname': 'master.master%d' % i,
        'host': 'master%d.golo' % i,
        'fullhost': 'master%d.golo.chromium.org' % i,
        'port': 20100 + i,
        'slave_port': 30100 + i,
        'alt_port': 40100 + i,
    } for i in xrange(num)]

  def test_master_host(self):
    masters = self._gen_masters(2)
    output = FakeOutput()
    mastermap.find_port('Master1', masters, output, FakeOpts())
    self.assertEquals(output.data[0]['master_base_class'], 'Master1')

  def test_skip_used_ports(self):
    masters = self._gen_masters(5)
    master_class_name = 'Master1'
    output = FakeOutput()
    mastermap.find_port(master_class_name, masters, output, FakeOpts())
    self.assertEquals(output.data, [ {
        u'master_base_class': u'Master1',
        u'master_port': u'20105',
        u'master_port_alt': u'40105',
        u'slave_port': u'30105',
    } ])

  def test_skip_blacklisted_ports(self):
    masters = [{'name': 'Master1', 'fullhost': 'master1.golo.chromium.org'}]
    master_class_name = 'Master1'
    output = FakeOutput()
    _real_blacklist = mastermap.PORT_BLACKLIST
    try:
      mastermap.PORT_BLACKLIST = set(xrange(40000, 50000))  # All alt_ports
      self.assertRaises(RuntimeError, mastermap.find_port,
                        master_class_name, masters, output, FakeOpts())
    finally:
      mastermap.PORT_BLACKLIST = _real_blacklist


class AuditTest(unittest.TestCase):
  # TODO(agable): Actually test this.
  pass


if __name__ == '__main__':
  unittest.TestCase.maxDiff = None
  unittest.main()
