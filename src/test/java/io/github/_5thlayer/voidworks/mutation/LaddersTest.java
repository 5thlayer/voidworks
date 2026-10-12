// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.voidworks.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github._5thlayer.voidworks.mutation.Ladders.Rung;
import org.junit.jupiter.api.Test;

/** The next Rung an item climbs to, across every Family's ladder. */
class LaddersTest {

    private static final Upgrade<String> TO_IRON = new Upgrade<>("iron_ingot", 1, 100, 0.8);
    private static final Upgrade<String> TO_GOLD = new Upgrade<>("gold_ingot", 1, 200, 0.8);

    private static final List<Rung<String>> INGOTS = List.of(
            bottom("copper_ingot"),
            new Rung<>(Set.of("iron_ingot", "tin_ingot"), Optional.of(TO_IRON)),
            new Rung<>(Set.of("gold_ingot"), Optional.of(TO_GOLD)));

    private static Rung<String> bottom(String... items) {
        return new Rung<>(Set.of(items), Optional.empty());
    }

    @Test
    void anItemOnARungUpgradesToTheNext() {
        var ladders = Ladders.of(Map.of("ingots", INGOTS));
        assertEquals(Optional.of(TO_IRON), ladders.next("copper_ingot"));
        assertEquals(Optional.of(TO_GOLD), ladders.next("iron_ingot"));
    }

    @Test
    void anotherItemJoiningARungUpgradesFromItToo() {
        assertEquals(Optional.of(TO_GOLD), Ladders.of(Map.of("ingots", INGOTS)).next("tin_ingot"));
    }

    @Test
    void aRungAcceptsItsOwnResultUnlisted() {
        var ladder = List.of(bottom("copper_ingot"), new Rung<>(Set.<String>of(), Optional.of(TO_IRON)),
                new Rung<>(Set.of("gold_ingot"), Optional.of(TO_GOLD)));
        assertEquals(Optional.of(TO_GOLD), Ladders.of(Map.of("ingots", ladder)).next("iron_ingot"));
    }

    @Test
    void anItemOnTheTopRungHasNoUpgrade() {
        assertEquals(Optional.empty(), Ladders.of(Map.of("ingots", INGOTS)).next("gold_ingot"));
    }

    @Test
    void anItemInNoFamilyHasNoUpgrade() {
        assertEquals(Optional.empty(), Ladders.of(Map.of("ingots", INGOTS)).next("stick"));
        assertEquals(Optional.empty(), Ladders.<String>empty().next("copper_ingot"));
    }

    @Test
    void familiesThatAgreeOnTheNextStepBothUpgrade() {
        var also = List.of(bottom("copper_ingot"), new Rung<>(Set.<String>of(), Optional.of(TO_IRON)));
        var ladders = Ladders.of(Map.of("ingots", INGOTS, "also", also));
        assertEquals(Optional.of(TO_IRON), ladders.next("copper_ingot"));
        assertEquals(Map.of(), ladders.disagreements());
    }

    @Test
    void familiesThatDisagreeOnTheResultLeaveNoUpgradeAndAreNamed() {
        var other = List.of(bottom("copper_ingot"), new Rung<>(Set.<String>of(), Optional.of(TO_GOLD)));
        var ladders = Ladders.of(Map.of("ingots", INGOTS, "other", other));
        assertEquals(Optional.empty(), ladders.next("copper_ingot"));
        assertEquals(Map.of("copper_ingot", new TreeSet<>(Set.of("ingots", "other"))), ladders.disagreements());
    }

    @Test
    void familiesThatAgreeOnTheResultButNotTheCostDisagree() {
        var dearer = List.of(bottom("copper_ingot"),
                new Rung<>(Set.<String>of(), Optional.of(new Upgrade<>("iron_ingot", 1, 800, 0.8))));
        var ladders = Ladders.of(Map.of("ingots", INGOTS, "dearer", dearer));
        assertEquals(Optional.empty(), ladders.next("copper_ingot"));
        assertEquals(Set.of("copper_ingot"), ladders.disagreements().keySet());
    }

    @Test
    void twoRungsOfOneFamilyAcceptingAnItemDisagree() {
        var overlapping = List.of(bottom("copper_ingot", "tin_ingot"),
                new Rung<>(Set.of("tin_ingot"), Optional.of(TO_IRON)),
                new Rung<>(Set.<String>of(), Optional.of(TO_GOLD)));
        var ladders = Ladders.of(Map.of("ingots", overlapping));
        assertEquals(Optional.empty(), ladders.next("tin_ingot"));
        assertEquals(Map.of("tin_ingot", new TreeSet<>(Set.of("ingots"))), ladders.disagreements());
    }

    @Test
    void aTopRungMakesNoClaimSoAnotherFamilyCarriesItOn() {
        var beyond = List.of(bottom("gold_ingot"),
                new Rung<>(Set.<String>of(), Optional.of(new Upgrade<>("diamond", 8, 6400, 0.5))));
        var ladders = Ladders.of(Map.of("ingots", INGOTS, "beyond", beyond));
        assertEquals(Optional.of(new Upgrade<>("diamond", 8, 6400, 0.5)), ladders.next("gold_ingot"));
        assertEquals(Map.of(), ladders.disagreements());
    }

    @Test
    void aFamilyOfOneRungUpgradesNothing() {
        assertEquals(Optional.empty(), Ladders.of(Map.of("lone", List.of(bottom("copper_ingot")))).next("copper_ingot"));
    }

    @Test
    void anUpgradeRefusesImpossibleNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new Upgrade<>("x", 0, 100, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Upgrade<>("x", 1, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Upgrade<>("x", 1, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Upgrade<>("x", 1, 100, 1.5));
    }
}
