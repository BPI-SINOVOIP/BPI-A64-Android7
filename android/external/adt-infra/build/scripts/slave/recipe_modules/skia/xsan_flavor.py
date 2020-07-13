# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Utils for running under *SAN"""


import default_flavor


class XSanFlavorUtils(default_flavor.DefaultFlavorUtils):
  def __init__(self, *args, **kwargs):
    super(XSanFlavorUtils, self).__init__(*args, **kwargs)
    self._sanitizer = {
      # We'd love to just pass 'address,undefined' and get all the checks, but
      # we're not anywhere close to being able to do that.  Instead we start
      # with a set of checks that we know pass or nearly pass.  See here for
      # more information:
      # http://clang.llvm.org/docs/UsersManual.html#controlling-code-generation
      'ASAN': ('address,bool,integer-divide-by-zero,null,object-size,return,'
               'nonnull-attribute,returns-nonnull-attribute,unreachable,'
               'vla-bound'),
      # TSAN and ASAN can't play together, so the TSAN stands alone.
      'TSAN': 'thread',
    }[self._skia_api.builder_cfg['extra_config']]

  def compile(self, target):
    cmd = [self._skia_api.m.path['checkout'].join('tools', 'xsan_build'),
           self._sanitizer, target]
    self._skia_api.run(self._skia_api.m.step, 'build %s' % target, cmd=cmd,
                       cwd=self._skia_api.m.path['checkout'])

  def step(self, name, cmd, env=None, **kwargs):
    """Wrapper for the Step API; runs a step as appropriate for this flavor."""
    skia_dir = self._skia_api.m.path['checkout']
    lsan_suppressions = skia_dir.join('tools', 'lsan.supp')
    tsan_suppressions = skia_dir.join('tools', 'tsan.supp')
    ubsan_suppressions = skia_dir.join('tools', 'ubsan.supp')
    env = dict(env or {})
    env['ASAN_OPTIONS'] = 'symbolize=1 detect_leaks=1'
    env['LSAN_OPTIONS'] = ('symbolize=1 print_suppressions=1 suppressions=%s' %
                           lsan_suppressions)
    env['TSAN_OPTIONS'] = 'suppressions=%s' % tsan_suppressions
    env['UBSAN_OPTIONS'] = 'suppressions=%s' % ubsan_suppressions

    path_to_app = self._skia_api.out_dir.join(
        self._skia_api.configuration, cmd[0])
    new_cmd = [path_to_app]
    new_cmd.extend(cmd[1:])
    return self._skia_api.run(self._skia_api.m.step, name, cmd=new_cmd, env=env,
                              **kwargs)

