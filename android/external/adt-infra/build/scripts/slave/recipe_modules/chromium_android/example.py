# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze

DEPS = [
    'adb',
    'chromium',
    'chromium_android',
    'json',
    'path',
    'properties',
    'step',
]

BUILDERS = freeze({
    'basic_builder': {
        'target': 'Release',
        'build': True,
        'skip_wipe': False,
    },
    'restart_usb_builder': {
        'restart_usb': True,
        'target': 'Release',
        'build': True,
        'skip_wipe': False,
    },
    'coverage_builder': {
        'coverage': True,
        'target': 'Debug',
        'build': True,
        'skip_wipe': False,
    },
    'tester': {
        'build': False,
        'skip_wipe': False,
    },
    'perf_runner': {
        'perf_config': 'sharded_perf_tests.json',
        'build': False,
        'skip_wipe': False,
    },
    'perf_runner_user_build': {
        'perf_config': 'sharded_perf_tests.json',
        'build': False,
        'skip_wipe': True,
    },
    'perf_runner_disable_location': {
        'perf_config': 'sharded_perf_tests.json',
        'build': False,
        'skip_wipe': False,
        'disable_location': True,
    },
    'perf_runner_allow_low_battery': {
        'perf_config': 'sharded_perf_tests.json',
        'build': False,
        'skip_wipe': False,
        'min_battery_level': 50,
    },
    'perf_adb_vendor_keys': {
        'adb_vendor_keys': True,
        'build': False,
        'skip_wipe': False,
    },
    'perf_runner_allow_high_battery_temp': {
        'perf_config': 'sharded_perf_tests.json',
        'build': False,
        'skip_wipe': False,
        'max_battery_temp': 500,
    },
    'gerrit_try_builder': {
        'build': True,
        'skip_wipe': True,
    },
    'java_method_count_builder': {
        'build': True,
        'skip_wipe': False,
        'java_method_count': True,
    },
    'webview_tester': {
        'build': False,
        'skip_wipe': False,
        'remove_system_webview': True,
        'disable_system_chrome': True,
    },
})

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
}

def RunSteps(api, buildername):
  config = BUILDERS[buildername]

  api.chromium_android.configure_from_properties(
      'base_config',
      REPO_URL='svn://svn.chromium.org/chrome/trunk/src',
      REPO_NAME='src/repo',
      INTERNAL=True,
      BUILD_CONFIG='Release')

  api.chromium_android.c.get_app_manifest_vars = True
  api.chromium_android.c.coverage = config.get('coverage', False)
  api.chromium_android.c.asan_symbolize = True

  if config.get('adb_vendor_keys'):
    api.chromium.c.env.ADB_VENDOR_KEYS = api.path['build'].join(
      'site_config', '.adb_key')

  api.chromium_android.init_and_sync()

  api.chromium.runhooks()
  api.chromium_android.run_tree_truth(additional_repos=['foo'])
  assert 'MAJOR' in api.chromium.get_version()

  if config['build']:
    api.chromium.compile()
    api.chromium_android.make_zip_archive('zip_build_proudct', 'archive.zip',
        filters=['*.apk'])
  else:
    api.chromium_android.download_build('build-bucket',
                                              'build_product.zip')
  api.chromium_android.git_number()

  if config.get('java_method_count'):
    api.chromium_android.java_method_count(
        api.chromium.output_dir.join('chrome_public_apk', 'classes.dex'))

  api.adb.root_devices()
  api.chromium_android.spawn_logcat_monitor()

  failure = False
  try:
    # TODO(luqui): remove redundant cruft, need one consistent API.
    api.chromium_android.detect_and_setup_devices()

    api.chromium_android.device_status_check(
      restart_usb=config.get('restart_usb', False))

    api.chromium_android.provision_devices(
        skip_wipe=config['skip_wipe'],
        disable_location=config.get('disable_location', False),
        min_battery_level=config.get('min_battery_level'),
        max_battery_temp=config.get('max_battery_temp'),
        remove_system_webview=config.get('remove_system_webview', False),
        disable_system_chrome=config.get('disable_system_chrome', False))

  except api.step.StepFailure as f:
    failure = f

  api.chromium_android.monkey_test()

  try:
    if config.get('perf_config'):
      api.chromium_android.run_sharded_perf_tests(
          config='fake_config.json',
          flaky_config='flake_fakes.json',
          upload_archives_to_bucket='archives-bucket')
  except api.step.StepFailure as f:
    failure = f

  api.chromium_android.run_instrumentation_suite(
      test_apk='AndroidWebViewTest',
      isolate_file_path='android_webview/android_webview_test_apk.isolate',
      flakiness_dashboard='test-results.appspot.com',
      annotation='SmallTest',
      except_annotation='FlakyTest',
      screenshot=True,
      official_build=True,
      host_driven_root=api.path['checkout'].join('chrome', 'test'))
  api.chromium_android.run_test_suite(
      'unittests',
      isolate_file_path=api.path['checkout'].join('some_file.isolate'),
      gtest_filter='WebRtc*',
      tool='asan')
  if not failure:
      api.chromium_android.run_bisect_script(extra_src='test.py',
                                             path_to_config='test.py')
  api.chromium_android.logcat_dump()
  api.chromium_android.stack_tool_steps()
  if config.get('coverage', False):
    api.chromium_android.coverage_report()

  if failure:
    raise failure

def GenTests(api):
  def properties_for(buildername):
    return api.properties.generic(
        buildername=buildername,
        slavename='tehslave',
        repo_name='src/repo',
        patch_url='https://the.patch.url/the.patch',
        repo_url='svn://svn.chromium.org/chrome/trunk/src',
        revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
        internal=True)

  for buildername in BUILDERS:
    yield api.test('%s_basic' % buildername) + properties_for(buildername)

  yield (api.test('tester_no_devices') +
         properties_for('tester') +
         api.step_data('device_status_check', retcode=1))

  yield (api.test('tester_other_device_failure') +
         properties_for('tester') +
         api.step_data('device_status_check', retcode=2))

  yield (api.test('tester_blacklisted_devices') +
         properties_for('tester') +
         api.override_step_data('provision_devices',
                                api.json.output(['abc123', 'def456'])))

  yield (api.test('tester_offline_devices') +
         properties_for('tester') +
         api.override_step_data('device_status_check',
                                api.json.output([{}, {}])))

  yield (api.test('perf_tests_failure') +
      properties_for('perf_runner') +
      api.step_data('perf_test.foo', retcode=1))

  yield (api.test('gerrit_refs') +
      api.properties.generic(
        buildername='gerrit_try_builder',
        slavename='testslave',
        repo_name='src/repo',
        patch_url='https://the.patch.url/the.patch',
        repo_url='svn://svn.chromium.org/chrome/trunk/src',
        revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
        internal=True, **({'event.patchSet.ref':'refs/changes/50/176150/1'})))
