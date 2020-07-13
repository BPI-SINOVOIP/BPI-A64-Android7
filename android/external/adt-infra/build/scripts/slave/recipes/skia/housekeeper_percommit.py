# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


# Recipe for the Skia PerCommit Housekeeper.


DEPS = [
  'path',
  'properties',
  'python',
  'skia',
  'step',
]


def RunSteps(api):
  # Checkout, compile, etc.
  api.skia.gen_steps()

  cwd = api.path['checkout']

  api.skia.run(
    api.step,
    'android platform self-tests',
    cmd=['python',
         cwd.join('platform_tools', 'android', 'tests', 'run_all.py')],
    cwd=cwd,
    abort_on_failure=False)

  # TODO(borenet): Detect static initializers?

  gsutil_path = api.path['depot_tools'].join('third_party', 'gsutil',
                                             'gsutil')
  if not api.skia.is_trybot:
    api.skia.run(
      api.step,
      'generate and upload doxygen',
      cmd=['python', api.skia.resource('generate_and_upload_doxygen.py'),
           gsutil_path],
      cwd=cwd,
      abort_on_failure=False)

  cmd = ['python', api.skia.resource('run_binary_size_analysis.py'),
         '--library', api.skia.out_dir.join('Release', 'lib', 'libskia.so'),
         '--githash', api.skia.got_revision,
         '--commit_ts', api.skia.m.git.get_timestamp(test_data='1408633190'),
         '--gsutil_path', gsutil_path]
  if api.skia.is_trybot:
    cmd.extend(['--issue_number', str(api.skia.m.properties['issue'])])
  api.skia.run(
    api.step,
    'generate and upload binary size data',
    cmd=cmd,
    cwd=cwd,
    abort_on_failure=False)

def GenTests(api):
  buildername = 'Housekeeper-PerCommit'
  mastername = 'client.skia.fyi'
  slavename = 'skiabot-linux-housekeeper-000'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   mastername=mastername,
                   slavename=slavename)
  )

  buildername = 'Housekeeper-PerCommit-Trybot'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   mastername=mastername,
                   slavename=slavename,
                   issue=500)
  )
