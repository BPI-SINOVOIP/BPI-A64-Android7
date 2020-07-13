# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze
from recipe_engine import recipe_api
from . import builders
from . import steps


class WebRTCApi(recipe_api.RecipeApi):
  def __init__(self, **kwargs):
    super(WebRTCApi, self).__init__(**kwargs)
    self._env = {}

  BUILDERS = builders.BUILDERS
  RECIPE_CONFIGS = builders.RECIPE_CONFIGS

  NORMAL_TESTS = (
    'audio_decoder_unittests',
    'common_audio_unittests',
    'common_video_unittests',
    'libjingle_media_unittest',
    'libjingle_p2p_unittest',
    'libjingle_peerconnection_unittest',
    'modules_tests',
    'modules_unittests',
    'rtc_unittests',
    'system_wrappers_unittests',
    'test_support_unittests',
    'tools_unittests',
    'video_engine_core_unittests',
    'video_engine_tests',
    'voice_engine_unittests',
  )

  # Android APK tests.
  ANDROID_APK_TESTS = freeze({
    'audio_decoder_unittests':
        'webrtc/modules/audio_decoder_unittests.isolate',
    'common_audio_unittests':
        'webrtc/common_audio/common_audio_unittests.isolate',
    'common_video_unittests':
        'webrtc/common_video/common_video_unittests.isolate',
    'modules_tests': 'webrtc/modules/modules_tests.isolate',
    'modules_unittests': 'webrtc/modules/modules_unittests.isolate',
    'system_wrappers_unittests':
        'webrtc/system_wrappers/system_wrappers_unittests.isolate',
    'test_support_unittests': 'webrtc/test/test_support_unittests.isolate',
    'tools_unittests': 'webrtc/tools/tools_unittests.isolate',
    'video_engine_core_unittests':
        'webrtc/video_engine/video_engine_core_unittests.isolate',
    'video_engine_tests': 'webrtc/video_engine_tests.isolate',
    'voice_engine_unittests':
        'webrtc/voice_engine/voice_engine_unittests.isolate',
  })

  ANDROID_APK_PERF_TESTS = freeze({
    'webrtc_perf_tests': 'webrtc/webrtc_perf_tests.isolate'
  })

  # Instrumentation tests may target a separate APK to be tested. In that case,
  # specify the APK name (without the .apk extension) as key in the dict below.
  ANDROID_INSTRUMENTATION_TESTS = {
     'AppRTCDemoTest': 'AppRTCDemo',
     'libjingle_peerconnection_android_unittest': None,
  }

  # Map of GS archive names to urls.
  # TODO(kjellander): Convert to use the auto-generated URLs once we've setup a
  # separate bucket per master.
  GS_ARCHIVES = freeze({
    'android_dbg_archive': 'gs://chromium-webrtc/android_chromium_dbg',
    'android_dbg_archive_fyi': ('gs://chromium-webrtc/'
                                'android_chromium_trunk_dbg'),
    'android_dbg_archive_arm64_fyi': ('gs://chromium-webrtc/'
                                      'android_chromium_trunk_arm64_dbg'),
    'android_apk_dbg_archive': 'gs://chromium-webrtc/android_dbg',
    'android_apk_arm64_rel_archive': 'gs://chromium-webrtc/android_arm64_rel',
    'android_apk_rel_archive': 'gs://chromium-webrtc/android_rel',
    'win_rel_archive': 'gs://chromium-webrtc/Win Builder',
    'win_rel_archive_fyi': 'gs://chromium-webrtc/win_rel-fyi',
    'mac_rel_archive': 'gs://chromium-webrtc/Mac Builder',
    'mac_rel_archive_fyi': 'gs://chromium-webrtc/mac_rel-fyi',
    'linux_rel_archive': 'gs://chromium-webrtc/Linux Builder',
    'fyi_android_apk_dbg_archive': 'gs://chromium-webrtc/fyi_android_dbg',
    'fyi_linux_asan_archive': 'gs://chromium-webrtc/Linux ASan Builder',
    'fyi_linux_chromium_rel_archive': 'gs://chromium-webrtc/Linux Chromium FYI',

  })

  BROWSER_TESTS_GTEST_FILTER = 'WebRtc*:Webrtc*:TabCapture*:*MediaStream*'
  DASHBOARD_UPLOAD_URL = 'https://chromeperf.appspot.com'

  @property
  def should_build(self):
    return self.bot_type in ('builder', 'builder_tester')

  @property
  def should_test(self):
    return self.bot_type in ('tester', 'builder_tester')

  @property
  def should_run_hooks(self):
    return not self.bot_config.get('disable_runhooks')

  @property
  def should_upload_build(self):
    return self.bot_type == 'builder'

  @property
  def should_download_build(self):
    return self.bot_type == 'tester'

  def apply_bot_config(self, builders, recipe_configs, perf_config=None,
                       git_hashes_as_perf_revisions=False):
    mastername = self.m.properties.get('mastername')
    buildername = self.m.properties.get('buildername')
    self.git_hashes_as_perf_revisions = git_hashes_as_perf_revisions
    master_dict = builders.get(mastername, {})
    master_settings = master_dict.get('settings', {})
    perf_config = master_settings.get('PERF_CONFIG')

    self.bot_config = master_dict.get('builders', {}).get(buildername)
    assert self.bot_config, ('Unrecognized builder name "%r" for master "%r".' %
                             (buildername, mastername))

    self.bot_type = self.bot_config.get('bot_type', 'builder_tester')
    recipe_config_name = self.bot_config['recipe_config']
    self.recipe_config = recipe_configs.get(recipe_config_name)
    assert self.recipe_config, (
        'Cannot find recipe_config "%s" for builder "%r".' %
        (recipe_config_name, buildername))

    self.set_config('webrtc', PERF_CONFIG=perf_config,
                    TEST_SUITE=self.recipe_config.get('test_suite'),
                    **self.bot_config.get('webrtc_config_kwargs', {}))

    chromium_kwargs = self.bot_config.get('chromium_config_kwargs', {})
    if self.recipe_config.get('chromium_android_config'):
      self.m.chromium_android.set_config(
          self.recipe_config['chromium_android_config'], **chromium_kwargs)

    self.m.chromium.set_config(self.recipe_config['chromium_config'],
                               **chromium_kwargs)
    self.m.gclient.set_config(self.recipe_config['gclient_config'])

    # Support applying configs both at the bot and the recipe config level.
    for c in self.bot_config.get('chromium_apply_config', []):
      self.m.chromium.apply_config(c)
    for c in self.recipe_config.get('chromium_apply_config', []):
      self.m.chromium.apply_config(c)

    for c in self.bot_config.get('gclient_apply_config', []):
      self.m.gclient.apply_config(c)
    for c in self.recipe_config.get('gclient_apply_config', []):
      self.m.gclient.apply_config(c)

    if self.m.tryserver.is_tryserver:
      self.m.chromium.apply_config('trybot_flavor')

    self.c.use_isolate = self.bot_config.get('use_isolate')
    if self.c.use_isolate:
      self.m.isolate.set_isolate_environment(self.m.chromium.c)

    if self.bot_config.get('enable_swarming'):
      self.m.chromium_tests.configure_swarming(
          'webrtc',
          precommit=self.m.tryserver.is_tryserver,
          mastername=mastername)

  def checkout(self, **kwargs):
    update_step = self.m.bot_update.ensure_checkout(**kwargs)
    assert update_step.json.output['did_run']

    # Whatever step is run right before this line needs to emit got_revision.
    revs = update_step.presentation.properties
    self.revision = revs['got_revision']
    self.revision_cp = revs['got_revision_cp']
    self.revision_number = str(self.m.commit_position.parse_revision(
        self.revision_cp))
    self.perf_revision = (self.revision if self.git_hashes_as_perf_revisions
                          else self.revision_number)

    # Check for properties specific to our Chromium builds.
    self.libjingle_revision = revs.get('got_libjingle_revision')
    self.libjingle_revision_cp = revs.get('got_libjingle_revision_cp')
    self.webrtc_revision = revs.get('got_webrtc_revision')
    self.webrtc_revision_cp = revs.get('got_webrtc_revision_cp')

  def check_swarming_version(self):
    if self.bot_config.get('enable_swarming'):
      self.m.swarming.check_client_version()

  def compile(self):
    compile_targets = self.recipe_config.get('compile_targets', [])
    self.m.chromium.compile(targets=compile_targets)

  def runtests(self):
    """Add a suite of test steps.

    Args:
      test_suite: The name of the test suite.
    """
    if self.c.use_isolate:
      self.m.isolate.remove_build_metadata()
      self.m.isolate.isolate_tests(self.m.chromium.output_dir,
                                   targets=self.NORMAL_TESTS)

    if (self.m.chromium.c.TARGET_PLATFORM == 'android' and
        self.m.chromium.c.gyp_env.GYP_DEFINES.get('asan', 0) == 1):
      self.m.chromium_android.asan_device_setup()

    with self.m.step.defer_results():
      tests = steps.generate_tests(self, self.c.TEST_SUITE, self.revision)
      if tests:
        if self.m.chromium.c.TARGET_PLATFORM == 'android':
          self.m.chromium_android.common_tests_setup_steps()
        for test in tests:
          test.run(self, suffix='')
        if self.m.chromium.c.TARGET_PLATFORM == 'android':
          self.m.chromium_android.common_tests_final_steps()


  def add_test(self, test, name=None, args=None, revision=None, env=None,
               perf_test=False, perf_dashboard_id=None, parallel=False,
               enable_swarming=True):
    """Helper function to invoke chromium.runtest().

    Notice that the name parameter should be the same as the test executable in
    order to get the stdio links in the perf dashboard to become correct.
    """
    name = name or test
    args = args or []
    env = env or {}
    if perf_test and self.c.PERF_ID:
      perf_dashboard_id = perf_dashboard_id or test
      assert self.perf_revision, (
          'A revision number must be specified for perf tests as they upload '
          'data to the perf dashboard.')
      self.m.chromium.runtest(
          test=test, args=args, name=name,
          results_url=self.DASHBOARD_UPLOAD_URL, annotate='graphing',
          xvfb=True, perf_dashboard_id=perf_dashboard_id,
          test_type=perf_dashboard_id, env=env, revision=self.perf_revision,
          perf_id=self.c.PERF_ID, perf_config=self.c.PERF_CONFIG)
    else:
      annotate = 'gtest'
      python_mode = False
      test_type = test
      flakiness_dash = (not self.m.tryserver.is_tryserver and
                        not self.m.chromium.c.runtests.memory_tool)
      if parallel:
        test_executable = self.m.chromium.c.build_dir.join(
          self.m.chromium.c.build_config_fs, test)
        args = [test_executable, '--'] + args
        test = self.m.path['checkout'].join('third_party', 'gtest-parallel',
                                            'gtest-parallel')
        python_mode = True
        annotate = None  # The parallel script doesn't output gtest format.
        flakiness_dash = False

      runtest_kwargs = {
          'annotate': annotate,
          'xvfb': True,
          'flakiness_dash': flakiness_dash,
          'python_mode': python_mode,
          'test_type': test_type,
          'env': env,
      }
      if self.c.use_isolate and enable_swarming:
        self.m.isolate.runtest(test=test, revision=revision,
                               webkit_revision='deadbeef', args=args, name=name,
                               **runtest_kwargs)
      else:
        self.m.chromium.runtest(test=test, args=args, name=name,
                                revision=revision, **runtest_kwargs)

  def _adb_install_apk(self, apk_name):
    """Installs an APK on an Android device.

    We cannot use chromium_android.adb_install_apk since it will fail due to
    the automatic path creation becomes invalid due to WebRTC's usage of
    symlinks and the Chromium checkout in chromium/src combined with
    assumptions in the Android test tool chain.
    """
    apk_path = str(self.m.chromium.c.build_dir.join(
        self.m.chromium.c.build_config_fs, 'apks', '%s.apk' % apk_name))
    install_cmd = [
        self.m.path['checkout'].join('build',
                                     'android',
                                     'adb_install_apk.py'),
        apk_path,
    ]
    if self.m.chromium.c.BUILD_CONFIG == 'Release':
      install_cmd.append('--release')
    env = { 'CHECKOUT_SOURCE_ROOT': self.m.path['checkout']}
    return self.m.step('install ' + apk_name, install_cmd, infra_step=True,
                       env=env)

  def sizes(self):
    # TODO(kjellander): Move this into a function of the chromium recipe
    # module instead.
    assert self.c.PERF_ID, ('You must specify PERF_ID for the builder that '
                            'runs the sizes step.')
    sizes_script = self.m.path['build'].join('scripts', 'slave', 'chromium',
                                             'sizes.py')
    args = ['--target', self.m.chromium.c.BUILD_CONFIG,
            '--platform', self.m.chromium.c.TARGET_PLATFORM]
    test_name = 'sizes'
    self.add_test(
        test=sizes_script,
        name=test_name,
        perf_dashboard_id=test_name,
        args=args,
        revision=self.perf_revision,
        perf_test=True)

  def maybe_trigger(self):
    triggers = self.bot_config.get('triggers')
    if triggers:
      properties = {
        'revision': self.revision,
        'parent_got_revision': self.revision,
        'parent_got_revision_cp': self.revision_cp,
      }
      if self.webrtc_revision:
        properties.update({
          'parent_got_libjingle_revision': self.libjingle_revision,
          'parent_got_libjingle_revision_cp': self.libjingle_revision_cp,
          'parent_got_webrtc_revision': self.webrtc_revision,
          'parent_got_webrtc_revision_cp': self.webrtc_revision_cp,
        })
      self.m.trigger(*[{
        'builder_name': builder_name,
        'properties': properties,
      } for builder_name in triggers])

  def package_build(self):
    if self.bot_config.get('build_gs_archive'):
      self.m.archive.zip_and_upload_build(
          'package build',
          self.m.chromium.c.build_config_fs,
          self.GS_ARCHIVES[self.bot_config['build_gs_archive']],
          build_revision=self.revision)

  def extract_build(self):
    if not self.m.properties.get('parent_got_revision'):
      raise self.m.step.StepFailure(
         'Testers cannot be forced without providing revision information.'
         'Please select a previous build and click [Rebuild] or force a build'
         'for a Builder instead (will trigger new runs for the testers).')

    # Ensure old build directory is not used is by removing it.
    self.m.file.rmtree(
        'build directory',
        self.m.chromium.c.build_dir.join(self.m.chromium.c.build_config_fs))

    self.m.archive.download_and_unzip_build(
        'extract build',
        self.m.chromium.c.build_config_fs,
        self.GS_ARCHIVES[self.bot_config['build_gs_archive']],
        build_revision=self.revision)

  def cleanup(self):
    self.clean_test_output()
    if self.m.chromium.c.TARGET_PLATFORM == 'android':
      self.m.chromium_android.clean_local_files()
    else:
      self.m.chromium.cleanup_temp()
    if self.c.use_isolate:
      self.m.isolate.clean_isolated_files(self.m.chromium.output_dir)

  def clean_test_output(self):
    """Remove all test output in out/, since we have tests leaking files."""
    out_dir = self.m.path['checkout'].join('out')
    self.m.python('clean test output files',
                  script=self.resource('cleanup_files.py'),
                  args=[out_dir],
                  infra_step=True)

  def virtual_webcam_check(self):
    self.m.python('webcam_check', self.resource('ensure_webcam_is_running.py'))
