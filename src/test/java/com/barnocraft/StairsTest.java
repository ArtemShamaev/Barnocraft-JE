package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StairsTest {
    @Test void walkingUpAndDownAllFourOrientationsNeedsNoJump() {
        int[][] uphill={{0,-1},{1,0},{0,1},{-1,0}};
        for (int facing=0;facing<4;facing++) {
            World world=World.flat(); Player player=new Player();
            assertTrue(world.placeStairs(20,6,20,facing,player));
            int dx=uphill[facing][0],dz=uphill[facing][1];
            for(int i=1;i<=3;i++) world.set(20+dx*i,6,20+dz*i,Block.STONE);
            player.position.set(20.5f-dx*2,6.001f,20.5f-dz*2);
            Vector3f direction=new Vector3f(dx,0,dz);
            for(int i=0;i<60;i++) player.update(world,1/60f,direction,false);
            assertEquals(7,player.position.y,.002,"Walks onto platform, facing "+facing);
            assertFalse(player.collides(world));
            for(int i=0;i<65;i++) player.update(world,1/60f,direction.negate(),false);
            assertEquals(6,player.position.y,.002,"Walks back down, facing "+facing);
        }
    }
    @Test void rayAndCollisionRespectEmptyUpperHalf() {
        World world=World.flat(); Player player=new Player(); world.placeStairs(20,10,20,0,player);
        Vector3f origin=new Vector3f(20.5f,10.75f,23),forward=new Vector3f(0,0,-1);
        assertNull(world.raycast(origin,forward,2.25f));
        assertNotNull(world.raycast(origin,forward,2.6f));
        assertNotNull(world.raycast(new Vector3f(20.5f,10.25f,23),forward,2.25f));
        player.position.set(20.5f,10.501f,20.85f); assertFalse(player.collides(world));
        assertFalse(world.rotate(20,10,20,player),"Cannot rotate upper half through player");
        player.position.set(40,6,40); assertTrue(world.rotate(20,10,20,player));
        assertNull(world.raycast(new Vector3f(20.25f,10.75f,23),forward,5));
        assertNotNull(world.raycast(new Vector3f(20.75f,10.75f,23),forward,5));
    }
    @Test void cannotStepUpThroughLowCeilingOrAFullBlock() {
        World world=World.flat(); Player player=new Player(); world.placeStairs(20,6,20,0,player);
        world.set(20,8,21,Block.STONE); player.position.set(20.5f,6.001f,22);
        for(int i=0;i<60;i++) player.update(world,1/60f,new Vector3f(0,0,-1),false);
        assertTrue(player.position.z>=21.29f); assertEquals(6,player.position.y,.002);
        world.set(20,8,21,Block.AIR); world.set(20,6,20,Block.STONE);
        for(int i=0;i<60;i++) player.update(world,1/60f,new Vector3f(0,0,-1),false);
        assertTrue(player.position.z>=21.29f); assertEquals(6,player.position.y,.002);
    }
    @Test void placementRotationAndDestructionKeepOneCell() {
        World world=World.flat(); Player player=new Player();
        assertFalse(world.placeStairs(40,6,40,0,player));
        assertTrue(world.placeStairs(20,6,20,3,player));
        assertEquals(Block.STAIRS,world.get(20,6,20)); assertEquals(Block.AIR,world.get(20,7,20));
        assertEquals(3,world.rotation(20,6,20)); assertTrue(world.rotate(20,6,20,player));
        assertEquals(0,world.rotation(20,6,20)); world.set(20,6,20,Block.AIR);
        assertEquals(0,world.rotation(20,6,20));
        assertFalse(world.set(20,6,20,Block.ROTATOR),"Tool cannot become a world block");
    }
}
