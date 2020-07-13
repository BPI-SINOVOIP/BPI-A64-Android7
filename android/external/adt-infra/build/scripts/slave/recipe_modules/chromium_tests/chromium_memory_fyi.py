# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-memory-fyi-archive',
  },
  'builders': {
    'Chromium Linux MSan Builder': {
      'chromium_config': 'chromium_msan',
      'gclient_config': 'chromium',
      'chromium_apply_config': ['prebuilt_instrumented_libraries'],
      'GYP_DEFINES': {
        'msan_track_origins': 2,
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux MSan Tests': {
      'chromium_config': 'chromium_msan',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'Chromium Linux MSan Builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
    },
    'Chromium Linux ChromeOS MSan Builder': {
      'chromium_config': 'chromium_msan',
      'gclient_config': 'chromium',
      'chromium_apply_config': ['prebuilt_instrumented_libraries'],
      'GYP_DEFINES': {
        'msan_track_origins': 2,
        'chromeos': 1
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ChromeOS MSan Tests': {
      'chromium_config': 'chromium_msan',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'Chromium Linux ChromeOS MSan Builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
    },
    'Chromium Linux TSan Builder': {
      'chromium_config': 'chromium_tsan2',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux TSan Tests': {
      'chromium_config': 'chromium_tsan2',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'Chromium Linux TSan Builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
  },
}
