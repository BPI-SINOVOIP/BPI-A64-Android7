# Copyright 2015 The Chromium Authors. All rights reserved.
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
]


def _run_presubmit(api, patch_root, bot_update_step):
  upstream = bot_update_step.json.output['properties'].get(
      api.gclient.c.got_revision_mapping[
      'infra/go/src/github.com/luci/luci-go'])
  # The presubmit must be run with proper Go environment.
  # infra/go/env.py takes care of this.
  presubmit_cmd = [
    'python',  # env.py will replace with this its sys.executable.
    api.path['depot_tools'].join('presubmit_support.py'),
    '--root', api.path['slave_build'].join(patch_root),
    '--commit',
    '--verbose', '--verbose',
    '--issue', api.properties['issue'],
    '--patchset', api.properties['patchset'],
    '--skip_canned', 'CheckRietveldTryJobExecution',
    '--skip_canned', 'CheckTreeIsOpen',
    '--skip_canned', 'CheckBuildbotPendingBuilds',
    '--rietveld_url', api.properties['rietveld'],
    '--rietveld_fetch',
    '--upstream', upstream,
    '--trybot-json', api.json.output(),
    '--rietveld_email', ''
  ]
  api.python('presubmit', api.path['checkout'].join('go', 'env.py'),
             presubmit_cmd, env={'PRESUBMIT_BUILDER': '1'})


def _commit_change(api, patch_root):
  api.git('-c', 'user.email=commit-bot@chromium.org',
          '-c', 'user.name=The Commit Bot',
          'commit', '-a', '-m', 'Committed patch',
          name='commit git patch',
          cwd=api.path['slave_build'].join(patch_root))


def RunSteps(api):
  api.gclient.set_config('luci_go')
  # patch_root must match the luci-go repo, not infra checkout.
  for path in api.gclient.c.got_revision_mapping:
    if 'luci-go' in path:
      patch_root = path
      break
  bot_update_step = api.bot_update.ensure_checkout(force=True,
                                                   patch_root=patch_root)

  is_presubmit = 'presubmit' in api.properties.get('buildername', '').lower()
  if is_presubmit:
    _commit_change(api, patch_root)
  api.gclient.runhooks()

  # This downloads the third parties, so that the next step doesn't have junk
  # output in it.
  api.python(
      'go third parties',
      api.path['checkout'].join('go', 'env.py'),
      ['go', 'version'])

  if is_presubmit:
    with api.tryserver.set_failure_hash():
      _run_presubmit(api, patch_root, bot_update_step)
  else:
    api.python(
        'go build',
        api.path['checkout'].join('go', 'env.py'),
        ['go', 'build', 'github.com/luci/luci-go/...'])

    api.python(
        'go test',
        api.path['checkout'].join('go', 'env.py'),
        ['go', 'test', 'github.com/luci/luci-go/...'])


def GenTests(api):
  yield (
    api.test('luci_go') +
    api.properties.git_scheduled(
        buildername='luci-go-linux64',
        buildnumber=123,
        mastername='chromium.infra',
        repository='https://chromium.googlesource.com/external/github.com/luci/luci-go',
    )
  )
  yield (
    api.test('presubmit_try_job') +
    api.properties.tryserver(
        mastername='tryserver.infra',
        buildername='Luci-go Presubmit',
    ) + api.step_data('presubmit', api.json.output([[]]))
  )
