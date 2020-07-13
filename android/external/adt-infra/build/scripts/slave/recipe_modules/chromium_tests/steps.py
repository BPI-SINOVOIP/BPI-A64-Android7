# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import re
import string


class Test(object):
  """
  Base class for tests that can be retried after deapplying a previously
  applied patch.
  """

  def __init__(self):
    super(Test, self).__init__()
    self._test_runs = {}

  @property
  def abort_on_failure(self):
    """If True, abort build when test fails."""
    return False

  @property
  def name(self):  # pragma: no cover
    """Name of the test."""
    raise NotImplementedError()

  @property
  def isolate_target(self):
    """Returns isolate target name. Defaults to name."""
    return self.name  # pragma: no cover

  @staticmethod
  def compile_targets(api):
    """List of compile targets needed by this test."""
    raise NotImplementedError()  # pragma: no cover

  def pre_run(self, api, suffix):  # pragma: no cover
    """Steps to execute before running the test."""
    return []

  def run(self, api, suffix):  # pragma: no cover
    """Run the test. suffix is 'with patch' or 'without patch'."""
    raise NotImplementedError()

  def post_run(self, api, suffix):  # pragma: no cover
    """Steps to execute after running the test."""
    return []

  def has_valid_results(self, api, suffix):  # pragma: no cover
    """
    Returns True if results (failures) are valid.

    This makes it possible to distinguish between the case of no failures
    and the test failing to even report its results in machine-readable
    format.
    """
    raise NotImplementedError()

  def failures(self, api, suffix):  # pragma: no cover
    """Return list of failures (list of strings)."""
    raise NotImplementedError()

  @property
  def uses_swarming(self):
    """Returns true if the test uses swarming."""
    return False

  @property
  def uses_local_devices(self):
    return False # pragma: no cover

  def _step_name(self, suffix):
    """Helper to uniformly combine tests's name with a suffix."""
    if not suffix:
      return self.name
    return '%s (%s)' % (self.name, suffix)


class ArchiveBuildStep(Test):
  def __init__(self, gs_bucket, gs_acl=None):
    self.gs_bucket = gs_bucket
    self.gs_acl = gs_acl

  def run(self, api, suffix):
    return api.chromium.archive_build(
        'archive build',
        self.gs_bucket,
        gs_acl=self.gs_acl,
    )

  @staticmethod
  def compile_targets(_):
    return []


class SizesStep(Test):
  def __init__(self, results_url, perf_id):
    self.results_url = results_url
    self.perf_id = perf_id

  def run(self, api, suffix):
    return api.chromium.sizes(self.results_url, self.perf_id)

  @staticmethod
  def compile_targets(_):
    return ['chrome']

  @property
  def name(self):
    return 'sizes'  # pragma: no cover

class ScriptTest(Test):  # pylint: disable=W0232
  """
  Test which uses logic from script inside chromium repo.

  This makes it possible to keep the logic src-side as opposed
  to the build repo most Chromium developers are unfamiliar with.

  Another advantage is being to test changes to these scripts
  on trybots.

  All new tests are strongly encouraged to use this infrastructure.
  """

  def __init__(self, name, script, all_compile_targets, script_args=None):
    super(ScriptTest, self).__init__()
    self._name = name
    self._script = script
    self._all_compile_targets = all_compile_targets
    self._script_args = script_args

  @property
  def name(self):
    return self._name

  def compile_targets(self, api):
    try:
      substitutions = {'name': self._name}

      return [string.Template(s).safe_substitute(substitutions)
              for s in self._all_compile_targets[self._script]]
    except KeyError:  # pragma: no cover
      # There are internal recipes that appear to configure
      # test script steps, but ones that don't have data.
      # We get around this by returning a default value for that case.
      # But the recipes should be updated to not do this.
      # We mark this as pragma: no cover since the public recipes
      # will not exercise this block.
      #
      # TODO(phajdan.jr): Revisit this when all script tests
      # lists move src-side. We should be able to provide
      # test data then.
      if api.chromium._test_data.enabled:
        return []

      raise

  def run(self, api, suffix):
    name = self.name
    if suffix:
      name += ' (%s)' % suffix

    run_args = []
    if suffix == 'without patch':
      run_args.extend([
          '--filter-file', api.json.input(self.failures(api, 'with patch'))
      ])  # pragma: no cover

    try:
      script_args = []
      if self._script_args:
        script_args = ['--args', api.json.input(self._script_args)]
      api.python(
          name,
          # Enforce that all scripts are in the specified directory
          # for consistency.
          api.path['checkout'].join(
              'testing', 'scripts', api.path.basename(self._script)),
          args=(api.chromium_tests.get_common_args_for_scripts() +
                script_args +
                ['run', '--output', api.json.output()] +
                run_args),
          step_test_data=lambda: api.json.test_api.output(
              {'valid': True, 'failures': []}))
    finally:
      self._test_runs[suffix] = api.step.active_result
      if self.has_valid_results(api, suffix):
        self._test_runs[suffix].presentation.step_text += (
            api.test_utils.format_step_text([
              ['failures:', self.failures(api, suffix)]
            ]))

    return self._test_runs[suffix]

  def has_valid_results(self, api, suffix):
    try:
      # Make sure the JSON includes all necessary data.
      self.failures(api, suffix)

      return self._test_runs[suffix].json.output['valid']
    except Exception:  # pragma: no cover
      return False

  def failures(self, api, suffix):
    return self._test_runs[suffix].json.output['failures']


