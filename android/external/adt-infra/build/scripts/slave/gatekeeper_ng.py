#!/usr/bin/env python
# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Closes tree if configured masters have failed tree-closing steps.

Given a list of masters, gatekeeper_ng will get a list of the latest builds from
the specified masters. It then checks if any tree-closing steps have failed, and
if so closes the tree and emails appropriate parties. Configuration for which
steps to close and which parties to notify are in a local gatekeeper.json file.
"""

from collections import defaultdict
from contextlib import closing, contextmanager
import argparse
import fnmatch
import getpass
import hashlib
import hmac
import itertools
import json
import logging
import operator
import os
import random
import re
import sys
import time
import urllib
import urllib2

from slave import build_scan
from slave import build_scan_db
from slave import gatekeeper_ng_config

DATA_DIR = os.path.dirname(os.path.abspath(__file__))

# Buildbot status enum.
SUCCESS, WARNINGS, FAILURE, SKIPPED, EXCEPTION, RETRY = range(6)


def get_pwd(password_file):
  if os.path.isfile(password_file):
    return open(password_file, 'r').read().strip()
  return getpass.getpass()


def in_glob_list(value, glob_list):
  """Returns True if 'value' matches any glob in 'glob_list'.

  Args:
    value: (str) The value to search for.
    glob_list: (list) A list of glob strings to test.
  """
  return any(fnmatch.fnmatch(value, glob)
             for glob in glob_list)


def logging_urlopen(url, *args, **kwargs):
  try:
    return urllib2.urlopen(url, *args, **kwargs)
  except urllib2.HTTPError as e:
    logging.debug('error accessing url %s: %s' % (url, e))
    raise


def update_status(tree_message, status_url_root, username, password, simulate):
  """Connects to chromium-status and closes the tree."""
  #TODO(xusydoc): append status if status is already closed.

  if isinstance(tree_message, unicode):
    tree_message = tree_message.encode('utf8')
  elif isinstance(tree_message, str):
    tree_message = tree_message.decode('utf8')

  params = urllib.urlencode({
      'message': tree_message,
      'username': username,
      'password': password
  })

  # Standard urllib doesn't raise an exception on 403, urllib2 does.
  if not simulate:
    status_url = status_url_root + "/status"
    with closing(logging_urlopen(status_url, params)):
      pass
  else:
    logging.info("Simulate: Setting tree [%s] status: %s",
                 status_url_root, tree_message)
  logging.info('success')


def get_tree_status(status_url_root, username, password):
  status_url = status_url_root + "/current?format=json"
  with closing(logging_urlopen(status_url)) as f:
    data = f.read()
  try:
    return json.loads(data)
  except ValueError:
    # Failed due to authentication error?
    if 'login' not in data:
      raise

  # Try using bot password to authenticate.
  params = urllib.urlencode({
      'username': username,
      'password': password
  })
  try:
    with closing(logging_urlopen(status_url, params)) as f:
      data = f.read()
  except urllib2.HTTPError, e:
    if e.code == 405:
      logging.warn("update your chromium_status app.")
    raise
  return json.loads(data)


def get_builder_section(gatekeeper_section, builder):
  """Returns the applicable gatekeeper config for the builder.

  If the builder isn't present or is excluded, return None.
  """
  if builder in gatekeeper_section:
    builder_section = gatekeeper_section[builder]
  elif '*' in gatekeeper_section:
    builder_section = gatekeeper_section['*']
  else:
    return None

  if not in_glob_list(builder, builder_section.get('excluded_builders', ())):
    return builder_section
  return None


def check_builds(master_builds, master_jsons, gatekeeper_config):
  """Given a gatekeeper configuration, see which builds have failed."""
  succeeded_builds = []
  failed_builds = []

  # Sort by buildnumber, highest first.
  sorted_builds = sorted(master_builds, key=lambda x: x[3], reverse=True)
  successful_builder_steps = defaultdict(lambda: defaultdict(set))
  current_builds_successful = True

  for build_json, master_url, builder, buildnum in sorted_builds:
    gatekeeper_sections = gatekeeper_config.get(master_url, [])
    for gatekeeper_section in gatekeeper_sections:
      section_hash = gatekeeper_ng_config.gatekeeper_section_hash(
          gatekeeper_section)

      gatekeeper = get_builder_section(
          gatekeeper_section, build_json['builderName'])
      if not gatekeeper:
        succeeded_builds.append((master_url, builder, buildnum))
        continue

      steps = build_json['steps']
      excluded_steps = set(gatekeeper.get('excluded_steps', []))
      forgiving = set(gatekeeper.get('forgiving_steps', [])) - excluded_steps
      forgiving_optional = (
          set(gatekeeper.get('forgiving_optional', [])) - excluded_steps)
      closing_steps = (
          set(gatekeeper.get('closing_steps', [])) | forgiving) - excluded_steps
      closing_optional = (
          (set(gatekeeper.get('closing_optional', [])) | forgiving_optional) -
          excluded_steps
      )
      tree_notify = set(gatekeeper.get('tree_notify', []))
      sheriff_classes = set(gatekeeper.get('sheriff_classes', []))
      status_template = gatekeeper.get(
          'status_template', gatekeeper_ng_config.DEFAULTS['status_template'])
      subject_template = gatekeeper.get(
          'subject_template', gatekeeper_ng_config.DEFAULTS[
             'subject_template'])
      finished = [s for s in steps if s.get('isFinished')]
      close_tree = gatekeeper.get('close_tree', True)
      respect_build_status = gatekeeper.get('respect_build_status', False)

      # We ignore EXCEPTION and RETRY here since those are usually
      # infrastructure-related instead of actual test errors.
      successful_steps = set(s['name'] for s in finished
                             if s.get('results', [FAILURE])[0] != FAILURE)

      successful_builder_steps[master_url][builder].update(successful_steps)

      finished_steps = set(s['name'] for s in finished)

      if '*' in forgiving_optional:
        forgiving_optional = (finished_steps - excluded_steps)
      if '*' in closing_optional:
        closing_optional = (finished_steps - excluded_steps)

      unsatisfied_steps = closing_steps - successful_steps
      failed_steps = finished_steps - successful_steps
      failed_optional_steps = failed_steps & closing_optional
      unsatisfied_steps |= failed_optional_steps

      # Build is not yet finished, don't penalize on unstarted/unfinished steps.
      if build_json.get('results', None) is None:
        unsatisfied_steps &= finished_steps

      # If the entire build failed.
      if (not unsatisfied_steps and 'results' in build_json and
          build_json['results'] == FAILURE and respect_build_status):
        unsatisfied_steps.add('[overall build status]')

      buildbot_url = master_jsons[master_url]['project']['buildbotURL']
      project_name = master_jsons[master_url]['project']['title']

      if unsatisfied_steps:
        failed_builds.append(({'base_url': buildbot_url,
                               'build': build_json,
                               'close_tree': close_tree,
                               'forgiving_steps': (
                                   forgiving | forgiving_optional),
                               'project_name': project_name,
                               'sheriff_classes': sheriff_classes,
                               'subject_template': subject_template,
                               'status_template': status_template,
                               'tree_notify': tree_notify,
                               'unsatisfied': unsatisfied_steps,
                              },
                              master_url,
                              builder,
                              buildnum,
                              section_hash))
        # If there is a failing step that a newer builder hasn't succeeded on,
        # don't open the tree.
        still_failing_steps = (
            unsatisfied_steps - successful_builder_steps[master_url][builder])
        if still_failing_steps:
          logging.debug('%s failed on %s, not yet resolved.',
              ','.join(still_failing_steps),
              generate_build_url(failed_builds[-1][0]))
          current_builds_successful = False
      else:
        succeeded_builds.append((master_url, builder, buildnum))

  return (list(reversed(failed_builds)), list(reversed(succeeded_builds)),
      successful_builder_steps, current_builds_successful)


def propagate_build_status_back_to_db(failure_tuples, success_tuples, build_db):
  """Write back to build_db which finished steps failed or succeeded."""
  for _, master_url, builder, buildnum, _ in failure_tuples:
    builder_dict = build_db.masters[master_url][builder]
    if builder_dict[buildnum].finished:
      # pylint: disable=W0212
      builder_dict[buildnum] = builder_dict[buildnum]._replace(
          succeeded=False)
  for master_url, builder, buildnum in success_tuples:
    builder_dict = build_db.masters[master_url][builder]
    if builder_dict[buildnum].finished:
      # pylint: disable=W0212
      builder_dict[buildnum] = builder_dict[buildnum]._replace(
          succeeded=True)


def get_build_properties(build_json, properties):
  """Obtains multiple build_properties from a build.

  Sets a property to None if it's not in the build.
  """

  properties = set(properties)
  result = dict.fromkeys(properties)  # Populates dict with {key: None}.
  for p in build_json.get('properties', []):
    if p[0] in properties:
      result[p[0]] = p[1]
  return result


@contextmanager
def log_section(url, builder, buildnum, section_hash=None):
  """Wraps a code block with information about a build it operates on."""
  logging.debug('%sbuilders/%s/builds/%d ----', url, builder, buildnum)
  if section_hash:
    logging.debug('  section hash: %s', section_hash)
  yield
  logging.debug('----')


COMMIT_POSITION_REGEX = re.compile(r'(.*)@{#(\d+)}')


def parse_commit_position(prop):
  """Determine if the revision is a SVN revision or a git commit position.

  If the revision is a git commit position, return just the numeric part.
  """
  if not isinstance(prop, basestring):
    return prop

  match = COMMIT_POSITION_REGEX.match(prop)
  if not match:
    return prop
  else:
    return int(match.group(2))


def convert_revisions_to_positions(property_dict):
  """Given a dictionary of revisions, return a dict of parsed revisions."""
  result = {}
  for k, v in property_dict.iteritems():
    result[k] = parse_commit_position(v)
  return result


def reject_old_revisions(failed_builds, build_db):
  """Ignore builds which triggered on revisions older than the current.

  triggered_revisions has the format: {'revision': 500,
                                       'got_webkit_revision': 15,
                                      }
  Each key is a buildproperty that was previously triggered on, and each value
  was the value of that key. Note that all keys present in triggered_revisions
  are used for the comparison. Only builds where at least one number is greater
  than and all numbers are greater than or equal are considered 'new' and are
  not rejected by this function. Any change in the set of keys triggers a full
  reset of the recorded data. In the common case, triggered_revisions only has
  one key ('revision') and rejects all builds where revision is less than or
  equal to the last triggered revision.
  """

  triggered_revisions = build_db.aux.get('triggered_revisions', {})
  if not triggered_revisions:
    # There was no previous revision information, so by default keep all
    # failing builds.
    logging.debug('no previous revision tracking information, '
                  'keeping all failures.')
    return failed_builds

  def build_start_time(build):
    """Sorting key that returns a build's build start time.

    By using reversed start time, we sort such that the latest builds come
    first. This gives us a crude approximation of revision order, which means
    we can update triggered_revisions with the highest revision first. Note that
    this isn't perfect, but the likelihood of multiple failures occurring in the
    same minute is low and multi-revision sorting is potentially error-prone. An
    action-log based approach would obviate this hack.
    """
    return build['build'].get('times', [None])[0]

  kept_builds = []
  for build in sorted(failed_builds, key=build_start_time, reverse=True):
    builder = build['build']['builderName']
    buildnum = build['build']['number']
    with log_section(build['base_url'], builder, buildnum):
      # get_build_properties will return a dict with all the keys given to it.
      # Since we're giving it triggered_revisions.keys(), revisions is
      # guaranteed to have the same keys as triggered_revisions.
      revisions = convert_revisions_to_positions(get_build_properties(
          build['build'], triggered_revisions.keys()))

      logging.debug('previous revision information: %s',
                    str(triggered_revisions))
      logging.debug('current revision information: %s', str(revisions))

      if any(x is None for x in revisions.itervalues()):
        # The revisions aren't in this build, err on the side of noisy.
        logging.debug('Nones detected in revision tracking information, '
                      'keeping build.')
        triggered_revisions = revisions
        kept_builds.append(build)
        continue

      paired = []
      for k in revisions:
        paired.append((triggered_revisions[k], revisions[k]))

      if all(l <= r for l, r in paired) and any(l < r for l, r in paired):
        # At least one revision is greater and all the others are >=, so let
        # this revision through.
        # TODO(stip): evaluate the greatest revision if we see a stream of
        # failures at once.
        logging.debug('keeping build')
        kept_builds.append(build)
        triggered_revisions = revisions
        continue
      logging.debug('rejecting build')

  build_db.aux['triggered_revisions'] = triggered_revisions
  return kept_builds


def debounce_failures(failed_builds, current_builds_successful, build_db):
  """Using trigger information in build_db, make sure we don't double-fire."""

  @contextmanager
  def save_build_failures(master_url, builder, buildnum, section_hash,
                          unsatisfied):
    yield
    build_db.masters[master_url][builder][buildnum].triggered[
        section_hash] = unsatisfied

  if failed_builds and current_builds_successful:
    logging.debug(
        'All failing steps succeeded in later runs, not closing tree.')
    return []
  true_failed_builds = []
  for build, master_url, builder, buildnum, section_hash in failed_builds:
    with log_section(build['base_url'], builder, buildnum, section_hash):
      with save_build_failures(master_url, builder, buildnum, section_hash,
                               build['unsatisfied']):
        build_db_builder = build_db.masters[master_url][builder]

        # Determine what the current and previous failing steps are.
        prev_triggered = []
        if buildnum-1 in build_db_builder:
          prev_triggered = build_db_builder[buildnum-1].triggered.get(
              section_hash, [])

        logging.debug('  previous failing tests: %s', ','.join(
            sorted(prev_triggered)))
        logging.debug('  current failing tests: %s', ','.join(
            sorted(build['unsatisfied'])))

        # Skip build if we already fired (or if the failing tests aren't new).
        if section_hash in build_db_builder[buildnum].triggered:
          logging.debug('  section has already been triggered for this build, '
                        'skipping...')
          continue

        new_tests = set(build['unsatisfied']) - set(prev_triggered)
        if not new_tests:
          logging.debug('  no new steps failed since previous build %d',
                        buildnum-1)
          continue

        logging.debug('  new failing steps since build %d: %s', buildnum-1,
                      ','.join(sorted(new_tests)))

        # If we're here it's a legit failing build.
        true_failed_builds.append(build)

        logging.debug('  build steps: %s', ', '.join(
            s['name'] for s in build['build']['steps']))
        logging.debug('  build complete: %s', bool(
            build['build'].get('results', None) is not None))
        logging.debug('  set to close tree: %s', build['close_tree'])
        logging.debug('  build failed: %s', bool(build['unsatisfied']))

  return true_failed_builds


