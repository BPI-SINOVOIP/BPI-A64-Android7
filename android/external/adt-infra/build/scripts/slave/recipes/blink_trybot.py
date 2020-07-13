# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze

DEPS = [
  'bot_update',
  'chromium',
  'chromium_tests',
  'gclient',
  'gpu',
  'isolate',
  'json',
  'path',
  'platform',
  'properties',
  'python',
  'raw_io',
  'rietveld',
  'step',
  'swarming',
  'test_utils',
  'tryserver',
]


# TODO(joelo): Move this to chromium_trybot.py and delete this recipe.
BUILDERS = freeze({
  'tryserver.v8': {
    'builders': {
      'v8_linux_layout_rel': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Release',
          'TARGET_BITS': 64,
        },
        'compile_only': False,
        'v8_blink_flavor': True,
        'root_override': 'src/v8',
        'set_custom_revs': {
          'src/v8': 'bleeding_edge:%(revision)s',
        },
        'testing': {
          'platform': 'linux',
        },
      },
      'v8_linux_layout_dbg': {
        'chromium_config_kwargs': {
          'BUILD_CONFIG': 'Debug',
          'TARGET_BITS': 64,
        },
        'compile_only': False,
        'v8_blink_flavor': True,
        'root_override': 'src/v8',
        'set_custom_revs': {
          'src/v8': 'bleeding_edge:%(revision)s',
        },
        'testing': {
          'platform': 'linux',
        },
      },
    },
  },
})


def _RunStepsInternal(api):
  mastername = api.properties.get('mastername')
  buildername = api.properties.get('buildername')
  master_dict = BUILDERS.get(mastername, {})
  bot_config = master_dict.get('builders', {}).get(buildername)

  api.chromium.set_config('blink',
                          **bot_config.get('chromium_config_kwargs', {}))
  api.chromium.apply_config('trybot_flavor')
  api.gclient.set_config('blink_internal',
                         PATCH_PROJECT=api.properties.get('patch_project'))
  if bot_config.get('v8_blink_flavor'):
    api.gclient.apply_config('v8_blink_flavor')
    api.gclient.apply_config('show_v8_revision')
  if api.properties['revision']:
    for dep, rev in bot_config.get('set_custom_revs', {}).iteritems():
      api.gclient.c.revisions[dep] = rev % api.properties

  webkit_lint = api.path['build'].join('scripts', 'slave', 'chromium',
                                       'lint_test_files_wrapper.py')
  webkit_python_tests = api.path['build'].join('scripts', 'slave', 'chromium',
                                               'test_webkitpy_wrapper.py')

  # Set patch_root used when applying the patch after checkout. Default None
  # makes bot_update determine the patch_root from tryserver root, e.g. 'src'.
  bot_update_step = api.bot_update.ensure_checkout(
      force=True, patch_root=bot_config.get('root_override'))

  api.chromium_tests.configure_swarming('blink', precommit=True)

  # Swarming uses Isolate to transfer files to swarming bots.
  # set_isolate_environment modifies GYP_DEFINES to enable test isolation.
  api.isolate.set_isolate_environment(api.chromium.c)

  # Ensure swarming_client is compatible with what recipes expect.
  api.swarming.check_client_version()

  api.chromium.runhooks()

  api.isolate.clean_isolated_files(api.chromium.output_dir)
  api.chromium.compile()
  api.isolate.isolate_tests(api.chromium.output_dir)

  api.python('webkit_lint', webkit_lint, [
    '--build-dir', api.path['checkout'].join('out'),
    '--target', api.chromium.c.BUILD_CONFIG
  ])
  api.python('webkit_python_tests', webkit_python_tests, [
    '--build-dir', api.path['checkout'].join('out'),
    '--target', api.chromium.c.BUILD_CONFIG,
  ])

  # TODO(martiniss) this is pretty goofy
  failed = False
  exception = Exception() # So that pylint doesn't complain
  try:
    api.chromium.runtest('webkit_unit_tests', xvfb=True)
  except api.step.StepFailure as f:
    failed = True
    exception = f

  try:
    api.chromium.runtest('blink_platform_unittests')
  except api.step.StepFailure as f:
    failed = True
    exception = f

  try:
    api.chromium.runtest('blink_heap_unittests')
  except api.step.StepFailure as f:
    failed = True
    exception = f

  try:
    api.chromium.runtest('wtf_unittests')
  except api.step.StepFailure as f:
    failed = True
    exception = f

  if failed:
    api.python.inline(
      'Aborting due to failed build state',
      "import sys; sys.exit(1)")
    raise exception

  def deapply_patch_fn(_failing_steps):  # pragma: no cover
    properties = bot_update_step.json.output['properties']
    # Use the got_cr_revision property if available. In component-driven
    # builds it will hold the chromium revision (while got_revision will
    # hold the revision of the component, e.g. v8).
    api.gclient.c.revisions['src'] = str(
        properties.get('got_cr_revision', properties['got_revision']))
    api.gclient.c.revisions['src/third_party/WebKit'] = str(
        properties['got_webkit_revision'])

    api.bot_update.ensure_checkout(patch=False, force=True)
    api.chromium.runhooks()
    api.isolate.clean_isolated_files(api.chromium.output_dir)
    api.chromium.compile()
    api.isolate.isolate_tests(api.chromium.output_dir)

  tests = [api.chromium_tests.steps.BlinkTest()]

  api.test_utils.determine_new_failures(api, tests, deapply_patch_fn)


