// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.dragon;

import io.github._5thlayer.voidworks.Voidworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An item seen moving between a Well hatch and a Matter-line dragon's paws ({@link Passing}), sent to the
 * players watching its presence as it starts: from the top of the hatch, arcing over the Well's rim, to
 * the paws of the presence {@code dragon}, following it, or the other way. It is cosmetic: the hatch was
 * emptied or filled as the stop began, and nothing moves on the server.
 *
 * @param hatch the middle of the hatch's top
 * @param over the point over the rim its arc bends toward
 * @param dragon the presence's entity id
 * @param stack what is seen moving
 * @param loading whether it moves from the hatch to the paws, else from the paws into the hatch
 * @param elapsed how many ticks it has been on its way already, for a presence shown partway through
 */
public record PassingShown(Vec3 hatch, Vec3 over, int dragon, ItemStack stack, boolean loading, int elapsed) implements CustomPacketPayload {

    public static final Type<PassingShown> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "passing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PassingShown> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, PassingShown::hatch,
            Vec3.STREAM_CODEC, PassingShown::over,
            ByteBufCodecs.VAR_INT, PassingShown::dragon,
            ItemStack.STREAM_CODEC, PassingShown::stack,
            ByteBufCodecs.BOOL, PassingShown::loading,
            ByteBufCodecs.VAR_INT, PassingShown::elapsed,
            PassingShown::new);

    /** How far above the rim's top an item's arc bends, in blocks. */
    private static final double OVER_THE_RIM = 1.5;

    /** Shows {@code passing} moving between its hatch and {@code dragon}'s paws to the players watching it, {@code elapsed} ticks on. */
    static void send(VoidDragon dragon, Passing passing, boolean loading, int elapsed) {
        var hatch = Vec3.atBottomCenterOf(passing.hatch().pos().above());
        var mouth = dragon.mouth();
        var over = new Vec3((hatch.x + mouth.x) / 2, mouth.y + OVER_THE_RIM, (hatch.z + mouth.z) / 2);
        var stack = StackWares.stack(passing.ware(), dragon.registryAccess());
        PacketDistributor.sendToPlayersTrackingEntity(dragon, new PassingShown(hatch, over, dragon.getId(), stack, loading, elapsed));
    }

    @Override
    public Type<PassingShown> type() {
        return TYPE;
    }
}
