# Void energy and Mutation — design

Resolved in two grilling sessions on 2026-10-06, the second on void logistics, and a third on
2026-10-07 that made motes a resource and never an item ([ADR 0002](../adr/0002-motes-are-a-resource-never-items.md)), and a fourth on
2026-10-11 that started void in the Overworld, before the End ([ADR 0003](../adr/0003-void-starts-in-the-overworld-before-the-end.md)), and a fifth the same day
on Families, from the original
idea in FactoryWorks' [docs/ideas/mutation_system.md](https://github.com/5thlayer/factoryworks/blob/main/docs/ideas/mutation_system.md), which stays as written.
Factorio fidelity was not a constraint.

The terms are in [`GLOSSARY.md`](../../GLOSSARY.md).

## Where it lives

In **Voidworks**, a mod of its own in its own repository, started from libworks' template. Nothing
the Modules do uses void, so it is not something the base mod holds for them, and a player can
leave the End-centred endgame out. It requires nothing a player installs but Minecraft and NeoForge
(libworks-runtime, which shows it in Jade, is nested in its jar): its inputs are
vanilla, Mutation reads the recipe manager, and its outputs go through NeoForge's item and fluid
faces. If the Displacer or the Void Well become multiblocks, it may require Groundworks, as Beltworks
and Wireworks do.

## Progression

Void starts in the Overworld at the diamond tier and pays best in the End
([ADR 0003](../adr/0003-void-starts-in-the-overworld-before-the-end.md)). Each step is the
lesson the next one pays off:

1. **The Overworld.** The Overworld's Void Displacer costs no motes: crying obsidian on the corners,
   ender pearls on the sides and a sticky piston in the centre. It needs no network to start, since
   it grows its own, and its grade-3 motes are worth one step where they were harvested.
2. **The first spend.** A Mutation Chamber (a smithing table, amethyst shards and crying obsidian)
   is placed Dormant touching that network, soaks its cost from it, and then upgrades items up their
   Family at the Overworld's low value.
3. **The Nether.** A ghast tear, crying obsidian and a blaze rod make the Void Siphon. It charges on
   the Overworld network and carries the motes down to the Nether, where each does twice the work.
   The Nether's Displacer is placed Dormant and paid for with those carried motes.
4. **The End.** After the dragon, the Siphon's End harvests give grade 4 and 5, the only motes worth
   anything at the End's Void Pressure, so they alone pay for a Dormant End Displacer. Dragon's
   breath goes to a higher Siphon tier.
5. **Wells and the Dragon.** The first egg soaks on a network and hatches while the player raids End
   cities, whose shulker shells make the Void Well; the Void Dragon carries between Wells.

Before the Nether, crying obsidian comes only from ruined portals, enough for a Displacer or two;
after it, piglins barter it, so relocating Displacers pulls a player into the Nether.

### Dormant

Every void machine whose recipe costs motes is crafted from items alone and placed **Dormant**: it
soaks its cost where it stands, from any touching mote store (a Voidstone network, another void
machine) or a Siphon emptying into it, by the spend rule at its own block, and works once it is paid. A mote is spent whole, so the last one can pay
more than is owed, and the rest is lost. The Overworld's Displacer costs no motes and is never
Dormant.

## Motes and void energy

- A mote is never an item. Only Voidstone, void machines (a Consumer's too, through the public
  `voidworks:motes` capability) and the Void Siphon hold motes, pooled by grade; chests, hoppers,
  belts and pipes never carry them. A broken store loses the motes it held.
- Void Pressure is a small integer, one scale shared by every rule that reads it. Each dimension
  has a base, from a data map keyed by dimension: End above Overworld above Nether, and a dimension
  the map does not name takes the Overworld's. Each entry also names the dimension's harvest kind
  (End, Overworld, Nether or none), which the Void Siphon follows; an unnamed dimension has none. In the End, a position with no block beneath it down
  to the bottom of the world is open to the void and sits one step higher.