def parse_sheriff_file(url):
  """Given a sheriff url, download and parse the appropirate sheriff list."""
  with closing(logging_urlopen(url)) as f:
    line = f.readline()
  usernames_matcher_ = re.compile(r'document.write\(\'([\w, ]+)\'\)')
  usernames_match = usernames_matcher_.match(line)
  sheriffs = set()
  if usernames_match:
    usernames_str = usernames_match.group(1)
    if usernames_str != 'None (channel is sheriff)':
      for sheriff in usernames_str.split(', '):
        if sheriff.count('@') == 0:
          sheriff += '@google.com'
        sheriffs.add(sheriff)
  return sheriffs


def get_sheriffs(classes, base_url):
  """Given a list of sheriff classes, download and combine sheriff emails."""
  sheriff_sets = (parse_sheriff_file(base_url % cls) for cls in classes)
  return reduce(operator.or_, sheriff_sets, set())


def hash_message(message, url, secret):
  utc_now = time.time()
  salt = random.getrandbits(32)
  hasher = hmac.new(secret, message, hashlib.sha256)
  hasher.update(str(utc_now))
  hasher.update(str(salt))
  client_hash = hasher.hexdigest()

  return {'message': message,
          'time': utc_now,
          'salt': salt,
          'url': url,
          'hmac-sha256': client_hash,
         }


