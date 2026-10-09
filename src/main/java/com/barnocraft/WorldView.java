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
    private final ModView modView;
    private final Material[] materials = new Material[ChunkMesh.TEXTURES.length];
    private final Map<Integer,Node> loaded = new HashMap<>();
    private final Map<Integer,Integer> versions = new HashMap<>();
    private final Set<Integer> pending = new HashSet<>(), dirty = new HashSet<>();
    private final ConcurrentLinkedQueue<Built> completed = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r,"chunk-builder"); t.setDaemon(true); return t;
    });
    private Set<Integer> desired = Set.of();
    private record Built(int key, int version, Mesh[] meshes, RuntimeException error) {}

    private final AssetManager assets;
    private static final Map<String,Texture> GENERATED_TEXTURES = new HashMap<>();
    private TexturePack pack = TexturePack.ORIGINAL;
    private int lastTorchRevision=-1,lastTorchChunkX=Integer.MIN_VALUE,lastTorchChunkZ=Integer.MIN_VALUE;
    public WorldView(AssetManager assets, World world) { this(assets, world, TexturePack.ORIGINAL); }
    public WorldView(AssetManager assets, World world, TexturePack pack) {
        this.assets = assets;
        this.world = world;
        modView=new ModView(assets,world.mods); node.attachChild(modView.node);
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
            m.setColor("Ambient",ColorRGBA.White); m.setColor("Diffuse",new ColorRGBA(1,1,1,i==16?.38f:i==5?.7f:1));
            if (i == 5 || i == 16) {
                m.setTransparent(true); m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
                m.getAdditionalRenderState().setDepthWrite(false);
            }
            if (i == 16) m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
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
    void setFogRange(float near,float far) {
        for(Material material:materials)material.setVector2("LinearFog",new Vector2f(near,far));
    }
    public void setDaylight(float value) {
        for(Material material:materials) material.setFloat("Daylight",Math.max(0,Math.min(1,value)));
    }
    public void setAtmosphere(Vector3f sun, ColorRGBA tint, ColorRGBA fog) {
        for(Material material:materials) {
            material.setVector3("SunDirection",sun);
            material.setColor("SunTint",tint);
            material.setColor("FogColor",fog);
        }
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
        int modIndex=name.indexOf("mod/"); if(modIndex>=0) return ModView.item(ItemType.valueOf(name.substring(modIndex+4)));
        String file=name.substring(name.lastIndexOf('/')+1);
        if(EquipmentTextures.supports(file)) {
            synchronized(GENERATED_TEXTURES) { return GENERATED_TEXTURES.computeIfAbsent(name,k -> pixelTexture(EquipmentTextures.create(file))); }
        }
        if(name.endsWith("lantern.png") || name.endsWith("bed.png") || name.endsWith("furnace_side.png") || name.endsWith("pillow.png")) {
            synchronized(GENERATED_TEXTURES) { return GENERATED_TEXTURES.computeIfAbsent(name,WorldView::createFurnitureTexture); }
        }
        boolean generated=switch(file) {
            case "stonecutter.png", "furnace.png", "chest.png", "farmland.png", "wheat.png", "wheat_seeds.png", "bread.png", "stone_hoe.png", "mutton.png", "cooked_mutton.png", "wool.png", "torch.png", "water.png",
                    "casting_table.png", "welder.png", "bucket_mold.png", "axe_mold.png", "pickaxe_mold.png",
                    "shovel_mold.png", "bucket.png", "water_bucket.png" -> true;
            default -> false;
        };
        if(generated) {
            synchronized(GENERATED_TEXTURES) {
                return GENERATED_TEXTURES.computeIfAbsent(name,key -> switch(file) {
                    case "stonecutter.png" -> createStonecutterTexture();
                    case "furnace.png" -> createFurnaceTexture();
                    case "chest.png" -> createChestTexture();
                    case "farmland.png" -> createFarmlandTexture();
                    case "wheat.png" -> createWheatTexture();
                    case "wheat_seeds.png" -> createSeedTexture();
                    case "bread.png" -> createBreadTexture();
                    case "stone_hoe.png" -> pixelTexture(EquipmentTextures.create("stone_shovel.png"));
                    case "mutton.png" -> createMuttonTexture();
                    case "cooked_mutton.png" -> createCookedMuttonTexture();
                    case "wool.png" -> createWoolTexture();
                    case "torch.png" -> createTorchTexture();
                    case "water.png" -> createWaterTexture();
                    default -> createWorkshopTexture(file);
                });
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
            float dx=x-7.5f,dy=y-7.5f,r=(float)Math.sqrt(dx*dx+dy*dy);
            if(r<6 && (r<5 || (x+y)%2==0)) color=0xffbecbd0;
            if(r<4.1f) color=0xff87989f;
            if(Math.abs(dx+dy)<1.2f && r<4.5f) color=0xffe1eaeb;
            if(r<1.4f) color=0xff343e42;
            image.setRGB(x,y,color);
        }
        Texture texture = new Texture2D(new AWTLoader().load(image,true));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        return texture;
    }
    private static Texture createFurnaceTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            boolean seam=y%4==0 || (x+(y/4%2)*4)%8==0;
            int color=seam?0xff393b3d:((x*7+y*3)%5==0?0xff858788:0xff6c7174);
            if(x>=3 && x<=12 && y>=5 && y<=12) color=0xff202426;
            if(x>=4 && x<=11 && y>=9 && y<=11) color=(x+y)%3==0?0xffffc557:0xffd46a29;
            if(y==8 && x>=3 && x<=12) color=0xff42494d;
            if((x==2 || x==13) && (y==4 || y==13)) color=0xffaeb4b5;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createFarmlandTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            int color=(y==2 || y==8 || y==14)?0xffa77b4b:((x+y*3)%7==0?0xff795438:0xff68472f);
            if(y==0)color=0xff8b6947;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createWheatTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=3;y<14;y++) {
            int x=7+(y%3==0?1:0);image.setRGB(x,y,0xffa77a35);image.setRGB(x+1,y,0xffd2a64c);
            if(y<9 && y%2==0){image.setRGB(x-2,y,0xffb7b84a);image.setRGB(x-1,y-1,0xffd7cb62);image.setRGB(x+2,y-2,0xffc8bd55);}
        }
        for(int x=6;x<=9;x++)image.setRGB(x,14,0xff8b6335);
        return pixelTexture(image);
    }
    private static Texture createSeedTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        int[][] seeds={{4,5},{8,4},{11,7},{5,10},{9,12},{12,11}};
        for(int[] seed:seeds){image.setRGB(seed[0],seed[1],0xff87602f);image.setRGB(seed[0]+1,seed[1],0xffd5b45d);}
        return pixelTexture(image);
    }
    private static Texture createBreadTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=5;y<=11;y++)for(int x=3;x<=12;x++) {
            boolean crust=x==3||x==12||y==10||y==11;
            image.setRGB(x,y,crust?0xff9a5d2b:0xffe2b968);
        }
        image.setRGB(5,6,0xffffd989);image.setRGB(9,7,0xffffd989);image.setRGB(7,9,0xffc98a3e);
        return pixelTexture(image);
    }
    private static Texture createChestTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
            boolean frame=x<2 || x>13 || y<2 || y>13;
            boolean seam=y==5 || y==6;
            int grain=(x*13+y*7)%9;
            int color=frame?0xff503321:seam?0xff352318:
                    ((y%4==0)?0xff80502b:(grain<3?0xffad783e:0xff996332));
            if((x==2 || x==13) && !frame)color=0xffc6944b;
            if((x==1 || x==14) && (y==2 || y==13))color=0xffd8bd77;
            if(x>=7 && x<=8 && y>=5 && y<=9)color=y==8?0xff4c4435:0xffe2c36d;
            image.setRGB(x,y,color);
        }
        return pixelTexture(image);
    }
    private static Texture createFurnitureTexture(String name) {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            int color;
            if(name.endsWith("lantern.png")) {
                boolean frame=x<3 || x>12 || y<3 || y>12;
                color=frame?0xff394650:((x+y)%4==0?0xffffe6a4:0xffffbd4c);
                if(!frame && x==4) color=0xfffff2cf;
            } else if(name.endsWith("bed.png")) {
                color=(x+y)%6==0?0xffac3e3c:0xff90302f;
                if(x==1 || x==14) color=0xffd5876d;
            } else if(name.endsWith("pillow.png")) {
                color=x==0 || y==0 || x==15 || y==15?0xffbdbbb0:((x+y)%7==0?0xffddd9cd:0xffeee9dd);
            } else {
                boolean seam=y%4==0 || (x+(y/4%2)*4)%8==0;
                color=seam?0xff393b3d:((x*7+y*3)%5==0?0xff858788:0xff6c7174);
            }
            image.setRGB(x,y,color);
        }
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
    private static Texture createWaterTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            int wave=((x+y*3)%7<2)?0xff5db8d0:0xff287aa5; image.setRGB(x,y,wave);
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
    private static Texture createCookedMuttonTexture() {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=4;y<=11;y++)for(int x=3;x<=12;x++) {
            int color=(x==3||x==12||y==4||y==11)?0xff633522:0xffb87438;
            if((x+y)%4==0)color=0xffe0b66c;
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
        modView.update();
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
                    completed.add(new Built(k,version,meshes,null));
                } catch (RuntimeException e) { completed.add(new Built(k,version,null,e)); }
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
            Material wool=mobMaterial(0xe5e1d1,0xbdb9ad, true);
            Material face=mobMaterial(0x9b8069,0x6e5447, false);
            Material legs=mobMaterial(0x715b48,0x453b34, false);
            for (int i=0;i<sheep.size();i++) {
                Node animal=new Node("Sheep");
                animal.attachChild(part("Wool",new Box(.55f,.31f,.36f),wool,0f,.66f,0f));
                animal.attachChild(part("Head",new Box(.22f,.24f,.24f),face,.59f,.76f,0f));
                animal.attachChild(part("Front wool",new Box(.25f,.20f,.22f),wool,.40f,.85f,0f));
                for (float dx:new float[]{-.36f,.36f}) for (float dz:new float[]{-.24f,.24f})
                    animal.attachChild(part("Leg",new Box(.09f,.24f,.09f),legs,dx,.25f,dz));
                for(float side:new float[]{-1,1}) {
                    animal.attachChild(part("Eye",new Box(.045f,.045f,.012f),flatMaterial(ColorRGBA.Black),.66f,.83f,side*.245f));
                    animal.attachChild(part("Ear",new Box(.10f,.055f,.07f),face,.48f,.91f,side*.29f));
                }
                animal.attachChild(part("Muzzle",new Box(.06f,.10f,.15f),legs,.82f,.68f,0));
                animal.attachChild(part("Tail",new Box(.13f,.10f,.10f),wool,-.61f,.70f,0));
                sheepModels.add(animal); sheepNode.attachChild(animal);
            }
        }
        for(int i=0;i<sheep.size();i++) {
            Sheep f=sheep.get(i); Node model=sheepModels.get(i);
            model.setLocalTranslation(f.x(),f.y(),f.z());
            model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(-(float)Math.atan2(f.directionZ(),f.directionX()),Vector3f.UNIT_Y));
            tintMob(model,world.hurtFlash(f.id()));
        }
    }
    private void refreshZombies() {
        List<Zombie> zombies=world.zombies();
        if(zombies.size()!=zombieModels.size()) {
            zombieNode.detachAllChildren(); zombieModels.clear();
            Material shirt=mobMaterial(0x387c83,0x245057,false);
            Material skin=mobMaterial(0x709153,0x486639,false);
            Material pants=mobMaterial(0x494568,0x302e4c,false);
            for(int i=0;i<zombies.size();i++) {
                Node model=new Node("Zombie");
                model.attachChild(part("Body",new Box(.30f,.42f,.18f),shirt,0,.88f,0));
                model.attachChild(part("Head",new Box(.22f,.22f,.22f),skin,0,1.52f,0));
                model.attachChild(part("Left leg",new Box(.12f,.30f,.17f),pants,-.16f,.30f,0));
                model.attachChild(part("Right leg",new Box(.12f,.30f,.17f),pants,.16f,.30f,0));
                for(float side:new float[]{-1,1}) model.attachChild(part("Eye",new Box(.055f,.035f,.012f),flatMaterial(ColorRGBA.Black),side*.10f,1.56f,.225f));
                model.attachChild(part("Mouth",new Box(.10f,.025f,.012f),flatMaterial(new ColorRGBA(.18f,.22f,.12f,1)),0,1.40f,.225f));
                model.attachChild(part("Left arm",new Box(.09f,.36f,.10f),skin,-.38f,.88f,0));
                model.attachChild(part("Right arm",new Box(.09f,.36f,.10f),skin,.38f,.88f,0));
                zombieModels.add(model); zombieNode.attachChild(model);
            }
        }
        for(int i=0;i<zombies.size();i++) {
            Zombie f=zombies.get(i); Node model=zombieModels.get(i);
            model.setLocalTranslation(f.x(),f.y(),f.z());
            model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis(f.heading(),Vector3f.UNIT_Y));
            tintMob(model,world.hurtFlash(f.id()));
            float stride=(float)Math.sin((f.x()+f.z())*5)*.45f;
            model.getChild("Left leg").setLocalRotation(new com.jme3.math.Quaternion().fromAngles(stride,0,0));
            model.getChild("Right leg").setLocalRotation(new com.jme3.math.Quaternion().fromAngles(-stride,0,0));
        }
    }
    private void refreshFish() {
        List<Fish> fish=world.fish();
        if(fish.size()!=fishModels.size()) {
            fishNode.detachAllChildren(); fishModels.clear();
            Material body=mobMaterial(0x79a8b0,0x3c737e,false);
            Material fin=mobMaterial(0x637d85,0x344e58,false);
            for(int i=0;i<fish.size();i++) {
                Node model=new Node("Yas");
                Geometry torso=new Geometry("Scales",new com.jme3.scene.shape.Sphere(8,12,1));
                torso.setMaterial(body); torso.setLocalScale(.14f,.20f,.43f); model.attachChild(torso);
                model.attachChild(fin("Tail",fin,0,0,-.39f,.23f,.25f));
                Geometry dorsal=fin("Dorsal fin",fin,0,.15f,.03f,.18f,.24f);
                dorsal.rotate(0,0,(float)Math.PI/2); model.attachChild(dorsal);
                for(float side:new float[]{-1,1}) {
                    model.attachChild(part("Eye white",new Box(.012f,.045f,.045f),flatMaterial(ColorRGBA.White),side*.11f,.065f,.27f));
                    model.attachChild(part("Pupil",new Box(.014f,.025f,.025f),flatMaterial(ColorRGBA.Black),side*.12f,.065f,.285f));
                    Geometry g=fin("Pectoral fin",fin,side*.12f,-.05f,.08f,.12f,.17f);
                    g.rotate(0,0,side*1.1f); model.attachChild(g);
                }
                fishModels.add(model); fishNode.attachChild(model);
            }
        }
        for(int i=0;i<fish.size();i++) {
            Fish f=fish.get(i); Node model=fishModels.get(i);
            model.setLocalTranslation(f.x(),f.y(),f.z());
            model.setLocalRotation(new com.jme3.math.Quaternion().fromAngleAxis((float)Math.atan2(f.dx(),f.dz()),Vector3f.UNIT_Y));
        }
    }
    private Geometry fin(String name,Material material,float x,float y,float z,float width,float length) {
        Mesh mesh=new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position,3,new float[]{0,0,0, 0,width,-length, 0,-width,-length});
        mesh.setBuffer(VertexBuffer.Type.TexCoord,2,new float[]{.5f,0,1,1,0,1});
        mesh.setBuffer(VertexBuffer.Type.Index,3,new int[]{0,1,2,2,1,0}); mesh.updateBound();
        Geometry g=new Geometry(name,mesh); g.setMaterial(material); g.setLocalTranslation(x,y,z); return g;
    }
    private Material mobMaterial(int base,int shade,boolean wool) {
        BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) {
            boolean dark=wool ? (x%4==0 && y%4<3 || y%4==0 && x%4<3) : (x*17+y*31)%11<3;
            image.setRGB(x,y,0xff000000 | (dark?shade:base));
        }
        Material material=flatMaterial(ColorRGBA.White); material.setTexture("ColorMap",pixelTexture(image)); return material;
    }
    private Geometry part(String name,Box shape,Material material,float x,float y,float z) {
        Geometry geometry=new Geometry(name,shape);
        Material normal=material.clone(),hurt=material.clone();
        hurt.setColor("Color",new ColorRGBA(1f,.48f,.48f,1));
        geometry.setUserData("normalMaterial",normal);geometry.setUserData("hurtMaterial",hurt);
        geometry.setMaterial(normal);
        geometry.setLocalTranslation(x,y,z);
        return geometry;
    }
    private void tintMob(Node model,boolean hurt) {
        for(Spatial part:model.getChildren())if(part instanceof Geometry geometry)
            geometry.setMaterial((Material)geometry.getUserData(hurt?"hurtMaterial":"normalMaterial"));
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
            if (i==5 || i==16) g.setQueueBucket(RenderQueue.Bucket.Transparent);
            chunk.attachChild(g);
        }
        loaded.put(result.key(),chunk); node.attachChild(chunk);
    }
    @Override public void close() { worker.shutdownNow(); node.removeFromParent(); loaded.clear(); }
}
