# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'ios',
  'json',
  'platform',
  'properties',
]

def RunSteps(api):
  api.ios.checkout()
  api.ios.read_build_config()
  api.ios.build()
  api.ios.test()

def GenTests(api):
  yield (
    api.test('basic')
    + api.platform('mac', 64)
    + api.properties(
      buildername='ios',
      buildnumber='0',
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': '6.1.1',
      'GYP_DEFINES': {
      },
      'compiler': 'xcodebuild',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator8.1',
      'tests': [
        {
          'app': 'fake test 1',
          'device type': 'fake device 1',
          'os': '8.1',
        },
        {
          'include': 'fake include.json',
          'device type': 'fake device 1',
          'os': '8.1',
        },
        {
          'app': 'fake test 2',
          'device type': 'fake device 2',
          'os': '7.1',
        },
        {
          'include': 'fake include.json',
          'device type': 'fake device 2',
          'os': '7.1',
        },
      ],
    })
  )

  yield (
    api.test('clobber')
    + api.platform('mac', 64)
    + api.properties(
      buildername='ios',
      buildnumber='0',
      clobber=True,
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': '6.1.1',
      'GYP_DEFINES': {
      },
      'compiler': 'xcodebuild',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator8.1',
      'tests': [
      ],
    })
  )
