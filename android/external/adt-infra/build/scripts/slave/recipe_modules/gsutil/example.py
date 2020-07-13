# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'gsutil',
  'path',
]


def RunSteps(api):
  """Move things around in a loop!"""
  local_file = api.path['slave_build'].join('boom')
  bucket = 'chromium-recipe-test'
  cloud_file = 'some/random/path/to/boom'
  api.gsutil.upload(local_file, bucket, cloud_file,
      metadata={
        'Test-Field': 'value',
        'Remove-Me': None,
        'x-custom-field': 'custom-value',
        'Cache-Control': 'no-cache',
      },
      unauthenticated_url=True)

  api.gsutil(['cp',
                    'gs://chromium-recipe-test/some/random/path/**',
                    'gs://chromium-recipe-test/staging'])

  api.gsutil(['cp',
                    'gs://chromium-recipe-test/some/random/path/**',
                    'gs://chromium-recipe-test/staging'], version='3.25')

  api.gsutil.download_url(
      'https://storage.cloud.google.com/' + bucket + '/' + cloud_file,
      local_file,
      name='gsutil download url')

  # Non-normalized URL.
  try:
    api.gsutil.download_url(
        'https://someotherservice.localhost',
        local_file,
        name='gsutil download url')
  except AssertionError:
    pass

  new_cloud_file = 'staging/to/boom'
  new_local_file = api.path['slave_build'].join('erang')
  api.gsutil.download(bucket, new_cloud_file, new_local_file)

  private_key_file = 'path/to/key'
  signed_url = api.gsutil.signurl(private_key_file, bucket, cloud_file,
                                  name='signed url')
  api.gsutil.remove_url('gs://%s/%s' % (bucket, new_cloud_file))

  api.gsutil.download_with_polling('gs://chromium-recipe-test/foo/bar',
                                   'local/path/for/download',
                                   15,
                                   600)


def GenTests(api):
  yield api.test('basic')
