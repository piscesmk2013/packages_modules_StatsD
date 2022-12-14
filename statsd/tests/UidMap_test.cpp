// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "packages/UidMap.h"
#include "StatsLogProcessor.h"
#include "config/ConfigKey.h"
#include "guardrail/StatsdStats.h"
#include "logd/LogEvent.h"
#include "hash.h"
#include "statslog_statsdtest.h"
#include "statsd_test_util.h"

#include <android/util/ProtoOutputStream.h>
#include <gtest/gtest.h>

#include <stdio.h>

using namespace android;

namespace android {
namespace os {
namespace statsd {

using android::util::ProtoOutputStream;
using android::util::ProtoReader;

#ifdef __ANDROID__
const string kApp1 = "app1.sharing.1";
const string kApp2 = "app2.sharing.1";

TEST(UidMapTest, TestIsolatedUID) {
    sp<UidMap> m = new UidMap();
    sp<StatsPullerManager> pullerManager = new StatsPullerManager();
    sp<AlarmMonitor> anomalyAlarmMonitor;
    sp<AlarmMonitor> subscriberAlarmMonitor;
    // Construct the processor with a no-op sendBroadcast function that does nothing.
    StatsLogProcessor p(
            m, pullerManager, anomalyAlarmMonitor, subscriberAlarmMonitor, 0,
            [](const ConfigKey& key) { return true; },
            [](const int&, const vector<int64_t>&) { return true; });

    std::unique_ptr<LogEvent> addEvent = CreateIsolatedUidChangedEvent(
            1 /*timestamp*/, 100 /*hostUid*/, 101 /*isolatedUid*/, 1 /*is_create*/);
    EXPECT_EQ(101, m->getHostUidOrSelf(101));
    p.OnLogEvent(addEvent.get());
    EXPECT_EQ(100, m->getHostUidOrSelf(101));

    std::unique_ptr<LogEvent> removeEvent = CreateIsolatedUidChangedEvent(
            1 /*timestamp*/, 100 /*hostUid*/, 101 /*isolatedUid*/, 0 /*is_create*/);
    p.OnLogEvent(removeEvent.get());
    EXPECT_EQ(101, m->getHostUidOrSelf(101));
}

TEST(UidMapTest, TestMatching) {
    UidMap m;
    const vector<int32_t> uids{1000, 1000};
    const vector<int64_t> versions{4, 5};
    const vector<String16> versionStrings{String16("v1"), String16("v1")};
    const vector<String16> apps{String16(kApp1.c_str()), String16(kApp2.c_str())};
    const vector<String16> installers{String16(""), String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}, {}};

    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);
    EXPECT_TRUE(m.hasApp(1000, kApp1));
    EXPECT_TRUE(m.hasApp(1000, kApp2));
    EXPECT_FALSE(m.hasApp(1000, "not.app"));

    std::set<string> name_set = m.getAppNamesFromUid(1000u, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 2u);
    EXPECT_TRUE(name_set.find(kApp1) != name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());

    name_set = m.getAppNamesFromUid(12345, true /* returnNormalized */);
    EXPECT_TRUE(name_set.empty());
}

TEST(UidMapTest, TestAddAndRemove) {
    UidMap m;
    const vector<int32_t> uids{1000, 1000};
    const vector<int64_t> versions{4, 5};
    const vector<String16> versionStrings{String16("v1"), String16("v1")};
    const vector<String16> apps{String16(kApp1.c_str()), String16(kApp2.c_str())};
    const vector<String16> installers{String16(""), String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}, {}};

    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    std::set<string> name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 2u);
    EXPECT_TRUE(name_set.find(kApp1) != name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());

    // Update the app1 version.
    m.updateApp(2, String16(kApp1.c_str()), 1000, 40, String16("v40"), String16(""),
                /* certificateHash */ {});
    EXPECT_EQ(40, m.getAppVersion(1000, kApp1));

    name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 2u);
    EXPECT_TRUE(name_set.find(kApp1) != name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());

    m.removeApp(3, String16(kApp1.c_str()), 1000);
    EXPECT_FALSE(m.hasApp(1000, kApp1));
    EXPECT_TRUE(m.hasApp(1000, kApp2));
    name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 1u);
    EXPECT_TRUE(name_set.find(kApp1) == name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());

    // Remove app2.
    m.removeApp(4, String16(kApp2.c_str()), 1000);
    EXPECT_FALSE(m.hasApp(1000, kApp1));
    EXPECT_FALSE(m.hasApp(1000, kApp2));
    name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    EXPECT_TRUE(name_set.empty());
}

