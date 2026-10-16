// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.displacer.EndDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.NetherDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.NetherDisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlockEntity;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlock;
import io.github._5thlayer.voidworks.dragon.SoakingEggBlockEntity;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlock;
import io.github._5thlayer.voidworks.mutation.MutationChamberBlockEntity;
import io.github._5thlayer.voidworks.mutation.UnmakingChamberBlock;
import io.github._5thlayer.voidworks.mutation.UnmakingChamberBlockEntity;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlock;
import io.github._5thlayer.voidworks.mutation.VoidCrucibleBlockEntity;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.network.VoidstoneMotes;
import io.github._5thlayer.voidworks.well.VoidWellBlock;
import io.github._5thlayer.voidworks.well.VoidWellBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatchBlock;
import io.github._5thlayer.voidworks.well.WellHatchBlockEntity;
import io.github._5thlayer.voidworks.well.WellHatchItem;
import io.github._5thlayer.voidworks.well.WellHatches;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Voidstone, the Void Displacers, the Mutation Chamber, the Unmaking Chamber, the Void Crucible, the
 * Void Well and its hatch, the Drained and Matter Dragon Eggs, the soaking egg and their capabilities. Voidstone and the Displacers are unbreakable and cannot be pushed: Voidstone for
 * good, a Displacer until it becomes Voidstone. Voidstone has no item; each Displacer's is its only
 * one. The Mutation blocks break with a pickaxe, as their crying obsidian does.
 */
public final class VoidworksBlocks {

