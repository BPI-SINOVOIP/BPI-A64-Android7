#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
Estimates capacity needs for all builders of a given master.
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
import tempfile
import urllib
import urllib2

import numpy

BASE_DIR = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def get_builds(mastername, buildername, days):
  results = []
  for day in days:
    cursor = ''
    while True:
      response = urllib2.urlopen(
          'https://chrome-build-extract.appspot.com/get_builds'
          '?master=%s&builder=%s&day=%s&cursor=%s' % (
              urllib.quote(mastername),
              urllib.quote(buildername),
              urllib.quote(str(day)),
              urllib.quote(cursor)))
      data = json.load(response)
      results.extend(data['builds'])
      cursor = data['cursor']
      if not cursor:
        break

  return results


def estimate_capacity(requests):
  hourly_buckets = {}
  daily_buckets = {}
  build_times = []
  for request in requests:
    build_times.append(request['build_time_s'])

    hourly_bucket = request['timestamp'].strftime('%Y-%m-%d-%H')
    hourly_buckets.setdefault(hourly_bucket, []).append(request['build_time_s'])

    daily_bucket = request['timestamp'].strftime('%Y-%m-%d')
    daily_buckets.setdefault(daily_bucket, []).append(request['build_time_s'])

  def min_bots(buckets, resolution_s):
    result = 0
    for times in buckets.itervalues():
      total_time = sum(times)
      bots = total_time / resolution_s
      result = max(result, bots)
    return result

  return {
    'hourly_bots': min_bots(hourly_buckets, 3600),
    'daily_bots': min_bots(daily_buckets, 3600 * 24),
    'build_times_s': build_times,
  }


def estimate_buildbot_capacity(builds):
  requests = []
  for build in builds:
    changes = build['sourceStamp']['changes']
    if changes:
      assert len(changes) == 1
      timestamp = datetime.datetime.utcfromtimestamp(changes[0]['when'])
    else:
      # Fallback for builds that don't have blamelist/source stamp,
      # e.g. win_pgo.
      timestamp = datetime.datetime.utcfromtimestamp(build['times'][0])

    requests.append({
        'build_time_s': build['times'][1] - build['times'][0],
        'timestamp': timestamp,
    })

  return estimate_capacity(requests)


def get_properties(build):
  return {p[0]: p[1] for p in build.get('properties', [])}


def chunks(iterable, length):
  for i in range(0, len(iterable), length):
    yield iterable[i:i + length]


def launch_and_collect(iterable, get_process, collect_result):
  # Run processes in chunks so that we don't exceed the process limit.
  for chunk in chunks(iterable, 50):
    processes = []
    try:
      for item in chunk:
        with tempfile.NamedTemporaryFile(delete=False, prefix='estimate_') as f:
          process, metadata = get_process(item, f.name)
          processes.append({
            'process': process,
            'metadata': metadata,
            'file': f.name,
          })
      for p in processes:
        rc = p['process'].wait()
        if rc != 0:
          raise Exception('fail')
        with open(p['file']) as f:
          data = json.load(f)
        collect_result(data, p['metadata'])
    finally:
      for p in processes:
        os.remove(p['file'])


