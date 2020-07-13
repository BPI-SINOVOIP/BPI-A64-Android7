# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.config import config_item_context, ConfigGroup
from recipe_engine.config import Single

GS_BASE_URL = 'gs://chrome-amp-keys'

def BaseConfig(**_kwargs):
  return ConfigGroup(
    pool = Single(basestring),
    api_key_file_url = Single(basestring),
    api_secret_file_url = Single(basestring),
  )

config_ctx = config_item_context(BaseConfig)

@config_ctx()
def main_pool(c):
  SetDevicePoolConfigs(c, 'main_pool')

@config_ctx()
def commit_queue_pool(c):
  SetDevicePoolConfigs(c, 'commit_queue_pool')

@config_ctx()
def webview_pool(c):  # pragma: no cover
  SetDevicePoolConfigs(c, 'webview_pool')

def SetDevicePoolConfigs(c, pool_name):
  c.pool = pool_name
  c.api_key_file_url = '%s/%s/%s' % (GS_BASE_URL, pool_name, 'api_key')
  c.api_secret_file_url = '%s/%s/%s' % (GS_BASE_URL, pool_name, 'api_secret')