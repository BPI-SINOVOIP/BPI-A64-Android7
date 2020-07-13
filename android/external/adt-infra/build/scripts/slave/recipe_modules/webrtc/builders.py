# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Contains the bulk of the WebRTC builder configurations so they can be reused
# from multiple recipes.

from recipe_engine.types import freeze

RECIPE_CONFIGS = freeze({
  'webrtc': {
    'chromium_config': 'webrtc_standalone',
    'gclient_config': 'webrtc',
    'test_suite': 'webrtc',
  },
  'webrtc_compile': {
    'chromium_config': 'webrtc_standalone',
    'gclient_config': 'webrtc',
  },
  'webrtc_compile_android': {
    'chromium_config': 'android',
    'chromium_android_config': 'webrtc',
    'gclient_config': 'webrtc',
    'gclient_apply_config': ['android'],
  },
  'webrtc_baremetal': {
    'chromium_config': 'webrtc_standalone',
    'gclient_config': 'webrtc',
    'test_suite': 'webrtc_baremetal',
  },
  'webrtc_clang': {
    'chromium_config': 'webrtc_clang',
    'gclient_config': 'webrtc',
    'test_suite': 'webrtc',
  },
  'webrtc_parallel': {
    'chromium_config': 'webrtc_standalone',
    'gclient_config': 'webrtc',
    'test_suite': 'webrtc_parallel',
  },
  'webrtc_parallel_clang': {
    'chromium_config': 'webrtc_clang',
    'gclient_config': 'webrtc',
    'test_suite': 'webrtc_parallel',
  },
  'webrtc_android': {
    'chromium_config': 'android',
    'chromium_android_config': 'webrtc',
    'gclient_config': 'webrtc',
    'gclient_apply_config': ['android'],
    'test_suite': 'android',
  },
  'webrtc_android_clang': {
    'chromium_config': 'android_clang',
    'chromium_android_config': 'webrtc',
    'gclient_config': 'webrtc',
    'gclient_apply_config': ['android'],
  },
  'webrtc_android_asan': {
    'chromium_config': 'android_asan',
    'chromium_android_config': 'webrtc',
    'gclient_config': 'webrtc',
    'gclient_apply_config': ['android'],
    'test_suite': 'android',
  },
  'webrtc_ios': {
    'chromium_config': 'webrtc_ios',
    'gclient_config': 'webrtc_ios',
  },
  'chromium_webrtc': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['dcheck', 'blink_logging_on'],
    'gclient_config': 'chromium_webrtc',
    'compile_targets': ['chromium_builder_webrtc'],
    'test_suite': 'chromium',
  },
  'chromium_webrtc_tot': {
    'chromium_config': 'chromium',
    'chromium_apply_config': ['dcheck', 'blink_logging_on'],
    'gclient_config': 'chromium_webrtc_tot',
    'compile_targets': ['chromium_builder_webrtc'],
    'test_suite': 'chromium',
  },
  # Temporary config to try out the complicated FYI builders pre-Git switch
  # (runs a tiny compile target and no test to save time and resources).
  'chromium_webrtc_tot_git_switch_testing': {
    'chromium_config': 'chromium',
    'gclient_config': 'chromium_webrtc_tot',
    'compile_targets': ['gtest'],
  },
  'chromium_webrtc_android': {
    'chromium_config': 'android',
    'chromium_android_config': 'base_config',
    'gclient_config': 'chromium_webrtc',
    'gclient_apply_config': ['android'],
    'compile_targets': ['android_builder_chromium_webrtc'],
    'test_suite': 'chromium',
  },
  'chromium_webrtc_tot_android': {
    'chromium_config': 'android',
    'chromium_android_config': 'base_config',
    'gclient_config': 'chromium_webrtc_tot',
    'gclient_apply_config': ['android'],
    'compile_targets': ['android_builder_chromium_webrtc'],
    'test_suite': 'chromium',
  },
})

WEBRTC_REVISION_PERF_CONFIG = '{\'a_default_rev\': \'r_webrtc_rev\'}'

