package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.control.BillboardControl;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;

/** Build nearest meshes off the render thread; attach at most one completed chunk per frame. */
public final class WorldView implements AutoCloseable {
    public final Node node = new Node("World");
    private final Node dropsNode = new Node("Dropped items");
    private final Node sheepNode = new Node("Sheep");
    private final Node zombieNode = new Node("Zombies");
    private final Node fishNode = new Node("Yas fish");
    private final List<Node> sheepModels = new ArrayList<>();
    private final List<Node> zombieModels = new ArrayList<>();
    private final List<Node> fishModels = new ArrayList<>();
    private final List<PointLight> torchLights = new ArrayList<>();
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
    private static final Map<String,Texture> GENERATED_TEXTURES = new HashMap<>();
    private TexturePack pack = TexturePack.ORIGINAL;
    private int lastTorchRevision=-1,lastTorchChunkX=Integer.MIN_VALUE,lastTorchChunkZ=Integer.MIN_VALUE;
    public WorldView(AssetManager assets, World world) { this(assets, world, TexturePack.ORIGINAL); }
    public WorldView(AssetManager assets, World world, TexturePack pack) {
        this.assets = assets;
        this.world = world;
        this.pack = pack;
        node.attachChild(dropsNode);
        node.attachChild(sheepNode);
        node.attachChild(zombieNode);
        node.attachChild(fishNode);
        for(int i=0;i<4;i++) {
            PointLight light=new PointLight();
            light.setColor(new ColorRGBA(1f,.67f,.32f,1));
            light.setRadius(12f);
            torchLights.add(light); node.addLight(light);
        }
        for (int i = 0; i < materials.length; i++) {
            Material m = new Material(assets,"MatDefs/Voxel.j3md");
            m.setTexture("DiffuseMap",texture(assets,pack.path(ChunkMesh.TEXTURES[i])));
            m.setBoolean("UseMaterialColors",true); m.setBoolean("UseFog",true);
            m.setColor("FogColor",new ColorRGBA(135/255f,206/255f,235/255f,1));
            m.setVector2("LinearFog",new Vector2f(40,64));
            m.setColor("Ambient",ColorRGBA.White); m.setColor("Diffuse",new ColorRGBA(1,1,1,i==5?.7f:1));
            if (i == 5) {
                m.setTransparent(true); m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                m.getAdditionalRenderState().setDepthWrite(false);
            }
            if (i == 7 || i == 11 || i == 15) {
                m.setFloat("AlphaDiscardThreshold",.5f);
                m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            }
            materials[i] = m;
        }
        setHeldTorch(null);
        setDaylight(.35f);
    }
    public void setTexturePack(TexturePack next) {
        if (next == pack) return;
        pack = next;
        for (int i=0;i<materials.length;i++) materials[i].setTexture("DiffuseMap",texture(assets,pack.path(ChunkMesh.TEXTURES[i])));
    }
    public void setDaylight(float value) {
        for(Material material:materials) material.setFloat("Daylight",Math.max(0,Math.min(1,value)));
    }
    /** Lights terrain materials around the torch held by the player. */
    public void setHeldTorch(Vector3f position) {
        boolean enabled = position != null;
        for (Material material : materials) {
            material.setBoolean("HeldTorch", enabled);
            if (enabled) material.setVector3("TorchPos", position);
        }
    }
    public static Texture texture(AssetManager assets, String name) {
        if (name.endsWith("stonecutter.png") || name.endsWith("sharp_stick.png") || name.endsWith("stone_axe.png")
                || name.endsWith("stone_pickaxe.png") || name.endsWith("stone_shovel.png") || name.endsWith("furnace.png")
                || name.endsWith("iron_ingot.png") || name.endsWith("iron_axe.png")
                || name.endsWith("iron_pickaxe.png") || name.endsWith("iron_shovel.png")
                || name.endsWith("mutton.png") || name.endsWith("wool.png") || name.endsWith("torch.png")
                || name.endsWith("casting_table.png") || name.endsWith("welder.png")
                || name.endsWith("bucket_mold.png") || name.endsWith("axe_mold.png")
                || name.endsWith("pickaxe_mold.png") || name.endsWith("shovel_mold.png")
                || name.endsWith("bucket.png") || name.endsWith("water_bucket.png")) {
            synchronized (GENERATED_TEXTURES) {
                return GENERATED_TEXTURES.computeIfAbsent(name, key -> key.endsWith("stonecutter.png")
                        ? createStonecutterTexture() : key.endsWith("stone_axe.png")
                ? createStoneAxeTexture() : key.endsWith("stone_pickaxe.png")
                ? createStonePickaxeTexture() : key.endsWith("stone_shovel.png")
                        ? createStoneShovelTexture() : key.endsWith("furnace.png")
                        ? createFurnaceTexture() : key.endsWith("iron_ingot.png")
                        ? createIronIngotTexture() : key.endsWith("iron_axe.png")
                        ? createIronAxeTexture() : key.endsWith("iron_pickaxe.png")
                        ? createIronPickaxeTexture() : key.endsWith("iron_shovel.png")
                        ? createIronShovelTexture() : key.endsWith("mutton.png")
                        ? createMuttonTexture() : key.endsWith("wool.png")
                        ? createWoolTexture() : key.endsWith("torch.png")
                        ? createTorchTexture() : createWorkshopTexture(key));
            }
        }
        Texture t;
        try {
            t = assets.loadTexture("Textures/"+name);
        } catch (AssetNotFoundException e) {
            int separator = name.lastIndexOf('/');
            if (separator < 0) throw e;
            t = assets.loadTexture("Textures/"+name.substring(separator+1));
        }
        t.setMagFilter(Texture.MagFilter.Nearest); t.setMinFilter(Texture.MinFilter.NearestNoMipMaps); return t;
    }
    private static Texture createStonecutterTexture() {
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        int[] stone = {0xff777d7c,0xff858b89,0xff686f6e,0xff929895};
        for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
            boolean seam = y%8==0 || (x+(y<8?0:4))%8==0;
            int color = seam ? 0xff4e5555 : stone[(x*3+y*5)%stone.length];
            if (x>=3 && x<=12 && y>=3 && y<=12) color = 0xff343a3b;
            if (x>=4 && x<=11 && y>=4 && y<=11) color = 0xff717b7c;
            if (x>=5 && x<=10 && y>=5 && y<=10) color = 0xffaab2af;
            if ((x==5 || x==10) && (y==5 || y==10)) color = 0xffd0b064;
            if ((x==6 && y>=9) || (x==7 && y==8) || (x==8 && y==7) || (x==9 && y==6)) color = 0xff394448;
            if ((x==7 && y==9) || (x==8 && y==8) || (x==9 && y==7) || (x==10 && y==6)) color = 0xffe2e5dc;
            image.setRGB(x,y,color);
        }
        Texture texture = new Texture2D(new AWTLoader().load(image,true));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }
    private static Texture createSharpStickTexture() {
        BufferedImage image = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for (int i=0;i<11;i++) {
            int x=3+i, y=12-i;
            image.setRGB(x,y,0xff53371f);
            if (i<10) image.setRGB(x+1,y,0xffa8753d);
            if (i>0) image.setRGB(x-1,y,0xff76502c);
        }
        image.setRGB(13,1,0xffe2dfd0);
        image.setRGB(13,2,0xffbfc7c3);
        image.setRGB(12,2,0xffe2dfd0);
        Texture texture = new Texture2D(new AWTLoader().load(image,true));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }
    private static Texture createStoneAxeTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for (int i=0;i<9;i++) {
            int x=4+i,y=12-i;
            image.setRGB(x,y,0xff76502c);
            if (x+1<16) image.setRGB(x+1,y,0xffa8753d);
        }
        int[][] head={{8,3},{9,2},{10,2},{11,2},{12,3},{13,4},{13,5},{12,6},{11,6},{10,5},{9,4}};
        for (int[] pixel:head) image.setRGB(pixel[0],pixel[1],0xff596366);
        image.setRGB(9,3,0xffc0c7c5); image.setRGB(10,3,0xff899391); image.setRGB(12,4,0xffa7b0ad);
        Texture texture=new Texture2D(new AWTLoader().load(image,true));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }
    private static Texture createStonePickaxeTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for (int i=0;i<9;i++) {
            int x=3+i,y=12-i;
            image.setRGB(x,y,0xff76502c);
            if (x+1<16) image.setRGB(x+1,y,0xffa8753d);
        }
        int[][] head={{3,5},{4,4},{5,4},{6,5},{7,5},{8,4},{9,4},{10,5},{11,5},{12,6}};
        for (int[] pixel:head) image.setRGB(pixel[0],pixel[1],0xff596366);
        image.setRGB(4,5,0xffc0c7c5); image.setRGB(7,6,0xff899391); image.setRGB(10,6,0xffa7b0ad);
        return pixelTexture(image);
    }
    private static Texture createStoneShovelTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for (int i=0;i<9;i++) {
            int x=4+i,y=12-i;
            image.setRGB(x,y,0xff76502c);
            if (x+1<16) image.setRGB(x+1,y,0xffa8753d);
        }
        image.setRGB(10,2,0xff596366); image.setRGB(11,2,0xff899391);
        image.setRGB(9,3,0xff596366); image.setRGB(10,3,0xffc0c7c5); image.setRGB(11,3,0xff899391); image.setRGB(12,3,0xff596366);
        image.setRGB(10,4,0xff596366); image.setRGB(11,4,0xff596366);
        return pixelTexture(image);
    }
    private static Texture createFurnaceTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        int[][] pixels={
                {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
                {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0},
                {0,1,2,2,2,2,2,2,2,2,2,2,2,2,1,0},
                {0,1,2,3,3,3,3,3,3,3,3,3,3,2,1,0},
                {0,1,2,3,4,4,4,4,4,4,4,4,3,2,1,0},
                {0,1,2,3,4,5,5,5,5,5,5,4,3,2,1,0},
                {0,1,2,3,4,5,5,5,5,5,5,4,3,2,1,0},
                {0,1,2,3,4,5,5,5,5,5,5,4,3,2,1,0},
                {0,1,2,3,4,5,5,5,5,5,5,4,3,2,1,0},
                {0,1,2,3,4,4,4,4,4,4,4,4,3,2,1,0},
                {0,1,2,3,3,3,3,3,3,3,3,3,3,2,1,0},
                {0,1,2,2,2,2,2,2,2,2,2,2,2,2,1,0},
                {0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0}
        };
        int[] colors={0x00000000,0xff303536,0xff687072,0xffa0a6a3,0xff41494b,0xffd2652e};
        for(int y=0;y<pixels.length;y++) for(int x=0;x<16;x++) image.setRGB(x,y,colors[pixels[y][x]]);
        return pixelTexture(image);
    }
    private static Texture createWorkshopTexture(String name) {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        int base=name.endsWith("welder.png")?0xff49636a:name.endsWith("casting_table.png")?0xff6b5544:0xff777d7c;
        for(int y=1;y<15;y++) for(int x=1;x<15;x++) {
            int shade=((x+y)%4==0)?18:0; image.setRGB(x,y,(0xff<<24)|Math.min(255,((base>>16)&255)+shade)<<16
                    |Math.min(255,((base>>8)&255)+shade)<<8|Math.min(255,(base&255)+shade));
        }
        if(name.endsWith("bucket.png") || name.endsWith("water_bucket.png")) {
            for(int y=5;y<13;y++) for(int x=4;x<12;x++) image.setRGB(x,y,name.endsWith("water_bucket.png")?0xff3b86b0:0xffa9b4b5);
            for(int x=5;x<11;x++) { image.setRGB(x,4,0xff596366); image.setRGB(x,13,0xff596366); }
        } else if(name.contains("mold")) {
            for(int y=4;y<12;y++) for(int x=3;x<13;x++) if(x==3||x==12||y==4||y==11) image.setRGB(x,y,0xffc28b54);
            image.setRGB(7,7,0xffe0b878); image.setRGB(8,7,0xffe0b878);
        } else {
            for(int x=3;x<13;x++) { image.setRGB(x,3,0xffc3a16b); image.setRGB(x,12,0xff302b28); }
            for(int y=4;y<12;y++) { image.setRGB(3,y,0xff302b28); image.setRGB(12,y,0xff302b28); }
        }
        return pixelTexture(image);
    }
    private static Texture createIronIngotTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=5;y<=10;y++) for(int x=3;x<=12;x++) {
            int color=y==5?0xffe2e5e2:y==10?0xff677174:x==3||x==12?0xff929b9c:0xffc3cbca;
            if (x==5 && y==6 || x==10 && y==9) color=0xfff0f1eb;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createMuttonTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=4;y<=11;y++) for(int x=3;x<=12;x++) {
            int color=(x==3 || x==12 || y==4 || y==11)?0xff74402e:0xffb85e55;
            if((x+y)%4==0) color=0xffd6a08a;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createWoolTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=3;y<=12;y++) for(int x=3;x<=12;x++) {
            int color=((x+y)%4==0)?0xffb9b8aa:0xffe8e5d4;
            if(x==3 || x==12 || y==3 || y==12) color=0xff77786f;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createTorchTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=5;y<15;y++) {
            int x=7+(y%2==0?0:1);
            image.setRGB(x,y,0xff754623); image.setRGB(x+1,y,0xffaa7138);
        }
        image.setRGB(7,4,0xffffd34e); image.setRGB(8,4,0xffff8e32);
        image.setRGB(7,3,0xffff8e32); image.setRGB(8,3,0xffffe66b);
        image.setRGB(6,5,0xffffa632); image.setRGB(9,5,0xffffa632);
        return pixelTexture(image);
    }
    private static Texture createIronAxeTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        drawHandle(image);
        int[][] head={{8,3},{9,2},{10,2},{11,2},{12,3},{13,4},{13,5},{12,6},{11,6},{10,5},{9,4}};
        drawIronHead(image,head);
        return pixelTexture(image);
    }
    private static Texture createIronPickaxeTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        drawHandle(image);
        int[][] head={{3,5},{4,4},{5,4},{6,5},{7,5},{8,4},{9,4},{10,5},{11,5},{12,6}};
        drawIronHead(image,head);
        return pixelTexture(image);
    }
    private static Texture createIronShovelTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        drawHandle(image);
        int[][] head={{9,2},{10,2},{11,2},{8,3},{9,3},{10,3},{11,3},{12,3},{9,4},{10,4},{11,4}};
        drawIronHead(image,head);
        return pixelTexture(image);
    }
    private static void drawHandle(BufferedImage image) {
        for (int i=0;i<9;i++) {
            int x=4+i,y=12-i;
            image.setRGB(x,y,0xff76502c);
            if (x+1<16) image.setRGB(x+1,y,0xffa8753d);
        }
    }
    private static void drawIronHead(BufferedImage image,int[][] pixels) {
        for (int[] pixel:pixels) image.setRGB(pixel[0],pixel[1],0xff929fa1);
        for (int[] pixel:pixels) {
            if (pixel[1]>0 && (pixel[0]+pixel[1])%3==0) image.setRGB(pixel[0],pixel[1],0xffe1e5e2);
        }
    }
    private static Texture pixelTexture(BufferedImage image) {
        Texture texture=new Texture2D(new AWTLoader().load(image,true));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
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
        refreshSheep();
        refreshZombies();
        refreshFish();
        refreshTorchLights(x,z);
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
    private void refreshTorchLights(float x,float z) {
        int cx=(int)x/World.CHUNK,cz=(int)z/World.CHUNK;
        if(lastTorchRevision==world.blockRevision() && lastTorchChunkX==cx && lastTorchChunkZ==cz) return;
        lastTorchRevision=world.blockRevision(); lastTorchChunkX=cx; lastTorchChunkZ=cz;
        List<Vector3f> positions=world.torchesNear(x,z,48,torchLights.size());
        for(int i=0;i<torchLights.size();i++) {
            PointLight light=torchLights.get(i);
            if(i<positions.size()) { light.setPosition(positions.get(i)); light.setRadius(13f); }
            else light.setRadius(0f);
        }
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
    private void refreshSheep() {
        List<Sheep> sheep=world.sheep();
        if (sheep.size()!=sheepModels.size()) {
            sheepNode.detachAllChildren(); sheepModels.clear();
            Material wool=flatMaterial(new ColorRGBA(.91f,.90f,.82f,1));
            Material face=flatMaterial(new ColorRGBA(.49f,.37f,.31f,1));
            Material legs=flatMaterial(new ColorRGBA(.36f,.28f,.24f,1));
            for (int i=0;i<sheep.size();i++) {
                Node animal=new Node("Sheep");
                animal.attachChild(part("Wool",new Box(.40f,.31f,.55f),wool,0f,.66f,0f));
                animal.attachChild(part("Head",new Box(.22f,.24f,.24f),face,.48f,.67f,0f));
                animal.attachChild(part("Front wool",new Box(.25f,.20f,.22f),wool,.32f,.64f,0f));
                for (float dx:new float[]{-.24f,.24f}) for (float dz:new float[]{-.34f,.34f})
                    animal.attachChild(part("Leg",new Box(.09f,.24f,.09f),legs,dx,.25f,dz));
                sheepModels.add(animal); sheepNode.attachChild(animal);
            }
        }
        for(int i=0;i<sheep.size();i++) sheepModels.get(i).setLocalTranslation(sheep.get(i).x(),sheep.get(i).y(),sheep.get(i).z());
    }
    private void refreshZombies() {
        List<Zombie> zombies=world.zombies();
        if(zombies.size()!=zombieModels.size()) {
            zombieNode.detachAllChildren(); zombieModels.clear();
            Material shirt=flatMaterial(new ColorRGBA(.19f,.34f,.25f,1));
            Material skin=flatMaterial(new ColorRGBA(.38f,.55f,.34f,1));
            Material pants=flatMaterial(new ColorRGBA(.20f,.23f,.39f,1));
            for(int i=0;i<zombies.size();i++) {
                Node model=new Node("Zombie");
                model.attachChild(part("Body",new Box(.30f,.42f,.18f),shirt,0,.88f,0));
                model.attachChild(part("Head",new Box(.22f,.22f,.22f),skin,0,1.52f,0));
                model.attachChild(part("Legs",new Box(.28f,.30f,.17f),pants,0,.30f,0));
                model.attachChild(part("Left arm",new Box(.09f,.36f,.10f),skin,-.38f,.88f,0));
                model.attachChild(part("Right arm",new Box(.09f,.36f,.10f),skin,.38f,.88f,0));
                zombieModels.add(model); zombieNode.attachChild(model);
            }
        }
        for(int i=0;i<zombies.size();i++) zombieModels.get(i).setLocalTranslation(zombies.get(i).x(),zombies.get(i).y(),zombies.get(i).z());
    }
    private void refreshFish() {
        List<Fish> fish=world.fish();
        if(fish.size()!=fishModels.size()) {
            fishNode.detachAllChildren(); fishModels.clear();
            Material body=flatMaterial(new ColorRGBA(.16f,.48f,.72f,1));
            Material fin=flatMaterial(new ColorRGBA(.08f,.25f,.42f,1));
            for(int i=0;i<fish.size();i++) {
                Node model=new Node("Yas");
                model.attachChild(part("Body",new Box(.30f,.12f,.55f),body,0,0,0));
                model.attachChild(part("Tail",new Box(.08f,.16f,.22f),fin,0,0,-.68f));
                fishModels.add(model); fishNode.attachChild(model);
            }
        }
        for(int i=0;i<fish.size();i++) {
            Fish f=fish.get(i); Node model=fishModels.get(i);
            model.setLocalTranslation(f.x(),f.y(),f.z());
            model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis((float)Math.atan2(f.dx(),f.dz()),Vector3f.UNIT_Y));
        }
    }
    private Geometry part(String name,Box shape,Material material,float x,float y,float z) {
        Geometry geometry=new Geometry(name,shape);
        geometry.setMaterial(material);
        geometry.setLocalTranslation(x,y,z);
        return geometry;
    }
    private Material flatMaterial(ColorRGBA color) {
        Material material=new Material(assets,"Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color",color);
        return material;
    }
    public void changed(int x, int z) {
        int cx=x/World.CHUNK,cz=z/World.CHUNK;
        // A torch changes the propagated light field beyond its own chunk.
        // Rebuild the surrounding ring as well so placing/removing one is
        // visible immediately instead of leaving a stale bright patch.
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) invalidate(cx+dx,cz+dz);
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
