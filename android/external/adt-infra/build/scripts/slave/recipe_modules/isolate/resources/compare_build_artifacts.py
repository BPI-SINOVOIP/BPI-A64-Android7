#!/usr/bin/env python
# Copyright 2014 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Compare the artifacts from two builds."""

import difflib
import json
import optparse
import os
import struct
import sys
import time

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


# List of files that are known to be non deterministic. This is a "temporary"
# workaround to find regression on the deterministic builders.
#
# PNaCl general bug: https://crbug.com/429358
#
# TODO(sebmarchand): Remove this once all the files are deterministic.
WHITELIST = {
  # https://crbug.com/383340
  'android': {
    'd8',
    'mksnapshot',
  },

  # https://crbug.com/330263
  'linux': {
    # Completed.
  },

  # https://crbug.com/330262
  'mac': {
    'accessibility_unittests',
    'accessibility_unittests.isolated',
    'angle_unittests',
    'app_list_demo',
    'app_list_unittests',
    'app_list_unittests.isolated',
    'app_shell_unittests',
    'app_shell_unittests.isolated',
    'ar_sample_test_driver',
    'audio_unittests',
    'audio_unittests.isolated',
    'base_i18n_perftests',
    'base_perftests',
    'base_unittests',
    'base_unittests.isolated',
    'bitmaptools',
    'blink_heap_unittests',
    'blink_platform_unittests',
    'boringssl_aead_test',
    'boringssl_aes_test',
    'boringssl_base64_test',
    'boringssl_bio_test',
    'boringssl_bn_test',
    'boringssl_bytestring_test',
    'boringssl_cipher_test',
    'boringssl_cmac_test',
    'boringssl_constant_time_test',
    'boringssl_dh_test',
    'boringssl_digest_test',
    'boringssl_dsa_test',
    'boringssl_ec_test',
    'boringssl_ecdsa_test',
    'boringssl_err_test',
    'boringssl_evp_extra_test',
    'boringssl_evp_test',
    'boringssl_example_mul',
    'boringssl_gcm_test',
    'boringssl_hkdf_test',
    'boringssl_hmac_test',
    'boringssl_lhash_test',
    'boringssl_pbkdf_test',
    'boringssl_pkcs12_test',
    'boringssl_pkcs7_test',
    'boringssl_pkcs8_test',
    'boringssl_poly1305_test',
    'boringssl_pqueue_test',
    'boringssl_refcount_test',
    'boringssl_rsa_test',
    'boringssl_ssl_test',
    'boringssl_tab_test',
    'boringssl_thread_test',
    'boringssl_unittests',
    'boringssl_v3name_test',
    'browser_tests',
    'browser_tests.isolated',
    'build_utf8_validator_tables',
    'cacheinvalidation_unittests',
    'cacheinvalidation_unittests.isolated',
    'cast_benchmarks',
    'cast_h264_vt_encoder_unittests',
    'cast_receiver_app',
    'cast_sender_app',
    'cast_simulator',
    'cast_unittests',
    'cast_unittests.isolated',
    'cc_blink_unittests',
    'cc_perftests',
    'cc_unittests',
    'cc_unittests.isolated',
    'check_example',
    'chrome_app_unittests',
    'chrome.isolated',
    'chromedriver',
    'chromedriver_tests',
    'chromedriver_unittests',
    'chromedriver_unittests.isolated',
    'chromoting_test_driver',
    'clear_system_cache',
    'cloud_print_unittests',
    'codesighs',
    'components_browsertests',
    'components_browsertests.isolated',
    'components_perftests',
    'components_unittests',
    'components_unittests.isolated',
    'compositor_unittests',
    'compositor_unittests.isolated',
    'content_browsertests',
    'content_browsertests.isolated',
    'content_gl_benchmark',
    'content_gl_tests',
    'content_perftests',
    'content_unittests',
    'content_unittests.isolated',
    'courgette',
    'courgette_fuzz',
    'courgette_minimal_tool',
    'courgette_unittests',
    'courgette_unittests.isolated',
    'crash_cache',
    'crash_inspector',
    'crashpad_handler',
    'crl_set_dump',
    'crypto_unittests',
    'crypto_unittests.isolated',
    'd8',
    'device_unittests',
    'device_unittests.isolated',
    'display_unittests',
    'dns_fuzz_stub',
    'dump_cache',
    'dump_syms',
    'env_chromium_unittests',
    'events_unittests',
    'events_unittests.isolated',
    'exif.so',
    'extensions_browsertests',
    'extensions_browsertests.isolated',
    'extensions_unittests',
    'extensions_unittests.isolated',
    'ffmpeg_regression_tests',
    'ffmpegsumo.so',
    'filter_fuzz_stub',
    'frame_analyzer',
    'gcapi_example',
    'gcm_unit_tests',
    'gcm_unit_tests.isolated',
    'gdig',
    'generate_barcode_video',
    'generate_test_gn_data',
    'generate_timecode_audio',
    'genmacro',
    'genmodule',
    'genperf',
    'genstring',
    'genversion',
    'get_server_time',
    'gfx_unittests',
    'gin_shell',
    'gin_unittests',
    'gl_tests',
    'gl_unittests',
    'gl_unittests.isolated',
    'gles2_conform_support',
    'gles2_conform_test',
    'gn',
    'gn_unittests',
    'gn_unittests.isolated',
    'goobsdiff',
    'goobspatch',
    'google_apis_unittests',
    'google_apis_unittests.isolated',
    'gpu_perftests',
    'gpu_unittests',
    'gpu_unittests.isolated',
    'hpack_example_generator',
    'hpack_fuzz_mutator',
    'hpack_fuzz_wrapper',
    'image_diff',
    'image_operations_bench',
    'infoplist_strings_tool',
    'interactive_ui_tests',
    'interactive_ui_tests.isolated',
    'ipc_mojo_perftests',
    'ipc_mojo_unittests',
    'ipc_perftests',
    'ipc_tests',
    'ipc_tests.isolated',
    'jingle_unittests',
    'jingle_unittests.isolated',
    'jtl_compiler',
    'khronos_glcts_test',
    'layout_test_helper',
    'libaddressinput_unittests',
    'libclearkeycdm.dylib',
    'libcommand_buffer_gles2.dylib',
    'liblzma_decompress.dylib',
    'libmojo_public_test_support.dylib',
    'libphonenumber_unittests',
    'load_library_perf_tests',
    'macviews_interactive_ui_tests',
    'maptsvdifftool',
    'mcs_probe',
    'media_perftests',
    'media_unittests',
    'media_unittests.isolated',
    'message_center_unittests',
    'message_center_unittests.isolated',
    'midi_unittests',
    'midi_unittests.isolated',
    'minidump_stackwalk',
    'mksnapshot',
    'mojo_common_unittests',
    'mojo_common_unittests.isolated',
    'mojo_js_integration_tests',
    'mojo_js_unittests',
    'mojo_message_pipe_perftests',
    'mojo_public_application_unittests',
    'mojo_public_bindings_perftests',
    'mojo_public_bindings_unittests',
    'mojo_public_bindings_unittests.isolated',
    'mojo_public_environment_unittests',
    'mojo_public_environment_unittests.isolated',
    'mojo_public_system_perftests',
    'mojo_public_system_unittests',
    'mojo_public_system_unittests.isolated',
    'mojo_public_utility_unittests',
    'mojo_public_utility_unittests.isolated',
    'mojo_system_unittests',
    'nacl_loader_unittests',
    'nacl_loader_unittests.isolated',
    'net_perftests',
    'net_unittests',
    'net_unittests.isolated',
    'net_watcher',
    'nm2tsv',
    'osmesa.so',
    'pdfium_diff',
    'pdfium_test',
    'pdfsqueeze',
    'peerconnection_server',
    'pepper_hash_for_uma',
    'performance_browser_tests',
    'ppapi_perftests',
    'ppapi_unittests',
    'printing_unittests',
    'printing_unittests.isolated',
    'protoc',
    'qcms_test',
    'quic_client',
    'quic_server',
    're2c',
    'remoting_perftests',
    'remoting_start_host',
    'remoting_unittests',
    'remoting_unittests.isolated',
    'rgba_to_i420_converter',
    'rlz_id',
    'rlz_unittests',
    'run_sync_testserver',
    'run_testserver',
    'sandbox_mac_unittests',
    'sandbox_mac_unittests.isolated',
    'skia_runner',
    'skia_unittests',
    'skia_unittests.isolated',
    'snapshot_unittests',
    'sql_unittests',
    'sql_unittests.isolated',
    'stress_cache',
    'symupload',
    'sync_client',
    'sync_integration_tests',
    'sync_integration_tests.isolated',
    'sync_listen_notifications',
    'sync_performance_tests',
    'sync_unit_tests',
    'sync_unit_tests.isolated',
    'telemetry_gpu_unittests.isolated',
    'tld_cleanup',
    'tls_edit',
    'udp_proxy',
    'ui_base_unittests',
    'ui_touch_selection_unittests',
    'ui_touch_selection_unittests.isolated',
    'unit_tests',
    'unit_tests.isolated',
    'url_unittests',
    'url_unittests.isolated',
    'views_examples_with_content_exe',
    'views_unittests',
    'webkit_unit_tests',
    'wifi_test',
    'wtf_unittests',
    'xz',
    'xzdec',
    'yasm',
},

  # https://crbug.com/330260
  'win': {
    'accessibility_unittests.exe',
    'accessibility_unittests.isolated',
    'angle_end2end_tests.exe',
    'angle_unittests.exe',
    'angle_perftests.exe',
    'app_list_demo.exe',
    'app_list_unittests.exe',
    'app_list_unittests.isolated',
    'app_shell.exe',
    'app_shell_unittests.exe',
    'app_shell_unittests.isolated',
    'ar_sample_test_driver.exe',
    'ash_shell.exe',
    'ash_unittests.exe',
    'ash_unittests.isolated',
    'audio_unittests.exe',
    'audio_unittests.isolated',
    'base_i18n_perftests.exe',
    'base_perftests.exe',
    'base_unittests.exe',
    'base_unittests.isolated',
    'blink_heap_unittests.exe',
    'blink_platform_unittests.exe',
    'browser_tests.exe',
    'browser_tests.isolated',
    'cast_unittests.exe',
    'cast_unittests.isolated',
    'cc_blink_unittests.exe',
    'cc_perftests.exe',
    'cc_unittests.exe',
    'cc_unittests.isolated',
    'chrome.dll',
    'chrome.exe',
    'chrome.isolated',
    'chrome_app_unittests.exe',
    'chrome_child.dll',
    'chrome_elf_unittests.exe',
    'chrome_watcher.dll',
    'chromedriver.exe',
    'chromedriver_tests.exe',
    'chromoting_test_driver.exe',
    'clearkeycdm.dll',
    'cloud_print_service.exe',
    'cloud_print_service_config.exe',
    'cloud_print_unittests.exe',
    'components_browsertests.exe',
    'components_browsertests.isolated',
    'components_unittests.exe',
    'components_unittests.isolated',
    'compositor_unittests.exe',
    'compositor_unittests.isolated',
    'content_browsertests.exe',
    'content_browsertests.isolated',
    'content_gl_benchmark.exe',
    'content_gl_tests.exe',
    'content_perftests.exe',
    'content_shell.exe',
    'content_unittests.exe',
    'content_unittests.isolated',
    'courgette64.exe',
    'crash_service64.exe',
    'crypto_unittests.exe',
    'crypto_unittests.isolated',
    'd8.exe',
    'delegate_execute.exe',
    'delegate_execute_unittests.exe',
    'device_unittests.exe',
    'device_unittests.isolated',
    'events_unittests.exe',
    'events_unittests.isolated',
    'extensions_browsertests.exe',
    'extensions_browsertests.isolated',
    'extensions_unittests.exe',
    'extensions_unittests.isolated',
    'gcapi_test.exe',
    'gcm_unit_tests.exe',
    'gcm_unit_tests.isolated',
    'gcp20_device.exe',
    'gcp20_device_unittests.exe',
    'gcp_portmon64.dll',
    'get_server_time.exe',
    'gfx_unittests.exe',
    'gin_shell.exe',
    'gin_unittests.exe',
    'gl_unittests.exe',
    'gl_unittests.isolated',
    'google_apis_unittests.exe',
    'google_apis_unittests.isolated',
    'gpu_perftests.exe',
    'gpu_unittests.exe',
    'gpu_unittests.isolated',
    'interactive_ui_tests.exe',
    'interactive_ui_tests.isolated',
    'ipc_mojo_perftests.exe',
    'ipc_mojo_unittests.exe',
    'ipc_perftests.exe',
    'ipc_tests.exe',
    'ipc_tests.isolated',
    'jingle_unittests.exe',
    'jingle_unittests.isolated',
    'keyboard_unittests.exe',
    'libaddressinput_unittests.exe',
    'media_unittests.exe',
    'media_unittests.isolated',
    'metro_driver.dll',
    'midi_unittests.exe',
    'midi_unittests.isolated',
    'mini_installer.exe',
    'mksnapshot.exe',
    'mock_nacl_gdb.exe',
    'mojo_js_integration_tests.exe',
    'mojo_js_unittests.exe',
    'mojo_message_pipe_perftests.exe',
    'mojo_public_bindings_perftests.exe',
    'mojo_public_bindings_unittests.exe',
    'mojo_public_bindings_unittests.isolated',
    'mojo_public_environment_unittests.exe',
    'mojo_public_environment_unittests.isolated',
    'mojo_public_system_perftests.exe',
    'mojo_public_system_unittests.exe',
    'mojo_public_system_unittests.isolated',
    'mojo_public_utility_unittests.exe',
    'mojo_public_utility_unittests.isolated',
    'mojo_system_unittests.exe',
    'nacl_loader_unittests.exe',
    'nacl_loader_unittests.isolated',
    'net_perftests.exe',
    'net_unittests.exe',
    'net_unittests.isolated',
    'np_test_netscape_plugin.dll',
    'npapi_test_plugin.dll',
    'pdfium_test.exe',
    'peerconnection_server.exe',
    'performance_browser_tests.exe',
    'ppapi_perftests.exe',
    'ppapi_unittests.exe',
    'printing_unittests.exe',
    'printing_unittests.isolated',
    'remoting_core.dll',
    'remoting_start_host.exe',
    'remoting_unittests.exe',
    'remoting_unittests.isolated',
    'sbox_integration_tests.isolated',
    'sbox_unittests.exe',
    'sbox_unittests.isolated',
    'sbox_validation_tests.isolated',
    'setup_unittests.exe',
    'setup_unittests.isolated',
    'skia_unittests.exe',
    'skia_unittests.isolated',
    'sql_unittests.exe',
    'sql_unittests.isolated',
    'sync_client.exe',
    'sync_integration_tests.exe',
    'sync_integration_tests.isolated',
    'sync_performance_tests.exe',
    'sync_unit_tests.exe',
    'sync_unit_tests.isolated',
    'test_registrar.exe',
    'ui_base_unittests.exe',
    'unit_tests.exe',
    'unit_tests.isolated',
    'url_unittests.exe',
    'url_unittests.isolated',
    'video_decode_accelerator_unittest.exe',
    'views_examples_with_content_exe.exe',
    'views_unittests.exe',
    'webkit_unit_tests.exe',
    'wtf_unittests.exe',
  },
}

