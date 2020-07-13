# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze

DEPS = [
    'auto_bisect',
    'bisect_tester',
    'bot_update',
    'chromium',
    'chromium_android',
    'chromium_tests',
    'gclient',
    'json',
    'path',
    'properties',
    'raw_io',
    'step',
]

REPO_URL = 'https://chromium.googlesource.com/chromium/src.git'

BUILDERS = freeze({
  'tryserver.chromium.perf': {
    'builders': {
      'android_one_perf_bisect': {
        'recipe_config': 'perf',
         'gclient_apply_config': ['android', 'perf'],
        'bucket': 'chrome-perf',
        'path': lambda api: ('android_perf_rel/full-build-linux_%s.zip' %
                              api.properties['parent_got_revision']),
      },
      'android_nexus5_perf_bisect': {
        'recipe_config': 'perf',
        'gclient_apply_config': ['android', 'perf'],
        'bucket': 'chrome-perf',
        'path': lambda api: ('android_perf_rel/full-build-linux_%s.zip' %
                              api.properties['parent_got_revision']),
      },
      'android_nexus6_perf_bisect': {
        'recipe_config': 'perf',
        'gclient_apply_config': ['android', 'perf'],
        'bucket': 'chrome-perf',
        'path': lambda api: ('android_perf_rel/full-build-linux_%s.zip' %
                              api.properties['parent_got_revision']),
      },
      'android_nexus7_perf_bisect': {
        'recipe_config': 'perf',
        'gclient_apply_config': ['android', 'perf'],
        'bucket': 'chrome-perf',
        'path': lambda api: ('android_perf_rel/full-build-linux_%s.zip' %
                              api.properties['parent_got_revision']),
      },
      'android_nexus9_perf_bisect': {
        'recipe_config': 'arm64_builder',
        'gclient_apply_config': ['android', 'perf'],
        'bucket': 'chrome-perf',
        'path': lambda api: ('android_perf_rel_arm64/full-build-linux_%s.zip' %
                              api.properties['parent_got_revision']),
      },
    },
  },
})

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'mastername': Property(),
  'buildername': Property(),
}


def RunSteps(api, mastername, buildername):
  master_dict = BUILDERS.get(mastername, {})
  bot_config = master_dict.get('builders', {}).get(buildername)
  # The following lines configures android bisect bot to to checkout codes,
  # executes runhooks, provisions devices and runs legacy bisect script.
  recipe_config = bot_config.get('recipe_config', 'perf')
  kwargs = {
    'REPO_NAME': 'src',
    'REPO_URL': REPO_URL,
    'INTERNAL': False,
    'BUILD_CONFIG': 'Release',
    'TARGET_PLATFORM': 'android',
  }
  kwargs.update(bot_config.get('kwargs', {}))
  api.chromium_android.configure_from_properties(recipe_config, **kwargs)
  api.chromium.set_config(recipe_config, **kwargs)
  api.chromium_android.c.set_val({'deps_file': 'DEPS'})
  api.gclient.set_config('chromium')
  for c in bot_config.get('gclient_apply_config', []):
    api.gclient.apply_config(c)
  update_step = api.bot_update.ensure_checkout()
  api.chromium_android.clean_local_files()

  api.auto_bisect.start_try_job(api, update_step=update_step,
                                master_dict=master_dict)

