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
    private static final ModConfigSpec.IntValue SOAK_INTERVAL;
    private static final ModConfigSpec.IntValue MUTATION_INTERVAL;
    private static final ModConfigSpec.IntValue CRUCIBLE_TICKS;
    private static final ModConfigSpec.IntValue CRUCIBLE_RADIUS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> DENSITY_MOTES;
    private static final ModConfigSpec.IntValue SIPHON_CAPACITY;
    private static final ModConfigSpec.IntValue SIPHON_INTERVAL;
    private static final ModConfigSpec.IntValue SIPHON_RATE;

    static {
        var builder = new ModConfigSpec.Builder();
        ZONE = builder
                .comment("How many positions a new Void Displacer harvests before it becomes Voidstone.")
                .defineInRange("zone", 64, 1, 1 << 20);
        VOIDSTONE_CAPACITY = builder
                .comment("How many motes each Voidstone adds to its network's capacity: about one harvest at a middling",
                        "Density, so a Displacer's body holds its harvest. A harvest of more motes than this fits only in",
                        "a network with room to spare, and stalls the Displacer until it has.")
                .defineInRange("voidstoneCapacity", 16, 1, 1 << 20);
        HARVEST_INTERVAL = builder
                .comment("Ticks between a Void Displacer's harvests.")
                .defineInRange("harvestInterval", 20, 1, 24000);
        SOAK_INTERVAL = builder
                .comment("Ticks between a Dormant machine's soaks of its cost from the mote stores touching it.")
                .defineInRange("soakInterval", 20, 1, 24000);
        MUTATION_INTERVAL = builder
                .comment("Ticks between a Mutation Chamber's upgrades, each one result for its Rung's count of items.")
                .defineInRange("mutationInterval", 20, 1, 24000);
        CRUCIBLE_TICKS = builder
                .comment("Ticks a Void Crucible takes per void energy of a Rung's cost, alone at a Void Pressure of 3, the End's",
                        "by default: each step of pressure above halves the time and each below doubles it.")
                .defineInRange("crucibleTicks", 30, 1, 24000);
        CRUCIBLE_RADIUS = builder
                .comment("How near, in blocks, working Void Crucibles share one rate: each one's time is multiplied by the",
                        "working Crucibles within this distance of it on every axis, a cube, itself included.")
                .defineInRange("crucibleRadius", 8, 0, 64);
        DENSITY_MOTES = builder
                .comment("How many motes a fed block yields, by its Density from 1 to 4: steeply rising, about 4^(Density - 1).")
                .defineList("densityMotes", List.of(1, 4, 16, 64), () -> 1,
                        value -> value instanceof Integer motes && motes > 0);
        SIPHON_CAPACITY = builder
                .comment("How many motes a Void Siphon holds, of every grade together: a small fraction of a Void Well, enough",
                        "to start a base or pay a Dormant machine, never to supply one.")
                .defineInRange("siphonCapacity", 256, 1, 1 << 20);
        SIPHON_INTERVAL = builder
                .comment("Ticks between a Void Siphon's charges from, or emptyings into, the Voidstone its holder stands on.")
                .defineInRange("siphonInterval", 10, 1, 24000);
        SIPHON_RATE = builder
                .comment("How many motes a Void Siphon charges or empties each time, at most.")
                .defineInRange("siphonRate", 8, 1, 1 << 20);
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

    /** @return ticks between a Dormant machine's soaks */
    public static int soakInterval() {
        return SOAK_INTERVAL.getAsInt();
    }

    /** @return ticks between a Mutation Chamber's upgrades */
    public static int mutationInterval() {
        return MUTATION_INTERVAL.getAsInt();
    }

    /** @return ticks a lone Void Crucible takes per void energy of cost at a Void Pressure of 3 */
    public static int crucibleTicks() {
        return CRUCIBLE_TICKS.getAsInt();
    }

    /** @return how near, in blocks, working Void Crucibles share one rate */
    public static int crucibleRadius() {
        return CRUCIBLE_RADIUS.getAsInt();
    }

    /** @return how many motes a Void Siphon holds, of every grade together */
    public static int siphonCapacity() {
        return SIPHON_CAPACITY.getAsInt();
    }

    /** @return ticks between a Void Siphon's charges or emptyings on Voidstone */
    public static int siphonInterval() {
        return SIPHON_INTERVAL.getAsInt();
    }

    /** @return how many motes a Void Siphon charges or empties each time, at most */
    public static int siphonRate() {
        return SIPHON_RATE.getAsInt();
    }

    /** @return whether the server config is loaded, which the client has only on a server */
    public static boolean loaded() {
        return SPEC.isLoaded();
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
