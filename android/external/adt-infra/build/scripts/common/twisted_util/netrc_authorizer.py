# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""NETRCAuthorizer class"""

import base64
import netrc
import urlparse

from common.twisted_util.authorizer import IAuthorizer
from zope.interface import implements


class EmptyNetrc(object):
  def authenticators(self, _):
    return None

  def __repr__(self):
    return ''

  @property
  def hosts(self):
    return {}

  @property
  def macros(self):
    return {}


class NETRCAuthorizer(object):
  """An Authorizer implementation that loads its authorization from a '.netrc'
  file.
  """
  implements(IAuthorizer)

  def __init__(self, netrc_path=None):
    """Initializes a new NetRC Authorizer

    Args:
      netrc_path: (str) If not None, use this as the 'netrc' file path;
          otherwise, use '~/.netrc'.
    """
    try:
      self._netrc = netrc.netrc(netrc_path)
    except IOError:
      self._netrc = EmptyNetrc()

  def addAuthHeadersForURL(self, headers, url):
    parsed_url = urlparse.urlparse(url)
    auth_entry = self._netrc.authenticators(parsed_url.hostname)
    if auth_entry is not None:
      auth_token = 'Basic %s' % \
        base64.b64encode('%s:%s' % (auth_entry[0], auth_entry[2]))
      headers.setRawHeaders('Authorization', [auth_token])
      return True
    return False