def submit_email(email_app, build_data, secret, simulate):
  """Submit json to a mailer app which sends out the alert email."""
  if simulate:
    logging.info("Simulate: Sending e-mail via [%s]: %s", email_app, build_data)
    return

  url = email_app + '/email'
  data = hash_message(json.dumps(build_data, sort_keys=True), url, secret)

  req = urllib2.Request(url, urllib.urlencode({'json': json.dumps(data)}))
  with closing(logging_urlopen(req)) as f:
    code = f.getcode()
    if code != 200:
      response = f.read()
      raise Exception('error connecting to email app: code %d %s' % (
          code, response))


def open_tree_if_possible(build_db, master_jsons, successful_builder_steps,
    current_builds_successful, username, password, status_url_root,
    set_status, emoji, simulate):
  if not current_builds_successful:
    logging.debug('Not opening tree because failing steps were detected.')
    return

  previously_failed_builds = []
  for master_url, master in master_jsons.iteritems():
    for builder in master['builders']:
      builder_dict = build_db.masters.get(master_url, {}).get(builder, {})
      for buildnum, build in builder_dict.iteritems():
        if build.finished:
          if not build.succeeded:
            if build.triggered:
              # See crbug.com/389740 for why the 0 is there.
              failing_steps = set(build.triggered.values()[0])
            else:
              failing_steps = set()
            still_failing_steps = (
                failing_steps - successful_builder_steps[master_url][builder])
            if still_failing_steps:
              previously_failed_builds.append(
                  '%s on %s %s/builders/%s/builds/%d' % (
                  ','.join(still_failing_steps), builder, master_url,
                  urllib.quote(builder), buildnum))

  if previously_failed_builds:
    logging.debug(
        'Not opening tree because previous builds weren\'t successful:')
    for build in previously_failed_builds:
      logging.debug('  %s' % build)
    return

  status = get_tree_status(status_url_root, username, password)
  # Don't change the status unless the tree is currently closed.
  if status['general_state'] != 'closed':
    logging.debug('Not opening tree because it is not closed (%s)'
                  % status['general_state'])
    return

  # Don't override human closures.
  closed_tree_key = 'closed_tree-%s' % status_url_root
  last_gatekeeper_closure = build_db.aux.get(closed_tree_key)
  if last_gatekeeper_closure:
    # Line 378 of http://goo.gl/ViyxDW limits status messages to 500 chars. The
    # code replaces the 500th char with a unicode ellipsis, so we effectively
    # have 499 chars to work with.
    status_limit = 499
    if (last_gatekeeper_closure['message'][:status_limit]
        != status['message'][:status_limit]):
      logging.debug(
          'Not opening tree because we didn\'t set the last message: %s vs %s'
          % (last_gatekeeper_closure['message'], status['message']))
      return
  else:
    # Backwards compatability hack.
    if not re.search(r"automatic", status['message'], re.IGNORECASE):
      logging.debug('Not opening tree because \'automatic\' was not found in %s'
                    % status['message'])
      return

  logging.info('All builders are green, opening the tree...')

  tree_status = 'Tree is open (Automatic)'
  if emoji:
    random_emoji = random.choice(emoji)
    if random_emoji.endswith(')'):
      random_emoji += ' '
    tree_status = 'Tree is open (Automatic: %s)' % random_emoji
  logging.info('Opening tree with message: \'%s\'' % tree_status)
  build_db.aux[closed_tree_key] = {}
  if set_status:
    update_status(tree_status, status_url_root, username, password, simulate)
  else:
    logging.info('set-status not set, not connecting to chromium-status!')


