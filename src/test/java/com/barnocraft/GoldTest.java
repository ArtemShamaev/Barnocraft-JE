package com.barnocraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GoldTest {
    @TempDir Path directory;
    private int slot(Inventory inv,ItemType item) {
        for(int i=0;i<Inventory.SLOT_COUNT;i++) if(inv.itemAt(i)==item) return i;
        throw new AssertionError("Missing "+item);
    }
    @Test void goldGeneratesDeepUndergroundAndDropsOre() {
        int total=0;
        for(int seed=1;seed<=3;seed++) {
            World w=World.sample(seed);
            for(int x=0;x<80;x++) for(int z=0;z<80;z++) for(int y=1;y<w.groundHeight(x,z);y++)
                if(w.get(x,y,z)==Block.GOLD_ORE) { total++; assertTrue(w.groundHeight(x,z)-y>=Underground.GOLD_MIN_DEPTH); }
        }
        assertTrue(total>0);
        Inventory inv=new Inventory();inv.addBlock(Block.GOLD_ORE);assertEquals(1,inv.count(ItemType.GOLD));
    }
    @Test void oreSmeltsAndEveryGoldToolCanBeCrafted() {
        World w=World.flat();w.set(20,6,20,Block.FURNACE);w.set(22,6,20,Block.STONECUTTER);
        Inventory inv=new Inventory(); inv.add(ItemType.GOLD,15);inv.add(ItemType.COAL,5);inv.add(ItemType.STICK,3);
        for(int i=0;i<2;i++) assertTrue(w.putFurnaceItem(20,6,20,0,inv,slot(inv,ItemType.COAL)));
        for(int i=0;i<15;i++) assertTrue(w.putFurnaceItem(20,6,20,1,inv,slot(inv,ItemType.GOLD)));
        for(int i=0;i<15;i++) assertTrue(w.smeltIron(20,6,20));
        assertEquals(ItemType.GOLD_INGOT,w.furnaceAt(20,6,20).output().item());
        for(int i=0;i<15;i++) assertTrue(w.takeFurnaceOutput(20,6,20,inv));
        ItemType[] heads={ItemType.GOLD_AXE_HEAD,ItemType.GOLD_PICKAXE_HEAD,ItemType.GOLD_SHOVEL_HEAD};
        ItemType[] tools={ItemType.GOLD_AXE,ItemType.GOLD_PICKAXE,ItemType.GOLD_SHOVEL};
        for(int n=0;n<3;n++) {
            assertTrue(w.selectStonecutterRecipe(22,6,20,heads[n]));
            assertTrue(w.putStonecutterItem(22,6,20,0,inv,slot(inv,ItemType.COAL)));
            for(int i=0;i<5;i++) assertTrue(w.putStonecutterItem(22,6,20,1,inv,slot(inv,ItemType.GOLD_INGOT)));
            assertTrue(w.craftStonecutterHead(22,6,20));assertTrue(w.takeStonecutterOutput(22,6,20,inv));
            inv.moveRef("slot:"+slot(inv,heads[n]),"craft:0",true);
            inv.moveRef("slot:"+slot(inv,ItemType.STICK),"craft:1",true);
            assertEquals(tools[n],inv.craftPreview());assertTrue(inv.craftFromGrid());
            int i=slot(inv,tools[n]);assertEquals(250,inv.slotAtIndex(i).durability());inv.damageToolAt(i);
            assertEquals(249,inv.slotAtIndex(i).durability());
        }
        assertEquals(Block.STONE.breakTime(ItemType.IRON_PICKAXE),Block.STONE.breakTime(ItemType.GOLD_PICKAXE));
    }
    @Test void oneIngotMakesWorkingRotatorAndBedsRotateAtomically() {
        Inventory inv=new Inventory();inv.add(ItemType.GOLD_INGOT,2);inv.moveRef("slot:0","craft:0");
        assertEquals(ItemType.ROTATOR,inv.craftPreview()); assertTrue(inv.craftFromGrid());
        assertEquals(1,inv.count(ItemType.GOLD_INGOT));assertEquals(Block.ROTATOR,Inventory.toBlock(ItemType.ROTATOR));
        World w=World.flat();Player p=new Player();w.set(20,6,20,Block.STONECUTTER);
        assertTrue(w.rotate(20,6,20,p)); assertEquals(1,w.rotation(20,6,20));
        w.placeBed(25,6,25,0,p); assertTrue(w.rotate(25,6,26,p));
        assertEquals(Block.BED_HEAD,w.get(24,6,25)); assertEquals(Block.AIR,w.get(25,6,26));
        w.set(25,6,24,Block.STONE);assertFalse(w.rotate(25,6,25,p));
        assertEquals(Block.BED_HEAD,w.get(24,6,25));
    }
    @Test void goldMachineContentsAndToolsSurviveSaving() throws Exception {
        World w=new World(42);w.set(20,90,20,Block.FURNACE);w.set(22,90,20,Block.STONECUTTER);
        Inventory inv=new Inventory(); inv.add(ItemType.GOLD,2);inv.add(ItemType.COAL,2);inv.add(ItemType.GOLD_INGOT,5);inv.add(ItemType.GOLD_PICKAXE);
        w.putFurnaceItem(20,90,20,0,inv,slot(inv,ItemType.COAL));
        for(int i=0;i<2;i++) w.putFurnaceItem(20,90,20,1,inv,slot(inv,ItemType.GOLD));
        w.smeltIron(20,90,20);
        w.selectStonecutterRecipe(22,90,20,ItemType.GOLD_PICKAXE_HEAD);
        for(int i=0;i<5;i++) w.putStonecutterItem(22,90,20,1,inv,slot(inv,ItemType.GOLD_INGOT));
        SaveStore store=new SaveStore(directory);store.save(1,w.snapshot(),new SaveStore.Pose(40,90,40,0,0,0),inv.snapshot());
        SaveStore.Saved saved=store.load(1);
        assertEquals(w.furnaceAt(20,90,20),saved.world().furnaceAt(20,90,20));
        assertEquals(w.stonecutterAt(22,90,20),saved.world().stonecutterAt(22,90,20));
        Inventory restored=new Inventory();restored.restore(saved.inventory());assertEquals(1,restored.count(ItemType.GOLD_PICKAXE));
    }
}
