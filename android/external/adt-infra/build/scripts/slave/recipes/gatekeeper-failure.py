# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Repeatedly fails as a way to ensure the gatekeeper is alive and well."""

DEPS = [
  'platform',
  'step',
]

def RunSteps(api):
  if api.platform.is_linux:
    api.step('fail', ['false'])

def GenTests(api):
  for plat in ('mac', 'linux', 'win'):
    yield api.test('basic_%s' % plat) + api.platform.name(plat)
