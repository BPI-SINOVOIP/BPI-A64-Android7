# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This recipe module allows triggering builds within the same master.

See README.md.
"""

from recipe_engine import recipe_api


class TriggerApi(recipe_api.RecipeApi):
  """APIs for triggering new builds."""

  def __init__(self, **kwargs):
    super(TriggerApi, self).__init__(**kwargs)

  def _port_from_properties_only(self, trigger_spec):
    """Convert from previous "properties-only" mode to trigger spec."""
    builder_name = trigger_spec.get('buildername')
    if not builder_name:
      return trigger_spec

    props = trigger_spec.copy()
    del props['buildername']
    return {
        'builder_name': builder_name,
        'properties': props,
    }

  def __call__(self, *trigger_specs, **kwargs):
    """Triggers new builds by builder names.

    Args:
      trigger_specs: a list of trigger dicts, where each dict specifies a build
        to trigger. Supported keys:
          builder_name (str): in BuildBot context, builder name
          bucket (str): buildbucket bucket where the triggered builds will be
            placed.
          properties (dict): build properties for a new build.
          buildbot_changes (list of dict): list of Buildbot changes to create.
            See below.
      name: name of the step. If not specified, it is generated
        automatically. Its format may change in future.

    Buildbot changes:
      buildbot_changes (a list of dicts) is a list of changes for the
      triggered builds. Each change is a dict with keys (all optional):
        author (str)
        revision
        revlink (str): link to a web view of the revision.
        comment
        when_timestamp (int): timestamp of the change, in seconds since Unix
          Epoch.
        branch
        category (str): Buildbot change category
        files (list of str): list of changed filenames
      The first change is used to populate source stamp properties.

    Examples:
      Basic:
        api.trigger({
            'builder_name': 'Release',
            'properties': {
                'my_prop': 123,
            },
        })

      Using BuildBucket:
        api.trigger({
            'builder_name': 'Release',
            'bucket': 'master.tryserver.chromium.linux',
            'properties': {
                'my_prop': 123,
            },
        })

      Create Buildbot changes:
        api.trigger({
            'builder_name': 'Release',
            'buildbot_changes': [{
                'author': 'someone@chromium.org',
                'branch': 'master',
                'files': ['a.txt.'],
                'comments': 'Refactoring',
                'revision': 'deadbeef',
                'revlink':
                  'http://chromium.googlesource.com/chromium/src/+/deadbeef',
                'when_timestamp': 1416859562,
            }]
        })
    """
    # Backward-compatibility:
    trigger_specs = map(self._port_from_properties_only, trigger_specs)

    builder_names = set()
    for trigger in trigger_specs:
      assert isinstance(trigger, dict), ('trigger spec must be a dict: %s'
                                          % (trigger,))
      builder_name = trigger.get('builder_name')
      assert builder_name, 'builder_name is missing: %s' % (trigger,)
      builder_names.add(builder_name)

    result = self.m.step(
        kwargs.get('name', 'trigger'),
        cmd=[],
        trigger_specs=trigger_specs,
    )
    if 'name' not in kwargs:
      result.presentation.step_text = "<br />".join(sorted(builder_names))
    return result