def generate_build_url(build):
  """Creates a URL to reference the build."""
  return '%s/builders/%s/builds/%d' % (
      build['base_url'].rstrip('/'),
      urllib.quote(build['build']['builderName']),
      build['build']['number']
  )


def get_results_string(result_value):
  """Returns a string for a BuildBot result value (SUCCESS, FAILURE, etc.)."""
  return {
      SUCCESS: 'success',
      WARNINGS: 'warnings',
      FAILURE: 'failure',
      SKIPPED: 'skipped',
      EXCEPTION: 'exception',
      RETRY: 'retry',
  }.get(result_value, 'unknown')


def close_tree_if_necessary(build_db, failed_builds, username, password,
                            status_url_root, set_status, revision_properties,
                            simulate):
  """Given a list of failed builds, close the tree if necessary."""

  closing_builds = [b for b in failed_builds if b['close_tree']]
  if not closing_builds:
    logging.info('no tree-closing failures!')
    return

  status = get_tree_status(status_url_root, username, password)
  # Don't change the status unless the tree is currently open.
  if status['general_state'] != 'open':
    return

  logging.info('%d failed builds found, closing the tree...' %
               len(closing_builds))

  template_build = closing_builds[0]
  template_vars = {
      'blamelist': ','.join(template_build['build']['blame']),
      'build_url': generate_build_url(template_build),
      'builder_name': template_build['build']['builderName'],
      'project_name': template_build['project_name'],
      'unsatisfied': ','.join(template_build['unsatisfied']),
      'result': get_results_string(template_build['build'].get('results')),
  }

  # First populate un-transformed build properties
  revision_props = get_build_properties(template_build['build'],
    ['revision', 'got_revision', 'buildnumber',])

  # Second add in transformed specified revision_properties.
  revision_props.update(convert_revisions_to_positions(
      get_build_properties(template_build['build'], revision_properties)))

  template_vars.update(revision_props)

  # Close on first failure seen.
  tree_status = template_build['status_template'] % template_vars

  logging.info('closing the tree with message: \'%s\'' % tree_status)
  if set_status:
    update_status(tree_status, status_url_root, username, password, simulate)
    closed_tree_key = 'closed_tree-%s' % status_url_root
    build_db.aux[closed_tree_key] = {
        'message': tree_status,
    }
  else:
    logging.info('set-status not set, not connecting to chromium-status!')


