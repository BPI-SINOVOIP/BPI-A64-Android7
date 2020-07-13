# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file is used by scripts/tools/buildbot-tool to generate master configs.

"""ActiveMaster definition."""

from config_bootstrap import Master

class %(master_classname)s(Master.%(master_base_class)s):
  project_name = '%(master_classname)s'
  master_port = %(master_port)s
  slave_port = %(slave_port)s
  master_port_alt = %(master_port_alt)s
  buildbot_url = '%(buildbot_url)s'
  buildbucket_bucket = %(buildbucket_bucket_str)s
  service_account_file = %(service_account_file_str)s
