package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepstoneTest {
    @Test void rockPatchesAreUndergroundVisibleInCavesAndLeaveStoneDominant() {
        int deep = 0, stone = 0, exposed = 0, adjacent = 0;
        for (int seed = 0; seed < 20; seed++) {
            World world = World.sample(seed);
            for (int x = 0; x < 80; x++)
                for (int z = 0; z < 80; z++)
                    for (int y = 1; y < world.groundHeight(x,z); y++) {
                        Block block = world.get(x,y,z);
                        if (block == Block.STONE) stone++;
                        if (block != Block.DEEPSTONE) continue;
                        deep++;
                        assertTrue(world.groundHeight(x,z) - y >= 3);
                        if (Underground.caveWall(world,x,y,z)) exposed++;
                        if (world.get(x+1,y,z) == Block.DEEPSTONE || world.get(x-1,y,z) == Block.DEEPSTONE
                                || world.get(x,y+1,z) == Block.DEEPSTONE || world.get(x,y-1,z) == Block.DEEPSTONE
                                || world.get(x,y,z+1) == Block.DEEPSTONE || world.get(x,y,z-1) == Block.DEEPSTONE) adjacent++;
                    }
        }
        assertTrue(deep > stone * .05 && deep < stone, "Visible variation without replacing most stone");
        assertTrue(exposed > 100);
        assertTrue(adjacent > deep * .9, "Patches rather than isolated speckles");
        System.out.println("Rock totals: stone=" + stone + ", deepstone=" + deep + ", exposed=" + exposed);
    }

    @Test void deepstoneRendersAndCanBeMined() {
        World world = World.flat(); world.set(20,10,20,Block.DEEPSTONE);
        assertEquals(12, ChunkMesh.build(world,1,1)[10].getTriangleCount());
        assertEquals(20, world.raycast(new Vector3f(18,10.5f,20.5f),Vector3f.UNIT_X,Player.REACH).x());
        assertTrue(world.set(20,10,20,Block.AIR));
        assertNull(world.raycast(new Vector3f(18,10.5f,20.5f),Vector3f.UNIT_X,Player.REACH));
    }
}
