# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""
This recipe checks if a version update on branch <B> is necessary, where
'version' refers to the contents of the v8 version file (part of the v8
sources).

The recipe will:
- Commit a v8 version change to <B> with an incremented patch level if the
  latest two commits point to the same version.
- Make sure that the actual HEAD of <B> is tagged with its v8 version (as
  specified in the v8 version file at HEAD).
- Update a ref called <B>-lkgr to point to the latest commit that has a unique,
  incremented version and that is tagged with that version.
"""

import re

DEPS = [
  'file',
  'gclient',
  'git',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
  'v8',
]

REPO = 'https://chromium.googlesource.com/v8/v8'
BRANCH_RE = re.compile(r'^\d+\.\d+$')
VERSION_FILE = 'include/v8-version.h'
VERSION_LINE_RE = r'^#define %s\s+(\d*)$'
VERSION_LINE_REPLACEMENT = '#define %s %s'
V8_MAJOR = 'V8_MAJOR_VERSION'
V8_MINOR = 'V8_MINOR_VERSION'
V8_BUILD = 'V8_BUILD_NUMBER'
V8_PATCH = 'V8_PATCH_LEVEL'
MAX_COMMIT_WAIT_RETRIES = 5


class V8Version(object):
  """A v8 version as used for tagging (with patch level), e.g. '3.4.5.1'."""

  def __init__(self, major, minor, build, patch):
    self.major = major
    self.minor = minor
    self.build = build
    self.patch = patch

  def __eq__(self, other):
    return (self.major == other.major and
            self.minor == other.minor and
            self.build == other.build and
            self.patch == other.patch)

  def __str__(self):
    return '%s.%s.%s.%s' % (self.major, self.minor, self.build, self.patch)

  def with_incremented_patch(self):
    return V8Version(
        self.major, self.minor, self.build, str(int(self.patch) + 1))

  def update_version_file_blob(self, blob):
    """Takes a version file's text and returns it with this object's version.
    """
    def sub(label, value, text):
      return re.sub(
          VERSION_LINE_RE % label,
          VERSION_LINE_REPLACEMENT % (label, value),
          text,
          flags=re.M,
      )
    blob = sub(V8_MAJOR, self.major, blob)
    blob = sub(V8_MINOR, self.minor, blob)
    blob = sub(V8_BUILD, self.build, blob)
    return sub(V8_PATCH, self.patch, blob)

  @staticmethod
  def from_version_file(blob):
    major = re.search(VERSION_LINE_RE % V8_MAJOR, blob, re.M).group(1)
    minor = re.search(VERSION_LINE_RE % V8_MINOR, blob, re.M).group(1)
    build = re.search(VERSION_LINE_RE % V8_BUILD, blob, re.M).group(1)
    patch = re.search(VERSION_LINE_RE % V8_PATCH, blob, re.M).group(1)
    return V8Version(major, minor, build, patch)


def InitClean(api):
  """Ensures a clean state of the git checkout."""
  api.git(
      'checkout', '-f', 'FETCH_HEAD',
      cwd=api.path['checkout'],
  )
  api.git(
      'branch', '-D', 'work',
      ok_ret='any',
      cwd=api.path['checkout'],
  )
  api.git(
      'clean', '-ffd',
      cwd=api.path['checkout'],
  )


def Git(api, *args, **kwargs):
  """Convenience wrapper."""
  return api.git(
      *args,
      cwd=api.path['checkout'],
      stdout=api.raw_io.output(),
      **kwargs
  ).stdout


def GetVersionContent(api, ref, desc):
  """Read the content of the version file at a paricular ref."""
  return Git(
      api, 'show', '%s:%s' % (ref, VERSION_FILE),
      name='Check %s version file' % desc,
  )


def GetCommitForRef(api, repo, ref):
  result = Git(
      api, 'ls-remote', repo, ref,
      # Need str() to turn unicode into ascii in production.
      name=str('git ls-remote %s' % ref.split('/')[-1]),
  ).strip()
  if result:
    # Extract hash if available. Otherwise keep empty string.
    result = result.split()[0]
  api.step.active_result.presentation.logs['ref'] = [result]
  return result


def PushRef(api, repo, ref, hsh):
  api.git(
      'push', repo, '+%s:%s' % (hsh, ref),
      cwd=api.path['checkout'],
  )


def LogStep(api, text):
  api.step('log', ['echo', text])


def IncrementVersion(api, ref, latest_version, latest_version_file):
  """Increment the version on branch 'ref' to the next patch level and wait
  for the committed ref to be gnumbd-ed or time out.

  Args:
    api: The recipe api.
    ref: Ref name where to change the version, e.g.
         refs/remotes/branch-heads/1.2.
    latest_version: The currently latest version to be incremented.
    latest_version_file: The content of the current version file.
  """

  # Create a fresh work branch.
  api.git(
      'new-branch', 'work', '--upstream', ref,
      cwd=api.path['checkout'],
  )

  # Increment patch level and update file content.
  latest_version = latest_version.with_incremented_patch()
  latest_version_file = latest_version.update_version_file_blob(
      latest_version_file)

  # Write file to disk.
  api.file.write(
      'Increment version',
      api.path['checkout'].join(VERSION_FILE),
      latest_version_file,
  )

  # Commit and push changes.
  api.git(
      'commit', '-am', 'Version %s' % latest_version,
      cwd=api.path['checkout'],
  )

  if api.properties.get('dry_run'):
    api.step('Dry-run commit', cmd=None)
    return

  api.git(
      'cl', 'land', '-f', '--bypass-hooks',
      cwd=api.path['checkout'],
  )

  # Function to check if commit has landed.
  def has_landed():
    api.git('fetch', cwd=api.path['checkout'])
    real_latest_version = V8Version.from_version_file(
        GetVersionContent(api, ref, 'committed'))
    return real_latest_version == latest_version

  # Wait for commit to land (i.e. wait for gnumbd).
  count = 1
  while not has_landed():
    if count == MAX_COMMIT_WAIT_RETRIES:
      # This is racy. Someone other than this script might
      # commit another version change right before the fetch (rarely).
      # In this case, we time out and leave this commit untagged.
      step_result = api.step(
          'Waiting for commit timed out', cmd=None)
      step_result.presentation.status = api.step.FAILURE
      break
    api.python.inline(
        'Wait for commit',
        'import time; time.sleep(%d)' % (5 * count),
    )
    count += 1


def RunSteps(api):
  # Ensure a proper branch is specified.
  branch = api.properties.get('branch')
  if not branch or not BRANCH_RE.match(branch):
    raise api.step.InfraFailure('A release branch must be specified.')
  repo = api.properties.get('repo', REPO)

  local_branch_ref = 'refs/remotes/branch-heads/%s' % branch
  lkgr_ref = 'refs/heads/%s-lkgr' % branch

  api.gclient.set_config('v8')
  api.gclient.checkout(with_branch_heads=True)

  # Enforce a clean state.
  InitClean(api)

  # Check the last two versions.
  latest_version_file = GetVersionContent(
      api, local_branch_ref, 'latest')
  latest_version = V8Version.from_version_file(latest_version_file)

  previous_version_file = GetVersionContent(
      api, local_branch_ref + '~1', 'previous')
  previous_version = V8Version.from_version_file(previous_version_file)

  # If the last two commits have the same version, we need to create a version
  # increment.
  if latest_version == previous_version:
    IncrementVersion(
        api, local_branch_ref, latest_version, latest_version_file)
  elif not latest_version == previous_version.with_incremented_patch():
    step_result = api.step(
        'Incorrect patch levels between %s and %s' % (
              previous_version, latest_version),
        cmd=None,
    )
    step_result.presentation.status = api.step.WARNING

  # Read again the current HEAD's version and check if it is tagged with it.
  # If fetching the version change from above has timed out, we don't want
  # to set the wrong tag.
  head = Git(api, 'log', '-n1', '--format=%H', local_branch_ref).strip()
  head_version = V8Version.from_version_file(
      GetVersionContent(api, head, 'head'))
  tag = Git(api, 'describe', '--tags', head).strip()

  if tag != str(head_version):
    # Tag latest version.
    if api.properties.get('dry_run'):
      api.step('Dry-run tag %s' % head_version, cmd=None)
    else:
      api.git(
          'tag', str(head_version), head,
          cwd=api.path['checkout'],
      )
      api.git(
          'push', repo, str(head_version),
          cwd=api.path['checkout'],
      )

  # Get the branch's current lkgr ref and update to HEAD.
  current_lkgr = GetCommitForRef(api, repo, lkgr_ref)
  # If the lkgr_ref doesn't exist, it's an empty string. In this case the push
  # ref command will create it.
  if head != current_lkgr:
    if api.properties.get('dry_run'):
      api.step('Dry-run lkgr update %s' % head, cmd=None)
    else:
      PushRef(api, repo, lkgr_ref, head)
  else:
    LogStep(api, 'There is no new lkgr.')


# Excerpt of the v8 version file.
VERSION_FILE_TMPL = """
#define V8_MAJOR_VERSION 3
#define V8_MINOR_VERSION 4
#define V8_BUILD_NUMBER 3
#define V8_PATCH_LEVEL %d
"""

def GenTests(api):
  hsh_old = '74882b7a8e55268d1658f83efefa1c2585cee723'
  hsh_new = 'c1a7fd0c98a80c52fcf6763850d2ee1c41cfe8d6'

  def stdout(step_name, text):
    return api.override_step_data(
        step_name, api.raw_io.stream_output(text, stream='stdout'))

  def wait_for_commit(patch_level, count, found_commit):
    """Simulate waiting for the commit to show up after gnumbd processing.

    Args:
      patch_level: The version patch level from before committing.
      count: The 'count'th time we wait for the commit. Assume count > 0.
      found_commit: Indicates that the commit has been found.
    """
    # Recipe step name disambiguation.
    suffix = " (%d)" % count if count > 1 else ""
    return stdout(
        'Check committed version file' + suffix,
        VERSION_FILE_TMPL % (patch_level + bool(found_commit)),
    )

  def test(name, patch_level_previous, patch_level_latest,
           patch_level_after_commit, current_lkgr, head, head_tag,
           wait_count=0, commit_found_count=0, dry_run=False):
    test_data = (
        api.test(name) +
        api.properties.generic(mastername='client.v8.fyi',
                               buildername='Auto-tag',
                               branch='3.4') +
        stdout(
            'Check latest version file',
            VERSION_FILE_TMPL % patch_level_latest,
        ) +
        stdout(
            'Check previous version file',
            VERSION_FILE_TMPL % patch_level_previous,
        ) +
        stdout(
            'Check head version file',
            VERSION_FILE_TMPL % patch_level_after_commit,
        ) +
        stdout('git log', head) +
        stdout('git describe', head_tag) +
        stdout(
            'git ls-remote 3.4-lkgr',
            current_lkgr + '\trefs/heads/3.4-lkgr',
        )
    )
    if dry_run:
      test_data += api.properties(dry_run=True)
    else:
      # Test data for the loop waiting for the version-increment commit.
      for count in range(1, wait_count + 1):
        test_data += wait_for_commit(
            patch_level_latest, count, count == commit_found_count)
    return test_data

  # Test where version, the tag at HEAD and the lkgr are up-to-date.
  yield test(
      'same_lkgr',
      patch_level_previous=2,
      patch_level_latest=3,
      patch_level_after_commit=3,
      current_lkgr=hsh_old,
      head=hsh_old,
      head_tag='3.4.3.3',
  )
  # Requires a version update, sets a tag and updates the lkgr. After the
  # version-increment commit has been found, 'git describe' doesn't find
  # an accurate version tag.
  yield test(
      'update',
      patch_level_previous=2,
      patch_level_latest=2,
      patch_level_after_commit=3,
      current_lkgr=hsh_old,
      head=hsh_new,
      head_tag='3.4.3.2-sometext',
      wait_count=2,
      commit_found_count=2,
  )
  # Requires a version update, but times out waiting for gnumbd. After the
  # timeout, HEAD still points to the last commit which has a consistent
  # version tag.
  yield test(
      'update_timeout',
      patch_level_previous=2,
      patch_level_latest=2,
      patch_level_after_commit=2,
      current_lkgr=hsh_old,
      head=hsh_old,
      head_tag='3.4.3.2',
      wait_count=MAX_COMMIT_WAIT_RETRIES,
      commit_found_count=MAX_COMMIT_WAIT_RETRIES + 1,
  )
  # No updates required, but lkgr ref is missing, i.e. was never set. Also warn
  # about an inconsistency in the patch levels.
  yield test(
      'missing',
      patch_level_previous=1,
      patch_level_latest=3,
      patch_level_after_commit=3,
      current_lkgr='',
      head=hsh_new,
      head_tag='3.4.3.3',
  )
  # Everything out-of-date, but dry run.
  yield test(
      'dry_run',
      patch_level_previous=2,
      patch_level_latest=2,
      patch_level_after_commit=2,
      current_lkgr='hsh_old',
      head=hsh_new,
      head_tag='3.4.3.1-sometext',
      dry_run=True
  )
  # The bot was triggered without specifying a branch.
  yield (
      api.test('missing_branch') +
      api.properties.generic(mastername='client.v8.fyi',
                             buildername='Auto-tag')
  )
