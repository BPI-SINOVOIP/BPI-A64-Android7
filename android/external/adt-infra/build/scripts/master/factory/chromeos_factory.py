# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Set of utilities to build the chromium master."""

import os

from buildbot.steps import shell
from buildbot.interfaces import IRenderable
from buildbot.process.properties import Property, WithProperties

from master import chromium_step
from master.factory import build_factory
from master.factory import chromeos_build_factory
from master.factory import annotator_factory
from master.master_utils import ConditionalProperty


class _ChromiteRecipeFactoryFunc(object):
  """
  Factory generation function wrapper that supplies Chromite recipe defaults.

  This class is a callable wrapper to annotator_factory.AnnotatorFactory's
  BaseFactory method.

  This class painfully avoids subclassing annotator_factory.AnnotatorFactory in
  order to preserve its status as a terminal factory.
  """

  # The default Chromite recipe timeout.
  _CHROMITE_TIMEOUT = 9000
  # The default maximum build time.
  _DEFAULT_MAX_TIME = 16 * 60 * 60

  @classmethod
  def __call__(cls, factory_obj, recipe, *args, **kwargs):
    """Returns a factory object to use for Chromite annotator recipes.

    Args:
      factory_obj (annotator_factory.AnnotatorFactory) The annotator factory.
      recipe: The name of the recipe to invoke.
      debug (bool): If True, override default debug logic.
      args, kwargs: Positional / keyword arguments (see
          annotator_factory.AnnotatorFactory.BaseFactory).
    """
    kwargs.setdefault('timeout', cls._CHROMITE_TIMEOUT)

    factory_properties = kwargs.setdefault('factory_properties', {})
    # Set the 'cbb_debug' property if we're not running in a production master.
    if kwargs.pop('debug', False):
      factory_properties['cbb_debug'] = True
    kwargs.setdefault('max_time', cls._DEFAULT_MAX_TIME)
    return factory_obj.BaseFactory(recipe, *args, **kwargs)

# Callable instance of '_ChromiteRecipeFactoryFunc'.
ChromiteRecipeFactory = _ChromiteRecipeFactoryFunc()


