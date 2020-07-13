# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Runs a pipeline to detect suspicious commits in Chromium."""


DEPS = [
  'bot_update',
  'gclient',
  'gsutil',
  'path',
  'properties',
  'python',
]


def RunSteps(api):
  api.gclient.set_config('infra_with_chromium')
  api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()
  dirname = api.path.mkdtemp('antibody').join('antibody')

  # project name, checkout path, database name
  repos = [
    ['chromium', api.m.path['slave_build'].join('src'), 'CHROMIUM_DB'],
    ['infra', api.m.path['slave_build'].join('infra'), 'INFRA_DB'],
  ]

  repo_list = [repo_name for repo_name, _, _ in repos]
  for _, checkout_path, database_name in repos:
    cmd = ['infra.tools.antibody']
    cmd.extend(['--sql-password-file', '/home/chrome-bot/.antibody_password'])
    cmd.extend(['--git-checkout-path', checkout_path])
    cmd.extend(['--output-dir-path', dirname])
    cmd.extend(['--since', '2015-01-01'])
    cmd.extend(['--database', database_name])
    cmd.extend(['--repo-list'] + repo_list)
    cmd.extend(['--run-antibody'])
    cmd.extend(['--logs-debug'])

    api.python('Antibody', 'run.py', cmd,
               cwd=api.m.path['slave_build'].join('infra'))
  api.gsutil(['-m', 'cp', '-r', '-a', 'public-read', dirname, 'gs://antibody/'])


def GenTests(api):
  yield (api.test('antibody') +
         api.properties(mastername='chromium.infra.cron',
                        buildername='antibody',
                        slavename='fake-slave'))
