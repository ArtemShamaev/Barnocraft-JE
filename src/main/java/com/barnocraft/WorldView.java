package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.control.BillboardControl;
import com.jme3.texture.Texture;
import java.util.*;
import java.util.concurrent.*;

/** Build nearest meshes off the render thread; attach at most one completed chunk per frame. */
public final class WorldView implements AutoCloseable {
    public final Node node = new Node("World");
    private final Node dropsNode = new Node("Dropped items");
    private final World world;
    private final Material[] materials = new Material[ChunkMesh.TEXTURES.length];
    private final Map<Integer,Node> loaded = new HashMap<>();
    private final Map<Integer,Integer> versions = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>(), dirty = new HashSet<>();
    private final ConcurrentLinkedQueue<Built> completed = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"chunk-builder"); t.setDaemon(true); return t;
    });
    private Set<Integer> desired = Set.of();
    private record Built(int key, int version, Mesh[] meshes, List<Door> doors, RuntimeException error) {}

    private final AssetManager assets;
    private TexturePack pack = TexturePack.ORIGINAL;
    public WorldView(AssetManager assets, World world) { this(assets, world, TexturePack.ORIGINAL); }
    public WorldView(AssetManager assets, World world, TexturePack pack) {
        this.assets = assets;
        this.world = world;
        this.pack = pack;
        node.attachChild(dropsNode);
        for (int i = 0; i < materials.length; i++) {
            Material m = new Material(assets,"Common/MatDefs/Light/Lighting.j3md");
            m.setTexture("DiffuseMap",texture(assets,pack.path(ChunkMesh.TEXTURES[i])));
            m.setBoolean("UseMaterialColors",true); m.setBoolean("UseFog",true);
            m.setColor("FogColor",new ColorRGBA(135/255f,206/255f,235/255f,1));
            m.setVector2("LinearFog",new Vector2f(40,64));
            m.setColor("Ambient",ColorRGBA.White); m.setColor("Diffuse",new ColorRGBA(1,1,1,i==5?.7f:1));
            if (i == 5) {
                m.setTransparent(true); m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                m.getAdditionalRenderState().setDepthWrite(false);
            }
            if (i == 7 || i == 11) {
                m.setFloat("AlphaDiscardThreshold",.5f);
                m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            }
            materials[i] = m;
        }
    }
    public void setTexturePack(TexturePack next) {
        if (next == pack) return;
        pack = next;
        for (int i=0;i<materials.length;i++) materials[i].setTexture("DiffuseMap",texture(assets,pack.path(ChunkMesh.TEXTURES[i])));
    }
    public static Texture texture(AssetManager assets, String name) {
        Texture t = assets.loadTexture("Textures/"+name);
        t.setMagFilter(Texture.MagFilter.Nearest); t.setMinFilter(Texture.MinFilter.NearestNoMipMaps); return t;
    }
    private int key(int cx, int cz) { return cx * world.chunksZ() + cz; }
    public int loadedCount() { return loaded.size(); }
    public boolean ready(float x, float z) {
        int cx = (int)x/World.CHUNK, cz = (int)z/World.CHUNK;
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++)
            if (valid(cx+dx,cz+dz) && !loaded.containsKey(key(cx+dx,cz+dz))) return false;
        return true;
    }
    private boolean valid(int cx, int cz) { return cx>=0 && cz>=0 && cx<world.chunksX() && cz<world.chunksZ(); }
    public void update(float x, float z, int radius) {
        refreshDrops();
        int cx = (int)x/World.CHUNK, cz = (int)z/World.CHUNK;
        List<Integer> nearest = new ArrayList<>();
        for (int dx=-radius; dx<=radius; dx++) for (int dz=-radius; dz<=radius; dz++)
            if (valid(cx+dx,cz+dz)) nearest.add(key(cx+dx,cz+dz));
        nearest.sort(Comparator.comparingInt(k -> {
            int dx=k/world.chunksZ()-cx,dz=k%world.chunksZ()-cz; return dx*dx+dz*dz;
        }));
        desired = new HashSet<>(nearest);
        loaded.entrySet().removeIf(e -> {
            if (desired.contains(e.getKey())) return false;
            e.getValue().removeFromParent(); return true;
        });
        Built result = completed.poll();
        if (result != null) {
            pending.remove(result.key());
            if (result.error()!=null) throw result.error();
            if (desired.contains(result.key()) && result.version()==versions.getOrDefault(result.key(),0)) {
                attach(result); dirty.remove(result.key());
            }
        }
        for (int k : nearest) {
            if (pending.size() >= 2) break;
            if (pending.contains(k) || (loaded.containsKey(k) && !dirty.contains(k))) continue;
            pending.add(k);
            int version = versions.getOrDefault(k,0);
            worker.submit(() -> {
                try {
                    Mesh[] meshes = ChunkMesh.build(world,k/world.chunksZ(),k%world.chunksZ());
                    completed.add(new Built(k,version,meshes,world.doorsInChunk(k/world.chunksZ(),k%world.chunksZ()),null));
                } catch (RuntimeException e) { completed.add(new Built(k,version,null,null,e)); }
            });
        }
        for (Material material : materials) material.setVector2("LinearFog",new Vector2f(radius*16-12,radius*16));
    }
    private void refreshDrops() {
        dropsNode.detachAllChildren();
        for (DroppedItem item:world.droppedItems()) {
            float size=item.type()==ItemType.STICK?.65f:.42f;
            Material m=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");
            m.setTexture("ColorMap",texture(assets,item.type().texture)); m.setTransparent(true);
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            Geometry g=new Geometry(item.type().name(),new Quad(size,size)); g.setMaterial(m);
            g.addControl(new BillboardControl()); g.setLocalTranslation(item.x()+.5f-size/2,item.y()+.03f,item.z()+.5f); dropsNode.attachChild(g);
        }
    }
    public void changed(int x, int z) {
        int cx=x/World.CHUNK,cz=z/World.CHUNK;
        invalidate(cx,cz);
        if (x%World.CHUNK==0) invalidate(cx-1,cz);
        if (x%World.CHUNK==15) invalidate(cx+1,cz);
        if (z%World.CHUNK==0) invalidate(cx,cz-1);
        if (z%World.CHUNK==15) invalidate(cx,cz+1);
    }
    private void invalidate(int cx,int cz) {
        if (!valid(cx,cz)) return;
        int k=key(cx,cz); versions.merge(k,1,Integer::sum); dirty.add(k);
    }
    private void attach(Built result) {
        Node old=loaded.remove(result.key()); if (old!=null) old.removeFromParent();
        Node chunk = new Node("Chunk "+result.key());
        for (int i=0;i<result.meshes().length;i++) {
            if (result.meshes()[i]==null) continue;
            Geometry g=new Geometry(ChunkMesh.TEXTURES[i],result.meshes()[i]); g.setMaterial(materials[i]);
            if (i==5) g.setQueueBucket(RenderQueue.Bucket.Transparent);
            chunk.attachChild(g);
        }
        for (Door door:result.doors()) {
            Door.Bounds b=door.bounds();
            Geometry g=new Geometry("Door",new Box((b.maxX()-b.minX())/2,1,(b.maxZ()-b.minZ())/2));
            g.setLocalTranslation((b.minX()+b.maxX())/2,door.y()+1,(b.minZ()+b.maxZ())/2);
            g.setMaterial(materials[11]); chunk.attachChild(g);
        }
        loaded.put(result.key(),chunk); node.attachChild(chunk);
    }
    @Override public void close() { worker.shutdownNow(); node.removeFromParent(); loaded.clear(); }
}
