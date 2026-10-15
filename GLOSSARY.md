# Voidworks

Voidworks harvests void from the world itself, at a permanent cost to the world, moves it between dimensions on dragons, and spends it to power gear and mutate items. It requires nothing a player installs but Minecraft and NeoForge; the libworks-runtime it uses is nested in its jar. The design is [docs/spec/void-energy.md](docs/spec/void-energy.md).

## Language

The Module's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Module**:
One of the 5thlayer mods, each owning one capability, playing alone in vanilla and requiring no
other Module.
_Avoid_: Library, Binding

**Voidworks**:
This Module: harvesting, Voidstone networks, the Void Dragon and Mutation. It requires nothing a player installs but Minecraft and NeoForge.

**Consumer**:
A mod or a pack that builds against or loads this Module.
_Avoid_: client (collides with the game's client side), dependent, integration

**Works tab**:
The creative tab the suite's Modules share, registered by the libworks-runtime nested in the jar. Every Voidworks item shows there and in no other tab; Voidworks has no tab of its own (FactoryWorks ADR-0128).
_Avoid_: Voidworks tab, creative tab (alone)

**Gallery pack**:
The optional datapack in the jar, "Voidworks Gallery", offered and off by default, whose functions lay the Module's features out as working scenes, one plot per scene with a sign naming it, for an operator player to see them at work (ADR 0008). Unlike `dev_pack/` and `gametest_pack/`, the datapacks only the Module's own runs load, it ships. It holds functions only, so turning it on changes nothing about how Voidworks plays.
_Avoid_: showcase (the `/voidworks showcase` command), demo world

### Void

**Void Mote**:
The unit of void, harvested, stored and moved; "mote" for short. A mote carries a grade: one step above the Void
Pressure where it was harvested, so it is always worth something there. Grades run from the lowest
any harvest gives to a fixed maximum. A mote is never an
item: only Voidstone, void machines (a Consumer's too) and the Void Siphon hold motes, pooled by
grade, and motes of different grades never pool.
_Avoid_: mote item, stack of motes

**Void energy**:
What a mote releases when it is spent: the more its grade exceeds the Void Pressure where it is
spent, the more it releases, doubling with each step. Nothing stores void energy; everything stores motes. It is not FE,
but a Void Siphon converts what it releases into FE in the FE items it charges.
Bare "void" means this mechanic; removing items is "destroying" or "trashing" them.
_Avoid_: voiding (for destroying items), void as "needs no power"

**Void Pressure**:
The ambient void level of a place, a small integer: a base per dimension, highest in the End, then
the Overworld, lowest in the Nether, and one step higher where the End is open to the void below. It sets the grade of motes harvested
there, the value of motes spent there, how fast a Void Crucible works there, and how fast a Void
Dragon diving to or from there weakens. Motes stored nearby never change it.
_Avoid_: network pressure (the motes' own, which only steers flow)

**Harvest kind**:
What a dimension harvests, named by its Void Pressure entry: `end`, `overworld`, `nether`, or `none`
for a dimension that harvests nothing. The Void Siphon behaves by it, and Voidstone placed there looks
by it.

**Density**:
How many motes a block yields when fed to a Void Displacer. It is set per block, by item tag, and
steepens sharply with each step: storage blocks are how harvesting scales. Only listed blocks have a
Density; every other block, easily renewed and abundant ones above all (cobblestone, stone, dirt,
sand, netherrack, end stone), has Density 0 and nothing harvests it, so every harvest, and every
Voidstone, costs something mined.

### Harvesting

**Voidstone**:
The indestructible block a harvested position becomes. Every harvest leaves one, so harvesting
permanently consumes the world, which is what bounds void. Touching Voidstone forms a network that
carries motes. It looks like the dimension it was placed in, by its harvest kind, but it is one block, and its networks
join whatever it looks like.
_Avoid_: bedrock, residue

**Void Displacer**:
A machine that harvests motes until its Zone is spent, attuned to the dimension it is built for
and working only there. It holds no motes and needs no network to start: it harvests outward from
itself, only positions touching it or the Voidstone it has already left, nearest first, so what it
has harvested is the network its motes go into. It cannot be broken or moved while its Zone is
unspent and it can still reach something to convert; then it becomes Voidstone itself.
_Avoid_: harvester, Void Well

**Zone**:
How many positions a Void Displacer harvests in its life. It is a count, not a region: the
Displacer grows outward until its Zone is spent, and upgrades paid in motes raise it.
_Avoid_: area, range, radius

**Void Siphon**:
The item a player harvests motes with by hand, at a far worse yield than a Void Displacer's. It
holds motes and keeps them wherever it goes, as many as its tier allows, and spends them to charge FE items.
Standing on Voidstone, it charges from the network or empties into it, as its holder sets it; set to
charge, it also charges the FE items its holder wears and holds, wherever they are.
Its tiers, the Void Siphon, the Resonant and the Draconic, hold more and charge faster, and it climbs
them at a smithing table.
_Avoid_: glove

**FE item**:
Any item, another mod's, that holds FE and takes it through NeoForge's item energy capability, such
as Gearworks' Gear. A Void Siphon set to charge charges it while it is worn or held, never in the rest
of the inventory; Voidworks makes no gear of its own.
_Avoid_: void gear, equipment (belt equipment in the suite), Gear (Gearworks' suit alone)

**Dormant**:
A void machine placed but not yet paid for: it soaks its cost in motes where it stands, from any
touching mote store (a Voidstone network, another void machine) or an emptying Void Siphon, and
works once it is paid. A machine that costs no
motes, such as the Overworld's Void Displacer or the Void Crucible, is never dormant.
_Avoid_: frame, uncharged (charging is the Void Siphon's)

### Moving motes

**Void Well**:
A large store of motes on a Voidstone network, and the stop where the Void Dragon loads, unloads
and heals: a 5×5×5 well of casing and a raised rim, its core, which holds the store, set in one
wall. Voidstone touching any block of it joins it to that network. A Well is the face of its
network: what it holds, how full it is, its fill target and what a dragon takes from it or heals
from are the whole network's, its core and every Voidstone together.
_Avoid_: roost, Void Displacer

**Stop**:
An owner's Void Wells on one network, which their dragons treat as one: a dragon never carries
between them, stops at whichever its dive reaches soonest, and judges the network by the highest of
their fill targets. Another owner's Wells on the network are that owner's stop.
_Avoid_: station, hub

**Fill target**:
How full a Void Well asks to be kept, as a share of all its network can hold: none, a quarter, a
half, three quarters or full. Void Dragons deliver to a stop below its target, the highest of its
Wells', and take from one above it; a stop whose Wells have none is a reservoir, which only gives.
_Avoid_: quota, setpoint

**Owner**:
The player a Void Dragon and a Void Well belong to: whoever placed the egg it hatched from, or the
Well's core. A dragon serves only its owner's Wells.
_Avoid_: tamer, master

**Void Dragon**:
A dragon raised on void that carries motes between Void Wells, diving through the void between
them, across dimensions. It grows from a Dragonling. Nothing hurts it: it only weakens on dives
outside the End, heals at its Wells, and never dies.
_Avoid_: Ender Dragon (the boss), tamed dragon

**Dive**:
A Void Dragon's passage through the void between two Void Wells, in any dimensions. It is a
distance the dragon covers at its speed, slower as it weakens on the way, and nothing in the world
sees the dragon, or its rider, while it lasts.
_Avoid_: teleport, flight

**Trip**:
A Void Dragon's errand: to a source Well to load, a dive, and on to the destination Well to unload.
It is claimed as it is planned, so a network's need counts the motes already on their way to any
Well on it, whoever's dragon carries them. A
load no Well needs is stowed: carried to the owner's Well with most room.
_Avoid_: route, job

**Dragonling**:
A young Void Dragon, hatched from an egg soaked in motes. It cannot carry yet; it stays near where
it hatched, follows a player holding a Void Siphon, and grows faster for the motes a Siphon feeds it.

**Drained Dragon Egg**:
The egg a grown Void Dragon lays as it works: a dragon egg drained of void. It and the vanilla egg,
the founder of every line, both soak motes once a player places them touching Voidstone, as a
Dormant machine soaks its cost, and hatch once they have soaked enough; broken first, an egg loses
what it soaked.
_Avoid_: lesser egg

### Mutation

**Mutation**:
Turning an item into another: upgrading it to the next tier of its Family, or breaking it down into
its ingredients. A Mutation Chamber upgrades quickly for motes, a Void Crucible upgrades slowly and
for free, and an Unmaking Chamber breaks down for motes. An upgrade is a chance roll that keeps the
item on failure, and a breakdown never returns more than went in.
_Avoid_: transmutation, recycling

**Mutation Chamber**:
The machine that upgrades items up their Family, spending motes. It never breaks items down.

**Unmaking Chamber**:
The machine that breaks an item down into the ingredients of its crafting recipe, spending motes.
Each ingredient comes back only by chance, at its yield: a quarter at first, rising with the motes
it has spent in its life, never to all of them. Broken, it forgets what it has spent.
_Avoid_: recycler, grinder, Void Sunderer

**Void Crucible**:
A block that holds items in Void Pressure until they upgrade up their Family. It spends no motes,
only time, and is never Dormant. It works twice as fast for each step of Void Pressure, never
fails an upgrade, and progresses only while its chunk is loaded. Crucibles near each other share
one rate, so many together upgrade no faster than one.
_Avoid_: altar, cradle

**Family**:
An ordered ladder of Rungs that an upgrade climbs, one item form to a Family: metal ingots climb
copper → iron → gold, metal blocks have a Family of their own. Families are data, so other mods'
items can join a Rung. An item on a top Rung or in no Family has no upgrade, unless another
Family carries it on.
_Avoid_: tier, quality, material ladder

**Rung**:
One step of a Family: the items accepted there, and the one result item an upgrade from the Rung
below lands on, with what the step costs: how many items it takes per result, how much void energy
a Mutation Chamber spends on it, and its chance. A Rung always accepts its own result, so an upgraded
item climbs on; the bottom Rung only accepts. Another mod's item joins a Rung as an input; the
result changes only on purpose.
_Avoid_: tier, level

**Steep Rung**:
A Rung whose count and cost are set high on purpose: the toll before a Gate item or across a
dimension border. It is a description of the numbers, not a kind of Rung.
_Avoid_: hard Rung, gated Rung

**Gate item**:
An item the progression uses as a toll before the step that earns it: the Void Siphon's ghast tear
and blaze rod, crying obsidian, netherite, blaze powder (the eye of ender), shulker shells. A Family
may reach one, but only by a steep Rung, so a gate is paid through, never barred.
_Avoid_: key item, progression item
