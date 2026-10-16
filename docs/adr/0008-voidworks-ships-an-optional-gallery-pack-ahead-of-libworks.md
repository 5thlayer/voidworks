---
status: accepted
---

# Voidworks ships an optional Gallery pack, ahead of libworks

FactoryWorks [#691](https://github.com/5thlayer/factoryworks/issues/691) gives every Module in the
FactoryWorks Showcase a Gallery pack: a datapack in its jar, offered and off by default, whose
function lays the Module's features out as working scenes. It leaves Voidworks out of scope, and
libworks has neither the runtime helper that registers the pack nor the shared check that tests it.
Voidworks needs its gallery now: a grown Void Dragon is server state (ADR 0005), so short of hatching and growing one,
nothing puts a working dragon in front of the camera to check it by eye.

**Decision.** Voidworks ships its Gallery pack now, `gallery_pack/` in its jar, and registers it
itself as a built-in pack that is offered but never added automatically: a pack source of its own
with the built-in label, since vanilla lists a pack of the `FEATURE` source, the other that is never
added automatically, as an experiment at world creation. Its game tests run a check of their own
that asserts what #691's shared check will. Both are written as the template for
libworks ([libworks#30](https://github.com/5thlayer/libworks/issues/30)) and give way to libworks'
helper and check once a runtime release carries them. The pack holds functions only: no recipes,
loot tables, tags or worldgen, so turning it on changes nothing about how Voidworks plays.

A function is only commands, and a scene with a Void Dragon needs records no block placement makes:
an owner's Wells with fill targets, and a grown dragon. Operator commands make them, so the dragon
scene writes those records as well as its blocks, and removes the dragons it built before it builds
again. A dragon a command makes is not hatched: it doubles no egg price, and it does not stop a
dragon kill bringing back the founder egg.

## Considered options

- **Wait for libworks**: the dragon goes unchecked by eye until a libworks release and the
  Fieldworks pilot land, for a gallery Voidworks would then build the same way.
- **Build the helper and check in libworks first**: right in the end, but a runtime release and its
  release train stand between Voidworks and a dragon on screen.
- **A Java command per scene**, as `/voidworks showcase` builds its line: simplest, but the scene
  lives in Java rather than the pack, so a check of the pack sees none of its blocks.
- **A growth shortcut**, summoning a Dragonling and growing it at once: the same dragon with one
  more step, testing growing rather than carrying.

## Consequences

- Each scene builds a plot of its own, clearing it first, so it builds the same way alone or as part
  of the whole gallery, and the same way again.
- Building the dragon scene changes server-wide records as well as blocks, unlike #691's other
  galleries, which change blocks alone. Its own Wells' records go with their cores, and its dragons
  with its `dragon remove`, so building it again leaves one dragon.
- A scene is replayed from its own sign: a click event runs its function again, rebuilding it from
  the start, so a scene that runs out (a Displacer's harvests, a Dormant machine waking) can be
  watched after `build` has laid out the whole row. A sign needs no setting, where a command block
  is off on a server until `enable-command-block` is set, and the Gallery already needs an operator.
- A function run from the server console has no player to own the scene's Wells and dragon, so the
  dragon scene fails there; it is for an operator player.
