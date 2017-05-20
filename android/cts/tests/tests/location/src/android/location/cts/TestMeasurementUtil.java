/*
 * Copyright (C) 2015 Google Inc.
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

package android.location.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import junit.framework.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for GnssMeasurement Tests.
 */
public final class TestMeasurementUtil {

    private static final String TAG = "TestMeasurementUtil";

    private static final long NSEC_IN_SEC = 1000_000_000L;
    // Generally carrier phase quality prr's have uncertainties around 0.001-0.05 m/s, vs.
    // doppler energy quality prr's closer to 0.25-10 m/s.  Threshold is chosen between those
    // typical ranges.
    private static final float THRESHOLD_FOR_CARRIER_PRR_UNC_METERS_PER_SEC = 0.15F;

    // For gpsTimeInNs >= 1.14 * 10^18 (year 2016+)
    private static final long GPS_TIME_YEAR_2016_IN_NSEC = 1_140_000_000L * NSEC_IN_SEC;

    // Error message for GnssMeasurements Registration.
    public static final String REGISTRATION_ERROR_MESSAGE = "Registration of GnssMeasurements" +
            " listener has failed, this indicates a platform bug. Please report the issue with" +
            " a full bugreport.";

    /**
     * Check if test can be run on the current device.
     *
     * @param  testLocationManager TestLocationManager
     * @return true if Build.VERSION &gt;=  Build.VERSION_CODES.N and Location GPS present on
     *         device.
     */
    public static boolean canTestRunOnCurrentDevice(TestLocationManager testLocationManager,
                                                    String testTag,
                                                    int minHardwareYear,
                                                    boolean isCtsVerifier) {
       // TODO(sumitk): Enable this check once api 24 for N is avaiable.
       /*
       if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.i(TAG, "This test is designed to work on N or newer. " +
                    "Test is being skipped because the platform version is being run in " +
                    Build.VERSION.SDK_INT);
            return false;
        }
        */

        // If device does not have a GPS, skip the test.
        PackageManager pm = testLocationManager.getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
          Log.w(testTag, "GPS feature not present on device, skipping GPS test.");
          return false;
        }

        // If device has a GPS, but it's turned off in settings, and this is CTS verifier,
        // fail the test now, because there's no point in going further.
        // If this is CTS only,we'll warn instead, and quickly pass the test.
        // (Cts non-verifier deep-indoors-forgiveness happens later, *if* needed)
        boolean gpsProviderEnabled = testLocationManager.getLocationManager()
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        SoftAssert.failOrWarning(isCtsVerifier, " GPS location disabled on the device. " +
                "Enable location in settings to continue test.", gpsProviderEnabled);
        // If CTS only, allow an early exit pass
        if (!isCtsVerifier && !gpsProviderEnabled) {
            return false;
        }

        // TODO - add this to the test info page
        int gnssYearOfHardware = testLocationManager.getLocationManager().getGnssYearOfHardware();
        Log.i(testTag, "This device is reporting GNSS hardware from year "
                + (gnssYearOfHardware == 0 ? "2015 or earlier" : gnssYearOfHardware) + ". "
                + "Devices " + (gnssYearOfHardware >= minHardwareYear ? "like this one " : "")
                + "from year " + minHardwareYear + " or newer provide GnssMeasurement support." );

