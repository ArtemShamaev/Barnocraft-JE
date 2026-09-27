package com.barnocraft;

/** Seeded smooth value noise sampled in world coordinates, including across chunk seams. */
final class Terrain {
    static final int WATER_LEVEL=10;
    private Terrain() {}

    static boolean desert(long seed,int x,int z) {
        double biome=noise(seed^0x444553455254L,x/290.0,z/290.0);
        double patch=noise(seed+71,x/92.0,z/92.0);
        return biome>.02 && patch>-.38;
    }

    static int height(long seed, int x, int z) {
        double broad = noise(seed, x / 105.0, z / 105.0) * 10.0;
        double slopes = noise(seed + 1, x / 39.0, z / 39.0) * 5.0;
        double detail = noise(seed + 2, x / 13.0, z / 13.0) * 2.0;
        double mountainField = Math.max(0,noise(seed + 3,x / 185.0,z / 185.0)-.12);
        double mountain = Math.pow(mountainField,1.35)*112
                + (mountainField>0 ? (1-Math.abs(noise(seed+4,x/31.0,z/31.0)))*8 : 0);
        int surface=(int)Math.round(14+broad+slopes+detail+mountain);

        // A broad, meandering lowland is the river bed. Keep the spawn area
        // dry so a new player never starts in water.
        if (river(seed,x,z) && (Math.abs(x-World.WIDTH/2)>18 || Math.abs(z-World.DEPTH/2)>18))
            surface=Math.min(surface,WATER_LEVEL-1);

        double canyon = Math.abs(noise(seed ^ 0x43414E594F4EL,x/112.0,z/112.0));
        if (canyon<.105) {
            int floor=9+(int)Math.round(Math.abs(noise(seed+5,x/27.0,z/27.0))*5);
            surface=Math.min(surface,floor);
        }
        return Math.max(4,Math.min(World.HEIGHT-8,surface));
    }

    static boolean river(long seed,int x,int z) {
        double bank=Math.abs(noise(seed ^ 0x524956455242414EL,x/82.0,z/82.0));
        double bend=noise(seed ^ 0x524956455242454DL,x/210.0,z/210.0);
        return bank<.075 && Math.abs(bend)>.08;
    }

    static Block rock(long seed, int x, int y, int z, int depth) {
        if (y == 0 || depth < 3) return Block.STONE;
        // Correlated samples form irregular patches, with more deep rock farther below ground.
        double patch = noise(seed ^ 0x524F434BL, x / 6.0, z / 6.0) * .65
                + noise(seed ^ 0x44454550L, x / 10.0 + y / 4.0, z / 10.0 - y / 4.0) * .35;
        double threshold = .4 - Math.min(depth, 10) * .035;
        return patch > threshold ? Block.DEEPSTONE : Block.STONE;
    }

    private static double noise(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        return mix(mix(value(seed, ix, iz), value(seed, ix + 1, iz), fx),
                mix(value(seed, ix, iz + 1), value(seed, ix + 1, iz + 1), fx), fz);
    }

    private static double value(long seed, int x, int z) {
        long n = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
        n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
        n ^= n >>> 31;
        return (n >>> 11) * 0x1.0p-53 * 2 - 1;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
