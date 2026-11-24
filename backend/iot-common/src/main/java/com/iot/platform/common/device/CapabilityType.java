package com.iot.platform.common.device;

public enum CapabilityType {

    POWER_SWITCH    (CapabilityKind.ACTIVE,  CapabilityDataType.BOOLEAN, null, null, null),
    BRIGHTNESS      (CapabilityKind.ACTIVE,  CapabilityDataType.INTEGER, "%",  0,    100),
    TEMPERATURE     (CapabilityKind.PASSIVE, CapabilityDataType.FLOAT,   "°C", null, null),
    HUMIDITY        (CapabilityKind.PASSIVE, CapabilityDataType.FLOAT,   "%",  0,    100),
    MOTION_DETECTED (CapabilityKind.PASSIVE, CapabilityDataType.BOOLEAN, null, null, null),
    BATTERY_LEVEL   (CapabilityKind.PASSIVE, CapabilityDataType.INTEGER, "%",  0,    100);

    private final CapabilityKind kind;
    private final CapabilityDataType dataType;
    private final String unit;
    private final Integer min;
    private final Integer max;

    CapabilityType(CapabilityKind kind, CapabilityDataType dataType,
                   String unit, Integer min, Integer max) {
        this.kind = kind;
        this.dataType = dataType;
        this.unit = unit;
        this.min = min;
        this.max = max;
    }

    public CapabilityKind getKind()             { return kind; }
    public CapabilityDataType getDataType()     { return dataType; }
    public String getUnit()                     { return unit; }
    public Integer getMin()                     { return min; }
    public Integer getMax()                     { return max; }

    public boolean isValidType(Object value) {
        if (value == null) {
            return false;
        }
        return switch (dataType) {
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Byte
                         || value instanceof Short
                         || value instanceof Integer
                         || value instanceof Long;
            case FLOAT   -> value instanceof Byte
                         || value instanceof Short
                         || value instanceof Integer
                         || value instanceof Long
                         || value instanceof Float
                         || value instanceof Double;
        };
    }

    public boolean isInRange(Object value) {
        if (min == null || max == null) return true;
        if (value == null) return false;
        double v = ((Number) value).doubleValue();
        return v >= min && v <= max;
    }
}
