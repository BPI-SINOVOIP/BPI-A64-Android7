# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This example file is meant to provide coverage to bisect_results."""

DEPS = [
    'auto_bisect',
    'path',
    'properties',
    'raw_io',
]

BASIC_CONFIG = {
    'test_type': 'perf',
    'command':
        ('src/tools/perf/run_benchmark -v --browser=release smoothness.'
         'tough_scrolling_cases'),
    'good_revision': '314015',
    'bad_revision': '314017',
    'metric': 'mean_input_event_latency/mean_input_event_latency',
    'repeat_count': '2',
    'max_time_minutes': '5',
    'truncate_percent': '0',
    'bug_id': '',
    'gs_bucket': 'chrome-perf',
    'builder_host': 'master4.golo.chromium.org',
    'builder_port': '8341',
    'dummy_builds': 'True',
    'skip_gclient_ops': 'True',
}

BASIC_ATTRIBUTES = {
    'bisect_over': True,
    'results_confidence': 0.99,
    'warnings': ['This is a demo warning'],
    'culprit_present': True,
}

NON_TELEMETRY_TEST_CONFIG = dict(BASIC_CONFIG)
NON_TELEMETRY_TEST_CONFIG['command'] = 'src/tools/dummy_command'

DEPS_CULPRIT_ATTRIBUTES = dict(BASIC_ATTRIBUTES)
DEPS_CULPRIT_ATTRIBUTES['culprit.depot'] = {'src': 'fake_location'}

FAILED_ATTRIBUTES = dict(BASIC_ATTRIBUTES)
FAILED_ATTRIBUTES['failed'] = True
FAILED_ATTRIBUTES['culprit_present'] = False

FAILED_DIRECTION_ATTRIBUTES = dict(BASIC_ATTRIBUTES)
FAILED_DIRECTION_ATTRIBUTES['failed_direction'] = True
FAILED_DIRECTION_ATTRIBUTES['culprit_present'] = False

ABORTED_BISECT_ATTRIBUTES = dict(BASIC_ATTRIBUTES)
ABORTED_BISECT_ATTRIBUTES['failed_initial_confidence'] = True
ABORTED_BISECT_ATTRIBUTES['culprit_present'] = False


def RunSteps(api):
  api.path['checkout'] = api.path.mkdtemp('bogus')
  bisector = api.auto_bisect.create_bisector(api.properties['bisect_config'],
                                             dummy_mode=True)
  # Load the simulated results of a bisect
  dummy_bisector_attributes = dict(api.properties['dummy_bisector_attributes'])

  # Simulate the side-effects of a bisect by setting the bisector attributes
  # directly.
  if dummy_bisector_attributes.pop('culprit_present'):
    bisector.culprit = bisector.bad_rev
  set_attributes(bisector, dummy_bisector_attributes)

  bisector.print_result()

def set_attributes(target, attributes):
  """Sets values to the attributes of an object based on a dict.

  This goes one extra level deep so as to set an attribute's attribute:
  e.g.: set_attributes(some_object, {'some_attribute': 1,
                                     'other_attribute.nested_val': False})
  """
  for k, v in attributes.iteritems():
    if '.' in k:
      sub_target = getattr(target, k.split('.')[0])
      setattr(sub_target, k.split('.')[1], v)
    else:
      setattr(target, k, v)

def add_revision_mapping(api, test, pos, sha):
  step_name = 'Resolving reference range.resolving commit_pos ' + pos
  stdout = api.raw_io.output('hash:' + sha)
  test += api.step_data(step_name, stdout=stdout)
  step_name = 'Resolving reference range.resolving hash ' + sha
  pos = 'refs/heads/master@{#%s}' % pos
  stdout = api.raw_io.output(pos)
  test += api.step_data(step_name, stdout=stdout)
  return test

def add_revision_info(api, test):
  step_name = 'Reading culprit cl information.'
  stdout = api.raw_io.output('S3P4R4T0R'.join(
      ['DummyAuthor', 'dummy@nowhere.com', 'Some random CL', '01/01/2015',
       'A long description for a CL.\n' 'Containing multiple lines']))
  return test + api.step_data(step_name, stdout=stdout)

def GenTests(api):
  basic_test = api.test('basic_test')
  basic_test = add_revision_mapping(api, basic_test, '314015', 'c001c0de')
  basic_test = add_revision_mapping(api, basic_test, '314017', 'deadbeef')
  basic_test = add_revision_info(api, basic_test)
  basic_test += api.properties(
      bisect_config = BASIC_CONFIG,
      dummy_bisector_attributes = BASIC_ATTRIBUTES)
  yield basic_test

  deps_culprit_test = api.test('deps_culprit_test')
  deps_culprit_test = add_revision_mapping(
      api, deps_culprit_test, '314015', 'c001c0de')
  deps_culprit_test = add_revision_mapping(
      api, deps_culprit_test, '314017', 'deadbeef')
  deps_culprit_test = add_revision_info(api, deps_culprit_test)
  deps_culprit_test += api.properties(
      bisect_config = BASIC_CONFIG,
      dummy_bisector_attributes = DEPS_CULPRIT_ATTRIBUTES)
  yield deps_culprit_test

  failed_test = api.test('failed_test')
  failed_test = add_revision_mapping(api, failed_test, '314015', 'c001c0de')
  failed_test = add_revision_mapping(api, failed_test, '314017', 'deadbeef')
  failed_test += api.properties(
      bisect_config = BASIC_CONFIG,
      dummy_bisector_attributes = FAILED_ATTRIBUTES)
  yield failed_test

  failed_direction_test = api.test('failed_direction_test')
  failed_direction_test = add_revision_mapping(api, failed_direction_test,
                                               '314015', 'c001c0de')
  failed_direction_test = add_revision_mapping(api, failed_direction_test,
                                               '314017', 'deadbeef')
  failed_direction_test += api.properties(
      bisect_config = BASIC_CONFIG,
      dummy_bisector_attributes = FAILED_DIRECTION_ATTRIBUTES)
  yield failed_direction_test

  aborted_bisect_test = api.test('aborted_non_telemetry_test')
  aborted_bisect_test = add_revision_mapping(api, aborted_bisect_test, '314015',
                                             'c001c0de')
  aborted_bisect_test = add_revision_mapping(api, aborted_bisect_test, '314017',
                                             'deadbeef')
  aborted_bisect_test += api.properties(
      bisect_config = NON_TELEMETRY_TEST_CONFIG,
      dummy_bisector_attributes = ABORTED_BISECT_ATTRIBUTES)
  yield aborted_bisect_test

