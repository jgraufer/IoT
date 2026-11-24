package com.iot.platform.edge.telemetry;

interface AggregatePointProjection {

    Long getBucketEpoch();

    Double getAverage();

    Long getSampleCount();
}
