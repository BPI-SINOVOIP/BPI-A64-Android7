#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Updates BuildBot builder directories to the new 'cbuildbot'-driven naming
scheme.

Classic BuildBot CrOS waterfalls define build directories by composing the
directory name from component parts resembling the target and a final branch
name. Oftentimes, these component parts (and, therefore, the composition) don't
actually match the name of the underlying 'cbuildbot' target.

This presents problems because the build target are fundamentally driven by
their underlying 'cbuildbot' target, but the composition scheme is extremely
arbitrary.

Consequently, BuildBot masters are being migrated to a new, deterministic,
'cbuildbot'-driven naming scheme. A builder building 'cbuildbot' target
<target> and checking Chromite/'cbuildbot' from branch <branch> will use the
builder name: <target>-<branch>. This is universally sustainable across all
waterfalls and ensures that 'cbuildbot' builds are tracked and numbered based
on their underlying 'cbuildbot' target.

This script is intended to be run on a stopped BuildBot master during build
directory migration. It will iterate through each build directory in the current
master naming scheme and rename the classic directories into their new
'cbuildbot'-driven namespace.
"""

import argparse
import collections
import logging
import os
import re
import shutil
import sys

from common import cros_chromite


class UpdateInfo(collections.namedtuple(
    'UpdateInfo',
    ('src', 'cbb_name', 'branch'))):
  """Information about a single directory update action."""

  _STATIC_PERMUTATIONS = {
      'Canary master': 'master-canary',
  }

  _TRANSFORMATIONS = (
      (r'-canary-', r'-release-'),
      (r'-full', r'-release'),
      (r'-pre-flight', r'-pre-flight-branch'),
      (r'(x86|amd64)$', r'\1-generic'),
      (r'^chromium-tot-chromeos-(.+)-asan', r'\1-tot-asan-informational'),
      (r'^chromium-tot-chromeos-(.+)', r'\1-tot-chrome-pfq-informational'),
      (r'^chromium-(.+)-telemetry$', r'\1-telemetry'),
      (r'(.+)-bin$', r'\1'),
  )

  @property
  def dst(self):
    """Constructs the <cbuildbot>-<branch> form."""
    return '%s-%s' % (self.cbb_name, self.branch)

  @classmethod
  def permutations(cls, name):
    """Attempts to permute a legacy BuildBot name into a Chromite target.

    Args:
      name (str): The source name to process and map.
    Yields (str): Various permutations of 'name'.
    """
    # No cbuildbot targets use upper-case letters.
    name = name.lower()

    # If 'name' is already a 'cbuildbot' target, return it unmodified.
    yield name

    # Apply static permutations.
    p = cls._STATIC_PERMUTATIONS.get(name)
    if p:
      yield p

    # Replace 'canary' with 'release'.
    for find, replace in cls._TRANSFORMATIONS:
      name = re.sub(find, replace, name)
      yield name

    # Is 'name' valid if it was a release group?
    if not name.endswith('-group'):
      # We never build 'full' group variants.
      name_group = ('%s-group' % (name,)).replace('-full-', '-release-')
      yield name_group

  @classmethod
  def process(cls, config, name, branches=None, blacklist=None):
    """Construct an UpdateInfo to map a source name.

    This function works by attempting to transform a source name into a known
    'cbuildbot' target name. If successful, it will use that successful
    transformation as validation of the correctness and return an UpdateInfo
    describing the transformation.

    Args:
      config (cros_chromite.ChromiteConfig) The Chromite config instance.
      name (str): The source name to process and map.
      branches (list): A list of valid branches, extracted from 'cros_chromite'.
    Returns (UpdateInfo/None): The constructed UpdateInfo, or None if there was
        no identified mapping.
    """
    def sliding_split_gen():
      parts = name.split('-')
      for i in xrange(len(parts), 0, -1):
        yield '-'.join(parts[:i]), '-'.join(parts[i:])

    logging.debug("Processing candidate name: %s", name)
    candidates = set()
    branch = None
    for orig_name, branch in sliding_split_gen():
      logging.debug("Trying construction: Name(%s), Branch(%s)",
          orig_name, branch)
      if branches and not branch in branches:
        logging.debug("Ignoring branch value '%s'.", branch)
        continue

      # See if we can properly permute the original name.
      for permuted_name in cls.permutations(orig_name):
        if blacklist and any(b in permuted_name for b in blacklist):
          logging.debug("Ignoring blacklisted config name: %s", permuted_name)
          continue
        if permuted_name in config:
          candidates.add(permuted_name)
      if not candidates:
        logging.debug("No 'cbuildbot' config for attempts [%s] branch [%s].",
                      orig_name, branch)
        continue

      # We've found a permutation that matches a 'cbuildbot' target.
      break
    else:
      logging.info("No 'cbuildbot' permutations for [%s].", name)
      return None

    if not branch:
      # We need to do an update to add the branch. Default to 'master'.
      branch = 'master'

    candidates = sorted(candidates)
    for candidate in candidates:
      logging.debug("Identified 'cbuildbot' name [%s] => [%s] branch [%s].",
                    name, candidate, branch)
    return [cls(name, p, branch) for p in candidates]


def main(args):
  """Main execution function.

  Args:
    args (list): Command-line argument array.
  """
  parser = argparse.ArgumentParser()
  parser.add_argument('path', nargs='+', metavar='PATH',
      help='The path to the master directory to process.')
  parser.add_argument('-v', '--verbose', action='count', default=0,
      help='Increase verbosity. Can be specified multiple times.')
  parser.add_argument('-d', '--dry-run', action='store_true',
      help="Print what actions will be taken, but don't modify anything.")
  parser.add_argument('-n', '--names', action='store_true',
      help="If specified, then regard 'path' as directory names to test.")
  parser.add_argument('-B', '--blacklist', action='append', default=[],
      help="Blacklist configs that contain this text.")
  args = parser.parse_args()

  # Select verbosity.
  if args.verbose == 0:
    loglevel = logging.WARNING
  elif args.verbose == 1:
    loglevel = logging.INFO
  else:
    loglevel = logging.DEBUG
  logging.getLogger().setLevel(loglevel)

  # Load all availables Chromite configs. We're going to load ToT.
  config_names = set()
  branches = set()
  for branch in cros_chromite.PINS.iterkeys():
    branches.add(branch)
    config_names.update(cros_chromite.Get(branch=branch).iterkeys())

  # If we're just testing against names, do that.
  if args.names:
    errors = 0
    for n in args.path:
      update_info_list = UpdateInfo.process(config_names, n, branches=branches,
                                            blacklist=args.blacklist)
      if update_info_list:
        for update_info in update_info_list:
          logging.warning("[%s] => [%s]", update_info.src, update_info.dst)
      else:
        logging.warning("No transformation for name [%s].", n)
        errors += 1
    return errors

  # Construct the set of actions to take.
  cbb_already = set()
  unmatched = set()
  multiples = {}
  updates = []
  for path in args.path:
    if not os.path.isdir(path):
      raise ValueError("Supplied master directory is not valid: %s" % (path,))

    seen = set()
    for f in os.listdir(path):
      f_path = os.path.join(path, f)
      if not os.path.isdir(f_path):
        continue

      update_info_list = UpdateInfo.process(config_names, f, branches=branches,
                                            blacklist=args.blacklist)
      if not update_info_list:
        logging.info("No update information for directory [%s]", f)
        unmatched.add(f)
        continue
      elif len(update_info_list) != 1:
        multiples[f] = update_info_list
        continue
      update_info = update_info_list[0]

      # Make sure that we don't stomp on directory names. This shouldn't happen,
      # since the mapping to 'cbuildbot' names is inherently deconflicting, but
      # it's good to assert it just in case.
      update_info_names = set((update_info.src, update_info.dst))
      if update_info_names.intersection(seen):
        logging.error("Updated names intersect with existing names: %s",
            ", ".join(update_info_names.intersection(seen)))
        return 1
      seen.update(update_info_names)

      # We are already in <cbuildbot>-<branch> format, so do nothing.
      if update_info.src == update_info.dst:
        cbb_already.add(update_info.src)
      else:
        updates.append((path, update_info))

  # Execute the updates.
  logging.info("Executing %d updates.", len(updates))
  for master_dir, update_info in updates:
    logging.info("Updating [%s]: [%s] => [%s]", master_dir, update_info.src,
                 update_info.dst)
    if not args.dry_run:
      shutil.move(os.path.join(master_dir, update_info.src),
                  os.path.join(master_dir, update_info.dst))
  logging.info("Updated %d directories.", len(updates))
  if logging.getLogger().isEnabledFor(logging.DEBUG):
    logging.debug("%d directories already matching: %s",
                  len(cbb_already), ', '.join(sorted(cbb_already)))
  if unmatched:
    logging.warning("%d unmatched directories: %s",
                    len(unmatched), ', '.join(sorted(unmatched)))
  if multiples:
    for f in sorted(multiples.iterkeys()):
      logging.warning("Multiple permutations of [%s]: %s\n%s",
                      f, ", ".join(m.dst for m in multiples[f]),
                      '\n'.join('mv %s %s' % (f, m.dst) for m in multiples[f]))
  return 0


if __name__ == '__main__':
  logging.basicConfig()
  sys.exit(main(sys.argv[1:]))
