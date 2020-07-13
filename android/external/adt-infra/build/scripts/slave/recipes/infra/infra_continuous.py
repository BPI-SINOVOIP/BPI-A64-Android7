# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'file',
  'gclient',
  'json',
  'path',
  'platform',
  'properties',
  'python',
  'step',
]


def build_cipd_packages(api, repo, rev):
  # Path to a service account credentials to use to talk to CIPD backend.
  # Deployed by Puppet.
  if api.platform.is_win:
    creds = 'C:\\creds\\service_accounts\\service-account-cipd-builder.json'
  else:
    creds = '/creds/service_accounts/service-account-cipd-builder.json'

  # Build packages locally.
  api.python(
      'cipd - build packages',
      api.path['checkout'].join('build', 'build.py'),
      ['--builder', api.properties.get('buildername')])

  # Verify they are good.
  api.python(
      'cipd - test packages integrity',
      api.path['checkout'].join('build', 'test_packages.py'))

  # Upload them, attach tags.
  tags = [
    'buildbot_build:%s/%s/%s' % (
        api.properties['mastername'],
        api.properties['buildername'],
        api.properties['buildnumber']),
    'git_repository:%s' % repo,
    'git_revision:%s' % rev,
  ]
  try:
    return api.python(
        'cipd - upload packages',
        api.path['checkout'].join('build', 'build.py'),
        [
          '--no-rebuild',
          '--upload',
          '--service-account-json', creds,
          '--json-output', api.json.output(),
          '--builder', api.properties.get('buildername'),
        ] + ['--tags'] + tags)
  finally:
    step_result = api.step.active_result
    output = step_result.json.output or {}
    p = step_result.presentation
    for pkg in output.get('succeeded', []):
      info = pkg['info']
      title = '%s %s' % (info['package'], info['instance_id'])
      p.links[title] = info.get('url', 'http://example.com/not-implemented-yet')


def build_luci(api):
  go_bin = api.path['checkout'].join('go', 'bin')
  go_env = api.path['checkout'].join('go', 'env.py')
  api.file.rmcontents('clean go bin', go_bin)

  api.python(
      'build luci-go', go_env,
      ['go', 'install', 'github.com/luci/luci-go/client/cmd/...'])

  files = sorted(api.file.listdir('listing go bin', go_bin))
  absfiles = [api.path.join(go_bin, i) for i in files]
  api.python(
      'upload go bin',
      api.path['depot_tools'].join('upload_to_google_storage.py'),
      ['-b', 'chromium-luci'] + absfiles)
  for name, abspath in zip(files, absfiles):
    sha1 = api.file.read(
        '%s sha1' % str(name), abspath + '.sha1',
        test_data='0123456789abcdeffedcba987654321012345678')
    api.step.active_result.presentation.step_text = sha1


def RunSteps(api):
  builder_name = api.properties.get('buildername')
  if builder_name.startswith('infra-internal-continuous'):
    project_name = 'infra_internal'
    repo_name = 'https://chrome-internal.googlesource.com/infra/infra_internal'
  elif builder_name.startswith('infra-continuous'):
    project_name = 'infra'
    repo_name = 'https://chromium.googlesource.com/infra/infra'
  else:  # pragma: no cover
    raise ValueError(
        'This recipe is not intended for builder %s. ' % builder_name)

  api.gclient.set_config(project_name)
  bot_update_step = api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()

  # Whatever is checked out by bot_update. It is usually equal to
  # api.properties['revision'] except when the build was triggered manually
  # ('revision' property is missing in that case).
  rev = bot_update_step.presentation.properties['got_revision']

  with api.step.defer_results():
    # Run Linux\Mac tests everywhere, Windows tests only on public CI.
    if not api.platform.is_win or project_name == 'infra':
      api.python(
          'infra python tests',
          'test.py',
          ['test'],
          cwd=api.path['checkout'])
      api.python(
          'infra javascript tests', 'testjs.py', [], cwd=api.path['checkout'])

    # Run Glyco tests only on public Linux\Mac CI.
    if project_name == 'infra' and not api.platform.is_win:
      api.python(
          'Glyco tests',
          api.path['checkout'].join('glyco', 'tests', 'run_all_tests.py'),
          [],
          cwd=api.path['checkout'])

    # This downloads Go third parties, so that the next step doesn't have junk
    # output in it.
    api.python(
        'go third parties',
        api.path['checkout'].join('go', 'env.py'),
        ['go', 'version'])
    # Note: env.py knows how to expand 'python' into sys.executable.
    api.python(
        'infra go tests',
        api.path['checkout'].join('go', 'env.py'),
        ['python', api.path['checkout'].join('go', 'test.py')])

  build_cipd_packages(api, repo_name, rev)

  # Only build luci-go executables on 64 bits, public CI.
  if project_name == 'infra' and builder_name.endswith('-64'):
    build_luci(api)


def GenTests(api):
  cipd_json_output = {
    'succeeded': [
      {
        'info': {
          'instance_id': 'abcdefabcdef63ad814cd1dfffe2fcfc9f81299c',
          'package': 'infra/tools/some_tool/linux-bitness',
        },
        'pkg_def_name': 'some_tool',
      },
    ],
    'failed': [],
  }

  yield (
    api.test('infra') +
    api.properties.git_scheduled(
        buildername='infra-continuous',
        buildnumber=123,
        mastername='chromium.infra',
        repository='https://chromium.googlesource.com/infra/infra',
    ) +
    api.override_step_data(
        'cipd - upload packages', api.json.output(cipd_json_output))
  )
  yield (
    api.test('infra_win') +
    api.properties.git_scheduled(
        buildername='infra-continuous',
        buildnumber=123,
        mastername='chromium.infra',
        repository='https://chromium.googlesource.com/infra/infra',
    ) +
    api.platform.name('win')
  )
  yield (
    api.test('infra_internal') +
    api.properties.git_scheduled(
        buildername='infra-internal-continuous',
        buildnumber=123,
        mastername='internal.infra',
        repository=
            'https://chrome-internal.googlesource.com/infra/infra_internal',
    ) +
    api.override_step_data(
        'cipd - upload packages', api.json.output(cipd_json_output))
  )
  yield (
    api.test('infra-64') +
    api.properties.git_scheduled(
        buildername='infra-continuous-64',
        buildnumber=123,
        mastername='chromium.infra',
        repository='https://chromium.googlesource.com/infra/infra',
    )
  )
