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
}
