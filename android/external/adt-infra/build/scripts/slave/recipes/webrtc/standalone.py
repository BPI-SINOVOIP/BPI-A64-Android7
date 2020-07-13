# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Recipe for building and running tests for WebRTC stand-alone.

DEPS = [
  'archive',
  'bot_update',
  'chromium',
  'chromium_android',
  'gclient',
  'path',
  'platform',
  'properties',
  'step',
  'tryserver',
  'webrtc',
]


def RunSteps(api):
  webrtc = api.webrtc
  webrtc.apply_bot_config(webrtc.BUILDERS, webrtc.RECIPE_CONFIGS)

  webrtc.checkout()
  webrtc.cleanup()
  api.chromium.runhooks()
  webrtc.check_swarming_version()

  if webrtc.should_build:
    if api.chromium.c.project_generator.tool == 'gn':
      api.chromium.run_gn(use_goma=True)
    webrtc.compile()

    if api.chromium.c.gyp_env.GYP_DEFINES.get('syzyasan', 0) == 1:
      api.chromium.apply_syzyasan()

  if webrtc.should_upload_build:
    webrtc.package_build()
  if webrtc.should_download_build:
    webrtc.extract_build()

  if webrtc.should_test:
    webrtc.runtests()

  webrtc.maybe_trigger()


def _sanitize_nonalpha(text):
  return ''.join(c if c.isalnum() else '_' for c in text.lower())


def GenTests(api):
  builders = api.webrtc.BUILDERS

  def generate_builder(mastername, buildername, revision,
                       parent_got_revision=None, legacy_trybot=False,
                       failing_test=None, suffix=None):
    suffix = suffix or ''
    bot_config = builders[mastername]['builders'][buildername]
    bot_type = bot_config.get('bot_type', 'builder_tester')

    if bot_type in ('builder', 'builder_tester'):
      assert bot_config.get('parent_buildername') is None, (
          'Unexpected parent_buildername for builder %r on master %r.' %
              (buildername, mastername))

    chromium_kwargs = bot_config.get('chromium_config_kwargs', {})
    test = (
      api.test('%s_%s%s' % (_sanitize_nonalpha(mastername),
                            _sanitize_nonalpha(buildername), suffix)) +
      api.properties(mastername=mastername,
                     buildername=buildername,
                     slavename='slavename',
                     BUILD_CONFIG=chromium_kwargs['BUILD_CONFIG']) +
      api.platform(bot_config['testing']['platform'],
                   chromium_kwargs.get('TARGET_BITS', 64))
    )

    if bot_config.get('parent_buildername'):
      test += api.properties(
          parent_buildername=bot_config['parent_buildername'])

    if revision:
      test += api.properties(revision=revision)
    if bot_type == 'tester':
      parent_rev = parent_got_revision or revision
      test += api.properties(parent_got_revision=parent_rev)

    if failing_test:
      test += api.step_data(failing_test, retcode=1)

    if mastername.startswith('tryserver'):
      if legacy_trybot:
        test += api.properties(patch_url='try_job_svn_patch')
      else:
        test += api.properties(issue=666666, patchset=1,
                               rietveld='https://fake.rietveld.url')
    else:
      test += api.properties(buildnumber=1337)
    return test

  for mastername in ('client.webrtc', 'client.webrtc.fyi', 'tryserver.webrtc'):
    master_config = builders[mastername]
    for buildername in master_config['builders'].keys():
      yield generate_builder(mastername, buildername, revision='12345')

  # Forced builds (not specifying any revision) and test failures.
  mastername = 'client.webrtc'
  buildername = 'Linux64 Debug'
  yield generate_builder(mastername, buildername, revision=None,
                         suffix='_forced')
  yield generate_builder(mastername, buildername, revision='12345',
                         failing_test='tools_unittests',
                         suffix='_failing_test')

  yield generate_builder(mastername, 'Android32 Builder', revision=None,
                         suffix='_forced')

  buildername = 'Android32 Tests (L Nexus5)'
  yield generate_builder(mastername, buildername, revision=None,
                         parent_got_revision='12345', suffix='_forced')
  yield generate_builder(mastername, buildername, revision=None,
                         suffix='_forced_invalid')
  yield generate_builder(mastername, buildername, revision='12345',
                         failing_test='tools_unittests', suffix='_failing_test')

  # Legacy trybot (SVN-based).
  mastername = 'tryserver.webrtc'
  yield generate_builder(mastername, 'linux_dbg', revision='12345',
                         legacy_trybot=True, suffix='_legacy_svn_patch')
