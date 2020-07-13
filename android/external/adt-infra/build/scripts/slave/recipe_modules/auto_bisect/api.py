# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""API for the bisect recipe module.

This API is meant to enable the bisect recipe to bisect any chromium-supported
platform for any test that can be run via buildbot, perf or otherwise.
"""

from recipe_engine import recipe_api
from . import bisector
from . import perf_revision_state

BISECT_CONFIG_FILE = 'tools/auto_bisect/bisect.cfg'


class AutoBisectApi(recipe_api.RecipeApi):
  """A module for bisect specific functions."""

  # Number of seconds to wait between polls for test results
  POLLING_INTERVAL = 60
  # GS bucket to use for communicating results and job state between bisector
  # and tester bots
  BUCKET = 'chrome-perf'
  # Directory within the above bucket to store results
  RESULTS_GS_DIR = 'bisect-results'
  GS_RESULTS_URL = 'gs://%s/%s/' % (BUCKET, RESULTS_GS_DIR)
  # Repo for triggering build jobs
  SVN_REPO_URL = 'svn://svn.chromium.org/chrome-try/try-perf'
  # Email to send on try jobs (for build requests) since git try will not
  # necessarily rely on a local checkout for that information
  BOT_EMAIL = 'chrome_bot@chromium.org'

  def __init__(self, *args, **kwargs):
    super(AutoBisectApi, self).__init__(*args, **kwargs)
    self.override_poll_interval = None

  def create_bisector(self, bisect_config_dict, dummy_mode=False):
    """Passes the api and the config dictionary to the Bisector constructor.

    For details about the keys in the bisect config dictionary go to:
    http://chromium.org/developers/speed-infra/perf-try-bots-bisect-bots/config

    Args:
      bisect_config_dict (dict): Contains the configuration for the bisect job.
      dummy_mode (bool): In dummy mode we prevent the bisector for expanding
        the revision range at construction, to avoid the need for lots of step
        data when performing certain tests (such as results output).
    """
    self.override_poll_interval = bisect_config_dict.get('poll_sleep')
    revision_class = self._get_revision_class(bisect_config_dict['test_type'])
    return bisector.Bisector(self, bisect_config_dict, revision_class,
                             init_revisions=not dummy_mode)

  def _get_revision_class(self, test_type):
    """Gets the particular subclass of Revision needed for the test type."""
    return perf_revision_state.PerfRevisionState

  def gsutil_file_exists(self, path):
    """Returns True if a file exists at the given GS path."""
    try:
      self.m.gsutil(['ls', path])
    except self.m.step.StepFailure:  # pragma: no cover
      return False
    return True

  def query_revision_info(self, revision, git_checkout_dir=None):
    """Gathers information on a particular revision, such as author's name,
    email, subject, and date.

    Args:
      revision (str): Revision you want to gather information on; a git
        commit hash.
      git_checkout_dir (slave.recipe_config_types.Path): A path to run git
        from.

    Returns:
      A dict in the following format:
      {
        'author': %s,
        'email': %s,
        'date': %s,
        'subject': %s,
        'body': %s,
      }
    """
    if not git_checkout_dir:
      git_checkout_dir = self.m.path['checkout']

    separator = 'S3P4R4T0R'
    formats = separator.join(['%aN', '%aE', '%s', '%cD', '%b'])
    targets = ['author', 'email', 'subject', 'date', 'body']
    command_parts = ['log', '--format=%s' % formats, '-1', revision]

    step_result = self.m.git(*command_parts,
                             name='Reading culprit cl information.',
                             cwd=git_checkout_dir,
                             stdout=self.m.raw_io.output())
    return dict(zip(targets, step_result.stdout.split(separator)))

  def run_bisect_script(self, extra_src='', path_to_config='', **kwargs):
    """Executes run-perf-bisect-regression.py to perform bisection.

    Args:
      extra_src (str): Path to extra source file. If this is supplied,
        bisect script will use this to override default behavior.
      path_to_config (str): Path to the config file to use. If this is supplied,
        the bisect script will use this to override the default config file
        path.
    """
    self.m.python(
        'Preparing for Bisection',
        script=self.m.path['checkout'].join(
            'tools', 'prepare-bisect-perf-regression.py'),
        args=['-w', self.m.path['slave_build']])
    args = []

    kwargs['allow_subannotations'] = True

    if extra_src:
      args = args + ['--extra_src', extra_src]
    if path_to_config:
      args = args + ['--path_to_config', path_to_config]

    if self.m.chromium.c.TARGET_PLATFORM != 'android':
      args += ['--path_to_goma', self.m.path['build'].join('goma')]
    args += [
        '--build-properties',
        self.m.json.dumps(dict(self.m.properties.legacy())),
    ]
    self.m.chromium.runtest(
        self.m.path['checkout'].join('tools', 'run-bisect-perf-regression.py'),
        ['-w', self.m.path['slave_build']] + args,
        name='Running Bisection',
        xvfb=True, **kwargs)

  def start_test_run_for_bisect(self, api, update_step, master_dict):
    mastername = api.properties.get('mastername')
    buildername = api.properties.get('buildername')
    bot_config = master_dict.get('builders', {}).get(buildername)
    api.bisect_tester.upload_job_url()
    if api.chromium.c.TARGET_PLATFORM == 'android':
      # The best way to ensure the old build directory is not used is to
      # remove it.
      build_dir = self.m.chromium.c.build_dir.join(
          self.m.chromium.c.build_config_fs)
      self.m.file.rmtree('build directory', build_dir)

      # crbug.com/535218, the way android builders on tryserver.chromium.perf
      # are archived is different from builders on chromium.per. In order to
      # support both forms of archives we added this temporary hack until
      # builders are fixed.
      zip_dir = self.m.path.join(self.m.path['checkout'], 'full-build-linux')
      if self.m.path.exists(zip_dir):  # pragma: no cover
        self.m.file.rmtree('full-build-linux directory', zip_dir)

      api.chromium_android.download_build(bucket=bot_config['bucket'],
                                          path=bot_config['path'](api))

      # crbug.com/535218, the way android builders on tryserver.chromium.perf
      # are archived is different from builders on chromium.per. In order to
      # support both forms of archives we added this temporary hack until
      # builders are fixed.
      if self.m.path.exists(zip_dir):  # pragma: no cover
        self.m.python.inline(
            'moving full-build-linux to out/Release ',
            """
            import shutil
            import sys
            shutil.move(sys.argv[1], sys.argv[2])
            """,
            args=[zip_dir, build_dir],
        )
    else:
      api.chromium_tests.tests_for_builder(
          mastername,
          buildername,
          update_step,
          master_dict,
          override_bot_type='tester')

    tests = [api.chromium_tests.steps.BisectTest()]

    if not tests:  # pragma: no cover
      return
    api.chromium_tests.configure_swarming(  # pragma: no cover
        'chromium', precommit=False, mastername=mastername)
    test_runner = api.chromium_tests.create_test_runner(api, tests)

    with api.chromium_tests.wrap_chromium_tests(mastername, tests):
      if api.chromium.c.TARGET_PLATFORM == 'android':
        api.chromium_android.adb_install_apk('ChromePublic.apk')
      test_runner()

  def start_try_job(self, api, update_step=None, master_dict=None, extra_src='',
                    path_to_config='', **kwargs):
    if master_dict is None:  # pragma: no cover
      master_dict = {}
    affected_files = self.m.tryserver.get_files_affected_by_patch()

    # Avoid duplication of device setup steps for bisect recipe tester which
    # is ran while executing tests in chromium_tests.wrap_chromium_tests and
    # also we don't want to execute runhooks since this just tests the build.
    if (api.properties.get('bisect_config') is None and
        api.chromium.c.TARGET_PLATFORM == 'android'):
      api.chromium_android.common_tests_setup_steps(perf_setup=True)
      api.chromium.runhooks()
    try:
      # Run legacy bisect script if the patch contains bisect.cfg.
      if BISECT_CONFIG_FILE in affected_files:
        self.run_bisect_script(extra_src='', path_to_config='', **kwargs)
      elif api.properties.get('bisect_config'):
        self.start_test_run_for_bisect(api, update_step, master_dict)
      else:
        self.m.perf_try.start_perf_try_job(
            affected_files, update_step, master_dict)
    finally:
      # Avoid duplication of device setup steps for bisect recipe tester, which
      # are run while running tests in chromium_tests.wrap_chromium_tests.
      if (api.properties.get('bisect_config') is None and
          api.chromium.c.TARGET_PLATFORM == 'android'):
        api.chromium_android.common_tests_final_steps()
