// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks;

import io.github._5thlayer.libworks.WorksTab;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Every Voidworks item, in the suite's Works tab and no other (FactoryWorks ADR-0128). One listener,
 * since separate ones would add in registration order rather than this one.
 */
final class WorksTabContents {

    private WorksTabContents() {
    }

    static void add(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != WorksTab.KEY) {
            return;
        }
        event.accept(VoidworksBlocks.OVERWORLD_DISPLACER_ITEM.get());
        event.accept(VoidworksBlocks.NETHER_DISPLACER_ITEM.get());
        event.accept(VoidworksBlocks.END_DISPLACER_ITEM.get());
        event.accept(VoidworksBlocks.MUTATION_CHAMBER_ITEM.get());
        event.accept(VoidworksBlocks.UNMAKING_CHAMBER_ITEM.get());
        event.accept(VoidworksBlocks.VOID_CRUCIBLE_ITEM.get());
        event.accept(VoidworksBlocks.VOID_WELL_ITEM.get());
        event.accept(VoidworksBlocks.WELL_CASING_ITEM.get());
        event.accept(VoidworksBlocks.WELL_RIM_ITEM.get());
        event.accept(VoidworksItems.VOID_SIPHON.get());
        event.accept(VoidworksItems.RESONANT_VOID_SIPHON.get());
        event.accept(VoidworksItems.DRACONIC_VOID_SIPHON.get());
        event.accept(VoidworksBlocks.DRAINED_DRAGON_EGG_ITEM.get());
    }
}
