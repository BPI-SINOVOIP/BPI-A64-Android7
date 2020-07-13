#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import logging
import sys
import time

import requests  # "Unable to import" pylint: disable=F0401


def main():
  parser = argparse.ArgumentParser(
      description='Get a url and print its document.',
      prog='./runit.py pycurl.py')
  parser.add_argument('url', help='the url to fetch')
  parser.add_argument('--outfile', help='write output to this file')
  parser.add_argument('--attempts', type=int, default=1,
      help='The number of attempts make (with exponential backoff) before '
           'failing.')
  args = parser.parse_args()

  if args.attempts < 1:
    args.attempts = 1

  retry_delay_seconds = 2
  for i in xrange(args.attempts):
    r = requests.get(args.url)
    if r.status_code == requests.codes.ok:
      break
    logging.error("(%d/%d) Request returned status code: %d",
        i+1, args.attempts, r.status_code)
    if (i+1) >= args.attempts:
      r.raise_for_status()
    logging.info("Sleeping %d seconds, then retrying.", retry_delay_seconds)
    time.sleep(retry_delay_seconds)
    retry_delay_seconds *= 2

  if args.outfile:
    with open(args.outfile, 'w') as f:
      f.write(r.text)
  else:
    print r.text


if __name__ == '__main__':
  logging.basicConfig()
  logging.getLogger().setLevel(logging.INFO)
  sys.exit(main())
