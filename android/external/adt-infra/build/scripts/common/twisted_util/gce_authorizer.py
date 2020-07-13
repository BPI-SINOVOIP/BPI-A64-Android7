# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""GCEAuthorizer class"""

import json
import time
import urllib2
import urlparse

from common.twisted_util.agent import Agent
from common.twisted_util.authorizer import IAuthorizer
from common.twisted_util.response import JsonResponse
from twisted.python import log
from twisted.internet import task
from twisted.internet import reactor
from twisted.web.http_headers import Headers
from zope.interface import implements


class GCEAuthorizer(object):
  """An Authorizer implementation for GCE hosts."""
  implements(IAuthorizer)

  ACQUIRE_URL = ('http://metadata/computeMetadata/v1/instance/service-accounts/'
                 'default/token')
  ACQUIRE_HEADERS = {"Metadata-Flavor": "Google"}
  ACQUIRE_RETRIES = 5

  def __init__(self, token_dict):
    assert self.is_gce_host(), "GCEAuthorizer should only be used on GCE hosts"
    self._update_from_token_dict(token_dict)
    self._schedule_next_update()

  def _schedule_next_update(self):
    # Update 25 seconds before token expires, but no more than every 5 seconds
    now = time.time()
    expires = max(self._expires_at - 25, now + 5)
    task.deferLater(reactor, expires - now, self._update)

  @classmethod
  def get_token_dict(cls):
    for _try in xrange(cls.ACQUIRE_RETRIES):
      request = urllib2.Request(cls.ACQUIRE_URL, headers=cls.ACQUIRE_HEADERS)
      try:
        token_json = urllib2.urlopen(request)
      except urllib2.URLError:
        time.sleep(2 ** _try)
        continue
      try:
        token_dict = json.load(token_json)
      except ValueError:
        time.sleep(2 ** _try)
        continue
      try:
        return token_dict
      except KeyError:
        raise Exception('Missing required keys in response from token server '
                        'when acquiring Git Token.')
      return
    raise Exception('Failed to acquire Git Token after 5 tries.')

  @classmethod
  def _get_token_dict_async(cls):
    protocol, host, path, _, _ = urlparse.urlsplit(cls.ACQUIRE_URL)
    headers = Headers()
    for key, value in cls.ACQUIRE_HEADERS.iteritems():
      headers.addRawHeader(key, value)
    agent = Agent('%s://%s' % (protocol, host))
    d = agent.GET(path, headers, retry=cls.ACQUIRE_RETRIES)
    d.addCallback(JsonResponse.Get)
    return d

  def _update_from_token_dict(self, token_dict):
    self._access_token = token_dict['access_token']
    self._token_type = token_dict['token_type']
    self._expires_at = token_dict['expires_in'] + time.time()

  def _update(self):
    d = self._get_token_dict_async()
    def update(token_dict):
      self._update_from_token_dict(token_dict)
    d.addCallback(update)
    def schedule_next(_ignored):
      self._schedule_next_update()
    d.addBoth(schedule_next)

  _is_gce_host_cache = None

  @classmethod
  def is_gce_host(cls):
    if cls._is_gce_host_cache is None:
      for _try in xrange(5):
        # Based on https://cloud.google.com/compute/docs/metadata#runninggce
        try:
          headers = urllib2.urlopen('http://metadata.google.internal').info()
        except urllib2.URLError:
          continue
        cls._is_gce_host_cache = headers.get('Metadata-Flavor') == 'Google'
        break
      if cls._is_gce_host_cache is None:
        log.msg('Failed to get GCE metadata after 5 retries. This is probably '
                'not a GCE instance.')
        cls._is_gce_host_cache = False
    return cls._is_gce_host_cache

  def addAuthHeadersForURL(self, headers, url):
    auth_token = '%s %s' % (self._token_type, self._access_token)
    headers.setRawHeaders('Authorization', [auth_token])
    return True
