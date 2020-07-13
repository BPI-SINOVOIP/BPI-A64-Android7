# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'adb',
  'amp',
  'bisect_tester',
  'bot_update',
  'chromium',
  'chromium_android',
  'chromium_tests',
  'commit_position',
  'file',
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
]

from recipe_engine import config_types

def ignore_undumpable(obj):  # pragma: no cover
  try:
    return config_types.json_fixup(obj)
  except TypeError:
    return None


def RunSteps(api):
  # build/tests/masters_recipes_tests.py needs to manipulate the BUILDERS
  # dict, so we provide an API to dump it here.
  if api.properties.get('dump_builders'):  # pragma: no cover
    api.file.write(
        'Dump BUILDERS dict', api.properties['dump_builders'],
        api.json.dumps(api.chromium_tests.builders, default=ignore_undumpable))
    return

  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')

  if mastername == 'tryserver.chromium.perf' and api.chromium_tests.builders[
      mastername]['builders'][buildername]['bot_type'] == 'tester':
    api.bisect_tester.upload_job_url()

  api.chromium_tests.configure_build(mastername, buildername)
  update_step, master_dict, test_spec = \
      api.chromium_tests.prepare_checkout(mastername, buildername)
  api.chromium_tests.compile(mastername, buildername, update_step, master_dict,
                             test_spec)
  tests = api.chromium_tests.tests_for_builder(
      mastername, buildername, update_step, master_dict)

  if not tests:
    return

  api.chromium_tests.configure_swarming('chromium', precommit=False,
                                        mastername=mastername)
  test_runner = api.chromium_tests.create_test_runner(api, tests)
  with api.chromium_tests.wrap_chromium_tests(mastername, tests):
    test_runner()


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text)