class LocalGTestTest(Test):
  def __init__(self, name, args=None, target_name=None, use_isolate=False,
               revision=None, webkit_revision=None, android_isolate_path=None,
               override_compile_targets=None, **runtest_kwargs):
    """Constructs an instance of LocalGTestTest.

    Args:
      name: Displayed name of the test. May be modified by suffixes.
      args: Arguments to be passed to the test.
      target_name: Actual name of the test. Defaults to name.
      use_isolate: When set, uses api.isolate.runtest to invoke the test.
          Calling recipe should have isolate in their DEPS.
      revision: Revision of the Chrome checkout.
      webkit_revision: Revision of the WebKit checkout.
      override_compile_targets: List of compile targets for this test
          (for tests that don't follow target naming conventions).
      runtest_kwargs: Additional keyword args forwarded to the runtest.
    """
    super(LocalGTestTest, self).__init__()
    self._name = name
    self._args = args or []
    self._target_name = target_name
    self._use_isolate = use_isolate
    self._revision = revision
    self._webkit_revision = webkit_revision
    self._android_isolate_path = android_isolate_path
    self._override_compile_targets = override_compile_targets
    self._runtest_kwargs = runtest_kwargs

  @property
  def name(self):
    return self._name

  @property
  def target_name(self):
    return self._target_name or self._name

  @property
  def uses_local_devices(self):
    return True # pragma: no cover

  @property
  def isolate_target(self):
    return self.target_name  # pragma: no cover

  def compile_targets(self, api):
    if self._override_compile_targets:
      return self._override_compile_targets

    if api.chromium.c.TARGET_PLATFORM == 'android':
      return [self.target_name + '_apk']

    return [self.target_name]

  def run(self, api, suffix):
    # Copy the list because run can be invoked multiple times and we modify
    # the local copy.
    args = self._args[:]
    is_android = api.chromium.c.TARGET_PLATFORM == 'android'

    kwargs = {}
    if suffix == 'without patch':
      failures = self.failures(api, 'with patch')
      if is_android:
        kwargs['gtest_filter'] = ':'.join(failures)  # pragma: no cover
      else:
        args.append(api.chromium.test_launcher_filter(failures))

    gtest_results_file = api.test_utils.gtest_results(add_json_log=False)
    step_test_data = lambda: api.test_utils.test_api.canned_gtest_output(True)

    kwargs['name'] = self._step_name(suffix)
    kwargs['args'] = args
    kwargs['step_test_data'] = step_test_data

    if is_android:
      # TODO(sergiyb): Figure out if we can reuse isolate module for running
      # isolated Android tests, rather than using custom solution in Android
      # test launcher.
      if self._android_isolate_path:
        isolate_path = api.path['checkout'].join(self._android_isolate_path)
        kwargs['isolate_file_path'] = isolate_path
      kwargs['json_results_file'] = gtest_results_file
      kwargs['flakiness_dashboard'] = 'test-results.appspot.com'
    else:
      kwargs['xvfb'] = True
      kwargs['test_type'] = self.name
      kwargs['annotate'] = 'gtest'
      kwargs['test_launcher_summary_output'] = gtest_results_file
      kwargs.update(self._runtest_kwargs)

    try:
      if is_android:
        api.chromium_android.run_test_suite(self.target_name, **kwargs)
      elif self._use_isolate:
        api.isolate.runtest(self.target_name, self._revision,
                            self._webkit_revision, **kwargs)
      else:
        api.chromium.runtest(self.target_name, revision=self._revision,
                             webkit_revision=self._webkit_revision, **kwargs)
    finally:
      step_result = api.step.active_result
      self._test_runs[suffix] = step_result

      if hasattr(step_result, 'test_utils'):
        r = step_result.test_utils.gtest_results
        p = step_result.presentation

        if r.valid:
          p.step_text += api.test_utils.format_step_text([
            ['failures:', r.failures]
          ])

    return step_result

  def has_valid_results(self, api, suffix):
    if suffix not in self._test_runs:
      return False  # pragma: no cover
    if not hasattr(self._test_runs[suffix], 'test_utils'):
      return False  # pragma: no cover
    gtest_results = self._test_runs[suffix].test_utils.gtest_results
    if not gtest_results.valid:  # pragma: no cover
      return False
    global_tags = gtest_results.raw.get('global_tags', [])
    return 'UNRELIABLE_RESULTS' not in global_tags

  def failures(self, api, suffix):
    return self._test_runs[suffix].test_utils.gtest_results.failures


def generate_gtest(api, mastername, buildername, test_spec,
                   enable_swarming=False, scripts_compile_targets=None):
  def canonicalize_test(test):
    if isinstance(test, basestring):
      canonical_test = {'test': test}
    else:
      canonical_test = test.copy()

    canonical_test.setdefault('shard_index', 0)
    canonical_test.setdefault('total_shards', 1)
    return canonical_test

  def get_tests(api):
    return [canonicalize_test(t) for t in
            test_spec.get(buildername, {}).get('gtest_tests', [])]

  for test in get_tests(api):
    args = test.get('args', [])
    if test['shard_index'] != 0 or test['total_shards'] != 1:
      args.extend(['--test-launcher-shard-index=%d' % test['shard_index'],
                   '--test-launcher-total-shards=%d' % test['total_shards']])
    use_swarming = False
    swarming_shards = 1
    if enable_swarming:
      swarming_spec = test.get('swarming', {})
      if swarming_spec.get('can_use_on_swarming_builders'):
        use_swarming = True
        swarming_shards = swarming_spec.get('shards', 1)
    yield GTestTest(str(test['test']), args=args, flakiness_dash=True,
                    enable_swarming=use_swarming,
                    swarming_shards=swarming_shards)


def generate_script(api, mastername, buildername, test_spec,
                    enable_swarming=False, scripts_compile_targets=None):
  for script_spec in test_spec.get(buildername, {}).get('scripts', []):
    yield ScriptTest(
        str(script_spec['name']),
        script_spec['script'],
        scripts_compile_targets,  # pragma: no cover
        script_spec.get('args', []))


class DynamicPerfTests(Test):
  def __init__(self, perf_id, platform, target_bits, max_battery_temp=None,
               num_device_shards=1, num_host_shards=1, shard_index=0):
    self._perf_id = perf_id
    self._platform = platform
    self._target_bits = target_bits

    self._max_battery_temp = max_battery_temp
    self._num_host_shards = num_host_shards
    self._num_device_shards = num_device_shards
    self._shard_index = shard_index

  @staticmethod
  def _browser_name(platform, target_bits):
    if platform == 'android':
      return 'android-chromium'
    if platform == 'win' and target_bits == 64:
      return 'release_x64'
    return 'release'

  @property
  def name(self):
    return 'dynamic_perf_tests'

  @property
  def uses_local_devices(self):
    return True

  def run(self, api, suffix):
    tests = self._test_list(api)

    if self._num_device_shards == 1:
      self._run_serially(api, tests)
    else:
      self._run_sharded(api, tests)

  def _test_list(self, api):
    if self._platform == 'android':
      # Must have already called device_status_check().
      device = api.chromium_android.devices[0]
    else:
      device = None

    tests = api.chromium.list_perf_tests(
        browser=self._browser_name(self._platform, self._target_bits),
        num_shards=self._num_host_shards * self._num_device_shards,
        device=device).json.output

    tests['steps'] = {k: v for k, v in tests['steps'].iteritems()
        if v['device_affinity'] / self._num_device_shards == self._shard_index}
    for test_info in tests['steps'].itervalues():
      test_info['device_affinity'] %= self._num_device_shards

    return tests

  def _run_sharded(self, api, tests):
    api.chromium_android.run_sharded_perf_tests(
      config=api.json.input(data=tests),
      perf_id=self._perf_id,
      chartjson_file=True,
      max_battery_temp=self._max_battery_temp)

  def _run_serially(self, api, tests):
    failure = None
    for test_name, test in sorted(tests['steps'].iteritems()):
      test_name = str(test_name)
      annotate = api.chromium.get_annotate_by_test_name(test_name)
      cmd = test['cmd'].split()
      try:
        api.chromium.runtest(
            cmd[1] if len(cmd) > 1 else cmd[0],
            args=cmd[2:],
            name=test_name,
            annotate=annotate,
            python_mode=True,
            results_url='https://chromeperf.appspot.com',
            perf_dashboard_id=test.get('perf_dashboard_id', test_name),
            perf_id=self._perf_id,
            test_type=test.get('perf_dashboard_id', test_name),
            xvfb=True,
            chartjson_file=True)
      except api.step.StepFailure as f:
        failure = f

    if failure:
      raise failure

  @staticmethod
  def compile_targets(_):
    return []