def estimate_swarming_capacity(swarming_py, builds):
  """Estimates swarming capacity needs based on a list of builds.

  Returns a dictionary, where keys are swarming pools
  and values are capacity estimates for corresponding pool,
  as returned by estimate_capacity function.
  """

  def get_ids_and_durations():
    result = []
    def get_process(build, tmp_filename):
      properties = get_properties(build)
      # TODO(phajdan.jr): Handle case where there are more results than limit.
      return subprocess.Popen([
          swarming_py, 'query',
          '-S', 'chromium-swarm.appspot.com',
          'tasks?tag=master:%s&tag=buildername:%s&tag=buildnumber:%s' % (
              urllib.quote(properties['mastername']),
              urllib.quote(properties['buildername']),
              urllib.quote(str(properties['buildnumber']))),
          '--json', tmp_filename]), None
    def collect_result(data, metadata):
      result.extend({
          'durations': item['durations'], 'id': item['id']}
          for item in data['items'] if item)
    launch_and_collect(builds, get_process, collect_result)
    return result

  ids_and_durations = get_ids_and_durations()

  def get_pools():
    result = []
    def get_process(id_and_durations, tmp_filename):
      # TODO(phajdan.jr): Handle case where there are more results than limit.
      return subprocess.Popen([
          swarming_py, 'query',
          '-S', 'chromium-swarm.appspot.com',
          'task/%s/request' % id_and_durations['id'],
          '--json', tmp_filename]), id_and_durations
    def collect_result(data, id_and_durations):
      result.append({
          'id': id_and_durations['id'],
          'total_duration': sum(id_and_durations['durations']),
          'dimensions': data['properties']['dimensions'],
          'created_ts': datetime.datetime.strptime(
              data['created_ts'], '%Y-%m-%d %H:%M:%S'),
      })
    launch_and_collect(ids_and_durations, get_process, collect_result)
    pools = {}
    for r in result:
      pools.setdefault(frozenset(r['dimensions'].iteritems()), []).append({
          'build_time_s': r['total_duration'],
          'timestamp': r['created_ts']})
    return pools

  pools = get_pools()
  capacity = {pool : estimate_capacity(requests)
              for pool, requests in pools.iteritems()}
  return capacity


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument('master')
  parser.add_argument('--days', type=int, default=14)
  parser.add_argument('--exclude-by-blamelist')
  parser.add_argument('--filter-by-blamelist')
  parser.add_argument('--filter-by-builder')
  parser.add_argument('--filter-by-patch-project')
  parser.add_argument('--print-builds', action='store_true')
  parser.add_argument('--swarming-py', help='Path to swarming.py')

  args = parser.parse_args(argv)

  with tempfile.NamedTemporaryFile() as f:
    subprocess.check_call([
        os.path.join(BASE_DIR, 'scripts', 'tools', 'runit.py'),
        os.path.join(BASE_DIR, 'scripts', 'tools', 'dump_master_cfg.py'),
        'masters/%s' % args.master,
        f.name])
    master_config = json.load(f)

  builder_pools = []
  for builder in master_config['builders']:
    if args.filter_by_builder and builder['name'] != args.filter_by_builder:
      continue

    slave_set = set(builder['slavenames'])
    builddir = builder.get('slavebuilddir', builder['name'])
    for pool in builder_pools:
      if pool['slave_set'] == slave_set:
        pool['builders'].setdefault(builddir, []).append(builder['name'])
        break
    else:
      builder_pools.append({
        'slave_set': slave_set,
        'builders': {builddir: [builder['name']]},
      })

  days = []
  for i in range(args.days):
    days.append(datetime.date.today() - datetime.timedelta(days=(i + 1)))
  all_builds = []
  cl_ids = set()
  for index, pool in enumerate(builder_pools):
    print 'Pool #%d:' % (index + 1)
    pool_capacity = {
      'hourly_bots': 0.0,
      'daily_bots': 0.0,
      'build_times_s': [],
    }
    # TODO(phajdan.jr): use multiprocessing pool to speed this up.
    for builddir, builders in pool['builders'].iteritems():
      print '  builddir "%s":' % builddir
      for builder in builders:
        raw_builds = get_builds(
            args.master.replace('master.', ''), builder, days)

        builds = []
        for build in raw_builds:
          properties = get_properties(build)
          if (args.filter_by_patch_project and
              properties.get('patch_project') != args.filter_by_patch_project):
            continue

          blamelist = build.get('blame', [])
          if (args.exclude_by_blamelist and
              args.exclude_by_blamelist in blamelist):
            continue
          if (args.filter_by_blamelist and
              args.filter_by_blamelist not in blamelist):
            continue

          if not properties.get('issue') or not properties.get('patchset'):
            continue

          builds.append(build)
          all_builds.append(build)
          cl_ids.add('%s:%s' % (properties['issue'], properties['patchset']))

        capacity = estimate_buildbot_capacity(builds)
        for key in ('hourly_bots', 'daily_bots', 'build_times_s'):
          pool_capacity[key] += capacity[key]

        if capacity['build_times_s']:
          avg_build_time = str(datetime.timedelta(
              seconds=int(numpy.mean(capacity['build_times_s']))))
          median_build_time = str(datetime.timedelta(
              seconds=int(numpy.median(capacity['build_times_s']))))
        else:
          avg_build_time = 'n/a'
          median_build_time = 'n/a'

        print ('    %-45s %-9s %-9s (%4s/%4s builds) '
               '%5.1f %5.1f       <%10.1f>' % (
            builder,
            avg_build_time,
            median_build_time,
            len(builds),
            len(raw_builds),
            capacity['daily_bots'],
            capacity['hourly_bots'],
            sum(capacity['build_times_s'])))
        if args.print_builds:
          def issue_patchset(build):
            properties = get_properties(build)
            return (properties.get('issue', -1), properties.get('patchset', -1))
          for build in sorted(builds, key=issue_patchset):
            properties = get_properties(build)
            build_time = build['times'][1] - build['times'][0]
            print '        build #%-9s %-25s %11s:%-11s %-9s' % (
                build['number'],
                properties.get('requester', 'n/a'),
                properties.get('issue', 'n/a'),
                properties.get('patchset', 'n/a'),
                str(datetime.timedelta(seconds=build_time)))
    print '  %-45s %s %5.1f %5.1f %5.1f <%10.1f>' % (
        'total',
        ' ' * 40,
        pool_capacity['daily_bots'],
        pool_capacity['hourly_bots'],
        len(pool['slave_set']),
        sum(pool_capacity['build_times_s']))

  if args.swarming_py:
    swarming_capacity = estimate_swarming_capacity(args.swarming_py, all_builds)
    if swarming_capacity:
      print 'Swarming pools:'
      for pool, capacity in swarming_capacity.iteritems():
        formatted_pool = ', '.join(sorted(':'.join(d) for d in pool))

        argv = [
          args.swarming_py, 'bots',
          '-S', 'chromium-swarm.appspot.com',
          '--bare',
        ]
        for d in pool:
          argv.extend(['-d', d[0], d[1]])

        swarming_bots = subprocess.check_output(argv)
        print '  %-86s %5.1f %5.1f %5.1f  <%10.1f>' % (
            formatted_pool,
            capacity['daily_bots'],
            capacity['hourly_bots'],
            len(swarming_bots.splitlines()),
            sum(capacity['build_times_s']))

  print 'Data generated for %d unique CL IDs (issue/patchset pairs)' % len(
      cl_ids)

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
