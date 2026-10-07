# Void energy and Mutation — design

Resolved in two grilling sessions on 2026-10-06, the second on void logistics, from the original
idea in FactoryWorks' [docs/ideas/mutation_system.md](https://github.com/5thlayer/factoryworks/blob/main/docs/ideas/mutation_system.md), which stays as written.
Factorio fidelity was not a constraint.

The terms are in [`GLOSSARY.md`](../../GLOSSARY.md).

## Where it lives

In **Voidworks**, a mod of its own in its own repository, started from libworks' template. Nothing
the Modules do uses void, so it is not something the base mod holds for them, and a player can
leave the End-centred endgame out. It requires nothing but Minecraft and NeoForge: its inputs are
vanilla, Mutation reads the recipe manager, and its outputs go through NeoForge's item and fluid
faces. If the Displacer or the Void Well become multiblocks, it may require Groundworks, as Beltworks
and Wireworks do.

## Progression

Void is post-End: easy to start means easy once the dragon is beaten. The Void Siphon's recipe
needs dragon's breath. Each Void Displacer's recipe needs its dimension's materials and motes.
Nothing forces the first Displacer into the End; the End is where harvesting pays best.

## Motes and void energy

- Void Pressure is a small integer, one scale shared by every rule that reads it. Each dimension
  has a base, from a data map keyed by dimension: End above Overworld above Nether, and a dimension
  the map does not name takes the Overworld's. In the End, a position with no block beneath it down
  to the bottom of the world is open to the void and sits one step higher.
- A mote's grade is one step above the Void Pressure where it was harvested, so every mote is
  worth something where it was harvested: Nether, Overworld, End, and End over the open void.
- Spending a mote releases void energy by how far its grade exceeds the local Void Pressure, and
  each further step doubles it: one step releases a provisional base amount, two steps twice that,
  three steps four times. A machine refuses a mote whose grade is not above it, and spends the
  lowest grade still worth something first.
- So the End is the reservoir and the Nether the turbine: one mote does the most work in the
  Nether, and moving motes downhill is why void logistics exists. A base runs on its own harvest
  at the lowest value, and the cheap Nether and Overworld motes pay for a start before the End
  pays off; carrying them downhill is what multiplies them.
- Every spend follows this rule — Mutation, gear, zone upgrades, Displacer recipes, hatching,
  void-only materials — except healing the Void Dragon, which happens only at a Void Well.
- What motes buy: power for endgame gear, Mutation, and void-only materials (a void alloy and the
  like). They never create ordinary items; Mutation is the only path from motes to those.

## Harvesting

Three Void Displacers, one per dimension, each working only in its own. All three consume fed
blocks, yield motes by the fed block's Density at the grade of the local Void Pressure, and turn
each harvested position into Voidstone.

