# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from copy import deepcopy
from recipe_engine import recipe_test_api

class iOSTestApi(recipe_test_api.RecipeTestApi):
  @recipe_test_api.mod_test_data
  @staticmethod
  def build_config(config):
    return deepcopy(config)

  def make_test_build_config(self, config):
    return self.build_config(config)

  @recipe_test_api.mod_test_data
  @staticmethod
  def parent_build_config(config):
    return deepcopy(config)

  def make_test_build_config_for_parent(self, config):
    return self.parent_build_config(config)

  def host_info(self):
    return self.m.json.output({
      'Mac OS X Version': '1.2.3',
      'Xcode Version': '6.7.8',
      'Xcode Build Version': '5D342509a',
      'Xcode SDKs': [
        'fake sdk 1.0',
        'fake sdk 1.1',
        'fake sdk 2.0',
      ],
    })

  def test_results(self):
    return self.m.json.output({
      'links': {
        'fake URL text': 'fake URL',
      },
      'logs': {
        'fake log': [
          'fake log line 1',
          'fake log line 2',
        ],
      }
    })
