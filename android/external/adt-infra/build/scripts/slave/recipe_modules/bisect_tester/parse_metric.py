import math
import re


def _geom_mean_and_std_dev_from_histogram(histogram):  # pragma: no cover
  # Copied from: https://code.google.com/p/chromium/codesearch#chromium/build/scripts/common/chromium_utils.py&l=222
  # TODO(robertocn): Remove this code duplication from common.chromium_utils
  if not 'buckets' in histogram:
    return 0.0, 0.0
  count = 0
  sum_of_logs = 0
  for bucket in histogram['buckets']:
    if 'high' in bucket:
      bucket['mean'] = (bucket['low'] + bucket['high']) / 2.0
    else:
      bucket['mean'] = bucket['low']
    if bucket['mean'] > 0:
      sum_of_logs += math.log(bucket['mean']) * bucket['count']
      count += bucket['count']

  if count == 0:
    return 0.0, 0.0

  sum_of_squares = 0
  geom_mean = math.exp(sum_of_logs / count)
  for bucket in histogram['buckets']:
    if bucket['mean'] > 0:
      sum_of_squares += (bucket['mean'] - geom_mean) ** 2 * bucket['count']
  return geom_mean, math.sqrt(sum_of_squares / count)


def parse_chartjson_metric(results, metric):  # pragma: no cover
  """Interpret results-chart.json, finding the needed values.

  Args:
    results: The dictionary parsed from the chartjson file.
    metric: A pair of strings indicating chart and trace names.

  Returns:
    A triple (valid_values, values, all_results) where valid_values is a
    boolean, values is a list of floating point numbers, and all_results is a
    dictionary containing all the results originally in results_str.
  """
  def escape_chars(original_string):
    return re.sub( r'[\:|=/#&,]' , '_', original_string)

  chart_name, trace_name = metric
  if trace_name == chart_name:
    trace_name = 'summary'
  try:
    for chart in results['charts']:
      if escape_chars(chart) == chart_name:
        chart_name = chart  # Unescaping
        break
    for trace in results['charts'][chart_name]:
      if escape_chars(trace) == trace_name:
        trace_name = trace  # Unescaping
        break
    if (results['charts'][chart_name][trace_name]['type'] ==
        'list_of_scalar_values'):
      values = results['charts'][chart_name][trace_name]['values']
      if values:
        avg_value = [sum(values) / len(values)]
        return True, avg_value, results
    if results['charts'][chart_name][trace_name]['type'] == 'histogram':
      return True, [_geom_mean_and_std_dev_from_histogram(
          results['charts'][chart_name][trace_name])[0]], results
  except KeyError:  # e.g. metric not found
    pass
  return False, [], results


# The following has largely been copied from bisect_perf_regression.py
def parse_metric(out, err, metric):  # pragma: no cover
  """Tries to parse the output in RESULT line format or HISTOGRAM format.

  Args:
    metric: The metric as a list of [<trace>, <value>] string pairs.
    out, err: stdout and stderr that may contain the output to be parsed

  Returns:
    A pair (valid_values, values) where valid_values is a boolean and values is
    a list of floating point numbers.
  """
  text = (out or '') + (err or '')
  result = _parse_result_values_from_output(metric, text)
  if not result:
    result = _parse_histogram_values_from_output(metric, text)
  return bool(len(result)), result


# TODO: Deprecate the text parsing approach to get results in favor of
#       chartjson.
def _parse_result_values_from_output(metric, text):  # pragma: no cover
  """Attempts to parse a metric in the format RESULT <graph>: <trace>= ...

  Args:
    metric: The metric as a list of [<trace>, <value>] string pairs.
    text: The text to parse the metric values from.

  Returns:
    A list of floating point numbers found.
  """
  if not text:
    return [False, None]
  # Format is: RESULT <graph>: <trace>= <value> <units>
  metric_re = re.escape('RESULT %s: %s=' % (metric[0], metric[1]))

  # The log will be parsed looking for format:
  # <*>RESULT <graph_name>: <trace_name>= <value>
  single_result_re = re.compile(
      metric_re + r'\s*(?P<VALUE>[-]?\d*(\.\d*)?)')

  # The log will be parsed looking for format:
  # <*>RESULT <graph_name>: <trace_name>= [<value>,value,value,...]
  multi_results_re = re.compile(
      metric_re + r'\s*\[\s*(?P<VALUES>[-]?[\d\., ]+)\s*\]')

  # The log will be parsed looking for format:
  # <*>RESULT <graph_name>: <trace_name>= {<mean>, <std deviation>}
  mean_stddev_re = re.compile(
      metric_re +
      r'\s*\{\s*(?P<MEAN>[-]?\d*(\.\d*)?),\s*(?P<STDDEV>\d+(\.\d*)?)\s*\}')

  text_lines = text.split('\n')
  values_list = []
  for current_line in text_lines:
    # Parse the output from the performance test for the metric we're
    # interested in.
    single_result_match = single_result_re.search(current_line)
    multi_results_match = multi_results_re.search(current_line)
    mean_stddev_match = mean_stddev_re.search(current_line)
    if (not single_result_match is None and
        single_result_match.group('VALUE')):
      values_list += [single_result_match.group('VALUE')]
    elif (not multi_results_match is None and
          multi_results_match.group('VALUES')):
      metric_values = multi_results_match.group('VALUES')
      values_list += metric_values.split(',')
    elif (not mean_stddev_match is None and
          mean_stddev_match.group('MEAN')):
      values_list += [mean_stddev_match.group('MEAN')]

  list_of_floats = []
  # It seems the pythonic way to do this is to try to cast and catch the error.
  for v in values_list:
    try:
      list_of_floats.append(float(v))
    except ValueError:
      pass
  values_list = list_of_floats

  # If the metric is times/t, we need to sum the timings in order to get
  # similar regression results as the try-bots.
  metrics_to_sum = [
      ['times', 't'],
      ['times', 'page_load_time'],
      ['cold_times', 'page_load_time'],
      ['warm_times', 'page_load_time'],
  ]

  if metric in metrics_to_sum:
    if values_list:
      values_list = [reduce(lambda x, y: float(x) + float(y), values_list)]

  return values_list


def _parse_histogram_values_from_output(metric, text):  # pragma: no cover
  """Attempts to parse a metric in the format HISTOGRAM <graph: <trace>.

  Args:
    metric: The metric as a list of [<trace>, <value>] strings.
    text: The text to parse the metric values from.

  Returns:
    A list of floating point numbers found, [] if none were found.
  """
  metric_formatted = 'HISTOGRAM %s: %s= ' % (metric[0], metric[1])

  text_lines = text.split('\n')
  values_list = []

  for current_line in text_lines:
    if metric_formatted in current_line:
      current_line = current_line[len(metric_formatted):]

      try:
        histogram_values = eval(current_line)

        for b in histogram_values['buckets']:
          average_for_bucket = float(b['high'] + b['low']) * 0.5
          # Extends the list with N-elements with the average for that bucket.
          values_list.extend([average_for_bucket] * b['count'])
      except Exception:
        pass

  return values_list
