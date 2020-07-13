# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import copy
import default_flavor


"""iOS flavor utils, used for building for and running tests on iOS."""


class iOSFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, skia_api):
    super(iOSFlavorUtils, self).__init__(skia_api)
    self.ios_bin = self._skia_api.m.path['slave_build'].join(
        'skia', 'platform_tools', 'ios', 'bin')

  def step(self, name, cmd, **kwargs):
    args = [self.ios_bin.join('ios_run_skia')]

    # Convert 'dm' and 'nanobench' from positional arguments
    # to flags, which is what iOSShell expects to select which
    # one is being run.
    cmd = ["--" + c if c in ['dm', 'nanobench'] else c
          for c in cmd]
    return self._skia_api.run(self._skia_api.m.step, name=name, cmd=args + cmd,
                              **kwargs)

  def compile(self, target):
    """Build the given target."""
    cmd = [self.ios_bin.join('ios_ninja')]
    self._skia_api.run(self._skia_api.m.step, 'build iOSShell', cmd=cmd,
                       cwd=self._skia_api.m.path['checkout'])

  def device_path_join(self, *args):
    """Like os.path.join(), but for paths on a connected iOS device."""
    return '/'.join(args)

  def device_path_exists(self, path):
    """Like os.path.exists(), but for paths on a connected device."""
    return self._skia_api.run(
        self._skia_api.m.step,
        'exists %s' % path,
        cmd=[self.ios_bin.join('ios_path_exists'), path],
        infra_step=True,
    ) # pragma: no cover

  def _remove_device_dir(self, path):
    """Remove the directory on the device."""
    return self._skia_api.run(
        self._skia_api.m.step,
        'rmdir %s' % path,
        cmd=[self.ios_bin.join('ios_rm'), path],
        infra_step=True,
    )

  def _create_device_dir(self, path):
    """Create the directory on the device."""
    return self._skia_api.run(
        self._skia_api.m.step,
        'mkdir %s' % path,
        cmd=[self.ios_bin.join('ios_mkdir'), path],
        infra_step=True,
    )

  def copy_directory_contents_to_device(self, host_dir, device_dir):
    """Like shutil.copytree(), but for copying to a connected device."""
    return self._skia_api.run(
        self._skia_api.m.step,
        name='push %s to %s' % (self._skia_api.m.path.basename(host_dir),
                                self._skia_api.m.path.basename(device_dir)),
        cmd=[self.ios_bin.join('ios_push_if_needed'),
             host_dir, device_dir],
        infra_step=True,
    )

  def copy_directory_contents_to_host(self, device_dir, host_dir):
    """Like shutil.copytree(), but for copying from a connected device."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='pull %s' % self._skia_api.m.path.basename(device_dir),
        cmd=[self.ios_bin.join('ios_pull_if_needed'),
             device_dir, host_dir],
        infra_step=True,
    )

  def copy_file_to_device(self, host_path, device_path):
    """Like shutil.copyfile, but for copying to a connected device."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='push %s' % host_path,
        cmd=[self.ios_bin.join('ios_push_file'), host_path, device_path],
        infra_step=True,
    ) # pragma: no cover

  def create_clean_device_dir(self, path):
    """Like shutil.rmtree() + os.makedirs(), but on a connected device."""
    self._remove_device_dir(path)
    self._create_device_dir(path)

  def install(self):
    """Run device-specific installation steps."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='install iOSShell',
        cmd=[self.ios_bin.join('ios_install')],
        infra_step=True)

  def cleanup_steps(self):
    """Run any device-specific cleanup steps."""
    self._skia_api.run(
        self._skia_api.m.step,
        name='reboot',
        cmd=[self.ios_bin.join('ios_restart')],
        infra_step=True)
    self._skia_api.run(
        self._skia_api.m.step,
        name='wait for reboot',
        cmd=['sleep', '20'],
        infra_step=True)

  def read_file_on_device(self, path):
    """Read the given file."""
    ret = self._skia_api.run(
        self._skia_api.m.step,
        name='read %s' % self._skia_api.m.path.basename(path),
        cmd=[self.ios_bin.join('ios_cat_file'), path],
        stdout=self._skia_api.m.raw_io.output(),
        infra_step=True)
    return ret.stdout.rstrip() if ret.stdout else ret.stdout

  def remove_file_on_device(self, path):
    """Remove the file on the device."""
    return self._skia_api.run(
        self._skia_api.m.step,
        'rm %s' % path,
        cmd=[self.ios_bin.join('ios_rm'), path],
        infra_step=True,
    )

  def get_device_dirs(self):
    """ Set the directories which will be used by the build steps."""
    prefix = self.device_path_join('skiabot', 'skia_')
    return default_flavor.DeviceDirs(
        dm_dir=prefix + 'dm',
        perf_data_dir=prefix + 'perf',
        resource_dir=prefix + 'resources',
        images_dir=prefix + 'images',
        skp_dirs=default_flavor.SKPDirs(
            prefix + 'skp', self._skia_api.builder_name, '/'),
        tmp_dir=prefix + 'tmp_dir')
