package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.*;

/** Clickable native jME GUI; hit boxes follow the same scaling as the rendered buttons. */
final class Menu {
    record Button(String id, String label) {}
    record Slider(String id, String label, String value) {}
    record ModEntry(String id,String name,String version,int api,boolean enabled,String entry,String error) {}
    private record Hit(Button button, float x, float y, float width, float height, Geometry background) {}
    final Node node = new Node("Menu");
    private final Node panel = new Node("Menu panel");
    private final AssetManager assets;
    private final List<Hit> hits = new ArrayList<>();
    private Geometry background,creativeTooltip,loadingBar;
    private boolean loadingBackdrop;
    private float loadingClock;
    private String creativeHovered="";
    private Geometry furnaceProgressFill;
    private World.FurnaceSlots shownFurnace;
    private int width = 1280, height = 720;
    private float scale = 1;
    private static final ColorRGBA NORMAL = new ColorRGBA(.13f,.20f,.24f,1);
    private static final ColorRGBA HOVER = new ColorRGBA(.24f,.36f,.37f,1);

    Menu(AssetManager assets) { this.assets = assets; node.attachChild(panel); }
    void loadingBackdrop(boolean value) { loadingBackdrop=value; }
    void showLoading(int slot) {
        panel.detachAllChildren();hits.clear();node.setCullHint(Node.CullHint.Never);
        text("Barnocraft Java Edition",32,270,ColorRGBA.White);
        panel.attachChild(Hud.rect(assets,620,92,new ColorRGBA(.025f,.045f,.06f,.84f),-310,-304,1));
        text(slot==0?"Загрузка сетевого мира":"Загрузка мира "+slot,20,-245,ColorRGBA.White);
        text("Лес и пустыня · блоки, существа и реки",13,-272,new ColorRGBA(.76f,.86f,.91f,1));
        panel.attachChild(Hud.rect(assets,560,4,new ColorRGBA(.12f,.2f,.24f,1),-280,-288,2));
        loadingBar=Hud.rect(assets,120,4,new ColorRGBA(.91f,.73f,.35f,1),-280,-288,3);panel.attachChild(loadingBar);
        resize(width,height);
    }
    void updateLoading(float dt) {
        if(loadingBackdrop && loadingBar!=null) {
            loadingClock+=dt;loadingBar.setLocalTranslation(-280+(float)(.5+.5*Math.sin(loadingClock*1.4))*440,-288,3);
        }
    }
    void show(String title, String subtitle, List<Button> buttons, String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,500,600,new ColorRGBA(.035f,.065f,.09f,.97f),-250,-300,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,228,2));
        text(title,32,180,ColorRGBA.White);
        text(subtitle,16,146,new ColorRGBA(.7f,.8f,.82f,1));
        float y = 86;
        for (Button button : buttons) {
            Geometry rect = Hud.rect(assets,400,44,NORMAL.clone(),-200,y,2);
            panel.attachChild(rect); hits.add(new Hit(button,-200f,y,400f,44f,rect));
            text(button.label(),20,y+9,ColorRGBA.White); y -= 54;
        }
        if (message != null && !message.isBlank()) {
            // Keep error text readable without overflowing the menu card.
            String remaining = message;
            for (int line=0; line<3 && !remaining.isEmpty(); line++) {
                int end=Math.min(remaining.length(),46);
                text(remaining.substring(0,end),15,-246-line*18,new ColorRGBA(1,.76f,.48f,1));
                remaining=remaining.substring(end);
            }
        }
        resize(width,height);
    }
    void showNetwork(boolean hosting,String address,String port,boolean addressFocused,int skin,boolean connecting,String message) {
        panel.detachAllChildren();hits.clear();node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,600,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-300,1));
        text(hosting?"Открыть для сети":"Мультиплеер",28,245,ColorRGBA.White);
        text(hosting?"Друзья подключаются по вашему IP и порту":"Подключение к миру друга",15,210,new ColorRGBA(.7f,.8f,.82f,1));
        if(!hosting)networkButton("network_address","Адрес: "+address+(addressFocused?"_":""),-270,143,540,38,addressFocused?HOVER:NORMAL);
        else text("TCP · до 8 игроков · мир хранится у хозяина",15,155,ColorRGBA.White);
        networkButton("network_port","Порт: "+port+(!addressFocused?"_":""),-160,93,320,38,!addressFocused?HOVER:NORMAL);
        for(int i=0;i<2;i++) {
            PlayerModel model=new PlayerModel(assets,i,false);
            model.node.setLocalScale(82);model.node.setLocalTranslation(i==0?-125:125,-113,25);
            model.node.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(0,com.jme3.math.FastMath.PI+.20f,0));
            panel.attachChild(model.node);
            networkButton("network_skin_"+i,(skin==i?"> ":"")+(i==0?"Саня":"Ваня"),i==0?-220:30,-164,190,38,skin==i?HOVER:NORMAL);
        }
        networkButton("network_start",connecting?"Подключение...":hosting?"Открыть мир":"Подключиться",-270,-232,300,44,new ColorRGBA(.32f,.43f,.27f,1));
        networkButton("back",connecting?"Отмена":"Назад",50,-232,220,44,NORMAL);
        if(message!=null && !message.isBlank())text(message.length()>64?message.substring(0,64):message,13,-274,new ColorRGBA(1,.76f,.48f,1));
        resize(width,height);
    }
    private void networkButton(String id,String label,float x,float y,float width,float height,ColorRGBA color) {
        Geometry rect=Hud.rect(assets,width,height,color.clone(),x,y,2);panel.attachChild(rect);
        hits.add(new Hit(new Button(id,label),x,y,width,height,rect));
        textAt(label,18,x+width/2,y+(height-18)/2,ColorRGBA.White);
    }
    void showWorlds(SaveStore saves,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,600,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-300,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,260,2));
        text("Миры",30,210,ColorRGBA.White);
        text("Пять сохранений",15,178,new ColorRGBA(.7f,.8f,.82f,1));
        for(int slot=1;slot<=5;slot++) {
            float y=124-(slot-1)*58;
            boolean exists=saves.exists(slot);
            String label=slot+": "+(exists?saves.worldName(slot):"Пустой слот");
            Geometry open=Hud.rect(assets,420,44,NORMAL.clone(),-250,y,2);
            panel.attachChild(open); hits.add(new Hit(new Button("world"+slot,label),-250,y,420,44,open));
            textAt(label,17,-228,y+13,ColorRGBA.White);
            if(exists) {
                Geometry delete=Hud.rect(assets,46,44,new ColorRGBA(.40f,.17f,.14f,1),180,y,2);
                panel.attachChild(delete);
                hits.add(new Hit(new Button("delete_world_"+slot,"Удалить"),180,y,46,44,delete));
                textAt("×",24,203,y+8,ColorRGBA.White);
            }
        }
        Geometry create=Hud.rect(assets,210,42,new ColorRGBA(.32f,.39f,.25f,1),-220,-210,2);
        panel.attachChild(create); hits.add(new Hit(new Button("create_world","Создать мир"),-220,-210,210,42,create));
        textAt("Создать мир",17,-115,-198,ColorRGBA.White);
        Geometry back=Hud.rect(assets,180,42,NORMAL.clone(),40,-210,2);
        panel.attachChild(back); hits.add(new Hit(new Button("back","Назад"),40,-210,180,42,back));
        textAt("Назад",17,130,-198,ColorRGBA.White);
        if(message!=null && !message.isBlank()) textAt(message,13,0,-260,new ColorRGBA(1,.76f,.48f,1));
        resize(width,height);
    }
    void showMods(List<ModEntry> mods) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,600,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-300,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,260,2));
        text("Моды",30,210,ColorRGBA.White);
        text("Загружаются при входе в мир · Python Mod API v2",14,178,new ColorRGBA(.7f,.8f,.82f,1));
        if(mods.isEmpty()) {
            text("Моды не найдены в папке mods",17,70,new ColorRGBA(.83f,.86f,.82f,1));
        } else {
            int shown=Math.min(mods.size(),8);
            for(int i=0;i<shown;i++) {
                ModEntry mod=mods.get(i);
                float y=132-i*46;
                ColorRGBA status=mod.enabled()?new ColorRGBA(.42f,.78f,.48f,1):new ColorRGBA(.72f,.72f,.68f,1);
                String state=mod.error().isEmpty()?(mod.enabled()?"ВКЛ":"ВЫКЛ"):"ОШИБКА MANIFEST";
                textAt(mod.name(),17,-270,y+7,ColorRGBA.White);
                textAt(mod.id()+"  ·  v"+mod.version()+"  ·  API "+mod.api()+"  ·  "+mod.entry(),12,-270,y-12,new ColorRGBA(.68f,.77f,.79f,1));
                textAt(state,11,250,y+1,status);
                if(!mod.error().isEmpty()) textAt(mod.error(),11,0,y-26,new ColorRGBA(1,.58f,.45f,1));
            }
            if(mods.size()>shown) text("И ещё модов: "+(mods.size()-shown),13, -208,new ColorRGBA(.7f,.8f,.82f,1));
        }
        Geometry back=Hud.rect(assets,180,42,NORMAL.clone(),-90,-238,2);
        panel.attachChild(back); hits.add(new Hit(new Button("back","Назад"),-90,-238,180,42,back));
        textAt("Назад",17,0,-226,ColorRGBA.White);
        resize(width,height);
    }
    void showDeleteWorld(String worldName,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,500,360,new ColorRGBA(.035f,.065f,.09f,.97f),-250,-180,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,128,2));
        text("Удалить мир?",28,84,ColorRGBA.White);
        textAt(worldName,18,0,34,new ColorRGBA(.78f,.83f,.82f,1));
        Geometry confirm=Hud.rect(assets,190,44,new ColorRGBA(.48f,.17f,.14f,1),-200,-62,2);
        panel.attachChild(confirm); hits.add(new Hit(new Button("confirm_delete_world","Удалить"),-200,-62,190,44,confirm));
        textAt("Удалить",17,-105,-49,ColorRGBA.White);
        Geometry cancel=Hud.rect(assets,190,44,NORMAL.clone(),10,-62,2);
        panel.attachChild(cancel); hits.add(new Hit(new Button("cancel_delete_world","Отмена"),10,-62,190,44,cancel));
        textAt("Отмена",17,105,-49,ColorRGBA.White);
        if(message!=null && !message.isBlank()) textAt(message,14,0,-125,new ColorRGBA(1,.76f,.48f,1));
        resize(width,height);
    }
    void showSliders(String title, String subtitle, List<Slider> sliders, List<Button> buttons, String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,500,600,new ColorRGBA(.035f,.065f,.09f,.97f),-250,-300,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,228,2));
        text(title,32,180,ColorRGBA.White); text(subtitle,16,146,new ColorRGBA(.7f,.8f,.82f,1));
        float y=86;
        for (Slider slider:sliders) {
            text(slider.label()+": "+slider.value(),17,y+19,ColorRGBA.White);
            panel.attachChild(Hud.rect(assets,360,6,new ColorRGBA(.20f,.30f,.32f,1),-180,y-5,2));
            panel.attachChild(Hud.rect(assets,80,10,new ColorRGBA(.83f,.60f,.22f,1),-40,y-7,3));
            Button b=new Button(slider.id(),slider.label()); Geometry hit=Hud.rect(assets,400,42,new ColorRGBA(0,0,0,0),-200,y-18,4); panel.attachChild(hit); hits.add(new Hit(b,-200f,y-18,400f,42f,hit));
            y-=64;
        }
        for (Button button:buttons) { Geometry rect=Hud.rect(assets,400,44,NORMAL.clone(),-200,y,2); panel.attachChild(rect); hits.add(new Hit(button,-200f,y,400f,44f,rect)); text(button.label(),20,y+9,ColorRGBA.White); y-=54; }
        resize(width,height);
    }
    void showCreateWorld(String name,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,500,360,new ColorRGBA(.035f,.065f,.09f,.97f),-250,-180,1));
        panel.attachChild(Hud.rect(assets,64,4,new ColorRGBA(.91f,.66f,.26f,1),-32,128,2));
        text("Новый мир",28,88,ColorRGBA.White);
        textAt("Имя",16,0,38,new ColorRGBA(.75f,.83f,.82f,1));
        panel.attachChild(Hud.rect(assets,400,48,new ColorRGBA(.13f,.20f,.24f,1),-200,-18,2));
        textAt((name.isEmpty()?"Введите имя":name)+"_",19,0,-1,ColorRGBA.White);
        Geometry create=Hud.rect(assets,190,42,new ColorRGBA(.32f,.39f,.25f,1),-200,-92,2);
        panel.attachChild(create); hits.add(new Hit(new Button("confirm_world_name","Создать"),-200,-92,190,42,create));
        textAt("Создать",17,-105,-80,ColorRGBA.White);
        Geometry cancel=Hud.rect(assets,190,42,NORMAL.clone(),10,-92,2);
        panel.attachChild(cancel); hits.add(new Hit(new Button("cancel_world_name","Отмена"),10,-92,190,42,cancel));
        textAt("Отмена",17,105,-80,ColorRGBA.White);
        if(message!=null && !message.isBlank()) textAt(message,14,0,-136,new ColorRGBA(1,.76f,.48f,1));
        resize(width,height);
    }
    void showInventory(Inventory inv) {

        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,560,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-280,1));

        final float slotSize = 44f;
        final float slotGap = 8f;
        final float invStartX = -200f;
        final float invStartY = 130f;
        for (int i = 0; i < Inventory.SLOT_COUNT; i++) {
            int row = i / 5;
            int col = i % 5;
            float x = invStartX + col * (slotSize + slotGap);
            float y = invStartY - row * (slotSize + slotGap);
            Geometry cell = Hud.rect(assets, slotSize, slotSize, new ColorRGBA(.17f,.24f,.29f,1), x, y, 2);
            panel.attachChild(cell);
            hits.add(new Hit(new Button("slot:" + i, "slot-" + i), x, y, slotSize, slotSize, cell));

            drawSlot(inv.slotAtIndex(i),x,y,slotSize,3);
        }

        final float hotbarY = -165f;
        for (int i = 0; i < Block.HOTBAR.length; i++) {
            float x = -200f + i * 42f;
            ColorRGBA slotColor = i == 0 ? new ColorRGBA(.22f,.31f,.35f,1) : new ColorRGBA(.14f,.19f,.22f,1);
            Geometry slot = Hud.rect(assets, 38f, 38f, slotColor, x, hotbarY, 2);
            panel.attachChild(slot);
            hits.add(new Hit(new Button("slot:" + i, "hotbar-" + i), x, hotbarY, 38f, 38f, slot));

            drawSlot(inv.slotAtIndex(i),x,hotbarY,38f,3);
        }

        final float craftX = 84f;
        final float craftY = 110f;
        for (int i = 0; i < Inventory.CRAFT_SIZE; i++) {
            int row = i / 2;
            int col = i % 2;
            float x = craftX + col * 78f;
            float y = craftY - row * 78f;
            Geometry slot = Hud.rect(assets, 64f, 64f, new ColorRGBA(.16f,.24f,.27f,1), x, y, 2);
            panel.attachChild(slot);
            hits.add(new Hit(new Button("craft:" + i, "craft-" + i), x, y, 64f, 64f, slot));

            drawSlot(inv.craftSlotAt(i),x,y,64f,3);
        }

        textAt("→",18,224f,88f,new ColorRGBA(.83f,.86f,.82f,1));
        final float resultX = 250f, resultY = 60f, resultSize = 56f;
        Geometry resultSlot = Hud.rect(assets,resultSize,resultSize,
                new ColorRGBA(.24f,.31f,.32f,1),resultX,resultY,2);
        panel.attachChild(resultSlot);
        hits.add(new Hit(new Button("craft_result","craft-result"),resultX,resultY,resultSize,resultSize,resultSlot));
        ItemType result = inv.craftPreview();
        if (result != null) {
            Geometry icon = Hud.rect(assets,42f,42f,ColorRGBA.White,resultX+7f,resultY+7f,3);
            icon.getMaterial().setTexture("ColorMap",WorldView.texture(assets,result.texture));
            panel.attachChild(icon);
            if(inv.craftPreviewCount()>1) {
                Geometry count=Hud.label(assets,String.valueOf(inv.craftPreviewCount()),13);
                count.setLocalTranslation(resultX+resultSize-14,resultY+4,4);panel.attachChild(count);
            }
        }
        textAt("Результат",13,resultX+resultSize/2f,resultY+resultSize+8f,new ColorRGBA(.76f,.82f,.81f,1));

        panel.attachChild(Hud.rect(assets,200f,30f,new ColorRGBA(.13f,.20f,.24f,1),-60f,-220f,2));
        text("Закрыть (E)",14,-212f,ColorRGBA.White);
        resize(width,height);
    }
    static java.util.List<ItemType> creativeItems() {
        return java.util.Arrays.stream(ItemType.values()).filter(item->!item.retired()).toList();
    }
    void showCreative(Inventory inventory,int page,int selected) {
        var items=creativeItems();int pages=Math.max(1,(items.size()+39)/40);page=Math.floorMod(page,pages);
        beginPanel("Креатив", "Выберите слот панели, затем предмет", 640, 600);
        for(int i=0;i<40 && page*40+i<items.size();i++) {
            ItemType item=items.get(page*40+i);
            panelSlot(new Button("creative:"+item.name(),item.title),new Inventory.Slot(item,item.stackLimit()),
                    -265+(i%10)*54,150-(i/10)*54,46);
        }
        button("creative_prev","<",-265,-92,55,28,NORMAL);
        button("creative_next",">",210,-92,55,28,NORMAL);
        textAt("Страница "+(page+1)+" / "+pages,15,0,-86,ColorRGBA.White);
        textAt("Слот "+(selected+1)+" · "+(inventory.hotbarItemAt(selected)==null?"Пусто":inventory.hotbarItemAt(selected).title),15,0,-135,ColorRGBA.White);
        panel.attachChild(Hud.rect(assets,46,46,new ColorRGBA(.9f,.75f,.35f,1),-238+selected*48,-208,1));
        drawHotbar(inventory,-205,48);
        textAt("Пробел — вверх · Shift — вниз · gamemode 0 — выживание",12,0,-255,ColorRGBA.White);
        resize(width,height);
    }
    void showInventoryModern(Inventory inv) {
        beginPanel("Инвентарь", "Создание · 2 × 2", 640, 600);
        float craftX=-250,craftY=150,size=48,gap=8;
        for(int i=0;i<Inventory.CRAFT_SIZE;i++) {
            float x=craftX+(i%2)*(size+gap),y=craftY-(i/2)*(size+gap);
            panelSlot(new Button("craft:"+i,"craft-"+i),inv.craftSlotAt(i),x,y,size);
        }
        textAt("→",22,-92,112,new ColorRGBA(.84f,.85f,.79f,1));
        ItemType result=inv.craftPreview();
        Inventory.Slot output=result==null?new Inventory.Slot(null,0):new Inventory.Slot(result,inv.craftPreviewCount());
        panelSlot(new Button("craft_result","Результат"),output,-55,105,56);
        textAt("Инвентарь",17,-245,30,new ColorRGBA(.86f,.79f,.61f,1));
        drawPlayerStorage(inv,-28,48);
        textAt("Быстрая панель",15,-245,-155,new ColorRGBA(.86f,.79f,.61f,1));
        drawHotbar(inv,-205,48);
        textAt("ЛКМ: взять / положить · ПКМ: взять половину / 1 предмет",12,0,-260,new ColorRGBA(.68f,.76f,.75f,1));
        resize(width,height);
    }
    void showStonecutterModern(Inventory inventory,World.MachineSlots machine,String message) {
        beginPanel("Камнерез", "Выберите заготовку", 640, 600);
        ItemType[] recipes={ItemType.AXE_HEAD,ItemType.PICKAXE_HEAD,ItemType.SHOVEL_HEAD,
                ItemType.IRON_AXE_HEAD,ItemType.IRON_PICKAXE_HEAD,ItemType.IRON_SHOVEL_HEAD,
                ItemType.GOLD_AXE_HEAD,ItemType.GOLD_PICKAXE_HEAD,ItemType.GOLD_SHOVEL_HEAD};
        String[] labels={"Топор","Кирка","Лопата","Жел. топор","Жел. кирка","Жел. лопата","Зол. топор","Зол. кирка","Зол. лопата"};
        for(int i=0;i<recipes.length;i++) {
            float x=-270+(i%3)*96,y=145-(i/3)*34;
            ColorRGBA color=machine.recipe()==recipes[i]?new ColorRGBA(.53f,.40f,.19f,1):NORMAL.clone();
            Geometry button=Hud.rect(assets,88,28,color,x,y,2);panel.attachChild(button);
            hits.add(new Hit(new Button("recipe:"+recipes[i].name(),labels[i]),x,y,88,28,button));
            textAt(labels[i],12,x+44,y+8,ColorRGBA.White);
        }
        ItemType material=World.stonecutterMaterial(machine.recipe());
        panelSlot(new Button("machine:0","Уголь"),machine.coal(),-135,15,52);
        textAt("+",17,-67,31,new ColorRGBA(.8f,.8f,.74f,1));
        panelSlot(new Button("machine:1",material.title),machine.stone(),-40,15,52);
        textAt("→",19,32,31,new ColorRGBA(.8f,.8f,.74f,1));
        panelSlot(new Button("machine:2","Выход"),machine.output(),60,15,52);
        button("machine_craft","Изготовить",160,22,112,34,new ColorRGBA(.37f,.43f,.27f,1));
        textAt("Инвентарь",16,-245,-54,new ColorRGBA(.86f,.79f,.61f,1));
        drawPlayerStorage(inventory,-78,40);
        textAt("Быстрая панель",14,-245,-167,new ColorRGBA(.86f,.79f,.61f,1));
        drawHotbar(inventory,-208,40);
        if(message!=null&&!message.isBlank())textAt(message,12,0,-253,new ColorRGBA(1,.75f,.45f,1));
        resize(width,height);
    }
    void showFurnaceModern(Inventory inventory,World.FurnaceSlots furnace,float progress,String message) {
        shownFurnace=furnace;furnaceProgressFill=null;
        beginPanel("Печь", "Перетащите топливо и продукт в слоты", 640, 600);
        panelSlot(new Button("furnace:0","Топливо"),furnace.coal(),-145,110,56);
        panelSlot(new Button("furnace:1","Для готовки"),furnace.input(),-20,110,56);
        panelSlot(new Button("furnace:2","Готовый продукт"),furnace.output(),150,110,56);
        textAt("Топливо",12,-117,89,new ColorRGBA(.77f,.80f,.75f,1));
        textAt("Сырьё",12,8,89,new ColorRGBA(.77f,.80f,.75f,1));
        textAt("Результат",12,178,89,new ColorRGBA(.77f,.80f,.75f,1));
        panel.attachChild(Hud.rect(assets,92,8,new ColorRGBA(.13f,.16f,.15f,1),8,78,2));
        furnaceProgressFill=Hud.rect(assets,92,8,new ColorRGBA(.87f,.56f,.20f,1),8,78,3);
        panel.attachChild(furnaceProgressFill);updateFurnaceProgress(progress);
        panel.attachChild(Hud.rect(assets,40,8,new ColorRGBA(.18f,.14f,.11f,1),-136,74,2));
        float fuel=furnace.coal().count()>0?1:.15f;
        panel.attachChild(Hud.rect(assets,40,fuel*8,new ColorRGBA(.94f,.38f,.13f,1),-136,74,3));
        textAt("Инвентарь",16,-245,34,new ColorRGBA(.86f,.79f,.61f,1));
        drawPlayerStorage(inventory,10,40);
        textAt("Быстрая панель",14,-245,-138,new ColorRGBA(.86f,.79f,.61f,1));
        drawHotbar(inventory,-180,40);
        if(message!=null&&!message.isBlank())textAt(message,12,0,-244,new ColorRGBA(1,.75f,.45f,1));
        resize(width,height);
    }
    void showChestModern(Inventory inventory,World.ChestData chest) {
        beginPanel(chest.deathCache()?"Сундук погибшего":"Сундук",
                chest.deathCache()?"Заберите вещи; сундук исчезнет при закрытии":"Хранилище · 27 слотов",640,600);
        textAt("Содержимое",15,-244,156,new ColorRGBA(.86f,.79f,.61f,1));
        for(int i=0;i<27;i++) {
            int row=i/9,col=i%9;float x=-220+col*48,y=100-row*48;
            panelSlot(new Button("chest:"+i,"chest-"+i),chest.slots().get(i),x,y,40);
        }
        textAt("Инвентарь",15,-244,-78,new ColorRGBA(.86f,.79f,.61f,1));
        for(int i=10;i<Inventory.SLOT_COUNT;i++) {
            int local=i-10,row=local/5,col=local%5;
            panelSlot(new Button("slot:"+i,"slot-"+i),inventory.slotAtIndex(i),-120+col*48,-125-row*46,40);
        }
        textAt("Быстрая панель",13,-244,-231,new ColorRGBA(.86f,.79f,.61f,1));
        for(int i=0;i<Inventory.HOTBAR_SIZE;i++)
            panelSlot(new Button("slot:"+i,"slot-"+i),inventory.slotAtIndex(i),-220+i*48,-250,40);
        resize(width,height);
    }
    void refreshFurnace(Inventory inventory,World.FurnaceSlots furnace,float progress) {
        if(!Objects.equals(shownFurnace,furnace))showFurnaceModern(inventory,furnace,progress,"");
        else updateFurnaceProgress(progress);
    }
    private void updateFurnaceProgress(float progress) {
        if(furnaceProgressFill!=null)furnaceProgressFill.setLocalScale(Math.max(0,Math.min(1,progress)),1,1);
    }
    private void beginPanel(String title,String subtitle,float panelWidth,float panelHeight) {
        panel.detachAllChildren();hits.clear();node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,panelWidth,panelHeight,new ColorRGBA(.075f,.085f,.078f,.98f),-panelWidth/2,-panelHeight/2,1));
        panel.attachChild(Hud.rect(assets,panelWidth-20,2,new ColorRGBA(.40f,.36f,.25f,1),-panelWidth/2+10,panelHeight/2-52,2));
        textAt(title,25,0,panelHeight/2-35,ColorRGBA.White);
        textAt(subtitle,13,0,panelHeight/2-72,new ColorRGBA(.75f,.77f,.70f,1));
        textAt("E / Esc — закрыть",12,0,-panelHeight/2+15,new ColorRGBA(.65f,.69f,.64f,1));
    }
    private void panelSlot(Button button,Inventory.Slot slot,float x,float y,float size) {
        Geometry cell=Hud.rect(assets,size,size,new ColorRGBA(.25f,.27f,.24f,1),x,y,2);
        panel.attachChild(cell);hits.add(new Hit(button,x,y,size,size,cell));
        panel.attachChild(Hud.rect(assets,size-4,size-4,new ColorRGBA(.10f,.115f,.105f,1),x+2,y+2,2.1f));
        drawSlot(slot,x,y,size,3);
    }
    private void drawPlayerStorage(Inventory inventory,float top,float step) {
        float startX=-((4*step)+40)/2;
        for(int i=10;i<Inventory.SLOT_COUNT;i++) {
            int local=i-10,row=local/5,col=local%5;
            panelSlot(new Button("slot:"+i,"slot-"+i),inventory.slotAtIndex(i),startX+col*step,top-row*step,40);
        }
    }
    private void drawHotbar(Inventory inventory,float y,float step) {
        float startX=-((Inventory.HOTBAR_SIZE-1)*step+40)/2;
        for(int i=0;i<Inventory.HOTBAR_SIZE;i++)
            panelSlot(new Button("slot:"+i,"slot-"+i),inventory.slotAtIndex(i),startX+i*step,y,40);
    }
    private void button(String id,String label,float x,float y,float w,float h,ColorRGBA color) {
        Geometry geometry=Hud.rect(assets,w,h,color,x,y,2);panel.attachChild(geometry);
        hits.add(new Hit(new Button(id,label),x,y,w,h,geometry));textAt(label,13,x+w/2,y+h/2-5,ColorRGBA.White);
    }
    void showStonecutter(Inventory inventory,World.MachineSlots machine,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,560,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-280,1));
        textAt("Камнерез",24,0f,226f,ColorRGBA.White);
        textAt("Выбери навершие",16,0f,198f,new ColorRGBA(.83f,.86f,.82f,1));
        ItemType[] recipes={ItemType.AXE_HEAD,ItemType.PICKAXE_HEAD,ItemType.SHOVEL_HEAD,
                ItemType.IRON_AXE_HEAD,ItemType.IRON_PICKAXE_HEAD,ItemType.IRON_SHOVEL_HEAD,
                ItemType.GOLD_AXE_HEAD,ItemType.GOLD_PICKAXE_HEAD,ItemType.GOLD_SHOVEL_HEAD};
        String[] recipeLabels={"Кам. топор","Кам. кирка","Кам. лопата","Жел. топор","Жел. кирка","Жел. лопата","Зол. топор","Зол. кирка","Зол. лопата"};
        for (int i=0;i<recipes.length;i++) {
            float x=18f+(i%3)*98f,y=172f-(i/3)*30f;
            ColorRGBA color=machine.recipe()==recipes[i]
                ? new ColorRGBA(.46f,.36f,.18f,1) : new ColorRGBA(.13f,.20f,.24f,1);
            Geometry button=Hud.rect(assets,88,26,color,x,y,2);
            panel.attachChild(button);
            hits.add(new Hit(new Button("recipe:"+recipes[i].name(),recipeLabels[i]),x,y,88,26,button));
            textAt(recipeLabels[i],12,x+44,y+7,ColorRGBA.White);
        }
        textAt(machine.recipe().title,12,170f,72f,new ColorRGBA(.92f,.74f,.38f,1));
        ItemType material=World.stonecutterMaterial(machine.recipe());
        String materialLabel=material!=ItemType.STONE?"Слитки ×5":"Камень";
        String[] labels={"Уголь",materialLabel,"Выход"};
        Inventory.Slot[] machineSlots={machine.coal(),machine.stone(),machine.output()};
        for (int i=0;i<3;i++) {
            float x=54f+i*82f,y=-8f;
            Geometry cell=Hud.rect(assets,64,64,new ColorRGBA(.16f,.24f,.27f,1),x,y,2);
            panel.attachChild(cell);
            hits.add(new Hit(new Button("machine:"+i,labels[i]),x,y,64,64,cell));
            textAt(labels[i],13,x+32,y-20,new ColorRGBA(.76f,.82f,.81f,1));
            drawSlot(machineSlots[i],x,y,64,3);
        }
        Geometry craftButton=Hud.rect(assets,116,30,new ColorRGBA(.32f,.39f,.25f,1),112,-55,2);
        panel.attachChild(craftButton);
        hits.add(new Hit(new Button("machine_craft","Изготовить"),112,-55,116,30,craftButton));
        textAt("Изготовить",14,170,-47,ColorRGBA.White);
        textAt("Инвентарь",16,-244f,172f,new ColorRGBA(.76f,.82f,.81f,1));
        for (int i=0;i<Inventory.SLOT_COUNT;i++) {
            int row=i/5,col=i%5;
            float x=-280f+col*48f,y=112f-row*48f;
            Geometry cell=Hud.rect(assets,40,40,new ColorRGBA(.17f,.24f,.29f,1),x,y,2);
            panel.attachChild(cell);
            hits.add(new Hit(new Button("slot:"+i,"slot-"+i),x,y,40,40,cell));
            drawSlot(new Inventory.Slot(inventory.itemAt(i),inventory.countAt(i)),x,y,40,3);
        }
        panel.attachChild(Hud.rect(assets,200f,30f,new ColorRGBA(.13f,.20f,.24f,1),-60f,-220f,2));
        text("Закрыть (E)",14,-212f,ColorRGBA.White);
        if (message!=null && !message.isBlank()) textAt(message,13,0f,-180f,new ColorRGBA(1f,.76f,.48f,1));
        resize(width,height);
    }
    void showFurnace(Inventory inventory,World.FurnaceSlots furnace,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,560,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-280,1));
        textAt("Печь",24,0f,226f,ColorRGBA.White);
        String[] labels={"Уголь","Руда / песок","Слитки / стекло"};
        Inventory.Slot[] machineSlots={furnace.coal(),furnace.input(),furnace.output()};
        for (int i=0;i<3;i++) {
            float x=54f+i*100f,y=90f;
            Geometry cell=Hud.rect(assets,72,72,new ColorRGBA(.16f,.24f,.27f,1),x,y,2);
            panel.attachChild(cell);
            hits.add(new Hit(new Button("furnace:"+i,labels[i]),x,y,72,72,cell));
            textAt(labels[i],13,x+36,y-20,new ColorRGBA(.76f,.82f,.81f,1));
            drawSlot(machineSlots[i],x,y,72,3);
        }
        textAt("Автоматическая плавка",14,174,46,new ColorRGBA(.86f,.72f,.45f,1));
        textAt("Инвентарь",16,-244f,172f,new ColorRGBA(.76f,.82f,.81f,1));
        for (int i=0;i<Inventory.SLOT_COUNT;i++) {
            int row=i/5,col=i%5;
            float x=-280f+col*48f,y=112f-row*48f;
            Geometry cell=Hud.rect(assets,40,40,new ColorRGBA(.17f,.24f,.29f,1),x,y,2);
            panel.attachChild(cell);
            hits.add(new Hit(new Button("slot:"+i,"slot-"+i),x,y,40,40,cell));
            drawSlot(new Inventory.Slot(inventory.itemAt(i),inventory.countAt(i)),x,y,40,3);
        }
        textAt("E: закрыть",14,0f,-212f,ColorRGBA.White);
        if (message!=null && !message.isBlank()) textAt(message,13,0f,-180f,new ColorRGBA(1f,.76f,.48f,1));
        resize(width,height);
    }

    private void drawSlot(Inventory.Slot slot,float x,float y,float size,float z) {
        if (slot==null || slot.item()==null) return;
        float iconSize=size-12;
        Geometry icon=Hud.rect(assets,iconSize,iconSize,ColorRGBA.White,x+(size-iconSize)/2,y+(size-iconSize)/2,z);
        icon.getMaterial().setTexture("ColorMap",WorldView.texture(assets,slot.item().texture));
        panel.attachChild(icon);
        if (slot.count()>1) {
            Geometry count=Hud.label(assets,String.valueOf(slot.count()),12);
            count.setLocalTranslation(x+size-19,y+5,z+1); panel.attachChild(count);
        }
        if (slot.item().tool()) {
            float fraction=(float)slot.durability()/slot.item().maxDurability();
            float width=size-12;
            panel.attachChild(Hud.rect(assets,width,3,new ColorRGBA(.08f,.08f,.08f,1),x+6,y+3,z+1));
            ColorRGBA color=fraction>.5f?new ColorRGBA(.34f,.78f,.34f,1)
                    :fraction>.2f?new ColorRGBA(.91f,.68f,.22f,1):new ColorRGBA(.88f,.28f,.22f,1);
            panel.attachChild(Hud.rect(assets,width*fraction,3,color,x+6,y+3,z+2));
        }
    }
    private void text(String value,int size,float y,ColorRGBA color) {
        Geometry label=Hud.label(assets,value,size); int w=label.getUserData("width");
        label.getMaterial().setColor("Color",color); label.setLocalTranslation(-w/2f,y,3); panel.attachChild(label);
    }
    private void textAt(String value,int size,float x,float y,ColorRGBA color) {
        Geometry label=Hud.label(assets,value,size); int w=label.getUserData("width");
        label.getMaterial().setColor("Color",color); label.setLocalTranslation(x-w/2f,y,3); panel.attachChild(label);
    }
    void hide() { node.setCullHint(Node.CullHint.Always); }
    void resize(int w,int h) {
        width=w; height=h; scale=Math.min(1,Math.min(w/540f,h/640f));
        panel.setLocalTranslation(w/2f,h/2f,10); panel.setLocalScale(scale);
        if (background!=null) background.removeFromParent();
        background=Hud.rect(assets,w,h,new ColorRGBA(.025f,.05f,.075f,loadingBackdrop?.08f:.91f),0,0,0);
        node.attachChild(background);
    }
    String click(Vector2f cursor) {
        float x=(cursor.x-width/2f)/scale,y=(cursor.y-height/2f)/scale;
        for (Hit hit:hits) {
            if (x >= hit.x() && x <= hit.x() + hit.width() && y >= hit.y() && y <= hit.y() + hit.height()) {
                return hit.button().id();
            }
        }
        return null;
    }
    void hover(Vector2f cursor) {
        String id=click(cursor);
        String tooltip=id!=null && id.startsWith("creative:")?ItemType.valueOf(id.substring(9)).title:"";
        if(!tooltip.equals(creativeHovered) || (creativeTooltip!=null && creativeTooltip.getParent()==null)) {
            creativeHovered=tooltip;
            if(creativeTooltip!=null)creativeTooltip.removeFromParent();
            creativeTooltip=null;
            if(!tooltip.isEmpty()) {
                creativeTooltip=Hud.label(assets,tooltip,13);
                int labelWidth=creativeTooltip.getUserData("width");
                creativeTooltip.setLocalTranslation(-labelWidth/2f,-162,4);panel.attachChild(creativeTooltip);
            }
        }
        for (Hit hit:hits) hit.background().getMaterial().setColor("Color",hit.button().id().equals(id)?HOVER:NORMAL);
    }
    Vector2f buttonCenter(String id) {
        for (Hit hit:hits) if (hit.button().id().equals(id)) return new Vector2f(width/2f + hit.x()*scale, height/2f + (hit.y()+hit.height()/2f)*scale);
        throw new IllegalArgumentException("No menu button: "+id);
    }
}
