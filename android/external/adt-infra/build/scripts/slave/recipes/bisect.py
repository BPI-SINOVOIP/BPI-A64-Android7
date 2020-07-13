# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import collections
import json

DEPS = [
    'auto_bisect',
    'properties',
    'test_utils',
    'chromium_tests',
    'raw_io',
    'step',
    'halt',
    'json',
]

AVAILABLE_BOTS = 1  # Change this for n-secting instead of bi-.


def RunSteps(api):
  _ensure_checkout(api)
  # HORRIBLE hack to get buildbot web ui to let us pass stuff as properties
  bisect_config_b32_string = api.properties.get('bcb32')
  if bisect_config_b32_string is not None:
    bisect_config = bisect_config_b32_string.replace('0', '=')
    bisect_config = base64.b32decode(bisect_config)
    bisect_config = json.loads(bisect_config)
  else:
    bisect_config = api.properties.get('bisect_config')
  assert isinstance(bisect_config, collections.Mapping)
  bisector = api.auto_bisect.create_bisector(bisect_config)
  with api.step.nest('Gathering reference values'):
    _gather_reference_range(api, bisector)
  if (not bisector.failed and bisector.check_improvement_direction() and
      bisector.check_initial_confidence()):
    if bisector.check_reach_adjacent_revision(bisector.good_rev):
      # Only show this step if bisect has reached adjacent revisions.
      with bisector.api.m.step.nest(str('Check bisect finished on revision ' +
          bisector.good_rev.revision_string)):  # pragma: no cover
        if bisector.check_bisect_finished(bisector.good_rev):
          bisector.bisect_over = True
    if not bisector.bisect_over:
      _bisect_main_loop(bisector)
  else:  # pragma: no cover
    bisector.bisect_over = True
  bisector.print_result_debug_info()
  bisector.print_result()


