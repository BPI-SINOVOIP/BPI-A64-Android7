# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'isolate',
  'json',
  'path',
  'platform',
  'properties',
  'python',
  'raw_io',
  'step',
  'swarming_client',
  'test_utils',
]

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'show_shards_in_collect_step': Property(default=False, kind=bool),
  'show_isolated_out_in_collect_step': Property(default=True, kind=bool),
}
