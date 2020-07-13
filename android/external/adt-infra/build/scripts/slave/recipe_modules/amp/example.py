
DEPS = [
  'amp',
  'json',
  'path',
  'properties',
]

BUILDERS = {
  'normal_example': {
    'device_name': ['SampleDevice'],
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'no_device_name': {
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'no_device_os': {
    'device_name': ['SampleDevice'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'split_example': {
    'device_name': ['SampleDevice'],
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'multiple_devices': {
    'device_name': ['SampleDevice0', 'SampleDevice1'],
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'multiple_device_oses': {
    'device_name': ['SampleDevice'],
    'device_os': ['SampleDeviceOS0', 'SampleDeviceOS1'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'device_oem': {
    'device_name': ['SampleDevice'],
    'device_oem': ['SampleDeviceOEM'],
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'minimum_device_os': {
    'device_minimum_os': 'MinimumSampleDeviceOS',
    'device_name': ['SampleDevice'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'underspecified_with_timeout': {
    'device_minimum_os': 'MinimumSampleDeviceOS',
    'device_name': ['SampleDevice0', 'SampleDevice1'],
    'device_timeout': 60,
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
  },
  'network_config_set': {
    'device_name': ['SampleDevice'],
    'device_os': ['SampleDeviceOS'],
    'api_address': '127.0.0.1',
    'api_port': '80',
    'api_protocol': 'http',
    'network_config': 8, # Sprint 4G 1 Bar
  },
}

AMP_RESULTS_BUCKET = 'chrome-amp-results'

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'buildername': Property(),
}

def RunSteps(api, buildername):
  api.amp.setup()
  api.amp.set_config('main_pool')

  builder = BUILDERS[buildername]
  api.path['checkout'] = api.path['slave_build'].join('src')

  gtest_test_id = api.amp.trigger_test_suite(
      'example_gtest_suite', 'gtest',
      api.amp.gtest_arguments('example_gtest_suite'),
      api.amp.amp_arguments(
          device_minimum_os=builder.get('device_minimum_os', None),
          device_name=builder.get('device_name', None),
          device_oem=builder.get('device_oem', None),
          device_os=builder.get('device_os', None),
          device_timeout=builder.get('device_timeout', None),
          api_address=builder.get('api_address', None),
          api_port=builder.get('api_port', None),
          api_protocol=builder.get('api_protocol', None),
          network_config=builder.get('network_config', None)))

  instrumentation_test_id = api.amp.trigger_test_suite(
      'example_instrumentation_suite', 'instrumentation',
      api.amp.instrumentation_test_arguments(
          apk_under_test='ApkUnderTest.apk',
          test_apk='TestApk.apk',
          additional_apks=['ExtraApk1.apk', 'ExtraApk2.apk'],
          isolate_file_path='isolate_file.isolate',
          annotation='SmallTest'),
      api.amp.amp_arguments(
          device_minimum_os=builder.get('device_minimum_os', None),
          device_name=builder.get('device_name', None),
          device_oem=builder.get('device_oem', None),
          device_os=builder.get('device_os', None),
          device_timeout=builder.get('device_timeout', None),
          api_address=builder.get('api_address', None),
          api_port=builder.get('api_port', None),
          api_protocol=builder.get('api_protocol', None),
          network_config=builder.get('network_config', None)))
 
  uirobot_test_id = api.amp.trigger_test_suite(
      'example_uirobot_suite', 'uirobot',
      api.amp.uirobot_arguments(app_under_test='Example.apk'),
      api.amp.amp_arguments(
          device_minimum_os=builder.get('device_minimum_os', None),
          device_name=builder.get('device_name', None),
          device_oem=builder.get('device_oem', None),
          device_os=builder.get('device_os', None),
          device_timeout=builder.get('device_timeout', None),
          api_address=builder.get('api_address', None),
          api_port=builder.get('api_port', None),
          api_protocol=builder.get('api_protocol', None),
          network_config=builder.get('network_config', None)))

  api.amp.collect_test_suite(
      'example_gtest_suite', 'gtest',
      api.amp.gtest_arguments('example_gtest_suite'),
      api.amp.amp_arguments(api_address=builder.get('api_address', None),
                            api_port=builder.get('api_port', None),
                            api_protocol=builder.get('api_protocol', None)),
      test_run_id=gtest_test_id)

  api.amp.upload_logcat_to_gs(
      AMP_RESULTS_BUCKET, 'example_gtest_suite', test_run_id=gtest_test_id)

  api.amp.collect_test_suite(
      'example_instrumentation_suite',
      'instrumentation',
      api.amp.instrumentation_test_arguments(
          apk_under_test='ApkUnderTest.apk',
          test_apk='TestApk.apk'),
      api.amp.amp_arguments(
          api_address=builder.get('api_address', None),
          api_port=builder.get('api_port', None),
          api_protocol=builder.get('api_protocol', None)),
      test_run_id=instrumentation_test_id)

  api.amp.upload_logcat_to_gs(
      AMP_RESULTS_BUCKET, 'example_instrumentation_suite',
      test_run_id=instrumentation_test_id)

  api.amp.collect_test_suite(
      'example_uirobot_suite', 'uirobot',
      api.amp.uirobot_arguments(),
      api.amp.amp_arguments(api_address=builder.get('api_address', None),
                            api_port=builder.get('api_port', None),
                            api_protocol=builder.get('api_protocol', None)),
      test_run_id=uirobot_test_id)

  api.amp.upload_logcat_to_gs(
      AMP_RESULTS_BUCKET, 'example_uirobot_suite', test_run_id=uirobot_test_id)

def GenTests(api):
  for buildername in BUILDERS:
    yield (
        api.test('%s_basic' % buildername) +
        api.properties.generic(buildername=buildername))

  yield (
      api.test('bad_device_data_from_trigger') +
      api.properties.generic(buildername='split_example') +
      api.override_step_data('[trigger] example_gtest_suite',
                             api.json.output({})))

  yield (
      api.test('bad_device_data_for_collect') +
      api.properties.generic(buildername='split_example') +
      api.override_step_data('[collect] load example_gtest_suite',
                             api.json.output({})))

  yield (
    api.test('bad_test_id_data_for_upload') +
    api.properties.generic(buildername='split_example') +
    api.override_step_data('[upload logcat] load example_gtest_suite data',
                           api.json.output({})))
