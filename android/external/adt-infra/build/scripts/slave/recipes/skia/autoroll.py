# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Recipe for the Skia AutoRoll Bot."""


import re
from common.skia import global_constants


DEPS = [
  'file',
  'gclient',
  'gsutil',
  'json',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
]


APPENGINE_IS_STOPPED_URL = 'http://skia-tree-status.appspot.com/arb_is_stopped'
APPENGINE_SET_STATUS_URL = (
    'https://skia-tree-status.appspot.com/set_arb_status')
DEPS_ROLL_AUTHOR = 'skia-deps-roller@chromium.org'
DEPS_ROLL_NAME = 'Skia DEPS Roller'
RIETVELD_URL = 'https://codereview.chromium.org'
ISSUE_URL_TEMPLATE = RIETVELD_URL + '/%(issue)s/'

METATADATA_STATUS_PASSWORD_URL = ('http://metadata/computeMetadata/v1/project/'
                                  'attributes/skia_tree_status')

REGEXP_ISSUE_CREATED = (
    r'Issue created. URL: %s/(?P<issue>\d+)' % RIETVELD_URL)
REGEXP_ROLL_ACTIVE = (
    r'%s/(?P<issue>\d+)/ is still active' % RIETVELD_URL)
REGEXP_ROLL_STOPPED = (
    r'%s/(?P<issue>\d+)/: Rollbot was stopped by' % RIETVELD_URL)
# This occurs when the ARB has "caught up" and has nothing new to roll, or when
# a different roll (typically a manual roll) has already rolled past it.
REGEXP_ROLL_TOO_OLD = r'Already at .+ refusing to roll backwards to .+'
ROLL_STATUS_IN_PROGRESS = 'In progress'
ROLL_STATUS_STOPPED = 'Stopped'
ROLL_STATUS_IDLE = 'Idle'
ROLL_STATUSES = (
  (REGEXP_ISSUE_CREATED, ROLL_STATUS_IN_PROGRESS),
  (REGEXP_ROLL_ACTIVE,   ROLL_STATUS_IN_PROGRESS),
  (REGEXP_ROLL_STOPPED,  ROLL_STATUS_STOPPED),
  (REGEXP_ROLL_TOO_OLD,  ROLL_STATUS_IDLE),
)

from recipe_engine.recipe_api import Property

PROPERTIES = {
  "test_arb_is_stopped": Property(default=None),
}

