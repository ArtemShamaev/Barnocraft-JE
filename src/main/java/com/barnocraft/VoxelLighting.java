package com.barnocraft;

/** Two light channels, propagated through open voxels; a 15-cell halo avoids chunk seams. */
final class VoxelLighting {
    private static final int SIZE=World.CHUNK+30, LAYER=SIZE*SIZE;
    private final int ox,oz;
    private final byte[] sky=new byte[LAYER*World.HEIGHT],block=new byte[sky.length];
    private final boolean[] open=new boolean[sky.length];
    VoxelLighting(World world,int cx,int cz) {
        ox=cx*World.CHUNK-15; oz=cz*World.CHUNK-15;
        for(int x=0;x<SIZE;x++) for(int z=0;z<SIZE;z++) {
            boolean exposed=true;
            for(int y=World.HEIGHT-1;y>=0;y--) {
                int i=index(x,y,z); Block b=world.get(ox+x,y,oz+z);
                open[i]=!b.solid() || b==Block.GLASS || b==Block.LEAVES;
                if(!open[i]) exposed=false;
                if(exposed) sky[i]=15;
                if(b==Block.TORCH) block[i]=14;
            }
        }
        spread(sky); spread(block);
    }
    private void spread(byte[] levels) {
        // Descending buckets allow each level to propagate without an oversized queue.
        for(int level=15;level>1;level--) for(int i=0;i<levels.length;i++) {
            if(levels[i]!=level) continue;
            int x=i%SIZE,z=(i%LAYER)/SIZE,y=i/LAYER;
            if(x>0) transfer(levels,i-1,level);
            if(x<SIZE-1) transfer(levels,i+1,level);
            if(z>0) transfer(levels,i-SIZE,level);
            if(z<SIZE-1) transfer(levels,i+SIZE,level);
            if(y>0) transfer(levels,i-LAYER,level);
            if(y<World.HEIGHT-1) transfer(levels,i+LAYER,level);
        }
    }
    private void transfer(byte[] levels,int i,int level) {
        if(open[i] && levels[i]<level-1) levels[i]=(byte)(level-1);
    }
    private static int index(int x,int y,int z) { return y*LAYER+z*SIZE+x; }
    int sky(int x,int y,int z) { return sample(sky,x,y,z); }
    int block(int x,int y,int z) { return sample(block,x,y,z); }
    private int sample(byte[] data,int x,int y,int z) {
        x-=ox; z-=oz;
        if(x<0 || x>=SIZE || z<0 || z>=SIZE || y<0 || y>=World.HEIGHT) return 0;
        return data[index(x,y,z)];
    }
}
