# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections

from . import steps


_builders = collections.defaultdict(dict)


SPEC = {
  'builders': {},
  'settings': {
    'build_gs_bucket': 'chrome-perf',
  },
}


def _BaseSpec(bot_type, chromium_apply_config, disable_tests,
              gclient_config, platform, target_bits):
  return {
    'bot_type': bot_type,
    'chromium_apply_config' : chromium_apply_config,
    'chromium_config': 'chromium_official',
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': target_bits,
    },
    'disable_tests': disable_tests,
    'gclient_config': gclient_config,
    'testing': {
      'platform': 'linux' if platform == 'android' else platform,
    },
  }


def _BuildSpec(platform, target_bits):
  spec = _BaseSpec(
      bot_type='builder',
      chromium_apply_config=['mb', 'chromium_perf', 'goma_hermetic_fallback'],
      disable_tests=True,
      gclient_config='chromium',
      platform=platform,
      target_bits=target_bits)

  if platform == 'android':
    spec['chromium_apply_config'].append('android')
    spec['chromium_config_kwargs']['TARGET_ARCH'] = 'arm'
    spec['gclient_apply_config'] = ['android', 'perf']
  else:
    spec['compile_targets'] = ['chromium_builder_perf']
    spec['gclient_apply_config'] = ['chrome_internal']

  return spec


def _TestSpec(parent_builder, perf_id, platform, target_bits, max_battery_temp,
              shard_index, num_host_shards, num_device_shards):
  spec = _BaseSpec(
      bot_type='tester',
      chromium_apply_config=[],
      disable_tests=platform == 'android',
      gclient_config='perf',
      platform=platform,
      target_bits=target_bits)

  spec['parent_buildername'] = parent_builder
  spec['perf-id'] = perf_id
  spec['results-url'] = 'https://chromeperf.appspot.com'
  spec['tests'] = [
    steps.DynamicPerfTests(perf_id, platform, target_bits, max_battery_temp,
                           num_device_shards, num_host_shards, shard_index),
  ]

  if platform == 'android':
    spec['android_config'] = 'perf'
    spec['chromium_config_kwargs']['TARGET_PLATFORM'] = 'android'
    spec['gclient_apply_config'] = ['android']
  else:
    spec['test_generators'] = [steps.generate_script]
    spec['test_spec_file'] = 'chromium.perf.json'

  return spec


def _AddBuildSpec(name, platform, target_bits=64):
  SPEC['builders'][name] = _BuildSpec(platform, target_bits)
  assert target_bits not in _builders[platform]
  _builders[platform][target_bits] = name


def _AddTestSpec(name, perf_id, platform, target_bits=64,
                 max_battery_temp=350, num_host_shards=1, num_device_shards=1):
  parent_builder = _builders[platform][target_bits]
  if num_host_shards > 1:
    for shard_index in xrange(num_host_shards):
      builder_name = '%s (%d)' % (name, shard_index + 1)
      SPEC['builders'][builder_name] = _TestSpec(
          parent_builder, perf_id, platform, target_bits, max_battery_temp,
          shard_index, num_host_shards, num_device_shards)
  else:
    SPEC['builders'][name] = _TestSpec(
        parent_builder, perf_id, platform, target_bits, max_battery_temp,
        0, num_host_shards, num_device_shards)


_AddBuildSpec('Android Builder', 'android', target_bits=32)
_AddBuildSpec('Android arm64 Builder', 'android')
_AddBuildSpec('Win Builder', 'win', target_bits=32)
_AddBuildSpec('Win x64 Builder', 'win')
_AddBuildSpec('Mac Builder', 'mac')
_AddBuildSpec('Linux Builder', 'linux')


_AddTestSpec('Android Galaxy S5 Perf', 'android-galaxy-s5', 'android',
             target_bits=32, num_device_shards=7, num_host_shards=3)
_AddTestSpec('Android Nexus5 Perf', 'android-nexus5', 'android',
             target_bits=32, num_device_shards=7, num_host_shards=2)
_AddTestSpec('Android Nexus6 Perf', 'android-nexus6', 'android',
             target_bits=32, num_device_shards=7, num_host_shards=2)
_AddTestSpec('Android Nexus7v2 Perf', 'android-nexus7v2', 'android',
             target_bits=32, num_device_shards=8)
_AddTestSpec('Android Nexus9 Perf', 'android-nexus9', 'android',
             num_device_shards=8)
_AddTestSpec('Android One Perf', 'android-one', 'android',
             target_bits=32, num_device_shards=7, num_host_shards=2)


_AddTestSpec('Win 10 Perf', 'chromium-rel-win10', 'win',
             num_host_shards=5)
_AddTestSpec('Win 8 Perf', 'chromium-rel-win8-dual', 'win',
             num_host_shards=5)
_AddTestSpec('Win 7 Perf', 'chromium-rel-win7-dual', 'win',
             target_bits=32, num_host_shards=5)
_AddTestSpec('Win 7 x64 Perf', 'chromium-rel-win7-x64-dual', 'win',
             num_host_shards=5)
_AddTestSpec('Win 7 ATI GPU Perf', 'chromium-rel-win7-gpu-ati', 'win',
             num_host_shards=5)
_AddTestSpec('Win 7 Intel GPU Perf', 'chromium-rel-win7-gpu-intel', 'win',
             num_host_shards=5)
_AddTestSpec('Win 7 Nvidia GPU Perf', 'chromium-rel-win7-gpu-nvidia', 'win',
             num_host_shards=5)
_AddTestSpec('Win 7 Low-End Perf', 'chromium-rel-win7-single', 'win',
             target_bits=32, num_host_shards=2)
_AddTestSpec('Win XP Perf', 'chromium-rel-xp-dual', 'win',
             target_bits=32, num_host_shards=5)


_AddTestSpec('Mac 10.10 Perf', 'chromium-rel-mac10', 'mac',
             num_host_shards=5)
_AddTestSpec('Mac 10.9 Perf', 'chromium-rel-mac9', 'mac',
             num_host_shards=5)
_AddTestSpec('Mac Retina Perf', 'chromium-rel-mac-retina', 'mac',
             num_host_shards=5)
_AddTestSpec('Mac HDD Perf', 'chromium-rel-mac-hdd', 'mac',
             num_host_shards=5)


_AddTestSpec('Linux Perf', 'linux-release', 'linux',
             num_host_shards=5)
