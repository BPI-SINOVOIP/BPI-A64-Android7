# Copyright (c) 2013 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Utility class to generate and manage a factory to be passed to a
builder dictionary as the 'factory' member, for each builder in c['builders'].

Specifically creates a basic factory that will execute an arbirary annotator
script.
"""

from master.factory import annotator_commands
from master.factory import commands
from master.factory.build_factory import BuildFactory


class AnnotatorFactory(object):
  """Encapsulates data and methods common to all annotators."""

  def __init__(self, active_master=None):
    self._factory_properties = None
    self.active_master = active_master

  def BaseFactory(self, recipe=None, factory_properties=None, triggers=None,
                  timeout=3600, max_time=None):
    """The primary input for the factory is the |recipe|, which specifies the
    name of a recipe file to search for. The recipe file will fill in the rest
    of the |factory_properties|. This setup allows for major changes to factory
    properties to occur on slave-side without master restarts.

    NOTE: Please be very discerning with what |factory_properties| you pass to
    this method. Ideally, you will pass none, and that will be sufficient in the
    vast majority of cases. Think very carefully before adding any
    |factory_properties| here, as changing them will require a master restart.

    |recipe| is the name of the recipe to pass to annotated_run.  If omitted,
    annotated_run will attempt to look up the recipe from builders.pyl in the
    master.

    |timeout| refers to the maximum number of seconds a step should be allowed
    to run without output. After no output for |timeout| seconds, the step is
    forcibly killed.

    |max_time| refers to the maximum number of seconds a step should be allowed
    to run, regardless of output. After |max_time| seconds, the step is forcibly
    killed.
    """
    factory_properties = factory_properties or {}
    if recipe:
      factory_properties.update({'recipe': recipe})
    self._factory_properties = factory_properties
    factory = BuildFactory()
    factory.properties.update(self._factory_properties, 'AnnotatorFactory')
    cmd_obj = annotator_commands.AnnotatorCommands(
        factory, active_master=self.active_master)
    cmd_obj.AddAnnotatedScript(
      factory_properties, timeout=timeout, max_time=max_time)

    for t in triggers or []:
      factory.addStep(commands.CreateTriggerStep(
          t, trigger_copy_properties=['swarm_hashes']))

    return factory
