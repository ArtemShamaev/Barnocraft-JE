package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainTest {
    @Test void landscapeHasContinuousHillsAndRoomForTrees() {
        for (long seed = 0; seed < 20; seed++) {
            World world = World.sample(seed);
            int lowest = World.HEIGHT, highest = 0;
            for (int x = 0; x < 80; x++) {
                for (int z = 0; z < 80; z++) {
                    int h = world.groundHeight(x,z);
                    lowest = Math.min(lowest,h); highest = Math.max(highest,h);
                    assertTrue(h >= 4 && h <= World.HEIGHT-8);
                    assertTrue(world.get(x,h,z) == Block.GRASS || world.get(x,h,z) == Block.SAND || world.get(x,h,z) == Block.AIR);
                    assertEquals(Block.STONE, world.get(x,0,z));
                    for (int y = 1; y < h; y++) {
                        Block block = world.get(x,y,z);
                        assertTrue(block.rock() || block == Block.AIR
                            || block == Block.COAL_ORE || block == Block.IRON_ORE
                            || block == Block.LOG || block == Block.LEAVES,
                            "Unexpected subsurface block "+block+" at "+x+","+y+","+z+" under surface "+h);
                    }
                    // Includes adjacent columns on both sides of every chunk seam.
                    if (x > 0) assertTrue(Math.abs(h - world.groundHeight(x-1,z)) < World.HEIGHT);
                    if (z > 0) assertTrue(Math.abs(h - world.groundHeight(x,z-1)) < World.HEIGHT);
                    assertEquals(Block.AIR, world.get(x,World.HEIGHT-1,z));
                }
            }
            assertTrue(highest - lowest >= 4, "World must have visible height variation");
        }
    }

    @Test void broadMapHasHighMountainsAndNearVerticalCanyonWalls() {
        int highest=0,steepest=0;
        for(int x=0;x<World.WIDTH;x+=2) for(int z=0;z<World.DEPTH;z+=2) {
            int height=Terrain.height(0,x,z);
            highest=Math.max(highest,height);
            if(x>0) steepest=Math.max(steepest,Math.abs(height-Terrain.height(0,x-1,z)));
            if(z>0) steepest=Math.max(steepest,Math.abs(height-Terrain.height(0,x,z-1)));
        }
        assertTrue(highest>60,"Terrain should generate tall mountains");
        assertTrue(steepest>20,"Canyon edges should include steep vertical cliffs");
    }

    @Test void spawnAndVoidRecoveryUseActualSurface() {
        for (long seed = 0; seed < 20; seed++) {
            World world = World.sample(seed);
            Player player = new Player(); player.respawn(world);
            int h = world.groundHeight(40,40);
            assertEquals(h + 1.001f, player.position.y, .0001f);
            assertFalse(player.collides(world));
            for (int i = 0; i < 30; i++) player.update(world, 1/60f, Vector3f.ZERO, false);
            assertTrue(player.grounded);
            assertEquals(h + 1, player.position.y, .001f);
            world.set(40,h+1,40,Block.LOG);
            player.position.y = -21;
            player.update(world, .01f, Vector3f.ZERO, false);
            assertEquals(h + 2.001f, player.position.y, .001f);
            assertFalse(player.collides(world));
        }
    }
}
