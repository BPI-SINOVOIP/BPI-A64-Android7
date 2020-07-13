# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

RESULTS_URL = 'https://chromeperf.appspot.com'

SPEC = {
  'builders': {
    'Google Chrome ChromeOS': {
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['mb', 'chromeos'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [
        'chrome',
        'chrome_sandbox',
        'linux_symbols',
        'symupload'
      ],
      'testing': {
        'platform': 'linux',
      },
    },
    'Google Chrome Linux': {
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [
        'linux_packages_all'
      ],
      'testing': {
        'platform': 'linux',
      },
      # TODO(hans): Figure why sizes doesn't work on this bot, and fix it.
    },
    'Google Chrome Linux x64': {
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [
        'linux_packages_all'
      ],
      'testing': {
        'platform': 'linux',
      },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'Google Chrome Linux x64')
      },
    },
    'Google Chrome Mac': {
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'compile_targets': [
        'chrome',
      ],
      'testing': {
        'platform': 'mac',
      },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'Google Chrome Mac')
      },
    },
    'Google Chrome Win': {
      'chromium_config': 'chromium_official',
      'chromium_apply_config': ['mb'],
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'compile_targets': [
        'chrome',
      ],
      'testing': {
        'platform': 'win',
      },
      'tests': {
        steps.SizesStep(RESULTS_URL, 'Google Chrome Win')
      },
    },
  },
}
