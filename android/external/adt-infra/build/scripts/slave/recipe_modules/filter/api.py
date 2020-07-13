# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re

from recipe_engine import recipe_api

class FilterApi(recipe_api.RecipeApi):
  def __init__(self, **kwargs):
    super(FilterApi, self).__init__(**kwargs)
    self._result = False
    self._matches_exclusion = False
    self._matching_exes = []
    self._compile_targets = []
    self._paths = []

  def __is_path_in_exclusion_list(self, path, exclusions):
    """Returns true if |path| matches any of the regular expressions in
    |exclusions|."""
    for regex in exclusions:
      match = regex.match(path)
      if match and match.end() == len(path):
        return regex.pattern
    return False

  @property
  def result(self):
    """Returns the result from most recent call to
    does_patch_require_compile."""
    return self._result

  @property
  def matches_exclusion(self):
    """Returns true if the patch matches an exclusion, i.e. "analyze" should
    not be used and all compile targets and tests should be used."""
    return self._matches_exclusion

  @property
  def matching_exes(self):
    """Returns the set of exes passed to does_patch_require_compile() that
    are effected by the set of files that have changed."""
    return self._matching_exes

  @property
  def compile_targets(self):
    """Returns the set of targets that need to be compiled based on the set of
    files that have changed."""
    return self._compile_targets

  @property
  def paths(self):
    """Returns the paths that have changed in this patch."""
    return self._paths

  def _load_analyze_config(self, file_name):
    config_path = self.m.path.join('testing', 'buildbot', file_name)
    step_result = self.m.json.read(
      'read filter exclusion spec',
      self.m.path['checkout'].join(config_path),
      step_test_data=lambda: self.m.json.test_api.output({
          'base': {
            'exclusions': [],
          },
          'chromium': {
            'exclusions': [],
          },
          'ios': {
            'exclusions': [],
          },
        })
      )
    step_result.presentation.step_text = 'path: %r' % config_path
    return step_result.json.output

  def _load_exclusions(self, names, file_name):
    file_contents = self._load_analyze_config(file_name)
    exclusions = []
    for name in names:
      exclusions.extend(file_contents[name]['exclusions'])
    return exclusions

  def does_patch_require_compile(self,
                                 affected_files,
                                 exes=None,
                                 compile_targets=None,
                                 additional_names=None,
                                 config_file_name='trybot_analyze_config.json',
                                 use_mb=False,
                                 build_output_dir=None,
                                 cros_board=None,
                                 **kwargs):
    """Return true if the current patch requires a build (and exes to run).
    Return value can be accessed by call to result().

    Args:
      affected_files: list of files affected by the current patch; paths
                      should only use forward slashes ("/") on all platforms
      exes: the possible set of executables that are desired to run. When done
      matching_exes() returns the set of exes that are effected by the files
      that have changed.
      compile_targets: proposed set of targets to compile.
      additional_names: additional top level keys to look up exclusions in,
      see |config_file_name|.
      conconfig_file_name: the config file to look up exclusions in.
      Within the file we concatenate "base.exclusions" and
      "|additional_names|.exclusions" (if |additional_names| is not none) to get
      the full list of exclusions.
      the exclusions should be list of python regular expressions (as strings).
      If any of the files in the current patch match one of the values in
      |exclusions| True is returned (by way of result()).
      """
    names = ['base']
    if additional_names is not None:
      names.extend(additional_names)
    exclusions = self._load_exclusions(names, config_file_name)

    self._matching_exes = exes if exes is not None else []
    self._compile_targets = compile_targets if compile_targets is not None \
                                            else []

    self._paths = affected_files

    # Check the path of each file against the exclusion list. If found, no need
    # to check dependencies.
    exclusion_regexs = [re.compile(exclusion) for exclusion in exclusions]
    for path in self.paths:
      first_match = self.__is_path_in_exclusion_list(path, exclusion_regexs)
      if first_match:
        step_result = self.m.python.inline(
            'analyze',
            'import sys; sys.exit(0)',
            add_python_log=False)
        step_result.presentation.logs.setdefault('excluded_files', []).append(
            '%s (regex = \'%s\')' % (path, first_match))
        self._result = True
        self._matches_exclusion = True
        return

    analyze_input = {'files': self.paths, 'targets': self._matching_exes}

    test_output = {'status': 'No dependency',
                   'targets': [],
                   'build_targets': []}

    kwargs.setdefault('env', {})

    # If building for CrOS, execute through the "chrome_sdk" wrapper. This will
    # override GYP environment variables, so we'll refrain from defining them
    # to avoid confusing output.
    if cros_board:
      kwargs['wrapper'] = self.m.chromium.get_cros_chrome_sdk_wrapper()
    else:
      kwargs['env'].update(self.m.chromium.c.gyp_env.as_jsonish())

    if use_mb:
      if 'env' in kwargs:
        # Ensure that mb runs in a clean environment to avoid
        # picking up any GYP_DEFINES accidentally.
        del kwargs['env']
      step_result = self.m.python(
          'analyze',
          self.m.path['checkout'].join('tools', 'mb', 'mb.py'),
          args=['analyze',
                '-m',
                self.m.properties['mastername'],
                '-b',
                self.m.properties['buildername'],
                '-v',
                build_output_dir,
                self.m.json.input(analyze_input),
                self.m.json.output()],
          step_test_data=lambda: self.m.json.test_api.output(
            test_output),
          **kwargs)
    else:
      step_result = self.m.python(
          'analyze',
          self.m.path['checkout'].join('build', 'gyp_chromium'),
          args=['--analyzer',
                self.m.json.input(analyze_input),
                self.m.json.output()],
          step_test_data=lambda: self.m.json.test_api.output(
            test_output),
          **kwargs)

    if 'error' in step_result.json.output:
      self._result = True
      step_result.presentation.step_text = 'Error: ' + \
          step_result.json.output['error']
      raise self.m.step.StepFailure(
          'Error: ' + step_result.json.output['error'])
    elif 'invalid_targets' in step_result.json.output:
      self._result = True
      raise self.m.step.StepFailure('Error, following targets were not ' + \
          'found: ' + ', '.join(step_result.json.output['invalid_targets']))
    elif step_result.json.output['status'] == 'Found dependency':
      self._result = True
      self._matching_exes = step_result.json.output['targets']
      self._compile_targets = step_result.json.output['build_targets']
    elif step_result.json.output['status'] == 'Found dependency (all)':
      self._result = True
    else:
      step_result.presentation.step_text = 'No compile necessary'
