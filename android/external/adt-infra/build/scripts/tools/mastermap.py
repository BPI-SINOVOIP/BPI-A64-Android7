#!/usr/bin/env python
# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

r"""Tool for viewing masters, their hosts and their ports.

Has three modes:
  a) In normal mode, simply prints the list of all known masters, sorted by
     hostname, along with their associated ports, for the perusal of the user.
  b) In --audit mode, tests to make sure that no masters conflict/overlap on
     ports (even on different masters) and that no masters have unexpected
     ports (i.e. differences of more than 100 between master, slave, and alt).
     Audit mode returns non-zero error code if conflicts are found. In audit
     mode, --verbose causes it to print human-readable output as well.
  c) In --find mode, prints a set of available ports for the given master
     class.

Ports are well-formed if they follow this spec:
XYYZZ
|| \__The last two digits identify the master, e.g. master.chromium
|\____The second and third digits identify the master host, e.g. master1.golo
\_____The first digit identifies the port type, e.g. master_port

In particular,
X==3: master_port (Web display)
X==4: slave_port (for slave TCP/RCP connections)
X==5: master_port_alt (Alt web display, with "force build" disabled)
The values X==1,2, and 6 are not used due to too few free ports in those ranges.

In all modes, --csv causes the output (if any) to be formatted as
comma-separated values.
"""

import argparse
import json
import os
import sys

# Should be <snip>/build/scripts/tools
SCRIPT_DIR = os.path.abspath(os.path.dirname(__file__))
BASE_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, os.pardir, os.pardir))
sys.path.insert(0, os.path.join(BASE_DIR, 'scripts'))
sys.path.insert(0, os.path.join(BASE_DIR, 'site_config'))

import config_bootstrap
from config_bootstrap import Master
from slave import bootstrap


# These are ports which are likely to be used by another service, or which have
# been officially reserved by IANA.
PORT_BLACKLIST = set([
    # We don't care about reserved ports below 30000, the lowest port we use.
    31457,  # TetriNET
    31620,  # LM-MON
    33434,  # traceroute
    34567,  # EDI service
    35357,  # OpenStack ID Service
    40000,  # SafetyNET p Real-time Industrial Ethernet protocol
    41794,  # Crestron Control Port
    41795,  # Crestron Control Port
    45824,  # Server for the DAI family of client-server products
    47001,  # WinRM
    47808,  # BACnet Building Automation and Control Networks
    48653,  # Robot Raconteur transport
    49151,  # Reserved
    # There are no reserved ports in the 50000-65535 range.
])


PORT_TYPE_MAP = {
  'port': Master.Base.MASTER_PORT,
  'slave_port': Master.Base.SLAVE_PORT,
  'alt_port': Master.Base.MASTER_PORT_ALT,
}


# A map of (full) master host to master class used in 'get_master_class'
# lookup.
MASTER_HOST_MAP = dict((m.master_host, m)
                       for m in Master.get_base_masters())


def get_args():
  """Process command-line arguments."""
  parser = argparse.ArgumentParser(
      description='Tool to list all masters along with their hosts and ports.')

  parser.add_argument(
      '-l', '--list', action='store_true', default=False,
      help='Output a list of all ports in use by all masters. Default behavior'
           ' if no other options are given.')
  parser.add_argument(
      '--sort-by', action='store',
      help='Define the primary key by which rows are sorted. Possible values '
           'are: "port", "alt_port", "slave_port", "host", and "name". Only '
           'one value is allowed (for now).')
  parser.add_argument(
      '--find', action='store', metavar='NAME',
      help='Outputs three available ports for the given master class.')
  parser.add_argument(
      '--audit', action='store_true', default=False,
      help='Output conflict diagnostics and return an error code if '
           'misconfigurations are found.')
  parser.add_argument(
      '--presubmit', action='store_true', default=False,
      help='The same as --audit, but prints no output. Overrides all other '
           'options.')

  parser.add_argument(
      '-f', '--format', choices=['human', 'csv', 'json'],
      default='human', help='Print output in the given format')
  parser.add_argument(
      '--full-host-names', action='store_true', default=False,
      help='Refrain from truncating the master host names')

  opts = parser.parse_args()

  opts.verbose = True

  if not (opts.find or opts.audit or opts.presubmit):
    opts.list = True

  if opts.presubmit:
    opts.list = False
    opts.audit = True
    opts.find = False
    opts.verbose = False

  return opts


def getint(string):
  """Try to parse an int (port number) from a string."""
  try:
    ret = int(string)
  except ValueError:
    ret = 0
  return ret


def print_columns_human(lines, verbose):
  """Given a list of lists of tokens, pretty prints them in columns.

  Requires all lines to have the same number of tokens, as otherwise the desired
  behavior is not clearly defined (i.e. which columns should be left empty for
  shorter lines?).
  """

  for line in lines:
    assert len(line) == len(lines[0])

  num_cols = len(lines[0])
  format_string = ''
  for col in xrange(num_cols - 1):
    col_width = max(len(str(line[col])) for line in lines) + 1
    format_string += '%-' + str(col_width) + 's '
  format_string += '%s'

  if verbose:
    for line in lines:
      print format_string % tuple(line)


