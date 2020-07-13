# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze
from recipe_engine import recipe_api

DEPS = [
  'archive',
  'amp',
  'bot_update',
  'chromium',
  'chromium_android',
  'filter',
  'gclient',
  'json',
  'path',
  'properties',
  'step',
  'tryserver',
]

CHROMIUM_AMP_INSTRUMENTATION_TESTS = freeze([
  {
    'gyp_target': 'content_shell_test_apk',
    'apk_under_test': 'ContentShell.apk',
    'test_apk': 'ContentShellTest.apk',
    'isolate_file_path': ['content', 'content_shell_test_apk.isolate'],
  },
  {
    'gyp_target': 'chrome_public_test_apk',
    'apk_under_test': 'ChromePublic.apk',
    'test_apk': 'ChromePublicTest.apk',
    'isolate_file_path': ['chrome', 'chrome_public_test_apk.isolate'],
  },
])

CHROMIUM_AMP_UNITTESTS = freeze([
  ['android_webview_unittests', None],
  ['base_unittests', ['base', 'base_unittests.isolate']],
  ['cc_unittests', None],
  ['components_unittests', ['components', 'components_unittests.isolate']],
  ['events_unittests', None],
  ['gl_tests', None],
  ['ipc_tests', None],
  ['skia_unittests', None],
  ['sql_unittests', ['sql', 'sql_unittests.isolate']],
  ['sync_unit_tests', ['sync', 'sync_unit_tests.isolate']],
  ['ui_android_unittests', None],
  ['ui_touch_selection_unittests', None],
])

JAVA_UNIT_TESTS = freeze([
  'junit_unit_tests',
])

PYTHON_UNIT_TESTS = freeze([
  'gyp_py_unittests',
  'pylib_py_unittests',
])

BUILDERS = freeze({
  'tryserver.chromium.linux': {
    'android_amp_rel_tests_recipe': {
      'config': 'main_builder',
      'amp_config': 'commit_queue_pool',
      'target': 'Release',
      'build': True,
      'try': True,
      'device_name': ['Nexus 5'],
      'device_os': ['4.4.2', '4.4.3'],
      'unittests': [],
      'instrumentation_tests': CHROMIUM_AMP_INSTRUMENTATION_TESTS,
      'java_unittests': JAVA_UNIT_TESTS,
      'python_unittests': PYTHON_UNIT_TESTS,
    },
  },
  'chromium.fyi': {
    'Android Tests (amp)(dbg)': {
      'config': 'main_builder',
      'amp_config': 'main_pool',
      'target': 'Debug',
      'build': False,
      'device_minimum_os': '4.0',
      'device_timeout': 60,
      'unittests': CHROMIUM_AMP_UNITTESTS,
      'instrumentation_tests': [],
    },
  },
  'chromium.linux': {
    'EXAMPLE_android_amp_builder_tester': {
      'config': 'main_builder',
      'amp_config': 'main_pool',
      'target': 'Release',
      'build': True,
      'device_name': ['Nexus 5'],
      'device_os': ['4.4.2', '4.4.3'],
      'unittests': CHROMIUM_AMP_UNITTESTS,
      'instrumentation_tests': [],
      'java_unittests': JAVA_UNIT_TESTS,
      'python_unittests': PYTHON_UNIT_TESTS,
    }
  }
})

REPO_URL = 'svn://svn-mirror.golo.chromium.org/chrome/trunk/src'

AMP_INSTANCE_ADDRESS = '172.22.21.180'
AMP_INSTANCE_PORT = '80'
AMP_INSTANCE_PROTOCOL = 'http'
AMP_RESULTS_BUCKET = 'chrome-amp-results'

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
  'mastername': Property(),
}


