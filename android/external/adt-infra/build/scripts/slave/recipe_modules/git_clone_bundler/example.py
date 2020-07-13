# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
    'git_clone_bundler',
    'path',
    'properties',
    'raw_io',
]


REPO_LIST_OUTPUT = """\
path/to/foo : src/foo
path/to/bar : src/bar
"""

REPO_LIST_OUTPUT_DUP = (REPO_LIST_OUTPUT + """
path/to/bar-v10 : src/bar
""")


def RunSteps(api):
  if api.properties.get('repo_manifest_url'):
    # Create a bundle from 'repo'.
    api.git_clone_bundler.create_repo(
        api.properties.get('repo_manifest_url'),
        'clone-dot-bundle-bucket',
        remote_name='origin',
        gs_subpath='checkout/repository')
  else:
    # Create a bundle.
    api.git_clone_bundler.create(
        api.path['slave_build'].join('checkout'),
        'clone-dot-bundle-bucket',
        gs_subpath='checkout/repository')


def GenTests(api):
  yield (api.test('basic'))

  yield (api.test('repo') +
         api.properties(
             repo_manifest_url='https://googlesource.com/manifest.xml') +
         api.step_data('repo list',
                       api.raw_io.stream_output(REPO_LIST_OUTPUT)) +
         api.step_data('lookup Git remote (src/foo)',
                       api.raw_io.stream_output('https://localhost/foo.git')) +
         api.step_data('lookup Git remote (src/bar)',
                       api.raw_io.stream_output('https://localhost/bar.git')))

  yield (api.test('repo_with_duplicate') +
         api.properties(
             repo_manifest_url='https://googlesource.com/manifest.xml') +
         api.step_data('repo list',
                       api.raw_io.stream_output(REPO_LIST_OUTPUT_DUP)))

  yield (api.test('repo_with_error') +
         api.properties(
             repo_manifest_url='https://googlesource.com/manifest.xml') +
         api.step_data('repo list',
                       api.raw_io.stream_output(REPO_LIST_OUTPUT)) +
         api.step_data('create bundle (src/foo)', retcode=1))
