package com.barnocraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Five independent, versioned saves. Write a temporary file before atomically replacing a slot. */
public final class SaveStore {
    // VERSION covers both the file schema and the procedural generator used by stored seeds.
    private static final int MAGIC = 0x4241524E, VERSION = 14;
    private final Path directory;
    public SaveStore(Path directory) { this.directory = directory; }
    public record Pose(float x, float y, float z, float yaw, float pitch, int selected,int health) {
        public Pose(float x,float y,float z,float yaw,float pitch,int selected) { this(x,y,z,yaw,pitch,selected,Player.MAX_HEALTH); }
    }
    public record Saved(World world, Pose pose, Inventory.Snapshot inventory,String worldName) {}
    public Path slotPath(int slot) {
        if (slot < 1 || slot > 5) throw new IllegalArgumentException("World slot must be 1–5");
        return directory.resolve("world-" + slot + ".dat");
    }
    public boolean exists(int slot) { return Files.exists(slotPath(slot)); }
    public boolean delete(int slot) throws IOException { return Files.deleteIfExists(slotPath(slot)); }
    public String worldName(int slot) {
        if (!exists(slot)) return "Мир "+slot;
        try (var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(slotPath(slot))))) {
            if(in.readInt()!=MAGIC) return "Мир "+slot;
            int version=in.readInt();
            return version>=10?in.readUTF():"Мир "+slot;
        } catch(IOException e) { return "Мир "+slot; }
    }
    public void save(int slot, World.Snapshot world, Pose pose) throws IOException {
        save(slot,world,pose,Inventory.emptySnapshot(),"Мир "+slot);
    }
    public void save(int slot, World.Snapshot world, Pose pose, Inventory.Snapshot inventory) throws IOException {
        save(slot,world,pose,inventory,"Мир "+slot);
    }
    public void save(int slot, World.Snapshot world, Pose pose, Inventory.Snapshot inventory,String worldName) throws IOException {
        String safeName=worldName==null?"Мир "+slot:worldName.strip();
        if(safeName.isEmpty() || safeName.length()>32) throw new IllegalArgumentException("Имя мира должно быть от 1 до 32 символов");
        Files.createDirectories(directory);
        Path target = slotPath(slot), temp = Files.createTempFile(directory,"world-"+slot+"-",".tmp");
        try {
            try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeUTF(safeName);
                out.writeFloat(world.dayTime()); out.writeBoolean(world.keepInventory()); out.writeLong(world.seed());
                out.writeFloat(pose.x()); out.writeFloat(pose.y()); out.writeFloat(pose.z());
                out.writeFloat(pose.yaw()); out.writeFloat(pose.pitch()); out.writeInt(pose.selected()); out.writeInt(pose.health());
                out.writeInt(world.edits().size());
                for (var entry : world.edits().entrySet()) { out.writeInt(entry.getKey()); out.writeUTF(entry.getValue().name()); }
                out.writeInt(world.doors().size());
                for (Door door : world.doors()) {
                    out.writeInt(door.x()); out.writeInt(door.y()); out.writeInt(door.z());
                    out.writeInt(door.facing()); out.writeBoolean(door.open());
                }
                out.writeInt(world.rotations().size());
                for (var entry : world.rotations().entrySet()) {
                    out.writeInt(entry.getKey()); out.writeByte(entry.getValue());
                }
                writeInventory(out, inventory);
                out.writeInt(world.stonecutters().size());
                for (var entry : world.stonecutters().entrySet()) {
                    out.writeInt(entry.getKey());
                    writeSlot(out,entry.getValue().coal());
                    writeSlot(out,entry.getValue().stone());
                    writeSlot(out,entry.getValue().output());
                    out.writeUTF(entry.getValue().recipe().name());
                }
                out.writeInt(world.furnaces().size());
                for (var entry : world.furnaces().entrySet()) {
                    out.writeInt(entry.getKey());
                    writeSlot(out,entry.getValue().coal());
                    writeSlot(out,entry.getValue().input());
                    writeSlot(out,entry.getValue().output());
                }
                out.writeInt(world.sheep().size());
                for (Sheep sheep:world.sheep()) {
                    out.writeFloat(sheep.x()); out.writeFloat(sheep.y()); out.writeFloat(sheep.z()); out.writeInt(sheep.health());
                    out.writeFloat(sheep.directionX()); out.writeFloat(sheep.directionZ()); out.writeFloat(sheep.fleeTime());
                }
                out.writeInt(world.workshops().size());
                for(var entry:world.workshops().entrySet()) {
                    out.writeInt(entry.getKey()); out.writeUTF(entry.getValue().recipe().name());
                    for(Inventory.Slot item:entry.getValue().slots()) writeSlot(out,item);
                }
                out.writeInt(world.zombies().size());
                for(Zombie zombie:world.zombies()) {
                    out.writeFloat(zombie.x()); out.writeFloat(zombie.y()); out.writeFloat(zombie.z());
                    out.writeInt(zombie.health()); out.writeFloat(zombie.attackCooldown());
                }
            }
            replace(temp,target);
        } finally { Files.deleteIfExists(temp); }
    }
    static void replace(Path temp, Path target) throws IOException {
        try { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
    }
    public Saved load(int slot) throws IOException {
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(slotPath(slot))))) {
            if (in.readInt() != MAGIC) throw new IOException("Неизвестный формат мира");
            int version=in.readInt();
            if (version<1 || version>VERSION) throw new IOException("Неизвестная версия мира");
            String worldName=version>=10?in.readUTF():"Мир "+slot;
            float dayTime=version>=12?in.readFloat():.28f;
            boolean keepInventory=version>=12 && in.readBoolean();
            if(!Float.isFinite(dayTime) || dayTime<0 || dayTime>=1) throw new IOException("Некорректное время суток");
            long seed = in.readLong();
            float px=in.readFloat(),py=in.readFloat(),pz=in.readFloat(),yaw=in.readFloat(),pitch=in.readFloat();
            int selected=in.readInt(),health=version>=12?in.readInt():Player.MAX_HEALTH;
            Pose pose = new Pose(px,py,pz,yaw,pitch,selected,health);
            if (!Float.isFinite(pose.x()+pose.y()+pose.z()+pose.yaw()+pose.pitch())
                    || pose.x() < 0 || pose.x() >= World.WIDTH || pose.z() < 0 || pose.z() >= World.DEPTH
                    || pose.y() < -100 || pose.y() > World.HEIGHT+100 || Math.abs(pose.pitch()) > Math.PI/2
                    || pose.selected() < 0 || pose.selected() >= Block.HOTBAR.length
                    || pose.health()<0 || pose.health()>Player.MAX_HEALTH) throw new IOException("Некорректная позиция игрока");
            int count = in.readInt(), max = World.WIDTH * World.DEPTH * World.HEIGHT;
            if (count < 0 || count > max) throw new IOException("Некорректный размер сохранения");
            Map<Integer,Block> edits = new HashMap<>();
            for (int i = 0; i < count; i++) {
                int key = in.readInt(); Block block = Block.valueOf(in.readUTF());
                if (key < 0 || key >= max || block==Block.ROTATOR || edits.put(key,block) != null) throw new IOException("Некорректный блок");
            }
            int doorCount = in.readInt();
            if (doorCount < 0 || doorCount > count/2) throw new IOException("Некорректное число дверей");
            List<Door> doors = new ArrayList<>(); Set<Integer> bases = new HashSet<>();
            for (int i = 0; i < doorCount; i++) {
                Door door = new Door(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readBoolean());
                if (door.x()<0 || door.x()>=World.WIDTH || door.z()<0 || door.z()>=World.DEPTH
                        || door.y()<1 || door.y()+1>=World.HEIGHT || door.facing()<0 || door.facing()>3)
                    throw new IOException("Некорректная дверь");
                int key = (door.y()*World.WIDTH+door.x())*World.DEPTH+door.z();
                if (!bases.add(key) || edits.get(key)!=Block.DOOR || edits.get(key+World.WIDTH*World.DEPTH)!=Block.DOOR_TOP)
                    throw new IOException("Повреждённая дверь");
                doors.add(door);
            }
            Map<Integer,Integer> rotations=new HashMap<>();
            if (version>=2) {
                int rotationCount=in.readInt();
                if (rotationCount<0 || rotationCount>count) throw new IOException("Некорректное число поворотов");
                for (int i=0;i<rotationCount;i++) {
                    int key=in.readInt(), turn=in.readUnsignedByte();
                    Block block=edits.get(key);
                    if (block==null || !block.solid() || block.door() || turn<1 || turn>3 || rotations.put(key,turn)!=null)
                        throw new IOException("Некорректная ориентация блока");
                }
            }
            Inventory.Snapshot inventory = version >= 3 ? readInventory(in,version>=7) : Inventory.emptySnapshot();
            Map<Integer,World.MachineSlots> stonecutters = new HashMap<>();
            if (version >= 4) {
                int machineCount=in.readInt();
                if (machineCount<0 || machineCount>count) throw new IOException("Некорректное число камнерезов");
                for (int i=0;i<machineCount;i++) {
                    int key=in.readInt();
                    Inventory.Slot coal=readSlot(in,version>=7), stone=readSlot(in,version>=7), output=readSlot(in,version>=7);
                        ItemType recipe=version>=5?ItemType.valueOf(in.readUTF()):ItemType.AXE_HEAD;
                    if (key<0 || key>=max || edits.get(key)!=Block.STONECUTTER
                            || !validMachineSlot(coal,ItemType.COAL) || !validMachineSlot(stone,World.stonecutterMaterial(recipe))
                            || !validMachineSlot(output,recipe) || !validMachineRecipe(recipe)
                            || stonecutters.put(key,new World.MachineSlots(coal,stone,output,recipe))!=null) {
                        throw new IOException("Повреждённый инвентарь камнереза");
                    }
                }
            }
            Map<Integer,World.FurnaceSlots> furnaces=new HashMap<>();
            if (version>=6) {
                int furnaceCount=in.readInt();
                if (furnaceCount<0 || furnaceCount>count) throw new IOException("Некорректное число печей");
                for (int i=0;i<furnaceCount;i++) {
                    int key=in.readInt();
                        Inventory.Slot coal=readSlot(in,version>=7), input=readSlot(in,version>=7), output=readSlot(in,version>=7);
                    if (key<0 || key>=max || edits.get(key)!=Block.FURNACE
                            || !validFurnaceSlots(coal,input,output)
                            || furnaces.put(key,new World.FurnaceSlots(coal,input,output))!=null) {
                        throw new IOException("Повреждённые слоты печи");
                    }
                }
            }
            List<Sheep> sheep=new ArrayList<>();
            if (version>=8) {
                int sheepCount=in.readInt();
                if (sheepCount<0 || sheepCount>10000) throw new IOException("Некорректное число овец");
                for (int i=0;i<sheepCount;i++) {
                    float x=in.readFloat(),y=in.readFloat(),z=in.readFloat(); int sheepHealth=in.readInt();
                    float directionX=version>=11?in.readFloat():1f;
                    float directionZ=version>=11?in.readFloat():0f;
                    float fleeTime=version>=11?in.readFloat():0f;
                    Sheep animal=new Sheep(x,y,z,sheepHealth,directionX,directionZ,fleeTime);
                    if (!Float.isFinite(animal.x()+animal.y()+animal.z()) || animal.x()<0 || animal.x()>=World.WIDTH
                            || animal.y()<0 || animal.y()>World.HEIGHT+4 || animal.z()<0 || animal.z()>=World.DEPTH
                        || animal.health()<1 || animal.health()>3 || !Float.isFinite(directionX+directionZ+fleeTime)
                        || fleeTime<0 || fleeTime>4.1f) throw new IOException("Некорректная овца");
                    sheep.add(animal);
                }
            }
            List<Zombie> zombies=new ArrayList<>();
            if(version>=13) {
                int zombieCount=in.readInt();
                if(zombieCount<0 || zombieCount>10000) throw new IOException("Некорректное число зомби");
                for(int i=0;i<zombieCount;i++) {
                    Zombie zombie=new Zombie(in.readFloat(),in.readFloat(),in.readFloat(),in.readInt(),in.readFloat());
                    if(!Float.isFinite(zombie.x()+zombie.y()+zombie.z()+zombie.attackCooldown())
                            || zombie.x()<0 || zombie.x()>=World.WIDTH || zombie.y()<0 || zombie.y()>World.HEIGHT
                            || zombie.z()<0 || zombie.z()>=World.DEPTH || zombie.health()<1 || zombie.health()>3
                            || zombie.attackCooldown()<0 || zombie.attackCooldown()>1.1f) throw new IOException("Некорректный зомби");
                    zombies.add(zombie);
                }
            }
            Map<Integer,Metalworking.State> workshops=new HashMap<>();
            if(version>=14) {
                int workshopCount=in.readInt();
                if(workshopCount<0 || workshopCount>count) throw new IOException("Некорректное число станков");
                for(int i=0;i<workshopCount;i++) {
                    int key=in.readInt(); ItemType recipe=ItemType.valueOf(in.readUTF());
                    List<Inventory.Slot> slots=new ArrayList<>();
                    for(int j=0;j<11;j++) slots.add(readSlot(in,true));
                    Block block=edits.get(key);
                    if(key<0 || key>=max || (block!=Block.CASTING_TABLE && block!=Block.WELDER))
                        throw new IOException("Повреждённый станок");
                    workshops.put(key,new Metalworking.State(block,slots,recipe));
                }
            }
            if (edits.values().stream().filter(Block::door).count() != doorCount*2L || in.read()!=-1)
                throw new IOException("Повреждённое сохранение");
            World.Snapshot snapshot=new World.Snapshot(seed,edits,doors,rotations,stonecutters,furnaces,sheep,zombies,dayTime,keepInventory,workshops);
            return new Saved(World.restore(snapshot),pose,inventory,worldName);
        } catch (IllegalArgumentException e) { throw new IOException("Неизвестный блок в сохранении",e); }
    }

    private static void writeInventory(DataOutputStream out, Inventory.Snapshot inventory) throws IOException {
        writeSlots(out, inventory.slots());
        writeSlots(out, inventory.craft());
    }

    private static void writeSlots(DataOutputStream out, List<Inventory.Slot> slots) throws IOException {
        out.writeInt(slots.size());
        for (Inventory.Slot slot : slots) writeSlot(out,slot);
    }

    private static void writeSlot(DataOutputStream out,Inventory.Slot slot) throws IOException {
        out.writeBoolean(slot.item()!=null);
        if (slot.item()!=null) out.writeUTF(slot.item().name());
        out.writeInt(slot.count());
        out.writeInt(slot.durability());
    }

    private static Inventory.Slot readSlot(DataInputStream in,boolean hasDurability) throws IOException {
        ItemType item=in.readBoolean()?ItemType.valueOf(in.readUTF()):null;
        int count=in.readInt();
        int durability=hasDurability?in.readInt():item!=null && item.tool() && count==1?item.maxDurability():0;
        try {
            Inventory.Slot slot=new Inventory.Slot(item,count,durability);
            if (count>(item!=null && item.tool()?1:Inventory.MAX_STACK) || (item==null)!=(count==0)) throw new IllegalArgumentException();
            return slot;
        } catch (IllegalArgumentException e) {
            throw new IOException("Некорректный слот камнереза",e);
        }
    }

    private static boolean validMachineSlot(Inventory.Slot slot,ItemType expected) {
        return slot.item()==null || slot.item()==expected;
    }

    private static boolean validFurnaceSlots(Inventory.Slot coal,Inventory.Slot input,Inventory.Slot output) {
        if (!validMachineSlot(coal,ItemType.COAL)
                || (input.item()!=null && input.item()!=ItemType.IRON && input.item()!=ItemType.SAND)
                || (output.item()!=null && output.item()!=ItemType.IRON_INGOT && output.item()!=ItemType.GLASS)) return false;
        return input.item()==null || output.item()==null
                || (input.item()==ItemType.IRON && output.item()==ItemType.IRON_INGOT)
                || (input.item()==ItemType.SAND && output.item()==ItemType.GLASS);
    }

    private static boolean validMachineRecipe(ItemType recipe) {
        return recipe==ItemType.AXE_HEAD || recipe==ItemType.PICKAXE_HEAD || recipe==ItemType.SHOVEL_HEAD
            || recipe==ItemType.IRON_AXE_HEAD || recipe==ItemType.IRON_PICKAXE_HEAD
            || recipe==ItemType.IRON_SHOVEL_HEAD;
    }

    private static Inventory.Snapshot readInventory(DataInputStream in,boolean hasDurability) throws IOException {
        return new Inventory.Snapshot(readSlots(in, Inventory.SLOT_COUNT,hasDurability), readSlots(in, Inventory.CRAFT_SIZE,hasDurability));
    }

    private static List<Inventory.Slot> readSlots(DataInputStream in, int expectedSize,boolean hasDurability) throws IOException {
        int size = in.readInt();
        if (size != expectedSize) throw new IOException("Некорректный размер инвентаря");
        List<Inventory.Slot> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(readSlot(in,hasDurability));
        }
        return slots;
    }
}
