---
status: accepted
---

# A Void Dragon is server state, not an entity

The Void Dragon is the only thing that carries motes between dimensions at scale, serving its
owner's Void Wells wherever they are. A vanilla mob is ticked only while its chunk is loaded, so a
dragon that was only an entity would carry only where a player stands, and void logistics would
never connect bases in different dimensions.

**Decision.** A Void Dragon is a record in server-wide saved data: its owner, health, load, home
Well and the trip it has claimed. Its routing, dives, weakening and healing run on that record
whether or not any chunk is loaded. The dragon a player sees is only a presence of it, an entity
shown near a Well whose chunk is loaded and taken away when it dives. Mote stores, Void Wells
included, already live in per-dimension saved data and conduct in unloaded chunks, so a dragon
loads and unloads a Well with no chunk loading at all. A Dragonling, which only stays near where
it hatched, is an ordinary entity until it grows.

## Considered options

- **An ordinary entity**, as vanilla's Happy Ghast is: simplest, but it works only while its chunk
  is loaded.
- **A record that waits for its destination's chunk to load**: no chunk loading, but routing stalls
  at every Well no player is near.
- **A record that loads its destination's chunk on arrival**: not needed, since Wells keep their
  motes in saved data.

## Consequences

- A dragon's presence entity holds no state of its own: removing it, or a chunk unloading, loses
  nothing, and two presences of one dragon never coexist.
- Riding moves a player along a record's trip: the rider waits out the dive in the void and is set
  down at the destination Well.
- What a dragon leaves in the world waits for a loaded chunk, never the dragon: an egg due at a Well
  whose chunk is not loaded is owed to its next stop at one that is, while it routes on.
