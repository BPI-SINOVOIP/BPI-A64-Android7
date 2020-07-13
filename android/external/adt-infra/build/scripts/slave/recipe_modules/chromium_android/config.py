# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import types

from recipe_engine.config import config_item_context, ConfigGroup
from recipe_engine.config import ConfigList, Dict, List, Single, Static
from recipe_engine.config_types import Path

def BaseConfig(INTERNAL=False, REPO_NAME=None, REPO_URL=None,
               BUILD_CONFIG='Debug', REVISION='', **_kwargs):
  return ConfigGroup(
    INTERNAL = Static(INTERNAL),
    REPO_NAME = Static(REPO_NAME),
    REPO_URL = Static(REPO_URL),
    BUILD_CONFIG = Static(BUILD_CONFIG),
    revision = Single(basestring, empty_val=REVISION),
    revisions = Dict(value_type=(basestring, types.NoneType)),
    asan_symbolize = Single(bool, required=False, empty_val=False),
    get_app_manifest_vars = Single(bool, required=False, empty_val=True),
    run_tree_truth = Single(bool, required=False, empty_val=True),
    deps_file = Single(basestring, required=False, empty_val='.DEPS.git'),
    internal_dir_name = Single(basestring, required=False),
    # deps_dir: where to checkout the gclient deps file
    deps_dir = Single(basestring, required=False, empty_val=REPO_NAME),
    managed = Single(bool, required=False, empty_val=True),
    extra_deploy_opts = List(inner_type=basestring),
    tests = List(inner_type=basestring),
    cr_build_android = Static(Path('[CHECKOUT]', 'build', 'android')),
    test_runner = Single(Path),
    gclient_custom_deps = Dict(value_type=(basestring, types.NoneType)),
    channel = Single(basestring, empty_val='chrome'),
    gclient_custom_vars = Dict(value_type=(basestring, types.NoneType)),
    coverage = Single(bool, required=False, empty_val=False),
    chrome_specific_wipe = Single(bool, required=False, empty_val=False),
    incremental_coverage = Single(bool, required=False, empty_val=False),
    env = ConfigGroup(
      LLVM_FORCE_HEAD_REVISION = Single(basestring, required=False),
    ),
  )


config_ctx = config_item_context(BaseConfig)

@config_ctx(is_root=True)
def base_config(c):
  c.internal_dir_name = 'clank'
  c.test_runner = Path('[CHECKOUT]', 'build', 'android', 'test_runner.py')

@config_ctx()
def main_builder(c):
  pass

@config_ctx()
def clang_builder(c):
  pass

@config_ctx(config_vars={'BUILD_CONFIG': 'Release'})
def clang_asan_tot_release_builder(c):  # pragma: no cover
  c.asan_symbolize = True
  c.env.LLVM_FORCE_HEAD_REVISION = 'YES'

@config_ctx()
def component_builder(c):
  pass

@config_ctx()
def x86_base(c):
  pass

@config_ctx(includes=['x86_base'])
def x86_builder(c):
  pass

@config_ctx()
def mipsel_base(c):
  pass

@config_ctx(includes=['mipsel_base'])
def mipsel_builder(c):
  pass

@config_ctx(includes=['main_builder'])
def dartium_builder(c):  # pragma: no cover
  c.get_app_manifest_vars = False
  c.run_tree_truth = False
  if c.deps_dir != 'src/dart':
    c.deps_file = 'DEPS'
  else:
    c.deps_file = 'tools/deps/dartium.deps/DEPS'
  c.managed = True

@config_ctx()
def arm_l_builder(c):  # pragma: no cover
  pass

@config_ctx()
def arm_l_builder_lto(c):  # pragma: no cover
  pass

@config_ctx()
def arm_l_builder_rel(c):  # pragma: no cover
  pass

@config_ctx()
def x64_base(c):
  pass

@config_ctx(includes=['x64_base'])
def x64_builder(c):
  pass

@config_ctx()
def arm64_builder(c):
  pass

@config_ctx()
def arm64_builder_rel(c):  # pragma: no cover
  pass

@config_ctx()
def try_base(c):
  pass  # pragma: no cover

@config_ctx(includes=['try_base'])
def try_builder(c):
  pass  # pragma: no cover

@config_ctx(includes=['x86_builder', 'try_builder'])
def x86_try_builder(c):
  pass  # pragma: no cover

@config_ctx()
def tests_base(c):  # pragma: no cover
  pass

@config_ctx(includes=['arm64_builder_rel'])
def tests_arm64(c):  # pragma: no cover
  pass

@config_ctx(includes=['x64_builder'])
def tests_x64(c):  # pragma: no cover
  pass

@config_ctx(includes=['tests_base'])
def instrumentation_tests(c):  # pragma: no cover
  c.tests.append('smoke_instrumentation_tests')
  c.tests.append('small_instrumentation_tests')
  c.tests.append('medium_instrumentation_tests')
  c.tests.append('large_instrumentation_tests')

@config_ctx(includes=['instrumentation_tests'])
def main_tests(c):
  pass  # pragma: no cover

@config_ctx(includes=['tests_base'])
def clang_tests(c):  # pragma: no cover
  c.tests.append('smoke_instrumentation_tests')
  c.asan_symbolize = True

@config_ctx(includes=['tests_base'])
def enormous_tests(c):  # pragma: no cover
  c.extra_deploy_opts = ['--await-internet']
  c.tests.append('enormous_instrumentation_tests')

@config_ctx(includes=['try_base', 'instrumentation_tests'])
def try_instrumentation_tests(c):
  pass  # pragma: no cover

@config_ctx(includes=['x86_base', 'try_base', 'instrumentation_tests'])
def x86_try_instrumentation_tests(c):
  c.extra_deploy_opts.append('--non-rooted')  # pragma: no cover

@config_ctx(includes=['main_builder'])
def coverage_builder_tests(c):  # pragma: no cover
  pass

@config_ctx(includes=['main_builder'])
def non_device_wipe_provisioning(c):
  c.chrome_specific_wipe = True

@config_ctx(includes=['main_builder'])
def incremental_coverage_builder_tests(c):
  c.incremental_coverage = True

@config_ctx(includes=['component_builder'])
def oilpan_builder(c):
  pass

@config_ctx()
def perf(c):
  pass

@config_ctx()
def webview_perf(c):
  pass

@config_ctx()
def cast_builder(c):
  pass