class SwarmingTest(Test):
  def __init__(self, name, dimensions=None, tags=None, target_name=None,
               extra_suffix=None):
    self._name = name
    self._tasks = {}
    self._results = {}
    self._target_name = target_name
    self._dimensions = dimensions
    self._tags = tags
    self._extra_suffix = extra_suffix

  @property
  def name(self):
    if self._extra_suffix:
      return '%s %s' % (self._name, self._extra_suffix)
    else:
      return self._name

  @property
  def target_name(self):
    return self._target_name or self._name

  @property
  def isolate_target(self):
    return self.target_name

  def create_task(self, api, suffix, isolated_hash):
    """Creates a swarming task. Must be overridden in subclasses.

    Args:
      api: Caller's API.
      suffix: Suffix added to the test name.
      isolated_hash: Hash of the isolated test to be run.

    Returns:
      A SwarmingTask object.
    """
    raise NotImplementedError()  # pragma: no cover

  def pre_run(self, api, suffix):
    """Launches the test on Swarming."""
    assert suffix not in self._tasks, (
        'Test %s was already triggered' % self._step_name(suffix))

    # *.isolated may be missing if *_run target is misconfigured. It's a error
    # in gyp, not a recipe failure. So carry on with recipe execution.
    isolated_hash = api.isolate.isolated_tests.get(self.target_name)
    if not isolated_hash:
      return api.python.inline(
          '[error] %s' % self._step_name(suffix),
          r"""
          import sys
          print '*.isolated file for target %s is missing' % sys.argv[1]
          sys.exit(1)
          """,
          args=[self.target_name])

    # Create task.
    self._tasks[suffix] = self.create_task(api, suffix, isolated_hash)

    # Add custom dimensions.
    if self._dimensions:
      self._tasks[suffix].dimensions.update(self._dimensions)

    # Add config-specific tags.
    self._tasks[suffix].tags.update(api.chromium.c.runtests.swarming_tags)

    # Add custom tags.
    if self._tags:
      self._tasks[suffix].tags.update(self._tags)

    # Set default value.
    if 'os' not in self._tasks[suffix].dimensions:
      self._tasks[suffix].dimensions['os'] = api.swarming.prefered_os_dimension(
          api.platform.name)

    return api.swarming.trigger_task(self._tasks[suffix])

  def run(self, api, suffix):  # pylint: disable=R0201
    """Not used. All logic in pre_run, post_run."""
    return []

  def validate_task_results(self, api, step_result):
    """Interprets output of a task (provided as StepResult object).

    Called for successful and failed tasks.

    Args:
      api: Caller's API.
      step_result: StepResult object to examine.

    Returns:
      A tuple (valid, failures), where valid is True if valid results are
      available and failures is a list of names of failed tests (ignored if
      valid is False).
    """
    raise NotImplementedError()  # pragma: no cover

  def post_run(self, api, suffix):
    """Waits for launched test to finish and collects the results."""
    assert suffix not in self._results, (
        'Results of %s were already collected' % self._step_name(suffix))

    # Emit error if test wasn't triggered. This happens if *.isolated is not
    # found. (The build is already red by this moment anyway).
    if suffix not in self._tasks:
      return api.python.inline(
          '[collect error] %s' % self._step_name(suffix),
          r"""
          import sys
          print '%s wasn\'t triggered' % sys.argv[1]
          sys.exit(1)
          """,
          args=[self.target_name])

    try:
      api.swarming.collect_task(self._tasks[suffix])
    finally:
      valid, failures = self.validate_task_results(api, api.step.active_result)
      self._results[suffix] = {'valid': valid, 'failures': failures}

  def has_valid_results(self, api, suffix):
    # Test wasn't triggered or wasn't collected.
    if suffix not in self._tasks or not suffix in self._results:
      return False
    return self._results[suffix]['valid']

  def failures(self, api, suffix):
    assert self.has_valid_results(api, suffix)
    return self._results[suffix]['failures']

  @property
  def uses_swarming(self):
    return True


class SwarmingGTestTest(SwarmingTest):
  def __init__(self, name, args=None, target_name=None, shards=1,
               dimensions=None, tags=None, extra_suffix=None,
               upload_test_results=True):
    super(SwarmingGTestTest, self).__init__(name, dimensions, tags, target_name,
                                            extra_suffix)
    self._args = args or []
    self._shards = shards
    self._upload_test_results = upload_test_results

  def compile_targets(self, api):
    # <X>_run target depends on <X>, and then isolates it invoking isolate.py.
    # It is a convention, not a hard coded rule.
    # Also include name without the _run suffix to help recipes correctly
    # interpret results returned by "analyze".
    return [self.target_name, self.target_name + '_run']

  def create_task(self, api, suffix, isolated_hash):
    # For local tests test_args are added inside api.chromium.runtest.
    args = self._args[:]
    args.extend(api.chromium.c.runtests.test_args)

    # If rerunning without a patch, run only tests that failed.
    if suffix == 'without patch':
      failed_tests = sorted(self.failures(api, 'with patch'))
      args.append('--gtest_filter=%s' % ':'.join(failed_tests))

    args.extend(api.chromium.c.runtests.swarming_extra_args)

    return api.swarming.gtest_task(
        title=self._step_name(suffix),
        isolated_hash=isolated_hash,
        shards=self._shards,
        test_launcher_summary_output=api.test_utils.gtest_results(add_json_log=False),
        extra_args=args)

  def validate_task_results(self, api, step_result):
    if not hasattr(step_result, 'test_utils'):
      return False, None  # pragma: no cover

    gtest_results = step_result.test_utils.gtest_results
    if not gtest_results:
      return False, None  # pragma: no cover

    global_tags = gtest_results.raw.get('global_tags', [])
    if 'UNRELIABLE_RESULTS' in global_tags:
      return False, None  # pragma: no cover

    return True, gtest_results.failures

  def post_run(self, api, suffix):
    """Waits for launched test to finish and collects the results."""
    try:
      super(SwarmingGTestTest, self).post_run(api, suffix)
    finally:
      step_result = api.step.active_result
      # Only upload test results if we have gtest results.
      if (self._upload_test_results and
          hasattr(step_result, 'test_utils') and
          hasattr(step_result.test_utils, 'gtest_results')):
        gtest_results = getattr(step_result.test_utils, 'gtest_results', None)
        if gtest_results and gtest_results.raw:
          parsed_gtest_data = gtest_results.raw
          chrome_revision_cp = api.bot_update.properties.get(
              'got_revision_cp', 'x@{#0}')
          chrome_revision = str(api.commit_position.parse_revision(
              chrome_revision_cp))
          api.test_results.upload(
              api.json.input(parsed_gtest_data),
              chrome_revision=chrome_revision,
              test_type=step_result.step['name'],
              test_results_server='test-results.appspot.com')


