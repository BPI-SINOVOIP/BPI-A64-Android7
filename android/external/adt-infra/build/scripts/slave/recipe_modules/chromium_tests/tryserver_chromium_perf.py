# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


from . import chromium_perf
from . import steps


SPEC = {
  'settings': chromium_perf.SPEC['settings'],
  'builders': {
    # This is intended to build in the same way as the main perf builder.
    'linux_perf_bisect_builder':
        chromium_perf.SPEC['builders']['Linux Builder'],
    'linux_perf_bisect':
        chromium_perf.SPEC['builders']['Linux Builder'],
    'win_perf_bisect_builder':
        chromium_perf.SPEC['builders']['Win Builder'],
    'win_perf_bisect':
        chromium_perf.SPEC['builders']['Win Builder'],
    'win_8_perf_bisect':
        chromium_perf.SPEC['builders']['Win Builder'],
    'win_xp_perf_bisect':
        chromium_perf.SPEC['builders']['Win Builder'],
    'winx64_10_perf_bisect':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'winx64_bisect_builder':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'win_x64_perf_bisect':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'winx64ati_perf_bisect':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'winx64nvidia_perf_bisect':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'winx64intel_perf_bisect':
        chromium_perf.SPEC['builders']['Win x64 Builder'],
    'mac_perf_bisect_builder':
        chromium_perf.SPEC['builders']['Mac Builder'],
    'mac_10_9_perf_bisect':
        chromium_perf.SPEC['builders']['Mac Builder'],
    'mac_10_10_perf_bisect':
        chromium_perf.SPEC['builders']['Mac Builder'],
    'mac_retina_perf_bisect':
        chromium_perf.SPEC['builders']['Mac Builder'],
    'linux_perf_tester':{
      'chromium_config': 'chromium_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'parent_buildername': 'Linux Builder',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'compile_targets': [
        'chromium_builder_perf',
      ],
      'testing': {
        'platform': 'linux',
      },
      'tests':[steps.BisectTest()],
      'chromium_apply_config': ['chromium_perf']
    },
    'linux_perf_bisector':{
      'chromium_config': 'chromium_official',
      'gclient_config': 'chromium',
      'gclient_apply_config': ['chrome_internal'],
      'parent_buildername': 'Linux Builder',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'bot_type': 'tester',
      'compile_targets': [
        'chromium_builder_perf',
      ],
      'testing': {
        'platform': 'linux',
      },
      'tests':[steps.BisectTest()],
      'chromium_apply_config': ['chromium_perf']
    },
    'win64_nv_tester':{
      'bot_type': 'tester',
      'chromium_config_kwargs': {
        'BUILD_CONFIG': 'Release',
        'TARGET_BITS': 64,
      },
      'parent_buildername': 'Win x64 Builder',
      'chromium_config': 'chromium_official',
      'gclient_config': 'perf',
      'testing': {
        'platform': 'win',
      },
      'tests':[steps.BisectTest()],
    },
  }
}

