# Void energy and Mutation — design

Resolved in two grilling sessions on 2026-10-06, the second on void logistics, and a third on
2026-10-07 that made motes a resource and never an item ([ADR 0002](../adr/0002-motes-are-a-resource-never-items.md)), and a fourth on
2026-10-11 that started void in the Overworld, before the End ([ADR 0003](../adr/0003-void-starts-in-the-overworld-before-the-end.md)), and a fifth the same day
on Families, from the original
idea in FactoryWorks' [docs/ideas/mutation_system.md](https://github.com/5thlayer/factoryworks/blob/main/docs/ideas/mutation_system.md), which stays as written,
and a sixth on 2026-10-13 on the Void Dragon, which made it server state rather than an entity
([ADR 0005](../adr/0005-a-void-dragon-is-server-state-not-an-entity.md)), and a seventh on
2026-10-14 that made the Void Siphon charge other mods' FE items in place of void gear of our own
([ADR 0007](../adr/0007-a-void-siphon-charges-fe-items-and-voidworks-makes-no-gear.md)),
and an eighth on 2026-10-15 that shipped a Gallery pack ahead of libworks
([ADR 0008](../adr/0008-voidworks-ships-an-optional-gallery-pack-ahead-of-libworks.md)),
and the [Carry matter between dimensions](https://github.com/5thlayer/voidworks/issues/86) map,
closed on 2026-10-16, which had Matter-line Void Dragons carry items between dimensions
([ADR 0011](../adr/0011-matter-line-void-dragons-carry-items-between-dimensions.md)).
Factorio fidelity was not a constraint.

The terms are in [`GLOSSARY.md`](../../GLOSSARY.md).

## Where it lives

In **Voidworks**, a mod of its own in its own repository, started from libworks' template. Nothing
the Modules do uses void, so it is not something the base mod holds for them, and a player can
leave the End-centred endgame out. It requires nothing a player installs but Minecraft and NeoForge
(libworks-runtime, which shows it in Jade, is nested in its jar): its inputs are
vanilla, Mutation reads the recipe manager, and its outputs go through NeoForge's item and fluid
faces. The Void Well is placed in one step from its core when Groundworks is present, and is built
by hand otherwise: its shape is a Groundworks Assembly declared as data, which Groundworks alone
reads. If the Displacer becomes a multiblock, it may require Groundworks, as Beltworks and
Wireworks do. It charges other mods' FE items through NeoForge's item energy capability alone,
so Gearworks, whose Gear it was tuned against, is only a mod its dev client runs, never a
dependency, and its game tests charge an FE item of their own.

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
   cities, whose shulker shells make the Void Well; the Void Dragon carries between Wells, and lays
   the eggs of the dragons after it.
6. **The Matter line.** A Drained Dragon Egg upgraded by Mutation becomes a Matter Dragon Egg, whose
   dragons carry items between Wells in any dimension, paying a Toll in void. The dense blocks an
   End Displacer wants, made in Overworld and Nether factories, ride to the End. Until then the
   player carries them by hand: one busy End Displacer eats about two shulker boxes an hour.

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
- Local means where the spender stands: a machine's own block, the player's feet for the Void Siphon
  and the FE items it charges, the egg's block for hatching. The open void's extra step counts for spending too, so
  a machine built over it refuses all but the top grade.
- So the End is the reservoir and the Nether the turbine: one mote does the most work in the
  Nether, and moving motes downhill is why void logistics exists. A base runs on its own harvest
  at the lowest value, and the cheap Nether and Overworld motes pay for a start before the End
  pays off; carrying them downhill is what multiplies them.
- Every spend follows this rule — Mutation, charging FE items, zone upgrades, Displacer recipes, hatching,
  void-only materials — except healing the Void Dragon, which happens only at a Void Well.
- What motes buy: FE for other mods' gear, through a Void Siphon, Mutation, and void-only materials (a void alloy and the
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
harvests in its life, a count and not a region; Zone upgrades raise it. Built touching an
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

Each Displacer has one item output inventory with an item face. An empty hand takes its output, or
with the output empty gives back its fed blocks. A full output inventory, tank or network stalls it. While its Zone is unspent and it can still reach something to convert — air,
natural terrain, lava — it cannot be broken or moved; then, once its output is empty, it becomes Voidstone
itself, its motes stay in the network, and relocating means building a new one. One that can reach nothing before
its first harvest was misplaced, and pops off as its item rather than becoming Voidstone. Zone upgrades raise a Zone;
relocation stays the main loop.

**Zone upgrades.** A Displacer starts with the server's Zone (provisional: 64) and buys more one
level at a time, with motes from its network. Sneak-using it with an empty hand quotes the next
level in the action bar ("Zone 64 → 80 for 1,600 void energy, sneak-use again to confirm"), and the
same player sneak-using it again within 5 seconds commits to it; anything else lets the quote lapse.
A network whose motes are worth less than the price at the Displacer's block is told the price and
what it is worth there, and arms nothing. Committed, the Displacer soaks the price as a Dormant
machine does, from the stores touching it by the spend rule at its block, while it goes on
harvesting, and its Zone rises once it is paid, the players near it told and hearing it; a payment
in part is kept across a save. Sneak-using
it while it soaks shows what is paid and offers to cancel, confirmed the same way; a cancelled
upgrade loses what it paid, as any spend does. A Displacer can be upgraded while it is awake and
not yet Voidstone: stalled, starved of fed blocks, or with its Zone spent and its output still
emptying, when it waits for the upgrade's soak, unbreakable, and harvests on. A Dormant one cannot,
nor one that can reach nothing; one left with nothing to reach while it soaks becomes Voidstone all
the same, and what it paid is spent. Each level adds a step of positions and costs a first price times a
growth per level bought, both datapack data per Displacer (provisional, all three: 16 positions,
1,600 void energy, doubling), with no last level. The levels belong to the Displacer, so a new one
starts at the first: the first two levels repay themselves at a middling Density, the third breaks
even, and from the fourth a new Displacer is cheaper. Jade shows a Displacer's level, the next
level's price, and an upgrade's soak.

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
or empties into it), or by charging FE items; no block takes a Siphon, so item logistics
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

