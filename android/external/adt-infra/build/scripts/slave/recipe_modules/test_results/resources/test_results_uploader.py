# Copyright (c) 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Single function to upload files to the test-results server.

Usage:
  upload_test_results('test-results.appspot.com',
                      [('master', 'chromium.webkit')],
                      ['times_ms.json', 'full_results.json'],
                      timeout_secs=120)
"""

import codecs
import logging
import mimetypes
import socket
import time
import urllib2


class TimeoutError(Exception):
  pass


def upload_test_results(host, params, files, timeout_secs):
  """Upload json files to test results server.

  Args:
    host: String, hostname of server.
    params: [(name, value), ...]
    files: ['file1.json', 'file2.json', ...]
    timeout_secs: Seconds until we give up and raise TimeoutError.

  Raises:
    TimeoutError if timeout_secs elapses without a successful attempt.
  """
  file_objs = []
  for filename, path in files:
    with codecs.open(path, 'rb') as f:
      file_objs.append(('file', filename, f.read()))

  return _retry_exp_backoff(
      lambda remaining_secs: _try_uploading_test_results(
          host, params, file_objs, remaining_secs),
      timeout_secs)


def _try_uploading_test_results(host, attrs, file_objs, timeout_secs):
  # TODO(estaab): https:// for prod.
  url = 'http://%s/testfile/upload' % host
  content_type, data = _encode_form_data(attrs, file_objs)
  headers = {'Content-Type': content_type}
  request = urllib2.Request(url, data, headers)
  return urllib2.urlopen(request, timeout=timeout_secs)


def _encode_form_data(fields, files):
  """Encode form fields for multipart/form-data.

  Args:
    fields: A sequence of (name, value) elements for regular form fields.
    files: A sequence of (name, filename, value) elements for data to be
       uploaded as files.
  Returns:
    (content_type, body) ready for httplib.HTTP instance.

  Source:
    http://code.google.com/p/rietveld/source/browse/trunk/upload.py
  """
  BOUNDARY = '-M-A-G-I-C---B-O-U-N-D-A-R-Y-'
  CRLF = '\r\n'
  lines = []

  for key, value in fields:
    lines.append('--' + BOUNDARY)
    lines.append('Content-Disposition: form-data; name="%s"' % key)
    lines.append('')
    if isinstance(value, unicode):
      value = value.encode('utf-8')
    lines.append(value)

  for key, filename, value in files:
    lines.append('--' + BOUNDARY)
    lines.append('Content-Disposition: form-data; name="%s"; filename="%s"' %
                 (key, filename))
    lines.append('Content-Type: application/json; charset=utf-8')
    lines.append('')
    if isinstance(value, unicode):
      value = value.encode('utf-8')
    lines.append(value)

  lines.append('--' + BOUNDARY + '--')
  lines.append('')
  body = CRLF.join(lines)
  content_type = 'multipart/form-data; boundary=%s' % BOUNDARY
  return content_type, body


def _retry_exp_backoff(func, timeout_secs):
  """Returns a retries a until timeout_seconds has passed.

  Args:
    func: Callable that takes a seconds remaining parameter.
    timeout_secs: Raise TimeoutError if this much time passes.

  Returns:
    Return value of func() on success.

  Raises:
    TimeoutError if timeout_secs seconds pass.
  """
  initial_backoff_secs = 10
  backoff_secs = initial_backoff_secs
  grown_factor = 1.5
  total_sleep = 0

  while True:
    try:
      return func(timeout_secs - total_sleep)
    except urllib2.HTTPError as e:
      if total_sleep + backoff_secs > timeout_secs:
        raise TimeoutError()
      logging.warn('Received HTTP status %s loading "%s".  '
                   'Retrying in %s seconds...',
                   e.code, e.filename, backoff_secs)
      time.sleep(backoff_secs)
      total_sleep += backoff_secs
      backoff_secs *= grown_factor
    except socket.timeout as e:
      logging.warn('Timed out after %d seconds.', timeout_secs)
      raise TimeoutError()
