# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Buildbot recipe definition for the various Syzygy Smoke Test builder.

To be tested using a command-line like:

  /build/scripts/tools/run_recipe.py syzygy/continuous
      revision=0e9f25b1098271be2b096fd1c095d6d907cf86f7
      mastername=master.client.syzygy
      "buildername=Syzygy Smoke Test"
      slavename=fake_slave
      buildnumber=1

Places resulting output in build/slave/fake_slave. In order for the Smoke Test
builder to run successfully the appropriate gsutil boto credentials must be
placed in build/site_config/.boto. Cloud storage destinations will be prefixed
with 'test/' in order to not pollute the official coverage archives during
testing.
"""

# Recipe module dependencies.
DEPS = [
  'chromium',
  'gclient',
  'platform',
  'properties',
  'syzygy',
]


from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  """Generates the sequence of steps that will be run on the coverage bot."""
  assert buildername == 'Syzygy Smoke Test'

  # Configure the build environment.
  s = api.syzygy
  kwargs = {'BUILD_CONFIG': 'Release'}
  s.set_config('syzygy', **kwargs)
  api.chromium.set_config('syzygy', **kwargs)
  api.gclient.set_config('syzygy', **kwargs)

  # Clean up any running processes on the slave.
  s.taskkill()

  # Checkout and compile the project.
  s.checkout()
  s.runhooks()

  s.download_binaries()
  s.smoke_test()


def GenTests(api):
  """Generates an end-to-end successful test for this builder."""
  yield api.syzygy.generate_test(api, 'Syzygy Smoke Test')
