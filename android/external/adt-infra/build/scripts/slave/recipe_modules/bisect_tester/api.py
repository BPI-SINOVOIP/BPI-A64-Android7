# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import os

from recipe_engine import recipe_api
from . import perf_test

BUCKET = 'chrome-perf'
RESULTS_GS_DIR = 'bisect-results'


class BisectTesterApi(recipe_api.RecipeApi):
  """A module for the bisect tester bot using the chromium recipe."""

  def __init__(self, **kwargs):
    super(BisectTesterApi, self).__init__(**kwargs)

  def load_config_from_dict(self, bisect_config):
    """Copies the required configuration keys to a new dict."""
    return {
        'command': bisect_config['command'],
        'metric': bisect_config.get('metric'),
        'repeat_count': int(bisect_config.get('repeat_count', 20)),
        'max_time_minutes': float(bisect_config.get('max_time_minutes', 25)),
        'test_type': bisect_config.get('test_type', 'perf')
    }

  def run_test(self, test_config, **kwargs):
    """Exposes perf tests implementation."""
    return perf_test.run_perf_test(self, test_config, **kwargs)

  def digest_run_results(self, results, retcodes, cfg):
    """Calculates relevant statistical functions from the results."""
    if cfg.get('test_type', 'perf') == 'return_code':
      # If any of the return codes is non-zero, output 1.
      overall_return_code = 0 if all(v == 0 for v in retcodes) else 1
      return {
          'mean': overall_return_code,
          'std_err': 0.0,
          'std_dev': 0.0,
          'values': results,
      }
    return perf_test.aggregate(self, results)

  def upload_results(self, output, results, retcodes):
    """Puts the results as a JSON file in a GS bucket."""
    gs_filename = (RESULTS_GS_DIR + '/' +
                   self.m.properties['job_name'] + '.results')
    contents = {'results': results, 'output': output, 'retcodes': retcodes}
    contents_json = json.dumps(contents)
    local_save_results = self.m.python('saving json to temp file',
                                       self.resource('put_temp.py'),
                                       stdout=self.m.raw_io.output(),
                                       stdin=self.m.raw_io.input(
                                           contents_json))
    local_file = local_save_results.stdout.splitlines()[0].strip()
    # TODO(robertocn): Look into using self.m.json.input(contents) instead of
    # local_file.
    self.m.gsutil.upload(local_file, BUCKET, gs_filename)

  def upload_job_url(self):
    """Puts the URL to the job's status on a GS file."""
    gs_filename = RESULTS_GS_DIR + '/' + self.m.properties['job_name']
    if 'TESTING_MASTER_HOST' in os.environ:  # pragma: no cover
      url = "http://%s:8041/json/builders/%s/builds/%s" % (
          os.environ['TESTING_MASTER_HOST'],
          self.m.properties['buildername'],
          self.m.properties['buildnumber'])
    else:
      url = "http://build.chromium.org/p/%s/json/builders/%s/builds/%s" % (
          self.m.properties['mastername'],
          self.m.properties['buildername'],
          self.m.properties['buildnumber'])
    local_save_results = self.m.python('saving url to temp file',
                                       self.resource('put_temp.py'),
                                       stdout=self.m.raw_io.output(),
                                       stdin=self.m.raw_io.input(url))
    local_file = local_save_results.stdout.splitlines()[0].strip()
    self.m.gsutil.upload(local_file, BUCKET, gs_filename, name=str(gs_filename))
