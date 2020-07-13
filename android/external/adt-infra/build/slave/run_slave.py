#!/usr/bin/env python
# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

""" Initialize the environment variables and start the buildbot slave.
"""

import os
import shutil
import socket
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BUILD_DIR = os.path.dirname(SCRIPT_DIR)
ROOT_DIR = os.path.dirname(BUILD_DIR)
needs_reboot = False

# Temporarily add scripts to the path.  We do so in a more consistent
# manner below, but cannot keep it here because of our recursive calls.
sys.path.insert(0, os.path.join(os.path.dirname(SCRIPT_DIR), 'scripts'))
from common import chromium_utils
sys.path.pop(0)

# By default, the slave will identify itself to the master by its hostname.
# To override that, explicitly set a slavename here.
slavename = None

def remove_all_vars_except(dictionary, keep):
  """Remove all keys from the specified dictionary except those in !keep|"""
  for key in set(dictionary.keys()) - set(keep):
    dictionary.pop(key)


def Log(message):
  """Log a message using the Buildbot/Twisted log facility but only if it
  already imported.
  """
  log_mod = sys.modules.get('twisted.python.log')
  if log_mod:
    log_mod.msg(message)
  else:
    print message


def IssueReboot():
  """Issue reboot command according to platform type."""
  if sys.platform.startswith('win'):
    subprocess.call(['shutdown', '-r', '-f', '-t', '1'])
  elif sys.platform in ('darwin', 'posix', 'linux2'):
    subprocess.call(['sudo', 'shutdown', '-r', 'now'])
  else:
    raise NotImplementedError('Implement IssueReboot function '
                              'for %s' % sys.platform)


def SigTerm(*args):
  """Receive a SIGTERM and do nothing."""
  Log('SigTerm: Received SIGTERM, doing nothing.')


def UpdateSignals():
  """Override the twisted SIGTERM handler with our own.

  Ensure that the signal module is available and do nothing if it is not.
  """
  try:
    import signal
  except ImportError:
    Log('UpdateSignals: Warning: signal module unavailable -- '
        'not installing signal handlers.')
    return
  # Twisted installs a SIGTERM signal handler which tries to shut the system
  # down.  Use our own handler instead.
  Log('UpdateSignals: installed new SIGTERM handler')
  signal.signal(signal.SIGTERM, SigTerm)


def Sleep(desired_sleep):
  """Sleep for |desired_sleep| seconds.

  time.sleep() can return in less time than desired if the process receives
  a signal.  We expect that to happen when the shutdown we run causes the system
  to send a TERM signal to us.  When that happens, we need to ensure we go
  back to sleep for the remainder of the time that's left."""
  actual_sleep = 0
  while True:
    sleep_length = desired_sleep - actual_sleep
    start_time = int(time.time())
    Log('Sleep: Sleeping for %s seconds' % sleep_length)
    time.sleep(sleep_length)
    this_sleep = int(time.time()) - start_time
    Log('Sleep: Actually slept for %s seconds' % actual_sleep)
    if this_sleep < 0:
      Log('Sleep: Error, this_sleep was %d (less than zero)' % actual_sleep)
      break
    actual_sleep += this_sleep
    if actual_sleep >= desired_sleep:
      Log('Sleep: Finished sleeping, returning' % actual_sleep)
      break
    Log('Sleep: Awoke too early, sleeping again')


def Reboot():
  """Repeatedly try to reboot the system.

  On some platforms, like Mac, run_slave.py is launched by a system launcher
  agent.  In those cases, any children of this process will get killed by the
  agent if they're still running after we exit.  Our strategy to ensure our
  system reboot command is issued without getting killed before sudo runs
  shutdown for us is:

  1. Only call Reboot() from run_slave.py, and not from within the
     remote_shutdown method.  This allows us to avoid the possibility of
     the sudo being executed and being interrupted by the Twisted service
     as it shuts down due to a separate reactor.stop() call.  (This was just
     the only theory available for why some bots would not reboot at times.)

  2. In IssueReboot, use subprocess.call() instead of Popen() to ensure that
     run_slave.py doesn't exit at all when it calls Reboot().  This ensures that
     run_slave.py won't exit and trigger any cleanup routines by whatever
     launched run_slave.py.

  Since our strategy depends on Reboot() never returning, raise an exception
  if that should occur to make it clear in logs that an error condition is
  occurring somewhere.
  """
  Log('Reboot: Starting system reboot cycle')
  UpdateSignals()
  i = 0
  while True:
    Log('Reboot: Reboot cycle %d' % i)
    IssueReboot()
    Sleep(60)
    i += 1
  raise Exception('Reboot: Should not return but would have')


