# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'gclient',
  'path',
  'platform',
  'properties',
  'python',
  'step',
]


def _CheckoutSteps(api):
  # Checkout pdfium and its dependencies (specified in DEPS) using gclient
  api.gclient.set_config('pdfium')
  api.gclient.checkout()


def _BuildSteps(api):
  # Generate build files for Ninja
  gyp_path = api.path['checkout'].join('build', 'gyp_pdfium.py')
  api.python('gyp_pdfium', gyp_path, env={'GYP_GENERATORS': 'ninja'})

  # Build sample file using Ninja
  debug_path = api.path['checkout'].join('out', 'Debug')
  api.step('compile with ninja', ['ninja', '-C', debug_path])


def _RunTests(api):
  unittests_path = str(api.path['checkout'].join('out', 'Debug',
                                                 'pdfium_unittests'))
  if api.platform.is_win:
    unittests_path += '.exe'
  api.step('unittests', [unittests_path])

  embeddertests_path = str(api.path['checkout'].join('out', 'Debug',
                                                     'pdfium_embeddertests'))
  if api.platform.is_win:
    embeddertests_path += '.exe'
  api.step('embeddertests', [embeddertests_path],
           cwd=api.path['checkout'])

  javascript_path = str(api.path['checkout'].join('testing', 'tools',
                                                  'run_javascript_tests.py'))
  api.python('javascript tests', javascript_path, cwd=api.path['checkout'])

  pixel_tests_path = str(api.path['checkout'].join('testing', 'tools',
                                                   'run_pixel_tests.py'))
  api.python('pixel tests', pixel_tests_path, cwd=api.path['checkout'])

  corpus_tests_path = str(api.path['checkout'].join('testing', 'tools',
                                                    'run_corpus_tests.py'))
  api.python('corpus tests', corpus_tests_path, cwd=api.path['checkout'])

def RunSteps(api):
  _CheckoutSteps(api)
  _BuildSteps(api)
  _RunTests(api)


def GenTests(api):
  yield api.test('win') + api.platform('win', 64)
  yield api.test('linux') + api.platform('linux', 64)
