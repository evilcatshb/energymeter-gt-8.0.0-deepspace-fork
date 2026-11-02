package com.github.almostreliable.energymeter.compat.gtceu;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class GTTransferRouterTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static GTTransferRouter.Output output(boolean enabled, boolean connected, long capacity, AtomicLong accepted) {
        return new GTTransferRouter.Output() {
            @Override public boolean enabled() { return enabled; }
            @Override public boolean connected() { return connected; }
            @Override public long accept(long requested) {
                long moved = Math.min(requested, capacity);
                accepted.addAndGet(moved);
                return moved;
            }
        };
    }

    public static void main(String[] args) {
        AtomicLong received = new AtomicLong();
        long moved = GTTransferRouter.route(4, List.of(output(true, true, 2, received)));
        check(moved == 2 && received.get() == 2, "partial acceptance must report actual accepted amperage");

        received.set(0);
        moved = GTTransferRouter.route(5, List.of(output(true, true, 2, received), output(true, true, 3, received)));
        check(moved == 5 && received.get() == 5, "multiple outputs must receive only the remaining packet amperage");

        received.set(0);
        moved = GTTransferRouter.route(5, List.of(output(true, true, 2, received), output(true, true, 9, received)));
        check(moved == 5 && received.get() == 5, "later output must not receive more than remaining amperage");

        received.set(0);
        moved = GTTransferRouter.route(5, List.of(output(false, true, 5, received), output(true, false, 5, received)));
        check(moved == 0 && received.get() == 0, "disabled and disconnected sides must never receive energy");

        received.set(0);
        moved = GTTransferRouter.route(5, List.of(output(true, true, 0, received), output(true, true, 2, received)));
        check(moved == 2 && received.get() == 2, "a rejecting output must not prevent a later valid output");

        check(GTTransferRouter.route(0, List.of(output(true, true, 5, new AtomicLong()))) == 0,
                "zero requested amperage must not transfer");
        check(GTTransferRouter.route(5, List.of()) == 0, "no outputs must accept no amperage");
        System.out.println("GTTransferRouterTest: 7 assertions passed");
    }
}
