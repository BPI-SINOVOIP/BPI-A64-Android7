# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine.recipe_api import Property

DEPS = [
  'bot_update',
  'file',
  'gclient',
  'path',
  'platform',
  'properties',
  'python',
  'step',
  'test_utils',
]


def _GetHostToolSuffix(platform):
  if platform.is_linux:
    if platform.bits == 64:
      return 'linux64'
  elif platform.is_mac:
    return 'mac'
  elif platform.is_win:
    return 'win32'
  # TODO(davidben): Add other host platforms as needed.


def _GetHostExeSuffix(platform):
  if platform.is_win:
    return '.exe'
  return ''


def _GetHostCMakeArgs(platform, bot_utils):
  args = {}
  if platform.is_win:
    # CMake does not allow backslashes in this variable. It writes the string
    # out to a cmake file with configure_file() and fails to escape it
    # correctly.
    # TODO(davidben): Fix the bug in CMake upstream and remove this workaround.
    args['CMAKE_ASM_NASM_COMPILER'] = \
        str(bot_utils.join('yasm-win32.exe')).replace('\\', '/')
    args['PERL_EXECUTABLE'] = bot_utils.join('perl-win32', 'perl', 'bin',
                                             'perl.exe')
  return args


def _HasToken(buildername, token):
  # Builder names are a sequence of tokens separated by underscores.
  return '_' + token + '_' in '_' + buildername + '_'


def _AppendFlags(args, key, flags):
  if key in args:
    args[key] += ' ' + flags
  else:
    args[key] = flags


def _GetTargetCMakeArgs(buildername, bot_utils):
  args = {}
  if _HasToken(buildername, 'shared'):
    args['BUILD_SHARED_LIBS'] = '1'
  if _HasToken(buildername, 'linux32'):
    # 32-bit Linux is cross-compiled on the 64-bit Linux bot.
    args['CMAKE_SYSTEM_NAME'] = 'Linux'
    args['CMAKE_SYSTEM_PROCESSOR'] = 'x86'
    _AppendFlags(args, 'CMAKE_CXX_FLAGS', '-m32 -msse2')
    _AppendFlags(args, 'CMAKE_C_FLAGS', '-m32 -msse2')
    _AppendFlags(args, 'CMAKE_ASM_FLAGS', '-m32 -msse2')
  if _HasToken(buildername, 'noasm'):
    args['OPENSSL_NO_ASM'] = '1'
  if _HasToken(buildername, 'asan'):
    args['CMAKE_C_COMPILER'] = bot_utils.join('llvm-build', 'bin', 'clang')
    args['CMAKE_CXX_COMPILER'] = bot_utils.join('llvm-build', 'bin', 'clang++')
    _AppendFlags(args, 'CMAKE_CXX_FLAGS', '-fsanitize=address')
    _AppendFlags(args, 'CMAKE_C_FLAGS', '-fsanitize=address')
  return args


def _GetTargetMSVCPrefix(buildername, bot_utils):
  if _HasToken(buildername, 'win32'):
    return ['python', bot_utils.join('vs_env.py'), 'x86']
  if _HasToken(buildername, 'win64'):
    return ['python', bot_utils.join('vs_env.py'), 'x64']
  return []


def _GetTargetEnv(buildername, bot_utils):
  env = {}
  if _HasToken(buildername, 'asan'):
    env['ASAN_SYMBOLIZER_PATH'] = bot_utils.join('llvm-build', 'bin',
                                                 'llvm-symbolizer')
  return env


def _LogFailingTests(api, deferred):
  if not deferred.is_ok:
    error = deferred.get_error()
    if hasattr(error.result, 'test_utils'):
      r = error.result.test_utils.test_results
      p = error.result.presentation
      p.step_text += api.test_utils.format_step_text([
        ['unexpected_failures:', r.unexpected_failures.keys()],
      ])


PROPERTIES = {
  'buildername': Property(),
}


