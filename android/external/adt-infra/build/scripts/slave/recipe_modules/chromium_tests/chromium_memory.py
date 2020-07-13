# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-memory-archive',
    # WARNING: src-side runtest.py is only tested with chromium CQ builders.
    # Usage not covered by chromium CQ is not supported and can break
    # without notice.
    'src_side_runtest_py': True,
  },
  'builders': {
    'Linux ASan LSan Builder': {
      'chromium_config': 'chromium_linux_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      # This doesn't affect the build, but ensures that trybots get the right
      # runtime flags.
      'chromium_apply_config': ['lsan'],
      'bot_type': 'builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ASan LSan Tests (1)': {
      'chromium_config': 'chromium_linux_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      # Enable LSan at runtime. This disables the sandbox in browser tests.
      # http://crbug.com/336218
      'chromium_apply_config': ['lsan'],
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Linux ASan LSan Builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
    },
    'Linux ASan Tests (sandboxed)': {
      'chromium_config': 'chromium_linux_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      # We want to test ASan+sandbox as well, so run browser tests again, this
      # time with LSan disabled.
      'bot_type': 'tester',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Linux ASan LSan Builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
    },
    'Mac ASan 64 Builder': {
      'chromium_config': 'chromium_mac_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {'platform': 'mac'},
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Mac ASan 64 Tests (1)': {
      'chromium_config': 'chromium_mac_asan',
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
      'parent_buildername': 'Mac ASan 64 Builder',
      'testing': {'platform': 'mac'},
      'enable_swarming': True,
    },
    'Linux Chromium OS ASan LSan Builder': {
      'chromium_config': 'chromium_chromiumos_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'chromium_apply_config': ['lsan'],
      'bot_type': 'builder',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux Chromium OS ASan LSan Tests (1)': {
      'chromium_config': 'chromium_chromiumos_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'chromium_apply_config': ['lsan'],
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'parent_buildername': 'Linux Chromium OS ASan LSan Builder',
      'bot_type': 'tester',
      'testing': {'platform': 'linux'},
      'enable_swarming': True,
    },
  },
}
