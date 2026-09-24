package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoorTest {
    @Test void twoCellsSupportClearanceAndRemoval() {
        World world=World.flat(); Player player=new Player();
        assertFalse(world.placeDoor(20,7,20,0,player),"Needs support");
        world.set(20,7,20,Block.STONE);
        assertFalse(world.placeDoor(20,6,20,0,player),"Needs headroom");
        world.set(20,7,20,Block.AIR);
        assertTrue(world.placeDoor(20,6,20,0,player));
        assertEquals(Block.DOOR,world.get(20,6,20)); assertEquals(Block.DOOR_TOP,world.get(20,7,20));
        assertEquals(world.doorAt(20,6,20),world.doorAt(20,7,20));
        world.set(20,7,20,Block.AIR);
        assertEquals(Block.AIR,world.get(20,6,20)); assertNull(world.doorAt(20,7,20));
        assertTrue(world.placeDoor(20,6,20,0,player));
        world.set(20,5,20,Block.AIR);
        assertEquals(Block.AIR,world.get(20,6,20)); assertEquals(Block.AIR,world.get(20,7,20));
        assertFalse(world.placeDoor(40,6,40,0,player),"Cannot place over player");
        assertFalse(world.placeDoor(20,World.HEIGHT-1,20,0,player),"Cannot cross world ceiling");
    }
    @Test void openingAllowsPassageAndClosingCannotTrapPlayer() {
        World world=World.flat(); Player player=new Player();
        assertTrue(world.placeDoor(20,6,20,0,player));
        player.position.set(20.5f,6.001f,20.05f); assertTrue(player.collides(world));
        assertTrue(world.toggleDoor(20,7,20,player)); assertFalse(player.collides(world));
        assertFalse(world.toggleDoor(20,6,20,player),"Do not close through player");
        player.position.set(20.5f,6.001f,22);
        assertTrue(world.toggleDoor(20,6,20,player));
        for (int i=0;i<60;i++) player.update(world,1/60f,new Vector3f(0,0,-1),false);
        assertTrue(player.position.z>=20.42f,"Closed panel stops walking");
        assertTrue(world.toggleDoor(20,6,20,player));
        for (int i=0;i<60;i++) player.update(world,1/60f,new Vector3f(0,0,-1),false);
        assertTrue(player.position.z<20,"Can walk through open doorway");
    }
    @Test void raysHitOnlyPanelForEveryOrientation() {
        for (int facing=0;facing<4;facing++) {
            World world=World.flat(); Player player=new Player();
            assertTrue(world.placeDoor(20,6,20,facing,player));
            for (int toggle=0;toggle<2;toggle++) {
                Door door=world.doorAt(20,6,20); Door.Bounds b=door.bounds();
                Vector3f center=new Vector3f((b.minX()+b.maxX())/2,7.5f,(b.minZ()+b.maxZ())/2);
                Vector3f normal=(b.maxX()-b.minX())<.5f?Vector3f.UNIT_X:Vector3f.UNIT_Z;
                World.Hit hit=world.raycast(center.add(normal.mult(2)),normal.negate(),5);
                assertNotNull(hit); assertEquals(7,hit.y()); assertNotNull(world.doorAt(hit.x(),hit.y(),hit.z()));
                assertTrue(world.toggleDoor(20,6,20,player));
            }
        }
        World world=World.flat(); Player player=new Player(); world.placeDoor(20,6,20,0,player);
        assertNull(world.raycast(new Vector3f(18,7,20.5f),Vector3f.UNIT_X,4),"Ray passes empty part of cell");
    }
}