class AMPTest(Test):
  AMP_INSTANCE_ADDRESS = '172.22.21.180'
  AMP_INSTANCE_PORT = '80'
  AMP_INSTANCE_PROTOCOL = 'http'
  AMP_RESULTS_BUCKET = 'chrome-amp-results'
  def __init__(self, name, device_name=None, device_os=None, device_oem=None,
               fallback_to_local=True):
    self._name = name
    self._device_name = device_name
    self._device_os = device_os
    self._device_oem = device_oem
    self._fallback_to_local = fallback_to_local
    self._test_run_id = None
    self._trigger_successful = None
    self._step_results = {}

  @property
  def name(self):
    return self._name

  @property
  def uses_local_devices(self):
    return self._fallback_to_local

  def amp_arguments(self, api):
    return api.amp.amp_arguments(
        api_address=AMPTest.AMP_INSTANCE_ADDRESS,
        api_port=AMPTest.AMP_INSTANCE_PORT,
        api_protocol=AMPTest.AMP_INSTANCE_PROTOCOL,
        device_name=self._device_name,
        device_oem=self._device_oem,
        device_os=self._device_os)

  def pre_run(self, api, suffix):
    """Triggers an AMP test."""
    isolate_file_path = (api.path['checkout'].join(self._android_isolate_path)
                         if self._android_isolate_path else None)
    try:
      self.trigger_test(api, suffix)
      self._trigger_successful = True
    except api.step.StepFailure:
      self._trigger_successful = False

  def trigger_test(self, api, suffix):
    self.test_run_id = api.amp.trigger_test_suite(
        step_name=self._step_name(suffix),
        suite=self.name,
        test_type=self.test_type(),
        test_type_args=self.test_type_args(api),
        amp_args=self.amp_arguments(api))

  def test_type(self):
    raise NotImplementedError() # pragma: no cover

  def test_type_args(self, api):
    raise NotImplementedError() # pragma: no cover

  def run(self, api, suffix):
    # If we were unable to successfully trigger the AMP job, run locally;
    # otherwise return no results as results will be collected in post_run.
    if not self._trigger_successful and self._fallback_to_local:
      try:
        self.run_test_locally(api, suffix)
      finally:
        step_result = api.step.active_result
        valid, failures = self.validate_task_results(api, step_result)
        self._step_results[suffix] = {'valid': valid, 'failures': failures}
    else:
      self._step_results[suffix] = {'valid': False, 'failures': []}

  def run_test_locally(self, api):
    raise NotImplementedError() # pragma: no cover

  def post_run(self, api, suffix):
    if self._trigger_successful:
      self.collect_test(api, suffix)

  def collect_test(self, api, suffix):
    gtest_results_file = api.test_utils.gtest_results(add_json_log=False)
    step_test_data = lambda: api.test_utils.test_api.canned_gtest_output(True)

    try:
      api.amp.collect_test_suite(
          step_name=self._step_name(suffix),
          test_run_id=self.test_run_id,
          suite=self.name, test_type=self.test_type(),
          test_type_args=self.test_type_args(api),
          amp_args=self.amp_arguments(api),
          json_results_file=gtest_results_file,
          step_test_data=step_test_data)
    finally:
      step_result = api.step.active_result
      valid, failures = self.validate_task_results(api, step_result)
      self._step_results[suffix] = {'valid': valid, 'failures': failures}

  def validate_task_results(self, api, step_result):
    try:
      gtest_results = step_result.test_utils.gtest_results
      valid = gtest_results.valid
      failures = gtest_results.failures
    except (AttributeError, KeyError, ValueError): # pragma: no cover
      valid = False
      failures = None

    if valid:
      step_result.presentation.step_text += api.test_utils.format_step_text([
        ['failures:', failures]
      ])

    return valid, failures

  def has_valid_results(self, api, suffix):
    return suffix in self._step_results and self._step_results[suffix]['valid']

  def failures(self, api, suffix):
    assert self.has_valid_results(api, suffix)
    return self._step_results[suffix]['failures']


class AMPGTestTest(AMPTest):
  def __init__(self, name, args=None, target_name=None, device_name=None,
               device_os=None, device_oem=None, android_isolate_path=None,
               fallback_to_local=True, **runtest_kwargs):
    super(AMPGTestTest, self).__init__(
        name=name, device_name=device_name, device_os=device_os,
        device_oem=device_oem, fallback_to_local=fallback_to_local)
    self._args = args
    self._target_name = target_name
    self._android_isolate_path = android_isolate_path
    # LocalGTestTest is used when AMP tests are not triggered successfully.
    self._local_test = LocalGTestTest(name, args, target_name, **runtest_kwargs)

  def compile_targets(self, api):
    return self._local_test.compile_targets(api)

  #override
  def test_type(self):
    return 'gtest'

  #override
  def test_type_args(self, api):
    isolate_file_path = (api.path['checkout'].join(self._android_isolate_path)
                         if self._android_isolate_path else None)
    return api.amp.gtest_arguments(
        self.name, isolate_file_path=isolate_file_path)

  #override
  def run_test_locally(self, api, suffix):
    return self._local_test.run(api, suffix)


