# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'auto_bisect',
  'bisect_tester',
  'chromium',
  'chromium_tests',
  'gclient',
  'json',
  'path',
  'platform',
  'properties',
  'raw_io',
  'step'
]

def RunSteps(api):
  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')
  # TODO(akuegel): Explicitly load the builder configs instead of relying on
  # builder.py from chromium_tests recipe module.
  api.chromium_tests.configure_build(mastername, buildername)
  api.gclient.apply_config('perf')
  update_step, master_dict, _ = \
      api.chromium_tests.prepare_checkout(mastername, buildername)
  api.auto_bisect.start_try_job(api, update_step=update_step,
                                master_dict=master_dict)

def GenTests(api):
  yield (api.test('basic')
  +api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/auto_bisect/bisect.cfg')))

  config_json = {
    "command": "./tools/perf/run_benchmark -v --browser=release sunspider",
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
  yield (api.test('basic_perf_tryjob')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
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

  yield (api.test('basic_perf_tryjob_with_metric')
  +api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
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

  yield (api.test('perf_tryjob_failed_test')
  +api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/run-perf-test.cfg'))
  + api.override_step_data(
      'load config',
      api.json.output(config_json))
  + api.step_data('Performance Test (Without Patch) 1 of 1', retcode=1)
  + api.step_data('Performance Test (With Patch) 1/1', retcode=1)
  )

  config_json.update({"good_revision": '306475', "bad_revision": '306476'})

  yield (api.test('basic_perf_tryjob_with_revisions')
  +api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
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

  yield (api.test('perf_tryjob_config_error')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.properties(requester='abcdxyz@chromium.org')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/run-perf-test.cfg'))
  + api.override_step_data(
      'load config',
      api.json.output(config_json))
  )

  yield (api.test('perf_cq_run_benchmark')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.properties(requester='commit-bot@chromium.org')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/perf/benchmarks/blink_perf.py'))
  )

  yield (api.test('perf_cq_no_changes')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.properties(requester='commit-bot@chromium.org')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/no_benchmark_file'))
  )

  yield (api.test('perf_cq_no_benchmark_to_run')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.properties(requester='commit-bot@chromium.org')
  + api.override_step_data(
      'git diff to analyze patch',
      api.raw_io.stream_output('tools/perf/benchmarks/sunspider.py'))
  )

  bisect_config = {
      'test_type': 'perf',
      'command': './tools/perf/run_benchmark -v '
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
  yield (api.test('basic_linux_bisect_tester_recipe')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.step_data('saving url to temp file',
                  stdout=api.raw_io.output('/tmp/dummy1'))
  + api.step_data('saving json to temp file',
                   stdout=api.raw_io.output('/tmp/dummy2'))
  + api.properties(bisect_config=bisect_config)
  + api.properties(job_name='f7a7b4135624439cbd27fdd5133d74ec')
  + api.bisect_tester(tempfile='/tmp/dummy')
  + api.properties(parent_got_revision='1111111')
  + api.properties(parent_build_archive_url='gs://test-domain/test-archive.zip')
  )

  bisect_ret_code_config = {
      'test_type': 'return_code',
      'command': './tools/perf/run_benchmark -v '
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
  yield (api.test('basic_linux_bisect_tester_recipe_ret_code')
  + api.properties.tryserver(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisect')
  + api.step_data('saving url to temp file',
                  stdout=api.raw_io.output('/tmp/dummy1'))
  + api.step_data('saving json to temp file',
                   stdout=api.raw_io.output('/tmp/dummy2'))
  + api.properties(bisect_config=bisect_ret_code_config)
  + api.properties(job_name='f7a7b4135624439cbd27fdd5133d74ec')
  + api.bisect_tester(tempfile='/tmp/dummy')
  + api.properties(parent_got_revision='1111111')
  + api.properties(parent_build_archive_url='gs://test-domain/test-archive.zip')
  )

