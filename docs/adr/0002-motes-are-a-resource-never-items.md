---
status: accepted
---

# Motes are a resource, never items

The spec says moving motes downhill is why void logistics exists: Voidstone networks within a
dimension, the Void Dragon between them. Motes were first built as an item with its grade as a
data component, so a hopper, a chest, a Beltworks belt or an ender chest moved them too, and any of
those made Voidstone and the Dragon pointless.

**Decision.** A mote is never an item. Motes are a NeoForge transfer resource of Voidworks' own,
one per grade, held only by Voidstone, void machines and the Void Siphon, and reached through a
public `voidworks:motes` capability that a Consumer's own void machines can provide and spend from.
The spend rule works on that resource. Belts, hoppers and pipes ask only for items or fluids, so
they never see a mote.

The Void Siphon is the one store that travels: it keeps its motes wherever it goes, and a player
may carry several. What keeps it from being a pipe is that only a player charges or empties it,
by harvesting, by standing on Voidstone, or by gear spending from it. No block takes a Siphon, so
moving Siphons by item logistics still needs a player at both ends, and its tiers cap what that
player carries far below a Void Well.

## Considered options

- **Motes as items**, as first built: any inventory holds them and `/give` makes them, but item
  logistics carries them across dimensions for free.
- **Motes internal to Voidworks**, with no capability: safest, but the FactoryWorks Pack, the first
  Consumer, could not build void machines of its own.
- **Motes held by the player rather than the Siphon**, capped per player: it rules out boxes of
  full Siphons, but dropping a Siphon leaving its motes behind reads wrong, and tiers plus the
  player-only rule bound carrying well enough.

## Consequences

- The mote item, its `voidworks:grade` component, spending from item inventories and
  `/voidworks mote` go. The command comes back with the first store, filling the store at a block
  position.
- Harvests are seen, not picked up: grade-tinted butterflies fly to the receiver, cosmetic but
  exact in count.
- Groundworks' multiblock footprint forwards only the energy, fluid and item capabilities, so a
  multiblock void machine forwards `voidworks:motes` itself.
