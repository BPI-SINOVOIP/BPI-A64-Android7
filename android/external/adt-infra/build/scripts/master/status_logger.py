# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Log status events to a file on disk or event collection endpoint."""


import json
import logging
import logging.handlers
import os
import time

from logging.handlers import TimedRotatingFileHandler

import buildbot.status.results

from buildbot.status.base import StatusReceiverMultiService
from twisted.python import log as twisted_log

from common import chromium_utils


class StatusEventLogger(StatusReceiverMultiService):
  """Log status events to a file on disk or event collection endpoint.

  Files on disk are rotated, while the event collection endpoint is contacted
  through a script in the infra/infra repository (separate checkout).

  A file, .logstatus, is used to configure the logger. If it exists then
  file logging is enabled. If it parses as json, the keys event_logging,
  file_logging, logging_ignore_basedir, logfile, can be used to configure the
  logger at runtime.
  """

  DEFAULT_LOGGING_IGNORE_BASEDIR = False

  def __init__(self, logfile='status.log', configfile='.logstatus',
               basedir=None, event_logging_dir=None):
    """Create a StatusEventLogger.

    Args:
      logfile: base filename for events to be written to.
      configfile: the name of the configuration file.
      basedir: the basedir of the configuration and log files. Set to the
               service's parent directory by default, mainly overridden for
               testing.
      event_logging_dir: directory where to write events. This object adds the
               master name to the path. Mainly overridden for testing.
    """
    self._logfile = self._original_logfile = logfile
    self._configfile = configfile
    self._basedir = basedir
    self.master_dir = os.path.basename(os.path.abspath(os.curdir))

    self._event_logging_dir = os.path.join(
      event_logging_dir or '/var/log/chrome-infra',
      'status_logger-' + self.master_dir)

    self._event_logfile = os.path.join(self._event_logging_dir, 'events.log')
    self._ts_mon_logfile = os.path.join(self._event_logging_dir, 'ts_mon.log')

    # These are defaults which may be overridden.
    self.logging_ignore_basedir = self.DEFAULT_LOGGING_IGNORE_BASEDIR

    # Will be initialized in startService.
    self.logger = None
    self.event_logger = None
    self.ts_mon_logger = None
    self.status = None
    self._active = False
    self._last_checked_active = 0
    self._logging = False
    self._event_logging = False
    self._ts_mon_logging = False
    # Can't use super because StatusReceiverMultiService is an old-style class.
    StatusReceiverMultiService.__init__(self)

  def as_dict(self):
    return {
        'basedir': self.basedir,
        'configfile': self.configfile,
        'file_logging': self._logging,
        'event_logging': self._event_logging,
        'ts_mon_logging': self._ts_mon_logging,
        'logfile': self.logfile,
        'logging_ignore_basedir': self.logging_ignore_basedir,
    }

  def _configure(self, config_data):
    old_config = self.as_dict()

    self._logging = config_data.get(
        'file_logging', True)  # Preserve old behavior.
    self._event_logging = config_data.get('event_logging',
                                          self._event_logging)
    self._ts_mon_logging = config_data.get('ts_mon_logging',
                                           self._ts_mon_logging)
    self._logfile = config_data.get(
        'logfile', self._original_logfile)
    self.logging_ignore_basedir = config_data.get(
        'logging_ignore_basedir', self.DEFAULT_LOGGING_IGNORE_BASEDIR)

    new_config = self.as_dict()
    if new_config != old_config:
      twisted_log.msg(
          'Configuration change detected. Old:\n%s\n\nNew:\n%s\n' % (
              json.dumps(old_config, sort_keys=True, indent=2),
              json.dumps(new_config, sort_keys=True, indent=2)))

    # Clean up if needed.
    if not old_config['file_logging'] and new_config['file_logging']:
      self._create_logger()

    if not old_config['event_logging'] and new_config['event_logging']:
      self._create_event_logger()

    if not old_config['ts_mon_logging'] and new_config['ts_mon_logging']:
      self._create_ts_mon_logger()

  @staticmethod
  def _get_requested_at_millis(build):
    return int(build.getProperty('requestedAt') * 1000)

  @property
  def basedir(self):
    """Returns dynamic or preset basedir.

    self.parent doesn't exist until the service is running, so this has to be
    here instead of precomputing the logfile and configfile in __init__.
    """
    return self._basedir or self.parent.basedir

  def _canonical_file(self, filename, ignore_basedir=False):
    """Returns an absolute path for a config or log file."""
    if ignore_basedir:
      full_filename = filename
    else:
      full_filename = os.path.join(self.basedir, filename)
    return chromium_utils.AbsoluteCanonicalPath(full_filename)

  @property
  def configfile(self):
    return self._canonical_file(self._configfile)

  @property
  def logfile(self):
    return self._canonical_file(
        self._logfile, ignore_basedir=self.logging_ignore_basedir)

  @property
  def active(self):
    now = time.time()
    # Cache the value for self._active for one minute.
    if now - self._last_checked_active > 60:
      active_before = self._active
      self._active = os.path.isfile(self.configfile)

      if not self._active and active_before:
        twisted_log.msg('Disabling status_logger.')

      if self._active:
        # Test if it parses as json, otherwise use defaults.
        data = {}
        try:
          with open(self.configfile) as f:
            data = json.load(f)
        except ValueError as err:
          twisted_log.msg("status_logger config file parsing failed: %s\n%s"
                          % (self.configfile, err), logLevel=logging.ERROR)
        self._configure(data)

        if not active_before:
          twisted_log.msg(
              'Enabling status_logger. file_logger: %s / event_logging: %s '
              '/ ts_mon_logging: %s' % (
              self._logging, self._event_logging, self._ts_mon_logging))
      else:
        self._configure({'file_logging': False,
                         'event_logging': False,
                         'ts_mon_logging': False})  # Reset to defaults.

      self._last_checked_active = now
    return self._active


  def send_build_result(
      self, started, finished, builder_name, bot_name, result, project_id=None):
    """Log a build result for ts_mon.

    This allows computing metrics for builds in mastermon.
    """
    d = {
        'timestamp_ms': finished * 1000,
        'builder': builder_name,
        'slave': bot_name,
        'result': result.lower(),
        'duration_s': finished - started,
    }
    if project_id:
      d['project_id'] = project_id
    self.ts_mon_logger.info(json.dumps(d))


  def send_build_event(self, timestamp_kind, timestamp, build_event_type,
                       bot_name, builder_name, build_number, build_scheduled_ts,
                       step_name=None, step_number=None, result=None):
    """Log a build/step event for event_mon."""

    if self.active and self._event_logging:
      # List options to pass to send_monitoring_event, without the --, to save
      # a bit of space.
      d = {'event-mon-timestamp-kind': timestamp_kind,
           'event-mon-event-timestamp': timestamp,
           'event-mon-service-name': 'buildbot/master/%s' % self.master_dir,
           'build-event-type': build_event_type,
           'build-event-hostname': bot_name,
           'build-event-build-name': builder_name,
           'build-event-build-number': build_number,
           'build-event-build-scheduling-time': build_scheduled_ts,
         }
      if step_name:
        d['build-event-step-name'] = step_name
        d['build-event-step-number'] = step_number
      if result:
        d['build-event-result'] = result.upper()

      self.event_logger.info(json.dumps(d))

  def _create_logging_dir(self):
    """Make sure the logging directory exists.

    Try to create the directory if it doesn't exist, returns False if it
    fails.

    Returns:
      logs_dir_exists(bool): True is the directory is available

    """
    event_logging_dir_exists = os.path.isdir(self._event_logging_dir)
    if not event_logging_dir_exists:
      try:
        os.mkdir(self._event_logging_dir)
      except OSError:
        twisted_log.msg('Logging directory cannot be created, no events will '
                        'be written:', self._event_logging_dir)
      else:
        event_logging_dir_exists = True

    return event_logging_dir_exists

  def _create_ts_mon_logger(self):
    """Set up a logger for ts_mon events.

    If the destination directory does not exist, ignore data sent to
    ts_mon_logger.
    """

    event_logging_dir_exists = self._create_logging_dir()
    logger = logging.getLogger(__name__ + '_ts_mon')
    # Remove handlers that may already exist. This is useful when changing the
    # log file name.
    for handler in logger.handlers:
      handler.flush()
      logger.handlers = []

    logger.propagate = False
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter('%(message)s')

    if event_logging_dir_exists:
      # Use delay=True so we don't open an empty file while self.active=False.
      # Also use WatchedFileHandler because it'll be rotated by an external
      # process.
      handler = logging.handlers.WatchedFileHandler(self._ts_mon_logfile,
                                                    encoding='utf-8',
                                                    delay=True)
    else:
      handler = logging.NullHandler()
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    self.ts_mon_logger = logger

  def _create_event_logger(self):
    """Set up a logger for monitoring events.

    If the destination directory does not exist, ignore data sent to
    event_logger.
    """
    event_logging_dir_exists = self._create_logging_dir()

    logger = logging.getLogger(__name__ + '_event')
    # Remove handlers that may already exist. This is useful when changing the
    # log file name.
    for handler in logger.handlers:
      handler.flush()
      logger.handlers = []

    logger.propagate = False
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter('%(message)s')

    if event_logging_dir_exists:
      # Use delay=True so we don't open an empty file while self.active=False.
      handler = TimedRotatingFileHandler(self._event_logfile, backupCount=120,
                                         when='M', interval=1, delay=True)
    else:
      handler = logging.NullHandler()
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    self.event_logger = logger

  def _create_logger(self):
    logger = logging.getLogger(__name__)
    # Remove handlers that may already exist. This is useful when changing the
    # log file name.
    for handler in logger.handlers:
      handler.flush()
      logger.handlers = []

    logger.propagate = False
    logger.setLevel(logging.INFO)
    # %(bbEvent)19s because builderChangedState is 19 characters long
    formatter = logging.Formatter('%(asctime)s - %(bbEvent)19s - %(message)s')
    # Use delay=True so we don't open an empty file while self.active=False.
    handler = TimedRotatingFileHandler(
        self._canonical_file(self.logfile),
        when='H', interval=1, delay=True)
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    self.logger = logger

  def startService(self):
    """Start the service and subscribe for updates."""
    self._create_logger()
    self._create_event_logger()
    self._create_ts_mon_logger()

    StatusReceiverMultiService.startService(self)
    self.status = self.parent.getStatus()
    self.status.subscribe(self)

  def log(self, event, message, *args):
    """Simple wrapper for log. Passes string formatting args through."""
    if self.active and self._logging:
      self.logger.info(message, *args, extra={'bbEvent': event})

  def requestSubmitted(self, request):
    builderName = request.getBuilderName()
    self.log('requestSubmitted', '%s, %r', builderName, request)

  def requestCancelled(self, builder, request):
    builderName = builder.getName()
    self.log('requestCancelled', '%s, %r', builderName, request)

  def buildsetSubmitted(self, buildset):
    reason = buildset.getReason()
    self.log('buildsetSubmitted', '%r, %s', buildset, reason)

  def builderAdded(self, builderName, builder):
    # Use slavenames rather than getSlaves() to just get strings.
    slaves = builder.slavenames
    self.log('builderAdded', '%s, %r', builderName, slaves)
    # Must return self in order to subscribe to builderChangedState and
    # buildStarted/Finished events.
    return self

  def builderChangedState(self, builderName, state):
    self.log('builderChangedState', '%s, %r', builderName, state)

  def buildStarted(self, builderName, build):
    build_number = build.getNumber()
    bot = build.getSlavename()
    self.log('buildStarted', '%s, %d, %s', builderName, build_number, bot)
    started, _ = build.getTimes()
    self.send_build_event(
        'BEGIN', started * 1000, 'BUILD', bot, builderName, build_number,
        self._get_requested_at_millis(build))
    # Must return self in order to subscribe to stepStarted/Finished events.
    return self

  def buildETAUpdate(self, build, ETA):
    # We don't actually care about ETA updates; they happen on a periodic clock.
    pass

  def changeAdded(self, change):
    self.log('changeAdded', '%r', change)

  def stepStarted(self, build, step):
    bot = build.getSlavename()
    builder_name = build.getBuilder().name
    build_number = build.getNumber()
    step_name = step.getName()
    self.log('stepStarted', '%s, %d, %s', builder_name, build_number, step_name)
    started, _ = step.getTimes()
    self.send_build_event(
        'BEGIN', started * 1000, 'STEP', bot, builder_name, build_number,
        self._get_requested_at_millis(build),
        step_name=step_name, step_number=step.step_number)
    # Must return self in order to subscribe to logStarted/Finished events.
    return self

  def stepTextChanged(self, build, step, text):
    build_name = build.getBuilder().name
    build_number = build.getNumber()
    step_name = step.getName()
    self.log('stepTextChanged', '%s, %d, %s, %s',
             build_name, build_number, step_name, text)

  def stepText2Changed(self, build, step, text2):
    build_name = build.getBuilder().name
    build_number = build.getNumber()
    step_name = step.getName()
    self.log('stepText2Changed', '%s, %d, %s, %s',
             build_name, build_number, step_name, text2)

  def stepETAUpdate(self, build, step, ETA, expectations):
    # We don't actually care about ETA updates; they happen on a periodic clock.
    pass

  def logStarted(self, build, step, log):
    build_name = build.getBuilder().name
    build_number = build.getNumber()
    step_name = step.getName()
    log_name = log.getName()
    log_file = log.filename
    self.log('logStarted', '%s, %d, %s, %s, %s',
             build_name, build_number, step_name, log_name, log_file)
    # Create an attr on the stateful log object to count its chunks.
    # pylint: disable=protected-access
    log.__num_chunks = 0
    # pylint: enable=protected-access
    # Must return self in order to subscribe to logChunk events.
    return self

  def logChunk(self, _build, _step, log, _channel, _text):
    # Like the NSA, we only want to process metadata.
    log.__num_chunks += 1

  def logFinished(self, build, step, log):
    build_name = build.getBuilder().name
    build_number = build.getNumber()
    step_name = step.getName()
    log_name = log.getName()
    log_file = log.filename
    # Access to protected member __num_chunks. pylint: disable=W0212
    log_chunks = log.__num_chunks
    self.log('logFinished', '%s, %d, %s, %s, %s, %d',
             build_name, build_number, step_name,
             log_name, log_file, log_chunks)

  def stepFinished(self, build, step, results):
    builder_name = build.getBuilder().name
    build_number = build.getNumber()
    bot = build.getSlavename()
    step_name = step.getName()
    self.log('stepFinished', '%s, %d, %s, %r',
             builder_name, build_number, step_name, results)
    _, finished = step.getTimes()
    self.send_build_event(
        'END', finished * 1000, 'STEP', bot, builder_name, build_number,
        self._get_requested_at_millis(build),
        step_name=step_name, step_number=step.step_number,
        result=buildbot.status.results.Results[results[0]])

  def buildFinished(self, builderName, build, results):
    build_number = build.getNumber()
    bot = build.getSlavename()
    self.log('buildFinished', '%s, %d, %s, %r',
             builderName, build_number, bot, results)
    started, finished = build.getTimes()
    # If property doesn't exist, this function returns None.
    # Note: this is not true for build.GetProperty(), it raises KeyError.
    project_id = build.getProperties().getProperty('patch_project')
    self.send_build_event(
        'END', finished * 1000, 'BUILD', bot, builderName, build_number,
        self._get_requested_at_millis(build),
        result=buildbot.status.results.Results[results])
    self.send_build_result(
        started, finished, builderName, bot,
        buildbot.status.results.Results[results],
        project_id)

  def builderRemoved(self, builderName):
    self.log('builderRemoved', '%s', builderName)

  def slaveConnected(self, slaveName):
    self.log('slaveConnected', '%s', slaveName)

  def slaveDisconnected(self, slaveName):
    self.log('slaveDisconnected', '%s', slaveName)
