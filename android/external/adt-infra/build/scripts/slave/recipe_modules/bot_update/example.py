# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'gclient',
  'path',
  'properties',
]

def RunSteps(api):
  api.gclient.use_mirror = True

  src_cfg = api.gclient.make_config()
  soln = src_cfg.solutions.add()
  soln.name = 'src'
  soln.url = 'svn://svn.chromium.org/chrome/trunk/src'
  api.gclient.c = src_cfg
  clobber = True if api.properties.get('clobber') else False
  force = True if api.properties.get('force') else False
  output_manifest = api.properties.get('output_manifest', False)
  with_branch_heads = api.properties.get('with_branch_heads', False)
  refs = api.properties.get('refs', [])
  oauth2 = api.properties.get('oauth2', False)
  api.bot_update.ensure_checkout(force=force,
                                 with_branch_heads=with_branch_heads,
                                 output_manifest=output_manifest,
                                 refs=refs, patch_oauth2=oauth2,
                                 clobber=clobber)


def GenTests(api):
  yield api.test('basic') + api.properties(
      mastername='chromium.linux',
      buildername='Linux Builder',
      slavename='totallyaslave-m1',
  )
  yield api.test('basic_with_branch_heads') + api.properties(
      mastername='chromium.linux',
      buildername='Linux Builder',
      slavename='totallyaslave-m1',
      with_branch_heads=True,
  )
  yield api.test('basic_output_manifest') + api.properties(
      mastername='chromium.linux',
      buildername='Linux Builder',
      slavename='totallyaslave-m1',
      output_manifest=True,
  )
  yield api.test('tryjob') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      issue=12345,
      patchset=654321,
      patch_url='http://src.chromium.org/foo/bar'
  )
  yield api.test('trychange') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      refs=['+refs/change/1/2/333'],
  )
  yield api.test('trychange_oauth2') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      oauth2=True,
  )
  yield api.test('tryjob_fail') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      issue=12345,
      patchset=654321,
      patch_url='http://src.chromium.org/foo/bar',
  ) + api.step_data('bot_update', retcode=1)
  yield api.test('tryjob_fail_patch') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      issue=12345,
      patchset=654321,
      patch_url='http://src.chromium.org/foo/bar',
      fail_patch='apply',
  ) + api.step_data('bot_update', retcode=88)
  yield api.test('tryjob_fail_patch_download') + api.properties(
      mastername='tryserver.chromium.linux',
      buildername='linux_rel',
      slavename='totallyaslave-c4',
      issue=12345,
      patchset=654321,
      patch_url='http://src.chromium.org/foo/bar',
      fail_patch='download'
  ) + api.step_data('bot_update', retcode=87)
  yield api.test('forced') + api.properties(
      mastername='experimental',
      buildername='Experimental Builder',
      slavename='somehost',
      force=1
  )
  yield api.test('off') + api.properties(
      mastername='experimental',
      buildername='Experimental Builder',
      slavename='somehost',
  )
  yield api.test('svn_mode') + api.properties(
      mastername='experimental.svn',
      buildername='Experimental SVN Builder',
      slavename='somehost',
      force=1
  )
  yield api.test('clobber') + api.properties(
      mastername='experimental',
      buildername='Experimental Builder',
      slavename='somehost',
      clobber=1
  )
