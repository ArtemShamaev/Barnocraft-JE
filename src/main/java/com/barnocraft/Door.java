package com.barnocraft;

import com.jme3.math.Vector3f;

/** A two-block door with a thin collision panel, kept inside its two occupied cells. */
public record Door(int x, int y, int z, int facing, boolean open) {
    public Door toggled() { return new Door(x,y,z,facing,!open); }
    public Bounds bounds() {
        return switch ((facing + (open ? 1 : 0)) & 3) {
            case 0 -> new Bounds(x,y,z,x+1,y+2,z+.125f);
            case 1 -> new Bounds(x,y,z,x+.125f,y+2,z+1);
            case 2 -> new Bounds(x,y,z+.875f,x+1,y+2,z+1);
            default -> new Bounds(x+.875f,y,z,x+1,y+2,z+1);
        };
    }
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public boolean intersects(float x0, float y0, float z0, float x1, float y1, float z1) {
            return x0 < maxX && x1 > minX && y0 < maxY && y1 > minY && z0 < maxZ && z1 > minZ;
        }
        public Contact ray(Vector3f o, Vector3f d, float reach) {
            float enter = 0, exit = reach;
            int nx = 0, ny = 0, nz = 0;
            float[] low = {minX,minY,minZ}, high = {maxX,maxY,maxZ};
            for (int axis = 0; axis < 3; axis++) {
                float dir = d.get(axis), origin = o.get(axis);
                if (Math.abs(dir) < 1e-8f) {
                    if (origin < low[axis] || origin > high[axis]) return null;
                    continue;
                }
                float a = (low[axis]-origin)/dir, b = (high[axis]-origin)/dir;
                float near = Math.min(a,b), far = Math.max(a,b);
                if (near > enter) {
                    enter = near;
                    int sign = dir > 0 ? -1 : 1;
                    nx = axis == 0 ? sign : 0; ny = axis == 1 ? sign : 0; nz = axis == 2 ? sign : 0;
                }
                exit = Math.min(exit,far);
                if (enter > exit) return null;
            }
            return new Contact(enter,nx,ny,nz);
        }
    }
    public record Contact(float distance, int nx, int ny, int nz) {}
}
