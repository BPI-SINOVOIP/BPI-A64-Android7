# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


class IsolateApi(recipe_api.RecipeApi):
  """APIs for interacting with isolates."""

  def __init__(self, **kwargs):
    super(IsolateApi, self).__init__(**kwargs)
    self._isolate_server = 'https://isolateserver.appspot.com'
    self._isolated_tests = {}

  @property
  def isolate_server(self):
    """URL of Isolate server to use, default is a production one."""
    return self._isolate_server

  @isolate_server.setter
  def isolate_server(self, value):
    """Changes URL of Isolate server to use."""
    self._isolate_server = value

  def set_isolate_environment(self, config):
    """Modifies the config to include isolate related GYP_DEFINES.

    Modifies the passed Config (which should generally be api.chromium.c) to set
    up the appropriate GYP_DEFINES to prepare all necessary files to do this
    after compile. This must be called early in your recipe; definitely before
    the checkout and runhooks steps.
    """
    config.gyp_env.GYP_DEFINES['test_isolation_mode'] = 'prepare'

  def clean_isolated_files(self, build_dir):
    """Cleans out all *.isolated files from the build directory in
    preparation for the compile. Needed in order to ensure isolates
    are rebuilt properly because their dependencies are currently not
    completely described to gyp.

    Should be invoked before compilation in both 'archive' or 'prepare' modes
    (see 'set_isolate_environment').
    """
    self.m.python(
      'clean isolated files',
      self.resource('find_isolated_tests.py'),
      [
        '--build-dir', build_dir,
        '--clean-isolated-files'
      ])

  def find_isolated_tests(self, build_dir, targets=None, **kwargs):
    """Returns a step which finds all *.isolated files in a build directory.

    Useful only with 'archive' isolation mode (see 'set_isolate_environment').
    In 'prepare' mode use 'isolate_tests' instead.

    Assigns the dict {target name -> *.isolated file hash} to the swarm_hashes
    build property. This implies this step can currently only be run once
    per recipe.

    If |targets| is None, the step will use all *.isolated files it finds.
    Otherwise, it will verify that all |targets| are found and will use only
    them. If some expected targets are missing, will abort the build.
    """
    step_result = self.m.python(
      'find isolated tests',
      self.resource('find_isolated_tests.py'),
      [
        '--build-dir', build_dir,
        '--output-json', self.m.json.output(),
      ],
      step_test_data=lambda: (self.test_api.output_json(targets)),
      **kwargs)

    assert isinstance(step_result.json.output, dict)
    self._isolated_tests = step_result.json.output
    if targets is not None and (
            step_result.presentation.status != self.m.step.FAILURE):
      found = set(step_result.json.output)
      expected = set(targets)
      if found >= expected:
        # Limit result only to |expected|.
        self._isolated_tests = {
          target: step_result.json.output[target] for target in expected
        }
      else:
        # Some expected targets are missing? Fail the step.
        step_result.presentation.status = self.m.step.FAILURE
        step_result.presentation.logs['missing.isolates'] = (
            ['Failed to find *.isolated files:'] + list(expected - found))
    step_result.presentation.properties['swarm_hashes'] = self._isolated_tests
    # No isolated files found? That looks suspicious, emit warning.
    if (not self._isolated_tests and
        step_result.presentation.status != self.m.step.FAILURE):
      step_result.presentation.status = self.m.step.WARNING

  def isolate_tests(self, build_dir, targets=None, verbose=False,
                    set_swarm_hashes=True, **kwargs):
    """Archives prepared tests in |build_dir| to isolate server.

    Works only if Chromium was compiled with test_isolation_mode=='prepare'. See
    set_isolate_environment(). In that mode src/tools/isolate_driver.py is
    invoked by ninja during compilation to produce *.isolated.gen.json files
    that describe how to archive tests.

    This step then uses *.isolated.gen.json files to actually performs the
    archival. By archiving all tests at once it is able to reduce the total
    amount of work. Tests share many common files, and such files are processed
    only once.

    Assigns the dict {target name -> *.isolated file hash} to the swarm_hashes
    build property (also accessible as 'isolated_tests' property). This implies
    this step can currently only be run once per recipe.
    """
    # TODO(vadimsh): Always require |targets| to be passed explicitly. Currently
    # chromium_trybot, blink_trybot and swarming/canary recipes rely on targets
    # autodiscovery. The code path in chromium_trybot that needs it is being
    # deprecated in favor of to *_ng builders, that pass targets explicitly.
    if targets is None:
      # Ninja builds <target>.isolated.gen.json files via isolate_driver.py.
      paths = self.m.file.glob(
          'find isolated targets',
          build_dir.join('*.isolated.gen.json'),
          test_data=[
            build_dir.join('dummy_target_%d.isolated.gen.json' % i)
            for i in (1, 2)
          ])
      targets = []
      for p in paths:
        name = self.m.path.basename(p)
        assert name.endswith('.isolated.gen.json'), name
        targets.append(name[:-len('.isolated.gen.json')])

    # No isolated tests found.
    if not targets:  # pragma: no cover
      return

    input_files = [build_dir.join('%s.isolated.gen.json' % t) for t in targets]
    try:
      # TODO(vadimsh): Differentiate between bad *.isolate and upload errors.
      # Raise InfraFailure on upload errors.
      args = [
        self.m.swarming_client.path,
        'batcharchive',
        '--dump-json', self.m.json.output(),
        '--isolate-server', self._isolate_server,
      ] + (['--verbose'] if verbose else []) + input_files
      return self.m.python(
          'isolate tests', self.resource('isolate.py'), args,
          step_test_data=lambda: (self.test_api.output_json(targets)),
          **kwargs)
    finally:
      step_result = self.m.step.active_result
      self._isolated_tests = step_result.json.output
      if self._isolated_tests:
        presentation = step_result.presentation
        if set_swarm_hashes:
          presentation.properties['swarm_hashes'] = self._isolated_tests
        missing = sorted(
            t for t, h in self._isolated_tests.iteritems() if not h)
        if missing:
          step_result.presentation.logs['failed to isolate'] = (
            ['Failed to isolate following targets:'] +
            missing +
            ['', 'See logs for more information.']
          )
          for k in missing:
            self._isolated_tests.pop(k)

  @property
  def isolated_tests(self):
    """The dictionary of 'target name -> isolated hash' for this run.

    These come either from the incoming swarm_hashes build property,
    or from calling find_isolated_tests, above, at some point during the run.
    """
    hashes = self.m.properties.get('swarm_hashes', self._isolated_tests)
    # Be robust in the case where swarm_hashes is an empty string
    # instead of an empty dictionary, or similar.
    if not hashes:
      return {} # pragma: no covergae
    return {
      k.encode('ascii'): v.encode('ascii')
      for k, v in hashes.iteritems()
    }

  @property
  def _run_isolated_path(self):
    """Returns the path to run_isolated.py."""
    return self.m.swarming_client.path.join('run_isolated.py')

  def runtest_args_list(self, test, args=None):
    """Array of arguments for running the given test via run_isolated.py.

    The test should be already uploaded to the isolated server. The method
    expects to find |test| as a key in the isolated_tests dictionary.
    """
    assert test in self.isolated_tests, (test, self.isolated_tests)
    full_args = [
      '--isolated',
      self.isolated_tests[test],
      '-I',
      self._isolate_server,
      # Always append '--' to the argument list. api.chromium.runtest
      # will add any flags like --gtest_output to the end of the command
      # line. run_isolated.py must treat these as extra arguments to the
      # isolate.
      '--'
    ]
    if args:
      full_args.extend(args)
    return full_args

  def run_isolated(self, name, isolate_hash, args=None, **kwargs):
    """Runs an isolated test."""
    cmd = [
        '--isolated', isolate_hash,
        '-I', self.isolate_server,
        '--verbose',
    ]
    if args:
      cmd.append('--')
      cmd.extend(args)
    self.m.python(name, self._run_isolated_path, cmd, **kwargs)

  def runtest(self, test, revision, webkit_revision, args=None, name=None,
              **runtest_kwargs):
    """Runs a test which has previously been isolated to the server.

    Uses runtest_args_list, above, and delegates to api.chromium.runtest.

    DEPRECATED - run_isolated above is strongly recommended for all new callers.
    """
    self.m.chromium.runtest(
        self._run_isolated_path,
        args=self.runtest_args_list(test, args),
        # We must use the name of the test as the name in order to avoid
        # duplicate steps called "run_isolated".
        name=name or test,
        revision=revision,
        webkit_revision=webkit_revision,
        **runtest_kwargs)

  def run_telemetry_test(self, isolate_name, test, revision, webkit_revision,
                         **runtest_kwargs):
    """Runs a Telemetry test which has previously isolated to the server.

    Uses runtest_args_list, above, and delegates to
    api.chromium.run_telemetry_test.
    """
    self.m.chromium.run_telemetry_test(
        self._run_isolated_path,
        test,
        # When running the Telemetry test via an isolate we need to tell
        # run_isolated.py the hash and isolate server first, and then give
        # the isolate the test name and other arguments separately.
        prefix_args=self.runtest_args_list(isolate_name),
        revision=revision,
        webkit_revision=webkit_revision,
        **runtest_kwargs)

  def remove_build_metadata(self):
    """Removes the build metadata embedded in the build artifacts."""
    args = [
        '--build-dir', self.m.chromium.output_dir,
        '--src-dir', self.m.path['checkout']
    ]
    # Turn the failures during this step into warnings, it's a best effort step
    # that shouldn't break the build for now.
    try:
      self.m.python('remove_build_metadata',
                    self.resource('remove_build_metadata.py'),
                    args=args,
                    cwd=self.m.path['slave_build'])
    except self.m.step.StepFailure:
      step_result = self.m.step.active_result
      step_result.presentation.status = self.m.step.WARNING

  def compare_build_artifacts(self, first_dir, second_dir):
    """Compare the artifacts from 2 builds."""
    args = [
        '--first-build-dir', first_dir,
        '--second-build-dir', second_dir,
        '--target-platform', self.m.chromium.c.TARGET_PLATFORM
    ]
    self.m.python('compare_build_artifacts',
                  self.resource('compare_build_artifacts.py'),
                  args=args,
                  cwd=self.m.path['slave_build'])
