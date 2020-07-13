# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections

from recipe_engine.types import freeze

DEPS = [
  'amp',
  'bot_update',
  'chromium',
  'chromium_android',
  'chromium_tests',
  'commit_position',
  'file',
  'gclient',
  'gpu',
  'isolate',
  'json',
  'path',
  'platform',
  'properties',
  'python',
  'raw_io',
  'step',
  'swarming',
  'test_results',
  'test_utils',
  'tryserver',
]


BUILDERS = freeze({
  'tryserver.blink': {
    'builders': {
      # TODO(joelo): Remove this builder.
      'linux_blink_rel_ng': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux',
      },
      'linux_blink_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux (dbg)',
      },
      'linux_blink_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux',
      },
      'linux_blink_compile_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux (dbg)',
        'analyze_mode': 'compile',
      },
      'linux_blink_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux',
        'analyze_mode': 'compile',
      },
      'linux_blink_oilpan_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux Oilpan (dbg)',
      },
      'linux_blink_oilpan_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux Oilpan',
      },
      'linux_blink_oilpan_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Linux Oilpan',
        'analyze_mode': 'compile',
      },
      'mac_blink_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac10.7 (dbg)',
      },
      'mac_blink_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac10.9',
      },
      'mac_blink_compile_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac10.7 (dbg)',
        'analyze_mode': 'compile',
      },
      'mac_blink_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac10.9',
        'analyze_mode': 'compile',
      },
      'mac_blink_oilpan_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac Oilpan (dbg)',
      },
      'mac_blink_oilpan_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac Oilpan',
      },
      'mac_blink_oilpan_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Mac Oilpan',
        'analyze_mode': 'compile',
      },
      'win_blink_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win7 (dbg)',
      },
      'win_blink_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win7',
      },
      'win_blink_compile_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win7 (dbg)',
        'analyze_mode': 'compile',
      },
      'win_blink_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win7',
        'analyze_mode': 'compile',
      },
      'win_blink_oilpan_dbg': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win Oilpan (dbg)',
      },
      'win_blink_oilpan_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win Oilpan',
      },
      'win_blink_oilpan_compile_rel': {
        'mastername': 'chromium.webkit',
        'buildername': 'WebKit Win Oilpan',
        'analyze_mode': 'compile',
      },
    },
  },
  'tryserver.chromium.angle': {
    'builders': {
      'linux_angle_rel_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Linux Builder (ANGLE)',
        'tester': 'Linux Tests (ANGLE)',
      },
      'linux_angle_dbg_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Linux Builder (dbg) (ANGLE)',
        'tester': 'Linux Tests (dbg) (ANGLE)',
      },
      'mac_angle_rel_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Mac Builder (ANGLE)',
        'tester': 'Mac10.8 Tests (ANGLE)',
      },
      'mac_angle_dbg_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Mac Builder (dbg) (ANGLE)',
        'tester': 'Mac10.8 Tests (dbg) (ANGLE)',
      },
      'win_angle_rel_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Win Builder (ANGLE)',
        'tester': 'Win7 Tests (ANGLE)',
      },
      'win_angle_dbg_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Win Builder (dbg) (ANGLE)',
        'tester': 'Win7 Tests (dbg) (ANGLE)',
      },
      'win_angle_x64_rel_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Win x64 Builder (ANGLE)',
        'tester': 'Win7 Tests x64 (ANGLE)',
      },
      'win_angle_x64_dbg_ng': {
        'mastername': 'chromium.angle',
        'buildername': 'Win x64 Builder (dbg) (ANGLE)',
        'tester': 'Win7 Tests x64 (dbg) (ANGLE)',
      },
    },
  },
  'tryserver.chromium.linux': {
    'builders': {
      'android_amp': {
        'mastername': 'chromium.fyi',
        'buildername': 'Android Tests (amp split)',
      },
      'android_arm64_dbg_recipe': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Arm64 Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'android_clang_dbg_recipe': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Clang Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'android_chromium_gn_compile_dbg': {
        'mastername': 'chromium.linux',
        'buildername': 'Android GN (dbg)',
        'analyze_mode': 'compile',
      },
      'android_chromium_gn_compile_rel': {
        'mastername': 'chromium.linux',
        'buildername': 'Android GN',
        'analyze_mode': 'compile',
      },
      'android_compile_dbg': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'android_compile_rel': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Builder',
        'analyze_mode': 'compile',
      },
      'android_coverage': {
        'mastername': 'chromium.fyi',
        'buildername': 'Android Coverage (dbg)'
      },
      'cast_shell_linux': {
        'mastername': 'chromium.linux',
        'buildername': 'Cast Linux',
      },
      'cast_shell_android': {
        'mastername': 'chromium.linux',
        'buildername': 'Cast Android (dbg)',
      },
      'linux_android_dbg_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Builder (dbg)',
        'tester': 'Android Tests (dbg)',
      },
      'linux_android_rel_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Android Builder',
        'tester': 'Android Tests',
      },
      'linux_arm_compile': {
        'mastername': 'chromium.fyi',
        'buildername': 'Linux ARM',
        'analyze_mode': 'compile',
      },
      'linux_chromium_dbg_32_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder (dbg)(32)',
        'tester': 'Linux Tests (dbg)(1)(32)',
      },
      'linux_chromium_dbg_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder (dbg)',
        'tester': 'Linux Tests (dbg)(1)',
      },
      'linux_chromium_gn_chromeos_dbg': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS GN (dbg)',
      },
      'linux_chromium_gn_chromeos_rel': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS GN',
      },
      'linux_chromium_rel_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder',
        'tester': 'Linux Tests',
      },
      'linux_chromium_asan_rel_ng': {
        'mastername': 'chromium.memory',
        'buildername': 'Linux ASan LSan Builder',
        'tester': 'Linux ASan LSan Tests (1)',
      },
      'linux_chromium_compile_dbg_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'linux_chromium_compile_rel_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder',
        'analyze_mode': 'compile',
      },
      'linux_chromium_clobber_rel_ng': {
        'mastername': 'chromium.fyi',
        'buildername': 'Linux Builder (clobber)',
        'analyze_mode': 'compile',
      },
      'linux_chromium_chromeos_dbg_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS Builder (dbg)',
        'tester': 'Linux ChromiumOS Tests (dbg)(1)',
      },
      'linux_chromium_chromeos_compile_dbg_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'linux_chromium_chromeos_rel_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS Builder',
        'tester': 'Linux ChromiumOS Tests (1)',
      },
      'linux_chromium_chromeos_asan_rel_ng': {
        'mastername': 'chromium.memory',
        'buildername': 'Linux Chromium OS ASan LSan Builder',
        'tester': 'Linux Chromium OS ASan LSan Tests (1)',
      },
      'chromeos_x86-generic_chromium_compile_only_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'ChromiumOS x86-generic Compile',
        'analyze_mode': 'compile',
      },
      'chromeos_amd64-generic_chromium_compile_only_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'ChromiumOS amd64-generic Compile',
        'analyze_mode': 'compile',
      },
      'chromeos_daisy_chromium_compile_only_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'ChromiumOS daisy Compile',
        'analyze_mode': 'compile',
      },
      'linux_chromium_chromeos_compile_rel_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS Builder',
        'analyze_mode': 'compile',
      },
      'linux_chromium_chromeos_msan_rel_ng': {
        'mastername': 'chromium.memory.fyi',
        'buildername': 'Chromium Linux ChromeOS MSan Builder',
        'tester': 'Linux ChromeOS MSan Tests',
      },
      'linux_chromium_chromeos_ozone_rel_ng': {
        'mastername': 'chromium.chromiumos',
        'buildername': 'Linux ChromiumOS Ozone Builder',
        'tester': 'Linux ChromiumOS Ozone Tests (1)',
      },
      'linux_chromium_compile_dbg_32_ng': {
        'mastername': 'chromium.linux',
        'buildername': 'Linux Builder (dbg)(32)',
        'analyze_mode': 'compile',
      },
      'linux_chromium_msan_rel_ng': {
        'mastername': 'chromium.memory.fyi',
        'buildername': 'Chromium Linux MSan Builder',
        'tester': 'Linux MSan Tests',
      },
      'linux_chromium_tsan_rel_ng': {
        'mastername': 'chromium.memory.fyi',
        'buildername': 'Chromium Linux TSan Builder',
        'tester': 'Linux TSan Tests',
      },
      'linux_chromium_cfi_rel_ng': {
        'mastername': 'chromium.fyi',
        'buildername': 'CFI Linux',
      },
      'linux_site_isolation': {
        'mastername': 'chromium.fyi',
        'buildername': 'Site Isolation Linux',
      },
      'linux_chromium_practice_rel_ng': {
        'mastername': 'chromium.fyi',
        'buildername': 'ChromiumPracticeFullTester',
      },
    },
  },
  'tryserver.chromium.mac': {
    'builders': {
      'mac_chromium_dbg_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder (dbg)',
        'tester': 'Mac10.9 Tests (dbg)',
      },
      'mac_chromium_gn_dbg': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac GN (dbg)',
      },
      'mac_chromium_gn_rel': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac GN',
      },
      'mac_chromium_rel_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder',
        'tester': 'Mac10.8 Tests',
      },
      'mac_chromium_10.6_rel_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder',
        'tester': 'Mac10.6 Tests',
      },
      'mac_chromium_10.10_rel_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder',
        'tester': 'Mac10.10 Tests',
      },
      'mac_chromium_compile_dbg_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'mac_chromium_compile_rel_ng': {
        'mastername': 'chromium.mac',
        'buildername': 'Mac Builder',
        'analyze_mode': 'compile',
      },
      'mac_chromium_asan_rel_ng': {
        'mastername': 'chromium.memory',
        'buildername': 'Mac ASan 64 Builder',
        'tester': 'Mac ASan 64 Tests (1)',
      },
    },
  },
  'tryserver.chromium.win': {
    'builders': {
      'win_chromium_dbg_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder (dbg)',
        'tester': 'Win7 Tests (dbg)(1)',
      },
      'win_chromium_gn_x64_dbg': {
        'mastername': 'chromium.win',
        'buildername': 'Win x64 GN (dbg)',
      },
      'win_chromium_gn_x64_rel': {
        'mastername': 'chromium.win',
        'buildername': 'Win x64 GN',
      },
      'win_chromium_rel_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder',
        'tester': 'Win7 Tests (1)',
      },
      'win_chromium_rel_ng_exp': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder',
        'tester': 'Win7 Tests (1)',
      },
      'win_chromium_xp_rel_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder',
        'tester': 'XP Tests (1)',
      },
      'win_chromium_vista_rel_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder',
        'tester': 'Vista Tests (1)',
      },
      'win_chromium_compile_dbg_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'win_chromium_compile_dbg_ng_exp': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder (dbg)',
        'analyze_mode': 'compile',
      },
      'win_chromium_compile_rel_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win Builder',
        'analyze_mode': 'compile',
      },
      'win_chromium_x64_rel_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win x64 Builder',
        'tester': 'Win 7 Tests x64 (1)',
      },
      'win_chromium_x64_rel_ng_exp': {
        'mastername': 'chromium.win',
        'buildername': 'Win x64 Builder',
        'tester': 'Win 7 Tests x64 (1)',
      },
      'win8_chromium_ng': {
        'mastername': 'chromium.win',
        'buildername': 'Win8 Aura',
      },
      'win8_chromium_gn_dbg': {
        'mastername': 'chromium.win',
        'buildername': 'Win8 GN (dbg)',
      },
      # Experimental clang/win bots.
      'win_clang_dbg': {
        'mastername': 'chromium.fyi',
        'buildername': 'CrWinClang(dbg)',
      },
      'win_clang_rel': {
        'mastername': 'chromium.fyi',
        'buildername': 'CrWinClang',
      },
      'win_clang_x64_dbg': {
        'mastername': 'chromium.fyi',
        'buildername': 'CrWinClang64(dbg)',
      },
      'win_clang_x64_rel': {
        'mastername': 'chromium.fyi',
        'buildername': 'CrWinClang64',
      },
    },
  },
})

