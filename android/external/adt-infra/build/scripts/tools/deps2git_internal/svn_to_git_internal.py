#!/usr/bin/python
# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import re


GIT_HOST = 'https://chrome-internal.googlesource.com/'


def SvnUrlToGitUrl(path, svn_url):
  """Convert a src-internal SVN URL to a gerrit-int Git URL."""

  match = re.match('svn://svn.chromium.org/chrome-internal(/.*)', svn_url)
  if match:
    svn_url = match.group(1)

  # Everything under /trunk/chrome goes to /chrome/*
  match = re.match('/trunk/chrome/(.*)', svn_url)
  if match:
    repo = '%s.git' % match.group(1)
    return (path, '%schrome/%s' % (GIT_HOST, repo), GIT_HOST)

  # Everything under /trunk/third_party goes to /chrome/deps/*
  match = re.match('/trunk/third_party/(.*)', svn_url)
  if match:
    repo = '%s.git' % match.group(1)
    return (path, '%schrome/deps/%s' % (GIT_HOST, repo), GIT_HOST)

  # Everything else under /trunk/ goes to /chrome/*
  match = re.match('/trunk/(.*)', svn_url)
  if match:
    repo = '%s.git' % match.group(1)
    return (path, '%schrome/%s' % (GIT_HOST, repo), GIT_HOST)
