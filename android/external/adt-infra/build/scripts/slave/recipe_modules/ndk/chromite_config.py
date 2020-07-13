# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import DEPS
CONFIG_CTX = DEPS['chromite'].CONFIG_CTX


@CONFIG_CTX(includes=['base'])
def chromite_config(c):
  c.cbb.builddir = 'android'
  c.cbb.config_repo = 'https://android.googlesource.com/toolchain/ndk_chromite_config'
