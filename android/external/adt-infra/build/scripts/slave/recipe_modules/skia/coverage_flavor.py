# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import datetime
import default_flavor
import posixpath
import ssh_devices


"""Utils for running coverage tests."""


class CoverageFlavorUtils(default_flavor.DefaultFlavorUtils):
  def compile(self, target):
    """Build the given target."""
    cmd = [self._skia_api.m.path['slave_build'].join('skia', 'tools',
                                                     'llvm_coverage_build'),
           target]
    self._skia_api.run(self._skia_api.m.step, 'build %s' % target, cmd=cmd,
                       cwd=self._skia_api.m.path['checkout'])

  def step(self, name, cmd, **kwargs):
    """Run the given step through coverage."""
    # Slice out the 'key' and 'properties' arguments to be reused.
    key = []
    properties = []
    current = None
    for i in xrange(0, len(cmd)):
      if isinstance(cmd[i], basestring) and cmd[i] == '--key':
        current = key
      elif isinstance(cmd[i], basestring) and cmd[i] == '--properties':
        current = properties
      elif isinstance(cmd[i], basestring) and cmd[i].startswith('--'):
        current = None
      if current is not None:
        current.append(cmd[i])

    results_dir = self._skia_api.out_dir.join('coverage_results')
    self.create_clean_host_dir(results_dir)

    # Run DM under coverage.
    report_file_basename = '%s.cov' % self._skia_api.got_revision
    report_file = results_dir.join(report_file_basename)
    args = [
        'python',
        self._skia_api.m.path['slave_build'].join('skia', 'tools',
                                                  'llvm_coverage_run.py'),
    ] + cmd + ['--outResultsFile', report_file]
    self._skia_api.run(self._skia_api.m.step, name=name, cmd=args,
                       cwd=self._skia_api.m.path['checkout'], **kwargs)

    # Generate nanobench-style JSON output from the coverage report.
    git_timestamp = self._skia_api.m.git.get_timestamp(test_data='1408633190',
                                                       infra_step=True)
    nanobench_json = results_dir.join('nanobench_%s_%s.json' % (
        self._skia_api.got_revision, git_timestamp))
    line_by_line_basename = ('coverage_by_line_%s_%s.json' % (
        self._skia_api.got_revision, git_timestamp))
    line_by_line = results_dir.join(line_by_line_basename)
    args = [
        'python',
        self._skia_api.m.path['slave_build'].join('skia', 'tools',
                                                  'parse_llvm_coverage.py'),
        '--report', report_file, '--nanobench', nanobench_json,
        '--linebyline', line_by_line]
    args.extend(key)
    args.extend(properties)
    self._skia_api.run(
        self._skia_api.m.step,
        'Generate Coverage Data',
        cmd=args, cwd=self._skia_api.m.path['checkout'])

    # Upload raw coverage data.
    now = self._skia_api.m.time.utcnow()
    gs_json_path = '/'.join((
        str(now.year).zfill(4), str(now.month).zfill(2),
        str(now.day).zfill(2), str(now.hour).zfill(2),
        self._skia_api.builder_name,
        str(self._skia_api.m.properties['buildnumber'])))
    if self._skia_api.is_trybot:
      gs_json_path = '/'.join(('trybot', gs_json_path,
                               str(self._skia_api.m.properties['issue'])))

    self._skia_api.gsutil_upload(
        'upload raw coverage data',
        source=report_file,
        bucket='skia-infra',
        dest='/'.join(('coverage-raw-v1', gs_json_path, report_file_basename)))

    # Upload nanobench JSON data.
    gsutil_path = self._skia_api.m.path['depot_tools'].join(
        'third_party', 'gsutil', 'gsutil')
    upload_args = [self._skia_api.builder_name,
                   self._skia_api.m.properties['buildnumber'],
                   results_dir,
                   self._skia_api.got_revision, gsutil_path]
    if self._skia_api.is_trybot:
      upload_args.append(self._skia_api.m.properties['issue'])
    self._skia_api.run(
        self._skia_api.m.python,
        'upload nanobench coverage results',
        script=self._skia_api.resource('upload_bench_results.py'),
        args=upload_args,
        cwd=self._skia_api.m.path['checkout'],
        abort_on_failure=False,
        infra_step=True)

    # Upload line-by-line coverage data.
    self._skia_api.gsutil_upload(
        'upload line-by-line coverage data',
        source=line_by_line,
        bucket='skia-infra',
        dest='/'.join(('coverage-json-v1', gs_json_path,
                       line_by_line_basename)))

