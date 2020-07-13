#!/usr/bin/env python

"""
A simple testing framework for emulator using python's unit testing framework.

Type:

./dotest.py -h

for available options.
"""

import sys
import os
import unittest
import logging
import re
import time
import psutil
from subprocess import PIPE

from utils import emu_argparser
from utils import emu_unittest

# Provides a regular expression for matching fail message
TIMEOUT_REGEX = re.compile(r"(^\d+)([smhd])?$")

main_logger = logging.getLogger()
def printResult(emuResult):
    def getTestName(id):
        return id.rsplit('.', 1)[-1]
    print
    main_logger.info("Test Summary")
    main_logger.info("Run %d tests (%d fail, %d pass, %d xfail, %d xpass)",
           emuResult.testsRun, len(emuResult.failures)+len(emuResult.errors), len(emuResult.passes),
           len(emuResult.expectedFailures), len(emuResult.unexpectedSuccesses))
    if len(emuResult.errors) > 0 or len(emuResult.failures) > 0:
        for x in emuResult.errors:
            if x[1].splitlines()[-1] == "TimeoutError":
                main_logger.info("TIMEOUT: %s", getTestName(x[0].id()))
            else:
                main_logger.info("FAIL: %s", getTestName(x[0].id()))
        for x in emuResult.failures:
            main_logger.info("FAIL: %s", getTestName(x[0].id()))

    if len(emuResult.passes) > 0:
        main_logger.info('------------------------------------------------------')
    for x in emuResult.passes:
        main_logger.info("PASS: %s, boot time: %s", getTestName(x.id()), x.boot_time)

    if len(emuResult.expectedFailures) > 0:
        main_logger.info('------------------------------------------------------')
    for x in emuResult.expectedFailures:
        main_logger.info("Expected Failure: %s", getTestName(x[0].id()))

    if len(emuResult.unexpectedSuccesses) > 0:
        main_logger.info('------------------------------------------------------')
    for x in emuResult.unexpectedSuccesses:
        main_logger.info("Unexpected Success: %s", getTestName(x.id()))

    main_logger.info('')
    main_logger.info("Test successful - %s", emuResult.wasSuccessful())

def setupLogger():
    """Create main_logger that will be used by test driver"""
    global main_logger
    log_formatter = logging.Formatter('%(message)s')
    file_name = 'main_%s.log' % time.strftime("%Y%m%d-%H%M%S")
    if emu_argparser.emu_args.session_dir is None:
        emu_argparser.emu_args.session_dir = time.strftime("%Y%m%d-%H%M%S")
    if not os.path.exists(emu_argparser.emu_args.session_dir):
        os.makedirs(emu_argparser.emu_args.session_dir)

    file_handler = logging.FileHandler(os.path.join(emu_argparser.emu_args.session_dir, file_name))
    file_handler.setFormatter(log_formatter)
    # Test summary goes to standard error, since we rely on stderr to parse test results in buildbot
    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setFormatter(log_formatter)

    main_logger.addHandler(file_handler)
    main_logger.addHandler(console_handler)
    main_logger.setLevel(getattr(logging, emu_argparser.emu_args.loglevel.upper()))

def findSystemAVDs():
    """Find available AVDs in system"""
    # avd is searched in the order of $ANDROID_AVD_HOME,$ANDROID_SDK_HOME/.android/avd and $HOME/.android/avd
    android_exec = "android.bat" if os.name == "nt" else "android"
    avd_list_proc = psutil.Popen([android_exec, "list", "avd", "-c"], stdout=PIPE, stderr=PIPE)
    (output, err) = avd_list_proc.communicate()
    logging.getLogger().debug(output)
    logging.getLogger().debug(err)
    avd_list = [x.strip() for x in output.splitlines()]
    main_logger.info("Found %d AVDs - %s", len(avd_list), avd_list)
    return avd_list

# Run the test case
if __name__ == '__main__':

    os.environ["SHELL"] = "/bin/bash"

    emu_argparser.emu_args = emu_argparser.get_parser().parse_args()
    setupLogger()
    main_logger.info(emu_argparser.emu_args)

    if emu_argparser.emu_args.avd_list is None:
        emu_argparser.emu_args.avd_list = findSystemAVDs()

    test_root_dir=os.path.dirname(os.path.realpath(__file__))
    emuSuite = unittest.TestLoader().discover(start_dir=test_root_dir, pattern=emu_argparser.emu_args.pattern)
    emuRunner = emu_unittest.EmuTextTestRunner(stream=sys.stdout)
    emuResult = emuRunner.run(emuSuite)
    printResult(emuResult)

    sys.exit(not emuResult.wasSuccessful())
