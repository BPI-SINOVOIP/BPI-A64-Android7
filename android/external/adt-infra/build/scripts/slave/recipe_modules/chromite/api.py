# Copyright 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import cgi
import re

from recipe_engine import recipe_api


class Config(object):
  _NONE = object()

  def __init__(self, name, *layers):
    self.name = name
    self.layers = layers

  def get(self, key, d=None):
    for l in self.layers:
      v = l.get(key, self._NONE)
      if v is not self._NONE:
        return v
    return d

  def __getitem__(self, key):
    v = self.get(key, self._NONE)
    if v is not self._NONE:
      return v
    raise KeyError('Invalid configuration key: %s' % (key,))

  def dict(self):
    d = {}
    for l in reversed(self.layers):
      d.update(l)
    return d


class ChromiteApi(recipe_api.RecipeApi):
  chromite_url = 'https://chromium.googlesource.com/chromiumos/chromite.git'
  manifest_url = 'https://chromium.googlesource.com/chromiumos/manifest.git'
  repo_url = 'https://chromium.googlesource.com/external/repo.git'

  _chromite_subpath = 'chromite'

  # The number of Gitiles attempts to make before giving up.
  _GITILES_ATTEMPTS = 10

  _MANIFEST_CMD_RE = re.compile(r'Automatic:\s+Start\s+([^\s]+)\s+([^\s]+)')
  _BUILD_ID_RE = re.compile(r'CrOS-Build-Id: (.+)')

  _cached_config = None

  @property
  def chromite_path(self):
    v = self.m.path.c.dynamic_paths.get('chromite')
    if v:
      return v
    return self.m.path['slave_build'].join(self._chromite_subpath)

  def _set_chromite_path(self, path):
    self.m.path.c.dynamic_paths['chromite'] = path

  def get_config_defaults(self):
    defaults = {
        'CBB_CONFIG': self.m.properties.get('cbb_config'),
        'CBB_BRANCH': self.m.properties.get('cbb_branch'),
        'CBB_DEBUG': self.m.properties.get('cbb_debug') is not None,
        'CBB_CLOBBER': 'clobber' in self.m.properties,
    }
    if 'buildnumber' in self.m.properties:
      defaults['CBB_BUILD_NUMBER'] = int(self.m.properties['buildnumber'])
    return defaults

  def _load_config_dump(self):
    if not self._cached_config:
      config_path = self.m.path.join(self.chromite_path,
                                     'cbuildbot', 'config_dump.json')
      step_result = self.m.json.read('read chromite config', config_path,
                                     add_json_log=False)
      self._cached_config = step_result.json.output
    return self._cached_config

  def load_config(self, name):
    c = self._load_config_dump()
    conf = c.get(name)
    if conf is None:
      return None

    layers = [conf]
    template = conf.get('_template')
    if template:
      layers.append(c['_templates'][template])
    default = c.get('_default')
    if default:
      layers.append(default)

    config = Config(name, *layers)

    presentation_dict = {name: config.dict()}
    self.m.step.active_result.presentation.logs['config'] = [
      self.m.json.dumps(presentation_dict, indent=2),
    ]
    return config

  def check_repository(self, repo_type_key, value):
    """Scans through registered repositories for a specified value.

    Args:
      repo_type_key (str): The key in the 'repositories' config to scan through.
      value (str): The value to scan for.
    Returns (bool): True if the value was found.
    """
    def remove_tail(v, tail):
      if v.endswith(tail):
        v = v[:-len(tail)]
      return v

    for v in self.c.repositories.get(repo_type_key, ()):
      if remove_tail(v, '.git') == remove_tail(value, '.git'):
        return True
    return False

  def load_manifest_config(self, repository, revision):
    """Loads manifest-specified parameters from the manifest commit.

    This method parses the commit log for the following information:
    - The branch to build (From the "Automatic": tag).
    - The build ID (from the CrOS-Build-Id: tag).

    Args:
      repository (str): The URL of the repository hosting the change.
      revision (str): The revision hash to load the build ID from.
    """
    commit_log = self.m.gitiles.commit_log(
        repository, revision, step_name='Fetch manifest config',
        attempts=self._GITILES_ATTEMPTS)
    result = self.m.step.active_result

    # Handle missing/invalid response.
    if not (commit_log and commit_log.get('message')):
      self.m.python.failing_step('Fetch manifest config failure',
                                 'Failed to fetch manifest config.')

    build_id = None
    loaded = []
    for line in reversed(commit_log['message'].splitlines()):
      # Automatic command?
      match = self._MANIFEST_CMD_RE.match(line)
      if match:
        self.c.chromite_branch = match.group(2)
        loaded.append('Chromite branch: %s' % (self.c.chromite_branch,))
        continue

      # Build ID?
      match = self._BUILD_ID_RE.match(line)
      if match:
        self.c.cbb.build_id = match.group(1)
        loaded.append('Build ID: %s' % (self.c.cbb.build_id,))
        continue
    if loaded:
      loaded.insert(0, '')
      result.presentation.step_text += '<br/>'.join(loaded)

  def gclient_config(self):
    """Generate a 'gclient' configuration to check out Chromite.

    Return: (config) A 'gclient' recipe module configuration.
    """
    cfg = self.m.gclient.make_config()
    soln = cfg.solutions.add()
    soln.name = 'chromite'
    soln.url = self.chromite_url
    # Set the revision using 'bot_update' remote branch:revision notation.
    # Omitting the revision uses HEAD.
    soln.revision = '%s:' % (self.c.chromite_branch,)
    return cfg

  def checkout(self, manifest_url=None, repo_url=None):
    manifest_url = manifest_url or self.manifest_url
    repo_url = repo_url or self.repo_url

    self.m.repo.init(manifest_url, '--repo-url', repo_url)
    self.m.repo.sync()

  @property
  def using_old_chromite_layout(self):
    """Returns (bool): True if we're using old Chromite checkout layout.
    """
    return self.c.chromite_branch in self.c.old_chromite_branches

  def cbuildbot(self, name, config, args=None, **kwargs):
    """Runs the cbuildbot command defined by the arguments.

    Args:
      name: (str) The name of the command step.
      config: (str) The name of the 'cbuildbot' configuration to invoke.
      args: (list) If not None, addition arguments to pass to 'cbuildbot'.

    Returns: (Step) The step that was run.
    """
    args = (args or [])[:]
    args.append(config)

    bindir = 'bin'
    if self.using_old_chromite_layout:
      bindir = 'buildbot'
    cmd = [self.chromite_path.join(bindir, 'cbuildbot')] + args
    return self.m.step(name, cmd, allow_subannotations=True, **kwargs)

  def cros_sdk(self, name, cmd, args=None, environ=None, **kwargs):
    """Return a step to run a command inside the cros_sdk."""
    chroot_cmd = self.chromite_path.join('bin', 'cros_sdk')

    arg_list = (args or [])[:]
    for t in sorted((environ or {}).items()):
      arg_list.append('%s=%s' % t)
    arg_list.append('--')
    arg_list.extend(cmd)

    self.m.python(name, chroot_cmd, arg_list, **kwargs)

  def setup_board(self, board, args=None, **kwargs):
    """Run the setup_board script inside the chroot."""
    self.cros_sdk('setup board',
                  ['./setup_board', '--board', board],
                  args, **kwargs)

  def build_packages(self, board, args=None, **kwargs):
    """Run the build_packages script inside the chroot."""
    self.cros_sdk('build packages',
                  ['./build_packages', '--board', board],
                  args, **kwargs)

  def configure(self, properties, config_map):
    """Loads configuration from build properties into this recipe config.

    Args:
      properties (Properties): The build properties object.
      config_map (dict): The configuration map to use.
    """
    master = properties['mastername']
    variant = properties.get('cbb_variant')

    # Set the master's base configuration.
    config_map = config_map.get(master, {})
    master_config = config_map.get('master_config')
    assert master_config, (
        "No 'master_config' configuration for '%s'" % (master,))
    self.set_config(master_config)

    # Apply any variant configurations.
    if variant:
      for config_name in config_map.get('variants', {}).get(variant, ()):
        self.apply_config(config_name)


    # If a config repo was specified, use it.
    if 'config_repo' in properties:
      self.c.cbb.config_repo = self.m.properties['config_repo']

  def run_cbuildbot(self, args=[]):
    self.checkout_chromite()
    self.run(args=args)

  def checkout_chromite(self):
    """Checks out the configured Chromite branch.
    """
    self.m.bot_update.ensure_checkout(
        gclient_config=self.gclient_config(),
        update_presentation=False,
        force=True)

    if self.c.chromite_branch and self.c.cbb.disable_bootstrap:
      # Chromite auto-detects which branch to build for based on its current
      # checkout. "bot_update" checks out remote branches, but Chromite requires
      # a local branch.
      #
      # Normally we'd bootstrap, but if we're disabling bootstrapping, we have
      # to checkout the local branch to let Chromite know which branch to build.
      self.m.git('checkout', self.c.chromite_branch,
          name=str('checkout chromite branch [%s]' % (self.c.chromite_branch)))
    self._set_chromite_path(self.m.path['checkout'])
    return self.chromite_path

  def run(self, args=[]):
    """Runs the configured 'cbuildbot' build.

    This workflow uses the registered configuration dictionary to make master-
    and builder-specific changes to the standard workflow.

    The specific workflow paths that are taken are also influenced by several
    build properties.

    TODO(dnj): When CrOS migrates away from BuildBot, replace property
        inferences with command-line parameters.

    This workflow:
    - Checks out the specified 'cbuildbot' repository.
    - Pulls information based on the configured change's repository/revision
      to pass to 'cbuildbot'.
    - Executes the 'cbuildbot' command.

    Args:
      args (list): If True, use this argument list as the base instead of the
          default, which is '--buildbot'.
    Returns: (Step) the 'cbuildbot' execution step.
    """
    # Assert correct configuration.
    assert self.c.cbb.config, 'An empty configuration was specified.'
    assert self.c.cbb.builddir, 'A build directory name must be specified.'

    # Load properties from the commit being processed. This requires both a
    # repository and revision to proceed.
    repository = self.m.properties.get('repository')
    revision = self.m.properties.get('revision')
    if repository and revision:
      # Pull more information from the commit if it came from certain known
      # repositories.
      if (self.c.use_chrome_version and
          self.check_repository('chromium', repository)):
        # If our change comes from a Chromium repository, add the
        # '--chrome_version' flag.
        self.c.cbb.chrome_version = self.m.properties['revision']
      if (self.c.read_cros_manifest and
          self.check_repository('cros_manifest', repository)):
        # This change comes from a manifest repository. Load configuration
        # parameters from the manifest command.
        self.load_manifest_config(repository, revision)

    buildroot = self.m.path['root'].join('cbuild', self.c.cbb.builddir)
    cbb_args = [
        '--buildroot', buildroot,
    ]
    if not args:
      cbb_args.append('--buildbot')
    if self.c.chromite_branch and not self.c.cbb.disable_bootstrap:
      cbb_args.extend(['--branch', self.c.chromite_branch])
    if self.c.cbb.build_number is not None:
      cbb_args.extend(['--buildnumber', self.c.cbb.build_number])
    if self.c.cbb.chrome_rev:
      cbb_args.extend(['--chrome_rev', self.c.cbb.chrome_rev])
    if self.c.cbb.debug:
      cbb_args.extend(['--debug'])
    if self.c.cbb.clobber:
      cbb_args.extend(['--clobber'])
    if self.c.cbb.chrome_version:
      cbb_args.extend(['--chrome_version', self.c.cbb.chrome_version])
    if self.c.cbb.config_repo:
      cbb_args.extend(['--config_repo', self.c.cbb.config_repo])

    # Set the build ID, if specified.
    if self.c.cbb.build_id:
      cbb_args.extend(['--master-build-id', self.c.cbb.build_id])

    # Add custom args, if there are any.
    cbb_args.extend(args)

    # Run cbuildbot.
    return self.cbuildbot(str('cbuildbot [%s]' % (self.c.cbb.config,)),
                          self.c.cbb.config,
                          args=cbb_args,
                          cwd=self.m.path['slave_root'])
