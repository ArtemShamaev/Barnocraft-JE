package com.barnocraft;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.ArrayList;
import java.util.List;

/** One mesh per texture in each chunk; hidden faces never reach the GPU. */
public final class ChunkMesh {
    public static final String[] TEXTURES = {"grass.png", "grass_2.png", "stone.png", "pesok.png", "tree_planks.png", "glass.png", "tree.png", "listia.png", "coal.png", "iron.png", "deeprock.png", "door.png", "stairs.png", "stonecutter.png", "furnace.png", "torch.png", "stone.png", "stonecutter.png", "furnace.png"};
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
                    if (block==Block.TORCH) { builders[15].torch(x,y,z); continue; }
                    if ((!block.solid() && block != Block.WATER) || block.door()) continue;
                    int rotation = world.rotation(x,y,z);
                    if (block==Block.STAIRS) { builders[12].stairs(x,y,z,rotation); continue; }
                    for (int f = 0; f < 6; f++) {
                        int[] n = rotate(NORMALS[f],rotation);
                        Block neighbor = world.get(x+n[0], y+n[1], z+n[2]);
                        // Cutout leaves do not fully cover any neighboring face, even another leaf.
                        boolean occluded = neighbor != Block.AIR && neighbor != Block.LEAVES && !neighbor.door() && neighbor != Block.STAIRS
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
                            case DEEPSTONE -> 10;
                            case STONECUTTER -> 13;
                            case FURNACE -> 14;
                            case WATER -> 16;
                            case CASTING_TABLE -> 17;
                            case WELDER -> 18;
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
                lights.add(lighting.sky(x+normal[0],y+normal[1],z+normal[2])/15f);
                lights.add(lighting.block(x+normal[0],y+normal[1],z+normal[2])/15f);
            }
            for (int i:new int[]{0,1,2,0,2,3}) indices.add(base+i);
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
