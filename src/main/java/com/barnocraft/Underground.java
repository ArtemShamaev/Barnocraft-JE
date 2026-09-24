package com.barnocraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded tunnels, surface entrances and clustered ores exposed along cave walls. */
final class Underground {
    static final double COAL_CHANCE = .020, IRON_CHANCE = .016;
    static final int COAL_MIN_DEPTH = 2, IRON_MIN_DEPTH = 4;
    private static final int[][] SIDES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    private Underground() {}

    static void generate(VoxelVolume world, long seed) {
        Random caves = new Random(seed ^ 0x4341564553L);
        for (int system = 0; system < 9; system++) {
            int sx = 0, sz = 0;
            boolean found = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                sx = 10 + caves.nextInt(world.width() - 20);
                sz = 10 + caves.nextInt(world.depth() - 20);
                if (!world.protectedSpawn(sx, sz, 5) && world.groundHeight(sx, sz) >= 8) { found = true; break; }
            }
            if (!found) continue;
            double x = sx + .5, z = sz + .5, y = world.groundHeight(sx, sz) - 3.5;
            double startY = y, heading = caves.nextDouble() * Math.PI * 2;
            double entranceHeading = heading + Math.PI;
            for (int step = 0; step < 75; step++) {
                carve(world, x, y, z, 1.7 + caves.nextDouble() * .6, false);
                if (step % 24 == 12) carve(world, x, y, z, 3, false);
                heading += (caves.nextDouble() - .5) * .45;
                x = Math.max(4, Math.min(world.width() - 5, x + Math.cos(heading) * .85));
                z = Math.max(4, Math.min(world.depth() - 5, z + Math.sin(heading) * .85));
                y += (caves.nextDouble() - .5) * .4;
                y = Math.max(3, Math.min(world.groundHeight((int) x, (int) z) - 3, y));
            }
            // A gently rising passage opens the underground system onto the surface.
            double ex = sx + .5 + Math.cos(entranceHeading) * 10;
            double ez = sz + .5 + Math.sin(entranceHeading) * 10;
            double ey = world.groundHeight((int) ex, (int) ez) + 2;
            for (int step = 0; step <= 24; step++) {
                double t = step / 24.0;
                carve(world, sx + .5 + (ex - sx - .5) * t, startY + (ey - startY) * t,
                        sz + .5 + (ez - sz - .5) * t, 1.8, true);
            }
        }
        generateOres(world, new Random(seed ^ 0x4F524553L));
    }

    private static void carve(VoxelVolume world, double cx, double cy, double cz, double radius, boolean entrance) {
        for (int x = (int) Math.floor(cx - radius); x <= Math.ceil(cx + radius); x++)
            for (int z = (int) Math.floor(cz - radius); z <= Math.ceil(cz + radius); z++) {
                if (x < 2 || z < 2 || x >= world.width() - 2 || z >= world.depth() - 2 || world.protectedSpawn(x,z,5)) continue;
                for (int y = Math.max(1, (int) Math.floor(cy - 2)); y <= Math.min(World.HEIGHT - 1, Math.ceil(cy + 2)); y++) {
                    double dx = (x + .5 - cx) / radius, dy = (y + .5 - cy) / 2, dz = (z + .5 - cz) / radius;
                    if (dx * dx + dy * dy + dz * dz > 1) continue;
                    if (!entrance && y > world.groundHeight(x,z) - 2) continue;
                    Block block = world.get(x,y,z);
                    if (block.rock() || block == Block.GRASS) world.set(x,y,z,Block.AIR);
                }
            }
    }

    static boolean caveWall(VoxelVolume world, int x, int y, int z) {
        for (int[] side : SIDES) {
            int nx = x + side[0], ny = y + side[1], nz = z + side[2];
            if (world.inside(nx,ny,nz) && ny >= 1 && ny <= world.groundHeight(nx,nz)
                    && world.get(nx,ny,nz) == Block.AIR) return true;
        }
        return false;
    }

    private static void generateOres(VoxelVolume world, Random random) {
        for (int x = 1; x < world.width() - 1; x++)
            for (int z = 1; z < world.depth() - 1; z++)
                for (int y = 1; y < world.groundHeight(x,z); y++) {
                    if (!world.get(x,y,z).rock() || !caveWall(world,x,y,z)) continue;
                    double roll = random.nextDouble();
                    if (roll < COAL_CHANCE)
                        vein(world,random,x,y,z,Block.COAL_ORE,COAL_MIN_DEPTH,3 + random.nextInt(4));
                    else if (roll < COAL_CHANCE + IRON_CHANCE)
                        vein(world,random,x,y,z,Block.IRON_ORE,IRON_MIN_DEPTH,3 + random.nextInt(4));
                }
    }

    private static void vein(VoxelVolume world, Random random, int x, int y, int z, Block ore, int minDepth, int size) {
        List<int[]> frontier = new ArrayList<>();
        frontier.add(new int[]{x,y,z});
        int placed = 0;
        while (!frontier.isEmpty() && placed < size) {
            int[] cell = frontier.remove(random.nextInt(frontier.size()));
            int cx = cell[0], cy = cell[1], cz = cell[2];
            if (!world.inside(cx,cy,cz) || cy < 1 || !world.get(cx,cy,cz).rock()
                    || world.groundHeight(cx,cz) - cy < minDepth) continue;
            world.set(cx,cy,cz,ore); placed++;
            for (int[] side : SIDES) frontier.add(new int[]{cx+side[0],cy+side[1],cz+side[2]});
        }
    }
}