def GenTests(api):
  config_json_main = {
    "command": ("./tools/perf/run_benchmark -v --browser=android-chrome "
                "sunspider"),
    "max_time_minutes": "25",
    "repeat_count": "1",
    "truncate_percent": "25",
    "target_arch": "ia32",
  }

  results_with_patch = """*RESULT dummy: dummy= [5.83,6.013,5.573]ms
Avg dummy: 5.907711ms
Sd  dummy: 0.255921ms
RESULT telemetry_page_measurement_results: num_failed= 0 count
RESULT telemetry_page_measurement_results: num_errored= 0 count

View online at http://storage.googleapis.com/chromium-telemetry/\
html-results/results-with_patch
"""

  results_without_patch = """*RESULT dummy: dummy= [5.83,6.013,5.573]ms
Avg dummy: 5.907711ms
Sd  dummy: 0.255921ms
RESULT telemetry_page_measurement_results: num_failed= 0 count
RESULT telemetry_page_measurement_results: num_errored= 0 count

View online at http://storage.googleapis.com/chromium-telemetry/html-results/\
results-without_patch
"""


  for _, master_dict in BUILDERS.items():
    for buildername in master_dict.get('builders', {}):
      config_json = config_json_main.copy()
      yield (api.test('basic_' + buildername)
      +api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/auto_bisect/bisect.cfg')))

      yield (api.test('basic_perf_tryjob_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/run-perf-test.cfg'))
      + api.override_step_data(
          'load config',
          api.json.output(config_json))
      + api.step_data('Performance Test (Without Patch) 1 of 1',
          stdout=api.raw_io.output(str(results_without_patch)))
      + api.step_data('Performance Test (With Patch) 1 of 1',
          stdout=api.raw_io.output(str(results_with_patch)))

      )
      config_json.update({"metric": "dummy/dummy"})

      yield (api.test('basic_perf_tryjob_with_metric_' + buildername)
      +api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/run-perf-test.cfg'))
      + api.override_step_data(
          'load config',
          api.json.output(config_json))
      + api.step_data('Performance Test (Without Patch) 1 of 1',
          stdout=api.raw_io.output(results_without_patch))
      + api.step_data('Performance Test (With Patch) 1 of 1',
          stdout=api.raw_io.output(results_with_patch))
      )

      yield (api.test('perf_tryjob_failed_test_' + buildername)
      +api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/run-perf-test.cfg'))
      + api.override_step_data(
          'load config',
          api.json.output(config_json))
      + api.step_data('Performance Test (Without Patch) 1 of 1', retcode=1)
      + api.step_data('Performance Test (With Patch) 1 of 1', retcode=1)
      )

      config_json.update({"good_revision": '306475', "bad_revision": '306476'})

      yield (api.test('basic_perf_tryjob_with_revisions_' + buildername)
      +api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/run-perf-test.cfg'))
      + api.override_step_data(
          'load config',
          api.json.output(config_json))
      + api.step_data('resolving commit_pos ' + config_json['good_revision'],
          stdout=api.raw_io.output('hash:d49c331def2a3bbf3ddd0096eb51551155'))
      + api.step_data('resolving commit_pos ' + config_json['bad_revision'],
          stdout=api.raw_io.output('hash:bad49c331def2a3bbf3ddd0096eb51551155'))
      + api.step_data(
          'Performance Test (d49c331def2a3bbf3ddd0096eb51551155) 1 of 1',
          stdout=api.raw_io.output(results_without_patch))
      + api.step_data(
          'Performance Test (bad49c331def2a3bbf3ddd0096eb51551155) 1 of 1',
          stdout=api.raw_io.output(results_with_patch))
      )

      config_json = {
        "max_time_minutes": "25",
        "repeat_count": "1",
        "truncate_percent": "25",
        "target_arch": "ia32",
      }

      yield (api.test('perf_tryjob_config_error_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.properties(requester='abcdxyz@chromium.org')
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/run-perf-test.cfg'))
      + api.override_step_data(
          'load config',
          api.json.output(config_json))
      )

      yield (api.test('perf_cq_run_benchmark_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.properties(requester='commit-bot@chromium.org')
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/perf/benchmarks/blink_perf.py'))
      )

      yield (api.test('perf_cq_no_changes_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.properties(requester='commit-bot@chromium.org')
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/no_benchmark_file'))
      )

      yield (api.test('perf_cq_no_benchmark_to_run_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.properties(requester='commit-bot@chromium.org')
      + api.override_step_data(
          'git diff to analyze patch',
          api.raw_io.stream_output('tools/perf/benchmarks/sunspider.py'))
      )

      bisect_config = {
          'test_type': 'perf',
          'command': './tools/perf/run_benchmark -v '
                     '--browser=android-chromium page_cycler.intl_ar_fa_he',
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
      yield (api.test('basic_recipe_' + buildername)
      + api.properties.tryserver(
          mastername='tryserver.chromium.perf',
          buildername=buildername)
      + api.step_data('saving url to temp file',
                      stdout=api.raw_io.output('/tmp/dummy1'))
      + api.step_data('saving json to temp file',
                       stdout=api.raw_io.output('/tmp/dummy2'))
      + api.properties(bisect_config=bisect_config)
      + api.properties(job_name='f7a7b4135624439cbd27fdd5133d74ec')
      + api.bisect_tester(tempfile='/tmp/dummy')
      + api.properties(parent_got_revision='1111111')
      + api.properties(
          parent_build_archive_url='gs://test-domain/test-archive.zip')
      )
