# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_test_api


class ChromiumTestApi(recipe_test_api.RecipeTestApi):
  def gen_tests_for_builders(self, builder_dict, overrides=None):
    # TODO: crbug.com/354674. Figure out where to put "simulation"
    # tests. Is this really the right place?

    def _sanitize_nonalpha(text):
      return ''.join(c if c.isalnum() else '_' for c in text)

    overrides = overrides or {}
    for mastername in builder_dict:
      for buildername in builder_dict[mastername]['builders']:
        if 'mac' in buildername or 'Mac' in buildername:
          platform_name = 'mac'
        elif 'win' in buildername or 'Win' in buildername:
          platform_name = 'win'
        else:
          platform_name = 'linux'
        test = (
            self.test('full_%s_%s' % (_sanitize_nonalpha(mastername),
                                      _sanitize_nonalpha(buildername))) +
            self.m.platform.name(platform_name)
        )
        if mastername.startswith('tryserver'):
          test += self.m.properties.tryserver(buildername=buildername,
                                              mastername=mastername)
        else:
          test += self.m.properties.generic(buildername=buildername,
                                            mastername=mastername)

        override_step_data = overrides.get(mastername, {}).get(buildername,
                                                               None)
        if override_step_data:
            test += override_step_data
        yield test
