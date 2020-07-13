# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import re
import urllib

from twisted.internet import defer
from twisted.python import log

from buildbot.process.properties import Properties
from buildbot.schedulers.base import BaseScheduler
from buildbot.status import results
from buildbot.status.base import StatusReceiverMultiService
from buildbot.status.builder import Results

from common.gerrit_agent import GerritAgent
from master.gerrit_poller import GerritPoller


class JobDefinition(object):
  """Describes a try job posted on Gerrit."""
  def __init__(self, builder_names=None, build_properties=None):
    # Force str type and remove empty builder names.
    self.builder_names = [str(b) for b in (builder_names or []) if b]
    self.build_properties = build_properties

  def __repr__(self):
    return repr(self.__dict__)

  @staticmethod
  def parse(text):
    """Parses a try job definition."""
    text = text and text.strip()
    if not text:
      # Return an empty definition.
      return JobDefinition()

    # Parse as json.
    try:
      job = json.loads(text)
    except:
      raise ValueError('Couldn\'t parse job definition: %s' % text)

    # Convert to canonical form.
    if isinstance(job, list):
      # Treat a list as builder name list.
      job = {'builderNames': job}
    elif not isinstance(job, dict):
      raise ValueError('Job definition must be a JSON object or array.')

    return JobDefinition(
        builder_names=job.get('builderNames'),
        build_properties=job.get('build_properties')
    )


class _TryJobGerritPoller(GerritPoller):
  """Polls issues, creates changes and calls scheduler.submitJob.

  This class is a part of TryJobGerritScheduler implementation and not designed
  to be used otherwise.
  """

  change_category = 'tryjob'
  label_name = 'Tryjob-Request'

  MESSAGE_REGEX_TRYJOB = re.compile('^!tryjob(.*)$', re.I | re.M)

  def __init__(self, scheduler, gerrit_host, gerrit_projects=None,
               pollInterval=None, dry_run=None):
    assert scheduler
    GerritPoller.__init__(self, gerrit_host, gerrit_projects, pollInterval,
                          dry_run)
    self.scheduler = scheduler

  def _is_interesting_message(self, message):
    return self.MESSAGE_REGEX_TRYJOB.search(message['message'])

  def getChangeQuery(self):
    query = GerritPoller.getChangeQuery(self)
    # Request only issues with TryJob=+1 label.
    query += '+label:%s=%s' % (self.label_name, urllib.quote('+1'))
    return query

  def parseJob(self, message):
    """Parses a JobDefinition from a Gerrit message."""
    tryjob_match = self.MESSAGE_REGEX_TRYJOB.search(message['message'])
    assert tryjob_match
    return JobDefinition.parse(tryjob_match.group(1))

  @defer.inlineCallbacks
  def addChange(self, change, message):
    """Parses a job, adds a change and calls self.scheduler.submitJob."""
    try:
      job = self.parseJob(message)
      revision = self.findRevisionShaForMessage(change, message)
      buildbot_change = yield self.addBuildbotChange(change, revision)
      yield self.scheduler.submitJob(buildbot_change, job)
      defer.returnValue(buildbot_change)
    except Exception as e:
      log.err('TryJobGerritPoller failed: %s' % e)
      raise


class TryJobGerritScheduler(BaseScheduler):
  """Polls try jobs on Gerrit and creates buildsets."""
  def __init__(self, name, default_builder_names, gerrit_host,
               gerrit_projects=None, pollInterval=None, dry_run=None):
    """Creates a new TryJobGerritScheduler.

    Args:
        name: name of the scheduler.
        default_builder_names: a list of builder names used in case a job didn't
            specify any.
        gerrit_host: URL to the Gerrit instance
        gerrit_projects: Gerrit projects to filter issues.
        pollInterval: frequency of polling.
    """
    BaseScheduler.__init__(self, name,
                           builderNames=default_builder_names,
                           properties={})
    self.poller = _TryJobGerritPoller(self, gerrit_host, gerrit_projects,
                                      pollInterval, dry_run)

  def setServiceParent(self, parent):
    BaseScheduler.setServiceParent(self, parent)
    self.poller.master = self.master
    self.poller.setServiceParent(self)

  def gotChange(self, *args, **kwargs):
    """Do nothing because changes are processed by submitJob."""

  @defer.inlineCallbacks
  def submitJob(self, change, job):
    props = Properties()
    if change.properties:
      props.updateFromProperties(change.properties)
    if job.build_properties:
      props.update(job.build_properties, 'Gerrit')

    bsid = yield self.addBuildsetForChanges(
        reason='tryjob',
        changeids=[change.number],
        builderNames=job.builder_names,
        properties=props)
    log.msg('Successfully submitted a Gerrit try job for %s: %s.' %
            (change.who, job))
    defer.returnValue(bsid)


