# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import DEPS
CONFIG_CTX = DEPS['gclient'].CONFIG_CTX
ChromiumGitURL = DEPS['gclient'].config.ChromiumGitURL


@CONFIG_CTX()
def libyuv(c):
  s = c.solutions.add()
  s.name = 'src'
  s.url = ChromiumGitURL(c, 'libyuv', 'libyuv')
  s.deps_file = 'DEPS'
  s.custom_vars['root_dir'] = 'src'

@CONFIG_CTX(includes=['libyuv', 'android'])
def libyuv_android(c):
  pass

@CONFIG_CTX(includes=['libyuv'])
def libyuv_ios(c):
  c.target_os.add('mac')
  c.target_os.add('ios')

@CONFIG_CTX(includes=['libyuv'])
def libyuv_valgrind(c):
  c.solutions[0].custom_deps['src/chromium/src/third_party/valgrind'] = \
      ChromiumGitURL(c, 'chromium', 'deps', 'valgrind', 'binaries')
