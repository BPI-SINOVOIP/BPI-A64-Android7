# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'gclient',
  'git',
  'path',
  'platform',
  'properties',
  'python',
  'raw_io',
  'step',
]


def RunSteps(api):
  project = api.properties['patch_project'] or api.properties['project']
  internal = (project == 'infra_internal')

  api.gclient.set_config(project)
  api.bot_update.ensure_checkout(force=True, patch_root=project,
                                 patch_oauth2=internal)

  api.git('-c', 'user.email=commit-bot@chromium.org',
          '-c', 'user.name=The Commit Bot',
          'commit', '-a', '-m', 'Committed patch',
          name='commit git patch', cwd=api.path['checkout'])

  api.gclient.runhooks()

  # Grab a list of changed files.
  result = api.git(
      'diff', '--name-only', 'HEAD', 'HEAD~',
      name='get change list',
      cwd=api.path['checkout'],
      stdout=api.raw_io.output())
  files = result.stdout.splitlines()
  result.presentation.logs['change list'] = files

  with api.step.defer_results():
    # Rietveld tests.
    deps_mod = 'DEPS' in files

    if not api.platform.is_win and (deps_mod or
        any(f.startswith('appengine/chromium_rietveld') for f in files)):
      api.step('rietveld tests',
               ['make', '-C', 'appengine/chromium_rietveld', 'test'],
               cwd=api.path['checkout'])

    if deps_mod or not all(f.startswith('go/') for f in files):
      api.python('test.py', 'test.py', ['test'], cwd=api.path['checkout'])

    if any(f.startswith('infra/glyco/') for f in files):
      api.python(
        'Glyco tests',
        api.path['checkout'].join('glyco', 'tests', 'run_all_tests.py'),
        [], cwd=api.path['checkout'])

    if deps_mod or any(f.startswith('go/') for f in files):
      # Note: env.py knows how to expand 'python' into sys.executable.
      api.python(
          'go test.py', api.path['checkout'].join('go', 'env.py'),
          ['python', api.path['checkout'].join('go', 'test.py')])


def GenTests(api):
  def diff(*files):
    return api.step_data(
        'get change list', api.raw_io.stream_output('\n'.join(files)))

  yield (
    api.test('basic') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('infra/stuff.py', 'go/src/infra/stuff.go')
  )

  yield (
    api.test('only_go') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('go/src/infra/stuff.go')
  )

  yield (
    api.test('only_python') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('infra/stuff.py')
  )

  yield (
    api.test('only_glyco_python') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('infra/glyco/stuff.py')
  )

  yield (
    api.test('infra_internal') +
    api.properties.tryserver(
        mastername='internal.infra',
        buildername='infra-internal-tester',
        patch_project='infra_internal') +
    diff('infra/stuff.py', 'go/src/infra/stuff.go')
  )

  yield (
    api.test('rietveld_tests') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('appengine/chromium_rietveld/codereview/views.py')
  )

  yield (
    api.test('rietveld_tests_on_win') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('appengine/chromium_rietveld/codereview/views.py') +
    api.platform.name('win')
  )

  yield (
    api.test('only_DEPS') +
    api.properties.tryserver(
        mastername='tryserver.chromium.linux',
        buildername='infra_tester',
        patch_project='infra') +
    diff('DEPS')
  )
