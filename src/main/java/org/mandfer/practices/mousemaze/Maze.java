package org.mandfer.practices.mousemaze;

/**
 * A grid of cells, and the only algorithm this track teaches: breadth-first reachability.
 *
 * MazeTest.java has carried "//TODO: Test maze dimensions." since 2014 and has no test methods
 * at all. src/main/.../mousemaze/ contains only Rules.txt. So this is greenfield with a written
 * spec, which is the best starting position in the repo.
 *
 * Rules.txt says the mouse can move forward one position, turn, and check the surrounding
 * cells. That is exactly the move set BFS needs, and "can the mouse reach the cheese, and in
 * how few moves" is the whole problem. It is not a shortest-path problem in the Dijkstra sense,
 * because every move costs the same -- and docs/dsa/11-reachability.md is about why that
 * distinction is the one worth knowing.
 *
 * TODO 1: dimensions -- rows() and columns(). MazeTest's oldest TODO.
 * TODO 2: canMove -- from a cell, in a direction. Blocked by the grid edge, by a border on THIS
 *         cell's side, or by a border on the NEIGHBOUR's opposite side. That third condition is
 *         the one people forget, and a maze where a wall only blocks one way round is the bug
 *         it causes. Direction.opposite() is provided for it.
 * TODO 3: stepsBetween -- fewest moves, or -1 if unreachable. BFS: a queue of positions and a
 *         visited set. Use ArrayDeque, for the reason in docs/dsa/01.
 *
 *         Then write down why BFS gives the FEWEST moves and depth-first search does not. The
 *         answer is one sentence about the order the queue imposes, and it is the only piece of
 *         graph theory this track asks you to hold.
 * TODO 4: reachableFrom -- how many cells are reachable at all. Same traversal, different
 *         bookkeeping; notice you can reuse TODO 3's loop almost unchanged, which is the point.
 */
public final class Maze {

    private final Cell[][] cells;

    /** A maze of open cells. The outer boundary is implied by the grid. */
    public Maze(int rows, int columns) {
        if (rows < 1 || columns < 1) {
            throw new IllegalArgumentException("maze must have at least one cell: " + rows + "x" + columns);
        }
        this.cells = new Cell[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells[row][column] = Cell.open();
            }
        }
    }

    public Cell cellAt(int row, int column) {
        return cells[row][column];
    }

    public void setCell(int row, int column, Cell cell) {
        cells[row][column] = cell;
    }

    /** TODO 1 */
    public int rows() {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** TODO 1 */
    public int columns() {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** True if the mouse can step from this cell in that direction. */
    public boolean canMove(int row, int column, Direction direction) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Fewest moves from one cell to another, 0 for the same cell, -1 if unreachable. */
    public int stepsBetween(int fromRow, int fromColumn, int toRow, int toColumn) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** How many cells are reachable from here, counting this one. */
    public int reachableFrom(int row, int column) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
