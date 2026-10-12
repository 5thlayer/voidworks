// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.gametest;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.voidworks.Voidworks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * A void machine for the game tests alone, {@code voidworks:dormant_test_machine}: it opts into
 * {@link Dormancy} as a real one does, and its work is counting the ticks it has worked. Only the
 * game test run registers it, and the game-test pack gives its cost.
 */
final class DormantTestMachine extends BaseEntityBlock {

    private static final MapCodec<DormantTestMachine> CODEC = simpleCodec(DormantTestMachine::new);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Voidworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Voidworks.MOD_ID);

    static final DeferredBlock<DormantTestMachine> BLOCK = BLOCKS.registerBlock("dormant_test_machine", DormantTestMachine::new);

    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Entity>> ENTITY =
            BLOCK_ENTITIES.register("dormant_test_machine", () -> new BlockEntityType<>(Entity::new, BLOCK.get()));

    DormantTestMachine(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Dormancy.DORMANT, true));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Dormancy.DORMANT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel) || type != ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, entity) -> ((Entity) entity).serverTick((ServerLevel) world);
    }

    /** The machine's block entity: Dormant until paid, then it works. */
    static final class Entity extends BlockEntity {

        private final Dormancy dormancy = new Dormancy(this);
        private int worked;

        Entity(BlockPos pos, BlockState state) {
            super(ENTITY.get(), pos, state);
        }

        Dormancy dormancy() {
            return dormancy;
        }

        /** @return the ticks it has worked, awake; not saved */
        int worked() {
            return worked;
        }

        void serverTick(ServerLevel level) {
            if (dormancy.soak(level)) {
                worked++;
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            dormancy.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            dormancy.load(input);
        }
    }
}
