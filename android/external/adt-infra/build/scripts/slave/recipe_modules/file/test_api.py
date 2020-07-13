# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os

from recipe_engine import recipe_test_api


# We want to be able to really read a file during a recipe test. Of course,
# we want to prevent recipe authors from reading files that only exist on
# their own local disk (e.g. any file in some other repository), so that a
# test only reads files that are guaranteed to be available wherever the test
# is run (and by whomever runs it).
# Therefore we only allow a real file to be read when it's in the same repo
# as the recipe.
# In order to enforce this, we accept a relative path and join it to the
# location of build... determined by the relative location of this file in
# the build repo on the user's local disk.
# This script currently lives at scripts/slave/recipe_modules/file/test_api.py.
# If you ever change that, you must update the path below.
_ROOT = os.path.abspath(os.path.join(
  os.path.dirname(__file__),
  '..',
  '..',
  '..',
  '..',
  '..',
))
BUILD = os.path.join(_ROOT, 'build')
BUILD_INTERNAL = os.path.join(_ROOT, 'build_internal')

class FileTestApi(recipe_test_api.RecipeTestApi):
  def listdir(self, files):
    def listdir_callback():
      return self.m.json.output(files)
    return listdir_callback
