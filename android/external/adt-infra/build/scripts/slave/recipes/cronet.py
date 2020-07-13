# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze
from recipe_engine.recipe_api import Property

DEPS = [
  'cronet',
  'path',
  'properties',
]

BUILDERS = freeze({
  'local_test': {
    'recipe_config': 'main_builder',
    'run_tests': True,
    'kwargs': {
      'BUILD_CONFIG': 'Debug',
      'REPO_URL': 'https://chromium.googlesource.com/chromium/src.git',
      'REPO_NAME': 'src',
    },
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_local_test_builder',
    },
    'gyp_defs': {
      'use_goma': 0,
    }
  },
  'Android Cronet Builder (dbg)': {
    'recipe_config': 'main_builder',
    'run_tests': True,
    'kwargs': {
      'BUILD_CONFIG': 'Debug',
    },
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_builder_dbg',
    },
  },
  'Android Cronet Builder': {
    'recipe_config': 'main_builder',
    'run_tests': True,
    'kwargs': {
      'BUILD_CONFIG': 'Release',
      'REPO_NAME': 'src',
    },
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_builder',
    },
  },
  'Android Cronet ARMv6 Builder': {
    'recipe_config': 'main_builder',
    'run_tests': True,
    'kwargs': {
      'BUILD_CONFIG': 'Release',
    },
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_armv6_builder',
    },
    'gyp_defs': {
      'arm_version': 6
    }
  },
  'Android Cronet ARM64 Builder': {
    'recipe_config': 'arm64_builder',
    'run_tests': False,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_arm64_builder',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Release',
    },
  },
  'Android Cronet ARM64 Builder (dbg)': {
    'recipe_config': 'arm64_builder',
    'run_tests': False,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_arm64_builder_dbg',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Debug',
    },
  },
  'Android Cronet x86 Builder': {
    'recipe_config': 'x86_builder',
    'run_tests': False,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_x86_builder',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Release',
    },
  },
  'Android Cronet x86 Builder (dbg)': {
    'recipe_config': 'x86_builder',
    'run_tests': False,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_x86_builder_dbg',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Debug',
    },
  },
  'Android Cronet MIPS Builder': {
    'recipe_config': 'mipsel_builder',
    'run_tests': False,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_mips_builder',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Release',
    },
  },
  'Android Cronet Data Reduction Proxy Builder': {
    'recipe_config': 'main_builder',
    'run_tests': True,
    'cronet_kwargs': {
      'PERF_ID': 'android_cronet_data_reduction_proxy_builder',
    },
    'kwargs': {
      'BUILD_CONFIG': 'Release',
    },
    'gyp_defs': {
      'enable_data_reduction_proxy_support': 1,
    }
  },
})


PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  builder_config = BUILDERS.get(buildername, {})
  recipe_config = builder_config['recipe_config']
  kwargs = builder_config.get('kwargs', {})
  cronet_kwargs = builder_config.get('cronet_kwargs', {})
  gyp_defs = builder_config.get('gyp_defs', {})

  api.cronet.init_and_sync(recipe_config, kwargs, gyp_defs)
  api.cronet.build()

  if cronet_kwargs['PERF_ID']:
    api.cronet.sizes(cronet_kwargs['PERF_ID'])
  if builder_config['run_tests']:
    api.cronet.run_tests(kwargs['BUILD_CONFIG'])


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text.lower())


def GenTests(api):
  for bot_id in BUILDERS.keys():
    props = api.properties.generic(
      buildername=bot_id,
      revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
      repository='https://chromium.googlesource.com/chromium/src',
      branch='master',
      project='src',
    )
    yield api.test(_sanitize_nonalpha(bot_id)) + props