- **End**: places fed blocks into the empty air of its zone.
- **Overworld**: swaps fed blocks with natural terrain in its zone — the blocks in a natural-block
  tag (stone, deepslate, dirt, ores…), which placed stone also matches. The terrain block goes to
  the Displacer's output, through a data table keyed by the terrain block and the fed block's
  Density, which by default gives the terrain block itself. Another mod or a pack fills the table
  to make displacing pay out its own items (FactoryWorks' Enriched ore,
  [enriched-ore.md](https://github.com/5thlayer/factoryworks/blob/main/docs/spec/enriched-ore.md)).
- **Nether**: displaces lava sources in its zone into an internal tank with a fluid output face, so
  any mod's pipe or tank, or a bucket, drains it (FactoryWorks [ADR-0121](https://github.com/5thlayer/factoryworks/blob/main/docs/adr/0121-fluid-is-carried-in-buckets-and-the-barrel-goes.md)).

Each Displacer has one item output inventory with an item face. A full output inventory or tank
stalls it. While its zone has anything left to convert — air, natural terrain, lava — it cannot be
broken or moved; when nothing is left it becomes Voidstone itself, and relocating means building a
new one. Zones can grow through upgrades paid in motes; relocation stays the main loop.

Density rises steeply (shape: about 4^density): stone is the cheap start, storage blocks are how
harvesting scales.

### By hand

The **Void Siphon** is one item that behaves by the dimension it is in, fed from the off hand, at a
far worse yield than a Displacer's and with an on/off toggle:

- **End**: the block goes into air and becomes Voidstone; nothing comes back.
- **Overworld**: the block swaps with a natural block, which comes back to the player.
- **Nether**: the block displaces a lava source, which fills an empty bucket or another mod's fluid
  item from the inventory. With none, it refuses.

## Moving motes

- **Voidstone conducts.** Motes crawl through face-touching Voidstone as a visible glint, at a
  finite speed and without loss, from high network pressure to low. Each grade flows on its own.
- Networks that touch merge, harmlessly: pressure, not topology, decides where motes go. There is no
  seal or valve.
- Network pressure is the motes' own and only steers flow. It is capped below the End's Void
  Pressure, so a full network never makes a place a stand-in for the End.
- Any void machine touching Voidstone joins the network. The Void Siphon charges or discharges while
  the player stands on it.

### The Void Well

One block: a large store of motes, the Void Dragon's stop and its healing point. Its pressure stays
low until it is nearly full, so with no fill target it is a reservoir that soaks up surplus and
gives it back. With a fill target, a Void Dragon delivers to it until the target is met.

### The Void Dragon

Named and raised on the pattern of vanilla's Dried Ghast, Ghastling and Happy Ghast.

- **Hatching.** The first dragon kill gives the vanilla egg; every later kill drops a **Drained
  Dragon Egg**. Either egg is placed touching a Voidstone network and soaks up motes from it over
  time, in visible stages, until it hatches; each egg needs more motes than the last. So carriers
  scale with dragon fights won.
- **Growing.** It hatches a **Dragonling**, which grows into a **Void Dragon**, faster when fed
  motes. A Dragonling cannot carry.
- **Routing.** A Void Dragon serves all its owner's Void Wells in every dimension, carrying from high
  pressure to low until each Well's fill target is met. Several dragons share the work.
- **Travel.** It flies visibly near its Wells and dives into the void between them, emerging after
  a travel time set by distance. It is the only thing that carries motes between dimensions.
- **Riding.** A player mounts it at a Void Well and picks another of their Wells; it dives and
  emerges there, in any dimension. It is not free flight. A rider costs nothing and the load
  travels as usual.
- **Harm.** Outside the End it weakens at a rate set by how far the local Void Pressure is below the
  End's, so it suffers most in the Nether, where motes pay most. Hurt, it flies more slowly; it
  cannot die. It heals with motes at a Void Well.

## Mutation

- The **Mutation Chamber** does two things for motes:
  - **Upgrade**: an item to the next tier of its **Family**. A chance roll; on failure the item is
    kept and the motes are spent.
  - **Breakdown**: an item into its crafting recipe's ingredients, read from the recipe in reverse,
    with a datapack override list. Items with no recipe or several are skipped unless overridden.
    It returns about 25% of the ingredients, more as more motes are spent, never more than went in.
- Families are data: tags or datapack ladders, shipped as separate metal and gem ladders, so other
  mods' materials can join.
- The **Void Crucible** upgrades items for free, slowly, at a rate set by Void Pressure, so items go
  to the End while motes leave it. Upgrade only, no breakdown. Progresses only while its chunk is
  loaded. Hoppers and belts feed and empty it.

## Loops

Loops that come out ahead are allowed and are meant to be the endgame puzzle. What bounds them is that every harvest permanently consumes the world and every Displacer
ends as Voidstone. The Void Siphon alone must never close a profitable loop.

## Open

- **Voidstone raising Void Pressure around it** — a later layer, compounding a site's harvests.
- **Numbers** — the grades, the Density curve, zone sizes and upgrade costs, upgrade chances per tier, breakdown yield against motes, mote speed, Void Well capacity
  and the network pressure cap, the Void Dragon's load, speed and harm rate, and the rise in hatching
  cost.
