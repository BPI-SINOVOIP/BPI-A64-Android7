# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import base64
import json
import os
import re
import shutil
import zlib

from StringIO import StringIO

try:
  # Create a block to work around evil sys.modules manipulation in
  # email/__init__.py that triggers pylint false positives.
  # pylint: disable=E0611,F0401
  from email.Message import Message
  from email.Utils import formatdate
except ImportError:
  raise

from buildbot.process.properties import Properties
from buildbot.schedulers.trysched import TryBase

from twisted.internet import defer, reactor, utils
from twisted.mail.smtp import SMTPSenderFactory
from twisted.python import log

from common.twisted_util.response import StringResponse
from master import gitiles_poller
from master.try_job_base import BadJobfile


class CbuildbotConfigs(object):

  # Valid 'etc' builder targets. Specifically, this ensures:
  # - The build name doesn't begin with a flag ('--')
  # - The build name doesn't contain spaces (to spill into extra args).
  _ETC_TARGET_RE = re.compile(r'^[a-zA-Z][\w-]+\w$')

  def __init__(self, configs, etc_builder=None):
    """Holds base state of the master's try job related configuration.

    configs (dict): A dictionary of all known CrOS configs. This will be as
        up-to-date as the Chromite pin.
    etc_builder (str): If not None, the name of the etc builder.
    """
    self.configs = configs
    self.etc_builder = etc_builder

  def AddBuildBucketHooks(self, c):
    """Build mutation hook called via BuildBucket when scheduling builds.

    The cbuildbot config is specified in the `cbb_config` property. The
    callback transforms that property to an actual waterfall builder name by
    mapping it based on its config.

    If an 'etc' builder is configured and the config name is unknown, it will be
    mapped to the 'etc' builder if possible.

    A tryserver BuildBucket build takes the form:
    - Empty `builder_name` parameter. If one is supplied, it will be ignored.
    - BuildBot changes can be added by including one or more BuildBucket
      `changes` parameters: [{'author': {'email': 'author@google.com'}}].
    - `cbb_config` property must be set to the build's cbuildbot config target.
    - `extra_args` property (optional) may be a JSON list of additional
      parameters to pass to the tryjob.
    - `slaves_request` property (optional) may be a JSON list of slaves on which
      this build may run.
    - Additional BuildBot properties may be added.

    NOTE: Internally, all of these parameters are converted to BuildBot
    properties and referenced as such in other areas of code. The Git poller
    also constructs the same property set, so code paths converge.
    """
    def params_hook(params, _build):
      # Map `cbb_config` to a builder name.
      properties = params.get('properties', {})
      config_name = properties.get('cbb_config')
      if not config_name:
        raise ValueError('Missing required `cbb_config` property.')
      params['builder_name'] = self.GetBuilderForConfig(config_name)

      # Validate other fields.
      if not isinstance(properties.get('extra_args', []), list):
        raise ValueError('`extra_args` property is not a list.')
      if not isinstance(properties.get('slaves_request', []), list):
        raise ValueError('`slaves_request` is not a list.')

      # Add mandatory properties to build.
      params['properties'] = properties
    c['buildbucket_params_hook'] = params_hook

  def GetBuilderForConfig(self, config_name):
    config = self.configs.get(config_name)
    if config:
      return config['_template'] or config_name
    self.ValidateEtcBuild(config_name)
    return self.etc_builder

  def ValidateEtcBuild(self, config_name):
    """Tests whether a specified build config_name is candidate for etc build.

    Raises a ValueError if an etc build cannot be dispatched.
    """
    if not self.etc_builder:
      raise ValueError('etc builder is not configured.')
    if not config_name:
      raise ValueError('Empty config name')
    if not self._ETC_TARGET_RE.match(config_name):
      raise ValueError('invalid etc config name (%s).' % (config_name,))


def translate_v1_to_v2(parsed_job):
  """Translate tryjob desc from V1 to V2."""
  parsed_job.setdefault('extra_args', []).append('--remote-trybot')
  parsed_job['version'] = 2