TEST(UidMapTest, TestUpdateApp) {
    UidMap m;
    m.updateMap(1, {1000, 1000}, {4, 5}, {String16("v4"), String16("v5")},
                {String16(kApp1.c_str()), String16(kApp2.c_str())}, {String16(""), String16("")},
                /* certificateHash */ {{}, {}});
    std::set<string> name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 2u);
    EXPECT_TRUE(name_set.find(kApp1) != name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());

    // Adds a new name for uid 1000.
    m.updateApp(2, String16("NeW_aPP1_NAmE"), 1000, 40, String16("v40"), String16(""),
                /* certificateHash */ {});
    name_set = m.getAppNamesFromUid(1000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 3u);
    EXPECT_TRUE(name_set.find(kApp1) != name_set.end());
    EXPECT_TRUE(name_set.find(kApp2) != name_set.end());
    EXPECT_TRUE(name_set.find("NeW_aPP1_NAmE") == name_set.end());
    EXPECT_TRUE(name_set.find("new_app1_name") != name_set.end());

    // This name is also reused by another uid 2000.
    m.updateApp(3, String16("NeW_aPP1_NAmE"), 2000, 1, String16("v1"), String16(""),
                /* certificateHash */ {});
    name_set = m.getAppNamesFromUid(2000, true /* returnNormalized */);
    ASSERT_EQ(name_set.size(), 1u);
    EXPECT_TRUE(name_set.find("NeW_aPP1_NAmE") == name_set.end());
    EXPECT_TRUE(name_set.find("new_app1_name") != name_set.end());
}

static void protoOutputStreamToUidMapping(ProtoOutputStream* proto, UidMapping* results) {
    vector<uint8_t> bytes;
    bytes.resize(proto->size());
    size_t pos = 0;
    sp<ProtoReader> reader = proto->data();
    while (reader->readBuffer() != NULL) {
        size_t toRead = reader->currentToRead();
        std::memcpy(&((bytes)[pos]), reader->readBuffer(), toRead);
        pos += toRead;
        reader->move(toRead);
    }
    results->ParseFromArray(bytes.data(), bytes.size());
}

// Test that uid map returns at least one snapshot even if we already obtained
// this snapshot from a previous call to getData.
TEST(UidMapTest, TestOutputIncludesAtLeastOneSnapshot) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);
    const vector<int32_t> uids{1000};
    const vector<int64_t> versions{5};
    const vector<String16> versionStrings{String16("v1")};
    const vector<String16> apps{String16(kApp2.c_str())};
    const vector<String16> installers{String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}};

    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    // Set the last timestamp for this config key to be newer.
    m.mLastUpdatePerConfigKey[config1] = 2;

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);

    // Check there's still a uidmap attached this one.
    UidMapping results;
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    EXPECT_EQ("v1", results.snapshots(0).package_info(0).version_string());
}

TEST(UidMapTest, TestRemovedAppRetained) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);
    const vector<int32_t> uids{1000};
    const vector<int64_t> versions{5};
    const vector<String16> versionStrings{String16("v5")};
    const vector<String16> apps{String16(kApp2.c_str())};
    const vector<String16> installers{String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}};

    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);
    m.removeApp(2, String16(kApp2.c_str()), 1000);

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);

    // Snapshot should still contain this item as deleted.
    UidMapping results;
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots(0).package_info_size());
    EXPECT_EQ(true, results.snapshots(0).package_info(0).deleted());
}

TEST(UidMapTest, TestRemovedAppOverGuardrail) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);
    vector<int32_t> uids;
    vector<int64_t> versions;
    vector<String16> versionStrings;
    vector<String16> installers;
    vector<String16> apps;
    vector<vector<uint8_t>> certificateHashes;
    const int maxDeletedApps = StatsdStats::kMaxDeletedAppsInUidMap;
    for (int j = 0; j < maxDeletedApps + 10; j++) {
        uids.push_back(j);
        apps.push_back(String16(kApp1.c_str()));
        versions.push_back(j);
        versionStrings.push_back(String16("v"));
        installers.push_back(String16(""));
        certificateHashes.push_back({});
    }
    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    // First, verify that we have the expected number of items.
    UidMapping results;
    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(maxDeletedApps + 10, results.snapshots(0).package_info_size());

    // Now remove all the apps.
    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);
    for (int j = 0; j < maxDeletedApps + 10; j++) {
        m.removeApp(4, String16(kApp1.c_str()), j);
    }

    proto.clear();
    m.appendUidMap(/* timestamp */ 5, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    // Snapshot drops the first nine items.
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(maxDeletedApps, results.snapshots(0).package_info_size());
}

