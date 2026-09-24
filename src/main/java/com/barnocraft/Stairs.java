package com.barnocraft;

import java.util.List;

/** Two half-height treads; facing 0 rises toward -Z, then turns clockwise in 90-degree steps. */
final class Stairs {
    private Stairs() {}
    static List<Door.Bounds> bounds(int x, int y, int z, int facing) {
        Door.Bounds lower = new Door.Bounds(x,y,z,x+1,y+.5f,z+1);
        Door.Bounds upper = switch (facing & 3) {
            case 0 -> new Door.Bounds(x,y+.5f,z,x+1,y+1,z+.5f);
            case 1 -> new Door.Bounds(x+.5f,y+.5f,z,x+1,y+1,z+1);
            case 2 -> new Door.Bounds(x,y+.5f,z+.5f,x+1,y+1,z+1);
            default -> new Door.Bounds(x,y+.5f,z,x+.5f,y+1,z+1);
        };
        return List.of(lower,upper);
    }
    static boolean filled(int x, int y, int z) {
        return x>=0 && x<2 && y>=0 && y<2 && z>=0 && z<2 && (y==0 || z==0);
    }
}
