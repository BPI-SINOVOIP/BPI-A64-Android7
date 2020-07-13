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
This test script exercises different GATT write procedures.
"""

from acts.test_utils.bt.BluetoothBaseTest import BluetoothBaseTest
from acts.test_utils.bt.GattConnectedBaseTest import GattConnectedBaseTest
from acts.test_utils.bt.GattEnum import GattCharacteristic
from acts.test_utils.bt.GattEnum import GattDescriptor
from acts.test_utils.bt.GattEnum import GattEvent
from acts.test_utils.bt.GattEnum import GattCbStrings
from acts.test_utils.bt.GattEnum import GattConnectionPriority
from acts.test_utils.bt.GattEnum import GattCharacteristicAttrLength
from acts.test_utils.bt.GattEnum import MtuSize
from acts.test_utils.bt.bt_gatt_utils import setup_gatt_mtu


class GattWriteTest(GattConnectedBaseTest):

    @BluetoothBaseTest.bt_test_wrap
    def test_write_char(self):
        """Test write characteristic value

        Test write characteristic value using Write Request

        1. Central: write WRITABLE_CHAR_UUID characteristic with char_value
           using write request.
        2. Peripheral: receive the written data.
        3. Peripheral: send response with status 0 (success).
        4. Central: make sure write callback is called.

        Expected Result:
        Verify that write request/response is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        char_value = [1, 2, 3, 4, 5, 6, 7]
        self.cen_ad.droid.gattClientCharacteristicSetValue(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_DEFAULT.value)

        self.cen_ad.droid.gattClientWriteCharacteristic(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID)

        event = self._server_wait(GattEvent.CHAR_WRITE_REQ)

        request_id = event['data']['requestId']
        self.assertEqual(True, event['data']['responseNeeded'],
                         "Should need response")
        self.assertEqual(char_value, event['data']['value'])
        self.assertEqual(0, event['data']['offset'])

        bt_device_id = 0
        status = 0
        #both offset and return value don't matter, just the status
        offset = 0
        self.per_ad.droid.gattServerGetConnectedDevices(self.gatt_server)
        self.per_ad.droid.gattServerSendResponse(
            self.gatt_server, bt_device_id, request_id, status, offset, [])

        event = self._client_wait(GattEvent.CHAR_WRITE)
        self.assertEqual(status, event["data"]["Status"],
                         "Write status should be 0")
        # Write response doesn't carry any data expcept status
        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_descr(self):
        """Test write descriptor value

        Test write descriptor value

        1. Central: write WRITABLE_DESC_UUID descriptor with desc_value.
        2. Peripheral: receive the written data.
        3. Peripheral: send response with status 0 (success).
        4. Central: make sure write callback is called.

        Expected Result:
        Verify that write request/response is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Descriptor
        Priority: 0
        """
        desc_value = [1, 2, 3, 4, 5, 6, 7]
        self.cen_ad.droid.gattClientDescriptorSetValue(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            self.WRITABLE_DESC_UUID, desc_value)

        self.cen_ad.droid.gattClientWriteDescriptor(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            self.WRITABLE_DESC_UUID)

        event = self._server_wait(GattEvent.DESC_WRITE_REQ)

        request_id = event['data']['requestId']
        self.assertEqual(True, event['data']['responseNeeded'],
                         "Should need response")
        self.assertEqual(desc_value, event['data']['value'])
        self.assertEqual(0, event['data']['offset'])

        bt_device_id = 0
        status = 0
        #both offset and return value don't matter, just the status
        offset = 0
        self.per_ad.droid.gattServerGetConnectedDevices(self.gatt_server)
        self.per_ad.droid.gattServerSendResponse(
            self.gatt_server, bt_device_id, request_id, status, offset, [])

        event = self._client_wait(GattEvent.DESC_WRITE)
        self.assertEqual(status, event["data"]["Status"],
                         "Write status should be 0")
        # Write response doesn't carry any data except status
        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_char_no_resp(self):
        """Test write characteristic value

        Test write characteristic value using Write Command

        1. Central: write WRITABLE_CHAR_UUID characteristic with char_value
           using write command.
        2. Central: make sure write callback is called.
        3. Peripheral: receive the written data.

        Expected Result:
        Verify that write command is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        char_value = [1, 2, 3, 4, 5, 6, 7]
        self.cen_ad.droid.gattClientCharacteristicSetValue(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_NO_RESPONSE.value)

        self.cen_ad.droid.gattClientWriteCharacteristic(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID)

        event = self._client_wait(GattEvent.CHAR_WRITE)
        if event["data"]["Status"] != 0:
            self.log.error("Write status should be 0")
            return False

        event = self._server_wait(GattEvent.CHAR_WRITE_REQ)

        request_id = event['data']['requestId']
        self.assertEqual(False, event['data']['responseNeeded'],
                         "Should not need response")
        self.assertEqual(0, event['data']['offset'])
        self.assertEqual(char_value, event['data']['value'])

        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_characteristic_long_no_resp(self):
        """Test write characteristic value

        Test write characteristic value using Write Command

        1. Central: write WRITABLE_CHAR_UUID characteristic with char_value
           using write command.
        2. Central: make sure write callback is called.
        3. Peripheral: receive the written data. Check it was properly trimmed.

        Expected Result:
        Verify that write command is properly trimmed and delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        char_value = []
        for i in range(512):
            char_value.append(i % 256)

        self.cen_ad.droid.gattClientCharacteristicSetValue(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_NO_RESPONSE.value)

        self.cen_ad.droid.gattClientWriteCharacteristic(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID)

        event = self._server_wait(GattEvent.CHAR_WRITE_REQ)

        request_id = event['data']['requestId']
        self.assertEqual(False, event['data']['responseNeeded'])

        # value shall be trimmed to MTU-3
        trimmed_value = char_value[0:self.mtu - 3]
        self.assertEqual(
            trimmed_value, event['data']['value'],
            "Received value should be sent value trimmed to MTU-3")

        event = self._client_wait(GattEvent.CHAR_WRITE)
        if event["data"]["Status"] != 0:
            self.log.error("Write status should be 0")
            return False
        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_characteristic_value_longer_than_mtu_request(self):
        """Test writing characteristic value longer than what mtu limts

        Test establishing a gatt connection between a GATT Peripheral and GATT
        Client. Request mtu size equal to the max MTU.
        The max number of bytes can be sent within a characteristic is
        (MTU - GattCharacteristicAttrLength.MTU_ATTR_2) since
        the GattCharacteristicAttrLength.MTU_ATTR_2 (3 bytes) are
        used for its attribute of the command code and its handle.
        Then reduce mtu by 1 and re-send the same characteristic.
        Make sure the characteristic value received by the remote side is
        also reduced properly.

        Steps:
        1. Create a GATT connection between the scanner(Client) and
           advertiser(Peripheral).
        2. Client: request new mtu size change to max MTU.
        3. Client: write a characteristic with char_value of max MTU bytes.
        4. Peripheral: receive the written data.  Check it was properly
           truncated to (max MTU - GattCharacteristicAttrLength.MTU_ATTR_2).
        5. Client: request mtu size change to (max MTU - 1).
        6. Client: write the same characteristic again.
        7. Peripheral: receive the written data.  Check it was properly
           truncated to (max MTU - 1 - GattCharacteristicAttrLength.MTU_ATTR_2)

        Expected Result:
        Verify that data received by the Peripheral side is properly truncated
        when mtu is set.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic, MTU
        Priority: 2
        """
        self.mtu = MtuSize.MAX.value
        self.log.info("Set mtu to max MTU: {}".format(self.mtu))
        # set new MTU to the middle point of min and max of MTU
        if not setup_gatt_mtu(self.cen_ad, self.bluetooth_gatt,
                              self.gatt_callback, self.mtu):
            return False

        # create a characteristic with max MTU (217) bytes
        char_value = []
        for i in range(MtuSize.MAX.value):
            char_value.append(i)

        self.cen_ad.droid.gattClientCharacteristicSetValue(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_NO_RESPONSE.value)

        # write data to the characteristic of the Peripheral
        self.cen_ad.droid.gattClientWriteCharacteristic(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID)

        event = self._server_wait(GattEvent.CHAR_WRITE_REQ)
        self.log.info("Received value with mtu = max MTU: {}".format(
            event['data']['value']))

        # check the data received by Peripheral shall be truncated to
        # (mtu - GattCharacteristicAttrLength.MTU_ATTR_2) bytes
        data_length = self.mtu - GattCharacteristicAttrLength.MTU_ATTR_2
        expected_value = char_value[:data_length]
        self.assertEqual(
            expected_value, event['data']['value'],
            "Received value should have {} bytes".format(data_length))

        # set the mtu to max MTU-1
        self.mtu = MtuSize.MAX.value - 1
        self.log.info("Set mtu to max MTU - 1 : {}".format(self.mtu))
        data_length = self.mtu - GattCharacteristicAttrLength.MTU_ATTR_2
        if not setup_gatt_mtu(self.cen_ad, self.bluetooth_gatt,
                              self.gatt_callback, self.mtu):
            return False

        # write the same characteric to Peripheral again
        self.cen_ad.droid.gattClientWriteCharacteristic(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID)

        event = self._server_wait(GattEvent.CHAR_WRITE_REQ)
        self.log.info("Data received when mtu = max MTU - 1: {}".format(
            event['data']['value']))

        # check the data received by Peripheral shall be truncated to
        # (mtu - GattCharacteristicAttrLength.MTU_ATTR_2) bytes
        # when mtu is reduced
        expected_value = char_value[:data_length]
        self.assertEqual(
            expected_value, event['data']['value'],
            "Received value should have {} bytes".format(data_length))
        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_characteristic_stress(self):
        """Stress test write characteristic value

        Test write characteristic value using Write Request

        1. Central: write WRITABLE_CHAR_UUID characteristic with char_value
           using write request.
        2. Peripheral: receive the written data.
        3. Peripheral: send response with status 0 (success).
        4. Central: make sure write callback is called.
        5. Repeat steps 1-4 100 times.

        Expected Result:
        Verify that write request/response is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        self.cen_ad.droid.gattClientRequestConnectionPriority(
            self.bluetooth_gatt,
            GattConnectionPriority.CONNECTION_PRIORITY_HIGH.value)

        bt_device_id = 0

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_DEFAULT.value)

        for i in range(100):

            char_value = []
            for j in range(i, i + self.mtu - 3):
                char_value.append(j % 256)

            self.cen_ad.droid.gattClientCharacteristicSetValue(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

            self.cen_ad.droid.gattClientWriteCharacteristic(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID)

            event = self._server_wait(GattEvent.CHAR_WRITE_REQ)

            self.log.info("{} event found: {}".format(
                GattCbStrings.CHAR_WRITE_REQ.value.format(
                    self.gatt_server_callback), event['data']['value']))
            request_id = event['data']['requestId']
            found_value = event['data']['value']
            if found_value != char_value:
                self.log.info("Values didn't match. Found: {}, "
                              "Expected: {}".format(found_value, char_value))
                return False

            # only status is sent
            status = 0
            offset = 0
            char_value_return = []
            self.per_ad.droid.gattServerSendResponse(
                self.gatt_server, bt_device_id, request_id, status, offset,
                char_value_return)

            event = self._client_wait(GattEvent.CHAR_WRITE)
            if event["data"]["Status"] != status:
                self.log.error("Write status should be 0")
                return False

        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_descriptor_stress(self):
        """Stress test write descriptor value

        Stress test write descriptor value

        1. Central: write WRITABLE_DESC_UUID descriptor with desc_value.
        2. Peripheral: receive the written data.
        3. Peripheral: send response with status 0 (success).
        4. Central: make sure write callback is called.
        5. Repeat 1-4 100 times

        Expected Result:
        Verify that write request/response is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Descriptor
        Priority: 0
        """
        self.cen_ad.droid.gattClientRequestConnectionPriority(
            self.bluetooth_gatt,
            GattConnectionPriority.CONNECTION_PRIORITY_HIGH.value)

        for i in range(100):

            desc_value = []
            for j in range(i, i + self.mtu - 3):
                desc_value.append(j % 256)

            self.cen_ad.droid.gattClientDescriptorSetValue(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID,
                self.WRITABLE_DESC_UUID, desc_value)

            self.cen_ad.droid.gattClientWriteDescriptor(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID,
                self.WRITABLE_DESC_UUID)

            event = self._server_wait(GattEvent.DESC_WRITE_REQ)

            self.log.info("{} event found: {}".format(
                GattCbStrings.CHAR_WRITE_REQ.value.format(
                    self.gatt_server_callback), event['data']['value']))

            request_id = event['data']['requestId']
            self.assertEqual(True, event['data']['responseNeeded'],
                             "Should need response")
            self.assertEqual(desc_value, event['data']['value'])
            self.assertEqual(0, event['data']['offset'])

            bt_device_id = 0
            status = 0
            #both offset and return value don't matter, just the status
            offset = 0
            self.per_ad.droid.gattServerGetConnectedDevices(self.gatt_server)
            self.per_ad.droid.gattServerSendResponse(
                self.gatt_server, bt_device_id, request_id, status, offset, [])

            event = self._client_wait(GattEvent.DESC_WRITE)
            self.assertEqual(status, event["data"]["Status"],
                             "Write status should be 0")
            # Write response doesn't carry any data except status
        return True

    @BluetoothBaseTest.bt_test_wrap
    def test_write_characteristic_no_resp_stress(self):
        """Stress test write characteristic value

        Stress test write characteristic value using Write Command

        1. Central: write WRITABLE_CHAR_UUID characteristic with char_value
           using write command.
        2. Central: make sure write callback is called.
        3. Peripheral: receive the written data.
        4. Repeat steps 1-3 100 times.

        Expected Result:
        Verify that write command is properly delivered.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        self.cen_ad.droid.gattClientRequestConnectionPriority(
            self.bluetooth_gatt,
            GattConnectionPriority.CONNECTION_PRIORITY_HIGH.value)

        bt_device_id = 0

        self.cen_ad.droid.gattClientCharacteristicSetWriteType(
            self.bluetooth_gatt, self.discovered_services_index,
            self.test_service_index, self.WRITABLE_CHAR_UUID,
            GattCharacteristic.WRITE_TYPE_NO_RESPONSE.value)

        for i in range(100):
            char_value = []
            for j in range(i, i + self.mtu - 3):
                char_value.append(j % 256)

            self.cen_ad.droid.gattClientCharacteristicSetValue(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID, char_value)

            self.cen_ad.droid.gattClientWriteCharacteristic(
                self.bluetooth_gatt, self.discovered_services_index,
                self.test_service_index, self.WRITABLE_CHAR_UUID)

            # client shall not wait for server, get complete event right away
            event = self._client_wait(GattEvent.CHAR_WRITE)
            if event["data"]["Status"] != 0:
                self.log.error("Write status should be 0")
                return False

            event = self._server_wait(GattEvent.CHAR_WRITE_REQ)

            self.log.info("{} event found: {}".format(
                GattCbStrings.CHAR_WRITE_REQ.value.format(
                    self.gatt_server_callback), event['data']['value']))
            request_id = event['data']['requestId']
            found_value = event['data']['value']
            if found_value != char_value:
                self.log.info("Values didn't match. Found: {}, "
                              "Expected: {}".format(found_value, char_value))
                return False

        return True
