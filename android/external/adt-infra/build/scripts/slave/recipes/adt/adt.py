# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Recipe for emulator boot tests."""

from recipe_engine.types import freeze
import os

DEPS = [
    'path',
    'platform',
    'properties',
    'python',
    'raw_io',
    'step',
]

MASTER_USER = 'user'
MASTER_IP = '172.27.213.40'

def RunSteps(api):
  buildername = api.properties['buildername']
  project = str(api.properties['project'])

  # Always download emulator image, only download system_image when there're changes to them
  remote_files_list = [api.properties['emulator_image']]
  if project == 'git_mnc-emu-dev':
    remote_files_list.append(api.properties['mnc_system_image'])
  elif project == 'git_lmp-mr1-emu-dev':
    remote_files_list.append(api.properties['lmp_mr1_system_image'])
  elif project == 'git_nyc-release':
    remote_files_list.append(api.properties['nyc_system_image'])
  elif project == 'git_lmp-emu-dev':
    remote_files_list.append(api.properties['lmp_system_image'])
  elif project == 'git_klp-emu-dev':
    remote_files_list.append(api.properties['klp_system_image'])

  download_path = api.path['slave_build'].join('')
  emulator_path = download_path.join('tools', 'emulator')

  env_path = ['%(PATH)s']

  # find android sdk root directory
  home_dir = os.path.expanduser('~')
  if api.platform.is_mac:
    android_sdk_home = os.path.join(home_dir, 'Android', 'android-sdk-macosx')
  elif api.platform.is_linux:
    android_sdk_home = os.path.join(home_dir, 'Android', 'android-sdk-linux')
  # On windows, we need cygwin and GnuWin for commands like, rm, scp, unzip
  elif api.platform.is_win:
    android_sdk_home = os.path.join(home_dir, 'Android', 'android-sdk')
    if api.platform.bits == 64:
      gnu_path = 'C:\\Program Files (x86)\\GnuWin32\\bin'
      cygwin_path = 'C:\\cygwin64\\bin'
    else:
      gnu_path = 'C:\\Program Files\\GnuWin32\\bin'
      cygwin_path = 'C:\\cygwin\\bin'
    env_path = [gnu_path, cygwin_path] + env_path
  else:
    raise # pragma: no cover

  android_tools_dir = os.path.join(android_sdk_home, 'tools')
  android_platform_dir = os.path.join(android_sdk_home, 'platform-tools')
  android_buildtools_dir = os.path.join(android_sdk_home, 'build-tools', '23.0.2')
  env_path += [android_tools_dir, android_platform_dir, android_buildtools_dir]
  env = {'PATH': api.path.pathsep.join(env_path),
         'ANDROID_SDK_ROOT': android_sdk_home}

  # Find emulator script based on current location
  # Current directory should be [project root]/build/scripts/slave/recipes/[recipeName]/
  # Emulator scripts are located [project root]/emu_test
  build_dir = api.path['build']
  script_root = api.path.join(build_dir, os.pardir, 'emu_test')
  dotest_path = api.path.join(script_root, 'dotest.py')
  image_util_path = api.path.join(script_root, 'utils', 'download_unzip_image.py')
  buildnum = api.properties['buildnumber']
  rev = api.properties['got_revision']
  log_util_path = api.path.join(script_root, 'utils', 'zip_upload_logs.py')
  init_bot_util_path = api.path.join(script_root, 'utils', 'emu_bot_init.py')
  log_dir = 'logs-build_%s-rev_%s' % (buildnum, rev)

  try:
    api.python('Clean up bot', init_bot_util_path,
               ['--build-dir', api.path['slave_build']],
               env=env)
  except api.step.StepFailure as f: # pragma: no cover
    # Not able to delete some files, it won't be the fault of emulator
    # not a stopper to run actual tests
    # so set status to "warning" and continue test
    f.result.presentation.status = api.step.WARNING

  api.python("Download and Unzip Images", image_util_path,
             ['--file', ','.join(remote_files_list),
              '--ip', MASTER_IP,
              '--user', MASTER_USER],
             env=env)
  def PythonTestStep(description,
                     session_dir,
                     test_pattern,
                     cfg_file, cfg_filter):
    deferred_step_result = api.python(description, dotest_path,
                                      ['-l', 'INFO', '-exec', emulator_path,
                                       '-s', session_dir,
                                       '-p', test_pattern,
                                       '-c', api.path.join(script_root, 'config', cfg_file),
                                       '-n', buildername,
                                       '-f', cfg_filter],
                                      env=env, stderr=api.raw_io.output('err'))
    if not deferred_step_result.is_ok:
      stderr_output = deferred_step_result.get_error().result.stderr
      print stderr_output
      lines = [line for line in stderr_output.split('\n')
               if line.startswith('FAIL:') or line.startswith('TIMEOUT:')]
      for line in lines:
        api.step.active_result.presentation.logs[line] = ''
    else:
      print deferred_step_result.get_result().stderr
    if "CTS" in description:
      api.step.active_result.presentation.links['View XML'] = api.path.join("..", "..", "..",
                                                    "CTS_Result", buildername.replace(" ", "_"), 'build_%s-rev_%s' % (buildnum, rev), "testResult.xml")

  with api.step.defer_results():
    # If this build is triggered by sys_image poller, skip public system image step
    # since these has been tested with the same emulator revision in the
    # build triggered by emu poller
    if project == "emu-master-dev":
      PythonTestStep('Boot Test - Public System Image',
                     api.path.join(log_dir, 'boot_test_public_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "<=21"}')
    # At least one of the system images are available
    if str(api.properties['lmp_mr1_revision']) != 'None' and project in ['git_lmp-mr1-emu-dev', 'emu-master-dev']:
      PythonTestStep('Boot Test - LMP MR1 System Image',
                     api.path.join(log_dir, 'boot_test_LMP_MR1_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "22"}')
    if str(api.properties['mnc_revision']) != 'None' and project in ['git_mnc-emu-dev', 'emu-master-dev']:
      PythonTestStep('Boot Test - MNC System Image',
                     api.path.join(log_dir, 'boot_test_MNC_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "23"}')
    if str(api.properties['nyc_revision']) != 'None' and project in ['git_nyc-release', 'emu-master-dev']:
      PythonTestStep('Boot Test - NYC System Image',
                     api.path.join(log_dir, 'boot_test_NYC_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "24"}')
    if str(api.properties['lmp_revision']) != 'None' and project in ['git_lmp-emu-dev', 'emu-master-dev']:
      PythonTestStep('Boot Test - LMP System Image',
                     api.path.join(log_dir, 'boot_test_LMP_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "21", "tag": "google_apis"}')
    if str(api.properties['klp_revision']) != 'None' and project in ['git_klp-emu-dev', 'emu-master-dev']:
      PythonTestStep('Boot Test - KLP System Image',
                     api.path.join(log_dir, 'boot_test_KLP_sysimage'),
                     'test_boot.*',
                     'boot_cfg.csv',
                     '{"api": "19", "tag": "default"}')
    PythonTestStep('Run Emulator CTS Test',
                   api.path.join(log_dir, 'CTS_test'),
                   'test_cts.*',
                   'cts_cfg.csv',
                   '{"tot_image": "no"}')
    api.python("Zip and Upload Logs", log_util_path,
               ['--dir', log_dir,
                '--name', 'build_%s-rev_%s.zip' % (buildnum, rev),
                '--ip', MASTER_IP,
                '--user', MASTER_USER,
                '--dst', '%s%s/'% (api.properties['logs_dir'], buildername)],
                env=env)

def GenTests(api):
  yield (
    api.test('basic-win32') +
    api.platform.name('win') +
    api.platform.bits(32) +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Win 7 32-bit HD 4400',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu_gspoller_windows/sdk-repo-windows-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('basic-win64') +
    api.platform.name('win') +
    api.platform.bits(64) +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Win 7 64-bit HD 4400',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu/sdk-repo-windows-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('basic-mac') +
    api.platform.name('mac') +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Mac 10.10.5 Iris Pro',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu/sdk-repo-mac-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('basic-linux') +
    api.platform.name('linux') +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Ubuntu 15.04 Quadro K600',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu/sdk-repo-linux-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('boot-test-mnc-project') +
    api.platform.name('linux') +
    api.properties(
      mastername='client.adt',
      project='git_mnc-emu-dev',
      buildername='Ubuntu 15.04 Quadro K600',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu/sdk-repo-linux-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('boot-test-lmp-project') +
    api.platform.name('linux') +
    api.properties(
      mastername='client.adt',
      project='git_lmp-mr1-emu-dev',
      buildername='Ubuntu 15.04 Quadro K600',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu/sdk-repo-linux-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    )
  )

  yield (
    api.test('boot-test-timeout-fail') +
    api.platform.name('linux') +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Ubuntu 15.04 Quadro K600',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu_gspoller_linux/sdk-repo-windows-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    ) +
    api.override_step_data('Boot Test - Public System Image',
                           api.raw_io.stream_output('TIMEOUT: foobar', 'stderr')
    ) +
    api.step_data('Boot Test - Public System Image', retcode=1)
  )

  yield (
    api.test('cts-test-timeout-fail') +
    api.platform.name('linux') +
    api.properties(
      mastername='client.adt',
      project='emu-master-dev',
      buildername='Ubuntu 15.04 Quadro K600',
      lmp_revision='2460722',
      mnc_revision='2458059',
      emulator_image='/images/emu_gspoller_linux/sdk-repo-windows-tools-2344972.zip',
      lmp_system_image='/images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2460722.zip,images/git_lmp-mr1-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2460722.zip',
      mnc_system_image='/images/git_mnc-emu-dev-linux-sdk_google_phone_x86_64-sdk_addon/sdk-repo-linux-system-images-2458059.zip,/images/git_mnc-emu-dev-linux-sdk_google_phone_x86-sdk_addon/sdk-repo-linux-system-images-2458059.zip',
      logs_dir='/home/slave_logs/',
      buildnumber='3077',
    ) +
    api.override_step_data('Run Emulator CTS Test',
                           api.raw_io.stream_output('TIMEOUT: foobar', 'stderr')
    ) +
    api.step_data('Run Emulator CTS Test', retcode=1)
  )
