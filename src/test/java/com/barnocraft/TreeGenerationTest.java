package com.barnocraft;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TreeGenerationTest {
    @Test void seedReproducesForestAndDifferentSeedsVaryIt() {
        World first = World.sample(42), same = World.sample(42), other = World.sample(43);
        int differences = 0;
        for (int x = 0; x < 80; x++)
            for (int z = 0; z < 80; z++)
                for (int y = 0; y < World.HEIGHT; y++) {
                    assertEquals(first.get(x,y,z), same.get(x,y,z));
                    if (first.get(x,y,z) != other.get(x,y,z)) differences++;
                }
        assertTrue(differences > 0);
    }

    @Test void treesHaveRootedTrunksCrownsAndSafeSpawnAcrossSeeds() {
        for (long seed = 0; seed < 20; seed++) {
            World world = World.sample(seed);
            int trees = 0;
            for (int x = 0; x < 80; x++) {
                for (int z = 0; z < 80; z++) {
                    int base = world.groundHeight(x,z) + 1;
                    assertTrue(world.get(x,base - 1,z) == Block.GRASS || world.get(x,base - 1,z) == Block.AIR);
                    if (world.get(x,base,z) == Block.LOG) {
                        trees++;
                        assertEquals(Block.GRASS, world.get(x,base - 1,z));
                        assertTrue(x >= 2 && x < 80 - 2 && z >= 2 && z < 80 - 2);
                        int height = 0;
                        while (world.get(x,base + height,z) == Block.LOG) height++;
                        assertTrue(height >= 4 && height <= 6);
                        assertEquals(Block.LEAVES, world.get(x,base + height,z));
                        assertEquals(Block.LEAVES, world.get(x + 2,base + height - 1,z));
                        assertEquals(Block.LEAVES, world.get(x - 2,base + height - 1,z));
                        assertEquals(Block.LEAVES, world.get(x,base + height - 1,z + 2));
                        assertEquals(Block.LEAVES, world.get(x,base + height - 1,z - 2));
                    }
                    for (int y = base; y < World.HEIGHT; y++) {
                        Block block = world.get(x,y,z);
                        if (Math.abs(x - 80 / 2) <= 5 && Math.abs(z - 80 / 2) <= 5)
                            assertEquals(Block.AIR, block, "Spawn clearing includes foliage");
                        if (block == Block.LOG && y > base) assertEquals(Block.LOG, world.get(x,y-1,z));
                        if (block == Block.LEAVES) {
                            boolean trunkNearby = false;
                            for (int dx = -2; dx <= 2; dx++)
                                for (int dz = -2; dz <= 2; dz++)
                                    if (world.inside(x+dx, 0, z+dz))
                                        trunkNearby |= world.get(x+dx,world.groundHeight(x+dx,z+dz) + 1,z+dz) == Block.LOG;
                            assertTrue(trunkNearby, "No detached foliage");
                        }
                    }
                }
            }
            assertTrue(trees >= 30 && trees <= 100, "Forest covers the world");
            Player player = new Player();
            player.respawn(world);
            assertFalse(player.collides(world));
        }
    }

    @Test void leavesPreserveFacesBehindHolesAndWoodIsTargetable() {
        World world = World.flat();
        world.set(20,10,20,Block.LOG);
        world.set(21,10,20,Block.LEAVES);
        world.set(22,10,20,Block.LEAVES);
        Mesh[] meshes = ChunkMesh.build(world,1,1);
        assertEquals(12, meshes[6].getTriangleCount(), "Wood stays visible behind cutout leaves");
        assertEquals(22, meshes[7].getTriangleCount(), "Only leaf face against solid wood is hidden");
        World.Hit hit = world.raycast(new Vector3f(18,10.5f,20.5f), Vector3f.UNIT_X, 10);
        assertEquals(new World.Hit(20,10,20,-1,0,0), hit);
        assertTrue(world.set(20,10,20,Block.AIR));
        assertEquals(21, world.raycast(new Vector3f(18,10.5f,20.5f), Vector3f.UNIT_X, 10).x());
    }
}
