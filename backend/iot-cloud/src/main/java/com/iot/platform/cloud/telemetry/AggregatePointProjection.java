package com.iot.platform.cloud.telemetry;

interface AggregatePointProjection {

    Long getBucketEpoch();

    Double getAverage();

    Long getSampleCount();
}