**Charging FE items.** Voidworks makes no gear of its own
([ADR 0007](../adr/0007-a-void-siphon-charges-fe-items-and-voidworks-makes-no-gear.md)): a Siphon
set to charge spends its motes into the FE items its holder wears or holds, any mod's item that
takes FE through NeoForge's item energy capability, such as Gearworks' Gear. It does so wherever the
holder is, once every Siphon interval, by the spend rule at the player's feet, in a dimension with
no harvest kind too, and the void energy released becomes FE at a fixed rate. So on Voidstone a
Siphon set to charge fills itself and the gear at once, and one set to empty keeps its motes for a
Dormant machine or a Dragonling. Only the four armour slots and both hands are charged; an FE item
elsewhere in the inventory is not, so a Siphon never fills batteries to carry to a base.

- Siphons spend in inventory order, the hotbar first, and their FE fills chest, legs, feet, head,
  main hand, then off hand, each item taking what it accepts.
- A Siphon spends a mote only when the items have room for all the FE it releases, so no FE is lost
  to a whole mote at the top of a battery.
- A tier's rate caps it: at most that many motes into FE items per interval.
- While its holder wears or holds an FE item, the Siphon's tooltip shows about how much FE its motes
  would charge there; with none, it shows nothing of FE.

FE comes only from a Siphon: no block turns motes into FE.

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

A built well, 5 by 5 by 5: a large store of motes, the Void Dragon's stop and its healing point. A
3×3 floor of Well casing, a 5×5 ring of casing walls three courses high standing around it, the
shaft inside them open, and a raised rim of Well rim around the top. Its core sits in the middle of
one wall, in its middle course, facing out, so a player and a Void Siphon reach it; the core holds
the store, and the rest is structure. The Well touches through every block of it: Voidstone beside its
casing, its rim or its core joins it to that network and pays its Dormant cost, so the core's face
stays free. It comes with the Dragon, after the dragon: the core's recipe is
shulker shells from the End cities, an ender chest and crying obsidian, placed Dormant, and it soaks
its cost only once the well around it is whole. A Well that is no longer whole stops: it leaves its
network, keeping its motes, until it is whole again. A broken core loses them. Before it, Voidstone
bodies are a base's storage. Its pressure stays low until it is nearly full, so with no fill target
it is a reservoir that soaks up surplus and gives it back to whatever draws on it: a spender pulling
motes toward it draws them out of a Well whatever its pressure, only the grades worth something
where it spends them, so motes it cannot spend never crowd out those it can. While it draws, a Well
soaks up none of those grades from the Voidstone between it and the spender: they are on their way.

A Well's **shaft shows its fill**, drawn and never placed, so the shaft stays open air: a
translucent void fluid, deep purple-black with a slowly moving surface and a faint glow, as high
as its network is full, so every Well on a network shows the same level, easing to a new level as
motes come and go. Butterflies tinted by grade drift in it below the surface, in the grades the
network holds, more of them the fuller it is, up to a dozen: a look, not a count. The fluid slows,
hides and holds nothing, and a Well that is Dormant or not whole shows an empty shaft. The fill
target is not marked on it.

