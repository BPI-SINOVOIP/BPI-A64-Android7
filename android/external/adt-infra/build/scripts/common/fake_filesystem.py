# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os


class FakeFilesystem(object):
  """A fake implementation of the Filesystem class in filesystem.py.

  This is a stripped-down version of the FakeHost class provided in the
  TYP (Test Your Project) framework: https://github.com/dpranke/typ.
  """
  def __init__(self, files=None, cwd='/'):
    self.sep = '/'
    self.cwd = cwd
    self.files = files or {}
    self.dirs = set(self.dirname(f) for f in self.files)

  def abspath(self, relpath):
    if relpath.startswith(self.sep):
      return relpath
    return self.join(self.cwd, relpath)

  def basename(self, path):
    return path.split(self.sep)[-1]

  def dirname(self, path):
    return self.sep.join(path.split(self.sep)[:-1])

  def exists(self, *comps):
    path = self.abspath(*comps)
    return ((path in self.files and self.files[path] is not None) or
             path in self.dirs)

  def join(self, *comps):
    p = ''
    for c in comps:
      if c in ('', '.'):
        continue
      elif c.startswith(self.sep):
        p = c
      elif p:
        p += self.sep + c
      else:
        p = c

    # Handle ./
    p = p.replace('/./', self.sep)

    # Handle ../
    while '../' in p:
      comps = p.split(self.sep)
      idx = comps.index('..')
      comps = comps[:idx-1] + comps[idx+1:]
      p = self.sep.join(comps)
    return p

  def listfiles(self, path):
    return [filepath.replace(path + self.sep, '') for filepath in self.files
            if (self.dirname(filepath) == path and
                not self.basename(filepath).startswith('.'))]

  def read_text_file(self, path):
    return self.files[self.abspath(path)]

  def relpath(self, path, start='.'):
    full_path = self.abspath(path)
    full_start = self.abspath(start)

    # TODO: handle cases where path is not directly under start.
    assert full_start in full_path
    return full_path[len(full_start) + 1:]

  def write_text_file(self, path, contents):
    self.files[self.abspath(path)] = contents
