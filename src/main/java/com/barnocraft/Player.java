package com.barnocraft;

import com.jme3.math.Vector3f;

/** Axis-separated AABB movement with small steps to prevent tunnelling. */
public final class Player {
    public static final float HALF_WIDTH = .3f, HEIGHT = 1.8f, EYE = 1.62f;
    public static final float REACH = 5f;
    private static final float EPSILON = .00001f;
    public final Vector3f position = new Vector3f(40, 6.001f, 40);
    public float velocityY;
    public boolean grounded = true;

    public void update(World world, float dt, Vector3f movement, boolean jump) {
        if (jump && grounded) { velocityY = 8; grounded = false; }
        int steps = Math.max(1, (int) Math.ceil(dt / .005f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            velocityY -= 25 * h;
            float dy = velocityY * h;
            grounded = false;
            if (!move(world, 1, dy)) { grounded = dy < 0; velocityY = 0; }
            moveHorizontal(world, 0, movement.x * 4 * h);
            moveHorizontal(world, 2, movement.z * 4 * h);
            position.x = Math.max(HALF_WIDTH, Math.min(world.width() - HALF_WIDTH, position.x));
            position.z = Math.max(HALF_WIDTH, Math.min(world.depth() - HALF_WIDTH, position.z));
        }
        if (position.y < -20) respawn(world);
    }

    private void moveHorizontal(World world, int axis, float amount) {
        if (amount==0) return;
        Vector3f start = position.clone();
        if (move(world,axis,amount) || !grounded) return;
        Vector3f blocked = position.clone();
        // Step over a half-block riser, but never climb a full block or a low ceiling.
        position.set(start); position.y += .5001f;
        if (collides(world)) { position.set(blocked); return; }
        position.set(axis,position.get(axis)+amount);
        if (collides(world)) { position.set(blocked); return; }
        move(world,1,-.5002f);
    }

    private boolean move(World world, int axis, float amount) {
        float start = position.get(axis);
        position.set(axis, start + amount);
        if (!collides(world)) return true;
        // Find the last non-intersecting point, retaining contact precision and wall sliding.
        float low = 0, high = 1;
        for (int i = 0; i < 18; i++) {
            float mid = (low + high) * .5f;
            position.set(axis, start + amount * mid);
            if (collides(world)) high = mid; else low = mid;
        }
        position.set(axis, start + amount * low);
        return false;
    }

    public boolean collides(World world) {
        for (int x = floor(position.x - HALF_WIDTH + EPSILON); x <= floor(position.x + HALF_WIDTH - EPSILON); x++)
            for (int y = floor(position.y + EPSILON); y <= floor(position.y + HEIGHT - EPSILON); y++)
                for (int z = floor(position.z - HALF_WIDTH + EPSILON); z <= floor(position.z + HALF_WIDTH - EPSILON); z++)
                    if (world.get(x,y,z).door()) {
                        Door door = world.doorAt(x,y,z);
                        if (door != null && intersects(door.bounds())) return true;
                    } else if (world.get(x,y,z)==Block.STAIRS) {
                        for (Door.Bounds box : Stairs.bounds(x,y,z,world.rotation(x,y,z)))
                            if (intersects(box)) return true;
                    } else if (world.get(x,y,z).solid()) return true;
        return false;
    }
    public boolean intersects(Door.Bounds b) {
        return b.intersects(position.x-HALF_WIDTH+EPSILON,position.y+EPSILON,position.z-HALF_WIDTH+EPSILON,
                position.x+HALF_WIDTH-EPSILON,position.y+HEIGHT-EPSILON,position.z+HALF_WIDTH-EPSILON);
    }
    public boolean intersects(int x, int y, int z) {
        // Contact is not overlap: the movement solver has a small floating-point tolerance.
        float margin = EPSILON * 4;
        return position.x - HALF_WIDTH + margin < x + 1 && position.x + HALF_WIDTH - margin > x
            && position.y + margin < y + 1 && position.y + HEIGHT - margin > y
            && position.z - HALF_WIDTH + margin < z + 1 && position.z + HALF_WIDTH - margin > z;
    }
    public void respawn(World world) {
        position.set(world.width() / 2f + .5f, World.HEIGHT + 1, world.depth() / 2f + .5f);
        for (int y = World.HEIGHT - 1; y >= 0; y--) {
            if (world.get(floor(position.x), y, floor(position.z)).solid()) {
                position.y = y + 1.001f;
                break;
            }
        }
        velocityY = 0;
        grounded = false;
    }
    private static int floor(float value) { return (int) Math.floor(value); }
}
