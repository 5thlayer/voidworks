// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.voidworks.displacer.EndDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.EndDisplacerBlockEntity;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlock;
import io.github._5thlayer.voidworks.displacer.OverworldDisplacerBlockEntity;
import io.github._5thlayer.voidworks.dormant.Dormancy;
import io.github._5thlayer.voidworks.network.VoidstoneBlock;
import io.github._5thlayer.voidworks.network.VoidstoneMotes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Voidstone, the Void Displacers and their capabilities. Every one is unbreakable and cannot be
 * pushed: Voidstone for good, a Displacer until it becomes Voidstone. Voidstone has no item; each
 * Displacer's is its only one.
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

    private VoidworksBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(VoidworksBlocks::registerCapabilities);
        modBus.addListener(VoidworksBlocks::addToCreativeTabs);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, OVERWORLD_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.itemFace());
        event.registerBlockEntity(Capabilities.Item.BLOCK, END_DISPLACER_ENTITY.get(), (displacer, side) -> displacer.itemFace());
        event.registerBlock(VoidworksCapabilities.MOTES,
                (level, pos, state, blockEntity, side) -> level instanceof ServerLevel server ? VoidstoneMotes.at(server, pos) : null,
                VOIDSTONE.get());
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(OVERWORLD_DISPLACER_ITEM.get());
            event.accept(END_DISPLACER_ITEM.get());
        }
    }
}
