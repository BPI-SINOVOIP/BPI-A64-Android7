# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'ios',
  'platform',
  'properties',
]

def RunSteps(api):
  api.ios.host_info()
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
      'xcode version': 'fake xcode version',
      'GYP_DEFINES': {
        'fake gyp define 1': 'fake value 1',
        'fake gyp define 2': 'fake value 2',
      },
      'env': {
        'fake env var 1': 'fake env value 1',
        'fake env var 2': 'fake env value 2',
      },
      'compiler': 'xcodebuild',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator8.0',
      'tests': [
        {
          'app': 'fake tests 1',
          'device type': 'fake device',
          'os': '8.0',
        },
        {
          'app': 'fake tests 2',
          'device type': 'fake device',
          'os': '7.1',
        },
      ],
    })
  )

  yield (
    api.test('no_tests')
    + api.platform('mac', 64)
    + api.properties(
      buildername='ios',
      buildnumber='0',
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': 'fake xcode version',
      'GYP_DEFINES': {
        'fake gyp define 1': 'fake value 1',
        'fake gyp define 2': 'fake value 2',
      },
      'compiler': 'ninja',
      'configuration': 'Release',
      'sdk': 'iphoneos8.0',
      'tests': [
      ],
    })
  )

  yield (
    api.test('test_failure')
    + api.platform('mac', 64)
    + api.properties(patch_url='patch url')
    + api.properties(
      buildername='ios',
      buildnumber='0',
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': 'fake xcode version',
      'GYP_DEFINES': {
        'fake gyp define 1': 'fake value 1',
        'fake gyp define 2': 'fake value 2',
      },
      'compiler': 'xcodebuild',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator8.0',
      'tests': [
        {
          'app': 'fake tests 1',
          'device type': 'fake device',
          'os': '8.0',
        },
        {
          'app': 'fake tests 2',
          'device type': 'fake device',
          'os': '7.1',
        },
      ],
    })
    + api.step_data(
      'fake tests 1 (fake device iOS 8.0)',
      retcode=1
    )
  )

  yield (
    api.test('infrastructure_failure')
    + api.platform('mac', 64)
    + api.properties(
      buildername='ios',
      buildnumber='0',
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': 'fake xcode version',
      'GYP_DEFINES': {
        'fake gyp define 1': 'fake value 1',
        'fake gyp define 2': 'fake value 2',
      },
      'compiler': 'ninja',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator8.0',
      'tests': [
        {
          'app': 'fake tests 1',
          'device type': 'fake device',
          'os': '8.0',
        },
        {
          'app': 'fake tests 2',
          'device type': 'fake device',
          'os': '7.1',
        },
      ],
    })
    + api.step_data(
      'fake tests 1 (fake device iOS 8.0)',
      retcode=2,
    )
  )

  yield (
    api.test('multiple_failures')
    + api.platform('mac', 64)
    + api.properties(
      buildername='ios',
      buildnumber='0',
      mastername='chromium.fake',
      slavename='fake-vm',
    )
    + api.ios.make_test_build_config({
      'xcode version': 'fake xcode version',
      'GYP_DEFINES': {
        'fake gyp define 1': 'fake value 1',
        'fake gyp define 2': 'fake value 2',
      },
      'compiler': 'xcodebuild',
      'configuration': 'Debug',
      'sdk': 'iphonesimulator7.1',
      'tests': [
        {
          'app': 'fake tests 1',
          'device type': 'fake device',
          'os': '8.1',
        },
        {
          'app': 'fake tests 2',
          'device type': 'fake device',
          'os': '8.1',
        },
        {
          'app': 'fake tests 3',
          'device type': 'fake device',
          'os': '8.1',
        },
        {
          'app': 'fake tests 4',
          'device type': 'fake device',
          'os': '8.1',
        },
        {
          'app': 'fake tests 5',
          'device type': 'fake device',
          'os': '8.1',
        },
        {
          'app': 'fake tests 6',
          'device type': 'fake device',
          'os': '8.1',
        },
      ],
    })
    + api.step_data(
      'fake tests 2 (fake device iOS 8.1)',
      retcode=1
    )
    + api.step_data(
      'fake tests 3 (fake device iOS 8.1)',
      retcode=1
    )
    + api.step_data(
      'fake tests 5 (fake device iOS 8.1)',
      retcode=2
    )
  )
