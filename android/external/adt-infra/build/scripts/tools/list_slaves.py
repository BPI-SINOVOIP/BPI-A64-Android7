#!/usr/bin/env python
# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A small maintenance tool to list slaves."""

import json
import optparse
import os
import re
import sys

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__),
    os.pardir)))

from common import chromium_utils


def ProcessShortName(master):
  """Substitutes shortcuts."""
  master = re.sub(r'\bt\b', 'tryserver', master)
  master = re.sub(r'\bc\b', 'chromium', master)
  master = re.sub(r'\bcr\b', 'chrome', master)
  master = re.sub(r'\bco\b', 'chromiumos', master)
  master = re.sub(r'\bcros\b', 'chromeos', master)
  return master


def LoadMaster(slaves, path):
  cur_slaves = chromium_utils.GetSlavesFromMasterPath(path)
  cur_master = os.path.basename(path)
  for cur_slave in cur_slaves:
    cur_slave['mastername'] = cur_master
  slaves.extend(cur_slaves)


def Main(argv):
  usage = """%prog [options]

Note: t is replaced with 'tryserver', 'c' with chromium' and
      co with 'chromiumos', 'cr' with chrome, 'cros' with 'chromeos'."""

  masters_path = chromium_utils.ListMastersWithSlaves()

  masters = [os.path.basename(f) for f in masters_path]
  # Strip off 'master.'
  masters = [re.match(r'(master\.|)(.*)', m).group(2) for m in masters]
  parser = optparse.OptionParser(usage=usage)
  group = optparse.OptionGroup(parser, 'Slaves to process')
  group.add_option('-x', '--master', default=[], action='append',
                   help='Master to use to load the slaves list.')
  group.add_option('-k', '--kind', action='append', default=[],
                   help='Only slaves with a substring present in a builder')
  group.add_option('-b', '--builder', action='append', default=[],
                   help='Only slaves attached to a specific builder')
  group.add_option('--os', action='append', default=[],
                   help='Only slaves using a specific OS')
  group.add_option('--os-version', action='append', default=[],
                   help='Only slaves using a specific OS version')
  group.add_option('-s', '--slave', action='append', default=[])
  group.add_option('--cq',
                   help='Only slaves used by specific CQ (commit queue) config')
  parser.add_option_group(group)
  group = optparse.OptionGroup(parser, 'Output format')
  group.add_option('-n', '--name', default='host',
                   dest='fmt', action='store_const', const='host',
                   help='Output slave hostname')
  group.add_option('-r', '--raw',
                   dest='fmt', action='store_const', const='raw',
                   help='Output all slave info')
  group.add_option('-t', '--assignment',
                   dest='fmt', action='store_const', const='assignment',
                   help='Output slave tasks too')
  group.add_option('', '--botmap',
                   dest='fmt', action='store_const', const='botmap',
                   help='Output botmap style')
  group.add_option('-w', '--waterfall',
                   dest='fmt', action='store_const', const='waterfall',
                   help='Output slave master and tasks')
  group.add_option('', '--json',
                   dest='fmt', action='store_const', const='json',
                   help='Output slave list as JSON')
  parser.add_option_group(group)
  options, args = parser.parse_args(argv)
  if args:
    parser.error('Unknown argument(s): %s\n' % args)

  slaves = []

  if not options.master:
    # Populates by default with every master with a twistd.pid, thus has
    # been started.
    for m_p in masters_path:
      if os.path.isfile(os.path.join(m_p, 'twistd.pid')):
        LoadMaster(slaves, m_p)
  else:
    for master in options.master:
      if master == 'allcros':
        for m in (m for m in masters if (m.startswith('chromeos') or
                                         m.startswith('chromiumos'))):
          LoadMaster(slaves, masters_path[masters.index(m)])
      elif master == 'all':
        for m_p in masters_path:
          LoadMaster(slaves, m_p)
        slaves.sort(key=lambda x: (x['mastername'], x.get('hostname')))
      else:
        if not master in masters:
          master = ProcessShortName(master)
        if not master in masters:
          parser.error('Unknown master \'%s\'.\nChoices are: %s' % (
            master, ', '.join(masters)))
        LoadMaster(slaves, masters_path[masters.index(master)])

  if options.kind:
    def kind_interested_in_any(builders):
      if isinstance(builders, basestring):
        return any(builders.find(k) >= 0 for k in options.kind)
      return any(any(x.find(k) >= 0 for k in options.kind) for x in builders)
    slaves = [s for s in slaves if kind_interested_in_any(s.get('builder'))]

  if options.builder:
    builders = set(options.builder)
    def builder_interested_in_any(x):
      return builders.intersection(set(x))
    slaves = [s for s in slaves
              if builder_interested_in_any(s.get('builder', []))]

  if options.os:
    selected = set(options.os)
    slaves = [s for s in slaves if s.get('os', 'unknown') in selected]

  if options.os_version:
    selected = set(options.os_version)
    slaves = [s for s in slaves if s.get('version', 'unknown') in selected]

  if options.slave:
    selected = set(options.slave)
    slaves = [s for s in slaves if s.get('hostname') in selected]

  if options.cq:
    with open(options.cq) as f:
      cq_config = json.load(f)

    # Handle CQ configs still remaining in the CQ repo (as opposed to project
    # repos, see http://crbug.com/443613 .
    legacy_config = cq_config.get(
        'verifiers_no_patch', {}).get('try_job_verifier')
    if not legacy_config:
      legacy_config = cq_config.get(
          'verifiers', {}).get('try_job_verifier')
    if legacy_config:
      cq_config = {'trybots': legacy_config}

    def is_used_by_cq(slave):
      def get_cq_builders(key):
        return cq_config.get('trybots', {}).get(key, {}).get(
            slave['mastername'].replace('master.', ''), {}).keys()
      cq_builders = set(
          get_cq_builders('launched') + get_cq_builders('triggered'))
      return bool(cq_builders.intersection(set(slave.get('builder', []))))
    slaves = [s for s in slaves if is_used_by_cq(s)]

  if options.fmt == 'json':
    normalized = []
    for s in slaves:
      host = s.get('hostname')
      if host:
        builder = s.get('builder') or []
        if isinstance(builder, basestring):
          builder = [builder]
        assert isinstance(builder, list)
        s['builder'] = sorted(builder)
        normalized.append(s)
    json.dump(
        sorted(normalized, key=lambda s: (s.get('mastername'), s['hostname'])),
        sys.stdout, sort_keys=True, indent=2, separators=(',', ': '))
    sys.stdout.write('\n')
    return

  for s in slaves:
    if options.fmt == 'raw':
      print s
    elif options.fmt == 'assignment':
      print s.get('hostname', 'unknown'), ':', s.get('builder', 'unknown')
    elif options.fmt == 'waterfall':
      print s.get('hostname', 'unknown'), ':', s.get('master', 'unknown'), \
            ':', s.get('builder', 'unknown')
    elif options.fmt == 'master':
      print s.get('hostname', 'unknown'), ':', s.get('mastername', 'unknown'), \
            ':', s.get('builder', 'unknown')
    elif options.fmt == 'botmap':
      host = s.get('hostname')
      if host:
        master = s.get('mastername') or '?'
        slaveos = s.get('os') or '?'
        pathsep = '\\' if s.get('os') == 'win' else '/'
        if 'subdir' in s:
          d = pathsep + 'c' + pathsep + s['subdir']
        else:
          d = pathsep + 'b'
        builders = s.get('builder') or '?'
        if type(builders) is not list:
          builders = [builders]
        for b in sorted(builders):
          print '%-30s %-20s %-35s %-35s %-10s' % (host, d, master, b, slaveos)
    else:
      print s.get('hostname', 'unknown')


if __name__ == '__main__':
  sys.exit(Main(None))