def GenTests(api):
  basic_test = api.test('basic')
  broken_bad_rev_test = api.test('broken_bad_revision_test')
  broken_good_rev_test = api.test('broken_good_revision_test')
  encoded_config_test = api.test('encoded_config_test')
  broken_cp_test = api.test('broken_cp_test')
  broken_hash_test = api.test('broken_hash_test')
  invalid_config_test = api.test('invalid_config_test')
  return_code_test = api.test('basic_return_code_test')
  basic_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  broken_bad_rev_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  broken_good_rev_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  broken_cp_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  broken_hash_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  invalid_config_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  encoded_config_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')
  return_code_test += api.properties.generic(
      mastername='tryserver.chromium.perf',
      buildername='linux_perf_bisector')

  bisect_config = {
      'test_type': 'perf',
      'command': ('tools/perf/run_benchmark -v '
                  '--browser=release page_cycler.intl_ar_fa_he'),
      'good_revision': '306475',
      'bad_revision': 'src@a6298e4afedbf2cd461755ea6f45b0ad64222222',
      'metric': 'warm_times/page_load_time',
      'repeat_count': '2',
      'max_time_minutes': '5',
      'bug_id': '425582',
      'gs_bucket': 'chrome-perf',
      'builder_host': 'master4.golo.chromium.org',
      'builder_port': '8341',
      'dummy_initial_confidence': '95',
      'poll_sleep': 0,
      'dummy_builds': True,
  }
  invalid_cp_bisect_config = dict(bisect_config)
  invalid_cp_bisect_config['good_revision'] = 'XXX'

  basic_test += api.properties(bisect_config=bisect_config)
  broken_bad_rev_test += api.properties(bisect_config=bisect_config)
  broken_good_rev_test += api.properties(bisect_config=bisect_config)
  broken_cp_test += api.properties(bisect_config=bisect_config)
  broken_hash_test += api.properties(bisect_config=bisect_config)
  invalid_config_test += api.properties(bisect_config=invalid_cp_bisect_config)
  encoded_config_test += api.properties(bcb32=base64.b32encode(json.dumps(
      bisect_config)).replace('=', '0'))

  # This data represents fake results for a basic scenario, the items in it are
  # passed to the `_gen_step_data_for_revision` that patches the necessary steps
  # with step_data instances.
  def test_data():
    return [
        {
            'refrange': True,
            'hash': 'a6298e4afedbf2cd461755ea6f45b0ad64222222',
            'commit_pos': '306478',
            'test_results': {
                'results': {
                    'mean': 20,
                    'std_err': 1,
                    'values': [19, 20, 21],
                },
                'retcodes':[0],
            },
            'cl_info': 'S3P4R4T0R'.join(['DummyAuthor', 'dummy@nowhere.com',
                                         'Some random CL', '01/01/2015',
                                         'A long description for a CL.\n'
                                         'Containing multiple lines'])
        },
        {
            'hash': '00316c9ddfb9d7b4e1ed2fff9fe6d964d2111111',
            'commit_pos': '306477',
            'test_results': {
                'results': {
                    'mean': 15,
                    'std_err': 1,
                    'values': [14, 15, 16],
                },
                'retcodes':[0],
            }
        },
        {
            'hash': 'fc6dfc7ff5b1073408499478969261b826441144',
            'commit_pos': '306476',
            'test_results': {
                'results': {
                    'mean': 70,
                    'std_err': 2,
                    'values': [68, 70, 72],
                },
                'retcodes':[0],
            }
        },
        {
            'refrange': True,
            'hash': 'e28dc0d49c331def2a3bbf3ddd0096eb51551155',
            'commit_pos': '306475',
            'test_results': {
                'results': {
                    'mean': 80,
                    'std_err': 10,
                    'values': [70, 70, 80, 90, 90],
                },
                'retcodes':[0],
            }
        },
    ]

  basic_test_data = test_data()
  for revision_data in basic_test_data:
    for step_data in _get_step_data_for_revision(api, revision_data):
      basic_test += step_data
      encoded_config_test += step_data
  basic_test += _get_revision_range_step_data(api, basic_test_data)
  yield basic_test
  encoded_config_test += _get_revision_range_step_data(api, basic_test_data)
  yield encoded_config_test

  broken_test_data = test_data()
  for revision_data in broken_test_data:
    for step_data in _get_step_data_for_revision(api, revision_data,
                                                 broken_cp='306475'):
      broken_cp_test += step_data
    for step_data in _get_step_data_for_revision(
        api, revision_data,
        broken_hash='e28dc0d49c331def2a3bbf3ddd0096eb51551155'):
      broken_hash_test += step_data
  broken_hash_test += _get_revision_range_step_data(api, broken_test_data)
  yield broken_hash_test
  broken_cp_test += _get_revision_range_step_data(api, broken_test_data)
  yield broken_cp_test

  doctored_data = test_data()
  doctored_data[0]['test_results']['results']['error'] = "Dummy error."
  for revision_data in doctored_data:
    revision_data.pop('cl_info', None)
    skip_results = revision_data in doctored_data[1:-1]
    for step_data in _get_step_data_for_revision(api, revision_data,
                                                 skip_results=skip_results):
      broken_bad_rev_test += step_data
  broken_bad_rev_test += _get_revision_range_step_data(api, doctored_data)
  yield broken_bad_rev_test

  doctored_data = test_data()
  doctored_data[-1]['test_results']['results']['error'] = "Dummy error."
  for revision_data in doctored_data:
    revision_data.pop('cl_info', None)
    skip_results = revision_data in doctored_data[1:-1]
    for step_data in _get_step_data_for_revision(api, revision_data,
                                                 skip_results=skip_results):
      broken_good_rev_test += step_data
  broken_good_rev_test += _get_revision_range_step_data(api, doctored_data)
  yield broken_good_rev_test
  invalid_config_test += _get_revision_range_step_data(api, doctored_data)
  yield invalid_config_test

  def return_code_test_data():
    return [
        {
            'refrange': True,
            'hash': 'a6298e4afedbf2cd461755ea6f45b0ad64222222',
            'commit_pos': '306478',
            'test_results': {
                'results': {
                    'mean': 1,
                    'std_err': 0,
                    'values': [],
                },
                'retcodes':[1],
            },
            'cl_info': 'S3P4R4T0R'.join(['DummyAuthor', 'dummy@nowhere.com',
                                       'Some random CL', '01/01/2015',
                                       'A long description for a CL.\n'
                                       'Containing multiple lines'])
        },
        {
            'hash': '00316c9ddfb9d7b4e1ed2fff9fe6d964d2111111',
            'commit_pos': '306477',
            'test_results': {
                'results': {
                    'mean': 1,
                    'std_err': 0,
                    'values': [],
                },
                'retcodes':[1],
            }
        },
        {
            'hash': 'fc6dfc7ff5b1073408499478969261b826441144',
            'commit_pos': '306476',
            'test_results': {
                'results': {
                    'mean': 1,
                    'std_err': 0,
                    'values': [],
                },
                'retcodes':[1],
            }
        },
        {
            'refrange': True,
            'hash': 'e28dc0d49c331def2a3bbf3ddd0096eb51551155',
            'commit_pos': '306475',
            'test_results': {
                'results': {
                    'mean': 0,
                    'std_err': 0,
                    'values': [],
                },
                'retcodes':[0],
            }
        },
    ]

  bisect_config_ret_code = bisect_config.copy()
  bisect_config_ret_code['test_type'] = 'return_code'
  return_code_test += api.properties(bisect_config=bisect_config_ret_code)
  return_code_test_data = return_code_test_data()
  for revision_data in return_code_test_data:
    for step_data in _get_step_data_for_revision(api, revision_data):
      return_code_test += step_data
  return_code_test += _get_revision_range_step_data(api, return_code_test_data)
  yield return_code_test


