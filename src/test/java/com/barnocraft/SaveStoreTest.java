package com.barnocraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
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
    @Test void inventoryAndCraftGridRoundTrip() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42); Inventory inventory=new Inventory();
        inventory.add(ItemType.IRON,66);
        assertTrue(inventory.moveRef("slot:1","craft:2"));
        assertTrue(inventory.moveRef("slot:1","craft:2"));
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,3);

        saves.save(1,world.snapshot(),pose,inventory.snapshot());
        SaveStore.Saved loaded=saves.load(1);
        Inventory restored=new Inventory();
        restored.restore(loaded.inventory());

        assertEquals(ItemType.IRON,restored.itemAt(0));
        assertEquals(64,restored.countAt(0));
        assertEquals(ItemType.IRON,restored.craftItemAt(2));
        assertEquals(2,restored.craftCountAt(2));
    }
    @Test void stonecutterSlotsCraftAndPersist() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42); Inventory inventory=new Inventory();
        assertTrue(world.set(12,8,14,Block.STONECUTTER));
        inventory.add(ItemType.COAL); inventory.add(ItemType.IRON_INGOT,5);
        assertTrue(world.selectStonecutterRecipe(12,8,14,ItemType.IRON_PICKAXE_HEAD));
        assertTrue(world.putStonecutterItem(12,8,14,0,inventory,0));
        for (int i=0;i<5;i++) assertTrue(world.putStonecutterItem(12,8,14,1,inventory,1));
        assertEquals(0,inventory.count(ItemType.COAL));
        assertEquals(0,inventory.count(ItemType.IRON_INGOT));
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        saves.save(1,world.snapshot(),pose,inventory.snapshot());
        World restored=saves.load(1).world();
        assertEquals(ItemType.COAL,restored.stonecutterAt(12,8,14).coal().item());
        assertEquals(ItemType.IRON_INGOT,restored.stonecutterAt(12,8,14).stone().item());
        assertEquals(5,restored.stonecutterAt(12,8,14).stone().count());
        assertEquals(ItemType.IRON_PICKAXE_HEAD,restored.stonecutterAt(12,8,14).recipe());
        assertTrue(restored.craftStonecutterHead(12,8,14));
        saves.save(1,restored.snapshot(),pose,inventory.snapshot());
        restored=saves.load(1).world();
        assertEquals(ItemType.IRON_PICKAXE_HEAD,restored.stonecutterAt(12,8,14).output().item());
        assertTrue(restored.takeStonecutterOutput(12,8,14,inventory));
        assertEquals(1,inventory.count(ItemType.IRON_PICKAXE_HEAD));
    }
    @Test void stonecutterCanSelectAndCraftAllThreeHeads() {
        World world=new World(42);
        assertTrue(world.set(12,8,14,Block.STONECUTTER));
        for (ItemType recipe : List.of(ItemType.AXE_HEAD,ItemType.PICKAXE_HEAD,ItemType.SHOVEL_HEAD,
                ItemType.IRON_AXE_HEAD,ItemType.IRON_PICKAXE_HEAD,ItemType.IRON_SHOVEL_HEAD)) {
            assertTrue(world.selectStonecutterRecipe(12,8,14,recipe));
            assertEquals(recipe,world.stonecutterAt(12,8,14).recipe());
            Inventory inventory=new Inventory(); inventory.add(ItemType.COAL);
            ItemType material=World.stonecutterMaterial(recipe);
            int materialCount=World.stonecutterMaterialCount(recipe);
            inventory.add(material,materialCount);
            assertTrue(world.putStonecutterItem(12,8,14,0,inventory,0));
            int initialMaterial=materialCount==5?4:materialCount;
            for (int i=0;i<initialMaterial;i++) assertTrue(world.putStonecutterItem(12,8,14,1,inventory,1));
            if (materialCount==5) {
                assertFalse(world.craftStonecutterHead(12,8,14));
                assertEquals(1,inventory.count(ItemType.IRON_INGOT));
                assertTrue(world.putStonecutterItem(12,8,14,1,inventory,1));
                assertEquals(5,world.stonecutterAt(12,8,14).stone().count());
            }
            assertTrue(world.craftStonecutterHead(12,8,14));
            assertEquals(recipe,world.stonecutterAt(12,8,14).output().item());
            assertTrue(world.takeStonecutterOutput(12,8,14,inventory));
            assertEquals(1,inventory.count(recipe));
            assertEquals(0,world.stonecutterAt(12,8,14).coal().count());
            assertEquals(0,world.stonecutterAt(12,8,14).stone().count());
        }
    }
    @Test void furnaceConsumesCoalAndIronAndPersistsItsSlots() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42); Inventory inventory=new Inventory();
        assertTrue(world.set(13,8,14,Block.FURNACE));
        inventory.add(ItemType.COAL); inventory.add(ItemType.IRON);
        assertFalse(world.smeltIron(13,8,14));
        assertTrue(world.putFurnaceItem(13,8,14,0,inventory,0));
        assertTrue(world.putFurnaceItem(13,8,14,1,inventory,1));
        assertFalse(world.putFurnaceItem(13,8,14,0,inventory,1));
        assertTrue(world.smeltIron(13,8,14));
        World.FurnaceSlots slots=world.furnaceAt(13,8,14);
        assertEquals(0,slots.coal().count());
        assertEquals(0,slots.iron().count());
        assertEquals(ItemType.IRON_INGOT,slots.output().item());
        assertEquals(1,slots.output().count());

        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        saves.save(1,world.snapshot(),pose,inventory.snapshot());
        World restored=saves.load(1).world();
        assertEquals(ItemType.IRON_INGOT,restored.furnaceAt(13,8,14).output().item());
        assertTrue(restored.takeFurnaceOutput(13,8,14,inventory));
        assertEquals(1,inventory.count(ItemType.IRON_INGOT));
    }
    @Test void furnaceSmeltsSandIntoGlassAndToolDurabilityPersists() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42); Inventory inventory=new Inventory();
        assertTrue(world.set(14,8,14,Block.FURNACE));
        inventory.add(ItemType.COAL); inventory.add(ItemType.SAND);
        assertTrue(world.putFurnaceItem(14,8,14,0,inventory,0));
        assertTrue(world.putFurnaceItem(14,8,14,1,inventory,1));
        assertTrue(world.smeltIron(14,8,14));
        assertEquals(ItemType.GLASS,world.furnaceAt(14,8,14).output().item());
        inventory.add(ItemType.IRON_PICKAXE);
        assertTrue(inventory.damageToolAt(0));

        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        saves.save(1,world.snapshot(),pose,inventory.snapshot());
        SaveStore.Saved loaded=saves.load(1);
        assertEquals(ItemType.GLASS,loaded.world().furnaceAt(14,8,14).output().item());
        assertEquals(ItemType.IRON_PICKAXE.maxDurability()-1,loaded.inventory().slots().get(0).durability());
    }
    @Test void livingSheepRoundTripsInWorldSave() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(42);
        Sheep sheep=new Sheep(20.5f,12f,30.5f,2,-.6f,.8f,2.5f);
        world.addSheep(sheep);
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        saves.save(1,world.snapshot(),pose,Inventory.emptySnapshot());
        assertTrue(saves.load(1).world().sheep().contains(sheep));
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
    @Test void namedWorldRoundTripsAndCanBeListedWithoutLoadingWorld() throws Exception {
        SaveStore saves=new SaveStore(directory); World world=new World(871);
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        saves.save(1,world.snapshot(),pose,Inventory.emptySnapshot(),"Северный каньон");
        assertEquals("Северный каньон",saves.worldName(1));
        assertEquals("Северный каньон",saves.load(1).worldName());
        assertEquals("Мир 2",saves.worldName(2));
    }
    @Test void deletingWorldRemovesOnlyItsSlot() throws Exception {
        SaveStore saves=new SaveStore(directory);
        SaveStore.Pose pose=new SaveStore.Pose(40,20,40,0,0,1);
        World first=new World(1),second=new World(2);
        saves.save(1,first.snapshot(),pose,Inventory.emptySnapshot(),"Удаляемый");
        saves.save(2,second.snapshot(),pose,Inventory.emptySnapshot(),"Оставшийся");

        assertTrue(saves.delete(1));
        assertFalse(saves.exists(1));
        assertTrue(saves.exists(2));
        assertEquals("Оставшийся",saves.load(2).worldName());
        assertFalse(saves.delete(1));
        assertThrows(IllegalArgumentException.class,()->saves.delete(6));
    }
}
