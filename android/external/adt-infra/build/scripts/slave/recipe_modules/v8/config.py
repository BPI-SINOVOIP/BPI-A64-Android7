# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.config import config_item_context, ConfigGroup
from recipe_engine.config import List, Single, Static


def BaseConfig(**_kwargs):
  shard_count = _kwargs.get('SHARD_COUNT', 1)
  shard_run = _kwargs.get('SHARD_RUN', 1)
  assert shard_count >= 1
  assert shard_run >= 1
  assert shard_run <= shard_count

  return ConfigGroup(
    gyp_env = ConfigGroup(
      AR = Single(basestring, required=False),
      CC = Single(basestring, required=False),
      CXX = Single(basestring, required=False),
      CXX_host = Single(basestring, required=False),
      LINK = Single(basestring, required=False),
      RANLIB = Single(basestring, required=False),
    ),
    mips_cross_compile = Single(bool, empty_val=False, required=False),
    # Test configuration that is the equal for all tests of a builder. It
    # might be refined later in the test runner for distinct tests.
    testing = ConfigGroup(
      test_args = List(basestring),
      may_shard = Single(bool, empty_val=True, required=False),

      SHARD_COUNT = Static(shard_count),
      SHARD_RUN = Static(shard_run),
    ),
  )


config_ctx = config_item_context(BaseConfig)


@config_ctx()
def v8(c):
  # Use the exhaustive set of testing variants by default. It's removed on bots
  # that are too slow for it.
  c.testing.test_args.append('--exhaustive-variants')


@config_ctx()
def arm_hard_float(c):
  c.gyp_env.CXX = '/usr/bin/arm-linux-gnueabihf-g++'
  c.gyp_env.LINK = '/usr/bin/arm-linux-gnueabihf-g++'


@config_ctx()
def code_serializer(c):
  c.testing.test_args.extend(
      ['--extra-flags', '--serialize-toplevel --cache=code'])


@config_ctx()
def deadcode(c):
  c.testing.test_args.append('--extra-flags=--dead-code-elimination')


@config_ctx()
def deopt_fuzz_normal(c):
  c.testing.test_args.append('--coverage=0.4')
  c.testing.test_args.append('--distribution-mode=smooth')


@config_ctx()
def deopt_fuzz_random(c):
  c.testing.test_args.append('--coverage=0.4')
  c.testing.test_args.append('--coverage-lift=50')
  c.testing.test_args.append('--distribution-mode=random')


@config_ctx()
def gc_stress(c):
  c.testing.test_args.append('--gc-stress')


@config_ctx()
def greedy_allocator(c):
  c.testing.test_args.extend(
      ['--extra-flags', '--turbo-verify-allocation --turbo-greedy-regalloc'])


@config_ctx()
def isolates(c):
  c.testing.test_args.append('--isolates')


@config_ctx()
def mips_cross_compile(c):
  c.mips_cross_compile = True


@config_ctx()
def no_i18n(c):
  c.testing.test_args.append('--noi18n')


@config_ctx()
def no_snapshot(c):
  c.testing.test_args.append('--no-snap')


@config_ctx()
def nosse3(c):
  c.testing.test_args.extend(
      ['--extra-flags', '--noenable-sse3 --noenable-avx'])


@config_ctx()
def nosse4(c):
  c.testing.test_args.extend(
      ['--extra-flags', '--noenable-sse4-1 --noenable-avx'])


@config_ctx()
def no_harness(c):
  c.testing.test_args.append('--no-harness')


@config_ctx()
def no_exhaustive_variants(c):
  test_args = list(c.testing.test_args)
  test_args.remove('--exhaustive-variants')
  c.testing.test_args = test_args


@config_ctx(includes=['no_exhaustive_variants'])
def no_variants(c):
  c.testing.test_args.append('--no-variants')


@config_ctx(includes=['no_exhaustive_variants'])
def turbo_variant(c):
  c.testing.test_args.append('--variants=turbofan')


@config_ctx()
def novfp3(c):
  c.testing.test_args.append('--novfp3')


@config_ctx()
def predictable(c):
  c.testing.test_args.append('--predictable')


@config_ctx()
def vector_stores(c):
  c.testing.test_args.extend(['--extra-flags', '--vector-stores'])
