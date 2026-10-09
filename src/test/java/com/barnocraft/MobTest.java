package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobTest {
    private Player player(float x,float y,float z) {
        Player p=new Player(); p.position.set(x,y,z); return p;
    }
    @Test void zombieReactsImmediatelyAndFacesPlayer() {
        World w=World.flat(); w.addZombie(new Zombie(20,6,20,8,0));
        w.updateZombies(.05f,player(25,6,20));
        Zombie z=w.zombies().get(0);
        assertTrue(z.x()>20.05f); assertEquals(Math.PI/2,z.heading(),.01);
    }
    @Test void zombieJumpsOntoOneBlockStep() {
        World w=World.flat(); w.addZombie(new Zombie(20.5f,6,20.5f,8,0));
        for(int x=22;x<28;x++) for(int z=18;z<24;z++) w.set(x,6,z,Block.STONE);
        Player p=player(26,7,20.5f); float highest=6;
        for(int i=0;i<150;i++) { w.updateZombies(1f/60,p); highest=Math.max(highest,w.zombies().get(0).y()); }
        assertTrue(highest>7.1f); assertTrue(w.zombies().get(0).x()>23);
    }
    @Test void zombieFallsWhenSupportDisappearsEvenWithoutTarget() {
        World w=World.flat(); w.set(20,9,20,Block.STONE);
        w.addZombie(new Zombie(20.5f,10,20.5f,8,0));
        w.set(20,9,20,Block.AIR);
        Player p=player(75,6,75);
        for(int i=0;i<120;i++) w.updateZombies(1f/60,p);
        assertEquals(6,w.zombies().get(0).y(),.01);
    }
    @Test void zombieWalksOffHighLedge() {
        World w=World.flat(); w.set(20,9,20,Block.STONE);
        w.addZombie(new Zombie(20.5f,10,20.5f,8,0));
        for(int i=0;i<180;i++) w.updateZombies(1f/60,player(28,6,20.5f));
        assertTrue(w.zombies().get(0).x()>24); assertEquals(6,w.zombies().get(0).y(),.01);
    }
    @Test void fishRemainSparseAcrossRepeatedSpawnChecks() {
        World w=World.flat();
        for(int x=1;x<79;x++) for(int z=1;z<79;z++) w.set(x,Terrain.WATER_LEVEL,z,Block.WATER);
        Vector3f p=new Vector3f(40,6,40);
        for(int i=0;i<1000;i++) { w.updateFish(.05f); w.ensureFishNear(p); }
        assertTrue(w.fish().size()<=3); assertFalse(w.fish().isEmpty());
    }
}
