# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This recipe is intended to control all of the GPU related bots:
#   chromium.gpu
#   chromium.gpu.fyi
#   The GPU bots on the chromium.webkit waterfall
#   The GPU bots on the tryserver.chromium.* waterfalls

DEPS = [
  'buildbot',
  'chromium',
  'isolate',
  'json',
  'filter',
  'gpu',
  'path',
  'platform',
  'properties',
  'raw_io',
  'step',
  'test_utils',
]

def RunSteps(api):
  api.gpu.setup()
  api.buildbot.prep()

  # For local testing: pass 'skip_checkout=True' to run_recipe to skip the
  # checkout step. A full checkout via the recipe must have been done
  # previously.
  if not api.properties.get('skip_checkout', False):
    api.gpu.checkout_steps()
  else:
    api.path['checkout'] = api.path['slave_build'].join('src')

  # For local testing: pass 'skip_compile=True' to run_recipe to skip the
  # runhooks and compile steps. A checkout and build via the recipe must have
  # been done previously.
  if not api.properties.get('skip_compile', False):
    api.gpu.compile_steps()

  api.gpu.run_tests(api, api.gpu.get_build_revision(),
                    api.gpu.get_webkit_revision())

def GenTests(api):
  for build_config in ['Release', 'Debug']:
    for plat in ['win', 'mac', 'linux']:
      # Normal builder configuration
      base_name = '%s_%s' % (plat, build_config.lower())
      yield (
        api.test(base_name) +
        api.properties.scheduled(build_config=build_config) +
        api.platform.name(plat)
      )

      # Blink builder configuration
      yield (
        api.test('%s_blink' % base_name) +
        api.properties.scheduled(
            build_config=build_config,
            top_of_tree_blink=True,
            project='webkit'
        ) +
        api.platform.name(plat)
      )

      # Try server configuration
      yield (
        api.test('%s_tryserver' % base_name) +
        api.properties.tryserver(build_config=build_config) +
        api.override_step_data('analyze', api.gpu.analyze_builds_everything) +
        api.platform.name(plat)
      )

  # Test one configuration using git mode.
  yield (
    api.test('mac_release_git') +
    api.properties.git_scheduled(build_config='Release', use_git=True) +
    api.platform.name('mac')
  )

  # Test one trybot configuration with Blink issues.
  yield (
    api.test('mac_release_tryserver_blink') +
    api.properties.tryserver(
        build_config='Release',
        patch_project='blink') +
    api.override_step_data('analyze', api.gpu.analyze_builds_everything) +
    api.platform.name('mac')
  )

  # Test one configuration skipping the checkout.
  yield (
    api.test('mac_release_skip_checkout') +
    api.properties.git_scheduled(
      build_config='Release',
      skip_checkout=True,
      parent_got_revision=10000,
      parent_got_webkit_revision=10001) +
    api.platform.name('mac')
  )

  # Test one configuration skipping the compile.
  yield (
    api.test('mac_release_skip_compile') +
    api.properties.git_scheduled(
      build_config='Release',
      skip_compile=True,
      # These would ordinarily be generated during the build step.
      swarm_hashes=api.gpu.dummy_swarm_hashes,
    ) +
    api.platform.name('mac')
  )

  # Test one Windows configuration on the FYI waterfall.
  yield (
    api.test('win_release_fyi') +
    api.properties.scheduled(
      build_config='Release',
      mastername='chromium.gpu.fyi',
    ) +
    api.platform.name('win')
  )

  # Test one Linux configuration on the FYI waterfall.
  yield (
    api.test('linux_release_fyi') +
    api.properties.scheduled(
      build_config='Release',
      mastername='chromium.gpu.fyi',
    ) +
    api.platform.name('linux')
  )

  yield (
    api.test('killall_gnome_keyring_failure') +
    api.properties.scheduled(
      build_config='Release',
      mastername='chromium.gpu.fyi',
    ) +
    api.platform.name('linux') +
    api.step_data('killall gnome-keyring-daemon', retcode=1)
  )
