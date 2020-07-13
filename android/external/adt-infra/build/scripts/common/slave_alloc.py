# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Library to generate, maintain, and read static slave pool maps."""

import collections
import itertools
import json
import os


# Used to store a unique slave class. Consists of a name and an optional
# subclass.
SlaveClass = collections.namedtuple('SlaveClass',
    ('name', 'subtype'))

# Used to store a full slave configuration. Each slave class maps to a single
# slave configuration.
SlaveClassConfig = collections.namedtuple('SlaveClassConfig',
    ('cls', 'exclusive', 'pools', 'count'))

# Used to store associations between slave class and slaves. Also used to
# store persistent state.
SlaveState = collections.namedtuple('SlaveState',
    ('class_map', 'unallocated'))

# Used to store a slave map entry (see SlaveAllocator.GetSlaveMap())
SlaveMap = collections.namedtuple('SlaveMap',
    ('entries', 'unallocated'))

# Used to store a slave map entry (see SlaveAllocator.GetSlaveMap())
SlaveMapEntry = collections.namedtuple('SlaveMapEntry',
    ('classes', 'keys'))


class SlaveAllocator(object):
  """A slave pool management object.

  Pools:
  Individual slave machines are added to named Pools. A slave cannot be a member
  of more than one pool.

  Classes:
  A Class is a named allocation specification. When allocation is performed,
  the allocator maps Clases to Slaves. Therefore, a Class is the unit at which
  allocation is performed.

  Classes may optionally include a subtype string to help disambiguate them;
  for all practical purposes the subtype is just part of the name.

  The primary allocation function, '_GetSlaveClassMap', deterministically
  associates Classes with sets of slaves from the registered Pools according
  to their registered specification.

  Keys:
  Keys are a generalization of a builder name, representing a specific entity
  that requires slave allocation. While key names need not correspond to
  builder names, it is expected that they largely will.

  In order to be assigned, Keys gain Class membership via 'Join()'. A key may
  join multiple classes, and will be assigned the superset of slaves that those
  classes were assigned.

  State:
  The Allocator may optionally save and load its class allocation state to an
  external JSON file. This can be used to enforce class-to-slave mapping
  consistency (i.e., builder affinity).

  When a new allocation is performed, the SlaveAllocator's State is updated, and
  subsequent operations will prefer the previous layout.
  """

  # The default path to load/save state to, if none is specified.
  DEFAULT_STATE_PATH = 'slave_pool.json'

  def __init__(self):
    """Initializes a new slave pool instance."""
    self._state = None
    self._pools = {}
    self._classes = {}
    self._membership = {}
    self._all_slaves = {}

  def LoadStateDict(self, state_class_map=None):
    """Loads previous allocation state from a state dictionary.

    The state dictionary is structured:
      <class-name>: {
        <class-subtype>: [
          <slave>
          ...
        ],
        ...
      }

    Args:
      state_class_map (dict): A state class map dictionary. If None or empty,
          the current state will be cleared.
    """
    if not state_class_map:
      self._state = None
      return

    class_map = {}
    for class_name, class_name_entry in state_class_map.iteritems():
      for subtype, slave_list in class_name_entry.iteritems():
        cls = SlaveClass(name=class_name, subtype=subtype)
        class_map.setdefault(cls, []).extend(str(s) for s in slave_list)
    self._state = SlaveState(
        class_map=class_map,
        unallocated=None)

  def LoadState(self, path=None, enforce=True):
    """Loads slave pools from the store, replacing the current in-memory set.

    Args:
      path (str): If provided, the path to load from; otherwise,
          DEFAULT_STATE_PATH will be used.
      enforce (bool): If True, raise an IOError if the state file does not
          exist or a ValueError if it could not be loaded.
    """
    state = {}
    path = path or self.DEFAULT_STATE_PATH
    if not os.path.exists(path):
      if enforce:
        raise IOError("State path does not exist: %s" % (path,))
    try:
      with open(path or self.DEFAULT_STATE_PATH, 'r') as fd:
        state = json.load(fd)
    except (IOError, ValueError):
      if enforce:
        raise
    self.LoadStateDict(state.get('class_map'))

  def SaveState(self, path=None, list_unallocated=False):
    """Saves the current slave pool set to the store path.

    Args:
      path (str): The path of the state file. If None, use DEFAULT_STATE_PATH.
      list_unallocated (bool): Include an entry listing unallocated slaves.
          This entry will be ignored for operations, but can be useful when
          generating expectations.
    """
    state_dict = {}
    if self._state and self._state.class_map:
      class_map = state_dict['class_map'] = {}
      for sc, slave_list in self._state.class_map.iteritems():
        class_dict = class_map.setdefault(sc.name, {})
        subtype_dict = class_dict.setdefault(sc.subtype, [])
        subtype_dict.extend(slave_list)

    if list_unallocated:
      state_dict['unallocated'] = list(self._state.unallocated or ())

    with open(path or self.DEFAULT_STATE_PATH, 'w') as fd:
      json.dump(state_dict, fd, sort_keys=True, indent=2)

  def AddPool(self, name, *slaves):
    """Returns (str): The slave pool that was allocated (for chaining).

    Args:
      name (str): The slave pool name.
      slaves: Slave name strings that belong to this pool.
    """
    pool = self._pools.get(name)
    if not pool:
      pool = self._pools[name] = set()
    for slave in slaves:
      current_pool = self._all_slaves.get(slave)
      if current_pool is not None:
        if current_pool != name:
          raise ValueError("Cannot register slave '%s' with multiple pools "
                           "(%s, %s)" % (slave, current_pool, name))
      else:
        self._all_slaves[slave] = name
    pool.update(slaves)
    return name

  def GetPool(self, name):
    """Returns (frozenset): The contents of the named slave pol.

    Args:
      name (str): The name of the pool to query.
    Raises:
      KeyError: If the named pool doesn't exist.
    """
    pool = self._pools.get(name)
    if not pool:
      raise KeyError("No pool named '%s'" % (name,))
    return frozenset(pool)

  def Alloc(self, name, subtype=None, exclusive=True, pools=None, count=1):
    """Returns (SlaveClass): The SlaveClass that was allocated.

    Args:
      name (str): The base name of the class to allocate.
      subtype (str): If not None, the class subtype. This, along with the name,
          forms the class ID.
      exclusive (bool): If True, slaves allocated in this class may not be
          reused in other allocations.
      pools (iterable): If not None, constrain allocation to the named slave
          pools.
      count (int): The number of slaves to allocate for this class.
    """
    # Expand our pools.
    pools = set(pools or ())

    invalid_pools = pools.difference(set(self._pools.iterkeys()))
    assert not invalid_pools, (
        "Class references undefined pools: %s" % (sorted(invalid_pools),))

    cls = SlaveClass(name=name, subtype=subtype)
    config = SlaveClassConfig(cls=cls, exclusive=exclusive, pools=pools,
                              count=count)

    # Register this configuration.
    current_config = self._classes.get(cls)
    if current_config:
      # Duplicate allocations must match configurations.
      assert current_config == config, (
          "Class allocation doesn't match current for %s: %s != %s" % (
              cls, config, current_config))
    else:
      self._classes[cls] = config
    return cls

  def Join(self, key, slave_class):
    """Returns (SlaveClass): The 'slave_class' passed in (for chaining).

    Args:
      name (str): The key to join to the slave class.
      slave_class (SlaveClass): The slave class to join.
    """
    self._membership.setdefault(slave_class, set()).add(key)
    return slave_class

  def _GetSlaveClassMap(self):
    """Returns (dict): A dictionary mapping SlaveClass to slave tuples.

    Applies the current slave configuration to the allocator's slave class. The
    result is a dictionary mapping slave names to a tuple of keys belonging
    to that slave.
    """
    all_slaves = set(self._all_slaves.iterkeys())
    n_state = SlaveState(
        class_map={},
        unallocated=all_slaves.copy())
    lru = all_slaves.copy()
    exclusive = set()

    # The remaining classes to allocate. We keep this sorted for determinism.
    remaining_classes = sorted(self._classes.iterkeys())

    def allocate_slaves(config, slaves):
      class_slaves = n_state.class_map.setdefault(config.cls, [])
      if config.count:
        slaves = slaves[:max(0, config.count - len(class_slaves))]
      class_slaves.extend(slaves)
      if config.exclusive:
        exclusive.update(slaves)
      lru.difference_update(slaves)
      n_state.unallocated.difference_update(slaves)
      if len(lru) == 0:
        # Reload LRU.
        lru.update(all_slaves)
      return class_slaves

    def candidate_slaves(config, state):
      # Get slaves from the candidate pools.
      slaves = set()
      for pool in (config.pools or self._pools.iterkeys()):
        slaves.update(self._pools[pool])
      if state:
        slaves &= set(state.class_map.get(config.cls, ()))

      # Remove any slaves that have been exclusively allocated.
      slaves.difference_update(exclusive)

      # Deterministically prefer slaves that haven't been used over those that
      # have.
      return sorted(slaves & lru) + sorted(slaves.difference(lru))

    def apply_config(state=None, finite=False):
      incomplete_classes = []
      for slave_class in remaining_classes:
        if not self._membership.get(slave_class):
          # This slave class has no members; ignore it.
          continue
        config = self._classes[slave_class]

        if not (finite and config.count is None):
          slaves = candidate_slaves(config, state)
        else:
          # We're only applying finite configurations in this pass.
          slaves = ()
        allocated_slaves = allocate_slaves(config, slaves)
        if len(allocated_slaves) < max(config.count, 1):
          incomplete_classes.append(slave_class)

      # Return the set of classes that still need allocations.
      return incomplete_classes

    # If we have a state, apply as much as possible to the current
    # configuration. Note that anything can change between the saved state and
    # the current configuration, including:
    # - Slaves added / removed from pools.
    # - Slaves moved from one pool to another.
    # - Slave classes added/removed.
    if self._state:
      remaining_classes = apply_config(self._state)
    remaining_classes = apply_config(finite=True)
    remaining_classes = apply_config()

    # Are there any slave classes remaining?
    assert not remaining_classes, (
        "Failed to apply config for slave classes: %s" % (remaining_classes,))
    self._state = n_state
    return n_state

  def GetSlaveMap(self):
    """Returns (dict): A dictionary mapping slaves to lists of keys.
    """
    slave_map_entries = {}
    n_state = self._GetSlaveClassMap()
    for slave_class, slaves in n_state.class_map.iteritems():
      for slave in slaves:
        entry = slave_map_entries.get(slave)
        if not entry:
          entry = slave_map_entries[slave] = SlaveMapEntry(classes=set(),
                                                           keys=[])
        entry.classes.add(slave_class)
        entry.keys.extend(self._membership.get(slave_class, ()))

    # Convert SlaveMapEntry fields to immutable form.
    result = SlaveMap(
        entries={},
        unallocated=frozenset(n_state.unallocated))
    for k, v in slave_map_entries.iteritems():
      result.entries[k] = SlaveMapEntry(
          classes=frozenset(v.classes),
          keys=tuple(sorted(v.keys)))
    return result
