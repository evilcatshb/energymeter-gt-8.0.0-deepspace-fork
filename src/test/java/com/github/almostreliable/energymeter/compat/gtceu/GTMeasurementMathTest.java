package com.github.almostreliable.energymeter.compat.gtceu;

import com.github.almostreliable.energymeter.meter.MeterDisplayMode;

public final class GTMeasurementMathTest {
    private static final long[] VOLTAGES = {8, 32, 128, 512};
    private static final String[] TIERS = {"ULV", "LV", "MV", "HV"};

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check("ULV".equals(GTMeasurementMath.voltageTier(8, VOLTAGES, TIERS)), "8V should map to ULV");
        check("LV".equals(GTMeasurementMath.voltageTier(9, VOLTAGES, TIERS)), "9V should map to LV");
        check("HV".equals(GTMeasurementMath.voltageTier(512, VOLTAGES, TIERS)), "512V should map to HV");
        check("UNKNOWN".equals(GTMeasurementMath.voltageTier(0, VOLTAGES, TIERS)), "zero voltage must be unknown");
        check("UNKNOWN".equals(GTMeasurementMath.voltageTier(8, new long[]{8}, TIERS)), "mismatched tier arrays must be unknown");
        check(GTMeasurementMath.loadPercent(2, 4) == 50.0, "2/4 amps should be 50 percent");
        check(GTMeasurementMath.loadPercent(8, 4) == 200.0, "overload must be visible above 100 percent");
        check(GTMeasurementMath.loadPercent(2, -1) == -1.0, "unknown rating must use -1 sentinel");
        check(GTMeasurementMath.loadPercent(2, 0) == -1.0, "zero rating must use -1 sentinel");
        check(GTMeasurementMath.transferRate(512, 3) == 1536.0, "transfer rate must use accepted, not requested, amperage");
        check(GTMeasurementMath.transferRate(512, 0) == 0.0, "zero accepted amps means zero measured transfer");
        check(GTMeasurementMath.hasAdjacentReading(32, 0.05), "positive current-window voltage and average amps are valid");
        check(!GTMeasurementMath.hasAdjacentReading(0, 2.0), "no current-window voltage means no current reading");
        check(!GTMeasurementMath.hasAdjacentReading(32, 0.0), "no observed amperage means no current reading");
        check(GTMeasurementMode.fromPersisted("ADJACENT") == GTMeasurementMode.ADJACENT, "adjacent mode must survive NBT string round-trip");
        check(GTMeasurementMode.fromPersisted("INLINE") == GTMeasurementMode.INLINE, "inline mode must survive NBT string round-trip");
        check(GTMeasurementMode.fromPersisted(null) == GTMeasurementMode.INLINE, "missing mode migrates to inline");
        check(GTMeasurementMode.fromPersisted("corrupt") == GTMeasurementMode.INLINE, "invalid saved mode migrates safely to inline");
        check(GTMeasurementMath.transferRate(512, 3) == 1536.0, "rate is voltage times accepted amps, not voltage itself");
        check(!GTMeasurementMath.hasAdjacentReading(0, 1.0), "stale current-tick voltage invalidates a rolling average");
        check(MeterDisplayMode.TRANSFER.next() == MeterDisplayMode.CONSUMER, "display mode must cycle transfer to consumer");
        check(MeterDisplayMode.CONSUMER.next() == MeterDisplayMode.GT_ADJACENT, "display mode must cycle consumer to adjacent GT");
        check(MeterDisplayMode.GT_ADJACENT.next() == MeterDisplayMode.GT_INLINE, "display mode must cycle adjacent GT to inline GT");
        check(MeterDisplayMode.GT_INLINE.next() == MeterDisplayMode.TRANSFER, "display mode cycle must wrap to transfer");
        check(MeterDisplayMode.fromPersisted("gt_adjacent", MeterDisplayMode.TRANSFER) == MeterDisplayMode.GT_ADJACENT, "display mode parsing must be case-insensitive");
        check(MeterDisplayMode.fromPersisted("corrupt", MeterDisplayMode.CONSUMER) == MeterDisplayMode.CONSUMER, "invalid display mode must use supplied migration fallback");
        System.out.println("GTMeasurementMathTest: 26 assertions passed");
    }
}
