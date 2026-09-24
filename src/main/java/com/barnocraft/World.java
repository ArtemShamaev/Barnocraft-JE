package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.*;

/** Lazily generated regions plus sparse persistent edits; no full-world block allocation. */
public final class World implements VoxelVolume {
    public static final int CHUNK = 16, CHUNKS_X = 40, CHUNKS_Z = 25;
    public static final int WIDTH = CHUNK * CHUNKS_X, DEPTH = CHUNK * CHUNKS_Z, HEIGHT = 32;
    public static final int REGION_CACHE_LIMIT = 8;
    private final long seed;
    private final int width, depth;
    private final boolean features;
    private final Map<Integer,Block> edits = new HashMap<>();
    private final Map<Integer,Integer> rotations = new HashMap<>();
    private final Map<Integer,Door> doors = new HashMap<>();
    private final LinkedHashMap<Integer,Region> regions = new LinkedHashMap<>(16,.75f,true);
    private final List<DroppedItem> drops = new ArrayList<>();

    public World() { this(new Random().nextLong()); }
    public World(long seed) { this(seed, WIDTH, DEPTH, true); }
    static World sample(long seed) { return new World(seed,80,80,true); }
    static World flat() { return new World(0,80,80,false); }
    private World(long seed, int width, int depth, boolean features) {
        this.seed = seed; this.width = width; this.depth = depth; this.features = features;
        if (features) generateDrops();
    }
    private void generateDrops() {
        // Убираем генерацию палок и камушков на земле
    }
    public synchronized List<DroppedItem> droppedItems() { return List.copyOf(drops); }
    public synchronized int collectNear(Vector3f position, float radius, ItemType type) {
        int gained=0;
        for (Iterator<DroppedItem> it=drops.iterator();it.hasNext();) {
            DroppedItem item=it.next();
            if (item.type()!=type) continue;
            float dx=position.x-(item.x()+.5f), dy=position.y-(item.y()+.25f), dz=position.z-(item.z()+.5f);
            if (dx*dx+dy*dy+dz*dz<=radius*radius) { it.remove(); gained++; }
        }
        return gained;
    }
    public synchronized ItemType collectNearest(Vector3f position, float radius) {
        DroppedItem found=null; float best=radius*radius;
        for (DroppedItem item:drops) {
            float dx=position.x-(item.x()+.5f), dy=position.y-(item.y()+.25f), dz=position.z-(item.z()+.5f);
            float distance=dx*dx+dy*dy+dz*dz;
            if (distance<=best) { best=distance; found=item; }
        }
        if (found==null) return null;
        drops.remove(found); return found.type();
    }
    public long seed() { return seed; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int chunksX() { return width / CHUNK; }
    public int chunksZ() { return depth / CHUNK; }
    public int groundHeight(int x, int z) { return features ? Terrain.height(seed,x,z) : 5; }
    public boolean protectedSpawn(int x, int z, int radius) {
        return Math.abs(x-width/2) <= radius && Math.abs(z-depth/2) <= radius;
    }
    public boolean inside(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < HEIGHT && z >= 0 && z < depth;
    }
    int key(int x, int y, int z) { return (y * width + x) * depth + z; }
    public synchronized int cachedRegions() { return regions.size(); }
    private Region region(int x, int z) {
        int rx = x / Region.WIDTH, rz = z / Region.DEPTH, key = rx * 100 + rz;
        Region result = regions.get(key);
        if (result == null) {
            result = new Region(seed,rx*Region.WIDTH,rz*Region.DEPTH,width,depth,features);
            regions.put(key,result);
            if (regions.size() > REGION_CACHE_LIMIT) regions.remove(regions.keySet().iterator().next());
        }
        return result;
    }
    public synchronized Block get(int x, int y, int z) {
        if (!inside(x,y,z)) return Block.AIR;
        Block edited = edits.get(key(x,y,z));
        return edited != null ? edited : region(x,z).get(x % Region.WIDTH,y,z % Region.DEPTH);
    }
    private void write(int x, int y, int z, Block block) { edits.put(key(x,y,z),block); }
    public synchronized boolean set(int x, int y, int z, Block block) {
        if (!inside(x,y,z) || block.door() || block == Block.ROTATOR || get(x,y,z) == block) return false;
        Door door = doorAt(x,y,z);
        if (door != null) removeDoor(door);
        write(x,y,z,block);
        rotations.remove(key(x,y,z));
        if (!block.solid()) {
            Door supported = doorAt(x,y+1,z);
            if (supported != null && supported.y() == y+1) removeDoor(supported);
        }
        return true;
    }
    private void removeDoor(Door door) {
        doors.remove(key(door.x(),door.y(),door.z()));
        write(door.x(),door.y(),door.z(),Block.AIR);
        write(door.x(),door.y()+1,door.z(),Block.AIR);
    }
    public synchronized Door doorAt(int x, int y, int z) {
        if (!inside(x,y,z)) return null;
        Door door = doors.get(key(x,y,z));
        return door != null ? door : y > 0 ? doors.get(key(x,y-1,z)) : null;
    }
    public synchronized List<Door> doorsInChunk(int cx, int cz) {
        return doors.values().stream().filter(d -> d.x()/CHUNK == cx && d.z()/CHUNK == cz).toList();
    }
    public synchronized boolean placeDoor(int x, int y, int z, int facing, Player player) {
        if (!inside(x,y+1,z) || y < 1 || get(x,y,z) != Block.AIR || get(x,y+1,z) != Block.AIR
                || !get(x,y-1,z).solid() || get(x,y-1,z).door() || get(x,y-1,z) == Block.STAIRS
                || player.intersects(x,y,z) || player.intersects(x,y+1,z)) return false;
        Door door = new Door(x,y,z,Math.floorMod(facing,4),false);
        doors.put(key(x,y,z),door); write(x,y,z,Block.DOOR); write(x,y+1,z,Block.DOOR_TOP);
        return true;
    }
    public synchronized boolean toggleDoor(int x, int y, int z, Player player) {
        Door door = doorAt(x,y,z);
        if (door == null) return false;
        Door next = door.toggled();
        if (player.intersects(next.bounds())) return false;
        doors.put(key(door.x(),door.y(),door.z()),next);
        return true;
    }
    public synchronized int rotation(int x, int y, int z) { return rotations.getOrDefault(key(x,y,z),0); }
    private void orientation(int x, int y, int z, int facing) {
        int turn = Math.floorMod(facing,4);
        if (turn==0) rotations.remove(key(x,y,z)); else rotations.put(key(x,y,z),turn);
    }
    public synchronized boolean placeStairs(int x, int y, int z, int facing, Player player) {
        if (!inside(x,y,z) || get(x,y,z)!=Block.AIR
                || Stairs.bounds(x,y,z,facing).stream().anyMatch(player::intersects)) return false;
        write(x,y,z,Block.STAIRS); orientation(x,y,z,facing); return true;
    }
    public synchronized boolean rotate(int x, int y, int z, Player player) {
        Block block = get(x,y,z);
        if (!block.solid()) return false;
        Door door = doorAt(x,y,z);
        if (door!=null) {
            Door next = new Door(door.x(),door.y(),door.z(),(door.facing()+1)&3,door.open());
            if (player.intersects(next.bounds())) return false;
            doors.put(key(door.x(),door.y(),door.z()),next); return true;
        }
        int facing = (rotation(x,y,z)+1)&3;
        if (block==Block.STAIRS && Stairs.bounds(x,y,z,facing).stream().anyMatch(player::intersects)) return false;
        // Capture natural blocks too: rotation is a persistent player edit.
        write(x,y,z,block); orientation(x,y,z,facing); return true;
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(seed,Map.copyOf(edits),List.copyOf(doors.values()),Map.copyOf(rotations));
    }
    public record Snapshot(long seed, Map<Integer,Block> edits, List<Door> doors, Map<Integer,Integer> rotations) {}
    public static World restore(Snapshot saved) {
        World world = new World(saved.seed());
        world.edits.putAll(saved.edits());
        world.rotations.putAll(saved.rotations());
        for (Door door : saved.doors()) world.doors.put(world.key(door.x(),door.y(),door.z()),door);
        return world;
    }
    public record Hit(int x, int y, int z, int nx, int ny, int nz) {}

    /** Amanatides-Woo traversal. Explicit infinity avoids 0 * infinity on grid planes. */
    public Hit raycast(Vector3f origin, Vector3f direction, float reach) {
        if (direction.lengthSquared() < 1e-12f || reach < 0) return null;
        Vector3f d = direction.normalize();
        int x = (int) Math.floor(origin.x), y = (int) Math.floor(origin.y), z = (int) Math.floor(origin.z);
        int sx = d.x >= 0 ? 1 : -1, sy = d.y >= 0 ? 1 : -1, sz = d.z >= 0 ? 1 : -1;
        double dx = delta(d.x), dy = delta(d.y), dz = delta(d.z);
        double tx = boundary(origin.x, x, d.x), ty = boundary(origin.y, y, d.y), tz = boundary(origin.z, z, d.z);
        double distance = 0;
        int nx = 0, ny = 0, nz = 0;
        while (distance <= reach) {
            Block block = get(x,y,z);
            if (block.door()) {
                Door door = doorAt(x,y,z);
                Door.Contact contact = door == null ? null : door.bounds().ray(origin,d,reach);
                if (contact != null && contact.distance() + .0001 >= distance
                        && contact.distance() <= Math.min(tx,Math.min(ty,tz)) + .0001)
                    return new Hit(x,y,z,contact.nx(),contact.ny(),contact.nz());
            } else if (block==Block.STAIRS) {
                Door.Contact nearest = null;
                for (Door.Bounds bounds : Stairs.bounds(x,y,z,rotation(x,y,z))) {
                    Door.Contact contact = bounds.ray(origin,d,reach);
                    if (contact!=null && contact.distance()+.0001>=distance
                            && contact.distance()<=Math.min(tx,Math.min(ty,tz))+.0001
                            && (nearest==null || contact.distance()<nearest.distance())) nearest=contact;
                }
                if (nearest!=null) return new Hit(x,y,z,nearest.nx(),nearest.ny(),nearest.nz());
            } else if (block.solid()) return new Hit(x,y,z,nx,ny,nz);
            if (tx < ty && tx < tz) {
                x += sx; distance = tx; tx += dx; nx = -sx; ny = 0; nz = 0;
            } else if (ty < tz) {
                y += sy; distance = ty; ty += dy; nx = 0; ny = -sy; nz = 0;
            } else {
                z += sz; distance = tz; tz += dz; nx = 0; ny = 0; nz = -sz;
            }
        }
        return null;
    }
    private static double delta(float d) { return d == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / d); }
    private static double boundary(float o, int cell, float d) {
        return d == 0 ? Double.POSITIVE_INFINITY : (d > 0 ? cell + 1 - o : o - cell) / Math.abs(d);
    }
}
