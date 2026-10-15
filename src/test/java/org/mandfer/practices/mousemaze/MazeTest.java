package org.mandfer.practices.mousemaze;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "//TODO: Test maze dimensions." has been the entire contents of this class since 2014.
 * It is the first test below.
 */
@Disabled("Exercise: docs/dsa/11-reachability.md. Delete this line to begin.")
class MazeTest {

    @Test
    void maze_dimensions() {
        Maze maze = new Maze(3, 5);

        assertEquals(3, maze.rows());
        assertEquals(5, maze.columns());
    }

    @Test
    void a_maze_must_have_at_least_one_cell() {
        assertThrows(IllegalArgumentException.class, () -> new Maze(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new Maze(3, 0));
    }

    @Test
    void the_grid_edge_blocks_movement() {
        Maze maze = new Maze(3, 3);

        assertFalse(maze.canMove(0, 0, Direction.UP), "top row cannot go up");
        assertFalse(maze.canMove(0, 0, Direction.LEFT), "left column cannot go left");
        assertTrue(maze.canMove(0, 0, Direction.RIGHT));
        assertTrue(maze.canMove(0, 0, Direction.DOWN));
    }

    @Test
    void an_open_maze_is_entirely_reachable() {
        Maze maze = new Maze(3, 3);

        assertEquals(9, maze.reachableFrom(0, 0));
        assertEquals(4, maze.stepsBetween(0, 0, 2, 2), "three rows, three columns, four moves");
    }

    @Test
    void the_shortest_path_to_yourself_is_no_moves() {
        assertEquals(0, new Maze(3, 3).stepsBetween(1, 1, 1, 1));
        assertEquals(1, new Maze(1, 1).reachableFrom(0, 0));
    }

    @Test
    void a_closed_cell_is_routed_around() {
        Maze maze = new Maze(3, 3);
        maze.setCell(1, 1, Cell.closed());

        assertEquals(8, maze.reachableFrom(0, 0), "every cell but the closed one");
        assertEquals(4, maze.stepsBetween(0, 0, 2, 2), "still four moves, around the centre");
    }

    @Test
    void a_closed_start_reaches_nothing() {
        Maze maze = new Maze(3, 3);
        maze.setCell(0, 0, Cell.closed());

        assertEquals(1, maze.reachableFrom(0, 0), "only itself");
        assertEquals(-1, maze.stepsBetween(0, 0, 2, 2), "unreachable is -1, not an exception");
    }

    /**
     * The condition people forget. The mouse is at (0,0) with no border of its own, but its
     * neighbour has a LEFT border, so the wall between them blocks the move. A canMove that
     * only checks the current cell lets the mouse walk through walls in one direction.
     */
    @Test
    void a_border_on_the_neighbour_blocks_the_move_too() {
        Maze maze = new Maze(1, 3);
        maze.setCell(0, 1, Cell.withLeftBorder());

        assertFalse(maze.canMove(0, 0, Direction.RIGHT), "the neighbour's left border is this wall");
        assertEquals(1, maze.reachableFrom(0, 0), "walled off after one cell");
        assertEquals(-1, maze.stepsBetween(0, 0, 0, 2));
    }
}