def RunSteps(api, buildername):
  # Sync and pull in everything.
  api.gclient.set_config('boringssl')
  api.bot_update.ensure_checkout(force=True)
  api.gclient.runhooks()

  # Set up paths.
  bot_utils = api.path['checkout'].join('util', 'bot')
  go_env = bot_utils.join('go', 'env.py')
  build_dir = api.path['checkout'].join('build')
  api.file.makedirs('mkdir', build_dir)
  runner_dir = api.path['checkout'].join('ssl', 'test', 'runner')

  # If building with MSVC, all commands must run with an environment wrapper.
  # This is necessary both to find the toolchain and the runtime dlls. Rather
  # than copy the runtime to every directory where a binary is installed, just
  # run the tests with the toolchain prefix as well.
  msvc_prefix = _GetTargetMSVCPrefix(buildername, bot_utils)

  # Build BoringSSL itself.
  cmake = bot_utils.join('cmake-' + _GetHostToolSuffix(api.platform), 'bin',
                         'cmake' + _GetHostExeSuffix(api.platform))
  cmake_args = _GetHostCMakeArgs(api.platform, bot_utils)
  cmake_args.update(_GetTargetCMakeArgs(buildername, bot_utils))
  api.python('cmake', go_env,
             msvc_prefix + [cmake, '-GNinja'] +
             ['-D%s=%s' % (k, v) for (k, v) in sorted(cmake_args.items())] +
             [api.path['checkout']],
             cwd=build_dir)
  api.python('ninja', go_env, msvc_prefix + ['ninja', '-C', build_dir])

  with api.step.defer_results():
    env = _GetTargetEnv(buildername, bot_utils)

    # Run the unit tests.
    deferred = api.python('unit tests', go_env,
                          msvc_prefix + ['go', 'run',
                                         api.path.join('util', 'all_tests.go'),
                                         '-json-output',
                                         api.test_utils.test_results()],
                          cwd=api.path['checkout'], env=env)
    _LogFailingTests(api, deferred)

    # Run the SSL tests.
    deferred = api.python('ssl tests', go_env,
                          msvc_prefix + ['go', 'test', '-pipe', '-json-output',
                                         api.test_utils.test_results()],
                          cwd=runner_dir, env=env)
    _LogFailingTests(api, deferred)


def GenTests(api):
  tests = [
    ('linux', api.platform('linux', 64)),
    ('linux_shared', api.platform('linux', 64)),
    ('linux32', api.platform('linux', 64)),
    ('linux_noasm_asan', api.platform('linux', 64)),
    ('linux32_noasm_asan', api.platform('linux', 64)),
    ('mac', api.platform('mac', 64)),
    ('win32', api.platform('win', 64)),
    ('win64', api.platform('win', 64)),
  ]
  for (buildername, host_platform) in tests:
    yield (
      api.test(buildername) +
      host_platform +
      api.properties.generic(mastername='client.boringssl',
                             buildername=buildername, slavename='slavename') +
      api.override_step_data('unit tests',
                             api.test_utils.canned_test_output(True)) +
      api.override_step_data('ssl tests',
                             api.test_utils.canned_test_output(True))
    )

  yield (
    api.test('failed_unit_tests') +
    api.platform('linux', 64) +
    api.properties.generic(mastername='client.boringssl', buildername='linux',
                           slavename='slavename') +
    api.override_step_data('unit tests',
                           api.test_utils.canned_test_output(False)) +
    api.override_step_data('ssl tests',
                           api.test_utils.canned_test_output(True))
  )

  yield (
    api.test('failed_ssl_tests') +
    api.platform('linux', 64) +
    api.properties.generic(mastername='client.boringssl', buildername='linux',
                           slavename='slavename') +
    api.override_step_data('unit tests',
                           api.test_utils.canned_test_output(True)) +
    api.override_step_data('ssl tests',
                           api.test_utils.canned_test_output(False))
  )
