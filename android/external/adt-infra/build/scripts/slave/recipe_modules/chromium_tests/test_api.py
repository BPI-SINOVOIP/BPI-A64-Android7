# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_test_api

from . import builders


class ChromiumTestsApi(recipe_test_api.RecipeTestApi):
  @property
  def builders(self):
    return builders.BUILDERS

  def platform(self, mastername, buildername):
    bot_config = builders.BUILDERS[mastername]['builders'][buildername]
    # TODO(phajdan.jr): Get the bitness from actual config for that bot.
    return self.m.platform(
        bot_config['testing']['platform'],
        bot_config.get(
            'chromium_config_kwargs', {}).get('TARGET_BITS', 64))
