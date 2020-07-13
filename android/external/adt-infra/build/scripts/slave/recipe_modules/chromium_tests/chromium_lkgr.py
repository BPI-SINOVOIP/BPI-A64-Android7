# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from . import steps

RESULTS_URL = 'https://chromeperf.appspot.com'

SPEC = {
  'builders': {
    'Win SyzyASAN LKGR': {
      'chromium_config': 'chromium_no_goma',
      'chromium_apply_config': ['syzyasan', 'clobber'],
      'gclient_config': 'chromium',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 32,
      },
      'bot_type': 'builder',
      'disable_tests': True,
      'cf_archive_build': True,
      'cf_gs_bucket': 'chromium-browser-syzyasan',
      'cf_gs_acl': 'public-read',
      'cf_archive_name': 'asan',
      'compile_targets': [
        'chromium_builder_asan',
      ],
      'testing': {
        'platform': 'win',
      },
    },
  },
}
