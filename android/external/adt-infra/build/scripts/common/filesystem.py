# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os


class Filesystem(object):
  """A simple filesystem abstraction, useful for faking out for testing.

  This is a stripped-down version of the Filesystem class provided in the
  TYP (Test Your Project) framework: https://github.com/dpranke/typ.
  """

  def abspath(self, path):
    return os.path.abspath(path)

  def basename(self, path):
    return os.path.basename(path)

  def dirname(self, path):
    return os.path.dirname(path)

  def exists(self, *comps):
    return os.path.exists(self.join(*comps))

  def isfile(self, *comps):
    return os.path.isfile(self.join(*comps))

  def isdir(self, *comps):
    return os.path.isdir(self.join(*comps))

  def join(self, *comps):
    return os.path.join(*comps)

  def listfiles(self, path):
    return [f for f in os.listdir(path)
            if not self.isdir(path, f) and
               not f.startswith('.')]

  def listdirs(self, path):
    return [d for d in os.listdir(path)
            if self.isdir(path, d) and
               not d.startswith('.')]

  def normpath(self, path):
    return os.path.normpath(path)

  def read_text_file(self, path):
    with open(path) as fp:
      return fp.read()

  def relpath(self, path, start='.'):
    return os.path.relpath(path, start)

  def write_text_file(self, path, contents):
    with open(path, 'w') as fp:
      fp.write(contents)
