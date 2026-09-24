package com.barnocraft;

/** Seeded smooth value noise sampled in world coordinates, including across chunk seams. */
final class Terrain {
    private Terrain() {}

    static int height(long seed, int x, int z) {
        double hills = noise(seed, x / 28.0, z / 28.0) * 5.0;
        double slopes = noise(seed + 1, x / 14.0, z / 14.0) * 2.0;
        double detail = noise(seed + 2, x / 7.0, z / 7.0) * .7;
        return Math.max(3, Math.min(17, (int) Math.round(10 + hills + slopes + detail)));
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
