# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze
from recipe_engine import recipe_api

import common

SIMPLE_TESTS_TO_RUN = freeze([
  'content_gl_tests',
  'gl_tests',
  'angle_unittests',
  'gl_unittests'
])

SIMPLE_NON_OPEN_SOURCE_TESTS_TO_RUN = freeze([
  'gles2_conform_test',
])

DEQP_TESTS_TO_RUN = freeze([
  'angle_deqp_gles2_tests'
])

WIN_ONLY_DEQP_TESTS_TO_RUN = freeze([
  'angle_deqp_gles3_tests'
])

D3D9_TEST_NAME_MAPPING = freeze({
  'gles2_conform_test': 'gles2_conform_d3d9_test',
  'webgl_conformance': 'webgl_conformance_d3d9'
})

GL_TEST_NAME_MAPPING = freeze({
  'gles2_conform_test': 'gles2_conform_gl_test',
  'webgl_conformance': 'webgl_conformance_gl'
})

DONT_USE_GPU_IN_TESTS = freeze([
  'content_unittests',
])

class GpuApi(recipe_api.RecipeApi):
  def setup(self):
    """Call this once before any of the other APIs in this module."""

    # These values may be replaced by external configuration later
    self._dashboard_upload_url = 'https://chromeperf.appspot.com'
    self._gs_bucket_name = 'chromium-gpu-archive'

    # The infrastructure team has recommended not to use git yet on the
    # bots, but it's useful -- even necessary -- when testing locally.
    # To use, pass "use_git=True" as an argument to run_recipe.py.
    self._use_git = self.m.properties.get('use_git', False)

    self._configuration = 'chromium'
    if self.m.gclient.is_blink_mode:
      self._configuration = 'blink'

    config = self._configuration
    if self.m.platform.is_mac:
      config += '_clang'

    self.m.chromium.set_config(
      config, GIT_MODE=self._use_git)
    self.m.gclient.set_config(
      self._configuration, GIT_MODE=self._use_git)
    self.m.gclient.apply_config('chrome_internal')

    # To catch errors earlier on Release bots, in particular the try
    # servers which are Release mode only, force dcheck and blink
    # asserts on.
    self.m.chromium.apply_config('dcheck')

    # To more easily diagnose failures from logs, enable logging in
    # Blink Release builds.
    self.m.chromium.apply_config('blink_logging_on')

    # The FYI waterfall is being used to test linking Chrome Mac against the
    # OSX 10.10 SDK.
    if self.is_fyi_waterfall and self.m.platform.is_mac:
      self.m.chromium.apply_config('chromium_mac_sdk_10_10')

    # Use the default Ash and Aura settings on all bots (specifically Blink
    # bots).
    self.m.chromium.c.gyp_env.GYP_DEFINES.pop('use_ash', None)
    self.m.chromium.c.gyp_env.GYP_DEFINES.pop('use_aura', None)

    # Enable archiving the GPU tests' isolates in chrome_tests.gypi.
    # The non-GPU trybots build the "all" target, and these tests
    # shouldn't be built or run on those bots.
    self.m.chromium.c.gyp_env.GYP_DEFINES['archive_gpu_tests'] = 1

    # TODO(kbr): remove the workaround for http://crbug.com/328249 .
    # See crbug.com/335827 for background on the conditional.
    if not self.m.platform.is_win:
      self.m.chromium.c.gyp_env.GYP_DEFINES['disable_glibcxx_debug'] = 1

    # Don't skip the frame_rate data, as it's needed for the frame rate tests.
    # Per iannucci@, it can be relied upon that solutions[1] is src-internal.
    # Consider managing this in a 'gpu' config.
    del self.m.gclient.c.solutions[1].custom_deps[
        'src/chrome/test/data/perf/frame_rate/private']

    self.m.chromium.c.gyp_env.GYP_DEFINES['internal_gles2_conform_tests'] = 1

    # This recipe requires the use of isolates for running tests.
    self.m.isolate.set_isolate_environment(self.m.chromium.c)

    # The FYI waterfall is being used to test top-of-tree ANGLE with
    # Chromium on all platforms.
    if self.is_fyi_waterfall:
      self.m.gclient.c.solutions[0].custom_vars['angle_revision'] = (
          'refs/remotes/origin/master')

    # The GPU bots must test the hardware-accelerated video decode
    # paths in order to avoid breaking them in the product.
    self.m.chromium.apply_config('chrome_with_codecs')

    self._enable_swarming = False
    self._swarming_dimension_sets = None

  # TODO(martinis) change this to a property that grabs the revision
  # the first time its run, and then caches the value.
  def get_build_revision(self):
    """Returns the revision of the current build. The pixel and maps
    tests use this value when uploading error images to cloud storage,
    only for naming purposes. This could be changed to use a different
    identifier (for example, the build number on the slave), but using
    this value is convenient for easily identifying results."""
    # On the Blink bots, the 'revision' property alternates between a
    # Chromium and a Blink revision, so is not a good value to use.
    #
    # In all cases on the waterfall, the tester is triggered from a
    # builder which sends down parent_got_revision. The only situation
    # where this doesn't happen is when running the build_and_test
    # recipe locally for testing purposes.
    rev = self.m.properties.get('parent_got_revision')
    if rev:
      return rev
    # Fall back to querying the workspace as a last resort. This should
    # only be necessary on combined builder/testers, which isn't a
    # configuration which actually exists on any waterfall any more. If the
    # build_and_test recipe is being run locally and the checkout is being
    # skipped, then the 'parent_got_revision' property can be specified on
    # the command line as a workaround.
    return self._bot_update.presentation.properties['got_revision']

  # TODO(phajdan.jr): Remove and update callers.
  def get_webkit_revision(self):
    """Returns the webkit revision of the current build."""
    return self.get_build_revision()

  @property
  def _master_class_name_for_testing(self):
    """Allows the class name of the build master to be mocked for
    local testing by setting the build property
    "master_class_name_for_testing" on the command line. The bots do
    not need to, and should not, set this property. Class names follow
    the naming convention like "ChromiumWebkit" and "ChromiumGPU".
    This value is used by the flakiness dashboard when uploading
    results. See the documentation of the --master-class-name argument
    to runtest.py for full documentation."""
    return self.m.properties.get('master_class_name_for_testing')

  @property
  def is_fyi_waterfall(self):
    """Indicates whether the recipe is running on the GPU FYI waterfall."""
    return self.m.properties['mastername'] == 'chromium.gpu.fyi'

  @property
  def is_deqp_tester(self):
    """Indicates whether the receipe is running on the dEQP tester bot."""
    return 'dEQP' in self.m.properties['buildername']

  @property
  def is_angle_trybot(self):
    """Indicates whether the receipe is running on an ANGLE trybot."""
    return self.m.properties['mastername'] == 'tryserver.chromium.angle'

  def checkout_steps(self):
    self._bot_update = self.m.bot_update.ensure_checkout(force=True)

  def _trim_run(self, str):
    assert str.endswith('_run')
    return str[:-4]

  def compile_steps(self):
    # We only need to runhooks if we're going to compile locally.
    self.m.chromium.runhooks()
    # Since performance tests aren't run on the debug builders, it isn't
    # necessary to build all of the targets there.
    build_tag = '' if self.m.chromium.is_release_build else 'debug_'
    # It's harmless to process the isolate-related targets even if they
    # aren't supported on the current configuration (because the component
    # build is used).
    is_tryserver = self.m.tryserver.is_tryserver
    isolates = common.GPU_ISOLATES
    if self.is_fyi_waterfall:
      isolates += common.FYI_GPU_ISOLATES
      if self.m.platform.is_win or self.m.platform.is_linux:
        # TODO(kbr): run these tests on the trybots as soon as there is
        # capacity to do so, and on all platforms as soon as ANGLE does.
        isolates += common.WIN_AND_LINUX_ONLY_FYI_ONLY_GPU_ISOLATES
      if self.m.platform.is_win:
        isolates += common.WIN_ONLY_FYI_ONLY_GPU_ISOLATES
    targets = [u'%s_run' % test for test in isolates]
    self.m.isolate.clean_isolated_files(
        self.m.chromium.c.build_dir.join(self.m.chromium.c.build_config_fs))
    if is_tryserver:
      self.m.filter.does_patch_require_compile(
          self.m.tryserver.get_files_affected_by_patch(),
          exes=targets,
          compile_targets=targets,
          additional_names=['chromium'],
          config_file_name='trybot_analyze_config.json')
      if not self.m.filter.result:
        # Early out if no work to do.
        return
      targets = list(self.m.filter.matching_exes)
      # Re-sort the targets to keep test expectations stable.
      targets.sort()
      try:
        self.m.chromium.compile(targets, name='compile (with patch)')
      except self.m.step.StepFailure:
        if self.m.platform.is_win:
          self.m.chromium.taskkill()
        bot_update_json = self._bot_update.json.output
        self.m.gclient.c.revisions['src'] = str(
            bot_update_json['properties']['got_revision'])
        self.m.bot_update.ensure_checkout(force=True,
                                          patch=False,
                                          update_presentation=False)
        self.m.chromium.runhooks()
        self.m.chromium.compile(targets, name='compile (without patch)')
        raise
    else:
      self.m.chromium.compile(targets=targets, name='compile')
    # Map 'targets' back to the names of the isolates (not their _run
    # build targets) in order to let analyze.py properly subset the
    # tests that are run on the testers.
    isolates_to_run = [self._trim_run(n) for n in targets]
    # Archive isolated test targets.
    self.m.isolate.isolate_tests(
        self.m.chromium.c.build_dir.join(self.m.chromium.c.build_config_fs),
        isolates_to_run)

  def run_tests(self, api, chrome_revision, webkit_revision,
                enable_swarming=False, swarming_dimension_sets=None, suffix=''):
    tests = self.create_tests(chrome_revision, webkit_revision, enable_swarming,
                              swarming_dimension_sets)
    test_runner = self.m.chromium_tests.create_test_runner(api, tests, suffix)
    test_runner()

  def create_tests(self, chrome_revision, webkit_revision,
                   enable_swarming=False, swarming_dimension_sets=None):
    """Produces a list of the GPU tests to be run.

    When enable_swarming is set to False, create_tests returns a single batch of
    local tests, otherwise a batch for each set in swarming_dimension_sets is
    created and a list containing all tests in all batches is returned. This is
    convenient when tests must be triggered for multiple configurations.

    Parameter swarming_dimension_sets should be specified as a list of
    dictionaries, where each dictionary describes a set of dimensions that
    should be set before running each test in the respective batch.

    Returns:
      A list of Test objects (from steps.py in chromium module).
    """
    tests = []
    if enable_swarming:
      for swarming_dimensions in swarming_dimension_sets:
        tests.extend(self._create_test_batch(chrome_revision, webkit_revision,
                                             enable_swarming,
                                             swarming_dimensions))
    else:
      tests.extend(self._create_test_batch(chrome_revision, webkit_revision))
    return tests

  def _create_test_batch(self, chrome_revision, webkit_revision,
                         enable_swarming=False, swarming_dimensions=None):
    # TODO(kbr): currently some properties are passed to runtest.py via
    # factory_properties in the master.cfg: generate_gtest_json,
    # show_perf_results, test_results_server, and perf_id. runtest.py
    # should be modified to take these arguments on the command line,
    # and the setting of these properties should happen in this recipe
    # instead.

    # Note: we do not run the crash_service on Windows any more now
    # that these bots do not auto-reboot. There's no script which
    # tears it down, and the fact that it's live prevents new builds
    # from being unpacked correctly.

    # Until this is more fully tested, leave this cleanup step local
    # to the GPU recipe.
    if self.m.platform.is_linux:
      try:
        result = self.m.step('killall gnome-keyring-daemon',
                             ['killall', '-9', 'gnome-keyring-daemon'])
      except self.m.step.StepFailure as f:
        result = f.result
      result.presentation.status = self.m.step.SUCCESS

    tests = []

    # Run only the dEQP tests on the dEQP GPU bots.
    if self.is_deqp_tester:
      test_names = list(DEQP_TESTS_TO_RUN)
      if self.m.platform.is_win:
          test_names += WIN_ONLY_DEQP_TESTS_TO_RUN

      for test_name in test_names:
        tests.append(self._create_gtest(
            test_name, chrome_revision, webkit_revision, enable_swarming,
            swarming_dimensions))

      return tests

    # Copy the test list to avoid mutating it.
    basic_tests = list(SIMPLE_TESTS_TO_RUN)
    if self.is_fyi_waterfall:
      basic_tests += common.FYI_GPU_ISOLATES
      # Only run tests on the tree closers and on the CQ which are
      # available in the open-source repository.
      basic_tests += SIMPLE_NON_OPEN_SOURCE_TESTS_TO_RUN

    # Run the ANGLE tests on the ANGLE trybots
    if self.is_angle_trybot:
      basic_tests += common.ANGLE_TRYBOTS_GPU_ISOLATES
      if self.m.chromium.is_release_build and self.m.platform.is_win:
        basic_tests += common.WIN_ONLY_RELEASE_ONLY_ANGLE_TRYBOTS_GPU_ISOLATES

    #TODO(martiniss) convert loop
    for test in basic_tests:
      args = [''] if test in DONT_USE_GPU_IN_TESTS else ['--use-gpu-in-tests']
      tests.append(self._create_gtest(test, chrome_revision, webkit_revision,
                                      enable_swarming, swarming_dimensions,
                                      args=args))

    # Run closed source tests with ANGLE-D3D9 and ANGLE-GL
    if self.is_fyi_waterfall and self.m.platform.is_win:
      for test in SIMPLE_NON_OPEN_SOURCE_TESTS_TO_RUN:
        tests.append(self._create_gtest(
            D3D9_TEST_NAME_MAPPING[test], chrome_revision, webkit_revision,
            enable_swarming, swarming_dimensions,
            args=['--use-gpu-in-tests', '--use-angle=d3d9'], target_name=test))
        tests.append(self._create_gtest(
            GL_TEST_NAME_MAPPING[test], chrome_revision, webkit_revision,
            enable_swarming, swarming_dimensions, target_name=test,
            args=[
              '--use-gpu-in-tests',
              '--use-angle=gl',
              '--disable-gpu-sandbox', # TODO(geofflang): Remove dependency on
                                       # the sandbox being disabled to use WGL
            ]))

    # Google Maps Pixel tests.
    tests.append(self._create_telemetry_test(
      'maps_pixel_test', chrome_revision, webkit_revision, enable_swarming,
      swarming_dimensions, target_name='maps',
      args=[
        '--build-revision',
        str(chrome_revision),
        '--test-machine-name',
        self.m.properties['buildername']
      ]))

    # Pixel tests.
    # Try servers pull their results from cloud storage; the other
    # tester bots send their results to cloud storage.
    #
    # NOTE that ALL of the bots need to share a bucket. They can't be split
    # by mastername/waterfall, because the try servers are on a different
    # waterfall (tryserver.chromium.*) than the other test bots (chromium.gpu
    # and chromium.webkit, as of this writing). This means there will be
    # races between bots with identical OS/GPU combinations, on different
    # waterfalls, attempting to upload results for new versions of each
    # pixel test. If this is a significant problem in practice then we will
    # have to rethink the cloud storage code in the pixel tests.
    ref_img_arg = '--upload-refimg-to-cloud-storage'
    if self.m.tryserver.is_tryserver:
      ref_img_arg = '--download-refimg-from-cloud-storage'
    cloud_storage_bucket = 'chromium-gpu-archive/reference-images'
    tests.append(self._create_telemetry_test(
        'pixel_test', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions, target_name='pixel',
        args=[
            '--build-revision',
            str(chrome_revision),
            ref_img_arg,
            '--refimg-cloud-storage-bucket',
            cloud_storage_bucket,
            '--os-type',
            self.m.chromium.c.TARGET_PLATFORM,
            '--test-machine-name',
            self.m.properties['buildername']
        ]))

    # WebGL conformance tests.
    tests.append(self._create_telemetry_test(
        'webgl_conformance', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions))

    # Run extra WebGL conformance tests in Windows FYI GPU bots with
    # ANGLE OpenGL/D3D9.
    # This ensures the ANGLE OpenGL/D3D9 gets some testing
    if ((self.is_fyi_waterfall or self.is_angle_trybot) and
        self.m.platform.is_win):
      tests.append(self._create_telemetry_test(
          D3D9_TEST_NAME_MAPPING['webgl_conformance'], chrome_revision,
          webkit_revision, enable_swarming, swarming_dimensions,
          target_name='webgl_conformance',
          extra_browser_args=['--use-angle=d3d9']))

      tests.append(self._create_telemetry_test(
          GL_TEST_NAME_MAPPING['webgl_conformance'], chrome_revision,
          webkit_revision, enable_swarming, swarming_dimensions,
          target_name='webgl_conformance',
          extra_browser_args=[
            '--use-gpu-in-tests',
            '--use-angle=gl',
            '--disable-gpu-sandbox', # TODO(geofflang): Remove dependency on
                                     # the sandbox being disabled to use WGL
          ]))

    # Run WebGL 2 conformance tests in FYI GPU bots
    if self.is_fyi_waterfall:
      tests.append(self._create_telemetry_test(
          'webgl2_conformance', chrome_revision, webkit_revision,
          enable_swarming, swarming_dimensions, target_name='webgl_conformance',
          args=['--webgl-conformance-version=2.0.0', '--webgl2-only=true']))

    # Context lost tests.
    tests.append(self._create_telemetry_test(
        'context_lost', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions))

    # Memory tests.
    tests.append(self._create_telemetry_test(
        'memory_test', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions))

    # Tracing tests.
    tests.append(self._create_telemetry_test(
        'trace_test', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions))

    # Screenshot synchronization tests.
    tests.append(self._create_telemetry_test(
        'screenshot_sync', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions))

    # Hardware acceleration tests.
    tests.append(self._create_telemetry_test(
        'hardware_accelerated_feature', chrome_revision, webkit_revision,
        enable_swarming, swarming_dimensions))

    # GPU process launch tests.
    tests.append(self._create_telemetry_test(
        'gpu_process_launch', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions, target_name='gpu_process'))

    # Smoke test for gpu rasterization of web content.
    tests.append(self._create_telemetry_test(
        'gpu_rasterization', chrome_revision, webkit_revision, enable_swarming,
        swarming_dimensions,
        args=[
          '--build-revision', str(chrome_revision),
          '--test-machine-name', self.m.properties['buildername']
        ]))

    # Tab capture end-to-end (correctness) tests.
    # This test is unfortunately disabled in Debug builds and the lack
    # of logs is causing alerts. Skip it on Debug bots. crbug.com/403012
    if self.m.chromium.is_release_build:
      tests.append(self._create_gtest(
          'tab_capture_end2end_tests', chrome_revision, webkit_revision,
          enable_swarming, swarming_dimensions))

    # Run GPU unit tests on FYI bots.
    if self.is_fyi_waterfall:
      tests.append(self._create_gtest(
          'gpu_unittests', chrome_revision, webkit_revision, enable_swarming,
          swarming_dimensions))

    # Remove empty entries as some tests may be skipped.
    tests = [test for test in tests if test]

    return tests

  def _get_gpu_suffix(self, dimensions):
    if dimensions is None:
      return None

    gpu_vendor_id = dimensions.get('gpu', '').split(':')[0].lower()
    if gpu_vendor_id == '8086':
      gpu_vendor = 'Intel'
    # TODO(sergiyb): Mocking various vendors IDs is currently difficult as they
    # are hard coded in the recipe. When we'll move the configs to an external
    # json file read in a dedicated step whose data can be overriden, we should
    # create tests for all GPUs and remove no-cover pragmas below.
    elif gpu_vendor_id == '10de':  # pragma: no cover
      gpu_vendor = 'NVIDIA'
    elif gpu_vendor_id == '1002':  # pragma: no cover
      gpu_vendor = 'ATI'
    else:
      gpu_vendor = '(%s)' % gpu_vendor_id  # pragma: no cover

    os = dimensions.get('os', '')
    if os.startswith('Mac'):
      if dimensions.get('hidpi', '') == '1':
        os_name = 'Mac Retina'
      else:
        os_name = 'Mac'
    elif os.startswith('Windows'):  # pragma: no cover
      os_name = 'Windows'
    else:  # pragma: no cover
      os_name = 'Linux'

    return 'on %s GPU on %s' % (gpu_vendor, os_name)

  def _create_gtest(self, name, chrome_revision, webkit_revision,
                    enable_swarming, swarming_dimensions,
                    args=[], target_name=None):
    # The step test must end in 'test' or 'tests' in order for the results to
    # automatically show up on the flakiness dashboard.
    #
    # Currently all tests on the GPU bots follow this rule, so we can't add
    # code like in chromium/api.py, run_telemetry_test.

    target_name = target_name or name
    assert target_name.endswith('test') or target_name.endswith('tests')

    results_directory = self.m.path['slave_build'].join('gtest-results', name)
    return self.m.chromium_tests.steps.GPUGTestTest(
        name,
        xvfb=False,
        args=args,
        target_name=target_name,
        use_isolate=True,
        generate_json_file=True,
        results_directory=results_directory,
        revision=chrome_revision,
        webkit_revision=webkit_revision,
        master_class_name=self._master_class_name_for_testing,
        enable_swarming=enable_swarming,
        swarming_dimensions=swarming_dimensions,
        swarming_extra_suffix=self._get_gpu_suffix(swarming_dimensions))

  def _create_telemetry_test(self, name, chrome_revision, webkit_revision,
                             enable_swarming, swarming_dimensions,
                             args=None, target_name=None,
                             extra_browser_args=None):
    test_args = ['-v', '--use-devtools-active-port']
    if args:
      test_args.extend(args)
    # --expose-gc allows the WebGL conformance tests to more reliably
    # reproduce GC-related bugs in the V8 bindings.
    extra_browser_args_string = (
        '--extra-browser-args=--enable-logging=stderr --js-flags=--expose-gc')
    if self.m.platform.is_mac and not self.m.tryserver.is_tryserver:
      #TODO(zmo): remove the vmodule flag after crbug.com/424024 is fixed.
      vmodules = [
        'startup_browser_creator=2'
      ]
      extra_browser_args_string += ' --vmodule=' + ','.join(vmodules)
    if extra_browser_args:
      extra_browser_args_string += ' ' + ' '.join(extra_browser_args)
    test_args.append(extra_browser_args_string)

    return self.m.chromium_tests.steps.TelemetryGPUTest(
        name, chrome_revision, webkit_revision, args=test_args,
        target_name=target_name, enable_swarming=enable_swarming,
        swarming_dimensions=swarming_dimensions,
        master_class_name=self._master_class_name_for_testing,
        swarming_extra_suffix=self._get_gpu_suffix(swarming_dimensions))
