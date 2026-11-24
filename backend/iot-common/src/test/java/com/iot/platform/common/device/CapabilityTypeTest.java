package com.iot.platform.common.device;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityTypeTest {

    @Nested
    class IsValidType {

        @Test
        void powerSwitch_acceptsOnlyBoolean() {
            assertThat(CapabilityType.POWER_SWITCH.isValidType(true)).isTrue();
            assertThat(CapabilityType.POWER_SWITCH.isValidType(false)).isTrue();

            assertThat(CapabilityType.POWER_SWITCH.isValidType("true")).isFalse();
            assertThat(CapabilityType.POWER_SWITCH.isValidType(1)).isFalse();
            assertThat(CapabilityType.POWER_SWITCH.isValidType(null)).isFalse();
        }

        @Test
        void brightness_acceptsIntegerFamilyButNotFloating() {
            assertThat(CapabilityType.BRIGHTNESS.isValidType((byte) 10)).isTrue();
            assertThat(CapabilityType.BRIGHTNESS.isValidType((short) 50)).isTrue();
            assertThat(CapabilityType.BRIGHTNESS.isValidType(75)).isTrue();
            assertThat(CapabilityType.BRIGHTNESS.isValidType(100L)).isTrue();

            assertThat(CapabilityType.BRIGHTNESS.isValidType(75.0)).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isValidType(75.5f)).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isValidType("75")).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isValidType(true)).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isValidType(null)).isFalse();
        }

        @Test
        void temperature_acceptsAnyNumber() {
            assertThat(CapabilityType.TEMPERATURE.isValidType(25)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isValidType(25L)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isValidType(25.5f)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isValidType(25.5)).isTrue();

            assertThat(CapabilityType.TEMPERATURE.isValidType("25.5")).isFalse();
            assertThat(CapabilityType.TEMPERATURE.isValidType(true)).isFalse();
            assertThat(CapabilityType.TEMPERATURE.isValidType(null)).isFalse();
        }
    }

    @Nested
    class IsInRange {

        @Test
        void brightness_boundsAreInclusive() {
            assertThat(CapabilityType.BRIGHTNESS.isInRange(0)).isTrue();
            assertThat(CapabilityType.BRIGHTNESS.isInRange(50)).isTrue();
            assertThat(CapabilityType.BRIGHTNESS.isInRange(100)).isTrue();
        }

        @Test
        void brightness_rejectsOutOfRange() {
            assertThat(CapabilityType.BRIGHTNESS.isInRange(-1)).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isInRange(101)).isFalse();
            assertThat(CapabilityType.BRIGHTNESS.isInRange(1_000_000)).isFalse();
        }

        @Test
        void isInRange_rejectsNull() {
            assertThat(CapabilityType.BRIGHTNESS.isInRange(null)).isFalse();
            assertThat(CapabilityType.HUMIDITY.isInRange(null)).isFalse();
        }

        @Test
        void capabilitiesWithoutRange_acceptAnyNonNullValue() {
            assertThat(CapabilityType.TEMPERATURE.isInRange(-273.15)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isInRange(0.0)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isInRange(20)).isTrue();
            assertThat(CapabilityType.TEMPERATURE.isInRange(9_999.99)).isTrue();

            assertThat(CapabilityType.POWER_SWITCH.isInRange(true)).isTrue();
            assertThat(CapabilityType.POWER_SWITCH.isInRange(false)).isTrue();
        }

        @Test
        void humidity_floatValuesWithinIntegerBoundsAreAccepted() {
            assertThat(CapabilityType.HUMIDITY.isInRange(0.0)).isTrue();
            assertThat(CapabilityType.HUMIDITY.isInRange(45.5)).isTrue();
            assertThat(CapabilityType.HUMIDITY.isInRange(55.7)).isTrue();
            assertThat(CapabilityType.HUMIDITY.isInRange(100.0)).isTrue();

            assertThat(CapabilityType.HUMIDITY.isInRange(-0.1)).isFalse();
            assertThat(CapabilityType.HUMIDITY.isInRange(100.1)).isFalse();
        }
    }

    @Nested
    class Metadata {

        @Test
        void activeKinds_areOnlyPowerSwitchAndBrightness() {
            assertThat(CapabilityType.POWER_SWITCH.getKind()).isEqualTo(CapabilityKind.ACTIVE);
            assertThat(CapabilityType.BRIGHTNESS.getKind()).isEqualTo(CapabilityKind.ACTIVE);

            assertThat(CapabilityType.TEMPERATURE.getKind()).isEqualTo(CapabilityKind.PASSIVE);
            assertThat(CapabilityType.HUMIDITY.getKind()).isEqualTo(CapabilityKind.PASSIVE);
            assertThat(CapabilityType.MOTION_DETECTED.getKind()).isEqualTo(CapabilityKind.PASSIVE);
            assertThat(CapabilityType.BATTERY_LEVEL.getKind()).isEqualTo(CapabilityKind.PASSIVE);
        }

        @Test
        void unitsMatchExpectedCatalog() {
            assertThat(CapabilityType.POWER_SWITCH.getUnit()).isNull();
            assertThat(CapabilityType.BRIGHTNESS.getUnit()).isEqualTo("%");
            assertThat(CapabilityType.TEMPERATURE.getUnit()).isEqualTo("°C");
            assertThat(CapabilityType.HUMIDITY.getUnit()).isEqualTo("%");
            assertThat(CapabilityType.MOTION_DETECTED.getUnit()).isNull();
            assertThat(CapabilityType.BATTERY_LEVEL.getUnit()).isEqualTo("%");
        }
    }
}
