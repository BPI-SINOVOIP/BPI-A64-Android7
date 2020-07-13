# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze
from recipe_engine import recipe_api

DEPS = [
  'bot_update',
  'chromium',
  'chromium_android',
  'gsutil',
  'path',
  'properties',
  'python',
  'step',
]


BUILDERS = freeze({
  'chromium.mojo': {
    'builders': {
      'Chromium Mojo Linux': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'linux',
        },
      },
      'Chromium Mojo Android': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
        },
        'gclient_apply_config': ['android'],
      },
      'Chromium Mojo Linux Perf': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'linux',
        },
        'run_perf_tests': True,
        'perf_test_info': {
          'browser_type': 'mandoline-release',
          'perf_id': 'mandoline-linux-release',
          'supported_testnames': [
            'blink_perf.dom',
            'blink_perf.events',
            'blink_perf.mutation',
            'blink_perf.shadow_dom',
            'page_cycler.typical_25',
            'startup.cold.blank_page',
            'startup.warm.blank_page',
          ],
        },
      },
      'Chromium Mojo Android Nexus5 Perf': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'android',
          'TARGET_ARCH': 'arm',
        },
        'gclient_apply_config': ['android'],
        # TODO(yzshen): Actually run perf tests.
      },
      'Chromium Mojo Windows': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'win',
        },
      },
      'Chromium Mojo Windows 7 Perf': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_PLATFORM': 'win',
        },
        'run_perf_tests': True,
        'perf_test_info': {
          'browser_type': 'mandoline-release',
          'perf_id': 'mandoline-win7-release',
          'supported_testnames': [
            'blink_perf.dom',
            'blink_perf.events',
            'blink_perf.mutation',
            'blink_perf.shadow_dom',
            'page_cycler.typical_25',
            'startup.cold.blank_page',
            'startup.warm.blank_page',
          ],
        },
      },
    },
  },
})


def _GetBotConfig(api):
  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')
  master_dict = BUILDERS.get(mastername, {})
  bot_config = master_dict.get('builders', {}).get(buildername, {})
  return bot_config


@recipe_api.composite_step
def _UploadMandolineAPKToGoogleStorage(api):
  with api.step.defer_results():
    apk = api.chromium.output_dir.join('apks', 'Mandoline.apk')
    api.gsutil.upload(apk, 'mandoline', 'Mandoline.apk')


@recipe_api.composite_step
def _RunApptests(api):
  runner = api.path['checkout'].join('mojo', 'tools', 'apptest_runner.py')
  api.python('app_tests', runner, [api.chromium.output_dir, '--verbose'])


def _RunUnitAndAppTests(api):
  with api.step.defer_results():
    api.chromium.runtest('html_viewer_unittests')
    api.chromium.runtest('ipc_mojo_unittests')
    api.chromium.runtest('mojo_common_unittests')
    api.chromium.runtest('mojo_public_application_unittests')
    api.chromium.runtest('mojo_public_bindings_unittests')
    api.chromium.runtest('mojo_public_environment_unittests')
    api.chromium.runtest('mojo_public_system_unittests')
    api.chromium.runtest('mojo_public_utility_unittests')
    api.chromium.runtest('mojo_runner_unittests')
    api.chromium.runtest('mojo_shell_unittests')
    api.chromium.runtest('mojo_surfaces_lib_unittests')
    api.chromium.runtest('mojo_system_unittests')
    api.chromium.runtest('mojo_view_manager_lib_unittests')
    api.chromium.runtest('resource_provider_unittests')
    api.chromium.runtest('view_manager_unittests')
    _RunApptests(api)


def _RunPerfTests(api, perf_test_info):
  tests = api.chromium.list_perf_tests(perf_test_info['browser_type'], 1)

  # TODO(yzshen): Remove this filter once we annotate tests disabled for
  # Mandoline in Telemetry. Consider reusing
  # chromium_tests.steps.DynamicPerfTests.
  tests = dict((k, v) for k, v in tests.json.output['steps'].iteritems()
      if str(k) in perf_test_info['supported_testnames'])

  with api.step.defer_results():
    for test_name, test in sorted(tests.iteritems()):
      test_name = str(test_name)
      annotate = api.chromium.get_annotate_by_test_name(test_name)
      cmd = test['cmd'].split()
      cmd.extend(
          ['--extra-browser-args=--override-use-gl-with-osmesa-for-tests'])

      api.chromium.runtest(
          cmd[1] if len(cmd) > 1 else cmd[0],
          args=cmd[2:],
          name=test_name,
          annotate=annotate,
          python_mode=True,
          results_url='https://chromeperf.appspot.com',
          perf_dashboard_id=test.get('perf_dashboard_id', test_name),
          perf_id=perf_test_info['perf_id'],
          test_type=test.get('perf_dashboard_id', test_name),
          xvfb=True,
          chartjson_file=True)


def RunSteps(api):
  # TODO(yzshen): Perf bots should retrieve build results from builders of the
  # same architecture.
  api.chromium.configure_bot(BUILDERS, ['gn'])

  api.bot_update.ensure_checkout(force=True)

  api.chromium.runhooks()

  api.chromium.run_mb(api.properties.get('mastername'),
                      api.properties.get('buildername'),
                      use_goma=True)

  api.chromium.compile(targets=['mandoline:all'])

  if api.chromium.c.TARGET_PLATFORM == 'android':
    _UploadMandolineAPKToGoogleStorage(api)
    api.chromium_android.detect_and_setup_devices()

  bot_config = _GetBotConfig(api)
  if bot_config.get('run_perf_tests', False):
    _RunPerfTests(api, bot_config['perf_test_info'])
  else:
    _RunUnitAndAppTests(api)


def GenTests(api):
  for test in api.chromium.gen_tests_for_builders(BUILDERS):
    yield test
