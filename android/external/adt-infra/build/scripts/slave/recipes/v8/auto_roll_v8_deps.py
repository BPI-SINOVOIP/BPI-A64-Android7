
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze

DEPS = [
  'bot_update',
  'gclient',
  'git',
  'json',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'v8',
]

AUTO_REVIEWERS = [
  'machenbach@chromium.org',
  'vogelheim@chromium.org',
  'hablich@chromium.org',
]
BASE_URL = 'https://chromium.googlesource.com'
V8_REPO = BASE_URL + '/v8/v8'
CR_REPO = BASE_URL + '/chromium/src'
V8_DEPS_DIFFS = freeze({
  'tools/gyp': 'build/gyp',
})

# Skip these dependencies (list without solution name prefix).
BLACKLIST = [
  'test/mozilla/data',
  'test/simdjs/data',
  'test/test262/data',
]

def GetDEPS(api, name, repo):
  # Make a fake spec. Gclient is not nice to us when having two solutions
  # side by side. The latter checkout kills the former's gclient file.
  spec = ('solutions=[{'
      '\'managed\':False,'
      '\'name\':\'%s\','
      '\'url\':\'%s\','
      '\'deps_file\':\'DEPS\'}]' % (name, repo))

  # Read local deps information. Each deps has one line in the format:
  # path/to/deps: repo@revision
  step_result = api.gclient(
      'get %s deps' % name,
      ['revinfo', '--deps', 'all', '--spec', spec],
      cwd=api.path['slave_build'],
      stdout=api.raw_io.output(),
  )

  # Transform into dict. Skip the solution prefix in keys (e.g. src/).
  deps = {}
  for line in step_result.stdout.strip().splitlines():
    key, value = line.strip().split(' ')

    # Remove trailing colon.
    key = key.split(':')[0]

    # Skip the deps entry to the solution itself.
    if not '/' in key:
      continue

    # Strip trailing solution name (e.g. src/).
    key = '/'.join(key.split('/')[1:])

    deps[key] = value

  # Log DEPS output.
  step_result.presentation.logs['deps'] = api.json.dumps(
      deps, indent=2).splitlines()
  return deps


def RunSteps(api):
  api.gclient.set_config('v8')
  api.gclient.apply_config('chromium')

  # Allow rolling all v8 os deps.
  api.gclient.c.target_os.add('android')
  api.gclient.c.target_os.add('win')

  # Skip large repos.
  s = api.gclient.c.solutions[1]
  s.custom_deps.update({
    'src/chrome/test/data/pdf_private': None,
    'src/native_client': None,
    'src/third_party/skia': None,
    'src/third_party/webrtc': None,
    'src/third_party/WebKit': None,
    'src/tools/valgrind': None,
    'src/v8': None,
  })
  api.bot_update.ensure_checkout(force=True, no_shallow=True)

  # Enforce a clean state.
  api.git(
      'checkout', '-f', 'origin/master',
      cwd=api.path['checkout'],
  )
  api.git(
      'branch', '-D', 'roll',
      ok_ret='any',
      cwd=api.path['checkout'],
  )
  api.git(
      'clean', '-ffd',
      cwd=api.path['checkout'],
  )
  api.git(
      'new-branch', 'roll',
      cwd=api.path['checkout'],
  )

  # Get chromium's and v8's deps information.
  cr_deps = GetDEPS(
      api, 'src', CR_REPO)
  v8_deps = GetDEPS(
      api, 'v8', V8_REPO)

  # Transform deps names that exist in src but have a different path in v8.
  cr_deps = dict((V8_DEPS_DIFFS.get(k, k), v) for k, v in cr_deps.iteritems())

  commit_message = []

  # Iterate over all v8 deps.
  for name in sorted(v8_deps.keys()):
    if name in BLACKLIST:
      continue
    def SplitValue(solution_name, value):
      assert '@' in value, (
          'Found %s value %s without pinned revision.' % (solution_name, name))
      return value.split('@')

    v8_repo, v8_rev = SplitValue('v8', v8_deps[name])
    cr_value = cr_deps.get(name)
    if cr_value:
      # Use the given revision from chromium's DEPS file.
      cr_repo, new_rev = SplitValue('src', cr_value)
      assert v8_repo == cr_repo, 'Found v8 %s for src %s.' % (v8_repo, cr_repo)
    else:
      # Use the HEAD of the deps repo.
      step_result = api.git(
        'ls-remote', v8_repo, 'HEAD',
        name='look up %s' % name.replace('/', '_'),
        stdout=api.raw_io.output(),
      )
      new_rev = step_result.stdout.strip().split('\t')[0]
      step_result.presentation.step_text = new_rev

    # Check if an update is necessary.
    if v8_rev != new_rev:
      step_result = api.step(
          'roll dependency %s' % name.replace('/', '_'),
          ['roll-dep-svn', '--no-verify-revision', 'v8/%s' % name, new_rev],
          ok_ret='any',
          cwd=api.path['checkout'],
      )
      if step_result.retcode == 0:
        commit_message.append('Rolling %s to %s' % ('v8/%s' % name, new_rev))
      else:
        step_result.presentation.status = api.step.WARNING

  # Check for a difference. If no deps changed, the diff is empty.
  step_result = api.git(
      'diff',
      stdout=api.raw_io.output(),
      cwd=api.path['checkout'],
  )
  diff = step_result.stdout.strip()
  step_result.presentation.logs['diff'] = diff.splitlines()

  # Commit deps change and send to CQ.
  if diff:
    args = ['commit', '-a', '-m', 'Update V8 DEPS.']
    for message in commit_message:
      args.extend(['-m', message])
    args.extend(['-m', 'TBR=%s' % ','.join(AUTO_REVIEWERS)])
    kwargs = {'stdout': api.raw_io.output(), 'cwd': api.path['checkout']}
    api.git(*args, **kwargs)
    api.git(
        'cl', 'upload', '-f', '--use-commit-queue', '--bypass-hooks',
        '--email', 'v8-autoroll@chromium.org', '--send-mail',
        cwd=api.path['checkout'],
    )


def GenTests(api):
  v8_deps_info = (
    'v8: repo1@v8_rev\n'
    'v8/a/dep: repo2@deadbeef\n'
    'v8/odd/dep: odd@odd\n'
    'v8/another/dep: repo3@deadbeef\n'
    'v8/test/test262/data: repo4@ignore\n'
  )
  cr_deps_info = (
    'src: repo3@cr_rev\n'
    'src/a/dep: repo2@beefdead\n'
    'src/yet/another/dep: repo3@deadbeef\n'
    'src/odd/dep: odd@weird\n'
  )
  yield (
      api.test('roll') +
      api.properties.generic(mastername='client.v8.fyi',
                             buildername='Auto-roll - v8 deps') +
      api.override_step_data(
          'gclient get v8 deps',
          api.raw_io.stream_output(v8_deps_info, stream='stdout'),
      ) +
      api.override_step_data(
          'gclient get src deps',
          api.raw_io.stream_output(cr_deps_info, stream='stdout'),
      ) +
      api.override_step_data(
          'roll dependency odd_dep',
          retcode=1,
      ) +
      api.override_step_data(
          'look up another_dep',
          api.raw_io.stream_output('deadbeaf\tHEAD', stream='stdout'),
      ) +
      api.override_step_data(
          'git diff',
          api.raw_io.stream_output('some difference', stream='stdout'),
      )
  )