        return true;
    }

    /**
     * Check if pseudorange rate uncertainty in Gnss Measurement is in the expected range.
     * See field description in {@code gps.h}.
     *
     * @param measurement GnssMeasurement
     * @return true if this measurement has prr uncertainty in a range indicative of carrier phase
     */
    public static boolean gnssMeasurementHasCarrierPhasePrr(GnssMeasurement measurement) {
      return (measurement.getPseudorangeRateUncertaintyMetersPerSecond() <
              THRESHOLD_FOR_CARRIER_PRR_UNC_METERS_PER_SEC);
    }

    /**
     * Assert all mandatory fields in Gnss Clock are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param clock       GnssClock
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    public static void assertGnssClockFields(GnssClock clock,
                                             SoftAssert softAssert,
                                             long timeInNs) {
        softAssert.assertTrue("time_ns: clock value",
                timeInNs,
                "X >= 0",
                String.valueOf(timeInNs),
                timeInNs >= 0L);

        // If full bias is valid and accurate within one sec. verify its sign & magnitude
        if (clock.hasFullBiasNanos() &&
                ((!clock.hasBiasUncertaintyNanos()) ||
                        (clock.getBiasUncertaintyNanos() < NSEC_IN_SEC))) {
            long gpsTimeInNs = timeInNs - clock.getFullBiasNanos();
            softAssert.assertTrue("TimeNanos - FullBiasNanos = GpsTimeNanos: clock value",
                    gpsTimeInNs,
                    "gpsTimeInNs >= 1.14 * 10^18 (year 2016+)",
                    String.valueOf(gpsTimeInNs),
                    gpsTimeInNs >= GPS_TIME_YEAR_2016_IN_NSEC);
        }

    }

    /**
     * Assert all mandatory fields in Gnss Measurement are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    public static void assertAllGnssMeasurementMandatoryFields(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        verifySvid(measurement, softAssert, timeInNs);
        verifyReceivedSatelliteVehicleTimeInNs(measurement, softAssert, timeInNs);
        verifyAccumulatedDeltaRanges(measurement, softAssert, timeInNs);

        int state = measurement.getState();
        softAssert.assertTrue("state: Satellite code sync state",
                timeInNs,
                "X > 0",
                String.valueOf(state),
                state > 0);

        // Check received_gps_tow_uncertainty_ns
        softAssert.assertTrueAsWarning("received_gps_tow_uncertainty_ns:" +
                        " Uncertainty of received GPS Time-of-Week in ns",
                timeInNs,
                "X > 0",
                String.valueOf(measurement.getReceivedSvTimeUncertaintyNanos()),
                measurement.getReceivedSvTimeUncertaintyNanos() > 0L);

        long timeOffsetInSec = TimeUnit.NANOSECONDS.toSeconds(
                (long) measurement.getTimeOffsetNanos());
        softAssert.assertTrue("time_offset_ns: Time offset",
                timeInNs,
                "-100 seconds < X < +10 seconds",
                String.valueOf(measurement.getTimeOffsetNanos()),
                (-100 < timeOffsetInSec) && (timeOffsetInSec < 10));

        softAssert.assertTrue("c_n0_dbhz: Carrier-to-noise density",
                timeInNs,
                "0.0 >= X <=63",
                String.valueOf(measurement.getCn0DbHz()),
                measurement.getCn0DbHz() >= 0.0 &&
                        measurement.getCn0DbHz() <= 63.0);

        softAssert.assertTrue("pseudorange_rate_mps: Pseudorange rate in m/s",
                timeInNs,
                "X != 0.0",
                String.valueOf(measurement.getPseudorangeRateMetersPerSecond()),
                measurement.getPseudorangeRateMetersPerSecond() != 0.0);

        softAssert.assertTrue("pseudorange_rate_uncertainty_mps: " +
                        "Pseudorange Rate Uncertainty in m/s",
                timeInNs,
                "X > 0.0",
                String.valueOf(
                        measurement.getPseudorangeRateUncertaintyMetersPerSecond()),
                measurement.getPseudorangeRateUncertaintyMetersPerSecond() > 0.0);

        // Check carrier_frequency_hz.
        if (measurement.hasCarrierFrequencyHz()) {
            softAssert.assertTrue("carrier_frequency_hz: Carrier frequency in hz",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(measurement.getCarrierFrequencyHz()),
                    measurement.getCarrierFrequencyHz() > 0.0);
        }

        // Check carrier_phase.
        if (measurement.hasCarrierPhase()) {
            softAssert.assertTrue("carrier_phase: Carrier phase",
                    timeInNs,
                    "0.0 >= X <= 1.0",
                    String.valueOf(measurement.getCarrierPhase()),
                    measurement.getCarrierPhase() >= 0.0 && measurement.getCarrierPhase() <= 1.0);
        }

        // Check carrier_phase_uncertainty..
        if (measurement.hasCarrierPhaseUncertainty()) {
            softAssert.assertTrue("carrier_phase_uncertainty: 1-Sigma uncertainty of the " +
                            "carrier-phase",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(measurement.getCarrierPhaseUncertainty()),
                    measurement.getCarrierPhaseUncertainty() > 0.0);
        }

        // Check GNSS Measurement's multipath_indicator.
        softAssert.assertTrue("multipath_indicator: GNSS Measurement's multipath indicator",
                timeInNs,
                "0 >= X <= 2",
                String.valueOf(measurement.getMultipathIndicator()),
                measurement.getMultipathIndicator() >= 0
                        && measurement.getMultipathIndicator() <= 2);


        // Check Signal-to-Noise ratio (SNR).
        if (measurement.hasSnrInDb()) {
            softAssert.assertTrue("snr: Signal-to-Noise ratio (SNR) in dB",
                    timeInNs,
                    "0.0 >= X <= 63",
                    String.valueOf(measurement.getSnrInDb()),
                    measurement.getSnrInDb() >= 0.0 && measurement.getSnrInDb() <= 63);
        }
    }

    /**
     * Verify accumulated delta ranges are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifyAccumulatedDeltaRanges(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        int accumulatedDeltaRangeState = measurement.getAccumulatedDeltaRangeState();
        softAssert.assertTrue("accumulated_delta_range_state: " +
                        "Accumulated delta range state",
                timeInNs,
                "0 <= X <= 7",
                String.valueOf(accumulatedDeltaRangeState),
                accumulatedDeltaRangeState >= 0 && accumulatedDeltaRangeState <= 7);
        if (accumulatedDeltaRangeState > 0) {
            double accumulatedDeltaRangeInMeters =
                    measurement.getAccumulatedDeltaRangeMeters();
            softAssert.assertTrue("accumulated_delta_range_m: " +
                            "Accumulated delta range in meter",
                    timeInNs,
                    "X != 0.0",
                    String.valueOf(accumulatedDeltaRangeInMeters),
                    accumulatedDeltaRangeInMeters != 0.0);
            double accumulatedDeltaRangeUncertainty =
                    measurement.getAccumulatedDeltaRangeUncertaintyMeters();
            softAssert.assertTrue("accumulated_delta_range_uncertainty_m: " +
                            "Accumulated delta range uncertainty in meter",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(accumulatedDeltaRangeUncertainty),
                    accumulatedDeltaRangeUncertainty > 0.0);
        }
    }

    /**
     * Verify svid's are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     */
    private static void verifySvid(GnssMeasurement measurement, SoftAssert softAssert,
        long timeInNs) {

        String svidLogMessageFormat = "svid: Space Vehicle ID. Constellation type = %s";
        int constellationType = measurement.getConstellationType();
        int svid = measurement.getSvid();
        String svidValue = String.valueOf(svid);

        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_GPS",
                        timeInNs,
                        "[1, 32]",
                        svidValue,
                        svid > 0 && svid <= 32);
                break;
            case GnssStatus.CONSTELLATION_SBAS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_SBAS",
                        timeInNs,
                        "120 <= X <= 192",
                        svidValue,
                        svid >= 120 && svid <= 192);
                break;
            case GnssStatus.CONSTELLATION_GLONASS:
                // Check Upper 8 bit, signed
                int freq = (svid >> 8);
                softAssert.assertTrue("svid: upper 8 bits, frequency number. Constellation type " +
                                "= CONSTELLATION_GLONASS",
                        timeInNs,
                        "freq == -127 || -7 <= freq <= 6",
                        svidValue,
                        // future proof check allowing a change in definition under discussion
                        (freq == -127) || (freq >= -7 && freq <= 6) || (freq >= 93 && freq <= 106));
                // Check lower 8 bits, signed
                byte slot = (byte) svid;
                softAssert.assertTrue("svid: lower 8 bits, slot. Constellation type " +
                                "= CONSTELLATION_GLONASS",
                        timeInNs,
                        "slot == -127 || 1 <= slot <= 24",
                        svidValue,
                        // future proof check allowingn a change in definition under discussion
                        (slot == -127) || (slot >= 1 && slot <= 24) || (slot >= 93 && slot <= 106));
                softAssert.assertTrue("svid: one of slot or freq is set (not -127). " +
                                "ConstellationType = CONSTELLATION_GLONASS,",
                        timeInNs,
                        "slot != -127 || freq != -127",
                        svidValue,
                        (slot != -127) || (freq != -127));
                break;
            case GnssStatus.CONSTELLATION_QZSS:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_QZSS",
                        timeInNs,
                        "193 <= X <= 200",
                        svidValue,
                        svid >= 193 && svid <= 200);
                break;
            case GnssStatus.CONSTELLATION_BEIDOU:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_BEIDOU",
                        timeInNs,
                        "1 <= X <= 36",
                        svidValue,
                        svid >= 1 && svid <= 36);
                break;
            case GnssStatus.CONSTELLATION_GALILEO:
                softAssert.assertTrue("svid: Space Vehicle ID. Constellation type " +
                                "= CONSTELLATION_GALILEO",
                        timeInNs,
                        "1 <= X <= 37",
                        String.valueOf(svid),
                        svid >= 1 && svid <= 37);
                break;
            default:
                // Explicit fail if did not receive valid constellation type.
                softAssert.assertTrue("svid: Space Vehicle ID. Did not receive any valid " +
                                "constellation type.",
                        timeInNs,
                        "Valid constellation type.",
                        svidValue,
                        false);
                break;
        }
    }

    /**
     * Verify sv times are in expected range.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     * */
    private static void verifyReceivedSatelliteVehicleTimeInNs(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {

        int constellationType = measurement.getConstellationType();
        int state = measurement.getState();
        long received_sv_time_ns = measurement.getReceivedSvTimeNanos();
        double sv_time_ms = TimeUnit.NANOSECONDS.toMillis(received_sv_time_ns);
        double sv_time_sec = TimeUnit.NANOSECONDS.toSeconds(received_sv_time_ns);
        double sv_time_days = TimeUnit.NANOSECONDS.toDays(received_sv_time_ns);

        // Check ranges for received_sv_time_ns for given Gps State
        if (state == 0) {
            softAssert.assertTrue("received_sv_time_ns:" +
                            " Received SV Time-of-Week in ns." +
                            " GNSS_MEASUREMENT_STATE_UNKNOWN.",
                    timeInNs,
                    "X == 0",
                    String.valueOf(received_sv_time_ns),
                    sv_time_ms == 0);
        }

        switch (constellationType) {
            case GnssStatus.CONSTELLATION_GPS:
                verifyGpsQzssSvTimes(measurement, softAssert, timeInNs, state, "CONSTELLATION_GPS");
                break;
            case GnssStatus.CONSTELLATION_QZSS:
                verifyGpsQzssSvTimes(measurement, softAssert, timeInNs, state,
                        "CONSTELLATION_QZSS");
                break;
            case GnssStatus.CONSTELLATION_SBAS:
                if ((state | GnssMeasurement.STATE_SBAS_SYNC)
                        == GnssMeasurement.STATE_SBAS_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SBAS_SYNC",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0s >= X <= 1s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 1);
                } else if ((state | GnssMeasurement.STATE_SYMBOL_SYNC)
                        == GnssMeasurement.STATE_SYMBOL_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SYMBOL_SYNC",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0ms >= X <= 2ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 2);
                } else if ((state | GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_SBAS"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
            case GnssStatus.CONSTELLATION_GLONASS:
                if ((state | GnssMeasurement.STATE_GLO_TOD_DECODED)
                        == GnssMeasurement.STATE_GLO_TOD_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GLO_TOD_DECODED",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0 day >= X <= 1 day",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 1);
                } else if ((state | GnssMeasurement.STATE_GLO_STRING_SYNC)
                        == GnssMeasurement.STATE_GLO_STRING_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GLO_STRING_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0s >= X <= 2s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 2);
                } else if ((state | GnssMeasurement.STATE_BIT_SYNC)
                        == GnssMeasurement.STATE_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 20ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 20);
                } else if ((state | GnssMeasurement.STATE_SYMBOL_SYNC)
                        == GnssMeasurement.STATE_SYMBOL_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SYMBOL_SYNC",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 10ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 10);
                } else if ((state | GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GLONASS"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
            case GnssStatus.CONSTELLATION_GALILEO:
                if ((state | GnssMeasurement.STATE_TOW_DECODED)
                        == GnssMeasurement.STATE_TOW_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0 >= X <= 7 days",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state | GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC)
                        == GnssMeasurement.STATE_GAL_E1B_PAGE_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1B_PAGE_SYNC",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0s >= X <= 2s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 2);
                } else if ((state | GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK)
                        == GnssMeasurement.STATE_GAL_E1C_2ND_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1C_2ND_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0ms >= X <= 100ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 100);
                } else if ((state | GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK)
                        == GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_GAL_E1BC_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_GALILEO"),
                            timeInNs,
                            "0ms >= X <= 4ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 4);
                }
                break;
            case GnssStatus.CONSTELLATION_BEIDOU:
                if ((state | GnssMeasurement.STATE_TOW_DECODED)
                        == GnssMeasurement.STATE_TOW_DECODED) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0 >= X <= 7 days",
                            String.valueOf(sv_time_days),
                            sv_time_days >= 0 && sv_time_days <= 7);
                } else if ((state | GnssMeasurement.STATE_SUBFRAME_SYNC)
                        == GnssMeasurement.STATE_SUBFRAME_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_SUBFRAME_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0s >= X <= 6s",
                            String.valueOf(sv_time_sec),
                            sv_time_sec >= 0 && sv_time_sec <= 6);
                } else if ((state | GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC)
                        == GnssMeasurement.STATE_BDS_D2_SUBFRAME_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BDS_D2_SUBFRAME_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 600ms (0.6sec)",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 600);
                } else if ((state | GnssMeasurement.STATE_BIT_SYNC)
                        == GnssMeasurement.STATE_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 20ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 20);
                } else if ((state | GnssMeasurement.STATE_BDS_D2_BIT_SYNC)
                        == GnssMeasurement.STATE_BDS_D2_BIT_SYNC) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_BDS_D2_BIT_SYNC",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 2ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 2);
                } else if ((state | GnssMeasurement.STATE_CODE_LOCK)
                        == GnssMeasurement.STATE_CODE_LOCK) {
                    softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                                    "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                                    "GnssStatus.CONSTELLATION_BEIDOU"),
                            timeInNs,
                            "0ms >= X <= 1ms",
                            String.valueOf(sv_time_ms),
                            sv_time_ms >= 0 && sv_time_ms <= 1);
                }
                break;
        }
    }

    private static String getReceivedSvTimeNsLogMessage(String constellationType, String state) {
        return "received_sv_time_ns: Received SV Time-of-Week in ns. Constellation type = "
                + constellationType + ". State = " + state;
    }

    /**
     * Verify sv times are in expected range for given constellation type.
     * This is common check for CONSTELLATION_GPS & CONSTELLATION_QZSS.
     *
     * @param measurement GnssMeasurement
     * @param softAssert  custom SoftAssert
     * @param timeInNs    event time in ns
     * @param state       GnssMeasurement State
     * @param constellationType Gnss Constellation type
     */
    private static void verifyGpsQzssSvTimes(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs, int state, String constellationType) {

        long received_sv_time_ns = measurement.getReceivedSvTimeNanos();
        double sv_time_ms = TimeUnit.NANOSECONDS.toMillis(received_sv_time_ns);
        double sv_time_sec = TimeUnit.NANOSECONDS.toSeconds(received_sv_time_ns);
        double sv_time_days = TimeUnit.NANOSECONDS.toDays(received_sv_time_ns);

        if ((state | GnssMeasurement.STATE_TOW_DECODED)
                == GnssMeasurement.STATE_TOW_DECODED) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_TOW_DECODED",
                            constellationType),
                    timeInNs,
                    "0 >= X <= 7 days",
                    String.valueOf(sv_time_days),
                    sv_time_days >= 0 && sv_time_days <= 7);
        } else if ((state | GnssMeasurement.STATE_SUBFRAME_SYNC)
                == GnssMeasurement.STATE_SUBFRAME_SYNC) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_SUBFRAME_SYNC",
                            constellationType),
                    timeInNs,
                    "0s >= X <= 6s",
                    String.valueOf(sv_time_sec),
                    sv_time_sec >= 0 && sv_time_sec <= 6);
        } else if ((state | GnssMeasurement.STATE_BIT_SYNC)
                == GnssMeasurement.STATE_BIT_SYNC) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_BIT_SYNC",
                            constellationType),
                    timeInNs,
                    "0ms >= X <= 20ms",
                    String.valueOf(sv_time_ms),
                    sv_time_ms >= 0 && sv_time_ms <= 20);

        } else if ((state | GnssMeasurement.STATE_CODE_LOCK)
                == GnssMeasurement.STATE_CODE_LOCK) {
            softAssert.assertTrue(getReceivedSvTimeNsLogMessage(
                            "GNSS_MEASUREMENT_STATE_CODE_LOCK",
                            constellationType),
                    timeInNs,
                    "0ms >= X <= 1ms",
                    String.valueOf(sv_time_ms),
                    sv_time_ms >= 0 && sv_time_ms <= 1);
        }
    }

    /**
     * Assert all mandatory fields in Gnss Navigation Message are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param events GnssNavigationMessageEvents
     */
    public static void verifyGnssNavMessageMandatoryField(List<GnssNavigationMessage> events) {
        // Verify mandatory GnssNavigationMessage field values.
        SoftAssert softAssert = new SoftAssert(TAG);
        for (GnssNavigationMessage message : events) {
            int type = message.getType();
            softAssert.assertTrue("Gnss Navigation Message Type:expected [0x0101 - 0x0104]," +
                            " actual = " + type,
                    type >= 0x0101 && type <= 0x0104);

            // if message type == TYPE_L1CA, verify PRN & Data Size.
            int messageType = message.getType();
            if (messageType == GnssNavigationMessage.TYPE_GPS_L1CA) {
                int svid = message.getSvid();
                softAssert.assertTrue("Space Vehicle ID : expected = [1, 32], actual = " +
                                svid,
                        svid >= 1 && svid <= 32);
                int dataSize = message.getData().length;
                softAssert.assertTrue("Data size: expected = 40, actual = " + dataSize,
                        dataSize == 40);
            } else {
                Log.i(TAG, "GnssNavigationMessage (type = " + messageType
                        + ") skipped for verification.");
            }
        }
        softAssert.assertAll();
    }
}
