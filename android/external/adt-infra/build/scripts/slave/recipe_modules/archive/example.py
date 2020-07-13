# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'archive',
  'json',
  'path',
  'platform',
  'properties',
]

TEST_HASH_MAIN='5e3250aadda2b170692f8e762d43b7e8deadbeef'
TEST_COMMIT_POSITON_MAIN='refs/heads/B1@{#123456}'

TEST_HASH_COMPONENT='deadbeefdda2b170692f8e762d43b7e8e7a96686'
TEST_COMMIT_POSITON_COMPONENT='refs/heads/master@{#234}'


def RunSteps(api):
  api.archive.clusterfuzz_archive(
      build_dir=api.path['slave_build'].join('src', 'out', 'Release'),
      update_properties=api.properties.get('update_properties'),
      gs_bucket='chromium',
      gs_acl=api.properties.get('gs_acl', ''),
      archive_prefix='chrome-asan',
      archive_subdir_suffix=api.properties.get('archive_subdir_suffix', ''),
      revision_dir=api.properties.get('revision_dir'),
      primary_project=api.properties.get('primary_project'),
  )


def GenTests(api):
  update_properties = {
    'got_revision': TEST_HASH_MAIN,
    'got_revision_cp': TEST_COMMIT_POSITON_MAIN,
  }
  for platform, build_files in (
        ('win', ['chrome', 'icu.dat', 'lib', 'file.obj']),
        ('mac', ['chrome', 'icu.dat', 'pdfsqueeze']),
        ('linux', ['chrome', 'icu.dat', 'lib.host']),
      ):
    yield (
      api.test('cf_archiving_%s' % platform) +
      api.platform(platform, 64) +
      api.properties(
          update_properties=update_properties,
          gs_acl='public-read',
          archive_subdir_suffix='subdir',
      ) +
      api.override_step_data('listdir build_dir', api.json.output(build_files))
    )

  # An svn project with a separate git property.
  update_properties = {
    'got_revision': '123456',
    'got_revision_git': TEST_HASH_MAIN,
    'got_revision_cp': TEST_COMMIT_POSITON_MAIN,
  }
  yield (
    api.test('cf_archiving_svn_with_git') +
    api.platform('linux', 64) +
    api.properties(update_properties=update_properties) +
    api.override_step_data(
        'listdir build_dir', api.json.output(['chrome']))
  )

  # An svn project without git hash.
  update_properties = {
    'got_revision': '123456',
    'got_revision_cp': TEST_COMMIT_POSITON_MAIN,
  }
  yield (
    api.test('cf_archiving_svn_no_git') +
    api.platform('linux', 64) +
    api.properties(update_properties=update_properties) +
    api.override_step_data(
        'listdir build_dir', api.json.output(['chrome']))
  )

  # A component build with git.
  update_properties = {
    'got_x10_revision': TEST_HASH_COMPONENT,
    'got_x10_revision_cp': TEST_COMMIT_POSITON_COMPONENT,
  }
  yield (
    api.test('cf_archiving_component') +
    api.platform('linux', 64) +
    api.properties(
        update_properties=update_properties,
        revision_dir='x10',
        primary_project='x10',
    ) +
    api.override_step_data(
        'listdir build_dir', api.json.output(['chrome', 'resources']))
  )

  # A component on svn with a separate git property.
  update_properties = {
    'got_x10_revision': '234',
    'got_x10_revision_git': TEST_HASH_COMPONENT,
    'got_x10_revision_cp': TEST_COMMIT_POSITON_COMPONENT,
  }
  yield (
    api.test('cf_archiving_component_svn_with_git') +
    api.platform('linux', 64) +
    api.properties(
        update_properties=update_properties,
        revision_dir='x10',
        primary_project='x10',
    ) +
    api.override_step_data(
        'listdir build_dir', api.json.output(['chrome']))
  )