def get_files_to_compare(build_dir, recursive=False):
  """Get the list of files to compare."""
  allowed = frozenset(
      ('', '.apk', '.app', '.dll', '.dylib', '.exe', '.nexe', '.so'))
  non_x_ok_exts = frozenset(('.apk', '.isolated'))
  def check(f):
    if not os.path.isfile(f):
      return False
    if os.path.basename(f).startswith('.'):
      return False
    ext = os.path.splitext(f)[1]
    if ext in non_x_ok_exts:
      return True
    return ext in allowed and os.access(f, os.X_OK)

  ret_files = set()
  for root, dirs, files in os.walk(build_dir):
    if not recursive:
      dirs[:] = [d for d in dirs if d.endswith('_apk')]
    for f in (f for f in files if check(os.path.join(root, f))):
      ret_files.add(os.path.relpath(os.path.join(root, f), build_dir))
  return ret_files


def diff_dict(a, b):
  """Returns a yaml-like textural diff of two dict.

  It is currently optimized for the .isolated format.
  """
  out = ''
  for key in set(a) | set(b):
    va = a.get(key)
    vb = b.get(key)
    if va.__class__ != vb.__class__:
      out += '- %s:  %r != %r\n' % (key, va, vb)
    elif isinstance(va, dict):
      c = diff_dict(va, vb)
      if c:
        out += '- %s:\n%s\n' % (
            key, '\n'.join('  ' + l for l in c.splitlines()))
    elif va != vb:
      out += '- %s:  %s != %s\n' % (key, va, vb)
  return out.rstrip()


