# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import re

# Note: The Perf Dashboard specifically for the string "Status: Positive" when
# deciding whether to CC authors on the bug.
_RESULTS_BANNER = """
===== BISECT JOB RESULTS =====
Status: %(status)s

Test Command: %(command)s
Test Metric: %(metric)s
Relative Change: %(change)s
Score: %(confidence).02f
Retested CL with revert: %(retest)s

"""

# When the bisect was aborted without a bisect failure the following template
# is used.
_ABORT_REASON_TEMPLATE = """
===== BISECTION ABORTED =====
The bisect was aborted because %(abort_reason)s
Please contact the the team (see below) if you believe this is in error.

Bug ID: %(bug_id)s

Test Command: %(command)s
Test Metric: %(metric)s
Good revision: %(good_revision)s
Bad revision: %(bad_revision)s

"""

# The perf dashboard specifically looks for the string
# "Author  : " to parse out who to cc on a bug. If you change the
# formatting here, please update the perf dashboard as well.
_RESULTS_REVISION_INFO = """
===== SUSPECTED CL(s) =====
Subject : %(subject)s
Author  : %(author)s
Commit description:
  %(commit_info)s
Commit  : %(cl)s
Date    : %(cl_date)s

"""

_REVISION_TABLE_TEMPLATE = """
===== TESTED REVISIONS =====
%(table)s

"""

_RESULTS_THANKYOU = """
| O O | Visit http://www.chromium.org/developers/speed-infra/perf-bug-faq
|  X  | for more information addressing perf regression bugs. For feedback,
| / \\ | file a bug with label Cr-Tests-AutoBisect.  Thank you!"""


_WARNINGS_TEMPLATE = """
===== WARNINGS =====
The following warnings were raised by the bisect job:

 * %(warnings)s

"""

_FAILED_INITIAL_CONFIDENCE_ABORT_REASON = (
    'The metric values for the initial "good" and "bad" revisions '
    'do not represent a clear regression.')

_DIRECTION_OF_IMPROVEMENT_ABORT_REASON = (
    'The metric values for the initial "good" and "bad" revisions match the '
    'expected direction of improvement. Thus, likely represent an improvement '
    'and not a regression.')

_REQUIRED_RESULTS_CONFIDENCE = 95.0