def print_columns_csv(lines, verbose):
  """Given a list of lists of tokens, prints them as comma-separated values.

  Requires all lines to have the same number of tokens, as otherwise the desired
  behavior is not clearly defined (i.e. which columns should be left empty for
  shorter lines?).
  """

  for line in lines:
    assert len(line) == len(lines[0])

  if verbose:
    for line in lines:
      print ','.join(str(t) for t in line)
    print '\n'


def extract_columns(spec, data):
  """Transforms some data into a format suitable for print_columns_...

  The data is a list of anything, to which the spec functions will be applied.

  The spec is a list of tuples representing the column names
  and how to the column from a row of data.  E.g.

  [ ('Master', lambda m: m['name']),
    ('Config Dir', lambda m: m['dirname']),
    ...
  ]
  """

  lines = [ [ s[0] for s in spec ] ]  # Column titles.

  for item in data:
    lines.append([ s[1](item) for s in spec ])
  return lines



def field(name):
  """Returns a function that extracts a particular field of a dictionary."""
  return lambda d: d[name]


def master_map(masters, output, opts):
  """Display a list of masters and their associated hosts and ports."""

  host_key = 'host' if not opts.full_host_names else 'fullhost'

  output([ ('Master', field('name')),
           ('Config Dir', field('dirname')),
           ('Host', field(host_key)),
           ('Web port', field('port')),
           ('Slave port', field('slave_port')),
           ('Alt port', field('alt_port')),
           ('URL', field('buildbot_url')) ],
         masters)


def get_master_class(master):
  return MASTER_HOST_MAP.get(master['fullhost'])


def get_master_port(master):
  master_class = get_master_class(master)
  if not master_class:
    return None
  return '%02d' % (master_class.master_port_base,)


def master_audit(masters, output, opts):
  """Check for port conflicts and misconfigurations on masters.

  Outputs lists of masters whose ports conflict and who have misconfigured
  ports. If any misconfigurations are found, returns a non-zero error code.
  """

  # Return value. Will be set to 1 the first time we see an error.
  ret = 0

  # Look for masters using the wrong ports for their port types.
  bad_port_masters = []
  for master in masters:
    for port_type, port_digit in PORT_TYPE_MAP.iteritems():
      if not str(master[port_type]).startswith(str(port_digit)):
        ret = 1
        bad_port_masters.append(master)
        break
  output([ ('Masters with misconfigured ports based on port type',
            field('name')) ],
         bad_port_masters)

  # Look for masters using the wrong ports for their hostname.
  bad_host_masters = []
  for master in masters:
    digits = get_master_port(master)
    if digits:
      for port in PORT_TYPE_MAP.iterkeys():
        if str(master[port])[1:3] != digits:
          ret = 1
          bad_host_masters.append(master)
          break
  output([ ('Masters with misconfigured ports based on hostname',
            field('name')) ],
         bad_host_masters)

  # Look for masters configured to use the same ports.
  web_ports = {}
  slave_ports = {}
  alt_ports = {}
  all_ports = {}
  for master in masters:
    web_ports.setdefault(master['port'], []).append(master)
    slave_ports.setdefault(master['slave_port'], []).append(master)
    alt_ports.setdefault(master['alt_port'], []).append(master)

    for port_type in ('port', 'slave_port', 'alt_port'):
      all_ports.setdefault(master[port_type], []).append(master)

  # Check for blacklisted ports.
  blacklisted_ports = []
  for port, lst in all_ports.iteritems():
    if port in PORT_BLACKLIST:
      ret = 1
      for m in lst:
        blacklisted_ports.append(
            { 'port': port, 'name': m['name'], 'host': m['host'] })
  output([ ('Blacklisted port', field('port')),
           ('Master', field('name')),
           ('Host', field('host')) ],
         blacklisted_ports)

  # Check for conflicting web ports.
  conflicting_web_ports = []
  for port, lst in web_ports.iteritems():
    if len(lst) > 1:
      ret = 1
      for m in lst:
        conflicting_web_ports.append(
            { 'web_port': port, 'name': m['name'], 'host': m['host'] })
  output([ ('Web port', field('web_port')),
           ('Master', field('name')),
           ('Host', field('host')) ],
         conflicting_web_ports)

  # Check for conflicting slave ports.
  conflicting_slave_ports = []
  for port, lst in slave_ports.iteritems():
    if len(lst) > 1:
      ret = 1
      for m in lst:
        conflicting_slave_ports.append(
            { 'slave_port': port, 'name': m['name'], 'host': m['host'] })
  output([ ('Slave port', field('slave_port') ),
           ('Master', field('name')),
           ('Host', field('host')) ],
         conflicting_slave_ports)

  # Check for conflicting alt ports.
  conflicting_alt_ports = []
  for port, lst in alt_ports.iteritems():
    if len(lst) > 1:
      ret = 1
      for m in lst:
        conflicting_alt_ports.append(
            { 'alt_port': port, 'name': m['name'], 'host': m['host'] })
  output([ ('Alt port', field('alt_port')),
           ('Master', field('name')),
           ('Host', field('host')) ],
         conflicting_alt_ports)

  # Look for masters whose port, slave_port, alt_port aren't separated by 10000.
  bad_sep_masters = []
  for master in masters:
    if (getint(master['slave_port']) - getint(master['port']) != 10000 or
        getint(master['alt_port']) - getint(master['slave_port']) != 10000):
      ret = 1
      bad_sep_masters.append(master)
  output([ ('Master', field('name')),
           ('Host', field('host')),
           ('Web port', field('port')),
           ('Slave port', field('slave_port')),
           ('Alt port', field('alt_port')) ],
         bad_sep_masters)

  return ret


