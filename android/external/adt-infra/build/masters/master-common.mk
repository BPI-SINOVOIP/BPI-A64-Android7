# -*- makefile -*-
# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Elements used to construct PYTHONPATH. These may be overridden by the
# including Makefile.
#
# For example: while we transition from buildbot 0.7.12 to buildbot 0.8.x ,
# some masters will override BUILDBOT_PATH in their local Makefiles.
TOPLEVEL_DIR ?= ../..
SCRIPTS_DIR ?= $(TOPLEVEL_DIR)/scripts

BUILDBOT8_PATH = $(shell $(SCRIPTS_DIR)/common/env.py -M "$(PWD)" echo)
BUILDBOT_PATH ?= $(BUILDBOT8_PATH)

# Define PYTHONPATH.
PYTHONPATH := $(BUILDBOT8_PATH)

include $(TOPLEVEL_DIR)/masters/master-common-rules.mk