def diff_binary(first_filepath, second_filepath, file_len):
  """Returns a compact binary diff if the diff is small enough."""
  CHUNK_SIZE = 32
  MAX_STREAMS = 10
  diffs = 0
  streams = []
  offset = 0
  with open(first_filepath, 'rb') as lhs:
    with open(second_filepath, 'rb') as rhs:
      while True:
        lhs_data = lhs.read(CHUNK_SIZE)
        rhs_data = rhs.read(CHUNK_SIZE)
        if not lhs_data:
          break
        if lhs_data != rhs_data:
          diffs += sum(l != r for l, r in zip(lhs_data, rhs_data))
          if streams is not None:
            if len(streams) < MAX_STREAMS:
              streams.append((offset, lhs_data, rhs_data))
            else:
              streams = None
        offset += len(lhs_data)
        del lhs_data
        del rhs_data
  if not diffs:
    return None
  result = '%d out of %d bytes are different (%.2f%%)' % (
        diffs, file_len, 100.0 * diffs / file_len)
  if streams:
    encode = lambda text: ''.join(i if 31 < ord(i) < 128 else '.' for i in text)
    for offset, lhs_data, rhs_data in streams:
      lhs_line = '%s \'%s\'' % (lhs_data.encode('hex'), encode(lhs_data))
      rhs_line = '%s \'%s\'' % (rhs_data.encode('hex'), encode(rhs_data))
      diff = list(difflib.Differ().compare([lhs_line], [rhs_line]))[-1][2:-1]
      result += '\n  0x%-8x: %s\n              %s\n              %s' % (
            offset, lhs_line, rhs_line, diff)
  return result