def RunSteps(api, mastername, buildername):
  api.amp.setup()
  builder = BUILDERS[mastername][buildername]
  api.chromium_android.configure_from_properties(
      builder['config'],
      REPO_NAME='src',
      REPO_URL=REPO_URL,
      INTERNAL=False,
      BUILD_CONFIG=builder['target'])

  api.amp.set_config(builder.get('amp_config', 'main_pool'))
  api.gclient.set_config('chromium')
  api.gclient.apply_config('android')
  api.gclient.apply_config('chrome_internal')

  api.bot_update.ensure_checkout()
  api.chromium_android.clean_local_files()
  api.chromium.runhooks()

  api.chromium_android.run_tree_truth()

  native_unittests = builder.get('unittests', [])
  instrumentation_tests = builder.get('instrumentation_tests', [])
  java_unittests = builder.get('java_unittests', [])
  python_unittests = builder.get('python_unittests', [])

  should_build = builder.get('build', False)

  if should_build:
    test_names = []
    test_names.extend(suite for suite, _ in native_unittests)
    test_names.extend(suite['gyp_target'] for suite in instrumentation_tests)
    test_names.extend(java_unittests)

    compile_targets = api.chromium.c.compile_py.default_targets

    if builder.get('try', False):
      api.tryserver.maybe_apply_issue()

      api.filter.does_patch_require_compile(
          api.tryserver.get_files_affected_by_patch(),
          exes=test_names,
          compile_targets=compile_targets,
          additional_names=['chromium'],
          config_file_name='trybot_analyze_config.json')
      if not api.filter.result:
        return
      compile_targets = (
          list(set(compile_targets) & set(api.filter.compile_targets))
          if compile_targets
          else api.filter.compile_targets)
      native_unittests = [
          i for i in native_unittests
          if i[0] in api.filter.matching_exes]
      instrumentation_tests = [
          i for i in instrumentation_tests
          if i['gyp_target'] in api.filter.matching_exes]
      java_unittests = [
          i for i in java_unittests
          if i in api.filter.matching_exes]

    api.chromium_android.run_tree_truth()
    api.chromium.compile(targets=compile_targets)

  if not instrumentation_tests and not native_unittests and not java_unittests:
    return

  if not should_build:
    api.archive.download_and_unzip_build(
        step_name='extract build',
        target=api.chromium.c.BUILD_CONFIG,
        build_url=None,
        build_archive_url=api.properties.get('parent_build_archive_url'))

  output_dir = api.chromium.output_dir

  with api.step.defer_results():
    successful_trigger_ids = {}

    amp_arguments = api.amp.amp_arguments(
        api_address=AMP_INSTANCE_ADDRESS,
        api_port=AMP_INSTANCE_PORT,
        api_protocol=AMP_INSTANCE_PROTOCOL,
        device_minimum_os=builder.get('device_minimum_os'),
        device_name=builder.get('device_name'),
        device_os=builder.get('device_os'),
        device_timeout=builder.get('device_timeout'))

    for i in instrumentation_tests:
      suite = i.get('gyp_target')
      isolate_file = i.get('isolate_file_path')

      isolate_file_path = (
          api.path['checkout'].join(*isolate_file) if isolate_file else None)
      deferred_trigger_result = api.amp.trigger_test_suite(
          suite, 'instrumentation',
          api.amp.instrumentation_test_arguments(
              apk_under_test=output_dir.join('apks', i['apk_under_test']),
              test_apk=output_dir.join('apks', i['test_apk']),
              isolate_file_path=isolate_file_path),
          amp_arguments)
      if deferred_trigger_result.is_ok:
        successful_trigger_ids[suite] = deferred_trigger_result.get_result()
    for suite, isolate_file in native_unittests:
      isolate_file_path = (
          api.path['checkout'].join(*isolate_file) if isolate_file else None)
      deferred_trigger_result = api.amp.trigger_test_suite(
          suite, 'gtest',
          api.amp.gtest_arguments(suite, isolate_file_path=isolate_file_path),
          amp_arguments)
      if deferred_trigger_result.is_ok:
        successful_trigger_ids[suite] = deferred_trigger_result.get_result()
    for suite in java_unittests:
      api.chromium_android.run_java_unit_test_suite(suite)

    for suite in python_unittests:
      api.chromium_android.run_python_unit_test_suite(suite)

    for suite, isolate_file in native_unittests:
      # Skip collection if test was not triggered successfully.
      if not successful_trigger_ids.get(suite):
        continue

      deferred_step_result = api.amp.collect_test_suite(
          suite, 'gtest',
          api.amp.gtest_arguments(suite),
          amp_arguments,
          test_run_id=successful_trigger_ids[suite])
      if not deferred_step_result.is_ok:
        # We only want to upload the logcat if there was a test failure.
        step_failure = deferred_step_result.get_error()
        if step_failure.result.presentation.status == api.step.FAILURE:
          api.amp.upload_logcat_to_gs(
              AMP_RESULTS_BUCKET, suite,
              successful_trigger_ids[suite])

    for i in instrumentation_tests:
      suite = i.get('gyp_target')

      # Skip collection if test was not triggered successfully.
      if not successful_trigger_ids.get(suite):
        continue

      deferred_step_result = api.amp.collect_test_suite(
          suite, 'instrumentation',
          api.amp.instrumentation_test_arguments(
              apk_under_test=output_dir.join('apks', i['apk_under_test']),
              test_apk=output_dir.join('apks', i['test_apk'])),
          amp_arguments,
          test_run_id=successful_trigger_ids[suite])
      if not deferred_step_result.is_ok:
        # We only want to upload the logcat if there was a test failure.
        step_failure = deferred_step_result.get_error()
        if step_failure.result.presentation.status == api.step.FAILURE:
          api.amp.upload_logcat_to_gs(
              AMP_RESULTS_BUCKET, suite,
              successful_trigger_ids[suite])

    api.chromium_android.test_report()