def notify_failures(failed_builds, sheriff_url, default_from_email,
                    email_app_url, secret, domain, filter_domain,
                    disable_domain_filter, simulate):
  # Email everyone that should be notified.
  emails_to_send = []
  for failed_build in failed_builds:
    waterfall_url = failed_build['base_url'].rstrip('/')
    build_url = generate_build_url(failed_build)
    project_name = failed_build['project_name']
    fromaddr = failed_build['build'].get('fromAddr', default_from_email)

    tree_notify = failed_build['tree_notify']

    if failed_build['unsatisfied'] <= failed_build['forgiving_steps']:
      blamelist = set()
    else:
      blamelist = set(failed_build['build']['blame'])

    sheriffs = get_sheriffs(failed_build['sheriff_classes'], sheriff_url)
    watchers = list(tree_notify | blamelist | sheriffs)

    build_data = {
        'build_url': build_url,
        'from_addr': fromaddr,
        'project_name': project_name,
        'subject_template': failed_build['subject_template'],
        'steps': [],
        'unsatisfied': list(failed_build['unsatisfied']),
        'waterfall_url': waterfall_url,
    }

    for field in ['builderName', 'number', 'reason']:
      build_data[field] = failed_build['build'][field]

    # The default value here is 2. In the case of failing on an unfinished
    # build, the build won't have a result yet. As of now, chromium-build treats
    # anything as 'not failure' as warning. Since we can't get into
    # notify_failures without a failure, it makes sense to have the default
    # value be failure (2) here.
    build_data['result'] = failed_build['build'].get('results', 2)
    build_data['blamelist'] = failed_build['build']['blame']
    build_data['changes'] = failed_build['build'].get('sourceStamp', {}).get(
        'changes', [])

    build_data['revisions'] = [x['revision'] for x in build_data['changes']]

    for step in failed_build['build']['steps']:
      new_step = {}
      for field in ['text', 'name', 'logs']:
        new_step[field] = step[field]
      new_step['started'] = step.get('isStarted', False)
      new_step['urls'] = step.get('urls', [])
      new_step['results'] = step.get('results', [0, None])[0]
      build_data['steps'].append(new_step)

    if email_app_url and watchers:
      emails_to_send.append((watchers, json.dumps(build_data, sort_keys=True)))

    buildnum = failed_build['build']['number']
    steps = failed_build['unsatisfied']
    builder = failed_build['build']['builderName']
    logging.info(
        'to %s: failure in %s build %s: %s' % (', '.join(watchers),
                                                        builder, buildnum,
                                                        list(steps)))
    if not email_app_url:
      logging.warn('no email_app_url specified, no email sent!')

  filtered_emails_to_send = []
  for email in emails_to_send:
    new_watchers = [x if '@' in x else (x + '@' + domain) for x in email[0]]
    if not disable_domain_filter:
      new_watchers = [x for x in new_watchers if x.split('@')[-1] in
                      filter_domain]
    if new_watchers:
      filtered_emails_to_send.append((new_watchers, email[1]))

  # Deduplicate emails.
  keyfunc = lambda x: x[1]
  for k, g in itertools.groupby(sorted(filtered_emails_to_send, key=keyfunc),
                                keyfunc):
    watchers = list(reduce(operator.or_, [set(e[0]) for e in g], set()))
    build_data = json.loads(k)
    build_data['recipients'] = watchers
    submit_email(email_app_url, build_data, secret, simulate)