class AMPInstrumentationTest(AMPTest):
  def __init__(self, test_apk, apk_under_test, compile_target=None,
               device_name=None, device_os=None, device_oem=None,
               android_isolate_path=None, fallback_to_local=True):
    super(AMPInstrumentationTest, self).__init__(
        test_apk, device_name=device_name, device_os=device_os,
        device_oem=device_oem, fallback_to_local=fallback_to_local)
    self._apk_under_test = apk_under_test
    self._compile_target = compile_target
    self._android_isolate_path = android_isolate_path

  def compile_targets(self, api):
    return [self._compile_target]

  #override
  def test_type(self):
    return 'instrumentation'

  #override
  def test_type_args(self, api):
    isolate_file_path = (api.path['checkout'].join(self._android_isolate_path)
                         if self._android_isolate_path else None)
    return api.amp.instrumentation_test_arguments(
        apk_under_test=self._apk_under_test,
        test_apk=self.name,
        isolate_file_path=isolate_file_path)

  #override
  def run_test_locally(self, api, suffix):
    isolate_file_path = (api.path['checkout'].join(self._android_isolate_path)
                         if self._android_isolate_path else None)
    return AndroidInstrumentationTest(
        name=self.name,
        compile_target=self._compile_target,
        adb_install_apk=self._apk_under_test,
        isolate_file_path=isolate_file_path).run(api, suffix)


class IsolatedScriptTest(Test):
  def __init__(self, name, args=None, target_name=None, **runtest_kwargs):
    """Constructs an instance of IsolatedScriptTest.

    An IsolatedScriptTest knows how to invoke an isolate which obeys a certain
    contract. The isolate's main target must be a wrapper script which must
    interpret certain command line arguments as follows:

      --isolated-script-test-output [FILENAME]

    The wrapper script must write the simplified json output that the recipes
    consume (similar to GTestTest and ScriptTest) into |FILENAME|.

    The contract may be expanded later to support functionality like sharding
    and retries of specific failed tests. Currently the examples of such wrapper
    scripts live in src/testing/scripts/ in the Chromium workspace.

    Args:
      name: Displayed name of the test. May be modified by suffixes.
      args: Arguments to be passed to the test.
      target_name: Actual name of the test. Defaults to name.
      runtest_kwargs: Additional keyword args forwarded to the runtest.
    """
    super(IsolatedScriptTest, self).__init__()
    self._name = name
    self._args = args or []
    self._target_name = target_name
    self._runtest_kwargs = runtest_kwargs

  @property
  def name(self):
    return self._name

  @property
  def target_name(self):
    return self._target_name or self._name

  @property
  def isolate_target(self):
    return self.target_name

  @property
  def uses_swarming(self):
    return True

  def compile_targets(self, _):
    return [self.target_name]

  # TODO(nednguyen, kbr): figure out what to do with Android.
  # (crbug.com/533480)
  def run(self, api, suffix):
    # Copy the list because run can be invoked multiple times and we modify
    # the local copy.
    args = self._args[:]

    # TODO(nednguyen, kbr): define contract with the wrapper script to rerun
    # a subset of the tests. (crbug.com/533481)

    json_results_file = api.json.output()
    args.extend(
        ['--isolated-script-test-output', json_results_file])

    step_test_data = lambda: api.json.test_api.output(
        {'valid': True, 'failures': []})

    try:
      api.isolate.run_isolated(
          self.name,
          api.isolate.isolated_tests[self.target_name],
          args,
          step_test_data=step_test_data)
    finally:
      self._test_runs[suffix] = api.step.active_result
      if self.has_valid_results(api, suffix):
        self._test_runs[suffix].presentation.step_text += (
            api.test_utils.format_step_text([
              ['failures:', self.failures(api, suffix)]
            ]))

    return self._test_runs[suffix]

  def has_valid_results(self, api, suffix):
    try:
      # Make sure the JSON includes all necessary data.
      self.failures(api, suffix)

      return self._test_runs[suffix].json.output['valid']
    except Exception:  # pragma: no cover
      return False

  def failures(self, api, suffix):
    return self._test_runs[suffix].json.output['failures']


def generate_isolated_script(api, mastername, buildername, test_spec,
                             enable_swarming=False,
                             scripts_compile_targets=None):
  for spec in test_spec.get(buildername, {}).get(
      'isolated_scripts', []):
    yield IsolatedScriptTest(
        str(spec['name']),
        args=spec.get('args', []),
        target_name=spec['isolate_name'])


class GTestTest(Test):
  def __init__(self, name, args=None, target_name=None, enable_swarming=False,
               swarming_shards=1, swarming_dimensions=None, swarming_tags=None,
               swarming_extra_suffix=None, **runtest_kwargs):
    super(GTestTest, self).__init__()
    if enable_swarming:
      self._test = SwarmingGTestTest(
          name, args, target_name, swarming_shards, swarming_dimensions,
          swarming_tags, swarming_extra_suffix)
    else:
      self._test = LocalGTestTest(name, args, target_name, **runtest_kwargs)

  @property
  def name(self):
    return self._test.name

  @property
  def uses_local_devices(self):
    return True

  @property
  def isolate_target(self):
    return self._test.isolate_target

  def compile_targets(self, api):
    return self._test.compile_targets(api)

  def pre_run(self, api, suffix):
    return self._test.pre_run(api, suffix)

  def run(self, api, suffix):
    return self._test.run(api, suffix)

  def post_run(self, api, suffix):
    return self._test.post_run(api, suffix)

  def has_valid_results(self, api, suffix):
    return self._test.has_valid_results(api, suffix)

  def failures(self, api, suffix):
    return self._test.failures(api, suffix)

  @property
  def uses_swarming(self):
    return self._test.uses_swarming


# TODO(sergiyb): GPU Tests do not always follow the Chromium convention to have
# a 'test' target for each 'test_run' target. Instead they use gyp dependencies.
# Chromium tests return both 'test' and 'test_run' to circumvent and issue with
# analyze step, while GPU tests do not require this.
class GPUGTestTest(GTestTest):
  def __init__(self, name, **kwargs):
    kwargs['swarming_tags'] = set(kwargs.get('swarming_tags') or [])
    kwargs['swarming_tags'] |= {'gpu_test:1'}
    super(GPUGTestTest, self).__init__(name, **kwargs)

  def compile_targets(self, api):
    return ['%s_run' % self._test.target_name]


class PythonBasedTest(Test):
  @staticmethod
  def compile_targets(_):
    return []  # pragma: no cover

  def run_step(self, api, suffix, cmd_args, **kwargs):
    raise NotImplementedError()  # pragma: no cover

  def run(self, api, suffix):
    cmd_args = ['--write-full-results-to',
                api.test_utils.test_results(add_json_log=False)]
    if suffix == 'without patch':
      cmd_args.extend(self.failures(api, 'with patch'))  # pragma: no cover

    try:
      self.run_step(
          api,
          suffix,
          cmd_args,
          step_test_data=lambda: api.test_utils.test_api.canned_test_output(True))
    finally:
      step_result = api.step.active_result
      self._test_runs[suffix] = step_result

      if hasattr(step_result, 'test_utils'):
        r = step_result.test_utils.test_results
        p = step_result.presentation
        p.step_text += api.test_utils.format_step_text([
          ['unexpected_failures:', r.unexpected_failures.keys()],
        ])

    return step_result

  def has_valid_results(self, api, suffix):
    # TODO(dpranke): we should just return zero/nonzero for success/fail.
    # crbug.com/357866
    step = self._test_runs[suffix]
    if not hasattr(step, 'test_utils'):
      return False  # pragma: no cover
    return (step.test_utils.test_results.valid and
            step.retcode <= step.test_utils.test_results.MAX_FAILURES_EXIT_STATUS and
            (step.retcode == 0) or self.failures(api, suffix))

  def failures(self, api, suffix):
    return self._test_runs[suffix].test_utils.test_results.unexpected_failures


