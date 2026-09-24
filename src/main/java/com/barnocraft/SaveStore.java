package com.barnocraft;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Five independent, versioned saves. Write a temporary file before atomically replacing a slot. */
public final class SaveStore {
    // VERSION covers both the file schema and the procedural generator used by stored seeds.
    private static final int MAGIC = 0x4241524E, VERSION = 2;
    private final Path directory;
    public SaveStore(Path directory) { this.directory = directory; }
    public record Pose(float x, float y, float z, float yaw, float pitch, int selected) {}
    public record Saved(World world, Pose pose) {}
    public Path slotPath(int slot) {
        if (slot < 1 || slot > 5) throw new IllegalArgumentException("World slot must be 1–5");
        return directory.resolve("world-" + slot + ".dat");
    }
    public boolean exists(int slot) { return Files.exists(slotPath(slot)); }
    public void save(int slot, World.Snapshot world, Pose pose) throws IOException {
        Files.createDirectories(directory);
        Path target = slotPath(slot), temp = Files.createTempFile(directory,"world-"+slot+"-",".tmp");
        try {
            try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(world.seed());
                out.writeFloat(pose.x()); out.writeFloat(pose.y()); out.writeFloat(pose.z());
                out.writeFloat(pose.yaw()); out.writeFloat(pose.pitch()); out.writeInt(pose.selected());
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
            long seed = in.readLong();
            Pose pose = new Pose(in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readInt());
            if (!Float.isFinite(pose.x()+pose.y()+pose.z()+pose.yaw()+pose.pitch())
                    || pose.x() < 0 || pose.x() >= World.WIDTH || pose.z() < 0 || pose.z() >= World.DEPTH
                    || pose.y() < -100 || pose.y() > World.HEIGHT+100 || Math.abs(pose.pitch()) > Math.PI/2
                    || pose.selected() < 0 || pose.selected() >= Block.HOTBAR.length) throw new IOException("Некорректная позиция игрока");
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
            if (edits.values().stream().filter(Block::door).count() != doorCount*2L || in.read()!=-1)
                throw new IOException("Повреждённое сохранение");
            return new Saved(World.restore(new World.Snapshot(seed,edits,doors,rotations)),pose);
        } catch (IllegalArgumentException e) { throw new IOException("Неизвестный блок в сохранении",e); }
    }
}
