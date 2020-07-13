#!/usr/bin/python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Simple client for the Gerrit REST API.

Usage:
  ./gerrit_client.py \
    -j /tmp/out.json \
    -u https://chromium.googlesource.com/chromium/src/+log?format=json \
"""

import argparse
import json
import logging
import sys
import time
import urllib
import urlparse

from common import find_depot_tools
from gerrit_util import CreateHttpConn, ReadHttpResponse, ReadHttpJsonResponse


class Error(Exception):
  pass


def main(args):
  parsed_url = urlparse.urlparse(args.url)
  if not parsed_url.scheme.startswith('http'):
    raise Error('Invalid URI scheme (expected http or https): %s' % args.url)

  # Force the format specified on command-line.
  qdict = {}
  if parsed_url.query:
    qdict.update(urlparse.parse_qs(parsed_url.query))

  f = qdict.get('format')
  if f:
    # Load the latest format specification.
    f = f[-1]
  else:
    # Default to JSON.
    f = 'json'

  path = parsed_url.path
  if qdict:
    path = '%s?%s' % (path, urllib.urlencode(qdict, doseq=True))
  if args.attempts < 1:
    args.attempts = 1
  retry_delay_seconds = 2
  for i in xrange(args.attempts):
    conn = CreateHttpConn(parsed_url.netloc, path)

    if f == 'json':
      result = ReadHttpJsonResponse(conn)
    elif f == 'text':
      # Text fetching will pack the text into structured JSON.
      result = ReadHttpResponse(conn).read()
      if result:
        # Wrap in a structured JSON for export to recipe module.
        result = {
          'value': result,
        }
      else:
        result = None
    else:
      raise ValueError('Unnknown format: %s' % (f,))

    logging.info('Reading from %s (%d/%d)', 
          conn.req_params['url'], (i+1), args.attempts)
    if result is not None or (i+1) >= args.attempts:
      if not args.quiet:
        logging.info('Read from %s (%d/%d): %s',
            conn.req_params['url'], (i+1), args.attempts, result)
        break

    logging.error("Request returned empty result; sleeping %d seconds",
        retry_delay_seconds)
    time.sleep(retry_delay_seconds)
    retry_delay_seconds *= 2

  with open(args.json_file, 'w') as json_file:
    json.dump(result, json_file)


if __name__ == '__main__':
  logging.basicConfig()
  logging.getLogger().setLevel(logging.INFO)

  parser = argparse.ArgumentParser()
  parser.add_argument(
    '-j',
    '--json-file',
    required=True,
    type=str,
  )
  parser.add_argument(
    '-u',
    '--url',
    required=True,
    type=str,
  )
  parser.add_argument(
    '-a',
    '--attempts',
    type=int,
    default=1,
    help='The number of attempts make (with exponential backoff) before '
         'failing.',
  )
  parser.add_argument(
    '-q',
    '--quiet',
    action='store_true',
    help='Suppress file contents logging output.')
  sys.exit(main(parser.parse_args()))
