# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from recipe_engine import recipe_api


class Gatekeeper(recipe_api.RecipeApi):
  """Module for Gatekeeper NG."""
  def __call__(self, gatekeeper_json, gatekeeper_trees_json):
    config = self.m.json.read(
      'reading %s' % self.m.path.basename(gatekeeper_trees_json),
      gatekeeper_trees_json,
    ).json.output

    for tree_name, tree_args in config.iteritems():
      args = ['-v', '--json', gatekeeper_json]

      if tree_args.get('status-url'):
        args.extend(['--status-url', tree_args['status-url']])
      if tree_args.get('sheriff-url'):
        args.extend(['--sheriff-url', tree_args['sheriff-url']])
      if tree_args.get('set-status'):
        args.append('--set-status')
      if tree_args.get('open-tree'):
        args.append('--open-tree')
      if tree_args.get('track-revisions'):
        args.append('--track-revisions')
      if tree_args.get('revision-properties'):
        args.extend(['--revision-properties', tree_args['revision-properties']])
      if tree_args.get('build-db'):
        args.extend(['--build-db', tree_args['build-db']])
      if tree_args.get('password-file'):
        args.extend(['--password-file', tree_args['password-file']])
      if tree_args.get('use-project-email-address'):
        args.extend(['--default-from-email',
                     '%s-buildbot@chromium-build.appspotmail.com' % tree_name])
      elif tree_args.get('default-from-email'): # pragma: nocover
        args.extend(['--default-from-email', tree_args['default-from-email']])
      if tree_args.get('filter-domain'):
        args.extend(['--filter-domain', tree_args['filter-domain']])
      if tree_args.get('status-user'):
        args.extend(['--status-user', tree_args['status-user']])

      if tree_args.get('masters'):
        args.extend(tree_args['masters'])

      try:
        self.m.python(
          'gatekeeper: %s' % str(tree_name),
          self.m.path['build'].join('scripts', 'slave', 'gatekeeper_ng.py'),
          args,
        )
      except self.m.step.StepFailure:
        pass
