package com.barnocraft;

import com.jme3.math.Vector3f;
import com.jme3.math.FastMath;
import java.util.*;

/** Lazily generated regions plus sparse persistent edits; no full-world block allocation. */
public final class World implements VoxelVolume {
    public static final int CHUNK = 16, CHUNKS_X = 60, CHUNKS_Z = 40;
    public static final int WIDTH = CHUNK * CHUNKS_X, DEPTH = CHUNK * CHUNKS_Z, HEIGHT = 128;
    public static final int REGION_CACHE_LIMIT = 8;
    private final long seed;
    private final int width, depth;
    private final boolean features;
    private final Map<Integer,Block> edits = new HashMap<>();
    private final Map<Integer,Integer> rotations = new HashMap<>();
    private final Map<Integer,Door> doors = new HashMap<>();
    private final Map<Integer,MachineSlots> stonecutters = new HashMap<>();
    private final Map<Integer,FurnaceSlots> furnaces = new HashMap<>();
    private final Map<Integer,Metalworking.State> workshops = new HashMap<>();
    private final LinkedHashMap<Integer,Region> regions = new LinkedHashMap<>(16,.75f,true);
    private final List<DroppedItem> drops = new ArrayList<>();
    private final List<Sheep> sheep = new ArrayList<>();
    private final List<Zombie> zombies = new ArrayList<>();
    private final List<Fish> fish = new ArrayList<>();
    private final Map<Integer,Integer> furnaceFuel = new HashMap<>();
    private float sheepClock;
    private float zombieSpawnClock;
    private float dayTime=0.28f;
    private boolean keepInventory;
    private int blockRevision;

