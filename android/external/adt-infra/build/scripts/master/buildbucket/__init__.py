# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""buildbucket module implements buildbucket-buildbot integration.

The main entry point is buildbucket.setup() that accepts master configuration
dict with other buildbucket parameters and configures master to run builds
scheduled on buildbucket service.

Example:
  buildbucket.setup(
      c,  # Configuration object.
      ActiveMaster,
      buckets=['qo'],
  )

"""

import functools
import os
import sys

from .common import Error
from .integration import BuildBucketIntegrator, MAX_MAX_BUILDS
from .poller import BuildBucketPoller
from .status import BuildBucketStatus
from . import client
from . import trigger


NO_LEASE_LIMIT = sys.maxint


def setup(
    config, active_master, buckets, build_params_hook=None,
    poll_interval=10, buildbucket_hostname=None, max_lease_count=None,
    verbose=None, dry_run=None):
  """Configures a master to lease, schedule and update builds on buildbucket.

  Requires config to have 'mergeRequests' set to False.

  Args:
    config (dict): master configuration dict.
    active_master (config.Master.Base): master site config.
    buckets (list of str): a list of buckets to poll.
    build_params_hook: callable with arguments (params, build) that can modify
      parameters (and properties via parameters['properties']) before creating
      a buildset during validation.
        params: dict name->value
        build: dict describing a buildbucket build.

      If a ValueError is raised, the build will be marked as an
      INVALID_BUILD_DEFINITION failure and the error message will be propagated
      to the BuildBucket status.
    poll_interval (int): frequency of polling, in seconds. Defaults to 10.
    buildbucket_hostname (str): if not None, override the default buildbucket
      service url.
    max_lease_count (int): maximum number of builds that can be leased at a
      time. Defaults to the number of connected slaves.
    verbose (bool): log more than usual. Defaults to False.
    dry_run (bool): whether to run buildbucket in a dry-run mode.

  Raises:
    buildbucket.Error if config['mergeRequests'] is not False.
  """
  assert isinstance(config, dict), 'config must be a dict'
  assert active_master
  assert active_master.service_account_path, 'Service account is not assigned'
  assert buckets, 'Buckets are not specified'
  assert isinstance(buckets, list), 'Buckets must be a list'
  assert all(isinstance(b, basestring) for b in buckets), (
        'all buckets must be strings')

  if dry_run is None:
    dry_run = 'POLLER_DRY_RUN' in os.environ

  integrator = BuildBucketIntegrator(
      buckets, build_params_hook=build_params_hook,
      max_lease_count=max_lease_count)

  buildbucket_service_factory = functools.partial(
      client.create_buildbucket_service, active_master, buildbucket_hostname,
      verbose)

  poller = BuildBucketPoller(
      integrator=integrator,
      poll_interval=poll_interval,
      dry_run=dry_run)
  status = BuildBucketStatus(
      integrator,
      buildbucket_service_factory=buildbucket_service_factory,
      dry_run=dry_run)
  config.setdefault('change_source', []).append(poller)
  config.setdefault('status', []).append(status)
