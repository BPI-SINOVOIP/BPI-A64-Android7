# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime
import json
import logging

from twisted.python import log as twistedLog

LOG_PREFIX = '[buildbucket] '

# Buildbot-related constants.
BUILD_PROPERTY = 'build'
BUILDBUCKET_CHANGE_ID_PROPERTY = 'change_id'
CHANGE_CATEGORY = 'buildbucket'
CHANGE_REASON = 'buildbucket'
INFO_PROPERTY = 'buildbucket'  # A Buildbot property for buildbucket info.

# UTC datetime corresponding to zero Unix timestamp.
EPOCH = datetime.datetime.utcfromtimestamp(0)


class Error(Exception):
  """Buildbucket-specific error."""


def log(message, level=None):
  if level is None:
    level = logging.INFO
  twistedLog.msg('%s%s' % (LOG_PREFIX, message), loglevel=level)


def log_on_error(deferred, msg_prefix=None):
  msg_prefix = msg_prefix or ''
  def on_failure(failure):
    msg = msg_prefix
    if msg:
      msg += ': '
    msg += '%s' % failure
    log(msg, level=logging.ERROR)
  deferred.addErrback(on_failure)


# Copied from "utils" appengine component
# https://chromium.googlesource.com/infra/swarming/+/master/appengine/components/components/utils.py
def datetime_to_timestamp(value):
  """Converts UTC datetime to integer timestamp in microseconds since epoch."""
  if not isinstance(value, datetime.datetime):
    raise ValueError(
        'Expecting datetime object, got %s instead' % type(value).__name__)
  if value.tzinfo is not None:
    raise ValueError('Only UTC datetime is supported')
  dt = value - EPOCH
  return dt.microseconds + 1000 * 1000 * (dt.seconds + 24 * 3600 * dt.days)


# Copied from "utils" appengine component
# https://chromium.googlesource.com/infra/swarming/+/master/appengine/components/components/utils.py
def timestamp_to_datetime(value):
  """Converts integer timestamp in microseconds since epoch to UTC datetime."""
  if not isinstance(value, (int, long, float)):
    raise ValueError(
        'Expecting a number, got %s instead' % type(value).__name__)
  return EPOCH + datetime.timedelta(microseconds=value)


def parse_info_property(value):
  """Parses the buildbot build property containing buildbucket INFO."""
  try:
    # Be compatible with existing builds that still store a dict.
    if isinstance(value, dict):
      # Due to a bug introduced in http://crrev.com/1328623003 (reverted), we
      # may have old builds that have 'build' value serialized as JSON.
      if isinstance(value['build'], basestring):
        value['build'] = json.loads(value['build'])
      return value
    parsed = json.loads(value)
    if not isinstance(parsed, dict):
      raise ValueError('must be a JSON dict')
    return parsed
  except ValueError as e:
    # Include value of the buildbucket property into the
    msg = 'failed to parse %s property "%s": %s' % (INFO_PROPERTY, value, e)
    log(msg, logging.ERROR)
    raise ValueError(msg)