A Well is the **face of its network**
([ADR 0006](../adr/0006-a-void-well-is-the-face-of-its-network.md)): what it holds, how full it is,
its fill target and what a dragon loads from it or heals from are the whole network's, its core and
every Voidstone together, so what a player reads of a Well is what a dragon uses. A dragon loading
takes from the core first, then from the network's Voidstone, at once, wherever it is; an unload
goes into the core, and only once it is full into the Voidstone nearest the Well. Several Wells on
one network are one stop: a dragon never carries between them, and each is a place to stop, load,
unload and heal. A second Well adds its core's capacity; whether that is worth its build and its
price is its owner's call. Wells of different owners may share a network, and with it its motes;
keeping others out is claiming the land.

A Well has an **owner**, the player who placed its core, and only its owner's dragons serve it. A
Well from before owners existed is claimed by the first player to name it or set its target.
Every control is in the world, with no screen:

- **Fill target**: none, a quarter, a half, three quarters or full of its network's capacity, one
  total over every grade. Its owner cycles it by sneak-using the core with an empty hand; using it plainly
  with an empty hand shows its status and target, and Jade shows both. A Void Dragon delivers to a Well below its
  target and takes from one above it; a Well with none is a reservoir, which only gives.
- **Name**: a name tag its owner uses on the core names the Well and is used up, as naming a mob
  is; anyone else's name tag is refused and tells them whose Well it is. Unnamed, a Well goes by
  its dimension and position.
- **Calling**: its owner standing on its rim calls a dragon (§ The Void Dragon).

### The Void Dragon

