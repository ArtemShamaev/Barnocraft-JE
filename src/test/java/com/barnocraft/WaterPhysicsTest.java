package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WaterPhysicsTest {
    private void tick(World world,int count) { for(int i=0;i<count;i++)world.updateWater(.12f); }
    @Test void waterFallsSpreadsStopsAtWallsAndDrains() {
        World w=World.flat();
        for(int y=6;y<12;y++)w.set(23,y,20,Block.STONE);
        w.set(20,10,20,Block.WATER);tick(w,80);
        assertEquals(Block.WATER,w.get(20,6,20));
        assertEquals(Block.WATER,w.get(21,6,20));
        assertEquals(Block.STONE,w.get(23,6,20));
        assertEquals(Block.AIR,w.get(21,10,20),"Water falls before spreading sideways");
        assertEquals(Block.AIR,w.get(30,6,20),"Finite horizontal reach");
        assertTrue(w.waterHeight(21,6,20)<w.waterHeight(20,6,20));
        w.set(20,10,20,Block.AIR);tick(w,180);
        for(int x=12;x<29;x++)for(int z=12;z<29;z++)for(int y=6;y<=10;y++)assertNotEquals(Block.WATER,w.get(x,y,z));
    }
    @Test void blockingAndReopeningAChannelUpdatesWater() {
        World w=World.flat();
        for(int x=18;x<=28;x++)for(int z:new int[]{19,21})w.set(x,6,z,Block.STONE);
        w.set(20,6,20,Block.WATER);w.set(22,6,20,Block.STONE);tick(w,80);
        assertEquals(Block.AIR,w.get(23,6,20));
        w.set(22,6,20,Block.AIR);tick(w,80);assertEquals(Block.WATER,w.get(23,6,20));
        w.set(22,6,20,Block.STONE);tick(w,100);assertEquals(Block.AIR,w.get(23,6,20));
    }
    @Test void playerSwimsAndMovesMoreSlowly() {
        World w=World.flat();
        for(int x=19;x<=23;x++)for(int y=6;y<=10;y++)w.set(x,y,20,Block.WATER);
        Player p=new Player();p.position.set(20.5f,7,20.5f);
        p.update(w,.2f,new Vector3f(1,0,0),true);
        assertTrue(p.position.y>7);assertEquals(20.9f,p.position.x,.01f);
    }
    @Test void flowingWaterRemainsFlowingAfterSave(@TempDir Path directory) throws Exception {
        World w=new World(42);
        for(int x=20;x<=36;x++)for(int z=20;z<=36;z++) {
            w.set(x,80,z,Block.STONE);for(int y=81;y<=83;y++)w.set(x,y,z,Block.AIR);
        }
        w.set(28,81,28,Block.WATER);tick(w,100);
        SaveStore saves=new SaveStore(directory);saves.save(1,w.snapshot(),new SaveStore.Pose(40,90,40,0,0,0));
        World restored=saves.load(1).world();assertEquals(w.snapshot().waterLevels(),restored.snapshot().waterLevels());
        restored.set(28,81,28,Block.AIR);tick(restored,200);
        assertEquals(Block.AIR,restored.get(29,81,28));
    }
}