class PrintPreviewTests(PythonBasedTest):  # pylint: disable=W032
  name = 'print_preview_tests'

  def run_step(self, api, suffix, cmd_args, **kwargs):
    platform_arg = '.'.join(['browser_test',
        api.platform.normalize_platform_name(api.platform.name)])
    args = cmd_args
    path = api.path['checkout'].join(
        'third_party', 'WebKit', 'Tools', 'Scripts', 'run-webkit-tests')
    args.extend(['--platform', platform_arg])

    # This is similar to how api.chromium.run_telemetry_test() sets the
    # environment variable for the sandbox.
    env = {}
    if api.platform.is_linux:
      env['CHROME_DEVEL_SANDBOX'] = api.path.join(
          '/opt', 'chromium', 'chrome_sandbox')

    return api.chromium.runtest(
        test=path,
        args=args,
        xvfb=True,
        name=self._step_name(suffix),
        python_mode=True,
        env=env,
        **kwargs)

  @staticmethod
  def compile_targets(api):
    targets = ['browser_tests', 'blink_tests']
    if api.platform.is_win:
      targets.append('crash_service')

    return targets


class TelemetryGPUTest(Test):  # pylint: disable=W0232
  def __init__(self, name, revision=None, webkit_revision=None,
               target_name=None, args=None, enable_swarming=False,
               swarming_dimensions=None, swarming_extra_suffix=None,
               **runtest_kwargs):
    if enable_swarming:
      self._test = SwarmingTelemetryGPUTest(
          name, args=args, dimensions=swarming_dimensions,
          target_name=target_name, extra_suffix=swarming_extra_suffix)
    else:
      self._test = LocalTelemetryGPUTest(
          name, revision, webkit_revision, args=args, target_name=target_name,
          **runtest_kwargs)

  @property
  def name(self):
    return self._test.name

  @property
  def isolate_target(self):
    return self._test.isolate_target

  def compile_targets(self, api):
    return self._test.compile_targets(api)

  def pre_run(self, api, suffix):
    return self._test.pre_run(api, suffix)

  def run(self, api, suffix):
    return self._test.run(api, suffix)

  def post_run(self, api, suffix):
    return self._test.post_run(api, suffix)

  def has_valid_results(self, api, suffix):
    return self._test.has_valid_results(api, suffix)

  def failures(self, api, suffix):
    return self._test.failures(api, suffix)

  @property
  def uses_swarming(self):
    return self._test.uses_swarming


class BisectTest(Test):  # pylint: disable=W0232
  name = 'bisect_test'

  @property
  def abort_on_failure(self):
    return True  # pragma: no cover

  @property
  def uses_local_devices(self):
    return True

  @staticmethod
  def compile_targets(_):  # pragma: no cover
    return ['chrome'] # Bisect always uses a separate bot for building.

  def pre_run(self, api, _):
    self.test_config = api.bisect_tester.load_config_from_dict(
        api.properties.get('bisect_config'))

  def run(self, api, _):
    self._run_results, self.test_output, self.retcodes = (
        api.bisect_tester.run_test(self.test_config))

  def post_run(self, api, _):
      self.values = api.bisect_tester.digest_run_results(
          self._run_results, self.retcodes, self.test_config)
      api.bisect_tester.upload_results(self.test_output, self.values,
                                       self.retcodes)

  def has_valid_results(self, *_):
    return len(getattr(self, 'values', [])) > 0  # pragma: no cover

  def failures(self, *_):
    return self._failures  # pragma: no cover


class LocalTelemetryGPUTest(Test):  # pylint: disable=W0232
  def __init__(self, name, revision, webkit_revision,
               target_name=None, **runtest_kwargs):
    """Constructs an instance of LocalTelemetryGPUTest.

    Args:
      name: Displayed name of the test. May be modified by suffixes.
      revision: Revision of the Chrome checkout.
      webkit_revision: Revision of the WebKit checkout.
      target_name: Actual name of the test. Defaults to name.
      runtest_kwargs: Additional keyword args forwarded to the runtest.
    """
    super(LocalTelemetryGPUTest, self).__init__()
    self._name = name
    self._target_name = target_name
    self._revision = revision
    self._webkit_revision = webkit_revision
    self._failures = {}
    self._valid = {}
    self._runtest_kwargs = runtest_kwargs

  @property
  def name(self):
    return self._name

  @property
  def target_name(self):
    return self._target_name or self._name

  @property
  def isolate_target(self):
    return self.target_name  # pragma: no cover

  def compile_targets(self, _):
    # TODO(sergiyb): Build 'chrome_public_apk' instead of 'chrome' on Android.
    return ['chrome', 'telemetry_gpu_test_run']  # pragma: no cover

  def run(self, api, suffix):  # pylint: disable=R0201
    kwargs = self._runtest_kwargs.copy()
    kwargs['args'].extend(['--output-format', 'json',
                           '--output-dir', api.raw_io.output_dir()])
    step_test_data=lambda: api.test_utils.test_api.canned_telemetry_gpu_output(
        passing=True, is_win=api.platform.is_win)
    try:
      api.isolate.run_telemetry_test(
          'telemetry_gpu_test',
          self.target_name,
          self._revision,
          self._webkit_revision,
          name=self._step_name(suffix),
          spawn_dbus=True,
          step_test_data=step_test_data,
          **self._runtest_kwargs)
    finally:
      step_result = api.step.active_result
      self._test_runs[suffix] = step_result

      try:
        res = api.json.loads(step_result.raw_io.output_dir['results.json'])
        failures = [res['pages'][str(value['page_id'])]['name']
                   for value in res['per_page_values']
                   if value['type'] == 'failure']
        if not failures and step_result.retcode != 0:
          failures = ['%s (entire test suite)' % self.name]

        self._failures[suffix] = failures
        self._valid[suffix] = True
      except (ValueError, KeyError, AttributeError):  # pragma: no cover
        self._valid[suffix] = False

      if self._valid[suffix]:
        step_result.presentation.step_text += api.test_utils.format_step_text([
          ['failures:', self._failures[suffix]]
        ])

  def has_valid_results(self, api, suffix):
    return suffix in self._valid and self._valid[suffix]  # pragma: no cover

  def failures(self, api, suffix):  # pragma: no cover
    assert self.has_valid_results(api, suffix)
    assert suffix in self._failures
    return self._failures[suffix]


