package com.barnocraft;

import java.nio.file.Path;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FurnitureTest {
    @TempDir Path directory;
    private Inventory recipe(ItemType... items) {
        Inventory inv=new Inventory();
        for(int i=0;i<items.length;i++) {
            inv.add(items[i]);
            for(int slot=0;slot<Inventory.SLOT_COUNT;slot++) if(inv.itemAt(slot)==items[i]) {
                assertTrue(inv.moveRef("slot:"+slot,"craft:"+i,true)); break;
            }
        }
        return inv;
    }
    @Test void recipesAndRetiredStations() {
        assertEquals(ItemType.BED,recipe(ItemType.WOOL,ItemType.WOOL,ItemType.PLANK,ItemType.PLANK).craftPreview());
        assertEquals(ItemType.LANTERN,recipe(ItemType.TORCH,ItemType.GLASS,ItemType.IRON_INGOT).craftPreview());
        assertNull(recipe(ItemType.IRON_INGOT,ItemType.IRON_INGOT,ItemType.PLANK,ItemType.PLANK).craftPreview());
        assertNull(recipe(ItemType.IRON_INGOT,ItemType.STICK,ItemType.WOOD,ItemType.WOOD).craftPreview());
        assertNull(Inventory.toBlock(ItemType.TORCH));
        assertNull(Inventory.toBlock(ItemType.WELDER));
        assertEquals(Block.LANTERN,Inventory.toBlock(ItemType.LANTERN));
    }
    @Test void bedIsAtomicAndBreakingEitherHalfRemovesBoth() {
        for(int facing=0;facing<4;facing++) {
            World w=World.flat(); Player p=new Player();
            assertTrue(w.placeBed(20,6,20,facing,p));
            int[][] offsets={{0,1},{-1,0},{0,-1},{1,0}};
            int hx=20+offsets[facing][0],hz=20+offsets[facing][1];
            assertEquals(Block.BED_HEAD,w.get(hx,6,hz));
            assertEquals(facing,w.rotation(hx,6,hz));
            assertTrue(w.set(hx,6,hz,Block.AIR)); assertEquals(Block.AIR,w.get(20,6,20));
        }
        World w=World.flat(); w.set(20,6,21,Block.STONE);
        assertFalse(w.placeBed(20,6,20,0,new Player())); assertEquals(Block.AIR,w.get(20,6,20));
    }
    @Test void sleepSkipsNightButRefusesNearbyZombie() {
        World w=World.flat(); assertTrue(w.placeBed(20,6,20,0,new Player()));
        w.setDayTime(.8f); w.addZombie(new Zombie(22,6,22,8,0));
        assertTrue(w.sleepAt(20,6,20).contains("зомби")); assertEquals(.8f,w.dayTime());
        World safe=World.flat(); safe.placeBed(20,6,20,0,new Player()); safe.setDayTime(.8f);
        assertEquals("Доброе утро!",safe.sleepAt(20,6,21)); assertEquals(.28f,safe.dayTime());
    }
    @Test void lanternLightsWorldAndCanBeTargetedAndBroken() {
        World w=World.flat(); w.set(20,6,20,Block.LANTERN);
        VoxelLighting light=new VoxelLighting(w,1,1);
        assertEquals(14,light.block(20,6,20)); assertTrue(light.block(21,6,20)>0);
        assertNotNull(w.raycast(new Vector3f(20.5f,7.5f,20.5f),new Vector3f(0,-1,0),3));
        w.set(20,5,20,Block.AIR); assertEquals(Block.AIR,w.get(20,6,20));
    }
    @Test void bedCollisionMatchesMattressHeight() {
        World w=World.flat(); w.placeBed(20,6,20,0,new Player());
        Player p=new Player(); p.position.set(20.5f,8,20.5f);
        for(int i=0;i<120;i++) p.update(w,1f/60,Vector3f.ZERO,false);
        assertEquals(6.6f,p.position.y,.02f);
    }
    @Test void furnitureSurvivesSaveAndLoad() throws Exception {
        World w=new World(42); Player p=new Player();
        for(int z=20;z<=21;z++) { w.set(20,89,z,Block.STONE); w.set(20,90,z,Block.AIR); }
        assertTrue(w.placeBed(20,90,20,0,p)); w.set(22,90,20,Block.LANTERN);
        SaveStore store=new SaveStore(directory);
        store.save(1,w.snapshot(),new SaveStore.Pose(40,90,40,0,0,0));
        World loaded=store.load(1).world();
        assertEquals(Block.BED,loaded.get(20,90,20)); assertEquals(Block.BED_HEAD,loaded.get(20,90,21));
        assertEquals(Block.LANTERN,loaded.get(22,90,20));
    }
    @Test void existingZombieHordeIsTrimmedAndSpawningIsSlow() {
        World w=World.flat(); Player p=new Player();
        for(int i=0;i<24;i++) w.addZombie(new Zombie(20+i,6,20,8,0));
        w.updateZombies(.01f,p); assertEquals(3,w.zombies().size());
        World quiet=World.flat(); quiet.setDayTime(.8f);
        for(int i=0;i<590;i++) quiet.updateZombies(.1f,p);
        assertTrue(quiet.zombies().isEmpty());
    }
}
