# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import default_flavor


"""Utils for running under Valgrind."""


class ValgrindFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, *args, **kwargs):
    super(ValgrindFlavorUtils, self).__init__(*args, **kwargs)
    self._suppressions_file = self._skia_api.m.path['slave_build'].join(
        'skia', 'tools', 'valgrind.supp')

  def step(self, name, cmd, **kwargs):
    new_cmd = ['valgrind', '--gen-suppressions=all', '--leak-check=full',
               '--track-origins=yes', '--error-exitcode=1', '--num-callers=40',
               '--suppressions=%s' % self._suppressions_file]
    path_to_app = self._skia_api.out_dir.join(
        self._skia_api.configuration, cmd[0])
    new_cmd.append(path_to_app)
    new_cmd.extend(cmd[1:])
    return self._skia_api.run(self._skia_api.m.step, name, cmd=new_cmd,
                              **kwargs)

