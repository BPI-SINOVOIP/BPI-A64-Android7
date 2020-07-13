# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Contains the bulk of the V8 builder configurations so they can be reused
# from multiple recipes.

from recipe_engine.types import freeze


class TestStepConfig(object):
  """Per-step test configuration."""
  def __init__(self, name, shards=1):
    self.name = name
    self.shards = shards


# Top-level test configs for convenience.
Benchmarks = TestStepConfig('benchmarks')
Deopt = TestStepConfig('deopt')
Fuzz = TestStepConfig('fuzz')
GCMole = TestStepConfig('gcmole')
Mjsunit = TestStepConfig('mjsunit')
Mozilla = TestStepConfig('mozilla')
OptimizeForSize = TestStepConfig('optimize_for_size')
Presubmit = TestStepConfig('presubmit')
SimdJs = TestStepConfig('simdjs')
SimpleLeak = TestStepConfig('simpleleak')
Test262 = TestStepConfig('test262')
Test262Variants = TestStepConfig('test262_variants')
Test262Variants_2 = TestStepConfig('test262_variants', shards=2)
Unittests = TestStepConfig('unittests')
V8Initializers = TestStepConfig('v8initializers')
V8Testing = TestStepConfig('v8testing')
Webkit = TestStepConfig('webkit')


