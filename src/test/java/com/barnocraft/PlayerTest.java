package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    @Test void standingJumpAndLanding() {
        World w = World.flat(); Player p = new Player();
        for (int i = 0; i < 60; i++) p.update(w, 1/60f, Vector3f.ZERO, false);
        assertEquals(6, p.position.y, .001); assertTrue(p.grounded);
        assertFalse(p.intersects(40,5,40), "Standing on a block must still allow breaking it");
        p.update(w, 1/60f, Vector3f.ZERO, true);
        assertTrue(p.position.y > 6); assertFalse(p.grounded);
        float velocity = p.velocityY;
        p.update(w, 1/60f, Vector3f.ZERO, true);
        assertTrue(p.velocityY < velocity, "Cannot jump in midair");
        for (int i = 0; i < 120; i++) p.update(w, 1/60f, Vector3f.ZERO, false);
        assertEquals(6, p.position.y, .001); assertTrue(p.grounded);
        assertFalse(p.intersects(40,5,40), "Standing on a block must still allow breaking it");
    }
    @Test void wallCeilingAndWorldBoundary() {
        World w = World.flat(); Player p = new Player();
        for (int y = 6; y < 10; y++) for (int z = 39; z <= 41; z++) w.set(41,y,z,Block.STONE);
        for (int i = 0; i < 120; i++) p.update(w, 1/60f, Vector3f.UNIT_X, false);
        assertEquals(40.7, p.position.x, .001); assertFalse(p.collides(w));
        w.set(40,8,40,Block.STONE); w.set(40,8,39,Block.STONE);
        for (int i = 0; i < 10; i++) {
            p.update(w, 1/60f, Vector3f.ZERO, i == 0);
            assertTrue(p.position.y + Player.HEIGHT <= 8.001);
        }
        p.position.set(.31f,6.001f,.31f);
        for (int i = 0; i < 60; i++) p.update(w, 1/60f, new Vector3f(-1,0,0), false);
        assertEquals(.3, p.position.x, .001);
    }
    @Test void fallsWhenSupportRemovedAndRecoversFromVoid() {
        World w = World.flat(); Player p = new Player(); p.position.set(40.5f,6.001f,40.5f);
        w.set(40,5,40,Block.AIR);
        for (int i = 0; i < 60; i++) p.update(w, 1/60f, Vector3f.ZERO, false);
        assertEquals(5, p.position.y, .001);
        p.position.y = -21; p.update(w, .01f, Vector3f.ZERO, false);
        assertTrue(p.position.y >= 5); assertFalse(p.collides(w));
    }
    @Test void placementCannotOverlapBody() {
        Player p = new Player();
        assertTrue(p.intersects(40,6,40)); assertTrue(p.intersects(39,7,39));
        assertFalse(p.intersects(40,5,40)); assertFalse(p.intersects(41,6,40));
    }
    @Test void hungerDrainsStarvationHurtsAndFoodRestoresBothMeters() {
        Player player=new Player();
        player.tickHunger(34);assertEquals(Player.MAX_HUNGER,player.hunger());
        player.tickHunger(1);assertEquals(Player.MAX_HUNGER-1,player.hunger());
        player.restoreHunger(0);assertFalse(player.tickHunger(4.9f));
        assertTrue(player.tickHunger(.1f));assertEquals(Player.MAX_HEALTH-1,player.health());
        Inventory inventory=new Inventory();inventory.add(ItemType.BREAD);
        assertTrue(player.eatFood(inventory,0));
        assertEquals(Player.MAX_HEALTH,player.health());assertEquals(7,player.hunger());
    }
    @Test void wheatCraftsBreadAndStoneHoe() {
        Inventory bread=new Inventory();bread.add(ItemType.WHEAT,3);
        for(int i=0;i<3;i++)assertTrue(bread.moveRef("slot:0","craft:"+i,true));
        assertEquals(ItemType.BREAD,bread.craftPreview());assertTrue(bread.craftFromGrid());
        assertEquals(1,bread.count(ItemType.BREAD));assertEquals(0,bread.count(ItemType.WHEAT));

        Inventory hoe=new Inventory();hoe.add(ItemType.PLANK,2);hoe.add(ItemType.STICK,2);
        for(int i=0;i<2;i++)assertTrue(hoe.moveRef("slot:0","craft:"+i,true));
        for(int i=0;i<2;i++)assertTrue(hoe.moveRef("slot:1","craft:"+(i+2),true));
        assertEquals(ItemType.STONE_HOE,hoe.craftPreview());assertTrue(hoe.craftFromGrid());
        assertEquals(1,hoe.count(ItemType.STONE_HOE));
    }
    @Test void respawnUsesLastUsedBedAndFallsBackWhenBedIsGone() {
        World world=World.flat();Player player=new Player();
        assertTrue(world.placeBed(20,6,20,0,player));player.setRespawnPoint(world.bedSpawn(20,6,20));
        player.damage(Player.MAX_HEALTH);player.respawn(world);
        assertEquals(20.5f,player.position.x,.001f);assertEquals(6.61f,player.position.y,.02f);
        world.set(20,6,20,Block.AIR);player.damage(Player.MAX_HEALTH);player.respawn(world);
        assertEquals(world.width()/2f+.5f,player.position.x,.001f);
    }
    @Test void chestRecipeProducesPlaceableStorageBlock() {
        Inventory inventory=new Inventory();inventory.add(ItemType.PLANK,3);inventory.add(ItemType.STICK);
        for(int i=0;i<3;i++)assertTrue(inventory.moveRef("slot:0","craft:"+i,true));
        assertTrue(inventory.moveRef("slot:1","craft:3",true));
        assertEquals(ItemType.CHEST,inventory.craftPreview());assertTrue(inventory.craftFromGrid());
        assertEquals(1,inventory.count(ItemType.CHEST));assertEquals(Block.CHEST,Inventory.toBlock(ItemType.CHEST));
    }
}
