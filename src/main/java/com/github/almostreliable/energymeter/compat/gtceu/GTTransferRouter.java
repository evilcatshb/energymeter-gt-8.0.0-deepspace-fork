package com.github.almostreliable.energymeter.compat.gtceu;

import java.util.List;

/**
 * Routes a GT packet across candidate output sides while preserving the source packet's voltage.
 * Each output is invoked at most once; reported amperage is clamped to the remaining packet.
 */
public final class GTTransferRouter {
    private GTTransferRouter() {}

    public interface Output {
        boolean enabled();
        boolean connected();
        long accept(long requestedAmperage);
    }

    public static long route(long requestedAmperage, List<? extends Output> outputs) {
        if (requestedAmperage <= 0 || outputs == null || outputs.isEmpty()) return 0;
        long remaining = requestedAmperage;
        long accepted = 0;
        for (Output output : outputs) {
            if (remaining <= 0) break;
            if (output == null || !output.enabled() || !output.connected()) continue;
            long moved = output.accept(remaining);
            if (moved <= 0) continue;
            moved = Math.min(moved, remaining);
            accepted += moved;
            remaining -= moved;
        }
        return accepted;
    }
}
