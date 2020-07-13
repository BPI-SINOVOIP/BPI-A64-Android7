# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

DEPS = [
  'bot_update',
  'chromium',
  'file',
  'gclient',
  'gsutil',
  'path',
  'properties',
  'python',
  'raw_io',
  'step',
]

INDEX_PACK_NAME = 'index_pack.zip'

def RunSteps(api):
  bucket_name = api.properties.get('bucket_name', 'chrome-codesearch')
  buildnumber = api.properties.get('buildnumber')

  spec = api.gclient.make_config('android_bare')
  spec.target_os = ['android']
  s = spec.solutions[0]
  s.name = api.properties['repo_name']
  s.url = api.properties['repo_url']
  s.revision = 'refs/remotes/origin/master'
  api.gclient.checkout(spec)
  # Many following steps depends on checkout being set as 'src'
  api.path['checkout'] = api.path['slave_build'].join('src')
  api.chromium.set_config('android_clang', BUILD_CONFIG='Debug',
                          TARGET_ARCH='arm', TARGET_BITS=32)
  api.chromium.runhooks()

  # Create the compilation database.
  debug_path = api.path['checkout'].join('out', api.chromium.c.BUILD_CONFIG)
  command = ['ninja', '-C', debug_path, 'all']
  # Add the parameters for creating the compilation database.
  command += ['-t', 'compdb', 'cc', 'cxx', 'objc', 'objcxx']
  result = api.step('generate compilation database', command,
                    stdout=api.raw_io.output())

  api.chromium.compile(targets=['all'])

  # Copy the created output to the correct directory. When running the clang
  # tool, it is assumed by the scripts that the compilation database is in the
  # out/Debug directory, and named 'compile_commands.json'.
  api.step('copy compilation database',
           ['cp', api.raw_io.input(data=result.stdout),
            debug_path.join('compile_commands.json')])

  # Compile the clang tool
  script_path = api.path.join('tools', 'clang', 'scripts', 'update.sh')
  api.step('compile translation_unit clang tool',
           [script_path, '--force-local-build', '--without-android',
            '--with-chrome-tools', 'translation_unit'],
           cwd=api.path['checkout'])

  # Run the clang tool
  args = [api.path['checkout'].join('third_party', 'llvm-build',
                                    'Release+Asserts', 'bin',
                                    'translation_unit'),
          debug_path, '--all']
  try:
    api.python(
        'run translation_unit clang tool',
        api.path['checkout'].join('tools', 'clang', 'scripts', 'run_tool.py'),
        args)
  except api.step.StepFailure as f:
    # For some files, the clang tool produces errors. This is a known issue,
    # but since it only affects very few files (currently 9), we ignore these
    # errors for now. At least this means we can already have cross references
    # support for the files where it works.
    api.step.active_result.presentation.step_text = f.reason_message()

  # Create the index pack
  api.python('create index pack',
             api.path['build'].join('scripts', 'slave', 'chromium',
                                    'package_index.py'),
             ['--path-to-compdb', debug_path.join('compile_commands.json'),
              '--path-to-archive-output', debug_path.join(INDEX_PACK_NAME)])

  # Remove the llvm-build directory, so that gclient runhooks will download
  # the pre-built clang binary and not use the locally compiled binary from
  # the 'compile translation_unit clang tool' step.
  api.file.rmtree('llvm-build',
                  api.path['checkout'].join('third_party', 'llvm-build'))

  # Upload the index pack
  api.gsutil.upload(
      name='upload index pack',
      source=debug_path.join(INDEX_PACK_NAME),
      bucket=bucket_name,
      dest='index_pack_%s.zip' % buildnumber
  )

  tarball_name = api.properties.get('tarball_name', 'src')
  tarball_name_with_build_number = '%s_%s.tar.bz2' % (tarball_name, buildnumber)
  tarball_name = tarball_name + '.tar.bz2'
  # Archive the source code. The checkout contains not only src, but also
  # src-internal.
  # TODO(akuegel): migrate this recipe to infra_internal repo.
  api.python('archive source',
             api.path['build'].join('scripts', 'slave',
                                    'archive_source_codesearch.py'),
             ['src', 'src-internal', '-f', tarball_name])

  api.gsutil.upload(
      name='upload source tarball',
      source=api.path['slave_build'].join(tarball_name),
      bucket=bucket_name,
      dest=tarball_name_with_build_number
  )

def GenTests(api):
  yield (
    api.test('codesearch_test') +
    api.step_data('generate compilation database',
                  stdout=api.raw_io.output('some compilation data')) +
    api.properties.generic(
      repo_name='src',
      repo_url='svn://svn-mirror.golo.chromium.org/chrome/trunk',
    )
  )

  yield (
    api.test('codesearch_test_fail_clang_tool') +
    api.step_data('generate compilation database',
                  stdout=api.raw_io.output('some compilation data')) +
    api.step_data('run translation_unit clang tool', retcode=2) +
    api.properties.generic(
      repo_name='src',
      repo_url='svn://svn-mirror.golo.chromium.org/chrome/trunk',
    )
  )

