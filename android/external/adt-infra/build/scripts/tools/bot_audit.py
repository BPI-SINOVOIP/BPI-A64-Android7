#!/usr/bin/env python
# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Create a dictionary of all Android bots listed by botmap.py."""

import json
import argparse
import os
import re
import sys


class Master:
  def __init__(self, name, bots=None, slaves=None, is_internal=None):
    self.name = name
    self.is_internal = is_internal
    self.bots = bots
    self.slaves = slaves

  def __str__(self):
    if self.is_internal:
      return '%s, %s, %s, %s' % (self.name, len(self.bots), len(self.slaves),
                                 self.is_internal)
    else:
      return '%s, %s, %s' % (self.name, len(self.bots), len(self.slaves))


class Bot:
  def __init__(self, name, master, slave_name, is_internal=None):
    self.name = name
    self.master = master
    self.slave_name = slave_name
    self.is_internal = is_internal

  def __str__(self):
    if self.is_internal:
      return '%s, %s, %s, %s' % (self.name, self.master, self.slave_name,
                                 self.is_internal)
    else:
      return '%s, %s, %s' % (self.name, self.master, self.slave_name)

def get_master_map(bots):
  master_map = {}
  for bot in bots:
    master_name = bot.master
    if master_name in master_map.keys():
      master_map[master_name].append(bot)
    else:
      master_map[master_name] = [bot]
  return master_map

def is_slave_vm(slave_name):
  # Currently I don't know a better way to tell if a bot is a vm.
  return slave_name.startswith('vm') or slave_name.startswith('slave') or \
    slave_name.startswith('skia-android') or slave_name.startswith('skiabot-')

def is_bot_internal(master_name, internal_masters, public_masters):
  if not internal_masters and not public_masters:
    return None
  elif not internal_masters:
    return master_name not in public_masters
  elif not public_masters:
    return master_name in internal_masters
  elif master_name in internal_masters and master_name not in public_masters:
    return True
  elif master_name in public_masters and master_name not in internal_masters:
    return False
  else:
    print 'Warning: %s in bot internal and public master list' % master_name
    return None

def read_master_list(masters_list_file):
  masters = set()
  with open(masters_list_file, 'r') as f:
    master_lines = f.readlines()
    for master_name in master_lines:
      masters.add(master_name.strip())
  return masters

def main():
  parser = argparse.ArgumentParser(
    description='Given a file dump of botmap.py, audits the bots.')
  parser.add_argument('--bots-file', type=str,
                      help='File including dump of botmap.py')
  parser.add_argument('--internal-masters', type=str,
                      help='File that lists the internal masters.')
  parser.add_argument('--public-masters', type=str,
                      help='File that lists the public masters.')
  args = parser.parse_args()
  if not args.bots_file:
    print 'Need to pass --bots-file with list of bots.'
    return 1

  if args.internal_masters:
    internal_masters = read_master_list(args.internal_masters)
  else:
    internal_masters = None
  if args.public_masters:
    public_masters = read_master_list(args.public_masters)
  else:
    public_masters = None

  if not internal_masters and not public_masters:
    print ('Warning: Did not provide internal/public file(s). '
           'Unable to determine if bots are public or internal.')

  bot_lines = []
  with open(args.bots_file, 'r') as f:
    bot_lines = f.readlines()

  internal_bots = []
  public_bots = []
  for bot_line in bot_lines:
    bot_fields = bot_line.split()
    bot_fields = bot_fields[:len(bot_fields)-1]
    bot_name = ' '.join(bot_fields[3:])
    master_name = bot_fields[2]
    is_internal = is_bot_internal(master_name, internal_masters, public_masters)
    if is_internal is None:
      print 'WARNING: Can not process %s, because no internal/public bot info.' \
        % bot_name
    elif is_internal:
      internal_bots.append(Bot(bot_name, master_name, bot_fields[0],
                               is_internal))
    else:
      public_bots.append(Bot(bot_name, master_name, bot_fields[0],
                               is_internal))

  internal_master_map = get_master_map(internal_bots)
  public_master_map = get_master_map(public_bots)

  print 'master_name, # Bare-metal slaves, # VMs'
  print 'Internal bots:'
  for master_name in internal_master_map.keys():
    # print '%s: %s' % (master_name, internal_master_map[master_name])
    internal_bots = internal_master_map[master_name]
    slave_set = set([bot.slave_name for bot in internal_bots])
    bare_metal_slaves = [s for s in slave_set if not is_slave_vm(s)]
    vm_slaves = [s for s in slave_set if is_slave_vm(s)]
    print '%s, %s, %s' % (master_name, len(bare_metal_slaves), len(vm_slaves))
    # for slave in sorted(slave_set):
    #   print '\t%s: bare-metal=%s' % (slave, slave in bare_metal_slaves)

    
  print 'External bots:'
  for master_name in public_master_map.keys():
    # print '%s: %s' % (master_name, public_master_map[master_name])
    public_bots = public_master_map[master_name]
    slave_set = set([bot.slave_name for bot in public_bots])
    bare_metal_slaves = [s for s in slave_set if not is_slave_vm(s)]
    vm_slaves = [s for s in slave_set if is_slave_vm(s)]
    print '%s, %s, %s' % (master_name, len(bare_metal_slaves), len(vm_slaves))
    # for slave in sorted(slave_set):
    #   print '\t%s: bare-metal=%s' % (slave, slave in bare_metal_slaves)

  # trybots = public_master_map['master.tryserver.chromium.linux']
  # for bot in trybots:
  #   print bot


if __name__ == '__main__':
  sys.exit(main())
