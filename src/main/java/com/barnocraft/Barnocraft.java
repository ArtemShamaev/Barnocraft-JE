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
    private enum Screen { MAIN, WORLDS, MULTIPLAYER, OPEN_NETWORK, MODS, CREATE_WORLD, DELETE_WORLD, SETTINGS, PAUSE, INVENTORY, STONECUTTER, FURNACE, CHEST, LOADING, GAME }
    private World world;
    private final Map<Dimension,World> dimensionWorlds=new EnumMap<>(Dimension.class);
    private final Map<Dimension,Vector3f> dimensionBeds=new EnumMap<>(Dimension.class);
    private float portalCooldown,lavaClock;

    private Multiplayer multiplayer;
    private Future<NetworkSession> connecting;
    private int characterSkin;
    private String serverAddresses="";
    private final StringBuilder networkAddress=new StringBuilder("localhost"),networkPort=new StringBuilder("25565");
    private boolean addressFocused=true;
    private final Map<Integer,PlayerModel> remoteModels=new HashMap<>();

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
    private boolean furnitureSmoke,creativeSmoke,panoramaSmoke,hellSmoke;
    private int networkSmoke;
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
        Barnocraft game=new Barnocraft(Arrays.asList(args).contains("--smoke") || Arrays.asList(args).contains("--furniture-smoke") || Arrays.asList(args).contains("--creative-smoke") || Arrays.asList(args).contains("--panorama-smoke") || Arrays.asList(args).contains("--network-smoke-host") || Arrays.asList(args).contains("--network-smoke-client") || Arrays.asList(args).contains("--hell-smoke"));
        game.hellSmoke=Arrays.asList(args).contains("--hell-smoke");
        game.networkSmoke=Arrays.asList(args).contains("--network-smoke-host")?1:Arrays.asList(args).contains("--network-smoke-client")?2:0;
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
        bind(); bindNetworkText(); show(Screen.MAIN);
        if (smoke) {
            setPauseOnLostFocus(false);
            screenshots=new ScreenshotAppState("target/",hellSmoke?"hell-smoke-":networkSmoke>0?"network-"+networkSmoke+"-":panoramaSmoke?"panorama-smoke-":creativeSmoke?"creative-smoke-":"menu-smoke-"); stateManager.attach(screenshots);
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
        if(screen==Screen.MULTIPLAYER || screen==Screen.OPEN_NETWORK) {
            if(pressed && name.equals("break"))menuAction(menu.click(inputManager.getCursorPosition()));
            if(pressed && name.equals("pause"))back();
            return;
        }
        if(pressed && isGuest() && multiplayer.busy() && !name.startsWith("move") && !Set.of("pause","quit","jump","descend").contains(name))return;

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
                String result=isGuest() && !(entered.startsWith("give ") || entered.startsWith("gamemode "))
                        ? "Команды мира доступны хозяину сервера" : Cheats.execute(entered,inventory,world,player);
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
        if(isGuest() && name.equals("place") && !(inventory.hotbarItemAt(selected)!=null && inventory.hotbarItemAt(selected).foodValue()>0)) {
            requestNetwork(NetworkGameplay.Command.of("use"));return;
        }
        if(isGuest() && name.equals("break")) { requestNetwork(NetworkGameplay.Command.of("attack"));return; }
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
        if(name.equals("break") && pressed && world.attackHellMob(cam.getLocation(),cam.getDirection(),Player.REACH,inventory)) {
            inventory.damageToolAt(selected);resetBreakProgress();return;
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
                buttons.add(new Menu.Button("worlds","Миры")); buttons.add(new Menu.Button("multiplayer","Мультиплеер")); buttons.add(new Menu.Button("mods","Моды"));
                buttons.add(new Menu.Button("settings","Настройки"));
                buttons.add(new Menu.Button("quit","Выход"));
            }
            case MULTIPLAYER, OPEN_NETWORK -> {
                menu.showNetwork(next==Screen.OPEN_NETWORK,networkAddress.toString(),networkPort.toString(),addressFocused,characterSkin,connecting!=null || (isGuest() && world==null),message);return;
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
                title=world!=null && world.dimension()==Dimension.HELL_FOREST?"Адский лес":isGuest()?"Сетевая игра":"Мир "+slot;
                subtitle=multiplayer==null?"Пауза • изменения сохраняются":isGuest()?"Мир хранится у хозяина":serverAddresses+":"+multiplayer.port();
                buttons.add(new Menu.Button("resume","Продолжить")); buttons.add(new Menu.Button("settings","Настройки"));
                if(!isGuest()) {
                    buttons.add(new Menu.Button(multiplayer==null?"open_network":"close_network",multiplayer==null?"Открыть для сети":"Закрыть сеть"));
                    buttons.add(new Menu.Button("save","Сохранить"));
                }
                buttons.add(new Menu.Button("main","В главное меню"));
                buttons.add(new Menu.Button("quit",isGuest()?"Отключиться и выйти":"Сохранить и выйти"));
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
        if(id.equals("network_address")){addressFocused=true;show(screen);return;}
        if(id.equals("network_port")){addressFocused=false;show(screen);return;}
        if(id.startsWith("network_skin_")){if(connecting==null && !isGuest())characterSkin=id.endsWith("0")?0:1;show(screen);return;}
        if(id.equals("network_start")){startNetwork();return;}
        if(isGuest() && multiplayer.busy())return;
        if(isGuest() && networkContainerAction(id))return;
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
            case "multiplayer" -> { addressFocused=true;show(Screen.MULTIPLAYER); }
            case "open_network" -> { addressFocused=false;show(Screen.OPEN_NETWORK); }
            case "close_network" -> { closeNetwork();message="Сервер закрыт";show(Screen.PAUSE); }
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
            case MULTIPLAYER -> { cancelConnection();show(Screen.MAIN); }
            case OPEN_NETWORK -> show(Screen.PAUSE);
            case WORLDS, MODS, CREATE_WORLD -> { creatingWorldName=false; show(Screen.MAIN); }
            case DELETE_WORLD -> { deletingSlot=0; show(Screen.WORLDS); }
            case SETTINGS -> show(settingsBack);
            case CHEST -> {
                if(isGuest())requestNetwork(new NetworkGameplay.Command("container-close",chestX,chestY,chestZ,0,0,false,""));
                else world.closeChest(chestX,chestY,chestZ);
                inventoryDrag=null;show(Screen.GAME);
            }
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
                SaveStore.Saved saved=loading.get(); world=saved.world();dimensionWorlds.put(world.dimension(),world); slot=loadingSlot; worldName=saved.worldName();
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
        if (saving!=null || world==null || isGuest()) return;
        World.Snapshot snapshot=world.snapshot(); SaveStore.Pose pose=pose(); int target=slot;
        Inventory.Snapshot inventorySnapshot = inventory.snapshot();
        String savedName=worldName;
        List<World.Snapshot> dimensions=dimensionWorlds.values().stream().map(World::snapshot).toList();
        saving=io.submit(()->{ saves.saveUniverse(target,dimensions,snapshot,pose,inventorySnapshot,savedName); return null; });
    }
    private boolean saveNow() {
        if (world==null || isGuest()) return true;
        try {
            if (saving!=null) { saving.get(); saving=null; }
            saves.saveUniverse(slot,dimensionWorlds.values().stream().map(World::snapshot).toList(),world.snapshot(),pose(),inventory.snapshot(),worldName); autosave=0; return true;
        } catch(Exception e) { saving=null; failure("Не удалось сохранить мир",e); show(Screen.PAUSE); return false; }
    }
    private void failure(String prefix,Exception e) {
        Throwable cause=e.getCause()==null?e:e.getCause();
        message=prefix+": "+cause.getMessage(); System.err.println(message);
    }
    private void unload() {
        cancelConnection();closeNetwork();dimensionWorlds.clear();dimensionBeds.clear();portalCooldown=0;lavaClock=0;
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
        cancelConnection();closeNetwork();
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
    private World dimensionWorld(Dimension dimension) {
        return dimensionWorlds.computeIfAbsent(dimension,key->{
            try {
                if(slot>0 && Files.exists(saves.dimensionPath(slot,key))) {
                    World saved=saves.loadDimension(slot,key).world();
                    if(saved.dimension()!=key || saved.seed()!=world.seed())throw new IOException("Измерение не соответствует миру");
                    return saved;
                }
                return new World(world.seed(),key);
            } catch(IOException e){throw new IllegalStateException("Не удалось открыть измерение: "+e.getMessage(),e);}
        });
    }
    private void updatePortal(float dt) {
        portalCooldown=Math.max(0,portalCooldown-dt);
        if(!active || world==null || portalCooldown>0)return;
        Portal.Site portal=Portal.touching(world,player.position);if(portal==null)return;
        if(isGuest()) {
            if(!multiplayer.busy()){requestNetwork(NetworkGameplay.Command.of("portal"));portalCooldown=1;}
            return;
        }
        try {
            World destination=dimensionWorld(world.dimension().other());
            if(!saveNow()){portalCooldown=3;return;}
            switchDimension(destination,Portal.safeExit(destination,portal));saveNow();
        } catch(RuntimeException e){portalCooldown=3;hud.notify(e.getMessage());}
    }
    private void switchDimension(World destination,Vector3f arrival) {
        dimensionBeds.put(world.dimension(),new Vector3f(player.respawnX(),player.respawnY(),player.respawnZ()));
        if(pythonMods!=null){pythonMods.close();pythonMods=null;}
        if(worldView!=null)worldView.close();
        world=destination;player.position.set(arrival);player.velocityY=0;player.grounded=false;
        Vector3f bed=dimensionBeds.getOrDefault(world.dimension(),new Vector3f(-1,-1,-1));player.restoreRespawnPoint(bed.x,bed.y,bed.z);
        portalCooldown=3;resetBreakProgress();inventoryDrag=null;commandOpen=false;hud.commandLine("");
        worldView=new WorldView(assetManager,world,TexturePack.from(options.texturePack));rootNode.attachChild(worldView.node);
        if(!isGuest()){pythonMods=new PythonModApi(PythonModApi.defaultDirectory(),world,player,inventory);pythonMods.start();}
        loadingSlot=0;show(Screen.LOADING);updateCamera();hud.notify(world.dimension().title);
    }
    private boolean isGuest(){return multiplayer!=null && !multiplayer.hosting();}
    private NetworkPlayer networkPose() {
        boolean moving=active && (movement[0] || movement[1] || movement[2] || movement[3]);
        return new NetworkPlayer(multiplayer==null?0:multiplayer.localId(),characterSkin,player.position.x,player.position.y,player.position.z,yaw,pitch,moving,!player.grounded,active && breakHeld,world==null?Dimension.SURFACE:world.dimension());
    }
    private void requestNetwork(NetworkGameplay.Command command) {
        if(isGuest())multiplayer.request(command,networkPose(),inventory,selected,player.creative());
    }
    private void bindNetworkText() {
        inputManager.addRawInputListener(new com.jme3.input.RawInputListener() {
            public void beginInput(){} public void endInput(){}
            public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent event){}
            public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent event){}
            public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent event){}
            public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent event){}
            public void onTouchEvent(com.jme3.input.event.TouchEvent event){}
            public void onKeyEvent(com.jme3.input.event.KeyInputEvent event) {
                if((screen!=Screen.MULTIPLAYER && screen!=Screen.OPEN_NETWORK) || !event.isPressed())return;
                int key=event.getKeyCode();
                if(key==KeyInput.KEY_ESCAPE)return;
                event.setConsumed();
                if(connecting!=null || isGuest())return;
                if(key==KeyInput.KEY_RETURN || key==KeyInput.KEY_NUMPADENTER){startNetwork();return;}
                if(key==KeyInput.KEY_TAB){addressFocused=screen==Screen.MULTIPLAYER && !addressFocused;show(screen);return;}
                StringBuilder buffer=addressFocused?networkAddress:networkPort;
                if(key==KeyInput.KEY_BACK){if(!buffer.isEmpty())buffer.deleteCharAt(buffer.length()-1);}
                else if(key==KeyInput.KEY_DELETE)buffer.setLength(0);
                else {
                    char c=event.getKeyChar();
                    if(buffer.length()<(addressFocused?64:5) && (addressFocused?(c>32 && c<127):Character.isDigit(c)))buffer.append(c);
                }
                message="";show(screen);
            }
        });
    }
    private void startNetwork() {
        if(connecting!=null || isGuest())return;
        try {
            int port=NetworkSession.parsePort(networkPort.toString());
            if(screen==Screen.OPEN_NETWORK) {
                if(world==null || multiplayer!=null)return;
                multiplayer=new Multiplayer(NetworkSession.host(port),characterSkin,networkListener());
                multiplayer.worldProvider(this::dimensionWorld);
                serverAddresses=NetworkSession.lanAddresses();setPauseOnLostFocus(false);message="Адрес: "+serverAddresses+" · порт "+port;show(Screen.PAUSE);
            } else if(screen==Screen.MULTIPLAYER) {
                String address=networkAddress.toString().strip();
                if(address.isEmpty())throw new IllegalArgumentException("Введите адрес сервера");
                connecting=CompletableFuture.supplyAsync(()->{
                    try { return NetworkSession.connect(address,port); }
                    catch(IOException e){throw new CompletionException(e);}
                },io);
                show(Screen.MULTIPLAYER);
            }
        } catch(IOException | IllegalArgumentException e) {
            message=e instanceof java.net.BindException?"Порт занят. Выберите другой порт":e.getMessage();show(screen);
        }
    }
    private void finishConnection() {
        if(connecting==null || !connecting.isDone())return;
        try {
            NetworkSession session=connecting.get();connecting=null;
            multiplayer=new Multiplayer(session,characterSkin,networkListener());
            setPauseOnLostFocus(false);message="Загрузка мира сервера...";show(Screen.MULTIPLAYER);
        } catch(Exception e) {
            connecting=null;Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
            // Keep the contextual LAN diagnostic from NetworkSession instead of the platform's terse cause.
            Throwable diagnostic=e;while(diagnostic.getCause()!=null && !(diagnostic instanceof IOException))diagnostic=diagnostic.getCause();
            message=diagnostic.getMessage()==null?cause.toString():diagnostic.getMessage();System.err.println(message);show(Screen.MULTIPLAYER);
        }
    }
    private void cancelConnection() {
        if(connecting instanceof CompletableFuture<?> pending)pending.whenComplete((session,error)->{if(session instanceof NetworkSession network)network.close();});
        connecting=null;
        if(isGuest() && world==null)closeNetwork();
    }
    private void closeNetwork() {
        if(multiplayer!=null){multiplayer.close();multiplayer=null;}
        for(PlayerModel model:remoteModels.values())model.node.removeFromParent();remoteModels.clear();
        setPauseOnLostFocus(smoke?false:true);
    }
    private Multiplayer.Listener networkListener() {
        return new Multiplayer.Listener() {
            public void joined(SaveStore.Decoded state) {
                world=World.restore(state.world());dimensionWorlds.put(world.dimension(),world);worldName=state.worldName();slot=0;loadingSlot=0;
                inventory.restore(Inventory.emptySnapshot());
                SaveStore.Pose spawn=state.pose();player.position.set(spawn.x(),spawn.y(),spawn.z());
                player.setCreative(false);inventory.setCreative(false);player.restoreHealth(Player.MAX_HEALTH);player.restoreHunger(Player.MAX_HUNGER);
                player.restoreRespawnPoint(-1,-1,-1);lastPlayerHealth=player.health();
                yaw=spawn.yaw();pitch=spawn.pitch();select(1);
                if(player.collides(world))player.respawn(world);
                worldView=new WorldView(assetManager,world,TexturePack.from(options.texturePack));rootNode.attachChild(worldView.node);
                updateCamera();show(Screen.LOADING);
            }
            public void transferred(SaveStore.Decoded state,Inventory.Snapshot items) {
                inventory.restore(items);inventory.setCreative(player.creative());
                World destination=World.restore(state.world());dimensionWorlds.put(destination.dimension(),destination);
                switchDimension(destination,new Vector3f(state.pose().x(),state.pose().y(),state.pose().z()));
            }
            public void damage(int amount){player.damage(amount);}
            public void changed(Set<Integer> cells) {
                if(worldView==null)return;
                Set<Integer> chunks=new HashSet<>();
                for(int key:cells){int z=key%World.DEPTH,x=(key/World.DEPTH)%World.WIDTH;int chunk=(x/World.CHUNK)*World.CHUNKS_Z+z/World.CHUNK;if(chunks.add(chunk))worldView.changed(x,z);}
                if(screen==Screen.CHEST || screen==Screen.FURNACE || screen==Screen.STONECUTTER) {
                    boolean exists=screen==Screen.CHEST?world.chestAt(chestX,chestY,chestZ)!=null:screen==Screen.FURNACE?world.furnaceAt(furnaceX,furnaceY,furnaceZ)!=null:world.stonecutterAt(stonecutterX,stonecutterY,stonecutterZ)!=null;
                    if(exists)show(screen);else { inventoryDrag=null;show(Screen.GAME); }
                }
            }
            public void result(Inventory.Snapshot items,NetworkGameplay.Reply reply) {
                inventory.restore(items);inventory.setCreative(player.creative());
                if(!reply.notice().isBlank())hud.notify(reply.notice());
                // A delayed reply must not reopen a screen after the player pressed Escape.
                if(active)switch(reply.screen()) {
                    case "CHEST" -> { chestX=reply.x();chestY=reply.y();chestZ=reply.z();show(Screen.CHEST); }
                    case "FURNACE" -> { furnaceX=reply.x();furnaceY=reply.y();furnaceZ=reply.z();show(Screen.FURNACE); }
                    case "STONECUTTER" -> { stonecutterX=reply.x();stonecutterY=reply.y();stonecutterZ=reply.z();show(Screen.STONECUTTER); }
                    case "BED" -> player.setRespawnPoint(world.bedSpawn(reply.x(),reply.y(),reply.z()));
                    default -> { }
                }
                if(screen==Screen.INVENTORY || screen==Screen.CHEST || screen==Screen.FURNACE || screen==Screen.STONECUTTER)show(screen);
            }
            public void disconnected(String reason) {
                boolean guest=isGuest();
                if(guest){unload();message="Отключено: "+reason;show(Screen.MULTIPLAYER);}
                else {closeNetwork();hud.notify(reason);}
            }
        };
    }
    private void updateRemotePlayers(float dt) {
        if(multiplayer==null)return;
        remoteModels.entrySet().removeIf(entry->{
            if(!multiplayer.players.containsKey(entry.getKey())){entry.getValue().node.removeFromParent();return true;}return false;
        });
        for(NetworkPlayer remote:multiplayer.players.values()) {
            if(remote.id()==multiplayer.localId())continue;
            if(world==null || remote.dimension()!=world.dimension()) {
                PlayerModel hidden=remoteModels.get(remote.id());if(hidden!=null)hidden.node.setCullHint(Spatial.CullHint.Always);continue;
            }
            PlayerModel model=remoteModels.get(remote.id());
            if(model==null || model.skin!=remote.skin()) {
                if(model!=null)model.node.removeFromParent();model=new PlayerModel(assetManager,remote.skin(),true);
                remoteModels.put(remote.id(),model);rootNode.attachChild(model.node);
            }
            model.node.setCullHint(screen==Screen.LOADING || world==null?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
            model.update(remote,dt);
        }
    }
    private boolean networkContainerAction(String id) {
        if(screen!=Screen.CHEST && screen!=Screen.FURNACE && screen!=Screen.STONECUTTER)return false;
        int x=screen==Screen.CHEST?chestX:screen==Screen.FURNACE?furnaceX:stonecutterX;
        int y=screen==Screen.CHEST?chestY:screen==Screen.FURNACE?furnaceY:stonecutterY;
        int z=screen==Screen.CHEST?chestZ:screen==Screen.FURNACE?furnaceZ:stonecutterZ;
        String prefix=screen==Screen.CHEST?"chest:":screen==Screen.FURNACE?"furnace:":"machine:";
        if(id.startsWith("recipe:") || id.equals("machine_craft")) {
            requestNetwork(new NetworkGameplay.Command(id.startsWith("recipe:")?"container-recipe":"container-craft",x,y,z,0,0,false,id.startsWith("recipe:")?id.substring(7):""));return true;
        }
        if(id.equals("furnace_smelt"))return true;
        String kind=null;int containerSlot=0,inventorySlot=0;
        if(id.startsWith(prefix)) {
            containerSlot=Integer.parseInt(id.substring(prefix.length()));
            if(inventoryDrag!=null && inventoryDrag.startsWith("slot:")) {
                inventorySlot=Integer.parseInt(inventoryDrag.substring(5));kind=screen==Screen.CHEST?"container-chest-put":"container-put";
            } else {inventoryDrag=id;return true;}
        } else if(id.startsWith("slot:") && inventoryDrag!=null && inventoryDrag.startsWith(prefix)) {
            containerSlot=Integer.parseInt(inventoryDrag.substring(prefix.length()));inventorySlot=Integer.parseInt(id.substring(5));
            kind=screen==Screen.CHEST?"container-chest-take":"container-take";
        }
        if(kind!=null){requestNetwork(new NetworkGameplay.Command(kind,x,y,z,containerSlot,inventorySlot,inventoryDragOne,""));inventoryDrag=null;inventoryDragOne=false;return true;}
        return false;
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
        if (block.immutable()) {resetBreakProgress();return;}
        if (block == Block.AIR || (!block.solid() && block != Block.TORCH && block != Block.LANTERN && !block.crop())) { resetBreakProgress(); return; }

        if (breakX != x || breakY != y || breakZ != z) {
            breakX = x; breakY = y; breakZ = z; breakProgress = 0f;
        }

        if(isGuest()) {
            if(multiplayer.busy())return;
            breakProgress+=tpf;
            if(player.creative() || breakProgress>=Math.max(0,block.breakTime(inventory.hotbarItemAt(selected)))) {
                requestNetwork(NetworkGameplay.Command.of("mine"));resetBreakProgress();
            }
            return;
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
        finishConnection();
        if(multiplayer!=null)multiplayer.tick(tpf,world,pose(),worldName,networkPose(),inventory,active);
        updateRemotePlayers(tpf);
        finishLoading();
        panorama.update(cam,tpf,screen==Screen.LOADING || screen==Screen.MAIN);menu.updateLoading(tpf);
        if(worldView!=null)worldView.node.setCullHint((screen==Screen.LOADING || screen==Screen.MAIN)?Spatial.CullHint.Always:Spatial.CullHint.Inherit);
        if(world!=null && !isGuest() && (active || multiplayer!=null)) world.advanceTime(tpf);
        updateLighting(tpf);
        updateBreakProgress(tpf);
        if(world!=null && !isGuest() && (screen==Screen.FURNACE || multiplayer!=null))world.updateFurnaces(tpf);
        if(world!=null && screen==Screen.FURNACE && world.furnaceAt(furnaceX,furnaceY,furnaceZ)!=null) {
            menu.refreshFurnace(inventory,world.furnaceAt(furnaceX,furnaceY,furnaceZ),
                    world.furnaceProgress(furnaceX,furnaceY,furnaceZ));
        }
        if(active && world!=null)player.tickHunger(tpf);
        if (!isGuest() && (active || multiplayer!=null) && world != null) {
            for(int[] water:world.updateWater(tpf))worldView.changed(water[0],water[2]);
            for(int[] crop:world.updateCrops(tpf))worldView.changed(crop[0],crop[2]);
            world.mods.tick(tpf,player);
            world.updateSheep(tpf);
            world.updateZombies(tpf,player);
            world.updateFish(tpf);
            if(multiplayer!=null && multiplayer.hosting())multiplayer.simulateDimensions(tpf,world,player);
            else world.updateHellMobs(tpf,List.of(player));
            world.ensureSheepNear(player.position);
            world.ensureFishNear(player.position);
            if(active)for (ItemType item : ItemType.values()) {
                int count=world.collectNear(player.position,1.25f,item);
                for(int i=0;i<count;i++) inventory.add(item);
            }
        }
        if(active && world!=null) {
            lavaClock-=tpf;
            if(world.get((int)player.position.x,(int)(player.position.y+.2f),(int)player.position.z)==Block.HELL_LAVA && lavaClock<=0){player.damage(4);lavaClock=.75f;}
        }
        if(player.health()<lastPlayerHealth)hud.damageFlash();
        lastPlayerHealth=player.health();
        if(player.dead() && world!=null) {
            if(!world.keepInventory() && !isGuest())world.createDeathChest(inventory,player.position);
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
        updatePortal(tpf);
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
        boolean hell=world!=null && world.dimension()==Dimension.HELL_FOREST && screen!=Screen.MAIN && screen!=Screen.LOADING;
        atmosphere.sky.setCullHint(hell?Spatial.CullHint.Always:Spatial.CullHint.Never);
        if(hell) {
            ColorRGBA fog=new ColorRGBA(.30f,.055f,.08f,1);
            ambientLight.setColor(new ColorRGBA(.65f,.32f,.25f,1));sunLight.setColor(new ColorRGBA(.4f,.15f,.08f,1));
            viewPort.setBackgroundColor(fog);
            if(worldView!=null){worldView.setDaylight(.4f);worldView.setAtmosphere(new Vector3f(.2f,.8f,.2f),new ColorRGBA(1,.5f,.3f,1),fog);}
            return;
        }
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
    private void networkSmokeFrame() {
        smokeTicks++;
        if(smokeTicks>7200)throw new IllegalStateException("Network smoke timeout: "+networkSmoke+" stage "+smokeStage+" "+message);
        if(networkSmoke==1) {
            switch(smokeStage) {
                case 0 -> {
                    if(smokeTicks==6)screenshots.takeScreenshot();
                    if(smokeTicks==10)smokeClick("multiplayer");
                    if(smokeTicks==16)screenshots.takeScreenshot();
                    if(smokeTicks==20){smokeClick("back");smokeClick("worlds");smokeClick("world1");smokeStage=1;}
                }
                case 1 -> {
                    if(screen!=Screen.GAME || worldView.loadedCount()<25)return;
                    for(int x=474;x<=486;x++)for(int z=316;z<=330;z++) {
                        for(int y=89;y<=94;y++)world.set(x,y,z,y==89?Block.PLANKS:Block.AIR);
                        worldView.changed(x,z);
                    }
                    world.set(480,91,319,Block.STONE);world.setDayTime(.5f);
                    player.position.set(480.5f,90,325.5f);yaw=-.46f;pitch=-.10f;updateCamera();
                    back();smokeClick("open_network");
                    networkPort.setLength(0);networkPort.append("0");startNetwork();
                    check(multiplayer==null && !message.isBlank(),"invalid port must not start a server");
                    networkPort.setLength(0);networkPort.append(System.getProperty("barnocraft.smokePort","25577"));
                    show(Screen.OPEN_NETWORK);smokeStage=2;smokeTicks=0;
                }
                case 2 -> {
                    if(smokeTicks==8)screenshots.takeScreenshot();
                    if(smokeTicks==12){smokeClick("network_start");check(multiplayer!=null,"host starts");smokeClick("resume");System.out.println("NETWORK_SMOKE_HOST_READY");smokeStage=3;smokeTicks=0;}
                }
                case 3 -> {
                    if(remoteModels.isEmpty() || world.get(480,91,320)!=Block.GLASS)return;
                    smokeStage=4;smokeTicks=0;
                }
                case 4 -> {
                    if(smokeTicks==60){check(remoteModels.values().stream().anyMatch(model->model.skin==1),"Vanya rendered on host");screenshots.takeScreenshot();}
                    if(smokeTicks>60 && remoteModels.isEmpty()) {
                        check(saveNow(),"save host world");
                        try { check(saves.load(slot).world().get(480,91,320)==Block.GLASS,"guest edit saved in host world"); }
                        catch(IOException e){throw new IllegalStateException(e);}
                        System.out.println("NETWORK_SMOKE_HOST_OK");quit();
                    }
                }
                default -> { }
            }
        } else {
            switch(smokeStage) {
                case 0 -> {
                    if(smokeTicks==8){smokeClick("multiplayer");smokeClick("network_skin_1");networkAddress.setLength(0);networkAddress.append(System.getProperty("barnocraft.smokeAddress","127.0.0.1"));networkPort.setLength(0);networkPort.append(System.getProperty("barnocraft.smokePort","25577"));show(Screen.MULTIPLAYER);}
                    if(smokeTicks==12)screenshots.takeScreenshot();
                    if(smokeTicks==16){smokeClick("network_start");smokeStage=1;}
                }
                case 1 -> {
                    if(screen!=Screen.GAME || worldView.loadedCount()<25)return;
                    player.position.set(480.5f,90,322.5f);yaw=0;pitch=0;updateCamera();
                    player.setCreative(true);inventory.setCreative(true);inventory.creativePick(selected,ItemType.GLASS);
                    if(multiplayer.busy())return;
                    requestNetwork(NetworkGameplay.Command.of("use"));smokeStage=2;smokeTicks=0;
                }
                case 2 -> {
                    if(multiplayer.busy() || world.get(480,91,320)!=Block.GLASS)return;
                    player.position.set(482.5f,90,322.5f);yaw=2.5536f;pitch=0;updateCamera();smokeStage=3;smokeTicks=0;
                }
                case 3 -> {
                    movement[2]=smokeTicks<120;
                    player.position.set(482.5f+.25f*FastMath.sin(smokeTicks*.1f),90,321.5f);
                    yaw=(float)Math.atan2(player.position.x-480.5f,player.position.z-325.5f);pitch=-.1f;updateCamera();
                    if(smokeTicks==90){check(remoteModels.values().stream().anyMatch(model->model.skin==0),"Sanya rendered on guest");screenshots.takeScreenshot();}
                    if(smokeTicks==240){check(!saves.exists(1),"guest must not save over a local slot");System.out.println("NETWORK_SMOKE_CLIENT_OK");quit();}
                }
                default -> { }
            }
        }
    }
    private void hellSmokeFrame() {
        smokeTicks++;if(smokeTicks>12000)throw new IllegalStateException("Hell smoke timeout stage "+smokeStage+" "+message);
        if(smokeStage==0 && smokeTicks==8){smokeClick("worlds");smokeClick("world1");smokeStage=1;}
        else if(smokeStage==1 && screen==Screen.GAME && worldView.loadedCount()>=25) {
            Portal.Site portal=world.portals().get(0);Vector3f exit=portal.exit(world);
            player.position.set(exit.x,exit.y,exit.z+2);player.setCreative(true);inventory.setCreative(true);
            inventory.add(ItemType.EMBER_CRYSTAL,3);yaw=0;pitch=0;updateCamera();smokeStage=2;smokeTicks=0;
        } else if(smokeStage==2 && smokeTicks>=90 && worldView.ready(player.position.x,player.position.z)) {
            screenshots.takeScreenshot();Portal.Site portal=world.portals().get(0);
            player.position.set(portal.x()+.5f,portal.floor(world.seed(),world.dimension())+1.01f,portal.z()+.5f);
            portalCooldown=0;smokeStage=3;smokeTicks=0;
        } else if(smokeStage==3 && world.dimension()==Dimension.HELL_FOREST && screen==Screen.GAME && worldView.loadedCount()>=25) {
            Portal.Site portal=world.portals().get(0);int y=portal.floor(world.seed(),world.dimension())+1;
            world.set(portal.x()+4,y,portal.z()+4,Block.HELL_LOG);worldView.changed(portal.x()+4,portal.z()+4);
            world.addHellMob(HellMob.spawn(HellMob.Kind.IMP,portal.x()-1.1f,y+1.2f,portal.z()+2.5f));
            world.addHellMob(HellMob.spawn(HellMob.Kind.BOAR,portal.x()+1.8f,y,portal.z()+2.5f));
            player.position.set(portal.x()+.5f,y+.01f,portal.z()+5.8f);yaw=0;pitch=-.04f;updateCamera();
            smokeStage=4;smokeTicks=0;
        } else if(smokeStage==4 && smokeTicks==90) {
            check(world.hellMobs().size()>=2,"hell creatures spawned");screenshots.takeScreenshot();
        } else if(smokeStage==4 && smokeTicks==110) {
            Portal.Site portal=world.portals().get(0);int y=portal.floor(world.seed(),world.dimension())+1;
            player.position.set(portal.x()+.5f,y+.01f,portal.z()+.5f);portalCooldown=0;smokeStage=5;
        } else if(smokeStage==5 && world.dimension()==Dimension.SURFACE && screen==Screen.GAME) {
            check(inventory.count(ItemType.EMBER_CRYSTAL)==3,"inventory survives both portal directions");
            check(saveNow(),"both dimensions saved");startWorld(slot);smokeStage=6;
        } else if(smokeStage==6 && screen==Screen.GAME) {
            Portal.Site portal=world.portals().get(0);
            player.position.set(portal.x()+.5f,portal.floor(world.seed(),world.dimension())+1.01f,portal.z()+.5f);portalCooldown=0;smokeStage=7;
        } else if(smokeStage==7 && world.dimension()==Dimension.HELL_FOREST && screen==Screen.GAME) {
            Portal.Site portal=world.portals().get(0);int y=portal.floor(world.seed(),world.dimension())+1;
            check(world.get(portal.x()+4,y,portal.z()+4)==Block.HELL_LOG,"hell edit survives reload");
            check(inventory.count(ItemType.EMBER_CRYSTAL)==3,"inventory survives reload");
            System.out.println("HELL_FOREST_SMOKE_OK");quit();
        }
    }
    private void smokeFrame() {
        if(hellSmoke){hellSmokeFrame();return;}
        if(networkSmoke>0){networkSmokeFrame();return;}
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