# TODO(sergiyb): This config should be read from an external JSON file
# in a custom step, which can then be mocked in the GenTests.
CHROMIUM_GPU_DIMENSION_SETS = freeze({
  'tryserver.chromium.angle': {
    'linux_angle_rel_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Linux',
      },
    ],
    'linux_angle_dbg_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Linux',
      },
    ],
    'mac_angle_rel_ng': [
      {
        'gpu': '8086:0116',  # Intel HD Graphics 3000
        'hidpi': '0',
        'os': 'Mac-10.8',
      }, {
        'gpu': '10de:0fe9',  # NVIDIA GeForce GT 750M
        'hidpi': '1',
        'os': 'Mac-10.9',
      },
    ],
    'mac_angle_dbg_ng': [
      {
        'gpu': '8086:0116',  # Intel HD Graphics 3000
        'hidpi': '0',
        'os': 'Mac-10.8',
      }, {
        'gpu': '10de:0fe9',  # NVIDIA GeForce GT 750M
        'hidpi': '1',
        'os': 'Mac-10.9',
      },
    ],
    'win_angle_rel_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Windows',
      }, {
        'gpu': '1002:6779',  # AMD Radeon HD 6450
        'os': 'Windows',
      },
    ],
    'win_angle_dbg_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Windows',
      }, {
        'gpu': '1002:6779',  # AMD Radeon HD 6450
        'os': 'Windows',
      },
    ],
    'win_angle_x64_rel_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Windows',
      }, {
        'gpu': '1002:6779',  # AMD Radeon HD 6450
        'os': 'Windows',
      },
    ],
    'win_angle_x64_dbg_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Windows',
      }, {
        'gpu': '1002:6779',  # AMD Radeon HD 6450
        'os': 'Windows',
      },
    ],
  },
  'tryserver.chromium.linux': {
    'linux_chromium_rel_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Linux',
      },
    ],
  },
  'tryserver.chromium.mac': {
    'mac_chromium_rel_ng': [
      {
        'gpu': '8086:0116',  # Intel HD Graphics 3000
        'hidpi': '0',
        'os': 'Mac-10.8',
      }, {
        'gpu': '10de:0fe9',  # NVIDIA GeForce GT 750M
        'hidpi': '1',
        'os': 'Mac-10.9',
      },
    ],
  },
  'tryserver.chromium.win': {
    'win_chromium_rel_ng': [
      {
        'gpu': '10de:104a',  # NVIDIA GeForce GT 610
        'os': 'Windows',
      }, {
        'gpu': '1002:6779',  # AMD Radeon HD 6450
        'os': 'Windows',
      },
    ],
  },
})


