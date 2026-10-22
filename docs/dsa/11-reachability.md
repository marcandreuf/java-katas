# 11. Reachability, not Dijkstra

**Stage 11 of [the DSA track](INDEX.md)** · new code: `mousemaze/Cell.java` (two TODOs) and
`mousemaze/Maze.java` (four TODOs), with `Direction.java` provided · tests: `CellTest` (7) and
`MazeTest` (8), both `@Disabled`

## What this stage is actually about

Five words, defined once:

- a **graph** is a set of nodes and the edges between them. A maze is one: cells are nodes, and an
  open side between two cells is an edge.
- **reachability** asks which nodes you can get to from a starting node at all.
- a **hop** is one edge crossed. "Fewest moves" means fewest hops.
- **breadth-first search** (BFS) explores from the start in rings: everything one hop away, then
  everything two hops away, and so on. It keeps the frontier in a FIFO **queue**.
- **depth-first search** (DFS) follows one path as far as it goes before backing up. It keeps the
  frontier in a LIFO **stack**, or in the call stack if it's recursive.

That gives one rule, and everything below follows from it: **when every hop costs the same, the
first time BFS reaches a node it's on a shortest path, because the queue hands out every node at
distance *k* before any node at distance *k + 1*.** DFS has no such order, so the first path it
finds can be any length. Dijkstra is BFS generalised to edges with *different* costs. When every
edge costs the same, it does the same job with a priority queue you didn't need.

## Why this is the only graph algorithm here

[`research.md`](research.md) rates Dijkstra and A\* **interview-only** and BFS/reachability
**load-bearing, narrowly**. The version you actually write at work is rarely called a graph
problem:

- "which of these services can reach that one": reachability
- "fewest hops from here to there": shortest path, unweighted
- "is this dependency graph cyclic": the same traversal with one more flag
- "what order do I run these migrations in": topological sort, the same family

**One queue and one visited set covers the first two outright**, and the other two are small
changes to the same loop. If you genuinely have weighted edges, you want a graph library and a
correctness argument, not a hand-rolled heap.

## The worked example: services, not mazes

Which services does `web` reach, and in how few calls?

    web     -> auth, billing
    auth    -> users
    users   -> billing
    billing -> (nothing)

A recursive DFS, trying neighbours in the order listed, finds `web → auth → users → billing`:
**three hops** (verified). BFS, with the queue printed after each node is taken off it (verified):

    poll web      (distance 0)   queue: [auth, billing]
    poll auth     (distance 1)   queue: [billing, users]
    poll billing  (distance 1)   queue: [users]
    poll users    (distance 2)   queue: []

    distances: web=0, auth=1, billing=1, users=2

`billing` is **one hop**. DFS found a real path, just not the short one, because it went down
`auth` first and kept going. BFS couldn't have done that: `billing` went into the queue in the
first ring, ahead of everything from the second.

Notice what the trace needed besides the queue: **a record of what's already been seen**, put in
when a node joins the queue, not when it leaves. Without it, `billing` would be queued twice, and
on a graph with a cycle the loop would never end. That's the whole algorithm: a queue, and a
"seen" set, and you'll write it yourself in TODO 3.

## Where it lands: `mousemaze/`

The best starting position in the repo. **A correction to the old version of this doc**, which said
`src/main/.../mousemaze/` contains only `Rules.txt` and the two tests have zero `@Test` methods.
That was the 2014 state, and this track changed it. Today (verified):

- `Rules.txt`, the 2014 spec, unchanged
- `Direction.java`, complete: `UP`, `RIGHT`, `DOWN`, `LEFT`, their row and column deltas, and
  `opposite()`
- `Cell.java`: a record of four booleans, with **TODO 1** (`blocks`) and **TODO 2** (four
  single-border factories)
- `Maze.java`: a grid of `Cell`s, with **TODO 1** (`rows`, `columns`), **TODO 2** (`canMove`),
  **TODO 3** (`stepsBetween`) and **TODO 4** (`reachableFrom`)
- `CellTest`, 7 tests, and `MazeTest`, 8 tests, both `@Disabled`. Their class comments quote
  the 2014 TODO lines they replace.