class SwarmingTelemetryGPUTest(SwarmingTest):
  def __init__(self, name, args=None, dimensions=None, target_name=None,
               extra_suffix=None):
    super(SwarmingTelemetryGPUTest, self).__init__(
        name, dimensions, {'gpu_test:1'}, 'telemetry_gpu_test', extra_suffix)
    self._args = args
    self._telemetry_target_name = target_name or name

  def compile_targets(self, _):
    # TODO(sergiyb): Build 'chrome_public_apk' instead of 'chrome' on Android.
    return ['chrome', 'telemetry_gpu_test_run']

  def create_task(self, api, suffix, isolated_hash):
    # For local tests args are added inside api.chromium.run_telemetry_test.
    browser_config = api.chromium.c.build_config_fs.lower()
    args = [self._telemetry_target_name, '--show-stdout',
            '--browser=%s' % browser_config] + self._args

    # If rerunning without a patch, run only tests that failed.
    if suffix == 'without patch':
      failed_tests = sorted(self.failures(api, 'with patch'))
      # Telemetry test launcher uses re.compile to parse --story-filter argument,
      # therefore we escape any special characters in test names.
      failed_tests = [re.escape(test_name) for test_name in failed_tests]
      args.append('--story-filter=%s' % '|'.join(failed_tests))

    return api.swarming.telemetry_gpu_task(
        title=self._step_name(suffix), isolated_hash=isolated_hash,
        extra_args=args)

  def validate_task_results(self, api, step_result):
    results = getattr(step_result, 'telemetry_results', None) or {}

    try:
      failures = [results['pages'][str(value['page_id'])]['name']
                  for value in results['per_page_values']
                  if value['type'] == 'failure']
      if not failures and step_result.retcode != 0:
        failures = ['%s (entire test suite)' % self.name]

      valid = True
    except (ValueError, KeyError) as e:  # pragma: no cover
      step_result.presentation.logs['invalid_results_exc'] = [str(e)]
      valid = False
      failures = None

    if valid:
      step_result.presentation.step_text += api.test_utils.format_step_text([
        ['failures:', failures]
      ])

    return valid, failures


class AndroidTest(Test):
  def __init__(self, name, compile_target, adb_install_apk=None,
               isolate_file_path=None):
    super(AndroidTest, self).__init__()

    self._name = name
    self.compile_target = compile_target

    self.adb_install_apk = adb_install_apk
    self.isolate_file_path = isolate_file_path

  @property
  def name(self):
    return self._name

  @property
  def uses_local_devices(self):
    return True

  def _get_failing_tests(self, step_result):
    """Parses test results and returns a list of failed tests.

    Args:
      step_result: Result returned from the test.

    Returns:
      None if results are invalid, a list of failures otherwise (may be empty).
    """
    try:
      # Extract test results.
      json_results = step_result.json.output
      test_results = {test_name: test_data[0]['status']
                      for result_dict in json_results['per_iteration_data']
                      for test_name, test_data in result_dict.iteritems()}

      # TODO(sergiyb): Figure out how to handle status UNKNOWN.
      return sorted(
          [test_name for test_name, test_status in test_results.iteritems()
           if test_status not in ['SUCCESS', 'SKIPPED']])
    except (KeyError, IndexError, TypeError,
            AttributeError):  # pragma: no cover
      return None

  def run_tests(self, api, suffix, json_results_file):
    """Runs the Android test suite and outputs the json results to a file.

    Args:
      api: Caller's API.
      suffix: Suffix added to the test name.
      json_results_file: File to output the test results.
    """
    raise NotImplementedError()  # pragma: no cover

  def run(self, api, suffix):
    assert api.chromium.c.TARGET_PLATFORM == 'android'
    if self.adb_install_apk:
      api.chromium_android.adb_install_apk(self.adb_install_apk)
    try:
      json_results_file = api.json.output(add_json_log=False)
      self.run_tests(api, suffix, json_results_file)
    finally:
      step_result = api.step.active_result
      failures = self._get_failing_tests(step_result)

      if failures is None:
        self._test_runs[suffix] = {'valid': False}  # pragma: no cover
      else:
        self._test_runs[suffix] = {'valid': True, 'failures': failures}

        step_result.presentation.step_text += api.test_utils.format_step_text([
          ['failures:', failures]
        ])

  def compile_targets(self, _):
    return [self.compile_target]

  def has_valid_results(self, api, suffix):
    if suffix not in self._test_runs:
      return False  # pragma: no cover
    return self._test_runs[suffix]['valid']

  def failures(self, api, suffix):
    assert self.has_valid_results(api, suffix)
    return self._test_runs[suffix]['failures']


class AndroidJunitTest(AndroidTest):
  def __init__(self, name):
    super(AndroidJunitTest, self).__init__(name, compile_target=name,
        adb_install_apk=None, isolate_file_path=None)

  #override
  def run_tests(self, api, suffix, json_results_file):
    mock_test_results = {
      'per_iteration_data': [{'TestA': [{'status': 'SUCCESS'}]},
                             {'TestB': [{'status': 'FAILURE'}]}]
    }
    api.chromium_android.run_java_unit_test_suite(
        self.name, verbose=True, suffix=suffix,
        json_results_file=json_results_file,
        step_test_data=lambda: api.json.test_api.output(mock_test_results))


class AndroidInstrumentationTest(AndroidTest):
  def __init__(self, name, compile_target, adb_install_apk=None,
               isolate_file_path=None,
               flakiness_dashboard='test-results.appspot.com',
               annotation=None, except_annotation=None, screenshot=False,
               verbose=True, host_driven_root=None):
    super(AndroidInstrumentationTest, self).__init__(name, compile_target,
        adb_install_apk, isolate_file_path)
    self._flakiness_dashboard = flakiness_dashboard
    self._annotation = annotation
    self._except_annotation = except_annotation
    self._screenshot = screenshot
    self._verbose = verbose
    self._host_driven_root = host_driven_root

  #override
  def run_tests(self, api, suffix, json_results_file):
    mock_test_results = {
      'per_iteration_data': [{'TestA': [{'status': 'SUCCESS'}]},
                             {'TestB': [{'status': 'FAILURE'}]}]
    }
    api.chromium_android.run_instrumentation_suite(
        self.name, suffix=suffix,
        isolate_file_path=self.isolate_file_path,
        flakiness_dashboard=self._flakiness_dashboard,
        annotation=self._annotation, except_annotation=self._except_annotation,
        screenshot=self._screenshot, verbose=self._verbose,
        host_driven_root=self._host_driven_root,
        json_results_file=json_results_file,
        step_test_data=lambda: api.json.test_api.output(mock_test_results))


