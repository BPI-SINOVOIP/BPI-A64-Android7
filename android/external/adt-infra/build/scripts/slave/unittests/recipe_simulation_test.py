#!/usr/bin/env python
# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import shutil
import sys

import test_env  # pylint: disable=W0403,W0611

# Delete the old recipe_engine directory which might have stale pyc files
# that will mess us up.
shutil.rmtree(os.path.join(
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.realpath(__file__))))),
        'third_party', 'recipe_engine'),
    ignore_errors=True)

RECIPES_PY = os.path.join(
    os.path.dirname(os.path.dirname(os.path.realpath(__file__))),
    'recipes.py')

args = [sys.argv[0], 'simulation_test'] + sys.argv[1:]
os.execvp(RECIPES_PY, args)
