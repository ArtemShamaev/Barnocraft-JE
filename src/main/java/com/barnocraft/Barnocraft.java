package com.barnocraft;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.WireBox;
import com.jme3.system.AppSettings;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class Barnocraft extends SimpleApplication {
    private enum Screen { MAIN, WORLDS, SETTINGS, PAUSE, INVENTORY, LOADING, GAME }
    private World world;
    private final Player player = new Player();
    private final boolean[] movement = new boolean[4];
    private WorldView worldView;
    private Hud hud;
    private Menu menu;
    private Geometry highlight, stepHighlight;
    private boolean active, jump, quitting;
    private int selected = 1, slot;
    private final Inventory inventory = new Inventory();
    private float yaw, pitch, autosave;
    private int screenWidth, screenHeight;
    private Screen screen = Screen.MAIN, settingsBack = Screen.MAIN;
    private String message = "";
    private final boolean smoke;
    private final GameOptions options = new GameOptions();
    private final Path savesPath;
    private final SaveStore saves;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"world-io"); t.setDaemon(true); return t;
    });
    private Future<SaveStore.Saved> loading;
    private Future<?> saving;
    private int loadingSlot;
    private int smokeStage, smokeTicks, smokeDoorY, smokeStairX, smokeStairY, smokeStairZ;
    private float smokeHomeX, smokeHomeZ;
    private ScreenshotAppState screenshots;

    private Barnocraft(boolean smoke) throws IOException {
        this.smoke=smoke;
        savesPath=smoke ? Files.createTempDirectory(Path.of("target"),"smoke-saves-")
                : Path.of(System.getProperty("barnocraft.saves","saves"));
        saves=new SaveStore(savesPath);
    }
    public static void main(String[] args) throws IOException {
        Barnocraft game=new Barnocraft(Arrays.asList(args).contains("--smoke"));
        AppSettings settings=new AppSettings(true);
        settings.setTitle("Barnocraft Alpha 0.3 — jMonkeyEngine"); settings.setResolution(1280,720);
        settings.setSamples(0); settings.setVSync(true); settings.setFrameRate(120);
        settings.setAudioRenderer(null); settings.setResizable(true);
        game.setSettings(settings); game.setShowSettings(false); game.start();
    }
    @Override public void simpleInitApp() {
        flyCam.setEnabled(false); setDisplayFps(false); setDisplayStatView(false);
        inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        viewPort.setBackgroundColor(new ColorRGBA(135/255f,206/255f,235/255f,1));
        AmbientLight ambient=new AmbientLight(); ambient.setColor(ColorRGBA.White.mult(.7f)); rootNode.addLight(ambient);
        DirectionalLight sun=new DirectionalLight(); sun.setColor(ColorRGBA.White.mult(.8f));
        sun.setDirection(new Vector3f(-60,-100,-40).normalizeLocal()); rootNode.addLight(sun);
        highlight=new Geometry("Target",new WireBox(.5015f,.5015f,.5015f));
        Material outline=new Material(assetManager,"Common/MatDefs/Misc/Unshaded.j3md");
        outline.setColor("Color",new ColorRGBA(.06f,.06f,.06f,1)); highlight.setMaterial(outline);
        highlight.setCullHint(Spatial.CullHint.Always); rootNode.attachChild(highlight);
        stepHighlight=new Geometry("Upper stair target",new WireBox(.5015f,.5015f,.5015f));
        stepHighlight.setMaterial(outline); stepHighlight.setCullHint(Spatial.CullHint.Always); rootNode.attachChild(stepHighlight);
        hud=new Hud(assetManager); guiNode.attachChild(hud.node);
        menu=new Menu(assetManager); guiNode.attachChild(menu.node);
        try { options.load(savesPath.resolve("settings.properties")); } catch (IOException e) { message=e.getMessage(); }
        hud.setTexturePack(TexturePack.from(options.texturePack));
        bind(); show(Screen.MAIN);
        if (smoke) {
            setPauseOnLostFocus(false);
            screenshots=new ScreenshotAppState("target/","menu-smoke-"); stateManager.attach(screenshots);
        }
    }
    private void bind() {
        int[] keys={KeyInput.KEY_W,KeyInput.KEY_S,KeyInput.KEY_A,KeyInput.KEY_D};
        for (int i=0;i<keys.length;i++) mapping("move"+i,new KeyTrigger(keys[i]));
        mapping("jump",new KeyTrigger(KeyInput.KEY_SPACE)); mapping("pause",new KeyTrigger(KeyInput.KEY_ESCAPE));
        mapping("inventory",new KeyTrigger(KeyInput.KEY_E));
        mapping("quit",new KeyTrigger(KeyInput.KEY_F10));
        mapping("break",new MouseButtonTrigger(MouseInput.BUTTON_LEFT)); mapping("place",new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        for (int i=0;i<Block.HOTBAR.length;i++) mapping("slot"+i,new KeyTrigger(KeyInput.KEY_1+i));
        mapping("next",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,true));
        mapping("previous",new MouseAxisTrigger(MouseInput.AXIS_WHEEL,false));
        String[] look={"left","right","up","down"};
        inputManager.addMapping("left",new MouseAxisTrigger(MouseInput.AXIS_X,true));
        inputManager.addMapping("right",new MouseAxisTrigger(MouseInput.AXIS_X,false));
        inputManager.addMapping("up",new MouseAxisTrigger(MouseInput.AXIS_Y,false));
        inputManager.addMapping("down",new MouseAxisTrigger(MouseInput.AXIS_Y,true));
        inputManager.addListener((AnalogListener)(name,value,tpf)->{
            if (!active) return;
            float amount=value*2*options.sensitivity;
            switch(name) {
                case "left" -> yaw+=amount; case "right" -> yaw-=amount;
                case "up" -> pitch+=amount; case "down" -> pitch-=amount;
                default -> { }
            }
            pitch=FastMath.clamp(pitch,-FastMath.HALF_PI+.01f,FastMath.HALF_PI-.01f); updateCamera();
        },look);
    }
    private void mapping(String name,Trigger trigger) {
        inputManager.addMapping(name,trigger); inputManager.addListener((ActionListener)this::action,name);
    }
    private void action(String name,boolean pressed,float tpf) {
        if (name.equals("inventory") && pressed) { if (screen==Screen.GAME) show(Screen.INVENTORY); else if (screen==Screen.INVENTORY) show(Screen.GAME); return; }
        if (name.startsWith("move")) { movement[name.charAt(4)-'0']=active && pressed; return; }
        if (!pressed) return;
        if (name.equals("quit")) { quit(); return; }
        if (name.equals("pause")) { back(); return; }
        if (!active) {
            if (name.equals("break")) menuAction(menu.click(inputManager.getCursorPosition()));
            return;
        }
        if (name.equals("jump")) jump=true;
        else if (name.startsWith("slot")) select(name.charAt(4)-'0');
        else if (name.equals("next")) select((selected+1)%Block.HOTBAR.length);
        else if (name.equals("previous")) select((selected+Block.HOTBAR.length-1)%Block.HOTBAR.length);
        else if (name.equals("break") || name.equals("place")) edit(name.equals("place"));
    }
    private void show(Screen next) {
        screen=next; active=next==Screen.GAME; jump=false; Arrays.fill(movement,false);
        inputManager.setCursorVisible(!active); hud.active(active);
        hud.node.setCullHint(active?Spatial.CullHint.Inherit:Spatial.CullHint.Always);
        highlight.setCullHint(Spatial.CullHint.Always);
        if (active) { menu.hide(); return; }
        List<Menu.Button> buttons=new ArrayList<>();
        String title="Barnocraft", subtitle="Alpha 0.3 • jMonkeyEngine";
        switch(next) {
            case MAIN -> {
                buttons.add(new Menu.Button("worlds","Миры")); buttons.add(new Menu.Button("settings","Настройки"));
                buttons.add(new Menu.Button("quit","Выход"));
            }
            case WORLDS -> {
                title="Миры"; subtitle="Пять независимых сохранений";
                for (int i=1;i<=5;i++) buttons.add(new Menu.Button("world"+i,"Мир "+i));
                buttons.add(new Menu.Button("back","Назад"));
            }
            case SETTINGS -> {
                title="Настройки"; subtitle="Ползунки параметров";
                menu.showSliders(title,subtitle,List.of(
                        new Menu.Slider("distance","Прорисовка",options.distance+" чанка"),
                        new Menu.Slider("sensitivity","Чувствительность",String.format(Locale.ROOT,"%.2f",options.sensitivity)),
                        new Menu.Slider("fov","Угол обзора",options.fov+"°"),
                        new Menu.Slider("textures","Текстуры",TexturePack.from(options.texturePack).title)),
                        List.of(new Menu.Button("back","Назад")),message);
                return;
            }
            case PAUSE -> {
                title="Мир "+slot; subtitle="Пауза • изменения сохраняются";
                buttons.add(new Menu.Button("resume","Продолжить")); buttons.add(new Menu.Button("settings","Настройки"));
                buttons.add(new Menu.Button("save","Сохранить")); buttons.add(new Menu.Button("main","В главное меню"));
                buttons.add(new Menu.Button("quit","Сохранить и выйти"));
            }
            case INVENTORY -> { menu.showInventory(inventory); return; }
            case LOADING -> { title="Мир "+loadingSlot; subtitle="Загрузка ближайших чанков…"; }
            default -> { }
        }
        menu.show(title,subtitle,buttons,message);
    }
    private void menuAction(String id) {
        if (id==null || screen==Screen.LOADING) return;
        message="";
        if (id.matches("world[1-5]")) { startWorld(id.charAt(5)-'0'); return; }
        switch(id) {
            case "craft_blade" -> craft(ItemType.STONE_BLADE);
            case "craft_plank" -> craft(ItemType.PLANK);
            case "craft_cutter" -> craft(ItemType.STONECUTTER);
            case "cutter_axe" -> craft(ItemType.AXE_HEAD);
            case "cutter_pick" -> craft(ItemType.PICKAXE_HEAD);
            case "craft_axe" -> craft(ItemType.AXE);
            case "craft_pick" -> craft(ItemType.PICKAXE);
            case "worlds" -> show(Screen.WORLDS);
            case "settings" -> { settingsBack=screen; show(Screen.SETTINGS); }
            case "resume" -> show(Screen.GAME);
            case "back" -> back();
            case "main" -> { if (saveNow()) { unload(); show(Screen.MAIN); } }
            case "save" -> { if (saveNow()) { message="Мир сохранён"; show(Screen.PAUSE); } }
            case "quit" -> quit();
            case "distance" -> { options.distance=options.distance==6?2:options.distance+1; saveOptions(); }
            case "sensitivity" -> { options.sensitivity=options.sensitivity>=2?.5f:options.sensitivity+.25f; saveOptions(); }
            case "fov" -> { options.fov=options.fov>=95?65:options.fov+10; saveOptions(); }
            case "textures" -> { options.texturePack=options.texturePack==0?1:0; hud.setTexturePack(TexturePack.from(options.texturePack)); if(worldView!=null) worldView.setTexturePack(TexturePack.from(options.texturePack)); saveOptions(); }
            default -> { }
        }
    }
    private void craft(ItemType result) { message=inventory.craft(result)?"Создано: "+result.title:"Недостаточно материалов"; show(Screen.INVENTORY); }
    private void saveOptions() {
        try { options.save(savesPath.resolve("settings.properties")); }
        catch(IOException e) { message="Не удалось сохранить настройки: "+e.getMessage(); }
        resize(); show(Screen.SETTINGS);
    }
    private void back() {
        message="";
        switch(screen) {
            case GAME -> { show(Screen.PAUSE); saveNow(); }
            case PAUSE -> show(Screen.GAME);
            case WORLDS -> show(Screen.MAIN);
            case SETTINGS -> show(settingsBack);
            case INVENTORY -> show(Screen.GAME);
            default -> { }
        }
    }
    private void startWorld(int targetSlot) {
        if (!saveNow()) return;
        unload(); loadingSlot=targetSlot; show(Screen.LOADING);
        loading=io.submit(()->{
            if (saves.exists(targetSlot)) return saves.load(targetSlot);
            World generated=smoke?new World(42+targetSlot):new World();
            Player spawn=new Player(); spawn.respawn(generated);
            SaveStore.Pose pose=new SaveStore.Pose(spawn.position.x,spawn.position.y,spawn.position.z,0,0,1);
            saves.save(targetSlot,generated.snapshot(),pose);
            return new SaveStore.Saved(generated,pose);
        });
    }
    private void finishLoading() {
        if (loading!=null && loading.isDone()) {
            try {
                SaveStore.Saved saved=loading.get(); world=saved.world(); slot=loadingSlot;
                SaveStore.Pose p=saved.pose(); player.position.set(p.x(),p.y(),p.z()); player.velocityY=0; player.grounded=false;
                yaw=p.yaw(); pitch=p.pitch(); select(p.selected());
                if (player.collides(world)) player.respawn(world);
                worldView=new WorldView(assetManager,world,TexturePack.from(options.texturePack)); rootNode.attachChild(worldView.node); updateCamera();
            } catch(Exception e) { failure("Не удалось открыть мир",e); show(Screen.WORLDS); }
            loading=null;
        }
        if (screen==Screen.LOADING && worldView!=null && worldView.ready(player.position.x,player.position.z)) {
            autosave=0; show(Screen.GAME);
        }
    }
    private SaveStore.Pose pose() {
        return new SaveStore.Pose(player.position.x,player.position.y,player.position.z,yaw,pitch,selected);
    }
    private void autoSave() {
        if (saving!=null || world==null) return;
        World.Snapshot snapshot=world.snapshot(); SaveStore.Pose pose=pose(); int target=slot;
        saving=io.submit(()->{ saves.save(target,snapshot,pose); return null; });
    }
    private boolean saveNow() {
        if (world==null) return true;
        try {
            if (saving!=null) { saving.get(); saving=null; }
            saves.save(slot,world.snapshot(),pose()); autosave=0; return true;
        } catch(Exception e) { saving=null; failure("Не удалось сохранить мир",e); show(Screen.PAUSE); return false; }
    }
    private void failure(String prefix,Exception e) {
        Throwable cause=e.getCause()==null?e:e.getCause();
        message=prefix+": "+cause.getMessage(); System.err.println(message);
    }
    private void unload() {
        if (worldView!=null) worldView.close(); worldView=null; world=null; slot=0;
    }
    private void quit() {
        if (quitting || !saveNow()) return;
        quitting=true; super.stop();
    }
    @Override public void requestClose(boolean esc) { enqueue(()->{ quit(); return null; }); }
    @Override public void destroy() {
        if (worldView!=null) worldView.close(); io.shutdownNow(); super.destroy();
    }
    @Override public void loseFocus() {
        super.loseFocus();
        if (active && !smoke) { show(Screen.PAUSE); saveNow(); }
    }
    private void select(int index) { selected=index; hud.select(index); }
    private void updateCamera() {
        Vector3f direction=new Vector3f(-FastMath.sin(yaw)*FastMath.cos(pitch),FastMath.sin(pitch),-FastMath.cos(yaw)*FastMath.cos(pitch));
        cam.setLocation(player.position.add(0,Player.EYE,0)); cam.lookAtDirection(direction,Vector3f.UNIT_Y);
    }
    private World.Hit target() { return world.raycast(cam.getLocation(),cam.getDirection(),Player.REACH); }
    private void edit(boolean place) {
        World.Hit hit=target();
        if (hit==null && !place && world!=null) {
            ItemType item=world.collectNearest(player.position,2.0f);
            if (item!=null) inventory.add(item);
            return;
        }
        if (hit==null) return;
        if (place && Block.HOTBAR[selected]==Block.ROTATOR) {
            if (world.rotate(hit.x(),hit.y(),hit.z(),player)) worldView.changed(hit.x(),hit.z());
            return;
        }
        if (place && world.doorAt(hit.x(),hit.y(),hit.z())!=null) {
            if (world.toggleDoor(hit.x(),hit.y(),hit.z(),player)) worldView.changed(hit.x(),hit.z());
            return;
        }
        int x=hit.x()+(place?hit.nx():0),y=hit.y()+(place?hit.ny():0),z=hit.z()+(place?hit.nz():0);
        if (place && Block.HOTBAR[selected]==Block.DOOR) {
            int facing=Math.floorMod(Math.round(yaw/FastMath.HALF_PI),4);
            if (world.placeDoor(x,y,z,facing,player)) worldView.changed(x,z);
            return;
        }
        if (place && Block.HOTBAR[selected]==Block.STAIRS) {
            int facing=Math.floorMod(Math.round(-yaw/FastMath.HALF_PI),4);
            if (world.placeStairs(x,y,z,facing,player)) worldView.changed(x,z);
            return;
        }
        if (player.intersects(x,y,z) || (place && world.get(x,y,z)!=Block.AIR)) return;
        if (!place) {
            Block broken=world.get(x,y,z);
            // Все блоки теперь ломаются без инструментов
            if (world.set(x,y,z,Block.AIR)) {
                if (broken==Block.LOG || broken==Block.PLANKS) inventory.add(ItemType.STICK);
                if (broken==Block.STONE) inventory.add(ItemType.STONE);
                if (broken==Block.DEEPSTONE) inventory.add(ItemType.DEEPSTONE);
                if (broken==Block.SAND) inventory.add(ItemType.PEBBLE);
                if (broken==Block.GLASS) inventory.add(ItemType.PEBBLE);
                if (broken==Block.LEAVES) inventory.add(ItemType.STICK);
                if (broken==Block.COAL_ORE) inventory.add(ItemType.PEBBLE);
                if (broken==Block.IRON_ORE) inventory.add(ItemType.PEBBLE);
                worldView.changed(x,z);
            }
        } else if (world.set(x,y,z,Block.HOTBAR[selected])) worldView.changed(x,z);
    }
    private void resize() {
        screenWidth=cam.getWidth(); screenHeight=cam.getHeight();
        cam.setFrustumPerspective(options.fov,(float)screenWidth/Math.max(1,screenHeight),.05f,500);
        hud.resize(screenWidth,screenHeight); menu.resize(screenWidth,screenHeight);
    }
    @Override public void simpleUpdate(float tpf) {
        if (cam.getWidth()!=screenWidth || cam.getHeight()!=screenHeight) resize();
        finishLoading();
        if (active && world != null) {
            for (ItemType item : ItemType.values()) {
                int count=world.collectNear(player.position,1.25f,item);
                for(int i=0;i<count;i++) inventory.add(item);
            }
        }
        if (worldView!=null) worldView.update(player.position.x,player.position.z,options.distance);
        if (active && worldView.ready(player.position.x,player.position.z)) {
            Vector3f forward=new Vector3f(-FastMath.sin(yaw),0,-FastMath.cos(yaw)),right=forward.cross(Vector3f.UNIT_Y),move=new Vector3f();
            if (movement[0]) move.addLocal(forward); if (movement[1]) move.subtractLocal(forward);
            if (movement[2]) move.subtractLocal(right); if (movement[3]) move.addLocal(right);
            player.update(world,Math.min(tpf,.05f),move.normalizeLocal(),jump); jump=false; updateCamera();
            autosave+=tpf; if (autosave>=30) { autoSave(); autosave=0; }
        }
        if (saving!=null && saving.isDone()) {
            try { saving.get(); } catch(Exception e) { failure("Ошибка автосохранения",e); show(Screen.PAUSE); }
            saving=null;
        }
        World.Hit hit=active?target():null;
        highlight.setCullHint(hit==null?Spatial.CullHint.Always:Spatial.CullHint.Never);
        stepHighlight.setCullHint(Spatial.CullHint.Always);
        if (hit!=null) {
            Door door=world.doorAt(hit.x(),hit.y(),hit.z());
            if (door!=null) {
                Door.Bounds b=door.bounds(); highlight.setLocalScale(b.maxX()-b.minX(),2,b.maxZ()-b.minZ());
                highlight.setLocalTranslation((b.minX()+b.maxX())/2,door.y()+1,(b.minZ()+b.maxZ())/2);
            } else if (world.get(hit.x(),hit.y(),hit.z())==Block.STAIRS) {
                var boxes=Stairs.bounds(hit.x(),hit.y(),hit.z(),world.rotation(hit.x(),hit.y(),hit.z()));
                outlineBox(highlight,boxes.get(0)); outlineBox(stepHighlight,boxes.get(1));
                stepHighlight.setCullHint(Spatial.CullHint.Never);
            } else { highlight.setLocalScale(1); highlight.setLocalTranslation(hit.x()+.5f,hit.y()+.5f,hit.z()+.5f); }
        }
        if (!active) menu.hover(inputManager.getCursorPosition());
        if (smoke) smokeFrame();
    }
    private void outlineBox(Geometry geometry,Door.Bounds b) {
        geometry.setLocalScale(b.maxX()-b.minX(),b.maxY()-b.minY(),b.maxZ()-b.minZ());
        geometry.setLocalTranslation((b.minX()+b.maxX())/2,(b.minY()+b.maxY())/2,(b.minZ()+b.maxZ())/2);
    }
    private void smokeClick(String id) { menuAction(menu.click(menu.buttonCenter(id))); }
    private void check(boolean condition,String error) { if (!condition) throw new IllegalStateException("Smoke: "+error); }
    private void smokeFrame() {
        smokeTicks++;
        if (smokeTicks>3600) throw new IllegalStateException("Smoke timed out in stage "+smokeStage);
        switch(smokeStage) {
            case 0 -> { if (smokeTicks==6) screenshots.takeScreenshot();
                if (smokeTicks==8) { smokeClick("settings"); smokeStage++; } }
            case 1 -> {
                if (smokeTicks==12) { smokeClick("sensitivity"); smokeClick("fov"); screenshots.takeScreenshot(); }
                if (smokeTicks==16) { smokeClick("back"); smokeClick("worlds"); smokeStage++; }
            }
            case 2 -> { if (smokeTicks==18) screenshots.takeScreenshot();
                if (smokeTicks==20) { smokeClick("world1"); smokeStage++; } }
            case 3 -> {
                if (screen!=Screen.GAME || worldView.loadedCount()<25) return;
                smokeHomeX=player.position.x; smokeHomeZ=player.position.z;
                int x=(int)smokeHomeX,z=(int)smokeHomeZ-3;
                smokeDoorY=world.groundHeight(x,z)+1;
                for (int y=smokeDoorY;y<smokeDoorY+2;y++) world.set(x,y,z,Block.AIR);
                check(world.placeDoor(x,smokeDoorY,z,0,player),"door placement"); worldView.changed(x,z);
                select(6); player.position.set(x+.5f,smokeDoorY,z+2.5f); yaw=0; pitch=-.1f; updateCamera();
                smokeStage++; smokeTicks=0;
            }
            case 4 -> {
                if (smokeTicks==18) screenshots.takeScreenshot();
                if (smokeTicks==20) { action("place",true,0);
                    Door door=world.doorAt((int)smokeHomeX,smokeDoorY,(int)smokeHomeZ-3);
                    check(door!=null && door.open(),"right click opens door"); }
                if (smokeTicks==33) screenshots.takeScreenshot();
                if (smokeTicks==35) {
                    smokeStairX=(int)smokeHomeX+2; smokeStairZ=(int)smokeHomeZ-2;
                    smokeStairY=world.groundHeight(smokeStairX,smokeStairZ)+1;
                    for (int x=smokeStairX-1;x<=smokeStairX+1;x++) for(int z=smokeStairZ;z<=smokeStairZ+3;z++) {
                        for(int y=1;y<World.HEIGHT;y++) world.set(x,y,z,y<smokeStairY?Block.STONE:Block.AIR);
                        worldView.changed(x,z);
                    }
                    player.position.set(smokeStairX+.5f,smokeStairY+.001f,smokeStairZ+2.2f);
                    yaw=0; pitch=-FastMath.atan2(Player.EYE,1.7f); updateCamera();
                    action("slot7",true,0); action("place",true,0);
                    check(world.get(smokeStairX,smokeStairY,smokeStairZ)==Block.STAIRS,"hotbar stair placement");
                }
                if (smokeTicks==50) screenshots.takeScreenshot();
                if (smokeTicks==52) {
                    action("slot8",true,0); action("place",true,0);
                    check(world.rotation(smokeStairX,smokeStairY,smokeStairZ)==1,"rotator turns stairs by 90 degrees");
                    check(world.get(smokeStairX,smokeStairY,smokeStairZ)==Block.STAIRS,"tool does not replace block");
                }
                if (smokeTicks==65) screenshots.takeScreenshot();
                if (smokeTicks==67) { back(); smokeClick("main"); smokeClick("worlds"); smokeClick("world2"); smokeStage++; }
            }
            case 5 -> {
                if (screen!=Screen.GAME) return;
                check(world.doorAt((int)smokeHomeX,smokeDoorY,(int)smokeHomeZ-3)==null,"slot isolation");
                back(); smokeClick("main"); smokeClick("worlds"); smokeClick("world1"); smokeStage++;
            }
            case 6 -> {
                if (screen!=Screen.GAME) return;
                Door door=world.doorAt((int)smokeHomeX,smokeDoorY,(int)smokeHomeZ-3);
                check(door!=null && door.open(),"door persisted after reload");
                check(world.get(smokeStairX,smokeStairY,smokeStairZ)==Block.STAIRS
                        && world.rotation(smokeStairX,smokeStairY,smokeStairZ)==1,"stairs rotation persisted");
                player.position.set(560.5f,World.HEIGHT+1,320.5f); updateCamera(); smokeStage++; smokeTicks=0;
            }
            case 7 -> {
                if (!worldView.ready(player.position.x,player.position.z) || smokeTicks<45) return;
                check(worldView.loadedCount()<=(2*options.distance+1)*(2*options.distance+1),"distant chunks unloaded");
                check(world.cachedRegions()<=World.REGION_CACHE_LIMIT,"bounded data cache");
                screenshots.takeScreenshot();
                System.out.println("BARN0CRAFT_SMOKE_OK: menus, settings, slots, doors, stairs, rotator, saves and streaming");
                smokeStage++; smokeTicks=0;
            }
            case 8 -> { if (smokeTicks>=3) quit(); }
            default -> { }
        }
    }
}
