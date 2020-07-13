# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-fyi-archive',
  },
  'builders': {
     'Linux Builder (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
      'use_isolate': True,
      'enable_swarming': True,
    },
    'Linux Tests (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Linux Builder (ANGLE)',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
    'Linux Builder (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux Tests (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Linux Builder (dbg) (ANGLE)',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },

    'Mac Builder (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop', 'chromium_mac_sdk_10_10'],
      'gclient_config': 'chromium_angle',
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
    'Mac10.8 Tests (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop', 'chromium_mac_sdk_10_10'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'Mac Builder (ANGLE)',
      'testing': {
        'platform': 'mac',
      },
      'enable_swarming': True,
      'swarming_dimensions': {
        'os': 'Mac-10.8',
      },
    },
    'Mac Builder (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop', 'chromium_mac_sdk_10_10'],
      'gclient_config': 'chromium_angle',
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
    'Mac10.8 Tests (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop', 'chromium_mac_sdk_10_10'],
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'Mac Builder (dbg) (ANGLE)',
      'testing': {
        'platform': 'mac',
      },
      'enable_swarming': True,
      'swarming_dimensions': {
        'os': 'Mac-10.8',
      },
    },

    'Win Builder (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'patch_root': 'src/third_party/angle',
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win7 Tests (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Win Builder (ANGLE)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win Builder (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'patch_root': 'src/third_party/angle',
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win7 Tests (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Win Builder (dbg) (ANGLE)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win x64 Builder (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'patch_root': 'src/third_party/angle',
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win7 Tests x64 (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Win x64 Builder (ANGLE)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win x64 Builder (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'patch_root': 'src/third_party/angle',
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Win7 Tests x64 (dbg) (ANGLE)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium_angle',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Win x64 Builder (dbg) (ANGLE)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
  },
}