def HotPatchSlaveBuilder(is_testing):
  """We could override the SlaveBuilder class but it's way simpler to just
  hotpatch it."""
  Log('HotPatchSlaveBuilder(%s)' % is_testing)
  from buildslave.bot import Bot  # pylint: disable=F0401

  Bot.old_remote_shutdown = Bot.remote_shutdown
  def rebooting_remote_shutdown(self):
    """Set a reboot flag and stop the reactor so when the slave exits we reboot.

    Note that Buildbot masters >= 0.8.3 try to stop the slave in 2 ways.  First
    they try the new way which works for slaves running based on Buildbot 0.8.3
    or later.  If the slave is running something earlier than Buildbot 0.8.3,
    the master will then try the old way which will be what actually restarts
    those older slaves.

    For older slaves, the new way is always run first, and this causes the
    "No such method 'remote_shutdown'" message that is seen in the old slaves'
    twistd.log files.  This error is safe to ignore since the master will have
    immediately tried the old way and correctly restarted those slaves after
    that error is caught.
    """
    global needs_reboot
    if not is_testing:
      needs_reboot = True
      self.old_remote_shutdown()
    else:
      Log('Faking Reboot')
  Bot.new_remote_shutdown = rebooting_remote_shutdown
  Bot.remote_shutdown = Bot.new_remote_shutdown

  Bot.old_remote_setBuilderList = Bot.remote_setBuilderList
  def cleanup(self, wanted):
    retval = self.old_remote_setBuilderList(wanted)
    wanted_dirs = sorted(
        ['info', 'cert', '.svn', 'cache_dir', 'goma_cache'] +
        [r[1] for r in wanted])
    Log('Wanted directories: %s' % wanted_dirs)
    actual_dirs = sorted(
        i for i in os.listdir(self.basedir)
        if os.path.isdir(os.path.join(self.basedir, i)))
    Log('Actual directories: %s' % actual_dirs)
    for d in actual_dirs:
      # Delete build.dead directories.
      possible_build_dead = os.path.join(self.basedir, d, 'build.dead')
      if os.path.isdir(possible_build_dead):
        Log('Deleting unwanted directory %s' % possible_build_dead)
        if not is_testing:
          chromium_utils.RemoveDirectory(possible_build_dead)

      # Delete old slave directories.
      if d not in wanted_dirs:
        Log('Deleting unwanted directory %s' % d)
        if not is_testing:
          chromium_utils.RemoveDirectory(os.path.join(self.basedir, d))
    return retval
  Bot.new_remote_setBuilderList = cleanup
  Bot.remote_setBuilderList = Bot.new_remote_setBuilderList


def GetActiveMaster(slave_bootstrap, config_bootstrap, active_slavename):
  master_name = os.environ.get(
      'TESTING_MASTER', chromium_utils.GetActiveMaster(active_slavename))
  if not master_name:
    raise RuntimeError('*** Failed to detect the active master')
  slave_bootstrap.ImportMasterConfigs(master_name)
  if hasattr(config_bootstrap.Master, 'active_master'):
    # pylint: disable=E1101
    return config_bootstrap.Master.active_master
  if master_name and getattr(config_bootstrap.Master, master_name):
    master = getattr(config_bootstrap.Master, master_name)
    config_bootstrap.Master.active_master = master
    return master
  raise RuntimeError('*** Failed to detect the active master')


def GetRoot():
  if chromium_utils.IsWindows():
    return os.path.splitdrive(SCRIPT_DIR)[0]
  return '/'


def GetThirdPartyVersions(master):
  """Checks whether the master to which this slave belongs specifies particular
  versions of buildbot and twisted for its slaves to run.  If not specified,
  this function returns default values.
  """
  bb_ver = 'buildbot_slave_8_4'
  tw_ver = 'twisted_10_2'
  if master:
    bb_ver = getattr(master, 'buildslave_version', bb_ver)
    tw_ver = getattr(master, 'twisted_version', tw_ver)
  print 'Using %s and %s' % (bb_ver, tw_ver)
  return (bb_ver, tw_ver)


def error(msg):
  print >> sys.stderr, msg
  sys.exit(1)


def UseBotoPath():
  """Mutate the environment to reference the prefered gs credentials."""
  # Get the path to the boto file containing the password.
  boto_file = os.path.join(BUILD_DIR, 'site_config', '.boto')
  # If the boto file exists, make sure gsutil uses this boto file.
  if os.path.exists(boto_file):
    os.environ['AWS_CREDENTIAL_FILE'] = boto_file
    os.environ['BOTO_CONFIG'] = boto_file


