# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This file contains buildbucket service client."""

import json
import logging
import sys

from master import auth
from master.buildbucket import common
from master.deferred_resource import DeferredResource

from oauth2client.client import SignedJwtAssertionCredentials
import httplib2
import apiclient


BUILDBUCKET_HOSTNAME_PRODUCTION = 'cr-buildbucket.appspot.com'
BUILDBUCKET_HOSTNAME_TESTING = 'cr-buildbucket-test.appspot.com'


def buildbucket_api_discovery_url(hostname=None):
  return (
      'https://%s/_ah/api/discovery/v1/apis/{api}/{apiVersion}/rest' % hostname)


def get_default_buildbucket_hostname(master):
  return (
      BUILDBUCKET_HOSTNAME_PRODUCTION if master.is_production_host
      else BUILDBUCKET_HOSTNAME_TESTING)


def create_buildbucket_service(
    master, hostname=None, verbose=None):
  """Asynchronously creates buildbucket API resource.

  Returns:
    A DeferredResource as Deferred.
  """
  hostname = hostname or get_default_buildbucket_hostname(master)
  return DeferredResource.build(
      'buildbucket',
      'v1',
      credentials=auth.create_credentials_for_master(master),
      max_concurrent_requests=10,
      discoveryServiceUrl=buildbucket_api_discovery_url(hostname),
      verbose=verbose or False,
      log_prefix=common.LOG_PREFIX,
  )
