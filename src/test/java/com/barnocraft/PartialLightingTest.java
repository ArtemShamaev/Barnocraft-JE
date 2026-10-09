package com.barnocraft;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.Test;
import java.nio.FloatBuffer;
import static org.junit.jupiter.api.Assertions.*;

class PartialLightingTest {
    @Test void partialBlocksDoNotBlackOutTheirVoxelsOrFloor() {
        World w=World.flat();Player p=new Player();
        w.set(20,6,20,Block.STONECUTTER);w.placeBed(22,6,20,0,p);w.placeDoor(25,6,20,0,p);
        VoxelLighting l=new VoxelLighting(w,1,1);
        for(int x:new int[]{20,22,25}) assertEquals(15,l.sky(x,6,20));
        assertEquals(15,l.sky(25,7,20));
        w.set(28,6,20,Block.STONE);l=new VoxelLighting(w,1,1);
        assertEquals(0,l.sky(28,6,20),"Full stone still blocks light");
    }
    @Test void doorsAndFurnitureCarryLightOnVisibleFacesInEveryRotation() {
        for(int rotation=0;rotation<4;rotation++) {
            World w=World.flat();Player p=new Player();
            w.set(20,6,20,Block.STONECUTTER);
            for(int i=0;i<rotation;i++) w.rotate(20,6,20,p);
            assertTrue(w.placeBed(23,6,23,rotation,p));assertTrue(w.placeDoor(27,6,27,rotation,p));
            Mesh[] meshes=ChunkMesh.build(w,1,1);
            for(int material:new int[]{11,13,20,22}) {
                Mesh mesh=meshes[material];assertNotNull(mesh);
                FloatBuffer light=mesh.getFloatBuffer(VertexBuffer.Type.TexCoord2);
                FloatBuffer normals=mesh.getFloatBuffer(VertexBuffer.Type.Normal);
                assertNotNull(light,"Door must have the same light attributes as terrain");
                for(int v=0;v<mesh.getVertexCount();v++) if(normals.get(v*3+1)>=0)
                    assertTrue(light.get(v*2)>.9f,"Lit exterior face of material "+material+" at rotation "+rotation);
            }
        }
    }
    @Test void lanternLightPassesThroughDoorAndBedVoxels() {
        World w=World.flat();Player p=new Player();
        w.placeDoor(20,6,20,0,p);w.placeBed(21,6,20,0,p);w.set(19,6,20,Block.LANTERN);
        VoxelLighting l=new VoxelLighting(w,1,1);
        assertTrue(l.block(20,6,20)>0);assertTrue(l.block(21,6,20)>0);
    }
}
