/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <unistd.h>

#include <gtest/gtest.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <utils/threads.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include <utils/SystemClock.h>
#include <IVehicleNetwork.h>

#include "IVehicleNetworkTestListener.h"

namespace android {

class IVehicleNetworkTest : public testing::Test {
public:
    IVehicleNetworkTest() {}

    ~IVehicleNetworkTest() {}

    sp<IVehicleNetwork> connectToService() {
        sp<IBinder> binder = defaultServiceManager()->getService(
                String16(IVehicleNetwork::SERVICE_NAME));
        if (binder != NULL) {
            sp<IVehicleNetwork> vn(interface_cast<IVehicleNetwork>(binder));
            return vn;
        }
        sp<IVehicleNetwork> dummy;
        return dummy;
    }

    sp<IVehicleNetwork> getDefaultVN() {
        return mDefaultVN;
    }
protected:
    virtual void SetUp() {
        ProcessState::self()->startThreadPool();
        mDefaultVN = connectToService();
        ASSERT_TRUE(mDefaultVN.get() != NULL);
    }
private:
    sp<IVehicleNetwork> mDefaultVN;
};

TEST_F(IVehicleNetworkTest, connect) {
    sp<IVehicleNetwork> vn = connectToService();
    ASSERT_TRUE(vn.get() != NULL);
}

TEST_F(IVehicleNetworkTest, listProperties) {
    sp<IVehicleNetwork> vn = getDefaultVN();
    sp<VehiclePropertiesHolder> properties = vn->listProperties();
    ASSERT_TRUE(properties.get() != NULL);
    int32_t numConfigs = properties->getList().size();
    ASSERT_TRUE(numConfigs > 0);
    for (auto& config : properties->getList()) {
        String8 msg = String8::format("prop 0x%x\n", config->prop);
        std::cout<<msg.string();
    }
    sp<VehiclePropertiesHolder> propertiesIvalid  = vn->listProperties(-1); // no such property
    ASSERT_TRUE(propertiesIvalid.get() == NULL);
    for (auto& config : properties->getList()) {
        String8 msg = String8::format("query single prop 0x%x\n", config->prop);
        std::cout<<msg.string();
        sp<VehiclePropertiesHolder> singleProperty = vn->listProperties(config->prop);
        ASSERT_EQ((size_t) 1, singleProperty->getList().size());
        vehicle_prop_config_t const * newConfig = *singleProperty->getList().begin();
        ASSERT_EQ(config->prop, newConfig->prop);
        ASSERT_EQ(config->access, newConfig->access);
        ASSERT_EQ(config->change_mode, newConfig->change_mode);
        //TODO add more check
    }
}

TEST_F(IVehicleNetworkTest, getProperty) {
    sp<IVehicleNetwork> vn = getDefaultVN();
    sp<VehiclePropertiesHolder> properties = vn->listProperties();
    ASSERT_TRUE(properties.get() != NULL);
    int32_t numConfigs = properties->getList().size();
    ASSERT_TRUE(numConfigs > 0);
    for (auto& config : properties->getList()) {
        if (config->prop == VEHICLE_PROPERTY_RADIO_PRESET) {
            continue;
        }
        String8 msg = String8::format("getting prop 0x%x\n", config->prop);
        std::cout<<msg.string();
        if ((config->prop >= (int32_t)VEHICLE_PROPERTY_INTERNAL_START) &&
                (config->prop <= (int32_t)VEHICLE_PROPERTY_INTERNAL_END)) {
            // internal property requires write to get anything.
            ScopedVehiclePropValue value;
            value.value.prop = config->prop;
            value.value.value_type = config->value_type;
            status_t r = vn->setProperty(value.value);
            ASSERT_EQ(NO_ERROR, r);
        }
        ScopedVehiclePropValue value;
        value.value.prop = config->prop;
        value.value.value_type = config->value_type;
        status_t r = vn->getProperty(&value.value);
        if ((config->access & VEHICLE_PROP_ACCESS_READ) == 0) { // cannot read
            ASSERT_TRUE(r != NO_ERROR);
        } else {
            ASSERT_EQ(NO_ERROR, r);
            ASSERT_EQ(config->value_type, value.value.value_type);
        }
    }
}

//TODO change this test to to safe write
TEST_F(IVehicleNetworkTest, setProperty) {
    sp<IVehicleNetwork> vn = getDefaultVN();
    sp<VehiclePropertiesHolder> properties = vn->listProperties();
    ASSERT_TRUE(properties.get() != NULL);
    int32_t numConfigs = properties->getList().size();
    ASSERT_TRUE(numConfigs > 0);
    for (auto& config : properties->getList()) {
        if (config->prop == VEHICLE_PROPERTY_RADIO_PRESET) {
            continue;
        }
        String8 msg = String8::format("setting prop 0x%x\n", config->prop);
        std::cout<<msg.string();
        ScopedVehiclePropValue value;
        value.value.prop = config->prop;
        value.value.value_type = config->value_type;
        status_t r = vn->setProperty(value.value);
        if ((config->access & VEHICLE_PROP_ACCESS_WRITE) == 0) { // cannot write
            ASSERT_TRUE(r != NO_ERROR);
        } else {
            ASSERT_EQ(NO_ERROR, r);
        }
    }
}

TEST_F(IVehicleNetworkTest, setSubscribe) {
    sp<IVehicleNetwork> vn = getDefaultVN();
    sp<VehiclePropertiesHolder> properties = vn->listProperties();
    ASSERT_TRUE(properties.get() != NULL);
    int32_t numConfigs = properties->getList().size();
    ASSERT_TRUE(numConfigs > 0);
    sp<IVehicleNetworkTestListener> listener(new IVehicleNetworkTestListener());
    for (auto& config : properties->getList()) {
        String8 msg = String8::format("subscribing property 0x%x\n", config->prop);
        std::cout<<msg.string();
        status_t r = vn->subscribe(listener, config->prop, config->max_sample_rate, 0);
        if (((config->access & VEHICLE_PROP_ACCESS_READ) == 0) ||
                (config->change_mode == VEHICLE_PROP_CHANGE_MODE_STATIC)) { // cannot subsctibe
            ASSERT_TRUE(r != NO_ERROR);
        } else {
            if ((config->prop >= (int32_t)VEHICLE_PROPERTY_INTERNAL_START) &&
                    (config->prop <= (int32_t)VEHICLE_PROPERTY_INTERNAL_END)) {
                // internal property requires write for event notification.
                ScopedVehiclePropValue value;
                value.value.prop = config->prop;
                value.value.value_type = config->value_type;
                status_t r = vn->setProperty(value.value);
                ASSERT_EQ(NO_ERROR, r);
            }
            ASSERT_EQ(NO_ERROR, r);
            ASSERT_TRUE(listener->waitForEvent(config->prop, 2000000000));
        }
    }
    for (auto& config : properties->getList()) {
        vn->unsubscribe(listener, config->prop);
    }
    usleep(1000000);
    //TODO improve this as this will wait for too long
    for (auto& config : properties->getList()) {
        ASSERT_TRUE(!listener->waitForEvent(config->prop, 1000000000));
    }
}

}; // namespace android