TEST(UidMapTest, TestClearingOutput) {
    UidMap m;

    ConfigKey config1(1, StringToId("config1"));
    ConfigKey config2(1, StringToId("config2"));

    m.OnConfigUpdated(config1);

    const vector<int32_t> uids{1000, 1000};
    const vector<int64_t> versions{4, 5};
    const vector<String16> versionStrings{String16("v4"), String16("v5")};
    const vector<String16> apps{String16(kApp1.c_str()), String16(kApp2.c_str())};
    const vector<String16> installers{String16(""), String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}, {}};
    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    UidMapping results;
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());

    // We have to keep at least one snapshot in memory at all times.
    proto.clear();
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());

    // Now add another configuration.
    m.OnConfigUpdated(config2);
    m.updateApp(5, String16(kApp1.c_str()), 1000, 40, String16("v40"), String16(""),
                /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());
    proto.clear();
    m.appendUidMap(/* timestamp */ 6, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(1, results.changes_size());
    ASSERT_EQ(1U, m.mChanges.size());

    // Add another delta update.
    m.updateApp(7, String16(kApp2.c_str()), 1001, 41, String16("v41"), String16(""),
                /* certificateHash */ {});
    ASSERT_EQ(2U, m.mChanges.size());

    // We still can't remove anything.
    proto.clear();
    m.appendUidMap(/* timestamp */ 8, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(1, results.changes_size());
    ASSERT_EQ(2U, m.mChanges.size());

    proto.clear();
    m.appendUidMap(/* timestamp */ 9, config2, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    protoOutputStreamToUidMapping(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(2, results.changes_size());
    // At this point both should be cleared.
    ASSERT_EQ(0U, m.mChanges.size());
}

TEST(UidMapTest, TestMemoryComputed) {
    UidMap m;

    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    size_t startBytes = m.mBytesUsed;
    const vector<int32_t> uids{1000};
    const vector<int64_t> versions{1};
    const vector<String16> versionStrings{String16("v1")};
    const vector<String16> apps{String16(kApp1.c_str())};
    const vector<String16> installers{String16("")};
    const vector<vector<uint8_t>> certificateHashes{{}};
    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    m.updateApp(3, String16(kApp1.c_str()), 1000, 40, String16("v40"), String16(""),
                /* certificateHash */ {});

    ProtoOutputStream proto;
    vector<uint8_t> bytes;
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    size_t prevBytes = m.mBytesUsed;

    m.appendUidMap(/* timestamp */ 4, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    EXPECT_TRUE(m.mBytesUsed < prevBytes);
}

TEST(UidMapTest, TestMemoryGuardrail) {
    UidMap m;
    string buf;

    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    size_t startBytes = m.mBytesUsed;
    vector<int32_t> uids;
    vector<int64_t> versions;
    vector<String16> versionStrings;
    vector<String16> installers;
    vector<String16> apps;
    vector<vector<uint8_t>> certificateHashes;
    for (int i = 0; i < 100; i++) {
        uids.push_back(1);
        buf = "EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY." + to_string(i);
        apps.push_back(String16(buf.c_str()));
        versions.push_back(1);
        versionStrings.push_back(String16("v1"));
        installers.push_back(String16(""));
        certificateHashes.push_back({});
    }
    m.updateMap(1 /* timestamp */, uids, versions, versionStrings, apps, installers,
                certificateHashes);

    m.updateApp(3, String16("EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY.0"), 1000, 2,
                String16("v2"), String16(""), /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());

    // Now force deletion by limiting the memory to hold one delta change.
    m.maxBytesOverride = 120; // Since the app string alone requires >45 characters.
    m.updateApp(5, String16("EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY.0"), 1000, 4,
                String16("v4"), String16(""), /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
