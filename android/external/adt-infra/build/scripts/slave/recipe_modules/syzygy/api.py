# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ast
import os
import re

from recipe_engine import recipe_api


class SyzygyApi(recipe_api.RecipeApi):
  # Used for constructing URLs to the Syzygy archives.
  _SYZYGY_ARCHIVE_URL = (
      'https://syzygy-archive.commondatastorage.googleapis.com')
  _SYZYGY_GS = 'gs://syzygy-archive'
  _SYZYGY_GITHUB = 'https://github.com/google/syzygy/commit/'

  # Fake unittests.gypi contents.
  _FAKE_UNITTESTS_GYPI_DATA = repr({
    'variables': {
      'unittests': [
        '<(src)/syzygy/agent/asan/asan.gyp:foo_unittests',
        '<(src)/syzygy/agent/common/common.gyp:bar_unittests',
        '<(src)/syzygy/agent/coverage/coverage.gyp:baz_unittests',
      ]
    }
  })

  # Fake version file data.
  _FAKE_VERSION_DATA = """# Copyright 2012 Google Inc. All Rights Reserved.
#
# Boilerplate!
#
#      http://url/to/nowhere
#
# And some more boilerplate, followed by a blank line!

MAJOR=0
MINOR=0
BUILD=0
PATCH=1
"""

  def __init__(self, *args, **kwargs):
    super(SyzygyApi, self).__init__(*args, **kwargs)
    # This is populated by the first call to 'version'.
    self._version = None
    # This is populated by the sync step.
    self._revision = None

  @property
  def build_dir(self):
    """Returns the build directory for the project."""
    build_tool = self.m.chromium.c.compile_py.build_tool
    if build_tool == 'vs':
      return self.m.path['checkout'].join('build')
    if build_tool == 'ninja':
      return self.m.path['checkout'].join('out')

  @property
  def output_dir(self):
    """Returns the configuration-specific output directory for the project."""
    return self.build_dir.join(self.m.chromium.c.BUILD_CONFIG)

  @property
  def public_scripts_dir(self):
    """Returns the public Syzygy build scripts directory."""
    return self.m.path['checkout'].join('syzygy', 'build')

  @property
  def internal_scripts_dir(self):
    """Returns the internal Syzygy build scripts directory."""
    return self.m.path['checkout'].join('syzygy', 'internal', 'build')

  @property
  def version(self):
    """Returns the version tuple associated with the checkout."""
    # Only read the value if it hasn't yet been read.
    if not self._version:
      version = self.c.version_file
      version = self.m.file.read('read_version', version,
                                 test_data=self._FAKE_VERSION_DATA)
      d = {}
      for l in version.splitlines():
        # Look for a 'NAME=VALUE' pair.
        m = re.match('^\s*([A-Z]+)\s*=\s*(\d+)\s*$', l)
        if not m:
          continue
        key = m.group(1)
        value = m.group(2)
        d[key] = int(value)
      self._version = (d['MAJOR'], d['MINOR'], d['BUILD'], d['PATCH'])

    # Return the cached value.
    return self._version

  @property
  def revision(self):
    """Returns the revision that is inferred by the gclient step.

    If this is not yet set then returns the global 'revision' property. If this
    is not yet set, then simply returns an empty string.
    """
    r = ''
    if 'revision' in self.m.properties:
      r = self.m.properties['revision']
    if self._revision:
      r = self._revision
    return r

  def _gen_step_gs_util_cp_dir(self, step_name, src_dir, dst_rel_path):
    """Returns a gsutil_cp_dir step. Internal use only.

    Args:
      step_name: The step name as a string.
      src_dir: The source directory on the local file system. This should be a
          Path object.
      dst_rel_path: The destination path relative to the syzygy_archive root.
          This should be a string.

    Returns:
      The generated python step.
    """
    gsutil_cp_dir_py = self.m.path['build'].join(
        'scripts', 'slave', 'syzygy', 'gsutil_cp_dir.py')
    dst_dir = '%s/%s' % (self._SYZYGY_GS, dst_rel_path)
    args = ['--public-read', src_dir, dst_dir]
    return self.m.python(step_name, gsutil_cp_dir_py, args)

  def _gen_step_gs_util_cp(self, step_name, src_path, dst_rel_path):
    """Returns a gsutil.py step. Internal use only.

    Args:
      step_name: The step name as a string.
      src_path: The source path on the local file system. This should be a
          Path object.
      dst_rel_path: The destination path relative to the syzygy_archive root.
          This should be a string.

    Returns:
      The generated python step.
    """
    gsutil_bat = self.m.path['build'].join(
        'scripts', 'slave', 'gsutil.bat')
    dst_dir = '%s/%s' % (self._SYZYGY_GS, dst_rel_path)
    args = ['cp', '-t', '-a', 'public-read', src_path, dst_dir]
    return self.m.step(step_name, [gsutil_bat] + list(args or []))

  def taskkill(self):
    """Run chromium.taskkill.

    This invokes a dummy step on the test slave as killing all instances of
    Chrome seriously impairs development.
    """
    if self.m.properties['slavename'] == 'fake_slave':
      return self.m.python.inline('taskkill', 'print "dummy taskkill"')
    return self.m.chromium.taskkill()

  def checkout(self):
    """Checks out the Syzygy code using the current gclient configuration."""
    step = self.m.gclient.checkout()
    self._revision = step.presentation.properties['got_revision']
    github_url = self._SYZYGY_GITHUB + str(self._revision)
    step.presentation.links[self._revision] = github_url
    return step

  def runhooks(self):
    return self.m.chromium.runhooks()

  def compile(self):
    """Generates a step to compile the project."""
    # TODO(chrisha): Migrate this to Ninja!
    return self.m.chromium.compile()

  def read_unittests_gypi(self):
    """Reads and parses unittests.gypi from the checkout, returning a list."""
    gypi = self.c.unittests_gypi
    gypi = self.m.file.read('read_unittests_gypi', gypi,
                            test_data=self._FAKE_UNITTESTS_GYPI_DATA)
    gypi = ast.literal_eval(gypi)
    unittests = [t.split(':')[1] for t in gypi['variables']['unittests']]
    return sorted(unittests)

  def run_unittests(self, unittests):
    # Set up the environment. This ensures that the tests emit metrics to a
    # global log.
    # TODO(chrisha): Make this use JSON, and make the log be specified on the
    #     command-line.
    os.environ['SYZYGY_UNITTEST_METRICS'] = '--emit-to-log'

    # Generate a test step for each unittest.
    app_verifier_py = self.public_scripts_dir.join('app_verifier.py')
    for unittest in unittests:
      unittest_path = self.output_dir.join(unittest + '.exe')
      args = ['--on-waterfall',
              unittest_path,
              '--',
              # Arguments to the actual gtest unittest.
              '--single-process-tests',  # Our VMs are single core.
              '--test-launcher-timeout=300000',  # 5 minutes in milliseconds.
              '--gtest_print_time']
      self.m.chromium.runtest(app_verifier_py, args, name=unittest,
                              test_type=unittest)

  def randomly_reorder_chrome(self):
    """Returns a test step that randomly reorders Chrome and ensures it runs."""
    randomize_chrome_py = self.internal_scripts_dir.join(
        'randomize_chrome.py')
    args = ['--build-dir', self.build_dir,
            '--target', self.m.chromium.c.BUILD_CONFIG,
            '--verbose']
    return self.m.python('randomly_reorder_chrome', randomize_chrome_py, args)

  def benchmark_chrome(self):
    """Returns a test step that benchmarks an optimized Chrome."""
    benchmark_chrome_py = self.internal_scripts_dir.join(
        'benchmark_chrome.py')
    args = ['--build-dir', self.build_dir,
            '--target', self.m.chromium.c.BUILD_CONFIG,
            '--verbose']
    return self.m.python('benchmark_chrome',  benchmark_chrome_py, args)

  def capture_unittest_coverage(self):
    """Returns a step that runs the coverage script.

    Only meant to be called from the 'Coverage' configuration.
    """
    assert self.m.chromium.c.BUILD_CONFIG == 'Coverage'
    generate_coverage_py = self.public_scripts_dir.join(
        'generate_coverage.py')
    args = ['--verbose',
            '--syzygy',
            '--build-dir', self.output_dir]
    return self.m.python(
        'capture_unittest_coverage', generate_coverage_py, args)

  def archive_coverage(self):
    """Returns a step that archives the coverage report.

    Only meant to be called from the 'Coverage' configuration.
    """
    assert self.m.chromium.c.BUILD_CONFIG == 'Coverage'
    cov_dir = self.output_dir.join('cov')
    archive_path = 'builds/coverage/%s' % self.revision
    if self.m.properties['slavename'] == 'fake_slave':
      archive_path = 'test/' + archive_path
    report_url = '%s/%s/index.html' % (self._SYZYGY_ARCHIVE_URL, archive_path)
    step = self._gen_step_gs_util_cp_dir(
        'archive_coverage', cov_dir, archive_path)
    step.presentation.links['coverage_report'] = report_url
    return step

  def archive_binaries(self):
    """Returns a step that archives the official binaries.

    Only meant to be called from an official build.
    """
    assert self.m.chromium.c.BUILD_CONFIG == 'Release' and self.c.official_build
    bin_dir = self.output_dir.join('archive')
    archive_path = 'builds/official/%s' % self.revision
    if self.m.properties['slavename'] == 'fake_slave':
      archive_path = 'test/' + archive_path
    bin_url = '%s/index.html?path=%s/' % (
        self._SYZYGY_ARCHIVE_URL, archive_path)
    link_text = '.'.join(str(i) for i in self.version) + ' archive'
    step = self._gen_step_gs_util_cp_dir(
        'archive_binaries', bin_dir, archive_path)
    step.presentation.links[link_text] = bin_url
    return step

  def upload_symbols(self):
    """Returns a step that source indexes and uploads symbols.

    Only meant to be called from an official build.
    """
    assert self.m.chromium.c.BUILD_CONFIG == 'Release' and self.c.official_build
    archive_symbols_py = self.m.path['checkout'].join(
        'syzygy', 'internal', 'scripts', 'archive_symbols.py')
    asan_rtl_dll = self.output_dir.join('*asan_rtl.dll')
    client_dlls = self.output_dir.join('*client.dll')
    args = ['-s', '-b', asan_rtl_dll, client_dlls]
    return self.m.python('upload_symbols', archive_symbols_py, args)

  def upload_kasko_symbols(self):
    """Returns a step that source indexes and uploads symbols for Kasko.

    Only meant to be called from an official build.
    """
    assert self.m.chromium.c.BUILD_CONFIG == 'Release' and self.c.official_build
    archive_symbols_py = self.m.path['checkout'].join(
        'syzygy', 'internal', 'scripts', 'archive_symbols.py')
    kasko_dll = self.output_dir.join('*kasko.dll')
    args = ['-s', '-b', kasko_dll]
    return self.m.python('upload_symbols', archive_symbols_py, args)

  def clobber_metrics(self):
    """Returns a step that clobbers an existing metrics file."""
    # TODO(chrisha): Make this whole thing use the JSON output mechanism.
    return self.m.file.rmwildcard('metrics.csv', self.output_dir)

  def archive_metrics(self):
    """Returns a step that archives any metrics collected by the unittests.
    This can be called from any build configuration.
    """
    # Determine the name of the archive.
    config = self.m.chromium.c.BUILD_CONFIG
    if config == 'Release' and self.c.official_build:
      config = 'Official'
    archive_path = 'builds/metrics/%s/%s.csv' % (self.revision, config.lower())
    step = self._gen_step_gs_util_cp(
        'archive_metrics', self.output_dir.join('metrics.csv'), archive_path)
    url = '%s/index.html?path=%s/' % (
        self._SYZYGY_ARCHIVE_URL, archive_path)
    step.presentation.links['archive'] = url
    return step

  def download_binaries(self):
    """Returns a step that downloads the current official binaries."""
    get_syzygy_binaries_py = self.public_scripts_dir.join(
        'get_syzygy_binaries.py')
    output_dir = self.m.path['checkout'].join('syzygy', 'binaries')
    args = ['--output-dir', output_dir,
            '--revision', self.revision,
            '--overwrite',
            '--verbose']
    return self.m.python('download_binaries', get_syzygy_binaries_py, args)

  def smoke_test(self):
    """Returns a step that launches the smoke test script."""
    smoke_test_py = self.internal_scripts_dir.join('smoke_test.py')
    build_dir = self.m.path['checkout'].join('build')
    args = ['--verbose', '--build-dir', build_dir]
    return self.m.python('smoke_test', smoke_test_py, args)
