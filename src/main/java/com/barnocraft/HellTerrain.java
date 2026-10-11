package com.barnocraft;

import java.util.Random;

/** Continuous volcanic caverns with crimson groves, lava lowlands and a basalt roof. */
final class HellTerrain {
    static final int LAVA_LEVEL=23;
    static int height(long seed,int x,int z) {
        int surface=29+(int)Math.round(Terrain.noise(seed^0x48454C4CL,x/64.0,z/64.0)*12+Terrain.noise(seed+87,x/19.0,z/19.0)*3);
        double distance=Math.hypot(x-(World.WIDTH/2+48),z-(World.DEPTH/2+24));
        // The first arrival is a dry grove, never a low platform at the bottom of a lava lake.
        if(distance<48)surface=Math.max(surface,LAVA_LEVEL+7-(int)Math.max(0,(distance-28)*.55));
        return surface;
    }
    static void generate(Region region,long seed,int ox,int oz) {
        for(int x=0;x<Region.WIDTH;x++)for(int z=0;z<Region.DEPTH;z++) {
            int wx=ox+x,wz=oz+z,floor=height(seed,wx,wz);
            int roof=83+(int)(Terrain.noise(seed^8192,wx/35.0,wz/35.0)*9);
            for(int y=0;y<=floor;y++)region.set(x,y,z,y==floor?Block.HELL_SOIL:y<5?Block.HELL_BASALT:Block.HELL_STONE);
            for(int y=floor+1;y<=LAVA_LEVEL;y++)region.set(x,y,z,Block.HELL_LAVA);
            for(int y=roof;y<roof+5;y++)region.set(x,y,z,Block.HELL_BASALT);
            if(Math.floorMod(wx*31+wz*47+(int)seed,151)==0)region.set(x,roof-1,z,Block.EMBER_CRYSTAL);
        }
        Random random=new Random(seed^0x464F52455354L^((long)ox<<32)^oz);
        for(int x=5;x<Region.WIDTH-5;x+=10)for(int z=5;z<Region.DEPTH-5;z+=10) {
            int tx=x+random.nextInt(3)-1,tz=z+random.nextInt(3)-1,ground=height(seed,ox+tx,oz+tz);
            if(ground<=LAVA_LEVEL || random.nextFloat()<.2f)continue;
            int top=ground+6+random.nextInt(5);
            for(int y=ground+1;y<=top;y++)region.set(tx,y,tz,Block.HELL_LOG);
            for(int dy=-2;dy<=2;dy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                if(dx*dx+dz*dz+dy*dy>13 || (dx==0 && dz==0 && dy<=0))continue;
                region.set(tx+dx,top+dy,tz+dz,random.nextInt(23)==0?Block.EMBER_CRYSTAL:Block.HELL_LEAVES);
            }
        }
    }
}
