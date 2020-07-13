# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ast
import os

from buildbot.schedulers.basic import SingleBranchScheduler
from buildbot.schedulers.filter import ChangeFilter
from buildbot.schedulers.timed import Nightly
from buildbot.status.mail import MailNotifier
from buildbot import util

from config_bootstrap import Master

from common import chromium_utils

from master import gitiles_poller
from master import master_utils
from master import repo_poller
from master import slaves_list
from master.factory import annotator_factory
from gs_multi_poller import GSMultiPoller
from emu_gs_scheduler import EmulatorSingleBranchScheduler

def PopulateBuildmasterConfig(BuildmasterConfig, builders_path,
                              active_master_cls):
  """Read builders_path and populate a build master config dict."""
  builders = chromium_utils.ReadBuildersFile(builders_path)
  _Populate(BuildmasterConfig, builders, active_master_cls)


def _Populate(BuildmasterConfig, builders, active_master_cls):
  m_annotator = annotator_factory.AnnotatorFactory(active_master_cls)

  c = BuildmasterConfig
  c['logCompressionLimit'] = False
  c['projectName'] = active_master_cls.project_name
  c['projectURL'] = Master.project_url
  c['buildbotURL'] = active_master_cls.buildbot_url

  # This sets c['db_url'] to the database connect string in found in
  # the .dbconfig in the master directory, if it exists. If this is
  # a production host, it must exist.
  chromium_utils.DatabaseSetup(
      c,
      require_dbconfig=active_master_cls.is_production_host)

  c['builders'] = _ComputeBuilders(builders, m_annotator)

  c['schedulers'] = _ComputeSchedulers(builders)

  c['change_source'], tag_comparator = _ComputeChangeSourceAndTagComparator(
      builders)

  # The 'slaves' list defines the set of allowable buildslaves. List all the
  # slaves registered to a builder. Remove dupes.
  c['slaves'] = master_utils.AutoSetupSlaves(
      c['builders'],
      Master.GetBotPassword(),
      missing_recipients=['buildbot@chromium-build-health.appspotmail.com'])

  # This does some sanity checks on the configuration.
  slaves = slaves_list.BaseSlavesList(
      chromium_utils.GetSlavesFromBuilders(builders),
      builders['master_classname'])
  master_utils.VerifySetup(c, slaves)

  # Adds common status and tools to this master.
  # TODO: Look at the logic in this routine to see if any of the logic
  # in this routine can be moved there to simplify things.
  master_utils.AutoSetupMaster(c, active_master_cls,
      public_html=os.path.join(chromium_utils.BUILD_DIR,
                               'masters', 'master.client.adt', 'public_html'),
      templates=builders['templates'],
      tagComparator=tag_comparator,
      enable_http_status_push=active_master_cls.is_production_host)

  # TODO: AutoSetupMaster's settings for the following are too low to be
  # useful for most projects. We should fix that.
  c['buildHorizon'] = 3000
  c['logHorizon'] = 3000
  # Must be at least 2x the number of slaves.
  c['eventHorizon'] = 200


def _ComputeBuilders(builders, m_annotator):
  actual_builders = []

  def cmp_fn(a, b):
    a_cat = builders['builders'][a].get('category')
    b_cat = builders['builders'][b].get('category')
    if a_cat != b_cat:
      return 1 if a_cat > b_cat else -1
    if a != b:
      return 1 if a > b else -1
    return 0

  for builder_name in sorted(builders['builders'], cmp=cmp_fn):
    builder_data = builders['builders'][builder_name]
    scheduler_name = builder_data['scheduler']

    # We will automatically merge all build requests for any
    # builder that can be scheduled; this is normally the behavior
    # we want for repo-triggered builders and cron-triggered builders.
    # You can override this behavior by setting the mergeRequests field though.
    merge_requests = builder_data.get('mergeRequests', bool(scheduler_name))

    slavebuilddir = builder_data.get('slavebuilddir',
                                     util.safeTranslate(builder_name))
    factory = m_annotator.BaseFactory(
        recipe=builder_data['recipe'],
        factory_properties=builder_data.get('properties')
    )
    actual_builders.append({
        'auto_reboot': builder_data.get('auto_reboot', False),
        'mergeRequests': merge_requests,
        'name': builder_name,
        'factory': factory,
        'slavebuilddir': slavebuilddir,
        'slavenames': chromium_utils.GetSlaveNamesForBuilder(builders,
                                                             builder_name),
        'category': builder_data.get('category'),
        'trybot': builder_data.get('trybot'),
    })

  return actual_builders


