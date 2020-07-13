# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'step',
  'url',
]

def RunSteps(api):
  api.step('step1',
           ['/bin/echo', api.url.join('foo', 'bar', 'baz')])
  api.step('step2',
           ['/bin/echo', api.url.join('foo/', '/bar/', '/baz')])
  api.step('step3',
           ['/bin/echo', api.url.join('//foo/', '//bar//', '//baz//')])
  api.step('step4',
           ['/bin/echo', api.url.join('//foo/bar//', '//baz//')])
  api.url.fetch('fake://foo/bar', attempts=5)

def GenTests(api):
  yield api.test('basic')
