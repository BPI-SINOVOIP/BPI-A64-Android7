# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import copy
import os
import sys

from . import chromium_linux
from . import chromium_mac
from . import chromium_win

# TODO(luqui): Separate the skia common scripts out so we can make this
# independent of build/.
sys.path.append(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))))))
from common.skia import builder_name_schema

# The Skia config just clones some regular Chromium builders, except that they
# use an up-to-date Skia.

# This list specifies which Chromium builders to "copy".
_builders = [
#  SPEC Module     Test Spec File         Builder Names
  (chromium_linux, 'chromium.linux.json', ['Linux Builder', 'Linux Tests']),
  (chromium_win,   'chromium.win.json',   ['Win Builder', 'Win7 Tests (1)']),
  (chromium_mac,   'chromium.mac.json',   ['Mac Builder', 'Mac10.9 Tests']),
]

SPEC = {
  'settings': {
    'build_gs_bucket': 'chromium-skia-gm',
  },
  'builders': {},
}

for spec_module, test_spec_file, builders_list in _builders:
  for builder in builders_list:
    for builder_name in (builder, builder_name_schema.TrybotName(builder)):
      builder_cfg = copy.deepcopy(spec_module.SPEC['builders'][builder])
      builder_cfg['gclient_config'] = 'chromium_skia'
      parent = builder_cfg.get('parent_buildername')
      if parent:
        if builder_name_schema.IsTrybot(builder_name):
          parent = builder_name_schema.TrybotName(parent)
        builder_cfg['parent_buildername'] = parent
      builder_cfg['patch_root'] = 'src/third_party/skia'
      builder_cfg['testing']['test_spec_file'] = test_spec_file
      SPEC['builders'][builder_name] = builder_cfg
