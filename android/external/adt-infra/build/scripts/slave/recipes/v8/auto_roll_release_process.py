# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'file',
  'gclient',
  'git',
  'gsutil',
  'json',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'time',
  'v8',
]

REPO = 'https://chromium.googlesource.com/v8/v8'
CLUSTERFUZZ = 'https://cluster-fuzz.appspot.com/testcase?key=%d'
SHOW_MAX_ISSUES = 5
CANDIDATE_REF = 'refs/heads/candidate'
LKGR_REF = 'refs/heads/lkgr'
ROLL_REF = 'refs/heads/roll'
STATUS_URL = 'https://v8-status.appspot.com'
SEC_TO_HOURS = 60 * 60
TIME_LIMIT_HOURS = 8
TIME_LIMIT_SEC = TIME_LIMIT_HOURS * SEC_TO_HOURS


def GetRef(api, repo, ref):
  # Fetch ref from remote.
  api.git(
      'fetch', repo, '+%s:%s' % (ref, ref),
      cwd=api.path['checkout'],
  )
  # Read ref locally.
  step_result = api.git(
      'show-ref', '-s', ref,
      name='git show-ref %s' % ref,
      cwd=api.path['checkout'],
      stdout=api.raw_io.output(),
  )
  result = step_result.stdout.strip()
  step_result.presentation.logs['ref'] = [result]
  return result


def PushRef(api, repo, ref, hsh):
  api.git(
      'update-ref', ref, hsh,
      cwd=api.path['checkout'],
  )
  api.git(
      'push', repo, '%s:%s' % (ref, ref),
      cwd=api.path['checkout'],
  )

  # Upload log for debugging.
  ref_log_file_name = ref.replace('/', '_') + '.log'
  ref_log_path = api.path['slave_build'].join(ref_log_file_name)
  log = []
  if api.path.exists(ref_log_path):
    log.append(api.file.read(
      'Read %s' % ref_log_file_name, ref_log_path, test_data=''))
  log.append('%s %s' % (hsh, str(api.time.time())))
  api.file.write('Write %s' % ref_log_file_name, ref_log_path, '\n'.join(log))
  api.gsutil.upload(
      ref_log_path,
      'chromium-v8-auto-roll',
      api.path.join('v8_release_process', ref_log_file_name),
  )


def ReadTimeStamp(api, name):
  return int(float(
      api.file.read(
          name,
          api.path['slave_build'].join('timestamp.txt'),
      ).strip()))


def WriteTimeStamp(api, name, timestamp):
  api.file.write(
      name,
      api.path['slave_build'].join('timestamp.txt'),
      str(timestamp),
  )


def LogStep(api, text):
  api.step('log', ['echo', text])


def AgeLimitBailout(api, new_date, old_date):
  age = (new_date - old_date) / SEC_TO_HOURS
  LogStep(api, 'Current candidate is %dh old (limit: %dh).' %
               (age, TIME_LIMIT_HOURS))
  return age < TIME_LIMIT_HOURS


def GetLKGR(api):
  step_result = api.python(
      'get new lkgr',
      api.path['build'].join('scripts', 'tools', 'runit.py'),
      [api.path['build'].join('scripts', 'tools', 'pycurl.py'),
       '%s/lkgr' % STATUS_URL],
      stdout=api.raw_io.output(),
  )
  lkgr = step_result.stdout.strip()
  step_result.presentation.logs['logs'] = [
    'New candidate: %s (%s)' % (lkgr, str(api.time.time())),
  ]
  return lkgr


def ClusterfuzzHasIssues(api):
  step_test_data = lambda: api.json.test_api.output([])
  step_result = api.python(
      'check clusterfuzz',
      api.path['checkout'].join(
          'tools', 'release', 'check_clusterfuzz.py'),
      ['--key-file', api.path['slave_build'].join('.cf_key'),
       '--results-file', api.json.output(add_json_log=False)],
      # Note: Output is suppressed for security reasons.
      stdout=api.raw_io.output('out'),
      stderr=api.raw_io.output('err'),
      step_test_data=step_test_data,
  )
  results = step_result.json.output
  if results:
    step_result.presentation.text = 'Found %s issues.' % len(results)
    for result in results[:SHOW_MAX_ISSUES]:
      step_result.presentation.links[str(result)] = CLUSTERFUZZ % int(result)
    step_result.presentation.status = api.step.FAILURE
    return True
  return False