class BlinkTest(Test):
  # TODO(dpranke): This should be converted to a PythonBasedTest, although it
  # will need custom behavior because we archive the results as well.
  def __init__(self, extra_args=None):
    super(BlinkTest, self).__init__()
    self._extra_args = extra_args

  name = 'webkit_tests'

  @staticmethod
  def compile_targets(api):
    return ['blink_tests']

  @property
  def uses_local_devices(self):
    return True

  def run(self, api, suffix):
    results_dir = api.path['slave_build'].join('layout-test-results')

    args = [
        '--target', api.chromium.c.BUILD_CONFIG,
        '-o', results_dir,
        '--build-dir', api.chromium.c.build_dir,
        '--json-test-results', api.test_utils.test_results(add_json_log=False),
        '--test-results-server', 'test-results.appspot.com',
        '--build-number', str(api.properties['buildnumber']),
        '--builder-name', api.properties['buildername'],
    ]
    if api.chromium.c.TARGET_PLATFORM == 'android':
      args.extend(['--platform', 'android'])
    if self._extra_args:
      args.extend(self._extra_args)
    if suffix == 'without patch':
      test_list = "\n".join(self.failures(api, 'with patch'))
      args.extend(['--test-list', api.raw_io.input(test_list),
                   '--skipped', 'always'])

    try:
      step_result = api.chromium.runtest(
          api.path['build'].join('scripts', 'slave', 'chromium',
                                 'layout_test_wrapper.py'),
          args, name=self._step_name(suffix),
          # TODO(phajdan.jr): Clean up the runtest.py mess.
          disable_src_side_runtest_py=True,
          step_test_data=lambda: api.test_utils.test_api.canned_test_output(
              passing=True, minimal=True))

      # Mark steps with unexpected flakes as warnings. Do this here instead of
      # "finally" blocks because we only want to do this if step was successful.
      # We don't want to possibly change failing steps to warnings.
      if step_result and step_result.test_utils.test_results.unexpected_flakes:
        step_result.presentation.status = api.step.WARNING
    finally:
      step_result = api.step.active_result
      self._test_runs[suffix] = step_result

      if step_result:
        r = step_result.test_utils.test_results
        p = step_result.presentation

        if r.valid:
          p.step_text += api.test_utils.format_step_text([
            ['unexpected_flakes:', r.unexpected_flakes.keys()],
            ['unexpected_failures:', r.unexpected_failures.keys()],
            ['Total executed: %s' % r.num_passes],
          ])

      if suffix in ('', 'with patch'):
        buildername = api.properties['buildername']
        buildnumber = api.properties['buildnumber']

        archive_layout_test_results = api.path['build'].join(
            'scripts', 'slave', 'chromium', 'archive_layout_test_results.py')

        archive_layout_test_args = [
          '--results-dir', results_dir,
          '--build-dir', api.chromium.c.build_dir,
          '--build-number', buildnumber,
          '--builder-name', buildername,
          '--gs-bucket', 'gs://chromium-layout-test-archives',
        ]
        # TODO(phajdan.jr): Pass gs_acl as a parameter, not build property.
        if api.properties.get('gs_acl'):
          archive_layout_test_args.extend(['--gs-acl', api.properties['gs_acl']])
        archive_result = api.python(
          'archive_webkit_tests_results',
          archive_layout_test_results,
          archive_layout_test_args)

        # TODO(infra): http://crbug.com/418946 .
        sanitized_buildername = re.sub('[ .()]', '_', buildername)
        base = (
          "https://storage.googleapis.com/chromium-layout-test-archives/%s/%s"
          % (sanitized_buildername, buildnumber))

        archive_result.presentation.links['layout_test_results'] = (
            base + '/layout-test-results/results.html')
        archive_result.presentation.links['(zip)'] = (
            base + '/layout-test-results.zip')

  def has_valid_results(self, api, suffix):
    if suffix not in self._test_runs:
      return False
    step = self._test_runs[suffix]
    # TODO(dpranke): crbug.com/357866 - note that all comparing against
    # MAX_FAILURES_EXIT_STATUS tells us is that we did not exit early
    # or abnormally; it does not tell us how many failures there actually
    # were, which might be much higher (up to 5000 diffs, where we
    # would bail out early with --exit-after-n-failures) or lower
    # if we bailed out after 100 crashes w/ -exit-after-n-crashes, in
    # which case the retcode is actually 130
    return (step.test_utils.test_results.valid and
            step.retcode <= step.test_utils.test_results.MAX_FAILURES_EXIT_STATUS)

  def failures(self, api, suffix):
    return self._test_runs[suffix].test_utils.test_results.unexpected_failures


class MiniInstallerTest(PythonBasedTest):  # pylint: disable=W0232
  name = 'test_installer'

  @staticmethod
  def compile_targets(_):
    return ['mini_installer']

  def run_step(self, api, suffix, cmd_args, **kwargs):
    test_path = api.path['checkout'].join('chrome', 'test', 'mini_installer')
    args = [
      '--build-dir', api.chromium.c.build_dir,
      '--target', api.chromium.c.build_config_fs,
      '--force-clean',
      '--config', test_path.join('config', 'config.config'),
    ]
    args.extend(cmd_args)
    return api.python(
      self._step_name(suffix),
      test_path.join('test_installer.py'),
      args,
      **kwargs)


class DiagnoseGomaTest(Test):
  name = 'diagnose_goma'

  @property
  def name(self):
    return 'diagnose_goma'

  def run(self, api, suffix):
    diagnose_goma_log_py = api.path['build'].join('goma',
                                                  'diagnose_goma_log.py')
    api.python('diagnose_goma', diagnose_goma_log_py, [])


class IncrementalCoverageTest(Test):
  name = 'incremental_coverage'

  @property
  def uses_local_devices(self):
    return True

  def has_valid_results(self, api, suffix):
    return True

  def failures(self, api, suffix):
    return []

  @property
  def name(self):  # pragma: no cover
    """Name of the test."""
    return self.name

  @staticmethod
  def compile_targets(api):
    """List of compile targets needed by this test."""
    return []

  def run(self, api, suffix):
    api.chromium_android.coverage_report(upload=False)
    api.chromium_android.get_changed_lines_for_revision()
    api.chromium_android.incremental_coverage_report()


GOMA_TESTS = [
  GTestTest('base_unittests'),
  GTestTest('content_unittests'),
]
