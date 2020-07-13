# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS_GIT_FILENAME = '.DEPS.git'

DEPS_FILENAME = 'DEPS'

GCLIENT_CUSTOM_DEPS_V8 = {
    'src/v8_bleeding_edge': 'https://chromium.googlesource.com/v8/v8.git'
}

DEPOT_DEPS_NAME = {
    'chromium': {
        'src': 'src',
        'recurse': True,
        'from': ['android-chrome'],
        'viewvc': 'https://chromium.googlesource.com/chromium/src/+/',
        'deps_var': 'chromium_rev'
    },
    'angle': {
        'src': 'src/third_party/angle',
        'src_old': 'src/third_party/angle_dx11',
        'recurse': True,
        'from': ['chromium'],
        'platform': 'nt',
        'viewvc': 'https://chromium.googlesource.com/angle/angle/+/',
        'deps_var': 'angle_revision'
    },
    'v8': {
        'src': 'src/v8',
        'recurse': True,
        'from': ['chromium'],
        'custom_deps': GCLIENT_CUSTOM_DEPS_V8,
        'viewvc': 'https://chromium.googlesource.com/v8/v8.git/+/',
        'deps_var': 'v8_revision'
    },
    'skia/src': {
        'src': 'src/third_party/skia',
        'recurse': True,
        'from': ['chromium'],
        'viewvc': 'https://chromium.googlesource.com/skia/+/',
        'deps_var': 'skia_revision'
    }
}
