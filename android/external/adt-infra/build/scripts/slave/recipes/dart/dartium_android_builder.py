# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'chromium',
  'chromium_android',
  'gclient',
  'json',
  'path',
  'properties',
  'python',
]

def RunSteps(api):
  recipe_config = 'dartium_builder'
  kwargs = {
    'REPO_URL': api.properties.get('deps_url'),
    'REPO_NAME': api.properties.get('deps_path'),
    'BUILD_CONFIG':  'Release',
    'INTERNAL': False,
  }

  api.chromium_android.configure_from_properties(
    recipe_config, **kwargs)

  revision = api.properties.get('revision', 'HEAD')
  api.chromium_android.c.revision = revision
  api.chromium_android.c.revisions['src/dart'] = revision

  api.chromium_android.init_and_sync()
  # TODO(iannucci): Remove when dartium syncs chromium to >= crrev.com/252649
  api.chromium.runhooks(env={'GYP_CROSSCOMPILE': "1"})
  api.chromium.compile(targets=['content_shell_apk'])

  build_products_dir = \
      api.chromium.c.build_dir.join(api.chromium.c.build_config_fs)
  api.python('dartium_test',
                   api.path['slave_build'].join('src', 'dart', 'tools',
                                                'bots', 'dartium_android.py'),
                   args=['--build-products-dir', build_products_dir])

def GenTests(api):
  yield (
      api.test('dartium_builder_basic') +
      api.properties.generic(
          revision='34567',
          buildername='dartium-builder',
          buildnumber=1337,
          deps_url='https://dart.googlecode.com/svn/trunk/deps/dartium.deps',
          deps_path='dartium.deps'))
  yield (
      api.test('dartium_builder_git') +
      api.properties.generic(
          revision='478695f60c28b5b8a951ce1d4fd60f53d80b21fb',
          buildername='dartium-builder',
          buildnumber=1337,
          deps_url='https://github.com/dart-lang/sdk.git',
          deps_path='src/dart'))
