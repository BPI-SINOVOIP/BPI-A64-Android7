#!/usr/bin/python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This is a small script designed to issue REINDEX TABLE commands to psql."""

import argparse
import os
import sys

# Import 'common.env' to load our Infra PYTHONPATH
sys.path.insert(0, os.path.join(
    os.path.dirname(os.path.realpath(__file__)), os.pardir))
import common.env
common.env.Install()

from common import chromium_utils


def get_database_creds(dbconfig):
  print 'reading dbconfig from %s' % dbconfig
  values = {}
  if os.path.isfile(dbconfig):
    execfile(dbconfig, values)
  if 'password' not in values:
    raise Exception('could not get db password')
  return values


def main():
  parser = argparse.ArgumentParser(
      description='Run a REINDEX TABLE command on postgres.')
  parser.add_argument('directory',
      help='location of the master to reindex.')
  parser.add_argument('--dbconfig-filename', default='.dbconfig',
      help='name of the dbconfig, defaults to %(default)s.')
  parser.add_argument('--prod', action='store_true',
      help='actually execute command instead of just displaying it.')
  args = parser.parse_args()

  filename = chromium_utils.AbsoluteCanonicalPath(os.path.join(
      args.directory, args.dbconfig_filename))
  dbconfig = get_database_creds(filename)

  cmd = ['psql', '-h', 'localhost', '-U', dbconfig['username'],
         '-d', dbconfig['dbname'], '-c',
         'REINDEX TABLE buildrequests;']
  new_env = os.environ.copy()
  new_env['PGPASSWORD'] = dbconfig['password']

  if args.prod:
    return chromium_utils.RunCommand(cmd, env=new_env)
  else:
    print 'Would have run %s.' % cmd
    print 'If this looks good, re-run with --prod.'
    return 0


if __name__ == '__main__':
  sys.exit(main())
