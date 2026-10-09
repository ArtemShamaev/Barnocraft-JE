package com.barnocraft;

import java.util.*;

/** Local fixed-step source/flow automaton. Rivers sleep until a nearby block changes. */
final class WaterSimulation {
    private final World world;
    final Map<Integer,Integer> levels=new HashMap<>();
    private final LinkedHashSet<Integer> pending=new LinkedHashSet<>();
    private float clock;
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    WaterSimulation(World world) { this.world=world; }
    int level(int x,int y,int z) { return levels.getOrDefault(world.key(x,y,z),0); }
    void changed(int x,int y,int z) {
        levels.remove(world.key(x,y,z));wake(x,y,z);
    }
    private void queue(int x,int y,int z) {
        if(world.inside(x,y,z))pending.add(world.key(x,y,z));
    }
    void wake(int x,int y,int z) {
        queue(x,y,z);queue(x,y-1,z);queue(x,y+1,z);
        for(int[] side:SIDES)queue(x+side[0],y,z+side[1]);
    }
    private boolean supported(int x,int y,int z) {
        Block below=world.get(x,y-1,z);
        return y==0 || below.solid() || (below==Block.WATER && level(x,y-1,z)==0);
    }
    List<int[]> update(float dt) {
        List<int[]> changed=new ArrayList<>();
        clock+=Math.max(0,Math.min(.25f,dt));
        if(clock<.12f)return changed;
        clock-=.12f;
        // Snapshot prevents newly queued cells from recursively flooding in one frame.
        List<Integer> work=new ArrayList<>(Math.min(512,pending.size()));
        var iterator=pending.iterator();
        while(iterator.hasNext() && work.size()<512) { work.add(iterator.next());iterator.remove(); }
        for(int key:work) {
            int z=key%world.depth(),v=key/world.depth(),x=v%world.width(),y=v/world.width();
            Block block=world.get(x,y,z);
            if(block!=Block.AIR && block!=Block.WATER)continue;
            if(block==Block.WATER && !levels.containsKey(key))continue; // Persistent source.
            int wanted=8;
            if(world.get(x,y+1,z)==Block.WATER)wanted=1;
            else for(int[] side:SIDES) {
                int nx=x+side[0],nz=z+side[1];
                if(world.get(nx,y,nz)==Block.WATER && supported(nx,y,nz))wanted=Math.min(wanted,level(nx,y,nz)+1);
            }
            if(wanted>7) {
                if(block!=Block.WATER)continue;
                world.flowingWater(x,y,z,-1);
            } else {
                if(block==Block.WATER && level(x,y,z)==wanted)continue;
                world.flowingWater(x,y,z,wanted);
            }
            changed.add(new int[]{x,y,z});wake(x,y,z);
        }
        return changed;
    }
}
