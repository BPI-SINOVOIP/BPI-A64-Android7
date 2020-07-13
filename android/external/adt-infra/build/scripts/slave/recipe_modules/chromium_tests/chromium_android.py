# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-android-archive',
  },
  'builders': {
    'Android arm Builder (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'main_builder',
      'compile_targets': [
        'android_builder_tests'
      ],
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
    },

    'Android arm64 Builder (dbg)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
        'TARGET_PLATFORM': 'android',
      },
      'android_config': 'main_builder',
      'compile_targets': [
        'android_builder_tests'
      ],
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
    },

    'Android One': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'parent_buildername': 'Android arm Builder (dbg)',
      'bot_type': 'tester',
      'android_config': 'main_builder',
      'root_devices': True,
      'tests': [
        steps.AndroidInstrumentationTest(
            'AndroidWebViewTest', 'android_webview_test_apk',
            isolate_file_path='android_webview/'
                'android_webview_test_apk.isolate',
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
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android Remoting Tests': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'compile_targets': [
        'remoting_apk',
      ],
      'parent_buildername': 'Android arm Builder (dbg)',
      'bot_type': 'tester',
      'android_config': 'main_builder',
      'root_devices': True,
      'tests': [
        steps.GTestTest('remoting_unittests'),
        steps.AndroidInstrumentationTest(
            'ChromotingTest', 'remoting_test_apk',
            adb_install_apk='Chromoting.apk'),
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android WebView (amp)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'amp_config': 'main_pool',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'parent_buildername': 'Android arm Builder (dbg)',
      'bot_type': 'tester',
      'android_config': 'main_builder',
      'enable_swarming': False,
      'tests': [
        steps.AMPInstrumentationTest(
            test_apk='AndroidWebViewTest',
            apk_under_test='AndroidWebView',
            android_isolate_path=
                'android_webview/android_webview_test_apk.isolate',
            compile_target='android_webview_test_apk',
            device_name=['Nexus 7'], device_os=['4.4.2'],
            fallback_to_local=False),
        steps.AMPInstrumentationTest(
            test_apk='AndroidWebViewTest',
            apk_under_test='AndroidWebView',
            android_isolate_path=
                'android_webview/android_webview_test_apk.isolate',
            compile_target='android_webview_test_apk',
            device_oem=['Motorola', 'motorola'], device_os=['4.4.2'],
            fallback_to_local=False),
        steps.AMPInstrumentationTest(
            test_apk='AndroidWebViewTest',
            apk_under_test='AndroidWebView',
            android_isolate_path=
                'android_webview/android_webview_test_apk.isolate',
            compile_target='android_webview_test_apk',
            device_oem=['Samsung', 'samsung'], device_os=['4.4.2'],
            fallback_to_local=False),
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
  },
}
