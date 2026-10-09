package com.barnocraft;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;

/** One mesh per texture in each chunk; hidden faces never reach the GPU. */
public final class ChunkMesh {
    public static final String[] TEXTURES = {"grass.png", "grass_2.png", "stone.png", "pesok.png", "tree_planks.png", "glass.png", "tree.png", "listia.png", "coal.png", "iron.png", "deeprock.png", "door.png", "stairs.png", "stonecutter.png", "furnace.png", "torch.png", "water.png", "casting_table.png", "welder.png", "lantern.png", "bed.png", "furnace_side.png", "pillow.png", "gold.png", "farmland.png", "chest.png"};
    private static final int[][] NORMALS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final int[][] U = {{0,0,-1},{0,0,1},{1,0,0},{1,0,0},{1,0,0},{-1,0,0}};
    private static final int[][] V = {{0,1,0},{0,1,0},{0,0,-1},{0,0,1},{0,1,0},{0,1,0}};
    private static final int[][] UV = {{0,0},{1,0},{1,1},{0,1}};

    public static Mesh[] build(World world, int cx, int cz) {
        Builder[] builders = new Builder[TEXTURES.length];
        VoxelLighting lighting=new VoxelLighting(world,cx,cz);
        for (int i = 0; i < builders.length; i++) builders[i] = new Builder(lighting);
        for (int x = cx * World.CHUNK; x < (cx + 1) * World.CHUNK; x++)
            for (int z = cz * World.CHUNK; z < (cz + 1) * World.CHUNK; z++)
                for (int y = 0; y < World.HEIGHT; y++) {
                    Block block = world.get(x, y, z);
                    if (block==Block.WATER) {
                        float height=world.waterHeight(x,y,z);
                        if(world.get(x,y+1,z)==Block.AIR)builders[16].waterFace(x,y,z,2,0,height);
                        for(int f:new int[]{0,1,4,5}) {
                            int nx=x+NORMALS[f][0],nz=z+NORMALS[f][2];
                            Block neighbor=world.get(nx,y,nz);
                            float lower=neighbor==Block.WATER?world.waterHeight(nx,y,nz):0;
                            if((neighbor==Block.AIR || neighbor==Block.WATER) && lower<height)
                                builders[16].waterFace(x,y,z,f,lower,height);
                        }
                        if(world.get(x,y-1,z)==Block.AIR)builders[16].waterFace(x,y,z,3,0,height);
                        continue;
                    }
                    if(block==Block.DOOR) {
                        Door door=world.doorAt(x,y,z);
                        if(door!=null) {
                            Door.Bounds bounds=door.bounds();
                            builders[11].cuboid(x,y,z,(bounds.minX()+bounds.maxX())/2-x,1,(bounds.minZ()+bounds.maxZ())/2-z,
                                    bounds.maxX()-bounds.minX(),2,bounds.maxZ()-bounds.minZ(),0);
                        }
                        continue;
                    }
                    if(block==Block.LANTERN) {
                        builders[19].cuboid(x,y,z,.5f,.10f,.5f,.44f,.16f,.44f,0);
                        builders[19].cuboid(x,y,z,.5f,.38f,.5f,.32f,.42f,.32f,0);
                        builders[19].cuboid(x,y,z,.5f,.63f,.5f,.44f,.10f,.44f,0);
                        builders[21].cuboid(x,y,z,.5f,.75f,.5f,.16f,.16f,.08f,0);
                        continue;
                    }
                    if(block.bed()) {
                        int r=world.rotation(x,y,z);
                        builders[4].cuboid(x,y,z,.5f,.27f,.5f,1,.16f,1,r);
                        builders[20].cuboid(x,y,z,.5f,.46f,.5f,.94f,.22f,1,r);
                        for(float bx:new float[]{.12f,.88f})
                            builders[4].cuboid(x,y,z,bx,.12f,block==Block.BED?.12f:.88f,.15f,.24f,.15f,r);
                        if(block==Block.BED_HEAD) builders[22].cuboid(x,y,z,.5f,.58f,.74f,.78f,.04f,.38f,r);
                        continue;
                    }
                    if (block==Block.TORCH) { builders[15].torch(x,y,z); continue; }
                    if(block.crop()) {
                        float height=.18f+block.cropStage()*.18f;
                        builders[7].cuboid(x,y,z,.5f,height/2,.5f,.16f,height,.16f,0);
                        if(block.cropStage()>=2)builders[7].cuboid(x,y,z,.5f,height,.5f,.42f,.16f,.42f,0);
                        continue;
                    }
                    if(block==Block.FARMLAND) {
                        builders[24].face(x,y,z,2,0,.5f,.5f,.5f,1);
                        for(int f:new int[]{0,1,3,4,5})builders[3].face(x,y,z,f,0,.5f,.5f,.5f,1);
                        continue;
                    }
                    if(block==Block.CHEST) {
                        int firstChestVertex=builders[25].positions.size()/3;
                        builders[25].cuboid(x,y,z,.5f,.45f,.5f,.88f,.78f,.78f,0);
                        builders[25].cuboid(x,y,z,.5f,.77f,.5f,.92f,.18f,.82f,0);
                        builders[25].cuboid(x,y,z,.5f,.45f,.105f,.12f,.16f,.025f,0);
                        // Opaque inset models sample outside their light-blocking voxel.
                        Builder chest=builders[25];
                        for(int v=firstChestVertex;v<chest.positions.size()/3;v++) {
                            int lx=x+chest.normals.get(v*3).intValue();
                            int ly=y+chest.normals.get(v*3+1).intValue();
                            int lz=z+chest.normals.get(v*3+2).intValue();
                            chest.lights.set(v*2,lighting.sky(lx,ly,lz)/15f);
                            chest.lights.set(v*2+1,lighting.block(lx,ly,lz)/15f);
                        }
                        continue;
                    }
                    if ((!block.solid() && block != Block.WATER) || block.door()) continue;
                    int rotation = world.rotation(x,y,z);
                    if (block==Block.STONECUTTER) {
                        builders[2].cuboid(x,y,z,.5f,.30f,.5f,1,.60f,1,rotation);
                        builders[13].cuboid(x,y,z,.5f,.70f,.5f,.10f,.58f,.70f,rotation);
                        for(int tooth=0;tooth<5;tooth++)
                            builders[13].cuboid(x,y,z,.5f,.98f,.22f+tooth*.14f,.14f,.04f,.07f,rotation);
                        continue;
                    }
                    if (block==Block.STAIRS) { builders[12].stairs(x,y,z,rotation); continue; }
                    for (int f = 0; f < 6; f++) {
                        int[] n = rotate(NORMALS[f],rotation);
                        Block neighbor = world.get(x+n[0], y+n[1], z+n[2]);
                        // Cutout leaves do not fully cover any neighboring face, even another leaf.
                        boolean occluded = neighbor != Block.AIR && neighbor != Block.WATER && neighbor != Block.LEAVES && neighbor != Block.CHEST && !neighbor.door() && neighbor != Block.STAIRS && neighbor != Block.STONECUTTER && !neighbor.bed() && neighbor != Block.LANTERN
                                && (neighbor != Block.GLASS || block == Block.GLASS);
                        if (occluded || block==Block.WATER && neighbor==Block.WATER) continue;
                        int texture = switch (block) {
                            case GRASS -> f == 2 ? 1 : 0;
                            case STONE -> 2;
                            case SAND -> 3;
                            case PLANKS -> 4;
                            case GLASS -> 5;
                            case LOG -> 6;
                            case LEAVES -> 7;
                            case COAL_ORE -> 8;
                            case IRON_ORE -> 9;
                            case GOLD_ORE -> 23;
                            case FARMLAND -> 3;
                            case CHEST -> 25;
                            case DEEPSTONE -> 10;
                            case STONECUTTER -> 13;
                            case FURNACE -> f==4?14:21;
                            case WATER -> 16;
                            case CASTING_TABLE -> f==2?17:4;
                            case WELDER -> f==2 || f==4?18:2;
                            default -> throw new IllegalStateException();
                        };
                        builders[texture].face(x,y,z,f,rotation,.5f,.5f,.5f,1);
                    }
                }
        Mesh[] result = new Mesh[builders.length];
        for (int i = 0; i < builders.length; i++) result[i] = builders[i].mesh();
        return result;
    }

