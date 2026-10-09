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
    private enum Screen { MAIN, WORLDS, MODS, CREATE_WORLD, DELETE_WORLD, SETTINGS, PAUSE, INVENTORY, STONECUTTER, FURNACE, CHEST, LOADING, GAME }
    private World world;
    private final Player player = new Player();
    private final boolean[] movement = new boolean[4];
    private WorldView worldView;
    private Hud hud;
    private Menu menu;
    private Geometry highlight, stepHighlight, breakOverlay;
    private Material breakMaterial;
    private AmbientLight ambientLight;
    private DirectionalLight sunLight;
    private Atmosphere atmosphere;
    private LoadingPanorama panorama;
    private boolean active, jump, quitting, mapInteraction, flyUp, flyDown;
    private int creativePage;
    private int selected = 1, slot;
    private int deletingSlot;
    private final Inventory inventory = new Inventory();
    private PythonModApi pythonMods;
    private String inventoryDrag;
    private boolean inventoryDragOne;
    private boolean commandOpen;
    private final StringBuilder commandBuffer=new StringBuilder();
    private boolean creatingWorldName;
    private final StringBuilder worldNameBuffer=new StringBuilder();
    private String worldName="";
    private int stonecutterX, stonecutterY, stonecutterZ;
    private int furnaceX, furnaceY, furnaceZ;
    private int chestX,chestY,chestZ;
    private boolean breakHeld;
    private int breakX, breakY, breakZ;
    private float breakProgress;
    private float yaw, pitch, autosave;
    private int lastPlayerHealth=Player.MAX_HEALTH;
    private int screenWidth, screenHeight;
    private Screen screen = Screen.MAIN, settingsBack = Screen.MAIN;
    private String message = "";
    private final boolean smoke;
    private boolean furnitureSmoke,creativeSmoke,panoramaSmoke;
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
        Barnocraft game=new Barnocraft(Arrays.asList(args).contains("--smoke") || Arrays.asList(args).contains("--furniture-smoke") || Arrays.asList(args).contains("--creative-smoke") || Arrays.asList(args).contains("--panorama-smoke"));
        game.panoramaSmoke=Arrays.asList(args).contains("--panorama-smoke");
        game.creativeSmoke=Arrays.asList(args).contains("--creative-smoke");
        game.furnitureSmoke=Arrays.asList(args).contains("--furniture-smoke");
        AppSettings settings=new AppSettings(true);
        settings.setTitle("Barnocraft Java Edition"); settings.setResolution(1280,720);
        settings.setSamples(0); settings.setVSync(true); settings.setFrameRate(120);
        settings.setAudioRenderer(null); settings.setResizable(true);
        game.setSettings(settings); game.setShowSettings(false); game.start();
    }
    @Override public void simpleInitApp() {
        flyCam.setEnabled(false); setDisplayFps(false); setDisplayStatView(false);
        inputManager.deleteMapping(INPUT_MAPPING_EXIT);
        viewPort.setBackgroundColor(new ColorRGBA(135/255f,206/255f,235/255f,1));
        ambientLight=new AmbientLight(); ambientLight.setColor(ColorRGBA.White.mult(.7f)); rootNode.addLight(ambientLight);
        sunLight=new DirectionalLight(); sunLight.setColor(ColorRGBA.White.mult(.8f));
        sunLight.setDirection(new Vector3f(-60,-100,-40).normalizeLocal()); rootNode.addLight(sunLight);
        atmosphere=new Atmosphere(assetManager); rootNode.attachChild(atmosphere.sky);
        highlight=new Geometry("Target",new WireBox(.5015f,.5015f,.5015f));
        Material outline=new Material(assetManager,"Common/MatDefs/Misc/Unshaded.j3md");
        outline.setColor("Color",new ColorRGBA(.06f,.06f,.06f,1)); highlight.setMaterial(outline);
        highlight.setCullHint(Spatial.CullHint.Always); rootNode.attachChild(highlight);
        stepHighlight=new Geometry("Upper stair target",new WireBox(.5015f,.5015f,.5015f));
        stepHighlight.setMaterial(outline); stepHighlight.setCullHint(Spatial.CullHint.Always); rootNode.attachChild(stepHighlight);
        breakOverlay=new Geometry("Breaking",new WireBox(.515f,.515f,.515f));
        breakMaterial=new Material(assetManager,"Common/MatDefs/Misc/Unshaded.j3md");
        breakMaterial.setColor("Color",new ColorRGBA(.95f,.18f,.08f,.9f));
        breakOverlay.setMaterial(breakMaterial); breakOverlay.setCullHint(Spatial.CullHint.Always); rootNode.attachChild(breakOverlay);
        hud=new Hud(assetManager); guiNode.attachChild(hud.node);
        menu=new Menu(assetManager); guiNode.attachChild(menu.node);
        try { options.load(savesPath.resolve("settings.properties")); } catch (IOException e) { message=e.getMessage(); }
        hud.setTexturePack(TexturePack.from(options.texturePack));
        panorama=new LoadingPanorama(assetManager);rootNode.attachChild(panorama.view.node);
        bind(); show(Screen.MAIN);
        if (smoke) {
            setPauseOnLostFocus(false);
            screenshots=new ScreenshotAppState("target/",panoramaSmoke?"panorama-smoke-":creativeSmoke?"creative-smoke-":"menu-smoke-"); stateManager.attach(screenshots);
        }
    }
    private void bind() {
        int[] keys={KeyInput.KEY_W,KeyInput.KEY_S,KeyInput.KEY_A,KeyInput.KEY_D};
        for (int i=0;i<keys.length;i++) mapping("move"+i,new KeyTrigger(keys[i]));
        mapping("descend",new KeyTrigger(KeyInput.KEY_LSHIFT));
        mapping("jump",new KeyTrigger(KeyInput.KEY_SPACE)); mapping("pause",new KeyTrigger(KeyInput.KEY_ESCAPE));
        mapping("map_toggle",new KeyTrigger(KeyInput.KEY_M));
        mapping("inventory",new KeyTrigger(KeyInput.KEY_E));
        mapping("quit",new KeyTrigger(KeyInput.KEY_F10));
        mapping("command_toggle",new KeyTrigger(KeyInput.KEY_F3));
        mapping("command_enter",new KeyTrigger(KeyInput.KEY_RETURN));
        mapping("command_backspace",new KeyTrigger(KeyInput.KEY_BACK));
        mapping("command_space",new KeyTrigger(KeyInput.KEY_SPACE));
        int[] letters={KeyInput.KEY_A,KeyInput.KEY_B,KeyInput.KEY_C,KeyInput.KEY_D,KeyInput.KEY_E,KeyInput.KEY_F,
            KeyInput.KEY_G,KeyInput.KEY_H,KeyInput.KEY_I,KeyInput.KEY_J,KeyInput.KEY_K,KeyInput.KEY_L,
            KeyInput.KEY_M,KeyInput.KEY_N,KeyInput.KEY_O,KeyInput.KEY_P,KeyInput.KEY_Q,KeyInput.KEY_R,
            KeyInput.KEY_S,KeyInput.KEY_T,KeyInput.KEY_U,KeyInput.KEY_V,KeyInput.KEY_W,KeyInput.KEY_X,
            KeyInput.KEY_Y,KeyInput.KEY_Z};
        for(int i=0;i<letters.length;i++) mapping("command_char_"+(char)('a'+i),new KeyTrigger(letters[i]));
        for(int i=0;i<10;i++) mapping("command_char_"+i,new KeyTrigger(new int[]{KeyInput.KEY_0,KeyInput.KEY_1,
            KeyInput.KEY_2,KeyInput.KEY_3,KeyInput.KEY_4,KeyInput.KEY_5,KeyInput.KEY_6,KeyInput.KEY_7,
            KeyInput.KEY_8,KeyInput.KEY_9}[i]));
        mapping("command_char_underscore",new KeyTrigger(KeyInput.KEY_MINUS));
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
            if (!active || mapInteraction) return;
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
        if (creatingWorldName) {
            if (!pressed) return;
            if (name.equals("break")) { menuAction(menu.click(inputManager.getCursorPosition())); return; }
            if (name.equals("pause")) {
                creatingWorldName=false; worldNameBuffer.setLength(0); message=""; show(Screen.WORLDS); return;
            }
            if (name.equals("command_backspace")) {
                if(!worldNameBuffer.isEmpty()) worldNameBuffer.deleteCharAt(worldNameBuffer.length()-1);
            } else if (name.equals("command_space")) {
                if(worldNameBuffer.length()<32) worldNameBuffer.append(' ');
            } else if (name.startsWith("command_char_")) {
                String character=name.substring("command_char_".length());
                if(worldNameBuffer.length()<32) worldNameBuffer.append(character.equals("underscore")?'_':character.charAt(0));
            } else if(name.equals("command_enter")) {
                createNamedWorld(); return;
            } else return;
            show(Screen.CREATE_WORLD);
            return;
        }
        if (name.equals("command_toggle") && pressed) {
            if (active && screen==Screen.GAME) {
                commandOpen=!commandOpen;
                flyUp=false;flyDown=false;jump=false;Arrays.fill(movement,false);
                commandBuffer.setLength(0);
                resetBreakProgress();
                hud.commandLine(commandOpen?"> ":"");
            }
            return;
        }
        if(name.equals("map_toggle") && pressed && active && !commandOpen) {
            mapInteraction=!mapInteraction;inputManager.setCursorVisible(mapInteraction);
            hud.notify(mapInteraction?"Метки карты: ЛКМ поставить · ПКМ убрать · M закрыть":"Режим меток закрыт");
            return;
        }
        if (commandOpen) {
            flyUp=false; flyDown=false;
            if (!pressed) return;
            if (name.equals("pause")) {
                commandOpen=false; commandBuffer.setLength(0); hud.commandLine(""); return;
            }
            if (name.equals("command_backspace")) {
                if (!commandBuffer.isEmpty()) commandBuffer.deleteCharAt(commandBuffer.length()-1);
            } else if (name.equals("command_space")) {
                if (commandBuffer.length()<120) commandBuffer.append(' ');
            } else if (name.startsWith("command_char_")) {
                String character=name.substring("command_char_".length());
                if (commandBuffer.length()<120) commandBuffer.append(character.equals("underscore")?'_':character.charAt(0));
            } else if (name.equals("command_enter")) {
                String entered=commandBuffer.toString();
                String result=Cheats.execute(entered,inventory,world,player);
                commandOpen=false; commandBuffer.setLength(0);
                hud.commandLine(entered+"  |  "+result);
                return;
            }
            hud.commandLine("> "+commandBuffer+"_");
            return;
        }
        if (name.equals("inventory") && pressed) {
            if (screen==Screen.GAME) show(Screen.INVENTORY);
            else if (screen==Screen.INVENTORY || screen==Screen.STONECUTTER || screen==Screen.FURNACE || screen==Screen.CHEST) { inventoryDrag=null; inventoryDragOne=false; back(); }
            return;
        }
        if(name.equals("jump"))flyUp=active && pressed;
        if(name.equals("descend")) { flyDown=active && pressed; return; }
        if (name.startsWith("move")) { movement[name.charAt(4)-'0']=active && !mapInteraction && pressed; return; }
        if (name.equals("break") || name.equals("place")) {
            if (name.equals("break")) {
                breakHeld = pressed;
                if (!pressed) { resetBreakProgress(); if (active) return; }
            }
        }
        if (!active) {
            if (name.equals("break") || name.equals("place")) {
                boolean dragScreen=screen==Screen.INVENTORY || screen==Screen.STONECUTTER || screen==Screen.FURNACE || screen==Screen.CHEST;
                if (!dragScreen) { if (name.equals("break") && pressed) menuAction(menu.click(inputManager.getCursorPosition())); }
                else if(pressed && inventoryDrag==null) {
                    if(inventoryDrag==null)inventoryDragOne=name.equals("place");
                    menuAction(menu.click(inputManager.getCursorPosition()));
                } else if(!pressed && inventoryDrag!=null)menuAction(menu.click(inputManager.getCursorPosition()));
            }
            return;
        }
        if (!pressed) return;
        if(mapInteraction && (name.equals("break") || name.equals("place"))) {
            var cell=hud.modOverlayCell(inputManager.getCursorPosition().x,inputManager.getCursorPosition().y);
            if(cell!=null) {
                Map<String,Object> click=new LinkedHashMap<>(cell);
                click.put("button",name.equals("break")?"left":"right");
                world.mods.event("ui.click",click);
                resetBreakProgress();breakHeld=false;return;
            }
            return;
        }
        if(name.equals("place") && (inventory.hotbarItemAt(selected)==ItemType.BUCKET || inventory.hotbarItemAt(selected)==ItemType.WATER_BUCKET)) {
            World.Hit changed=world.useBucket(inventory,selected,cam.getLocation(),cam.getDirection());
            if(changed!=null)worldView.changed(changed.x(),changed.z());return;
        }
        if (name.equals("quit")) { quit(); return; }
        if (name.equals("pause")) { back(); return; }
        if(name.equals("place") && inventory.hotbarItemAt(selected)!=null
                && inventory.hotbarItemAt(selected).foodValue()>0) {
            if(player.eatFood(inventory,selected))hud.notify("Здоровье восстановлено");
            else hud.notify(player.health()>=Player.MAX_HEALTH?"Здоровье уже полное":"Это нельзя съесть");
            return;
        }
        if((name.equals("break") || name.equals("place")) && world.mods.playerAction(name.equals("place"),cam.getLocation(),cam.getDirection(),player,inventory,selected)) {
            resetBreakProgress();return;
        }
        if (name.equals("break") && pressed && world.attackSheep(cam.getLocation(),cam.getDirection(),Player.REACH,inventory)) {
            inventory.damageToolAt(selected);
            breakHeld=false;
            resetBreakProgress();
            return;
        }
        if (name.equals("break") && pressed && world.attackZombie(cam.getLocation(),cam.getDirection(),Player.REACH)) {
            inventory.damageToolAt(selected);
            breakHeld=false;
            resetBreakProgress();
            return;
        }
        if (name.equals("jump")) jump=true;
        else if (name.startsWith("slot")) select(name.charAt(4)-'0');
        else if (name.equals("next")) select((selected+1)%Block.HOTBAR.length);
        else if (name.equals("previous")) select((selected+Block.HOTBAR.length-1)%Block.HOTBAR.length);
        else if (name.equals("break")) { }
        else if (name.equals("place")) edit(name.equals("place"));
    }
    private void show(Screen next) {
        screen=next; active=next==Screen.GAME; jump=false; flyUp=false; flyDown=false; Arrays.fill(movement,false);
        if(!active) mapInteraction=false;
        inputManager.setCursorVisible(!active || mapInteraction); hud.active(active);
        hud.node.setCullHint(active?Spatial.CullHint.Inherit:Spatial.CullHint.Always);
        highlight.setCullHint(Spatial.CullHint.Always);
        menu.loadingBackdrop(next==Screen.LOADING || next==Screen.MAIN);
        if (active) { updateCamera();menu.hide(); return; }
        List<Menu.Button> buttons=new ArrayList<>();
        String title="Barnocraft", subtitle="Java Edition";
        switch(next) {
            case MAIN -> {
                buttons.add(new Menu.Button("worlds","Миры")); buttons.add(new Menu.Button("mods","Моды"));
                buttons.add(new Menu.Button("settings","Настройки"));
                buttons.add(new Menu.Button("quit","Выход"));
            }
            case WORLDS -> {
                menu.showWorlds(saves,message); return;
            }
            case MODS -> {
                menu.showMods(modCatalog()); return;
            }
            case CREATE_WORLD -> { menu.showCreateWorld(worldNameBuffer.toString(),message); return; }
            case DELETE_WORLD -> { menu.showDeleteWorld(saves.worldName(deletingSlot),message); return; }
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
            case INVENTORY -> { if(player.creative())menu.showCreative(inventory,creativePage,selected); else menu.showInventoryModern(inventory); return; }
            case STONECUTTER -> { menu.showStonecutterModern(inventory,world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ),message); return; }
            case FURNACE -> { menu.showFurnaceModern(inventory,world.furnaceAt(furnaceX,furnaceY,furnaceZ),world.furnaceProgress(furnaceX,furnaceY,furnaceZ),message); return; }
            case CHEST -> { menu.showChestModern(inventory,world.chestAt(chestX,chestY,chestZ)); return; }
            case LOADING -> { menu.showLoading(loadingSlot); return; }
            default -> { }
        }
        menu.show(title,subtitle,buttons,message);
    }
    private void menuAction(String id) {
        if (id==null || screen==Screen.LOADING) return;
        message="";
        if(screen==Screen.INVENTORY && player.creative()) {
            if(id.equals("creative_prev") || id.equals("creative_next")) {
                int pages=Math.max(1,(Menu.creativeItems().size()+39)/40);
                creativePage=Math.floorMod(creativePage+(id.equals("creative_next")?1:-1),pages);
                show(Screen.INVENTORY);return;
            }
            if(id.startsWith("creative:")) {
                ItemType item=ItemType.valueOf(id.substring(9));inventory.creativePick(selected,item);
                show(Screen.INVENTORY);return;
            }
            if(id.startsWith("slot:")) { select(Integer.parseInt(id.substring(5)));show(Screen.INVENTORY);return; }
        }
        if (id.matches("world[1-5]")) { startWorld(id.charAt(5)-'0'); return; }
        if (id.equals("create_world")) {
            creatingWorldName=true; worldNameBuffer.setLength(0); message="";
            show(Screen.CREATE_WORLD);
            return;
        }
        if (id.equals("cancel_world_name")) {
            creatingWorldName=false; worldNameBuffer.setLength(0); message="";
            show(Screen.WORLDS);
            return;
        }
        if (id.equals("confirm_world_name")) {
            createNamedWorld();
            return;
        }
        if (id.matches("delete_world_[1-5]")) {
            deletingSlot=id.charAt(id.length()-1)-'0';
            show(Screen.DELETE_WORLD);
            return;
        }
        if (id.equals("cancel_delete_world")) {
            deletingSlot=0; show(Screen.WORLDS); return;
        }
        if (id.equals("confirm_delete_world")) {
            try {
                boolean deleted=saves.delete(deletingSlot);
                message=deleted?"Мир удалён":"Сохранение уже отсутствует";
            } catch(IOException e) {
                message="Не удалось удалить мир: "+e.getMessage();
            }
            deletingSlot=0;
            show(Screen.WORLDS);
            return;
        }
        if (id.startsWith("hotbar:")) {
            id = "slot:" + id.substring(7);
        }
        if(id.startsWith("chest:")) {
            if(inventoryDrag!=null && inventoryDrag.startsWith("slot:")) {
                int from=Integer.parseInt(inventoryDrag.substring(5)),to=Integer.parseInt(id.substring(6));
                world.moveChestItem(chestX,chestY,chestZ,to,inventory,from,false,inventoryDragOne);
                inventoryDrag=null;inventoryDragOne=false;show(Screen.CHEST);
            } else if(inventoryDrag==null) { inventoryDrag=id; }
            return;
        }
        if(id.startsWith("slot:") && inventoryDrag!=null && inventoryDrag.startsWith("chest:")) {
            int from=Integer.parseInt(inventoryDrag.substring(6)),to=Integer.parseInt(id.substring(5));
            world.moveChestItem(chestX,chestY,chestZ,from,inventory,to,true,inventoryDragOne);
            inventoryDrag=null;inventoryDragOne=false;show(Screen.CHEST);return;
        }
        if (id.equals("craft_result")) {
            ItemType result = inventory.craftPreview();
            inventory.craftFromGrid();
            message = result == null ? "Рецепт не найден" : "Создано: " + result.title;
            show(Screen.INVENTORY);
            return;
        }
        if (id.startsWith("recipe:")) {
            ItemType recipe=ItemType.valueOf(id.substring("recipe:".length()));
            message=world.selectStonecutterRecipe(stonecutterX,stonecutterY,stonecutterZ,recipe)
                    ? "Выбрано: "+recipe.title : "Не удалось выбрать рецепт";
            show(Screen.STONECUTTER);
            return;
        }
        if (id.equals("machine_craft")) {
            World.MachineSlots slots=world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ);
            if (slots==null) { show(Screen.GAME); return; }
            message=world.craftStonecutterHead(stonecutterX,stonecutterY,stonecutterZ)
                    ? "Изготовлено: "+slots.recipe().title
                    : "Нужны 1 уголь и "+World.stonecutterMaterialCount(slots.recipe())+" "+World.stonecutterMaterial(slots.recipe()).title;
            show(Screen.STONECUTTER);
            return;
        }
        if (id.startsWith("machine:")) {
            int machineSlot=Integer.parseInt(id.substring("machine:".length()));
            if (machineSlot==2) {
                World.MachineSlots slots=world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ);
                if (slots==null) { show(Screen.GAME); return; }
                if (slots.output().count()>0) {
                    message=world.takeStonecutterOutput(stonecutterX,stonecutterY,stonecutterZ,inventory)
                        ? "Предмет забран" : "Нет места в инвентаре";
                } else {
                    message=world.craftStonecutterHead(stonecutterX,stonecutterY,stonecutterZ)
                        ? "Изготовлено: "+slots.recipe().title
                        : "Нужны 1 уголь и "+World.stonecutterMaterialCount(slots.recipe())+" "+World.stonecutterMaterial(slots.recipe()).title;
                }
                show(Screen.STONECUTTER);
                return;
            }
            if (inventoryDrag==null) {
                World.MachineSlots slots=world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ);
                if (slots!=null && slots.at(machineSlot).item()!=null) inventoryDrag=id;
            } else if (inventoryDrag.startsWith("slot:")) {
                int inventorySlot=Integer.parseInt(inventoryDrag.substring(5));
                World.MachineSlots state=world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ);
                ItemType required=machineSlot==0?ItemType.COAL:World.stonecutterMaterial(state.recipe());
                boolean moved=world.putStonecutterItem(stonecutterX,stonecutterY,stonecutterZ,machineSlot,inventory,inventorySlot), anyMoved=moved;
                if (!inventoryDragOne) while (moved && inventory.countAt(inventorySlot)>0) {
                    moved=world.putStonecutterItem(stonecutterX,stonecutterY,stonecutterZ,machineSlot,inventory,inventorySlot); anyMoved|=moved;
                }
                message=anyMoved ? "Предмет помещён" : "Этот слот принимает только "+required.title;
                inventoryDrag=null;
                inventoryDragOne=false;
                show(Screen.STONECUTTER);
            }
            return;
        }
        if (id.equals("furnace_smelt")) {
            World.FurnaceSlots state=world.furnaceAt(furnaceX,furnaceY,furnaceZ);
            ItemType result=state==null?null:World.smeltingResult(state.input().item());
            message=world.smeltIron(furnaceX,furnaceY,furnaceZ)
                ? "Получено: "+result.title : "Нужны уголь и песок, железо или золото; освободите выход";
            show(Screen.FURNACE);
            return;
        }
        if (id.startsWith("furnace:")) {
            int machineSlot=Integer.parseInt(id.substring("furnace:".length()));
            World.FurnaceSlots state=world.furnaceAt(furnaceX,furnaceY,furnaceZ);
            if (state==null) { show(Screen.GAME); return; }
            if (machineSlot==2) {
                message=state.output().count()>0
                    ? (world.takeFurnaceOutput(furnaceX,furnaceY,furnaceZ,inventory)
                        ? "Продукт забран" : "Нет места в инвентаре")
                    : "Готового продукта пока нет";
                show(Screen.FURNACE);
                return;
            }
            if (inventoryDrag==null) {
                if (state.at(machineSlot).item()!=null) inventoryDrag=id;
            } else if (inventoryDrag.startsWith("slot:")) {
                int inventorySlot=Integer.parseInt(inventoryDrag.substring(5));
                ItemType source=inventory.itemAt(inventorySlot);
                boolean moved=world.putFurnaceItem(furnaceX,furnaceY,furnaceZ,machineSlot,inventory,inventorySlot), anyMoved=moved;
                if (!inventoryDragOne) while (moved && inventory.countAt(inventorySlot)>0) {
                    moved=world.putFurnaceItem(furnaceX,furnaceY,furnaceZ,machineSlot,inventory,inventorySlot); anyMoved|=moved;
                }
                message=anyMoved ? "Предмет помещён" : "Слот принимает "+(machineSlot==0?"уголь":"предметы для плавки или готовки")
                        + (source==null?"":"; выбрано: "+source.title);
                inventoryDrag=null;
                inventoryDragOne=false;
                show(Screen.FURNACE);
            }
            return;
        }
        if (id.startsWith("slot:") && inventoryDrag!=null && inventoryDrag.startsWith("furnace:")) {
            int machineSlot=Integer.parseInt(inventoryDrag.substring("furnace:".length()));
            int inventorySlot=Integer.parseInt(id.substring(5));
            boolean moved=world.takeFurnaceItem(furnaceX,furnaceY,furnaceZ,machineSlot,inventory,inventorySlot), anyMoved=moved;
            if (!inventoryDragOne) while (moved) { moved=world.takeFurnaceItem(furnaceX,furnaceY,furnaceZ,machineSlot,inventory,inventorySlot); anyMoved|=moved; }
            message=anyMoved ? "Предмет забран" : "Не удалось забрать предмет";
            inventoryDrag=null;
            inventoryDragOne=false;
            show(Screen.FURNACE);
            return;
        }
        if (id.startsWith("slot:") && inventoryDrag!=null && inventoryDrag.startsWith("machine:")) {
            int machineSlot=Integer.parseInt(inventoryDrag.substring("machine:".length()));
            int inventorySlot=Integer.parseInt(id.substring(5));
            boolean moved=world.takeStonecutterItem(stonecutterX,stonecutterY,stonecutterZ,machineSlot,inventory,inventorySlot);
            if (!inventoryDragOne) while (moved) moved=world.takeStonecutterItem(stonecutterX,stonecutterY,stonecutterZ,machineSlot,inventory,inventorySlot);
            inventoryDrag=null;
            inventoryDragOne=false;
            show(Screen.STONECUTTER);
            return;
        }
        if (id.startsWith("slot:") || id.startsWith("craft:")) {
            if (inventoryDrag == null) {
                if (!inventory.hasItemRef(id)) return;
                inventoryDrag = id;
                return;
            }
            if (!inventoryDrag.equals(id)) {
                inventory.moveRef(inventoryDrag, id, inventoryDragOne);
            }
            Screen inventoryScreen=screen;
            inventoryDrag = null;
            inventoryDragOne=false;
            show(inventoryScreen);
            return;
        }
        switch(id) {
            case "worlds" -> show(Screen.WORLDS);
            case "mods" -> show(Screen.MODS);
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
            case WORLDS, MODS, CREATE_WORLD -> { creatingWorldName=false; show(Screen.MAIN); }
            case DELETE_WORLD -> { deletingSlot=0; show(Screen.WORLDS); }
            case SETTINGS -> show(settingsBack);
            case CHEST -> { world.closeChest(chestX,chestY,chestZ);inventoryDrag=null;show(Screen.GAME); }
            case INVENTORY, STONECUTTER, FURNACE -> { inventoryDrag=null; show(Screen.GAME); }
            default -> { }
        }
    }
    private List<Menu.ModEntry> modCatalog() {
        Path directory=PythonModApi.defaultDirectory();
        if(!Files.isDirectory(directory)) return List.of();
        List<Menu.ModEntry> entries=new ArrayList<>();
        try(var paths=Files.list(directory)) {
            for(Path folder:paths.sorted().toList()) {
                if(Files.isRegularFile(folder) && folder.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".py")) {
                    String filename=folder.getFileName().toString();String id=PythonModApi.standaloneId(folder);
                    entries.add(new Menu.ModEntry(id,id,"1.0.0",PythonModApi.API_VERSION,true,filename+" · одиночный файл",""));
                    continue;
                }
                if(!Files.isDirectory(folder)) continue;
                Path manifestPath=folder.resolve("mod.json");
                if(!Files.isRegularFile(manifestPath)) continue;
                try {
                    if(Files.size(manifestPath)>1_048_576) throw new IOException("manifest слишком большой");
                    Map<String,Object> manifest=Json.obj(Json.parse(Files.readString(manifestPath)));
                    entries.add(new Menu.ModEntry(
                            Json.str(manifest,"id",folder.getFileName().toString()),
                            Json.str(manifest,"name",folder.getFileName().toString()),
                            Json.str(manifest,"version","?"),
                            Json.integer(manifest,"api",0,0,100),
                            !Boolean.FALSE.equals(manifest.get("enabled")),
                            Json.str(manifest,"entry","main.py"),""));
                } catch(Exception e) {
                    entries.add(new Menu.ModEntry(folder.getFileName().toString(),
                            folder.getFileName().toString(),"?",0,false,"",e.getMessage()));
                }
            }
        } catch(IOException e) {
            return List.of(new Menu.ModEntry("mods","Папка модов недоступна","",0,false,"",e.getMessage()));
        }
        return List.copyOf(entries);
    }
    private void startWorld(int targetSlot) {
        startWorld(targetSlot,null);
    }
    private void createNamedWorld() {
        String name=worldNameBuffer.toString().strip();
        if(name.isEmpty()) { message="Имя не должно быть пустым"; show(Screen.CREATE_WORLD); return; }
        if(name.length()>32) { message="Максимум 32 символа"; show(Screen.CREATE_WORLD); return; }
        for(int candidate=1;candidate<=5;candidate++) {
            if(!saves.exists(candidate)) {
                creatingWorldName=false; worldNameBuffer.setLength(0); message="";
                startWorld(candidate,name);
                return;
            }
        }
        message="Все слоты миров заняты; сначала освободите слот";
        show(Screen.CREATE_WORLD);
    }
    private void startWorld(int targetSlot,String requestedWorldName) {
        if (!saveNow()) return;
        unload(); loadingSlot=targetSlot; show(Screen.LOADING);
        loading=io.submit(()->{
            if (saves.exists(targetSlot)) return saves.load(targetSlot);
            World generated=smoke?new World(42+targetSlot):new World();
            Player spawn=new Player(); spawn.respawn(generated);
            SaveStore.Pose pose=new SaveStore.Pose(spawn.position.x,spawn.position.y,spawn.position.z,0,0,1);
            String createdName=requestedWorldName==null?"Мир "+targetSlot:requestedWorldName;
            saves.save(targetSlot,generated.snapshot(),pose,Inventory.emptySnapshot(),createdName);
            return new SaveStore.Saved(generated,pose,Inventory.emptySnapshot(),createdName);
        });
    }
    private void finishLoading() {
        if (loading!=null && loading.isDone()) {
            try {
                SaveStore.Saved saved=loading.get(); world=saved.world(); slot=loadingSlot; worldName=saved.worldName();
                inventory.restore(saved.inventory());
                SaveStore.Pose p=saved.pose(); player.position.set(p.x(),p.y(),p.z()); player.velocityY=0; player.grounded=false;
                player.setCreative(p.creative());inventory.setCreative(p.creative());
                player.restoreHealth(p.health());
                player.restoreHunger(p.hunger());
                player.restoreRespawnPoint(p.respawnX(),p.respawnY(),p.respawnZ());
                lastPlayerHealth=player.health();
                world.ensureSheepNear(player.position);
                yaw=p.yaw(); pitch=p.pitch(); select(p.selected());
                if (player.collides(world)) player.respawn(world);
                worldView=new WorldView(assetManager,world,TexturePack.from(options.texturePack)); rootNode.attachChild(worldView.node); updateCamera();
                pythonMods=new PythonModApi(PythonModApi.defaultDirectory(),world,player,inventory);
                pythonMods.start();
            } catch(Exception e) { failure("Не удалось открыть мир",e); show(Screen.WORLDS); }
            loading=null;
        }
        if (screen==Screen.LOADING && worldView!=null && worldView.ready(player.position.x,player.position.z)) {
            autosave=0; show(Screen.GAME);
        }
    }
    private SaveStore.Pose pose() {
        return new SaveStore.Pose(player.position.x,player.position.y,player.position.z,yaw,pitch,selected,Math.max(1,player.health()),player.hunger(),player.respawnX(),player.respawnY(),player.respawnZ(),player.creative());
    }
    private void autoSave() {
        if (saving!=null || world==null) return;
        World.Snapshot snapshot=world.snapshot(); SaveStore.Pose pose=pose(); int target=slot;
        Inventory.Snapshot inventorySnapshot = inventory.snapshot();
        String savedName=worldName;
        saving=io.submit(()->{ saves.save(target,snapshot,pose,inventorySnapshot,savedName); return null; });
    }
    private boolean saveNow() {
        if (world==null) return true;
        try {
            if (saving!=null) { saving.get(); saving=null; }
            saves.save(slot,world.snapshot(),pose(),inventory.snapshot(),worldName); autosave=0; return true;
        } catch(Exception e) { saving=null; failure("Не удалось сохранить мир",e); show(Screen.PAUSE); return false; }
    }
    private void failure(String prefix,Exception e) {
        Throwable cause=e.getCause()==null?e:e.getCause();
        message=prefix+": "+cause.getMessage(); System.err.println(message);
    }
    private void unload() {
        if (pythonMods!=null) { pythonMods.close(); pythonMods=null; }
        hud.modOverlays(java.util.Map.of());
        if (worldView!=null) worldView.close(); worldView=null; world=null; slot=0; worldName="";
    }
    private void quit() {
        if(screen==Screen.CHEST && world!=null)world.closeChest(chestX,chestY,chestZ);
        if (quitting || !saveNow()) return;
        quitting=true; super.stop();
    }
    @Override public void requestClose(boolean esc) { enqueue(()->{ quit(); return null; }); }
    @Override public void destroy() {
        if (worldView!=null) worldView.close();
        if(panorama!=null)panorama.close();
        io.shutdownNow(); super.destroy();
    }
    @Override public void loseFocus() {
        super.loseFocus();
        if (active && !smoke) { show(Screen.PAUSE); saveNow(); }
    }
    private Block selectedBlock() { return Inventory.toBlock(inventory.hotbarItemAt(selected)); }
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
        Block blockToPlace = selectedBlock();
        if (place && blockToPlace==Block.ROTATOR) {
            if (world.rotate(hit.x(),hit.y(),hit.z(),player)) { inventory.damageToolAt(selected); worldView.changed(hit.x(),hit.z()); }
            return;
        }
        if (place && world.get(hit.x(),hit.y(),hit.z())==Block.STONECUTTER) {
            stonecutterX=hit.x(); stonecutterY=hit.y(); stonecutterZ=hit.z();
            show(Screen.STONECUTTER);
            return;
        }
        if (place && world.get(hit.x(),hit.y(),hit.z())==Block.FURNACE) {
            furnaceX=hit.x(); furnaceY=hit.y(); furnaceZ=hit.z();
            show(Screen.FURNACE);
            return;
        }
        if(place && world.get(hit.x(),hit.y(),hit.z())==Block.CHEST) {
            chestX=hit.x();chestY=hit.y();chestZ=hit.z();
            if(world.openChest(chestX,chestY,chestZ))show(Screen.CHEST);
            else hud.notify("Этот сундук уже открывали");
            return;
        }
        if(place && world.get(hit.x(),hit.y(),hit.z()).bed()) {
            player.setRespawnPoint(world.bedSpawn(hit.x(),hit.y(),hit.z()));
            hud.notify(world.sleepAt(hit.x(),hit.y(),hit.z())); return;
        }
        ItemType held=inventory.hotbarItemAt(selected);
        if(place && held==ItemType.STONE_HOE && world.get(hit.x(),hit.y(),hit.z())==Block.GRASS) {
            if(world.till(hit.x(),hit.y(),hit.z())) {
                inventory.damageToolAt(selected);worldView.changed(hit.x(),hit.z());
            }
            return;
        }
        if(place && held==ItemType.WHEAT_SEEDS && world.get(hit.x(),hit.y(),hit.z())==Block.FARMLAND) {
            if(world.plantCrop(hit.x(),hit.y(),hit.z()))inventory.consumeAt(selected,held);
            return;
        }

        if (place && world.doorAt(hit.x(),hit.y(),hit.z())!=null) {
            if (world.toggleDoor(hit.x(),hit.y(),hit.z(),player)) worldView.changed(hit.x(),hit.z());
            return;
        }
        if(place && held==ItemType.TORCH) {
            hud.notify("Факел светит в руке. Для установки нужен фонарь"); return;
        }
        int x=hit.x()+(place?hit.nx():0),y=hit.y()+(place?hit.ny():0),z=hit.z()+(place?hit.nz():0);
        if (place && blockToPlace==Block.DOOR) {
            int facing=Math.floorMod(Math.round(yaw/FastMath.HALF_PI),4);
            if (world.placeDoor(x,y,z,facing,player)) {
                inventory.consumeAt(selected,inventory.hotbarItemAt(selected));
                worldView.changed(x,z);
            }
            return;
        }
        if(place && blockToPlace==Block.BED) {
            int facing=Math.floorMod(Math.round(-yaw/FastMath.HALF_PI),4);
            if(world.placeBed(x,y,z,facing,player)) {
                inventory.consumeAt(selected,ItemType.BED); worldView.changed(x,z);
            } else hud.notify("Кровати нужны два свободных блока на твёрдой опоре");
            return;
        }
        if (place && blockToPlace==Block.STAIRS) {
            int facing=Math.floorMod(Math.round(-yaw/FastMath.HALF_PI),4);
            if (world.placeStairs(x,y,z,facing,player)) {
                inventory.consumeAt(selected,inventory.hotbarItemAt(selected));
                worldView.changed(x,z);
            }
            return;
        }
        if (player.intersects(x,y,z) || (place && world.get(x,y,z)!=Block.AIR)) return;
        if (!place) {
            Block broken=world.get(x,y,z);
            if (world.set(x,y,z,Block.AIR)) {
                inventory.addBlock(broken);
                worldView.changed(x,z);
            }
        } else if (blockToPlace != null && inventory.hotbarItemAt(selected)!=null
                && (blockToPlace!=Block.LANTERN || world.get(x,y-1,z).solid()) && world.set(x,y,z,blockToPlace)) {
            if(blockToPlace==Block.FURNACE) for(int turn=0;turn<Math.floorMod(Math.round(-yaw/FastMath.HALF_PI),4);turn++) world.rotate(x,y,z,player);
            inventory.consumeAt(selected,inventory.hotbarItemAt(selected));
            worldView.changed(x,z);
        }
    }
    private void resize() {
        screenWidth=cam.getWidth(); screenHeight=cam.getHeight();
        cam.setFrustumPerspective(options.fov,(float)screenWidth/Math.max(1,screenHeight),.05f,500);
        hud.resize(screenWidth,screenHeight); menu.resize(screenWidth,screenHeight);
    }
    private void resetBreakProgress() {
        breakHeld = false;
        breakProgress = 0f;
        breakX = breakY = breakZ = Integer.MIN_VALUE;
    }
    private void updateBreakProgress(float tpf) {
        if (!active || world == null) {
            if (breakHeld) resetBreakProgress();
            return;
        }

        if (!breakHeld) {
            if (breakProgress > 0f) resetBreakProgress();
            return;
        }

        World.Hit hit = target();
        if (hit == null) { resetBreakProgress(); return; }

        int x = hit.x();
        int y = hit.y();
        int z = hit.z();
        Block block = world.get(x, y, z);
        if (block == Block.AIR || (!block.solid() && block != Block.TORCH && block != Block.LANTERN && !block.crop())) { resetBreakProgress(); return; }

        if (breakX != x || breakY != y || breakZ != z) {
            breakX = x; breakY = y; breakZ = z; breakProgress = 0f;
        }

        if(player.creative()) {
            if(world.set(x,y,z,Block.AIR))worldView.changed(x,z);
            resetBreakProgress();return;
        }
        float need = block.breakTime(inventory.hotbarItemAt(selected));
        if (need <= 0f) {
            if(block.crop()) {
                if(world.harvestCrop(x,y,z,inventory))worldView.changed(x,z);
            } else if (world.set(x, y, z, Block.AIR)) {
                inventory.addBlock(block);
                inventory.damageToolAt(selected);
                worldView.changed(x, z);
            }
            resetBreakProgress();
            return;
        }

        breakProgress += tpf;
        if (breakProgress >= need) {
            if(block.crop()) {
                if(world.harvestCrop(x,y,z,inventory))worldView.changed(x,z);
            } else if (world.set(x,y,z,Block.AIR)) {
                inventory.addBlock(block);
                inventory.damageToolAt(selected);
                worldView.changed(x,z);
            }
            resetBreakProgress();
        }
    }
    @Override public void simpleUpdate(float tpf) {
        if (cam.getWidth()!=screenWidth || cam.getHeight()!=screenHeight) resize();
        finishLoading();
        panorama.update(cam,tpf,screen==Screen.LOADING || screen==Screen.MAIN);menu.updateLoading(tpf);
        if(worldView!=null)worldView.node.setCullHint((screen==Screen.LOADING || screen==Screen.MAIN)?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        if(world!=null && active) world.advanceTime(tpf);
        updateLighting(tpf);
        updateBreakProgress(tpf);
        if(world!=null && screen==Screen.FURNACE) {
            world.updateFurnaces(tpf);
            menu.refreshFurnace(inventory,world.furnaceAt(furnaceX,furnaceY,furnaceZ),
                    world.furnaceProgress(furnaceX,furnaceY,furnaceZ));
        }
        if (active && world != null) {
            player.tickHunger(tpf);
            for(int[] water:world.updateWater(tpf))worldView.changed(water[0],water[2]);
            for(int[] crop:world.updateCrops(tpf))worldView.changed(crop[0],crop[2]);
            world.mods.tick(tpf,player);
            world.updateSheep(tpf);
            world.updateZombies(tpf,player);
            world.updateFish(tpf);
            world.ensureSheepNear(player.position);
            world.ensureFishNear(player.position);
            for (ItemType item : ItemType.values()) {
                int count=world.collectNear(player.position,1.25f,item);
                for(int i=0;i<count;i++) inventory.add(item);
            }
        }
        if(player.health()<lastPlayerHealth)hud.damageFlash();
        lastPlayerHealth=player.health();
        if(player.dead() && world!=null) {
            if(!world.keepInventory())world.createDeathChest(inventory,player.position);
            player.respawn(world);
            player.restoreHunger(Player.MAX_HUNGER);
            message=world.keepInventory()?"Вы погибли. Инвентарь сохранён":"Вы погибли. Предметы выпали";
            if(!active) show(Screen.GAME);
        }
        if(world!=null && !world.mods.notice.isEmpty()){hud.notify(world.mods.notice);world.mods.notice="";}
        hud.updateNotice(tpf);
        hud.updateDamageFlash(tpf);
        hud.setVitals(player.health(),player.hunger(),player.creative());
        hud.refreshHotbar(inventory);
        if(world!=null) hud.modOverlays(world.mods.overlays);
        if (worldView!=null) {
            worldView.update(player.position.x,player.position.z,options.distance);
            worldView.setHeldTorch(inventory.hotbarItemAt(selected)==ItemType.TORCH
                    ? player.position.add(0,Player.EYE-.15f,0) : null);
        }
        if (pythonMods!=null && world!=null && active) pythonMods.tick(tpf,selected,yaw);
        if (active && worldView.ready(player.position.x,player.position.z)) {
            Vector3f forward=new Vector3f(-FastMath.sin(yaw),0,-FastMath.cos(yaw)),right=forward.cross(Vector3f.UNIT_Y),move=new Vector3f();
            if (movement[0]) move.addLocal(forward); if (movement[1]) move.subtractLocal(forward);
            if (movement[2]) move.subtractLocal(right); if (movement[3]) move.addLocal(right);
            move.normalizeLocal();
            if(player.creative())move.y=(flyUp?1:0)-(flyDown?1:0);
            player.update(world,Math.min(tpf,.05f),move,jump || (player.inWater(world) && flyUp)); jump=false; updateCamera();
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
        breakOverlay.setCullHint(Spatial.CullHint.Always);
        if (hit!=null && breakHeld && breakX==hit.x() && breakY==hit.y() && breakZ==hit.z() && breakProgress>0) {
            Block breaking=world.get(hit.x(),hit.y(),hit.z());
            float progress=FastMath.clamp(breakProgress/Math.max(.01f,breaking.breakTime(inventory.hotbarItemAt(selected))),0,1);
            float pulse=1.015f+.035f*FastMath.sin(breakProgress*28f);
            breakOverlay.setLocalScale(pulse,pulse,pulse);
            breakOverlay.setLocalTranslation(hit.x()+.5f,hit.y()+.5f,hit.z()+.5f);
            breakMaterial.setColor("Color",new ColorRGBA(.95f,.12f+.55f*progress,.04f,.85f));
            breakOverlay.setCullHint(Spatial.CullHint.Never);
        }
        if (!active) menu.hover(inputManager.getCursorPosition());
        if (smoke) smokeFrame();
    }
    private void updateLighting(float dt) {
        float time=world==null || screen==Screen.LOADING || screen==Screen.MAIN?.5f:world.dayTime();
        float sunHeight=FastMath.sin((time-.25f)*FastMath.TWO_PI);
        float day=FastMath.clamp((sunHeight+.08f)/.78f,0,1);
        float ambient=.10f+.62f*day;
        float direct=.035f+.88f*day;
        ambientLight.setColor(new ColorRGBA(.55f+.45f*day,.62f+.38f*day,.78f+.22f*day,1).mult(ambient));
        sunLight.setColor(new ColorRGBA(1f,.69f+.31f*day,.48f+.52f*day,1).mult(direct));
        atmosphere.update(cam,time,dt);
        sunLight.setDirection(atmosphere.sun.negate());
        viewPort.setBackgroundColor(atmosphere.horizon);
        if(worldView!=null) {
            worldView.setDaylight(day);
            worldView.setAtmosphere(atmosphere.sun,atmosphere.tint,atmosphere.horizon);
        }
    }
    private void outlineBox(Geometry geometry,Door.Bounds b) {
        geometry.setLocalScale(b.maxX()-b.minX(),b.maxY()-b.minY(),b.maxZ()-b.minZ());
        geometry.setLocalTranslation((b.minX()+b.maxX())/2,(b.minY()+b.maxY())/2,(b.minZ()+b.maxZ())/2);
    }
    private void smokeClick(String id) { menuAction(menu.click(menu.buttonCenter(id))); }
    private void check(boolean condition,String error) { if (!condition) throw new IllegalStateException("Smoke: "+error); }
    private void furnitureSmokeFrame() {
        smokeTicks++;
        if(smokeTicks>3600) throw new IllegalStateException("Furniture smoke timed out");
        if(smokeStage==0 && smokeTicks>=8) { smokeClick("worlds"); smokeClick("world1"); smokeStage=1; }
        else if(smokeStage==1 && screen==Screen.GAME && worldView.loadedCount()>=25) {
            int x=(int)player.position.x,z=(int)player.position.z;
            for(int bx=x-5;bx<=x+5;bx++) for(int bz=z-3;bz<=z+7;bz++) {
                for(int y=89;y<95;y++) world.set(bx,y,bz,y==89?Block.PLANKS:Block.AIR);
                worldView.changed(bx,bz);
            }
            player.position.set(x+.5f,90,z+5.5f); yaw=0; pitch=-.26f; updateCamera();
            world.setDayTime(.5f);
            world.set(x,90,z+2,Block.CHEST);
            world.set(x-2,90,z,Block.FURNACE);
            world.set(x,90,z-1,Block.STONECUTTER);
            check(world.placeDoor(x-3,90,z+1,0,player),"lit door placement");
            world.set(x+3,90,z+1,Block.GOLD_ORE);
            stonecutterX=x; stonecutterY=90; stonecutterZ=z-1;
            world.selectStonecutterRecipe(x,90,z-1,ItemType.GOLD_PICKAXE_HEAD);
            inventory.clear();
            ItemType[] equipment={ItemType.STONE_AXE,ItemType.STONE_PICKAXE,ItemType.STONE_SHOVEL,
                    ItemType.IRON_AXE,ItemType.IRON_PICKAXE,ItemType.IRON_SHOVEL,ItemType.GOLD_AXE,
                    ItemType.GOLD_PICKAXE,ItemType.GOLD_SHOVEL,ItemType.ROTATOR};
            for(int i=0;i<equipment.length;i++) inventory.acceptSlotAt(i,new Inventory.Slot(equipment[i],1));
            world.set(x-1,90,z+1,Block.LANTERN);
            check(world.placeBed(x+1,90,z,0,player),"bed placement");
            worldView.changed(x,z);
            check(Inventory.toBlock(ItemType.TORCH)==null,"torch cannot be placed");
            check(new VoxelLighting(world,x/16,z/16).block(x-1,90,z+1)==14,"lantern emits light");
            smokeStage=2; smokeTicks=0;
        } else if(smokeStage==2 && smokeTicks>=120) {
            screenshots.takeScreenshot(); smokeStage=3; smokeTicks=0;
        } else if(smokeStage==3 && smokeTicks>=4) {
            world.setDayTime(.8f); smokeStage=4; smokeTicks=0;
        } else if(smokeStage==4 && smokeTicks>=60) {
            screenshots.takeScreenshot(); smokeStage=5; smokeTicks=0;
        } else if(smokeStage==5 && smokeTicks>=4) {
            world.setDayTime(.25f); smokeStage=6; smokeTicks=0;
        } else if(smokeStage==6 && smokeTicks>=6) {
            screenshots.takeScreenshot(); smokeStage=7; smokeTicks=0;
        } else if(smokeStage==7 && smokeTicks>=4) {
            world.setDayTime(.75f); smokeStage=8; smokeTicks=0;
        } else if(smokeStage==8 && smokeTicks>=6) {
            screenshots.takeScreenshot(); smokeStage=9; smokeTicks=0;
        } else if(smokeStage==9 && smokeTicks>=4) {
            System.out.println("Furniture smoke passed: lit door, bed, stonecutter, gold tools and recipes, day and night rendering"); quit();
        }
    }
    private void creativeSmokeFrame() {
        smokeTicks++;
        if(smokeTicks>3600)throw new IllegalStateException("Creative smoke timed out");
        if(smokeStage==0 && smokeTicks==6)screenshots.takeScreenshot();
        if(smokeStage==0 && smokeTicks>=8) { smokeClick("worlds");smokeClick("world1");smokeStage=1; }
        else if(smokeStage==1 && screen==Screen.GAME && worldView.loadedCount()>=25) {
            // Use the real command-input path.
            action("command_toggle",true,0);commandBuffer.append("gamemode 1");action("command_enter",true,0);
            check(player.creative(),"gamemode command");
            player.position.set(40.5f,90,40.5f);yaw=0;pitch=-.45f;updateCamera();
            world.set(40,90,37,Block.STONE);worldView.changed(40,37);
            show(Screen.INVENTORY);smokeStage=2;smokeTicks=0;
        } else if(smokeStage==2 && smokeTicks>=10) {
            smokeClick("creative:"+ItemType.PLANK.name());
            check(inventory.hotbarItemAt(selected)==ItemType.PLANK,"catalog item selection");
            screenshots.takeScreenshot();smokeStage=3;smokeTicks=0;
        } else if(smokeStage==3 && smokeTicks>=6) {
            smokeClick("creative_next");screenshots.takeScreenshot();smokeStage=4;smokeTicks=0;
        } else if(smokeStage==4 && smokeTicks>=6) {
            show(Screen.GAME);int count=inventory.countAt(selected);edit(true);
            check(inventory.countAt(selected)==count,"unlimited block stack");
            check(world.get(40,90,38)==Block.PLANKS,"creative block placement");
            breakHeld=true;updateBreakProgress(.001f);breakHeld=false;
            check(world.get(40,90,38)==Block.AIR,"instant creative break");
            check(inventory.countAt(selected)==count,"no block reward in creative");
            action("jump",true,0);smokeHomeX=player.position.y;smokeStage=5;smokeTicks=0;
        } else if(smokeStage==5 && smokeTicks>=20) {
            action("jump",false,0);check(player.position.y>smokeHomeX,"hold space to fly");
            screenshots.takeScreenshot();smokeStage=6;smokeTicks=0;
        } else if(smokeStage==6 && smokeTicks>=6) {
            action("command_toggle",true,0);commandBuffer.append("gamemode 0");action("command_enter",true,0);
            check(!player.creative(),"return to survival");
            show(Screen.INVENTORY);screenshots.takeScreenshot();smokeStage=7;smokeTicks=0;
        } else if(smokeStage==7 && smokeTicks>=6) {
            System.out.println("BARN0CRAFT_CREATIVE_SMOKE_OK");quit();
        }
    }
    private void panoramaSmokeFrame() {
        smokeTicks++;
        if(smokeTicks>5000)throw new IllegalStateException("Panorama smoke timed out");
        if(smokeStage==0) { check(screen==Screen.MAIN,"startup main menu");smokeStage=1; }
        else if(smokeStage==1 && panorama.ready()) {
            check(panorama.world.sheep().size()==1 && panorama.world.zombies().size()==1 && panorama.world.fish().size()==4,"panorama actors");
            smokeStage=2;smokeTicks=0;
        } else if(smokeStage==2 && smokeTicks>=20) {
            screenshots.takeScreenshot();smokeStage=3;smokeTicks=0;
        } else if(smokeStage==3 && smokeTicks>=6) {
            loadingSlot=1;show(Screen.LOADING);
            panorama.world.set(35,21,49,Block.WATER);smokeStage=4;smokeTicks=0;
        } else if(smokeStage==4) {
            for(int[] cell:panorama.world.updateWater(.12f))panorama.view.changed(cell[0],cell[2]);
            if(smokeTicks>=240) { screenshots.takeScreenshot();smokeStage=5;smokeTicks=0; }
        } else if(smokeStage==5 && smokeTicks>=6) {
            check(panorama.world.get(35,14,49)==Block.WATER,"visible falling water");
            System.out.println("BARN0CRAFT_PANORAMA_WATER_SMOKE_OK");quit();
        }
    }
    private void smokeFrame() {
        if(panoramaSmoke) { panoramaSmokeFrame(); return; }
        if(creativeSmoke) { creativeSmokeFrame(); return; }
        if(furnitureSmoke) { furnitureSmokeFrame(); return; }
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
