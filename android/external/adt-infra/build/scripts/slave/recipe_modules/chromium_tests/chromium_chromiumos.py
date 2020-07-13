# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-chromiumos-archive',
    # WARNING: src-side runtest.py is only tested with chromium CQ builders.
    # Usage not covered by chromium CQ is not supported and can break
    # without notice.
    'src_side_runtest_py': True,
  },
  'builders': {
    'Linux ChromiumOS Full': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'app_list_unittests',
        'aura_builder',
        'base_unittests',
        'browser_tests',
        'cacheinvalidation_unittests',
        'chromeos_unittests',
        'components_unittests',
        'compositor_unittests',
        'content_browsertests',
        'content_unittests',
        'crypto_unittests',
        'dbus_unittests',
        'device_unittests',
        'gcm_unit_tests',
        'google_apis_unittests',
        'gpu_unittests',
        'interactive_ui_tests',
        'ipc_tests',
        'jingle_unittests',
        'media_unittests',
        'message_center_unittests',
        'nacl_loader_unittests',
        'net_unittests',
        'ppapi_unittests',
        'printing_unittests',
        'remoting_unittests',
        'sandbox_linux_unittests',
        'sql_unittests',
        'sync_unit_tests',
        'ui_base_unittests',
        'unit_tests',
        'url_unittests',
        'views_unittests',
      ],
      'tests': [
        steps.ArchiveBuildStep(
            'chromium-browser-snapshots',
            gs_acl='public-read',
        ),
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Linux ChromiumOS Builder': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'aura_builder',
      ],
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ChromiumOS Tests (1)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
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
      'parent_buildername': 'Linux ChromiumOS Builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
    'Linux ChromiumOS GN': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'chromeos',
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'enable_swarming': True,
      'testing': {
        'platform': 'linux',
      },
    },

    'Linux ChromiumOS Ozone Builder': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ozone', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'aura_builder',
      ],
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ChromiumOS Ozone Tests (1)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ozone', 'ninja_confirm_noop'],
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
      'parent_buildername': 'Linux ChromiumOS Ozone Builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ChromiumOS Builder (dbg)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'aura_builder',
      ],
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
      'use_isolate': True,
    },
    'Linux ChromiumOS Tests (dbg)(1)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
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
      'parent_buildername': 'Linux ChromiumOS Builder (dbg)',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
    'Linux ChromiumOS GN (dbg)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_PLATFORM': 'chromeos',
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'enable_swarming': True,
      'testing': {
        'platform': 'linux',
      },
    },
  },
}

# Simple Chrome compile-only builders.
for board in ('x86-generic', 'amd64-generic', 'daisy'):
  SPEC['builders']['ChromiumOS %s Compile' % (board,)] = {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['chromeos', 'ninja_confirm_noop'],
    'gclient_config': 'chromium',
    'chromium_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_PLATFORM': 'chromeos',
      'TARGET_CROS_BOARD': board,
    },
    'bot_type': 'builder',
    'compile_targets': [
      'chromiumos_preflight',
    ],
    'testing': {
      'platform': 'linux',
    },
  }
