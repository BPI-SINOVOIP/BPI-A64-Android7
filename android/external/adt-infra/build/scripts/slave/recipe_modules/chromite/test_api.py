# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import hashlib

from recipe_engine import recipe_test_api

DEPS = [
    'gitiles',
]

class ChromiteTestApi(recipe_test_api.RecipeTestApi):
  def seed_chromite_config(self, data, branch='master'):
    """Seeds step data for the Chromite configuration fetch.
    """
    return self.m.step.step_data('read chromite config',
        self.m.json.output(data))
