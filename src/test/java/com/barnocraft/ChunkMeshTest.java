package com.barnocraft;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.math.Vector3f;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkMeshTest {
    private int triangles(Mesh[] meshes) {
        int n = 0; for (Mesh m : meshes) if (m != null) n += m.getTriangleCount(); return n;
    }
    @Test void hidesBuriedFacesAndUpdatesBothSidesOfSeam() {
        World w = World.flat();
        int left = triangles(ChunkMesh.build(w, 1, 1)), right = triangles(ChunkMesh.build(w, 2, 1));
        assertEquals(1024, left); // 256 top + 256 bottom faces, no internal chunk walls.
        w.set(31,4,20,Block.AIR);
        assertEquals(left + 10, triangles(ChunkMesh.build(w,1,1)));
        assertEquals(right + 2, triangles(ChunkMesh.build(w,2,1)));
    }
    @Test void glassExposesOpaqueFacesButHidesSharedGlassFaces() {
        World w = World.flat();
        w.set(20,10,20,Block.GLASS); w.set(21,10,20,Block.GLASS);
        Mesh[] meshes = ChunkMesh.build(w,1,1);
        assertEquals(20, meshes[5].getTriangleCount());
        w.set(21,10,20,Block.STONE);
        meshes = ChunkMesh.build(w,1,1);
        assertEquals(10, meshes[5].getTriangleCount());
        assertEquals(512 + 12, meshes[2].getTriangleCount());
    }
    @Test void trianglesFaceOutwards() {
        Mesh[] meshes = ChunkMesh.build(World.flat(),0,0);
        for (Mesh mesh : meshes) {
            if (mesh == null) continue;
            FloatBuffer normals = mesh.getFloatBuffer(VertexBuffer.Type.Normal);
            int[] indices = new int[3]; Vector3f a = new Vector3f(), b = new Vector3f(), c = new Vector3f();
            for (int i = 0; i < mesh.getTriangleCount(); i++) {
                mesh.getTriangle(i,a,b,c); mesh.getTriangle(i,indices);
                int n = indices[0] * 3;
                Vector3f normal = new Vector3f(normals.get(n),normals.get(n+1),normals.get(n+2));
                assertTrue(b.subtract(a).cross(c.subtract(a)).dot(normal) > 0);
            }
        }
    }
}
