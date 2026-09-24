package com.barnocraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SaveStoreTest {
    @TempDir Path directory;
    @Test void fiveIndependentSlotsRoundTripEditsDoorsAndPose() throws Exception {
        SaveStore saves=new SaveStore(directory);
        for (int slot=1;slot<=5;slot++) {
            World world=new World(100+slot); Player player=new Player();
            world.set(31,24,15,Block.PLANKS); world.set(31,25,15,Block.AIR); world.set(31,26,15,Block.AIR);
            assertTrue(world.placeDoor(31,25,15,slot%4,player));
            if (slot%2==1) assertTrue(world.toggleDoor(31,26,15,player));
            world.set(12,0,13,Block.AIR); world.set(510,20,300,Block.GLASS);
            SaveStore.Pose pose=new SaveStore.Pose(100+slot,24.5f,200,.3f,-.4f,6);
            saves.save(slot,world.snapshot(),pose);
            SaveStore.Saved loaded=saves.load(slot);
            assertEquals(world.seed(),loaded.world().seed()); assertEquals(pose,loaded.pose());
            assertEquals(Block.AIR,loaded.world().get(12,0,13)); assertEquals(Block.GLASS,loaded.world().get(510,20,300));
            assertEquals(world.doorAt(31,25,15),loaded.world().doorAt(31,26,15));
            assertEquals(world.get(400,5,220),loaded.world().get(400,5,220));
        }
        for (int slot=1;slot<=5;slot++) assertEquals(100+slot,saves.load(slot).world().seed());
        try(var files=Files.list(directory)) { assertEquals(5,files.count(),"No temporary files left behind"); }
        assertThrows(IllegalArgumentException.class,()->saves.slotPath(0));
        assertThrows(IllegalArgumentException.class,()->saves.slotPath(6));
    }
    @Test void corruptSaveIsRejectedWithoutOverwritingIt() throws Exception {
        SaveStore saves=new SaveStore(directory); byte[] corrupt={1,2,3,4};
        Files.write(saves.slotPath(1),corrupt);
        assertThrows(IOException.class,()->saves.load(1)); assertArrayEquals(corrupt,Files.readAllBytes(saves.slotPath(1)));
    }
    @Test void orientationsRoundTripAndOldVersionStillLoads() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42); Player player=new Player();
        world.set(20,25,20,Block.STAIRS); world.rotate(20,25,20,player);
        world.set(21,25,20,Block.LOG); world.rotate(21,25,20,player); world.rotate(21,25,20,player);
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,8);
        saves.save(1,world.snapshot(),pose); SaveStore.Saved loaded=saves.load(1);
        assertEquals(Block.STAIRS,loaded.world().get(20,25,20));
        assertEquals(1,loaded.world().rotation(20,25,20)); assertEquals(2,loaded.world().rotation(21,25,20));
        assertEquals(8,loaded.pose().selected());
        try (var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(saves.slotPath(2))))) {
            out.writeInt(0x4241524E); out.writeInt(1); out.writeLong(42);
            out.writeFloat(40); out.writeFloat(20); out.writeFloat(40); out.writeFloat(0); out.writeFloat(0); out.writeInt(6);
            out.writeInt(1); out.writeInt((25*World.WIDTH+20)*World.DEPTH+20); out.writeUTF("PLANKS");
            out.writeInt(0);
        }
        SaveStore.Saved old=saves.load(2);
        assertEquals(Block.PLANKS,old.world().get(20,25,20)); assertEquals(0,old.world().rotation(20,25,20));
        saves.save(2,old.world().snapshot(),old.pose()); assertEquals(old.pose(),saves.load(2).pose());
    }
    @Test void settingsRoundTrip() throws Exception {
        GameOptions options=new GameOptions(); options.distance=5; options.fov=85; options.sensitivity=1.75f;
        Path path=directory.resolve("settings.properties"); options.save(path);
        GameOptions loaded=new GameOptions(); loaded.load(path);
        assertEquals(5,loaded.distance); assertEquals(85,loaded.fov); assertEquals(1.75f,loaded.sensitivity);
    }
}
