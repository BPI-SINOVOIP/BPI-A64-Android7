# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import default_flavor
import posixpath
import ssh_devices


"""Utils for running tests remotely over SSH."""


class SSHFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, *args, **kwargs):
    super(SSHFlavorUtils, self).__init__(*args, **kwargs)
    slave_info = ssh_devices.SLAVE_INFO.get(self._skia_api.slave_name,
                                            ssh_devices.SLAVE_INFO['default'])
    self._host = slave_info.ssh_host
    self._port = slave_info.ssh_port
    self._user = slave_info.ssh_user

  @property
  def host(self):
    return self._host

  @property
  def port(self):
    return self._port

  @property
  def user(self):
    return self._user

  def ssh(self, name, cmd, **kwargs):
    """Run the given SSH command."""
    ssh_cmd = ['ssh']
    if self.port:
      ssh_cmd.extend(['-p', self.port])
    dest = self.host
    if self.user:
      dest = self.user + '@' + dest
    ssh_cmd.append(dest)
    ssh_cmd.extend(cmd)
    return self._skia_api.run(self._skia_api.m.step, name, cmd=ssh_cmd,
                              **kwargs)

  def step(self, *args, **kwargs):
    """Run the given step over SSH."""
    self.ssh(*args, **kwargs)

  def device_path_join(self, *args):
    """Like os.path.join(), but for paths on a remote machine."""
    return posixpath.join(*args)

  def device_path_exists(self, path):  # pragma: no cover
    """Like os.path.exists(), but for paths on a remote device."""
    try:
      self.ssh('exists %s' % path, ['test', '-e', path], infra_step=True)
      return True
    except self._skia_api.m.step.StepFailure:
      return False

  def _remove_device_dir(self, path):
    """Remove the directory on the device."""
    self.ssh(name='rmdir %s' % self._skia_api.m.path.basename(path),
             cmd=['rm', '-rf', path],
             infra_step=True)

  def _create_device_dir(self, path):
    """Create the directory on the device."""
    self.ssh(name='mkdir %s' % self._skia_api.m.path.basename(path),
             cmd=['mkdir', '-p', path],
             infra_step=True)

  def create_clean_device_dir(self, path):
    """Like shutil.rmtree() + os.makedirs(), but on a remote device."""
    self._remove_device_dir(path)
    self._create_device_dir(path)

  def _make_scp_cmd(self, remote_path, recurse=True):
    """Prepare an SCP command.

    Returns a partial SCP command and an adjusted remote path.
    """
    cmd = ['scp']
    if recurse:
      cmd.append('-r')
    if self.port:
      cmd.extend(['-P', self.port])
    adj_remote_path = self.host + ':' + remote_path
    if self.user:
      adj_remote_path = self.user + '@' + adj_remote_path
    return cmd, adj_remote_path

  def copy_directory_contents_to_device(self, host_dir, device_dir):
    """Like shutil.copytree(), but for copying to a remote device."""
    _, remote_path = self._make_scp_cmd(device_dir)
    cmd = [self._skia_api.resource('scp_dir_contents.sh'),
           host_dir, remote_path]
    self._skia_api.run(self._skia_api.m.step,
                       name='scp %s' % self._skia_api.m.path.basename(host_dir),
                       cmd=cmd,
                       infra_step=True)

  def copy_directory_contents_to_host(self, device_dir, host_dir):
    """Like shutil.copytree(), but for copying from a remote device."""
    _, remote_path = self._make_scp_cmd(device_dir)
    cmd = [self._skia_api.resource('scp_dir_contents.sh'),
           remote_path, host_dir]
    self._skia_api.run(
        self._skia_api.m.step,
        name='scp %s' % self._skia_api.m.path.basename(device_dir),
        cmd=cmd,
        infra_step=True)

  def copy_file_to_device(self, host_path, device_path):
    """Like shutil.copyfile, but for copying to a connected device."""
    cmd, remote_path = self._make_scp_cmd(device_path, recurse=False)
    cmd.extend([host_path, remote_path])
    self._skia_api.run(
        self._skia_api.m.step,
        name='scp %s' % self._skia_api.m.path.basename(host_path),
        cmd=cmd,
        infra_step=True)

  def read_file_on_device(self, path):
    self.ssh(name='read %s' % self._skia_api.m.path.basename(path),
             cmd=['cat', path],
             stdout=self._skia_api.m.raw_io.output(),
             infra_step=True).stdout.rstrip()

  def remove_file_on_device(self, path, *args, **kwargs):
    """Delete the given file."""
    return self.ssh(name='rm %s' % self._skia_api.m.path.basename(path),
                    cmd=['rm', '-f', path],
                    infra_step=True,
                    *args,
                    **kwargs)