def RunSteps(api):
  repo = api.properties.get('repo', REPO)
  fail_on_exit = []

  api.gclient.set_config('v8')
  api.bot_update.ensure_checkout(force=True, no_shallow=True)

  # Get current lkgr ref and update.
  new_lkgr = GetLKGR(api)
  current_lkgr = GetRef(api, repo, LKGR_REF)
  if new_lkgr != current_lkgr:
    PushRef(api, repo, LKGR_REF, new_lkgr)
  else:
    LogStep(api, 'There is no new lkgr.')

  # Get current candidate and update roll ref.
  current_candidate = GetRef(api, repo, CANDIDATE_REF)
  current_roll = GetRef(api, repo, ROLL_REF)

  try:
    current_date = ReadTimeStamp(api, 'check timestamp')
  except Exception:
    # If anything goes wrong, the process restarts with a fresh timestamp.
    current_date = api.time.time()
    WriteTimeStamp(api, 'init timestamp', current_date)
    fail_on_exit.append(
        'Timestamp file was missing. Starting new candidate cycle.')

  # Check for clusterfuzz problems before bailout to be more informative.
  clusterfuzz_has_issues = ClusterfuzzHasIssues(api)
  if clusterfuzz_has_issues:
    fail_on_exit.append('Clusterfuzz had issues.')

  new_date = api.time.time()
  if not AgeLimitBailout(api, new_date, current_date):
    if current_candidate != new_lkgr:
      PushRef(api, repo, CANDIDATE_REF, new_lkgr)
      WriteTimeStamp(api, 'update timestamp', api.time.time())
    else:
      LogStep(api, 'There is no new candidate.')

    # Promote the successful candidate to the roll ref in order to get
    # rolled. This is independent of a new lkgr. Every candidate that is
    # more than 8h old is promoted.
    if current_candidate != current_roll:
      PushRef(api, repo, ROLL_REF, current_candidate)

  if fail_on_exit:
    raise api.step.StepFailure(' '.join(fail_on_exit))


def GenTests(api):
  hsh_old = '74882b7a8e55268d1658f83efefa1c2585cee723'
  hsh_recent = '0df953c9e12c1e3b0e37f2d4ef1ef8c319e095cb'
  hsh_new = 'c1a7fd0c98a80c52fcf6763850d2ee1c41cfe8d6'
  date_old = str(100.0 * SEC_TO_HOURS + 0.5)
  date_recent = str(105.0 * SEC_TO_HOURS + 0.5)
  date_new = str(110.0 * SEC_TO_HOURS + 0.5)

  def Test(name, current_lkgr, current_date, new_lkgr, new_date,
           current_roll=None):
    current_roll = current_roll or current_lkgr
    return (
        api.test(name) +
        api.properties.generic(mastername='client.v8.fyi',
                               buildername='Auto-roll - release process') +
        api.override_step_data(
            'get new lkgr',
            api.raw_io.stream_output(new_lkgr, stream='stdout'),
        ) +
        api.override_step_data(
            'git show-ref %s' % LKGR_REF,
            api.raw_io.stream_output(current_lkgr, stream='stdout'),
        ) +
        api.override_step_data(
            'git show-ref %s' % CANDIDATE_REF,
            api.raw_io.stream_output(current_lkgr, stream='stdout'),
        ) +
        api.override_step_data(
            'git show-ref %s' % ROLL_REF,
            api.raw_io.stream_output(current_roll, stream='stdout'),
        ) +
        api.override_step_data(
            'check timestamp',
            api.raw_io.output(current_date),
        ) +
        api.time.seed(int(float(new_date))) +
        api.time.step(2) +
        api.path.exists(api.path['slave_build'].join(
            LKGR_REF.replace('/', '_') + '.log'))
    )

  yield Test(
      'same_lkgr',
      hsh_old,
      date_old,
      hsh_old,
      date_new,
  )
  yield Test(
      'recent_lkgr',
      hsh_recent,
      date_recent,
      hsh_new,
      date_new,
  )
  yield Test(
      'update',
      hsh_old,
      date_old,
      hsh_new,
      date_new,
  )
  yield Test(
      'update_roll_only',
      hsh_recent,
      date_old,
      hsh_recent,
      date_new,
      current_roll=hsh_old,
  )
  yield Test(
      'clusterfuzz_issues',
      hsh_recent,
      date_old,
      hsh_recent,
      date_new,
      current_roll=hsh_old,
  ) + api.override_step_data('check clusterfuzz', api.json.output([1, 2]))
  yield Test(
      'new_lkgr_failed_timestamp',
      hsh_recent,
      date_recent,
      hsh_new,
      date_new,
  ) + api.override_step_data('check timestamp', retcode=1)
