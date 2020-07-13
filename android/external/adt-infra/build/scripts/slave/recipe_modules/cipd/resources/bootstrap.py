# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import errno
import hashlib
import httplib
import json
import os
import sys
import tempfile
import time
import traceback
import urllib
import urllib2


# Default package repository URL.
CIPD_BACKEND_URL = 'https://chrome-infra-packages.appspot.com'

# ./cipd resolve \
#     infra/tools/cipd/ \
#     -version=git_revision:b5ececbd33984d968138f30f5cdee49574828328
CLIENT_VERSIONS = {
  'linux-386': 'a4e3ba1a926b614a93001f34cf46ab6aec726c1d',
  'linux-amd64': 'bcca10926e0f0c060a8f57cdb3cd228f9e83c395',
  'mac-amd64': 'c7218f9e18aab4a13736f2e85cd2aeff2ea0324d',
  'windows-386': 'c9cf5d35297a967b6e56e9f87f7b4f5bb3f521b4',
  'windows-amd64': 'c4165da82db2cc587000aaaa355244f3e40bbc50',
}


class CipdBootstrapError(Exception):
  """Raised by install_cipd_client on fatal error."""

def install_cipd_client(path, package, version):
  """Installs CIPD client to <path>/cipd.

  Args:
    path: root directory to install CIPD client into.
    package: cipd client package name, e.g. infra/tools/cipd/linux-amd64.
    version: version of the package to install.

  Returns:
    Absolute path to CIPD executable.
  """
  print('Ensuring CIPD client is up-to-date')
  version_file = os.path.join(path, 'VERSION')
  bin_file = os.path.join(path, 'cipd')

  # Resolve version to concrete instance ID, e.g "live" -> "abcdef0123....".
  instance_id = call_cipd_api(
      'repo/v1/instance/resolve',
      {'package_name': package, 'version': version})['instance_id']
  print('CIPD client %s => %s', version, instance_id)

  # Already installed?
  installed_instance_id = (read_file(version_file) or '').strip()
  if installed_instance_id == instance_id and os.path.exists(bin_file):
    return bin_file

  # Resolve instance ID to an URL to fetch client binary from.
  client_info = call_cipd_api(
      'repo/v1/client',
      {'package_name': package, 'instance_id': instance_id})
  print('CIPD client binary info:\n%s', dump_json(client_info))

  # Fetch the client. It is ~10 MB, so don't bother and fetch it into memory.
  status, raw_client_bin = fetch_url(client_info['client_binary']['fetch_url'])
  if status != 200:
    print('Failed to fetch client binary, HTTP %d' % status)
    raise CipdBootstrapError('Failed to fetch client binary, HTTP %d' % status)
  digest = hashlib.sha1(raw_client_bin).hexdigest()
  if digest != client_info['client_binary']['sha1']:
    raise CipdBootstrapError('Client SHA1 mismatch')

  # Success.
  print('Fetched CIPD client %s:%s at %s', package, instance_id, bin_file)
  write_file(bin_file, raw_client_bin)
  os.chmod(bin_file, 0755)
  write_file(version_file, instance_id + '\n')
  return bin_file


def call_cipd_api(endpoint, query):
  """Sends GET request to CIPD backend, parses JSON response."""
  url = '%s/_ah/api/%s' % (CIPD_BACKEND_URL, endpoint)
  if query:
    url += '?' + urllib.urlencode(query)
  status, body = fetch_url(url)
  if status != 200:
    raise CipdBootstrapError('Server replied with HTTP %d' % status)
  try:
    body = json.loads(body)
  except ValueError:
    raise CipdBootstrapError('Server returned invalid JSON')
  status = body.get('status')
  if status != 'SUCCESS':
    m = body.get('error_message') or '<no error message>'
    raise CipdBootstrapError('Server replied with error %s: %s' % (status, m))
  return body


def fetch_url(url, headers=None):
  """Sends GET request (with retries).

  Args:
    url: URL to fetch.
    headers: dict with request headers.

  Returns:
    (200, reply body) on success.
    (HTTP code, None) on HTTP 401, 403, or 404 reply.

  Raises:
    Whatever urllib2 raises.
  """
  req = urllib2.Request(url)
  req.add_header('User-Agent', 'cipd recipe bootstrap.py')
  for k, v in (headers or {}).iteritems():
    req.add_header(str(k), str(v))
  i = 0
  while True:
    i += 1
    try:
      print('GET %s', url)
      return 200, urllib2.urlopen(req, timeout=60).read()
    except Exception as e:
      if isinstance(e, urllib2.HTTPError):
        print('Failed to fetch %s, server returned HTTP %d', url, e.code)
        if e.code in (401, 403, 404):
          return e.code, None
      else:
        print('Failed to fetch %s', url)
      if i == 20:
        raise
    print('Retrying in %d sec.', i)
    time.sleep(i)


def ensure_directory(path):
  """Creates a directory."""
  # Handle a case where a file is being converted into a directory.
  chunks = path.split(os.sep)
  for i in xrange(len(chunks)):
    p = os.sep.join(chunks[:i+1])
    if os.path.exists(p) and not os.path.isdir(p):
      os.remove(p)
      break
  try:
    os.makedirs(path)
  except OSError as e:
    if e.errno != errno.EEXIST:
      raise


def read_file(path):
  """Returns contents of a file or None if missing."""
  try:
    with open(path, 'r') as f:
      return f.read()
  except IOError as e:
    if e.errno == errno.ENOENT:
      return None
    raise


def write_file(path, data):
  """Puts a file on disk, atomically."""
  assert sys.platform in ('linux2', 'darwin')
  ensure_directory(os.path.dirname(path))
  fd, temp_file = tempfile.mkstemp(dir=os.path.dirname(path))
  with os.fdopen(fd, 'w') as f:
    f.write(data)
  os.rename(temp_file, path)


def dump_json(obj):
  """Pretty-formats object to JSON."""
  return json.dumps(obj, indent=2, sort_keys=True, separators=(',',':'))


def main():
  data = json.load(sys.stdin)
  package = "infra/tools/cipd/%s" % data['platform']
  version = CLIENT_VERSIONS[data['platform']]
  bin_path = data['bin_path']

  # return if this client version is already installed.
  exe_path = os.path.join(bin_path, 'cipd')
  try:
    if not os.path.isfile(exe_path):
      out = install_cipd_client(bin_path, package, version)
      assert out == exe_path
  except Exception as e:
    print ("Exception installing cipd: %s" % e)
    exc_type, exc_value, exc_traceback = sys.exc_info()
    traceback.print_tb(exc_traceback)
    return 1

  return 0

if __name__ == '__main__':
  sys.exit(main())
