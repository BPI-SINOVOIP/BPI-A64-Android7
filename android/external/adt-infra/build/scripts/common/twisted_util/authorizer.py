# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""IAuthorizer interface"""

from zope.interface import Interface


# Disable missing '__init__' method | pylint: disable=W0232
# Disable zope.interface.Interface error | pylint:disable=inherit-non-class
class IAuthorizer(Interface):
  """Interface to augment an HTTP request with implementation-specific
  authorization data.
  """

  def addAuthHeadersForURL(self, headers, url):
    """
    Augments a set of HTTP headers with this authorizer's authorization data
    for the requested URL. If no authorization is needed for the URL, no
    headers will be added.

    Arguments:
      headers: (Headers) the headers to augment
      url: (str) the URL to authorize

    Returns: (bool) True if authorization was added, False if not.
    """