# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Buildbot recipe definition for the official Kasko builder.

To be tested using a command-line like:

  /build/scripts/tools/run_recipe.py syzygy/official_kasko
      revision=0e9f25b1098271be2b096fd1c095d6d907cf86f7
      mastername=master.client.syzygy
      "buildername=Kasko Official"
      slavename=fake_slave
      buildnumber=1

Places resulting output in build/slave/fake_slave.
"""

# Recipe module dependencies.
DEPS = [
  'chromium',
  'gclient',
  'platform',
  'properties',
  'syzygy',
  'trigger',
]


# Valid continuous builders and the configurations they load.
_BUILDERS = {'Kasko Official': ('kasko_official', {})}


from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  """Generates the sequence of steps that will be run by the slave."""
  assert buildername in _BUILDERS

  # Configure the build environment.
  s = api.syzygy
  config, kwargs = _BUILDERS[buildername]
  s.set_config(config, **kwargs)
  api.chromium.set_config(config, **kwargs)
  api.gclient.set_config(config, **kwargs)

  # Clean up any running processes on the slave.
  s.taskkill()

  # Checkout and compile the project.
  s.checkout()
  s.runhooks()
  s.compile()

  # Load and run the unittests.
  unittests = s.read_unittests_gypi()
  s.run_unittests(unittests)

  build_config = api.chromium.c.BUILD_CONFIG

  assert s.c.official_build

  assert build_config == 'Release'
  s.archive_binaries()
  s.upload_kasko_symbols()


def GenTests(api):
  """Generates an end-to-end successful test for each builder."""
  for buildername in _BUILDERS.iterkeys():
    yield api.syzygy.generate_test(api, buildername)