def simulate_build_failure(build_db, master, builder, *steps):
  master_json = {
      'project': {
        'buildbotURL': master,
        'title': 'Simulated Master',
      },
      'builders': [builder],
  }
  build_json = (
      {
        'builderName': builder,
        'number': 0,
        'steps': [{
          'name': s,
          'isFinished': True,
          'text': [
            'Simulated Build Step',
          ],
          'logs': [],
        } for s in steps],
        'results': FAILURE,
        'reason': 'simulation',
        'blame': ['you'],
      },
      master,
      builder,
      0,
  )
  build_db.masters.setdefault(master, {})
  build_db.masters[master].setdefault(builder, {})
  build_db.masters[master][builder][0] = build_scan_db.gen_build(finished=True)
  return {master: master_json}, (build_json,)


def get_args(argv):
  parser = argparse.ArgumentParser(description='Closes the tree if annotated '
                                               'builds fail.')
  parser.add_argument('--build-db', default='build_db.json',
                      help='records the last-seen build for each builder')
  parser.add_argument('--clear-build-db', action='store_true',
                      help='reset build_db to be empty')
  parser.add_argument('--sync-build-db', action='store_true',
                      help='don\'t process any builds, but update build_db '
                           'to the latest build numbers')
  parser.add_argument('--skip-build-db-update', action='store_true',
                      help='don\' write to the build_db, overridden by sync and'
                           ' clear db options')
  parser.add_argument('--password-file', default='.status_password',
                      help='password file to update chromium-status')
  parser.add_argument('-s', '--set-status', action='store_true',
                      help='close the tree by connecting to chromium-status')
  parser.add_argument('--open-tree', action='store_true',
                      help='open the tree by connecting to chromium-status')
  parser.add_argument('--status-url',
                      default='https://chromium-status.appspot.com',
                      help='URL for root of the status app')
  parser.add_argument('--track-revisions', action='store_true',
                      help='only close on increasing revisions')
  parser.add_argument('--revision-properties', default='revision',
                      help='comma-separated list of buildproperties to compare '
                           'revision on.')
  parser.add_argument('--status-user', default='buildbot@chromium.org',
                      help='username for the status app')
  parser.add_argument('--disable-domain-filter', action='store_true',
                      help='allow emailing any domain')
  parser.add_argument('--filter-domain', default='chromium.org,google.com',
                      help='only email users in these comma separated domains')
  parser.add_argument('--email-domain', default='google.com',
                      help='default email domain to add to users without one')
  parser.add_argument('--sheriff-url',
                      default='http://build.chromium.org/p/chromium/%s.js',
                      help='URL pattern for the current sheriff list')
  parser.add_argument('--parallelism', default=16,
                      help='up to this many builds can be queried '
                           'simultaneously')
  parser.add_argument('--default-from-email',
                      default='buildbot@chromium.org',
                      help='default email address to send from')
  parser.add_argument('--email-app-url',
                      default='https://chromium-build.appspot.com/mailer',
                      help='URL of the application to send email from')
  parser.add_argument('--email-app-secret-file',
                      default='.mailer_password',
                      help='file containing secret used in email app auth')
  parser.add_argument('--no-email-app', action='store_true',
                      help='don\'t send emails')
  parser.add_argument('--json',
                      default=os.path.join(DATA_DIR, 'gatekeeper.json'),
                      help='location of gatekeeper configuration file')
  parser.add_argument('--emoji',
                      default=os.path.join(DATA_DIR, 'gatekeeper_emoji.json'),
                      help='location of gatekeeper configuration file (None to'
                           'turn off)')
  parser.add_argument('--verify', action='store_true',
                      help='verify that the gatekeeper config file is correct')
  parser.add_argument('--flatten-json', action='store_true',
                      help='display flattened gatekeeper.json for debugging')
  parser.add_argument('--no-hashes', action='store_true',
                      help='don\'t insert gatekeeper section hashes')
  parser.add_argument('-v', '--verbose', action='store_true',
                      help='turn on extra debugging information')
  parser.add_argument('master_url', nargs='*',
                      help='The master URLs to poll.')

  group = parser.add_argument_group(title='Testing')
  group.add_argument('--simulate-master', metavar='MASTER',
                     help='Simulate a build failure. This is the name of the '
                          'master on which the failure occurs.')
  group.add_argument('--simulate-builder', metavar='BUILDER',
                     help='The builder to simulate the failure on.')
  group.add_argument('--simulate-step', metavar='NAME', default=[],
                     action='append',
                     help='The steps to simulate completion.')

  args = parser.parse_args(argv)

  args.email_app_secret = None
  args.password = None

  if args.no_hashes and not args.flatten_json:
    parser.error('specifying --no-hashes doesn\'t make sense without '
                 '--flatten-json')

  if args.verify or args.flatten_json:
    return args

  if not args:
    parser.error('you need to specify at least one master URL')

  if args.no_email_app:
    args.email_app_url = None

  if args.email_app_url and not args.simulate_master:
    if os.path.exists(args.email_app_secret_file):
      with open(args.email_app_secret_file) as f:
        args.email_app_secret = f.read().strip()
    else:
      parser.error('Must provide email app auth with  %s.' % (
          args.email_app_secret_file))

  args.filter_domain = args.filter_domain.split(',')

  args.master_url = [url.rstrip('/') for url in args.master_url]

  return args


