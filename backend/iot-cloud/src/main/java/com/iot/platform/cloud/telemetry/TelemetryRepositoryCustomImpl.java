package com.iot.platform.cloud.telemetry;

import com.iot.platform.common.device.CapabilityType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
class TelemetryRepositoryCustomImpl implements TelemetryRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<Telemetry> search(UUID edgeId, UUID deviceId, CapabilityType capability,
                                  Instant since, Instant until, int limit) {
        QTelemetry t = QTelemetry.telemetry;
        BooleanBuilder where = new BooleanBuilder(t.edgeId.eq(edgeId));
        if (deviceId != null) {
            where.and(t.deviceId.eq(deviceId));
        }
        if (capability != null) {
            where.and(t.capabilityType.eq(capability));
        }
        if (since != null) {
            where.and(t.timestamp.goe(since));
        }
        if (until != null) {
            where.and(t.timestamp.loe(until));
        }

        return new JPAQueryFactory(entityManager)
                .selectFrom(t)
                .where(where)
                .orderBy(t.timestamp.desc())
                .limit(limit)
                .fetch();
    }
}
