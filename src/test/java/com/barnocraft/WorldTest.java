package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    @Test void interactionReachStopsAtFiveBlocks() {
        World w = World.flat();
        w.set(20,10,20,Block.LOG);
        Vector3f direction = new Vector3f(10,0,0);
        assertNotNull(w.raycast(new Vector3f(15,10.5f,20.5f), direction, Player.REACH));
        assertNull(w.raycast(new Vector3f(14.99f,10.5f,20.5f), direction, Player.REACH));
        assertNotNull(w.raycast(new Vector3f(15.01f,10.5f,20.5f), direction, Player.REACH));
    }

    @Test void originalTerrainAndBounds() {
        World w = World.flat();
        assertEquals(Block.STONE, w.get(0, 4, 79));
        assertEquals(Block.GRASS, w.get(79, 5, 0));
        assertEquals(Block.AIR, w.get(40, 6, 40));
        assertEquals(Block.AIR, w.get(-1, 0, 0));
        assertFalse(w.set(80, 6, 0, Block.GLASS));
        assertTrue(w.set(16, 6, 16, Block.GLASS));
        assertEquals(Block.GLASS, w.get(16, 6, 16));
    }
    @Test void verticalRayOnIntegerPlanesHasNoNaN() {
        World w = World.flat();
        assertEquals(new World.Hit(40, 5, 40, 0, 1, 0), w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,-1,0), 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,1,0), 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), Vector3f.ZERO, 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,-1,0), 1));
    }
    @Test void rayFindsAllPlacementNormalsAndGlass() {
        World w = World.flat(); w.set(20, 10, 20, Block.GLASS);
        int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] n : directions) {
            Vector3f normal = new Vector3f(n[0], n[1], n[2]);
            World.Hit hit = w.raycast(new Vector3f(20.5f,10.5f,20.5f).add(normal.mult(3)), normal.negate(), 4);
            assertEquals(new World.Hit(20,10,20,n[0],n[1],n[2]), hit);
        }
    }
}
