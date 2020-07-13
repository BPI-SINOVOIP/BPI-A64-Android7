# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import zlib


DEPS = [
  'chromite',
  'gitiles',
  'json',
  'properties',
]

# Map master name to 'chromite' configuration name.
_MASTER_CONFIG_MAP = {
    'chromiumos.tryserver': {
      'master_config': 'chromiumos_tryserver',
    },
}


# Testing: Tryjob data file JSON.
_TRYJOB_DATA = """
{
  "name": "12345",
  "email": "testauthor@fake.chromium.org",
  "extra_args": [
    "--timeout",
    "14400",
    "--remote-trybot",
    "--remote-version=4"
  ]
}
"""

# JSON string containing sufficient Chromite configuration layout for our test
# configs.
_CHROMITE_CONFIG = {
  '_default': {
    'type': 'undefined',
  },
  '_templates': {
    'full': {
      'type': 'full',
    },
    'paladin': {
      'type': 'paladin',
    },
  },
  'x86-generic-full': {
    '_template': 'full',
  },
  'internal-paladin': {
    '_template': 'paladin',
    'internal': True,
  },
}


def RunSteps(api):
  # The 'cbuildbot' config name to build is the name of the builder.
  #
  # TODO(dnj): After we fully switch to BuildBucket scheduling, load the config
  #            name from the BuildBucket job instead of `cbb_config` build
  #            property. We can't do this yet b/c the job description can
  #            specify multiple configs in one tryjob, so there's no way for us
  #            to know which one we are.
  cbb_config_name = api.properties.get('cbb_config')
  assert cbb_config_name, "No configuration name specified."

  # Apply our generic configuration.
  api.chromite.configure(
      api.properties,
      _MASTER_CONFIG_MAP)
  api.chromite.c.cbb.config = cbb_config_name

  repository = api.properties.get('repository')
  revision = api.properties.get('revision')
  assert repository, "A repository must be specified."
  assert revision, "A revision must be specified."
  assert api.chromite.check_repository('tryjob', repository), (
      "Refusing to query unknown tryjob repository: %s" % (repository,))

  # Load the Chromite configuration for our target.
  api.chromite.checkout_chromite()
  cbb_config = api.chromite.load_config(cbb_config_name)

  # Add parameters specified in the tryjob description.
  tryjob_args = api.properties.get('cbb_extra_args', [])
  if tryjob_args:
    if tryjob_args.startswith('z:'):
      tryjob_args = zlib.decompress(base64.b64decode(tryjob_args[2:]))
    tryjob_args = api.json.loads(tryjob_args)

  # Determine our build directory name based on whether this build is internal
  # or external.
  #
  # We have two checkout options: internal and external. By default we will
  # infer which to use based on the Chromite config. However, the pinned
  # Chromite config may not be up to date. If the value cannot be inferred, we
  # will "quarantine" the build by running it in a separate "etc_master"
  # build root and instructing `cbuildbot` to clobber beforehand.
  #
  # TODO: As the configuration owner, Chromite should be the entity to make the
  # internal/external buildroot decision. A future iteration should add flags
  # to Chromite to inform it of the internal/external build roots on the slave
  # and defer to it to decide which to use based on the config that it is
  # executing.
  if not api.chromite.c.cbb.builddir:
    if cbb_config:
      namebase = 'internal' if cbb_config.get('internal') else 'external'
      api.chromite.c.cbb.builddir = '%s_master' % (namebase,)
    else:
      api.chromite.c.cbb.builddir = 'etc_master'
      api.chromite.c.cbb.clobber = True

  # Run our 'cbuildbot'.
  api.chromite.run(args=tryjob_args)


def GenTests(api):
  # Test a CrOS tryjob.
  yield (
      api.test('external')
      + api.properties(
          mastername='chromiumos.tryserver',
          buildername='full',
          slavename='test',
          repository='https://chromium.googlesource.com/chromiumos/tryjobs.git',
          revision=api.gitiles.make_hash('test'),
          cbb_config='x86-generic-full',
          cbb_extra_args='["--timeout", "14400", "--remote-trybot",'
                         '"--remote-version=4"]',
      )
      + api.chromite.seed_chromite_config(_CHROMITE_CONFIG)
  )

  yield (
      api.test('internal')
      + api.properties(
          mastername='chromiumos.tryserver',
          buildername='paladin',
          slavename='test',
          repository='https://chromium.googlesource.com/chromiumos/tryjobs.git',
          revision=api.gitiles.make_hash('test'),
          cbb_config='internal-paladin',
          cbb_extra_args='["--timeout", "14400", "--remote-trybot",'
                         '"--remote-version=4"]',
      )
      + api.chromite.seed_chromite_config(_CHROMITE_CONFIG)
  )

  # Test a CrOS tryjob with compressed "cbb_extra_args".
  yield (
      api.test('basic_compressed')
      + api.properties(
          mastername='chromiumos.tryserver',
          buildername='full',
          slavename='test',
          repository='https://chromium.googlesource.com/chromiumos/tryjobs.git',
          revision=api.gitiles.make_hash('test'),
          cbb_config='x86-generic-full',
          cbb_extra_args=(
            'z:eJyLVtLVLcnMTc0vLVHSUVAyNDExMAAxdHWLUnPzS1J1S4oqk/JLUITKUouKM'
            '/PzbE2UYgFJaBNI'),
      )
      + api.chromite.seed_chromite_config(_CHROMITE_CONFIG)
  )

  # Test a config that is not registered in Chromite.
  yield (
      api.test('unknown_config')
      + api.properties(
          mastername='chromiumos.tryserver',
          buildername='etc',
          slavename='test',
          repository='https://chromium.googlesource.com/chromiumos/tryjobs.git',
          revision=api.gitiles.make_hash('test'),
          cbb_config='xxx-fakeboard-fakebuild',
          cbb_extra_args='["--timeout", "14400", "--remote-trybot",'
                         '"--remote-version=4"]',
      )
      + api.chromite.seed_chromite_config(_CHROMITE_CONFIG)
  )
