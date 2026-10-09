package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;

/** A separate, non-persistent exhibition world; never touches the player's save. */
final class LoadingPanorama implements AutoCloseable {
    final World world=World.flat();
    final WorldView view;
    private float time;
    LoadingPanorama(AssetManager assets) {
        buildLandscape();
        view=new WorldView(assets,world);
        view.setDaylight(1);
        view.node.setCullHint(Spatial.CullHint.Always);
    }
    private void buildLandscape() {
        for(int x=0;x<80;x++)for(int z=0;z<80;z++) {
            int river=40+(int)(3*Math.sin(z*.13));
            boolean wet=Math.abs(x-river)<4;
            int hill=(int)(Math.max(0,Math.sin(x*.13)*Math.cos(z*.12))*4);
            int top=wet?8:11+hill;
            for(int y=6;y<=top;y++)world.set(x,y,z,x>river || wet?Block.SAND:y==top?Block.GRASS:Block.STONE);
            if(wet)for(int y=9;y<=10;y++)world.set(x,y,z,Block.WATER);
        }
        for(int[] tree:new int[][]{{13,23},{19,32},{12,43},{25,20},{29,28},{22,48}}) {
            int x=tree[0],z=tree[1],y=World.HEIGHT-1;
            while(y>0 && world.get(x,y,z)==Block.AIR)y--;
            y++;
            for(int h=0;h<5;h++)world.set(x,y+h,z,Block.LOG);
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=3;dy<=5;dy++)
                if(Math.abs(dx)+Math.abs(dz)<4)world.set(x+dx,y+dy,z+dz,Block.LEAVES);
        }
        // Tiered stone-and-wood display spanning the two banks.
        for(int x=25;x<=57;x++)for(int z=31;z<=44;z++) {
            int top=12+Math.min(2,(44-z)/4)*2;
            for(int y=11;y<=top;y++)world.set(x,y,z,y==top?Block.PLANKS:Block.STONE);
            for(int y=top+1;y<23;y++)world.set(x,y,z,Block.AIR);
        }
        Player absent=new Player();absent.position.set(1,40,1);
        int index=0;
        for(Block block:Block.values()) {
            if(block==Block.AIR || block==Block.DOOR_TOP || block==Block.BED_HEAD || block==Block.ROTATOR)continue;
            int row=index/11,col=index%11,x=26+col*3,z=43-row*4,y=13+row*2;index++;
            if(block==Block.DOOR)world.placeDoor(x,y,z,0,absent);
            else if(block==Block.BED)world.placeBed(x,y,z,0,absent);
            else world.set(x,y,z,block);
        }
        for(int x:new int[]{30,51})for(int z=47;z<=50;z++)for(int dx=-2;dx<=2;dx++) {
            for(int y=11;y<=12;y++)world.set(x+dx,y,z,Block.PLANKS);
            for(int y=13;y<18;y++)world.set(x+dx,y,z,Block.AIR);
        }
        world.addSheep(new Sheep(30.5f,13,48.5f,8));
        world.addZombie(new Zombie(51.5f,13,48.5f,20,0));
        for(int i=0;i<4;i++)world.addFish(new Fish(40+i*.55f,10.35f,53+i*.65f,1,0));
    }
    void update(Camera camera,float dt,boolean visible) {
        view.node.setCullHint(visible?Spatial.CullHint.Never:Spatial.CullHint.Always);
        if(view.loadedCount()<25 || visible)view.update(40,40,2);
        view.setFogRange(65,85);
        if(!visible)return;
        time+=dt;world.updateFish(dt);
        camera.setLocation(new Vector3f(41+FastMath.sin(time*.08f)*3,23,69));
        camera.lookAt(new Vector3f(41,13,38),Vector3f.UNIT_Y);
    }
    boolean ready() { return view.loadedCount()>=25; }
    @Override public void close() { view.close(); }
}