def build_port_str(master_class, port_type, digits):
  port_base = PORT_TYPE_MAP[port_type]
  port = '%d%02d%02d' % (port_base, master_class.master_port_base, digits)
  assert len(port) == 5, "Invalid port generated (%s)" % (port,)
  return port


def find_port(master_class_name, masters, output, opts):
  """Finds a triplet of free ports appropriate for the given master."""
  try:
    master_class = getattr(Master, master_class_name)
  except AttributeError:
    raise ValueError('Master class %s does not exist' % master_class_name)

  used_ports = set()
  for m in masters:
    for port in ('port', 'slave_port', 'alt_port'):
      used_ports.add(m.get(port, 0))
  used_ports = used_ports | PORT_BLACKLIST

  def _inner_loop():
    for digits in xrange(0, 100):
      port = build_port_str(master_class, 'port', digits)
      slave_port = build_port_str(master_class, 'slave_port', digits)
      alt_port = build_port_str(master_class, 'alt_port', digits)
      if all([
          int(port) not in used_ports,
          int(slave_port) not in used_ports,
          int(alt_port) not in used_ports]):
        return port, slave_port, alt_port
    return None, None, None
  port, slave_port, alt_port = _inner_loop()

  if not all([port, slave_port, alt_port]):
    raise RuntimeError('Unable to find available ports on host')

  output([ ('Master', field('master_base_class')),
           ('Port', field('master_port')),
           ('Alt port', field('master_port_alt')),
           ('Slave port', field('slave_port')) ],
         [ { 'master_base_class': master_class_name,
           'master_port': port,
           'master_port_alt': alt_port,
           'slave_port': slave_port } ])


def format_host_name(host):
  for suffix in ('.chromium.org', '.corp.google.com'):
    if host.endswith(suffix):
      return host[:-len(suffix)]
  return host


def extract_masters():
  """Extracts the data we want from a collection of possibly-masters."""
  good_masters = []
  for master in config_bootstrap.Master.get_all_masters():
    host = getattr(master, 'master_host', '')
    local_config_path = getattr(master, 'local_config_path', '')
    build_dir = os.path.basename(os.path.abspath(os.path.join(local_config_path,
                                                        os.pardir, os.pardir)))
    is_internal = build_dir == 'build_internal'
    good_masters.append({
        'name': master.__name__,
        'host': format_host_name(host),
        'fullhost': host,
        'port': getattr(master, 'master_port', 0),
        'slave_port': getattr(master, 'slave_port', 0),
        'alt_port': getattr(master, 'master_port_alt', 0),
        'buildbot_url': getattr(master, 'buildbot_url', ''),
        'dirname': os.path.basename(local_config_path),
        'internal': is_internal
    })
  return good_masters


def real_main(include_internal=False):
  opts = get_args()

  bootstrap.ImportMasterConfigs(include_internal=include_internal)

  masters = extract_masters()

  # Define sorting order
  sort_keys = ['host', 'port', 'alt_port', 'slave_port', 'name']
  # Move key specified on command-line to the front of the list
  if opts.sort_by is not None:
    try:
      index = sort_keys.index(opts.sort_by)
    except ValueError:
      pass
    else:
      sort_keys.insert(0, sort_keys.pop(index))

  for key in reversed(sort_keys):
    masters.sort(key=lambda m: m[key])  # pylint: disable=cell-var-from-loop

  def output_csv(spec, data):
    print_columns_csv(extract_columns(spec, data), opts.verbose)
    print

  def output_human(spec, data):
    print_columns_human(extract_columns(spec, data), opts.verbose)
    print

  def output_json(spec, data):
    print json.dumps(data, sort_keys=True, indent=2, separators=(',', ': '))

  output = {
      'csv': output_csv,
      'human': output_human,
      'json': output_json,
    }[opts.format]

  if opts.list:
    master_map(masters, output, opts)

  ret = 0
  if opts.audit or opts.presubmit:
    ret = master_audit(masters, output, opts)

  if opts.find:
    find_port(opts.find, masters, output, opts)

  return ret


def main():
  return real_main(include_internal=False)


if __name__ == '__main__':
  sys.exit(main())
