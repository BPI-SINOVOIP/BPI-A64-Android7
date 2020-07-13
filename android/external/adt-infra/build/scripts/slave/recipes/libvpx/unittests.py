# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
    'git',
    'path',
    'platform',
    'properties',
    'python',
    'step',
]

# TODO (joshualitt) the configure script is messed up so we need a relative
# path.  Essentially, it must be using argv[0] when invoking some of the
# scripts in the libvpx directory
CONFIGURE_PATH_REL = './libvpx/configure'

def RunSteps(api):
  # Paths and other constants
  build_root = api.path['slave_build']

  # libvpx paths
  libvpx_git_url = api.properties['libvpx_git_url']
  libvpx_root = build_root.join('libvpx')

  api.python.inline(
      'clean_build', r"""
          import os, sys, shutil
          root = sys.argv[1]
          nuke_dirs = sys.argv[2:]
          for fname in os.listdir(root):
            path = os.path.join(root, fname)
            if os.path.isfile(path):
              os.unlink(path)
            elif fname in nuke_dirs:
              shutil.rmtree(path)
      """, args=[build_root, 'libs', 'obj', 'vp8', 'vp9', 'vpx', 'vpx_mem',
                 'vpx_ports', 'vpx_scale', 'third_party'])

  api.git.checkout(
      libvpx_git_url, dir_path=libvpx_root, recursive=True)

  api.step('configure', [CONFIGURE_PATH_REL])

  api.step('run tests', ['make', 'test', '-j8'])

def GenTests(api):
  # Right now we just support linux, but one day we will have mac and windows
  # as well
  yield (
    api.test('basic_linux_64') +
    api.properties(
        libvpx_git_url='https://chromium.googlesource.com/webm/libvpx'))
