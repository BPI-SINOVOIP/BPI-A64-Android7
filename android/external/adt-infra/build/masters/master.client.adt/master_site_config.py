# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file was generated from
# scripts/tools/buildbot_tool_templates/master_site_config.py
# by "../../build/scripts/tools/buildbot-tool gen .".
# DO NOT EDIT BY HAND!


"""ActiveMaster definition."""

from config_bootstrap import Master

class ClientAdt(Master.Master3):
  project_name = 'ClientAdt'
  master_port = 8200
  slave_port = 8300
  master_port_alt = 8400
  buildbot_url = 'http://chromeos1-row3-rack2-host1.cros.corp.google.com:8200/'
  buildbucket_bucket = None
  service_account_file = None
