"""Test the emulator boot time"""

import unittest
import os
import time
import psutil
import shutil

from utils.emu_error import *
from utils.emu_argparser import emu_args
import utils.emu_testcase
from utils.emu_testcase import EmuBaseTestCase, AVDConfig

class BootTestCase(EmuBaseTestCase):
    def __init__(self, *args, **kwargs):
        super(BootTestCase, self).__init__(*args, **kwargs)
        self.avd_config = None
    @classmethod
    def setUpClass(cls):
        super(BootTestCase, cls).setUpClass()

    def tearDown(self):
        self.m_logger.debug('First try - quit emulator by adb emu kill')
        kill_proc = psutil.Popen(["adb", "emu", "kill"])
        # check emulator process is terminated
        result = self.term_check(timeout=5)
        if not result:
            self.m_logger.debug('Second try - quit emulator by psutil')
            self.kill_proc_by_name(["emulator", "qemu-system"])
            result = self.term_check(timeout=10)
            self.m_logger.debug("term_check after psutil.kill - %s", result)
        self.m_logger.info("Remove AVD inside of tear down")
        # avd should be found $HOME/.android/avd/
        avd_dir = os.path.join(os.path.expanduser('~'), '.android', 'avd')
        try:
            if result:
                self.start_proc.wait()
            time.sleep(1)
            self.kill_proc_by_name(["crash-service"])
            psutil.Popen(["adb", "kill-server"])
            os.remove(os.path.join(avd_dir, '%s.ini' % self.avd_config.name()))
            shutil.rmtree(os.path.join(avd_dir, '%s.avd' % self.avd_config.name()), ignore_errors=True)
        except:
            pass

    def boot_check(self, avd):
        self.boot_time = self.launch_emu_and_wait(avd)
        self.m_logger.info('AVD %s, boot time: %s, expected time: %s', avd, self.boot_time, emu_args.expected_boot_time)
        self.assertLessEqual(self.boot_time, emu_args.expected_boot_time)

    def run_boot_test(self, avd_config):
        self.avd_config = avd_config
        self.assertEqual(self.create_avd(avd_config), 0)
        self.boot_check(avd_config)


def create_test_case_for_avds():
    avd_list = emu_args.avd_list
    for avd in avd_list:
        def fn(i):
            return lambda self: self.boot_check(i)
        setattr(BootTestCase, "test_boot_%s" % avd, fn(avd))

if emu_args.config_file is None:
    create_test_case_for_avds()
else:
    utils.emu_testcase.create_test_case_from_file("boot", BootTestCase, BootTestCase.run_boot_test)

if __name__ == '__main__':
    os.environ["SHELL"] = "/bin/bash"
    emu_argparser.emu_args = emu_argparser.get_parser().parse_args()
    print emu_argparser.emu_args
    sys.argv[1:] = emu_args.unittest_args
    unittest.main()