def compare_files(first_filepath, second_filepath):
  """Compares two binaries and return the number of differences between them.

  Returns None if the files are equal, a string otherwise.
  """
  if first_filepath.endswith('.isolated'):
    with open(first_filepath, 'rb') as f:
      lhs = json.load(f)
    with open(second_filepath, 'rb') as f:
      rhs = json.load(f)
    diff = diff_dict(lhs, rhs)
    if diff:
      return '\n' + '\n'.join('  ' + line for line in diff.splitlines())
    # else, falls through binary comparison, it must be binary equal too.

  file_len = os.stat(first_filepath).st_size
  if file_len != os.stat(second_filepath).st_size:
    return 'different size: %d != %d' % (
        file_len, os.stat(second_filepath).st_size)

  return diff_binary(first_filepath, second_filepath, file_len)


def compare_build_artifacts(first_dir, second_dir, target_platform,
                            recursive=False):
  """Compares the artifacts from two distinct builds."""
  if not os.path.isdir(first_dir):
    print >> sys.stderr, '%s isn\'t a valid directory.' % first_dir
    return 1
  if not os.path.isdir(second_dir):
    print >> sys.stderr, '%s isn\'t a valid directory.' % second_dir
    return 1

  epoch_hex = struct.pack('<I', int(time.time())).encode('hex')
  print('Epoch: %s' %
      ' '.join(epoch_hex[i:i+2] for i in xrange(0, len(epoch_hex), 2)))

  with open(os.path.join(BASE_DIR, 'deterministic_build_blacklist.json')) as f:
    blacklist = frozenset(json.load(f))
  whitelist = WHITELIST[target_platform]

  # The two directories.
  first_list = get_files_to_compare(first_dir, recursive) - blacklist
  second_list = get_files_to_compare(second_dir, recursive) - blacklist

  equals = []
  expected_diffs = []
  unexpected_diffs = []
  unexpected_equals = []
  all_files = sorted(first_list & second_list)
  missing_files = sorted(first_list.symmetric_difference(second_list))
  if missing_files:
    print >> sys.stderr, 'Different list of files in both directories:'
    print >> sys.stderr, '\n'.join('  ' + i for i in missing_files)
    unexpected_diffs.extend(missing_files)

  max_filepath_len = max(len(n) for n in all_files)
  for f in all_files:
    first_file = os.path.join(first_dir, f)
    second_file = os.path.join(second_dir, f)
    result = compare_files(first_file, second_file)
    if not result:
      tag = 'equal'
      equals.append(f)
      if f in whitelist:
        unexpected_equals.append(f)
    else:
      if f in whitelist:
        expected_diffs.append(f)
        tag = 'expected'
      else:
        unexpected_diffs.append(f)
        tag = 'unexpected'
      result = 'DIFFERENT (%s): %s' % (tag, result)
    print('%-*s: %s' % (max_filepath_len, f, result))
  unexpected_diffs.sort()

  print('Equals:           %d' % len(equals))
  print('Expected diffs:   %d' % len(expected_diffs))
  print('Unexpected diffs: %d' % len(unexpected_diffs))
  if unexpected_diffs:
    print('Unexpected files with diffs:\n')
    for u in unexpected_diffs:
      print('  %s' % u)
  if unexpected_equals:
    print('Unexpected files with no diffs:\n')
    for u in unexpected_equals:
      print('  %s' % u)

  return int(bool(unexpected_diffs))


def main():
  parser = optparse.OptionParser(usage='%prog [options]')
  parser.add_option(
      '-f', '--first-build-dir', help='The first build directory.')
  parser.add_option(
      '-s', '--second-build-dir', help='The second build directory.')
  parser.add_option('-r', '--recursive', action='store_true', default=False,
                    help='Indicates if the comparison should be recursive.')
  parser.add_option('-t', '--target-platform', help='The target platform.')
  options, _ = parser.parse_args()

  if not options.first_build_dir:
    parser.error('--first-build-dir is required')
  if not options.second_build_dir:
    parser.error('--second-build-dir is required')
  if not options.target_platform:
    parser.error('--target-platform is required')

  return compare_build_artifacts(os.path.abspath(options.first_build_dir),
                                 os.path.abspath(options.second_build_dir),
                                 options.target_platform,
                                 options.recursive)


if __name__ == '__main__':
  sys.exit(main())
