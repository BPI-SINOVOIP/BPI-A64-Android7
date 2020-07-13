#!/usr/bin/python
#
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Checks buildbot jobs by name."""

import json
import os
import subprocess
import sys
import time
import urllib2

BASE_URL = ('https://build.chromium.org/p/%(master)s/json/builders/%(builder)s/'
            'builds/_all?as_text=1&filter=0')
LINK_URL = ('https://build.chromium.org/p/%(master)s/builders/%(builder)s/'
            'builds/%(build_num)s')


def _get_build_name(job):
  for property_tuple in job['properties']:
    if property_tuple[0] == 'job_name':
      return property_tuple[1]


def main(config):
  """"Checks jobs on a buildbot builder.

  Args:
    config (dict): a configuration in the following format.

  {
    "master": "tryserver.chromium.perf",
    "builder": "linux_perf_bisect",
    "job_names": [ "abc1234", "deadbeef000", "f00ba12"]
  }

  Returns:
    A dictionary containing a list of failed and/or a list of completed jobs and
    a mapping between failed jobs and their urls when available
    e.g.
    {
     "failed": ["deadbeef000"],
     "completed": ["abc1234"],
     "failed_job_urls": {"deadbeef000":"https://build....org/p/tryserver..."}
    }
  """
  master = config['master']
  builder = config['builder']
  job_names = config['job_names']
  results = {}
  completed_jobs = []
  failed_jobs = []
  job_urls = {}

  if 'TESTING_MASTER_HOST' in os.environ:
    url = ('http://%(host)s:8041/json/builders/%(builder)s/'
           'builds/_all?as_text=1&filter=0') % {
               'host': os.environ['TESTING_MASTER_HOST'],
               'builder': builder,
            }
  else:
    url = BASE_URL % {'master': master, 'builder': builder}
  sys.stderr.write('Using the following url to check builds:' + url)
  sys.stderr.flush()
  builds_info = json.load(urllib2.urlopen(url))

  for build_num in builds_info.keys():
    build_name = _get_build_name(builds_info[build_num])
    if build_name in job_names:
      if builds_info[build_num]['results'] in [2,3,4]:
        failed_jobs.append(build_name)
        job_urls[build_name] = LINK_URL % {
            'master': master,
            'builder': builder,
            'build_num': str(build_num),
        }
      elif builds_info[build_num]['results'] in [0, 1]:
        job_urls[build_name] = LINK_URL % {
            'master': master,
            'builder': builder,
            'build_num': str(build_num),
        }
        completed_jobs.append(build_name)

  if completed_jobs:
    results['completed'] = completed_jobs
  if failed_jobs:
    results['failed'] = failed_jobs
  if job_urls:
    results['job_urls'] = job_urls
  return results


if __name__ == '__main__':
  config = json.loads(sys.stdin.read())
  print main(config)
  sys.exit(0)
