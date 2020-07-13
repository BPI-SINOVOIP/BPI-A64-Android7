# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import types

from recipe_engine.config import config_item_context, ConfigGroup, Single
from recipe_engine.config_types import Path
from . import api as syzygy_api


def BaseConfig(**dummy_kwargs):
  return ConfigGroup(
    official_build = Single(bool, empty_val=False, required=False),
    unittests_gypi = Single(Path, required=False),
    version_file = Single(Path, required=False),
  )


config_ctx = config_item_context(BaseConfig)


@config_ctx(is_root=True)
def BASE(c):
  pass


@config_ctx()
def syzygy(c):
  c.official_build = False
  c.unittests_gypi = Path('[CHECKOUT]', 'syzygy', 'unittests.gypi')
  c.version_file = Path('[CHECKOUT]', 'syzygy', 'SYZYGY_VERSION')


@config_ctx(includes=['syzygy'])
def syzygy_msvs(dummy_c):
  pass


@config_ctx()
def syzygy_official(c):
  c.official_build = True
  c.unittests_gypi = Path('[CHECKOUT]', 'syzygy', 'unittests.gypi')
  c.version_file = Path('[CHECKOUT]', 'syzygy', 'SYZYGY_VERSION')


@config_ctx()
def kasko_official(c):
  c.official_build = True
  c.unittests_gypi = Path('[CHECKOUT]', 'syzygy', 'kasko', 'unittests.gypi')
  c.version_file = Path('[CHECKOUT]', 'syzygy', 'kasko', 'VERSION')
