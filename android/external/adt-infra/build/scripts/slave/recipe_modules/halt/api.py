# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be

from recipe_engine import recipe_api

class HaltApi(recipe_api.RecipeApi):

  def __call__(self, reason='Unknown', **kwargs):
    """Return a failing step with the given message."""
    name = kwargs.pop('name', 'Recipe failed.')
    name += ' Reason: ' + reason

    self.m.python.inline(name, "import sys; sys.exit(1)")