    private static int[] rotate(int[] source, int rotation) {
        int x=source[0],z=source[2];
        for (int turn=0;turn<rotation;turn++) { int oldX=x; x=-z; z=oldX; }
        return new int[]{x,source[1],z};
    }

    private static final class Builder {
        final VoxelLighting lighting;
        final List<Float> positions = new ArrayList<>(), normals = new ArrayList<>(), uv = new ArrayList<>(), lights = new ArrayList<>();
        final List<Integer> indices = new ArrayList<>();
        Builder(VoxelLighting lighting) { this.lighting=lighting; }
        void cuboid(int x,int y,int z,float cx,float cy,float cz,float sx,float sy,float sz,int rotation) {
            int start=positions.size();
            for(int f=0;f<6;f++) face(x,y,z,f,0,.5f,.5f,.5f,1);
            for(int i=start;i<positions.size();i+=3) {
                float px=cx+(positions.get(i)-x-.5f)*sx;
                float py=cy+(positions.get(i+1)-y-.5f)*sy;
                float pz=cz+(positions.get(i+2)-z-.5f)*sz;
                for(int turn=0;turn<rotation;turn++) { float old=px; px=1-pz; pz=old; }
                positions.set(i,x+px); positions.set(i+1,y+py); positions.set(i+2,z+pz);
                int[] n=rotate(new int[]{normals.get(i).intValue(),normals.get(i+1).intValue(),normals.get(i+2).intValue()},rotation);
                for(int a=0;a<3;a++) normals.set(i+a,(float)n[a]);
            }
            for(int vertex=start/3;vertex<positions.size()/3;vertex+=4) lightFace(vertex);
        }
        void waterFace(int x,int y,int z,int face,float bottom,float top) {
            int start=positions.size();
            face(x,y,z,face,0,.5f,.5f,.5f,1);
            for(int i=start+1;i<positions.size();i+=3)
                positions.set(i,y+bottom+(positions.get(i)-y)*(top-bottom));
            lightFace(start/3);
        }
        void torch(int x,int y,int z) {
            // A torch is a small solid support with a visible flame.  The old
            // four one-sided pixels looked like an X-Ray marker and had no
            // volume for ray picking.
            for (int f=0; f<6; f++) face(x,y,z,f,0,.5f,.34f,.5f,.14f);
            face(x,y,z,2,0,.5f,.76f,.5f,.28f);
            face(x,y,z,3,0,.5f,.76f,.5f,.28f);
            face(x,y,z,4,0,.5f,.76f,.5f,.28f);
            face(x,y,z,5,0,.5f,.76f,.5f,.28f);
        }
        void stairs(int x, int y, int z, int rotation) {
            for (int hx=0;hx<2;hx++) for (int hy=0;hy<2;hy++) for (int hz=0;hz<2;hz++) {
                if (!Stairs.filled(hx,hy,hz)) continue;
                for (int f=0;f<6;f++) {
                    int[] n=NORMALS[f];
                    if (!Stairs.filled(hx+n[0],hy+n[1],hz+n[2]))
                        face(x,y,z,f,rotation,hx*.5f+.25f,hy*.5f+.25f,hz*.5f+.25f,.5f);
                }
            }
        }
        void face(int x, int y, int z, int f, int rotation, float cx, float cy, float cz, float size) {
            int base=positions.size()/3;
            int[] normal=rotate(NORMALS[f],rotation);
            for (int[] tex:UV) {
                float[] p={cx,cy,cz};
                for (int a=0;a<3;a++)
                    p[a]+=NORMALS[f][a]*size*.5f + U[f][a]*(tex[0]-.5f)*size + V[f][a]*(tex[1]-.5f)*size;
                float u=.5f,v=.5f;
                for (int a=0;a<3;a++) { u+=(p[a]-.5f)*U[f][a]; v+=(p[a]-.5f)*V[f][a]; }
                for (int turn=0;turn<rotation;turn++) { float px=p[0]; p[0]=1-p[2]; p[2]=px; }
                positions.add(x+p[0]); positions.add(y+p[1]); positions.add(z+p[2]);
                for (int a=0;a<3;a++) normals.add((float)normal[a]);
                uv.add(u); uv.add(v);
                lights.add(0f); lights.add(0f);
            }
            lightFace(base);
            for (int i:new int[]{0,1,2,0,2,3}) indices.add(base+i);
        }
        private void lightFace(int vertex) {
            float[] center=new float[3];
            for(int v=0;v<4;v++) for(int axis=0;axis<3;axis++) center[axis]+=positions.get((vertex+v)*3+axis)*.25f;
            int[] cell=new int[3];
            for(int axis=0;axis<3;axis++) cell[axis]=(int)Math.floor(center[axis]+normals.get(vertex*3+axis)*.002f);
            float sky=lighting.sky(cell[0],cell[1],cell[2])/15f;
            float block=lighting.block(cell[0],cell[1],cell[2])/15f;
            for(int v=0;v<4;v++) { lights.set((vertex+v)*2,sky); lights.set((vertex+v)*2+1,block); }
        }
        Mesh mesh() {
            if (indices.isEmpty()) return null;
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(array(positions)));
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(array(normals)));
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(array(uv)));
            mesh.setBuffer(VertexBuffer.Type.TexCoord2, 2, BufferUtils.createFloatBuffer(array(lights)));
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices.stream().mapToInt(i -> i).toArray()));
            mesh.updateBound(); mesh.setStatic();
            return mesh;
        }
        float[] array(List<Float> source) {
            float[] result = new float[source.size()];
            for (int i = 0; i < result.length; i++) result[i] = source.get(i);
            return result;
        }
    }
}