Named and raised on the pattern of vanilla's Dried Ghast, Ghastling and Happy Ghast, and reusing
what it can of them: an egg whose stages are its block's, one creature that is young and then
grown, a home it stays near, and an item it follows. Unlike the Happy Ghast it has an **owner**, the
player who placed its egg, and it serves only its owner's Wells
([#53](https://github.com/5thlayer/voidworks/issues/53) weighs making it stealable). A grown dragon
is server state, not an entity: it routes, dives, weakens and heals with no chunk loaded, and the
dragon a player sees near a Well is only its presence
([ADR 0005](../adr/0005-a-void-dragon-is-server-state-not-an-entity.md)). Nothing hurts a
Dragonling or a Void Dragon, and only old age kills a Void Dragon
([ADR 0010](../adr/0010-a-void-dragon-dies-of-old-age.md)). Every dragon has a **Line**, fixed in
its egg: the **Mote line** carries motes and the **Matter line** items, never both
([ADR 0011](../adr/0011-matter-line-void-dragons-carry-items-between-dimensions.md)). This section
says how a dragon carries motes; a Matter-line dragon does everything else here as written, and
§ Moving matter says how it carries items.

- **Eggs.** The first dragon kill gives the vanilla egg, the founder of the Mote line. Later kills give
  nothing, but while no player has hatched a dragon in the world, a kill places a vanilla egg on the
  exit portal again, so a lost founder is replaced. Every other Mote-line egg is a **Drained Dragon Egg**,
  laid by an adult Mote-line dragon each time it has carried a set number of motes since its last, or
  since it became adult, counted as it unloads them, a stowed load too: it pops out of the shaft of the next Well it stops at, to
  load or unload, as an item, so eggs are given and traded. A Well whose chunk is not loaded lays
  none: the dragon owes the egg, with any others it comes to owe, to its next stop at a loaded Well,
  where it lays them all, so its eggs follow only the motes it carries; eggs still owed as it turns
  senior are laid the same way, and none after. A dragon lays eggs of its own Line, so 8 in its life.
  The Matter line's egg is the **Matter Dragon Egg**: a Drained Dragon Egg upgraded by Mutation
  (§ Families) founds the line, never the other way, and an adult Matter-line dragon lays one by
  the same rules, counted in the stacks it unloads (§ Moving matter). Until it soaks, a Drained or
  Matter Dragon Egg falls and teleports when hit, as the vanilla egg does.
- **Hatching.** Any of the three eggs, placed by a player touching a Voidstone network, soaks up motes from it
  by the spend rule at the egg's block, in four visible stages, until it hatches a Dragonling of its
  Line that its placer owns. A soaking egg neither falls nor teleports. Its price is fixed when it starts soaking
  and grows, doubling by default, with every living dragon its owner has, Dragonling or Void Dragon of
  either Line, and every egg they are soaking, so carriers grow as fast as an owner can pay for them,
  a dead dragon's replacement costs what it did, and an egg given away starts its new owner cheap. An egg no player placed
  never soaks. Broken while soaking, it drops the egg it was and loses what it soaked.
- **Growing.** A **Dragonling** is a Void Dragon before it grows, an ordinary creature until then. It stays
  near where it hatched, follows a player holding a Void Siphon, and grows up over a set time while
  its chunk is loaded. Its owner feeds it by using a Siphon on it, which empties into it by the spend
  rule at the Dragonling, and the void energy takes time off its growing. It cannot carry, and does
  not weaken.
- **Ageing.** A grown Void Dragon ages through four **life stages**, **young**, **adult**, **senior**
  and **old**, by the cargo it unloads, in its Line's unit: each stage lasts the same amount, so a
  stage changes only at a stop, as it unloads. Its load takes the new stage's size from its next
  load, and its speed from its next dive; a load it holds is never cut. Each stage multiplies the
  Line's speed and load (provisional: young 1 and 1, adult 0.6 and 2, senior 0.35 and 3.5, old 0.2
  and 6), so a young dragon is a quick courier and an old one a slow freighter, carrying about as
  much an hour either way. Stages are not inherited: every dragon lives all four, and bred traits,
  if they come, multiply on top of them. An old dragon whose stage runs out **dies**, only at a stop
  at a loaded Well, once that stop is over: carrying nothing, it lays the eggs it owes, sets any rider
  down on the rim, even one still mounted, then its presence rises over the Well in a short Ender
  Dragon death, beams and sound, and leaves nothing. Its stage running out at a Well that is not
  loaded, it lives on and dies at its next stop at a loaded one, so no egg is lost. Nothing else
  tells its owner. Dragons from before life stages start young, their count toward an egg dropped. Matching a dragon's stage to a trip, fast for far and big for large shortfalls, is left
  to routing as it is, and to a later ticket if play needs it.
- **Routing.** A Mote-line dragon serves all its owner's Void Wells in every dimension. It flies only to a
  Well below its fill target, and takes from the owner's Well of highest network pressure that holds
  something worth delivering there, a reservoir or a Well above its target, which gives its excess;
  a Well below its target never gives. A network with several of the owner's Wells is judged by the
  highest of their targets, a reservoir among them setting none, and another owner's targets never
  bind it; it stops at whichever of them its dive reaches soonest, the nearest of those it reaches
  as soon. It loads only grades worth
  something at the destination's Void Pressure, highest first, up to its load. Each trip is claimed,
  and a network's need counts the motes already on their way to it, whoever's dragon carries them,
  as what it gives counts those any dragon is on its way to load there, so several dragons share the
  work without overshooting. A
  destination that is broken, no longer whole or no longer short when the dragon arrives sends the
  load on to another of the owner's Wells that needs it, else to the one with most room; with no
  room anywhere it holds the load and rests until there is.
- **Travel.** It flies visibly near its Wells and **dives** into the void between them, emerging
  after a travel time: a time per block of distance in Overworld blocks, the Nether's counting eight
  and the End's one, and a fixed time more for a dive between dimensions. Every dive takes at least
  2 seconds, so even between near Wells it is seen diving into one shaft and emerging from the
  other. It is the only thing that carries motes between dimensions at scale; a player carries a
  Siphon's worth. With nothing to do it rests at the Well it last stopped at, neither weakening nor
  healing: it lands and sits astride the Well's rim over its shaft, lengthwise along the core's wall
  so neither head nor tail covers the core, in the Ender Dragon's sitting pose, its wings held still
  and down and its head turned to a nearby player, never roaring or breathing at anyone. Where
  blocks fill the room it would sit in, it flies about over the shaft instead, and lands once there
  is room. As it sets off it takes off, rising over the shaft within its dive's time, then dives
  into the shaft. Holding a load it has no room for, it rests the same way. At work there, loading,
  unloading, waiting for its owner or setting a rider down, it stays in the air close about the
  Well. One with nothing to do away from its owner's Wells, whole and awake, such as one just grown or
  one whose Well went Dormant or was broken, dives to the nearest of them and rests there.
- **Riding.** Its owner uses a dragon stopped at one of their Wells, resting or at work there, and
  mounts it, with no harness; anyone else cannot. It hovers, taking off first if it sat resting
  (and landing again if they get off before a dive), and waits at its stop for as long as
  they ride: strafing left and right steps through the owner's other Wells, whole and awake, in the
  order they were placed, the one picked shown above the hotbar by its name, or its dimension and
  position; jumping dives, and sneaking gets off before the dive, onto the rim of the Well it is at.
  Through the dive no one sees them, and they cannot act, get off or be hurt; the dragon sets them
  down on the rim of the Well they picked, in whatever dimension. It is not free flight. The rider's
  Well is a stop added to the trip, which drops what that Well is short of, in grades worth
  something there, and the rest goes on to the claimed destination at the usual speed.
- **Ridden dive.** A dive with a rider aboard, to the rider's Well, takes at most a set short time
  (provisional 3 seconds), however far and across whatever dimensions; one already shorter keeps
  its own time. The rider takes up room: its load shrinks by the share of time saved, so a ridden
  dive carries no more motes a second than the same dive unridden. A dragon holding more than that
  as the rider jumps first unloads the excess into the Well it is at, as an ordinary unload; if the
  Well's network has no room for all of it, the dragon does not dive, and the rider is told so
  above the hotbar, staying mounted. The rider watches the dragon start down, unseen from the jump
  on, and half a second after it has taken them into the shaft, their eyes below the Well's rim,
  sees the void, its motes swirling in, and in the middle of the screen the picked Well's label,
  as the picker showed it, over a bar that fills as the dive goes, in the destination dimension's colour (Overworld blue,
  Nether red, End pale yellow-green, a datapack map naming others, and the void's purple for any it
  does not). The Nether portal's hum rises toward emerging.
