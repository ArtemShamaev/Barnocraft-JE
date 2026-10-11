package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.*;

/** Seeded ruins, never an inventory item or a player-built recipe. Paired across dimensions. */
final class Portal {
    record Site(int x,int z) {
        int floor(long seed,Dimension dimension){return dimension==Dimension.SURFACE?Terrain.height(seed,x,z):Math.max(HellTerrain.LAVA_LEVEL+3,HellTerrain.height(seed,x,z));}
        Vector3f exit(World world){return new Vector3f(x+.5f,floor(world.seed(),world.dimension())+1.01f,z+2.5f);}
    }
    static List<Site> sites(long seed,int width,int depth) {
        if(width<160 || depth<160)return List.of();
        List<Site> sites=new ArrayList<>();
        sites.add(new Site(width/2+48,depth/2+24));
        Random random=new Random(seed^0x504F5254414CL);
        for(int x=96;x<width-64;x+=192)for(int z=96;z<depth-64;z+=192) {
            Site site=new Site(x+random.nextInt(41)-20,z+random.nextInt(41)-20);
            if(sites.stream().noneMatch(p->Math.abs(p.x-site.x)<64 && Math.abs(p.z-site.z)<64))sites.add(site);
        }
        return List.copyOf(sites);
    }
    static Site touching(World world,Vector3f feet) {
        if(world.get((int)Math.floor(feet.x),(int)Math.floor(feet.y+.6f),(int)Math.floor(feet.z))!=Block.FOREST_PORTAL)return null;
        return world.portals().stream().filter(p->Math.abs(feet.x-p.x())<=2.5f && Math.abs(feet.z-p.z()-.5f)<1).findFirst().orElse(null);
    }
    static void generate(Region region,long seed,Dimension dimension,int ox,int oz,int width,int depth) {
        for(Site site:sites(seed,width,depth)) {
            if(site.x()+4<ox || site.x()-4>=ox+Region.WIDTH || site.z()+6<oz || site.z()-4>=oz+Region.DEPTH)continue;
            int floor=site.floor(seed,dimension);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=6;dz++) {
                for(int y=floor+1;y<=floor+7;y++)region.set(site.x()+dx-ox,y,site.z()+dz-oz,Block.AIR);
                for(int y=floor-3;y<=floor;y++)region.set(site.x()+dx-ox,y,site.z()+dz-oz,Block.HELL_BASALT);
            }
            for(int dx=-2;dx<=2;dx++)for(int dy=0;dy<=5;dy++)region.set(site.x()+dx-ox,floor+dy,site.z()-oz,
                    Math.abs(dx)==2 || dy==0 || dy==5?Block.PORTAL_FRAME:Block.FOREST_PORTAL);
            for(int dx:new int[]{-3,3})region.set(site.x()+dx-ox,floor+1,site.z()+2-oz,Block.EMBER_CRYSTAL);
        }
    }
    static Vector3f safeExit(World world,Site portal) {
        Vector3f exit=portal.exit(world);int x=(int)exit.x,y=(int)exit.y,z=(int)exit.z;
        // Returning remains possible even if someone built on the arrival pad.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            world.set(x+dx,y-1,z+dz,Block.HELL_BASALT);
            for(int dy=0;dy<3;dy++)world.set(x+dx,y+dy,z+dz,Block.AIR);
        }
        return exit;
    }
}