def GenTests(api):
  sanitize = lambda s: ''.join(c if c.isalnum() else '_' for c in s)

  for mastername in BUILDERS:
    master = BUILDERS[mastername]
    for buildername in master:
      builder = master[buildername]

      test_props = (
          api.test('%s_basic' % sanitize(buildername)) +
          api.properties.generic(
              revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
              buildername=buildername,
              slavename='slavename',
              mastername=mastername))
      if builder.get('try'):
        test_props += api.override_step_data(
            'analyze',
            api.json.output({
                'status': 'Found dependency',
                'targets': [
                    'chrome_public_test_apk',
                    'base_unittests',
                    'junit_unit_tests'],
                'build_targets': [
                    'chrome_public_test_apk',
                    'base_unittests_apk',
                    'junit_unit_tests']}))

      if not builder.get('build'):
        test_props += api.properties(
            parent_build_archive_url='gs://test-domain/test-archive.zip')

      yield test_props

      test_props = (
          api.test('%s_test_failure' % sanitize(buildername)) +
          api.properties.generic(
              revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
              buildername=buildername,
              slavename='slavename',
              mastername=mastername) +
          api.override_step_data(
              'analyze',
              api.json.output({
                  'status': 'Found dependency',
                  'targets': [
                      'chrome_public_test_apk',
                      'android_webview_unittests'],
                  'build_targets': [
                      'chrome_public_test_apk',
                      'android_webview_unittests_apk']})) +
          api.step_data('[trigger] components_unittests', retcode=1) +
          # Test runner error
          api.step_data('[collect] chrome_public_test_apk', retcode=1) +
          api.step_data('[collect] android_webview_unittests', retcode=1) +
          # Test runner infrastructure error
          api.step_data('[collect] base_unittests', retcode=87) +
          # Test runner warning
          api.step_data('[collect] cc_unittests', retcode=88))

      if not builder.get('build'):
        test_props += api.properties(
            parent_build_archive_url='gs://test-domain/test-archive.zip')

      yield test_props

  yield (
    api.test('instrumentation_test_trigger_failure') +
    api.properties.generic(
        revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
        mastername='tryserver.chromium.linux',
        buildername='android_amp_rel_tests_recipe',
        slavename='slavename',
        parent_build_archive_url='gs://test-domain/test-archive.zip') +
    api.override_step_data(
        'analyze',
        api.json.output({
            'status': 'Found dependency',
            'targets': ['content_shell_test_apk'],
            'build_targets': ['content_shell_test_apk']})) +
    api.step_data('[trigger] content_shell_test_apk', retcode=1))

  yield (
    api.test('instrumentation_test_collect_failure') +
    api.properties.generic(
        revision='4f4b02f6b7fa20a3a25682c457bbc8ad589c8a00',
        mastername='tryserver.chromium.linux',
        buildername='android_amp_rel_tests_recipe',
        slavename='slavename',
        parent_build_archive_url='gs://test-domain/test-archive.zip') +
    api.override_step_data(
        'analyze',
        api.json.output({
            'status': 'Found dependency',
            'targets': ['content_shell_test_apk'],
            'build_targets': ['content_shell_test_apk']})) +
    api.step_data('[collect] content_shell_test_apk', retcode=1))

  yield (
      api.test('analyze_no_compilation') +
      api.properties.generic(
          mastername='tryserver.chromium.linux',
          buildername='android_amp_rel_tests_recipe',
          slavename='slavename') +
      api.override_step_data(
          'analyze', api.json.output({'status': 'No compile necessary'})))

  yield (
      api.test('analyze_no_tests') +
      api.properties.generic(
          mastername='tryserver.chromium.linux',
          buildername='android_amp_rel_tests_recipe',
          slavename='slavename') +
      api.override_step_data(
          'analyze',
          api.json.output({
              'status': 'Found dependency',
              'targets': [],
              'build_targets': ['base_unittests']})))
