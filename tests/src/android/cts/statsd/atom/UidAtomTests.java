/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.cts.statsd.atom;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.net.wifi.WifiModeEnum;
import android.os.WakeLockLevelEnum;
import android.server.ErrorSource;

import com.android.internal.os.StatsdConfigProto.FieldMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.ANROccurred;
import com.android.os.AtomsProto.AppCrashOccurred;
import com.android.os.AtomsProto.AppOps;
import com.android.os.AtomsProto.AppStartOccurred;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.AttributionNode;
import com.android.os.AtomsProto.AudioStateChanged;
import com.android.os.AtomsProto.BinderCalls;
import com.android.os.AtomsProto.BleScanResultReceived;
import com.android.os.AtomsProto.BleScanStateChanged;
import com.android.os.AtomsProto.CameraStateChanged;
import com.android.os.AtomsProto.DangerousPermissionState;
import com.android.os.AtomsProto.DeviceCalculatedPowerBlameUid;
import com.android.os.AtomsProto.FlashlightStateChanged;
import com.android.os.AtomsProto.ForegroundServiceStateChanged;
import com.android.os.AtomsProto.GpsScanStateChanged;
import com.android.os.AtomsProto.HiddenApiUsed;
import com.android.os.AtomsProto.LooperStats;
import com.android.os.AtomsProto.LmkKillOccurred;
import com.android.os.AtomsProto.MediaCodecStateChanged;
import com.android.os.AtomsProto.OverlayStateChanged;
import com.android.os.AtomsProto.PictureInPictureStateChanged;
import com.android.os.AtomsProto.ProcessMemoryHighWaterMark;
import com.android.os.AtomsProto.ProcessMemorySnapshot;
import com.android.os.AtomsProto.ProcessMemoryState;
import com.android.os.AtomsProto.ScheduledJobStateChanged;
import com.android.os.AtomsProto.SyncStateChanged;
import com.android.os.AtomsProto.TestAtomReported;
import com.android.os.AtomsProto.VibratorStateChanged;
import com.android.os.AtomsProto.WakelockStateChanged;
import com.android.os.AtomsProto.WakeupAlarmOccurred;
import com.android.os.AtomsProto.WifiLockStateChanged;
import com.android.os.AtomsProto.WifiMulticastLockStateChanged;
import com.android.os.AtomsProto.WifiScanStateChanged;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.log.LogUtil;

import com.google.common.collect.Range;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Statsd atom tests that are done via app, for atoms that report a uid.
 */
public class UidAtomTests extends DeviceAtomTestCase {

    private static final String TAG = "Statsd.UidAtomTests";

