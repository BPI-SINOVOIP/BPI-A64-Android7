# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.config import BadConf
from recipe_engine.config_types import Path

import DEPS
CONFIG_CTX = DEPS['chromium'].CONFIG_CTX


SUPPORTED_TARGET_ARCHS = ('intel', 'arm')


@CONFIG_CTX(includes=['chromium', 'dcheck'])
def webrtc_standalone(c):
  _compiler_defaults(c)

  c.runtests.memory_tests_runner = Path('[CHECKOUT]', 'tools',
                                        'valgrind-webrtc', 'webrtc_tests',
                                        platform_ext={'win': '.bat',
                                                      'mac': '.sh',
                                                      'linux': '.sh'})

@CONFIG_CTX(includes=['chromium_clang', 'dcheck'])
def webrtc_clang(c):
  _compiler_defaults(c)

@CONFIG_CTX(includes=['chromium', 'dcheck', 'static_library'])
def webrtc_ios(c):
  if c.HOST_PLATFORM != 'mac':
    raise BadConf('Only "mac" host platform is supported for iOS (got: "%s")' %
                  c.HOST_PLATFORM)  # pragma: no cover
  if c.TARGET_PLATFORM != 'ios':
    raise BadConf('Only "ios" target platform is supported (got: "%s")' %
                  c.TARGET_PLATFORM)  # pragma: no cover
  if c.TARGET_ARCH == 'arm':
    c.build_config_fs = c.BUILD_CONFIG + '-iphoneos'
  if c.TARGET_ARCH == 'intel':
    c.build_config_fs = c.BUILD_CONFIG + '-iphonesimulator'

  gyp_defs = c.gyp_env.GYP_DEFINES
  gyp_defs['build_with_libjingle'] = 1
  gyp_defs['chromium_ios_signing'] = 0
  gyp_defs['key_id'] = ''
  gyp_defs['OS'] = c.TARGET_PLATFORM
  _compiler_defaults(c)

@CONFIG_CTX(includes=['gn'])
def webrtc_gn(c):
  c.compile_py.default_targets = ['all']
  c.project_generator.args = ['build_with_chromium=false']

def _compiler_defaults(c):
  c.compile_py.default_targets = ['All']
  if c.TARGET_PLATFORM in ('mac', 'ios'):
    c.gyp_env.GYP_DEFINES['mac_sdk'] = '10.9'
