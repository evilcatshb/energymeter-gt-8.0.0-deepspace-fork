package com.github.almostreliable.energymeter.compat.gtceu;

import java.util.Locale;

/** The source of GTCEu measurements returned by the existing Energy Meter peripheral. */
public enum GTMeasurementMode {
    INLINE,
    ADJACENT;

    /** Unknown or missing saved values safely migrate to the original inline behavior. */
    public static GTMeasurementMode fromPersisted(String value) {
        if (value == null) return INLINE;
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return INLINE;
        }
    }
}
