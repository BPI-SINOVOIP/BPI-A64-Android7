# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

GPU_ISOLATES = (
  'angle_unittests',
  'content_gl_tests',
  'gl_tests',
  'gl_unittests',
  'gles2_conform_test',
  'gpu_unittests',
  'tab_capture_end2end_tests',
  'telemetry_gpu_test',
)

# Until the media-only tests are extracted from content_unittests and
# these both can be run on the commit queue with
# --require-audio-hardware-for-testing, run them only on the FYI
# waterfall.
# TODO(jmadill): Run them on all GPU bots once stable
FYI_ONLY_GPU_ISOLATES = (
  'audio_unittests',
  'content_unittests',
)

# Until we have more capacity, run angle_end2end_tests only on the
# FYI waterfall and the ANGLE_TRYBOTS
ANGLE_TRYBOTS_GPU_ISOLATES = (
  'angle_end2end_tests',
)

# Slower tests that we should only run on Release configurations
# for the ANGLE try bots.
# TODO(jmadill): Also run on Linux and Mac bots.
WIN_ONLY_RELEASE_ONLY_ANGLE_TRYBOTS_GPU_ISOLATES = (
  'angle_deqp_gles2_tests',
)

FYI_GPU_ISOLATES = (
  FYI_ONLY_GPU_ISOLATES +
  ANGLE_TRYBOTS_GPU_ISOLATES
)

# This will be folded into the list above once ANGLE is running on all
# platforms.
WIN_AND_LINUX_ONLY_FYI_ONLY_GPU_ISOLATES = (
  'angle_deqp_gles2_tests',
)

WIN_ONLY_FYI_ONLY_GPU_ISOLATES = (
  'angle_deqp_gles3_tests',
)

# A list of all the Linux FYI isolates for testing
ALL_LINUX_FYI_GPU_ISOLATES = (
  GPU_ISOLATES +
  FYI_GPU_ISOLATES +
  WIN_AND_LINUX_ONLY_FYI_ONLY_GPU_ISOLATES
)

# A list of all Windows FYI isolates for testing
ALL_WIN_FYI_GPU_ISOLATES = (
  GPU_ISOLATES +
  FYI_GPU_ISOLATES +
  WIN_AND_LINUX_ONLY_FYI_ONLY_GPU_ISOLATES +
  WIN_ONLY_FYI_ONLY_GPU_ISOLATES
)

# A list of all ANGLE trybot isolates for testing
ALL_ANGLE_TRYBOT_GPU_ISOLATES = (
  GPU_ISOLATES +
  FYI_GPU_ISOLATES +
  WIN_AND_LINUX_ONLY_FYI_ONLY_GPU_ISOLATES
)
