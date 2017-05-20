/*
 * Copyright 2013 The Android Open Source Project
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
#ifndef __AWKEYMASTER_MODULEAPI_INCLUDE_H_
#define __AWKEYMASTER_MODULEAPI_INCLUDE_H_

#include <hardware/keymaster0.h>

/**
 * The id of this module
 */
#define AW_KEYSTORE_KEYMASTER "aw_keymaster"

/**
 * The API level of this version of the header. The allows the implementing
 * module to recognize which API level of the client it is dealing with in
 * the case of pre-compiled binary clients.
 */
#define AW_KEYMASTER_API_VERSION KEYMASTER_MODULE_API_VERSION_0_2

int aw_keymaster_req_libsym(void *handle);

int aw_schw_generate_keypair(const keymaster0_device_t* dev,
        const keymaster_keypair_t key_type, const void* key_params,
        uint8_t** keyBlob, size_t* keyBlobLength);

int aw_schw_import_keypair(const keymaster0_device_t* dev,
        const uint8_t* key, const size_t key_length,
        uint8_t** key_blob, size_t* key_blob_length);

int aw_schw_get_keypair_public(const struct keymaster0_device* dev,
        const uint8_t* key_blob, const size_t key_blob_length,
        uint8_t** x509_data, size_t* x509_data_length);

int aw_schw_sign_data(const keymaster0_device_t* dev,
        const void* params,
        const uint8_t* keyBlob, const size_t keyBlobLength,
        const uint8_t* data, const size_t dataLength,
        uint8_t** signedData, size_t* signedDataLength);

int aw_schw_verify_data(const keymaster0_device_t* dev,
        const void* params,
        const uint8_t* keyBlob, const size_t keyBlobLength,
        const uint8_t* signedData, const size_t signedDataLength,
        const uint8_t* signature, const size_t signatureLength);




#endif /* AWKEYMASTER_INCLUDE_H */
