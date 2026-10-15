# 11. Reachability, not Dijkstra

**Stage 11 of [the DSA track](INDEX.md)** · new code: `mousemaze/Direction.java`, `Cell.java`,
`Maze.java` · and the two 2014 test stubs become tests

## Where it lands

**`mousemaze/`**, which is the best starting position in the repo: `src/main/.../mousemaze/`
contains only `Rules.txt`, and `MazeTest.java` and `CellTest.java` have **zero `@Test` methods** —
only TODO comments, since 2014.

So this is greenfield *with a written spec*. `Rules.txt`, in full:

    The mouse needs to move throughout the maze, cell by cell, until it finds the cheese.

    A mouse can:
      move forward one position at a time
      turn left or right
      check the surrounding cells

**That move set is exactly what breadth-first search needs**, which is why this is the one graph
algorithm the track teaches.

## Why this is the only graph algorithm here

[`research.md`](research.md) rates Dijkstra and A\* **interview-only** and BFS/reachability
**load-bearing, narrowly**. The distinction is the whole stage:

**Every move in this maze costs the same.** One step is one step. When edges are unweighted, BFS
gives the shortest path and Dijkstra degenerates into BFS with a priority queue you did not need.
Dijkstra earns its keep only when edges have *different* weights, and if you genuinely have that
you want a graph library and a correctness argument, not a hand-rolled heap.

And the version you actually write at work is rarely called a graph problem. It is:

- "what order do I run these migrations in" — topological sort
- "which of these services can reach that one" — reachability
- "is this dependency graph cyclic" — the same traversal with one more flag
- "fewest hops from here to there" — this

**One queue and one visited set covers all of them.** That is the load-bearing version, and it is
TODO 3 and TODO 4 being almost the same loop.

## The exercise

### Honouring the 2014 design

`CellTest` has carried these since 2014:

    //TODO: Define types of cells. One cell type per border combination.
    //TODO: Create cell with all borders.
    //TODO: Create cell with only left border.   (and right, top, bottom)

"One cell type per border combination" is sixteen classes if taken literally. `Cell` is a **record
of four booleans**, which is the same thing in one line — the language grew into that answer in
Java 16, two years after the TODO was written. See
[modernization doc 01](../modernization/01-records.md).

Those five TODOs are now five tests, plus one the record gives for free: two cells with the same
borders are `equals`, which is what makes `Cell.open()` safe to share across every cell in the
grid. [Stage 03](03-equals-and-hashcode.md) is why that is not free in a hand-written class.

`MazeTest`'s single TODO — `//TODO: Test maze dimensions.` — is TODO 1.

### TODO 2 is the bug people write

A move from cell A in direction D is blocked by **three** things, and the third is the one that
gets forgotten:

1. the grid edge
2. a border on **A's** side facing D
3. a border on the **neighbour's** opposite side

Miss the third and **walls become one-way**: the mouse can walk from A into B but not from B into
A, because only B carries the wall between them. `Direction.opposite()` is provided for exactly
this, and `a_border_on_the_neighbour_blocks_the_move_too` is the test that catches it.

This is the general lesson about modelling a graph as a grid: **a wall is an edge property, and
you are storing it on the nodes.** Storing it twice means it can disagree with itself. (Storing
edges instead is the other design, and it is worth a sentence in "what got worse" about which you
would pick.)

### TODO 3: why BFS and not DFS

Both find *whether* the cheese is reachable. Only BFS finds the **fewest moves**, and the reason is
one sentence about the queue: a FIFO queue visits every cell at distance *k* before any cell at
distance *k+1*, so the first time you reach the target you are on a shortest path. A stack
(depth-first) dives, so the first path it finds can be arbitrarily long.

**Write that sentence down.** It is the only piece of graph theory this track asks you to hold, and
it is the answer to "why BFS" in an interview and in a design review.

Use `ArrayDeque` for the queue, for the reasons in [stage 01](01-which-collection.md) — and note
from [stage 02](02-contract-matrix.md) that it will refuse a `null`, so no null sentinels.

**TODO 4 reuses TODO 3's loop almost unchanged**, which is the point: reachability and
shortest-hops are the same traversal with different bookkeeping.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: borders stored per cell can disagree with themselves — would you store edges instead, and
what does that cost in the common case of a dense grid? `boolean[][] seen` is fine for a grid and
useless for a general graph — what replaces it, and what does [stage 03](03-equals-and-hashcode.md)
have to say about the node type? At what maze size does BFS stop being instant, and is that a size
anybody would reach? `Rules.txt` says the mouse can "turn left or right", so it has an orientation
— does modelling that change the problem, and did ignoring it lose anything?
