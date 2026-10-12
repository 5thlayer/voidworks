# Voidworks

Voidworks harvests void from the world itself, at a permanent cost to the world, moves it between dimensions on dragons, and spends it to power gear and mutate items. It requires nothing a player installs but Minecraft and NeoForge; the libworks-runtime it uses is nested in its jar. The design is [docs/spec/void-energy.md](docs/spec/void-energy.md).

## Language

The Module's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Module**:
One of the 5thlayer mods, each owning one capability, playing alone in vanilla and requiring no
other Module.
_Avoid_: Library

**Voidworks**:
This Module: harvesting, Voidstone networks, the Void Dragon and Mutation. It requires nothing a player installs but Minecraft and NeoForge.

**Consumer**:
A mod or a pack that builds against or loads this Module.
_Avoid_: client (collides with the game's client side), dependent, integration

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
spent, the more it releases, doubling with each step. Nothing stores void energy; everything stores motes. It is not FE.
Bare "void" means this mechanic; removing items is "destroying" or "trashing" them.
_Avoid_: voiding (for destroying items), void as "needs no power"

**Void Pressure**:
The ambient void level of a place, a small integer: a base per dimension, highest in the End, then
the Overworld, lowest in the Nether, and one step higher where the End is open to the void below. It sets the grade of motes harvested
there, the value of motes spent there, how fast a Void Crucible works there, and how fast a Void
Dragon weakens there. Motes stored nearby never change it.
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
holds motes and keeps them wherever it goes, as many as its tier allows, and void gear draws from it.
Standing on Voidstone, it charges from the network or empties into it, as its holder sets it.
_Avoid_: glove

**Dormant**:
A void machine placed but not yet paid for: it soaks its cost in motes where it stands, from any
touching mote store (a Voidstone network, another void machine) or an emptying Void Siphon, and
works once it is paid. A machine that costs no
motes, such as the Overworld's Void Displacer, is never dormant.
_Avoid_: frame, uncharged (charging is the Void Siphon's)

### Moving motes

**Void Well**:
A large store of motes on a Voidstone network, and the stop where the Void Dragon loads, unloads
and heals.
_Avoid_: roost, Void Displacer

**Void Dragon**:
A dragon raised on void that carries motes between Void Wells, diving through the void between
them, across dimensions. It grows from a Dragonling.
_Avoid_: Ender Dragon (the boss), tamed dragon

**Dragonling**:
A young Void Dragon, hatched from an egg soaked in motes. It cannot carry yet.

**Drained Dragon Egg**:
The egg every dragon kill after the first drops: a dragon egg drained of void, which hatches once
it has soaked up enough motes.
_Avoid_: lesser egg

### Mutation

**Mutation**:
Turning an item into another: upgrading it to the next tier of its Family, or breaking it down into
its ingredients. A Mutation Chamber does it quickly for motes; a Void Crucible upgrades items
slowly and for free. An upgrade is a chance roll that keeps the item on failure, and a breakdown
never returns more than went in.
_Avoid_: transmutation, recycling

**Mutation Chamber**:
The machine that performs a Mutation, spending motes.

**Void Crucible**:
A block that holds items in Void Pressure until they upgrade up their Family. It spends no motes,
only time, and progresses only while its chunk is loaded.
_Avoid_: altar, cradle

**Family**:
An ordered ladder of Rungs that an upgrade climbs, one item form to a Family: metal ingots climb
copper → iron → gold, metal blocks have a Family of their own. Families are data, so other mods'
items can join a Rung, and an item on the top Rung or in no Family has no upgrade.
_Avoid_: tier, quality, material ladder

**Rung**:
One step of a Family: the items accepted there, and the one result item an upgrade from the Rung
below lands on, with what the step costs: how many items it takes per result, how much void energy
a Mutation Chamber spends on it, and its chance. Another mod's item joins a Rung as an input; the
result changes only on purpose.
_Avoid_: tier, level

**Gate item**:
An item the progression uses as a toll before the step that earns it: the Void Siphon's ghast tear
and blaze rod, crying obsidian, netherite, blaze powder (the eye of ender), shulker shells. A Family
may reach one, but only by a steep Rung, so a gate is paid through, never barred.
_Avoid_: key item, progression item
