# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

RESULTS_URL = 'https://chromeperf.appspot.com'

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-fyi-archive',
  },
  'builders': {
    'Chromium Mac 10.10': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['chromium_mac_sdk_10_10'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'mac',
      },
    },
    'Linux ARM Cross-Compile': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_ARCH': 'arm',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'GYP_DEFINES': {
        'test_isolation_mode': 'archive',
      },
      # TODO(phajdan.jr): Automatically add _run targets when used.
      'compile_targets': [
        'browser_tests_run',
      ],
      'testing': {
        'platform': 'linux',
      },
      'use_isolate': True,
    },
    'Linux ARM': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_ARCH': 'arm',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'use_isolate': True,
      'testing': {
        'platform': 'linux',
      },
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'Linux Trusty': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'all',
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
    'Linux Trusty (32)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'all',
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
    'Linux Trusty (dbg)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'all',
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
    'Linux Trusty (dbg)(32)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'all',
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
    'Linux V8 API Stability': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['v8_canary', 'with_branch_heads'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'all',
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Print Preview Linux': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'GYP_DEFINES': {
        'component': 'shared_library',
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'linux',
        'TARGET_BITS': 64,
      },
      'tests': [
        steps.PrintPreviewTests(),
      ],
      'bot_type': 'builder_tester',
      'testing': {
        'platform': 'linux',
      },
    },
    'Print Preview Mac': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'GYP_DEFINES': {
        'component': 'shared_library',
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'mac',
        'TARGET_BITS': 64,
      },
      'tests': [
        steps.PrintPreviewTests(),
      ],
      'bot_type': 'builder_tester',
      'testing': {
        'platform': 'mac',
      },
    },
    'Print Preview Win': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'GYP_DEFINES': {
        'component': 'shared_library',
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'win',
        'TARGET_BITS': 32,
      },
      'tests': [
        steps.PrintPreviewTests(),
      ],
      'bot_type': 'builder_tester',
      'testing': {
        'platform': 'win',
      },
    },
    'CFI Linux': {
      'chromium_config': 'chromium_cfi',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'bot_type': 'builder_tester',
      'testing': {
        'platform': 'linux',
      },
    },
    'Mac OpenSSL': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': {
        'use_openssl': '1',
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'mac',
      },
    },
    'Site Isolation Linux': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'dcheck_always_on': '1',
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'content_unittests',
        'content_browsertests',
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
    'Site Isolation Win': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'win',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'dcheck_always_on': '1',
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'content_unittests',
        'content_browsertests',
        'crash_service',
      ],
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'win',
      },
    },
    'Browser Side Navigation Linux': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'content_unittests',
        'content_browsertests',
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
    'ChromiumPractice': {
      'chromium_config': 'chromium',
      'gclient_config': 'blink_merged',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
    },
    'ChromiumPracticeTester': {
      'chromium_config': 'chromium',
      'gclient_config': 'blink_merged',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'tests': [
        steps.BlinkTest(),
      ],
      'bot_type': 'tester',
      'parent_buildername': 'ChromiumPractice',
      'testing': {
        'platform': 'linux',
      },
    },
    'ChromiumPracticeFullTester': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['ninja_confirm_noop'],
      'gclient_config': 'blink_merged',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder_tester',
      'compile_targets': [
        'blink_tests',
        'chromium_swarm_tests',
      ],
      'tests': [
        steps.BlinkTest(),
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
    'CrWinClang': {
      'chromium_config': 'chromium_win_clang_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClang(dbg)': {
      'chromium_config': 'chromium_win_clang',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      # Recipes builds Debug builds with component=shared_library by default.
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang(dbg) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang(dbg)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClang(shared)': {
      'chromium_config': 'chromium_win_clang',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': { 'component': 'shared_library' },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang(shared) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang(shared)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClang64': {
      'chromium_config': 'chromium_win_clang_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang64 tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang64',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClang64(dbg)': {
      'chromium_config': 'chromium_win_clang',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      # Recipes builds Debug builds with component=shared_library by default.
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      # TODO(thakis): Reenable when swarming works in gn http://crbug.com/480053
      #'use_isolate': True,
      #'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang64(dbg) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang64(dbg)',
      'testing': {
        'platform': 'win',
      },
      # TODO(thakis): Reenable when swarming works in gn http://crbug.com/480053
      #'enable_swarming': True,
    },
    'CrWinClang64(dll)': {
      'chromium_config': 'chromium_win_clang',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': { 'component': 'shared_library' },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClang64(dll) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClang64(dll)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClangLLD': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': { 'component': 'shared_library', 'use_lld': 1 },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClangLLD tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClangLLD',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClngLLDdbg': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': { 'component': 'shared_library', 'use_lld': 1 },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClngLLDdbg tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClngLLDdbg',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClangLLD64': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': { 'component': 'shared_library', 'use_lld': 1 },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClangLLD64 tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClangLLD64',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinClngLLD64dbg': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': { 'component': 'shared_library', 'use_lld': 1 },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'CrWinClngLLD64dbg tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinClngLLD64dbg',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinAsan': {
      'chromium_config': 'chromium_win_clang_asan_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      'compile_targets': [ 'chromium_builder_asan' ],
      # add_tests_as_compile_targets not needed for the asan bot, it doesn't
      # build everything.
    },
    'CrWinAsan tester': {
      'chromium_config': 'chromium_win_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinAsan',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinAsan(dll)': {
      'chromium_config': 'chromium_win_clang_asan_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': { 'component': 'shared_library' },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      'compile_targets': [ 'chromium_builder_asan' ],
      # add_tests_as_compile_targets not needed for the asan bot, it doesn't
      # build everything.
    },
    'CrWinAsan(dll) tester': {
      'chromium_config': 'chromium_win_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinAsan(dll)',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinAsanCov': {
      'chromium_config': 'chromium_win_clang_asan_tot_coverage',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'win',
      },
      'use_isolate': True,
      'enable_swarming': True,
      'compile_targets': [ 'chromium_builder_asan' ],
      # add_tests_as_compile_targets not needed for the asan bot, it doesn't
      # build everything.
    },
    'CrWinAsanCov tester': {
      'chromium_config': 'chromium_win_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [
        steps.generate_gtest,
      ],
      'bot_type': 'tester',
      'parent_buildername': 'CrWinAsanCov',
      'testing': {
        'platform': 'win',
      },
      'enable_swarming': True,
    },
    'CrWinGoma': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWinGoma(dll)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'shared_library'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWin7Goma': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWin7Goma(dll)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'shared_library'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWin7Goma(dbg)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'GYP_DEFINES': {
        'win_z7': '1'
      },
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWin7Goma(clbr)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'clobber', 'shared_library'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'CrWinClangGoma': {
      'chromium_config': 'chromium_win_clang_goma',
      'chromium_apply_config': ['goma_canary', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'win'
      }
    },
    'Chromium Linux Goma Canary': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'linux'
      }
    },
    'Chromium Linux Goma Canary (clobber)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'linux'
      }
    },
    'Chromium Linux32 Goma Canary (clobber)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'linux'
      }
    },
    'Chromium Linux Precise Goma LinkTest': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'goma_linktest'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'linux'
      }
    },
    'Chromium Mac 10.9 Goma Canary': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'mac'
      }
    },
    'Chromium Mac 10.9 Goma Canary (dbg)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'mac'
      }
    },
    'Chromium Mac 10.9 Goma Canary (clobber)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'mac'
      }
    },
    'Chromium Mac 10.9 Goma Canary (dbg)(clobber)': {
      'chromium_config': 'chromium',
      'chromium_apply_config': ['goma_canary', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'compile_targets': [ 'chromium_builder_tests' ],
      'goma_canary': True,
      'tests': steps.GOMA_TESTS,
      'testing': {
        'platform': 'mac'
      }
    },
    'ClangToTLinux': {
      'chromium_config': 'clang_tot_linux',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'component': 'shared_library',
        'werror': '',

        # Enable debug info, as on official builders, to catch issues with
        # optimized debug info.
        'linux_dump_symbols': '1',

        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': {
        'platform': 'linux',
      },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTLinux')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTLinux tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'ClangToTLinux',
      'testing': {
        'platform': 'linux',
      },
      'enable_swarming': True,
    },
    'ClangToTLinux (dbg)': {
      'chromium_config': 'clang_tot_linux',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'werror': '',

        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'linux', },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTLinux (dbg)')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTLinuxASan': {
      'chromium_config': 'clang_tot_linux_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'chromium_apply_config': ['lsan'],
      'GYP_DEFINES': {
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'linux', },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTLinuxASan')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTLinuxASan tester': {
      'chromium_config': 'chromium_linux_asan',
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
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTLinuxASan',
      'testing': { 'platform': 'linux', },
      'enable_swarming': True,
    },
    'ClangToTAndroidASan': {
      'chromium_config': 'clang_tot_android_asan',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_PLATFORM': 'android',
        'TARGET_ARCH': 'arm',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': {
        'component': 'shared_library',
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'android_config': 'clang_asan_tot_release_builder',
      'testing': { 'platform': 'linux', },
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTAndroidASan tester': {
      'chromium_config': 'clang_tot_android_asan',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTAndroidASan',
      'android_config': 'clang_asan_tot_release_builder',
      'root_devices': True,
      'tests': [
        steps.AndroidJunitTest('base_junit_tests'),
        steps.GTestTest(
            'components_browsertests',
            android_isolate_path='components/components_browsertests.isolate'),
        steps.GTestTest('gfx_unittests'),
        steps.AndroidInstrumentationTest(
            'ChromePublicTest', 'chrome_public_test_apk',
            isolate_file_path='chrome/chrome_public_test_apk.isolate',
            adb_install_apk='ChromePublic.apk'),
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'ClangToTMac': {
      'chromium_config': 'clang_tot_mac',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'component': 'shared_library',
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'mac', },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTMac')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTMac tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'ClangToTMac',
      'testing': {
        'platform': 'mac',
      },
      'enable_swarming': True,
    },
    'ClangToTMac (dbg)': {
      'chromium_config': 'clang_tot_mac',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'mac', },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTMac (dbg)')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTMacASan': {
      'chromium_config': 'clang_tot_mac_asan',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': {
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'mac', },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'ClangToTMacASan')
      },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTMacASan tester': {
      'chromium_config': 'chromium_mac_asan',
      'gclient_config': 'chromium',
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
      'parent_buildername': 'ClangToTMacASan',
      'testing': { 'platform': 'mac', },
      'enable_swarming': True,
    },
    'ClangToTiOS': {
      'chromium_config': 'clang_tot_ios',
      'gclient_config': 'ios',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_PLATFORM': 'ios',
        'TARGET_BITS': 32,
      },
      'gclient_config_kwargs': {
        'GIT_MODE': True,
      },
      'GYP_DEFINES': {
        'werror': '',
        # Plugin flags often need to be changed when using a plugin newer than
        # the latest Clang package, so disable plugins.
        'clang_use_chrome_plugins': '0',
      },
      'testing': {
        'platform': 'mac',
      }
    },
    'ClangToTWin': {
      'chromium_config': 'chromium_win_clang_official_tot',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'ClangToTWin(dbg)': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin(dbg)') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin(dbg) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 32,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin(dbg)',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'ClangToTWin(dll)': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'GYP_DEFINES': { 'component': 'shared_library' },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin(dll)') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin(dll) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin(dll)',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'ClangToTWin64': {
      'chromium_config': 'chromium_win_clang_official_tot',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin64') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin64 tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin64',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'ClangToTWin64(dbg)': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin64(dbg)') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin64(dbg) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Debug',
        'TARGET_BITS': 64,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin64(dbg)',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'ClangToTWin64(dll)': {
      'chromium_config': 'chromium_win_clang_tot',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'GYP_DEFINES': { 'component': 'shared_library' },
      'bot_type': 'builder',
      'testing': { 'platform': 'win', },
      'tests': { steps.SizesStep(RESULTS_URL, 'ClangToTWin64(dll)') },
      'use_isolate': True,
      'enable_swarming': True,
      # Workaround so that recipes doesn't add random build targets to our
      # compile line. We want to build everything.
      'add_tests_as_compile_targets': False,
    },
    'ClangToTWin64(dll) tester': {
      'chromium_config': 'chromium_no_goma',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [steps.generate_gtest],
      'bot_type': 'tester',
      'parent_buildername': 'ClangToTWin64(dll)',
      'testing': { 'platform': 'win' },
      'enable_swarming': True,
    },
    'Linux Builder (clobber)': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_apply_config': ['clobber', 'ninja_confirm_noop'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [
        'chromium_builder_tests',
      ],
      'testing': {
        'platform': 'linux',
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
      'compile_targets': [
        'chromium_builder_tests',
      ],
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
        'chromedriver_webview_shell_apk',
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android Tests (L Nexus5)(dbg)': {
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
        steps.AndroidJunitTest('base_junit_tests'),
        steps.GTestTest(
            'components_browsertests',
            android_isolate_path='components/components_browsertests.isolate'),
        steps.GTestTest('gfx_unittests'),
        steps.AndroidInstrumentationTest(
            'ChromePublicTest', 'chrome_public_test_apk',
            isolate_file_path='chrome/chrome_public_test_apk.isolate',
            adb_install_apk='ChromePublic.apk'),
      ],
      'testing': {
        'platform': 'linux',
      },
    },

    'Android Tests (trial)(dbg)': {
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
      'android_config': 'non_device_wipe_provisioning',
      'root_devices': True,
      'tests': [
        steps.GTestTest('gfx_unittests'),
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

    'Android Tests (amp split)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'amp_config': 'commit_queue_pool',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'bot_type': 'tester',
      'parent_buildername': 'Android Builder (dbg)',
      'android_config': 'main_builder',
      'root_devices': True,
      'enable_swarming': False,
      'tests': [
        steps.AndroidInstrumentationTest(
            'AndroidWebViewTest', 'android_webview_test_apk',
            isolate_file_path='android_webview/android_webview_test_apk.isolate',
            adb_install_apk='AndroidWebView.apk'),
        steps.AndroidInstrumentationTest(
            'ContentShellTest', 'content_shell_test_apk',
            isolate_file_path='content/content_shell_test_apk.isolate',
            adb_install_apk='ContentShell.apk'),
        steps.AndroidInstrumentationTest(
            'ChromePublicTest', 'chrome_public_test_apk',
            isolate_file_path='chrome/chrome_public_test_apk.isolate',
            adb_install_apk='ChromePublic.apk'),
        steps.AndroidInstrumentationTest(
            'ChromeSyncShellTest', 'chrome_sync_shell_test_apk',
            adb_install_apk='ChromeSyncShell.apk'),
        steps.AMPGTestTest('android_webview_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2']),
        steps.AMPGTestTest('base_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2'],
            android_isolate_path='base/base_unittests.isolate'),
        steps.GTestTest(
            'breakpad_unittests',
            override_compile_targets=['breakpad_unittests_deps'],
            android_isolate_path='breakpad/breakpad_unittests.isolate'),
        steps.GTestTest('cc_unittests'),
        steps.AMPGTestTest('components_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2'],
            android_isolate_path='components/components_unittests.isolate'),
        steps.GTestTest('content_browsertests'),
        steps.GTestTest('content_unittests'),
        steps.AMPGTestTest('events_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2']),
        steps.AMPGTestTest('gl_tests',
            device_name=['Nexus 5'], device_os=['4.4.2']),
        steps.GTestTest('gpu_unittests'),
        steps.AMPGTestTest('ipc_tests', device_name=['Nexus 5'],
                           device_os=['4.4.2']),
        steps.GTestTest('media_unittests'),
        steps.GTestTest('net_unittests'),
        steps.GTestTest(
            'sandbox_linux_unittests',
            override_compile_targets=['sandbox_linux_unittests_deps']),
        steps.AMPGTestTest('sql_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2'],
            android_isolate_path='sql/sql_unittests.isolate'),
        steps.AMPGTestTest('sync_unit_tests',
            device_name=['Nexus 5'], device_os=['4.4.2'],
            android_isolate_path='sync/sync_unit_tests.isolate'),
        steps.AMPGTestTest('ui_android_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2']),
        steps.GTestTest('ui_base_unittests'),
        steps.AMPGTestTest('ui_touch_selection_unittests',
            device_name=['Nexus 5'], device_os=['4.4.2']),
        steps.GTestTest('unit_tests'),
        steps.AndroidJunitTest('junit_unit_tests'),
        steps.AndroidJunitTest('chrome_junit_tests'),
        steps.AndroidJunitTest('content_junit_tests'),
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

    'Android Tests (amp instrumentation test split)': {
      'chromium_config': 'android',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['android'],
      'amp_config': 'main_pool',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
        'TARGET_PLATFORM': 'android',
      },
      'bot_type': 'tester',
      'parent_buildername': 'Android Builder (dbg)',
      'android_config': 'main_builder',
      'root_devices': True,
      'enable_swarming': False,
      'tests': [
        steps.AMPInstrumentationTest(
            test_apk='AndroidWebViewTest',
            apk_under_test='AndroidWebView',
            android_isolate_path=
                'android_webview/android_webview_test_apk.isolate',
            compile_target='android_webview_test_apk',
            device_name=['Nexus 5'], device_os=['4.4.2'],
            fallback_to_local=True),
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

    'Chromium Win 10': {
      'chromium_config': 'chromium',
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'test_generators': [
        steps.generate_gtest,
        steps.generate_script,
        steps.generate_isolated_script,
      ],
      'testing': {
        'platform': 'win',
      },
    },
    'Android Coverage (dbg)': {
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
      'android_config': 'incremental_coverage_builder_tests',
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
        steps.IncrementalCoverageTest(),
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
