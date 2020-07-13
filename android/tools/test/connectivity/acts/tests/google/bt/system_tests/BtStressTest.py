#/usr/bin/env python3.4
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Basic Bluetooth Classic stress tests.
"""

import time
from acts.base_test import BaseTestClass
from acts.test_utils.bt.BluetoothBaseTest import BluetoothBaseTest
from acts.test_utils.bt.bt_test_utils import clear_bonded_devices
from acts.test_utils.bt.bt_test_utils import pair_pri_to_sec
from acts.test_utils.bt.bt_test_utils import reset_bluetooth
from acts.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test


class BtStressTest(BluetoothBaseTest):
    default_timeout = 20
    iterations = 100

    def __init__(self, controllers):
        BluetoothBaseTest.__init__(self, controllers)


    def teardown_test(self):
        super(BluetoothBaseTest, self).teardown_test()
        self.log_stats()
        return True

    def test_toggle_bluetooth(self):
        """Stress test toggling bluetooth on and off.

        Test the integrity of toggling bluetooth on and off.

        Steps:
        1. Toggle bluetooth off.
        2. Toggle bluetooth on.
        3. Repeat steps 1 and 2 one-hundred times.

        Expected Result:
        Each iteration of toggling bluetooth on and off should not cause an
        exception.

        Returns:
          Pass if True
          Fail if False

        TAGS: Classic, Stress
        Priority: 1
        """
        for n in range(self.iterations):
            self.log.info("Toggling bluetooth iteration {}.".format(n + 1))
            if not reset_bluetooth([self.android_devices[0]]):
                self.log.error("Failure to reset Bluetooth")
                return False
        return True

    def test_pair_bluetooth_stress(self):
        """Stress test for pairing BT devices.

        Test the integrity of Bluetooth pairing.

        Steps:
        1. Pair two Android devices
        2. Verify both devices are paired
        3. Unpair devices.
        4. Verify devices unpaired.
        5. Repeat steps 1-4 100 times.

        Expected Result:
        Each iteration of toggling Bluetooth pairing and unpairing
        should succeed.

        Returns:
          Pass if True
          Fail if False

        TAGS: Classic, Stress
        Priority: 1
        """
        for n in range(self.iterations):
            self.log.info("Pair bluetooth iteration {}.".format(n + 1))
            self.start_timer()
            if (not pair_pri_to_sec(self.android_devices[0].droid,
                                self.android_devices[1].droid)):
                self.log.error("Failed to bond devices.")
                return False
            self.log.info("Total time (ms): {}".format(self.end_timer()))
            for ad in self.android_devices:
                if not clear_bonded_devices(ad):
                    return False
                # Necessary sleep time for entries to update unbonded state
                time.sleep(1)
                bonded_devices = ad.droid.bluetoothGetBondedDevices()
                if len(bonded_devices) > 0:
                    self.log.error("Failed to unbond devices: {}".format(
                        bonded_devices))
                    return False
        return True
