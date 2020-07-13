# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_test_api


class DiskApi(recipe_test_api.RecipeTestApi):
  def space_usage_result(self):
    GiB = 1 << 30
    return {
        'capacity': 100 * GiB,
        'used': 50 * GiB,
    }