BUILDERS = freeze({
  'chromium.webrtc': {
    'builders': {
      'Win Builder': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'win_rel_archive',
        'testing': {'platform': 'win'},
        'triggers': [
          'WinXP Tester',
          'Win7 Tester',
          'Win8 Tester',
          'Win10 Tester',
        ],
      },
      'WinXP Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-xp',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive',
        'disable_runhooks': True,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Win7 Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-7',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive',
        # TODO(kjellander): Disable the hooks on as soon we've moved away
        # from downloading test resources in that step.
        'disable_runhooks': False,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Win8 Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-win8',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive',
        # TODO(kjellander): Disable the hooks on as soon we've moved away
        # from downloading test resources in that step.
        'disable_runhooks': False,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Win10 Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-win10',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive',
        # TODO(kjellander): Disable the hooks on as soon we've moved away
        # from downloading test resources in that step.
        'disable_runhooks': False,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Mac Builder': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'mac_rel_archive',
        'testing': {'platform': 'mac'},
        'triggers': ['Mac Tester'],
      },
      'Mac Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-mac',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'mac_rel_archive',
        'parent_buildername': 'Mac Builder',
        'testing': {'platform': 'mac'},
      },
      'Linux Builder': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': ['Linux Tester'],
      },
      'Linux Tester': {
        'recipe_config': 'chromium_webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-rel-linux',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'linux_rel_archive',
        'parent_buildername': 'Linux Builder',
        'testing': {'platform': 'linux'},
      },
    },
  },
  'chromium.webrtc.fyi': {
    'settings': {
      'PERF_CONFIG': WEBRTC_REVISION_PERF_CONFIG,
    },
    'builders': {
      'Win Builder': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-win',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'win_rel_archive_fyi',
        'testing': {'platform': 'win'},
        'triggers': [
          'WinXP Tester',
          'Win7 Tester',
          'Win10 Tester',
        ],
      },
      'WinXP Tester': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-winxp',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive_fyi',
        'disable_runhooks': True,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Win7 Tester': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-win7',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive_fyi',
        # TODO(kjellander): Disable the hooks on Win7 as soon we've moved away
        # from downloading test resources in that step.
        'disable_runhooks': False,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Win10 Tester': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-win10',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'win_rel_archive_fyi',
        # TODO(kjellander): Disable the hooks on Win7 as soon we've moved away
        # from downloading test resources in that step.
        'disable_runhooks': False,
        'parent_buildername': 'Win Builder',
        'testing': {'platform': 'win'},
      },
      'Mac Builder': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-mac',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'mac_rel_archive_fyi',
        'testing': {'platform': 'mac'},
        'triggers': ['Mac Tester'],
      },
      'Mac Tester': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-mac',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'mac_rel_archive_fyi',
        'parent_buildername': 'Mac Builder',
        'testing': {'platform': 'mac'},
      },
      'Linux': {
        'recipe_config': 'chromium_webrtc_tot',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-rel-linux',
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Android Builder (dbg)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'testing': {'platform': 'linux'},
        'triggers': [
          'Android Tests (dbg) (J Nexus4)',
          'Android Tests (dbg) (K Nexus5)',
          'Android Tests (dbg) (L Nexus5)',
          'Android Tests (dbg) (L Nexus6)',
          'Android Tests (dbg) (L Nexus7.2)',
        ],
      },
      'Android Builder ARM64 (dbg)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-arm64',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_dbg_archive_arm64_fyi',
        'testing': {'platform': 'linux'},
        'triggers': [
          'Android Tests (dbg) (L Nexus9)',
        ],
      },
      'Android Tests (dbg) (J Nexus4)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-nexus4-j',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'parent_buildername': 'Android Builder (dbg)',
        'testing': {'platform': 'linux'},
      },
      'Android Tests (dbg) (K Nexus5)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-nexus5-k',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'parent_buildername': 'Android Builder (dbg)',
        'testing': {'platform': 'linux'},
      },
      'Android Tests (dbg) (L Nexus5)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-nexus5',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'parent_buildername': 'Android Builder (dbg)',
        'testing': {'platform': 'linux'},
      },
      'Android Tests (dbg) (L Nexus6)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-nexus6',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'parent_buildername': 'Android Builder (dbg)',
        'testing': {'platform': 'linux'},
      },
      'Android Tests (dbg) (L Nexus7.2)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'chromium-webrtc-trunk-tot-dbg-android-nexus72',
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_fyi',
        'parent_buildername': 'Android Builder (dbg)',
        'testing': {'platform': 'linux'},
      },
      'Android Tests (dbg) (L Nexus9)': {
        'recipe_config': 'chromium_webrtc_tot_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'build_gs_archive': 'android_dbg_archive_arm64_fyi',
        'parent_buildername': 'Android Builder ARM64 (dbg)',
        'testing': {'platform': 'linux'},
      },
    },
  },
  'client.webrtc': {
    'settings': {
      'PERF_CONFIG': WEBRTC_REVISION_PERF_CONFIG,
    },
    'builders': {
      'Win32 Debug': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win32 Release': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win64 Debug': {
        'recipe_config': 'webrtc_parallel',
        'chromium_apply_config': ['static_library'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win64 Release': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win64 Debug (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'win'},
      },
      'Win64 Release (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'win'},
      },
      'Win32 Release [large tests]': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-win-large-tests',
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win DrMemory Full': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['drmemory_full'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win DrMemory Light': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['drmemory_light'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Win SyzyASan': {
        'recipe_config': 'webrtc_parallel',
        'chromium_apply_config': ['syzyasan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'Mac32 Debug': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac32 Release': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Debug': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Release': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Debug (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Release (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'Mac Asan': {
        'recipe_config': 'webrtc_clang',
        'chromium_apply_config': ['asan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac32 Release [large tests]': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-mac-large-tests',
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'iOS32 Debug': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'iOS32 Release': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'iOS64 Debug': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'iOS64 Release': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'iOS32 Simulator Debug': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'intel',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'iOS64 Simulator Debug': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'intel',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'Linux32 Debug': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux32 Release': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Debug': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Release': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Debug (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Release (GN)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Linux Asan': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['asan', 'lsan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux Memcheck': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['memcheck'],
        'gclient_apply_config': ['valgrind'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux MSan': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['msan', 'msan_full_origin_tracking',
                                  'prebuilt_instrumented_libraries'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux Tsan v2': {
        'recipe_config': 'webrtc_clang',
        'chromium_apply_config': ['tsan2'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Release [large tests]': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-linux-large-tests',
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Android32 Builder': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_apk_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'Android32 Builder (dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_apk_dbg_archive',
        'testing': {'platform': 'linux'},
      },
      'Android64 Builder': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_apk_arm64_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'Android64 Builder (dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Android32 Clang (dbg)': {
        'recipe_config': 'webrtc_android_clang',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Android32 GN (dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Android32 GN': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Android32 Tests (L Nexus5)(dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android32 Builder (dbg)',
        'build_gs_archive': 'android_apk_dbg_archive',
        'testing': {'platform': 'linux'},
      },
      'Android32 Tests (L Nexus5)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-android-tests-nexus5',
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android32 Builder',
        'build_gs_archive': 'android_apk_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'Android32 Tests (L Nexus7.2)(dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android32 Builder (dbg)',
        'build_gs_archive': 'android_apk_dbg_archive',
        'testing': {'platform': 'linux'},
      },
      'Android32 Tests (L Nexus7.2)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-android-tests-nexus72',
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android32 Builder Release',
        'build_gs_archive': 'android_apk_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'Android64 Tests (L Nexus9)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'webrtc_config_kwargs': {
          'PERF_ID': 'webrtc-android-tests-nexus9',
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android64 Builder',
        'build_gs_archive': 'android_apk_arm64_rel_archive',
        'testing': {'platform': 'linux'},
      },
    },
  },
  'client.webrtc.fyi': {
    'builders':  {
      'Win32 Release (swarming)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
        'use_isolate': True,
        'enable_swarming': True,
      },
      'Mac64 Debug (parallel)': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Release (parallel)': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Mac64 Release (swarming)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
        'use_isolate': True,
        'enable_swarming': True,
      },
      'Mac Asan (parallel)': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['asan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'Linux Tsan v2 (parallel)': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['tsan2'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Linux64 Release (swarming)': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
        'use_isolate': True,
        'enable_swarming': True,
      },
      'Linux32 ARM': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'Android32 ASan (L Nexus6)': {
        'recipe_config': 'webrtc_android_asan',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'Android Builder (dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'fyi_android_apk_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'Android32 Tests (L Nexus6)(dbg)',
        ],
      },
      'Android32 Tests (L Nexus6)(dbg)': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'Android Builder (dbg)',
        'build_gs_archive': 'fyi_android_apk_dbg_archive',
        'testing': {'platform': 'linux'},
      },
    },
  },
  'tryserver.webrtc': {
    'builders': {
      'win_compile_dbg': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_compile_rel': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_compile_x64_dbg': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_compile_x64_rel': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_dbg': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_rel': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_x64_dbg': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_x64_rel': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_x64_gn_dbg': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'win'},
      },
      'win_x64_gn_rel': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'win'},
      },
      'win_baremetal': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_asan': {
        'recipe_config': 'webrtc_parallel',
        'chromium_apply_config': ['syzyasan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_drmemory_light': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['drmemory_light'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'win_drmemory_full': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['drmemory_full'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'mac_compile_dbg': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_compile_rel': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_compile_x64_dbg': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_compile_x64_rel': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_dbg': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_rel': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_x64_dbg': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_x64_rel': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_x64_gn_dbg': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'mac_x64_gn_rel': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'mac_asan': {
        'recipe_config': 'webrtc_clang',
        'chromium_apply_config': ['asan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'mac_baremetal': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
      'ios_dbg': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'ios_rel': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'ios_arm64_dbg': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'ios_arm64_rel': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'arm',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'ios32_sim_dbg': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'TARGET_ARCH': 'intel',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'ios64_sim_dbg': {
        'recipe_config': 'webrtc_ios',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'TARGET_ARCH': 'intel',
          'TARGET_PLATFORM': 'ios',
        },
        'bot_type': 'builder',
        'testing': {'platform': 'mac'},
      },
      'linux_compile_dbg': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_compile_rel': {
        'recipe_config': 'webrtc_compile',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_dbg': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_rel': {
        'recipe_config': 'webrtc_parallel',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_gn_dbg': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'linux_gn_rel': {
        'recipe_config': 'webrtc',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'linux_asan': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['asan', 'lsan'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_memcheck': {
        'recipe_config': 'webrtc',
        'chromium_apply_config': ['memcheck'],
        'gclient_apply_config': ['valgrind'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_msan': {
        'recipe_config': 'webrtc_parallel_clang',
        'chromium_apply_config': ['msan', 'msan_full_origin_tracking',
                                  'prebuilt_instrumented_libraries'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_tsan2': {
        'recipe_config': 'webrtc_clang',
        'chromium_apply_config': ['tsan2'],
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'linux_baremetal': {
        'recipe_config': 'webrtc_baremetal',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_compile_dbg': {
        'recipe_config': 'webrtc_compile_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_compile_rel': {
        'recipe_config': 'webrtc_compile_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_compile_arm64_dbg': {
        'recipe_config': 'webrtc_compile_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_compile_arm64_rel': {
        'recipe_config': 'webrtc_compile_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_dbg': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_rel': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_clang_dbg': {
        'recipe_config': 'webrtc_android_clang',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'android_arm64_rel': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_n6': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'android_gn_dbg': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
      'android_gn_rel': {
        'recipe_config': 'webrtc_android',
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'chromium_apply_config': ['webrtc_gn'],
        'bot_type': 'builder',
        'testing': {'platform': 'linux'},
      },
    },
  },
})

