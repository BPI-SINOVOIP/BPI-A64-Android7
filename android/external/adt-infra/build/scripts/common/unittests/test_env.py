# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Script to setup the environment to run unit tests.

Modifies PYTHONPATH to automatically include parent, common and pylibs
directories.
"""

import os
import sys

RUNTESTS_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_PATH = os.path.join(RUNTESTS_DIR, 'data')
BASE_DIR = os.path.abspath(os.path.join(RUNTESTS_DIR, '..', '..', '..'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'buildbot_8_4p1'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'buildbot_slave_8_4'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party',
                                'google_api_python_client'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'httplib2/python2'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'jinja2'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'markupsafe'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'mock-1.0.1'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'oauth2client'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'sqlalchemy_0_7_1'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'twisted_10_2'))
sys.path.insert(0, os.path.join(BASE_DIR, 'third_party', 'uritemplate'))
sys.path.insert(0, os.path.join(BASE_DIR, 'scripts'))
sys.path.insert(0, os.path.join(BASE_DIR, 'site_config'))
