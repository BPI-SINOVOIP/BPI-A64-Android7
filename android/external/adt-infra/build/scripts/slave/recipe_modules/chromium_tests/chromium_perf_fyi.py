# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps


RESULTS_URL = 'https://chromeperf.appspot.com'


def _Spec(platform, parent_builder, perf_id, index, num_shards, target_bits):
  if platform == 'android':
    return {
      'disable_tests': True,
      'bot_type': 'tester',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': target_bits,
        'TARGET_PLATFORM': 'android',
      },
      'gclient_config': 'perf',
      'gclient_apply_config': ['android'],
      'parent_buildername': parent_builder,
      'chromium_config': 'chromium_official',
      'gclient_config': 'perf',
      'android_config': 'perf',
      'testing': {
        'platform': 'linux',
      },
      'perf-id': perf_id,
        'results-url': RESULTS_URL,
      'tests': [],
    }
  else:
    return {
      'disable_tests': True,
      'bot_type': 'tester',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': target_bits,
      },
      'parent_buildername': parent_builder,
      'chromium_config': 'chromium_official',
      'gclient_config': 'perf',
      'testing': {
        'platform': platform,
      },
      'perf-id': perf_id,
      'results-url': RESULTS_URL,
      'tests': [
        steps.DynamicPerfTests(perf_id, platform, target_bits,
                               shard_index=index, num_host_shards=num_shards),
      ],
    }


def _AddBotSpec(name, platform, parent_builder, perf_id, target_bits,
  num_shards, extra_tests=[]):
  if num_shards > 1:
    for i in range(0, num_shards):
      builder_name = "%s (%d)" % (name, i + 1)
      SPEC['builders'][builder_name] = _Spec(platform, parent_builder, perf_id,
        i, num_shards, target_bits)
      SPEC['builders'][builder_name]['tests'].extend(extra_tests)
  else:
    SPEC['builders'][name] = _Spec(platform, parent_builder, perf_id,
        0, 1, target_bits)
    SPEC['builders'][name]['tests'].extend(extra_tests)


SPEC = {
  'settings': {
    'build_gs_bucket': 'chrome-perf',
  },
  'builders': {
    'android_oilpan_builder': {
      'disable_tests': True,
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['oilpan', 'chromium_perf', 'android'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal', 'android', 'perf'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_ARCH': 'arm',
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
    },
    'Linux Oilpan Builder': {
      'disable_tests': True,
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['oilpan', 'chromium_perf'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'chromium_builder_perf',
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Win x64 FYI Builder': {
      'disable_tests': True,
      'chromium_config': 'chromium_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'chromium_builder_perf',
      ],
      'testing': {
        'platform': 'win',
      },
      'chromium_apply_config': ['chromium_perf_fyi']
    },
    'Win Clang Builder': {
      'disable_tests': True,
      'chromium_config': 'chromium_win_clang_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'chromium_builder_perf',
      ],
      'testing': {
        'platform': 'win',
      },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'win-clang-builder')
      },
      'chromium_apply_config': ['chromium_perf_fyi'],
    },
  },
}

_AddBotSpec(
    name='Linux Oilpan Perf',
    platform='linux',
    parent_builder='Linux Oilpan Builder',
    perf_id='linux-oilpan-release',
    target_bits=64,
    num_shards=4)

_AddBotSpec(
    name='Win 7 Intel GPU Perf (Xeon)',
    platform='win',
    parent_builder='Win x64 FYI Builder',
    perf_id='chromium-rel-win7-gpu-intel',
    target_bits=64,
    num_shards=1)

_AddBotSpec(
    name='Win Clang Perf',
    platform='win',
    parent_builder='Win Clang Builder',
    perf_id='chromium-win-clang',
    target_bits=32,
    num_shards=1)

_AddBotSpec(
    name='android_nexus5_oilpan_perf',
    platform='android',
    parent_builder='android_oilpan_builder',
    perf_id='android-nexus5-oilpan',
    target_bits=32,
    num_shards=1,
    extra_tests=[
      steps.DynamicPerfTests('android-nexus5-oilpan', 'android', 32),
    ])
