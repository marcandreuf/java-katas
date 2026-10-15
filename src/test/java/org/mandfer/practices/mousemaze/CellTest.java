package org.mandfer.practices.mousemaze;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These were the TODO comments in this file from 2014 until now:
 *
 *   //TODO: Define types of cells. One cell type per border combination.
 *   //TODO: Create cell with all borders.
 *   //TODO: Create cell with only left border.
 *   //TODO: Create cell with only right border.
 *   //TODO: Create cell with only top border.
 *   //TODO: Create cell with only bottom border.
 *
 * They are now tests. "One cell type per border combination" would have been sixteen classes;
 * Cell is a record of four booleans, which is the same thing and arrived in Java 16.
 */
@Disabled("Exercise: docs/dsa/11-reachability.md. Delete this line to begin.")
class CellTest {

    @Test
    void an_open_cell_blocks_nothing() {
        Cell open = Cell.open();

        for (Direction direction : Direction.values()) {
            assertFalse(open.blocks(direction), "open cell should not block " + direction);
        }
    }

    @Test
    void a_closed_cell_blocks_everything() {
        Cell closed = Cell.closed();

        for (Direction direction : Direction.values()) {
            assertTrue(closed.blocks(direction), "closed cell should block " + direction);
        }
    }

    @Test
    void a_cell_with_only_a_left_border_blocks_only_left() {
        assertBlocksOnly(Cell.withLeftBorder(), Direction.LEFT);
    }

    @Test
    void a_cell_with_only_a_right_border_blocks_only_right() {
        assertBlocksOnly(Cell.withRightBorder(), Direction.RIGHT);
    }

    @Test
    void a_cell_with_only_a_top_border_blocks_only_up() {
        assertBlocksOnly(Cell.withTopBorder(), Direction.UP);
    }

    @Test
    void a_cell_with_only_a_bottom_border_blocks_only_down() {
        assertBlocksOnly(Cell.withBottomBorder(), Direction.DOWN);
    }

    /** The record gives us this for free, and it is what makes cells safe to share. */
    @Test
    void cells_with_the_same_borders_are_equal() {
        assertTrue(Cell.open().equals(Cell.open()));
        assertTrue(Cell.withLeftBorder().equals(new Cell(false, false, false, true)));
        assertFalse(Cell.withLeftBorder().equals(Cell.withRightBorder()));
    }

    private static void assertBlocksOnly(Cell cell, Direction blocked) {
        for (Direction direction : Direction.values()) {
            if (direction == blocked) {
                assertTrue(cell.blocks(direction), "should block " + direction);
            } else {
                assertFalse(cell.blocks(direction), "should not block " + direction);
            }
        }
    }
}
