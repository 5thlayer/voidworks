// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import java.util.List;

import io.github._5thlayer.voidworks.mutation.UnmakingYield;
import io.github._5thlayer.voidworks.siphon.SiphonTier;
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
    private static final ModConfigSpec.IntValue WELL_CAPACITY;
    private static final ModConfigSpec.IntValue CONDUCTION_INTERVAL;
    private static final ModConfigSpec.IntValue CONDUCTION_RATE;
    private static final ModConfigSpec.IntValue HARVEST_INTERVAL;
    private static final ModConfigSpec.IntValue SOAK_INTERVAL;
    private static final ModConfigSpec.IntValue MUTATION_INTERVAL;
    private static final ModConfigSpec.DoubleValue UNMAKING_YIELD_START;
    private static final ModConfigSpec.DoubleValue UNMAKING_YIELD_CAP;
    private static final ModConfigSpec.IntValue UNMAKING_YIELD_HALVING;
    private static final ModConfigSpec.IntValue CRUCIBLE_TICKS;
    private static final ModConfigSpec.IntValue CRUCIBLE_RADIUS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> DENSITY_MOTES;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SIPHON_CAPACITY;
    private static final ModConfigSpec.IntValue SIPHON_INTERVAL;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SIPHON_RATE;
    private static final ModConfigSpec.LongValue FE_PER_VOID_ENERGY;
    private static final ModConfigSpec.LongValue FIRST_EGG_PRICE;
    private static final ModConfigSpec.IntValue DRAGONLING_GROW_TICKS;
    private static final ModConfigSpec.LongValue DRAGONLING_FEED_ENERGY;
    private static final ModConfigSpec.IntValue DRAGON_LOAD;
    private static final ModConfigSpec.IntValue DRAGON_STOP_TICKS;
    private static final ModConfigSpec.IntValue DRAGON_DIVE_TICKS;
    private static final ModConfigSpec.IntValue DRAGON_DIVE_BLOCKS_PER_SECOND;
    private static final ModConfigSpec.IntValue DRAGON_WEAKEN_TICKS;
    private static final ModConfigSpec.IntValue DRAGON_HEAL_MOTES;
    private static final ModConfigSpec.LongValue DRAGON_EGG_MOTES;

    static {
        var builder = new ModConfigSpec.Builder();
        ZONE = builder
                .comment("How many positions a new Void Displacer harvests before it becomes Voidstone.")
                .defineInRange("zone", 64, 1, 1 << 20);
        VOIDSTONE_CAPACITY = builder
                .comment("How many motes each Voidstone holds: about one harvest at a middling Density, so a Displacer's body",
                        "holds its harvest. A harvest of more motes than this settles into the nearest Voidstone with room,",
                        "and one its network has no room for stalls the Displacer until it has.")
                .defineInRange("voidstoneCapacity", 16, 1, 1 << 20);
        WELL_CAPACITY = builder
                .comment("How many motes a Void Well holds, of every grade together: many times a Void Siphon's top tier.")
                .defineInRange("wellCapacity", 65_536, 1, 1 << 30);
        CONDUCTION_INTERVAL = builder
                .comment("Ticks between steps of conduction, in which motes move one Voidstone through their network.")
                .defineInRange("conductionInterval", 10, 1, 24000);
        CONDUCTION_RATE = builder
                .comment("How many motes of each grade cross a face between two Voidstone, or a Voidstone and a Void Well,",
                        "in one step of conduction, at most.")
                .defineInRange("conductionRate", 4, 1, 1 << 20);
        HARVEST_INTERVAL = builder
                .comment("Ticks between a Void Displacer's harvests.")
                .defineInRange("harvestInterval", 20, 1, 24000);
        SOAK_INTERVAL = builder
                .comment("Ticks between a Dormant machine's soaks of its cost from the mote stores touching it.")
                .defineInRange("soakInterval", 20, 1, 24000);
        MUTATION_INTERVAL = builder
                .comment("Ticks between a Mutation Chamber's upgrades, each one result for its Rung's count of items.")
                .defineInRange("mutationInterval", 20, 1, 24000);
        UNMAKING_YIELD_START = builder
                .comment("The chance each ingredient item comes back from an Unmaking Chamber that has spent nothing on breakdowns.")
                .defineInRange("unmakingYieldStart", 0.25, 0.0, 1.0);
        UNMAKING_YIELD_CAP = builder
                .comment("What an Unmaking Chamber's yield rises toward as it spends on breakdowns, and never reaches; below",
                        "the start, the yield stays at the start.")
                .defineInRange("unmakingYieldCap", 0.75, 0.0, 1.0);
        UNMAKING_YIELD_HALVING = builder
                .comment("The void energy an Unmaking Chamber spends on breakdowns to close half the gap from its yield to the cap.")
                .defineInRange("unmakingYieldHalving", 25_600, 1, Integer.MAX_VALUE);
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
                .comment("How many motes a Void Siphon holds, of every grade together, by tier: the Void Siphon, the Resonant",
                        "and the Draconic. Even the top is a small fraction of a Void Well, enough to start a base or pay a",
                        "Dormant machine, never to supply one.")
                .defineList("siphonCapacity", SiphonTier.DEFAULT_CAPACITIES, () -> 1,
                        value -> value instanceof Integer motes && motes > 0);
        SIPHON_INTERVAL = builder
                .comment("Ticks between a Void Siphon's charges from, or emptyings into, the Voidstone its holder stands on.")
                .defineInRange("siphonInterval", 10, 1, 24000);
        SIPHON_RATE = builder
                .comment("How many motes a Void Siphon charges or empties each time, at most, by tier: the Void Siphon, the",
                        "Resonant and the Draconic.")
                .defineList("siphonRate", SiphonTier.DEFAULT_RATES, () -> 1,
                        value -> value instanceof Integer motes && motes > 0);
        FE_PER_VOID_ENERGY = builder
                .comment("The FE each void energy a Void Siphon releases charges into the FE items its holder wears or holds,",
                        "any mod's. Provisional, tuned against Gearworks' Gear, whose battery upgrade holds 100,000 FE;",
                        "balance it against the FE mods in the pack.")
                .defineInRange("fePerVoidEnergy", 10L, 1L, 1L << 20);
        FIRST_EGG_PRICE = builder
                .comment("The void energy a player's first dragon egg soaks before it hatches. Each egg's price is fixed when it",
                        "starts soaking, at this doubled for every dragon its owner has hatched or is soaking.")
                .defineInRange("firstEggPrice", 25_600L, 1L, Long.MAX_VALUE);
        DRAGONLING_GROW_TICKS = builder
                .comment("Ticks a Dragonling takes to grow into a Void Dragon, counted only while its chunk is loaded.")
                .defineInRange("dragonlingGrowTicks", 48_000, 1, Integer.MAX_VALUE);
        DRAGONLING_FEED_ENERGY = builder
                .comment("The void energy its owner feeds a Dragonling, from a Void Siphon, that takes a minute off its growing.")
                .defineInRange("dragonlingFeedEnergy", 1_000L, 1L, Long.MAX_VALUE);
        DRAGON_LOAD = builder
                .comment("How many motes a Void Dragon carries on a trip, of every grade together, at most.")
                .defineInRange("dragonLoad", 8_192, 1, 1 << 30);
        DRAGON_STOP_TICKS = builder
                .comment("Ticks a Void Dragon stops at a Void Well to load or unload.")
                .defineInRange("dragonStopTicks", 100, 0, 24000);
        DRAGON_DIVE_TICKS = builder
                .comment("Ticks any dive of a Void Dragon's through the void between two Void Wells takes, however near.")
                .defineInRange("dragonDiveTicks", 200, 0, 24000);
        DRAGON_DIVE_BLOCKS_PER_SECOND = builder
                .comment("How many blocks apart two Void Wells are, in Overworld blocks, for each second a dive between them",
                        "takes beyond the fixed time: a Nether block counts eight, an End block one.")
                .defineInRange("dragonDiveBlocksPerSecond", 64, 1, 1 << 20);
        DRAGON_WEAKEN_TICKS = builder
                .comment("Ticks in which a Void Dragon diving between Void Wells loses a point of its health for each step the",
                        "Void Pressure at the worse of the dive's two ends is below the End's: twice as fast to or from the Nether",
                        "as in the Overworld, and not at all between End Wells. Hurt, it flies more slowly, down to a quarter of its",
                        "speed; its health never drops below 1.")
                .defineInRange("dragonWeakenTicks", 200, 1, Integer.MAX_VALUE);
        DRAGON_HEAL_MOTES = builder
                .comment("Motes a Void Dragon takes from a Void Well before it dives from it, lowest grade first, to heal a point",
                        "of health.")
                .defineInRange("dragonHealMotes", 16, 1, 1 << 20);
        DRAGON_EGG_MOTES = builder
                .comment("Motes a grown Void Dragon carries, counted as it unloads them at Void Wells, for each Drained Dragon Egg",
                        "it lays at a Well it stops at.")
                .defineInRange("dragonEggMotes", 65_536L, 1L, Long.MAX_VALUE);
        SPEC = builder.build();
    }

    private VoidworksConfig() {
    }

    /** @return how many positions a new Displacer harvests in its life */
    public static int zone() {
        return ZONE.getAsInt();
    }

    /** @return how many motes a Voidstone holds */
    public static int voidstoneCapacity() {
        return VOIDSTONE_CAPACITY.getAsInt();
    }

    /** @return how many motes a Void Well holds */
    public static int wellCapacity() {
        return WELL_CAPACITY.getAsInt();
    }

    /** @return ticks between steps of conduction */
    public static int conductionInterval() {
        return CONDUCTION_INTERVAL.getAsInt();
    }

    /** @return how many motes of a grade cross one face in a step of conduction, at most */
    public static int conductionRate() {
        return CONDUCTION_RATE.getAsInt();
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

    /** @return an Unmaking Chamber's yield: where it starts, what it rises toward, and how fast */
    public static UnmakingYield unmakingYield() {
        double start = UNMAKING_YIELD_START.getAsDouble();
        return new UnmakingYield(start, Math.max(start, UNMAKING_YIELD_CAP.getAsDouble()), UNMAKING_YIELD_HALVING.getAsInt());
    }

    /** @return ticks a lone Void Crucible takes per void energy of cost at a Void Pressure of 3 */
    public static int crucibleTicks() {
        return CRUCIBLE_TICKS.getAsInt();
    }

    /** @return how near, in blocks, working Void Crucibles share one rate */
    public static int crucibleRadius() {
        return CRUCIBLE_RADIUS.getAsInt();
    }

    /** @return how many motes a Void Siphon of {@code tier} holds, of every grade together */
    public static int siphonCapacity(SiphonTier tier) {
        return tier.of(SIPHON_CAPACITY.get());
    }

    /** @return ticks between a Void Siphon's charges or emptyings on Voidstone */
    public static int siphonInterval() {
        return SIPHON_INTERVAL.getAsInt();
    }

    /** @return how many motes a Void Siphon of {@code tier} charges or empties each time, at most */
    public static int siphonRate(SiphonTier tier) {
        return tier.of(SIPHON_RATE.get());
    }

    /** @return the FE each void energy a Void Siphon releases charges into an FE item */
    public static long fePerVoidEnergy() {
        return FE_PER_VOID_ENERGY.getAsLong();
    }

    /** @return the void energy a player's first dragon egg soaks before it hatches */
    public static long firstEggPrice() {
        return FIRST_EGG_PRICE.getAsLong();
    }

    /** @return ticks a Dragonling takes to grow up, while its chunk is loaded */
    public static int dragonlingGrowTicks() {
        return DRAGONLING_GROW_TICKS.getAsInt();
    }

    /** @return the void energy fed to a Dragonling that takes a minute off its growing */
    public static long dragonlingFeedEnergy() {
        return DRAGONLING_FEED_ENERGY.getAsLong();
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

    /** @return the most motes a Void Dragon carries on a trip */
    public static int dragonLoad() {
        return DRAGON_LOAD.getAsInt();
    }

    /** @return ticks a Void Dragon stops at a Well to load or unload */
    public static int dragonStopTicks() {
        return DRAGON_STOP_TICKS.getAsInt();
    }

    /** @return ticks any dive takes, however near */
    public static int dragonDiveTicks() {
        return DRAGON_DIVE_TICKS.getAsInt();
    }

    /** @return Overworld blocks apart for each second a dive takes beyond its fixed time */
    public static int dragonDiveBlocksPerSecond() {
        return DRAGON_DIVE_BLOCKS_PER_SECOND.getAsInt();
    }

    /** @return ticks in which a diving Void Dragon loses a point of health for each step of pressure below the End's */
    public static int dragonWeakenTicks() {
        return DRAGON_WEAKEN_TICKS.getAsInt();
    }

    /** @return motes a Void Dragon takes from a Well before it dives from it to heal a point of health */
    public static int dragonHealMotes() {
        return DRAGON_HEAL_MOTES.getAsInt();
    }

    /** @return motes a Void Dragon carries for each Drained Dragon Egg it lays */
    public static long dragonEggMotes() {
        return DRAGON_EGG_MOTES.getAsLong();
    }
}
