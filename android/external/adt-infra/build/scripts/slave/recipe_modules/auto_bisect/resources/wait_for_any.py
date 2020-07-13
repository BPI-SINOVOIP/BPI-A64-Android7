#!/usr/bin/python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Waits for any one job out of a list to complete or a default timeout."""

import json
import os
import subprocess
import sys
import time

import check_buildbot

# Return codes.
COMPLETED, FAILED, TIMED_OUT, BAD_ARGS = 0, 1, 2, 3

# The following intervals are specified in seconds, are expected to be sent as
# arguments to time.sleep()

# If none of the URLs is determined to be ready, we sleep for a 'long'
# interval.
SLEEP_INTERVAL = 60
# We should check buildbot not more often than every 10 minutes.
BUILDBOT_CHECK_INTERVAL = 600

next_buildbot_check_due_time = 0


def _print_usage(argv):
  usage = 'Usage: %s <gsutil path> [--timeout=<seconds>]'
  print usage % argv[0]
  print 'main.__doc__'
  print main.__doc__
  return BAD_ARGS


def _gs_file_exists(gsutil_path, url):
  """Checks that running 'gsutil ls' returns 0 to see if file at url exists."""
  cmd = [gsutil_path, 'ls', url]
  error = subprocess.call(cmd, stdout=open(os.devnull, 'wb'))
  return not error


def _next_buildbot_check_due():
  """To limit how often we pull the [potentially big] json object from bb."""
  global next_buildbot_check_due_time
  if time.time() > next_buildbot_check_due_time:
    next_buildbot_check_due_time = time.time() + BUILDBOT_CHECK_INTERVAL
    sys.stderr.write('Checking buildbot for completed/failed jobs')
    return True
  return False


def _check_buildbot_jobs(jobs_to_check):
  if not jobs_to_check:
    return None
  jobs = {}
  completed_results = []
  failed_results = []
  # Mapping from job names to the original dictionary sent in jobs_to_check
  entries = {}
  job_urls = {}
  for entry in jobs_to_check:
    master = entry['master']
    builder = entry['builder']
    job_name = entry['job_name']
    # The entries in this list may have multiple jobs for a single builder, and
    # we want to avoid hitting the builder for each job, since we get the
    # information for all builds each time.
    #
    # To prevent this we are taking this:
    # [{'master': 'M', 'builder': 'B', 'job_name': 'J1'},
    #  {'master': 'M', 'builder': 'B', 'job_name': 'J2'},
    #  {'master': 'M', 'builder': 'C', 'job_name': 'J3'},
    # ]
    # And building this in the jobs variable:
    # {'M': { 'B': ['J1', 'J2'], 'C': ['J3']}}
    jobs.setdefault(master, {}).setdefault(builder, []).append(job_name)
    entries[job_name] = entry
  for master in jobs.keys():
    for builder in jobs[master].keys():
      config = {
        'master': master,
        'builder': builder,
        'job_names': jobs[master][builder],
      }
      builder_results = check_buildbot.main(config)
      completed_results += builder_results.get('completed', [])
      failed_results += builder_results.get('failed', [])
      job_urls.update(builder_results.get('job_urls', {}))
    results = {}
    if completed_results:
      results['completed'] = [entries[k] for k in completed_results]
    if failed_results:
      results['failed'] = [entries[k] for k in failed_results]
    for job in results.get('failed', []) + results.get('completed', []):
      if job['job_name'] in job_urls:
        job['job_url'] = job_urls[job['job_name']]

    return results


def main(argv):
  """Main function of the script.

  The script expects the path to gsutil to be provided on the command line, and
  a json object containing the details of the jobs to monitor on standard input.

  Each job in the list, should be one of the following types:
    - GS location, which must at least contain:
      - The "type" key set to the "gs" value.
      - The "location" key, containing the location ("gs://...") of the gs
        object to check.
    - Buildbot job, which must at least contain:
      - The "type" key set to the "buildbot" value.
      - The "master" key containing the name of the appropriate master, e.g.
        "tryserver.chromium.perf".
      - The "builder" key set to the name of the builder performing the job.
      - The "job_name" key containing the name of the job to check. i.e.
        typically a uuid or a hash will be used.

  The script will wait until the first of the following conditions becomes true:
    - An object exists at one of the GS locations
    - One of the buildbot jobs completes as succesful
    - One of the buildbot jobs fails
    - One week elapses from the invocation of the script. (The exact timeout may
      be overriden from the command line)

  The return code will be:
    0 if a buildbot job succeeds or an object exists at the GS locations.
    1 if a buildbot job fails
    2 if the one-week timeout is triggered.

  Additionally, a json object will be written to standard output containig the
  results of the script.

  Example of expected stdin:
  {
   "jobs": [
    {
     "type": "gs",
     "location": "gs://chrome-perf/some_path/some_object.json"
    },
    {
     "type": "buildbot",
     "master": "tryserver.chromium.perf",
     "builder": "linux_perf_bisect",
     "job_name": "f74fb8e0418d47bfb7d01fad0dd4df06"
    }
   ]
  }
  EOF

  Examples of results from stdout:
  cat <<EOF #Successful result
  {
   "completed": [
    {
     "type": "buildbot",
     "master": "tryserver.chromium.perf",
     "builder": "linux_perf_bisect",
     "job_name": "f74fb8e0418d47bfb7d01fad0dd4df06"
    }
   ]
  }
  EOF

  cat <<EOF #Unsuccessful result
  {
   "failed": [
    {
     "type": "buildbot",
     "master": "tryserver.chromium.perf",
     "builder": "linux_perf_bisect",
     "job_name": "f74fb8e0418d47bfb7d01fad0dd4df06"
    }
   ]
  }
  EOF
  """
  start_time  = time.time()
  # Default timeout: six days
  timeout_interval = 6 * 24 * 60 * 60
  if argv[-1].startswith('--timeout='):
    timeout_interval = int(argv[-1].split('=')[1])
    argv = argv[:-1]

  jobs = json.loads(sys.stdin.read())['jobs']
  gs_jobs = [job for job in jobs if job['type'] == 'gs']
  buildbot_jobs = [job for job in jobs if job['type'] == 'buildbot']

  if ((not gs_jobs and not buildbot_jobs) or
      (gs_jobs and len(argv) < 2)):
    return _print_usage(argv)

  gsutil_path = argv[1] if gs_jobs else ''

  while time.time() < start_time + timeout_interval:
    # Checking GS jobs
    completed_jobs = []
    for job in gs_jobs:
      if _gs_file_exists(gsutil_path, job['location']):
        completed_jobs.append(job)

    # Checking Buildbot jobs
    if completed_jobs or _next_buildbot_check_due():
      # buildbot_results will only contain jobs that have been completed or
      # failed. All other jobs (scheduled, in progress, etc.) will be ignored.
      buildbot_results = _check_buildbot_jobs(buildbot_jobs)
      if buildbot_results:
        print json.dumps(buildbot_results)
        if 'completed' in buildbot_results and buildbot_results['completed']:
          return COMPLETED
        return FAILED

    if completed_jobs:
      # This clause is just a fallback. Ideally when a results file shows up at
      # a gs location, we'd want to run check_buildbot jobs first to find the
      # url to the job detaisl.
      print json.dumps({'completed': completed_jobs})
      return COMPLETED
    # At this point, no jobs were completed nor failed. We print a char to
    # prevent buildbot from killing this process for inactivity.
    sys.stderr.write('Sleeping.\n')
    sys.stderr.flush()
    time.sleep(SLEEP_INTERVAL)
  return TIMED_OUT

if __name__ == '__main__':
  sys.exit(main(sys.argv))