    public World() { this(new Random().nextLong()); }
    public World(long seed) { this(seed, WIDTH, DEPTH, true); }
    static World sample(long seed) { return new World(seed,80,80,true); }
    static World flat() { return new World(0,80,80,false); }
    private World(long seed, int width, int depth, boolean features) {
        this.seed = seed; this.width = width; this.depth = depth; this.features = features;
        if (features) { generateDrops(); generateSheep(); }
    }
    private void generateDrops() {
        // Убираем генерацию палок и камушков на земле
    }
    private void generateSheep() {
        Random random=new Random(seed^0x53484545504cL);
        spawnSheepNear(new Vector3f(width/2f,0,depth/2f),24,random,12,56);
    }
    public synchronized List<Sheep> sheep() { return List.copyOf(sheep); }
    public synchronized List<Zombie> zombies() { return List.copyOf(zombies); }
    public synchronized List<Fish> fish() { return List.copyOf(fish); }
    public synchronized float dayTime() { return dayTime; }
    /** One full day/night cycle lasts two real minutes. */
    /** One in-game day lasts ten real minutes. */
    public synchronized void advanceTime(float tpf) { dayTime=(dayTime+Math.max(0,tpf)/600f)%1f; }
    public synchronized boolean keepInventory() { return keepInventory; }
    public synchronized void keepInventory(boolean enabled) { keepInventory=enabled; }
    public synchronized void setDayTime(float time) { dayTime=time-(float)Math.floor(time); }
    public float daylight() { return Math.max(0,Math.min(1,(float)((Math.sin((dayTime-.25)*Math.PI*2)+.08)/.78))); }
    boolean canSpawnZombie(int x,int y,int z,VoxelLighting lighting) { return get(x,y-1,z).solid() && get(x,y,z)==Block.AIR && lighting.sky(x,y,z)<4; }
    Map<Integer,Metalworking.State> workshops() { return workshops; }
    public synchronized Metalworking.State workshopAt(int x,int y,int z) {
        Block b=get(x,y,z); if(b!=Block.CASTING_TABLE && b!=Block.WELDER) return null;
        return workshops.getOrDefault(key(x,y,z),Metalworking.State.empty(b));
    }
    public synchronized boolean selectCastingRecipe(int x,int y,int z,ItemType recipe) {
        Metalworking.State s=workshopAt(x,y,z);
        if(s==null || s.block()!=Block.CASTING_TABLE || !Metalworking.CASTING_RECIPES.contains(recipe)) return false;
        workshops.put(key(x,y,z),new Metalworking.State(s.block(),s.slots(),recipe)); return true;
    }
    public synchronized boolean putWorkshopItem(int x,int y,int z,int stationSlot,Inventory inventory,int inventorySlot) {
        Metalworking.State s=workshopAt(x,y,z); Inventory.Slot source=inventory.slotAtIndex(inventorySlot);
        if(s==null || source==null || !Metalworking.accepts(s.block(),stationSlot,source.item())) return false;
        Inventory.Slot current=s.at(stationSlot);
        if(current.item()!=null && current.item()!=source.item() || current.count()>=source.item().stackLimit()) return false;
        if(!inventory.consumeAt(inventorySlot,source.item())) return false;
        workshops.put(key(x,y,z),s.with(stationSlot,new Inventory.Slot(source.item(),current.count()+1,source.durability()))); return true;
    }
    public synchronized boolean takeWorkshopItem(int x,int y,int z,int stationSlot,Inventory inventory,int inventorySlot) {
        Metalworking.State s=workshopAt(x,y,z); if(s==null || s.at(stationSlot)==null || s.at(stationSlot).item()==null) return false;
        Inventory.Slot source=s.at(stationSlot);
        if(!inventory.acceptSlotAt(inventorySlot,new Inventory.Slot(source.item(),1,source.durability()))) return false;
        workshops.put(key(x,y,z),s.with(stationSlot,Metalworking.decrement(source,1))); return true;
    }
    public synchronized boolean takeWorkshopOutput(int x,int y,int z,Inventory inventory) {
        for(int i=0;i<Inventory.SLOT_COUNT;i++) if(takeWorkshopItem(x,y,z,Metalworking.OUTPUT,inventory,i)) return true;
        return false;
    }
    public synchronized boolean craftWorkshop(int x,int y,int z) {
        Metalworking.State s=workshopAt(x,y,z), next=s==null?null:Metalworking.craft(s);
        if(next==null) return false; workshops.put(key(x,y,z),next); return true;
    }
    public synchronized Hit useBucket(Inventory inventory,int slot,Vector3f origin,Vector3f direction) {
        ItemType item=inventory.itemAt(slot); if(item!=ItemType.BUCKET && item!=ItemType.WATER_BUCKET) return null;
        Hit hit=raycast(origin,direction,Player.REACH); if(hit==null) return null;
        if(item==ItemType.BUCKET && get(hit.x(),hit.y(),hit.z())==Block.WATER) {
            if(!set(hit.x(),hit.y(),hit.z(),Block.AIR)) return null; inventory.replaceAt(slot,item,ItemType.WATER_BUCKET); return hit;
        }
        if(item==ItemType.WATER_BUCKET) { int x=hit.x()+hit.nx(),y=hit.y()+hit.ny(),z=hit.z()+hit.nz(); if(get(x,y,z)!=Block.AIR || !set(x,y,z,Block.WATER)) return null; inventory.replaceAt(slot,item,ItemType.BUCKET); return new Hit(x,y,z,0,0,0); }
        return null;
    }
    synchronized void addSheep(Sheep animal) { sheep.add(animal); }
    public synchronized void spawnSheepNear(Vector3f position,int amount) {
        spawnSheepNear(position,amount,new Random(seed^System.nanoTime()),3,10);
    }
    private void spawnSheepNear(Vector3f position,int amount,Random random,int minRadius,int maxRadius) {
        for (int i=0;i<amount && sheep.size()<400;i++) {
            double angle=random.nextDouble()*Math.PI*2;
            float radius=minRadius+random.nextFloat()*(maxRadius-minRadius);
            int x=Math.max(1,Math.min(width-2,(int)(position.x+Math.cos(angle)*radius)));
            int z=Math.max(1,Math.min(depth-2,(int)(position.z+Math.sin(angle)*radius)));
            sheep.add(new Sheep(x+.5f,groundHeight(x,z)+1f,z+.5f,3,(float)Math.cos(angle),(float)Math.sin(angle),0));
        }
    }
    public synchronized void ensureSheepNear(Vector3f position) {
        long local=sheep.stream().filter(animal -> {
            float dx=animal.x()-position.x,dz=animal.z()-position.z;
            return dx*dx+dz*dz<72*72;
        }).count();
        if (local<16) spawnSheepNear(position,(int)(16-local),new Random(seed^Float.floatToIntBits(position.x)^((long)Float.floatToIntBits(position.z)<<32)^sheep.size()),12,64);
    }
    public synchronized void ensureFishNear(Vector3f position) {
        int local=0; for(Fish f:fish) if(Math.abs(f.x()-position.x)<48 && Math.abs(f.z()-position.z)<48) local++;
        if(local<8) spawnFishNear(position,8-local,new Random(seed^Float.floatToIntBits(position.x)^((long)Float.floatToIntBits(position.z)<<32)^fish.size()),18,64);
    }
    private void spawnFishNear(Vector3f position,int amount,Random random) { spawnFishNear(position,amount,random,8,28); }
    private void spawnFishNear(Vector3f position,int amount,Random random,int minRadius,int maxRadius) {
        for(int n=0,tries=0;n<amount && tries<amount*40;tries++) {
            double angle=random.nextDouble()*Math.PI*2, radius=minRadius+random.nextDouble()*(maxRadius-minRadius);
            int x=Math.max(1,Math.min(width-2,(int)(position.x+Math.cos(angle)*radius)));
            int z=Math.max(1,Math.min(depth-2,(int)(position.z+Math.sin(angle)*radius)));
            if(get(x,Terrain.WATER_LEVEL,z)!=Block.WATER) continue;
            double a=random.nextDouble()*Math.PI*2; fish.add(new Fish(x+.5f,Terrain.WATER_LEVEL+.35f,z+.5f,(float)Math.cos(a),(float)Math.sin(a))); n++;
        }
    }
    public synchronized void updateFish(float tpf) {
        if(tpf<=0)return;
        for(int i=0;i<fish.size();i++) {
            Fish f=fish.get(i); float speed=Math.min(.06f,tpf)*1.8f;
            float nx=f.x()+f.dx()*speed,nz=f.z()+f.dz()*speed;
            if(get((int)nx,Terrain.WATER_LEVEL,(int)nz)!=Block.WATER) fish.set(i,f.move(f.x(),f.y(),f.z(),-f.dx(),-f.dz()));
            else fish.set(i,f.move(nx,Terrain.WATER_LEVEL+.35f,nz,f.dx(),f.dz()));
        }
    }
    public synchronized int killSheep(Inventory inventory) {
        int killed=sheep.size();
        for (Sheep animal:sheep) dropSheep(animal,inventory);
        sheep.clear();
        return killed;
    }
    public synchronized int killSheep() { return killSheep(null); }
    private void dropSheep(Sheep animal,Inventory inventory) {
        int x=(int)Math.floor(animal.x()),y=(int)Math.floor(animal.y()),z=(int)Math.floor(animal.z());
        if(inventory==null || inventory.add(ItemType.MUTTON,1)>0) drops.add(new DroppedItem(ItemType.MUTTON,x,y,z));
        if(inventory==null || inventory.add(ItemType.WOOL,1)>0) drops.add(new DroppedItem(ItemType.WOOL,x,y,z));
    }
    public synchronized void updateSheep(float tpf) {
        sheepClock+=Math.max(0,tpf);
        for (int i=0;i<sheep.size();i++) {
            Sheep animal=sheep.get(i);
            float flee=Math.max(0,animal.fleeTime()-tpf);
            float dx=animal.directionX(),dz=animal.directionZ();
            if(flee==0) {
                float phase=sheepClock*.23f+i*2.17f;
                dx=dx*.94f+FastMath.sin(phase)*.06f;
                dz=dz*.94f+FastMath.cos(phase*.81f)*.06f;
            }
            float length=FastMath.sqrt(dx*dx+dz*dz);
            if(length<.001f) { dx=1; dz=0; length=1; }
            dx/=length; dz/=length;
            float speed=flee>0?3.2f:.55f;
            float step=speed*Math.min(Math.max(tpf,0),.1f);
            float nextX=animal.x()+dx*step,nextZ=animal.z()+dz*step;
            if(sheepCanOccupy(animal,nextX,nextZ)) {
                int ground=groundHeight((int)nextX,(int)nextZ);
                sheep.set(i,animal.move(nextX,ground+1f,nextZ,dx,dz,flee));
            } else if(sheepCanOccupy(animal,nextX,animal.z())) {
                int ground=groundHeight((int)nextX,(int)animal.z());
                sheep.set(i,animal.move(nextX,ground+1f,animal.z(),dx,dz,flee));
            } else if(sheepCanOccupy(animal,animal.x(),nextZ)) {
                int ground=groundHeight((int)animal.x(),(int)nextZ);
                sheep.set(i,animal.move(animal.x(),ground+1f,nextZ,dx,dz,flee));
            } else sheep.set(i,animal.move(animal.x(),animal.y(),animal.z(),dx,dz,flee));
        }
    }
    public synchronized void updateSheep(float tpf,Vector3f threat) { updateSheep(tpf); }
    public synchronized int collectIntoInventory(Vector3f position,float radius,Inventory inventory) {
        int collected=0;
        for(Iterator<DroppedItem> it=drops.iterator();it.hasNext();) {
            DroppedItem item=it.next();
            if(position.distanceSquared(new Vector3f(item.x()+.5f,item.y(),item.z()+.5f))>radius*radius) continue;
            for(int slot=0;slot<Inventory.SLOT_COUNT;slot++) {
                if(inventory.acceptSlotAt(slot,new Inventory.Slot(item.type(),1,item.durability()))) { it.remove(); collected++; break; }
            }
        }
        return collected;
    }
    private boolean sheepCanOccupy(Sheep animal,float x,float z) {
        if(x<1 || x>=width-1 || z<1 || z>=depth-1) return false;
        int oldGround=groundHeight((int)animal.x(),(int)animal.z());
        int newGround=groundHeight((int)x,(int)z);
        if(Math.abs(newGround-oldGround)>1) return false;
        float feet=newGround+1.001f;
        int minX=(int)Math.floor(x-.32f),maxX=(int)Math.floor(x+.32f);
        int minZ=(int)Math.floor(z-.32f),maxZ=(int)Math.floor(z+.32f);
        for(int bx=minX;bx<=maxX;bx++) for(int bz=minZ;bz<=maxZ;bz++)
            for(int by=(int)Math.floor(feet+.02f);by<=Math.floor(feet+1.15f);by++)
                if(get(bx,by,bz).solid()) return false;
        return true;
    }
    public synchronized boolean attackSheep(Vector3f origin,Vector3f direction,float reach,Inventory inventory) {
        if (direction.lengthSquared()<1e-8f) return false;
        Vector3f ray=direction.normalize();
        Hit blockHit=raycast(origin,ray,reach);
        float blockDistance=Float.POSITIVE_INFINITY;
        if (blockHit!=null) {
            Vector3f blockCenter=new Vector3f(blockHit.x()+.5f,blockHit.y()+.5f,blockHit.z()+.5f);
            blockDistance=Math.max(0,blockCenter.subtract(origin).dot(ray)-.9f);
        }
        int hitIndex=-1;
        float nearest=reach;
        float sheepRadius=.72f;
        for (int i=0;i<sheep.size();i++) {
            Sheep animal=sheep.get(i);
            Vector3f center=new Vector3f(animal.x(),animal.y()+.55f,animal.z());
            float along=center.subtract(origin).dot(ray);
            if (along<0 || along>reach+sheepRadius) continue;
            Vector3f closest=origin.add(ray.mult(along));
            float perpendicularSquared=closest.distanceSquared(center);
            if (perpendicularSquared>sheepRadius*sheepRadius) continue;
            float contact=along-FastMath.sqrt(sheepRadius*sheepRadius-perpendicularSquared);
            if (contact<=nearest && contact<=blockDistance) { nearest=contact; hitIndex=i; }
        }
        if (hitIndex<0) return false;
        Sheep original=sheep.get(hitIndex);
        Sheep animal=original.hurt(original.x()-origin.x,original.z()-origin.z);
        if (animal.health()<=0) {
            sheep.remove(hitIndex);
            dropSheep(animal,inventory);
        } else sheep.set(hitIndex,animal);
        return true;
    }
    public synchronized void updateZombies(float tpf,Player player) {
        if(tpf<=0) return;
        zombieSpawnClock+=tpf;
        boolean night=dayTime>=.72f || dayTime<.22f;
        if(night && zombieSpawnClock>=5f && zombies.size()<24) {
            zombieSpawnClock=0;
            Random random=new Random(seed^System.nanoTime());
            float angle=random.nextFloat()*FastMath.TWO_PI;
            float radius=12+random.nextFloat()*10;
            int x=Math.max(1,Math.min(width-2,(int)(player.position.x+FastMath.cos(angle)*radius)));
            int z=Math.max(1,Math.min(depth-2,(int)(player.position.z+FastMath.sin(angle)*radius)));
            zombies.add(new Zombie(x+.5f,groundHeight(x,z)+1f,z+.5f,8,0));
        }
        for(int i=zombies.size()-1;i>=0;i--) {
            Zombie zombie=zombies.get(i);
            float dx=player.position.x-zombie.x(),dz=player.position.z-zombie.z();
            float distance=FastMath.sqrt(dx*dx+dz*dz);
            float cooldown=Math.max(0,zombie.attackCooldown()-tpf);
            if(distance<1.15f) {
                if(cooldown==0) { player.damage(1); cooldown=1.5f; }
            } else if(distance<40) {
                float step=Math.min(.1f,tpf)*1.8f;
                float nx=zombie.x()+dx/distance*step,nz=zombie.z()+dz/distance*step;
                if(zombieCanOccupy(zombie,nx,nz)) {
                    int ground=groundHeight((int)nx,(int)nz);
                    zombie=zombie.move(nx,ground+1f,nz,cooldown);
                } else {
                    float sideX=-dz/distance,sideZ=dx/distance;
                    float sx=zombie.x()+sideX*step,sz=zombie.z()+sideZ*step;
                    if(zombieCanOccupy(zombie,sx,sz)) zombie=zombie.move(sx,groundHeight((int)sx,(int)sz)+1f,sz,cooldown);
                    else zombie=zombie.move(zombie.x(),zombie.y(),zombie.z(),cooldown);
                }
            } else zombie=zombie.move(zombie.x(),zombie.y(),zombie.z(),cooldown);
            zombies.set(i,zombie);
        }
    }
    private boolean zombieCanOccupy(Zombie zombie,float x,float z) {
        if(x<1 || x>=width-1 || z<1 || z>=depth-1) return false;
        int oldGround=groundHeight((int)zombie.x(),(int)zombie.z()),newGround=groundHeight((int)x,(int)z);
        if(Math.abs(newGround-oldGround)>1) return false;
        float feet=newGround+1.001f;
        for(int bx=(int)Math.floor(x-.32f);bx<=(int)Math.floor(x+.32f);bx++)
            for(int bz=(int)Math.floor(z-.32f);bz<=(int)Math.floor(z+.32f);bz++)
                for(int by=(int)Math.floor(feet+.02f);by<=Math.floor(feet+1.7f);by++)
                    if(get(bx,by,bz).solid()) return false;
        return true;
    }
    public synchronized boolean attackZombie(Vector3f origin,Vector3f direction,float reach) {
        if(direction.lengthSquared()<1e-8f) return false;
        Vector3f ray=direction.normalize();
        Hit blockHit=raycast(origin,ray,reach);
        float blockDistance=Float.POSITIVE_INFINITY;
        if(blockHit!=null) blockDistance=Math.max(0,new Vector3f(blockHit.x()+.5f,blockHit.y()+.5f,blockHit.z()+.5f).subtract(origin).dot(ray)-.9f);
        int found=-1; float nearest=reach;
        for(int i=0;i<zombies.size();i++) {
            Zombie zombie=zombies.get(i); Vector3f center=new Vector3f(zombie.x(),zombie.y()+.9f,zombie.z());
            float along=center.subtract(origin).dot(ray); if(along<0 || along>reach+.55f) continue;
            float perpendicular=origin.add(ray.mult(along)).distanceSquared(center);
            if(perpendicular>.55f*.55f) continue;
            float contact=along-FastMath.sqrt(.55f*.55f-perpendicular);
            if(contact<=nearest && contact<=blockDistance) { found=i; nearest=contact; }
        }
        if(found<0) return false;
        Zombie hurt=zombies.get(found).hurt();
        if(hurt.health()<=0) {
            zombies.remove(found);
            drops.add(new DroppedItem(ItemType.ROTTEN_FLESH,(int)hurt.x(),(int)hurt.y(),(int)hurt.z()));
        } else zombies.set(found,hurt);
        return true;
    }
    public synchronized List<DroppedItem> droppedItems() { return List.copyOf(drops); }
    public synchronized void dropInventory(Inventory inventory,Vector3f position) {
        for(int i=0;i<Inventory.SLOT_COUNT;i++) {
            Inventory.Slot slot=inventory.slotAtIndex(i);
            if(slot==null || slot.item()==null) continue;
            for(int count=0;count<slot.count();count++) drops.add(new DroppedItem(slot.item(),(int)position.x,(int)position.y,(int)position.z));
        }
        inventory.clear();
    }
    public synchronized List<Vector3f> torchesNear(float x,float z,float radius,int limit) {
        float radiusSquared=radius*radius;
        List<Vector3f> torches=new ArrayList<>();
        for(var entry:edits.entrySet()) {
            if(entry.getValue()!=Block.TORCH) continue;
            int key=entry.getKey(),torchZ=key%depth,temp=key/depth,torchX=temp%width,torchY=temp/width;
            float dx=torchX+.5f-x,dz=torchZ+.5f-z;
            if(dx*dx+dz*dz<=radiusSquared) torches.add(new Vector3f(torchX+.5f,torchY+.7f,torchZ+.5f));
        }
        torches.sort(Comparator.comparingDouble(light -> light.distanceSquared(new Vector3f(x,0,z))));
        return torches.size()>limit?List.copyOf(torches.subList(0,limit)):List.copyOf(torches);
    }
    public synchronized int blockRevision() { return blockRevision; }
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
    public boolean desert(int x,int z) { return features && Terrain.desert(seed,x,z); }
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
        if (get(x,y,z) == Block.STONECUTTER) stonecutters.remove(key(x,y,z));
        if (get(x,y,z) == Block.FURNACE) { furnaces.remove(key(x,y,z)); furnaceFuel.remove(key(x,y,z)); }
        Door door = doorAt(x,y,z);
        if (door != null) removeDoor(door);
        write(x,y,z,block);
        blockRevision++;
        rotations.remove(key(x,y,z));
        if (!block.solid()) {
            Door supported = doorAt(x,y+1,z);
            if (supported != null && supported.y() == y+1) removeDoor(supported);
        }
        return true;
    }
    public record MachineSlots(Inventory.Slot coal, Inventory.Slot stone, Inventory.Slot output, ItemType recipe) {
        public MachineSlots {
            if (coal == null || stone == null || output == null || !stonecutterRecipe(recipe)) {
                throw new IllegalArgumentException("Invalid stonecutter state");
            }
        }
        public MachineSlots(Inventory.Slot coal, Inventory.Slot stone, Inventory.Slot output) {
            this(coal,stone,output,ItemType.AXE_HEAD);
        }
        static MachineSlots empty() {
            Inventory.Slot empty = new Inventory.Slot(null,0);
            return new MachineSlots(empty,empty,empty,ItemType.AXE_HEAD);
        }
        Inventory.Slot at(int slot) {
            return switch (slot) { case 0 -> coal; case 1 -> stone; case 2 -> output; default -> null; };
        }
    }
    private static boolean stonecutterRecipe(ItemType recipe) {
        return recipe==ItemType.AXE_HEAD || recipe==ItemType.PICKAXE_HEAD || recipe==ItemType.SHOVEL_HEAD
                || recipe==ItemType.IRON_AXE_HEAD || recipe==ItemType.IRON_PICKAXE_HEAD
                || recipe==ItemType.IRON_SHOVEL_HEAD;
    }
    public static ItemType stonecutterMaterial(ItemType recipe) {
        return recipe==ItemType.IRON_AXE_HEAD || recipe==ItemType.IRON_PICKAXE_HEAD || recipe==ItemType.IRON_SHOVEL_HEAD
                ? ItemType.IRON_INGOT : ItemType.STONE;
    }
    public static int stonecutterMaterialCount(ItemType recipe) {
        return stonecutterMaterial(recipe)==ItemType.IRON_INGOT?5:1;
    }
    public record FurnaceSlots(Inventory.Slot coal,Inventory.Slot input,Inventory.Slot output) {
        public FurnaceSlots {
            if (coal==null || input==null || output==null) throw new IllegalArgumentException("Missing furnace slot");
        }
        static FurnaceSlots empty() {
            Inventory.Slot empty=new Inventory.Slot(null,0);
            return new FurnaceSlots(empty,empty,empty);
        }
        Inventory.Slot iron() { return input; }
        Inventory.Slot at(int slot) {
            return switch (slot) { case 0 -> coal; case 1 -> input; case 2 -> output; default -> null; };
        }
    }
    public synchronized FurnaceSlots furnaceAt(int x,int y,int z) {
        if (get(x,y,z)!=Block.FURNACE) return null;
        return furnaces.getOrDefault(key(x,y,z),FurnaceSlots.empty());
    }
    public synchronized boolean putFurnaceItem(int x,int y,int z,int machineSlot,Inventory inventory,int inventorySlot) {
        FurnaceSlots state=furnaceAt(x,y,z);
        if (state==null || (machineSlot!=0 && machineSlot!=1)) return false;
        Inventory.Slot source=inventory.slotAtIndex(inventorySlot), current=state.at(machineSlot);
        ItemType sourceType=source==null?null:source.item();
        ItemType required=machineSlot==0?ItemType.COAL:sourceType;
        if (machineSlot==1 && sourceType!=ItemType.IRON && sourceType!=ItemType.SAND) return false;
        if (source==null || source.item()!=required || (current.item()!=null && current.item()!=required)
                || current.count()>=Inventory.MAX_STACK || !inventory.consumeAt(inventorySlot,required)) return false;
        Inventory.Slot updated=new Inventory.Slot(required,current.count()+1);
        furnaces.put(key(x,y,z),machineSlot==0
            ? new FurnaceSlots(updated,state.input(),state.output())
            : new FurnaceSlots(state.coal(),updated,state.output()));
        return true;
    }
    /** Furnace processing runs from the game tick; no button press is needed. */
    public synchronized void updateFurnaces() {
        for (Integer location : new ArrayList<>(furnaces.keySet())) {
            int z=location%depth, value=location/depth, x=value%width, y=value/width;
            smeltIron(x,y,z);
        }
    }
    public synchronized boolean smeltIron(int x,int y,int z) {
        FurnaceSlots state=furnaceAt(x,y,z);
        ItemType result=state==null?null:state.input().item()==ItemType.SAND?ItemType.GLASS:ItemType.IRON_INGOT;
        int furnaceKey=key(x,y,z), fuel=furnaceFuel.getOrDefault(furnaceKey,0);
        if (state==null || (fuel<=0 && state.coal().count()<1) || state.input().count()<1
            || (state.input().item()!=ItemType.IRON && state.input().item()!=ItemType.SAND)
            || (state.output().item()!=null && state.output().item()!=result)
                || state.output().count()>=Inventory.MAX_STACK) return false;
        Inventory.Slot coal=state.coal();
        if (fuel<=0) { coal=decrement(coal); fuel=8; }
        furnaces.put(furnaceKey,new FurnaceSlots(coal,decrement(state.input()),increment(state.output(),result)));
        furnaceFuel.put(furnaceKey,fuel-1);
        return true;
    }
    public synchronized boolean takeFurnaceItem(int x,int y,int z,int machineSlot,Inventory inventory,int inventorySlot) {
        FurnaceSlots state=furnaceAt(x,y,z);
        if (state==null || machineSlot<0 || machineSlot>2) return false;
        Inventory.Slot source=state.at(machineSlot);
        if (source.count()<1 || !inventory.acceptAt(inventorySlot,source.item())) return false;
        Inventory.Slot updated=decrement(source);
        furnaces.put(key(x,y,z),switch (machineSlot) {
            case 0 -> new FurnaceSlots(updated,state.input(),state.output());
            case 1 -> new FurnaceSlots(state.coal(),updated,state.output());
            default -> new FurnaceSlots(state.coal(),state.input(),updated);
        });
        return true;
    }
    public synchronized boolean takeFurnaceOutput(int x,int y,int z,Inventory inventory) {
        FurnaceSlots state=furnaceAt(x,y,z);
        if (state==null || state.output().count()<1 || inventory.add(state.output().item(),1)!=0) return false;
        furnaces.put(key(x,y,z),new FurnaceSlots(state.coal(),state.input(),decrement(state.output())));
        return true;
    }
    public synchronized MachineSlots stonecutterAt(int x, int y, int z) {
        if (get(x,y,z) != Block.STONECUTTER) return null;
        return stonecutters.getOrDefault(key(x,y,z),MachineSlots.empty());
    }
    public synchronized boolean putStonecutterItem(int x, int y, int z, int machineSlot, Inventory inventory, int inventorySlot) {
        MachineSlots state=stonecutterAt(x,y,z);
        if (state==null || (machineSlot!=0 && machineSlot!=1)) return false;
        Inventory.Slot source=inventory.slotAtIndex(inventorySlot);
        ItemType required=machineSlot==0?ItemType.COAL:stonecutterMaterial(state.recipe());
        Inventory.Slot current=state.at(machineSlot);
        if (source==null || source.item()!=required || source.count()<1
                || (current.item()!=null && current.item()!=required) || current.count()>=Inventory.MAX_STACK) return false;
        if (!inventory.consumeAt(inventorySlot,required)) return false;
        Inventory.Slot updated=new Inventory.Slot(required,current.count()+1);
        stonecutters.put(key(x,y,z),machineSlot==0
            ? new MachineSlots(updated,state.stone(),state.output(),state.recipe())
            : new MachineSlots(state.coal(),updated,state.output(),state.recipe()));
        return true;
    }
        public synchronized boolean selectStonecutterRecipe(int x,int y,int z,ItemType recipe) {
        MachineSlots state=stonecutterAt(x,y,z);
        if (state==null || !stonecutterRecipe(recipe)) return false;
        stonecutters.put(key(x,y,z),new MachineSlots(state.coal(),state.stone(),state.output(),recipe));
        return true;
        }
    public synchronized boolean craftStonecutterHead(int x, int y, int z) {
        MachineSlots state=stonecutterAt(x,y,z);
        ItemType material=state==null?null:stonecutterMaterial(state.recipe());
        int materialCount=state==null?0:stonecutterMaterialCount(state.recipe());
        if (state==null || state.coal().count()<1 || state.stone().item()!=material || state.stone().count()<materialCount
            || (state.output().item()!=null && state.output().item()!=state.recipe())
                || state.output().count()>=Inventory.MAX_STACK) return false;
        stonecutters.put(key(x,y,z),new MachineSlots(decrement(state.coal()),decrement(state.stone(),materialCount),
            increment(state.output(),state.recipe()),state.recipe()));
        return true;
    }
    public synchronized boolean takeStonecutterOutput(int x, int y, int z, Inventory inventory) {
        MachineSlots state=stonecutterAt(x,y,z);
        if (state==null || state.output().count()<1) return false;
        if (inventory.add(state.output().item(),1)!=0) return false;
        stonecutters.put(key(x,y,z),new MachineSlots(state.coal(),state.stone(),decrement(state.output()),state.recipe()));
        return true;
    }
    public synchronized boolean takeStonecutterItem(int x,int y,int z,int machineSlot,Inventory inventory,int inventorySlot) {
        MachineSlots state=stonecutterAt(x,y,z);
        if (state==null || machineSlot<0 || machineSlot>2) return false;
        Inventory.Slot source=state.at(machineSlot);
        if (source.count()<1 || !inventory.acceptAt(inventorySlot,source.item())) return false;
        Inventory.Slot updated=decrement(source);
        stonecutters.put(key(x,y,z),switch (machineSlot) {
            case 0 -> new MachineSlots(updated,state.stone(),state.output(),state.recipe());
            case 1 -> new MachineSlots(state.coal(),updated,state.output(),state.recipe());
            default -> new MachineSlots(state.coal(),state.stone(),updated,state.recipe());
        });
        return true;
    }
    private static Inventory.Slot decrement(Inventory.Slot slot) {
        return slot.count()==1?new Inventory.Slot(null,0):new Inventory.Slot(slot.item(),slot.count()-1);
    }
    private static Inventory.Slot decrement(Inventory.Slot slot,int amount) {
        return slot.count()==amount?new Inventory.Slot(null,0):new Inventory.Slot(slot.item(),slot.count()-amount);
    }
    private static Inventory.Slot increment(Inventory.Slot slot,ItemType item) {
        return new Inventory.Slot(item,slot.count()+1);
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
        return new Snapshot(seed,Map.copyOf(edits),List.copyOf(doors.values()),Map.copyOf(rotations),
                Map.copyOf(stonecutters),Map.copyOf(furnaces),List.copyOf(sheep),List.copyOf(zombies),dayTime,keepInventory,Map.copyOf(workshops));
    }
    public record Snapshot(long seed, Map<Integer,Block> edits, List<Door> doors, Map<Integer,Integer> rotations,
                           Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                           float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops) {
        public Snapshot { workshops=Map.copyOf(workshops); }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                        float dayTime,boolean keepInventory) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,Map.of());
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations) {
            this(seed,edits,doors,rotations,Map.of(),Map.of(),List.of(),List.of(),.28f,false);
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters) {
            this(seed,edits,doors,rotations,stonecutters,Map.of(),List.of(),List.of(),.28f,false);
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,List.of(),List.of(),.28f,false);
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,List.of(),.28f,false);
        }
    }
    public static World restore(Snapshot saved) {
        World world = new World(saved.seed());
        world.edits.putAll(saved.edits());
        world.blockRevision=saved.edits().size();
        world.rotations.putAll(saved.rotations());
        world.stonecutters.putAll(saved.stonecutters());
        world.furnaces.putAll(saved.furnaces());
        world.workshops.putAll(saved.workshops());
        world.sheep.clear(); world.sheep.addAll(saved.sheep());
        world.zombies.clear(); world.zombies.addAll(saved.zombies());
        world.dayTime=saved.dayTime(); world.keepInventory=saved.keepInventory();
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
            } else if (block.solid() || block == Block.TORCH) return new Hit(x,y,z,nx,ny,nz);
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
