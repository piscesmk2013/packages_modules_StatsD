/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.cts.statsd.metric;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.internal.os.StatsdConfigProto;
import com.android.internal.os.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.StatsdConfigProto.EventActivation;
import com.android.internal.os.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.StatsdConfigProto.SimpleAtomMatcher;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class MetricsUtils {
    public static final long COUNT_METRIC_ID = 3333;
    public static final long DURATION_METRIC_ID = 4444;
    public static final long GAUGE_METRIC_ID = 5555;
    public static final long VALUE_METRIC_ID = 6666;
    public static final long EVENT_METRIC_ID = 7777;

    public static AtomMatcher.Builder getAtomMatcher(int atomId) {
        AtomMatcher.Builder builder = AtomMatcher.newBuilder();
        builder.setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder()
                        .setAtomId(atomId));
        return builder;
    }

    public static AtomMatcher startAtomMatcher(int id) {
      return AtomMatcher.newBuilder()
          .setId(id)
          .setSimpleAtomMatcher(
              SimpleAtomMatcher.newBuilder()
                  .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                  .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                            .setField(AppBreadcrumbReported.STATE_FIELD_NUMBER)
                                            .setEqInt(AppBreadcrumbReported.State.START.ordinal())))
          .build();
    }

    public static AtomMatcher startAtomMatcherWithLabel(int id, int label) {
        return appBreadcrumbMatcherWithLabelAndState(id, label, AppBreadcrumbReported.State.START);
    }

    public static AtomMatcher stopAtomMatcher(int id) {
      return AtomMatcher.newBuilder()
          .setId(id)
          .setSimpleAtomMatcher(
              SimpleAtomMatcher.newBuilder()
                  .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                  .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                            .setField(AppBreadcrumbReported.STATE_FIELD_NUMBER)
                                            .setEqInt(AppBreadcrumbReported.State.STOP.ordinal())))
          .build();
    }

    public static AtomMatcher stopAtomMatcherWithLabel(int id, int label) {
        return appBreadcrumbMatcherWithLabelAndState(id, label, AppBreadcrumbReported.State.STOP);
    }

    public static AtomMatcher unspecifiedAtomMatcher(int id) {
        return AtomMatcher.newBuilder()
                .setId(id)
                .setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder()
                        .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                        .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                .setField(AppBreadcrumbReported.STATE_FIELD_NUMBER)
                                .setEqInt(AppBreadcrumbReported.State.UNSPECIFIED.ordinal())))
                .build();
    }

    public static AtomMatcher simpleAtomMatcher(int id) {
      return AtomMatcher.newBuilder()
          .setId(id)
          .setSimpleAtomMatcher(
              SimpleAtomMatcher.newBuilder().setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER))
          .build();
    }

    public static AtomMatcher appBreadcrumbMatcherWithLabel(int id, int label) {
        return AtomMatcher.newBuilder()
                .setId(id)
                .setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder()
                        .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                        .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                .setField(AppBreadcrumbReported.LABEL_FIELD_NUMBER)
                                .setEqInt(label)))
                .build();
    }

    public static AtomMatcher appBreadcrumbMatcherWithLabelAndState(int id, int label,
            final AppBreadcrumbReported.State state) {

        return AtomMatcher.newBuilder()
                .setId(id)
                .setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder()
                        .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                        .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                .setField(AppBreadcrumbReported.STATE_FIELD_NUMBER)
                                .setEqInt(state.ordinal()))
                        .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                                .setField(AppBreadcrumbReported.LABEL_FIELD_NUMBER)
                                .setEqInt(label)))
                .build();
    }

    public static AtomMatcher simpleAtomMatcher(int id, int label) {
      return AtomMatcher.newBuilder()
          .setId(id)
          .setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder()
                  .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                  .addFieldValueMatcher(FieldValueMatcher.newBuilder()
                            .setField(AppBreadcrumbReported.LABEL_FIELD_NUMBER)
                            .setEqInt(label)
                  )
          )
          .build();
    }

    public static EventActivation.Builder createEventActivation(int ttlSecs, int matcherId,
            int cancelMatcherId) {
        return EventActivation.newBuilder()
                .setAtomMatcherId(matcherId)
                .setTtlSeconds(ttlSecs)
                .setDeactivationAtomMatcherId(cancelMatcherId);
    }

    public static long StringToId(String str) {
      return str.hashCode();
    }

    public static void assertBucketTimePresent(Message bucketInfo) {
        Descriptor descriptor = bucketInfo.getDescriptorForType();
        boolean found = false;
        FieldDescriptor bucketNum = descriptor.findFieldByName("bucket_num");
        FieldDescriptor startMillis = descriptor.findFieldByName("start_bucket_elapsed_millis");
        FieldDescriptor endMillis = descriptor.findFieldByName("end_bucket_elapsed_millis");
        if (bucketNum != null && bucketInfo.hasField(bucketNum)) {
            found = true;
        } else if (startMillis != null && bucketInfo.hasField(startMillis) &&
                   endMillis != null && bucketInfo.hasField(endMillis)) {
            found = true;
        }
        assertWithMessage(
                "Bucket info did not have either bucket num or start and end elapsed millis"
        ).that(found).isTrue();
    }
}
