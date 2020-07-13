#!/usr/bin/env python2.7
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Simple module to implement a build-local cache."""

import logging
import os

BUILD_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__),
                                          os.pardir, os.pardir))


class ConfigCacheError(RuntimeError):
  """Parent error type for configcache-related failures."""

class FetcherError(ConfigCacheError):
  """Exception raised if a write operation is performed on a read-only cache."""

class ReadOnlyError(ConfigCacheError):
  """Exception raised if a write operation is performed on a read-only cache."""


class CacheManager(object):
  """Class that manages the pinned configuration cache."""

  # The default cache path.
  CACHE_PATH = os.path.join(BUILD_PATH, '.config_cache')

  def __init__(self, cache_name, cache_dir=None, fetcher=None,
               base_logger=None):
    """
    The CacheManager uses a configured Fetcher callable to actually perform
    cache fetches. The Fetcher is a callable with the following form:
      Fetcher Args:
        name: (str) The name of the item to fetch.
        version: (str) The version of the item to fetch. If None, select a
            default version.

      Fetcher Returns: (data, version)
        data: (str) The data content of the fetched item.
        version: (str) The version that was fetched.

      Fetcher Raises:
        FetcherError: If there was an error fetching the requested artifact.

    If no Fetcher is specified, the cache is read-only. It can still retrieve
    existing cached values, but will refuse to cache new values.

    Args:
      cache_name: (str) The cache sub-name.
      fetcher: (callable) A Fetcher callable, or None to initialize the cache
          as read-only.
      base_logger: (logging.Logger) If not None, the logger to use as the
          parent for this cache's internal logger.
    """
    assert fetcher is None or callable(fetcher)
    self.logger = (base_logger or logging.getLogger()).getChild(
        "Cache[%s]" % (cache_name,))
    self.cache_dir = cache_dir or self.CACHE_PATH
    self.fetcher = fetcher
    self.cache_name = cache_name

  def _ArtifactPath(self, name):
    return os.path.join(self.cache_dir, self.cache_name, name)

  def _ArtifactVersionPath(self, name):
    return '%s.version' % (self._ArtifactPath(name),)

  def _ArtifactDataPath(self, name):
    return '%s.data' % (self._ArtifactPath(name),)

  @property
  def read_only(self):
    """Returns: (bool) whether or not the cache is read-only.

    A cache is read-only if it has no configured fetcher.
    """
    return not callable(self.fetcher)

  @staticmethod
  def _WriteFile(path, data):
    with open(path, 'w') as fd:
      fd.write(data)

  @staticmethod
  def _ReadFile(path):
    with open(path) as fd:
      return fd.read()

  def FetchAndCache(self, name, version=None):
    """Forces a cache artifact to be fetched and cached.

    This will operate even if the artifact is currently present in the cache.

    Returns: (data, version)
      data: (str) The fetched cache artifact.
      version: (str) The fetched artifact version.

    Raises:
      ReadOnlyError: If this cache is read-only.
      ValueError: If the fetcher doesn't return the requested version.
    """
    if self.read_only:
      raise ReadOnlyError("Cache is read-only.")

    # Get artifact paths. Create directory if needed.
    artifact_dir = os.path.dirname(self._ArtifactPath(name))
    if not os.path.isdir(artifact_dir):
      os.makedirs(artifact_dir)

    # Get the artifact.
    data, fetched_version = self.fetcher(name, version)
    if version and version != fetched_version:
      raise ValueError("Fetched artifact version (%s) differs from "
                       "requested (%s)" % (fetched_version, version))
    version = fetched_version
    logging.debug("Fetched artifact '%s' version '%s' (%d bytes)",
                  name, version, len(data))

    # Create the cached artifact.
    data_path = self._ArtifactDataPath(name)
    version_path = self._ArtifactVersionPath(name)

    self._WriteFile(data_path, data)
    self._WriteFile(version_path, version)
    return data, version

  def GetArtifactVersion(self, name):
    """Returns: (str) The artifact version, or None if it doesn't exist.
    """
    try:
      return self._ReadFile(self._ArtifactVersionPath(name))
    except IOError:
      # Artifact version information doesn't exist.
      return None

  def Get(self, name, version=None):
    """Retrieves a currently-cached data result.

    Args:
      name: (str) The artifact name.
      version: (str) The requested artifact version, or None to use the current
          version, if it exists.

    Returns: (str) The artifact data, or None if it does not exist.
    """
    if version:
      current_version = self.GetArtifactVersion(name)
      if current_version != version:
        # No current artifact with the requested version.
        return None

    try:
      return self._ReadFile(self._ArtifactDataPath(name))
    except IOError:
      # Path does not exist / can't be opened.
      pass
    return None
