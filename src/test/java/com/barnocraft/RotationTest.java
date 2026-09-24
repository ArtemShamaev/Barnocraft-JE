package com.barnocraft;

import com.jme3.math.Vector3f;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RotationTest {
    @Test void fourTurnsRestoreMeshAndCubeTexturesReallyRotate() {
        World world=World.flat(); Player player=new Player();
        world.set(20,10,20,Block.PLANKS); world.placeStairs(22,10,20,0,player);
        Mesh[] original=ChunkMesh.build(world,1,1);
        assertEquals(44,original[12].getTriangleCount());
        for(int turn=1;turn<=4;turn++) {
            assertTrue(world.rotate(20,10,20,player)); assertTrue(world.rotate(22,10,20,player));
            Mesh[] meshes=ChunkMesh.build(world,1,1);
            assertEquals(turn%4,world.rotation(22,10,20));
            if(turn==1) assertNotEquals(original[4].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind(),meshes[4].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind());
            assertOutwards(meshes[12]);
            if(turn==4) {
                assertEquals(original[4].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind(),meshes[4].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind());
                assertEquals(original[12].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind(),meshes[12].getFloatBuffer(VertexBuffer.Type.Position).duplicate().rewind());
                assertEquals(original[4].getFloatBuffer(VertexBuffer.Type.TexCoord).duplicate().rewind(),meshes[4].getFloatBuffer(VertexBuffer.Type.TexCoord).duplicate().rewind());
            }
        }
    }
    @Test void rotatingEitherDoorHalfPreservesOpenState() {
        World world=World.flat(); Player player=new Player(); world.placeDoor(20,6,20,0,player);
        world.toggleDoor(20,6,20,player);
        for(int turn=1;turn<=4;turn++) {
            assertTrue(world.rotate(20,7,20,player));
            assertEquals(turn%4,world.doorAt(20,6,20).facing()); assertTrue(world.doorAt(20,7,20).open());
        }
        assertFalse(world.rotate(10,20,10,player));
    }
    private void assertOutwards(Mesh mesh) {
        var normals=mesh.getFloatBuffer(VertexBuffer.Type.Normal);
        Vector3f a=new Vector3f(),b=new Vector3f(),c=new Vector3f(); int[] ids=new int[3];
        for(int i=0;i<mesh.getTriangleCount();i++) {
            mesh.getTriangle(i,a,b,c); mesh.getTriangle(i,ids); int n=ids[0]*3;
            assertTrue(b.subtract(a).cross(c.subtract(a)).dot(new Vector3f(normals.get(n),normals.get(n+1),normals.get(n+2)))>0);
        }
    }
}
