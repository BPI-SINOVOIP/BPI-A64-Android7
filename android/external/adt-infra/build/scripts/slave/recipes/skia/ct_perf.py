# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Recipe for the Cluster Telemetry Perf Trybots."""


from common.skia import global_constants


DEPS = [
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'skia',
]


from recipe_engine.recipe_api import Property


def RunSteps(api):

  buildername = api.properties['buildername']

  if (not api.properties.get('rietveld') or
      not api.properties.get('issue') or
      not api.properties.get('patchset') or
      not api.properties.get('requester')):
    raise Exception('%s can only be run as a trybot.' % buildername)

  # Figure out which benchmark to use.
  if 'Repaint' in buildername:
    benchmark = 'repaint'
  elif 'RR' in buildername:
    benchmark = 'rasterize_and_record_micro'
  else:
    raise Exception('Do not recognise the buildername %s.' % buildername)

  # Run Cluster Telemetry Perf.
  cmd = ['python', api.skia.resource('trigger_wait_ct_task.py'),
        '--issue', api.properties.get('issue'),
        '--patchset', api.properties.get('patchset'),
        '--requester', api.properties.get('requester'),
        '--benchmark', benchmark,
        ]
  if 'Parallel' in buildername:
    cmd.append('--parallel')
  api.step('Cluster Telemetry %s run' % benchmark, cmd=cmd,
           allow_subannotations=True)


def GenTests(api):
  rietveld = True
  issue = 123
  patchset = 2001
  requester = 'superman@krypton'

  # Test required params not specified.
  buildername = 'CT-Perf-10k-Linux-Repaint-Serial-Trybot'
  yield (
    api.test(buildername + '_missing-params') +
    api.properties(buildername=buildername) +
    api.expect_exception('Exception')
  )

  # Test unsupported benchmark.
  buildername = 'CT-Perf-10k-Linux-unsupported_benchmark-Serial-Trybot'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   rietveld=rietveld,
                   issue=issue,
                   patchset=patchset,
                   requester=requester,
                  ) +
    api.expect_exception('Exception')
  )

  # Test normal repaint flow.
  buildername = 'CT-Perf-10k-Linux-Repaint-Serial-Trybot'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   rietveld=rietveld,
                   issue=issue,
                   patchset=patchset,
                   requester=requester,
                  )
  )

  # Test serial and parallel rasterize_and_record_micro flows.
  buildername = 'CT-Perf-10k-Linux-RR-Parallel-Trybot'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   rietveld=rietveld,
                   issue=issue,
                   patchset=patchset,
                   requester=requester,
                  )
  )
  buildername = 'CT-Perf-10k-Linux-RR-Serial-Trybot'
  yield (
    api.test(buildername) +
    api.properties(buildername=buildername,
                   rietveld=rietveld,
                   issue=issue,
                   patchset=patchset,
                   requester=requester,
                  )
  )

