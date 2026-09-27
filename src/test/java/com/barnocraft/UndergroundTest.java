package com.barnocraft;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndergroundTest {
    private static final int[][] SIDES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    @Test void cavesHaveEntrancesSolidBottomAndRarerDeepIron() {
        long totalCoal = 0, totalIron = 0;
        for (int seed = 0; seed < 20; seed++) {
            World world = World.sample(seed);
            int coal = 0, iron = 0, exposedCoal = 0, exposedIron = 0, air = 0, entrances = 0, headroom = 0;
            for (int x = 0; x < 80; x++) {
                for (int z = 0; z < 80; z++) {
                    int ground = world.groundHeight(x,z);
                    assertEquals(Block.STONE, world.get(x,0,z));
                    if (world.get(x,ground,z) == Block.AIR) entrances++;
                    for (int y = 1; y <= ground; y++) {
                        Block block = world.get(x,y,z);
                        if (Math.abs(x-40) <= 5 && Math.abs(z-40) <= 5) assertTrue(block.solid(), "Spawn foundation is intact");
                        if (block == Block.AIR) {
                            air++;
                            if (world.get(x,y+1,z) == Block.AIR && world.get(x,y-1,z).solid()) headroom++;
                        }
                        if (block == Block.COAL_ORE) {
                            coal++; assertTrue(ground - y >= Underground.COAL_MIN_DEPTH);
                            if (Underground.caveWall(world,x,y,z)) exposedCoal++;
                        }
                        if (block == Block.IRON_ORE) {
                            iron++; assertTrue(ground - y >= Underground.IRON_MIN_DEPTH);
                            if (Underground.caveWall(world,x,y,z)) exposedIron++;
                        }
                    }
                }
            }
            assertTrue(air > 500, "Substantial cave volume for seed " + seed);
            assertTrue(entrances > 0); assertTrue(headroom > 100);
            assertTrue(exposedCoal > 0); assertTrue(exposedIron > 0);
            totalCoal += coal; totalIron += iron;
        }
        assertTrue(totalCoal < 20993 * .75, "Less coal than the previous balance on these seeds");
        assertTrue(totalIron > 2365 && totalIron < 9130 * .9, "Deeper caves expose more iron without reaching the excessive setting");
        double ratio = (double) totalCoal / totalIron;
        assertTrue(ratio > .5 && ratio < 3, "Neither ore overwhelms the other");
        System.out.println("Ore totals across 20 seeds: coal=" + totalCoal + ", iron=" + totalIron);
    }

    @Test void surfaceEntrancesConnectToUndergroundRooms() {
        World world = World.sample(42);
        boolean[][][] seen = new boolean[80][World.HEIGHT][80];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < 80; x++)
            for (int z = 0; z < 80; z++) {
                int y = world.groundHeight(x,z);
                if (world.get(x,y,z) == Block.AIR) { queue.add(new int[]{x,y,z}); seen[x][y][z] = true; }
            }
        int deep = 0;
        while (!queue.isEmpty()) {
            int[] p = queue.remove();
            if (world.groundHeight(p[0],p[2]) - p[1] >= 8) deep++;
            for (int[] d : SIDES) {
                int x = p[0]+d[0], y = p[1]+d[1], z = p[2]+d[2];
                if (!world.inside(x,y,z) || seen[x][y][z] || world.get(x,y,z) != Block.AIR) continue;
                seen[x][y][z] = true; queue.add(new int[]{x,y,z});
            }
        }
        assertTrue(deep > 500, "Entrances lead to deep tunnels, not just shallow pits");
    }

    @Test void oreTexturesProduceVisibleMeshesAndCanBeMined() {
        World world = World.flat();
        world.set(20,10,20,Block.COAL_ORE); world.set(22,10,20,Block.IRON_ORE);
        var meshes = ChunkMesh.build(world,1,1);
        assertEquals(12, meshes[8].getTriangleCount()); assertEquals(12, meshes[9].getTriangleCount());
        var origin = new com.jme3.math.Vector3f(18,10.5f,20.5f);
        var direction = com.jme3.math.Vector3f.UNIT_X;
        assertEquals(20, world.raycast(origin,direction,Player.REACH).x());
        world.set(20,10,20,Block.AIR);
        assertEquals(22, world.raycast(origin,direction,Player.REACH).x());
    }
}