def RunSteps(api, test_arb_is_stopped):
  # Check out Chrome.
  gclient_cfg = api.gclient.make_config()
  s = gclient_cfg.solutions.add()
  s.name = 'src'
  s.url = 'https://chromium.googlesource.com/chromium/src.git'
  gclient_cfg.got_revision_mapping['src/third_party/skia'] = 'got_revision'

  api.gclient.checkout(gclient_config=gclient_cfg)

  src_dir = api.path['checkout']
  api.step('git config user.name',
           ['git', 'config', '--local', 'user.name', DEPS_ROLL_NAME],
           cwd=src_dir)
  api.step('git config user.email',
           ['git', 'config', '--local', 'user.email', DEPS_ROLL_AUTHOR],
           cwd=src_dir)

  res = api.python.inline(
      'is_stopped',
      '''
      import urllib2
      import sys
      import time

      attempts = 5
      res = None
      for attempt in range(attempts):
        try:
          res = urllib2.urlopen(sys.argv[1]).read()
          break
        except urllib2.URLError:
          if attempt == attempts - 1:
            raise
          time.sleep(2 ** attempt)
      with open(sys.argv[2], 'w') as f:
        f.write(res)
      ''',
      args=[APPENGINE_IS_STOPPED_URL, api.json.output()],
      step_test_data=lambda: api.json.test_api.output({
        'is_stopped': test_arb_is_stopped,
       }))
  is_stopped = res.json.output['is_stopped']

  output = ''
  error = None
  issue = None
  if is_stopped:
    # Find any active roll and stop it.
    issue = api.python.inline(
      'stop_roll',
      '''
      import json
      import re
      import sys
      import urllib2

      sys.path.insert(0, sys.argv[4])
      import rietveld

      # Find the active roll, if it exists.
      res = json.load(urllib2.urlopen(
          '%s/search?closed=3&owner=%s&format=json' % (sys.argv[1], sys.argv[2])
      ))['results']
      issue = None
      for i in res:
        if re.search('Roll src/third_party/skia .*:.*', i['subject']):
          issue = i
          break

      # Report back the issue number.
      with open(sys.argv[3], 'w') as f:
        json.dump({'issue': issue['issue'] if issue else None}, f)

      # Uncheck the 'commit' box.
      if issue and issue['commit']:
        r = rietveld.Rietveld(sys.argv[1], None, sys.argv[2])
        r.set_flag(issue['issue'], issue['patchsets'][-1], 'commit', False)
      ''',
      args=[RIETVELD_URL, DEPS_ROLL_AUTHOR, api.json.output(),
            api.path['depot_tools']],
      step_test_data=lambda: api.json.test_api.output({'issue': 1234})
    ).json.output['issue']
  else:
    auto_roll = api.path['build'].join('scripts', 'tools', 'blink_roller',
                                       'auto_roll.py')
    try:
      output = api.step(
          'do auto_roll',
          ['python', auto_roll, 'skia', DEPS_ROLL_AUTHOR, src_dir],
          cwd=src_dir,
          stdout=api.raw_io.output()).stdout
    except api.step.StepFailure as f:
      output = f.result.stdout
      # Suppress failure for "refusing to roll backwards."
      if not re.search(REGEXP_ROLL_TOO_OLD, output):
        error = f

    match = (re.search(REGEXP_ISSUE_CREATED, output) or
             re.search(REGEXP_ROLL_ACTIVE, output) or
             re.search(REGEXP_ROLL_STOPPED, output))
    if match:
      issue = match.group('issue')

  if is_stopped:
    roll_status = ROLL_STATUS_STOPPED
  else:
    roll_status = None
    for regexp, status_msg in ROLL_STATUSES:
      match = re.search(regexp, output)
      if match:
        roll_status = status_msg
        break

  if roll_status:
    # POST status to appengine.
    api.python.inline(
      'update_status',
      '''
      import json
      import shlex
      import subprocess
      import sys
      import urllib
      import urllib2

      roll_status = sys.argv[1]
      password_url = sys.argv[2]
      issue_url = sys.argv[3]
      appengine_status_url = sys.argv[4]

      def full_hash(short):
        return subprocess.check_output(['git', 'rev-parse', short]).rstrip()

      password = urllib2.urlopen(urllib2.Request(
          password_url,
          headers={'Metadata-Flavor': 'Google'})).read()
      params = {'status': roll_status,
                'password': password}
      if issue_url == '' and roll_status == 'Idle':
        params['last_roll_rev'] = full_hash('origin/master')
      if issue_url != '':
        params['deps_roll_link'] = issue_url
        split = issue_url.split('/')
        split.insert(-2, 'api')
        api_url = '/'.join(split)
        issue_details = json.load(urllib2.urlopen(api_url))
        old, new = shlex.split(issue_details['subject'])[-1].split(':')
        params['last_roll_rev'] = full_hash(old)
        params['curr_roll_rev'] = full_hash(new)

      urllib2.urlopen(urllib2.Request(
          appengine_status_url,
          urllib.urlencode(params)))
      ''',
      args=[roll_status,
            METATADATA_STATUS_PASSWORD_URL,
            ISSUE_URL_TEMPLATE % {'issue': issue} if issue else '',
            APPENGINE_SET_STATUS_URL],
      cwd=src_dir.join('third_party/skia'))

  if error:
    # Pylint complains about raising NoneType, but that's exactly what we're
    # NOT doing here...
    # pylint: disable=E0702
    raise error


def GenTests(api):
  yield (
    api.test('AutoRoll_upload') +
    api.properties(test_arb_is_stopped=False) +
    api.step_data('do auto_roll', retcode=0, stdout=api.raw_io.output(
        'Issue created. URL: %s/1234' % RIETVELD_URL))
  )
  yield (
    api.test('AutoRoll_failed') +
    api.properties(test_arb_is_stopped=False) +
    api.step_data('do auto_roll', retcode=1, stdout=api.raw_io.output('fail'))
  )
  yield (
    api.test('AutoRoll_stopped') +
    api.properties(test_arb_is_stopped=True)
  )
