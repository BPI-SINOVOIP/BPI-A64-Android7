# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import android_devices
import copy
import default_flavor


"""Android flavor utils, used for building for and running tests on Android."""


class _ADBWrapper(object):
  """Wrapper for the ADB recipe module.

  The ADB recipe module looks for the ADB binary at a path we don't have checked
  out on our bots. This wrapper ensures that we set a custom ADB path before
  attempting to use the module.
  """
  def __init__(self, adb_api, path_to_adb, serial, android_flavor):
    self._adb = adb_api
    self._adb.set_adb_path(path_to_adb)
    self._serial = serial
    self._wait_count = 0
    self._android_flavor = android_flavor

  def wait_for_device(self):
    """Run 'adb wait-for-device'."""
    self._wait_count += 1
    cmd = [
        self._android_flavor.android_bin.join('adb_wait_for_device'),
        '-s', self._serial,
    ]
    self._android_flavor._skia_api.run(
        self._android_flavor._skia_api.m.step,
        name='wait for device (%d)' % self._wait_count,
        cmd=cmd,
        env=self._android_flavor._default_env,
        infra_step=True)

  def maybe_wait_for_device(self):
    """Run 'adb wait-for-device' if it hasn't already been run."""
    if self._wait_count == 0:
      self.wait_for_device()

  def __call__(self, *args, **kwargs):
    self.maybe_wait_for_device()
    return self._android_flavor._skia_api.run(self._adb, *args, **kwargs)


class AndroidFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, skia_api):
    super(AndroidFlavorUtils, self).__init__(skia_api)
    self.device = self._skia_api.builder_spec['device_cfg']
    slave_info = android_devices.SLAVE_INFO.get(
        self._skia_api.slave_name,
        android_devices.SLAVE_INFO['default'])
    self.serial = slave_info.serial
    self.android_bin = self._skia_api.m.path['slave_build'].join(
        'skia', 'platform_tools', 'android', 'bin')
    self._android_sdk_root = slave_info.android_sdk_root
    self._adb = _ADBWrapper(
        self._skia_api.m.adb,
        self._skia_api.m.path.join(self._android_sdk_root,
                                   'platform-tools', 'adb'),
        self.serial,
        self)
    self._has_root = slave_info.has_root
    self._default_env = {'ANDROID_SDK_ROOT': self._android_sdk_root,
                         'ANDROID_HOME': self._android_sdk_root,
                         'SKIA_ANDROID_VERBOSE_SETUP': 1}

  def step(self, name, cmd, env=None, **kwargs):
    self._adb.maybe_wait_for_device()
    args = [self.android_bin.join('android_run_skia'),
            '--verbose',
            '--logcat',
            '-d', self.device,
            '-s', self.serial,
            '-t', self._skia_api.configuration,
    ]
    env = dict(env or {})
    env.update(self._default_env)

    return self._skia_api.run(self._skia_api.m.step, name=name, cmd=args + cmd,
                              env=env, **kwargs)

  def compile(self, target):
    """Build the given target."""
    env = dict(self._default_env)
    ccache = self._skia_api.ccache()
    if ccache:
      env['ANDROID_MAKE_CCACHE'] = ccache

    cmd = [self.android_bin.join('android_ninja'), target, '-d', self.device]
    if 'Clang' in self._skia_api.builder_name:
      cmd.append('--clang')
    self._skia_api.run(self._skia_api.m.step, 'build %s' % target, cmd=cmd,
                       env=env, cwd=self._skia_api.m.path['checkout'])

  def device_path_join(self, *args):
    """Like os.path.join(), but for paths on a connected Android device."""
    return '/'.join(args)

  def device_path_exists(self, path):
    """Like os.path.exists(), but for paths on a connected device."""
    exists_str = 'FILE_EXISTS'
    return exists_str in self._adb(
        name='exists %s' % self._skia_api.m.path.basename(path),
        serial=self.serial,
        cmd=['shell', 'if', '[', '-e', path, '];',
             'then', 'echo', exists_str + ';', 'fi'],
        stdout=self._skia_api.m.raw_io.output(),
        infra_step=True
    ).stdout

  def _remove_device_dir(self, path):
    """Remove the directory on the device."""
    self._adb(name='rmdir %s' % self._skia_api.m.path.basename(path),
              serial=self.serial,
              cmd=['shell', 'rm', '-r', path],
              infra_step=True)
    # Sometimes the removal fails silently. Verify that it worked.
    if self.device_path_exists(path):
      raise Exception('Failed to remove %s!' % path)  # pragma: no cover

  def _create_device_dir(self, path):
    """Create the directory on the device."""
    self._adb(name='mkdir %s' % self._skia_api.m.path.basename(path),
              serial=self.serial,
              cmd=['shell', 'mkdir', '-p', path],
              infra_step=True)

  def copy_directory_contents_to_device(self, host_dir, device_dir):
    """Like shutil.copytree(), but for copying to a connected device."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='push %s' % self._skia_api.m.path.basename(host_dir),
        cmd=[self.android_bin.join('adb_push_if_needed'), '--verbose',
             '-s', self.serial, host_dir, device_dir],
        env=self._default_env,
        infra_step=True)

  def copy_directory_contents_to_host(self, device_dir, host_dir):
    """Like shutil.copytree(), but for copying from a connected device."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='pull %s' % self._skia_api.m.path.basename(device_dir),
        cmd=[self.android_bin.join('adb_pull_if_needed'), '--verbose',
             '-s', self.serial, device_dir, host_dir],
        env=self._default_env,
        infra_step=True)

  def copy_file_to_device(self, host_path, device_path):
    """Like shutil.copyfile, but for copying to a connected device."""
    self._adb(name='push %s' % self._skia_api.m.path.basename(host_path),
              serial=self.serial,
              cmd=['push', host_path, device_path],
              infra_step=True)

  def create_clean_device_dir(self, path):
    """Like shutil.rmtree() + os.makedirs(), but on a connected device."""
    self._remove_device_dir(path)
    self._create_device_dir(path)

  def install(self):
    """Run device-specific installation steps."""
    if self._has_root:
      self._adb(name='adb root',
                serial=self.serial,
                cmd=['root'],
                infra_step=True)
      # Wait for the device to reconnect.
      self._skia_api.run(
          self._skia_api.m.step,
          name='wait',
          cmd=['sleep', '10'],
          infra_step=True)
      self._adb.wait_for_device()

    # TODO(borenet): Set CPU scaling mode to 'performance'.
    self._skia_api.run(self._skia_api.m.step,
                       name='kill skia',
                       cmd=[self.android_bin.join('android_kill_skia'),
                            '--verbose', '-s', self.serial],
                       env=self._default_env,
                       infra_step=True)
    if self._has_root:
      self._adb(name='stop shell',
                serial=self.serial,
                cmd=['shell', 'stop'],
                infra_step=True)

  def cleanup_steps(self):
    """Run any device-specific cleanup steps."""
    self._adb(name='reboot',
              serial=self.serial,
              cmd=['reboot'],
              infra_step=True)
    self._skia_api.run(
        self._skia_api.m.step,
        name='wait for reboot',
        cmd=['sleep', '10'],
        infra_step=True)
    self._adb.wait_for_device()

  def read_file_on_device(self, path, *args, **kwargs):
    """Read the given file."""
    return self._adb(name='read %s' % self._skia_api.m.path.basename(path),
                     serial=self.serial,
                     cmd=['shell', 'cat', path],
                     stdout=self._skia_api.m.raw_io.output(),
                     infra_step=True).stdout.rstrip()

  def remove_file_on_device(self, path, *args, **kwargs):
    """Delete the given file."""
    return self._adb(name='rm %s' % self._skia_api.m.path.basename(path),
                     serial=self.serial,
                     cmd=['shell', 'rm', '-f', path],
                     infra_step=True,
                     *args,
                     **kwargs)

  def get_device_dirs(self):
    """ Set the directories which will be used by the build steps."""
    device_scratch_dir = self._adb(
        name='get EXTERNAL_STORAGE dir',
        serial=self.serial,
        cmd=['shell', 'echo', '$EXTERNAL_STORAGE'],
        stdout=self._skia_api.m.raw_io.output(),
        infra_step=True,
    ).stdout.rstrip()
    prefix = self.device_path_join(device_scratch_dir, 'skiabot', 'skia_')
    return default_flavor.DeviceDirs(
        dm_dir=prefix + 'dm',
        perf_data_dir=prefix + 'perf',
        resource_dir=prefix + 'resources',
        images_dir=prefix + 'images',
        skp_dirs=default_flavor.SKPDirs(
            prefix + 'skp', self._skia_api.builder_name, '/'),
        tmp_dir=prefix + 'tmp_dir')

