# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Default flavor utils class, used for desktop builders."""


class SKPDirs(object):
  """Wraps up important directories for SKP-related testing."""

  def __init__(self, root_dir, builder_name, path_sep):
    self._root_dir = root_dir
    self._builder_name = builder_name
    self._path_sep = path_sep

  @property
  def root_dir(self):
    return self._root_dir

  def skp_dir(self, skp_version=None):
    root_dir = self.root_dir
    if skp_version:
      root_dir += '_%s' % skp_version
    return self._path_sep.join((root_dir, 'skps'))


class DeviceDirs(object):
  def __init__(self,
               dm_dir,
               perf_data_dir,
               resource_dir,
               images_dir,
               skp_dirs,
               tmp_dir):
    self._dm_dir = dm_dir
    self._perf_data_dir = perf_data_dir
    self._resource_dir = resource_dir
    self._images_dir = images_dir
    self._skp_dir = skp_dirs.skp_dir()
    self._tmp_dir = tmp_dir

  @property
  def dm_dir(self):
    """Where DM writes."""
    return self._dm_dir

  @property
  def perf_data_dir(self):
    return self._perf_data_dir

  @property
  def resource_dir(self):
    return self._resource_dir

  @property
  def images_dir(self):
    return self._images_dir

  @property
  def skp_dir(self):
    """Holds SKP files that are consumed by RenderSKPs and BenchPictures."""
    return self._skp_dir

  @property
  def tmp_dir(self):
    return self._tmp_dir


class DefaultFlavorUtils(object):
  """Utilities to be used by build steps.

  The methods in this class define how certain high-level functions should
  work. Each build step flavor should correspond to a subclass of
  DefaultFlavorUtils which may override any of these functions as appropriate
  for that flavor.

  For example, the AndroidFlavorUtils will override the functions for
  copying files between the host and Android device, as well as the
  'step' function, so that commands may be run through ADB.
  """
  def __init__(self, skia_api, *args, **kwargs):
    self._skia_api = skia_api
    self._chrome_path = None

  def step(self, name, cmd, **kwargs):
    """Wrapper for the Step API; runs a step as appropriate for this flavor."""
    path_to_app = self._skia_api.out_dir.join(
        self._skia_api.configuration, cmd[0])
    if (self._skia_api.m.platform.is_linux and
        'x86_64' in self._skia_api.builder_name and
        not 'TSAN' in self._skia_api.builder_name):
      new_cmd = ['catchsegv', path_to_app]
    else:
      new_cmd = [path_to_app]
    new_cmd.extend(cmd[1:])
    return self._skia_api.run(self._skia_api.m.step,
                              name, cmd=new_cmd, **kwargs)

  @property
  def chrome_path(self):
    """Path to a checkout of Chrome on this machine."""
    if self._chrome_path is None:
      test_data = lambda: self._skia_api.m.raw_io.test_api.output(
          '/home/chrome-bot/src')
      self._chrome_path = self._skia_api.m.python.inline(
          'get CHROME_PATH',
          """
          import os
          import sys
          with open(sys.argv[1], 'w') as f:
            f.write(os.path.join(os.path.expanduser('~'), 'src'))
          """,
          args=[self._skia_api.m.raw_io.output()],
          step_test_data=test_data,
          infra_step=True,
      ).raw_io.output
    return self._chrome_path

  def compile(self, target):
    """Build the given target."""
    # The CHROME_PATH environment variable is needed for builders that use
    # toolchains downloaded by Chrome.
    env = {'CHROME_PATH': self.chrome_path}
    if self._skia_api.m.platform.is_win:
      make_cmd = ['python', 'make.py']
    else:
      make_cmd = ['make']
    cmd = make_cmd + [target]
    self._skia_api.run(self._skia_api.m.step, 'build %s' % target, cmd=cmd,
                       env=env, cwd=self._skia_api.m.path['checkout'])

  def device_path_join(self, *args):
    """Like os.path.join(), but for paths on a connected device."""
    return self._skia_api.m.path.join(*args)

  def device_path_exists(self, path):
    """Like os.path.exists(), but for paths on a connected device."""
    return self._skia_api.m.path.exists(path, infra_step=True)  # pragma: no cover

  def copy_directory_contents_to_device(self, host_dir, device_dir):
    """Like shutil.copytree(), but for copying to a connected device."""
    # For "normal" builders who don't have an attached device, we expect
    # host_dir and device_dir to be the same.
    if str(host_dir) != str(device_dir):
      raise ValueError('For builders who do not have attached devices, copying '
                       'from host to device is undefined and only allowed if '
                       'host_path and device_path are the same (%s vs %s).' % (
                       str(host_path), str(device_path)))  # pragma: no cover

  def copy_directory_contents_to_host(self, device_dir, host_dir):
    """Like shutil.copytree(), but for copying from a connected device."""
    # For "normal" builders who don't have an attached device, we expect
    # host_dir and device_dir to be the same.
    if str(host_dir) != str(device_dir):
      raise ValueError('For builders who do not have attached devices, copying '
                       'from device to host is undefined and only allowed if '
                       'host_path and device_path are the same (%s vs %s).' % (
                       str(host_path), str(device_path)))  # pragma: no cover

  def copy_file_to_device(self, host_path, device_path):
    """Like shutil.copyfile, but for copying to a connected device."""
    # For "normal" builders who don't have an attached device, we expect
    # host_dir and device_dir to be the same.
    if str(host_path) != str(device_path):  # pragma: no cover
      raise ValueError('For builders who do not have attached devices, copying '
                       'from host to device is undefined and only allowed if '
                       'host_path and device_path are the same (%s vs %s).' % (
                       str(host_path), str(device_path)))

  def create_clean_device_dir(self, path):
    """Like shutil.rmtree() + os.makedirs(), but on a connected device."""
    self.create_clean_host_dir(path)

  def create_clean_host_dir(self, path):
    """Convenience function for creating a clean directory."""
    self._skia_api.m.file.rmtree(
        self._skia_api.m.path.basename(path), path, infra_step=True)
    self._skia_api.m.file.makedirs(
        self._skia_api.m.path.basename(path), path, infra_step=True)

  def install(self):
    """Run device-specific installation steps."""
    pass

  def cleanup_steps(self):
    """Run any device-specific cleanup steps."""
    pass

  def get_device_dirs(self):
    """ Set the directories which will be used by the build steps.

    These refer to paths on the same device where the test executables will
    run, for example, for Android bots these are paths on the Android device
    itself. For desktop bots, these are just local paths.
    """
    pardir = self._skia_api.m.path.pardir
    join = self._skia_api.m.path['slave_build'].join
    return DeviceDirs(
        dm_dir=join('dm'),
        perf_data_dir=self._skia_api.perf_data_dir,
        resource_dir=self._skia_api.resource_dir,
        images_dir=join('images'),
        skp_dirs=self._skia_api.local_skp_dirs,
        tmp_dir=join('tmp'))

  def __repr__(self):
    return '<%s object>' % self.__class__.__name__  # pragma: no cover
