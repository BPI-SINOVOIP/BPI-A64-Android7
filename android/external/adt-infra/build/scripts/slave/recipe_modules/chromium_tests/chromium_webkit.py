# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import copy

from . import chromium_chromiumos
from . import steps

SPEC = copy.deepcopy(chromium_chromiumos.SPEC)
for b in SPEC['builders'].itervalues():
    b['gclient_apply_config'] = ['blink']

SPEC['settings']['build_gs_bucket'] = 'chromium-webkit-archive'
SPEC['settings']['src_side_runtest_py'] = False

SPEC['builders'].update({
  'WebKit Win Builder': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'bot_type': 'builder',
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit XP': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Win Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win7': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Win Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win10': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Win Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win x64 Builder': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'chromium_apply_config': ['shared_library'],
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Shouldn't be needed once we have 64-bit testers.
      'blink_tests',

      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'bot_type': 'builder_tester',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win Builder (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 32,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'bot_type': 'builder',
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win7 (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 32,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Win Builder (dbg)',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win x64 Builder (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Shouldn't be needed once we have 64-bit testers.
      'blink_tests',

      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'bot_type': 'builder_tester',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac Builder': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'builder',
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.6': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromium_mac_sdk_10_10'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.7': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromium_mac_sdk_10_10'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.8': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromium_mac_sdk_10_10'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.9 (retina)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.9': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromium_mac_sdk_10_10'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.10': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac Builder (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'bot_type': 'builder',
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac10.7 (dbg)': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromium_mac_sdk_10_10'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'bot_type': 'tester',
    'parent_buildername': 'WebKit Mac Builder (dbg)',
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['ninja_confirm_noop'],
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Trusty': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux 32': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux ASAN': {
    'chromium_config': 'chromium_clang',
    'gclient_config': 'chromium',
    'chromium_apply_config': ['asan'],
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/ASANExpectations',
          # ASAN is roughly 8x slower than Release.
          '--time-out-ms', '48000',
          '--options=--enable-sanitizer',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux MSAN': {
    'chromium_config': 'chromium_clang',
    'gclient_config': 'chromium',
    'chromium_apply_config': [
      'prebuilt_instrumented_libraries',
      'msan',
      'msan_full_origin_tracking',
    ],
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/MSANExpectations',
          # Because JS code is run on a simulator, the slowdown in JS-heavy
          # tests will be much higher than MSan's average of 3x.
          '--time-out-ms', '66000',
          '--options=--enable-sanitizer',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'Android Builder': {
    'chromium_config': 'android',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['android', 'blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
      'TARGET_PLATFORM': 'android',
    },
    'android_config': 'main_builder',
    'bot_type': 'builder',
    'testing': {
      'platform': 'linux',
    },
  },
  'WebKit Android (Nexus4)': {
    'chromium_config': 'android',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['android', 'blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
      'TARGET_PLATFORM': 'android',
    },
    'bot_type': 'tester',
    'parent_buildername': 'Android Builder',
    'android_config': 'main_builder',
    'root_devices': True,
    'tests': [
      steps.GTestTest('blink_heap_unittests'),
      steps.GTestTest('webkit_unit_tests'),
      steps.BlinkTest(),
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'testing': {
      'platform': 'linux',
    },
  },
  'WebKit Win Oilpan': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 32,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src\\third_party\\WebKit\\LayoutTests\\OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Win Oilpan (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 32,
    },
    'compile_targets': [
      # TODO(phajdan.jr): Find a way to automatically add crash_service
      # to Windows builds (so that start_crash_service step works).
      'crash_service',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src\\third_party\\WebKit\\LayoutTests\\OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'win',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac Oilpan': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Mac Oilpan (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'mac',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Oilpan': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Oilpan ASAN': {
    'chromium_config': 'chromium_clang',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan', 'asan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/ASANExpectations',
          # ASAN is roughly 8x slower than Release.
          '--time-out-ms', '48000',
          '--options=--enable-sanitizer',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Leak': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/LeakExpectations',
          '--options=--enable-leak-detection',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Oilpan Leak': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanExpectations',
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/LeakExpectations',
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanLeakExpectations',
          '--options=--enable-leak-detection',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
  'WebKit Linux Oilpan (dbg)': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium',
    'gclient_apply_config': ['blink_or_chromium'],
    'chromium_apply_config': ['oilpan'],
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Debug',
      'TARGET_BITS': 64,
    },
    'compile_targets': [
      'blink_tests',
    ],
    'test_generators': [
      steps.generate_gtest,
      steps.generate_script,
    ],
    'tests': [
      steps.BlinkTest(extra_args=[
          '--additional-expectations',
          'src/third_party/WebKit/LayoutTests/OilpanExpectations',
      ]),
    ],
    'testing': {
      'platform': 'linux',
    },
    'enable_swarming': True,
    'use_isolate': True,
  },
})
