# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-linux-archive',
    # WARNING: src-side runtest.py is only tested with chromium CQ builders.
    # Usage not covered by chromium CQ is not supported and can break
    # without notice.
    'src_side_runtest_py': True,
  },
  'builders': {
    'Linux Builder': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'chromium_swarm_tests',
      ],
      'testing': {
        'platform': 'linux',
      },
      'use_isolate': True,
      'enable_swarming': True,
    },
    'Linux Tests': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
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
      'parent_buildername': 'Linux Builder',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
    'Linux Builder (dbg)(32)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'compile_targets': [
        'google_apis_unittests',
        'sync_integration_tests',
      ],
      'testing': {
        'platform': 'linux',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Temporary hack because the binaries are too large to be isolated.
      'GYP_DEFINES': {
        'fastbuild': 2,
      },
    },
    'Linux Tests (dbg)(1)(32)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
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
      'parent_buildername': 'Linux Builder (dbg)(32)',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },

    'Linux Builder (dbg)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
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
    'Linux Tests (dbg)(1)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
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
      'parent_buildername': 'Linux Builder (dbg)',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },

    'Android GN': {
      'chromium_config': 'android',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'android',
        'TARGET_ARCH': 'arm',
      },
      'android_config': 'main_builder',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Android Arm64 Builder (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'arm64_builder',
      'compile_targets': [
        'android_builder_tests'
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Android Builder (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'main_builder',
      'bot_type': 'builder',
      'compile_targets': [
        'cronet_test_instrumentation_apk',
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Android GN (dbg)': {
      'chromium_config': 'android',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_PLATFORM': 'android',
        'TARGET_ARCH': 'arm',
      },
      'android_config': 'main_builder',
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Android Tests (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'bot_type': 'tester',
      'parent_buildername': 'Android Builder (dbg)',
      'android_config': 'main_builder',
      'root_devices': True,
      'tests': [
        steps.AndroidInstrumentationTest(
            'AndroidWebViewTest', 'android_webview_test_apk',
            isolate_file_path='android_webview/android_webview_test_apk.isolate',
            adb_install_apk='AndroidWebView.apk'),
        steps.AndroidInstrumentationTest(
            'ChromePublicTest', 'chrome_public_test_apk',
            isolate_file_path='chrome/chrome_public_test_apk.isolate',
            adb_install_apk='ChromePublic.apk'),
        steps.AndroidInstrumentationTest(
            'ContentShellTest', 'content_shell_test_apk',
            isolate_file_path='content/content_shell_test_apk.isolate',
            adb_install_apk='ContentShell.apk'),
        steps.AndroidInstrumentationTest(
            'ChromeSyncShellTest', 'chrome_sync_shell_test_apk',
            adb_install_apk='ChromeSyncShell.apk'),
        steps.GTestTest('android_webview_unittests'),
        steps.GTestTest(
            'base_unittests',
            android_isolate_path='base/base_unittests.isolate'),
        steps.GTestTest(
            'breakpad_unittests',
            override_compile_targets=['breakpad_unittests_deps'],
            android_isolate_path='breakpad/breakpad_unittests.isolate'),
        steps.GTestTest('cc_unittests'),
        steps.GTestTest(
            'components_browsertests',
            android_isolate_path='components/components_browsertests.isolate'),
        steps.GTestTest(
            'components_unittests',
            android_isolate_path='components/components_unittests.isolate'),
        steps.GTestTest(
            'content_browsertests',
            android_isolate_path='content/content_browsertests.isolate'),
        steps.GTestTest(
            'content_unittests',
            android_isolate_path='content/content_unittests.isolate'),
        steps.GTestTest('device_unittests'),
        steps.GTestTest('events_unittests'),
        steps.GTestTest('gl_tests'),
        steps.GTestTest('gl_unittests'),
        steps.GTestTest('gpu_unittests'),
        steps.GTestTest('ipc_tests'),
        steps.GTestTest(
            'media_unittests',
            android_isolate_path='media/media_unittests.isolate'),
        steps.GTestTest(
            'net_unittests',
            android_isolate_path='net/net_unittests.isolate'),
        steps.GTestTest(
            'sandbox_linux_unittests',
            override_compile_targets=['sandbox_linux_unittests_deps']),
        steps.GTestTest(
            'sql_unittests',
            android_isolate_path='sql/sql_unittests.isolate'),
        steps.GTestTest(
            'sync_unit_tests',
            android_isolate_path='sync/sync_unit_tests.isolate'),
        steps.GTestTest('ui_android_unittests'),
        steps.GTestTest(
            'ui_base_unittests',
            android_isolate_path='ui/base/ui_base_tests.isolate'),
        steps.GTestTest('ui_touch_selection_unittests'),
        steps.GTestTest(
            'unit_tests',
            android_isolate_path='chrome/unit_tests.isolate'),
        steps.AndroidJunitTest('base_junit_tests'),
        steps.AndroidJunitTest('chrome_junit_tests'),
        steps.AndroidJunitTest('components_junit_tests'),
        steps.AndroidJunitTest('content_junit_tests'),
        steps.AndroidJunitTest('junit_unit_tests'),
        steps.AndroidJunitTest('net_junit_tests'),
      ],
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android Builder': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
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
    'Android Tests': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
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
        steps.AndroidInstrumentationTest(
            'AndroidWebViewTest', 'android_webview_test_apk',
            isolate_file_path='android_webview/android_webview_test_apk.isolate',
            adb_install_apk='AndroidWebView.apk'),
        steps.AndroidInstrumentationTest(
            'ChromePublicTest', 'chrome_public_test_apk',
            isolate_file_path='chrome/chrome_public_test_apk.isolate',
            adb_install_apk='ChromePublic.apk'),
        steps.AndroidInstrumentationTest(
            'ContentShellTest', 'content_shell_test_apk',
            isolate_file_path='content/content_shell_test_apk.isolate',
            adb_install_apk='ContentShell.apk'),
        steps.AndroidInstrumentationTest(
            'ChromeSyncShellTest', 'chrome_sync_shell_test_apk',
            adb_install_apk='ChromeSyncShell.apk'),
        steps.GTestTest('android_webview_unittests'),
        steps.GTestTest(
            'base_unittests',
            android_isolate_path='base/base_unittests.isolate'),
        steps.GTestTest(
            'breakpad_unittests',
            override_compile_targets=['breakpad_unittests_deps'],
            android_isolate_path='breakpad/breakpad_unittests.isolate'),
        steps.GTestTest('cc_unittests'),
        steps.GTestTest(
            'components_browsertests',
            android_isolate_path='components/components_browsertests.isolate'),
        steps.GTestTest(
            'components_unittests',
            android_isolate_path='components/components_unittests.isolate'),
        steps.GTestTest(
            'content_browsertests',
            android_isolate_path='content/content_browsertests.isolate'),
        steps.GTestTest(
            'content_unittests',
            android_isolate_path='content/content_unittests.isolate'),
        steps.GTestTest('device_unittests'),
        steps.GTestTest('events_unittests'),
        steps.GTestTest('gl_tests'),
        steps.GTestTest('gl_unittests'),
        steps.GTestTest('gpu_unittests'),
        steps.GTestTest('ipc_tests'),
        steps.GTestTest(
            'media_unittests',
            android_isolate_path='media/media_unittests.isolate'),
        steps.GTestTest(
            'net_unittests',
            android_isolate_path='net/net_unittests.isolate'),
        steps.GTestTest(
            'sandbox_linux_unittests',
            override_compile_targets=['sandbox_linux_unittests_deps']),
        steps.GTestTest(
            'sql_unittests',
            android_isolate_path='sql/sql_unittests.isolate'),
        steps.GTestTest(
            'sync_unit_tests',
            android_isolate_path='sync/sync_unit_tests.isolate'),
        steps.GTestTest('ui_android_unittests'),
        steps.GTestTest(
            'ui_base_unittests',
            android_isolate_path='ui/base/ui_base_tests.isolate'),
        steps.GTestTest('ui_touch_selection_unittests'),
        steps.GTestTest(
            'unit_tests',
            android_isolate_path='chrome/unit_tests.isolate'),
        steps.AndroidJunitTest('base_junit_tests'),
        steps.AndroidJunitTest('chrome_junit_tests'),
        steps.AndroidJunitTest('components_junit_tests'),
        steps.AndroidJunitTest('content_junit_tests'),
        steps.AndroidJunitTest('junit_unit_tests'),
        steps.AndroidJunitTest('net_junit_tests'),
      ],
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android Clang Builder (dbg)': {
      'chromium_config': 'android_clang',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'clang_builder',
      'bot_type': 'builder_tester',
      'compile_targets': [
        'android_builder_tests',
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Cast Linux': {
      'chromium_config': 'cast_linux',
      'chromium_apply_config': ['mb', 'ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [
        'cast_shell',
      ],
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Cast Android (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'compile_targets': [
        'cast_shell_apk',
      ],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'cast_builder',
      'testing': {
        'platform': 'linux',
      },
    },
  },
}