def _ComputeSchedulers(builders):
  scheduler_to_builders = {}
  for builder_name, builder_data in builders['builders'].items():
    scheduler_names = builder_data['scheduler']
    if scheduler_names:
      if not isinstance(scheduler_names, list):
        scheduler_names = [scheduler_names]
      for scheduler_name in scheduler_names:
        if scheduler_name not in builders['schedulers']:
          raise ValueError('unknown scheduler "%s"' % scheduler_name)
        scheduler_to_builders.setdefault(scheduler_name, []).append(builder_name)

  schedulers = []
  for scheduler_name, scheduler_values in builders['schedulers'].items():
    scheduler_type = scheduler_values['type']
    builder_names = scheduler_to_builders[scheduler_name]

    if scheduler_type in ('git_poller', 'repo_poller'):
      schedulers.append(SingleBranchScheduler(
          name=scheduler_name,
          branch='master',
          treeStableTimer=60,
          builderNames=builder_names))

    elif scheduler_type == 'cron':
      schedulers.append(Nightly(
          name=scheduler_name,
          branch='master',
          minute=scheduler_values['minute'],
          hour=scheduler_values['hour'],
          builderNames=builder_names))

    elif scheduler_type == 'gs_poller':
      schedulers.append(EmulatorSingleBranchScheduler(
          name=scheduler_name,
          treeStableTimer=1,
          builderNames=builder_names,
          change_filter = ChangeFilter(
                branch=scheduler_values['branch'],
                project=scheduler_values['project'])))

    else:
      raise ValueError('unsupported scheduler type "%s"' % scheduler_type)

  return schedulers


def _ComputeChangeSourceAndTagComparator(builders):
  change_source = []
  tag_comparator = None

  if builders['change_source'] is not None:
    for cs_name, cs_values in builders['change_source'].items():
      if cs_values['type'] == 'GSMultiPoller':
        change_source.append(GSMultiPoller(cs_name,
                                     cs_values['gs_bucket'],
                                     cs_values['gs_path'],
                                     cs_values['pollInterval'],
                                     cs_values['project'],
                                     cs_values['branch'],
                                     cs_values['name_identifier']))
      else:
        raise ValueError('unsupported change source type %s' % cs_values['type'])
    return change_source, tag_comparator

  for url in sorted(set(v['git_repo_url'] for
                        v in builders['schedulers'].values()
                        if v['type'] == 'git_poller')):
    change_source.append(gitiles_poller.GitilesPoller(url))

  for scheduler_config in builders['schedulers'].values():
    if scheduler_config['type'] != 'repo_poller':
      continue

    rev_link_template = scheduler_config.get('rev_link_template')
    branch = scheduler_config.get('branch')
    branches = [branch] if branch is not None else None
    change_source.append(repo_poller.RepoPoller(
        repo_url=scheduler_config['repo_url'],
        manifest='manifest',
        repo_branches=branches,
        pollInterval=300,
        revlinktmpl=rev_link_template))

  # We have to set the tag_comparator to something, but if we have multiple
  # repos, the tag_comparator will not work properly (it's meaningless).
  # It's not clear if there's a good answer to this.
  if change_source:
    tag_comparator = change_source[0].comparator

  return change_source, tag_comparator