    /** {@code voidworks:overworld_terrain}: the natural terrain an Overworld Displacer swaps, which placed stone matches too. */
    public static final TagKey<Block> OVERWORLD_TERRAIN =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Voidworks.MOD_ID, "overworld_terrain"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Voidworks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Voidworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Voidworks.MOD_ID);

    public static final DeferredBlock<VoidstoneBlock> VOIDSTONE = BLOCKS.registerBlock("voidstone", VoidstoneBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3_600_000.0F).noLootTable()
                    .pushReaction(PushReaction.BLOCK).sound(SoundType.DEEPSLATE).isValidSpawn((state, level, pos, type) -> false));

    public static final DeferredBlock<OverworldDisplacerBlock> OVERWORLD_DISPLACER = BLOCKS.registerBlock("overworld_void_displacer",
            OverworldDisplacerBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).noLootTable()
                    .pushReaction(PushReaction.BLOCK).sound(SoundType.STONE).lightLevel(state -> 7));

    public static final DeferredItem<BlockItem> OVERWORLD_DISPLACER_ITEM = ITEMS.registerSimpleBlockItem(OVERWORLD_DISPLACER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverworldDisplacerBlockEntity>> OVERWORLD_DISPLACER_ENTITY =
            BLOCK_ENTITIES.register("overworld_void_displacer",
                    () -> new BlockEntityType<>(OverworldDisplacerBlockEntity::new, OVERWORLD_DISPLACER.get()));

    /** It gives no light while Dormant, its forge unlit, and the Overworld Displacer's once awake. */
    public static final DeferredBlock<EndDisplacerBlock> END_DISPLACER = BLOCKS.registerBlock("end_void_displacer",
            EndDisplacerBlock::new,
            properties -> properties.mapColor(MapColor.SAND).strength(-1.0F, 3_600_000.0F).noLootTable()
                    .pushReaction(PushReaction.BLOCK).sound(SoundType.STONE).lightLevel(state -> state.getValue(Dormancy.DORMANT) ? 0 : 7));

    public static final DeferredItem<BlockItem> END_DISPLACER_ITEM = ITEMS.registerSimpleBlockItem(END_DISPLACER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndDisplacerBlockEntity>> END_DISPLACER_ENTITY =
            BLOCK_ENTITIES.register("end_void_displacer",
                    () -> new BlockEntityType<>(EndDisplacerBlockEntity::new, END_DISPLACER.get()));

    /** Like the End's, it gives no light while Dormant and the Overworld Displacer's once awake. */
    public static final DeferredBlock<NetherDisplacerBlock> NETHER_DISPLACER = BLOCKS.registerBlock("nether_void_displacer",
            NetherDisplacerBlock::new,
            properties -> properties.mapColor(MapColor.NETHER).strength(-1.0F, 3_600_000.0F).noLootTable()
                    .pushReaction(PushReaction.BLOCK).sound(SoundType.NETHER_BRICKS).lightLevel(state -> state.getValue(Dormancy.DORMANT) ? 0 : 7));

    public static final DeferredItem<BlockItem> NETHER_DISPLACER_ITEM = ITEMS.registerSimpleBlockItem(NETHER_DISPLACER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetherDisplacerBlockEntity>> NETHER_DISPLACER_ENTITY =
            BLOCK_ENTITIES.register("nether_void_displacer",
                    () -> new BlockEntityType<>(NetherDisplacerBlockEntity::new, NETHER_DISPLACER.get()));

    /** Like the Nether's Displacer, it gives no light while Dormant. */
    public static final DeferredBlock<MutationChamberBlock> MUTATION_CHAMBER = BLOCKS.registerBlock("mutation_chamber",
            MutationChamberBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(state -> state.getValue(Dormancy.DORMANT) ? 0 : 7));

    public static final DeferredItem<BlockItem> MUTATION_CHAMBER_ITEM = ITEMS.registerSimpleBlockItem(MUTATION_CHAMBER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MutationChamberBlockEntity>> MUTATION_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register("mutation_chamber",
                    () -> new BlockEntityType<>(MutationChamberBlockEntity::new, MUTATION_CHAMBER.get()));

    /** Like the Mutation Chamber, it gives no light while Dormant. */
    public static final DeferredBlock<UnmakingChamberBlock> UNMAKING_CHAMBER = BLOCKS.registerBlock("unmaking_chamber",
            UnmakingChamberBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(state -> state.getValue(Dormancy.DORMANT) ? 0 : 7));

    public static final DeferredItem<BlockItem> UNMAKING_CHAMBER_ITEM = ITEMS.registerSimpleBlockItem(UNMAKING_CHAMBER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UnmakingChamberBlockEntity>> UNMAKING_CHAMBER_ENTITY =
            BLOCK_ENTITIES.register("unmaking_chamber",
                    () -> new BlockEntityType<>(UnmakingChamberBlockEntity::new, UNMAKING_CHAMBER.get()));

    /** Never Dormant, it gives the awake Chamber's light while it upgrades. */
    public static final DeferredBlock<VoidCrucibleBlock> VOID_CRUCIBLE = BLOCKS.registerBlock("void_crucible",
            VoidCrucibleBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(state -> state.getValue(VoidCrucibleBlock.WORKING) ? 7 : 0));

    public static final DeferredItem<BlockItem> VOID_CRUCIBLE_ITEM = ITEMS.registerSimpleBlockItem(VOID_CRUCIBLE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidCrucibleBlockEntity>> VOID_CRUCIBLE_ENTITY =
            BLOCK_ENTITIES.register("void_crucible",
                    () -> new BlockEntityType<>(VoidCrucibleBlockEntity::new, VOID_CRUCIBLE.get()));

    /** The Void Well's core. Like the Mutation Chamber, it gives no light while Dormant; it breaks with a pickaxe, as its crying obsidian does, and cannot be pushed, so its motes stay where it is. */
    public static final DeferredBlock<VoidWellBlock> VOID_WELL = BLOCKS.registerBlock("void_well", VoidWellBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(22.5F, 600.0F).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.STONE).lightLevel(state -> state.getValue(Dormancy.DORMANT) ? 0 : 7));

    public static final DeferredItem<BlockItem> VOID_WELL_ITEM = ITEMS.registerSimpleBlockItem(VOID_WELL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidWellBlockEntity>> VOID_WELL_ENTITY =
            BLOCK_ENTITIES.register("void_well", () -> new BlockEntityType<>(VoidWellBlockEntity::new, VOID_WELL.get()));

    /** The Void Well's floor and walls ({@link io.github._5thlayer.voidworks.well.WellStructure}), as hard as obsidian. */
    public static final DeferredBlock<Block> WELL_CASING = BLOCKS.registerSimpleBlock("void_well_casing",
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(22.5F, 600.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> WELL_CASING_ITEM = ITEMS.registerSimpleBlockItem(WELL_CASING);

    /** The Void Well's raised rim, around the top of its walls. */
    public static final DeferredBlock<Block> WELL_RIM = BLOCKS.registerSimpleBlock("void_well_rim",
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(22.5F, 600.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> WELL_RIM_ITEM = ITEMS.registerSimpleBlockItem(WELL_RIM);

    /** A Well hatch, placed against a Void Well's wall casing: as hard as a machine, as blast-proof as the casing, and it cannot be pushed, so what it holds stays where it is. */
    public static final DeferredBlock<WellHatchBlock> WELL_HATCH = BLOCKS.registerBlock("well_hatch", WellHatchBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 600.0F).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.STONE));

    /**
     * A provider Well hatch in hand, the block's own item, which its recipe makes. Each mode's item has
     * its own name and the block none, so Jade names a hatch by the item it is picked as.
     */
    public static final DeferredItem<WellHatchItem> WELL_HATCH_ITEM = ITEMS.registerItem("well_hatch",
            properties -> new WellHatchItem(WELL_HATCH.get(), WellHatches.Mode.PROVIDER, properties), Item.Properties::useItemDescriptionPrefix);

    /** A requester Well hatch in hand, which a provider's turns into, sneak-used in the air, and back. */
    public static final DeferredItem<WellHatchItem> WELL_HATCH_REQUESTER_ITEM = ITEMS.registerItem("well_hatch_requester",
            properties -> new WellHatchItem(WELL_HATCH.get(), WellHatches.Mode.REQUESTER, properties), Item.Properties::useItemDescriptionPrefix);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WellHatchBlockEntity>> WELL_HATCH_ENTITY =
            BLOCK_ENTITIES.register("well_hatch", () -> new BlockEntityType<>(WellHatchBlockEntity::new, WELL_HATCH.get()));

    /** Laid by a grown Void Dragon: as the vanilla egg, it falls, and teleports when hit, until a player places it to soak. */
    public static final DeferredBlock<DragonEggBlock> DRAINED_DRAGON_EGG = BLOCKS.registerBlock("drained_dragon_egg", DragonEggBlock::new,
            properties -> properties.mapColor(MapColor.TERRACOTTA_WHITE).strength(3.0F, 9.0F).lightLevel(state -> 1).noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredItem<BlockItem> DRAINED_DRAGON_EGG_ITEM =
            ITEMS.registerSimpleBlockItem(DRAINED_DRAGON_EGG, properties -> properties.rarity(Rarity.EPIC));

    /**
     * A Drained Dragon Egg upgraded by Mutation, which hatches the Matter line: the Drained egg's model tinted amber. As the vanilla egg, it
     * falls, and teleports when hit, until a player places it to soak.
     */
    public static final DeferredBlock<DragonEggBlock> MATTER_DRAGON_EGG = BLOCKS.registerBlock("matter_dragon_egg", DragonEggBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 9.0F).lightLevel(state -> 1).noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredItem<BlockItem> MATTER_DRAGON_EGG_ITEM =
            ITEMS.registerSimpleBlockItem(MATTER_DRAGON_EGG, properties -> properties.rarity(Rarity.EPIC));

    /** Any egg soaking up motes ({@link SoakingEggBlock}), as hard as the vanilla egg and glowing brighter at each stage. It has no item. */
    public static final DeferredBlock<SoakingEggBlock> SOAKING_EGG = BLOCKS.registerBlock("soaking_dragon_egg", SoakingEggBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_BLACK).strength(3.0F, 9.0F)
                    .lightLevel(state -> 1 + 3 * state.getValue(SoakingEggBlock.STAGE)).noOcclusion().pushReaction(PushReaction.DESTROY));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoakingEggBlockEntity>> SOAKING_EGG_ENTITY =
            BLOCK_ENTITIES.register("soaking_dragon_egg", () -> new BlockEntityType<>(SoakingEggBlockEntity::new, SOAKING_EGG.get()));

    private VoidworksBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(VoidworksBlocks::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, OVERWORLD_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, END_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, NETHER_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.itemFace());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, NETHER_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.fluidFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, MUTATION_CHAMBER_ENTITY.get(), (chamber, side) -> chamber.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, UNMAKING_CHAMBER_ENTITY.get(), (chamber, side) -> chamber.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, VOID_CRUCIBLE_ENTITY.get(), (crucible, side) -> crucible.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, WELL_HATCH_ENTITY.get(), (hatch, side) -> hatch.itemFace());
        event.registerBlock(VoidworksCapabilities.MOTES,
                (level, pos, state, blockEntity, side) -> level instanceof ServerLevel server ? VoidstoneMotes.at(server, pos) : null,
                VOIDSTONE.get());
        // A Dormant Well is not in a network yet: it holds nothing, and pays its cost through Dormancy.
        event.registerBlock(VoidworksCapabilities.MOTES,
                (level, pos, state, blockEntity, side) -> level instanceof ServerLevel server && !state.getValue(Dormancy.DORMANT)
                        ? VoidstoneMotes.at(server, pos) : null,
                VOID_WELL.get());
    }
}
