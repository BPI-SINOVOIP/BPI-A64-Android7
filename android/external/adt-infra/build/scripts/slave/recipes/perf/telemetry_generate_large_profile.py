# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'archive',
  'bot_update',
  'chromium',
  'file',
  'gclient',
  'gsutil',
  'path',
  'platform',
  'properties',
  'python',
  'step',
  'zip',
]


def _PlatformSpecificExecutable(api):
  """The OS-specific path to the executable."""
  if api.platform.is_mac:
    return ['Google Chrome.app', 'Contents', 'MacOS', 'Google Chrome']
  elif api.platform.is_linux:
    return ['chrome']
  elif api.platform.is_win:
    return ['chrome.exe']


def _CloudStoragePath(api):
  """The path in cloud storage to store the zipped large profile."""
  return 'large_profile/' + api.platform.name


def _DownloadAndExtractBinary(api):
  """Downloads the binary from the revision passed to the recipe."""
  build_archive_url = api.properties['parent_build_archive_url']
  api.archive.download_and_unzip_build(
      step_name='Download and Extract Binary',
      target='Release',
      build_url=None,  # This is a required parameter, but has no effect.
      build_archive_url=build_archive_url)


def _GenerateProfile(api, output_directory):
  """Generates a large profile.

  Args:
    output_directory: A string representation of the directory in which a
    profile should be generated.
  """
  executable_suffix = _PlatformSpecificExecutable(api)
  browser_executable = api.chromium.output_dir.join(*executable_suffix)
  script_path = api.path['checkout'].join('tools', 'perf', 'generate_profile')

  args = [
      '--browser=exact',
      '--browser-executable=' + str(browser_executable),
      '--profile-type-to-generate=large_profile',
      '--output-dir=' + output_directory,
  ]

  api.python('Generate Large Profile', script_path, args=args)


def RunSteps(api):
  api.chromium.set_config('chromium')
  api.gclient.set_config('chromium')
  api.bot_update.ensure_checkout(force=True)

  try:
    profile_directory = api.path.mkdtemp('large-profile')
    zipped_profile_directory = api.path.mkdtemp('zipped-profile')
    _DownloadAndExtractBinary(api)
    _GenerateProfile(api, str(profile_directory))

    # Zip the profile.
    zipped_profile_path = zipped_profile_directory.join('large_profile.zip')
    api.zip.directory(
        'Zip Large Profile', profile_directory, zipped_profile_path)

    # Upload the result to cloud storage.
    bucket = 'chrome-partner-telemetry'
    cloud_file = _CloudStoragePath(api)
    api.gsutil.upload(str(zipped_profile_path), bucket, cloud_file)
  finally:
    api.file.rmtree('Remove profile directory.', profile_directory)
    api.file.rmtree('Remove zipped profile directory.',
                    zipped_profile_directory)


def GenTests(api):
  for platform in ('linux', 'win', 'mac'):
    archive_url = 'gs://chrome-perf/%s Builder/testbuildurl' % platform.title()
    yield (
        api.test(platform) +
        api.properties.generic(
            mastername='master.chromium.perf.fyi',
            parent_build_archive_url=archive_url,
            parent_got_revision='5d6dd8eaee742daf7f298f533fb0827dc4a693fd') +
        api.platform.name(platform)
    )