def GenTests(api):
  for mastername, master_config in api.chromium_tests.builders.iteritems():

    # parent builder name -> list of triggered builders.
    triggered_by_parent = {}
    for buildername, bot_config in master_config['builders'].iteritems():
      parent = bot_config.get('parent_buildername')
      if parent:
        triggered_by_parent.setdefault(parent, []).append(buildername)

    for buildername, bot_config in master_config['builders'].iteritems():
      bot_type = bot_config.get('bot_type', 'builder_tester')

      if bot_type in ['builder', 'builder_tester']:
        assert bot_config.get('parent_buildername') is None, (
            'Unexpected parent_buildername for builder %r on master %r.' %
                (buildername, mastername))

      properties = {
        'mastername': mastername,
        'buildername': buildername,
        'parent_buildername': bot_config.get('parent_buildername'),
      }
      if mastername == 'chromium.webkit':
        properties['gs_acl'] = 'public-read'
      test = (
        api.test('full_%s_%s' % (_sanitize_nonalpha(mastername),
                                 _sanitize_nonalpha(buildername))) +
        api.properties.generic(**properties) +
        api.platform(bot_config['testing']['platform'],
                     bot_config.get(
                         'chromium_config_kwargs', {}).get('TARGET_BITS', 64))
      )

      if bot_type == 'tester' and mastername == 'tryserver.chromium.perf':
        bisect_config = {
            'test_type': 'perf',
            'command': 'tools/perf/run_benchmark -v '
                       '--browser=release page_cycler.intl_ar_fa_he',
            'good_revision': '300138',
            'bad_revision': '300148',
            'metric': 'warm_times/page_load_time',
            'repeat_count': '2',
            'max_time_minutes': '5',
            'truncate_percent': '25',
            'bug_id': '425582',
            'gs_bucket': 'chrome-perf',
            'builder_host': 'master4.golo.chromium.org',
            'builder_port': '8341',
        }
        test += api.step_data('saving url to temp file',
                              stdout=api.raw_io.output('/tmp/dummy1'))
        test += api.step_data('saving json to temp file',
                              stdout=api.raw_io.output('/tmp/dummy2'))
        if 'bisector' in buildername:
          test += api.step_data('Performance Test 2 of 2', retcode=1)
        test += api.properties(bisect_config=bisect_config)
        test += api.properties(job_name='f7a7b4135624439cbd27fdd5133d74ec')
        test += api.bisect_tester(tempfile='/tmp/dummy')
      if bot_config.get('parent_buildername'):
        test += api.properties(parent_got_revision='1111111')
        test += api.properties(
            parent_build_archive_url='gs://test-domain/test-archive.zip')

      if mastername == 'client.v8.fyi':
        test += api.properties(revision='22135')

      if bot_config.get('enable_swarming'):
        if bot_type == 'tester':
          test += api.properties(swarm_hashes={
            'browser_tests': 'ffffffffffffffffffffffffffffffffffffffff',
          })

        builders_with_tests = []
        if bot_type == 'builder':
          builders_with_tests = triggered_by_parent.get(buildername, [])
        else:
          builders_with_tests = [buildername]

        test += api.override_step_data('read test spec', api.json.output({
            b: {
            'gtest_tests': [
              {
                'test': 'browser_tests',
                'swarming': {'can_use_on_swarming_builders': True},
              },
            ],
          } for b in builders_with_tests
        }))

      yield test

  yield (
    api.test('dynamic_gtest') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'gtest_tests': [
          'base_unittests',
          {'test': 'browser_tests', 'shard_index': 0, 'total_shards': 2},
        ],
      },
    }))
  )

  yield (
    api.test('dynamic_swarmed_gtest') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'gtest_tests': [
          {'test': 'browser_tests',
           'swarming': {'can_use_on_swarming_builders': True}},
        ],
      },
    }))
  )

  yield (
    api.test('build_dynamic_isolated_script_test') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'isolated_scripts': [
          {
            'isolate_name': 'telemetry_gpu_unittests',
            'name': 'telemetry_gpu_unittests'
          },
        ],
      },
    }))
  )

  yield (
    api.test('dynamic_isolated_script_test') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder') +
    api.properties(swarm_hashes={
      'telemetry_gpu_unittests': 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
    }) +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'isolated_scripts': [
          {
            'isolate_name': 'telemetry_gpu_unittests',
            'name': 'telemetry_gpu_unittests'
          },
        ],
      },
    }))
  )

  yield (
    api.test('goma_with_diagnose_goma_failure') +
    api.properties.generic(mastername='chromium.fyi',
                           buildername='CrWinGoma') +
    api.step_data('diagnose_goma', retcode=1)
  )

  yield (
    api.test('dynamic_gtest_on_builder') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'gtest_tests': [
          'base_unittests',
          {'test': 'browser_tests', 'shard_index': 0, 'total_shards': 2},
        ],
      },
    }))
  )

  yield (
    api.test('dynamic_gtest_win') +
    api.properties.generic(mastername='chromium.win',
                           buildername='Win7 Tests (1)',
                           parent_buildername='Win Builder') +
    api.platform('win', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Win7 Tests (1)': {
        'gtest_tests': [
          'aura_unittests',
          {'test': 'browser_tests', 'shard_index': 0, 'total_shards': 2},
        ],
      },
    }))
  )

  # Tests switching on asan and swiching off lsan for sandbox tester.
  yield (
    api.test('dynamic_gtest_memory_asan_no_lsan') +
    api.properties.generic(mastername='chromium.memory',
                           buildername='Linux ASan Tests (sandboxed)',
                           parent_buildername='Linux ASan LSan Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux ASan Tests (sandboxed)': {
        'gtest_tests': [
          'browser_tests',
        ],
      },
    }))
  )

  # Tests that the memory builder is using the correct compile targets.
  yield (
    api.test('dynamic_gtest_memory_builder') +
    api.properties.generic(mastername='chromium.memory',
                           buildername='Linux ASan LSan Builder',
                           revision='123456') +
    api.platform('linux', 64) +
    # The builder should build 'browser_tests', because there exists a child
    # tester that uses that test.
    api.override_step_data('read test spec', api.json.output({
      'Linux ASan Tests (sandboxed)': {
        'gtest_tests': [
          'browser_tests',
        ],
      },
    }))
  )

  # Tests that the memory mac tester is using the correct test flags.
  yield (
    api.test('dynamic_gtest_memory_mac64') +
    api.properties.generic(
        mastername='chromium.memory',
        buildername='Mac ASan 64 Tests (1)',
        parent_buildername='Mac ASan 64 Builder') +
    api.platform('mac', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Mac ASan 64 Tests (1)': {
        'gtest_tests': [
          'browser_tests',
        ],
      },
    }))
  )

  yield (
    api.test('arm') +
    api.properties.generic(mastername='chromium.fyi',
                           buildername='Linux ARM Cross-Compile') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux ARM Cross-Compile': {
        'compile_targets': ['browser_tests_run'],
        'gtest_tests': [{
          'test': 'browser_tests',
          'args': ['--gtest-filter', '*NaCl*.*'],
          'shard_index': 0,
          'total_shards': 1,
        }],
      },
    }))
  )

  yield (
    api.test('tsan') +
    api.properties.generic(mastername='chromium.memory.fyi',
                           buildername='Linux TSan Tests',
                           parent_buildername='Chromium Linux TSan Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux TSan Tests': {
        'compile_targets': ['base_unittests'],
        'gtest_tests': ['base_unittests'],
      },
    }))
  )

  yield (
    api.test('msan') +
    api.properties.generic(mastername='chromium.memory.fyi',
                           buildername='Linux MSan Tests',
                           parent_buildername='Chromium Linux MSan Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux MSan Tests': {
        'compile_targets': ['base_unittests'],
        'gtest_tests': ['base_unittests'],
      },
    }))
  )

  yield (
    api.test('buildnumber_zero') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder',
                           buildnumber=0) +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'gtest_tests': [
          'base_unittests',
          {'test': 'browser_tests', 'shard_index': 0, 'total_shards': 2},
        ],
      },
    }))
  )

  # FIXME(iannucci): Make this test work.
  #yield (
  #  api.test('one_failure_keeps_going') +
  #  api.properties.generic(mastername='chromium.linux',
  #                         buildername='Linux Tests',
  #                         parent_buildername='Linux Builder') +
  #  api.platform('linux', 64) +
  #  api.step_data('mojo_python_tests', retcode=1)
  #)

  yield (
    api.test('one_failure_keeps_going_dynamic_tests') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'gtest_tests': [
          'base_unittests',
          {'test': 'browser_tests', 'shard_index': 0, 'total_shards': 2},
        ],
      },
    })) +
    api.step_data('base_unittests', retcode=1)
  )

  yield (
    api.test('archive_dependencies_failure') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Builder',
                           buildnumber=0) +
    api.platform('linux', 64) +
    api.override_step_data('archive dependencies',
                           api.test_utils.canned_gtest_output(True), retcode=1)
  )

  yield (
    api.test('perf_test_profile_failure') +
    api.properties.generic(mastername='chromium.perf',
                           buildername='Linux Perf (1)',
                           parent_buildername='Linux Builder',
                           buildnumber=0) +
    api.platform('linux', 64) +
    api.override_step_data(
        'blink_perf.all.release',
        api.json.output([]),
        retcode=1)
  )

  yield (
    api.test('dynamic_script_test_with_args') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'scripts': [
          {
            'name': 'media_perftests',
            'script': 'gtest_perf_test.py',
            'args': ['media_perftests', '--single-process-tests']
          },
        ],
      },
    }))
  )

  yield (
    api.test('dynamic_script_test_failure') +
    api.properties.generic(mastername='chromium.linux',
                           buildername='Linux Tests',
                           parent_buildername='Linux Builder') +
    api.platform('linux', 64) +
    api.override_step_data('read test spec', api.json.output({
      'Linux Tests': {
        'scripts': [
          {
            'name': 'test_script_with_broken_tests',
            'script': 'test_script_with_broken_tests.py'
          }
        ]
      }
    })) +
    api.override_step_data('test_script_with_broken_tests',
                           api.json.output({
      'valid': True,
      'failures': ['FailSuite.Test1', 'FlakySuite.TestA']
    }))
  )

  yield (
    api.test('chromium_webkit_crash') +
    api.properties.generic(mastername='chromium.webkit',
                           buildername='WebKit Linux') +
    api.platform('linux', 64) +
    api.override_step_data(
        'webkit_tests',
        api.test_utils.raw_test_output(None, retcode=1))
  )

  yield (
    api.test('chromium_webkit_warnings') +
    api.properties.generic(mastername='chromium.webkit',
                           buildername='WebKit Linux') +
    api.platform('linux', 64) +
    api.override_step_data(
        'webkit_tests',
        api.test_utils.canned_test_output(
            passing=True, unexpected_flakes=True, retcode=0))
  )

  yield (
    api.test('chromium_webkit_revision_webkit') +
    api.properties.generic(mastername='chromium.webkit',
                           buildername='WebKit Linux',
                           project='webkit',
                           revision='191187') +
    api.platform('linux', 64)
  )

  yield (
    api.test('chromium_webkit_revision_chromium') +
    api.properties.generic(
        mastername='chromium.webkit',
        buildername='WebKit Linux',
        project='chromium',
        revision='3edb4989f8f69c968c0df14cb1c26d21dd19bf1f') +
    api.platform('linux', 64)
  )

  yield (
    api.test('chromium_webkit_parent_revision_webkit') +
    api.properties.generic(
        mastername='chromium.webkit',
        buildername='WebKit XP',
        project='webkit',
        parent_buildername='WebKit Win Builder',
        parent_got_revision='7496f63cbefd34b2d791022fbad64a82838a3f3f',
        parent_got_webkit_revision='191275',
        revision='191275') +
    api.platform('win', 32)
  )

  yield (
    api.test('chromium_webkit_parent_revision_chromium') +
    api.properties.generic(
        mastername='chromium.webkit',
        buildername='WebKit XP',
        project='chromium',
        parent_buildername='WebKit Win Builder',
        parent_got_revision='1e74b372f951d4491f305ec64f6decfcda739e73',
        parent_got_webkit_revision='191269',
        revision='1e74b372f951d4491f305ec64f6decfcda739e73') +
    api.platform('win', 32)
  )

  yield (
    api.test('amp_split_recipe_trigger_failure') +
    api.properties(
        mastername='chromium.fyi',
        buildername='Android Tests (amp split)',
        slavename='build1-a1',
        buildnumber='77457',
        parent_build_archive_url='gs://test-domain/test-archive.zip'
    ) +
    api.override_step_data('[trigger] base_unittests', retcode=1)
  )

  yield (
    api.test('amp_split_recipe_instrumentation_trigger_failure') +
    api.properties(
        mastername='chromium.fyi',
        buildername='Android Tests (amp instrumentation test split)',
        slavename='build1-a1',
        buildnumber='77457',
        parent_build_archive_url='gs://test-domain/test-archive.zip'
    ) +
    api.override_step_data('[trigger] AndroidWebViewTest', retcode=1)
  )

  yield (
    api.test('amp_split_recipe_trigger_local_failure') +
    api.properties(
        mastername='chromium.fyi',
        buildername='Android Tests (amp split)',
        slavename='build1-a1',
        buildnumber='77457',
        parent_build_archive_url='gs://test-domain/test-archive.zip'
    ) +
    api.override_step_data('[trigger] base_unittests', retcode=1) +
    api.override_step_data('base_unittests', retcode=1)
  )

  yield (
    api.test('amp_split_recipe_collect_failure') +
    api.properties(
        mastername='chromium.fyi',
        buildername='Android Tests (amp split)',
        slavename='build1-a1',
        buildnumber='77457',
        parent_build_archive_url='gs://test-domain/test-archive.zip'
    ) +
    api.override_step_data('[collect] base_unittests', retcode=1)
  )
