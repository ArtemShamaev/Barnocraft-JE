package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    @Test void interactionReachStopsAtFiveBlocks() {
        World w = World.flat();
        w.set(20,10,20,Block.LOG);
        Vector3f direction = new Vector3f(10,0,0);
        assertNotNull(w.raycast(new Vector3f(15,10.5f,20.5f), direction, Player.REACH));
        assertNull(w.raycast(new Vector3f(14.99f,10.5f,20.5f), direction, Player.REACH));
        assertNotNull(w.raycast(new Vector3f(15.01f,10.5f,20.5f), direction, Player.REACH));
    }

    @Test void originalTerrainAndBounds() {
        World w = World.flat();
        assertEquals(Block.STONE, w.get(0, 4, 79));
        assertEquals(Block.GRASS, w.get(79, 5, 0));
        assertEquals(Block.AIR, w.get(40, 6, 40));
        assertEquals(Block.AIR, w.get(-1, 0, 0));
        assertFalse(w.set(80, 6, 0, Block.GLASS));
        assertTrue(w.set(16, 6, 16, Block.GLASS));
        assertEquals(Block.GLASS, w.get(16, 6, 16));
    }
    @Test void verticalRayOnIntegerPlanesHasNoNaN() {
        World w = World.flat();
        assertEquals(new World.Hit(40, 5, 40, 0, 1, 0), w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,-1,0), 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,1,0), 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), Vector3f.ZERO, 150));
        assertNull(w.raycast(new Vector3f(40, 8, 40), new Vector3f(0,-1,0), 1));
    }
    @Test void rayFindsAllPlacementNormalsAndGlass() {
        World w = World.flat(); w.set(20, 10, 20, Block.GLASS);
        int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] n : directions) {
            Vector3f normal = new Vector3f(n[0], n[1], n[2]);
            World.Hit hit = w.raycast(new Vector3f(20.5f,10.5f,20.5f).add(normal.mult(3)), normal.negate(), 4);
            assertEquals(new World.Hit(20,10,20,n[0],n[1],n[2]), hit);
        }
    }
    @Test void sheepDropsMuttonAndWoolWhenKilled() {
        World world=World.flat();
        world.addSheep(new Sheep(20.5f,6f,20.5f,3));
        Inventory inventory=new Inventory();
        Vector3f playerEye=new Vector3f(20.5f,6.55f,15f);
        Vector3f look=new Vector3f(0,0,1);

        assertTrue(world.attackSheep(playerEye,look,Player.REACH,inventory));
        assertEquals(2,world.sheep().get(0).health());
        assertTrue(world.sheep().get(0).fleeTime()>0);
        assertTrue(world.attackSheep(playerEye,look,Player.REACH,inventory));
        assertTrue(world.attackSheep(playerEye,look,Player.REACH,inventory));
        assertTrue(world.sheep().isEmpty());
        assertEquals(1,inventory.count(ItemType.MUTTON));
        assertEquals(1,inventory.count(ItemType.WOOL));
        assertTrue(world.droppedItems().isEmpty());
    }
    @Test void sheepRunAwayAndCannotWalkThroughSolidBlocks() {
        World world=World.flat();
        world.set(21,6,20,Block.STONE);
        world.addSheep(new Sheep(20.5f,6f,20.5f,3,1,0,0));
        for(int i=0;i<20;i++) world.updateSheep(.1f);
        assertTrue(world.sheep().get(0).x()<20.7f,"Stone wall blocks the sheep");

        World fleeing=World.flat();
        fleeing.addSheep(new Sheep(20.5f,6f,20.5f,3));
        Vector3f attacker=new Vector3f(20.5f,6.5f,18f);
        assertTrue(fleeing.attackSheep(attacker,new Vector3f(0,0,1),Player.REACH,new Inventory()));
        float before=fleeing.sheep().get(0).z();
        fleeing.updateSheep(.1f);
        assertTrue(fleeing.sheep().get(0).z()>before,"Sheep flees away from the attacker");
    }
    @Test void newWorldStartsWithSheepNearSpawnAndCanReplenishOldWorlds() {
        World world=new World(1942);
        Vector3f spawn=new Vector3f(World.WIDTH/2f+.5f,20,World.DEPTH/2f+.5f);
        assertTrue(world.sheep().size()>=20);
        assertTrue(world.sheep().stream().anyMatch(s -> Math.hypot(s.x()-spawn.x,s.z()-spawn.z)<72));

        World old=World.flat();
        old.ensureSheepNear(spawn);
        assertTrue(old.sheep().size()>=16);
    }
}