BUILDERS = {
####### Waterfall: client.v8
  'client.v8': {
    'builders': {
####### Category: Linux
      'V8 Linux - builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Deopt Fuzzer',
          'V8 Linux',
          'V8 Linux - deadcode',
          'V8 Linux - gcmole',
          'V8 Linux - isolates',
          'V8 Linux - nosse3',
          'V8 Linux - nosse4',
          'V8 Linux - presubmit',
        ],
      },
      'V8 Linux - swarming staging builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_swarming_staging_archive',
        'enable_swarming': True,
        'testing': {
          'platform': 'linux',
          'isolated_tests': {
            'benchmarks': '[dummy hash for benchmarks]',
            'bot_default': '[dummy hash for bot_default]',
            'mozilla': '[dummy hash for mozilla]',
            'optimize_for_size': '[dummy hash for optimize_for_size]',
            'simdjs': '[dummy hash for simdjs]',
            'test262': '[dummy hash for test262]',
          },
        },
        'triggers': [
          'V8 Linux - swarming staging',
        ],
      },
      'V8 Linux - debug builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 GC Stress - 1',
          'V8 GC Stress - 2',
          'V8 GC Stress - 3',
          'V8 Linux - debug',
          'V8 Linux - debug - avx2',
          'V8 Linux - debug - code serializer',
          'V8 Linux - debug - isolates',
          'V8 Linux - debug - nosse3',
          'V8 Linux - debug - nosse4',
          'V8 Linux - memcheck',
          'V8 Linux - test262 - debug',
          'V8 Linux - debug - greedy allocator',
        ],
      },
      'V8 Linux - nosnap builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_nosnap_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux - nosnap',
        ],
      },
      'V8 Linux - nosnap debug builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_nosnap_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux - nosnap - debug - 1',
          'V8 Linux - nosnap - debug - 2',
        ],
      },
      'V8 Linux - presubmit': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [Presubmit],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [
          V8Initializers,
          V8Testing,
          OptimizeForSize,
          Benchmarks,
          SimdJs,
          Test262Variants,
          Mozilla,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - swarming staging': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - swarming staging builder',
        'build_gs_archive': 'linux_swarming_staging_archive',
        'tests': [
          V8Initializers,
          V8Testing,
          OptimizeForSize,
          Benchmarks,
          SimdJs,
          Test262Variants_2,
          Mozilla,
        ],
        'testing': {'platform': 'linux'},
        'enable_swarming': True,
        'swarming_dimensions': {
          'os': 'Ubuntu-12.04',
        },
      },
      'V8 Linux - debug': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing, Benchmarks, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - avx2': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing, Benchmarks, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - test262 - debug': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [Test262Variants],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - shared': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'shared_library', 'verify_heap'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - nosnap': {
        'v8_apply_config': ['no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - nosnap builder',
        'build_gs_archive': 'linux_nosnap_rel_archive',
        'tests': [
          V8Testing,
          SimdJs,
          Test262,
          Mozilla,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - nosnap - debug - 1': {
        'v8_apply_config': ['no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - nosnap debug builder',
        'build_gs_archive': 'linux_nosnap_dbg_archive',
        'tests': [V8Testing, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - nosnap - debug - 2': {
        'v8_apply_config': ['no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - nosnap debug builder',
        'build_gs_archive': 'linux_nosnap_dbg_archive',
        'tests': [V8Testing, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - isolates': {
        'v8_apply_config': ['isolates'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - nosse3': {
        'v8_apply_config': ['nosse3'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [V8Testing, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - nosse4': {
        'v8_apply_config': ['nosse4'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [V8Testing, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - deadcode': {
        'v8_apply_config': ['deadcode'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - isolates': {
        'v8_apply_config': ['isolates'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - nosse3': {
        'v8_apply_config': ['nosse3'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - nosse4': {
        'v8_apply_config': ['nosse4'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - gcmole': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [GCMole],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - interpreted regexp': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'interpreted_regexp'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - noi18n - debug': {
        'v8_apply_config': ['no_i18n', 'no_exhaustive_variants'],
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_i18n'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Mozilla, Test262, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - code serializer': {
        'v8_apply_config': ['code_serializer', 'no_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [Mjsunit, Mozilla, Test262, Benchmarks,
                  SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - debug - greedy allocator': {
        'v8_apply_config': ['greedy_allocator', 'turbo_variant'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [V8Testing, Benchmarks, SimdJs],
        'testing': {'platform': 'linux'},
      },
####### Category: Linux64
      'V8 Linux64 - builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux64_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux64',
          'V8 Linux64 - avx2',
        ],
      },
      'V8 Linux64 - debug builder': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux64_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Fuzzer',
          'V8 Linux64 - debug',
          'V8 Linux64 - debug - avx2',
          'V8 Linux64 - debug - greedy allocator',
        ],
      },
      'V8 Linux64 - custom snapshot - debug builder': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'embed_script_mjsunit'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux64_custom_snapshot_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux64 - custom snapshot - debug',
          'V8 Linux64 GC Stress - custom snapshot',
        ],
      },
      'V8 Linux64': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - builder',
        'build_gs_archive': 'linux64_rel_archive',
        'tests': [
          V8Initializers,
          V8Testing,
          OptimizeForSize,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - avx2': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - builder',
        'build_gs_archive': 'linux64_rel_archive',
        'tests': [
          V8Testing,
          Benchmarks,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - debug': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - debug builder',
        'build_gs_archive': 'linux64_dbg_archive',
        'tests': [
          V8Testing,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - debug - avx2': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - debug builder',
        'build_gs_archive': 'linux64_dbg_archive',
        'tests': [
          V8Testing,
          Benchmarks,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - internal snapshot': {
        'chromium_apply_config': [
          'v8_ninja', 'clang', 'goma', 'internal_snapshot',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - custom snapshot - debug': {
        'v8_apply_config': ['no_harness'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - custom snapshot - debug builder',
        'build_gs_archive': 'linux64_custom_snapshot_dbg_archive',
        'tests': [Mjsunit],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - debug - greedy allocator': {
        'v8_apply_config': ['greedy_allocator', 'turbo_variant'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'HOST_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - debug builder',
        'build_gs_archive': 'linux64_dbg_archive',
        'tests': [V8Testing, Benchmarks, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - debug - vector stores': {
        'chromium_apply_config': [
          'v8_ninja', 'clang', 'goma', 'vector_stores',
        ],
        'v8_apply_config': ['vector_stores'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          OptimizeForSize,
          Benchmarks,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 - cfi': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma', 'cfi'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          OptimizeForSize,
          Benchmarks,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
####### Category: Windows
      'V8 Win32 - builder': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'win32_rel_archive',
        'testing': {'platform': 'win'},
        'triggers': [
          'V8 Win32 - 1',
          'V8 Win32 - 2',
        ],
      },
      'V8 Win32 - debug builder': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'win32_dbg_archive',
        'testing': {'platform': 'win'},
        'triggers': [
          'V8 Win32 - debug - 1',
          'V8 Win32 - debug - 2',
          'V8 Win32 - debug - 3'
        ],
      },
      'V8 Win32 - 1': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Win32 - builder',
        'build_gs_archive': 'win32_rel_archive',
        'tests': [V8Testing, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win32 - 2': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Win32 - builder',
        'build_gs_archive': 'win32_rel_archive',
        'tests': [V8Testing, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win32 - nosnap - shared': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja',
          'msvs2013',
          'shared_library',
          'no_snapshot',
         ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'win'},
      },
      'V8 Win32 - debug - 1': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Win32 - debug builder',
        'build_gs_archive': 'win32_dbg_archive',
        'tests': [V8Testing, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win32 - debug - 2': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Win32 - debug builder',
        'build_gs_archive': 'win32_dbg_archive',
        'tests': [V8Testing, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win32 - debug - 3': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 3,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Win32 - debug builder',
        'build_gs_archive': 'win32_dbg_archive',
        'tests': [V8Testing, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win64': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
      'V8 Win64 - debug': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs, Test262, Mozilla],
        'testing': {'platform': 'win'},
      },
####### Category: Mac
      'V8 Mac': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'V8 Mac - debug': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'V8 Mac64': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'V8 Mac64 - debug': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'V8 Mac64 - xcode': {
        'chromium_apply_config': ['clang'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'mac'},
      },
####### Category: Arm
      'V8 Arm - builder': {
        'chromium_apply_config': ['arm_hard_float'],
        'v8_apply_config': ['arm_hard_float'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'arm_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Arm',
        ],
      },
      'V8 Arm - debug builder': {
        'chromium_apply_config': ['arm_hard_float'],
        'v8_apply_config': ['arm_hard_float'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'arm_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Arm - debug - 1',
          'V8 Arm - debug - 2',
          'V8 Arm GC Stress',
        ],
      },
      'V8 Android Arm - builder': {
        'gclient_apply_config': ['android'],
        'chromium_apply_config': ['v8_ninja', 'default_compiler', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
          'TARGET_PLATFORM': 'android',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_arm_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'V8 Android Arm64 - builder': {
        'gclient_apply_config': ['android'],
        'chromium_apply_config': ['v8_ninja', 'default_compiler', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 64,
          'TARGET_PLATFORM': 'android',
        },
        'bot_type': 'builder',
        'build_gs_archive': 'android_arm64_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'V8 Arm': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Arm - builder',
        'build_gs_archive': 'arm_rel_archive',
        'tests': [
          V8Testing,
          Benchmarks,
          OptimizeForSize,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Arm - debug': {
        'v8_apply_config': ['no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Arm - debug builder',
        'build_gs_archive': 'arm_dbg_archive',
        'tests': [V8Testing, OptimizeForSize, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Arm - debug - 1': {
        'v8_apply_config': ['no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Arm - debug builder',
        'build_gs_archive': 'arm_dbg_archive',
        'tests': [V8Testing, OptimizeForSize, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Arm - debug - 2': {
        'v8_apply_config': ['no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Arm - debug builder',
        'build_gs_archive': 'arm_dbg_archive',
        'tests': [V8Testing, OptimizeForSize, SimdJs],
        'testing': {'platform': 'linux'},
      },
####### Category: MIPS
      'V8 Mips - builder': {
        'chromium_apply_config': ['no_snapshot', 'no_i18n'],
        'v8_apply_config': ['mips_cross_compile', 'no_snapshot', 'no_i18n'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'mips',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'mips_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Mips - big endian - nosnap - 1',
          'V8 Mips - big endian - nosnap - 2',
        ],
      },
      'V8 Mips - big endian - nosnap - 1': {
        'v8_apply_config': ['no_snapshot', 'no_i18n'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'mips',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Mips - builder',
        'build_gs_archive': 'mips_rel_archive',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Mips - big endian - nosnap - 2': {
        'v8_apply_config': ['no_snapshot', 'no_i18n'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'mips',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Mips - builder',
        'build_gs_archive': 'mips_rel_archive',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
####### Category: Simulators
      'V8 Linux - arm - sim': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm - sim - debug': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm - sim - novfp3': {
        # TODO(machenbach): Can these configs be reduced to one?
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_arm', 'novfp3'],
        'v8_apply_config': ['novfp3'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm - sim - debug - novfp3': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_arm', 'novfp3'],
        'v8_apply_config': ['novfp3', 'no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim - debug': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim - nosnap - debug - 1': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_arm', 'no_snapshot'],
        'v8_apply_config': ['no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 1,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim - nosnap - debug - 2': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_arm', 'no_snapshot'],
        'v8_apply_config': ['no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'SHARD_COUNT': 2,
          'SHARD_RUN': 2,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Test262, Mozilla, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim - gc stress': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - mipsel - sim - builder': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_mipsel'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'mipsel_sim_rel_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux - mipsel - sim',
        ],
      },
      'V8 Linux - mips64el - sim - builder': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_mipsel'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'mips64el_sim_rel_archive',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - mipsel - sim': {
        'chromium_apply_config': ['simulate_mipsel'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - mipsel - sim - builder',
        'build_gs_archive': 'mipsel_sim_rel_archive',
        'tests': [V8Testing, Test262, SimdJs],
        'testing': {'platform': 'linux'},
      },
####### Category: Misc
      'V8 Fuzzer': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
          'HOST_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - debug builder',
        'build_gs_archive': 'linux64_dbg_archive',
        'tests': [Fuzz],
        'testing': {'platform': 'linux'},
      },
      'V8 Deopt Fuzzer': {
        'v8_apply_config': ['deopt_fuzz_normal', 'no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - builder',
        'build_gs_archive': 'linux_rel_archive',
        'tests': [Deopt],
        'testing': {'platform': 'linux'},
      },
      'V8 GC Stress - 1': {
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 1,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'V8 GC Stress - 2': {
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 2,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'V8 GC Stress - 3': {
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
          'SHARD_COUNT': 3,
          'SHARD_RUN': 3,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'V8 Mac GC Stress': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'mac'},
      },
      'V8 Arm GC Stress': {
        'v8_apply_config': ['gc_stress', 'no_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Arm - debug builder',
        'build_gs_archive': 'arm_dbg_archive',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 GC Stress - custom snapshot': {
        'v8_apply_config': ['gc_stress', 'no_harness'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux64 - custom snapshot - debug builder',
        'build_gs_archive': 'linux64_custom_snapshot_dbg_archive',
        'tests': [Mjsunit],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux gcc 4.8': {
        'chromium_apply_config': ['make', 'no_clang'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 ASAN': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'asan', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux64 ASAN no inline - release builder',
          'V8 Linux64 ASAN - debug builder',
          'V8 Linux64 ASAN arm64 - debug builder',
          'V8 Linux ASAN arm - debug builder',
          'V8 Linux ASAN mipsel - debug builder',
        ],
      },
      'V8 Linux64 ASAN no inline - release builder': {
        'chromium_apply_config': [
          'clang',
          'v8_ninja',
          'goma',
          'asan',
          'asan_symbolized',
          'clobber',
          'default_target_d8',
          'sanitizer_coverage',
          'v8_verify_heap',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'cf_archive_build': True,
        'cf_gs_bucket': 'v8-asan',
        'cf_gs_acl': 'public-read',
        'cf_archive_name': 'd8-asan-no-inline',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 ASAN - debug builder': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'clobber',
          'default_target_d8',
          'asan',
          'goma',
          'sanitizer_coverage',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'cf_archive_build': True,
        'cf_gs_bucket': 'v8-asan',
        'cf_gs_acl': 'public-read',
        'cf_archive_name': 'd8-asan',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 ASAN arm64 - debug builder': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'clobber',
          'default_target_d8',
          'asan',
          'goma',
          'sanitizer_coverage',
          'simulate_arm',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'cf_archive_build': True,
        'cf_gs_bucket': 'v8-asan',
        'cf_gs_acl': 'public-read',
        'cf_archive_name': 'd8-arm64-asan',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux ASAN arm - debug builder': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'clobber',
          'default_target_d8',
          'asan',
          'goma',
          'sanitizer_coverage',
          'simulate_arm',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'cf_archive_build': True,
        'cf_gs_bucket': 'v8-asan',
        'cf_gs_acl': 'public-read',
        'cf_archive_name': 'd8-arm-asan',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux ASAN mipsel - debug builder': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'clobber',
          'default_target_d8',
          'asan',
          'goma',
          'sanitizer_coverage',
          'simulate_mipsel',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'cf_archive_build': True,
        'cf_gs_bucket': 'v8-asan',
        'cf_gs_acl': 'public-read',
        'cf_archive_name': 'd8-mipsel-asan',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux64 TSAN': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'tsan2', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - arm64 - sim - MSAN': {
        # 'simulate_arm' is actually implied by 'msan'. We still set it
        # explicitly for the sake of consistency.
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'msan',
          'simulate_arm',
          'goma',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - memcheck': {
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - debug builder',
        'build_gs_archive': 'linux_dbg_archive',
        'tests': [SimpleLeak],
        'testing': {'platform': 'linux'},
      },
      'V8 Mac64 ASAN': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'asan', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'mac'},
      },
####### Category: FYI
      'V8 Linux - vtunejit': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'vtunejit'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - x32 - nosnap - debug builder': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja', 'default_compiler', 'goma', 'no_snapshot', 'x32'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_x32_nosnap_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux - x32 - nosnap - debug',
        ],
      },
      'V8 Linux - x32 - nosnap - debug': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja', 'default_compiler', 'goma', 'no_snapshot', 'x32'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - x32 - nosnap - debug builder',
        'build_gs_archive': 'linux_x32_nosnap_dbg_archive',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - x87 - nosnap - debug builder': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja', 'default_compiler', 'goma', 'no_snapshot',  'x87'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder',
        'build_gs_archive': 'linux_x87_nosnap_dbg_archive',
        'testing': {'platform': 'linux'},
        'triggers': [
          'V8 Linux - x87 - nosnap - debug',
        ],
      },
      'V8 Linux - x87 - nosnap - debug': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja', 'default_compiler', 'goma', 'no_snapshot', 'x87'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'tester',
        'parent_buildername': 'V8 Linux - x87 - nosnap - debug builder',
        'build_gs_archive': 'linux_x87_nosnap_dbg_archive',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - predictable': {
        'v8_apply_config': ['predictable'],
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma', 'predictable'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit, Benchmarks, Mozilla],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - ppc - sim': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_ppc'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - ppc64 - sim': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_ppc'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Linux - full debug': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'no_optimized_debug'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'V8 Random Deopt Fuzzer - debug': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_apply_config': ['deopt_fuzz_random', 'no_exhaustive_variants'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [Deopt],
        'testing': {'platform': 'linux'},
      },
    },
  },
####### Waterfall: tryserver.v8
  'tryserver.v8': {
    'builders': {
      'v8_linux_rel': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          OptimizeForSize,
          Test262,
          Mozilla,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_avx2_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_nodcheck_rel': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_dcheck'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Benchmarks, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_greedy_allocator_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_apply_config': ['greedy_allocator', 'turbo_variant'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Benchmarks, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_nosnap_rel': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_nosnap_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'no_snapshot'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_gcc_compile_rel': {
        'chromium_apply_config': ['no_dcheck', 'no_clang'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'v8_linux_gcc_rel': {
        'chromium_apply_config': ['no_dcheck', 'no_clang'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_rel': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Initializers,
          V8Testing,
          OptimizeForSize,
          Test262,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_avx2_rel': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_avx2_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [
          V8Testing,
          SimdJs,
        ],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_greedy_allocator_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_apply_config': ['greedy_allocator', 'turbo_variant'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, Benchmarks],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_gc_stress_dbg': {
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_asan_rel': {
        # TODO(machenbach): Run with exhaustive variants as soon as bot runs
        # on swarming.
        'v8_apply_config': ['no_exhaustive_variants'],
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'asan',
          'goma',
          'no_dcheck',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_msan_rel': {
        # 'simulate_arm' is actually implied by 'msan'. We still set it
        # explicitly for the sake of consistency.
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'msan',
          'simulate_arm',
          'goma',
          'no_dcheck',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_linux64_tsan_rel': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'tsan2',
          'goma',
          'no_dcheck',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
      'v8_win_rel': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'win'},
      },
      'v8_win_dbg': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'win'},
      },
      'v8_win_compile_dbg': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'v8_win_nosnap_shared_compile_rel': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja',
          'msvs2013',
          'shared_library',
          'no_snapshot',
         ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'v8_win_nosnap_shared_rel': {
        'v8_apply_config': ['no_snapshot'],
        'chromium_apply_config': [
          'v8_ninja',
          'msvs2013',
          'no_dcheck',
          'no_snapshot',
          'shared_library',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'win'},
      },
      'v8_win64_compile_rel': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'win'},
      },
      'v8_win64_rel': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'win'},
      },
      'v8_win64_ninja_rel': {
        'chromium_apply_config': [
          'default_compiler',
          'v8_ninja',
          'goma',
          'msvs2013',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'win'},
      },
      'v8_win64_dbg': {
        'chromium_apply_config': ['v8_ninja', 'msvs2013'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'win'},
      },
      'v8_mac_rel': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'v8_mac_dbg': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'v8_mac64_rel': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'v8_mac64_dbg': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'mac'},
      },
      'v8_mac_gc_stress_dbg': {
        'chromium_apply_config': ['v8_ninja', 'clang', 'goma'],
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'mac'},
      },
      'v8_mac64_asan_rel': {
        'chromium_apply_config': [
          'v8_ninja',
          'clang',
          'asan',
          'goma',
          'no_dcheck',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'mac'},
      },
      'v8_linux_arm_rel': {
        # TODO(machenbach): Run with exhaustive variants as soon as bot runs
        # on swarming.
        'v8_apply_config': ['no_exhaustive_variants'],
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_arm_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_arm64_rel': {
        # TODO(machenbach): Run with exhaustive variants as soon as bot runs
        # on swarming.
        'v8_apply_config': ['no_exhaustive_variants'],
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_arm64_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing, SimdJs],
        'testing': {'platform': 'linux'},
      },
      'v8_linux_arm64_gc_stress_dbg': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma', 'simulate_arm'],
        'v8_apply_config': ['gc_stress'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [Mjsunit, Webkit],
        'testing': {'platform': 'linux'},
      },
      'v8_android_arm_compile_rel': {
        'gclient_apply_config': ['android'],
        'chromium_apply_config': [
          'v8_ninja',
          'default_compiler',
          'goma',
          'no_dcheck',
        ],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_ARCH': 'arm',
          'TARGET_BITS': 32,
          'TARGET_PLATFORM': 'android',
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'v8_linux_mipsel_compile_rel': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_mipsel', 'no_dcheck'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'v8_linux_mips64el_compile_rel': {
        'chromium_apply_config': [
          'clang', 'v8_ninja', 'goma', 'simulate_mipsel', 'no_dcheck'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'testing': {'platform': 'linux'},
      },
      'v8_swarming_staging': {
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 32,
        },
        'bot_type': 'builder_tester',
        'enable_swarming': True,
        'tests': [
          V8Testing,
          Test262Variants_2,
        ],
        'testing': {'platform': 'linux'},
      },
    },
  },
####### Waterfall: client.dynamorio
  'client.dynamorio': {
    'builders': {
      'linux-v8-dr': {
        'gclient_apply_config': ['dynamorio'],
        'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
        'v8_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'bot_type': 'builder_tester',
        'tests': [V8Testing],
        'testing': {'platform': 'linux'},
      },
    },
  },
}

####### Waterfall: client.v8.branches
BRANCH_BUILDERS = {}

def AddBranchBuilder(build_config, arch, bits, presubmit=False,
                     unittests_only=False):
  if unittests_only:
    tests = [Unittests]
  else:
    tests = [V8Testing, Test262, Mozilla]
  if presubmit:
    tests = [Presubmit] + tests
  config = {
    'chromium_apply_config': ['clang', 'v8_ninja', 'goma'],
    'v8_config_kwargs': {
      'BUILD_CONFIG': build_config,
      'TARGET_ARCH': arch,
      'TARGET_BITS': bits,
    },
    'bot_type': 'builder_tester',
    'tests': tests,
    'testing': {'platform': 'linux'},
  }
  if not unittests_only:
    config['gclient_apply_config'] = ['mozilla_tests']
  return config

for build_config, name_suffix in (('Release', ''), ('Debug', ' - debug')):
  for branch_name in ('stable branch', 'beta branch'):
    name = 'V8 Linux - %s%s' % (branch_name, name_suffix)
    BRANCH_BUILDERS[name] = AddBranchBuilder(
        build_config, 'intel', 32, presubmit=True)
    name = 'V8 Linux64 - %s%s' % (branch_name, name_suffix)
    BRANCH_BUILDERS[name] = AddBranchBuilder(build_config, 'intel', 64)
    name = 'V8 arm - sim - %s%s' % (branch_name, name_suffix)
    BRANCH_BUILDERS[name] = AddBranchBuilder(build_config, 'intel', 32)
    BRANCH_BUILDERS[name]['chromium_apply_config'].append('simulate_arm')

for branch_name in ('stable branch', 'beta branch'):
  name = 'V8 mipsel - sim - %s' % branch_name
  BRANCH_BUILDERS[name] = AddBranchBuilder(
      'Release', 'intel', 32, unittests_only=True)
  BRANCH_BUILDERS[name]['chromium_apply_config'].append('simulate_mipsel')

  name = 'V8 mips64el - sim - %s' % branch_name
  BRANCH_BUILDERS[name] = AddBranchBuilder(
      'Release', 'intel', 64, unittests_only=True)
  BRANCH_BUILDERS[name]['chromium_apply_config'].append('simulate_mipsel')

  name = 'V8 ppc - sim - %s' % branch_name
  BRANCH_BUILDERS[name] = AddBranchBuilder(
      'Release', 'intel', 32, unittests_only=True)
  BRANCH_BUILDERS[name]['chromium_apply_config'].append('simulate_ppc')

  name = 'V8 ppc64 - sim - %s' % branch_name
  BRANCH_BUILDERS[name] = AddBranchBuilder(
      'Release', 'intel', 64, unittests_only=True)
  BRANCH_BUILDERS[name]['chromium_apply_config'].append('simulate_ppc')

BUILDERS['client.v8.branches'] = {'builders': BRANCH_BUILDERS}

BUILDERS['client.dart.fyi'] = {'builders': {
  'v8-%s-release' % platform: {
    'chromium_apply_config': ['disassembler'],
    'v8_config_kwargs': {
      'BUILD_CONFIG': 'Release',
      'TARGET_ARCH': 'intel',
      'TARGET_BITS': 32,
    },
    'bot_type': 'builder',
    'build_gs_archive': 'v8_for_dart_archive',
    'testing': {'platform': platform},
  } for platform in ('win', 'linux', 'mac')
}}

dart_linux_release = (
  BUILDERS['client.dart.fyi']['builders']['v8-linux-release'])
dart_linux_release['chromium_apply_config'].extend(
    ['clang', 'v8_ninja', 'goma'])

dart_mac_release = BUILDERS['client.dart.fyi']['builders']['v8-mac-release']
dart_mac_release['chromium_apply_config'].extend(['v8_ninja', 'clang', 'goma'])

dart_win_release = BUILDERS['client.dart.fyi']['builders']['v8-win-release']
dart_win_release['chromium_apply_config'].extend(['v8_ninja', 'msvs2013'])

BUILDERS = freeze(BUILDERS)
BRANCH_BUILDERS = freeze(BRANCH_BUILDERS)

def iter_builders():
  for mastername, master_config in BUILDERS.iteritems():
    builders = master_config['builders']
    for buildername, bot_config in builders.iteritems():
      yield mastername, builders, buildername, bot_config
