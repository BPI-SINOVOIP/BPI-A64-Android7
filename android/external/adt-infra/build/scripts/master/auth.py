# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This module provides means to authenticate buildbot master to GAE apps.

A master may have an assigned service account specified in the
"service_account_file" attribute of a master definition in
master_site_config.py. It is a name of a JSON key file created and deployed by
administrators. One service account can be used to authenticate to different GAE
apps.

Cloud Endpoints and other Google APIs
-------------------------------------

This sample creates a Cloud Endpoints service client for Twisted code,
authorized with master's service account:

    from master import auth
    from master import deferred_resource

    MY_SERVICE_HOSTNAME = 'my_service.appspot.com'
    MY_SERVICE_DISCOVERY_URL = (
        '%s/_ah/api/discovery/v1/apis/{api}/{apiVersion}/rest' %
        MY_SERVICE_HOSTNAME
    )

    @defer.inlineCallbacks
    def my_call(active_master)
      creds = auth.create_credentials_for_master(active_master)
      my_service = yield deferred_resource.DeferredResource.build(
          'my_service',
          'v1',
          credentials=creds,
          discoveryServiceUrl=MY_SERVICE_DISCOVERY_URL)

      res = yield my_service.api.greet('John', body={'message': 'hi'})


Other Google APIs can be called in the same way, but you don't have to specify
discoveryServiceUrl.
"""

import json
import logging

from twisted.python import log
import apiclient
import httplib2
import oauth2client


DEFAULT_SCOPES = ['email']


class Error(Exception):
  """Authorization-related error."""


def validate_json_key(key):
  """Validates a parsed JSON key. Raises Error if it is invalid.

  Example of a well-formatted key (this is not a real key):
    {
      "private_key_id": "4168d274cdc7a1eaef1c59f5b34bdf255",
      "private_key": ("-----BEGIN PRIVATE KEY-----\nMIIhkiG9w0BAQEFAASCAmEwsd" +
                      "sdfsfFd\ngfxFChctlOdTNm2Wrr919Nx9q+sPV5ibyaQt5Dgn89fKV" +
                      "jftrO3AMDS3sMjaE4Ib\nZwJgy90wwBbMT7/YOzCgf5PZfivUe8KkB" +
                      -----END PRIVATE KEY-----\n",
      "client_email": "234243-rjstu8hi95iglc8at3@developer.gserviceaccount.com",
      "client_id": "234243-rjstu8hi95iglc8at3.apps.googleusercontent.com",
      "type": "service_account"
    }
  """
  if not isinstance(key, dict):
    raise Error('key is not a dict')
  if key.get('type') != 'service_account':
    raise Error('Unexpected key type: %s' % key.get('type'))
  if not key.get('client_email'):
    raise Error('Client email not specified')
  if not key.get('private_key'):
    raise Error('Private key not specified')


def create_service_account_credentials(json_key_filename, scope=None):
  """Creates SignedJwtAssertionCredentials with values in json_key_filename.

  Args:
    json_key_filename (str): path to the service account key file.
      See validate_json_key() docstring for the file format.
    scope (str|list of str): scope(s) of the credentials being
      requested. Defaults to https://www.googleapis.com/auth/userinfo.email.
  """
  scope = scope or DEFAULT_SCOPES
  if isinstance(scope, basestring):
    scope = [scope]
  assert all(isinstance(s, basestring) for s in scope)

  try:
    with open(json_key_filename, 'r') as f:
      key = json.load(f)
    validate_json_key(key)
    return oauth2client.client.SignedJwtAssertionCredentials(
        key['client_email'], key['private_key'], scope)
  except Exception as ex:
    msg = ('Invalid service account json key in %s: %s' %
           (json_key_filename, ex))
    log.msg(msg, loglevel=logging.ERROR)
    raise Error(msg)


def create_credentials_for_master(master, scope=None):
  """Creates service account credentials for the master.

  Args:
    master (config.Master): master configuration (what is normally
        called ActiveMaster in master.cfg) with service_account_path set.
    scope (str|list of str): scope(s) of the credentials being
      requested. Defaults to https://www.googleapis.com/auth/userinfo.email.

  Returns:
    oauth2client.client.SignedJwtAssertionCredentials.
  """
  if master.service_account_path:
    return create_service_account_credentials(
        master.service_account_path, scope)

  if master.is_production_host:
    # If we're a live master and there is no configured service account,
    # that is an error.
    raise Error(
        'Production instances must have a service account configured. '
        'Set service_account_path or service_account_file in the master site '
        'config.')
  return None
