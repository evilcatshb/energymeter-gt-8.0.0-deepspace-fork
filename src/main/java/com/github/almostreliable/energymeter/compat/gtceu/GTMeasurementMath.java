package com.github.almostreliable.energymeter.compat.gtceu;

public final class GTMeasurementMath {
    private GTMeasurementMath() {}

    public static double loadPercent(long measuredAmperage, long ratedAmperage) {
        if (measuredAmperage < 0 || ratedAmperage <= 0) return -1.0;
        return 100.0 * measuredAmperage / ratedAmperage;
    }

    public static double transferRate(long voltage, long acceptedAmperage) {
        if (voltage <= 0 || acceptedAmperage <= 0) return 0.0;
        return (double) voltage * acceptedAmperage;
    }

    public static boolean hasAdjacentReading(long voltage, double averageAmperage) {
        return voltage > 0 && averageAmperage > 0.0;
    }

    public static String voltageTier(long voltage, long[] tierVoltages, String[] tierNames) {
        if (voltage <= 0 || tierVoltages == null || tierNames == null || tierVoltages.length == 0 || tierVoltages.length != tierNames.length) return "UNKNOWN";
        for (int i = 0; i < tierVoltages.length; i++) if (voltage <= tierVoltages[i]) return tierNames[i];
        return tierNames[tierNames.length - 1];
    }
}