def RunSteps(api):
  with api.tryserver.set_failure_hash():
    return _RunStepsInternal(api)


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text)


def GenTests(api):
  canned_test = api.test_utils.canned_test_output
  with_patch = 'webkit_tests (with patch)'
  without_patch = 'webkit_tests (without patch)'

  def properties(mastername, buildername, patch_project='blink', **kwargs):
    return api.properties.tryserver(mastername=mastername,
                                    buildername=buildername,
                                    patch_project=patch_project,
                                    swarm_hashes=api.gpu.dummy_swarm_hashes,
                                    **kwargs)

  for mastername, master_config in BUILDERS.iteritems():
    for buildername, bot_config in master_config['builders'].iteritems():
      test_name = 'full_%s_%s' % (_sanitize_nonalpha(mastername),
                                  _sanitize_nonalpha(buildername))
      tests = []
      for pass_first in (True, False):
        test = (
          api.test(test_name + ('_pass' if pass_first else '_fail')) +
          api.override_step_data(with_patch, canned_test(passing=pass_first))
        )
        if not pass_first:
          test += api.override_step_data(
              without_patch, canned_test(passing=False, minimal=True))
        tests.append(test)

      for test in tests:
        test += (
          properties(mastername, buildername,
                     blamelist_real=['someone@chromium.org']) +
          api.platform(bot_config['testing']['platform'],
                       bot_config.get(
                           'chromium_config_kwargs', {}).get('TARGET_BITS', 64))
        )

        yield test

  # This tests that if the first fails, but the second pass succeeds
  # that we fail the whole build.
  yield (
    api.test('minimal_pass_continues') +
    properties('tryserver.v8', 'v8_linux_layout_dbg') +
    api.override_step_data(with_patch, canned_test(passing=False)) +
    api.override_step_data(without_patch,
                           canned_test(passing=True, minimal=True))
  )

  yield (
    api.test('preamble_test_failure') +
    properties('tryserver.v8', 'v8_linux_layout_dbg') +
    api.step_data('webkit_unit_tests', retcode=1)
  )
  for test in (
          'blink_platform_unittests',
          'blink_heap_unittests',
          'wtf_unittests'):
    yield (
      api.test('%s_failure' % test) +
      properties('tryserver.v8', 'v8_linux_layout_dbg') +
      api.step_data(test, retcode=1)
    )

  # This tests what happens if something goes horribly wrong in
  # run-webkit-tests and we return an internal error; the step should
  # be considered a hard failure and we shouldn't try to compare the
  # lists of failing tests.
  # 255 == test_run_results.UNEXPECTED_ERROR_EXIT_STATUS in run-webkit-tests.
  yield (
    api.test('webkit_tests_unexpected_error') +
    properties('tryserver.v8', 'v8_linux_layout_dbg') +
    api.override_step_data(with_patch, canned_test(passing=False,
                                                   retcode=255))
  )

  # TODO(dpranke): crbug.com/357866 . This tests what happens if we exceed the
  # number of failures specified with --exit-after-n-crashes-or-times or
  # --exit-after-n-failures; the step should be considered a hard failure and
  # we shouldn't try to compare the lists of failing tests.
  # 130 == test_run_results.INTERRUPTED_EXIT_STATUS in run-webkit-tests.
  yield (
    api.test('webkit_tests_interrupted') +
    properties('tryserver.v8', 'v8_linux_layout_dbg') +
    api.override_step_data(with_patch, canned_test(passing=False,
                                                   retcode=130))
  )

  # This tests what happens if we don't trip the thresholds listed
  # above, but fail more tests than we can safely fit in a return code.
  # (this should be a soft failure and we can still retry w/o the patch
  # and compare the lists of failing tests).
  yield (
    api.test('too_many_failures_for_retcode') +
    properties('tryserver.v8', 'v8_linux_layout_dbg') +
    api.override_step_data(with_patch,
                           canned_test(passing=False,
                                       num_additional_failures=125)) +
    api.override_step_data(without_patch,
                           canned_test(passing=True, minimal=True))
  )

  yield (
    api.test('non_cq_tryjob') +
    properties('tryserver.v8', 'v8_linux_layout_dbg',
               requester='someone@chromium.org') +
    api.platform.name('win') +
    api.override_step_data(with_patch, canned_test(passing=True))
  )

  yield (
    api.test('use_v8_patch_on_blink_trybot') +
    properties(buildername='v8_linux_layout_dbg',
               mastername='tryserver.v8',
               patch_project='v8') +
    api.platform.name('mac')
  )
