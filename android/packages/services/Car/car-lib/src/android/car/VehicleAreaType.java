/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.car;

import android.annotation.SystemApi;

/**
 * Represents vehicle area such as window, door, seat, zone, etc.
 * See also {@link android.car.VehicleDoor}, {@link android.car.VehicleSeat},
 * {@link android.car.VehicleWindow}, {@link android.car.VehicleZone}
 *
 * @hide
 */
@SystemApi
public class VehicleAreaType {
    /** Used for global properties */
    public static final int VEHICLE_AREA_TYPE_NONE = 0;

    public static final int VEHICLE_AREA_TYPE_ZONE = 1;
    public static final int VEHICLE_AREA_TYPE_WINDOW = 2;
    public static final int VEHICLE_AREA_TYPE_SEAT = 3;
    public static final int VEHICLE_AREA_TYPE_DOOR = 4;
}
