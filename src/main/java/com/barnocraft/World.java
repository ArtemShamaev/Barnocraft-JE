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
    private final Dimension dimension;
    private final List<Portal.Site> portals;
    private final List<HellMob> hellMobs=new ArrayList<>();
    private float hellSpawnClock=25;
    private final int width, depth;
    private final boolean features;
    private final Map<Integer,Block> edits = new HashMap<>();
    private final Map<Integer,Integer> rotations = new HashMap<>();
    private final Map<Integer,Door> doors = new HashMap<>();
    private final Map<Integer,MachineSlots> stonecutters = new HashMap<>();
    private final Map<Integer,FurnaceSlots> furnaces = new HashMap<>();
    private final Map<Integer,ChestData> chests = new HashMap<>();
    private final Map<Integer,Metalworking.State> workshops = new HashMap<>();
    private final LinkedHashMap<Integer,Region> regions = new LinkedHashMap<>(16,.75f,true);
    private final List<DroppedItem> drops = new ArrayList<>();
    private final List<Sheep> sheep = new ArrayList<>();
    private final List<Zombie> zombies = new ArrayList<>();
    private final Map<String,Float> hurtUntil = new HashMap<>();
    final ModWorld mods=new ModWorld(this);
    private final List<Fish> fish = new ArrayList<>();
    private final Map<Integer,Integer> furnaceFuel = new HashMap<>();
    private final Map<Integer,Float> furnaceProgress = new HashMap<>();
    private float sheepClock;
    private float zombieSpawnClock;
    private float fishSpawnClock=5;
    private float dayTime=0.28f;
    private float gameClock;
    private float cropGrowthClock;
    private boolean keepInventory;
    private int blockRevision;
    final WaterSimulation water=new WaterSimulation(this);

    public World() { this(new Random().nextLong()); }
    public World(long seed) { this(seed,Dimension.SURFACE); }
    public World(long seed,Dimension dimension) { this(seed,WIDTH,DEPTH,true,dimension); }
    static World sample(long seed) { return new World(seed,80,80,true); }
    static World flat() { return new World(0,80,80,false); }
    private World(long seed, int width, int depth, boolean features) {
        this(seed,width,depth,features,Dimension.SURFACE);
    }
    private World(long seed,int width,int depth,boolean features,Dimension dimension) {
        this.dimension=dimension;this.portals=features?Portal.sites(seed,width,depth):List.of();
        this.seed = seed; this.width = width; this.depth = depth; this.features = features;
        if (features && dimension==Dimension.SURFACE) { generateDrops(); generateSheep(); }
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
    public synchronized void advanceTime(float tpf) { float dt=Math.max(0,tpf);gameClock+=dt;dayTime=(dayTime+dt/600f)%1f;hurtUntil.values().removeIf(until->until<=gameClock); }
    synchronized void flashHurt(String id) { hurtUntil.put(id,gameClock+.28f); }
    synchronized boolean hurtFlash(String id) { return hurtUntil.getOrDefault(id,0f)>gameClock; }
    public synchronized boolean keepInventory() { return keepInventory; }
    public synchronized void keepInventory(boolean enabled) { keepInventory=enabled; }
    public synchronized void setDayTime(float time) { dayTime=time-(float)Math.floor(time); }
    public synchronized boolean till(int x,int y,int z) {
        if(get(x,y,z)!=Block.GRASS || get(x,y+1,z)!=Block.AIR)return false;
        return set(x,y,z,Block.FARMLAND);
    }
    public synchronized boolean plantCrop(int x,int y,int z) {
        if(get(x,y,z)!=Block.FARMLAND || get(x,y+1,z)!=Block.AIR)return false;
        return set(x,y+1,z,Block.CROP_0);
    }
    public synchronized boolean harvestCrop(int x,int y,int z,Inventory inventory) {
        Block crop=get(x,y,z);if(!crop.crop())return false;
        if(crop==Block.CROP_3) {
            int wheatLeft=inventory.add(ItemType.WHEAT,1);
            if(wheatLeft>0)drop(ItemType.WHEAT,wheatLeft,x,y,z);
            int seedsLeft=inventory.add(ItemType.WHEAT_SEEDS,1+(int)(Math.random()*3));
            if(seedsLeft>0)drop(ItemType.WHEAT_SEEDS,seedsLeft,x,y,z);
        } else if(Math.random()<.35) {
            int seedsLeft=inventory.add(ItemType.WHEAT_SEEDS,1);
            if(seedsLeft>0)drop(ItemType.WHEAT_SEEDS,seedsLeft,x,y,z);
        }
        return set(x,y,z,Block.AIR);
    }
    public synchronized List<int[]> updateCrops(float tpf) {
        cropGrowthClock+=Math.max(0,tpf);List<int[]> grown=new ArrayList<>();
        while(cropGrowthClock>=30f) {
            cropGrowthClock-=30f;
            for(var entry:new ArrayList<>(edits.entrySet())) {
                Block block=entry.getValue();if(!block.crop() || block==Block.CROP_3)continue;
                int key=entry.getKey(),z=key%depth,value=key/depth,x=value%width,y=value/width;
                Block next=Block.values()[block.ordinal()+1];edits.put(key,next);blockRevision++;
                grown.add(new int[]{x,y,z});
            }
        }
        return grown;
    }
    public float daylight() { return Math.max(0,Math.min(1,(float)((Math.sin((dayTime-.25)*Math.PI*2)+.08)/.78))); }
    boolean canSpawnZombie(int x,int y,int z,VoxelLighting lighting) { return get(x,y-1,z).solid() && get(x,y,z)==Block.AIR && lighting.sky(x,y,z)<4; }
    Map<Integer,Metalworking.State> workshops() { return workshops; }
    public synchronized Metalworking.State workshopAt(int x,int y,int z) {
        return null; // Legacy workshop data is read only for save compatibility.
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
        Hit hit=raycast(origin,direction,Player.REACH,item==ItemType.BUCKET); if(hit==null) return null;
        if(item==ItemType.BUCKET && get(hit.x(),hit.y(),hit.z())==Block.WATER) {
            if(!set(hit.x(),hit.y(),hit.z(),Block.AIR)) return null; if(!inventory.creative())inventory.replaceAt(slot,item,ItemType.WATER_BUCKET); return hit;
        }
        if(item==ItemType.WATER_BUCKET) { int x=hit.x()+hit.nx(),y=hit.y()+hit.ny(),z=hit.z()+hit.nz(); if(get(x,y,z)!=Block.AIR || !set(x,y,z,Block.WATER)) return null; if(!inventory.creative())inventory.replaceAt(slot,item,ItemType.BUCKET); return new Hit(x,y,z,0,0,0); }
        return null;
    }
    synchronized void addFish(Fish animal) { fish.add(animal); }
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
        if(dimension!=Dimension.SURFACE)return;
        long local=sheep.stream().filter(animal -> {
            float dx=animal.x()-position.x,dz=animal.z()-position.z;
            return dx*dx+dz*dz<72*72;
        }).count();
        if (local<16) spawnSheepNear(position,(int)(16-local),new Random(seed^Float.floatToIntBits(position.x)^((long)Float.floatToIntBits(position.z)<<32)^sheep.size()),12,64);
    }
    public synchronized void ensureFishNear(Vector3f position) {
        if(dimension!=Dimension.SURFACE)return;
        fish.removeIf(f -> Math.abs(f.x()-position.x)>96 || Math.abs(f.z()-position.z)>96);
        if(fishSpawnClock<5) return;
        fishSpawnClock=0;
        int local=0; for(Fish f:fish) if(Math.abs(f.x()-position.x)<64 && Math.abs(f.z()-position.z)<64) local++;
        if(local<3 && fish.size()<12) spawnFishNear(position,Math.min(3-local,12-fish.size()),new Random(seed^Float.floatToIntBits(position.x)^fish.size()),12,48);
    }

    private void spawnFishNear(Vector3f position,int amount,Random random) { spawnFishNear(position,amount,random,8,28); }
    private void spawnFishNear(Vector3f position,int amount,Random random,int minRadius,int maxRadius) {
        for(int n=0,tries=0;n<amount && tries<amount*40;tries++) {
            double angle=random.nextDouble()*Math.PI*2, radius=minRadius+random.nextDouble()*(maxRadius-minRadius);
            int x=Math.max(1,Math.min(width-2,(int)(position.x+Math.cos(angle)*radius)));
            int z=Math.max(1,Math.min(depth-2,(int)(position.z+Math.sin(angle)*radius)));
            if(get(x,Terrain.WATER_LEVEL,z)!=Block.WATER) continue;
            boolean crowded=false;
            for(Fish f:fish) if((f.x()-x)*(f.x()-x)+(f.z()-z)*(f.z()-z)<64) { crowded=true; break; }
            if(crowded) continue;
            double a=random.nextDouble()*Math.PI*2; fish.add(new Fish(x+.5f,Terrain.WATER_LEVEL+.35f,z+.5f,(float)Math.cos(a),(float)Math.sin(a))); n++;
        }
    }
    public synchronized void updateFish(float tpf) {
        if(tpf<=0)return;
        fishSpawnClock+=tpf;
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
        mods.event("damage",Map.of("source","player","target",original.id(),"amount",1));
        flashHurt(original.id());
        Sheep animal=original.hurt(original.x()-origin.x,original.z()-origin.z);
        if (animal.health()<=0) {
            sheep.remove(hitIndex);
            dropSheep(animal,inventory);
        } else sheep.set(hitIndex,animal);
        return true;
    }
    public synchronized void updateZombies(float tpf,Player player) {
        if(dimension!=Dimension.SURFACE)return;
        if(tpf<=0) return;
        zombies.removeIf(m -> (m.x()-player.position.x)*(m.x()-player.position.x)+(m.z()-player.position.z)*(m.z()-player.position.z)>80*80);
        if(zombies.size()>3) {
            zombies.sort(Comparator.comparingDouble(m -> (m.x()-player.position.x)*(m.x()-player.position.x)+(m.z()-player.position.z)*(m.z()-player.position.z)));
            zombies.subList(3,zombies.size()).clear();
        }
        zombieSpawnClock+=tpf;
        boolean night=dayTime>=.72f || dayTime<.22f;
        if(night && zombieSpawnClock>=60f && zombies.size()<3) {
            zombieSpawnClock=0;
            Random random=new Random(seed^System.nanoTime());
            float angle=random.nextFloat()*FastMath.TWO_PI;
            float radius=24+random.nextFloat()*16;
            int x=Math.max(1,Math.min(width-2,(int)(player.position.x+FastMath.cos(angle)*radius)));
            int z=Math.max(1,Math.min(depth-2,(int)(player.position.z+FastMath.sin(angle)*radius)));
            float y=groundHeight(x,z)+1f;
            if(mobFits(x+.5f,y,z+.5f) && get(x,(int)y,z)==Block.AIR && torchesNear(x,z,12,1).isEmpty())
                zombies.add(new Zombie(x+.5f,y,z+.5f,8,0));
        }
        for(int i=zombies.size()-1;i>=0;i--) {
            Zombie mob=zombies.get(i);
            float remaining=Math.min(tpf,.25f);
            while(remaining>0) {
                float dt=Math.min(remaining,1f/60); remaining-=dt;
                float dx=player.position.x-mob.x(),dz=player.position.z-mob.z();
                float distance=FastMath.sqrt(dx*dx+dz*dz);
                float cooldown=Math.max(0,mob.attackCooldown()-dt);
                float heading=distance>.01f?(float)Math.atan2(dx,dz):mob.heading();
                float x=mob.x(),y=mob.y(),z=mob.z(),vy=mob.velocityY();
                boolean grounded=!mobFits(x,y-.04f,z);
                if(distance<1.15f && Math.abs(player.position.y-y)<1.8f) {
                    if(cooldown==0) { if(player.damage(1)) mods.event("damage",Map.of("source",mob.id(),"target","player","amount",1)); cooldown=1.5f; }
                } else if(distance<48 && distance>.01f) {
                    float mx=dx/distance*dt*2.2f,mz=dz/distance*dt*2.2f;
                    boolean blocked=!mobFits(x+mx,y,z+mz);
                    if(blocked && grounded && mobFits(x,y+1.1f,z) && mobFits(x+mx,y+1.1f,z+mz)) vy=8;
                    if(mobFits(x+mx,y,z)) x+=mx;
                    if(mobFits(x,y,z+mz)) z+=mz;
                }
                vy-=22*dt;
                float nextY=y+vy*dt;
                if(mobFits(x,nextY,z)) y=nextY;
                else {
                    float lo=0,hi=1;
                    for(int n=0;n<12;n++) { float mid=(lo+hi)/2; if(mobFits(x,y+(nextY-y)*mid,z)) lo=mid; else hi=mid; }
                    y+=(nextY-y)*lo; vy=0;
                }
                mob=new Zombie(x,y,z,mob.health(),cooldown,vy,heading,mob.id());
            }
            zombies.set(i,mob);
        }
    }
    synchronized void addZombie(Zombie zombie) { zombies.add(zombie); }
    private boolean mobFits(float x,float y,float z) {
        if(x<.32f || x>=width-.32f || z<.32f || z>=depth-.32f || y<0) return false;
        for(int bx=(int)Math.floor(x-.31f);bx<=(int)Math.floor(x+.31f);bx++)
            for(int bz=(int)Math.floor(z-.31f);bz<=(int)Math.floor(z+.31f);bz++)
                for(int by=(int)Math.floor(y+.001f);by<=(int)Math.floor(y+1.74f);by++)
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
        mods.event("damage",Map.of("source","player","target",zombies.get(found).id(),"amount",1));
        Zombie hurt=zombies.get(found).hurt();
        flashHurt(hurt.id());
        if(hurt.health()<=0) {
            zombies.remove(found);
            drops.add(new DroppedItem(ItemType.ROTTEN_FLESH,(int)hurt.x(),(int)hurt.y(),(int)hurt.z()));
        } else zombies.set(found,hurt);
        return true;
    }
    synchronized boolean damageSheep(String id,int amount,String source) {
        for(int i=0;i<sheep.size();i++) if(sheep.get(i).id().equals(id)) {
            Sheep s=sheep.get(i);for(int n=0;n<amount;n++)s=s.hurt(-s.directionX(),-s.directionZ());
            mods.event("damage",Map.of("source",source,"target",id,"amount",amount));
            flashHurt(id);
            if(s.health()<=0){sheep.remove(i);dropSheep(s,null);}else sheep.set(i,s);return true;
        }
        return false;
    }
    synchronized boolean damageZombie(String id,int amount,String source) {
        for(int i=0;i<zombies.size();i++) if(zombies.get(i).id().equals(id)) {
            Zombie z=zombies.get(i).hurt(amount); mods.event("damage",Map.of("source",source,"target",id,"amount",amount));
            flashHurt(id);
            if(z.health()<=0) { zombies.remove(i); drop(ItemType.ROTTEN_FLESH,1,z.x(),z.y(),z.z()); }
            else zombies.set(i,z); return true;
        }
        return false;
    }
    synchronized void drop(ItemType item,int count,float x,float y,float z) {
        for(int i=0;i<count;i++) drops.add(new DroppedItem(item,(int)x,(int)y,(int)z));
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
            if(entry.getValue()!=Block.TORCH && entry.getValue()!=Block.LANTERN) continue;
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
    public Dimension dimension(){return dimension;}
    List<Portal.Site> portals(){return portals;}
    public long seed() { return seed; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int chunksX() { return width / CHUNK; }
    public int chunksZ() { return depth / CHUNK; }
    public int groundHeight(int x, int z) { return dimension==Dimension.HELL_FOREST?HellTerrain.height(seed,x,z):features ? Terrain.height(seed,x,z) : 5; }
    public boolean desert(int x,int z) { return dimension==Dimension.SURFACE && features && Terrain.desert(seed,x,z); }
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
            result = new Region(seed,rx*Region.WIDTH,rz*Region.DEPTH,width,depth,features,dimension);
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
    private void write(int x, int y, int z, Block block) {
        edits.put(key(x,y,z),block);water.changed(x,y,z);
    }
    public synchronized List<int[]> updateWater(float dt) { return water.update(dt); }
    public synchronized float waterHeight(int x,int y,int z) {
        if(get(x,y,z)!=Block.WATER)return 0;
        return get(x,y+1,z)==Block.WATER?1f:.9f-.1f*water.level(x,y,z);
    }
    synchronized void flowingWater(int x,int y,int z,int level) {
        edits.put(key(x,y,z),level<0?Block.AIR:Block.WATER);
        if(level<0)water.levels.remove(key(x,y,z));else water.levels.put(key(x,y,z),level);
        blockRevision++;
    }

    public synchronized boolean set(int x, int y, int z, Block block) {
        if (!inside(x,y,z) || block.immutable() || get(x,y,z).immutable() || block.door() || block == Block.ROTATOR || get(x,y,z) == block) return false;
        if (get(x,y,z) == Block.STONECUTTER) stonecutters.remove(key(x,y,z));
        if (get(x,y,z) == Block.FURNACE) { furnaces.remove(key(x,y,z)); furnaceFuel.remove(key(x,y,z)); furnaceProgress.remove(key(x,y,z)); }
        if(get(x,y,z)==Block.CHEST) {
            ChestData chest=chests.remove(key(x,y,z));
            if(chest!=null)for(Inventory.Slot slot:chest.slots())if(slot.item()!=null)drop(slot.item(),slot.count(),x,y,z);
        }
        if(get(x,y,z).bed()) {
            int[] offset=bedOffset(rotation(x,y,z));
            int sign=get(x,y,z)==Block.BED?1:-1;
            int bx=x+sign*offset[0],bz=z+sign*offset[1];
            if(get(bx,y,bz).bed()) { write(bx,y,bz,Block.AIR); rotations.remove(key(bx,y,bz)); }
        }
        Door door = doorAt(x,y,z);
        if (door != null) removeDoor(door);
        write(x,y,z,block);
        if(block==Block.CHEST)chests.putIfAbsent(key(x,y,z),ChestData.empty());
        blockRevision++;
        rotations.remove(key(x,y,z));
        if (!block.solid()) {
            if(get(x,y+1,z).bed()) {
                set(x,y+1,z,Block.AIR); drops.add(new DroppedItem(ItemType.BED,x,y+1,z));
            }
            if(get(x,y+1,z)==Block.LANTERN) {
                set(x,y+1,z,Block.AIR); drops.add(new DroppedItem(ItemType.LANTERN,x,y+1,z));
            }
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
    static boolean stonecutterRecipe(ItemType recipe) {
        return recipe==ItemType.AXE_HEAD || recipe==ItemType.PICKAXE_HEAD || recipe==ItemType.SHOVEL_HEAD
                || recipe==ItemType.IRON_AXE_HEAD || recipe==ItemType.IRON_PICKAXE_HEAD
                || recipe==ItemType.IRON_SHOVEL_HEAD || recipe==ItemType.GOLD_AXE_HEAD
                || recipe==ItemType.GOLD_PICKAXE_HEAD || recipe==ItemType.GOLD_SHOVEL_HEAD;
    }
    public static ItemType stonecutterMaterial(ItemType recipe) {
        if(recipe==ItemType.GOLD_AXE_HEAD || recipe==ItemType.GOLD_PICKAXE_HEAD || recipe==ItemType.GOLD_SHOVEL_HEAD) return ItemType.GOLD_INGOT;
        return recipe==ItemType.IRON_AXE_HEAD || recipe==ItemType.IRON_PICKAXE_HEAD || recipe==ItemType.IRON_SHOVEL_HEAD
                ? ItemType.IRON_INGOT : ItemType.STONE;
    }
    public static int stonecutterMaterialCount(ItemType recipe) {
        return stonecutterMaterial(recipe)==ItemType.STONE?1:5;
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
    public record ChestData(List<Inventory.Slot> slots,boolean deathCache,boolean opened) {
        public ChestData { slots=List.copyOf(slots);if(slots.size()!=27)throw new IllegalArgumentException("Chest needs 27 slots"); }
        static ChestData empty(){return new ChestData(Collections.nCopies(27,new Inventory.Slot(null,0)),false,false);}
    }
    public synchronized ChestData chestAt(int x,int y,int z) {
        if(get(x,y,z)!=Block.CHEST)return null;
        return chests.getOrDefault(key(x,y,z),ChestData.empty());
    }
    public synchronized Map<Integer,ChestData> chests(){return Map.copyOf(chests);}
    public synchronized boolean openChest(int x,int y,int z) {
        ChestData chest=chestAt(x,y,z);if(chest==null || chest.deathCache()&&chest.opened())return false;
        if(chest.deathCache())chests.put(key(x,y,z),new ChestData(chest.slots(),true,true));
        return true;
    }
    public synchronized boolean moveChestItem(int x,int y,int z,int chestSlot,Inventory inventory,int inventorySlot,boolean chestToInventory,boolean one) {
        ChestData chest=chestAt(x,y,z);if(chest==null || chestSlot<0 || chestSlot>=27)return false;
        List<Inventory.Slot> slots=new ArrayList<>(chest.slots());Inventory.Slot source=slots.get(chestSlot);
        if(chestToInventory) {
            if(source.item()==null)return false;
            int amount=one?1:source.count(),left=inventory.add(source.item(),amount),moved=amount-left;
            if(moved<=0)return false;
            slots.set(chestSlot,moved==source.count()?new Inventory.Slot(null,0):new Inventory.Slot(source.item(),source.count()-moved,source.durability()));
        } else {
            Inventory.Slot target=inventory.slotAtIndex(inventorySlot);
            if(target==null || target.item()==null)return false;
            if(one) {
                if(source.item()!=null && (source.item()!=target.item() || source.count()>=source.item().stackLimit()))return false;
                slots.set(chestSlot,new Inventory.Slot(target.item(),(source.item()==null?0:source.count())+1));inventory.consumeAt(inventorySlot,target.item());
            } else {
                if(source.item()!=null && (source.item()!=target.item() || source.item().tool()))return false;
                int current=source.item()==null?0:source.count();int moved=Math.min(target.count(),target.item().stackLimit()-current);if(moved<=0)return false;
                slots.set(chestSlot,new Inventory.Slot(target.item(),current+moved));inventory.take(target.item(),moved);
            }
        }
        chests.put(key(x,y,z),new ChestData(slots,chest.deathCache(),chest.opened()));return true;
    }
    public synchronized void closeChest(int x,int y,int z) {
        ChestData chest=chestAt(x,y,z);if(chest==null || !chest.deathCache())return;
        chests.remove(key(x,y,z));write(x,y,z,Block.AIR);blockRevision++;
        for(Inventory.Slot slot:chest.slots())if(slot.item()!=null)drop(slot.item(),slot.count(),x,y,z);
    }
    public synchronized void createDeathChest(Inventory inventory,Vector3f position) {
        int cx=Math.max(1,Math.min(width-2,(int)Math.floor(position.x)));
        int cz=Math.max(1,Math.min(depth-2,(int)Math.floor(position.z)));
        int cy=groundHeight(cx,cz)+1;boolean found=false;
        for(int radius=0;radius<8&&!found;radius++)for(int dx=-radius;dx<=radius&&!found;dx++)for(int dz=-radius;dz<=radius&&!found;dz++) {
            int x=cx+dx,z=cz+dz;if(!inside(x,cy,z)||get(x,cy,z)!=Block.AIR||!get(x,cy-1,z).solid())continue;
            cx=x;cz=z;found=true;
        }
        if(!found)return;
        List<Inventory.Slot> contents=new ArrayList<>();
        for(int i=0;i<27;i++) {
            if(i<Inventory.SLOT_COUNT)contents.add(inventory.slotAtIndex(i));
            else if(i<Inventory.SLOT_COUNT+Inventory.CRAFT_SIZE)contents.add(inventory.craftSlotAt(i-Inventory.SLOT_COUNT));
            else contents.add(new Inventory.Slot(null,0));
        }
        inventory.clear();write(cx,cy,cz,Block.CHEST);chests.put(key(cx,cy,cz),new ChestData(contents,true,false));blockRevision++;
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
        if (machineSlot==1 && smeltingResult(sourceType)==null) return false;
        if (source==null || source.item()!=required || (current.item()!=null && current.item()!=required)
                || current.count()>=Inventory.MAX_STACK || !inventory.consumeAt(inventorySlot,required)) return false;
        Inventory.Slot updated=new Inventory.Slot(required,current.count()+1);
        furnaces.put(key(x,y,z),machineSlot==0
            ? new FurnaceSlots(updated,state.input(),state.output())
            : new FurnaceSlots(state.coal(),updated,state.output()));
        return true;
    }
    /** Furnace processing runs from the game tick; no button press is needed. */
    public synchronized void updateFurnaces(float tpf) {
        for (Integer location : new ArrayList<>(furnaces.keySet())) {
            int z=location%depth, value=location/depth, x=value%width, y=value/width;
            FurnaceSlots state=furnaceAt(x,y,z);
            ItemType result=smeltingResult(state.input().item());
            boolean hasFuel=furnaceFuel.getOrDefault(location,0)>0 || state.coal().count()>0;
            boolean canCook=state.input().count()>0 && result!=null
                    && (state.output().item()==null || state.output().item()==result)
                    && state.output().count()<Inventory.MAX_STACK;
            if(!hasFuel || !canCook){furnaceProgress.remove(location);continue;}
            float progress=furnaceProgress.getOrDefault(location,0f)+Math.max(0,tpf);
            if(progress>=2f){if(smeltIron(x,y,z))progress-=2f;else progress=0;}
            furnaceProgress.put(location,Math.min(progress,2f));
        }
    }
    static ItemType smeltingResult(ItemType input) {
        if(input==null) return null;
        String custom=input.cookingResult();if(!custom.isEmpty())return ItemType.valueOf(custom);
        return switch (input.name()) { case "IRON" -> ItemType.IRON_INGOT; case "GOLD" -> ItemType.GOLD_INGOT; case "SAND" -> ItemType.GLASS; default -> null; };
    }
    public synchronized float furnaceProgress(int x,int y,int z){return furnaceProgress.getOrDefault(key(x,y,z),0f)/2f;}
    public synchronized boolean smeltIron(int x,int y,int z) {
        FurnaceSlots state=furnaceAt(x,y,z);
        ItemType result=state==null?null:smeltingResult(state.input().item());
        int furnaceKey=key(x,y,z), fuel=furnaceFuel.getOrDefault(furnaceKey,0);
        if (state==null || (fuel<=0 && state.coal().count()<1) || state.input().count()<1
            || result==null
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
    private static int[] bedOffset(int facing) {
        return switch(Math.floorMod(facing,4)) { case 0 -> new int[]{0,1}; case 1 -> new int[]{-1,0}; case 2 -> new int[]{0,-1}; default -> new int[]{1,0}; };
    }
    public synchronized boolean placeBed(int x,int y,int z,int facing,Player player) {
        int[] d=bedOffset(facing); int hx=x+d[0],hz=z+d[1];
        if(!inside(x,y,z) || !inside(hx,y,hz) || get(x,y,z)!=Block.AIR || get(hx,y,hz)!=Block.AIR
                || !get(x,y-1,z).solid() || !get(hx,y-1,hz).solid()
                || player.intersects(x,y,z) || player.intersects(hx,y,hz)) return false;
        write(x,y,z,Block.BED); write(hx,y,hz,Block.BED_HEAD);
        orientation(x,y,z,facing); orientation(hx,y,hz,facing); blockRevision++; return true;
    }
    public synchronized Vector3f bedSpawn(int x,int y,int z) {
        if(!get(x,y,z).bed())return null;
        int baseX=x,baseZ=z;
        if(get(x,y,z)==Block.BED_HEAD){int[] offset=bedOffset(rotation(x,y,z));baseX-=offset[0];baseZ-=offset[1];}
        return new Vector3f(baseX+.5f,y+.61f,baseZ+.5f);
    }
    public synchronized boolean validBedSpawn(float x,float y,float z) {
        return inside((int)x,(int)y,(int)z)&&get((int)x,(int)y,(int)z).bed();
    }
    public synchronized String sleepAt(int x,int y,int z) {
        if(!get(x,y,z).bed()) return "Здесь нет кровати";
        if(dayTime>=.22f && dayTime<.72f) return "Спать можно только ночью";
        for(Zombie mob:zombies) if(Math.abs(mob.x()-x)<8 && Math.abs(mob.z()-z)<8 && Math.abs(mob.y()-y)<4)
            return "Рядом зомби — спать небезопасно";
        dayTime=.28f; zombieSpawnClock=0; zombies.clear(); return "Доброе утро!";
    }
    public synchronized boolean placeStairs(int x, int y, int z, int facing, Player player) {
        if (!inside(x,y,z) || get(x,y,z)!=Block.AIR
                || Stairs.bounds(x,y,z,facing).stream().anyMatch(player::intersects)) return false;
        write(x,y,z,Block.STAIRS); orientation(x,y,z,facing); return true;
    }
    public synchronized boolean rotate(int x, int y, int z, Player player) {
        if(get(x,y,z).immutable())return false;
        Block block = get(x,y,z);
        if(block.bed()) {
            int oldFacing=rotation(x,y,z); int[] old=bedOffset(oldFacing);
            if(block==Block.BED_HEAD) { x-=old[0]; z-=old[1]; }
            int nextFacing=(oldFacing+1)&3; int[] next=bedOffset(nextFacing);
            int hx=x+next[0],hz=z+next[1];
            if(!inside(hx,y,hz) || get(hx,y,hz)!=Block.AIR || !get(hx,y-1,hz).solid()
                    || player.intersects(x,y,z) || player.intersects(hx,y,hz)) return false;
            write(x+old[0],y,z+old[1],Block.AIR); rotations.remove(key(x+old[0],y,z+old[1]));
            write(hx,y,hz,Block.BED_HEAD); orientation(x,y,z,nextFacing); orientation(hx,y,hz,nextFacing);
            blockRevision++; return true;
        }
        if (!block.solid()) return false;
        Door door = doorAt(x,y,z);
        if (door!=null) {
            Door next = new Door(door.x(),door.y(),door.z(),(door.facing()+1)&3,door.open());
            if (player.intersects(next.bounds())) return false;
            doors.put(key(door.x(),door.y(),door.z()),next); blockRevision++; return true;
        }
        int facing = (rotation(x,y,z)+1)&3;
        if (block==Block.STAIRS && Stairs.bounds(x,y,z,facing).stream().anyMatch(player::intersects)) return false;
        // Capture natural blocks too: rotation is a persistent player edit.
        write(x,y,z,block); orientation(x,y,z,facing); blockRevision++; return true;
    }
    public synchronized Snapshot snapshot() {
        return new Snapshot(seed,Map.copyOf(edits),List.copyOf(doors.values()),Map.copyOf(rotations),
            Map.copyOf(stonecutters),Map.copyOf(furnaces),List.copyOf(sheep),List.copyOf(zombies),dayTime,keepInventory,Map.copyOf(workshops),mods.save(),Map.copyOf(chests),Map.copyOf(water.levels),dimension,List.copyOf(hellMobs));
    }
    public record Snapshot(long seed, Map<Integer,Block> edits, List<Door> doors, Map<Integer,Integer> rotations,
                           Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                           float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops,String modData,Map<Integer,ChestData> chests,Map<Integer,Integer> waterLevels,Dimension dimension,List<HellMob> hellMobs) {
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                        float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops,String modData,Map<Integer,ChestData> chests,Map<Integer,Integer> waterLevels) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,workshops,modData,chests,waterLevels,Dimension.SURFACE,List.of());
        }
        public Snapshot { hellMobs=List.copyOf(hellMobs);Objects.requireNonNull(dimension);workshops=Map.copyOf(workshops);chests=Map.copyOf(chests);waterLevels=Map.copyOf(waterLevels); }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                        float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops,String modData,Map<Integer,ChestData> chests) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,workshops,modData,chests,Map.of());
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                        float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops,String modData) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,workshops,modData,Map.of());
        }
        public Snapshot(long seed,Map<Integer,Block> edits,List<Door> doors,Map<Integer,Integer> rotations,
                        Map<Integer,MachineSlots> stonecutters,Map<Integer,FurnaceSlots> furnaces,List<Sheep> sheep,List<Zombie> zombies,
                        float dayTime,boolean keepInventory,Map<Integer,Metalworking.State> workshops) {
            this(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,workshops,"{}");
        }
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
    synchronized void applyNetworkEntities(List<DroppedItem> items,List<Fish> animals) { drops.clear();drops.addAll(items);fish.clear();fish.addAll(animals); }

    /** Apply an authoritative server snapshot without rebuilding the world or its region cache. */
    synchronized Set<Integer> applyNetwork(Snapshot state) {
        if(state.seed()!=seed || state.dimension()!=dimension)throw new IllegalArgumentException("Server world changed");
        Set<Integer> changed=new HashSet<>();
        Set<Integer> keys=new HashSet<>(edits.keySet());keys.addAll(state.edits().keySet());
        for(int key:keys)if(!Objects.equals(edits.get(key),state.edits().get(key))
                || !Objects.equals(rotations.get(key),state.rotations().get(key))
                || !Objects.equals(water.levels.get(key),state.waterLevels().get(key)))changed.add(key);
        Map<Integer,Door> nextDoors=new HashMap<>();
        for(Door door:state.doors())nextDoors.put(key(door.x(),door.y(),door.z()),door);
        Set<Integer> doorKeys=new HashSet<>(doors.keySet());doorKeys.addAll(nextDoors.keySet());
        for(int key:doorKeys)if(!Objects.equals(doors.get(key),nextDoors.get(key)))changed.add(key);
        edits.clear();edits.putAll(state.edits());rotations.clear();rotations.putAll(state.rotations());
        doors.clear();doors.putAll(nextDoors);water.levels.clear();water.levels.putAll(state.waterLevels());
        stonecutters.clear();stonecutters.putAll(state.stonecutters());furnaces.clear();furnaces.putAll(state.furnaces());
        chests.clear();chests.putAll(state.chests());
        sheep.clear();sheep.addAll(state.sheep());zombies.clear();zombies.addAll(state.zombies());
        hellMobs.clear();hellMobs.addAll(state.hellMobs());
        dayTime=state.dayTime();keepInventory=state.keepInventory();
        if(!changed.isEmpty())blockRevision++;
        return changed;
    }

    public static World restore(Snapshot saved) {
        World world = new World(saved.seed(),saved.dimension());
        world.edits.putAll(saved.edits());
        world.blockRevision=saved.edits().size();
        world.rotations.putAll(saved.rotations());
        world.stonecutters.putAll(saved.stonecutters());
        world.furnaces.putAll(saved.furnaces());
        world.chests.putAll(saved.chests());
        world.water.levels.putAll(saved.waterLevels());
        for(var entry:saved.edits().entrySet())if((entry.getValue()==Block.WATER || entry.getValue()==Block.AIR)) {
            int k=entry.getKey(),z=k%world.depth,v=k/world.depth,x=v%world.width,y=v/world.width;
            world.water.wake(x,y,z);
        }
        // Retire workshop blocks, preserving their stored contents as drops.
        for(var entry:saved.workshops().entrySet()) {
            int k=entry.getKey(),z=k%world.depth,t=k/world.depth,x=t%world.width,y=t/world.width;
            for(Inventory.Slot slot:entry.getValue().slots()) if(slot.item()!=null && !slot.item().retired())
                for(int i=0;i<slot.count();i++) world.drops.add(new DroppedItem(slot.item(),x,y,z));
        }
        world.edits.replaceAll((k,b) -> b==Block.CASTING_TABLE || b==Block.WELDER?Block.AIR:b==Block.TORCH?Block.LANTERN:b);
        world.rotations.keySet().removeIf(k -> world.edits.get(k)==Block.AIR);
        world.sheep.clear(); world.sheep.addAll(saved.sheep());
        world.zombies.clear(); world.zombies.addAll(saved.zombies());
        world.dayTime=saved.dayTime(); world.keepInventory=saved.keepInventory();
        for (Door door : saved.doors()) world.doors.put(world.key(door.x(),door.y(),door.z()),door);
        world.hellMobs.addAll(saved.hellMobs());
        world.mods.restore(saved.modData());
        return world;
    }
    public synchronized List<HellMob> hellMobs(){return List.copyOf(hellMobs);}
    synchronized void addHellMob(HellMob mob){hellMobs.add(mob);}
    synchronized void updateHellMobs(float dt,List<Player> players) {
        if(dimension!=Dimension.HELL_FOREST || players.isEmpty())return;
        // Retire distant creatures and trim populations from older saves too.
        hellMobs.removeIf(m->players.stream().allMatch(p->hellDistanceSquared(m,p)>80*80));
        if(hellMobs.size()>4)hellMobs.subList(4,hellMobs.size()).clear();
        hellSpawnClock-=Math.max(0,dt);
        if(hellSpawnClock<=0) {
            hellSpawnClock=25;
            if(hellMobs.size()<4) {
                Random random=new Random(seed^Float.floatToIntBits(dayTime)^hellMobs.size());
                Player player=players.get(random.nextInt(players.size()));
                for(int attempt=0;attempt<8;attempt++) {
                    double angle=random.nextDouble()*Math.PI*2;
                    int radius=24+random.nextInt(13);
                    int x=(int)(player.position.x+Math.cos(angle)*radius),z=(int)(player.position.z+Math.sin(angle)*radius);
                    if(portals.stream().anyMatch(p->(p.x()-x)*(p.x()-x)+(p.z()-z)*(p.z()-z)<20*20))continue;
                    if(players.stream().anyMatch(p->{float dx=p.position.x-x,dz=p.position.z-z;return dx*dx+dz*dz<24*24;}))continue;
                    int y=groundHeight(x,z)+1;
                    if(inside(x,y+2,z) && get(x,y,z)==Block.AIR && get(x,y+1,z)==Block.AIR && get(x,y+2,z)==Block.AIR
                            && get(x,y-1,z).solid() && y>HellTerrain.LAVA_LEVEL) {
                        HellMob.Kind kind=random.nextBoolean()?HellMob.Kind.IMP:HellMob.Kind.BOAR;
                        hellMobs.add(HellMob.spawn(kind,x+.5f,y+(kind==HellMob.Kind.IMP?1.2f:0),z+.5f));
                        break;
                    }
                }
            }
        }
        for(int i=0;i<hellMobs.size();i++) {
            HellMob mob=hellMobs.get(i);
            Player nearest=players.stream().min(Comparator.comparingDouble(p->p.position.distanceSquared(new Vector3f(mob.x(),mob.y(),mob.z())))).orElseThrow();
            hellMobs.set(i,mob.tick(this,Math.min(dt,.1f),nearest));
        }
    }
    private static float hellDistanceSquared(HellMob mob,Player player) {
        float dx=mob.x()-player.position.x,dz=mob.z()-player.position.z;return dx*dx+dz*dz;
    }
    synchronized boolean attackHellMob(Vector3f origin,Vector3f direction,float reach,Inventory inventory) {
        Hit wall=raycast(origin,direction,reach);float limit=wall==null?reach:origin.distance(new Vector3f(wall.x()+.5f,wall.y()+.5f,wall.z()+.5f));
        int found=-1;float best=limit;
        for(int i=0;i<hellMobs.size();i++) {
            HellMob mob=hellMobs.get(i);float r=mob.kind()==HellMob.Kind.IMP?.35f:.55f;
            Door.Contact hit=new Door.Bounds(mob.x()-r,mob.y(),mob.z()-r,mob.x()+r,mob.y()+1.2f,mob.z()+r).ray(origin,direction,reach);
            if(hit!=null && hit.distance()<best){best=hit.distance();found=i;}
        }
        if(found<0)return false;
        HellMob mob=hellMobs.get(found);int health=mob.health()-2;
        if(health<=0){hellMobs.remove(found);inventory.add(mob.kind()==HellMob.Kind.IMP?ItemType.EMBER_CRYSTAL:ItemType.COAL,2);}
        else hellMobs.set(found,new HellMob(mob.id(),mob.kind(),mob.x(),mob.y(),mob.z(),health,mob.heading(),mob.cooldown()));
        return true;
    }

    public record Hit(int x, int y, int z, int nx, int ny, int nz) {}

    /** Amanatides-Woo traversal. Explicit infinity avoids 0 * infinity on grid planes. */
    public Hit raycast(Vector3f origin, Vector3f direction, float reach) {
        return raycast(origin,direction,reach,false);
    }
    private Hit raycast(Vector3f origin, Vector3f direction, float reach, boolean includeWater) {
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
            } else if(block.bed() || block==Block.LANTERN) {
                Door.Bounds bounds=block.bed()?new Door.Bounds(x,y,z,x+1,y+.6f,z+1)
                        :new Door.Bounds(x+.28f,y,z+.28f,x+.72f,y+.83f,z+.72f);
                Door.Contact contact=bounds.ray(origin,d,reach);
                if(contact!=null && contact.distance()+.0001>=distance && contact.distance()<=Math.min(tx,Math.min(ty,tz))+.0001)
                    return new Hit(x,y,z,contact.nx(),contact.ny(),contact.nz());
            } else if(block.crop()) {
                float cropHeight=.18f+block.cropStage()*.18f;
                Door.Contact contact=new Door.Bounds(x+.28f,y,z+.28f,x+.72f,y+cropHeight+.12f,z+.72f).ray(origin,d,reach);
                if(contact!=null && contact.distance()+.0001>=distance && contact.distance()<=Math.min(tx,Math.min(ty,tz))+.0001)
                    return new Hit(x,y,z,contact.nx(),contact.ny(),contact.nz());
            } else if (block.solid() || block == Block.TORCH || block == Block.LANTERN || includeWater && block == Block.WATER) return new Hit(x,y,z,nx,ny,nz);
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
