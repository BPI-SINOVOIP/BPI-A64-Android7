# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import copy

from recipe_engine import recipe_api


class iOSApi(recipe_api.RecipeApi):
  def __init__(self, *args, **kwargs):
    super(iOSApi, self).__init__(*args, **kwargs)
    self.__config = None

  def host_info(self):
    """Emits information about the current host and available tools."""
    step_result = self.m.step('host and tools info', [
      self.m.path['build'].join(
        'scripts',
        'slave',
        'ios',
        'host_info.py',
      ),
      '--json-file', self.m.json.output(),
    ], infra_step=True, step_test_data=self.test_api.host_info)

    if step_result.json.output:
      step_result.presentation.step_text = '<br />OS X %s, Xcode %s (%s)' % (
        step_result.json.output['Mac OS X Version'],
        step_result.json.output['Xcode Version'],
        step_result.json.output['Xcode Build Version'],
      )
    return step_result

  def checkout(self, **kwargs):
    """Checks out Chromium."""
    kwargs.setdefault('force', True)
    self.m.gclient.set_config('ios')
    update_step = self.m.bot_update.ensure_checkout(**kwargs)
    self.m.path['checkout'] = self.m.path['slave_build'].join('src')
    return update_step

  @property
  def compiler(self):
    assert self.__config is not None
    return self.__config['compiler']

  @property
  def configuration(self):
    assert self.__config is not None
    return self.__config['configuration']

  @property
  def platform(self):
    assert self.__config is not None
    if self.__config['sdk'].startswith('iphoneos'):
      return 'device'
    elif self.__config['sdk'].startswith('iphonesimulator'):
      return 'simulator'

  def read_build_config(
    self,
    master_name=None,
    build_config_dir=None,
    include_dir=None,
  ):
    """Reads the iOS build config for this bot.

    Args:
      master_name: Name of a master to read the build config from, or None
        to read from buildbot properties at run-time.
      build_config_dir: Directory to read the build config from, or None
        to read from the default directory.
      include_dir: Directory to read includes from, or None to read from
        the default directory.
    """
    build_config_dir = build_config_dir or self.m.path['checkout'].join(
      'ios',
      'build',
      'bots',
      master_name or self.m.properties['mastername'],
    )
    include_dir = include_dir or self.m.path['checkout'].join(
      'ios',
      'build',
      'bots',
      'tests',
    )

    self.__config = self.m.json.read(
      'read build config',
      build_config_dir.join('%s.json' % self.m.properties['buildername']),
      step_test_data=lambda: self.m.json.test_api.output(
        self._test_data['build_config']
      ),
    ).json.output

    # If this bot is triggered by another bot, then the build configuration
    # has to be read from the parent's build config. A triggered bot only
    # specifies the tests.
    parent = str(self.__config.get('triggered by', ''))

    if parent:
      parent_config = self.m.json.read(
        'read parent build config (%s)' % parent,
        build_config_dir.join('%s.json' % parent),
        step_test_data=lambda: self.m.json.test_api.output(
          self._test_data['parent_build_config'],
        ),
      ).json.output

      for key in (
        'xcode version',
        'GYP_DEFINES',
        'compiler',
        'configuration',
        'sdk',
      ):
        self.__config[key] = parent_config[key]

    # We set some default GYP_DEFINES so developers don't have to set them
    # manually on every bot. Add them in here.
    self.__config['GYP_DEFINES']['component'] = 'static_library'
    self.__config['GYP_DEFINES']['OS'] = 'ios'

    # In order to simplify the code that uses the values of self.__config, here
    # we default to empty values of their respective types, so in other places
    # we can iterate over them without having to check if they are in the dict
    # at all.
    self.__config['triggered bots'] = self.__config.get('triggered bots', {})
    self.__config['tests'] = self.__config.get('tests', [])
    self.__config['env'] = self.__config.get('env', {})

    # Elements of the "tests" list are dicts. There are two types of elements,
    # determined by the presence of one of these mutually exclusive keys:
    #   "app": This says to run a particular app.
    #   "include": This says to include a common set of tests from include_dir.
    # So now we go through the "tests" list replacing any "include" keys.
    # The value of an "include" key is the name of a set of tests to include,
    # which can be found as a .json file in include_dir. Read the contents
    # lazily as needed into includes.
    def read_include(includes):
      """Reads the contents of the given include.

      Args:
        include: Name of the include.
      """
      return self.m.json.read(
        'include %s' % include,
        include_dir.join(include),
        step_test_data=lambda: self.m.json.test_api.output({
          'tests': [
            {
              'app': 'fake included test 1',
            },
            {
              'app': 'fake included test 2',
            },
          ],
        }),
      ).json.output

    includes = {}
    expanded_tests_list = []

    # expanded_tests_list will be the list of test dicts, with
    # any "include" replaced with the tests from that include.
    for element in self.__config['tests']:
      if element.get('include'):
        # This is an include dict.
        include = str(element.pop('include'))

        # Lazily read the include if we haven't already.
        if include not in includes:
          includes[include] = read_include(include)

        # Now take each test dict from the include, update it with the
        # extra keys (e.g. device, OS), and append to the list of tests.
        for included_test in includes[include]['tests']:
          expanded_tests_list.append(copy.deepcopy(included_test))
          expanded_tests_list[-1].update(element)
      else:
        # This is a test dict.
        expanded_tests_list.append(element)

    self.__config['tests'] = expanded_tests_list

    self.m.step('finalize build config', [
      'echo',
      '-e',
      self.m.json.dumps(self.__config, indent=2),
    ])

    step_result = self.m.step(
      'find xcode', [
      self.m.path['build'].join(
        'scripts',
        'slave',
        'ios',
        'find_xcode.py',
      ),
      '--json-file', self.m.json.output(),
      '--version', self.__config['xcode version'],
    ], step_test_data=lambda: self.m.json.test_api.output({}))

    cfg = self.m.chromium.make_config()
    cfg.gyp_env.GYP_CROSSCOMPILE = 1
    cfg.gyp_env.GYP_DEFINES = copy.deepcopy(self.__config['GYP_DEFINES'])
    self.m.chromium.c = cfg

  def build(self, suffix=None):
    """Builds from this bot's build config."""
    assert self.__config is not None

    suffix = ' (%s)' % suffix if suffix else ''

    # Add the default GYP_DEFINES.
    gyp_defines = [
      '%s=%s' % (k, v) for k, v in self.__config['GYP_DEFINES'].iteritems()
    ]

    env = {
      'GYP_DEFINES': ' '.join(gyp_defines),
      'LANDMINES_VERBOSE': '1',
    }

    # Add extra env variables.
    env.update(self.__config['env'])

    if self.compiler == 'xcodebuild':
      env['GYP_GENERATORS'] = 'xcode'
      env['GYP_GENERATOR_FLAGS'] = 'xcode_project_version=3.2'
      cwd = self.m.path['checkout'].join('xcodebuild')
      cmd = [
        'xcodebuild',
        '-configuration', self.configuration,
        '-project', self.m.path['checkout'].join(
          'build',
          'all.xcodeproj',
        ),
        '-sdk', self.__config['sdk'],
      ]
    elif self.compiler == 'ninja':
      env['GYP_CROSSCOMPILE'] = '1'
      env['GYP_GENERATORS'] = 'ninja'
      cwd = self.m.path['checkout'].join(
        'out',
        '%s-%s' % (self.configuration, {
          'simulator': 'iphonesimulator',
          'device': 'iphoneos',
        }[self.platform])
      )
      cmd = ['ninja', '-C', cwd]

    step_result = self.m.gclient.runhooks(name='runhooks' + suffix, env=env)
    step_result.presentation.step_text = (
      '<br />GYP_DEFINES:<br />%s' % '<br />'.join(gyp_defines)
    )

    if (self.compiler == 'ninja' and
        self.m.tryserver.is_tryserver and
        'without patch' not in suffix):
      affected_files = self.m.tryserver.get_files_affected_by_patch()
      tests = [test['app'] for test in self.__config['tests']]

      requires_compile, test_targets, compile_targets = (
        self.m.chromium_tests.analyze(
          affected_files,
          tests,
          ['all'] + tests,  # https://crbug.com/463676.
          'trybot_analyze_config.json',
          additional_names=['chromium', 'ios'],
        )
      )

      test_targets = set(test_targets)

      for test in self.__config['tests']:
        if test['app'] not in test_targets:
          test['skip'] = True

      if requires_compile: # pragma: no cover
        cmd.extend(compile_targets)
      else:
        return

    self.m.step('compile' + suffix, cmd, cwd=cwd)

  def test(self, *args):
    """Runs tests as instructed by this bot's build config.

    Args:
      *args: Any additional arguments to pass to the test harness.
    """
    assert self.__config is not None
    test_failures = []
    infrastructure_failures = []

    for test in self.__config['tests']:
      cmd = [
        self.m.path['build'].join(
          'scripts',
          'slave',
          'ios',
          'run.py',
        ),
        '--app', self.m.path['slave_build'].join(
          self.most_recent_app_dir,
          '%s.app' % test['app'],
        ),
        '--json_file', self.m.json.output(),
      ]

      step_name = test['app']

      if self.platform == 'simulator':
        cmd.extend([
          '--iossim', self.m.path['slave_build'].join(self.most_recent_iossim),
          '--platform', test['device type'],
          '--version', test['os'],
        ])

        # Since we may be running simulator tests on multiple platforms,
        # include the platform and OS in the name of the step.
        step_name = '%s (%s iOS %s)' % (
          test['app'],
          test['device type'],
          test['os'],
        )

      cmd.extend(args)

      if test.get('skip'):
        step_result = self.m.step('[skipped] %s' % str(step_name), [])
        step_result.presentation.step_text = (
          'This test was skipped because it was not affected.'
        )
        continue

      try:
        step_result = self.m.step(
          str(step_name),
          cmd,
          step_test_data=self.test_api.test_results,
        )
      except self.m.step.StepFailure as f:
        step_result = f.result

        # The test scripts use a return code of 2 to indicate
        # an infrastructure failure.
        if step_result.retcode == 2:
          step_result.presentation.status = self.m.step.EXCEPTION
          infrastructure_failures.append(step_name)
        else:
          test_failures.append(step_name)

      if step_result.json.output:
        step_result.presentation.logs.update(
          step_result.json.output.get('logs', {})
        )
        step_result.presentation.links.update(
          step_result.json.output.get('links', {})
        )
        step_result.presentation.step_text = (
          step_result.json.output.get('step_text', '')
        )

    # Here we turn the build red if there were any test failures, or purple if
    # there were any infrastructure failures. If there were both, turn the build
    # red to call sheriff attention to the legitimate failures.
    if test_failures:
      raise self.m.step.StepFailure(
        'Failed %s.' % ', '.join(test_failures + infrastructure_failures)
      )
    elif infrastructure_failures:
      raise self.m.step.InfraFailure(
        'Failed %s.' % ', '.join(infrastructure_failures)
      )

  @property
  def most_recent_app_dir(self):
    """Returns the path to the directory of the most recently compiled apps."""
    build_dir = {
      'xcodebuild': 'xcodebuild',
      'ninja': 'out',
    }[self.compiler]

    platform = {
      'device': 'iphoneos',
      'simulator': 'iphonesimulator',
    }[self.platform]

    return self.m.path.join(
      'src',
      build_dir,
      '%s-%s' % (self.configuration, platform),
    )

  @property
  def most_recent_iossim(self):
    """Returns the path to the most recently compiled iossim."""
    build_dir = {
      'xcodebuild': self.m.path.join('xcodebuild', 'ninja-iossim'),
      'ninja': 'out',
    }[self.compiler]

    # If built with Xcode, the iossim path depends on whether the target is
    # Debug or Release, but doesn't depend on the platform.
    # i.e. iossim is located at one of:
    # xcodebuild/ninja-iossim/Debug/iossim
    # xcodebuild/ninja-iossim/Release/iossim
    # However if built with ninja, the iossim path does depend on the platform
    # as well.
    # i.e. iossim could be located at:
    # out/Debug-iphoneos/iossim
    # out/Debug-iphonesimulator/iossim
    # out/Release-iphoneos/iossim
    # out/Release-iphonesimulator/iossim

    platform = {
      'device': 'iphoneos',
      'simulator': 'iphonesimulator',
    }[self.platform]

    return {
      'xcodebuild': self.m.path.join(
        'src',
        build_dir,
        self.configuration,
        'iossim',
      ),
      'ninja': self.m.path.join(
        'src',
         build_dir,
         '%s-%s' % (self.configuration, platform),
         'iossim',
      ),
    }[self.compiler]
