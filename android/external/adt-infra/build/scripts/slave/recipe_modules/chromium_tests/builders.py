# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.types import freeze

from . import chromium_android
from . import chromium_angle
from . import chromium_chrome
from . import chromium_chromiumos
from . import chromium_fyi
from . import chromium_linux
from . import chromium_lkgr
from . import chromium_mac
from . import chromium_memory
from . import chromium_memory_fyi
from . import chromium_perf
from . import chromium_perf_fyi
from . import chromium_webkit
from . import chromium_win
from . import client_skia
from . import client_v8_fyi
from . import tryserver_chromium_linux
from . import tryserver_chromium_mac
from . import tryserver_chromium_perf

BUILDERS = freeze({
  'chromium.android': chromium_android.SPEC,
  'chromium.angle': chromium_angle.SPEC,
  'chromium.chrome': chromium_chrome.SPEC,
  'chromium.chromiumos': chromium_chromiumos.SPEC,
  'chromium.fyi': chromium_fyi.SPEC,
  'chromium.linux': chromium_linux.SPEC,
  'chromium.lkgr': chromium_lkgr.SPEC,
  'chromium.mac': chromium_mac.SPEC,
  'chromium.memory': chromium_memory.SPEC,
  'chromium.memory.fyi': chromium_memory_fyi.SPEC,
  'chromium.perf': chromium_perf.SPEC,
  'chromium.perf.fyi': chromium_perf_fyi.SPEC,
  'chromium.webkit': chromium_webkit.SPEC,
  'chromium.win': chromium_win.SPEC,
  'client.skia': client_skia.SPEC,
  'client.v8.fyi': client_v8_fyi.SPEC,
  'tryserver.chromium.linux': tryserver_chromium_linux.SPEC,
  'tryserver.chromium.mac': tryserver_chromium_mac.SPEC,
  'tryserver.chromium.perf': tryserver_chromium_perf.SPEC,
})
