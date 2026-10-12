// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import io.github._5thlayer.voidworks.VoidworksCapabilities;
import io.github._5thlayer.voidworks.pressure.VoidPressure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;

/**
 * The mote stores touching a void machine, which it spends from: any loaded block on one of its six
 * faces that provides {@code voidworks:motes}, a Voidstone network or a Consumer's machine. A network
 * touching it on several faces is found once per face.
 */
public final class TouchingMotes {

    /**
     * A mote store touching the machine.
     *
     * @param pos where the store stands, which its motes' butterflies fly from
     */
    public record Store(BlockPos pos, ResourceHandler<MoteResource> motes) {
    }

    private TouchingMotes() {
    }

    /** @return the mote stores touching {@code pos}, face by face */
    public static List<Store> at(ServerLevel level, BlockPos pos) {
        var stores = new ArrayList<Store>();
        for (var direction : Direction.values()) {
            var neighbour = pos.relative(direction);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            var motes = level.getCapability(VoidworksCapabilities.MOTES, neighbour, direction.getOpposite());
            if (motes != null) {
                stores.add(new Store(neighbour, motes));
            }
        }
        return stores;
    }

    /**
     * @return the most any one store touching {@code pos} is worth spent there, in void energy, or
     *         empty when none touches it
     */
    public static OptionalLong bestWorth(ServerLevel level, BlockPos pos) {
        int pressure = VoidPressure.at(level, pos);
        return at(level, pos).stream()
                .mapToLong(store -> VoidEnergy.worth(VoidSpender.countByGrade(store.motes()), pressure))
                .max();
    }

    /** @return for a player: what the best store touching it is worth there, or that none touches it */
    public static Component describe(OptionalLong bestWorth) {
        return bestWorth.isPresent()
                ? Component.translatable("voidworks.motes.touching", bestWorth.getAsLong())
                : Component.translatable("voidworks.motes.none_touching");
    }
}
