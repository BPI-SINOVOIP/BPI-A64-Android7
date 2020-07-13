# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Set of utilities to add commands to a buildbot factory.

This is based on commands.py and adds chromium-specific commands."""

import logging
import os
import re

from buildbot.process.properties import WithProperties
from buildbot.steps import shell
from buildbot.steps import trigger
from buildbot.steps.transfer import FileUpload

import config
from master import chromium_step
from master.factory import commands

from master.log_parser import archive_command
from master.log_parser import retcode_command
from master.log_parser import webkit_test_command


class ChromiumCommands(commands.FactoryCommands):
  """Encapsulates methods to add chromium commands to a buildbot factory."""

  def __init__(self, factory=None, target=None, build_dir=None,
               target_platform=None, target_os=None):

    commands.FactoryCommands.__init__(self, factory, target, build_dir,
                                      target_platform)

    self._target_os = target_os

    # Where the chromium slave scripts are.
    self._chromium_script_dir = self.PathJoin(self._script_dir, 'chromium')
    self._private_script_dir = self.PathJoin(self._script_dir, '..', '..', '..',
                                             'build_internal', 'scripts',
                                             'slave')
    self._bb_dir = self.PathJoin('src', 'build', 'android', 'buildbot')

    # Create smaller name for the functions and vars to simplify the code below.
    J = self.PathJoin
    s_dir = self._chromium_script_dir
    p_dir = self._private_script_dir

    self._process_dumps_tool = self.PathJoin(self._script_dir,
                                             'process_dumps.py')
    gsutil = 'gsutil'
    if self._target_platform and self._target_platform.startswith('win'):
      gsutil = 'gsutil.bat'
    self._gsutil = self.PathJoin(self._script_dir, gsutil)

    # Scripts in the chromium scripts dir.
    self._process_coverage_tool = J(s_dir, 'process_coverage.py')
    self._layout_archive_tool = J(s_dir, 'archive_layout_test_results.py')
    self._crash_handler_tool = J(s_dir, 'run_crash_handler.py')
    self._upload_parity_tool = J(s_dir, 'upload_parity_data.py')
    self._target_tests_tool = J(s_dir, 'target-tests.py')
    self._layout_test_tool = J(s_dir, 'layout_test_wrapper.py')
    self._lint_test_files_tool = J(s_dir, 'lint_test_files_wrapper.py')
    self._test_webkitpy_tool = J(s_dir, 'test_webkitpy_wrapper.py')
    self._archive_coverage = J(s_dir, 'archive_coverage.py')
    self._cf_archive_tool = J(s_dir, 'cf_archive_build.py')
    self._archive_tool = J(s_dir, 'archive_build.py')
    self._sizes_tool = J(s_dir, 'sizes.py')
    self._windows_syzyasan_tool = J(s_dir, 'win_apply_syzyasan.py')
    self._dynamorio_coverage_tool = J(s_dir, 'dynamorio_coverage.py')
    self._checkbins_tool = J(s_dir, 'checkbins_wrapper.py')
    self._mini_installer_tests_tool = J(s_dir, 'test_mini_installer_wrapper.py')
    self._device_status_check = J(self._bb_dir, 'bb_device_status_check.py')

    # Scripts in the private dir.
    self._download_and_extract_official_tool = self.PathJoin(
        p_dir, 'get_official_build.py')

    # These scripts should be move to the script dir.
    self._check_deps_tool = J('src', 'buildtools', 'checkdeps', 'checkdeps.py')
    self._check_perms_tool = J('src', 'tools', 'checkperms', 'checkperms.py')
    self._check_licenses_tool = J('src', 'tools', 'checklicenses',
                                  'checklicenses.py')
    self._posix_memory_tests_runner = J('src', 'tools', 'valgrind',
                                        'chrome_tests.sh')
    self._win_memory_tests_runner = J('src', 'tools', 'valgrind',
                                      'chrome_tests.bat')
    self._nacl_integration_tester_tool = J(
        'src', 'chrome', 'test', 'nacl_test_injection',
        'buildbot_nacl_integration.py')
    # chrome_staging directory, relative to the build directory.
    self._staging_dir = self.PathJoin('..', 'chrome_staging')

    self._telemetry_tool = self.PathJoin(self._script_dir, 'telemetry.py')
    self._telemetry_unit_tests = J('src', 'tools', 'telemetry', 'run_tests')
    self._telemetry_perf_unit_tests = J('src', 'tools', 'perf', 'run_tests')

  def AddArchiveStep(self, data_description, base_url, link_text, command,
                     more_link_url=None, more_link_text=None,
                     index_suffix='', include_last_change=True):
    step_name = ('archive_%s' % data_description).replace(' ', '_')
    self._factory.addStep(archive_command.ArchiveCommand,
                          name=step_name,
                          timeout=600,
                          description='archiving %s' % data_description,
                          descriptionDone='archived %s' % data_description,
                          base_url=base_url,
                          link_text=link_text,
                          more_link_url=more_link_url,
                          more_link_text=more_link_text,
                          command=command,
                          index_suffix=index_suffix,
                          include_last_change=include_last_change)

  # TODO(stip): not sure if this is relevant for new perf dashboard.
  def AddUploadPerfExpectations(self, factory_properties=None):
    """Adds a step to the factory to upload perf_expectations.json to the
    master.
    """
    perf_id = factory_properties.get('perf_id')
    if not perf_id:
      logging.error('Error: cannot upload perf expectations: perf_id is unset')
      return
    slavesrc = 'src/tools/perf_expectations/perf_expectations.json'
    masterdest = ('../../scripts/master/log_parser/perf_expectations/%s.json' %
                  perf_id)

    self._factory.addStep(FileUpload(slavesrc=slavesrc,
                                     masterdest=masterdest))

  def AddWindowsSyzyASanStep(self):
    """Adds a step to run syzyASan over the output directory."""
    cmd = [self._python, self._windows_syzyasan_tool,
           '--target', self._target]
    self.AddTestStep(shell.ShellCommand, 'apply_syzyasan', cmd)

  def AddArchiveBuild(self, mode='dev', show_url=True, factory_properties=None):
    """Adds a step to the factory to archive a build."""

    extra_archive_paths = factory_properties.get('extra_archive_paths')
    use_build_number = factory_properties.get('use_build_number', False)
    build_name = factory_properties.get('build_name')

    if show_url:
      (url, index_suffix) = _GetSnapshotUrl(factory_properties)
      text = 'download'
    else:
      url = None
      index_suffix = None
      text = None

    cmd = [self._python, self._archive_tool,
           '--target', self._target,
           '--mode', mode]
    if extra_archive_paths:
      cmd.extend(['--extra-archive-paths', extra_archive_paths])
    if use_build_number:
      cmd.extend(['--build-number', WithProperties('%(buildnumber)s')])
    if build_name:
      cmd.extend(['--build-name', build_name])

    gclient_env = (factory_properties or {}).get('gclient_env', {})
    if 'target_arch=arm' in gclient_env.get('GYP_DEFINES', ''):
      cmd.extend(['--arch', 'arm'])

    cmd = self.AddBuildProperties(cmd)
    cmd = self.AddFactoryProperties(factory_properties, cmd)

    self.AddArchiveStep(data_description='build', base_url=url, link_text=text,
                        command=cmd, index_suffix=index_suffix)

  def AddCFArchiveBuild(self, factory_properties=None):
    """Adds a step to the factory to archive a ClusterFuzz build."""

    cmd = [self._python, self._cf_archive_tool,
           '--target', self._target]

    cmd = self.AddBuildProperties(cmd)
    cmd = self.AddFactoryProperties(factory_properties, cmd)

    self.AddTestStep(retcode_command.ReturnCodeCommand,
                     'ClusterFuzz Archive', cmd)

  def GetAnnotatedPerfCmd(self, gtest_filter, log_type, test_name,
                          cmd_name, tool_opts=None,
                          options=None, factory_properties=None,
                          py_script=False, dashboard_url=None):
    """Return a runtest command suitable for most perf test steps."""

    dashboard_url = dashboard_url or config.Master.dashboard_upload_url

    tool_options = ['--annotate=' + log_type]
    tool_options.extend(tool_opts or [])
    tool_options.append('--results-url=%s' % dashboard_url)

    arg_list = options or []
    if gtest_filter:
      arg_list += ['--gtest_filter=' + gtest_filter]

    factory_properties = factory_properties or {}
    factory_properties['test_name'] = test_name

    perf_id = factory_properties.get('perf_id')
    show_results = factory_properties.get('show_perf_results')

    perf_name = self._PerfStepMappings(show_results,
                                       perf_id)
    factory_properties['perf_name'] = perf_name

    if py_script:
      return self.GetPythonTestCommand(cmd_name, wrapper_args=tool_options,
                                       arg_list=arg_list,
                                       factory_properties=factory_properties)
    else:
      arg_list.extend([
          # Prevents breakages in perf tests, but we shouldn't have to set this.
          # TODO(phajdan.jr): Do not set this.
          '--single-process-tests',
          ])

      return self.GetTestCommand(cmd_name, wrapper_args=tool_options,
                                 arg_list=arg_list,
                                 factory_properties=factory_properties)

  def AddAnnotatedPerfStep(self, test_name, gtest_filter, log_type,
                           factory_properties, cmd_name,
                           tool_opts=None, cmd_options=None, step_name=None,
                           timeout=1200, py_script=False, dashboard_url=None,
                           addmethod=None, alwaysRun=False):

    """Add an annotated perf step to the builder.

    Args:
      test_name: name of the test given to runtest.py. If step_name is not
        provided, a standard transform will be applied and the step on the
        waterfall will be test_name_test.

      gtest_filter: most steps use --gtest_filter to filter their output.

      log_type: one of the log parsers in runtest.py --annotate=list, such
        as 'graphing' or 'framerate'.

      cmd_name: command to run.

      tool_opts: additional options for runtest.py.

      cmd_options: additional options for the test run under runtest.py.

      step_name: the step name for the builder/waterfall.

      factory_properties: additional properties from the factory.
    """

    step_name = step_name or test_name.replace('-', '_') + '_test'
    factory_properties = factory_properties.copy()
    factory_properties['step_name'] = factory_properties.get('step_name',
                                                             step_name)
    addmethod = addmethod or self.AddTestStep

    cmd = self.GetAnnotatedPerfCmd(gtest_filter, log_type, test_name,
                                   cmd_name=cmd_name, options=cmd_options,
                                   tool_opts=tool_opts,
                                   factory_properties=factory_properties,
                                   py_script=py_script,
                                   dashboard_url=dashboard_url)

    addmethod(chromium_step.AnnotatedCommand, step_name, cmd,
              do_step_if=self.TestStepFilter, target=self._target,
              factory_properties=factory_properties, timeout=timeout,
              alwaysRun=alwaysRun)

  def AddBuildrunnerAnnotatedPerfStep(self, *args, **kwargs):
    """Add annotated step to be run by buildrunner."""
    kwargs.setdefault('addmethod', self.AddBuildrunnerTestStep)
    self.AddAnnotatedPerfStep(*args, **kwargs)

  def AddCheckDepsStep(self):
    cmd = [self._python, self._check_deps_tool,
           '--root', self._repository_root]
    self.AddTestStep(shell.ShellCommand, 'check_deps', cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerCheckDepsStep(self):
    cmd = [self._python, self._check_deps_tool,
           '--root', self._repository_root]
    self.AddBuildrunnerTestStep(shell.ShellCommand, 'check_deps', cmd,
                                do_step_if=self.TestStepFilter)

  def AddCheckBinsStep(self):
    cmd = [self._python, self._checkbins_tool, '--target', self._target]
    self.AddTestStep(shell.ShellCommand, 'check_bins', cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerCheckBinsStep(self):
    cmd = [self._python, self._checkbins_tool, '--target', self._target]
    self.AddBuildrunnerTestStep(shell.ShellCommand, 'check_bins', cmd,
                                do_step_if=self.TestStepFilter)

  def AddCheckPermsStep(self):
    cmd = [self._python, self._check_perms_tool,
           '--root', self._repository_root]
    self.AddTestStep(shell.ShellCommand, 'check_perms', cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerCheckPermsStep(self):
    cmd = [self._python, self._check_perms_tool,
           '--root', self._repository_root]
    self.AddBuildrunnerTestStep(shell.ShellCommand, 'check_perms', cmd,
                                do_step_if=self.TestStepFilter)

  def AddCheckLicensesStep(self, factory_properties):
    cmd = [self._python, self._check_licenses_tool,
           '--root', self._repository_root]
    self.AddTestStep(shell.ShellCommand, 'check_licenses', cmd,
                     do_step_if=self.GetTestStepFilter(factory_properties))

  def AddBuildrunnerCheckLicensesStep(self, factory_properties):
    cmd = [self._python, self._check_licenses_tool,
           '--root', self._repository_root]
    self.AddBuildrunnerTestStep(shell.ShellCommand, 'check_licenses', cmd,
        do_step_if=self.GetTestStepFilter(factory_properties))

  def AddMachPortsTests(self, factory_properties=None):
    self.AddAnnotatedPerfStep(
        'mach_ports', 'MachPortsTest.*', 'graphing',
        cmd_name='performance_browser_tests',
        cmd_options=['--test-launcher-print-test-stdio=always'],
        step_name='mach_ports',
        factory_properties=factory_properties)

  def AddCCPerfTests(self, factory_properties=None):
    self.AddAnnotatedPerfStep('cc_perftests', None, 'graphing',
                              cmd_name='cc_perftests',
                              step_name='cc_perftests',
                              factory_properties=factory_properties)

  def AddMediaPerfTests(self, factory_properties=None):
    self.AddAnnotatedPerfStep('media_perftests', None, 'graphing',
                              cmd_name='media_perftests',
                              step_name='media_perftests',
                              factory_properties=factory_properties)

  def AddLoadLibraryPerfTests(self, factory_properties=None):
    self.AddAnnotatedPerfStep('load_library_perf_tests', None, 'graphing',
                              cmd_name='load_library_perf_tests',
                              step_name='load_library_perf_tests',
                              factory_properties=factory_properties)

  def AddSizesTests(self, factory_properties=None):
    factory_properties = factory_properties or {}

    # For Android, platform is hardcoded as target_platform is set to linux2.
    # By default, the sizes.py script looks at sys.platform to identify
    # the platform (which is also linux2).
    args = ['--target', self._target]

    if self._target_os == 'android':
      args.extend(['--platform', 'android'])

    tool_opts = [
        '--revision', WithProperties('%(got_revision)s'),
        '--webkit-revision', WithProperties('%(got_webkit_revision:-)s')]

    self.AddAnnotatedPerfStep(
        'sizes', None, 'graphing', step_name='sizes', tool_opts=tool_opts,
        cmd_name=self._sizes_tool, cmd_options=args,
        py_script=True, factory_properties=factory_properties)

  def AddBuildrunnerSizesTests(self, factory_properties=None):
    factory_properties = factory_properties or {}

    # For Android, platform is hardcoded as target_platform is set to linux2.
    # By default, the sizes.py script looks at sys.platform to identify
    # the platform (which is also linux2).
    args = ['--target', self._target]

    if self._target_os == 'android':
      args.extend(['--platform', 'android'])

    tool_opts = [
        '--revision', WithProperties('%(got_revision)s'),
        '--webkit-revision', WithProperties('%(got_webkit_revision:-)s')]

    self.AddBuildrunnerAnnotatedPerfStep(
        'sizes', None, 'graphing', step_name='sizes', tool_opts=tool_opts,
        cmd_name=self._sizes_tool, cmd_options=args,
        py_script=True, factory_properties=factory_properties)

  def AddTabCapturePerformanceTests(self, factory_properties=None):
    options = ['--enable-gpu',
               '--test-launcher-jobs=1',
               '--test-launcher-print-test-stdio=always']
    tool_options = ['--no-xvfb']

    self.AddAnnotatedPerfStep(
        'tab_capture_performance',
        'TabCapturePerformanceTest*:CastV2PerformanceTest*',
        'graphing',
        cmd_name='performance_browser_tests',
        step_name='tab_capture_performance_tests',
        cmd_options=options,
        tool_opts=tool_options,
        factory_properties=factory_properties)

  def AddTelemetryUnitTests(self):
    step_name = 'telemetry_unittests'
    if self._target_os == 'android':
      args = ['--browser=android-content-shell']
    else:
      args = ['--browser=%s' % self._target.lower()]
    cmd = self.GetPythonTestCommand(self._telemetry_unit_tests,
                                    arg_list=args,
                                    wrapper_args=['--annotate=gtest',
                                                  '--test-type=%s' % step_name])

    self.AddTestStep(chromium_step.AnnotatedCommand, step_name, cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerTelemetryUnitTests(self):
    step_name = 'telemetry_unittests'
    if self._target_os == 'android':
      args = ['--browser=android-content-shell']
    else:
      args = ['--browser=%s' % self._target.lower()]
    cmd = self.GetPythonTestCommand(self._telemetry_unit_tests,
                                    arg_list=args,
                                    wrapper_args=['--annotate=gtest',
                                                  '--test-type=%s' % step_name])

    self.AddBuildrunnerTestStep(chromium_step.AnnotatedCommand, step_name, cmd,
                                do_step_if=self.TestStepFilter)

  def AddTelemetryPerfUnitTests(self):
    step_name = 'telemetry_perf_unittests'
    if self._target_os == 'android':
      args = ['--browser=android-content-shell']
    else:
      args = ['--browser=%s' % self._target.lower()]
    cmd = self.GetPythonTestCommand(self._telemetry_perf_unit_tests,
                                    arg_list=args,
                                    wrapper_args=['--annotate=gtest',
                                                  '--test-type=%s' % step_name])

    self.AddTestStep(chromium_step.AnnotatedCommand, step_name, cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerTelemetryPerfUnitTests(self):
    step_name = 'telemetry_perf_unittests'
    if self._target_os == 'android':
      args = ['--browser=android-content-shell']
    else:
      args = ['--browser=%s' % self._target.lower()]
    cmd = self.GetPythonTestCommand(self._telemetry_perf_unit_tests,
                                    arg_list=args,
                                    wrapper_args=['--annotate=gtest',
                                                  '--test-type=%s' % step_name])

    self.AddBuildrunnerTestStep(chromium_step.AnnotatedCommand, step_name, cmd,
                                do_step_if=self.TestStepFilter)

  def AddInstallerTests(self, factory_properties):
    if self._target_platform == 'win32':
      self.AddGTestTestStep('installer_util_unittests',
                            factory_properties)

  def AddBuildrunnerInstallerTests(self, factory_properties):
    if self._target_platform == 'win32':
      self.AddGTestTestStep('installer_util_unittests',
                            factory_properties)

  def AddChromeUnitTests(self, factory_properties):
    self.AddGTestTestStep('ipc_tests', factory_properties)
    self.AddGTestTestStep('sync_unit_tests', factory_properties)
    self.AddGTestTestStep('unit_tests', factory_properties)
    self.AddGTestTestStep('skia_unittests', factory_properties)
    self.AddGTestTestStep('sql_unittests', factory_properties)
    self.AddGTestTestStep('ui_base_unittests', factory_properties)
    self.AddGTestTestStep('content_unittests', factory_properties)
    if self._target_platform == 'win32':
      self.AddGTestTestStep('views_unittests', factory_properties)

  def AddBuildrunnerChromeUnitTests(self, factory_properties):
    self.AddBuildrunnerGTest('ipc_tests', factory_properties)
    self.AddBuildrunnerGTest('sync_unit_tests', factory_properties)
    self.AddBuildrunnerGTest('unit_tests', factory_properties)
    self.AddBuildrunnerGTest('skia_unittests', factory_properties)
    self.AddBuildrunnerGTest('sql_unittests', factory_properties)
    self.AddBuildrunnerGTest('ui_base_unittests', factory_properties)
    self.AddBuildrunnerGTest('content_unittests', factory_properties)
    if self._target_platform == 'win32':
      self.AddBuildrunnerGTest('views_unittests', factory_properties)

  def AddSyncIntegrationTests(self, factory_properties):
    options = ['--ui-test-action-max-timeout=120000']

    self.AddGTestTestStep('sync_integration_tests',
                          factory_properties, '',
                          options)

  def AddBuildrunnerSyncIntegrationTests(self, factory_properties):
    options = ['--ui-test-action-max-timeout=120000']

    self.AddBuildrunnerGTest('sync_integration_tests',
                             factory_properties, '',
                             options)

  def AddBrowserTests(self, factory_properties=None):
    description = ''
    options = ['--lib=browser_tests']

    total_shards = factory_properties.get('browser_total_shards')
    shard_index = factory_properties.get('browser_shard_index')
    options.append(factory_properties.get('browser_tests_filter', []))

    options = filter(None, options)

    self.AddGTestTestStep('browser_tests', factory_properties,
                          description, options,
                          total_shards=total_shards,
                          shard_index=shard_index)

  def AddPushCanaryTests(self, factory_properties=None):
    description = ''
    options = ['--lib=browser_tests']
    options.append('--run_manual')
    total_shards = factory_properties.get('browser_total_shards')
    shard_index = factory_properties.get('browser_shard_index')
    options.append('--gtest_filter=PushMessagingCanaryTest.*')
    options.append('--password-file-for-test=' +
                   '/usr/local/google/work/chromium/test_pass1.txt')
    options.append('--override-user-data-dir=' +
                   '/usr/local/google/work/chromium/foo')
    options.append('--ui-test-action-timeout=120000')
    options.append('--v=2')

    self.AddGTestTestStep('browser_tests', factory_properties,
                          description, options,
                          total_shards=total_shards,
                          shard_index=shard_index)

  def AddBuildrunnerBrowserTests(self, factory_properties):
    description = ''
    options = ['--lib=browser_tests']

    total_shards = factory_properties.get('browser_total_shards')
    shard_index = factory_properties.get('browser_shard_index')
    options.append(factory_properties.get('browser_tests_filter', []))
    options.extend(factory_properties.get('browser_tests_extra_options', []))

    options = filter(None, options)

    self.AddBuildrunnerGTest('browser_tests', factory_properties,
                             description, options,
                             total_shards=total_shards,
                             shard_index=shard_index)

  def AddMemoryTest(self, test_name, tool_name, timeout=1200,
                    factory_properties=None,
                    wrapper_args=None, addmethod=None):
    factory_properties = factory_properties or {}
    if not wrapper_args:
      wrapper_args = []
    wrapper_args.extend([
        '--annotate=gtest',
        '--test-type', 'memory test: %s' % test_name,
        '--pass-build-dir',
        '--pass-target',
    ])
    command_class = chromium_step.AnnotatedCommand
    addmethod = addmethod or self.AddTestStep

    matched = re.search(r'_([0-9]*)_of_([0-9]*)$', test_name)
    if matched:
      test_name = test_name[0:matched.start()]
      shard = int(matched.group(1))
      numshards = int(matched.group(2))
      wrapper_args.extend(['--shard-index', str(shard),
                           '--total-shards', str(numshards)])

    # Memory tests runner script path is relative to build dir.
    if self._target_platform != 'win32':
      runner = os.path.join('..', '..', '..', self._posix_memory_tests_runner)
    else:
      runner = os.path.join('..', '..', '..', self._win_memory_tests_runner)

    cmd = self.GetShellTestCommand(runner, arg_list=[
        '--test', test_name,
        '--tool', tool_name],
        wrapper_args=wrapper_args,
        factory_properties=factory_properties)

    test_name = 'memory test: %s' % test_name
    addmethod(command_class, test_name, cmd,
              timeout=timeout,
              do_step_if=self.TestStepFilter)

  def AddBuildrunnerMemoryTest(self, *args, **kwargs):
    """Add a memory test using buildrunner."""
    kwargs.setdefault('addmethod', self.AddBuildrunnerTestStep)
    self.AddMemoryTest(*args, **kwargs)

  def _AddBasicPythonTest(self, test_name, script, args=None, timeout=1200):
    args = args or []
    J = self.PathJoin
    if self._target_platform == 'win32':
      py26 = J('src', 'third_party', 'python_26', 'python_slave.exe')
      test_cmd = ['cmd', '/C'] + [py26, script] + args
    elif self._target_platform == 'darwin':
      test_cmd = ['python2.6', script] + args
    elif self._target_platform == 'linux2':
      # Run thru runtest.py on linux to launch virtual x server
      test_cmd = self.GetTestCommand('/usr/local/bin/python2.6',
                                     [script] + args)

    self.AddTestStep(retcode_command.ReturnCodeCommand,
                     test_name,
                     test_cmd,
                     timeout=timeout,
                     do_step_if=self.TestStepFilter)

  def AddChromeDriverTest(self, timeout=1200):
    J = self.PathJoin
    script = J('src', 'chrome', 'test', 'webdriver', 'test',
               'run_chromedriver_tests.py')
    self._AddBasicPythonTest('chromedriver_tests', script, timeout=timeout)

  def AddWebDriverTest(self, timeout=1200):
    J = self.PathJoin
    script = J('src', 'chrome', 'test', 'webdriver', 'test',
               'run_webdriver_tests.py')
    self._AddBasicPythonTest('webdriver_tests', script, timeout=timeout)

  def AddDeviceStatus(self, factory_properties=None):
    """Reports the status of the bot devices."""
    factory_properties = factory_properties or {}

    self.AddBuildrunnerAnnotatedPerfStep(
      'device_status', None, 'graphing',
      cmd_name=self._device_status_check,
      cmd_options=['--device-status-dashboard'], step_name='device_status',
      py_script=True, factory_properties=factory_properties, alwaysRun=True)

  def AddTelemetryTest(self, test_name, step_name=None,
                       factory_properties=None, timeout=1200,
                       tool_options=None, dashboard_url=None):
    """Adds a Telemetry performance test.

    Args:
      test_name: The name of the benchmark module to run.
      step_name: The name used to build the step's logfile name and descriptions
          in the waterfall display. Defaults to |test_name|.
      factory_properties: A dictionary of factory property values.
    """
    step_name = step_name or test_name

    factory_properties = (factory_properties or {}).copy()
    factory_properties['test_name'] = test_name
    factory_properties['target'] = self._target
    factory_properties['target_os'] = self._target_os
    factory_properties['target_platform'] = self._target_platform
    factory_properties['step_name'] = factory_properties.get('step_name',
                                                             step_name)

    cmd_options = self.AddFactoryProperties(factory_properties)

    log_type = 'graphing'
    if test_name.split('.')[0] == 'page_cycler':
      log_type = 'pagecycler'

    self.AddAnnotatedPerfStep(step_name, None, log_type, factory_properties,
                              cmd_name=self._telemetry_tool,
                              cmd_options=cmd_options,
                              step_name=step_name, timeout=timeout,
                              tool_opts=tool_options, py_script=True,
                              dashboard_url=dashboard_url)

  def AddBisectTest(self):
    """Adds a step to the factory to run a bisection on a range of revisions
    to investigate performance regressions."""

    # Need to run this in advance to create the depot and sync
    # the appropriate directories so that apache will launch correctly.
    cmd_name = self.PathJoin('src', 'tools',
                             'prepare-bisect-perf-regression.py')
    cmd = [self._python, cmd_name, '-w', '.']
    self.AddTestStep(chromium_step.AnnotatedCommand, 'Preparing for Bisection',
                     cmd, timeout=60*60, max_time=4*60*60)

    cmd_name = self.PathJoin('src', 'tools', 'run-bisect-perf-regression.py')
    cmd_args = ['-w', '.', '-p', self.PathJoin('..', '..', '..', 'goma')]
    cmd_args = self.AddBuildProperties(cmd_args)
    cmd = self.GetPythonTestCommand(cmd_name, arg_list=cmd_args)
    self.AddTestStep(chromium_step.AnnotatedCommand, 'Running Bisection',
        cmd, timeout=60*60, max_time=24*60*60)

  def AddWebkitLint(self, factory_properties=None):
    """Adds a step to the factory to lint the test_expectations.txt file."""
    cmd = [self._python, self._lint_test_files_tool,
           '--target', self._target]
    self.AddTestStep(shell.ShellCommand,
                     test_name='webkit_lint',
                     test_command=cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerWebkitLint(self, factory_properties=None):
    """Adds a step to the factory to lint the test_expectations.txt file."""
    cmd = [self._python, self._lint_test_files_tool,
           '--target', self._target]
    self.AddBuildrunnerTestStep(shell.ShellCommand,
                                test_name='webkit_lint',
                                test_command=cmd,
                                do_step_if=self.TestStepFilter)

  def AddWebkitPythonTests(self, factory_properties=None):
    """Adds a step to the factory to run test-webkitpy."""
    cmd = [self._python, self._test_webkitpy_tool,
           '--target', self._target]
    self.AddTestStep(shell.ShellCommand,
                     test_name='webkit_python_tests',
                     test_command=cmd,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerWebkitPythonTests(self, factory_properties=None):
    """Adds a step to the factory to run test-webkitpy."""
    cmd = [self._python, self._test_webkitpy_tool,
           '--target', self._target]
    self.AddBuildrunnerTestStep(shell.ShellCommand,
                                test_name='webkit_python_tests',
                                test_command=cmd,
                                do_step_if=self.TestStepFilter)

  def AddWebkitTests(self, factory_properties=None):
    """Adds a step to the factory to run the WebKit layout tests.

    Args:
      test_timeout: buildbot timeout for the test step
      archive_timeout: buildbot timeout for archiving the test results and
          crashes, if requested
      archive_results: whether to archive the test results
      archive_crashes: whether to archive crash reports resulting from the
          tests
      test_results_server: If specified, upload results json files to test
          results server
      driver_name: If specified, alternate layout test driver to use.
      additional_drt_flag: If specified, additional flag to pass to DRT.
      webkit_test_options: A list of additional options passed to
          run-webkit-tests. The list [o1, o2, ...] will be passed as a
          space-separated string 'o1 o2 ...'.
      layout_tests: List of layout tests to run.
    """
    factory_properties = factory_properties or {}
    archive_results = factory_properties.get('archive_webkit_results')
    layout_part = factory_properties.get('layout_part')
    test_results_server = factory_properties.get('test_results_server')
    enable_hardware_gpu = factory_properties.get('enable_hardware_gpu')
    layout_tests = factory_properties.get('layout_tests')
    time_out_ms = factory_properties.get('time_out_ms')
    driver_name = factory_properties.get('driver_name')
    additional_drt_flag = factory_properties.get('additional_drt_flag')
    webkit_test_options = factory_properties.get('webkit_test_options')

    builder_name = '%(buildername)s'
    result_str = 'results'
    test_name = 'webkit_tests'

    webkit_result_dir = '/'.join(['..', '..', 'layout-test-results'])

    cmd_args = ['--target', self._target,
                '-o', webkit_result_dir,
                '--build-number', WithProperties('%(buildnumber)s'),
                '--builder-name', WithProperties(builder_name)]

    for comps in factory_properties.get('additional_expectations', []):
      cmd_args.append('--additional-expectations')
      cmd_args.append(self.PathJoin('src', *comps))

    if layout_part:
      cmd_args.extend(['--run-part', layout_part])

    if test_results_server:
      cmd_args.extend(['--test-results-server', test_results_server])

    if time_out_ms:
      cmd_args.extend(['--time-out-ms', time_out_ms])

    if driver_name:
      cmd_args.extend(['--driver-name', driver_name])

    if additional_drt_flag:
      cmd_args.extend(['--additional-drt-flag', additional_drt_flag])

    additional_options = []
    if webkit_test_options:
      additional_options.extend(webkit_test_options)

    if enable_hardware_gpu:
      additional_options.append('--enable-hardware-gpu')

    if additional_options:
      cmd_args.append('--options=' + ' '.join(additional_options))

    # The list of tests is given as arguments.
    if layout_tests:
      cmd_args.extend(layout_tests)

    cmd = self.GetPythonTestCommand(self._layout_test_tool,
                                    cmd_args,
                                    wrapper_args=['--no-xvfb'],
                                    factory_properties=factory_properties)

    self.AddTestStep(webkit_test_command.WebKitCommand,
                     test_name=test_name,
                     test_command=cmd,
                     do_step_if=self.TestStepFilter)

    if archive_results:
      gs_bucket = 'chromium-layout-test-archives'
      factory_properties['gs_bucket'] = 'gs://' + gs_bucket
      cmd = [self._python, self._layout_archive_tool,
             '--results-dir', webkit_result_dir,
             '--build-number', WithProperties('%(buildnumber)s'),
             '--builder-name', WithProperties(builder_name)]

      cmd = self.AddBuildProperties(cmd)
      cmd = self.AddFactoryProperties(factory_properties, cmd)

      base_url = ("https://storage.googleapis.com/" +
                  gs_bucket + "/%(build_name)s")
      self.AddArchiveStep(
          data_description='webkit_tests ' + result_str,
          base_url=base_url,
          link_text='layout test ' + result_str,
          command=cmd,
          include_last_change=False,
          index_suffix='layout-test-results/results.html',
          more_link_text='(zip)',
          more_link_url='layout-test-results.zip')

  def AddRunCrashHandler(self, build_dir=None, target=None):
    target = target or self._target
    cmd = [self._python, self._crash_handler_tool, '--target', target]
    self.AddTestStep(shell.ShellCommand, 'start_crash_handler', cmd)

  def AddProcessDumps(self):
    cmd = [self._python, self._process_dumps_tool,
           '--target', self._target]
    self.AddTestStep(retcode_command.ReturnCodeCommand, 'process_dumps', cmd)

  def AddProcessCoverage(self, factory_properties=None):
    factory_properties = factory_properties or {}

    args = ['--target', self._target,
            '--build-id', WithProperties('%(got_revision)s')]
    if factory_properties.get('test_platform'):
      args += ['--platform', factory_properties.get('test_platform')]
    if factory_properties.get('upload-dir'):
      args += ['--upload-dir', factory_properties.get('upload-dir')]

    args = self.AddFactoryProperties(factory_properties, args)

    self.AddAnnotatedPerfStep('coverage', None, 'graphing',
                              step_name='process_coverage',
                              cmd_name=self._process_coverage_tool,
                              cmd_options=args, py_script=True,
                              factory_properties=factory_properties)

    # Map the perf ID to the coverage subdir, so we can link from the coverage
    # graph
    perf_mapping = self.PERF_TEST_MAPPINGS[self._target]
    perf_id = factory_properties.get('perf_id')
    perf_subdir = perf_mapping.get(perf_id)

    # 'total_coverage' is the default archive_folder for
    # archive_coverage.py script.
    url = _GetArchiveUrl('coverage', perf_subdir) + '/total_coverage'
    text = 'view coverage'
    cmd_archive = [self._python, self._archive_coverage,
                   '--target', self._target,
                   '--perf-subdir', perf_subdir]
    if factory_properties.get('use_build_number'):
      cmd_archive.extend(['--build-number', WithProperties('%(buildnumber)s')])

    self.AddArchiveStep(data_description='coverage', base_url=url,
                        link_text=text, command=cmd_archive)

  def AddDownloadAndExtractOfficialBuild(self, qa_identifier, branch=None):
    """Download and extract an official build.

    Assumes the zip file has e.g. "Google Chrome.app" in the top level
    directory of the zip file.
    """
    cmd = [self._python, self._download_and_extract_official_tool,
           '--identifier', qa_identifier,
           # TODO(jrg): for now we are triggered on a timer and always
           # use the latest build.  Instead we should trigger on the
           # presence of new build and pass that info down for a
           # --build N arg.
           '--latest']
    if branch:  # Fetch latest on given branch
      cmd += ['--branch', str(branch)]
    self.AddTestStep(commands.WaterfallLoggingShellCommand,
                     'Download and extract official build', cmd,
                     halt_on_failure=True)

  def AddNaClIntegrationTestStep(self, factory_properties, target=None,
                                 buildbot_preset=None, timeout=1200):
    target = target or self._target
    cmd = [self._python, self._nacl_integration_tester_tool,
           '--mode', target]
    if buildbot_preset is not None:
      cmd.extend(['--buildbot', buildbot_preset])

    self.AddTestStep(chromium_step.AnnotatedCommand, 'nacl_integration', cmd,
                     halt_on_failure=True, timeout=timeout,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerNaClIntegrationTestStep(self, factory_properties,
          target=None, buildbot_preset=None, timeout=1200):
    target = target or self._target
    cmd = [self._python, self._nacl_integration_tester_tool,
           '--mode', target]
    if buildbot_preset is not None:
      cmd.extend(['--buildbot', buildbot_preset])

    self.AddBuildrunnerTestStep(chromium_step.AnnotatedCommand,
                                'nacl_integration', cmd, halt_on_failure=True,
                                timeout=timeout, do_step_if=self.TestStepFilter)

  def AddAnnotatedSteps(self, factory_properties, timeout=1200):
    factory_properties = factory_properties or {}
    cmd = [self.PathJoin(self._chromium_script_dir,
                         factory_properties.get('annotated_script', ''))]

    if os.path.splitext(cmd[0])[1] == '.py':
      cmd.insert(0, self._python)
    cmd = self.AddBuildProperties(cmd)
    cmd = self.AddFactoryProperties(factory_properties, cmd)
    self._factory.addStep(chromium_step.AnnotatedCommand,
                          name='annotated_steps',
                          description='annotated_steps',
                          timeout=timeout,
                          haltOnFailure=True,
                          command=cmd)

  def AddAnnotationStep(self, name, cmd, factory_properties=None, env=None,
                        timeout=6000, maxTime=8*60*60, active_master=None):
    """Add an @@@BUILD_STEP step@@@ annotation script build command.

    This function allows the caller to specify the name of the
    annotation script.  In contrast, AddAnnotatedSteps() simply adds
    in a hard-coded annotation script that is not yet in the tree.
    TODO(jrg): resolve this inconsistency with the
    chrome-infrastrucure team; we shouldn't need two functions.
    """
    factory_properties = factory_properties or {}

    # Ensure cmd is a list, which is required for AddBuildProperties.
    if not isinstance(cmd, list):
      cmd = [cmd]

    if os.path.splitext(cmd[0])[1] == '.py':
      cmd.insert(0, self._python)
    cmd = self.AddBuildProperties(cmd)
    cmd = self.AddFactoryProperties(factory_properties, cmd)
    self._factory.addStep(chromium_step.AnnotatedCommand,
                          name=name,
                          description=name,
                          timeout=timeout,
                          haltOnFailure=True,
                          command=cmd,
                          env=env,
                          maxTime=maxTime,
                          factory_properties=factory_properties,
                          active_master=active_master)

  def AddMiniInstallerTestStep(self, factory_properties):
    cmd = [self._python, self._mini_installer_tests_tool,
           '--target', self._target]
    self.AddTestStep(chromium_step.AnnotatedCommand, 'test_installer', cmd,
                     halt_on_failure=True, timeout=600,
                     do_step_if=self.TestStepFilter)

  def AddBuildrunnerMiniInstallerTestStep(self, factory_properties,
          target=None, buildbot_preset=None, timeout=1200):
    target = target or self._target
    cmd = [self._python, self._mini_installer_tests_tool,
           '--target', target]
    if buildbot_preset is not None:
      cmd.extend(['--buildbot', buildbot_preset])

    self.AddBuildrunnerTestStep(chromium_step.AnnotatedCommand,
                                'test_installer', cmd,
                                halt_on_failure=True, timeout=timeout,
                                do_step_if=self.TestStepFilter)

  def AddTriggerCoverageTests(self, factory_properties):
    """Trigger coverage testers, wait for completion, then process coverage."""
    # Add trigger step.
    self._factory.addStep(trigger.Trigger(
        schedulerNames=[factory_properties.get('coverage_trigger')],
        updateSourceStamp=True,
        waitForFinish=True))

  def AddPreProcessCoverage(self, dynamorio_dir, factory_properties):
    """Prepare dynamorio before running coverage tests."""
    cmd = [self._python,
           self._dynamorio_coverage_tool,
           '--pre-process',
           '--dynamorio-dir', dynamorio_dir]
    cmd = self.AddFactoryProperties(factory_properties, cmd)
    self.AddTestStep(shell.ShellCommand,
                     'pre-process coverage', cmd,
                     timeout=900, halt_on_failure=True)

  def AddCreateCoverageFile(self, test, dynamorio_dir, factory_properties):
    # Create coverage file.
    cmd = [self._python,
           self._dynamorio_coverage_tool,
           '--post-process',
           '--build-id', WithProperties('%(got_revision)s'),
           '--platform', factory_properties['test_platform'],
           '--dynamorio-dir', dynamorio_dir,
           '--test-to-upload', test]
    cmd = self.AddFactoryProperties(factory_properties, cmd)
    self.AddTestStep(shell.ShellCommand,
                     'create_coverage_' + test, cmd,
                     timeout=900, halt_on_failure=True)

  def AddCoverageTests(self, factory_properties):
    """Add tests to run with dynamorio code coverage tool."""
    factory_properties['coverage_gtest_exclusions'] = True
    # TODO(thakis): Don't look at _build_dir here.
    dynamorio_dir = self.PathJoin(self._build_dir, 'dynamorio')
    ddrun_bin = self.PathJoin(dynamorio_dir, 'bin32',
                              self.GetExecutableName('drrun'))
    ddrun_cmd = [ddrun_bin, '-t', 'bbcov', '--']
    # Run browser tests with dynamorio environment vars.
    tests = factory_properties['tests']
    if 'browser_tests' in tests:
      browser_tests_prop = factory_properties.copy()
      browser_tests_prop['testing_env'] = {
          'BROWSER_WRAPPER': ' '.join(ddrun_cmd)}
      arg_list = ['--lib=browser_tests']
      arg_list += ['--ui-test-action-timeout=1200000',
                   '--ui-test-action-max-timeout=2400000',
                   '--ui-test-terminate-timeout=1200000']
      # Run single thread.
      arg_list += ['--jobs=1']
      arg_list = filter(None, arg_list)
      total_shards = factory_properties.get('browser_total_shards')
      shard_index = factory_properties.get('browser_shard_index')
      self.AddPreProcessCoverage(dynamorio_dir, browser_tests_prop)
      self.AddGTestTestStep('browser_tests',
                            browser_tests_prop,
                            description='',
                            arg_list=arg_list,
                            total_shards=total_shards,
                            shard_index=shard_index,
                            timeout=3*10*60,
                            max_time=24*60*60)
      self.AddCreateCoverageFile('browser_tests',
                                 dynamorio_dir,
                                 factory_properties)

    # Add all other tests without sharding.
    shard_index = factory_properties.get('browser_shard_index')
    if not shard_index or shard_index == 1:
      # TODO(thakis): Don't look at _build_dir here.
      test_path = self.PathJoin(self._build_dir, self._target)
      for test in tests:
        if test != 'browser_tests':
          cmd = ddrun_cmd + [self.PathJoin(test_path,
                             self.GetExecutableName(test))]
          self.AddPreProcessCoverage(dynamorio_dir, factory_properties)
          self.AddTestStep(shell.ShellCommand, test, cmd)
          self.AddCreateCoverageFile(test,
                                     dynamorio_dir,
                                     factory_properties)


def _GetArchiveUrl(archive_type, builder_name='%(build_name)s'):
  # The default builder name is dynamically filled in by
  # ArchiveCommand.createSummary.
  return '%s/%s/%s' % (config.Master.archive_url, archive_type, builder_name)


def _GetSnapshotUrl(factory_properties=None, builder_name='%(build_name)s'):
  if not factory_properties or 'gs_bucket' not in factory_properties:
    return (_GetArchiveUrl('snapshots', builder_name), None)
  gs_bucket = factory_properties['gs_bucket']
  gs_bucket = re.sub(r'^gs://', 'http://commondatastorage.googleapis.com/',
                     gs_bucket)
  return ('%s/index.html?prefix=%s' % (gs_bucket, builder_name), '')