- **Calling.** The owner standing on the rim of one of their Wells for a second calls a dragon to
  it, the block under their feet a rim block of that Well, whether or not they are on the ground; a
  hop does not end a call, stepping off does. Anyone else's rim calls nothing and says nothing. A
  dragon of theirs already stopped at that Well answers by staying there, the one that just set them
  down or that they got off among them; otherwise the next of theirs to finish its stop, or one with
  nothing to do at once, the nearest of those, comes to that Well as though its owner rode it
  already: its dive takes no longer than a ridden dive, and it first puts what it holds beyond its
  ridden load into the Well it is at, as for a ridden dive; with no room there for that, it dives
  the whole way with all it carries. It drops none of its load at the called Well, the action bar
  telling them one is coming, with a bar just over it that fills as its dive goes, in the colour of
  the called Well's dimension, and once it waits there, that it waits for them. It waits a stop's
  time at least, and while the owner stays on the rim, then goes on with its trip; one already on
  its way when they step off still comes, and waits its stop's time. Only one of the owner's dragons
  answers a call.
- **Harm.** It weakens as it dives, at a rate set by how far the Void Pressure at the worse of the
  dive's two ends is below the End's, so a trip to or from the Nether, where motes pay most, costs it
  most, and one between End Wells nothing. Stopped at a Well or resting, it does not weaken. Hurt, it
  flies more slowly, its health's share of its stage's speed, never below a quarter of it, and a dive slows as it weakens during it, so a
  long dive to or from the Nether takes longer still; weakening never kills it. It never dives from a Well before
  that Well has healed it, from its store, a fixed number of motes per point of health, lowest grade
  first and not by the spend rule, as far as the motes go; an empty Well heals nothing.
