// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.displacer;

import io.github._5thlayer.voidworks.VoidworksBlocks;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.pressure.HarvestKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Nether Void Displacer: it displaces the lava sources touching it or the Voidstone it has
 * left, one fed block each, so its body grows along a lava lake, and each source's bucket of lava
 * goes into its tank, which its fluid face, the same on every side, lets any pipe, tank or bucket
 * drain; nothing comes back to its item output. A full tank stalls it. It is placed Dormant, and
 * harvests only once it has soaked its cost.
 */
public final class NetherDisplacerBlockEntity extends DisplacerBlockEntity implements Dormancy.Holder {

    /** How much lava its tank holds, provisional: sixteen buckets. */
    public static final int TANK_CAPACITY = 16 * FluidType.BUCKET_VOLUME;

    private static final String TANK_KEY = "tank";
    private static final FluidResource LAVA = FluidResource.of(Fluids.LAVA);

    private final Dormancy dormancy = new Dormancy(this);
    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, TANK_CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final ResourceHandler<FluidResource> fluidFace = new FluidFace();

    public NetherDisplacerBlockEntity(BlockPos pos, BlockState state) {
        super(VoidworksBlocks.NETHER_DISPLACER_ENTITY.get(), pos, state);
    }

    @Override
    public Dormancy dormancy() {
        return dormancy;
    }

    /** Its tank, for the game tests. */
    public FluidStacksResourceHandler tank() {
        return tank;
    }

    /** The fluid capability, the same on every side: it gives the tank's lava and takes nothing. */
    public ResourceHandler<FluidResource> fluidFace() {
        return fluidFace;
    }

    @Override
    protected HarvestKind kind() {
        return HarvestKind.NETHER;
    }

    @Override
    protected boolean harvestable(BlockState state) {
        var fluid = state.getFluidState();
        return state.getBlock() instanceof LiquidBlock && fluid.is(FluidTags.LAVA) && fluid.isSource();
    }

    @Override
    protected ItemStack displaced(BlockState harvested, int density) {
        return ItemStack.EMPTY;
    }

    /** A lava source fills a bucket's worth of its tank. */
    @Override
    protected boolean toTank(BlockState harvested, TransactionContext transaction) {
        return tank.insert(0, LAVA, FluidType.BUCKET_VOLUME, transaction) == FluidType.BUCKET_VOLUME;
    }

    @Override
    protected boolean ready(ServerLevel level) {
        return dormancy.soak(level);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        dormancy.save(output);
        tank.serialize(output.child(TANK_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dormancy.load(input);
        tank.deserialize(input.childOrEmpty(TANK_KEY));
    }

    /** The fluid capability: extracts come from the tank, and nothing is inserted. */
    private final class FluidFace implements ResourceHandler<FluidResource> {

        @Override
        public int size() {
            return tank.size();
        }

        @Override
        public FluidResource getResource(int index) {
            return tank.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return tank.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return tank.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return tank.extract(index, resource, amount, transaction);
        }
    }
}
