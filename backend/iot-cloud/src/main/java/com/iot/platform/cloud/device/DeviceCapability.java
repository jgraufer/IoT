package com.iot.platform.cloud.device;

import com.iot.platform.common.device.CapabilityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(
        name = "device_capability",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_capability",
                columnNames = {"device_id", "capability_type"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCapability {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_type", nullable = false, length = 32)
    private CapabilityType capabilityType;
}
