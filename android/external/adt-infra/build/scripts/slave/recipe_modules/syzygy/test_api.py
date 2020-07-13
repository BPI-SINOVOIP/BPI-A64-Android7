# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_test_api

# pylint: disable=W0232
class SyzygyTestApi(recipe_test_api.RecipeTestApi):
  @staticmethod
  def sanitize_non_alpha(text):
    """Sanitizes non-alphanumeric characters in a string, replacing with _."""
    return ''.join(c if c.isalnum() else '_' for c in text)

  @staticmethod
  def test_properties():
    """Returns a set of common properties for use in tests."""
    return {'mastername': 'master.client.syzygy',
            # A known good revision that builds and passes all unittests.
            'revision': '0e9f25b1098271be2b096fd1c095d6d907cf86f7',
            'slavename': 'vm331-m3'}

  def generate_test(self, api, buildername, slavename=None):
    """Returns a test object for the given builder."""
    props = self.test_properties()
    mastername = props['mastername']
    props['buildername'] = buildername
    if slavename:
      props['slavename'] = slavename
    test = (
        api.test('full_%s_%s' % (self.sanitize_non_alpha(mastername),
                                 self.sanitize_non_alpha(buildername))) +
        api.properties.generic(**props) +
        api.platform('win', 32)
    )
    return test
