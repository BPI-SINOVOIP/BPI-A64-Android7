# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Recipe to test the deterministic build.

Waterfall page: https://build.chromium.org/p/chromium.swarm/waterfall

TODO(sebmarchand): This recipe has been moved to the recipe's root directory.
Remove this copy once all the master have been updated.
"""

from recipe_engine.recipe_api import Property
from recipe_engine.types import freeze

DEPS = [
  'bot_update',
  'chromium',
  'chromium_android',
  'gclient',
  'isolate',
  'json',
  'path',
  'platform',
  'properties',
  'python',
  'step',
]

DETERMINISTIC_BUILDERS = freeze({
  'Android deterministic': {
    'chromium_config': 'android',
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
      'TARGET_PLATFORM': 'android',
    },
    'platform': 'linux',
    'targets': ['all'],
  },
  'Linux deterministic': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'platform': 'linux',
    'targets': ['all'],
  },
  'Mac deterministic': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'platform': 'mac',
    'targets': ['all'],
  },
  'Windows deterministic': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'platform': 'win',
    'targets': ['all'],
  },
  'Windows Clang deterministic': {
    'chromium_config': 'chromium_win_clang',
    'gclient_config': 'chromium',
    'platform': 'win',
    'targets': ['all'],
  },
})


def MoveBuildDirectory(api, src_dir, dst_dir):
  api.python.inline('Move %s to %s' % (src_dir, dst_dir),
                    """
                    import os
                    import shutil
                    import sys
                    if os.path.exists(sys.argv[2]):
                      shutil.rmtree(sys.argv[2])
                    shutil.move(sys.argv[1], sys.argv[2])""",
                    args=[src_dir, dst_dir])


def ConfigureChromiumBuilder(api, recipe_config):
  api.chromium.set_config(recipe_config['chromium_config'],
                          **recipe_config.get('chromium_config_kwargs',
                                              {'BUILD_CONFIG': 'Release'}))
  api.chromium.apply_config('chromium_deterministic_build')
  api.gclient.set_config(recipe_config['gclient_config'],
                         **recipe_config.get('gclient_config_kwargs', {}))

  api.chromium.cleanup_temp()

  # Checkout chromium.
  api.bot_update.ensure_checkout(force=True)


def ConfigureAndroidBuilder(api, recipe_config):
  kwargs = {
    'REPO_NAME': 'src',
    'REPO_URL': 'https://chromium.googlesource.com/chromium/src.git',
    'Internal': False,
  }
  kwargs.update(recipe_config.get('chromium_config_kwargs',
                                  {'BUILD_CONFIG': 'Release'}))

  api.chromium_android.configure_from_properties(
      'base_config', **kwargs)
  api.chromium.set_config('base_config', **kwargs)
  api.chromium.apply_config(recipe_config['chromium_config'])

PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  recipe_config = DETERMINISTIC_BUILDERS[buildername]
  enable_isolate = True

  targets = recipe_config.get('targets', ['chromium_swarm_tests'])
  if recipe_config.get('chromium_config_kwargs'):
    target_platform = recipe_config['chromium_config_kwargs'].get(
        'TARGET_PLATFORM')
  else:
    target_platform = recipe_config.get('platform')

  if target_platform in ('linux', 'mac', 'win'):
    ConfigureChromiumBuilder(api, recipe_config)
  elif target_platform is 'android':
    # Disable the tests isolation on Android as it's not supported yet.
    enable_isolate = False
    ConfigureAndroidBuilder(api, recipe_config)
    api.chromium_android.init_and_sync()

  if enable_isolate:
    # Enable test isolation. Modifies GYP_DEFINES used in 'runhooks' below.
    api.isolate.set_isolate_environment(api.chromium.c)

  # Do a first build and move the build artifact to the temp directory.
  api.chromium.runhooks()
  api.chromium.compile(targets, force_clobber=True, name='First build')
  api.isolate.remove_build_metadata()
  if enable_isolate:
    # This archives the results and regenerate the .isolated files.
    api.isolate.isolate_tests(api.chromium.output_dir)
  MoveBuildDirectory(api, str(api.chromium.output_dir),
                     str(api.chromium.output_dir).rstrip('\\/') + '.1')

  # Do the second build and move the build artifact to the temp directory.
  api.chromium.runhooks()
  api.chromium.compile(targets, force_clobber=True, name='Second build')
  api.isolate.remove_build_metadata()
  if enable_isolate:
    # This should be quick if the build is indeed deterministic.
    api.isolate.isolate_tests(api.chromium.output_dir)
  MoveBuildDirectory(api, str(api.chromium.output_dir),
                     str(api.chromium.output_dir).rstrip('\\/') + '.2')

  # Compare the artifacts from the 2 builds, raise an exception if they're
  # not equals.
  # TODO(sebmarchand): Do a smarter comparison.
  api.isolate.compare_build_artifacts(
      str(api.chromium.output_dir).rstrip('\\/') + '.1',
      str(api.chromium.output_dir).rstrip('\\/') + '.2')


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text)


def GenTests(api):
  mastername = 'chromium.swarm'
  for buildername in DETERMINISTIC_BUILDERS:
    test_name = 'full_%s_%s' % (_sanitize_nonalpha(mastername),
                                _sanitize_nonalpha(buildername))
    yield (
      api.test(test_name) +
      api.properties.scheduled() +
      api.properties.generic(buildername=buildername,
                             mastername=mastername) +
      api.platform(DETERMINISTIC_BUILDERS[buildername]['platform'], 32) +
      api.properties(configuration='Release') +
      api.step_data('remove_build_metadata', retcode=1)
    )
