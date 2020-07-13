#!/usr/bin/python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import argparse
import os
import re
import sys


TOOLS_DIR = os.path.abspath(os.path.dirname(__file__))
SCRIPTS_DIR = os.path.dirname(TOOLS_DIR)
BASE_DIR = os.path.dirname(SCRIPTS_DIR)

# This adjusts sys.path, so must be done before we import other modules.
if not SCRIPTS_DIR in sys.path:  # pragma: no cover
  sys.path.append(SCRIPTS_DIR)

from common import chromium_utils
from common import filesystem


TEMPLATE_SUBPATH = os.path.join('scripts', 'tools', 'buildbot_tool_templates')
TEMPLATE_DIR = os.path.join(BASE_DIR, TEMPLATE_SUBPATH)


def main(argv, fs):
  args = parse_args(argv)
  return args.func(args, fs)


def parse_args(argv):
  parser = argparse.ArgumentParser()
  subps = parser.add_subparsers()

  subp = subps.add_parser('gen', help=run_gen.__doc__)
  subp.add_argument('master_dirname', nargs=1,
                    help='Path to master config directory (must contain '
                         'a builders.pyl file).')
  subp.set_defaults(func=run_gen)

  subp = subps.add_parser('genall', help=run_gen_all.__doc__)
  subp.set_defaults(func=run_gen_all)

  subp = subps.add_parser('help', help=run_help.__doc__)
  subp.add_argument(nargs='?', action='store', dest='subcommand',
                    help='The command to get help for.')
  subp.set_defaults(func=run_help)

  return parser.parse_args(argv)


def generate(builders_path, fs, print_prefix=''):
  """Generate a new master config."""

  out_dir = fs.dirname(builders_path)
  out_subpath = fs.relpath(out_dir, BASE_DIR)
  values = chromium_utils.ParseBuildersFileContents(
      builders_path,
      fs.read_text_file(builders_path))

  for filename in fs.listfiles(TEMPLATE_DIR):
    template = fs.read_text_file(fs.join(TEMPLATE_DIR, filename))
    contents = _expand(template, values,
                       '%s/%s' % (TEMPLATE_SUBPATH, filename),
                       out_subpath)
    fs.write_text_file(fs.join(out_dir, filename), contents)
    print('%sWrote %s.' % (print_prefix, filename))

  return 0


def run_gen(args, fs):
  """Generate a new master config."""

  master_dirname = args.master_dirname[0]
  builders_path = fs.join(master_dirname, 'builders.pyl')

  if not fs.exists(builders_path):
    print("%s not found" % builders_path, file=sys.stderr)
    return 1

  generate(builders_path, fs)
  return 0


def run_gen_all(args, fs):
  """Generate new master configs for all masters that use builders.pyl."""

  masters_dirs = [
    fs.join(BASE_DIR, 'masters'),
    fs.join(BASE_DIR, '..', 'build_internal', 'masters'),
  ]
  for masters_dir in masters_dirs:
    if not fs.isdir(masters_dir):
      continue
    for master_dir in fs.listdirs(masters_dir):
      if not master_dir.startswith('master.'):
        continue
      builders_path = fs.join(masters_dir, master_dir, 'builders.pyl')
      if fs.isfile(builders_path):
        print('%s:' % master_dir)
        generate(builders_path, fs, print_prefix='  ')
  return 0


def run_help(args, fs):
  """Get help on a subcommand."""

  if args.subcommand:
    return main([args.subcommand, '--help'], fs)
  return main(['--help'], fs)


def _expand(template, values, source, master_subpath):
  try:
    contents = template % values
  except:
    print("Error populating template %s" % source, file=sys.stderr)
    raise
  return _update_generated_file_disclaimer(contents, source, master_subpath)


def _update_generated_file_disclaimer(contents, source, master_subpath):
  pattern = '# This file is used by scripts/tools/buildbot-tool.*'
  replacement = ('# This file was generated from\n'
                 '# %s\n'
                 '# by "../../build/scripts/tools/buildbot-tool gen .".\n'
                 '# DO NOT EDIT BY HAND!\n' % source)
  return re.sub(pattern, replacement, contents)


if __name__ == '__main__':  # pragma: no cover
  sys.exit(main(sys.argv[1:], filesystem.Filesystem()))
