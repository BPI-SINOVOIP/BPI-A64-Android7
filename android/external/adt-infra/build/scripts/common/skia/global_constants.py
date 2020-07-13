# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Constants used in multiple places across Skia's infrastructure code."""


# TODO(borenet): Keep these in sync with those in the skia-buildbot repo:
# https://skia.googlesource.com/buildbot/+/3de60f3003e3/site_config/global_variables.json

CONFIG_COVERAGE = 'Coverage'
CONFIG_DEBUG = 'Debug'
CONFIG_RELEASE = 'Release'
VALID_CONFIGS = (CONFIG_COVERAGE, CONFIG_DEBUG, CONFIG_RELEASE)

GM_ACTUAL_FILENAME = 'actual-results.json'
GM_EXPECTATIONS_FILENAME = 'expected-results.json'
GM_IGNORE_TESTS_FILENAME = 'ignored-tests.txt'

GS_GM_BUCKET = 'chromium-skia-gm'
GS_SUMMARIES_BUCKET = 'chromium-skia-gm-summaries'

SKIA_REPO = 'https://skia.googlesource.com/skia.git'
INFRA_REPO = 'https://skia.googlesource.com/buildbot.git'

SERVICE_ACCOUNT_FILE = 'service-account-skia.json'
SERVICE_ACCOUNT_INTERNAL_FILE = 'service-account-skia-internal.json'