class ChromiteFactory(object):
  """
  Create a build factory that runs a chromite script.

  This is designed mainly to utilize build scripts directly hosted in
  chromite.git.

  Attributes:
      script: the name of the chromite command to run (bin/<foo>)
      params: space-delimited string of parameters to pass to the cbuildbot
          command, or IRenderable.
      b_params:  An array of StepParameters to pass to the main command.
      timeout: Timeout in seconds for the main command. Default 9000 seconds.
      branch: git branch of the chromite repo to pull.
      chromite_repo: git repo for chromite toolset.
      factory: a factory with pre-existing steps to extend rather than start
          fresh.  Allows composing.
      use_chromeos_factory: indicates we want a default of a chromeos factory.
      slave_manager: whether we should manage the script area for the bot.
      chromite_patch: a url and ref pair (dict) to patch the checked out
          chromite. Fits well with a single change from a codereview, to use
          on one or more builders for realistic testing, or experiments.
      show_gclient_output: Set to False to hide the output of 'gclient sync'.
          Used by external masters to prevent leaking sensitive information,
          since both external and internal slaves use internal.DEPS/.
      max_time: Max overall time from the start before the command is killed.
  """
  _default_git_base = 'https://chromium.googlesource.com/chromiumos'
  _default_chromite = _default_git_base + '/chromite.git'
  _default_max_time = 16 * 60 * 60

  def __init__(self, script, params=None, b_params=None, timeout=9000,
               branch='master', chromite_repo=_default_chromite,
               factory=None, use_chromeos_factory=False, slave_manager=True,
               chromite_patch=None, show_gclient_output=True,
               max_time=_default_max_time):
    if chromite_patch:
      assert 'url' in chromite_patch and 'ref' in chromite_patch

    self.branch = branch
    self.chromite_patch = chromite_patch
    self.chromite_repo = chromite_repo
    self.timeout = timeout
    self.show_gclient_output = show_gclient_output
    self.slave_manager = slave_manager
    self.step_args = {}
    self.step_args['maxTime'] = max_time

    if factory:
      self.f_cbuild = factory
    elif use_chromeos_factory:
      # Suppresses revisions, at the moment.
      self.f_cbuild = chromeos_build_factory.BuildFactory()
    else:
      self.f_cbuild = build_factory.BuildFactory()

    self.chromite_dir = None
    self.add_bootstrap_steps()
    if script:
      self.add_chromite_step(script, params, b_params)

  def git_clear_and_checkout(self, repo, patch=None):
    """Clears and clones the given git repo. Returns relative path to repo.

    Args:
      repo: ssh: uri for the repo to be checked out
      patch: object with url and ref to patch on top
    """
    git_bin = '/usr/bin/git'
    def git(*args):
      return ' '.join((git_bin,) + args)

    git_checkout_dir = os.path.basename(repo).replace('.git', '')

    commands = []
    commands += ['rm -rf "%s"' % (git_checkout_dir,)]
    commands += [git('retry', 'clone', repo)]
    commands += ['cd "%s"' % (git_checkout_dir,)]

    # We ignore branches coming from buildbot triggers and rely on those in the
    # config.  This is because buildbot branch names do not match up with
    # cros builds.
    commands += [git('checkout', self.branch)]
    msg = 'Clear and Clone %s' % git_checkout_dir
    if patch:
      commands += [git('retry', 'pull', patch['url'], patch['ref'])]
      msg = 'Clear, Clone and Patch %s' % git_checkout_dir

    self.f_cbuild.addStep(shell.ShellCommand,
                          command=' && '.join(commands),
                          name=msg,
                          description=msg,
                          haltOnFailure=True)

    return git_checkout_dir

  def add_bootstrap_steps(self):
    """Bootstraps Chromium OS Build by syncing pre-requisite repositories.

    * gclient sync of /b
    * clearing of chromite
    * clean checkout of chromite
    """
    if self.slave_manager:
      build_slave_sync = ['gclient', 'sync', '--verbose', '--force',
                          '--delete_unversioned_trees']
      self.f_cbuild.addStep(shell.ShellCommand,
                            command=build_slave_sync,
                            name='update_scripts',
                            description='Sync buildbot slave files',
                            workdir='/b',
                            timeout=300,
                            want_stdout=self.show_gclient_output,
                            want_stderr=self.show_gclient_output)

    self.chromite_dir = self.git_clear_and_checkout(self.chromite_repo,
                                                    self.chromite_patch)

  def add_chromite_step(self, script, params, b_params, legacy=False):
    """Adds a step that runs a chromite command.

    Args:
      script:  Name of the script to run from chromite/bin.
      params: space-delimited string of parameters to pass to the cbuildbot
          command, or IRenderable.
      b_params:  An array of StepParameters.
      legacy:  Use a different directory for some legacy invocations.
    """
    script_subdir = 'buildbot' if legacy else 'bin'
    cmd = ['%s/%s/%s' % (self.chromite_dir, script_subdir, script)]
    if b_params:
      cmd.extend(b_params)
    if not params:
      pass
    elif isinstance(params, basestring):
      cmd.extend(params.split())
    elif IRenderable.providedBy(params):
      cmd += [params]
    else:
      raise TypeError("Unsupported 'params' type: %s" % (type(params),))

    self.f_cbuild.addStep(chromium_step.AnnotatedCommand,
                          command=cmd,
                          timeout=self.timeout,
                          name=script,
                          description=script,
                          usePTY=False,
                          **self.step_args)

  def get_factory(self):
    """Returns the produced factory."""
    return self.f_cbuild