Both files number their TODOs from 1, so below they're "Cell TODO 1" and "Maze TODO 1" and so on.

`Rules.txt` in full:

    The mouse needs to move throughout the maze, cell by cell, until
    it finds the cheese.

    A mouse can:
      move forward one position at a time
      turn left or right
      check the surrounding cells

### Honouring the 2014 design

`CellTest` carried six TODO lines from 2014: "Define types of cells. One cell type per border
combination", "Create cell with all borders", and four "Create cell with only *X* border". Taken
literally, the first one is sixteen classes. `Cell` is a **record of four booleans**, which is the
same thing in one line: the language grew into that answer in Java 16. See
[modernization doc 01](../modernization/01-records.md).

The seven tests (verified): open blocks nothing; closed blocks everything (the "all borders" line);
one each for the four single borders; and one the record gives for free, that two cells with the
same borders are `equals`. That last one is what makes sharing a single `Cell.open()` across the
whole grid safe. [Stage 03](03-equals-and-hashcode.md) is why it wouldn't be free in a
hand-written class.

### Maze TODO 2 is the bug people write

A move from cell A in direction D is blocked by **three** things, and the third is the one that
gets forgotten:

1. the grid edge
2. a border on **A's** side facing D
3. a border on the **neighbour's** opposite side

Miss the third and **walls become one-way**. Verified, against a scratch implementation that
checks only conditions 1 and 2, on a 1×3 maze whose middle cell has a left border:

| | three checks | only 1 and 2 |
| --- | --- | --- |
| `canMove(0, 0, RIGHT)`, into the wall | false | **true** |
| `canMove(0, 1, LEFT)`, out through it | false | false |
| `reachableFrom(0, 0)` | 1 | **3** |

And on a 3×3 maze with a closed centre, the two-check version gives `reachableFrom(0, 0)` = **9**,
not 8: the mouse walks *into* a closed cell, because only the closed cell's own borders say it's
closed. Two of `MazeTest`'s eight tests catch this, `a_border_on_the_neighbour_blocks_the_move_too`
and `a_closed_cell_is_routed_around`. `Direction.opposite()` is provided for condition 3.

This is the general lesson about modelling a graph as a grid: **a wall is an edge property, and
you're storing it on the nodes.** Storing it twice means it can disagree with itself.

## The commits

New code under tests that are already written, so there's nothing to characterize first.

1. **`CellTest`**: delete its `@Disabled` (7 red, all `UnsupportedOperationException`, verified),
   then Cell TODO 1 and Cell TODO 2, a commit each.
2. **`MazeTest`**: delete its `@Disabled` (7 red, 1 green, verified: the green one is
   `a_maze_must_have_at_least_one_cell`, because the constructor already checks). Then Maze TODO 1
   and Maze TODO 2, a commit each. Run the neighbour test against a two-check `canMove` first and
   watch it fail, so you've seen the bug and not just read about it.
3. **Maze TODO 3, `stepsBetween`**: BFS. Use `ArrayDeque` for the queue, for the reasons in
   [stage 01](01-which-collection.md). [Stage 02](02-contract-matrix.md) explains why it refuses
   `null`, so no null sentinels. Then **write down the one-sentence answer to "why BFS and not
   DFS"** in your own words: the rule at the top, applied to this maze. It's the only piece of
   graph theory this track asks you to hold, and it answers the question in an interview and in a
   design review.
4. **Maze TODO 4, `reachableFrom`**: the same loop, counting instead of stopping. If you find
   yourself copying TODO 3 and editing it, ask whether one private method can serve both.

For scale (verified, a scratch BFS on an open grid, while other JVMs were running, so treat the
times as rough): 1,000×1,000 cells in about **0.25 s**; 3,000×3,000 in about **3.4 s**.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: borders stored per cell can disagree with themselves. Would you store edges instead, and
what does that cost on a dense grid? A `boolean[][]` "seen" works for a grid and not for a general
graph. What replaces it, and what does [stage 03](03-equals-and-hashcode.md) say about the node
type? Nine million cells took seconds: is that a size anybody would reach? `Rules.txt` says the
mouse can "turn left or right", so it has a facing direction, and `Direction` ignores that.
Does modelling it change the problem, and did ignoring it lose anything?
