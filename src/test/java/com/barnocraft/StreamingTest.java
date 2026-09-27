package com.barnocraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StreamingTest {
    @Test void thousandChunksAreLazyAndEvictionPreservesEdits() {
        World world=new World(42);
        assertEquals(2400,world.chunksX()*world.chunksZ()); assertEquals(0,world.cachedRegions());
        world.set(15,25,15,Block.PLANKS); world.set(16,25,15,Block.GLASS);
        Block natural=world.get(17,5,16); assertEquals(1,world.cachedRegions());
        for (int x=0;x<world.width();x+=80) for(int z=0;z<world.depth();z+=80) world.get(x,5,z);
        assertTrue(world.cachedRegions()<=World.REGION_CACHE_LIMIT);
        assertEquals(Block.PLANKS,world.get(15,25,15)); assertEquals(Block.GLASS,world.get(16,25,15));
        assertEquals(natural,world.get(17,5,16));
        assertTrue(world.inside(World.WIDTH-1,31,World.DEPTH-1));
        assertFalse(world.inside(World.WIDTH,1,0));
    }
    @Test void generationOrderDoesNotChangeRegionBoundaries() {
        World a=new World(812),b=new World(812);
        int[][] locations={{79,5,79},{80,5,80},{319,9,199},{320,9,200},{639,4,399}};
        for(int[] p:locations) a.get(p[0],p[1],p[2]);
        for(int i=locations.length-1;i>=0;i--) { int[] p=locations[i]; b.get(p[0],p[1],p[2]); }
        for(int[] p:locations) assertEquals(a.get(p[0],p[1],p[2]),b.get(p[0],p[1],p[2]));
        int steepest=0;
        for(int x=1;x<World.WIDTH;x++) steepest=Math.max(steepest,Math.abs(a.groundHeight(x,123)-a.groundHeight(x-1,123)));
        assertTrue(steepest>10,"Generated terrain retains steep canyon walls");
    }
}