class CbuildbotFactory(ChromiteFactory):
  """
  Create a build factory that runs the cbuildbot script.

  Attributes:
      params: space-delimited string of parameters to pass to the cbuildbot
          command, or IRenderable.
      script: name of the cbuildbot command.  Default cbuildbot.
      buildroot: buildroot to set. Default is /b/cbuild.
      dry_run: Don't push anything as we're running a test run.
      chrome_root: The place to put or use the chrome source.
      pass_revision: to pass the chrome revision desired into the build.
      legacy_chromite: If set, ask chromite to use an older cbuildbot directory.
      clobber: If True, force a clobber.
      *: anything else is passed to the base Chromite class.
  """

  def __init__(self,
               params,
               script='cbuildbot',
               buildroot='/b/cbuild',
               dry_run=False,
               chrome_root=None,
               pass_revision=None,
               legacy_chromite=False,
               clobber=False,
               **kwargs):
    super(CbuildbotFactory, self).__init__(None, None,
        use_chromeos_factory=not pass_revision, **kwargs)

    self.script = script
    self.chrome_root = chrome_root
    self.pass_revision = pass_revision
    self.legacy_chromite = legacy_chromite
    self.buildroot = buildroot
    self.dry_run = dry_run
    self.clobber = clobber
    assert params
    self.add_cbuildbot_step(params)


  def add_cbuildbot_step(self, params):
    self.add_chromite_step(self.script, params, self.compute_buildbot_params(),
                           legacy=self.legacy_chromite)


  def compute_buildbot_params(self):
    cmd = [
        WithProperties('--buildnumber=%(buildnumber)s'),
       ConditionalProperty(
           'buildroot',
           WithProperties('--buildroot=%(buildroot)s'),
           '--buildroot=%s' % self.buildroot),
       '--buildbot',
    ]

    # Add '--master-build-id' flag when build ID property is present
    cmd.append(
        ConditionalProperty(
            'master_build_id',
            WithProperties('--master-build-id=%(master_build_id)s'),
            [], # Will be flattened to nothing.
        )
    )

    if self.dry_run:
      cmd += ['--debug']

    if self.chrome_root:
      cmd.append('--chrome_root=%s' % self.chrome_root)

    if self.pass_revision:
      cmd.append(WithProperties('--chrome_version=%(revision)s'))

    # Clobber if forced or if the 'clobber' property is set.
    if self.clobber:
      cmd.append('--clobber')
    else:
      cmd.append(
          ConditionalProperty(
              'clobber',
              '--clobber',
              [], # Will be flattened to nothing.
          )
      )

    return cmd


class ChromitePlusFactory(ChromiteFactory):
  """
  Create a build factory that depends on chromite but runs a script from
  another repo.

  Arg Changes from Parent Class:
     script: Instead of pointing to a chromite script, points to a script in the
             additional repo specified. Chromite repo is also checked out and
             included in the PYTHONPATH to called script.
     chromite_plus_repo: Repo that script resides in.
     buildroot: buildroot to set. Default /b/cbuild.
     dry_run: Don't push anything as we're running a test run.
  """
  def __init__(self, script, chromite_plus_repo,
               buildroot='/b/cbuild',
               dry_run=False,
               *args, **dargs):
    # Initialize without running a script step similar to Cbuildbot factory.
    super(ChromitePlusFactory, self).__init__(None, **dargs)
    self.buildroot = buildroot
    self.dry_run = dry_run

    # Checkout and run the script.
    plus_checkout_dir = self.git_clear_and_checkout(chromite_plus_repo, None)
    self.add_chromite_plus_step(script, plus_checkout_dir)

  def add_chromite_plus_step(self, script, plus_checkout_dir):
    """Adds a step that runs the chromite_plus command.

    Args:
      script:  Name of the script to run from chromite_plus_repo.
      plus_checkout_dir: Directory that script resides in.
    """
    cmd = [os.path.join(plus_checkout_dir, script)]

    # Are we a debug build.
    if self.dry_run:
      cmd.extend(['--debug'])

    # Adds buildroot / clobber as last arg.
    cmd.append(WithProperties('%s' + self.buildroot, 'clobber:+--clobber '))

    self.f_cbuild.addStep(chromium_step.AnnotatedCommand,
                          command=cmd,
                          timeout=self.timeout,
                          name=script,
                          description=script,
                          usePTY=False,
                          env={'PYTHONPATH':'.'},
                          ** self.step_args)