class BisectResults(object):

  def __init__(self, bisector, partial=False):
    """Create a new results object from a finished bisect job."""
    if not bisector.bisect_over and not partial:
      raise ValueError(
          'Invalid parameter, the bisect must be over by the time the '
          'BisectResults constructor is called')  # pragma: no cover
    self._bisector = bisector
    self.abort_reason = None
    self.culprit_cl_hash = None
    self.commit_info = None
    self.culprit_author = None
    self.culprit_subject = None
    self.culprit_date = None
    self.partial = partial
    self._gather_results()

  def as_string(self):
    return self._make_header() + self._make_body() + self._make_footer()

  def _make_header(self):
    # Unconditionally include this string at the top of the results since it is
    # used by the dashboard to separate the bisect results from other buildbot
    # output.
    if self.partial:
       return '---partial bisect results start here---\n'
    header = '---bisect results start here---\n'
    if not self.abort_reason:
      header += _RESULTS_BANNER % {
          'status': self.status,
          'command': self.command,
          'metric': self.metric,
          'change': self.relative_change,
          'confidence': self.results_confidence or 0,
          'retest': 'Not Implemented.'
      }
    else:
      header += _ABORT_REASON_TEMPLATE % {
          'abort_reason': self.abort_reason,
          'bug_id': self.bug_id,
          'command': self.command,
          'metric': self.metric,
          'good_revision': self.good_revision,
          'bad_revision': self.bad_revision
      }
    if self.warnings and not self.partial:
      header += _WARNINGS_TEMPLATE % {'warnings': '\n * '.join(self.warnings)}
    return header

  def _make_body(self):
    body = ''
    if self.culprit_cl_hash:
      body += _RESULTS_REVISION_INFO % {
          'subject': self.culprit_subject,
          'author': self.culprit_author,
          'cl_date': self.culprit_date,
          'commit_info': self.commit_info,
          'cl': self.culprit_cl_hash
      }
    body += self._compose_revisions_table()
    return body.encode('ascii', 'replace')

  def _make_footer(self):
    if self.partial:
      return '----End of partial results----'
    return _RESULTS_THANKYOU

  def _gather_results(self):
    # TODO(robertocn): Add viewcl link here.
    # TODO(robertocn): Merge this into constructor.
    bisector = self._bisector
    config = bisector.bisect_config

    # TODO(robertocn): Add platform here.
    self.relative_change = bisector.relative_change
    self.warnings = bisector.warnings
    self.command = config['command']
    self.metric = config['metric']
    self.bug_id = config.get('bug_id')
    self.good_revision = bisector.good_rev.commit_hash
    self.bad_revision = bisector.bad_rev.commit_hash
    self.results_confidence = bisector.results_confidence
    self.is_telemetry = ('tools/perf/run_' in self.command or
                         'tools\\perf\\run_' in self.command)
    self.culprit_cl_hash = None

    if self.is_telemetry:
      self.telemetry_command = re.sub(r'--browser=[^\s]+',
                                      '--browser=<bot-name>',
                                      self.command)

    self._set_culprit_attributes(bisector.culprit)

    if bisector.failed_initial_confidence:
      self.abort_reason = _FAILED_INITIAL_CONFIDENCE_ABORT_REASON
    elif bisector.failed_direction:
      self.abort_reason = _DIRECTION_OF_IMPROVEMENT_ABORT_REASON

    if self.partial:
      self.status = 'Partial Results only.'
    elif bisector.failed:
      self.status = 'Negative: Failed to bisect.'
    elif self.results_confidence > _REQUIRED_RESULTS_CONFIDENCE:
      self.status = 'Positive: A suspected commit was found.'
    else:
      self.status = ('Negative: Completed, but no culprit was found with '
                     'high confidence.')

  def _set_culprit_attributes(self, culprit):
    self.culprit_cl_hash = None
    api = self._bisector.api
    if culprit:
      self.culprit_cl_hash = (culprit.deps_revision or
                              culprit.commit_hash)
      if culprit.depot_name != 'chromium':  # pragma: no cover
        repo_path = api.m.path['slave_build'].join(culprit.depot['src'])
      else:
        repo_path = None
      culprit_info = api.query_revision_info(self.culprit_cl_hash, repo_path)
      self.culprit_subject = culprit_info['subject']
      self.culprit_author = (culprit_info['author'] + ', ' +
                             culprit_info['email'])
      self.commit_info = culprit_info['body']
      self.culprit_date = culprit_info['date']

  def _compose_revisions_table(self):
    def revision_row(r):
      result = [
          r.depot_name,
          r.deps_revision or 'r' + str(r.commit_pos),
          r.mean_value if r.mean_value is not None else 'N/A',
          r.std_err if r.std_err is not None else 'N/A',
          'good' if r.good else 'bad' if r.bad else 'unknown',
          '<-' if self._bisector.culprit == r else '',
      ]
      return map(str, result)

    headers_row = [[
        'Depot',
        'Revision',
        'Mean Value',
        'Std. Err.',
        'Good?',
        '',
    ]]
    revision_rows = [revision_row(r)
                     for r in self._bisector.revisions
                     if r.tested or r.aborted]
    all_rows = headers_row + revision_rows
    return _REVISION_TABLE_TEMPLATE % {'table': _pretty_table(all_rows)}


def _pretty_table(data):
  """Arrange a matrix of strings into an ascii table.

  This function was ripped off directly from somewhere in skia. It is
  inefficient and so, should be avoided for large data sets.

  Args:
    data (list): A list of lists of strings containing the data to tabulate. It
      is expected to be rectangular.

  Returns: A multi-line string containing the data arranged in a tabular manner.
  """
  result = ''
  column_widths = [0] * len(data[0])
  for row in data:
    column_widths = [max(longest_len, len(prop)) for
                     longest_len, prop in zip(column_widths, row)]
  for row in data:
    is_culprit_row = row[-1] == '<-'
    if is_culprit_row:
      result += '\n'
    for prop, width in zip(row, column_widths):
      result += prop.ljust(width + 1)
    result += '\n'
    if is_culprit_row:
      result += '\n'
  return result
