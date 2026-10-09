package com.barnocraft;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaterTest {
    @Test void waterHasVisibleBanksAndBottomRemainsVisible() {
        World w = World.flat();
        int ground = ChunkMesh.build(w,1,1)[1].getTriangleCount();
        w.set(20,6,20,Block.WATER);
        w.set(20,7,20,Block.WATER);
        w.set(21,7,20,Block.WATER);
        Mesh[] meshes = ChunkMesh.build(w,1,1);
        assertEquals(ground,meshes[1].getTriangleCount());
        assertTrue(meshes[16].getTriangleCount()>4,"Waterfall sides must be visible");
        var positions=meshes[16].getFloatBuffer(VertexBuffer.Type.Position);
        var normals=meshes[16].getFloatBuffer(VertexBuffer.Type.Normal);
        int topVertices=0;
        for(int i=0;i<meshes[16].getVertexCount();i++)if(normals.get(i*3+1)>.5f) {
            assertEquals(7.9f,positions.get(i*3+1),.0001f);topVertices++;
        }
        assertEquals(8,topVertices,"Only the exposed top faces are drawn");
    }
    @Test void playerAndOrdinaryRayPassThroughButBucketCollectsWater() {
        World w = World.flat();
        w.set(40,6,40,Block.WATER);
        w.set(40,7,40,Block.WATER);
        Player p = new Player(); p.position.set(40.5f,6,40.5f);
        assertFalse(p.collides(w));
        assertTrue(p.inWater(w));
        Vector3f origin = new Vector3f(40.5f,9,40.5f);
        Vector3f down = new Vector3f(0,-1,0);
        assertEquals(5,w.raycast(origin,down,6).y());
        Inventory inventory = new Inventory();
        assertTrue(inventory.acceptSlotAt(0,new Inventory.Slot(ItemType.BUCKET,1)));
        assertEquals(7,w.useBucket(inventory,0,origin,down).y());
        assertEquals(Block.AIR,w.get(40,7,40));
        assertEquals(ItemType.WATER_BUCKET,inventory.itemAt(0));
    }
}