- A mote's grade is one step above the Void Pressure where it was harvested, so every mote is
  worth something where it was harvested: Nether, Overworld, End, and End over the open void.
- The grade bands, provisional like every number: the default pressures are Nether 1, Overworld 2
  and End 3, 4 over the open void, so harvests give grades 2 to 5. A grade runs from 2, the lowest
  any harvest gives, to 16, which leaves room for the
  numbers to grow and keeps a spend's arithmetic exact. No mote has a grade outside it.
- Spending a mote releases void energy by how far its grade exceeds the local Void Pressure, and
  each further step doubles it: one step releases a provisional base amount, two steps twice that,
  three steps four times. A machine refuses a mote whose grade is not above it, and spends the
  lowest grade still worth something first.
- Local means where the spender stands: a machine's own block, the player's feet for gear and the
  Void Siphon, the egg's block for hatching. The open void's extra step counts for spending too, so
  a machine built over it refuses all but the top grade.
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
each harvested position into Voidstone. Fed blocks come in through an item input face that hoppers,
belts and pipes fill, which refuses any block of Density 0, so a misfed block stays on the belt.

A Displacer holds no motes and needs no network to start: it grows its own. It harvests only
positions touching it or the Voidstone it has already left, nearest first, so its first harvest is
the position beside it and what it has harvested is the network its motes go into. It reaches only
what is connected to it: a cave or an air pocket bounds it. Its **Zone** is how many positions it
harvests in its life, a count and not a region; upgrades paid in motes raise it. Built touching an
older network, a Displacer still grows only from itself, and the networks merge.

Every harvest shows its motes. Each flies from the harvested position to whatever receives it, a
Displacer or the player's Void Siphon, as a small butterfly with flapping wings, tinted by grade:
our own sprite, after Boulder Dash's butterflies. They are cosmetic and credited at once, but the
count is exact: like XP orbs, a butterfly stands for 1, 8 or 64 motes by its size.

- **End**: places fed blocks into the empty air of its zone. It faces the way its placer looked and
  grows only into the space ahead of that face, so it never walls in its other faces.
