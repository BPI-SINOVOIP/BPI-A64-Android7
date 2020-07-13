#!/usr/bin/env python
# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import optparse
import sys

def parse_args():
  parse = optparse.OptionParser()

  parse.add_option('-s', '--source', help='Path to configuration file.')
  parse.add_option('-o', '--output_json',
                   help='Output JSON information into a specified file.')
  return parse.parse_args()

def main():
  options, _ = parse_args()
  local_vars = {}
  execfile(options.source, local_vars)
  with open(options.output_json, 'w') as f:
    f.write(json.dumps(local_vars['config']))

if __name__ == '__main__':
  sys.exit(main())