class TryJobGerritStatus(StatusReceiverMultiService):
  """Posts results of a try job back to a Gerrit change."""

  def __init__(self, gerrit_host, review_factory=None, cq_builders=None,
               **kwargs):
    """Creates a TryJobGerritStatus.

    Args:
      gerrit_host: a URL of the Gerrit instance.
      review_factory: a function (self, builder_name, build, result) => review,
        where review is a dict described in Gerrit docs:
        https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input
      cq_builders: a list of buildernames, if specified, patchset will be
          submitted if all builders have completed successfully.
      kwargs: keyword arguments passed to GerritAgent.
    """
    StatusReceiverMultiService.__init__(self)
    self.review_factory = review_factory or TryJobGerritStatus.createReview
    self.agent = GerritAgent(gerrit_host, **kwargs)
    self.status = None
    self.cq_builders = cq_builders

  def createReview(self, builder_name, build, result):
    review = {}
    if result is not None:
      message = ('A try job has finished on builder %s: %s' %
                 (builder_name, Results[result].upper()))
    else:
      message = 'A try job has started on builder %s' % builder_name
      # Do not send email about this.
      review['notify'] = 'NONE'

    # Append build url.
    # A line break in a Gerrit message is \n\n.
    assert self.status
    build_url = self.status.getURLForThing(build)
    message = '%s\n\n%s' % (message, build_url)

    review['message'] = message
    return review

  def sendUpdate(self, builder_name, build, result):
    """Posts a message and labels, if any, on a Gerrit change."""
    props = build.properties
    change_id = (props.getProperty('event.change.id') or
                 props.getProperty('parent_event.change.id'))
    revision = props.getProperty('revision')
    if change_id and revision:
      review = self.review_factory(self, builder_name, build, result)
      if review:
        log.msg('Sending a review for change %s: %s' % (change_id, review))
        path = '/changes/%s/revisions/%s/review' % (change_id, revision)
        return self.agent.request('POST', path, body=review)

  @defer.inlineCallbacks
  def _add_verified_label(self, change_id, revision, patchset_id, commit):
    message = 'All tryjobs have passed for patchset %s' % patchset_id
    path = '/changes/%s/revisions/%s/review' % (change_id, revision)
    body = {'message': message, 'labels': {'Verified': '+1'}}
    yield self.agent.request('POST', path, body=body)
    # commit the change
    if commit:
      path = 'changes/%s/submit' % change_id
      body = {'wait_for_merge': True}
      yield self.agent.request('POST', path, body=body)

  MESSAGE_REGEX_TRYJOB_RESULT = re.compile(
    'A try job has finished on builder (.+): SUCCESS', re.I | re.M)

  def submitPatchSetIfNecessary(self, builder_name, build, result):
    """This is a temporary hack until Gerrit CQ is deployed."""
    if not self.cq_builders:
      return
    if not (result == results.SUCCESS or results == results.WARNINGS):
      return
    props = build.properties
    change_id = (props.getProperty('event.change.id') or
                 props.getProperty('parent_event.change.id'))
    revision = props.getProperty('revision')
    patchset_id = props.getProperty('event.patchSet.ref').rsplit('/', 1)[1]
    builders = [x for x in self.cq_builders if x != builder_name]
    o_params = '&'.join('o=%s' % x for x in (
        'MESSAGES', 'ALL_REVISIONS', 'ALL_COMMITS', 'ALL_FILES', 'LABELS'))
    path = '/changes/%s?%s' % (change_id, o_params)
    d = self.agent.request('GET', path)
    def _parse_messages(j):
      if not j:
        return
      commit = 'approved' in j.get('labels', {}).get('Commit-Queue', {})
      if len(builders) == 0:
        self._add_verified_label(change_id, revision, patchset_id, commit)
        return
      if 'messages' not in j:
        return
      for m in reversed(j['messages']):
        if m['_revision_number'] == int(patchset_id):
          match = self.MESSAGE_REGEX_TRYJOB_RESULT.search(m['message'])
          if match:
            builder = match.groups()[0]
            if builder in builders:
              builders.remove(builder)
              if len(builders) == 0:
                self._add_verified_label(change_id, revision, patchset_id,
                                         commit)
                break
    d.addCallback(_parse_messages)
    return d

  def startService(self):
    StatusReceiverMultiService.startService(self)
    self.status = self.parent.getStatus()
    self.status.subscribe(self)

  def builderAdded(self, name, builder):
    # Subscribe to this builder.
    return self

  def buildStarted(self, builder_name, build):
    self.sendUpdate(builder_name, build, None)

  def buildFinished(self, builder_name, build, result):
    self.sendUpdate(builder_name, build, result)
    self.submitPatchSetIfNecessary(builder_name, build, result)

