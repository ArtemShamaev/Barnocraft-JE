package com.barnocraft;

interface VoxelVolume {
    int width();
    int depth();
    int groundHeight(int x, int z);
    Block get(int x, int y, int z);
    boolean set(int x, int y, int z, Block block);
    boolean inside(int x, int y, int z);
    boolean protectedSpawn(int x, int z, int radius);
}
