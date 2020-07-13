# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import android_devices
import copy
import default_flavor


"""Appurify flavor utils, used for building and running tests in Appurify."""


class AppurifyFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, skia_api):
    super(AppurifyFlavorUtils, self).__init__(skia_api)
    self.build_cfg = self._skia_api.builder_spec['device_cfg']
    self.device = self._skia_api.builder_spec['builder_cfg']['model']
    slave_info = android_devices.SLAVE_INFO.get(
        self._skia_api.slave_name,
        android_devices.SLAVE_INFO['default'])
    self.android_tools = self._skia_api.m.path['slave_build'].join(
        'skia', 'platform_tools', 'android')
    self.android_bin = self.android_tools.join('bin')
    self.apk_dir = self.android_tools.join('apps', 'visualbench', 'build',
                                           'outputs', 'apk')
    self.assets_dir = self.android_tools.join('apps', 'visualbench', 'src',
                                              'main', 'assets')
    self._android_sdk_root = slave_info.android_sdk_root
    self._default_env = {'ANDROID_SDK_ROOT': self._android_sdk_root,
                         'ANDROID_HOME': self._android_sdk_root,
                         'SKIA_ANDROID_VERBOSE_SETUP': 1}

  def step(self, name, cmd, env=None, **kwargs):

    env = dict(self._default_env)
    ccache = self._skia_api.ccache()
    if ccache:
      env['ANDROID_MAKE_CCACHE'] = ccache

    # Clean out any previous builds.
    self.create_clean_host_dir(self.apk_dir)

    # Write the nanobench flags to a file inside the APK.

    # Chomp 'nanobench' from the command.
    cmd = cmd[1:]

    self.create_clean_host_dir(self.assets_dir)
    self._skia_api._writefile(self.assets_dir.join('nanobench_flags.txt'),
                              ' '.join([str(c) for c in cmd]))

    # Build the APK.
    target = 'VisualBenchTest_APK'
    cmd = [self.android_bin.join('android_ninja'), target, '-d', self.build_cfg]
    self._skia_api.run(self._skia_api.m.step, 'build %s' % target, cmd=cmd,
                       env=env, cwd=self._skia_api.m.path['checkout'])

    # Run VisualBench on Appurify.
    main_apk = self.apk_dir.join('visualbench-arm-release.apk')
    test_apk = self.apk_dir.join(
        'visualbench-arm-debug-androidTest-unaligned.apk')
    cmd = ['python', self._skia_api.resource('appurify_wrapper.py'),
      '--device', self.device,
      '--apk', main_apk,
      '--test-apk', test_apk,
      '--result-dir', self._skia_api.tmp_dir,
      '--skp-dir', self._skia_api.local_skp_dirs.skp_dir(),
      '--resource-dir', self._skia_api.resource_dir,
    ]
    env = dict(env or {})
    env.update(self._default_env)
    env.update({
      'APPURIFY_API_HOST': '172.22.21.180',
      'APPURIFY_API_PORT': '80',
      'APPURIFY_API_PROTO': 'http',
    })

    result = self._skia_api.run(self._skia_api.m.step, name=name, cmd=cmd,
                                env=env, **kwargs)

    # Unzip the results.
    cmd = ['unzip', '-o', self._skia_api.tmp_dir.join('results.zip'),
           '-d', self._skia_api.tmp_dir]
    self._skia_api.run(self._skia_api.m.step, name='unzip_results', cmd=cmd)

    return result

  def compile(self, target):
    """Build the given target."""
    # No-op. We compile when we actually want to run, since we need to package
    # other items into the APK.
    pass

  def read_file_on_device(self, f):
    # This is a no-op, since we don't actually have access to the device.
    return ''

  def remove_file_on_device(self, f):
    # This is a no-op, since we don't actually have access to the device.
    pass

  def create_clean_device_dir(self, f):
    # This is a no-op, since we don't actually have access to the device.
    pass

  def copy_directory_contents_to_device(self, h, d):
    # This is a no-op, since we don't actually have access to the device.
    pass

  def copy_directory_contents_to_host(self, device_dir, host_dir):
    # The test results are already on the host but in the results dir. Copy them
    # to the expected location.
    device_dir = self._skia_api.tmp_dir.join(
        'appurify_results', 'artifacts_directory',
        'sdcard-skia_results')
    # shutil.copytree requires that the dest dir does not exist.
    self._skia_api.m.file.rmtree(self._skia_api.m.path.basename(host_dir),
                                 host_dir)
    self._skia_api.m.file.copytree('copy dir', device_dir, host_dir)

  def copy_file_to_device(self, h, d):
    # This is a no-op, since we don't actually have access to the device.
    pass

  def get_device_dirs(self):
    """Set the directories which will be used by the build steps."""
    sdcard = '/sdcard'
    results_dir = sdcard + '/skia_results'
    return default_flavor.DeviceDirs(
        dm_dir=results_dir,
        perf_data_dir=results_dir,
        resource_dir=sdcard + '/resources',
        images_dir=sdcard + '/images',
        skp_dirs=default_flavor.SKPDirs(
            sdcard, self._skia_api.builder_name, '/'),
        tmp_dir=sdcard + '/tmp')