# TODO(phajdan.jr): Remove special case for layout tests.
# This could be done by moving layout tests to main waterfall.
CHROMIUM_BLINK_TESTS_BUILDERS = freeze([
  'linux_chromium_rel_ng',
  'mac_chromium_rel_ng',
  'win_chromium_rel_ng',
])


def tests_in_compile_targets(api, compile_targets, tests):
  """Returns the tests in |tests| that have at least one of their compile
  targets in |compile_targets|."""
  result = []
  for test in tests:
    test_compile_targets = test.compile_targets(api)

    # Always return tests that don't require compile. Otherwise we'd never
    # run them.
    if ((set(compile_targets).intersection(set(test_compile_targets))) or
        not test_compile_targets):
      result.append(test)

  return result


def all_compile_targets(api, tests):
  """Returns the compile_targets for all the Tests in |tests|."""
  return sorted(set(x
                    for test in tests
                    for x in test.compile_targets(api)))


def is_source_file(api, filepath):
  """Returns true iff the file is a source file."""
  _, ext = api.path.splitext(filepath)
  return ext in ['.c', '.cc', '.cpp', '.h', '.java', '.mm']

def _RunStepsInternal(api):
  def get_bot_config(mastername, buildername):
    master_dict = BUILDERS.get(mastername, {})
    return master_dict.get('builders', {}).get(buildername)

  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')
  bot_config = get_bot_config(mastername, buildername)
  api.chromium_tests.configure_swarming('chromium', precommit=True)

  # TODO(sergiyb): This is a temporary hack to run GPU tests on tryserver
  # only. This should be removed when we will convert chromium.gpu waterfall
  # to swarming and be able to replicate the tests to tryserver automatically.
  master = api.properties['mastername']
  builder = api.properties['buildername']
  enable_gpu_tests = builder in CHROMIUM_GPU_DIMENSION_SETS.get(master, {})

  api.chromium_tests.configure_build(
      bot_config['mastername'],
      bot_config['buildername'],
      override_bot_type='builder_tester')

  api.chromium.apply_config('trybot_flavor')
  if enable_gpu_tests:
    api.chromium.apply_config('archive_gpu_tests')

  if api.properties.get('patch_project') == 'blink':  # pragma: no cover
    raise Exception('CLs which use blink project are not supported. '
                    'Please re-create the CL using fresh checkout after '
                    'the blink merge.')

  bot_update_step, master_dict, test_spec = \
      api.chromium_tests.prepare_checkout(
          bot_config['mastername'],
          bot_config['buildername'])

  tests = list(api.chromium_tests.tests_for_builder(
      bot_config['mastername'],
      bot_config['buildername'],
      bot_update_step,
      master_dict,
      override_bot_type='builder_tester'))
  tester = bot_config.get('tester', '')
  if tester:
    test_config = master_dict.get('builders', {}).get(tester)
    for key, value in test_config.get('swarming_dimensions', {}).iteritems():
      api.swarming.set_default_dimension(key, value)
    tests.extend(api.chromium_tests.tests_for_builder(
        bot_config['mastername'],
        tester,
        bot_update_step,
        master_dict,
        override_bot_type='builder_tester'))

  if enable_gpu_tests:
    tests.extend(api.gpu.create_tests(
        bot_update_step.presentation.properties['got_revision'],
        bot_update_step.presentation.properties['got_revision'],
        enable_swarming=True,
        swarming_dimension_sets=CHROMIUM_GPU_DIMENSION_SETS[master][builder]))

  affected_files = api.tryserver.get_files_affected_by_patch()

  # TODO(phajdan.jr): Remove special case for layout tests.
  add_blink_tests = False
  if (any([f.startswith('third_party/WebKit') for f in affected_files]) and
      buildername in CHROMIUM_BLINK_TESTS_BUILDERS):
    add_blink_tests = True

  # Add blink tests that work well with "analyze" here. The tricky ones
  # that bypass it (like the layout tests) are added later.
  if add_blink_tests:
    tests.extend([
        api.chromium_tests.steps.GTestTest('blink_heap_unittests'),
        api.chromium_tests.steps.GTestTest('blink_platform_unittests'),
        api.chromium_tests.steps.GTestTest('webkit_unit_tests'),
        api.chromium_tests.steps.GTestTest('wtf_unittests'),
    ])

  compile_targets, tests_including_triggered = \
      api.chromium_tests.get_compile_targets_and_tests(
          bot_config['mastername'],
          bot_config['buildername'],
          master_dict,
          test_spec,
          override_bot_type='builder_tester',
          override_tests=tests)

  targets_for_analyze = sorted(set(
      all_compile_targets(api, tests + tests_including_triggered) +
      compile_targets))
  requires_compile, matching_exes, compile_targets = \
      api.chromium_tests.analyze(
          affected_files,
          targets_for_analyze,
          compile_targets,
          'trybot_analyze_config.json')
  if bot_config.get('analyze_mode') == 'compile':
    # Do not pass tests to compile_specific_targets below. It assumes
    # all of these targets would be compiled (matching_exes), but that's
    # explicitly what we're not doing here. One example of how this may
    # break is only foo_unittests being in returned compile_targets,
    # while matching_exes containing both foo_unittests and foo_unittests_run.
    # Then if we proceed with tests passed to compile_specific_targets,
    # trying to isolate affected targets may fail.
    tests = []
    tests_including_triggered = []

    # TODO(sergiyb): This is a hotfix that should be removed if we implement a
    # proper long-term fix. It is currently needed to ensure that we compile
    # matching_exes also when a bot is running analyze in compile-only mode and
    # stay consistent with corresponding waterfall bots. Please see
    # http://crbug.com/529798 for more context.
    compile_targets = sorted(set(compile_targets + matching_exes))
  else:
    # Note that compile_targets doesn't necessarily include matching_exes,
    # and for correctness we need to add them. Otherwise it's possible we'd
    # only build say foo_unittests but not foo_unittests_run and fail when
    # trying to isolate the tests. Also see above comment.
    compile_targets = sorted(set(compile_targets + matching_exes))

  # Blink tests have to bypass "analyze", see below.
  if requires_compile or add_blink_tests:
    tests = tests_in_compile_targets(api, matching_exes, tests)
    tests_including_triggered = tests_in_compile_targets(
        api, matching_exes, tests_including_triggered)

    # Blink tests are tricky at this moment. We'd like to use "analyze" for
    # everything else. However, there are blink changes that only add or modify
    # layout test files (html etc). This is not recognized by "analyze" as
    # compile dependency. However, the blink tests should still be executed.
    if add_blink_tests:
      blink_tests = [
          api.chromium_tests.steps.ScriptTest(
              'webkit_lint', 'webkit_lint.py', collections.defaultdict(list)),
          api.chromium_tests.steps.ScriptTest(
              'webkit_python_tests', 'webkit_python_tests.py',
              collections.defaultdict(list)),
          api.chromium_tests.steps.BlinkTest(),
      ]
      tests.extend(blink_tests)
      tests_including_triggered.extend(blink_tests)
      for test in blink_tests:
        compile_targets.extend(test.compile_targets(api))
      compile_targets = sorted(set(compile_targets))

    api.chromium_tests.compile_specific_targets(
        bot_config['mastername'],
        bot_config['buildername'],
        bot_update_step,
        master_dict,
        compile_targets,
        tests_including_triggered,
        override_bot_type='builder_tester')
  else:
    # Even though the patch doesn't compile on this platform, we'd still like
    # to run tests not depending on compiled targets (that's obviously not
    # covered by the 'analyze' step) if any source files change.
    if any([is_source_file(api, f) for f in affected_files]):
      tests = [t for t in tests if not t.compile_targets(api)]
    else:
      return

  if not tests:
    return

  api.chromium_tests.run_tests_and_deapply_as_needed(mastername, api, tests,
                                                     bot_update_step)