def translate_v2_to_v3(parsed_job):
  """Translate tryjob desc from V2 to V3."""
  # V3 --remote-patches format is not backwards compatible.
  if any(a.startswith('--remote-patches')
         for a in parsed_job.get('extra_args', ())):
    raise BadJobfile('Cannot translate --remote-patches from tryjob v.2 to '
                     'v.3.  Please run repo sync.')

  parsed_job['version'] = 3


class CrOSTryJobGit(TryBase):
  """Poll a Git server to grab patches to try."""

  # Name of property source for generated properties.
  _PROPERTY_SOURCE = 'Try Job'

  # The version of tryjob that the master is expecting.
  _TRYJOB_FORMAT_VERSION = 3

  # Functions that translate from one tryjob version to another.
  _TRANSLATION_FUNCS = {
      1 : translate_v1_to_v2,
      2 : translate_v2_to_v3,
  }

  # Template path URL component to retrieve the Base64 contents of a file from
  # Gitiles.
  _GITILES_PATH_TMPL = '%(repo)s/+/%(revision)s/%(path)s?format=text'

  @classmethod
  def updateJobDesc(cls, parsed_job):
    """Ensure job description is in the format we expect."""
    while parsed_job['version'] < cls._TRYJOB_FORMAT_VERSION:
      prev_ver = parsed_job['version']
      translation_func = cls._TRANSLATION_FUNCS[parsed_job['version']]
      translation_func(parsed_job)
      if parsed_job['version'] <= prev_ver:
        raise AssertionError('translation function %s not incrementing version!'
                             % str(translation_func))

  def __init__(self, name, pollers, smtp_host, from_addr, reply_to,
               email_footer, cbuildbot_configs, properties=None):
    """Initialize the class.

    Arguments:
      name: See TryBase.__init__().
      pollers: A list of job repo git pit pollers.
      smtp_host: The smtp host for sending out error emails.
      from_addr: The email address to display as being sent from.
      reply_to: The email address to put in the 'Reply-To' email header field.
      email_footer: The footer to append to any emails sent out.
      cbuildbot_configs: (CbuildbotConfigs) A configuration set instance. Any
          'bot' request outside of this list will go to an 'etc' builder, if
          available.
      properties: See TryBase.__init__()
    """
    TryBase.__init__(self, name, [], properties or {})
    self.pollers = pollers
    self.smtp_host = smtp_host
    self.from_addr = from_addr
    self.reply_to = reply_to
    self.email_footer = email_footer
    self.cbb = cbuildbot_configs

  def startService(self):
    TryBase.startService(self)
    self.startConsumingChanges()

  @staticmethod
  def load_job(data):
    try:
      return json.loads(data)
    except ValueError as e:
      raise BadJobfile("Failed to parse job JSON: %s" % (e.message,))

  def validate_job(self, parsed_job):
    # A list of field description tuples of the format:
    # (name, type, required).
    fields = [('name', basestring, True),
              ('user', basestring, True),
              ('email', list, True),
              ('bot', list, True),
              ('extra_args', list, False),
              ('version', int, True),
              ('slaves_request', list, False),
    ]

    error_msgs = []
    for name, f_type, required in fields:
      val = parsed_job.get(name)
      if val is None:
        if required:
          error_msgs.append('Option %s missing!' % name)
      elif not isinstance(val, f_type):
        error_msgs.append('Option %s of wrong type!' % name)

    # If we're an 'etc' job, we must have bots defined to execute.
    for bot in parsed_job['bot']:
      if bot in self.cbb.configs:
        continue
      if self.etc_builder:
        # Assert that this is a valid 'etc' build.
        try:
          self.cbb.ValidateEtcBuild(bot)
        except ValueError as e:
          error_msgs.append("Invalid 'etc' build (%s): %s" % (bot, e.message))
      else:
        error_msgs.append("Unknown bot config '%s' with no 'etc' builder" % (
            bot,))

    if error_msgs:
      raise BadJobfile('\n'.join(error_msgs))

  def get_props(self, config, options):
    """Overriding base class method."""
    props = Properties()

    props.setProperty('slaves_request', options.get('slaves_request', []),
                      self._PROPERTY_SOURCE)
    props.setProperty('cbb_config', config, self._PROPERTY_SOURCE)

    extra_args = options.get('extra_args')
    if extra_args:
      # This field can be quite large, and exceed BuildBot property limits.
      # Compress it, Base64 encode it, and prefix it with "z:" so the consumer
      # knows its size.
      extra_args = 'z:' + base64.b64encode(zlib.compress(json.dumps(
        extra_args)))
      props.setProperty('cbb_extra_args', extra_args,
                        self._PROPERTY_SOURCE)
    return props

  def create_buildset(self, ssid, parsed_job):
    """Overriding base class method."""
    dlist = []
    buildset_name = '%s:%s' % (parsed_job['user'], parsed_job['name'])
    for bot in parsed_job['bot']:
      builder_name = self.cbuildbot_conifgs.GetBuilderForConfig(bot)
      log.msg("Creating '%s' try job(s) %s for %s" % (builder_name, ssid, bot))
      dlist.append(self.addBuildsetForSourceStamp(ssid=ssid,
              reason=buildset_name,
              external_idstring=buildset_name,
              builderNames=[builder_name],
              properties=self.get_props(bot, parsed_job)))
    return defer.DeferredList(dlist)

  def send_validation_fail_email(self, name, emails, error):
    """Notify the user via email about the tryjob error."""
    html_content = []
    html_content.append('<html><body>')
    body = """
Your tryjob with name '%(name)s' failed the validation step.  This is most
likely because <br>you are running an older version of cbuildbot.  Please run
<br><code>repo sync chromiumos/chromite</code> and try again.  If you still
see<br>this message please contact chromeos-build@google.com.<br>
"""
    html_content.append(body % {'name': name})
    html_content.append("Extra error information:")
    html_content.append(error.replace('\n', '<br>\n'))
    html_content.append(self.email_footer)
    m = Message()
    m.set_payload('<br><br>'.join(html_content), 'utf8')
    m.set_type("text/html")
    m['Date'] = formatdate(localtime=True)
    m['Subject'] = 'Tryjob failed validation'
    m['From'] = self.from_addr
    m['Reply-To'] = self.reply_to
    result = defer.Deferred()
    sender_factory = SMTPSenderFactory(self.from_addr, emails,
                                       StringIO(m.as_string()), result)
    reactor.connectTCP(self.smtp_host, 25, sender_factory)

  @defer.inlineCallbacks
  def gotChange(self, change, important):
    """Process the received data and send the queue buildset."""
    # Find poller that this change came from.
    for poller in self.pollers:
      if not isinstance(poller, gitiles_poller.GitilesPoller):
        continue
      if poller.repo_url == change.repository:
        break
    else:
      raise BadJobfile(
          'Received tryjob from unsupported repository %s' % change.repository)

    # pylint: disable=W0631
    file_contents = yield self.loadGitilesChangeFile(poller, change)

    parsed = {}
    try:
      parsed = self.load_job(file_contents)
      self.validate_job(parsed)
      self.updateJobDesc(parsed)
    except BadJobfile as e:
      self.send_validation_fail_email(parsed.setdefault('name', ''),
                                      parsed['email'], str(e))
      raise
    except Exception as e:
      print 'EXCEPTION:', e
      import traceback
      traceback.print_exc()
      raise

    # The sourcestamp/buildsets created will be merge-able.
    ssid = yield self.master.db.sourcestamps.addSourceStamp(
        branch=change.branch,
        revision=change.revision,
        project=change.project,
        repository=change.repository,
        changeids=[change.number])
    yield self.create_buildset(ssid, parsed)

  @defer.inlineCallbacks
  def loadGitilesChangeFile(self, poller, change):
    if len(change.files) != 1:
      # We only accept changes with 1 diff file.
      raise BadJobfile(
          'Try job with too many files %s' % (','.join(change.files)))

    # Load the contents of the modified file.
    path = self._GITILES_PATH_TMPL % {
        'repo': poller.repo_path,
        'revision': change.revision,
        'path': change.files[0],
    }
    contents_b64 = yield poller.agent.request('GET', path, retry=5,
                                              protocol=StringResponse.Get)
    defer.returnValue(base64.b64decode(contents_b64))
