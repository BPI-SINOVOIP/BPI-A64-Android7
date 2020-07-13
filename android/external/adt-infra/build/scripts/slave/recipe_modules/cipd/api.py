# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api

class CIPDApi(recipe_api.RecipeApi):
  """CIPDApi provides support for CIPD."""
  def __init__(self, *args, **kwargs):
    super(CIPDApi, self).__init__(*args, **kwargs)
    self.bin_path = None

  def install_client(self, step_name):
    bin_path = self.m.path['slave_build'].join('cipd')
    script_input = {
      'platform': self.platform_tag(),
      'bin_path': bin_path,
    }

    self.m.python(
        name=step_name,
        script=self.resource('bootstrap.py'),
        stdin=self.m.json.input(script_input))

    self.bin_path = bin_path.join('cipd')
    # TODO(seanmccullough): clean up older CIPD installations.

  def build(self, input_dir, output_package, package_name):
    self.m.step('build %s' % self.m.path.basename(package_name), [
        self.bin_path,
        'pkg-build',
        '-in', input_dir,
        '-json-output', self.m.json.output(),
        '-name', package_name,
        '-out', output_package,
    ], step_test_data=lambda: self.m.json.test_api.output({
        'result': {
            'package': package_name,
            'instance_id': 'fake-inst',
        },
    }))

  def register(self, package_path, service_account_credentials, *refs, **tags):
    package_name = self.m.path.basename(package_path)
    cmd = [
        self.bin_path,
        'pkg-register',
        '-json-output', self.m.json.output(),
        '-service-account-json', service_account_credentials,
    ]
    for ref in refs:
      cmd.extend(['-ref', ref])
    for tag, value in tags.iteritems():
      cmd.extend(['-tag', '%s:%s' % (tag, value)])
    cmd.append(package_path)
    self.m.step('register %s' % package_name, cmd,
                step_test_data=lambda: self.m.json.test_api.output({
                    'result': {
                        'package': package_name,
                        'instance_id': 'fake-inst',
                },
    }))

  def platform_tag(self):
    return '%s-%s' % (
        self.m.platform.name.replace('win', 'windows'),
        {
            32: '386',
            64: 'amd64',
        }[self.m.platform.bits],
    )

  def ensure_installed(self, root, pkgs, service_account_credentials=None):
    pkg_list = []
    for pkg_name in sorted(pkgs):
      pkg_spec = pkgs[pkg_name]
      pkg_list.append('%s %s' % (pkg_name, pkg_spec['version']))

    list_data = self.m.raw_io.input('\n'.join(pkg_list))
    bin_path = self.m.path['slave_build'].join('cipd')
    cmd = [bin_path.join('cipd'), 'ensure',
          '--root', root, '--list', list_data]
    if service_account_credentials:
      cmd.extend(['-service-account-json', service_account_credentials])
    self.m.step('ensure_installed', cmd)
