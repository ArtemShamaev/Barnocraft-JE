package com.barnocraft;

import com.jme3.scene.VertexBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChestRenderingTest {
    @Test void chestTextureHasNoTransparentHoles() {
        for(String prefix:new String[]{"","soft/"}) {
            var image=WorldView.texture(null,prefix+"chest.png").getImage();
            var pixels=com.jme3.texture.image.ImageRaster.create(image);
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                assertEquals(1f,pixels.getPixel(x,y).a,0.001f);
        }
    }
    @Test void opaqueChestHasLitExterior() {
        World world=World.flat();world.set(20,6,20,Block.CHEST);
        assertTrue(Block.CHEST.blocksLight());
        assertEquals(0,new VoxelLighting(world,1,1).sky(20,6,20));
        var mesh=ChunkMesh.build(world,1,1)[25];
        var normals=mesh.getFloatBuffer(VertexBuffer.Type.Normal);
        var light=mesh.getFloatBuffer(VertexBuffer.Type.TexCoord2);
        for(int v=0;v<mesh.getVertexCount();v++)if(normals.get(v*3+1)>=0)
            assertTrue(light.get(v*2)>.8f,"Chest exterior must sample light outside its opaque voxel");
    }
}