def main():
  # Use adhoc argument parsing because of twisted's twisted argument parsing.
  # Change the current directory to the directory of the script.
  os.chdir(SCRIPT_DIR)
  # Windows bots need depot_tools in the ROOT_DIR
  depot_tools = os.path.join(ROOT_DIR, 'depot_tools')
  if sys.platform.startswith('win') and not os.path.isdir(depot_tools):
    error('You must put a copy of depot_tools in %s' % depot_tools)
  bot_password_file = os.path.normpath(
      os.path.join(BUILD_DIR, 'site_config', '.bot_password'))
  if not os.path.isfile(bot_password_file):
    error('You forgot to put the password at %s' % bot_password_file)

  # Make sure the current python path is absolute.
  old_pythonpath = os.environ.get('PYTHONPATH', '')
  os.environ['PYTHONPATH'] = ''
  for path in old_pythonpath.split(os.pathsep):
    if path:
      os.environ['PYTHONPATH'] += os.path.abspath(path) + os.pathsep

  # Update the python path.
  python_path = [
    os.path.join(BUILD_DIR, 'site_config'),
    os.path.join(BUILD_DIR, 'scripts'),
    os.path.join(BUILD_DIR, 'scripts', 'release'),
    os.path.join(BUILD_DIR, 'third_party'),
    os.path.join(BUILD_DIR, 'third_party', 'requests_1_2_3'),
    os.path.join(ROOT_DIR, 'build_internal', 'site_config'),
    os.path.join(ROOT_DIR, 'build_internal', 'symsrc'),
    SCRIPT_DIR,  # Include the current working directory by default.
  ]

  # Need to update sys.path prior to the following imports.
  sys.path = python_path + sys.path
  import slave.bootstrap
  import config_bootstrap
  active_slavename = chromium_utils.GetActiveSlavename()
  config_bootstrap.Master.active_slavename = active_slavename
  active_master = GetActiveMaster(
      slave.bootstrap, config_bootstrap, active_slavename)

  bb_ver, tw_ver = GetThirdPartyVersions(active_master)
  python_path.append(os.path.join(BUILD_DIR, 'third_party', bb_ver))
  python_path.append(os.path.join(BUILD_DIR, 'third_party', tw_ver))
  sys.path = python_path[-2:] + sys.path

  os.environ['PYTHONPATH'] = (
      os.pathsep.join(python_path) + os.pathsep + os.environ['PYTHONPATH'])

  os.environ['CHROME_HEADLESS'] = '1'
  os.environ['PAGER'] = 'cat'

  # Platform-specific initialization.

  if sys.platform.startswith('win'):
    # list of all variables that we want to keep
    env_var = [
        'APPDATA',
        'BUILDBOT_ARCHIVE_FORCE_SSH',
        'CHROME_HEADLESS',
        'CHROMIUM_BUILD',
        'COMMONPROGRAMFILES',
        'COMMONPROGRAMFILES(X86)',
        'COMMONPROGRAMW6432',
        'COMSPEC',
        'COMPUTERNAME',
        'DBUS_SESSION_BUS_ADDRESS',
        'DEPOT_TOOLS_GIT_BLEEDING',
        # TODO(maruel): Remove once everyone is on 2.7.5.
        'DEPOT_TOOLS_PYTHON_275',
        'DXSDK_DIR',
        'GIT_USER_AGENT',
        'HOME',
        'HOMEDRIVE',
        'HOMEPATH',
        'LOCALAPPDATA',
        'NUMBER_OF_PROCESSORS',
        'OS',
        'PATH',
        'PATHEXT',
        'PROCESSOR_ARCHITECTURE',
        'PROCESSOR_ARCHITEW6432',
        'PROCESSOR_IDENTIFIER',
        'PROGRAMFILES',
        'PROGRAMW6432',
        'PYTHONPATH',
        'SYSTEMDRIVE',
        'SYSTEMROOT',
        'TEMP',
        'TESTING_MASTER',
        'TESTING_MASTER_HOST',
        'TESTING_SLAVENAME',
        'TMP',
        'USERNAME',
        'USERDOMAIN',
        'USERPROFILE',
        'VS100COMNTOOLS',
        'VS110COMNTOOLS',
        'WINDIR',
    ]

    remove_all_vars_except(os.environ, env_var)

    # Extend the env variables with the chrome-specific settings.
    slave_path = [
        depot_tools,
        # Reuse the python executable used to start this script.
        os.path.dirname(sys.executable),
        os.path.join(os.environ['SYSTEMROOT'], 'system32'),
        os.path.join(os.environ['SYSTEMROOT'], 'system32', 'WBEM'),
        # Use os.sep to make this absolute, not relative.
        os.path.join(os.environ['SYSTEMDRIVE'], os.sep, 'Program Files',
                     '7-Zip'),
        # TODO(hinoka): Remove this when its no longer needed crbug.com/481695
        os.path.join(os.environ['SYSTEMDRIVE'], os.sep, 'cmake', 'bin'),
    ]
    # build_internal/tools contains tools we can't redistribute.
    tools = os.path.join(ROOT_DIR, 'build_internal', 'tools')
    if os.path.isdir(tools):
      slave_path.append(os.path.abspath(tools))
    os.environ['PATH'] = os.pathsep.join(slave_path)
    os.environ['LOGNAME'] = os.environ['USERNAME']

  elif sys.platform in ('darwin', 'posix', 'linux2'):
    # list of all variables that we want to keep
    env_var = [
        'CCACHE_DIR',
        'CHROME_ALLOCATOR',
        'CHROME_HEADLESS',
        'CHROME_VALGRIND_NUMCPUS',
        'DISPLAY',
        'DISTCC_DIR',
        'GIT_USER_AGENT',
        'HOME',
        'HOSTNAME',
        'HTTP_PROXY',
        'http_proxy',
        'HTTPS_PROXY',
        'LANG',
        'LOGNAME',
        'PAGER',
        'PATH',
        'PWD',
        'PYTHONPATH',
        'SHELL',
        'SSH_AGENT_PID',
        'SSH_AUTH_SOCK',
        'SSH_CLIENT',
        'SSH_CONNECTION',
        'SSH_TTY',
        'TESTING_MASTER',
        'TESTING_MASTER_HOST',
        'TESTING_SLAVENAME',
        'USER',
        'USERNAME',
    ]

    remove_all_vars_except(os.environ, env_var)
    slave_path = [
        os.path.join(os.path.expanduser('~'), 'slavebin'),
    ]
    # Git on mac is installed from git-scm.com/download/mac
    if sys.platform == 'darwin' and os.path.isdir('/usr/local/git/bin'):
      slave_path.append('/usr/local/git/bin')
    slave_path += [
        # Reuse the python executable used to start this script.
        os.path.dirname(sys.executable),
        '/usr/bin', '/bin', '/usr/sbin', '/sbin', '/usr/local/bin'
    ]
    os.environ['PATH'] = os.pathsep.join(slave_path)

  else:
    error('Platform %s is not implemented yet' % sys.platform)

  git_exe = 'git' + ('.bat' if sys.platform.startswith('win') else '')
  try:
    git_version = subprocess.check_output([git_exe, '--version'])
  except (OSError, subprocess.CalledProcessError) as e:
    Log('WARNING: Could not get git version information: %r' % e)
    git_version = '?'
  os.environ.setdefault('GIT_USER_AGENT', '%s git/%s %s' % (
      sys.platform, git_version.rstrip().split()[-1], socket.getfqdn()))
  # This may be redundant, unless this is imported and main is called.
  UseBotoPath()

  # This envrionment is defined only when testing the slave on a dev machine.
  is_testing = 'TESTING_MASTER' in os.environ
  HotPatchSlaveBuilder(is_testing)

  import twisted.scripts.twistd as twistd
  twistd.run()
  shutdown_file = os.path.join(os.path.dirname(__file__), 'shutdown.stamp')
  if os.path.isfile(shutdown_file):
    # If this slave is being shut down gracefully, don't reboot it.
    try:
      os.remove(shutdown_file)
      # Only disable reboot if the file can be removed.  Otherwise, the slave
      # might get stuck offline after every build.
      global needs_reboot
      needs_reboot = False
    except OSError:
      Log('Could not delete graceful shutdown signal file %s' % shutdown_file)

  # Although prevent_reboot_file looks similar to shutdown_file above, it is not
  # the same as shutdown.stamp is actually used by Buildbot to shut down the
  # slave process, while ~/no_reboot prevents rebooting the slave machine.
  prevent_reboot_file = os.path.join(os.path.expanduser('~'), 'no_reboot')
  if needs_reboot:
    if not os.path.isfile(prevent_reboot_file):
      # Send the appropriate system shutdown command.
      Reboot()
      # This line should not be reached.
    else:
      Log('Reboot was prevented by %s. Please remove the file and reboot the '
          'slave manually to resume automatic reboots.' % prevent_reboot_file)


if '__main__' == __name__:
  print 'TODO(navabi): Add repo sync to run_slave.py...'
  main()
