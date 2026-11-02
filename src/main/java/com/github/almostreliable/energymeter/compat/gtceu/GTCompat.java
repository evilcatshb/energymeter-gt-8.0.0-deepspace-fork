package com.github.almostreliable.energymeter.compat.gtceu;

import com.github.almostreliable.energymeter.meter.MeterEntity;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Isolated GTCEu bridge. This class is only invoked after GTCEu is confirmed loaded. */
public final class GTCompat {
    private static final String MOD_ID = "gtceu";
    private static final Map<MeterEntity, State> STATES = new WeakHashMap<>();
    private static final ThreadLocal<java.util.Set<MeterEntity>> FORWARDING = ThreadLocal.withInitial(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    private GTCompat() {}

    public static boolean isLoaded() { return ModList.get().isLoaded(MOD_ID); }

    private static State state(MeterEntity meter) {
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(meter, ignored -> new State(meter));
            GTMeasurementMode persisted = GTMeasurementMode.fromPersisted(meter.getGTMeasurementModeState());
            if (state.mode != persisted) {
                state.mode = persisted;
                state.valid = false;
                state.voltage = 0;
                state.successfulAmperage = 0;
                state.requestedAmperage = -1;
                state.transferRate = 0;
                state.maxAmperage = -1;
                state.maxVoltage = -1;
                state.sampleTime = -1;
                invalidateContainers(state);
                state.rebuildContainers();
            }
            return state;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> LazyOptional<T> getCapability(MeterEntity meter, Capability<T> capability, @Nullable Direction side) {
        if (!isLoaded() || capability != GTCapability.CAPABILITY_ENERGY_CONTAINER || side == null) return null;
        State state = state(meter);
        if (state.mode != GTMeasurementMode.INLINE || meter.getSideConfig().get(side) == com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.OFF) return null;
        return (LazyOptional<T>) state.containers.get(side);
    }

    public static void invalidate(MeterEntity meter) {
        synchronized (STATES) {
            State state = STATES.remove(meter);
            if (state != null) state.containers.values().forEach(LazyOptional::invalidate);
        }
    }

    public static String setMode(MeterEntity meter, String requested) {
        final GTMeasurementMode mode;
        if (requested == null) return "invalid mode; use INLINE or ADJACENT";
        try { mode = GTMeasurementMode.valueOf(requested.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return "invalid mode; use INLINE or ADJACENT"; }
        State state = state(meter);
        state.mode = mode;
        // GT routing must not inherit a previously selected legacy CONSUMER mode.
        meter.setMode(com.github.almostreliable.energymeter.util.TypeEnums.MODE.TRANSFER);
        meter.setGTMeasurementModeState(mode.name());
        state.valid = false;
        state.voltage = 0;
        state.successfulAmperage = 0;
        state.requestedAmperage = -1;
        state.transferRate = 0;
        state.maxAmperage = -1;
        state.maxVoltage = -1;
        state.sampleTime = -1;
        meter.clearGTSnapshot();
        invalidateContainers(state);
        state.rebuildContainers();
        meter.setChanged();
        meter.updateNeighbors();
        meter.setDisplayModeFromGT(state.mode.name());
        return state.mode.name();
    }

    private static void invalidateContainers(State state) {
        state.containers.values().forEach(LazyOptional::invalidate);
        state.containers.clear();
    }

    /** Refreshes the server-side GT reading and publishes a coherent snapshot to clients. */
    public static void refresh(MeterEntity meter) {
        if (!isLoaded() || meter.getLevel() == null || meter.getLevel().isClientSide) return;
        State s = state(meter);
        long now = meter.getLevel().getGameTime();
        if (s.mode == GTMeasurementMode.ADJACENT) sampleAdjacent(meter, s);
        else if (s.valid && (s.sampleTime < 0 || now - s.sampleTime > 20)) {
            s.valid = false; s.voltage = 0; s.successfulAmperage = 0; s.requestedAmperage = -1;
            s.transferRate = 0; s.sampleTime = now;
        }
        Map<String, Object> values = snapshot(meter);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("mode", String.valueOf(values.get("mode")));
        tag.putBoolean("valid", Boolean.TRUE.equals(values.get("valid")));
        tag.putLong("voltage", ((Number) values.get("voltage")).longValue());
        tag.putString("voltageTier", String.valueOf(values.get("voltageTier")));
        tag.putLong("amperage", ((Number) values.get("amperage")).longValue());
        tag.putLong("requestedAmperage", ((Number) values.get("requestedAmperage")).longValue());
        tag.putDouble("transferRate", ((Number) values.get("transferRate")).doubleValue());
        tag.putLong("maxAmperage", ((Number) values.get("maxAmperage")).longValue());
        tag.putLong("maxVoltage", ((Number) values.get("maxVoltage")).longValue());
        tag.putDouble("loadPercent", ((Number) values.get("loadPercent")).doubleValue());
        tag.putLong("worldTime", ((Number) values.get("worldTime")).longValue());
        if (!tag.equals(meter.getGTSnapshot())) {
            meter.setGTSnapshot(tag);
            meter.syncData(com.github.almostreliable.energymeter.core.Constants.SYNC_FLAGS.GT_SNAPSHOT);
        }
    }
    public static Map<String, Object> snapshot(MeterEntity meter) {
        State s = state(meter);
        if (s.mode == GTMeasurementMode.ADJACENT) sampleAdjacent(meter, s);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("mode", s.mode.name());
        out.put("valid", s.valid);
        out.put("voltage", s.valid ? s.voltage : 0L);
        out.put("voltageTier", s.valid ? voltageTier(s.voltage) : "UNKNOWN");
        out.put("amperage", s.valid ? s.successfulAmperage : 0L);
        out.put("requestedAmperage", s.mode == GTMeasurementMode.INLINE && s.valid ? s.requestedAmperage : -1L);
        out.put("transferRate", s.valid ? s.transferRate : 0.0);
        out.put("maxAmperage", s.maxAmperage);
        out.put("maxVoltage", s.maxVoltage);
        out.put("loadPercent", s.valid ? GTMeasurementMath.loadPercent(s.successfulAmperage, s.maxAmperage) : -1.0);
        out.put("worldTime", s.sampleTime);
        return out;
    }

    public static void recordTransfer(MeterEntity meter, long voltage, long requested, long accepted) {
        State s = state(meter);
        if (s.mode != GTMeasurementMode.INLINE) return;
        s.voltage = voltage;
        s.requestedAmperage = requested;
        s.successfulAmperage = accepted;
        s.transferRate = GTMeasurementMath.transferRate(voltage, accepted);
        s.valid = true;
        s.sampleTime = meter.getLevel() == null ? -1 : meter.getLevel().getGameTime();
        s.maxAmperage = -1;
        s.maxVoltage = -1;
        if (meter.getLevel() != null) {
            for (int pass = 0; pass < 2 && s.maxAmperage < 0; pass++) {
                for (Direction direction : Direction.values()) {
                    var setting = meter.getSideConfig().get(direction);
                    if (pass == 0 && setting != com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.IN) continue;
                    if (pass == 1 && setting != com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.OUT) continue;
                    BlockEntity adjacent = meter.getLevel().getBlockEntity(meter.getBlockPos().relative(direction));
                    if (adjacent instanceof CableBlockEntity cable) {
                        s.maxAmperage = cable.getMaxAmperage();
                        s.maxVoltage = cable.getMaxVoltage();
                        break;
                    }
                }
            }
        }
    }

    private static void sampleAdjacent(MeterEntity meter, State s) {
        if (meter.getLevel() == null || meter.getLevel().isClientSide) return;
        CableBlockEntity selected = null;
        for (int pass = 0; pass < 2 && selected == null; pass++) {
            for (Direction direction : Direction.values()) {
                var sideSetting = meter.getSideConfig().get(direction);
                if (pass == 0 && sideSetting != com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.IN) continue;
                if (pass == 1 && sideSetting != com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.OUT) continue;
                var adjacentPos = meter.getBlockPos().relative(direction);
                if (!meter.getLevel().hasChunkAt(adjacentPos)) continue;
                BlockEntity be = meter.getLevel().getBlockEntity(adjacentPos);
                if (be instanceof CableBlockEntity cable) { selected = cable; break; }
            }
        }
        if (selected == null) {
            s.valid = false; s.voltage = 0; s.successfulAmperage = 0; s.requestedAmperage = -1;
            s.transferRate = 0; s.maxAmperage = -1; s.maxVoltage = -1; s.sampleTime = meter.getLevel().getGameTime();
            return;
        }
        s.voltage = selected.getCurrentMaxVoltage();
        double averageAmperage = selected.getAverageAmperage();
        s.successfulAmperage = Math.max(0L, Math.round(averageAmperage));
        s.requestedAmperage = -1;
        // GTCEu averages voltage * amperage over its rolling 20-tick window (EU/t).
        s.transferRate = selected.getAverageVoltage();
        s.maxAmperage = selected.getMaxAmperage();
        s.maxVoltage = selected.getMaxVoltage();
        s.valid = GTMeasurementMath.hasAdjacentReading(s.voltage, averageAmperage);
        if (!s.valid) {
            s.voltage = 0;
            s.successfulAmperage = 0;
            s.transferRate = 0;
        }
        s.sampleTime = meter.getLevel().getGameTime();
    }

    private static String voltageTier(long voltage) {
        return GTMeasurementMath.voltageTier(voltage, GTValues.V, GTValues.VN);
    }

    private static final class State {
        final MeterEntity meter;
        final EnumMap<Direction, LazyOptional<IEnergyContainer>> containers = new EnumMap<>(Direction.class);
        GTMeasurementMode mode = GTMeasurementMode.INLINE;
        boolean valid;
        long voltage, successfulAmperage, requestedAmperage = -1, maxAmperage = -1, maxVoltage = -1, sampleTime = -1;
        double transferRate;
        State(MeterEntity meter) { this.meter = meter; this.mode = "ADJACENT".equals(meter.getGTMeasurementModeState()) ? GTMeasurementMode.ADJACENT : GTMeasurementMode.INLINE; rebuildContainers(); }
        void rebuildContainers() {
            for (Direction side : Direction.values()) containers.put(side, LazyOptional.of(() -> new MeterEnergyContainer(this, side)));
        }
    }

    private static final class MeterEnergyContainer implements IEnergyContainer {
        private final State state;
        private final Direction side;
        MeterEnergyContainer(State state, Direction side) { this.state = state; this.side = side; }
        @Override public long acceptEnergyFromNetwork(Direction fromSide, long voltage, long amperage) {
            if (voltage <= 0 || amperage <= 0 || state.mode != GTMeasurementMode.INLINE ||
                    state.meter.getLevel() == null || state.meter.getLevel().isClientSide || FORWARDING.get().contains(state.meter)) return 0;
            if (state.meter.getSideConfig().get(side) != com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.IN) return 0;
            java.util.List<GTTransferRouter.Output> outputs = new java.util.ArrayList<>();
            for (Direction outputSide : Direction.values()) {
                boolean configuredOutput = state.meter.getSideConfig().get(outputSide) ==
                        com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.OUT;
                BlockEntity target = configuredOutput
                        ? state.meter.getLevel().getBlockEntity(state.meter.getBlockPos().relative(outputSide)) : null;
                IEnergyContainer destination = target == null || target == state.meter ? null :
                        target.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, outputSide.getOpposite()).orElse(null);
                outputs.add(new GTTransferRouter.Output() {
                    @Override public boolean enabled() {
                        return state.meter.getSideConfig().get(outputSide) ==
                                com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.OUT;
                    }
                    @Override public boolean connected() {
                        return destination != null && destination.inputsEnergy(outputSide.getOpposite());
                    }
                    @Override public long accept(long requestedAmperage) {
                        return destination.acceptEnergyFromNetwork(outputSide.getOpposite(), voltage, requestedAmperage);
                    }
                });
            }
            long accepted;
            FORWARDING.get().add(state.meter);
            try {
                accepted = GTTransferRouter.route(amperage, outputs);
            } finally {
                FORWARDING.get().remove(state.meter);
                if (FORWARDING.get().isEmpty()) FORWARDING.remove();
            }
            if (accepted > 0) {
                recordTransfer(state.meter, voltage, amperage, accepted);
                state.meter.recordGTTransfer(voltage, accepted);
            } else {
                recordTransfer(state.meter, voltage, amperage, 0);
            }
            return accepted;
        }
        @Override public boolean inputsEnergy(Direction direction) {
            return state.mode == GTMeasurementMode.INLINE && direction == side &&
                    state.meter.getSideConfig().get(side) == com.github.almostreliable.energymeter.util.TypeEnums.IO_SETTING.IN;
        }
        @Override public boolean outputsEnergy(Direction direction) { return false; }
        @Override public long changeEnergy(long amount) { return 0; }
        @Override public long getEnergyStored() { return 0; }
        @Override public long getEnergyCapacity() { return Long.MAX_VALUE; }
        @Override public long getEnergyCanBeInserted() { return Long.MAX_VALUE; }
        @Override public long getInputAmperage() { return Long.MAX_VALUE; }
        @Override public long getInputVoltage() { return Long.MAX_VALUE; }
    }
}
