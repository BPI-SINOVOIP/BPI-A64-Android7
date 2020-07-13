#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
Dumps master config as JSON.

Uses master_cfg_utils.LoadConfig, which should be called at most once
in the same process. That's why this is a separate utility.
"""

import argparse
import json
import os
import sys

SCRIPTS_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), os.pardir))
if not SCRIPTS_DIR in sys.path:
  sys.path.insert(0, SCRIPTS_DIR)

from common import env

env.Install()

from common import master_cfg_utils
from master.factory.build_factory import BuildFactory


class BuildbotJSONEncoder(json.JSONEncoder):
  def default(self, obj):  # pylint: disable=E0202
    if isinstance(obj, BuildFactory):
      return {'repr': repr(obj), 'properties': obj.properties.asDict()}

    return repr(obj)


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument('master')
  parser.add_argument('output', type=argparse.FileType('w'), default=sys.stdout)

  args = parser.parse_args(argv)

  result = master_cfg_utils.LoadConfig(args.master)
  json.dump(result['BuildmasterConfig'],
            args.output,
            cls=BuildbotJSONEncoder,
            indent=4)
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