def RunSteps(api):
  # build/tests/masters_recipes_tests.py needs to manipulate the BUILDERS
  # dict, so we provide an API to dump it here.
  if api.properties.get('dump_builders'):  # pragma: no cover
    api.file.copy('Dump BUILDERS dict',
        api.json.input(BUILDERS), api.properties['dump_builders'])
    return

  with api.tryserver.set_failure_hash():
    return _RunStepsInternal(api)


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text)


def GenTests(api):
  canned_test = api.test_utils.canned_gtest_output

  def props(config='Release', mastername='tryserver.chromium.linux',
            buildername='linux_chromium_rel_ng', extra_swarmed_tests=None,
            **kwargs):
    kwargs.setdefault('revision', None)
    swarm_hashes = api.gpu.get_dummy_swarm_hashes_for_trybot(mastername)
    if extra_swarmed_tests:
      for test in extra_swarmed_tests:
        swarm_hashes[test] = '[dummy hash for %s]' % test
    return api.properties.tryserver(
      build_config=config,
      mastername=mastername,
      buildername=buildername,
      swarm_hashes=swarm_hashes,
      **kwargs
    )

  def suppress_analyze():
    """Overrides analyze step data so that all targets get compiled."""
    return api.override_step_data(
        'read filter exclusion spec',
        api.json.output({
            'base': {
                'exclusions': ['f.*'],
            },
            'chromium': {
                'exclusions': [],
            },
        })
    )

  # While not strictly required for coverage, record expectations for each
  # of the configs so we can see when and how they change.
  for mastername, master_config in BUILDERS.iteritems():
    for buildername, bot_config in master_config['builders'].iteritems():
      for analyze in ['', '_analyze']:
        test_name = 'full_%s_%s%s' % (_sanitize_nonalpha(mastername),
                                      _sanitize_nonalpha(buildername),
                                      analyze)
        yield (
          api.test(test_name) +
          api.chromium_tests.platform(
              bot_config['mastername'], bot_config['buildername']) +
          (api.empty_test_data() if analyze else suppress_analyze()) +
          props(mastername=mastername, buildername=buildername)
        )

  # Additional tests for blink trybots.
  blink_trybots = BUILDERS['tryserver.blink']['builders'].iteritems()
  for buildername, bot_config in blink_trybots:
    if bot_config.get('analyze_mode') == 'compile':
      continue

    for pass_first in (True, False):
      test_name = 'full_%s_%s_%s' % (_sanitize_nonalpha('tryserver.blink'),
                                     _sanitize_nonalpha(buildername),
                                     'pass' if pass_first else 'fail')
      test = (api.test(test_name) +
              suppress_analyze() +
              props(mastername='tryserver.blink',
                    buildername=buildername) +
              api.chromium_tests.platform(
                  bot_config['mastername'], bot_config['buildername']) +
              api.override_step_data('webkit_tests (with patch)',
                  api.test_utils.canned_test_output(passing=pass_first)))
      if not pass_first:
        test += api.override_step_data('webkit_tests (without patch)',
            api.test_utils.canned_test_output(passing=False, minimal=True))
      yield test

  # Regression test for http://crbug.com/453471#c16
  yield (
    api.test('clobber_analyze') +
    props(buildername='linux_chromium_clobber_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency', 'targets': [],
                       'build_targets': ['base_unittests', 'net_unittests']}))
  )

  # Do not fail the build if process_dumps fails.
  # http://crbug.com/520660
  yield (
    api.test('process_dumps_failure') +
    props(mastername='tryserver.chromium.win',
          buildername='win_chromium_rel_ng') +
    api.platform.name('win') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': ['blink_tests'],
    })) +
    suppress_analyze() +
    api.override_step_data('process_dumps', retcode=1)
  )

  yield (
    api.test('invalid_results') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
            'gtest_tests': ['base_unittests'],
        },
    })) +
    suppress_analyze() +
    api.override_step_data('base_unittests (with patch)',
                           canned_test(passing=False)) +
    api.override_step_data('base_unittests (without patch)',
                           api.test_utils.raw_gtest_output(None, retcode=1))
  )

  yield (
    api.test('swarming_trigger_failure') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
            'gtest_tests': [
                {
                  'test': 'base_unittests',
                  'swarming': {'can_use_on_swarming_builders': True},
                },
            ],
        },
    })) +
    suppress_analyze()
  )

  yield (
    api.test('swarming_test_failure') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
            'gtest_tests': [
                {
                  'test': 'gl_tests',
                  'swarming': {'can_use_on_swarming_builders': True},
                },
            ],
        },
    })) +
    suppress_analyze() +
    api.override_step_data('gl_tests (with patch)',
                           canned_test(passing=False))
  )

  yield (
    api.test('amp_test_failure') +
    props(buildername='android_amp',
          mastername='tryserver.chromium.linux') +
    api.platform.name('linux') +
    suppress_analyze() +
    api.override_step_data('[collect] base_unittests (with patch)',
                           canned_test(passing=False), retcode=1)
  )

  yield (
    api.test('amp_test_local_fallback') +
    props(buildername='android_amp',
          mastername='tryserver.chromium.linux') +
    api.platform.name('linux') +
    suppress_analyze() +
    api.override_step_data('[trigger] base_unittests (with patch)',
                           retcode=1)
  )

  yield (
    api.test('amp_test_local_fallback_failure') +
    props(buildername='android_amp',
          mastername='tryserver.chromium.linux') +
    api.platform.name('linux') +
    suppress_analyze() +
    api.override_step_data('[trigger] base_unittests (with patch)',
                           retcode=1) +
    api.override_step_data('base_unittests (with patch)',
                           canned_test(passing=False), retcode=1)
  )

  yield (
    api.test('compile_failure_without_patch_deapply_fn') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
          'gtest_tests': ['base_unittests'],
        },
      })
    ) +
    suppress_analyze() +
    api.override_step_data('base_unittests (with patch)',
                           canned_test(passing=False)) +
    api.override_step_data('compile (without patch)', retcode=1)
  )

  for step in ('bot_update', 'gclient runhooks (with patch)'):
    yield (
      api.test(_sanitize_nonalpha(step) + '_failure') +
      props() +
      api.platform.name('linux') +
      api.step_data(step, retcode=1)
    )

  yield (
    api.test('runhooks_failure') +
    props(buildername='win_chromium_rel_ng',
          mastername='tryserver.chromium.win') +
    api.platform.name('win') +
    api.step_data('gclient runhooks (with patch)', retcode=1) +
    api.step_data('gclient runhooks (without patch)', retcode=1)
  )

  yield (
    api.test('runhooks_failure_ng') +
    api.platform('linux', 64) +
    props(mastername='tryserver.chromium.linux',
          buildername='linux_chromium_rel_ng') +
    api.step_data('gclient runhooks (with patch)', retcode=1)
  )

  yield (
    api.test('compile_failure_ng') +
    api.platform('linux', 64) +
    props(mastername='tryserver.chromium.linux',
          buildername='linux_chromium_rel_ng') +
    suppress_analyze() +
    api.step_data('compile (with patch)', retcode=1)
  )

  yield (
    api.test('compile_failure_without_patch_ng') +
    api.platform('linux', 64) +
    props(mastername='tryserver.chromium.linux',
          buildername='linux_chromium_rel_ng') +
    suppress_analyze() +
    api.step_data('compile (with patch)', retcode=1) +
    api.step_data('compile (without patch)', retcode=1)
  )

  yield (
    api.test('check_swarming_version_failure') +
    props() +
    api.platform.name('linux') +
    api.step_data('swarming.py --version', retcode=1) +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
          'gtest_tests': ['base_unittests'],
        },
      })
      ) +
    suppress_analyze()
  )

  # Successfully compiling, isolating and running two targets on swarming for a
  # commit queue job.
  yield (
    api.test('swarming_basic_cq') +
    props(requester='commit-bot@chromium.org', blamelist='joe@chromium.org',
          blamelist_real=['joe@chromium.org'],
          extra_swarmed_tests=['base_unittests', 'browser_tests']) +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
            'swarming': {
              'can_use_on_swarming_builders': True,
              'shards': 5,
              'platforms': ['linux'],
            },
          },
        ],
      })
    ) +
    suppress_analyze()
  )

  # Successfully compiling, isolating and running two targets on swarming for a
  # manual try job.
  yield (
    api.test('swarming_basic_try_job') +
    props(buildername='linux_chromium_rel_ng', requester='joe@chromium.org',
          extra_swarmed_tests=['base_unittests', 'browser_tests']) +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
            'swarming': {
              'can_use_on_swarming_builders': True,
              'shards': 5,
              'platforms': ['linux'],
            },
          },
        ],
      })
    ) +
    suppress_analyze()
  )

  # One target (browser_tests) failed to produce *.isolated file.
  yield (
    api.test('swarming_missing_isolated') +
    props(requester='commit-bot@chromium.org', blamelist='joe@chromium.org',
          blamelist_real=['joe@chromium.org'],
          extra_swarmed_tests=['base_unittests']) +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
        ],
      })
    ) +
    suppress_analyze()
  )

  yield (
    api.test('no_compile_because_of_analyze') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({}))
  )

  # Verifies analyze skips projects other than src.
  yield (
    api.test('dont_analyze_for_non_src_project') +
    props(buildername='linux_chromium_rel_ng') +
    props(patch_project='v8') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({}))
  )

  # This should result in a compile.
  yield (
    api.test('compile_because_of_analyze_matching_exclusion') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({})) +
    suppress_analyze()
  )

  # This should result in a compile.
  yield (
    api.test('compile_because_of_analyze') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({})) +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency', 'targets': [],
                       'build_targets': []}))
  )

  yield (
    api.test('compile_because_of_analyze_with_filtered_tests_no_builder') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
          },
          {
            'test': 'unittests',
          },
        ],
      })
    ) +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency',
                       'targets': ['browser_tests', 'base_unittests'],
                       'build_targets': ['browser_tests', 'base_unittests']}))
  )

  yield (
    api.test('compile_because_of_analyze_with_filtered_tests') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
          },
          {
            'test': 'unittests',
          },
        ],
      })
    ) +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency',
                       'targets': ['browser_tests', 'base_unittests'],
                       'build_targets': ['browser_tests', 'base_unittests']}))
  )

  # Tests compile_target portion of analyze module.
  yield (
    api.test('compile_because_of_analyze_with_filtered_compile_targets') +
    props(buildername='linux_chromium_rel_ng') +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'gtest_tests': [
          {
            'test': 'base_unittests',
            'swarming': {'can_use_on_swarming_builders': True},
          },
          {
            'test': 'browser_tests',
          },
          {
            'test': 'unittests',
          },
        ],
      })
    ) +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency',
                       'targets': ['browser_tests', 'base_unittests'],
                       'build_targets': ['chrome', 'browser_tests',
                                         'base_unittests']}))
  )

  # Tests compile_targets portion of analyze with a bot that doesn't include the
  # 'all' target.
  yield (
    api.test(
      'compile_because_of_analyze_with_filtered_compile_targets_exclude_all') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
          'gtest_tests': ['base_unittests'],
        },
      })
    ) +
    api.override_step_data(
      'analyze',
      api.json.output({'status': 'Found dependency',
                       'targets': ['browser_tests', 'base_unittests'],
                       'build_targets': ['base_unittests']}))
  )

  # Tests compile_targets portion of analyze with a bot that doesn't include the
  # 'all' target.
  yield (
    api.test(
      'analyze_finds_invalid_target') +
    props() +
    api.platform.name('linux') +
    api.override_step_data('read test spec', api.json.output({
        'Linux Tests': {
          'gtest_tests': ['base_unittests'],
        },
      })
    ) +
    api.override_step_data(
      'analyze',
      api.json.output({'invalid_targets': ['invalid target', 'another one']}))
  )

  gpu_targets = ['angle_unittests_run', 'chrome', 'chromium_builder_tests',
                 'content_gl_tests_run', 'gl_tests_run',
                 'tab_capture_end2end_tests_run', 'telemetry_gpu_test_run']
  yield (
    api.test('gpu_tests') +
    props(
      mastername='tryserver.chromium.mac',
      buildername='mac_chromium_rel_ng',
    ) +
    api.platform.name('mac') +
    api.override_step_data(
        'pixel_test on Intel GPU on Mac (with patch)',
        api.test_utils.canned_telemetry_gpu_output(
            passing=False, is_win=False, swarming=True)) +
    api.override_step_data(
        'pixel_test on Intel GPU on Mac (without patch)',
        api.test_utils.canned_telemetry_gpu_output(
            passing=False, is_win=False, swarming=True)) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )

  yield (
    api.test('telemetry_gpu_no_summary') +
    props(
      mastername='tryserver.chromium.mac',
      buildername='mac_chromium_rel_ng',
    ) +
    api.platform.name('mac') +
    api.override_step_data('pixel_test on Intel GPU on Mac (with patch)',
                           api.raw_io.output_dir({'0/results.json': ''})) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )

  yield (
    api.test('telemetry_gpu_harness_failure') +
    props(
      mastername='tryserver.chromium.linux',
      buildername='linux_chromium_rel_ng',
    ) +
    api.platform.name('linux') +
    api.override_step_data(
        'maps_pixel_test on NVIDIA GPU on Linux (with patch) on Linux',
        api.test_utils.canned_telemetry_gpu_output(
            passing=False, is_win=False, swarming=True,
            empty_per_page_values=True),
        retcode=255) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )

  yield (
    api.test('telemetry_gpu_swarming_error') +
    props(
      mastername='tryserver.chromium.mac',
      buildername='mac_chromium_rel_ng',
    ) +
    api.platform.name('mac') +
    api.override_step_data(
        'pixel_test on Intel GPU on Mac (with patch)',
        api.raw_io.output_dir({
          '0/results.json': '',
          'summary.json': '{"shards": [{"internal_failure": true}]}'
        })) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )

  yield (
    api.test('telemetry_gpu_no_results') +
    props(
      mastername='tryserver.chromium.mac',
      buildername='mac_chromium_rel_ng',
    ) +
    api.platform.name('mac') +
    api.override_step_data(
        'pixel_test on Intel GPU on Mac (with patch)',
        api.raw_io.output_dir({
          '0/results.json': '',
          'summary.json': '{"shards": [{"internal_failure": false}]}'
        })) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )

  yield (
    api.test('telemetry_gpu_with_results_but_bad_exit_code') +
    props(
      mastername='tryserver.chromium.mac',
      buildername='mac_chromium_rel_ng',
    ) +
    api.platform.name('mac') +
    # passing=True, but exit code != 0.
    api.override_step_data(
        'pixel_test on Intel GPU on Mac (with patch)',
        api.test_utils.canned_telemetry_gpu_output(
            passing=True, is_win=False, swarming=True),
        retcode=255
    ) +
    api.override_step_data('analyze',
                           api.json.output({'status': 'Found dependency',
                                            'targets': gpu_targets,
                                            'build_targets': gpu_targets}))
  )


  yield (
    api.test('use_v8_patch_on_chromium_trybot') +
    props(buildername='win_chromium_rel_ng',
          mastername='tryserver.chromium.win',
          patch_project='v8') +
    api.platform.name('win')
  )


  # Tests that we only run the angle_unittests isolate if that's all
  # that analyze said to rebuild.
  all_hashes = api.gpu.dummy_swarm_hashes
  angle_unittests_hash = {x: all_hashes[x] for x in ['angle_unittests']}
  yield (
    api.test('analyze_runs_only_angle_unittests') +
    api.properties.tryserver(
      mastername='tryserver.chromium.win',
      buildername='win_chromium_rel_ng',
      swarm_hashes=angle_unittests_hash
    ) +
    api.platform.name('win') +
    api.override_step_data('analyze', api.gpu.analyze_builds_angle_unittests)
  )

  yield (
    api.test('swarming_time_out_is_handled_correctly') +
    api.properties.tryserver(
      mastername='tryserver.chromium.win',
      buildername='win_chromium_rel_ng'
    ) +
    api.platform.name('win') +
    api.override_step_data('analyze', api.gpu.analyze_builds_pixel_test) +
    api.override_step_data('pixel_test on NVIDIA GPU on Windows (with patch) '
                           'on Windows', api.raw_io.output_dir({}))
  )

  # Tests that we run nothing if analyze said we didn't have to run anything.
  yield (
    api.test('analyze_runs_nothing') +
    api.properties.tryserver(
      mastername='tryserver.chromium.win',
      buildername='win_chromium_rel_ng',
      swarm_hashes={}
    ) +
    api.platform.name('win') +
    api.override_step_data('analyze', api.gpu.analyze_builds_nothing)
  )

  # Tests that we run nothing if analyze said we didn't have to run anything
  # and there were no source file changes.
  yield (
    api.test('analyze_runs_nothing_with_no_source_file_changes') +
    api.properties.tryserver(
      mastername='tryserver.chromium.win',
      buildername='win_chromium_rel_ng',
      swarm_hashes={}
    ) +
    api.platform.name('win') +
    api.override_step_data('analyze', api.gpu.analyze_builds_nothing) +
    api.override_step_data(
        'git diff to analyze patch',
        api.raw_io.stream_output('README.md\nfoo/bar/baz.py')
    )
  )

  yield (
    api.test('analyze_webkit') +
    api.properties.tryserver(
      mastername='tryserver.chromium.win',
      buildername='win_chromium_rel_ng',
      swarm_hashes={}
    ) +
    api.platform.name('win') +
    api.override_step_data(
        'git diff to analyze patch',
        api.raw_io.stream_output(
            'third_party/WebKit/Source/core/dom/Element.cpp\n')
    )
  )

  yield (
    api.test('swarming_paths') +
    api.properties.tryserver(
      mastername='tryserver.chromium.linux',
      buildername='linux_chromium_rel_ng',
      path_config='swarming',
    ) +
    api.platform.name('linux')
  )

  # This tests that if the first fails, but the second pass succeeds
  # that we fail the whole build.
  yield (
    api.test('blink_minimal_pass_continues') +
    props(mastername='tryserver.blink',
          buildername='linux_blink_rel') +
    suppress_analyze() +
    api.platform.name('linux') +
    api.override_step_data('webkit_tests (with patch)',
        api.test_utils.canned_test_output(passing=False)) +
    api.override_step_data('webkit_tests (without patch)',
        api.test_utils.canned_test_output(passing=True, minimal=True))
  )

  # This tests what happens if something goes horribly wrong in
  # run-webkit-tests and we return an internal error; the step should
  # be considered a hard failure and we shouldn't try to compare the
  # lists of failing tests.
  # 255 == test_run_results.UNEXPECTED_ERROR_EXIT_STATUS in run-webkit-tests.
  yield (
    api.test('webkit_tests_unexpected_error') +
    props(mastername='tryserver.blink',
          buildername='linux_blink_rel') +
    suppress_analyze() +
    api.platform.name('linux') +
    api.override_step_data('webkit_tests (with patch)',
        api.test_utils.canned_test_output(passing=False, retcode=255))
  )

  # TODO(dpranke): crbug.com/357866 . This tests what happens if we exceed the
  # number of failures specified with --exit-after-n-crashes-or-times or
  # --exit-after-n-failures; the step should be considered a hard failure and
  # we shouldn't try to compare the lists of failing tests.
  # 130 == test_run_results.INTERRUPTED_EXIT_STATUS in run-webkit-tests.
  yield (
    api.test('webkit_tests_interrupted') +
    props(mastername='tryserver.blink',
          buildername='linux_blink_rel') +
    suppress_analyze() +
    api.platform.name('linux') +
    api.override_step_data('webkit_tests (with patch)',
        api.test_utils.canned_test_output(passing=False, retcode=130))
  )

  # This tests what happens if we don't trip the thresholds listed
  # above, but fail more tests than we can safely fit in a return code.
  # (this should be a soft failure and we can still retry w/o the patch
  # and compare the lists of failing tests).
  yield (
    api.test('too_many_failures_for_retcode') +
    props(mastername='tryserver.blink',
          buildername='linux_blink_rel') +
    suppress_analyze() +
    api.platform.name('linux') +
    api.override_step_data('webkit_tests (with patch)',
        api.test_utils.canned_test_output(passing=False,
                                          num_additional_failures=125)) +
    api.override_step_data('webkit_tests (without patch)',
        api.test_utils.canned_test_output(passing=True, minimal=True))
  )

  yield (
    api.test('non_cq_blink_tryjob') +
    props(mastername='tryserver.blink',
          buildername='win_blink_rel',
          requester='someone@chromium.org') +
    suppress_analyze() +
    api.platform.name('win') +
    api.override_step_data('webkit_tests (with patch)',
                           api.test_utils.canned_test_output(passing=True))
  )

  yield (
    api.test('use_v8_patch_on_blink_trybot') +
    props(mastername='tryserver.blink',
          buildername='mac_blink_rel',
          patch_project='v8') +
    api.platform.name('mac')
  )