- **Overworld**: swaps fed blocks with natural terrain in its zone — the blocks in a natural-block
  tag (stone, deepslate, dirt, ores…), which placed stone also matches. The terrain block goes to
  the Displacer's output, through a data table keyed by the terrain block and the fed block's
  Density, which by default gives the terrain block itself. Another mod or a pack fills the table
  to make displacing pay out its own items (FactoryWorks' Enriched ore,
  [enriched-ore.md](https://github.com/5thlayer/factoryworks/blob/main/docs/spec/enriched-ore.md)).
- **Nether**: displaces lava sources in its zone into an internal tank with a fluid output face, so
  any mod's pipe or tank, or a bucket, drains it (FactoryWorks [ADR-0121](https://github.com/5thlayer/factoryworks/blob/main/docs/adr/0121-fluid-is-carried-in-buckets-and-the-barrel-goes.md)).

Each Displacer has one item output inventory with an item face. A full output inventory, tank or
network stalls it. While its Zone is unspent and it can still reach something to convert — air,
natural terrain, lava — it cannot be broken or moved; then it becomes Voidstone itself, its motes
stay in the network, and relocating means building a new one. One that can reach nothing before
its first harvest was misplaced, and pops off as its item rather than becoming Voidstone. Upgrades raise a Zone; relocation
stays the main loop.

Only blocks a Density tag names can be harvested. Every other block is Density 0, nothing harvests
it, and it leaves no Voidstone: the renewable and abundant blocks above all (cobblestone, stone,
deepslate, dirt, sand, gravel, netherrack, blackstone, basalt, end stone, obsidian, snow, ice), and
everything made from them, so a generator never feeds a harvest. Density rises steeply (shape: about
4^(density − 1), so 1, 4, 16, 64). Density tags and Families are separate data and need not agree:
amethyst, quartz and glowstone climb the gem and dust Families but stay Density 0, being renewable.

| Density | Motes | Blocks, raw and refined alike |
|---|---|---|
| 1 | 1 | copper, coal |
| 2 | 4 | iron, redstone, lapis |
| 3 | 16 | gold, emerald |
| 4 | 64 | diamond, netherite |

One Mutation up a metal block Family so multiplies a block's harvest by four. No fed block yields more than about
64 motes, so a harvest stays countable, and a mote releases correspondingly more void energy.

### By hand

The **Void Siphon** is one item that behaves by the harvest kind of the dimension it is in, and refuses
in a dimension with none. Its recipe is a ghast tear, crying obsidian and a blaze rod. Held in the
main hand, each use harvests once, aimed like placing a block, and consumes one block from the off
hand; a block of Density 0 is refused. It yields one Density step below a Displacer, never below 1
(1, 1, 4, 16):

- **End**: the block goes into the air aimed at and becomes Voidstone; nothing comes back. Off the
  island's edge that is the open void, and each Voidstone closes the column above it, so the top
  grade is always at the frontier of open air.
- **Overworld**: the block swaps with a natural block, which comes back to the player.
- **Nether**: the block displaces a lava source, which fills an empty bucket or another mod's fluid
  item from the inventory. With none, it refuses.

The Siphon holds motes, as many as its tier allows, and keeps them wherever it goes: dropped,
stored, or carried through a portal. Only a player charges or empties it, by harvesting, by
standing on Voidstone (its toggle, flipped by sneak-use, sets whether it charges from the network
or empties into it), or by gear spending from it; no block takes a Siphon, so item logistics
moving Siphons still needs a player at both ends. A player may carry several. Even the top tier
holds a small fraction of a Void Well: carrying by hand starts a base or powers gear, and never
supplies one.

**Tiers.** The Siphon comes in three tiers, and a tier raises only what it holds and how fast it
charges or empties on Voidstone; its harvest yield stays one Density step below a Displacer at
every tier, so no tier closes a profitable loop. A Siphon climbs at a smithing table, with no
template, keeping its motes and its toggle, as netherite keeps a tool's enchantments:

| Tier | Climbs with | Capacity | Rate |
|---|---|---|---|
| Void Siphon | (crafted) | 256 | 8 |
| Resonant Void Siphon | an echo shard, from the deep dark | 1024 | 16 |
| Draconic Void Siphon | dragon's breath, after the dragon | 4096 | 32 |

The capacities and rates are provisional, a list per tier in the server config. The Siphon has no
durability.

## Moving motes

- **Voidstone stores.** Each Voidstone adds to its network's capacity, about what its own harvest
  yielded at a middling Density, so a Displacer's body holds its harvest and spent bodies are a
  base's storage until the Void Well.
- **Voidstone looks like where it was placed**, by the harvest kind there. Where it is `end` it takes
  the End Displacer's yellows, and where it is `nether` the Nether Displacer's reds, so a Displacer
  reads as part of its body there; elsewhere the void's purples. Only the look
  differs: it is one block, and networks join across looks.
- **Voidstone conducts.** Motes crawl through face-touching Voidstone, seen as the harvest's
  butterflies, smaller, walking its faces, at a
  finite speed and without loss, from high network pressure to low. Each grade flows on its own.
- Networks that touch merge, harmlessly: pressure, not topology, decides where motes go. There is no
  seal or valve.
- Network pressure is the motes' own and only steers flow. It is capped below the End's Void
  Pressure, so a full network never makes a place a stand-in for the End.
- Any void machine touching Voidstone joins the network. The Void Siphon charges or discharges while
  the player stands on it.

### The Void Well

One block: a large store of motes, the Void Dragon's stop and its healing point. It comes with the
Dragon, after the dragon: its recipe is shulker shells from the End cities, an ender chest and
crying obsidian, placed Dormant. Before it, Voidstone bodies are a base's storage. Its pressure stays
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
  a travel time set by distance. It is the only thing that carries motes between dimensions at
  scale; a player carries a Siphon's worth.
- **Riding.** A player mounts it at a Void Well and picks another of their Wells; it dives and
  emerges there, in any dimension. It is not free flight. A rider costs nothing and the load
  travels as usual.
- **Harm.** Outside the End it weakens at a rate set by how far the local Void Pressure is below the
  End's, so it suffers most in the Nether, where motes pay most. Hurt, it flies more slowly; it
  cannot die. It heals with motes at a Void Well.

## Mutation

Three blocks Mutate, none with a screen or a held recipe: the item decides what it becomes
([ADR 0004](../adr/0004-breakdown-is-a-block-of-its-own-with-no-screen.md)). Each has an item face,
the same on every side, that takes into its input only items it can work on and gives only from its
output, so hoppers and belts feed and empty it, and a hand puts items in and takes the result.

- The **Mutation Chamber** is a smithing table, amethyst shards and crying obsidian, placed Dormant.
  It is the first thing motes buy, before the Nether. It spends from every mote store touching it
  together; a Void Siphon pays only its Dormant cost. It **upgrades** items to the next Rung of their
  **Family**, by that Rung's count, void energy cost and chance. A chance roll; on failure the items
  are kept and the motes are spent. It never breaks items down.
- The **Unmaking Chamber** is a grindstone, amethyst shards and crying obsidian, placed Dormant, and
  spends as the Mutation Chamber does. It **breaks down** an item into its crafting recipe's
  ingredients, read from the recipe in reverse:
  - Only crafting-table recipes count. An item two or more of them make, or none, is skipped, and so
    is one whose recipe has no fixed ingredients (fireworks, banner copies) or whose result carries
    data components. A recipe that makes several items takes that many per breakdown.
  - An ingredient that accepts several items gives back the first in its own order (oak planks for
    any planks). One that leaves a remainder in the crafting grid, such as a milk bucket, never comes
    back, since its container went back to the crafter.
  - A datapack override list, a data map keyed by item, names the count and the ingredients for an
    item, which fills in for an item with several recipes or none, and an empty list bars an item.
  - Each ingredient item comes back alone by chance, at the Chamber's yield, so a breakdown never
    returns more than went in. Worn and enchanted items break down at the same yield, their
    enchantments, names and trims lost.
  - The yield starts at about 25% and rises with the motes the Chamber has spent on breakdowns in its
    life, its Dormant cost aside, each further amount closing half the gap to a cap of about 75%. It
    forgets what it has spent when broken. A breakdown costs 100 void energy per ingredient item.
  - It has nine outputs and waits, spending nothing, until everything the recipe could return fits.
- The **Void Crucible** upgrades items for free, slowly, at a rate set by Void Pressure, so items go
  to the End while motes leave it. It is a cauldron, amethyst shards and crying obsidian, and is never
  Dormant. It takes each Rung's count, and a Rung's void energy cost sets how long it takes there, so a
  steep Rung is slow in the Crucible too; each step of Void Pressure halves the time, and it never
  fails. Its time is set at the End's shipped Void Pressure, so a pack that moves the pressures moves
  the Crucible's speed with them. Working Crucibles within a few blocks of each other on every axis, a
  cube a player counts out on the grid, share one rate, so a wall of them upgrades no faster than one.
  Upgrade only, no breakdown. It works on one result at a time, which floats above it while it does,
  and what it has done belongs to that item: emptied or given another, it starts again, and it waits,
  done, while its output is full, its result held still above it. Progresses only while its chunk is
  loaded.

### Families

A Family is a ladder of Rungs, one item form to a Family: metal ingots are one Family, metal blocks
another. Each Family is a datapack registry entry listing its Rungs in order. A Rung names:

- **items**: what it accepts, an item tag of Voidworks' own per Rung,
  `#voidworks:rung/<family>/<rung>`, that takes in the conventional tag (`#c:ingots/iron` on
  `#voidworks:rung/metal_ingots/iron`), so another mod's item joins a Rung without code;
- **result**: the one item an upgrade from the Rung below lands on, never implied by order;
- **count**: how many items the upgrade takes per result, 1 unless set;
- **cost** and **chance**: the void energy a Mutation Chamber spends per result, and its odds.

The bottom Rung names only its items: nothing upgrades into it, and a Family whose bottom Rung names
a result, count, cost or chance fails to load. Every other Rung accepts its own result besides its
tag, so an upgraded item climbs on. Families are not synced to clients until a client reads them.

A datapack adds a Family with a file of its own and replaces one by overriding its file. An item on
a top Rung, or in no Family, has no upgrade from it: a top Rung makes no claim, so another Family
that accepts the item lower down carries it on, as a pack extends a ladder. An item that several
Rungs accept, in one Family or several, upgrades only if their next steps are the same in result,
count, cost and chance; if they differ in any, it has none, and loading warns, naming them. Every
upgrade gives one result for its count of items.

What Voidworks ships, ⇒ marking a steep Rung, each ladder one Family (`gems`, `dusts`,
`hostile_drops`, `passive_drops`) but the metals:

- **Metals**: five Families (`metal_ingots`, `metal_nuggets`, `metal_blocks`, `raw_metals`,
  `raw_metal_blocks`), each copper → iron → gold. Ores
  are left out, since Fortune makes an ore worth more than its ingot and Displacers swap ores.
- **Gems**: lapis → amethyst shard → quartz → emerald → diamond ⇒ netherite scrap.
  Gem blocks are left out: amethyst and quartz blocks hold four, the others nine.
- **Dusts**: redstone → glowstone dust ⇒ blaze powder.
- **Hostile drops**, one ladder through the dimensions, steep at each border and into each gate
  item: rotten flesh → bone → string → spider eye → gunpowder → slimeball → phantom membrane →
  breeze rod ⇒ magma cream ⇒ blaze rod ⇒ ghast tear → wither skeleton skull ⇒ ender pearl ⇒ shulker
  shell. Boss drops (nether star, dragon egg, dragon's breath) are left out.
- **Passive drops**: feather → leather → rabbit hide → ink sac → glow ink sac → rabbit's foot →
  armadillo scute → turtle scute.

The shipped Families keep three rules, which a pack may break on purpose:

- **Gates are paid through, never barred.** A **gate item** (the Siphon's ghast tear and blaze rod,
  blaze powder for the eye of ender, netherite, shulker shells) is reached only by a steep Rung, a
  high count and cost; ratios multiply up a ladder, so reaching a Nether drop from Overworld ones
  costs every border crossed. Crying obsidian has no ladder and stays out: ruined portals and
  piglin barter are its sources.
- **No Rung beats vanilla.** Where a vanilla recipe turns one item into another, a Rung between them
  is no better a rate: leather sits below rabbit hide, which crafts four to one into it.
- **No food.** Meats, eggs and crops are trivially farmed and feed their own cooking.

Farming up a ladder is allowed, the farm's yield halving or worse at each step.

## Loops

Loops that come out ahead are allowed and are meant to be the endgame puzzle. What bounds them is that every harvest permanently consumes the world and every Displacer
ends as Voidstone. The Void Siphon alone must never close a profitable loop.

## Open

- **Voidstone raising Void Pressure around it** — a later layer, compounding a site's harvests.
- **Tool and armour Families** — wooden → stone → copper → iron → golden → diamond, and what an
  upgrade carries over: enchantments, damage, names, trims.
- **Numbers** — the pressures and grades beyond the provisional bands above, the Density curve, Zone sizes and upgrade costs,
  each Dormant machine's cost, how much a Voidstone holds, each Rung's count, cost and chance (provisional: metal, gem
  and dust Rungs count 1, drop Rungs 2, steep Rungs 8; a Rung's cost is 100 void energy times its place up
  the ladder, eight times that when steep; chance 0.8, 0.5 when steep), breakdown yield against motes (provisional: 25% rising toward 75%, each 25,600 void energy spent
  closing half the gap), the Void Crucible's speed and how near Crucibles share it, mote speed, Void Well capacity,
  the Void Siphon's capacity per tier, and the network pressure cap, the Void Dragon's load, speed and harm rate, and the rise in hatching
  cost.
