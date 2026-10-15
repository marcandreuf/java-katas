package org.mandfer.practices.mousemaze;

/**
 * One cell of the maze, with a border on each side or not.
 *
 * CellTest.java has carried these TODOs since 2014:
 *
 *   //TODO: Define types of cells. One cell type per border combination.
 *   //TODO: Create cell with all borders.
 *   //TODO: Create cell with only left border.  (and right, top, bottom)
 *
 * "One cell type per border combination" is sixteen classes if you take it literally. A record
 * of four booleans is the same thing in one line, which is the answer the language grew into
 * between then and now -- see docs/modernization/01-records.md.
 *
 * See docs/dsa/11-reachability.md.
 *
 * TODO 1: implement blocks(). A cell blocks a direction if it has a border on that side.
 * TODO 2: implement the four single-border factories, so CellTest's 2014 TODOs can be tests.
 */
public record Cell(boolean top, boolean right, boolean bottom, boolean left) {

    /** No borders at all. */
    public static Cell open() {
        return new Cell(false, false, false, false);
    }

    /** A border on all four sides: nothing can enter or leave. */
    public static Cell closed() {
        return new Cell(true, true, true, true);
    }

    /** TODO 2 */
    public static Cell withTopBorder() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** TODO 2 */
    public static Cell withRightBorder() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** TODO 2 */
    public static Cell withBottomBorder() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** TODO 2 */
    public static Cell withLeftBorder() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** True if a border on this side stops the mouse leaving in that direction. */
    public boolean blocks(Direction direction) {
        throw new UnsupportedOperationException("TODO 1");
    }
}