    private static final boolean DAVEY_ENABLED = false;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        resetBatteryStatus();
        super.tearDown();
    }

    public void testLmkKillOccurred() throws Exception {
        if (statsdDisabled() || !"true".equals(getProperty("ro.lmk.log_stats"))) {
            return;
        }

        StatsdConfig.Builder conf = createConfigBuilder()
                .addAllowedLogSource("AID_LMKD");
        final int atomTag = Atom.LMK_KILL_OCCURRED_FIELD_NUMBER;
        addAtomEvent(conf, atomTag, false);
        uploadConfig(conf);

        Thread.sleep(WAIT_TIME_SHORT);

        executeBackgroundService(ACTION_LMK);
        Thread.sleep(5_000);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasLmkKillOccurred()).isTrue();
        LmkKillOccurred atom = data.get(0).getAtom().getLmkKillOccurred();
        assertThat(atom.getUid()).isEqualTo(getUid());
        assertThat(atom.getProcessName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
        assertThat(atom.getOomAdjScore()).isAtLeast(500);
    }

    public void testAppCrashOccurred() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        createAndUploadConfig(atomTag, false);
        Thread.sleep(WAIT_TIME_SHORT);

        runActivity("StatsdCtsForegroundActivity", "action", "action.crash");

        Thread.sleep(WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        assertThat(atom.getEventType()).isEqualTo("crash");
        assertThat(atom.getIsInstantApp().getNumber())
            .isEqualTo(AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
            .isEqualTo(AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo("com.android.server.cts.device.statsd");
    }

    public void testAppStartOccurred() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.APP_START_OCCURRED_FIELD_NUMBER;

        createAndUploadConfig(atomTag, false);
        Thread.sleep(WAIT_TIME_SHORT);

        runActivity("StatsdCtsForegroundActivity", "action", "action.sleep_top");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        AppStartOccurred atom = data.get(0).getAtom().getAppStartOccurred();
        assertThat(atom.getPkgName()).isEqualTo("com.android.server.cts.device.statsd");
        assertThat(atom.getActivityName())
            .isEqualTo("com.android.server.cts.device.statsd.StatsdCtsForegroundActivity");
        assertThat(atom.getIsInstantApp()).isFalse();
        assertThat(atom.getActivityStartMillis()).isGreaterThan(0L);
        assertThat(atom.getTransitionDelayMillis()).isGreaterThan(0);
    }

    public void testAudioState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_AUDIO_OUTPUT, true)) return;

        final int atomTag = Atom.AUDIO_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testAudioState";

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        Thread.sleep(WAIT_TIME_SHORT);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", name);

        Thread.sleep(WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // AudioStateChanged timestamp is fuzzed to 5min buckets
        assertStatesOccurred(stateSet, data, 0,
                atom -> atom.getAudioStateChanged().getState().getNumber());
    }

    public void testBleScan() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_BLUETOOTH_LE, true)) return;

        final int atom = Atom.BLE_SCAN_STATE_CHANGED_FIELD_NUMBER;
        final int field = BleScanStateChanged.STATE_FIELD_NUMBER;
        final int stateOn = BleScanStateChanged.State.ON_VALUE;
        final int stateOff = BleScanStateChanged.State.OFF_VALUE;
        final int minTimeDiffMillis = 1_500;
        final int maxTimeDiffMillis = 3_000;

        List<EventMetricData> data = doDeviceMethodOnOff("testBleScanUnoptimized", atom, field,
                stateOn, stateOff, minTimeDiffMillis, maxTimeDiffMillis, true);

        BleScanStateChanged a0 = data.get(0).getAtom().getBleScanStateChanged();
        BleScanStateChanged a1 = data.get(1).getAtom().getBleScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
        assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
    }

    public void testBleUnoptimizedScan() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_BLUETOOTH_LE, true)) return;

        final int atom = Atom.BLE_SCAN_STATE_CHANGED_FIELD_NUMBER;
        final int field = BleScanStateChanged.STATE_FIELD_NUMBER;
        final int stateOn = BleScanStateChanged.State.ON_VALUE;
        final int stateOff = BleScanStateChanged.State.OFF_VALUE;
        final int minTimeDiffMillis = 1_500;
        final int maxTimeDiffMillis = 3_000;

        List<EventMetricData> data = doDeviceMethodOnOff("testBleScanUnoptimized", atom, field,
                stateOn, stateOff, minTimeDiffMillis, maxTimeDiffMillis, true);

        BleScanStateChanged a0 = data.get(0).getAtom().getBleScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
        assertThat(a0.getIsFiltered()).isFalse();
        assertThat(a0.getIsFirstMatch()).isFalse();
        assertThat(a0.getIsOpportunistic()).isFalse();
        BleScanStateChanged a1 = data.get(1).getAtom().getBleScanStateChanged();
        assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
        assertThat(a1.getIsFiltered()).isFalse();
        assertThat(a1.getIsFirstMatch()).isFalse();
        assertThat(a1.getIsOpportunistic()).isFalse();


        // Now repeat the test for opportunistic scanning and make sure it is reported correctly.
        data = doDeviceMethodOnOff("testBleScanOpportunistic", atom, field,
                stateOn, stateOff, minTimeDiffMillis, maxTimeDiffMillis, true);

        a0 = data.get(0).getAtom().getBleScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
        assertThat(a0.getIsFiltered()).isFalse();
        assertThat(a0.getIsFirstMatch()).isFalse();
        assertThat(a0.getIsOpportunistic()).isTrue();  // This scan is opportunistic.
        a1 = data.get(1).getAtom().getBleScanStateChanged();
        assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
        assertThat(a1.getIsFiltered()).isFalse();
        assertThat(a1.getIsFirstMatch()).isFalse();
        assertThat(a1.getIsOpportunistic()).isTrue();
    }

    public void testBleScanResult() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_BLUETOOTH_LE, true)) return;

        final int atom = Atom.BLE_SCAN_RESULT_RECEIVED_FIELD_NUMBER;
        final int field = BleScanResultReceived.NUM_RESULTS_FIELD_NUMBER;

        StatsdConfig.Builder conf = createConfigBuilder();
        addAtomEvent(conf, atom, createFvm(field).setGteInt(0));
        List<EventMetricData> data = doDeviceMethod("testBleScanResult", conf);

        assertThat(data.size()).isAtLeast(1);
        BleScanResultReceived a0 = data.get(0).getAtom().getBleScanResultReceived();
        assertThat(a0.getNumResults()).isAtLeast(1);
    }

    public void testHiddenApiUsed() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        String oldRate = getDevice().executeShellCommand(
                "device_config get app_compat hidden_api_access_statslog_sampling_rate").trim();

        getDevice().executeShellCommand(
                "device_config put app_compat hidden_api_access_statslog_sampling_rate 65536");
        try {
            final int atomTag = Atom.HIDDEN_API_USED_FIELD_NUMBER;

            createAndUploadConfig(atomTag, false);

            runActivity("HiddenApiUsedActivity", null, null);


            List<EventMetricData> data = getEventMetricDataList();
            assertThat(data).hasSize(1);

            HiddenApiUsed atom = data.get(0).getAtom().getHiddenApiUsed();

            int uid = getUid();
            assertThat(atom.getUid()).isEqualTo(uid);
            assertThat(atom.getAccessDenied()).isFalse();
            assertThat(atom.getSignature())
                .isEqualTo("Landroid/app/Activity;->mWindow:Landroid/view/Window;");
        } finally {
            if (!oldRate.equals("null")) {
                getDevice().executeShellCommand(
                        "device_config put app_compat hidden_api_access_statslog_sampling_rate "
                        + oldRate);
            } else {
                getDevice().executeShellCommand(
                        "device_config delete hidden_api_access_statslog_sampling_rate");
            }
        }
    }

    public void testCameraState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_CAMERA, true) && !hasFeature(FEATURE_CAMERA_FRONT, true)) return;

        final int atomTag = Atom.CAMERA_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> cameraOn = new HashSet<>(Arrays.asList(CameraStateChanged.State.ON_VALUE));
        Set<Integer> cameraOff = new HashSet<>(Arrays.asList(CameraStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(cameraOn, cameraOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testCameraState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_LONG,
                atom -> atom.getCameraStateChanged().getState().getNumber());
    }

    public void testCpuTimePerUid() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WATCH, false)) return;
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.CPU_TIME_PER_UID_FIELD_NUMBER, null);

        uploadConfig(config);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testSimpleCpu");

        Thread.sleep(WAIT_TIME_SHORT);
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_LONG);

        List<Atom> atomList = getGaugeMetricDataList();

        // TODO: We don't have atom matching on gauge yet. Let's refactor this after that feature is
        // implemented.
        boolean found = false;
        int uid = getUid();
        for (Atom atom : atomList) {
            if (atom.getCpuTimePerUid().getUid() == uid) {
                found = true;
                assertThat(atom.getCpuTimePerUid().getUserTimeMicros()).isGreaterThan(0L);
                assertThat(atom.getCpuTimePerUid().getSysTimeMicros()).isGreaterThan(0L);
            }
        }
        assertWithMessage(String.format("did not find uid %d", uid)).that(found).isTrue();
    }

    public void testDeviceCalculatedPowerUse() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_LEANBACK_ONLY, false)) return;

        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.DEVICE_CALCULATED_POWER_USE_FIELD_NUMBER, null);
        uploadConfig(config);
        unplugDevice();

        Thread.sleep(WAIT_TIME_LONG);
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testSimpleCpu");
        Thread.sleep(WAIT_TIME_SHORT);
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_LONG);

        Atom atom = getGaugeMetricDataList().get(0);
        assertThat(atom.getDeviceCalculatedPowerUse().getComputedPowerNanoAmpSecs())
            .isGreaterThan(0L);
    }


    public void testDeviceCalculatedPowerBlameUid() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_LEANBACK_ONLY, false)) return;

        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config,
                Atom.DEVICE_CALCULATED_POWER_BLAME_UID_FIELD_NUMBER, null);
        uploadConfig(config);
        unplugDevice();

        Thread.sleep(WAIT_TIME_LONG);
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testSimpleCpu");
        Thread.sleep(WAIT_TIME_SHORT);
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_LONG);

        List<Atom> atomList = getGaugeMetricDataList();
        boolean uidFound = false;
        int uid = getUid();
        long uidPower = 0;
        for (Atom atom : atomList) {
            DeviceCalculatedPowerBlameUid item = atom.getDeviceCalculatedPowerBlameUid();
                if (item.getUid() == uid) {
                assertWithMessage(String.format("Found multiple power values for uid %d", uid))
                    .that(uidFound).isFalse();
                uidFound = true;
                uidPower = item.getPowerNanoAmpSecs();
            }
        }
        assertWithMessage(String.format("No power value for uid %d", uid)).that(uidFound).isTrue();
        assertWithMessage(String.format("Non-positive power value for uid %d", uid))
            .that(uidPower).isGreaterThan(0L);
    }

    public void testDavey() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!DAVEY_ENABLED ) return;
        long MAX_DURATION = 2000;
        long MIN_DURATION = 750;
        final int atomTag = Atom.DAVEY_OCCURRED_FIELD_NUMBER;
        createAndUploadConfig(atomTag, false); // UID is logged without attribution node

        runActivity("DaveyActivity", null, null);

        List<EventMetricData> data = getEventMetricDataList();
        assertThat(data).hasSize(1);
        long duration = data.get(0).getAtom().getDaveyOccurred().getJankDurationMillis();
        assertWithMessage("Incorrect jank duration")
            .that(duration).isIn(Range.closed(MIN_DURATION, MAX_DURATION));
    }

    public void testFlashlightState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_CAMERA_FLASH, true)) return;

        final int atomTag = Atom.FLASHLIGHT_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testFlashlight";

        Set<Integer> flashlightOn = new HashSet<>(
            Arrays.asList(FlashlightStateChanged.State.ON_VALUE));
        Set<Integer> flashlightOff = new HashSet<>(
            Arrays.asList(FlashlightStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(flashlightOn, flashlightOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        Thread.sleep(WAIT_TIME_SHORT);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getFlashlightStateChanged().getState().getNumber());
    }

    public void testForegroundServiceState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.FOREGROUND_SERVICE_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testForegroundService";

        Set<Integer> enterForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.ENTER_VALUE));
        Set<Integer> exitForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.EXIT_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(enterForeground, exitForeground);

        createAndUploadConfig(atomTag, false);
        Thread.sleep(WAIT_TIME_SHORT);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getForegroundServiceStateChanged().getState().getNumber());
    }

    public void testGpsScan() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_LOCATION_GPS, true)) return;
        // Whitelist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DEVICE_SIDE_TEST_PACKAGE));

        try {
            final int atom = Atom.GPS_SCAN_STATE_CHANGED_FIELD_NUMBER;
            final int key = GpsScanStateChanged.STATE_FIELD_NUMBER;
            final int stateOn = GpsScanStateChanged.State.ON_VALUE;
            final int stateOff = GpsScanStateChanged.State.OFF_VALUE;
            final int minTimeDiffMillis = 500;
            final int maxTimeDiffMillis = 60_000;

            List<EventMetricData> data = doDeviceMethodOnOff("testGpsScan", atom, key,
                    stateOn, stateOff, minTimeDiffMillis, maxTimeDiffMillis, true);

            GpsScanStateChanged a0 = data.get(0).getAtom().getGpsScanStateChanged();
            GpsScanStateChanged a1 = data.get(1).getAtom().getGpsScanStateChanged();
            assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
            assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }

    public void testGpsStatus() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_LOCATION_GPS, true)) return;
        // Whitelist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DEVICE_SIDE_TEST_PACKAGE));

        try {
            final int atom = Atom.GPS_LOCATION_STATUS_REPORTED_FIELD_NUMBER;

            createAndUploadConfig(atom);
            Thread.sleep(WAIT_TIME_SHORT);
            runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testGpsStatus");

            // Sorted list of events in order in which they occurred.
            List<EventMetricData> data = getEventMetricDataList();

            /*
             We will sleep for a minimum of 5 seconds and if time to first fix is at max we would
             wait for at most 90 seconds. Meaning we should see a minimum of 1 status message and a
             maximum of 90 status messages.
             */
            assertThat(data.size()).isAtLeast(1);
            assertThat(data.size()).isAtMost(90);
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }

    public void testGpsTimeToFirstFix() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_LOCATION_GPS, true)) return;
        // Whitelist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DEVICE_SIDE_TEST_PACKAGE));

        try {
            final int atom = Atom.GPS_TIME_TO_FIRST_FIX_REPORTED_FIELD_NUMBER;

            createAndUploadConfig(atom);
            Thread.sleep(WAIT_TIME_SHORT);
            runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testGpsStatus");

            // Sorted list of events in order in which they occurred.
            List<EventMetricData> data = getEventMetricDataList();

            assertThat(data.size()).isEqualTo(1);
            assertThat(data.get(0).getAtom().getGpsTimeToFirstFixReported()
                    .getTimeToFirstFixMillis()).isGreaterThan(0);
            assertThat(data.get(0).getAtom().getGpsTimeToFirstFixReported()
                    .getTimeToFirstFixMillis()).isAtMost(90_000);
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }

    public void testMediaCodecActivity() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WATCH, false)) return;
        final int atomTag = Atom.MEDIA_CODEC_STATE_CHANGED_FIELD_NUMBER;

        // 5 seconds. Starting video tends to be much slower than most other
        // tests on slow devices. This is unfortunate, because it leaves a
        // really big slop in assertStatesOccurred.  It would be better if
        // assertStatesOccurred had a tighter range on large timeouts.
        final int waitTime = 5000;

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        Thread.sleep(WAIT_TIME_SHORT);

        runActivity("VideoPlayerActivity", "action", "action.play_video",
            waitTime);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, waitTime,
                atom -> atom.getMediaCodecStateChanged().getState().getNumber());
    }

    public void testOverlayState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WATCH, false)) return;
        final int atomTag = Atom.OVERLAY_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> entered = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.ENTERED_VALUE));
        Set<Integer> exited = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.EXITED_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(entered, exited);

        createAndUploadConfig(atomTag, false);

        runActivity("StatsdCtsForegroundActivity", "action", "action.show_application_overlay",
                5_000);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        // The overlay box should appear about 2sec after the app start
        assertStatesOccurred(stateSet, data, 0,
                atom -> atom.getOverlayStateChanged().getState().getNumber());
    }

    public void testPictureInPictureState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        String supported = getDevice().executeShellCommand("am supports-multiwindow");
        if (!hasFeature(FEATURE_WATCH, false) ||
                !hasFeature(FEATURE_PICTURE_IN_PICTURE, true) ||
                !supported.contains("true")) {
            LogUtil.CLog.d("Skipping picture in picture atom test.");
            return;
        }

        final int atomTag = Atom.PICTURE_IN_PICTURE_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> entered = new HashSet<>(
                Arrays.asList(PictureInPictureStateChanged.State.ENTERED_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(entered);

        createAndUploadConfig(atomTag, false);

        LogUtil.CLog.d("Playing video in Picture-in-Picture mode");
        runActivity("VideoPlayerActivity", "action", "action.play_video_picture_in_picture_mode");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_LONG,
                atom -> atom.getPictureInPictureStateChanged().getState().getNumber());
    }

    public void testScheduledJobState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        String expectedName = "com.android.server.cts.device.statsd/.StatsdJobService";
        final int atomTag = Atom.SCHEDULED_JOB_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> jobSchedule = new HashSet<>(
                Arrays.asList(ScheduledJobStateChanged.State.SCHEDULED_VALUE));
        Set<Integer> jobOn = new HashSet<>(
                Arrays.asList(ScheduledJobStateChanged.State.STARTED_VALUE));
        Set<Integer> jobOff = new HashSet<>(
                Arrays.asList(ScheduledJobStateChanged.State.FINISHED_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(jobSchedule, jobOn, jobOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        allowImmediateSyncs();
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testScheduledJob");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        assertStatesOccurred(stateSet, data, 0,
                atom -> atom.getScheduledJobStateChanged().getState().getNumber());

        for (EventMetricData e : data) {
            assertThat(e.getAtom().getScheduledJobStateChanged().getJobName())
                .isEqualTo(expectedName);
        }
    }

    //Note: this test does not have uid, but must run on the device
    public void testScreenBrightness() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        int initialBrightness = getScreenBrightness();
        boolean isInitialManual = isScreenBrightnessModeManual();
        setScreenBrightnessMode(true);
        setScreenBrightness(200);
        Thread.sleep(WAIT_TIME_LONG);

        final int atomTag = Atom.SCREEN_BRIGHTNESS_CHANGED_FIELD_NUMBER;

        Set<Integer> screenMin = new HashSet<>(Arrays.asList(47));
        Set<Integer> screen100 = new HashSet<>(Arrays.asList(100));
        Set<Integer> screen200 = new HashSet<>(Arrays.asList(198));
        // Set<Integer> screenMax = new HashSet<>(Arrays.asList(255));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(screenMin, screen100, screen200);

        createAndUploadConfig(atomTag);
        Thread.sleep(WAIT_TIME_SHORT);
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testScreenBrightness");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Restore initial screen brightness
        setScreenBrightness(initialBrightness);
        setScreenBrightnessMode(isInitialManual);

        popUntilFind(data, screenMin, atom->atom.getScreenBrightnessChanged().getLevel());
        popUntilFindFromEnd(data, screen200, atom->atom.getScreenBrightnessChanged().getLevel());
        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
            atom -> atom.getScreenBrightnessChanged().getLevel());
    }
    public void testSyncState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.SYNC_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> syncOn = new HashSet<>(Arrays.asList(SyncStateChanged.State.ON_VALUE));
        Set<Integer> syncOff = new HashSet<>(Arrays.asList(SyncStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(syncOn, syncOff, syncOn, syncOff);

        createAndUploadConfig(atomTag, true);
        allowImmediateSyncs();
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testSyncState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getSyncStateChanged().getState().getNumber());
    }

    public void testVibratorState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!checkDeviceFor("checkVibratorSupported")) return;

        final int atomTag = Atom.VIBRATOR_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testVibratorState";

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(VibratorStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(VibratorStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        Thread.sleep(WAIT_TIME_SHORT);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", name);

        Thread.sleep(WAIT_TIME_LONG);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        assertStatesOccurred(stateSet, data, 300,
                atom -> atom.getVibratorStateChanged().getState().getNumber());
    }

    public void testWakelockState() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> wakelockOn = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.ACQUIRE_VALUE,
                WakelockStateChanged.State.CHANGE_ACQUIRE_VALUE));
        Set<Integer> wakelockOff = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.RELEASE_VALUE,
                WakelockStateChanged.State.CHANGE_RELEASE_VALUE));

        final String EXPECTED_TAG = "StatsdPartialWakelock";
        final WakeLockLevelEnum EXPECTED_LEVEL = WakeLockLevelEnum.PARTIAL_WAKE_LOCK;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(wakelockOn, wakelockOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testWakelockState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
            atom -> atom.getWakelockStateChanged().getState().getNumber());

        for (EventMetricData event: data) {
            String tag = event.getAtom().getWakelockStateChanged().getTag();
            WakeLockLevelEnum type = event.getAtom().getWakelockStateChanged().getType();
            assertThat(tag).isEqualTo(EXPECTED_TAG);
            assertThat(type).isEqualTo(EXPECTED_LEVEL);
        }
    }

    public void testWakeupAlarm() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        // For automotive, all wakeup alarm becomes normal alarm. So this
        // test does not work.
        if (!hasFeature(FEATURE_AUTOMOTIVE, false)) return;
        final int atomTag = Atom.WAKEUP_ALARM_OCCURRED_FIELD_NUMBER;

        StatsdConfig.Builder config = createConfigBuilder();
        addAtomEvent(config, atomTag, true);  // True: uses attribution.

        List<EventMetricData> data = doDeviceMethod("testWakeupAlarm", config);
        assertThat(data.size()).isAtLeast(1);
        for (int i = 0; i < data.size(); i++) {
            WakeupAlarmOccurred wao = data.get(i).getAtom().getWakeupAlarmOccurred();
            assertThat(wao.getTag()).isEqualTo("*walarm*:android.cts.statsd.testWakeupAlarm");
            assertThat(wao.getPackageName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
        }
    }

    public void testWifiLockHighPerf() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WIFI, true)) return;
        if (!hasFeature(FEATURE_PC, false)) return;

        final int atomTag = Atom.WIFI_LOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> lockOn = new HashSet<>(Arrays.asList(WifiLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(Arrays.asList(WifiLockStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testWifiLockHighPerf");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getWifiLockStateChanged().getState().getNumber());

        for (EventMetricData event : data) {
            assertThat(event.getAtom().getWifiLockStateChanged().getMode())
                .isEqualTo(WifiModeEnum.WIFI_MODE_FULL_HIGH_PERF);
        }
    }

    public void testWifiLockLowLatency() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WIFI, true)) return;
        if (!hasFeature(FEATURE_PC, false)) return;

        final int atomTag = Atom.WIFI_LOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> lockOn = new HashSet<>(Arrays.asList(WifiLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(Arrays.asList(WifiLockStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        createAndUploadConfig(atomTag, true);  // True: uses attribution.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testWifiLockLowLatency");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getWifiLockStateChanged().getState().getNumber());

        for (EventMetricData event : data) {
            assertThat(event.getAtom().getWifiLockStateChanged().getMode())
                .isEqualTo(WifiModeEnum.WIFI_MODE_FULL_LOW_LATENCY);
        }
    }

    public void testWifiMulticastLock() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WIFI, true)) return;
        if (!hasFeature(FEATURE_PC, false)) return;

        final int atomTag = Atom.WIFI_MULTICAST_LOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> lockOn = new HashSet<>(
                Arrays.asList(WifiMulticastLockStateChanged.State.ON_VALUE));
        Set<Integer> lockOff = new HashSet<>(
                Arrays.asList(WifiMulticastLockStateChanged.State.OFF_VALUE));

        final String EXPECTED_TAG = "StatsdCTSMulticastLock";

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(lockOn, lockOff);

        createAndUploadConfig(atomTag, true);
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testWifiMulticastLock");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        // Assert that the events happened in the expected order.
        assertStatesOccurred(stateSet, data, WAIT_TIME_SHORT,
                atom -> atom.getWifiMulticastLockStateChanged().getState().getNumber());

        for (EventMetricData event: data) {
            String tag = event.getAtom().getWifiMulticastLockStateChanged().getTag();
            assertThat(tag).isEqualTo(EXPECTED_TAG);
        }
    }

    public void testWifiScan() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        if (!hasFeature(FEATURE_WIFI, true)) return;

        final int atom = Atom.WIFI_SCAN_STATE_CHANGED_FIELD_NUMBER;
        final int key = WifiScanStateChanged.STATE_FIELD_NUMBER;
        final int stateOn = WifiScanStateChanged.State.ON_VALUE;
        final int stateOff = WifiScanStateChanged.State.OFF_VALUE;
        final int minTimeDiffMillis = 250;
        final int maxTimeDiffMillis = 60_000;
        final boolean demandExactlyTwo = false; // Two scans are performed, so up to 4 atoms logged.

        List<EventMetricData> data = doDeviceMethodOnOff("testWifiScan", atom, key,
                stateOn, stateOff, minTimeDiffMillis, maxTimeDiffMillis, demandExactlyTwo);

        assertThat(data.size()).isIn(Range.closed(2, 4));
        WifiScanStateChanged a0 = data.get(0).getAtom().getWifiScanStateChanged();
        WifiScanStateChanged a1 = data.get(1).getAtom().getWifiScanStateChanged();
        assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
        assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
    }

    public void testBinderStats() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        try {
            unplugDevice();
            Thread.sleep(WAIT_TIME_SHORT);
            enableBinderStats();
            binderStatsNoSampling();
            resetBinderStats();
            StatsdConfig.Builder config = getPulledConfig();
            addGaugeAtomWithDimensions(config, Atom.BINDER_CALLS_FIELD_NUMBER, null);

            uploadConfig(config);
            Thread.sleep(WAIT_TIME_SHORT);

            runActivity("StatsdCtsForegroundActivity", "action", "action.show_notification",3_000);

            setAppBreadcrumbPredicate();
            Thread.sleep(WAIT_TIME_SHORT);

            boolean found = false;
            int uid = getUid();
            List<Atom> atomList = getGaugeMetricDataList();
            for (Atom atom : atomList) {
                BinderCalls calls = atom.getBinderCalls();
                boolean classMatches = calls.getServiceClassName().contains(
                        "com.android.server.notification.NotificationManagerService");
                boolean methodMatches = calls.getServiceMethodName()
                        .equals("createNotificationChannels");

                if (calls.getUid() == uid && classMatches && methodMatches) {
                    found = true;
                    assertThat(calls.getRecordedCallCount()).isGreaterThan(0L);
                    assertThat(calls.getCallCount()).isGreaterThan(0L);
                    assertThat(calls.getRecordedTotalLatencyMicros())
                        .isIn(Range.open(0L, 1000000L));
                    assertThat(calls.getRecordedTotalCpuMicros()).isIn(Range.open(0L, 1000000L));
                }
            }

            assertWithMessage(String.format("Did not find a matching atom for uid %d", uid))
                .that(found).isTrue();

        } finally {
            disableBinderStats();
            plugInAc();
        }
    }

    public void testLooperStats() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        try {
            unplugDevice();
            setUpLooperStats();
            StatsdConfig.Builder config = getPulledConfig();
            addGaugeAtomWithDimensions(config, Atom.LOOPER_STATS_FIELD_NUMBER, null);
            uploadConfig(config);
            Thread.sleep(WAIT_TIME_SHORT);

            runActivity("StatsdCtsForegroundActivity", "action", "action.show_notification", 3_000);

            setAppBreadcrumbPredicate();
            Thread.sleep(WAIT_TIME_SHORT);

            List<Atom> atomList = getGaugeMetricDataList();

            boolean found = false;
            int uid = getUid();
            for (Atom atom : atomList) {
                LooperStats stats = atom.getLooperStats();
                String notificationServiceFullName =
                        "com.android.server.notification.NotificationManagerService";
                boolean handlerMatches =
                        stats.getHandlerClassName().equals(
                                notificationServiceFullName + "$WorkerHandler");
                boolean messageMatches =
                        stats.getMessageName().equals(
                                notificationServiceFullName + "$EnqueueNotificationRunnable");
                if (atom.getLooperStats().getUid() == uid && handlerMatches && messageMatches) {
                    found = true;
                    assertThat(stats.getMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedTotalLatencyMicros())
                        .isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedTotalCpuMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedMaxLatencyMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedMaxCpuMicros()).isIn(Range.open(0L, 1000000L));
                    assertThat(stats.getRecordedDelayMessageCount()).isGreaterThan(0L);
                    assertThat(stats.getRecordedTotalDelayMillis())
                        .isIn(Range.closedOpen(0L, 5000L));
                    assertThat(stats.getRecordedMaxDelayMillis()).isIn(Range.closedOpen(0L, 5000L));
                }
            }
            assertWithMessage(String.format("Did not find a matching atom for uid %d", uid))
                .that(found).isTrue();
        } finally {
            cleanUpLooperStats();
            plugInAc();
        }
    }

    public void testProcessMemoryState() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        // Get ProcessMemoryState as a simple gauge metric.
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.PROCESS_MEMORY_STATE_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        // Start test app.
        try (AutoCloseable a = withActivity("StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            Thread.sleep(WAIT_TIME_SHORT);
            // Trigger a pull and wait for new pull before killing the process.
            setAppBreadcrumbPredicate();
            Thread.sleep(WAIT_TIME_LONG);
        }

        // Assert about ProcessMemoryState for the test app.
        List<Atom> atoms = getGaugeMetricDataList();
        int uid = getUid();
        boolean found = false;
        for (Atom atom : atoms) {
            ProcessMemoryState state = atom.getProcessMemoryState();
            if (state.getUid() != uid) {
                continue;
            }
            found = true;
            assertThat(state.getProcessName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
            assertThat(state.getOomAdjScore()).isAtLeast(0);
            assertThat(state.getPageFault()).isAtLeast(0L);
            assertThat(state.getPageMajorFault()).isAtLeast(0L);
            assertThat(state.getRssInBytes()).isGreaterThan(0L);
            assertThat(state.getCacheInBytes()).isAtLeast(0L);
            assertThat(state.getSwapInBytes()).isAtLeast(0L);
        }
        assertWithMessage(String.format("Did not find a matching atom for uid %d", uid))
            .that(found).isTrue();
    }

    public void testProcessMemoryHighWaterMark() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        // Get ProcessMemoryHighWaterMark as a simple gauge metric.
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.PROCESS_MEMORY_HIGH_WATER_MARK_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = withActivity("StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            Thread.sleep(WAIT_TIME_SHORT);
            // Trigger a pull and wait for new pull before killing the process.
            setAppBreadcrumbPredicate();
            Thread.sleep(WAIT_TIME_LONG);
        }

        // Assert about ProcessMemoryHighWaterMark for the test app, statsd and system server.
        List<Atom> atoms = getGaugeMetricDataList();
        int uid = getUid();
        boolean foundTestApp = false;
        boolean foundStatsd = false;
        boolean foundSystemServer = false;
        for (Atom atom : atoms) {
            ProcessMemoryHighWaterMark state = atom.getProcessMemoryHighWaterMark();
            if (state.getUid() == uid) {
                foundTestApp = true;
                assertThat(state.getProcessName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            } else if (state.getProcessName().contains("/statsd")) {
                foundStatsd = true;
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            } else if (state.getProcessName().equals("system")) {
                foundSystemServer = true;
                assertThat(state.getRssHighWaterMarkInBytes()).isGreaterThan(0L);
            }
        }
        assertWithMessage(String.format("Did not find a matching atom for test app uid=%d",uid))
            .that(foundTestApp).isTrue();
        assertWithMessage("Did not find a matching atom for statsd").that(foundStatsd).isTrue();
        assertWithMessage("Did not find a matching atom for system server")
            .that(foundSystemServer).isTrue();
    }

    public void testProcessMemorySnapshot() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        // Get ProcessMemorySnapshot as a simple gauge metric.
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.PROCESS_MEMORY_SNAPSHOT_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        // Start test app and trigger a pull while it is running.
        try (AutoCloseable a = withActivity("StatsdCtsForegroundActivity", "action",
                "action.show_notification")) {
            setAppBreadcrumbPredicate();
            Thread.sleep(WAIT_TIME_LONG);
        }

        // Assert about ProcessMemorySnapshot for the test app, statsd and system server.
        List<Atom> atoms = getGaugeMetricDataList();
        int uid = getUid();
        boolean foundTestApp = false;
        boolean foundStatsd = false;
        boolean foundSystemServer = false;
        for (Atom atom : atoms) {
          ProcessMemorySnapshot snapshot = atom.getProcessMemorySnapshot();
          if (snapshot.getUid() == uid) {
              foundTestApp = true;
              assertThat(snapshot.getProcessName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
          } else if (snapshot.getProcessName().contains("/statsd")) {
              foundStatsd = true;
          } else if (snapshot.getProcessName().equals("system")) {
              foundSystemServer = true;
          }

          assertThat(snapshot.getPid()).isGreaterThan(0);
          assertThat(snapshot.getAnonRssAndSwapInKilobytes()).isGreaterThan(0);
          assertThat(snapshot.getAnonRssAndSwapInKilobytes()).isEqualTo(
                  snapshot.getAnonRssInKilobytes() + snapshot.getSwapInKilobytes());
          assertThat(snapshot.getRssInKilobytes()).isAtLeast(0);
          assertThat(snapshot.getAnonRssInKilobytes()).isAtLeast(0);
          assertThat(snapshot.getSwapInKilobytes()).isAtLeast(0);
        }
        assertWithMessage(String.format("Did not find a matching atom for test app uid=%d",uid))
            .that(foundTestApp).isTrue();
        assertWithMessage("Did not find a matching atom for statsd").that(foundStatsd).isTrue();
        assertWithMessage("Did not find a matching atom for system server")
            .that(foundSystemServer).isTrue();
    }

    /**
     * The the app id from a uid.
     *
     * @param uid The uid of the app
     *
     * @return The app id of the app
     *
     * @see android.os.UserHandle#getAppId
     */
    private static int getAppId(int uid) {
        return uid % 100000;
    }

    public void testRoleHolder() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        // Make device side test package a role holder
        String callScreenAppRole = "android.app.role.CALL_SCREENING";
        getDevice().executeShellCommand(
                "cmd role add-role-holder " + callScreenAppRole + " " + DEVICE_SIDE_TEST_PACKAGE);

        // Set up what to collect
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.ROLE_HOLDER_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        boolean verifiedKnowRoleState = false;

        // Pull a report
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_SHORT);

        int testAppId = getAppId(getUid());

        for (Atom atom : getGaugeMetricDataList()) {
            AtomsProto.RoleHolder roleHolder = atom.getRoleHolder();

            assertThat(roleHolder.getPackageName()).isNotNull();
            assertThat(roleHolder.getUid()).isAtLeast(0);
            assertThat(roleHolder.getRole()).isNotNull();

            if (roleHolder.getPackageName().equals(DEVICE_SIDE_TEST_PACKAGE)) {
                assertThat(getAppId(roleHolder.getUid())).isEqualTo(testAppId);
                assertThat(roleHolder.getPackageName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
                assertThat(roleHolder.getRole()).isEqualTo(callScreenAppRole);

                verifiedKnowRoleState = true;
            }
        }

        assertThat(verifiedKnowRoleState).isTrue();
    }

    public void testDangerousPermissionState() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED =  1 << 8;
        final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED =  1 << 9;

        // Set up what to collect
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.DANGEROUS_PERMISSION_STATE_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        boolean verifiedKnowPermissionState = false;

        // Pull a report
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_SHORT);

        int testAppId = getAppId(getUid());

        for (Atom atom : getGaugeMetricDataList()) {
            DangerousPermissionState permissionState = atom.getDangerousPermissionState();

            assertThat(permissionState.getPermissionName()).isNotNull();
            assertThat(permissionState.getUid()).isAtLeast(0);
            assertThat(permissionState.getPackageName()).isNotNull();

            if (getAppId(permissionState.getUid()) == testAppId) {

                if (permissionState.getPermissionName().equals(
                        "android.permission.ACCESS_FINE_LOCATION")) {
                    assertThat(permissionState.getIsGranted()).isTrue();
                    assertThat(permissionState.getPermissionFlags() & (~(
                            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
                            | FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED)))
                        .isEqualTo(0);

                    verifiedKnowPermissionState = true;
                }
            }
        }

        assertThat(verifiedKnowPermissionState).isTrue();
    }

    public void testAppOps() throws Exception {
        if (statsdDisabled()) {
            return;
        }

        // Set up what to collect
        StatsdConfig.Builder config = getPulledConfig();
        addGaugeAtomWithDimensions(config, Atom.APP_OPS_FIELD_NUMBER, null);
        uploadConfig(config);
        Thread.sleep(WAIT_TIME_SHORT);

        // Pull a report
        setAppBreadcrumbPredicate();
        Thread.sleep(WAIT_TIME_SHORT);

        int accessInstancesRecorded = 0;

        for (Atom atom : getGaugeMetricDataList()) {
            AppOps appOps = atom.getAppOps();
            accessInstancesRecorded += appOps.getTrustedForegroundGrantedCount();
        }

        assertThat(accessInstancesRecorded).isAtLeast(1);
    }

    public void testANROccurred() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.ANR_OCCURRED_FIELD_NUMBER;
        createAndUploadConfig(atomTag, false);
        Thread.sleep(WAIT_TIME_SHORT);

        try (AutoCloseable a = withActivity("ANRActivity", null, null)) {
            Thread.sleep(WAIT_TIME_SHORT);
            getDevice().executeShellCommand(
                    "am broadcast -a action_anr -p " + DEVICE_SIDE_TEST_PACKAGE);
            Thread.sleep(20_000);
        }

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasAnrOccurred()).isTrue();
        ANROccurred atom = data.get(0).getAtom().getAnrOccurred();
        assertThat(atom.getIsInstantApp().getNumber())
            .isEqualTo(ANROccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
            .isEqualTo(ANROccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertThat(atom.getPackageName()).isEqualTo(DEVICE_SIDE_TEST_PACKAGE);
    }

    public void testWriteRawTestAtom() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final int atomTag = Atom.TEST_ATOM_REPORTED_FIELD_NUMBER;
        createAndUploadConfig(atomTag, true);
        Thread.sleep(WAIT_TIME_SHORT);

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testWriteRawTestAtom");

        Thread.sleep(WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = getEventMetricDataList();
        assertThat(data).hasSize(4);

        TestAtomReported atom = data.get(0).getAtom().getTestAtomReported();
        List<AttributionNode> attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(1234);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");
        assertThat(attrChain.get(1).getUid()).isEqualTo(getUid());
        assertThat(attrChain.get(1).getTag()).isEqualTo("tag2");

        assertThat(atom.getIntField()).isEqualTo(42);
        assertThat(atom.getLongField()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(3.14f);
        assertThat(atom.getStringField()).isEqualTo("This is a basic test!");
        assertThat(atom.getBooleanField()).isFalse();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.ON_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
            .containsExactly(1L, 2L, 3L).inOrder();


        atom = data.get(1).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(9999);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag9999");
        assertThat(attrChain.get(1).getUid()).isEqualTo(getUid());
        assertThat(attrChain.get(1).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(100);
        assertThat(atom.getLongField()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(-2.5f);
        assertThat(atom.getStringField()).isEqualTo("Test null uid");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.UNKNOWN_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
            .containsExactly(1L, 2L, 3L).inOrder();

        atom = data.get(2).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(getUid());
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");

        assertThat(atom.getIntField()).isEqualTo(-256);
        assertThat(atom.getLongField()).isEqualTo(-1234567890L);
        assertThat(atom.getFloatField()).isEqualTo(42.01f);
        assertThat(atom.getStringField()).isEqualTo("Test non chained");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
            .containsExactly(1L, 2L, 3L).inOrder();

        atom = data.get(3).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(getUid());
        assertThat(attrChain.get(0).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(0);
        assertThat(atom.getLongField()).isEqualTo(0L);
        assertThat(atom.getFloatField()).isEqualTo(0f);
        assertThat(atom.getStringField()).isEmpty();
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList()).isEmpty();
    }

}
