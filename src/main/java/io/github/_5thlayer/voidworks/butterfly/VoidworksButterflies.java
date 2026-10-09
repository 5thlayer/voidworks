// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.butterfly;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.energy.VoidEnergy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The butterflies harvested motes are seen as: the particle type whose sprites they are drawn with,
 * the {@link MoteFlight} payload that shows them, and {@code /voidworks butterflies <grade> <count>},
 * an operator's way to show them before any harvest exists. The client draws them
 * ({@code client.VoidworksClient}).
 */
public final class VoidworksButterflies {

    /** The most motes one {@code /voidworks butterflies} shows. */
    static final int MAX_COUNT = 4096;

    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Voidworks.MOD_ID);

    /** The butterfly's sprite frames, {@code assets/voidworks/particles/mote_butterfly.json}. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE_BUTTERFLY =
            PARTICLE_TYPES.register("mote_butterfly", () -> new SimpleParticleType(false));

    private VoidworksButterflies() {
    }

    public static void register(IEventBus modBus) {
        PARTICLE_TYPES.register(modBus);
        modBus.addListener(VoidworksButterflies::registerPayloads);
        NeoForge.EVENT_BUS.addListener(VoidworksButterflies::registerCommands);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The client registers the handler; a dedicated server only sends. Optional, so a client
        // without Voidworks can still join and sees nothing.
        event.registrar("1").optional().playToClient(MoteFlight.TYPE, MoteFlight.STREAM_CODEC);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voidworks")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("butterflies")
                        .then(Commands.argument("grade", IntegerArgumentType.integer(VoidEnergy.MIN_GRADE, VoidEnergy.MAX_GRADE))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                        .executes(VoidworksButterflies::butterflies)))));
    }

    /** Shows the motes flying to the player from four blocks ahead of them. @return how many butterflies */
    private static int butterflies(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int grade = IntegerArgumentType.getInteger(context, "grade");
        int count = IntegerArgumentType.getInteger(context, "count");
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().multiply(4, 0, 4));
        int butterflies = MoteFlight.toEntity(player.level(), ahead, player, grade, count);
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.voidworks.butterflies", butterflies, count, grade), false);
        return butterflies;
    }
}
