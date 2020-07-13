# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import contextlib
import datetime
import json
import os
import re
import urllib

from recipe_engine.types import freeze
from recipe_engine import recipe_api


def _TimestampToIsoFormat(timestamp):
  return datetime.datetime.utcfromtimestamp(timestamp).strftime('%Y%m%dT%H%M%S')


class AndroidApi(recipe_api.RecipeApi):
  def __init__(self, **kwargs):
    super(AndroidApi, self).__init__(**kwargs)
    self._devices = None
    self._file_changes_path = None

  def get_config_defaults(self):
    return {
      'REVISION': self.m.properties.get('revision', '')
    }

  def get_env(self):
    return self.m.chromium.get_env()

  @property
  def devices(self):
    assert self._devices is not None,\
        'devices is only available after device_status_check()'
    return self._devices

  @property
  def out_path(self):
    return self.m.path['checkout'].join('out')

  @property
  def coverage_dir(self):
    return self.out_path.join(self.c.BUILD_CONFIG, 'coverage')

  @property
  def file_changes_path(self):
    """Get or create the path to the file containing changes for this revision.

    This file will contain a dict mapping file paths to lists of changed lines
    for each file. This is used to generate incremental coverage reports.
    """
    if not self._file_changes_path:
      self._file_changes_path = (
          self.m.path.mkdtemp('coverage').join('file_changes.json'))
    return self._file_changes_path

  def configure_from_properties(self, config_name, **kwargs):
    self.set_config(config_name, **kwargs)
    self.m.chromium.set_config(config_name, optional=True, **kwargs)

  def make_zip_archive(self, step_name, archive_name, files=None,
                       preserve_paths=True, filters=None, **kwargs):
    """Creates and stores the archive file.

    Args:
      step_name: Name of the step.
      archive_name: Name of the archive file.
      files: If specified, only include files here instead of out/<target>.
      filters: List of globs to be included in the archive.
      preserve_paths: If True, files will be stored using the subdolders
        in the archive.
    """
    archive_args = ['--target', self.m.chromium.c.BUILD_CONFIG,
                    '--name', archive_name]

    # TODO(luqui): Clean up when these are covered by the external builders.
    if files:              # pragma: no cover
      archive_args.extend(['--files', ','.join(files)])
    if filters:
      archive_args.extend(['--filters', ','.join(filters)])
    if not preserve_paths: # pragma: no cover
      archive_args.append('--ignore-subfolder-names')

    self.m.python(
      step_name,
      str(self.m.path['build'].join(
          'scripts', 'slave', 'android', 'archive_build.py')),
      archive_args,
      infra_step=True,
      **kwargs
    )

  def init_and_sync(self, gclient_config='android_bare',
                    with_branch_heads=False):
    # TODO(sivachandra): Move the setting of the gclient spec below to an
    # internal config extension when they are supported by the recipe system.
    spec = self.m.gclient.make_config(gclient_config)
    spec.target_os = ['android']
    s = spec.solutions[0]
    s.name = self.c.deps_dir
    s.url = self.c.REPO_URL
    s.custom_deps = self.c.gclient_custom_deps or {}
    s.deps_file = self.c.deps_file
    s.custom_vars = self.c.gclient_custom_vars or {}
    s.managed = self.c.managed
    s.revision = self.c.revision
    spec.revisions = self.c.revisions

    self.m.gclient.break_locks()
    refs = self.m.properties.get('event.patchSet.ref')
    if refs:
      refs = [refs]
    result = self.m.bot_update.ensure_checkout(
        spec, refs=refs, with_branch_heads=with_branch_heads)
    if not result.json.output['did_run']:
      result = self.m.gclient.checkout(spec, with_branch_heads=with_branch_heads)

    # TODO(sivachandra): Manufacture gclient spec such that it contains "src"
    # solution + repo_name solution. Then checkout will be automatically
    # correctly set by gclient.checkout
    self.m.path['checkout'] = self.m.path['slave_build'].join('src')

    self.clean_local_files()

    return result

  def clean_local_files(self):
    target = self.c.BUILD_CONFIG
    debug_info_dumps = self.m.path['checkout'].join('out',
                                                    target,
                                                    'debug_info_dumps')
    test_logs = self.m.path['checkout'].join('out', target, 'test_logs')
    build_product = self.m.path['checkout'].join('out', 'build_product.zip')
    self.m.python.inline(
        'clean local files',
        """
          import shutil, sys, os
          shutil.rmtree(sys.argv[1], True)
          shutil.rmtree(sys.argv[2], True)
          try:
            os.remove(sys.argv[3])
          except OSError:
            pass
          for base, _dirs, files in os.walk(sys.argv[4]):
            for f in files:
              if f.endswith('.pyc'):
                os.remove(os.path.join(base, f))
        """,
        args=[debug_info_dumps, test_logs, build_product,
              self.m.path['checkout']],
        infra_step=True,
    )

  def run_tree_truth(self, additional_repos=None):
    # TODO(sivachandra): The downstream ToT builder will require
    # 'Show Revisions' step.
    repos = ['src']
    if additional_repos:
      repos.extend(additional_repos)
    if self.c.REPO_NAME not in repos and self.c.REPO_NAME:
      repos.append(self.c.REPO_NAME)
    # TODO(sivachandra): Disable subannottations after cleaning up
    # tree_truth.sh.
    self.m.step('tree truth steps',
                [self.m.path['checkout'].join('build', 'tree_truth.sh'),
                self.m.path['checkout']] + repos,
                allow_subannotations=False)

  def git_number(self, **kwargs):
    return self.m.step(
        'git_number',
        [self.m.path['depot_tools'].join('git_number.py')],
        stdout = self.m.raw_io.output(),
        step_test_data=(
          lambda:
            self.m.raw_io.test_api.stream_output('3000\n')
        ),
        cwd=self.m.path['checkout'],
        infra_step=True,
        **kwargs)

  def java_method_count(self, dexfile, name='java_method_count'):
    self.m.chromium.runtest(
        self.m.path['checkout'].join('build', 'android', 'method_count.py'),
        args=[dexfile],
        annotate='graphing',
        results_url='https://chromeperf.appspot.com',
        perf_id=self.m.properties['buildername'],
        perf_dashboard_id=name,
        test_type=name)

  def check_webview_licenses(self, name='check licenses'):
    self.m.python(
        name,
        self.m.path['checkout'].join('android_webview',
                                     'tools',
                                     'webview_licenses.py'),
        args=['scan'],
        cwd=self.m.path['checkout'])

  def upload_build(self, bucket, path):
    archive_name = 'build_product.zip'

    zipfile = self.m.path['checkout'].join('out', archive_name)

    self.make_zip_archive(
      'zip_build_product',
      archive_name,
      preserve_paths=True,
      cwd=self.m.path['checkout']
    )

    self.m.gsutil.upload(
        name='upload_build_product',
        source=zipfile,
        bucket=bucket,
        dest=path,
        version='4.7')

  def download_build(self, bucket, path, extract_path=None):
    zipfile = self.m.path['checkout'].join('out', 'build_product.zip')
    self.m.gsutil.download(
        name='download_build_product',
        bucket=bucket,
        source=path,
        dest=zipfile,
        version='4.7',
    )
    extract_path = extract_path or self.m.path['checkout']
    self.m.step(
      'unzip_build_product',
      ['unzip', '-o', zipfile],
      cwd=extract_path,
      infra_step=True,
    )

  def zip_and_upload_build(self, bucket):
    # TODO(luqui): Unify make_zip_archive and upload_build with this
    # (or at least make the difference clear).
    self.m.archive.zip_and_upload_build(
        'zip_build',
        target=self.m.chromium.c.BUILD_CONFIG,
        # We send None as the path so that zip_build.py gets it from factory
        # properties.
        build_url=None,
        src_dir=self.m.path['slave_build'].join('src'),
        exclude_files='lib.target,gen,android_webview,jingle_unittests')

  def spawn_logcat_monitor(self):
    self.m.step(
        'spawn_logcat_monitor',
        [self.m.path['build'].join('scripts', 'slave', 'daemonizer.py'),
         '--', self.c.cr_build_android.join('adb_logcat_monitor.py'),
         self.m.chromium.c.build_dir.join('logcat')],
        env=self.m.chromium.get_env(),
        infra_step=True)

  def spawn_device_temp_monitor(self):
    script = self.m.path['build'].join('scripts', 'slave', 'daemonizer.py')
    args = [
        '--action', 'restart',
        '--pid-file-path', '/tmp/device_monitor.pid',
        '--', self.resource('spawn_device_temp_monitor.py'),
        self.m.adb.adb_path(),
        json.dumps(self._devices),
        self.m.properties['mastername'],
        self.m.properties['buildername']
    ]
    self.m.python('spawn_device_temp_monitor', script, args, infra_step=True)

  def shutdown_device_temp_monitor(self):
    script = self.m.path['build'].join('scripts', 'slave', 'daemonizer.py')
    args = [
        '--action', 'stop',
        '--pid-file-path', '/tmp/device_monitor.pid',
    ]
    self.m.python('shutdown_device_temp_monitor', script, args, infra_step=True)

  def authorize_adb_devices(self):
    script = self.m.path['build'].join('scripts', 'slave', 'android',
                                    'authorize_adb_devices.py')
    adb_path = self.m.path['checkout'].join(
        'third_party', 'android_tools', 'sdk', 'platform-tools', 'adb')
    args = ['--verbose', '--adb-path', adb_path]
    return self.m.python('authorize_adb_devices', script, args, infra_step=True,
                         env=self.m.chromium.get_env())

  def detect_and_setup_devices(self, restart_usb=False, skip_wipe=False,
                               disable_location=False, min_battery_level=None,
                               disable_network=False, disable_java_debug=False,
                               reboot_timeout=None, max_battery_temp=None):
    self.authorize_adb_devices()
    self.device_status_check(restart_usb=restart_usb)
    self.provision_devices(
      skip_wipe=skip_wipe, disable_location=disable_location,
      min_battery_level=min_battery_level, disable_network=disable_network,
      disable_java_debug=disable_java_debug, reboot_timeout=reboot_timeout,
      max_battery_temp=max_battery_temp)

  @property
  def blacklist_file(self):
    return self.out_path.join('bad_devices.json')

  def device_status_check(self, restart_usb=False, **kwargs):
    args = [
        '--json-output', self.m.json.output(),
        '--blacklist-file', self.blacklist_file,
    ]
    if restart_usb:
      args += ['--restart-usb']

    try:
      result = self.m.step(
          'device_status_check',
          [self.m.path['checkout'].join('build', 'android', 'buildbot',
                                'bb_device_status_check.py')] + args,
          step_test_data=lambda: self.m.json.test_api.output([
              {
                "battery": {
                    "status": "5",
                    "scale": "100",
                    "temperature": "249",
                    "level": "100",
                    "AC powered": "false",
                    "health": "2",
                    "voltage": "4286",
                    "Wireless powered": "false",
                    "USB powered": "true",
                    "technology": "Li-ion",
                    "present": "true"
                },
                "wifi_ip": "",
                "imei_slice": "Unknown",
                "build": "LRX21O",
                "build_detail":
                    "google/razor/flo:5.0/LRX21O/1570415:userdebug/dev-keys",
                "serial": "07a00ca4",
                "type": "flo",
                "adb_status": "device",
                "blacklisted": False,
                "usb_status": True,
            },
            {
              "adb_status": "offline",
              "blacklisted": True,
              "serial": "03e0363a003c6ad4",
              "usb_status": False,
            },
            {
              "adb_status": "unauthorized",
              "blacklisted": True,
              "serial": "03e0363a003c6ad5",
              "usb_status": True,
            },
            {
              "adb_status": "device",
              "blacklisted": True,
              "serial": "03e0363a003c6ad6",
              "usb_status": True,
            }
          ]),
          env=self.m.chromium.get_env(),
          infra_step=True,
          **kwargs)
      self._devices = []
      offline_device_index = 1
      for d in result.json.output:
        try:
          if not d['usb_status']:
            key = '%s: missing' % d['serial']
          elif d['adb_status'] != 'device':
            key = '%s: adb status %s' % (d['serial'], d['adb_status'])
          elif d['blacklisted']:
            key = '%s: blacklisted' % d['serial']
          else:
            key = '%s %s %s' % (d['type'], d['build'], d['serial'])
            self._devices.append(d['serial'])
        except KeyError:
          key = 'unknown device %d' % offline_device_index
          offline_device_index += 1
        result.presentation.logs[key] = self.m.json.dumps(
            d, indent=2).splitlines()
      result.presentation.step_text = 'Online devices: %s' % len(self._devices)
      return result
    except self.m.step.InfraFailure as f:
      params = {
        'summary': ('Device Offline on %s %s' %
          (self.m.properties['mastername'], self.m.properties['slavename'])),
        'comment': ('Buildbot: %s\n(Please do not change any labels)' %
          self.m.properties['buildername']),
        'labels': 'Restrict-View-Google,OS-Android,Infra,Infra-Labs',
      }
      link = ('https://code.google.com/p/chromium/issues/entry?%s' %
        urllib.urlencode(params))
      f.result.presentation.links.update({
        'report a bug': link
      })
      raise

  def provision_devices(self, skip_wipe=False, disable_location=False,
                        min_battery_level=None, disable_network=False,
                        disable_java_debug=False, reboot_timeout=None,
                        max_battery_temp=None, disable_system_chrome=False,
                        remove_system_webview=False, **kwargs):
    args = [
        '-t', self.m.chromium.c.BUILD_CONFIG,
        '--blacklist-file', self.blacklist_file,
        '--output-device-blacklist', self.m.json.output(add_json_log=False),
    ]
    if skip_wipe:
      args.append('--skip-wipe')
    if disable_location:
      args.append('--disable-location')
    if reboot_timeout is not None:
      assert isinstance(reboot_timeout, int)
      assert reboot_timeout > 0
      args.extend(['--reboot-timeout', reboot_timeout])
    if min_battery_level is not None:
      assert isinstance(min_battery_level, int)
      assert min_battery_level >= 0
      assert min_battery_level <= 100
      args.extend(['--min-battery-level', min_battery_level])
    if disable_network:
      args.append('--disable-network')
    if disable_java_debug:
      args.append('--disable-java-debug')
    if max_battery_temp:
      assert isinstance(max_battery_temp, int)
      assert max_battery_temp >= 0
      assert max_battery_temp <= 500
      args.extend(['--max-battery-temp', max_battery_temp])
    if disable_system_chrome:
      args.append('--disable-system-chrome')
    if remove_system_webview:
      args.append('--remove-system-webview')
    if self.c and self.c.chrome_specific_wipe:
      args.append('--chrome-specific-wipe')
    result = self.m.python(
      'provision_devices',
      self.m.path['checkout'].join(
          'build', 'android', 'provision_devices.py'),
      args=args,
      env=self.m.chromium.get_env(),
      infra_step=True,
      **kwargs)
    blacklisted_devices = result.json.output
    if blacklisted_devices:
      result.presentation.status = self.m.step.WARNING
      for d in blacklisted_devices:
        key = 'blacklisted %s' % d
        result.presentation.logs[key] = [d]

  def adb_install_apk(self, apk):
    install_cmd = [
        self.m.path['checkout'].join('build',
                                     'android',
                                     'adb_install_apk.py'),
        apk, '-v', '--blacklist-file', self.blacklist_file,
    ]
    if self.m.chromium.c.BUILD_CONFIG == 'Release':
      install_cmd.append('--release')
    return self.m.step('install ' + apk, install_cmd,
                       infra_step=True,
                       env=self.m.chromium.get_env())

  def asan_device_setup(self):
    install_cmd = [
        self.m.path['checkout'].join('tools', 'android', 'asan', 'third_party',
                                     'asan_device_setup.sh'),
        '--lib',
        self.m.path['checkout'].join(
            'third_party', 'llvm-build', 'Release+Asserts', 'lib', 'clang',
            '3.8.0', 'lib', 'linux', 'libclang_rt.asan-arm-android.so')
    ]
    return self.m.step('asan_device_setup.sh', install_cmd,
                       infra_step=True,
                       env=self.m.chromium.get_env())

  def monkey_test(self, **kwargs):
    args = [
        'monkey',
        '-v',
        '--package=%s' % self.c.channel,
        '--event-count=50000',
        '--blacklist-file', self.blacklist_file,
    ]
    return self.test_runner(
        'Monkey Test',
        args,
        env={'BUILDTYPE': self.c.BUILD_CONFIG},
        **kwargs)


  def _run_sharded_tests(self,
                         config='sharded_perf_tests.json',
                         flaky_config=None,
                         chartjson_output=False,
                         max_battery_temp=None,
                         **kwargs):
    args = [
        'perf',
        '--release',
        '--verbose',
        '--steps', config,
        '--blacklist-file', self.blacklist_file
    ]
    if flaky_config:
      args.extend(['--flaky-steps', flaky_config])
    if chartjson_output:
      args.append('--collect-chartjson-data')
    if max_battery_temp:
      args.extend(['--max-battery-temp', max_battery_temp])

    self.test_runner(
        'Sharded Perf Tests',
        args,
        cwd=self.m.path['checkout'],
        env=self.m.chromium.get_env(),
        **kwargs)

  def run_sharded_perf_tests(self, config, flaky_config=None, perf_id=None,
                             test_type_transform=lambda x: x,
                             chartjson_file=False, max_battery_temp=None,
                             upload_archives_to_bucket=None,
                             **kwargs):
    """Run the perf tests from the given config file.

    config: the path of the config file containing perf tests.
    flaky_config: optional file of tests to avoid.
    perf_id: the id of the builder running these tests
    test_type_transform: a lambda transforming the test name to the
      test_type to upload to.
    """
    # test_runner.py actually runs the tests and records the results
    self._run_sharded_tests(config=config, flaky_config=flaky_config,
                            chartjson_output=chartjson_file,
                            max_battery_temp=max_battery_temp, **kwargs)

    # now obtain the list of tests that were executed.
    result = self.m.step(
        'get perf test list',
        [self.c.test_runner,
         'perf', '--steps', config, '--output-json-list', self.m.json.output(),
         '--blacklist-file', self.blacklist_file],
        step_test_data=lambda: self.m.json.test_api.output([
            {'test': 'perf_test.foo', 'device_affinity': 0,
             'end_time': 1443438432.949711, 'has_archive': True},
            {'test': 'page_cycler.foo', 'device_affinity': 0}]),
        env=self.m.chromium.get_env()
    )
    perf_tests = result.json.output

    if perf_tests and isinstance(perf_tests[0], dict):
      perf_tests = sorted(perf_tests,
          key=lambda x: (x['device_affinity'], x['test']))
    else:
      perf_tests = [{'test': v} for v in perf_tests]

    failures = []
    for test_data in perf_tests:
      test_name = str(test_data['test'])  # un-unicode
      test_type = test_type_transform(test_name)
      annotate = self.m.chromium.get_annotate_by_test_name(test_name)

      if upload_archives_to_bucket and test_data.get('has_archive'):
        archive = self.m.path.mkdtemp('perf_archives').join('output_dir.zip')
      else:
        archive = None
      print_step_cmd = ['perf', '--print-step', test_name, '--verbose',
                        '--blacklist-file', self.blacklist_file]
      if archive:
        print_step_cmd.extend(['--get-output-dir-archive', archive])

      try:
        self.m.chromium.runtest(
          self.c.test_runner,
          print_step_cmd,
          name=test_name,
          perf_dashboard_id=test_type,
          test_type=test_type,
          annotate=annotate,
          results_url='https://chromeperf.appspot.com',
          perf_id=perf_id,
          env=self.m.chromium.get_env(),
          chartjson_file=chartjson_file)
      except self.m.step.StepFailure as f:
        failures.append(f)
      finally:
        if 'device_affinity' in test_data:
          step_result = self.m.step.active_result
          step_result.presentation.step_text += (
              self.m.test_utils.format_step_text(
                  [['Device Affinity: %s' % test_data['device_affinity']]]))

      if archive:
        dest = '{builder}/{test}/{timestamp}_build_{buildno}.zip'.format(
          builder=self.m.properties['buildername'],
          test=test_name,
          timestamp=_TimestampToIsoFormat(test_data['end_time']),
          buildno=self.m.properties['buildnumber'])
        self.m.gsutil.upload(
          name='upload %s output dir archive' % test_name,
          source=archive,
          bucket=upload_archives_to_bucket,
          dest=dest,
          link_name='output_dir.zip')

    if failures:
      raise self.m.step.StepFailure('sharded perf tests failed %s' % failures)

  def run_instrumentation_suite(self, test_apk,
                                isolate_file_path=None,
                                flakiness_dashboard=None,
                                annotation=None, except_annotation=None,
                                screenshot=False, verbose=False, tool=None,
                                apk_package=None, host_driven_root=None,
                                official_build=False, install_apk=None,
                                json_results_file=None, suffix=None, **kwargs):
    if install_apk:
      self.adb_install_apk(install_apk['apk'])

    args = ['--test-apk', test_apk, '--blacklist-file', self.blacklist_file]
    if isolate_file_path:
      args.extend(['--isolate-file-path', isolate_file_path])
    if tool:
      args.append('--tool=%s' % tool)
    if flakiness_dashboard:
      args.extend(['--flakiness-dashboard-server', flakiness_dashboard])
    if annotation:
      args.extend(['-A', annotation])
    if except_annotation:
      args.extend(['-E', except_annotation])
    if screenshot:
      args.append('--screenshot')
    if verbose:
      args.append('--verbose')
    if self.m.chromium.c.BUILD_CONFIG == 'Release':
      args.append('--release')
    if self.c.coverage:
      args.extend(['--coverage-dir', self.coverage_dir,
                   '--python-only'])
    if self.c.incremental_coverage:
      args.extend(['--coverage-dir', self.coverage_dir])
    if host_driven_root:
      args.extend(['--host-driven-root', host_driven_root])
    if official_build:
      args.extend(['--official-build'])
    if json_results_file:
      args.extend(['--json-results-file', json_results_file])

    return self.test_runner(
        'Instrumentation test %s%s' % (annotation or test_apk,
                                       ' (%s)' % suffix if suffix else ''),
        args=['instrumentation'] + args,
        **kwargs)

  def logcat_dump(self, gs_bucket=None):
    if gs_bucket:
      log_path = self.m.chromium.output_dir.join('full_log')
      self.m.python(
          'logcat_dump',
          self.m.path['checkout'].join('build', 'android',
                                       'adb_logcat_printer.py'),
          [ '--output-path', log_path,
            self.m.path['checkout'].join('out', 'logcat') ],
          infra_step=True)
      self.m.gsutil.upload(
          log_path,
          gs_bucket,
          'logcat_dumps/%s/%s' % (self.m.properties['buildername'],
                                  self.m.properties['buildnumber']),
          link_name='logcat dump',
          version='4.7')
    else:
      self.m.python(
          'logcat_dump',
          self.m.path['build'].join('scripts', 'slave', 'tee.py'),
          [self.m.chromium.output_dir.join('full_log'),
           '--',
           self.m.path['checkout'].join('build', 'android',
                                        'adb_logcat_printer.py'),
           self.m.path['checkout'].join('out', 'logcat')],
          infra_step=True,
          )

  def stack_tool_steps(self):
    log_file = self.m.path['checkout'].join('out',
                                            self.m.chromium.c.BUILD_CONFIG,
                                            'full_log')
    target_arch = self.m.chromium.c.gyp_env.GYP_DEFINES['target_arch']
    # gyp converts ia32 to x86, bot needs to do the same
    target_arch = {'ia32': 'x86'}.get(target_arch) or target_arch
    self.m.step(
        'stack_tool_with_logcat_dump',
        [self.m.path['checkout'].join('third_party', 'android_platform',
                              'development', 'scripts', 'stack'),
         '--arch', target_arch, '--more-info', log_file],
        env=self.m.chromium.get_env(),
        infra_step=True)
    self.m.step(
        'stack_tool_for_tombstones',
        [self.m.path['checkout'].join('build', 'android', 'tombstones.py'),
         '-a', '-s', '-w'], env=self.get_env(),
        infra_step=True)
    if self.c.asan_symbolize:
      self.m.step(
          'stack_tool_for_asan',
          [self.m.path['checkout'].join('build',
                                        'android',
                                        'asan_symbolize.py'),
           '-l', log_file], env=self.m.chromium.get_env(),
          infra_step=True)

  def test_report(self):
    self.m.python.inline(
        'test_report',
         """
            import glob, os, sys
            for report in glob.glob(sys.argv[1]):
              with open(report, 'r') as f:
                for l in f.readlines():
                  print l
              os.remove(report)
         """,
         args=[self.m.path['checkout'].join('out',
                                            self.m.chromium.c.BUILD_CONFIG,
                                            'test_logs',
                                            '*.log')],
    )

  def common_tests_setup_steps(self, perf_setup=False):
    self.spawn_logcat_monitor()
    self.authorize_adb_devices()
    self.device_status_check()
    if perf_setup:
      kwargs = {
          'min_battery_level': 95,
          'disable_network': True,
          'disable_java_debug': True,
          'max_battery_temp': 350}
    else:
      kwargs = {}
    self.provision_devices(**kwargs)
    self.spawn_device_temp_monitor()

  def common_tests_final_steps(self, logcat_gs_bucket=None):
    self.shutdown_device_temp_monitor()
    self.logcat_dump(gs_bucket=logcat_gs_bucket)
    self.stack_tool_steps()
    self.test_report()

  def run_bisect_script(self, extra_src='', path_to_config='', **kwargs):
    self.m.step('prepare bisect perf regression',
        [self.m.path['checkout'].join('tools',
                                      'prepare-bisect-perf-regression.py'),
         '-w', self.m.path['slave_build']])

    args = []
    if extra_src:
      args = args + ['--extra_src', extra_src]
    if path_to_config:
      args = args + ['--path_to_config', path_to_config]
    self.m.step('run bisect perf regression',
        [self.m.path['checkout'].join('tools',
                                      'run-bisect-perf-regression.py'),
         '-w', self.m.path['slave_build']] + args, **kwargs)

  def run_test_suite(self, suite, verbose=True, isolate_file_path=None,
                     gtest_filter=None, tool=None, flakiness_dashboard=None,
                     name=None, json_results_file=None, shard_timeout=None,
                     args=None, **kwargs):
    args = args or []
    args.extend(['--blacklist-file', self.blacklist_file])
    if verbose:
      args.append('--verbose')
    if self.c.BUILD_CONFIG == 'Release':
      args.append('--release')
    if isolate_file_path:
      args.append('--isolate_file_path=%s' % isolate_file_path)
    if gtest_filter:
      args.append('--gtest_filter=%s' % gtest_filter)
    if tool:
      args.append('--tool=%s' % tool)
    if flakiness_dashboard:
      args.append('--flakiness-dashboard-server=%s' %
          flakiness_dashboard)
    if json_results_file:
      args.extend(['--json-results-file', json_results_file])
    if shard_timeout:
      args.extend(['-t', str(shard_timeout)])

    self.test_runner(
        name or str(suite),
        ['gtest', '-s', suite] + args,
        env=self.m.chromium.get_env(),
        **kwargs)

  def run_java_unit_test_suite(self, suite, verbose=True,
                               json_results_file=None, suffix=None, **kwargs):
    args = []
    if verbose:
      args.append('--verbose')
    if self.c.BUILD_CONFIG == 'Release':
      args.append('--release')
    if json_results_file:
      args.extend(['--json-results-file', json_results_file])

    self.test_runner(
        '%s%s' % (str(suite), ' (%s)' % suffix if suffix else ''),
        ['junit', '-s', suite] + args,
        env=self.m.chromium.get_env(),
        **kwargs)

  def run_python_unit_test_suite(self, suite, verbose=True, **kwargs):
    args = []
    if verbose:
      args.append('--verbose')

    self.test_runner(
        str(suite),
        ['python', '-s', suite] + args,
        env=self.m.chromium.get_env(),
        **kwargs)

  def coverage_report(self, upload=True, **kwargs):
    """Creates an EMMA HTML report and optionally uploads it to storage bucket.

    Creates an EMMA HTML report using generate_emma_html.py, and uploads the
    HTML report to the chrome-code-coverage storage bucket if |upload| is True.

    Args:
      upload: Uploads EMMA HTML report to storage bucket unless False is passed
        in.
      **kwargs: Kwargs for python and gsutil steps.
    """
    assert self.c.coverage or self.c.incremental_coverage, (
        'Trying to generate coverage report but coverage is not enabled')

    self.m.python(
        'Generate coverage report',
        self.m.path['checkout'].join(
            'build', 'android', 'generate_emma_html.py'),
        args=['--coverage-dir', self.coverage_dir,
              '--metadata-dir', self.out_path.join(self.c.BUILD_CONFIG),
              '--cleanup',
              '--output', self.coverage_dir.join('coverage_html',
                                                 'index.html')],
        infra_step=True,
        **kwargs)

    if upload:
      gs_dest = 'java/%s/%s' % (
          self.m.properties['buildername'], self.m.properties['revision'])
      self.m.gsutil.upload(
          source=self.coverage_dir.join('coverage_html'),
          bucket='chrome-code-coverage',
          dest=gs_dest,
          args=['-R'],
          name='upload coverage report',
          link_name='Coverage report',
          version='4.7',
          **kwargs)

  def incremental_coverage_report(self):
    """Creates an incremental code coverage report.

    Generates a JSON file containing incremental coverage stats. Requires
    |file_changes_path| to contain a file with a valid JSON object.
    """
    step_result = self.m.python(
        'Incremental coverage report',
        self.m.path.join('build', 'android', 'emma_coverage_stats.py'),
        cwd=self.m.path['checkout'],
        args=['-v',
              '--out', self.m.json.output(),
              '--emma-dir', self.coverage_dir.join('coverage_html'),
              '--lines-for-coverage', self.file_changes_path],
        step_test_data=lambda: self.m.json.test_api.output({
          'files': {
            'sample file 1': {
              'absolute': {
                'covered': 70,
                'total': 100,
              },
              'incremental': {
                'covered': 30,
                'total': 50,
              },
            },
            'sample file 2': {
              'absolute': {
                'covered': 50,
                'total': 100,
              },
              'incremental': {
                'covered': 50,
                'total': 50,
              },
            },
          },
          'patch': {
            'incremental': {
              'covered': 80,
              'total': 100,
            },
          },
        })
    )

    if step_result.json.output:
      covered_lines = step_result.json.output['patch']['incremental']['covered']
      total_lines = step_result.json.output['patch']['incremental']['total']
      percentage = covered_lines * 100.0 / total_lines if total_lines else 0

      step_result.presentation.properties['summary'] = (
          'Test coverage for this patch: %s/%s lines (%s%%).' % (
            covered_lines,
            total_lines,
            int(percentage),
        )
      )

      step_result.presentation.properties['moreInfoURL'] = self.m.url.join(
          self.m.properties['buildbotURL'],
          'builders',
          self.m.properties['buildername'],
          'builds',
          self.m.properties['buildnumber'] or '0',
          'steps',
          'Incremental%20coverage%20report',
          'logs',
          'json.output',
      )

  def get_changed_lines_for_revision(self):
    """Saves a JSON file containing the files/lines requiring coverage analysis.

    Saves a JSON object mapping file paths to lists of changed lines to the
    coverage directory.
    """
    # Git provides this default value for the commit hash for staged files when
    # the -l option is used with git blame.
    blame_cached_revision = '0000000000000000000000000000000000000000'

    file_changes = {}
    new_files = self.staged_files_matching_filter('A')
    for new_file in new_files:
      lines = self.m.file.read(
          ('Finding lines changed in added file %s' % new_file),
          new_file,
          test_data='int n = 0;\nn++;\nfor (int i = 0; i < n; i++) {'
      )
      file_changes[new_file] = range(1, len(lines.splitlines()) + 1)

    changed_files = self.staged_files_matching_filter('M')
    for changed_file in changed_files:
      blame = self.m.git(
          'blame', '-l', '-s', changed_file,
          stdout=self.m.raw_io.output(),
          name='Finding lines changed in modified file %s' % changed_file,
          step_test_data=(
              lambda: self.m.raw_io.test_api.stream_output(
                  'int n = 0;\nn++;\nfor (int i = 0; i < n; i++) {'))
      )
      blame_lines = blame.stdout.splitlines()
      file_changes[changed_file] = [i + 1 for i, line in enumerate(blame_lines)
                                    if line.startswith(blame_cached_revision)]

    self.m.file.write(
        'Saving changed lines for revision.',
        self.file_changes_path,
        self.m.json.dumps(file_changes)
    )

  def staged_files_matching_filter(self, diff_filter):
    """Returns list of files changed matching the provided diff-filter.

    Args:
      diff_filter: A string to be used as the diff-filter.

    Returns:
      A list of file paths (strings) matching the provided |diff-filter|.
    """
    diff = self.m.git(
        'diff', '--staged', '--name-only', '--diff-filter', diff_filter,
        stdout=self.m.raw_io.output(),
        name='Finding changed files matching diff filter: %s' % diff_filter,
        step_test_data=(
            lambda: self.m.raw_io.test_api.stream_output(
                'fake/file1.java\nfake/file2.java;\nfake/file3.java'))
    )
    return diff.stdout.splitlines()

  @contextlib.contextmanager
  def handle_exit_codes(self):
    """Handles exit codes emitted by the test runner and other scripts."""
    EXIT_CODES = {
      'error': 1,
      'infra': 87,
      'warning': 88,
    }
    try:
      yield
    except self.m.step.StepFailure as f:
      if (f.result.retcode == EXIT_CODES['error']):
        f.result.presentation.status = self.m.step.FAILURE
      elif (f.result.retcode == EXIT_CODES['infra']):
        f.result.presentation.status = self.m.step.EXCEPTION
      elif (f.result.retcode == EXIT_CODES['warning']):
        f.result.presentation.status = self.m.step.WARNING
      raise

  def test_runner(self, step_name, args=None, **kwargs):
    """Wrapper for the python testrunner script.

    Args:
      step_name: Name of the step.
      args: Testrunner arguments.
    """
    with self.handle_exit_codes():
      return self.m.python(
          step_name, self.c.test_runner, args, **kwargs)
