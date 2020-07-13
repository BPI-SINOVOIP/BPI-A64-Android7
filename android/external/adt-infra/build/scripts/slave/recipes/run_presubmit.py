# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'gclient',
  'git',
  'json',
  'path',
  'properties',
  'python',
  'tryserver',
  'rietveld',
  'v8',
  'webrtc',
]


def _RunStepsInternal(api):
  root = api.rietveld.calculate_issue_root(extra_patch_project_roots={'v8': []})

  repo_name = api.properties['repo_name']
  codereview_auth = api.properties.get('codereview_auth', False)
  force_checkout = api.properties.get('force_checkout', False)

  api.gclient.set_config(repo_name)

  bot_update_step = api.bot_update.ensure_checkout(
      force=force_checkout, patch_project_roots={'v8': []},
      patch_oauth2=codereview_auth)
  relative_root = '%s/%s' % (api.gclient.c.solutions[0].name, root)
  relative_root = relative_root.strip('/')
  got_revision_property = api.gclient.c.got_revision_mapping[relative_root]
  upstream = bot_update_step.json.output['properties'].get(
      got_revision_property)
  if (not upstream or
      isinstance(upstream, int) or
      (upstream.isdigit() and len(upstream) < 40)):
    # If got_revision is an svn revision, then use got_revision_git.
    upstream = bot_update_step.json.output['properties'].get(
        '%s_git' % got_revision_property) or ''

  # TODO(hinoka): Extract email/name from issue?
  api.git('-c', 'user.email=commit-bot@chromium.org',
          '-c', 'user.name=The Commit Bot',
          'commit', '-a', '-m', 'Committed patch',
          name='commit git patch', cwd=api.path['checkout'].join(root))

  if api.properties.get('runhooks'):
    api.gclient.runhooks()

  presubmit_args = [
    '--root', api.path['checkout'].join(root),
    '--commit',
    '--verbose', '--verbose',
    '--issue', api.properties['issue'],
    '--patchset', api.properties['patchset'],
    '--skip_canned', 'CheckRietveldTryJobExecution',
    '--skip_canned', 'CheckTreeIsOpen',
    '--skip_canned', 'CheckBuildbotPendingBuilds',
    '--rietveld_url', api.properties['rietveld'],
    '--rietveld_fetch',
    '--upstream', upstream,  # '' if not in bot_update mode.
    '--trybot-json', api.json.output(),
  ]

  if codereview_auth:
    presubmit_args.extend([
        '--rietveld_email_file',
        api.path['build'].join('site_config', '.rietveld_client_email')])
    presubmit_args.extend([
        '--rietveld_private_key_file',
        api.path['build'].join('site_config', '.rietveld_secret_key')])
  else:
    presubmit_args.extend(['--rietveld_email', ''])  # activate anonymous mode

  env = {}
  if repo_name in ['build', 'build_internal', 'build_internal_scripts_slave']:
    # This should overwrite the existing pythonpath which includes references to
    # the local build checkout (but the presubmit scripts should only pick up
    # the scripts from presubmit_build checkout).
    env['PYTHONPATH'] = ''

  api.python('presubmit', api.path['depot_tools'].join('presubmit_support.py'),
             presubmit_args, env=env)


def RunSteps(api):
  with api.tryserver.set_failure_hash():
    return _RunStepsInternal(api)


def GenTests(api):
  # TODO(machenbach): This uses the same tryserver for all repos, which doesn't
  # reflect reality (cosmetical problem only).
  for repo_name in ['chromium', 'v8', 'nacl', 'naclports', 'gyp',
                    'build', 'build_internal', 'build_internal_scripts_slave',
                    'depot_tools', 'skia', 'chrome_golo', 'webrtc', 'catapult']:
    yield (
      api.test(repo_name) +
      api.properties.tryserver(
          mastername='tryserver.chromium.linux',
          buildername='%s_presubmit' % repo_name,
          repo_name=repo_name,
          patch_project=repo_name) +
      api.step_data('presubmit', api.json.output([['%s_presubmit' % repo_name,
                                                   ['compile']]]))
    )

  yield (
    api.test('fake_svn_master') +
    api.properties.tryserver(
        mastername='experimental.svn',
        buildername='chromium_presubmit',
        repo_name='chromium',
        force_checkout=True) +
    api.step_data('presubmit', api.json.output([['chromium_presubmit',
                                                 ['compile']]]))
  )

  yield (
    api.test('chromium_with_auth') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='chromium_presubmit',
        repo_name='chromium',
        codereview_auth=True,
        patch_project='chromium') +
    api.step_data('presubmit', api.json.output([['chromium_presubmit',
                                                 ['compile']]]))
  )

  yield (
    api.test('infra_with_runhooks') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_presubmit',
        repo_name='infra',
        patch_project='infra',
        runhooks=True) +
    api.step_data('presubmit', api.json.output([['infra_presubmit',
                                                 ['compile']]]))
  )

  yield (
    api.test('recipes-py') +
    api.properties.tryserver(
        mastername='tryserver.infra',
        buildername='infra_presubmit',
        repo_name='recipes_py',
        patch_project='recipes-py',
        runhooks=True) +
    api.step_data('presubmit', api.json.output([['infra_presubmit',
                                                 ['compile']]]))
  )
