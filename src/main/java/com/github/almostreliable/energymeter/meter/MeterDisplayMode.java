package com.github.almostreliable.energymeter.meter;

/** Visible measurement/display mode. Kept separate from the legacy energy-routing MODE. */
public enum MeterDisplayMode {
    TRANSFER, CONSUMER, GT_ADJACENT, GT_INLINE;
    public static MeterDisplayMode fromPersisted(String value, MeterDisplayMode fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return fallback; }
    }
    public MeterDisplayMode next() { return values()[(ordinal() + 1) % values().length]; }
    public boolean isGT() { return this == GT_ADJACENT || this == GT_INLINE; }
}