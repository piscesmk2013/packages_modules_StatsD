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

#pragma once

#include <android/os/IPullAtomCallback.h>
#include <android/os/IStatsCompanionService.h>
#include <binder/IServiceManager.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <list>
#include <vector>

#include "PullDataReceiver.h"
#include "StatsPuller.h"
#include "guardrail/StatsdStats.h"
#include "logd/LogEvent.h"

namespace android {
namespace os {
namespace statsd {


typedef struct PullerKey {
    // The uid of the process that registers this puller.
    const int uid = -1;
    // The atom that this puller is for.
    const int atomTag;

    bool operator<(const PullerKey& that) const {
        if (uid < that.uid) {
            return true;
        }
        if (uid > that.uid) {
            return false;
        }
        return atomTag < that.atomTag;
    };

    bool operator==(const PullerKey& that) const {
        return uid == that.uid && atomTag == that.atomTag;
    };
} PullerKey;

class StatsPullerManager : public virtual RefBase {
public:
    StatsPullerManager();

    virtual ~StatsPullerManager() {
    }

    // Registers a receiver for tagId. It will be pulled on the nextPullTimeNs
    // and then every intervalNs thereafter.
    virtual void RegisterReceiver(int tagId, wp<PullDataReceiver> receiver, int64_t nextPullTimeNs,
                                  int64_t intervalNs);

    // Stop listening on a tagId.
    virtual void UnRegisterReceiver(int tagId, wp<PullDataReceiver> receiver);

    // Verify if we know how to pull for this matcher
    bool PullerForMatcherExists(int tagId) const;

    void OnAlarmFired(int64_t elapsedTimeNs);

    // Pulls the most recent data.
    // The data may be served from cache if consecutive pulls come within
    // mCoolDownNs.
    // Returns true if the pull was successful.
    // Returns false when
    //   1) the pull fails
    //   2) pull takes longer than mPullTimeoutNs (intrinsic to puller)
    // If the metric wants to make any change to the data, like timestamps, they
    // should make a copy as this data may be shared with multiple metrics.
    virtual bool Pull(int tagId, vector<std::shared_ptr<LogEvent>>* data);

    // Clear pull data cache immediately.
    int ForceClearPullerCache();

    // Clear pull data cache if it is beyond respective cool down time.
    int ClearPullerCacheIfNecessary(int64_t timestampNs);

    void SetStatsCompanionService(sp<IStatsCompanionService> statsCompanionService);

    void RegisterPullAtomCallback(const int uid, const int32_t atomTag, const int64_t coolDownNs,
                                  const int64_t timeoutNs, const vector<int32_t>& additiveFields,
                                  const sp<IPullAtomCallback>& callback);

    void UnregisterPullAtomCallback(const int uid, const int32_t atomTag);

    std::map<const PullerKey, sp<StatsPuller>> kAllPullAtomInfo;

private:
    sp<IStatsCompanionService> mStatsCompanionService = nullptr;

    typedef struct {
        int64_t nextPullTimeNs;
        int64_t intervalNs;
        wp<PullDataReceiver> receiver;
    } ReceiverInfo;

    // mapping from simple matcher tagId to receivers
    std::map<int, std::list<ReceiverInfo>> mReceivers;

    bool PullLocked(int tagId, vector<std::shared_ptr<LogEvent>>* data);

    // locks for data receiver and StatsCompanionService changes
    Mutex mLock;

    void updateAlarmLocked();

    int64_t mNextPullTimeNs;

    FRIEND_TEST(GaugeMetricE2eTest, TestRandomSamplePulledEvents);
    FRIEND_TEST(GaugeMetricE2eTest, TestRandomSamplePulledEvent_LateAlarm);
    FRIEND_TEST(GaugeMetricE2eTest, TestRandomSamplePulledEventsWithActivation);
    FRIEND_TEST(GaugeMetricE2eTest, TestRandomSamplePulledEventsNoCondition);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents_LateAlarm);
    FRIEND_TEST(ValueMetricE2eTest, TestPulledEvents_WithActivation);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
