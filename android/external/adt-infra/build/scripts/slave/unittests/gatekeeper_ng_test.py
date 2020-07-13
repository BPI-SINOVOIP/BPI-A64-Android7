#!/usr/bin/env python
# coding=utf-8
# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit tests for gatekeeper_ng.py.

This is a basic check that gatekeeper_ng.py can properly interpret builds and
close the tree.

"""

# Needs to be at the top, otherwise coverage will spit nonsense.
import utils  # "relative import" pylint: disable=W0403

import copy
import contextlib
import json
import mock
import os
import StringIO
import sys
import tempfile
import unittest
import urllib
import urllib2
import urlparse

import test_env  # pylint: disable=W0403,W0611

from slave import gatekeeper_ng
from slave import gatekeeper_ng_config
from slave import build_scan_db


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


class BuildLog(object):
  def __init__(self, name, fp=None, string=None, logjson=None):
    self.name = name
    self.fp = fp
    self.string = string
    self.json = logjson

    if (self.fp and self.string or
        self.string and self.json or
        self.json and self.fp):
      raise ValueError('Can only set one of: fp, string, logjson')

  def handle(self, handler, url):
    if self.fp:
      handler.handle_url_fp(url, self.fp)
    if self.string:
      handler.handle_url_str(url, self.string)
    if self.json:
      handler.handle_url_json(url, self.json)


class BuildStep(object):
  def __init__(self, name, logs, results=None, isStarted=False,
               isFinished=False):
    self.name = name
    self.logs = logs
    self.results = results or [0, None]
    self.isStarted = isStarted
    self.isFinished = isFinished
    self.text = 'steptext'


class Build(object):
  def __init__(self, number, steps, blame, results=0, finished=True):
    self.number = number
    self.steps = steps
    self.blame = blame
    self.results = results
    self.properties = [
        ['buildnumber', 1337, 'GatekeeperTest'],
        ['revision', 72453, 'GatekeeperTest'],
        ['got_webkit_revision', 100, 'GatekeeperTest'],
    ]
    self.times = [100, 200]
    self.reason = 'scheduler'
    self.sourcestamp = {
        'branch': 'src',
        'changes': [
            {
             'at': 'Sat 04 May 2013 07:03:09',
             'branch': 'src',
             'comments': 'Fake commit',
             'files': [
                  {
                    'name': 'chrome/browser/signin/DEPS'
                  },
              ],
             'number': 72453,
             'repository': 'svn://svn-mirror.golo.chromium.org/chrome/trunk',
             'rev': '198311',
             'revision': '198311',
             'revlink': ('http://src.chromium.org/viewvc/chrome?'
                         'view=rev&revision=11'),
             'when': 1367676189,
             'who': 'a_committeri@chromium.org',
             }
         ]
    }
    self.finished = finished


class Builder(object):
  def __init__(self, name, builds):
    self.name = name
    self.builds = builds


class Master(object):
  def __init__(self, title, url, builders):
    self.title = title
    self.url = url
    self.builders = builders


class GatekeeperTest(unittest.TestCase):
  def setUp(self):
    self.files_to_cleanup = []
    patcher = mock.patch('urllib2.urlopen')
    self.urlopen = patcher.start()
    self.addCleanup(patcher.stop)
    self.argv = []

    self.urlopen.side_effect = self._url_handler
    self.urls = {}

    self.url_calls = []

    self.status_url_root = 'https://chromium-status.appspot.com'
    self.get_status_url = self.status_url_root + '/current?format=json'
    self.set_status_url = self.status_url_root + '/status'
    # Default to "open" to break fewer unittests.
    self.handle_url_json(self.get_status_url, {
      'message': 'tree is open',
      'general_state': 'open',
    })
    self.handle_url_str(self.set_status_url, '0')

    self.mailer_url = 'https://chromium-build.appspot.com/mailer/email'
    self.handle_url_str(self.mailer_url, '')

    self.master_url_root = 'http://build.chromium.org/p/'
    self.masters = [self.create_generic_build_tree('Chromium FYI',
                                                   'chromium.fyi')]

    self.build_db_file = self.fill_tempfile('{}')
    self.gatekeeper_file = self.fill_tempfile('{}')
    self.email_secret_file = self.fill_tempfile('seekrit')
    self.status_secret_file = self.fill_tempfile('reindeerflotilla')

    self._gatekeeper_config = None

  def fill_tempfile(self, content):
    fd, filename = tempfile.mkstemp()
    os.write(fd, content)
    os.close(fd)
    self.files_to_cleanup.append(filename)

    return filename

  def tearDown(self):
    for filename in self.files_to_cleanup:
      if os.path.exists(filename):
        os.remove(filename)

  def handle_build_tree(self, masters):
    """Before calling gatekeeper, synthesize master and build json.

    Also adds URL handlers where needed.
    """

    for master in masters:
      master_json = {'builders': {},
                     'project': {'buildbotURL': master.url + '/',
                                 'title': master.title}}

      for builder in master.builders:
        builder_url = master.url + '/builders/%s' % urllib.quote(builder.name)
        builder_json = {'cachedBuilds': [],
                        'currentBuilds': []}

        for build in builder.builds:
          build_url = builder_url + '/builds/%d' % build.number
          build_json = {'steps': [],
                        'reason': build.reason,
                        'builderName': builder.name,
                        'blame': build.blame,
                        'properties': build.properties,
                        'sourceStamp': build.sourcestamp,
                        'times': build.times,
                        'number': build.number}
          if build.finished:
            build_json['results'] = build.results

          for step in build.steps:
            step_url = build_url + '/steps/%s' % step.name
            step_json = {'name': step.name,
                         'logs': [],
                         'results': step.results,
                         'text': step.text}
            if step.isStarted:
              step_json['isStarted'] = True
            if step.isFinished:
              step_json['isFinished'] = True

            for log in step.logs:
              log_url = step_url + '/logs/%s' % log.name
              log.handle(self, log_url)
              step_json['logs'].append([log.name, log_url])

            build_json['steps'].append(step_json)

          if build.finished:
            builder_json['cachedBuilds'].append(build.number)
          else:
            builder_json['currentBuilds'].append(build.number)

          build_json_url = master.url + '/json/builders/%s/builds/%d' % (
              urllib.quote(builder.name), build.number)

          self.handle_url_json(build_json_url, build_json)

        master_json['builders'][builder.name] = builder_json
      self.handle_url_json(master.url + '/json', master_json)

  @staticmethod
  def create_generic_build(number, committers):
    step0 = BuildStep('step0', [], isStarted=True, isFinished=True)
    step1 = BuildStep('step1', [], isStarted=True, isFinished=True)
    step2 = BuildStep('step2', [], isStarted=True, isFinished=True)
    step3 = BuildStep('step3', [], isStarted=True, isFinished=True)

    return Build(number, [step0, step1, step2, step3], committers)

  def create_generic_build_tree(self, master_title, master_url_chunk):
    build = GatekeeperTest.create_generic_build(1, ['a_committer@chromium.org'])

    builder = Builder('mybuilder', [build])

    return Master(master_title, self.master_url_root + master_url_chunk,
                  [builder])

  def call_gatekeeper(self, build_db=None, json=None):
    # pylint: disable=W0621
    """Sets up handlers for all the json and actually calls gatekeeper."""
    self.url_calls = []
    self.handle_build_tree(self.masters)
    json = json or self.gatekeeper_file
    self._gatekeeper_config = self._gatekeeper_config or {}
    if not build_db:
      build_db = build_scan_db.gen_db(masters={
          self.masters[0].url: {
              'mybuilder': {
                  0: build_scan_db.gen_build(finished=True)
              }
          }
      })

    with open(self.build_db_file, 'w') as f:
      build_scan_db.convert_db_to_json(build_db, self._gatekeeper_config, f)

    argv = self.argv[:]
    argv.extend(['--build-db=%s' % self.build_db_file,
                 '--json', json])

    try:
      ret = gatekeeper_ng.main(argv)
    except SystemExit as e:
      ret = e.code

    if ret != 0:
      raise ValueError('return code was %d' % ret)

    # Return urls as a convenience.
    return [call['url'] for call in self.url_calls]


  def process_build_db(self, master, builder):
    """Reads the build_db from a file and splits out finished/unfinished."""
    new_build_db = build_scan_db.get_build_db(self.build_db_file)
    builds = new_build_db.masters[master][builder]
    finished_new_builds = dict(
        (k, v) for k, v in builds.iteritems() if v.finished)
    unfinished_new_builds = dict(
        (k, v) for k, v in builds.iteritems() if not v.finished)
    return unfinished_new_builds, finished_new_builds


  @contextlib.contextmanager
  def gatekeeper_config_editor(self):
    """Wrapper to edit the gatekeeper_config, then reserialize it."""
    if not self._gatekeeper_config:
      with open(self.gatekeeper_file) as f:
        self._gatekeeper_config = json.load(f)

      yield self._gatekeeper_config

      with open(self.gatekeeper_file, 'w') as f:
        json.dump(self._gatekeeper_config, f)
      self._gatekeeper_config = None
    else:
      yield self._gatekeeper_config

  @contextlib.contextmanager
  def gatekeeper_config_reader(self):
    """Wrapper to read the flattened gatekeeper_config."""
    if not self._gatekeeper_config:
      config = gatekeeper_ng_config.load_gatekeeper_config(self.gatekeeper_file)
      yield config
    else:
      self.fail('Unable to read self._gatekeeper_config while writing to it.')

  def add_gatekeeper_master_config(self, master_url, data):
    """Adds a gatekeeper category to a build."""
    with self.gatekeeper_config_editor() as gatekeeper_config:
      gatekeeper_config.setdefault('masters', {}).setdefault(master_url, [])
      gatekeeper_config['masters'][master_url].append({})
      self.add_gatekeeper_master_section(master_url, -1, data)

  def add_gatekeeper_master_section(self, master_url, idx, data):
    with self.gatekeeper_config_editor() as gatekeeper_config:
      # Don't stomp 'builders' if it's there.
      for key in data:
        gatekeeper_config['masters'][master_url][idx][key] = data[key]

  def add_gatekeeper_category(self, category, data):
    """Adds a gatekeeper category to a build."""
    with self.gatekeeper_config_editor() as gatekeeper_config:
      gatekeeper_config.setdefault('categories', {})
      gatekeeper_config['categories'][category] = data

  def add_gatekeeper_section(self, master_url, builder, data, idx=-1):
    """Adds a gatekeeper_spec to a build."""
    with self.gatekeeper_config_editor() as gatekeeper_config:
      gatekeeper_config.setdefault('masters', {}).setdefault(master_url, [])
      if idx == -1:
        gatekeeper_config['masters'][master_url].append({})
      gatekeeper_config['masters'][master_url][idx].setdefault('builders', {})
      gatekeeper_config['masters'][master_url][idx]['builders'][builder] = data

  def get_gatekeeper_section_shas(self):
    """Return the SHAs of all the gatekeeper sections."""
    sections = {}
    with self.gatekeeper_config_reader() as gatekeeper_config:
      for master_url, master in gatekeeper_config.iteritems():
        sections[master_url] = [
            gatekeeper_ng_config.gatekeeper_section_hash(section)
            for section in master]
    return sections

  def _url_handler(self, req, params=None):
    """Used by the mocked urlopen to respond to different URLs."""
    if isinstance(req, urllib2.Request):
      url = req.get_full_url()
      params = req.get_data()
    else:
      url = req

    call = {'url': url}
    if params:
      call['params'] = params
    self.url_calls.append(call)

    if url in self.urls:
      return copy.copy(self.urls[url](params))
    else:
      raise urllib2.HTTPError(url, 404, 'Not Found: %s' % url,
                              None, StringIO.StringIO(''))

  @staticmethod
  def decode_param_json(param):
    data = urlparse.parse_qs(param)
    payload = json.loads(data['json'][0])['message']
    return json.loads(payload)

  def handle_url_fp(self, url, fp):
    """Add a file object to handle a mocked URL."""
    setattr(fp, 'getcode', lambda: 200)
    self.urls[url] = lambda _: fp

  def handle_url_custom(self, url, handler):
    """Handler func will be called with params as first argument."""
    self.urls[url] = handler

  def handle_url_str(self, url, response):
    """Add a string to handle a mocked URL."""
    buf = StringIO.StringIO(response)
    self.handle_url_fp(url, buf)

  def handle_url_json(self, url, data):
    """Add a json object to handle a mocked URL."""
    buf = StringIO.StringIO()
    json.dump(data, buf)
    buf.seek(0)
    self.handle_url_fp(url, buf)


  #### Email and status.

  def testIgnoreNoGatekeeper(self):
    """Check that logs aren't read unless the builder is noted in the config."""

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app'])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {})

    urls = self.call_gatekeeper()
    self.assertEquals(urls, [self.masters[0].url + '/json'])

  def testFailedBuildDetected(self):
    """Test that an erroneous build result closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].results = 2
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'respect_build_status': True})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testRetryDoesntClose(self):
    """Test that a step marked retry doesn't close the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].results = 5
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'respect_build_status': True})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)

    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)

  def testExceptionDoesntClose(self):
    """Test that a step marked exception doesn't close the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].results = 4
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'respect_build_status': True})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)

    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)

  def testFailedBuildNoEmail(self):
    """Test that no email is sent if there are no watchers."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])


    self.masters[0].builders[0].builds[0].results = 3
    self.masters[0].builders[0].builds[0].blame = []
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'respect_build_status': True})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)


    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)


  def testStepNonCloserFailureIgnored(self):
    """Test that a non-closing failure is ignored."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[2].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)

  def testStepCloserFailureDetected(self):
    """Test that a failed closing step closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStepExceptionIgnored(self):
    """Test that an exception on a closing step doesn't close the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [5, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)

  def testStepCloserFailureOptional(self):
    """Test that a failed closing_optional step closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_optional': ['step1']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStepCloserFailureOptionalStar(self):
    """Test that a failed closing_optional * step closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_optional': ['*']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStepOmissionDetected(self):
    """Test that the lack of a closing step closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step4']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStepOmissionOptional(self):
    """Test that the lack of a closing_optional step doesn't close the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_optional': ['step4']})

    self.call_gatekeeper()
    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)
    self.assertNotIn(self.mailer_url, urls)

  def testStepForgivingOmissionOptional(self):
    """Test that the lack of a forgiving_optional step doesn't close tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'forgiving_optional': ['step4']})

    self.call_gatekeeper()

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)
    self.assertNotIn(self.mailer_url, urls)

  def testStepNotStarted(self):
    """Test that a skipped closing step closes the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[0].builders[0].builds[0].steps[1].isStarted = False
    self.masters[0].builders[0].builds[0].steps[1].isFinished = False

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testGatekeeperOOO(self):
    """Test that gatekeeper_spec works even if not the first step."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]

    spec = self.masters[0].builders[0].builds[0].steps
    self.masters[0].builders[0].builds[0].steps = spec[1:]+spec[:1]

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testFailedBuildClosesTree(self):
    """Test that a failed build calls to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertIn(self.set_status_url, urls)
    # Check that written build_db_file contains tree closing message.
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertIn('closed',
        build_db.aux['closed_tree-%s' % self.status_url_root]['message'])

  def testIgnoredStepsDontCloseTree(self):
    """Test that ignored steps don't call to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step2']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testExcludedStepsDontCloseTree(self):
    """Test that excluded steps don't call to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'excluded_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testExcludedBuildersDontCloseTree(self):
    """Test that excluded builders don't call to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'excluded_builders': [
                                     self.masters[0].builders[0].name]})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testGlobbedExcludedBuildersDontCloseTree(self):
    """Test that excluded builders don't call to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    glob = '%s*' % (self.masters[0].builders[0].name[0], )
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'excluded_builders': [glob]})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testOpenTree(self):
    """Test that we open the tree if no tracked failures."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].finished = False
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True, succeeded=True)
            }
        }
    })

    # Open the tree if it was previously automatically closed.
    # This is the old way to distinguish human and gatekeeper_ng.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed (automatic)',
      'general_state': 'closed',
    })
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

    # Same as above and get_status_url requires bot login.
    json_handler = self.urls.pop(self.get_status_url)
    def handler(params):
      if params == None:
        return StringIO.StringIO("<blabla>login</blabla>")
      else:
        return json_handler(params)
    self.handle_url_custom(self.get_status_url, handler)
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-2]['url'], self.get_status_url)
    self.assertIsNotNone(self.url_calls[-2]['params'])
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

    # However, don't touch the tree status if a human set it.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed, world is on fire',
      'general_state': 'closed',
    })
    urls = self.call_gatekeeper(build_db=build_db)
    self.assertNotIn(self.set_status_url, urls)

    # Yet open the tree if gatekeeper_ng set previous tree status message.
    # This is the new way to distinguish human and gatekeeper_ng.
    self.argv.remove('--skip-build-db-update')
    closed_message = 'closed by gatekeeper_ng'
    self.handle_url_json(self.get_status_url, {
      'message': closed_message,
      'general_state': 'closed',
    })
    closed_tree_key = 'closed_tree-%s' % self.status_url_root
    build_db.aux[closed_tree_key] = {'message': closed_message}
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))
    written_build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertDictEqual(written_build_db.aux.get(closed_tree_key, {}), {})
    build_db.aux.pop(closed_tree_key)
    self.argv.append('--skip-build-db-update')

    # Only change the tree status if it's currently 'closed'
    self.handle_url_json(self.get_status_url, {
      'message': 'come on in, we\'re open',
      'general_state': 'open',
    })
    urls = self.call_gatekeeper(build_db=build_db)
    self.assertNotIn(self.set_status_url, urls)

  def testOpenTreeOverflowStatus(self):
    """Test that we open the tree if the status message has been clipped."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].finished = False
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True, succeeded=True)
            }
        }
    })

    # Here we create a message 500 chars long.
    closed_message = 'a' * 499 + u'…'
    self.handle_url_json(self.get_status_url, {
      'message': closed_message,
      'general_state': 'closed',
    })
    closed_tree_key = 'closed_tree-%s' % self.status_url_root
    # Here we create a message 800 chars long.
    build_db.aux[closed_tree_key] = {'message': 'a' * 800}
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

  def testOpenTreeOnUnfinishedBuild(self):
    """Test that the tree opens if builds succeed on previously failed steps."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[0].finished = False

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    self.masters[0].builders[0].builds[0].finished = False

    # Open the tree if it was previously automatically closed.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed (automatic)',
      'general_state': 'closed',
    })
    self.call_gatekeeper()
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

  def testOpenTreeIfFailedFinishedStepsSucceeded(self):
    """Test that we open the tree if no tracked failures."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].finished = False
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(
                  finished=True,
                  succeeded=False,
                  triggered={0: ['step1']})
            }
        }
    })

    # Open the tree if it was previously automatically closed.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed (automatic)',
      'general_state': 'closed',
    })
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

  def testOpenTreeIfMultipleStepsSucceeded(self):
    """Test that we open the tree if all failing steps succeded."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[0].finished = False
    self.masters[0].builders[0].builds[0].steps[0].results = [2, None]
    self.masters[0].builders[0].builds[0].steps[2].results = [None, None]
    self.masters[0].builders[0].builds[0].steps[3].results = [None, None]

    self.masters[0].builders[0].builds[1].finished = False
    self.masters[0].builders[0].builds[1].steps[1].results = [None, None]
    self.masters[0].builders[0].builds[1].steps[2].results = [None, None]
    self.masters[0].builders[0].builds[1].steps[3].results = [None, None]

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': [
                                     'step0',
                                     'step1',
                                     'step2',
                                     'step3',
                                 ]})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(
                  finished=True,
                  succeeded=False,
                  triggered={0: ['step1']})
            }
        }
    })

    # Open the tree if it was previously automatically closed.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed (automatic)',
      'general_state': 'closed',
    })
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

  def testOpenTreeIfMultipleStepsSucceededInFlight(self):
    """Test we open the tree if all newly-failing builds have steps succeed."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--open-tree',
                      '--password-file', self.status_secret_file])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    new_build = self.create_generic_build(
        3, ['a_third_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]

    self.masters[0].builders[0].builds[1].finished = False
    self.masters[0].builders[0].builds[1].steps[0].results = [2, None]
    self.masters[0].builders[0].builds[1].steps[2].results = [None, None]
    self.masters[0].builders[0].builds[1].steps[3].results = [None, None]

    self.masters[0].builders[0].builds[2].finished = False
    self.masters[0].builders[0].builds[2].steps[1].results = [None, None]
    self.masters[0].builders[0].builds[2].steps[2].results = [None, None]
    self.masters[0].builders[0].builds[2].steps[3].results = [None, None]


    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': [
                                     'step0',
                                     'step1',
                                     'step2',
                                     'step3',
                                 ]})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(
                  finished=True, succeeded=True)
            }
        }
    })

    # Open the tree if it was previously automatically closed.
    self.handle_url_json(self.get_status_url, {
      'message': 'closed (automatic)',
      'general_state': 'closed',
    })
    self.call_gatekeeper(build_db=build_db)
    self.assertEquals(self.url_calls[-1]['url'], self.set_status_url)
    status_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    self.assertTrue(status_data['message'][0].startswith(
      "Tree is open (Automatic"))

  def testBuildStatusWrittenToBuildDB(self):
    """Test that build success and failure is written to the build_db."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])


    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[0].finished = True

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db()
    self.call_gatekeeper(build_db=build_db)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.masters, {
      self.masters[0].url: {
        self.masters[0].builders[0].name: {
          1: build_scan_db.gen_build(finished=True)
        }
      }
    })

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    new_build = self.create_generic_build(
        3, ['a_third_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    self.masters[0].builders[0].builds[1].steps[2].results = [2, None]
    self.masters[0].builders[0].builds[1].finished = True
    self.masters[0].builders[0].builds[2].finished = False
    self.call_gatekeeper(build_db=build_db)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.masters, {
      self.masters[0].url: {
        self.masters[0].builders[0].name: {
          2: build_scan_db.gen_build(finished=True, succeeded=True),
          3: build_scan_db.gen_build(finished=False, succeeded=False)
        }
      }
    })

  def testDefaultSubjectTemplate(self):
    """Test that the subject template is set by default."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step4']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['subject_template'], unicode(
        'buildbot %(result)s in %(project_name)s on %(builder_name)s, '
        'revision %(revision)s'))

  def testDefaultStatusTemplate(self):
    """Test that the status template is set by default."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--revision-properties', 'revision,got_webkit_revision'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.call_gatekeeper()
    gatekeeper_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    msg = ['Tree is closed (Automatic: "step1" on "mybuilder" '
           'a_committer@chromium.org)']
    self.assertEquals(gatekeeper_data['message'], msg)

  def testStatusTemplate(self):
    """Test that the status template can be set.

    Also checks that revisions set in --revision-properties are set as template
    variables.
    """
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--revision-properties', 'revision,got_webkit_revision'])

    template = ('Tree is radioactively melting due to %(unsatisfied)s on '
        '%(builder_name)s %(blamelist)s %(build_url)s %(project_name)s '
        '%(revision)s %(got_webkit_revision)s %(buildnumber)s %(result)s')

    new_build = self.create_generic_build(1, ['a_committer@chromium.org'])
    new_build.results = gatekeeper_ng.WARNINGS
    new_builder = Builder('my builder', [new_build])
    self.masters[0].builders.append(new_builder)

    self.masters[0].builders[1].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[1].name,
                                {'closing_steps': ['step1'],
                                 'status_template': template})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'my builder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })

    self.call_gatekeeper(build_db=build_db)
    gatekeeper_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    msg = template % {
      'blamelist': 'a_committer@chromium.org',
      'build_url': ('http://build.chromium.org/p/chromium.fyi/'
                    'builders/my%20builder/builds/1'),
      'builder_name': 'my builder',
      'buildnumber': 1337,
      'got_webkit_revision': 100,
      'project_name': 'Chromium FYI',
      'revision': 72453,
      'unsatisfied': 'step1',
      'result': 'warnings',
    }
    self.assertEquals(gatekeeper_data['message'], [msg])


  def testEmailJson(self):
    """Test that the email json is formatted correctly."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    subject_template = 'build %(result)s, oh no!'
    self.masters[0].builders[0].builds[0].results = 2
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'respect_build_status': True})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'subject_template': subject_template},
                                idx=0)

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

    build_url = self.masters[0].url + '/builders/%s/builds/%d' % (
        self.masters[0].builders[0].name,
        self.masters[0].builders[0].builds[0].number)

    step_dicts = []
    for step in self.masters[0].builders[0].builds[0].steps:
      step_url = build_url + '/steps/%s' % step.name
      step_json = {'name': step.name,
                   'logs': [],
                   'results': step.results[0],
                   'text': step.text}

      step_json['started'] = step.isStarted
      step_json['urls'] = []

      for log in step.logs:
        log_url = step_url + '/logs/%s' % log.name
        step_json['logs'].append([log.name, log_url])
      step_dicts.append(step_json)

    self.assertEquals(mailer_data['steps'], step_dicts)
    self.assertEquals(mailer_data['result'], 2)
    self.assertEquals(mailer_data['blamelist'], ['a_committer@chromium.org'])
    self.assertEquals(mailer_data['changes'],
        self.masters[0].builders[0].builds[0].sourcestamp['changes'])
    self.assertEquals(mailer_data['waterfall_url'], unicode(
        self.masters[0].url))

    self.assertEquals(mailer_data['build_url'], unicode(build_url))
    self.assertEquals(mailer_data['project_name'], unicode('Chromium FYI'))
    self.assertEquals(mailer_data['from_addr'], 'buildbot@chromium.org')
    self.assertEquals(mailer_data['subject_template'],
                      unicode(subject_template))


  #### BuildDB operation.

  def testIgnorePastFailures(self):
    """If the build_db is nonexistent, don't fail on past builds."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[1].finished = False

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db()
    urls = self.call_gatekeeper(build_db=build_db)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(1, urls.count(self.mailer_url))

  def testHonorNewFailures(self):
    """If the build_db is nonexistent, fail on current builds."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[0].finished = False
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db()
    urls = self.call_gatekeeper(build_db=build_db)
    self.assertIn(self.set_status_url, urls)

  def testIncrementalScanning(self):
    """Test that builds in the build DB are skipped."""
    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                1: build_scan_db.gen_build(finished=True)}}})

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file])


    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]

    self.masters[0].builders[0].builds.append(
        GatekeeperTest.create_generic_build(2, [
            'a_second_committer@chromium.org']))
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]

    self.call_gatekeeper(build_db=build_db)
    _, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(finished_new_builds,
                      {2: build_scan_db.gen_build(finished=True, triggered={
                          shas[0]: ['step1']})})

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])
    urls = [call['url'] for call in self.url_calls]
    self.assertEquals(urls.count(self.mailer_url), 1)


  #### Gatekeeper parsing.

  def testSheriffParsing(self):
    """Test that sheriff annotations are properly parsed."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'sheriff_classes': ['sheriff_android']})


    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'asheriff, anothersheriff\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    self.call_gatekeeper()

    # Check that gatekeeper checked the sheriff file.
    self.assertEquals(self.url_calls[-2]['url'], sheriff_url)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(mailer_data['recipients'],
                      ['a_committer@chromium.org',
                       'anothersheriff@google.com',
                       'asheriff@google.com'])

  def testNoSheriff(self):
    """Test that a no-sheriff condition works OK (weekends)."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].blame = []
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'sheriff_classes': ['sheriff_android']})

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'None (channel is sheriff)\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-1]['url'], sheriff_url)

    urls = [call['url'] for call in self.url_calls]
    self.assertNotIn(self.mailer_url, urls)

  def testNoSheriffButBlame(self):
    """Test that no-sheriff works ok with a blamelist."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'sheriff_classes': ['sheriff_android']})

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'None (channel is sheriff)\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-2]['url'], sheriff_url)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testMultiSheriff(self):
    """Test that multiple sheriff lists can be merged."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'sheriff_classes': ['sheriff_android',
                                                     'sheriff']})

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'asheriff, anothersheriff\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    aux_sheriff_url = 'http://build.chromium.org/p/chromium/sheriff.js'
    aux_sheriff_string = 'document.write(\'asheriff, athirdsheriff\')'
    self.handle_url_str(aux_sheriff_url, aux_sheriff_string)

    urls = self.call_gatekeeper()

    # Check that gatekeeper checked the sheriff file.
    self.assertIn(sheriff_url, urls)
    self.assertIn(aux_sheriff_url, urls)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(mailer_data['recipients'],
                      ['a_committer@chromium.org',
                       'anothersheriff@google.com',
                       'asheriff@google.com',
                       'athirdsheriff@google.com'])

  def testNotifyParsing(self):
    """Test that additional watchers can be merged to the mailing list."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'tree_notify': ['a_watcher@chromium.org']})

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'asheriff, anothersheriff\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(mailer_data['recipients'],
                      ['a_committer@chromium.org',
                       'a_watcher@chromium.org'])

  def testNotifyNoBlame(self):
    """Test that notify works with no blamelist."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].blame = []
    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'tree_notify': ['a_watcher@chromium.org']})

    self.call_gatekeeper()

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(mailer_data['recipients'], ['a_watcher@chromium.org'])

  def testForgivingSteps(self):
    """Test that forgiving steps set status but don't email blamelist."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'forgiving_steps': ['step1']})
    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)
    self.assertIn(self.set_status_url, urls)

  def testForgivingOptional(self):
    """Test that forgiving_optional steps set status but don't email."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'forgiving_optional': ['step1']})
    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)
    self.assertIn(self.set_status_url, urls)

  def testForgivingOptionalStar(self):
    """Test that forgiving_optional * sets status but doesn't email."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'forgiving_optional': ['*']})
    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)
    self.assertIn(self.set_status_url, urls)

  def testForgiveAllSteps(self):
    """Test that setting forgive_all prevents emailing the blamelist."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'forgive_all': 'true'})
    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)
    self.assertIn(self.set_status_url, urls)

  def testForgiveAllOptionalSteps(self):
    """Test that setting forgive_all prevents emailing the blamelist."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_optional': ['step1'],
                                 'forgive_all': 'true'})
    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)
    self.assertIn(self.set_status_url, urls)

  #### Revision tracking operation.

  def testEmptyRevisionInfoWorks(self):
    """Test that an empty build_db still lets revisions through."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72453})

  def testOlderRevisionIgnored(self):
    """Test that an old revision is ignored."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72454}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertNotIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72454})

  def testOlderCommitPositionIgnored(self):
    """Test that an old commit position is ignored."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'got_revision_cp'])

    self.masters[0].builders[0].builds[0].properties = [
        ['got_revision_cp', 'refs/heads/master@{#72453}', 'GatekeeperTest'],
        ['revision', 'a cool git sha', 'GatekeeperTest'],
        ['got_webkit_revision', 100, 'GatekeeperTest'],
    ]

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'got_revision_cp': 72454}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertNotIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'got_revision_cp': 72454})

  def testNewerRevisionAccepted(self):
    """Test that a newer revision is accepted."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72453})

  def testNewerCommitPositionAccepted(self):
    """Test that a newer revision is accepted."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'got_revision_cp'])

    self.masters[0].builders[0].builds[0].properties = [
        ['got_revision_cp', 'refs/heads/master@{#72453}', 'GatekeeperTest'],
        ['revision', 'a cool git sha', 'GatekeeperTest'],
        ['got_webkit_revision', 100, 'GatekeeperTest'],
    ]

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'got_revision_cp': 72452}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'got_revision_cp': 72453})


  def testRevisionChangeClears(self):
    """Test that changing the revision forces a reset in the build_db."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72454,
                                           'other_revision': 2}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72453})

  def testMultiRevisionAccepted(self):
    """Test that a newer multi-revision is accepted."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'revision,got_webkit_revision'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452,
                                           'got_webkit_revision': 100}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72453,
                       'got_webkit_revision': 100})

  def testMultiRevisionRejected(self):
    """Test that an older multi-revision is rejected."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'revision,got_webkit_revision'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452,
                                           'got_webkit_revision': 102}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertNotIn(self.set_status_url, urls)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72452,
                       'got_webkit_revision': 102})

  def testHighestGetsWritten(self):
    """Test that only the highest revision is written to the build_db."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'revision,got_webkit_revision'])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    new_build.properties = [
        ['revision', 72457, 'GatekeeperTest'],
        ['got_webkit_revision', 150, 'GatekeeperTest'],
    ]
    new_build.times = [200, 300]
    new_builder = Builder('mybuilder2', [new_build])
    self.masters[0].builders.append(new_builder)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[1].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[1].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            },
            'mybuilder2': {
                0: build_scan_db.gen_build(finished=True)
            }
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452,
                                           'got_webkit_revision': 100}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72457,
                       'got_webkit_revision': 150})

  def testLatestTriggered(self):
    """Test that only the latest failure is triggered if multiple are seen."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'revision,got_webkit_revision'])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    new_build.properties = [
        ['revision', 72457, 'GatekeeperTest'],
        ['got_webkit_revision', 150, 'GatekeeperTest'],
    ]
    new_build.times = [200, 300]
    new_builder = Builder('mybuilder2', [new_build])
    self.masters[0].builders.append(new_builder)

    newer_build = self.create_generic_build(
        3, ['a_third_committer@chromium.org'])
    newer_build.properties = [
        ['revision', 72459, 'GatekeeperTest'],
        ['got_webkit_revision', 200, 'GatekeeperTest'],
    ]
    newer_builder = Builder('mybuilder3', [newer_build])
    self.masters[0].builders.append(newer_builder)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[1].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[2].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[1].name,
                                {'closing_steps': ['step1']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[2].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            },
            'mybuilder2': {
                0: build_scan_db.gen_build(finished=True)
            },
            'mybuilder3': {
                0: build_scan_db.gen_build(finished=True)
            },
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452,
                                           'got_webkit_revision': 100}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    gatekeeper_data = urlparse.parse_qs(self.url_calls[-1]['params'])
    msg = ['Tree is closed (Automatic: "step1" on "mybuilder2" '
           'a_second_committer@chromium.org)']
    self.assertEquals(gatekeeper_data['message'], msg)

  def testOldFailuresNotRecorded(self):
    """Test that only new failing steps are recorded to the build_db.

    A failing step that we've already seen shouldn't bump triggered_revisions
    since doing so would bump triggered_revisions on every build -- suppressing
    every legitimate failure in the process.
    """
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file,
                      '--track-revisions',
                      '--revision-properties', 'revision,got_webkit_revision'])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    new_build.properties = [
        ['revision', 72459, 'GatekeeperTest'],
        ['got_webkit_revision', 200, 'GatekeeperTest'],
    ]
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[1].finished = False

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)
            },
        }
    })
    build_db.aux['triggered_revisions'] = {'revision': 72452,
                                           'got_webkit_revision': 100}

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    self.assertEquals(build_db.aux['triggered_revisions'],
                      {'revision': 72453,
                       'got_webkit_revision': 100})

  #### Multiple failures.

  def testSequentialFailures(self):
    """Test that the status app is only hit once if many failures are seen."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1', 'step2']})

    self.masters[0].builders[0].builds[1].steps[2].results = [2, None]

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    self.assertEquals(self.url_calls[-2]['url'], self.mailer_url)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-2]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])

  def testSequentialOneFailure(self):
    """Test that failing builds aren't mixed with good ones."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)
    self.assertEquals(urls.count(self.mailer_url), 1)

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])

  def testStarBuilder(self):
    """Test that * captures failures across all builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                '*',
                                {'closing_steps': ['step4']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStarBuilderOverride(self):
    """Test that * can be explicitly overridden."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    # step3 won't fail the build.
    self.add_gatekeeper_section(self.masters[0].url,
                                '*',
                                {'closing_steps': ['step3']})

    # But step4 will.
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step4']},
                                idx=0)

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testStarBuilderNoPropagate(self):
    """Test that * doesn't propagate to other builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    # step4 will fail the build.
    self.add_gatekeeper_section(self.masters[0].url,
                                '*',
                                {'closing_steps': ['step4']})

    # But step3 won't.
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step3']},
                                idx=0)

    urls = self.call_gatekeeper()

    self.assertNotIn(self.mailer_url, urls)

  def testMultiBuilderOneFailure(self):
    """Test that failure in one build doesn't affect another."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)},
            'mybuilder2': {
                0: build_scan_db.gen_build(finished=True)},
            }})

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders.append(Builder('mybuilder2', [new_build]))

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[0].builders[1].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[1].name,
                                {'closing_steps': ['step1']},
                                idx=0)

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    self.assertEquals(urls.count(self.mailer_url), 1)

    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])

  def testMultiBuilderFailures(self):
    """Test that failures on several builders are handled properly."""
    master_url = 'http://build.chromium.org/p/chromium.fyi'
    self.argv.extend([master_url,
                      '--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)},
            'mybuilder2': {
                0: build_scan_db.gen_build(finished=True)},
            }})

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders.append(Builder('mybuilder2', [new_build]))

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[0].builders[1].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[1].name,
                                {'closing_steps': ['step1']},
                                idx=0)

    urls = self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)

    self.assertEquals(self.url_calls[-2]['url'], self.mailer_url)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-2]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])

  def testMultiMaster(self):
    """Test that multiple master failures are handled properly."""
    self.masters.append(self.create_generic_build_tree('Chromium FYI 2',
                                                       'chromium2.fyi'))

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)},
        },
        self.masters[1].url: {
            'mybuilder': {
                0: build_scan_db.gen_build(finished=True)},
        },
    })

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.masters[1].builders[0].builds[0].blame = [
        'a_second_committer@chromium.org']
    self.masters[1].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[1].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper(build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)

    self.assertEquals(urls[-2], self.mailer_url)
    self.assertEquals(urls[-1], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-2]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_committer@chromium.org'])
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'],
                      ['a_second_committer@chromium.org'])

  #### Partial builds (still running).

  def testDontFailOmissionOnUncompletedBuild(self):
    """Don't fail a running build because of omitted steps."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps.append(
        BuildStep('step4', [], isStarted=True, isFinished=True))
    mybuild = self.create_generic_build(2, ['a_second_committer@chromium.org'])
    mybuild.finished = False
    self.masters[0].builders[0].builds.append(mybuild)
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step4']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testFailedBuildInProgress(self):
    """Test that a still-running build can close the tree."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status',
                      '--password-file', self.status_secret_file])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})
    mybuild = self.create_generic_build(2, ['a_second_committer@chromium.org'])
    mybuild.finished = False
    mybuild.steps[1].results = [2, None]
    self.masters[0].builders[0].builds.append(mybuild)

    urls = self.call_gatekeeper()
    self.assertIn(self.set_status_url, urls)
    self.assertIn(self.mailer_url, urls)
    for call in self.url_calls:
      if call['url'] == self.mailer_url:
        mailer_data = GatekeeperTest.decode_param_json(
            call['params'])
        self.assertEquals(mailer_data['result'], 2)

  def testUpdateBuildDBNotCompletedButFailed(self):
    """Test that partial builds increment the DB if they failed."""
    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                1: build_scan_db.gen_build(finished=True)}}})

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    mybuild = self.create_generic_build(2, ['a_second_committer@chromium.org'])
    mybuild.steps[1].results = [2, None]
    mybuild.finished = False
    self.masters[0].builders[0].builds.append(mybuild)
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper(build_db=build_db)
    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')

    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]

    self.assertEquals(finished_new_builds,
                      {1: build_scan_db.gen_build(finished=True)})
    self.assertEquals(unfinished_new_builds,
                      {2: build_scan_db.gen_build(triggered={
                          shas[0]: ['step1']})})

    self.assertIn(self.set_status_url, urls)

  def testDontUpdateBuildDBIfNotCompleted(self):
    """Test that partial builds aren't marked as finished."""
    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                1: build_scan_db.gen_build(finished=True),
                2: build_scan_db.gen_build()}}})

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    mybuild = self.create_generic_build(2, ['a_second_committer@chromium.org'])
    mybuild.finished = False
    self.masters[0].builders[0].builds.append(mybuild)
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step4']})

    urls = self.call_gatekeeper(build_db=build_db)
    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')

    self.assertEquals(finished_new_builds,
                      {1: build_scan_db.gen_build(finished=True)})
    self.assertEquals(unfinished_new_builds,
                      {2: build_scan_db.gen_build()})
    self.assertNotIn(self.set_status_url, urls)

  def testTriggeringDoesntTriggerOnSameBuild(self):
    """Test that a section won't fire twice on a build."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[0].finished = False
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(finished_new_builds,
                      {0: build_scan_db.gen_build(finished=True)})
    self.assertEquals(unfinished_new_builds,
                      {1: build_scan_db.gen_build(triggered={
                          shas[0]: ['step1']})})
    self.assertEquals(1, len([u for u in urls if u == self.set_status_url]))

  def testTriggeringOneHashDoesntStopAnother(self):
    """Test that firing on one hash doesn't prevent another hash triggering."""
    build_db = build_scan_db.gen_db(masters={
        self.masters[0].url: {
            'mybuilder': {
                1: build_scan_db.gen_build(finished=True)}}})

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    mybuild = self.create_generic_build(2, ['a_second_committer@chromium.org'])
    mybuild.finished = False
    self.masters[0].builders[0].builds.append(mybuild)
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper(build_db=build_db)
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step2']})
    self.masters[0].builders[0].builds[1].steps[2].results = [2, None]
    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(finished_new_builds,
                      {1: build_scan_db.gen_build(finished=True)})
    self.assertEquals(unfinished_new_builds,
                      {2: build_scan_db.gen_build(triggered={
                          shas[0]: ['step1'],
                          shas[1]: ['step2']})})
    self.assertEquals(2, len([u for u in urls if u == self.set_status_url]))

  def testTriggerIsRemovedIfNoFailure(self):
    """Test that build_db triggers aren't present if a step hasn't failed."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    _, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    self.assertEquals(finished_new_builds,
                      {2: build_scan_db.gen_build(
                        finished=True, succeeded=True)})

  def testOnlyFireOnNewFailures(self):
    """Test that the tree isn't closed if only an old test failed."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]

    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)

  def testTriggerDoesntPersistOldFailures(self):
    """Test that gatekeeper doesn't persist old failing tests."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.masters[0].builders[0].builds[0].steps[2].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1', 'step2']})

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)
    self.masters[0].builders[0].builds[1].steps[1].results = [2, None]

    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)

    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(finished_new_builds,
                      {2: build_scan_db.gen_build(finished=True, triggered={
                          shas[0]: ['step1'],
                          })})
    self.assertEquals(unfinished_new_builds, {})

  def testFireOnNewAndOldTests(self):
    """Test that build_db triggers when new steps go green and red."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1', 'step2']})

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    new_build = self.create_generic_build(
        2, ['a_second_committer@chromium.org'])
    self.masters[0].builders[0].builds.append(new_build)

    self.masters[0].builders[0].builds[1].steps[2].results = [2, None]

    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 2)
    unfinished_new_builds, finished_new_builds = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(finished_new_builds,
                      {2: build_scan_db.gen_build(finished=True, triggered={
                          shas[0]: [u'step2']})})
    self.assertEquals(unfinished_new_builds, {})

  def testRecordsAllFailuresInBuild(self):
    """Test that all failures are recorded, even after initial trigger."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--email-app-secret-file=%s' % self.email_secret_file,
                      '--set-status', '--password-file', self.status_secret_file
                      ])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1', 'step2']})

    self.masters[0].builders[0].builds[0].finished = False

    urls = self.call_gatekeeper()
    self.assertEquals(urls.count(self.set_status_url), 1)

    self.masters[0].builders[0].builds[0].steps[2].results = [2, None]

    build_db = build_scan_db.get_build_db(self.build_db_file)
    urls += self.call_gatekeeper(build_db=build_db)
    self.assertEquals(urls.count(self.set_status_url), 1)
    unfinished_new_builds, _ = self.process_build_db(
        self.masters[0].url, 'mybuilder')
    shas = self.get_gatekeeper_section_shas()[self.masters[0].url]
    self.assertEquals(unfinished_new_builds,
                      {1: build_scan_db.gen_build(triggered={
                          shas[0]: [
                              'step1', 'step2']})})

  ### JSON config file tests.

  def testInheritFromCategory(self):
    """Check that steps in categories are inherited by builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step1']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'categories': ['mycat']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testMultiCategory(self):
    """Check that steps in categories are inherited by builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[2].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step1']})
    self.add_gatekeeper_category('mycat2', {'closing_steps': ['step2']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'categories': ['mycat', 'mycat2']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testAddonCategory(self):
    """Check that builders can add-on to categories."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step2']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'categories': ['mycat'],
                                 'closing_steps': ['step1']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testInheritFromMaster(self):
    """Check that steps in masters are inherited by builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'sheriff_classes': ['sheriff_android']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']},
                                idx=0)

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'asheriff, anothersheriff\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    self.call_gatekeeper()

    # Check that gatekeeper checked the sheriff file.
    self.assertEquals(self.url_calls[-2]['url'], sheriff_url)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(mailer_data['recipients'],
                      ['a_committer@chromium.org',
                       'anothersheriff@google.com',
                       'asheriff@google.com'])

  def testAddonToMaster(self):
    """Check that steps in masters can be added by builders."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'sheriff_classes': ['sheriff_android']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'sheriff_classes': ['sheriff']},
                                idx=0)

    sheriff_url = 'http://build.chromium.org/p/chromium/sheriff_android.js'
    sheriff_string = 'document.write(\'asheriff, anothersheriff\')'
    self.handle_url_str(sheriff_url, sheriff_string)

    sheriff_url2 = 'http://build.chromium.org/p/chromium/sheriff.js'
    sheriff_string2 = 'document.write(\'asheriff2, anothersheriff2\')'
    self.handle_url_str(sheriff_url2, sheriff_string2)

    urls = self.call_gatekeeper()

    # Check that gatekeeper checked the sheriff file.
    self.assertIn(sheriff_url, urls)
    self.assertIn(sheriff_url2, urls)
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)

    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    mailer_data['recipients'].sort()
    self.assertEquals(sorted(mailer_data['recipients']),
                      ['a_committer@chromium.org',
                       'anothersheriff2@google.com',
                       'anothersheriff@google.com',
                       'asheriff2@google.com',
                       'asheriff@google.com'])

  def testInheritCategoryFromMaster(self):
    """Check that steps can inherit categories from masters."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step1']})
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {'categories': ['mycat']})
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testMasterSections(self):
    """Check that master sections work correctly."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step1']})
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {}
                                      )
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {},
                                idx=0)

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'categories': ['mycat']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testMasterSectionEmails(self):
    """Check that master section handles email properly."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_category('mycat', {'closing_steps': ['step1']})
    self.add_gatekeeper_master_config(self.masters[0].url,
                                      {}
                                      )
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1'],
                                 'tree_notify': ['a_watcher@chromium.org']},
                                idx=0)

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'categories': ['mycat']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org',
                                                  'a_watcher@chromium.org'])

  def testEmailFilter(self):
    """Test that no email is sent if the email isn't in the domain filter."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--filter-domain=squirrels.net,squirrels.com'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.mailer_url, urls)

  def testDisableEmailFilter(self):
    """Test that no email is sent if the email isn't in the domain filter."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--email-app-secret-file=%s' % self.email_secret_file,
                      '--disable-domain-filter',
                      '--filter-domain=squirrels.net,squirrels.com'])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.call_gatekeeper()

    # Check that gatekeeper indeed sent an email.
    self.assertEquals(self.url_calls[-1]['url'], self.mailer_url)
    mailer_data = GatekeeperTest.decode_param_json(
        self.url_calls[-1]['params'])
    self.assertEquals(mailer_data['recipients'], ['a_committer@chromium.org'])

  def testMasterNotConfigured(self):
    """Check that gatekeeper fails if a master isn't in config json."""

    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app'])
    with self.assertRaises(ValueError):
      self.call_gatekeeper()

  def testSectionWillNotCloseTree(self):
    """Test that close_tree=False sections don't call to the status app."""
    self.argv.extend([m.url for m in self.masters])
    self.argv.extend(['--skip-build-db-update',
                      '--no-email-app', '--set-status',
                      '--password-file', self.status_secret_file])

    self.masters[0].builders[0].builds[0].steps[1].results = [2, None]
    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'closing_steps': ['step1']})

    self.add_gatekeeper_master_section(self.masters[0].url, -1,
                                       {'close_tree': False})

    urls = self.call_gatekeeper()
    self.assertNotIn(self.set_status_url, urls)

  def testInvalidConfigIsCaught(self):
    self.argv.extend(['--verify'])

    self.add_gatekeeper_section(self.masters[0].url,
                                self.masters[0].builders[0].name,
                                {'squirrels': ['yay']})
    with self.assertRaises(AssertionError):
      self.call_gatekeeper()

  # Check that the checked in gatekeeper.json is valid.
  def testCheckedInConfigIsValid(self):
    self.argv.extend(['--verify'])
    self.call_gatekeeper(
        json=os.path.join(SCRIPT_DIR, os.pardir, 'gatekeeper.json'))

  def testCheckedInTreeConfigIsValid(self): # pylint: disable=R0201
    tree_config = os.path.join(SCRIPT_DIR, os.pardir, 'gatekeeper_trees.json')
    gatekeeper_ng_config.load_gatekeeper_tree_config(tree_config)


if __name__ == '__main__':
  with utils.print_coverage(include=['gatekeeper_ng.py']):
    unittest.main()