def _get_revision_range_step_data(api, range_data):
  min_rev = range_data[-1]['hash']
  max_rev = range_data[0]['hash']
  # Simulating the output of git log (reverse order from max_rev until and
  # excluding min_rev).
  simulated_git_log_output = [[d['hash'], d['commit_pos']]
                               for d in range_data[:-1]]
  step_name = ('Expanding revision range.for revisions %s:%s' %
               (min_rev, max_rev))

  result = api.step_data(step_name, stdout=api.json.output(
      simulated_git_log_output))
  return result


def _get_step_data_for_revision(api, revision_data, broken_cp=None,
                                broken_hash=None, skip_results=False):
  """Generator that produces step patches for fake results."""
  commit_pos = revision_data['commit_pos']
  commit_hash = revision_data['hash']
  test_results = revision_data['test_results']

  if 'refrange' in revision_data:
    parent_step = 'Resolving reference range.'
    step_name = parent_step + 'resolving commit_pos ' + commit_pos
    if commit_pos == broken_cp:
      yield api.step_data(step_name, stdout=api.raw_io.output(''))
    else:
      yield api.step_data(step_name, stdout=api.raw_io.output('hash:' +
                                                            commit_hash))

    step_name = parent_step + 'resolving hash ' + commit_hash
    if commit_hash == broken_hash:
      yield api.step_data(step_name, stdout=api.raw_io.output('UnCastable'))
    else:
      commit_pos_str = 'refs/heads/master@{#%s}' % commit_pos
      yield api.step_data(step_name, stdout=api.raw_io.output(commit_pos_str))

  if not skip_results:
    step_name = 'gsutil Get test results for build ' + commit_hash
    if 'refrange' in revision_data:
      step_name = 'Gathering reference values.' + step_name
    else:
      step_name = 'Working on revision %s.' % commit_hash + step_name
    yield api.step_data(step_name, stdout=api.raw_io.output(json.dumps(
        test_results)))

  if 'cl_info' in revision_data:
    step_name = 'Reading culprit cl information.'
    stdout = api.raw_io.output(revision_data['cl_info'])
    yield api.step_data(step_name, stdout=stdout)


def _ensure_checkout(api):
  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')
  # TODO(akuegel): Explicitly load the configs for the builders and don't rely
  # on builders.py in chromium_tests recipe module.
  api.chromium_tests.configure_build(mastername, buildername)
  api.chromium_tests.prepare_checkout(mastername, buildername)


def _gather_reference_range(api, bisector):
  bisector.good_rev.start_job()
  bisector.bad_rev.start_job()
  bisector.wait_for_all([bisector.good_rev, bisector.bad_rev])
  if bisector.good_rev.failed:
    api.halt('Testing the "good" revision failed')
    bisector.failed = True
  elif bisector.bad_rev.failed:
    api.halt('Testing the "bad" revision failed')
    bisector.failed = True
    bisector.api.m.halt('Testing the "good" revision failed')
  else:
    bisector.compute_relative_change()


def _bisect_main_loop(bisector):
  """This is the main bisect loop.

  It gets an evenly distributed number of revisions in the candidate range,
  then it starts them in parallel and waits for them to finish.
  """
  while not bisector.bisect_over:
    revisions_to_check = bisector.get_revisions_to_eval(AVAILABLE_BOTS)
    # TODO: Add a test case to remove this pragma
    if not revisions_to_check:  # pragma: no cover
      bisector.bisect_over = True
      break

    completed_revisions = []
    with bisector.api.m.step.nest(str('Working on revision ' +
                                  revisions_to_check[0].revision_string)):
      nest_step_result = bisector.api.m.step.active_result
      partial_results = bisector.partial_results().splitlines()
      nest_step_result.presentation.logs['Partial Results'] = partial_results
      for r in revisions_to_check:
        r.start_job()
      completed_revisions = _wait_for_revisions(bisector, revisions_to_check)

    for completed_revision in completed_revisions:
      if not bisector.check_reach_adjacent_revision(completed_revision):
        continue
      # Only show this step if bisect has reached adjacent revisions.
      with bisector.api.m.step.nest(str('Check bisect finished on revision ' +
                                    completed_revisions[0].revision_string)):
        if bisector.check_bisect_finished(completed_revision):
          bisector.bisect_over = True


def _wait_for_revisions(bisector, revisions_to_check):
  """Wait for possibly multiple revision evaluations.

  Waits for the first of such revisions to finish, it then checks if any of the
  other revisions in progress has become superfluous and has them aborted.

  If such revision completes the bisect process it sets the flag so that the
  main loop stops.
  """
  completed_revisions = []
  while revisions_to_check:
    completed_revision = bisector.wait_for_any(revisions_to_check)
    if completed_revision in revisions_to_check:
      revisions_to_check.remove(completed_revision)
    else:
      bisector.api.m.step.active_result.presentation.status = (
          bisector.api.m.step.WARNING)  # pragma: no cover
      bisector.api.m.step.active_result.presentation.logs['WARNING'] = (
          ['Tried to remove revision not in list'])  # pragma: no cover
    if not (completed_revision.aborted or completed_revision.failed):
      completed_revisions.append(completed_revision)
      bisector.abort_unnecessary_jobs()
  return completed_revisions
