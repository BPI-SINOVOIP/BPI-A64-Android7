# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import DEPS
CONFIG_CTX = DEPS['gclient'].CONFIG_CTX
from recipe_engine.config import BadConf


# TODO(machenbach): This is copied from gclient's config.py and should be
# unified somehow.
def ChromiumSvnSubURL(c, *pieces):
  BASES = ('https://src.chromium.org',
           'svn://svn-mirror.golo.chromium.org')
  return '/'.join((BASES[c.USE_MIRROR],) + pieces)


# TODO(machenbach): Remove the method above in favor of this one.
def ChromiumSvnTrunkURL(c, *pieces):
  BASES = ('https://src.chromium.org/svn/trunk',
           'svn://svn-mirror.golo.chromium.org/chrome/trunk')
  return '/'.join((BASES[c.USE_MIRROR],) + pieces)


@CONFIG_CTX()
def v8(c):
  soln = c.solutions.add()
  soln.name = 'v8'
  soln.url = 'https://chromium.googlesource.com/v8/v8'
  soln.custom_vars = {'chromium_trunk': ChromiumSvnTrunkURL(c)}
  c.got_revision_mapping['v8'] = 'got_revision'
  # Needed to get the testers to properly sync the right revision.
  # TODO(infra): Upload full buildspecs for every build to isolate and then use
  # them instead of this gclient garbage.
  c.parent_got_revision_mapping['parent_got_revision'] = 'got_revision'


@CONFIG_CTX(includes=['v8'])
def dynamorio(c):
  soln = c.solutions.add()
  soln.name = 'dynamorio'
  soln.url = 'https://chromium.googlesource.com/external/dynamorio'


@CONFIG_CTX(includes=['v8'])
def mozilla_tests(c):
  c.solutions[0].custom_deps['v8/test/mozilla/data'] = ChromiumSvnSubURL(
      c, 'chrome', 'trunk', 'deps', 'third_party', 'mozilla-tests')
