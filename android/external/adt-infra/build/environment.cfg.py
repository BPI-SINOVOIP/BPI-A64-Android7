# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Extension script to <build>/scripts/common/env.py to add 'build_internal'
paths.
"""

import os

def Extend(pythonpath, cwd):
  """Path extension function (see common.env).

  In this invocation, 'cwd' is the <build> directory.
  """
  third_party_base = os.path.join(cwd, 'third_party')
  build_path = [
      os.path.join(cwd, 'scripts'),
      os.path.join(cwd, 'site_config'),
      third_party_base,
  ]

  # Add 'BUILD/third_party' paths.
  build_path += [os.path.join(third_party_base, *parts) for parts in (
      ('buildbot_8_4p1',),
      ('buildbot_slave_8_4',),
      ('jinja2',),
      ('markupsafe',),
      ('mock-1.0.1',),
      ('coverage-3.7.1',),
      ('twisted_10_2',),
      ('requests_1_2_3',),
      ('sqlalchemy_0_7_1',),
      ('sqlalchemy_migrate_0_7_1',),
      ('tempita_0_5',),
      ('decorator_3_3_1',),
      ('setuptools-0.6c11',),
      ('httplib2', 'python2',),
      ('oauth2client',),
      ('uritemplate',),
      ('google_api_python_client',),
      ('site-packages',),
      ('boto'),
      ('gcs_oauth2_boto_plugin'),
      ('pyasn1'),
      ('pyasn1_modules'),
      ('retry_decorator'),
      ('rsa'),
      ('socks'),
  )]
  return pythonpath.Append(*build_path)