def main(argv):
  args = get_args(argv)

  logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO)

  gatekeeper_config = gatekeeper_ng_config.load_gatekeeper_config(args.json)

  if args.verify:
    return 0

  simulate = bool(args.simulate_master)

  if args.flatten_json:
    if not args.no_hashes:
      gatekeeper_config = gatekeeper_ng_config.inject_hashes(gatekeeper_config)
    gatekeeper_ng_config.flatten_to_json(gatekeeper_config, sys.stdout)
    print
    return 0

  if args.set_status and not simulate:
    args.password = get_pwd(args.password_file)

  masters = set(args.master_url)
  if not masters <= set(gatekeeper_config):
    print 'The following masters are not present in the gatekeeper config:'
    for m in masters - set(gatekeeper_config):
      print '  ' + m
    return 1

  emoji = []
  if args.emoji != 'None':
    try:
      with open(args.emoji) as f:
        emoji = json.load(f)
    except (IOError, ValueError) as e:
      logging.warning('Could not load emoji file %s: %s', args.emoji, e)

  if args.clear_build_db:
    build_db = build_scan_db.gen_db()
    build_scan_db.save_build_db(build_db, gatekeeper_config,
                                args.build_db)
  else:
    build_db = build_scan_db.get_build_db(args.build_db)

  if not simulate:
    master_jsons, build_jsons = build_scan.get_updated_builds(
        masters, build_db, args.parallelism)
  else:
    master_jsons, build_jsons = simulate_build_failure(
        build_db, args.simulate_master, args.simulate_builder,
        *args.simulate_step)

  if args.sync_build_db:
    build_scan_db.save_build_db(build_db, gatekeeper_config,
                                args.build_db)
    return 0

  (failure_tuples, success_tuples, successful_builder_steps,
      current_builds_successful) = check_builds(
        build_jsons, master_jsons, gatekeeper_config)

  # Write failure / success information back to the build_db.
  propagate_build_status_back_to_db(failure_tuples, success_tuples, build_db)

  # opening is an option, mostly to keep the unittests working which
  # assume that any setting of status is negative.
  if args.open_tree:
    open_tree_if_possible(build_db, master_jsons, successful_builder_steps,
        current_builds_successful, args.status_user, args.password,
        args.status_url, args.set_status, emoji, simulate)

  # debounce_failures does 3 things:
  # 1. Groups logging by builder
  # 2. Selects out the "build" part from the failure tuple.
  # 3. Rejects builds we've already warned about (and logs).
  new_failures = debounce_failures(failure_tuples,
      current_builds_successful, build_db)

  if args.track_revisions:
    # Only close the tree if it's a newer revision than before.
    properties = args.revision_properties.split(',')
    triggered_revisions = build_db.aux.get('triggered_revisions', {})
    if not triggered_revisions or (
        sorted(triggered_revisions) != sorted(properties)):
      logging.info('revision properties have changed from %s to %s. '
                   'clearing previous data.', triggered_revisions, properties)
      build_db.aux['triggered_revisions'] = dict.fromkeys(properties)
    new_failures = reject_old_revisions(new_failures, build_db)

  close_tree_if_necessary(build_db, new_failures,
                          args.status_user, args.password,
                          args.status_url, args.set_status,
                          args.revision_properties.split(','),
                          simulate)
  try:
    notify_failures(new_failures, args.sheriff_url,
                    args.default_from_email, args.email_app_url,
                    args.email_app_secret, args.email_domain,
                    args.filter_domain, args.disable_domain_filter,
                    simulate)
  finally:
    if not args.skip_build_db_update and not simulate:
      build_scan_db.save_build_db(build_db, gatekeeper_config,
                               args.build_db)

  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
