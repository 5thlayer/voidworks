// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Voidworks' server config, {@code voidworks-server.toml} in a world's {@code serverconfig}: the
 * harvest's numbers, every one provisional (docs/spec/void-energy.md, Numbers).
 */
public final class VoidworksConfig {

    /** How many Densities there are: 1 to 4, copper and coal to diamond and netherite. */
    public static final int DENSITIES = 4;

    static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue ZONE;
    private static final ModConfigSpec.IntValue VOIDSTONE_CAPACITY;
    private static final ModConfigSpec.IntValue HARVEST_INTERVAL;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> DENSITY_MOTES;

    static {
        var builder = new ModConfigSpec.Builder();
        ZONE = builder
                .comment("How many positions a new Overworld Void Displacer harvests before it becomes Voidstone.")
                .defineInRange("zone", 64, 1, 1 << 20);
        VOIDSTONE_CAPACITY = builder
                .comment("How many motes each Voidstone adds to its network's capacity: about one harvest at a middling",
                        "Density, so a Displacer's body holds its harvest. A harvest of more motes than this fits only in",
                        "a network with room to spare, and stalls the Displacer until it has.")
                .defineInRange("voidstoneCapacity", 16, 1, 1 << 20);
        HARVEST_INTERVAL = builder
                .comment("Ticks between an Overworld Void Displacer's harvests.")
                .defineInRange("harvestInterval", 20, 1, 24000);
        DENSITY_MOTES = builder
                .comment("How many motes a fed block yields, by its Density from 1 to 4: steeply rising, about 4^(Density - 1).")
                .defineList("densityMotes", List.of(1, 4, 16, 64), () -> 1,
                        value -> value instanceof Integer motes && motes > 0);
        SPEC = builder.build();
    }

    private VoidworksConfig() {
    }

    /** @return how many positions a new Displacer harvests in its life */
    public static int zone() {
        return ZONE.getAsInt();
    }

    /** @return how many motes a Voidstone adds to its network's capacity */
    public static int voidstoneCapacity() {
        return VOIDSTONE_CAPACITY.getAsInt();
    }

    /** @return ticks between a Displacer's harvests */
    public static int harvestInterval() {
        return HARVEST_INTERVAL.getAsInt();
    }

    /**
     * @param density from 1 to {@link #DENSITIES}
     * @return how many motes a fed block of {@code density} yields; a list too short for it repeats its last entry
     */
    public static int motes(int density) {
        var motes = DENSITY_MOTES.get();
        return motes.get(Math.min(density, motes.size()) - 1);
    }
}
