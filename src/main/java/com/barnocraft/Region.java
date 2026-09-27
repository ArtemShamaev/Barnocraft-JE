package com.barnocraft;

import java.util.Random;

/** Voxel data independent of rendering; coordinates describe the lower corner of a block. */
final class Region implements VoxelVolume {
    static final int WIDTH = 80, DEPTH = 80, HEIGHT = World.HEIGHT;
    private final int ox, oz, worldWidth, worldDepth;
    private final byte[] blocks = new byte[WIDTH * HEIGHT * DEPTH];
    private final int[][] ground = new int[WIDTH][DEPTH];
    private static final Block[] TYPES = Block.values();

    Region(long seed, int ox, int oz, int worldWidth, int worldDepth, boolean features) {
        this.ox = ox; this.oz = oz; this.worldWidth = worldWidth; this.worldDepth = worldDepth;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                int surface = features ? Terrain.height(seed, x + ox, z + oz) : 5;
                ground[x][z] = surface;
                boolean desert=features && Terrain.desert(seed,x+ox,z+oz);
                for (int y = 0; y <= surface; y++)
                    set(x, y, z, y == surface ? desert?Block.SAND:Block.GRASS
                            : features ? Terrain.rock(seed, x + ox, y, z + oz, surface - y) : Block.STONE);
                if (features && Terrain.river(seed,x+ox,z+oz)
                        && (Math.abs(x+ox-worldWidth/2)>18 || Math.abs(z+oz-worldDepth/2)>18)) {
                    for (int y=surface+1; y<=Terrain.WATER_LEVEL; y++) set(x,y,z,Block.WATER);
                }
            }
        }
        if (features) {
            Underground.generate(this, seed ^ ((long) ox << 32) ^ oz);
            generateTrees(new Random(seed ^ ((long) ox << 32) ^ oz),seed);
        }
    }

    private void generateTrees(Random random,long seed) {
        // Jittered, randomly occupied cells give trees space without a regular grid of trunks.
        // The two-block crown radius always stays inside the world.
        for (int cellX = 3; cellX < WIDTH - 2; cellX += 8) {
            for (int cellZ = 3; cellZ < DEPTH - 2; cellZ += 8) {
                int x = cellX + random.nextInt(Math.min(4, WIDTH - 2 - cellX));
                int z = cellZ + random.nextInt(Math.min(4, DEPTH - 2 - cellZ));
                boolean desert=Terrain.desert(seed,x+ox,z+oz);
                if (Terrain.river(seed,x+ox,z+oz)) continue;
                if (random.nextFloat() > (desert?.025f:.7f)) continue;
                // Includes crown clearance around the player's starting point.
                if (protectedSpawn(x,z,7)) continue;
                int height=4+random.nextInt(3);
                if (get(x, groundHeight(x,z), z) == Block.GRASS && crownClear(x,z,height))
                    growTree(x, z, height);
            }
        }
    }

    private boolean crownClear(int x,int z,int treeHeight) {
        int base=groundHeight(x,z)+1,top=base+treeHeight-1;
        for(int y=top-1;y<=top+2;y++) {
            int radius=y<=top?2:1;
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                if((radius==2 || y==top+2) && Math.abs(dx)==radius && Math.abs(dz)==radius) continue;
                if(groundHeight(x+dx,z+dz)>=y) return false;
            }
        }
        return true;
    }

    private void growTree(int x, int z, int height) {
        int base = groundHeight(x, z) + 1, top = base + height - 1;
        for (int y = base; y <= top; y++) set(x, y, z, Block.LOG);
        for (int y = top - 1; y <= top + 2; y++) {
            int radius = y <= top ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Rounded lower layers and a smaller cross-shaped tip.
                    if ((radius == 2 || y == top + 2)
                            && Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    if (get(x + dx, y, z + dz) == Block.AIR)
                        set(x + dx, y, z + dz, Block.LEAVES);
                }
            }
        }
    }

    /** Original terrain surface, before trees and player edits. */
    public int groundHeight(int x, int z) { return ground[x][z]; }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < DEPTH;
    }
    public Block get(int x, int y, int z) {
        return inside(x, y, z) ? TYPES[blocks[(y * WIDTH + x) * DEPTH + z]] : Block.AIR;
    }
    public boolean set(int x, int y, int z, Block block) {
        if (!inside(x, y, z) || get(x, y, z) == block) return false;
        blocks[(y * WIDTH + x) * DEPTH + z] = (byte) block.ordinal();
        return true;
    }

    public int width() { return WIDTH; }
    public int depth() { return DEPTH; }
    public boolean protectedSpawn(int x, int z, int radius) {
        return Math.abs(x + ox - worldWidth / 2) <= radius && Math.abs(z + oz - worldDepth / 2) <= radius;
    }
}
