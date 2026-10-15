// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.well.SavedVoidWells;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWells;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Riding a Void Dragon, its presence carrying its owner: they mount one of theirs stopped at one of
 * their Wells, with no harness, and it hovers there, its record held at its stop ({@link Routing}).
 * Strafing left and right steps through their other Wells, the one picked shown above the hotbar;
 * jumping dives there ({@link Routing#ride}), and sneaking gets off before the dive, onto the rim of
 * the Well it is at. Through the dive the presence holds its rider down the shaft, seeing nothing but
 * the void and seen by no one, where they cannot act, get off or be hurt; once its dragon has stopped
 * at the picked Well, in whatever dimension, it hands them to the presence emerging there, which
 * brings them up out of the shaft on its back and sets them down on that Well's rim. It is not free
 * flight. A rider who leaves the game is set down first, on the rim of the Well the dragon is
 * diving to, or else is at, so no presence is ever saved with them.
 */
public final class DragonRiding {

    /** How long a rider's unseeing lasts, renewed every tick of the dive. */
    private static final int VOID_TICKS = 40;

    private DragonRiding() {
    }

    public static void register() {
        // A rider in the dive cannot get off, but a presence that is gone, or sets them down, lets them go.
        NeoForge.EVENT_BUS.addListener((EntityMountEvent event) -> {
            if (event.isDismounting() && event.getEntityBeingMounted() instanceof VoidDragon presence && presence.holding() && !presence.isRemoved()) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> holdStill(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> holdStill(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> holdStill(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> holdStill(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> holdStill(event));
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent event) -> holdStill(event));
        // Before the server saves a leaving player, with any vehicle they ride.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity().getVehicle() instanceof VoidDragon presence && event.getEntity().level() instanceof ServerLevel level) {
                leave(level.getServer(), presence);
            }
        });
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
            if (held(event.getEntity())) {
                event.setCanceled(true);
            }
        });
    }

    /** A rider held through the dive, or coming up out of it, does nothing. */
    private static <E extends PlayerEvent & ICancellableEvent> void holdStill(E event) {
        if (held(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** @return whether {@code entity} rides a dragon that holds it, through its dive or coming up out of it */
    private static boolean held(Entity entity) {
        return entity.getVehicle() instanceof VoidDragon presence && presence.holding();
    }

    /** Its rider leaves the game: carried through a dive, they are set down where it dives to; hovering, they get off onto the rim. */
    private static void leave(MinecraftServer server, VoidDragon presence) {
        if (!presence.carrying()) {
            presence.release();
            presence.ejectPassengers();
            return;
        }
        var to = record(server, presence).flatMap(VoidDragonRecord::trip).flatMap(Trip::added).or(presence::shownAt);
        if (to.isPresent()) {
            setDown(server, presence, to.get());
        } else {
            presence.release();
            presence.ejectPassengers();
        }
    }

    /** @return whether {@code entity} rides a dragon through its dive */
    public static boolean inTheDive(Entity entity) {
        return entity.getVehicle() instanceof VoidDragon presence && presence.carrying();
    }

    /**
     * {@code player} uses the grown dragon's {@code presence}: its owner mounts it while it is stopped at
     * one of their Wells, flying about it rather than emerging or diving, and no one rides it.
     *
     * @return whether they mounted it
     */
    static boolean mount(ServerLevel level, VoidDragon presence, Player player) {
        var server = level.getServer();
        var dragon = record(server, presence);
        if (dragon.isEmpty() || !dragon.get().owner().equals(player.getUUID()) || presence.passing() || presence.isVehicle() || player.isPassenger()
                || dragon.get().trip().flatMap(Trip::divingTo).isPresent() || !DragonRouting.wellsOf(server, player.getUUID()).contains(dragon.get().rest())) {
            return false;
        }
        if (!player.startRiding(presence, false, true)) {
            return false;
        }
        presence.input = player instanceof ServerPlayer rider ? rider.getLastClientInput() : Input.EMPTY;
        presence.picked = null;
        player.sendOverlayMessage(Component.translatable("voidworks.dragon.ride.mounted"));
        return true;
    }

    /** Its rider's strafes step through their other Wells, and a jump dives to the one picked; each acts as it is pressed. */
    static void steer(ServerLevel level, VoidDragon presence, ServerPlayer rider) {
        var input = rider.getLastClientInput();
        var before = presence.input;
        presence.input = input;
        int step = (input.right() && !before.right() ? 1 : 0) - (input.left() && !before.left() ? 1 : 0);
        if (step != 0) {
            pick(level.getServer(), presence, rider, step);
        } else if (input.jump() && !before.jump()) {
            dive(level.getServer(), presence, rider);
        }
    }

    /** Picks the Well {@code step} on from the one picked, through the rider's Wells but the one it is at, round from the last to the first. */
    private static void pick(MinecraftServer server, VoidDragon presence, ServerPlayer rider, int step) {
        var dragon = record(server, presence);
        if (dragon.isEmpty()) {
            return;
        }
        var wells = DragonRouting.wellsOf(server, rider.getUUID()).stream().filter(place -> !place.equals(dragon.get().rest())).toList();
        if (wells.isEmpty()) {
            presence.picked = null;
            rider.sendOverlayMessage(Component.translatable("voidworks.dragon.ride.nowhere"));
            return;
        }
        int at = presence.picked == null ? -1 : wells.indexOf(presence.picked);
        presence.picked = wells.get(at == -1 ? (step > 0 ? 0 : wells.size() - 1) : Math.floorMod(at + step, wells.size()));
        rider.sendOverlayMessage(Component.translatable("voidworks.dragon.ride.picked", label(server, presence.picked)));
    }

    /** It dives to the Well picked, carrying its rider; with none picked, it tells them how to pick one. */
    private static void dive(MinecraftServer server, VoidDragon presence, ServerPlayer rider) {
        if (presence.picked == null) {
            rider.sendOverlayMessage(Component.translatable("voidworks.dragon.ride.mounted"));
            return;
        }
        var id = presence.dragonId();
        if (id.isEmpty() || DragonRouting.ride(server, id.get(), presence.picked).isEmpty()) {
            rider.sendOverlayMessage(Component.translatable("voidworks.dragon.ride.cannot", label(server, presence.picked)));
            return;
        }
        presence.carry();
    }

    /** Through the dive no one sees its rider, who sees only the void, which their own client draws. */
    static void hide(Entity rider) {
        if (rider instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VOID_TICKS, 0, false, false, false));
        }
    }

    /**
     * The dive over, the {@code presence} that carried its riders sets them down on the rim of the Well
     * at {@code place}, in its dimension, and is gone.
     */
    static void setDown(MinecraftServer server, VoidDragon presence, Place place) {
        presence.release();
        var level = place.level(server);
        for (var rider : List.copyOf(presence.getPassengers())) {
            rider.stopRiding();
            seeAgain(rider);
            if (level != null) {
                var rim = rim(level, place.pos());
                rider.teleport(new TeleportTransition(level, rim.at(), Vec3.ZERO, rim.yRot(), 0.0F, TeleportTransition.DO_NOTHING));
                rider.resetFallDistance();
            }
        }
        presence.discard();
    }

    /**
     * The dive over, the {@code carried} presence hands its riders to the {@code presence} emerging at
     * {@code emerges} in {@code level}, at the Well its dragon stopped at, and is gone: they come up out
     * of the shaft on its back, seeing again, and it sets them down on the rim once out.
     */
    static void handOver(VoidDragon carried, VoidDragon presence, ServerLevel level, BlockPos emerges) {
        carried.release();
        for (var rider : List.copyOf(carried.getPassengers())) {
            seeAgain(rider);
            var moved = rider.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(emerges), Vec3.ZERO, rider.getYRot(), 0.0F,
                    TeleportTransition.DO_NOTHING));
            if (moved != null) {
                moved.resetFallDistance();
                moved.startRiding(presence, true, false);
            }
        }
        carried.discard();
        presence.bring();
    }

    /** Out of the dive, its rider is seen again. */
    private static void seeAgain(Entity rider) {
        if (rider instanceof LivingEntity living) {
            living.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /**
     * @return where a rider is set down at the Well whose core is at {@code core}: standing on its rim
     *         over the core, facing into the shaft, or with the core broken, where its rim would be
     */
    static Rim rim(ServerLevel level, BlockPos core) {
        // The core is in its wall's middle course: the casing over it, then the rim, then the air.
        var on = Vec3.atBottomCenterOf(core.above(3));
        var state = level.getBlockState(core);
        return state.is(VoidworksBlocks.VOID_WELL.get()) ? new Rim(on, state.getValue(VoidWellBlock.FACING).getOpposite().toYRot()) : new Rim(on, 0.0F);
    }

    /** @return the record of the dragon whose {@code presence} it is, while there is one */
    private static Optional<VoidDragonRecord> record(MinecraftServer server, VoidDragon presence) {
        return presence.dragonId().flatMap(id -> SavedVoidDragons.of(server).get(id));
    }

    /** @return what the Well at {@code place} goes by: its name, or its dimension and position */
    public static Component label(MinecraftServer server, Place place) {
        return SavedVoidWells.of(server).at(place).map(VoidWells.Entry::label)
                .orElseGet(() -> VoidWells.label(Optional.empty(), place.dimension().toString(), place.pos()));
    }

    /**
     * Where a rider is set down.
     *
     * @param at where their feet are
     * @param yRot the way they face
     */
    record Rim(Vec3 at, float yRot) {
    }
}