- **Seen in Jade.** Anyone looking at a dragon's presence sees what its record holds: what it is
  doing, its life stage, the Well it is bound for by name, what it carries (a Mote-line dragon's
  motes by grade and in total, a Matter-line dragon's load as § Moving matter shows it), its health
  and its owner, with no line naming its Line, since its load already shows it; looking at a
  Dragonling, how far it has grown and its owner.
- **In the way of no one.** A dragon's presence pushes no one: a player stands on the rim beside a
  dragon sitting astride it, or under one hovering at work, and is not moved. Nothing collides with
  it. A Dragonling pushes and is pushed as any mob does.
- **Look.** The vanilla Ender Dragon, its model and its texture read from the player's own game at
  runtime and tinted the void's purple, whatever its Line: the Void Dragon about half the Ender Dragon's size, scaled by its stage (young 0.8, adult 1, senior
  1.1, old 1.2), the
  Dragonling about a fifth. Nothing of Mojang's ships in the jar, and the dragon has no texture of its
  own. A dragon shows its Line by what it carries: a Mote-line one trails `DUST` sparks in the
  colours of the grades it carries, and a Matter-line one hangs its first carried stack under its
  body (§ Moving matter), as a Matter-line Dragonling holds a chest. The Drained Dragon Egg is a pale, cracked dragon egg on the vanilla egg's shape, its cracks dark
  while it is only an egg. Soaking, either egg wears that look, the vanilla egg too, though it is still
  the vanilla egg it drops if broken; its cracks spread and glow brighter at each of its four stages,
  glowing in the dark. A soaking Mote-line egg gives off mote sparks. The Matter Dragon Egg is the
  Drained Dragon Egg tinted amber through a `tintindex` and a block and item colour handler, soaking
  or not, so it has no texture of its own and its colour tells it from a Mote-line egg in a chest;
  soaking, it gives off `ITEM` flecks of itself.

## Moving matter

Matter-line Void Dragons carry items between their owner's Void Wells, in any dimension
([ADR 0011](../adr/0011-matter-line-void-dragons-carry-items-between-dimensions.md)); before the
Matter line, a player carries them by hand. A Matter-line dragon is a Void Dragon in everything but
its cargo: it stops at its owner's whole Wells, heals from their motes, weakens by Void Pressure,
ages, is ridden and called, rests on a rim, holds a load it has no room for and never carries
within a network, all as § The Void Dragon says. It ignores motes as cargo, and a Mote-line dragon
ignores Well hatches.

### Well hatches

A **Well hatch** is where a Matter-line dragon loads and unloads, a requester or a provider after
Factorio's chests.

- **Placing.** A full block, made from a shulker box and Well casing. It is placed against the
  outer face of any wall casing block of a Void Well, on any course, never against the core and never
  on top of the rim, and joins the Well it was placed against without being part of its structure.
  A Well takes any number. Only the Well's owner places one against it, and only they control it.
- **Holding.** 27 slots. Its contents live in saved data, as a Well's motes do, so a dragon loads
  and unloads it with no chunk loaded. Every face is an item face, so hoppers, belts and pipes fill
  and empty it, a hopper beneath it with nothing but vanilla. Its Well no longer whole, it is idle
  and keeps what it holds; its core broken, it is a plain container until a new core makes the Well
  whole again.
- **Requester or provider.** It is placed a provider, and its owner switches it by sneak-using it
  with an empty hand. A **provider** offers whatever it holds to its owner's requesters elsewhere,
  and never asks; using it with a stack puts the stack in, and with an empty hand takes one out.
  A **requester** asks for one item, so many stacks of it, and never gives; it empties only through
  its item faces. Using an item on it sets its request to that item's kind, components ignored, and
  adds a stack per use, up to 27, shown above the hotbar; another item replaces the request and
  starts again at one stack; an empty hand clears it. A requester switched to a provider loses its
  request and offers what it holds; a provider switched to a requester keeps what it holds for its
  item faces to drain.
- **Seen in Jade.** Its mode, its request and its contents. A provider shows how many of its stacks
  its network can pay the Toll to lift, against what it holds: `Holds 9 stacks; its network can pay
  the Toll for 2`; when it can pay for none, a stall line, as on Voidworks' machines: `Stalled: its
  network can't pay the Toll for one`. The Well's own Jade shows no Toll.

### The Matter line

- **Routing**, mirroring the Mote line's. A stop's hatches are all those on its owner's Wells on
  that network; another owner's are never touched. A requester is short by its request, less what it
  holds and what is on its way to it. A dragon flies to a stop whose requesters are short, choosing
  as a Mote-line dragon chooses its destination, and loads from the owner's stop whose providers hold
  most of those items, the item most short first, up to its load. Trips are claimed: what is in
  flight counts against both the request it fills and the offer it came from. A provider stop's
  offer is capped at the stacks its network can pay the Toll for to that destination, and a stop
  that can pay for none is never picked. On arrival, what does not fit goes to another of the
  owner's requesters asking for it, else to the owner's providers with room, else the dragon holds
  it and rests until there is room.
- **Load**, counted in **stacks**, each slot full by its item's stack size: young 27, adult 54,
  senior 94, old 162, the life stages' load multipliers on a young load. A filled container, such as
  a shulker box, takes one stack of it. A young dragon moves about 1,600 to 2,700 stacks an hour, so
  the Toll, not the dragon, bounds throughput.
- **Toll.** As it loads, a Matter-line dragon pays a **Toll** from the departure Well's network, by
  the spend rule at that Well's core. It is counted in **stack-equivalents**: a part stack pays its
  share, its items over the item's stack size (16 ender pearls a whole one, 32 cobblestone half),
  and a filled container pays for what it holds, counted the same way, plus one for itself. Each
  stack-equivalent costs 256 base void energy, doubled for each step the destination Well's Void
  Pressure stands above the departure's, both read at their Well's core, so an End Well over the open
  void climbs one step more; a level or downhill trip pays the base. One price for every item, in
  datapack data ([ADR 0009](../adr/0009-prices-are-datapack-data-and-physics-is-server-config.md)).
  By the spend rule, a stack lifted into the End costs the same top-grade motes from anywhere:

  | Trip | Toll | Paid in Nether grade 2 | Overworld grade 3 | End grade 4 | Open void grade 5 |
  |---|---|---|---|---|---|
  | Nether → End | 1,024 | 1,024 motes | 512 | 256 | 128 |
  | Overworld → End | 512 | refused | 512 | 256 | 128 |
  | End → End, End → Nether | 256 | refused | refused | 256 | 128 |
  | Nether → Nether | 256 | 256 | 128 | 64 | 32 |
  | Overworld → Overworld | 256 | refused | 256 | 128 | 64 |

  So a Density-2 stack lifted to the End breaks even, 256 grade-4 motes paid and 256 harvested, and
  an End Displacer over the open void doubles that; a diamond stack yields sixteen times its Toll, a
  copper one a quarter. Density stays the reason to ship dense blocks. The Toll is taken first, then
  the dragon heals from what is left, as far as it goes, so a poor network sends a loaded, slow
  dragon, and never runs into debt. It is paid once, priced to the claimed destination: a load sent
  on to another Well, dropped in part at a rider's Well or held while the dragon rests pays nothing
  more and gets nothing back. The Mote line pays no Toll.
- **Riding and calling** are the Mote line's, in stacks: a rider's Well is a stop that takes what its
  requesters are short of, a ridden dive's load shrinks by stacks, and what it holds beyond that as
  the rider jumps goes into the Well's hatches as on arrival, the dragon not diving if they have no
  room for it.
- **Life**, in stacks unloaded: each life stage lasts 1,728 stacks (64 young loads), and an adult
  lays a Matter Dragon Egg each 216 (8 young loads), so 8 in its life, as the Mote line lays. Its egg
  hatches at the same price as any other.
- **Seen.** Its presence hangs its first carried stack under its body, drawn with the item renderer
  from that stack's own item model, a shulker box when it carries one, so the presence syncs that
  first stack. Jade lists its load with item icons, one line per item, a filled container showing its
  contents, then a line such as `6 of 27 stacks, Toll 3,072 paid`, the Toll this load paid as it
  loaded.

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
- **Dragon eggs**: Drained Dragon Egg ⇒ Matter Dragon Egg, one egg for one, which founds the Matter
  line ([ADR 0011](../adr/0011-matter-line-void-dragons-carry-items-between-dimensions.md)). The
  vanilla egg stays out with the other boss drops, so the Matter line waits for a Mote-line adult's
  eggs.
- **Hostile drops**, one ladder through the dimensions, steep at each border and into each gate
  item: rotten flesh → bone → string → spider eye → gunpowder → slimeball → phantom membrane →
  breeze rod ⇒ magma cream ⇒ blaze rod ⇒ ghast tear → wither skeleton skull ⇒ ender pearl ⇒ shulker
  shell. Boss drops (nether star, dragon egg, dragon's breath) are left out.
- **Passive drops**: feather → leather → rabbit hide → ink sac → glow ink sac → rabbit's foot →
  armadillo scute → turtle scute.
- **No tools or armour.** An upgrade gives a fresh result and carries no item data, so gear has no Family; void energy reaches gear as FE through the Void Siphon instead ([ADR 0007](../adr/0007-a-void-siphon-charges-fe-items-and-voidworks-makes-no-gear.md)).

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

## The Gallery pack

Voidworks ships a **Gallery pack**, as FactoryWorks [#691](https://github.com/5thlayer/factoryworks/issues/691)
plans for every Module in the FactoryWorks Showcase, ahead of libworks
([ADR 0008](../adr/0008-voidworks-ships-an-optional-gallery-pack-ahead-of-libworks.md)): an
optional datapack in its jar, "Voidworks Gallery", offered in the Data Packs screen and off by
default, holding functions only. An operator player turns it on and runs `voidworks:gallery/build`,
which lays the Module's features out as working scenes in a row, one function per scene,
`voidworks:gallery/<scene>`, that also builds on its own.

- **Layout.** On the world's axes, whichever way the player faces: a scene's plot starts 2 blocks
  east of where it is run, at the player's feet, and `build` runs each further scene 24 blocks east
  of the last (`execute positioned ~24 ~ ~`, then `~48`), so its plot follows.
  A scene clears its plot, 16 blocks high, floors it with stone and builds on it, so it builds the
  same way wherever it is run and every time. A sign at its south front names the feature, in the
  player's language.
- **Working.** A scene is built working, from Voidworks and vanilla alone. It places blocks, and
  where its feature needs them, the records an operator's commands make; never time, weather,
  gamerules or the player.
- **The Void Dragon**, the first scene: two of the runner's Void Wells 12 blocks apart in one
  dimension, on networks of their own. The west Well is a reservoir, filled through a Voidstone
  beside it with motes one grade above the End's Void Pressure over the open void, so they are worth
  something wherever it is built; the east Well's target is full, and it holds nothing. A grown
  dragon rests at the reservoir and carries between them, about eight trips at the default numbers,
  diving into one shaft and emerging from the other.
- **Commands** make what the dragon scene needs, all for operators:
  - `/voidworks well <pos> <target>` readies the Well core at `pos` for whoever runs it: theirs, with
    that fill target, and its Dormant cost paid, so once the well around it is whole it is in its
    network at once.
  - `/voidworks dragon <pos>` adds a grown Void Dragon owned by whoever runs it, resting at the
    Well core at `pos`. It is not hatched: it doubles no egg price.
  - `/voidworks dragon remove <from> <to>` removes every dragon, whoever's, resting in the box, in
    the runner's dimension, or on a trip to or from a place in it, and its presence with it.
- **Tested** by the Module's game tests as #691's shared check will test it: the pack is offered and
  off; `build`, run by an operator player, runs every command without failing, a failure naming the
  command; and each scene's key block, the dragon scene's two Well cores, stands where the scene
  puts it. A test of its own checks that the dragon scene's dragon sets off from the reservoir for
  the other Well, and that building it again leaves one dragon.
- **Later.** A scene for each of the README's other features, and libworks' helper and check in
  place of Voidworks' own once a runtime release carries them
  ([libworks#30](https://github.com/5thlayer/libworks/issues/30)).

## Loops

Loops that come out ahead are allowed and are meant to be the endgame puzzle. What bounds them is that every harvest permanently consumes the world and every Displacer
ends as Voidstone. The Void Siphon alone must never close a profitable loop.

## Open

- **Voidstone raising Void Pressure around it** — a later layer, compounding a site's harvests.
- **Numbers** — prices are datapack data and the rest server config
  ([ADR 0009](../adr/0009-prices-are-datapack-data-and-physics-is-server-config.md)): the pressures and grades beyond the provisional bands above, the Density curve,
  each Dormant machine's cost, how much a Voidstone holds, each Rung's count, cost and chance (provisional: metal, gem
  and dust Rungs count 1, drop Rungs 2, steep Rungs 8 but the egg Rung 1; a Rung's cost is 100 void energy times its place up
  the ladder, eight times that when steep; chance 0.8, 0.5 when steep), breakdown yield against motes (provisional: 25% rising toward 75%, each 25,600 void energy spent
  closing half the gap), the Void Crucible's speed and how near Crucibles share it, mote speed, Void Well capacity,
  the Void Siphon's capacity per tier, the FE a void energy charges (provisional: 10, tuned against
  Gearworks' Gear, whose battery upgrade holds 100,000; a server config value), and the network pressure cap, and the Void Dragon's (provisional:
  the first egg 25,600 void energy, doubling per dragon; Dragonling growth 40 minutes, 1 minute off
  per 1,000 void energy fed; a young dragon's load of 8,192 motes; a life stage 524,288 motes carried; about 5 seconds a stop; 1 second a dive per 64
  blocks, and 10 seconds more between dimensions; 100 health, weakening by the pressure gap per 10 seconds, healing 1 per 16
  motes; an egg laid each 65,536 motes carried), and the Matter line's (provisional: a young load of
  27 stacks; a life stage 1,728 stacks unloaded; an egg laid each 216 stacks; a Toll of 256 void
  energy a stack-equivalent, doubling per step of Void Pressure climbed; a Well hatch's 27 slots, a
  server config value).
- **FE from a network** — a Voidstone network or a Void Well turning motes into FE for any mod's
  cables: base power, which balancing void energy against FE generation makes a design of its own.
- **Teams** — after the first major version: players sharing Wells and dragons as a team, beyond
  owners sharing a network's motes.
- **Planned routes** — after the first major version: a player directing a dragon's travel beyond
  its automated routing, such as tying one dragon to carrying from one Well to another.
