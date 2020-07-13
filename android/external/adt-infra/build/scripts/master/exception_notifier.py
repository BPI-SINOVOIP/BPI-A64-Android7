# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""A fixed version of MailNotifier which treats exception as failure."""

from buildbot.status import mail
from buildbot.status.results import FAILURE, EXCEPTION

class ExceptionNotifier(mail.MailNotifier):
  """Same as MailNotifier that treats EXCEPTION as failure."""
  def isMailNeeded(self, build, results):
    builder = build.getBuilder()
    if self.builders is not None and builder.name not in self.builders:
      return False
    if self.categories is not None and builder.category not in self.categories:
      return False
    if self.mode == 'failing' and results in [FAILURE, EXCEPTION]:
      return True
    return mail.MailNotifier.isMailNeeded(self, build, results)
