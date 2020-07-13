# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import DEPS
CONFIG_CTX = DEPS['chromium'].CONFIG_CTX

from recipe_engine.config_types import Path


SYZYGY_SLN = Path('[CHECKOUT]', 'syzygy', 'syzygy.sln')


# The common bits of configuration shared across all valid Syzygy
# configurations. This is included in _syzygy_msvs and _syzygy_ninja,
# exactly one of which is included in each valid usable configuration.
@CONFIG_CTX(includes=['msvs2013'])
def _syzygy_base(c):
  c.project_generator.tool = 'gyp'

  # We don't use a component build, so remove the GYP define.
  c.gyp_env.GYP_DEFINES.pop('component', None)


@CONFIG_CTX(includes=['msvs', '_syzygy_base'])
def _syzygy_msvs(dummy_c):
  pass


@CONFIG_CTX(includes=['ninja', '_syzygy_base'])
def _syzygy_ninja(c):
  # Generate MSVS projects as well for ease of debugging on the bot.
  c.gyp_env.GYP_GENERATORS.add('ninja')
  c.gyp_env.GYP_GENERATORS.add('msvs-ninja')
  # Inject a Ninja no-op build confirmation step.
  c.compile_py.ninja_confirm_noop = True


# The common bits of configuration shared by continuous builder
# configurations: syzygy, syzygy_msvs.
@CONFIG_CTX()
def _syzygy_continuous(c):
  assert 'official_build' not in c.gyp_env.GYP_DEFINES
  c.compile_py.default_targets.clear()
  c.compile_py.default_targets.add('build_all')


# Configuration to be used by continuous builders: Debug, Release and Coverage.
@CONFIG_CTX(includes=['_syzygy_ninja', '_syzygy_continuous'])
def syzygy(dummy_c):
  pass


# Configuration to be used by continuous builders: Debug, Release and Coverage.
# Currently this is only used by the Debug builder to ensure at least one bot
# continues to build with MSVS.
@CONFIG_CTX(includes=['_syzygy_msvs', '_syzygy_continuous'])
def syzygy_msvs(c):
  c.compile_py.solution = SYZYGY_SLN


@CONFIG_CTX(includes=['_syzygy_ninja'],
            config_vars={'BUILD_CONFIG': 'Release'})
def syzygy_official(c):
  c.compile_py.clobber = True
  c.compile_py.default_targets.clear()
  c.compile_py.default_targets.add('official_build')
  c.gyp_env.GYP_DEFINES['official_build'] = 1


@CONFIG_CTX(includes=['_syzygy_ninja'],
            config_vars={'BUILD_CONFIG': 'Release'})
def kasko_official(c):
  c.compile_py.clobber = True
  c.compile_py.default_targets.clear()
  c.compile_py.default_targets.add('official_kasko_build')
  c.gyp_env.GYP_DEFINES['official_build'] = 1
