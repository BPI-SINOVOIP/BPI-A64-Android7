# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Common steps for recipes that sync/build Android sources."""

from recipe_engine import recipe_api

class AOSPApi(recipe_api.RecipeApi):
  def __init__(self, patch_project, revision, clobber, **kwargs):
    super(AOSPApi, self).__init__(**kwargs)
    self._repo_path = None
    self._patch_project = patch_project
    self._revision = revision
    self._clobber = clobber

  @property
  def with_lunch_command(self):
    return [self.m.path['build'].join('scripts', 'slave',
                                      'android', 'with_lunch'),
            self.c.build_path,
            self.c.lunch_flavor]

  def create_spec(self):
    spec = self.m.gclient.make_config(
      'chromium', PATCH_PROJECT=self._patch_project)
    spec.target_os = ['android']

    spec.solutions[0].revision = self._revision

    return spec

  def sync_chromium(self, spec):
    result = self.m.bot_update.ensure_checkout(spec)
    if not result.json.output['did_run']:
      self.m.gclient.checkout(spec)

    self.m.gclient.runhooks(env={'GYP_CHROMIUM_NO_ACTION': 1})

  def lastchange_steps(self):
    lastchange_command = self.m.path['checkout'].join('build', 'util',
                                                      'lastchange.py')
    self.m.step('Chromium LASTCHANGE', [
      lastchange_command,
      '-o', self.m.path['checkout'].join('build', 'util', 'LASTCHANGE'),
      '-s', self.m.path['checkout']]),
    self.m.step('Blink LASTCHANGE', [
      lastchange_command,
      '-o', self.m.path['checkout'].join('build', 'util', 'LASTCHANGE.blink'),
      '-s', self.m.path['checkout'].join('third_party', 'WebKit')])

  # TODO(iannucci): Refactor repo stuff into another module?
  def repo_init_steps(self):
    # The version of repo checked into depot_tools doesn't support switching
    # between branches correctly due to
    # https://code.google.com/p/git-repo/issues/detail?id=46 which is why we use
    # the copy of repo from the Android tree.
    # The copy of repo from depot_tools is only used to bootstrap the Android
    # tree checkout.
    repo_in_android_path = self.c.build_path.join('.repo', 'repo', 'repo')
    repo_copy_dir = self.m.path['slave_build'].join('repo_copy')
    repo_copy_path = self.m.path['slave_build'].join('repo_copy', 'repo')
    if self.m.path.exists(repo_in_android_path):
      self.m.file.makedirs('repo copy dir', repo_copy_dir)
      self.m.step('copy repo from Android', [
        'cp', repo_in_android_path, repo_copy_path])
      self.m.repo.repo_path = repo_copy_path
    self.m.file.makedirs('android source root', self.c.build_path)
    self.m.repo.init(self.c.repo.url, '-b', self.c.repo.branch,
                           cwd=self.c.build_path)
    self.m.path.mock_add_paths(repo_in_android_path)

  def repo_sync_steps(self):
    # repo_init_steps must have been invoked first.
    sync_flags = self.c.repo.sync_flags.as_jsonish()
    if self.c.sync_manifest_override:
      sync_flags.extend(['-m', self.c.sync_manifest_override])

    try:
      self.m.repo.sync(*sync_flags, cwd=self.c.build_path)
    except self.m.step.StepFailure:
      # Remove index.locks
      self.m.gclient.break_locks()
      # Clean checkouts.
      self.m.repo.clean(cwd=self.c.build_path)
      # Retry.
      self.m.repo.sync(*sync_flags, cwd=self.c.build_path,
                             suffix='retry')

  def rsync_chromium_into_android_tree_step(self):
    # Calculate the blacklist of files to not copy across.
    result = self.m.step(
      'calculate blacklist',
      [
        self.m.path['checkout'].join('android_webview', 'buildbot',
                                     'deps_whitelist.py'),
        '--method', 'android_rsync_build',
        '--path-to-deps', self.m.path['checkout'].join('DEPS'),
        '--output-json', self.m.json.output()
      ],
      step_test_data=self.test_api.calculate_blacklist
    )

    blacklist = result.json.output['blacklist']
    chrome_checkout = str(self.m.path['checkout'])
    android_chrome_checkout = self.c.slave_chromium_in_android_path

    # rsync expects the from path to end in a / otherwise it copies
    # the source folder into the destination folder instead of over
    # it.
    if chrome_checkout[-1] != '/':
      chrome_checkout += '/'

    # rsync command format: rsync [options] from/ to
    # -r  recurse
    # -a  'archive', ensures that symbolic links etc. survive
    # -v  Show files being copied
    # --delete  Delete destination files not present in source directory
    # --delete-excluded  Delete destination files we've excluded
    # --exclude=dont/copy/me  Don't sync directory.
    options = []
    options.append('-rav')
    options.append('--delete')
    options.append('--delete-excluded')
    options.append('--exclude=.svn')
    options.append('--exclude=.git')
    options.extend(['--exclude=' + proj for proj in blacklist])
    command = ['rsync'] + options + [chrome_checkout, android_chrome_checkout]
    self.m.step('rsync chromium_org', command)

  def gyp_webview_step(self):
    gyp_webview_path = self.c.slave_chromium_in_android_path.join(
        'android_webview', 'tools', 'gyp_webview')
    self.m.step(
        'gyp_webview',
        self.with_lunch_command + [gyp_webview_path, 'all'],
        cwd=self.c.slave_chromium_in_android_path)

  def all_incompatible_directories_check_step(self):
    webview_license_tool_path = self.c.slave_chromium_in_android_path.join(
        'android_webview', 'tools', 'webview_licenses.py')
    self.m.python('incompatible directories', webview_license_tool_path,
                        ['all_incompatible_directories'])

  def compile_step(self, build_tool, step_name='compile', targets=None,
                   use_goma=True, src_dir=None, target_out_dir=None,
                   envsetup=None, defines=None, env=None, force_clobber=False):
    src_dir = src_dir or self.c.build_path
    target_out_dir = target_out_dir or self.c.slave_android_out_path
    envsetup = envsetup or self.with_lunch_command
    targets = targets or []
    env = env or {}
    if defines:
      defines_str = ' '.join('%s=%s' % kv for kv in defines.iteritems())
      targets.insert(0, defines_str)

    compiler_option = []
    compile_script = [self.m.path['build'].join('scripts', 'slave',
                                                'compile.py')]
    if use_goma and self.m.path.exists(self.m.path['build'].join('goma')):
      compiler_option = ['--compiler', 'goma',
                         '--goma-dir', self.m.path['build'].join('goma')]

    if self._clobber or force_clobber:
      compiler_option.append('--clobber')

    self.m.step(step_name,
                      envsetup +
                      compile_script +
                      targets +
                      ['--build-dir', self.m.path['slave_build']] +
                      ['--src-dir', src_dir] +
                      ['--build-tool', build_tool] +
                      ['--verbose'] +
                      compiler_option,
                      cwd=self.m.path['slave_build'],
                      env=env)

  def update_defaut_props_step(self, extra_properties):
    update_default_props_command = (
        [self.resource('update_default_props.py')] +
        ['%s=%s' % (k,v) for k,v in extra_properties.iteritems()])
    self.m.step('update /root/default.prop',
                       self.with_lunch_command + update_default_props_command)

