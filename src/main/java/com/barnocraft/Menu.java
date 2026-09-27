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
    private record Hit(Button button, float x, float y, float width, float height, Geometry background) {}
    final Node node = new Node("Menu");
    private final Node panel = new Node("Menu panel");
    private final AssetManager assets;
    private final List<Hit> hits = new ArrayList<>();
    private Geometry background;
    private int width = 1280, height = 720;
    private float scale = 1;
    private static final ColorRGBA NORMAL = new ColorRGBA(.13f,.20f,.24f,1);
    private static final ColorRGBA HOVER = new ColorRGBA(.24f,.36f,.37f,1);

    Menu(AssetManager assets) { this.assets = assets; node.attachChild(panel); }
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
        showInventory(inv,false);
    }
    void showInventory(Inventory inv, boolean book) {
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
        }
        textAt("Результат",13,resultX+resultSize/2f,resultY+resultSize+8f,new ColorRGBA(.76f,.82f,.81f,1));

        Geometry bookButton=Hud.rect(assets,170f,30f,new ColorRGBA(.32f,.39f,.25f,1),145f,-150f,2);
        panel.attachChild(bookButton); hits.add(new Hit(new Button("recipe_book","Книга рецептов"),145f,-150f,170f,30f,bookButton));
        textAt(book?"Скрыть рецепты":"Книга рецептов",13,230f,-141f,ColorRGBA.White);
        if (book) {
            String[] recipes={"Рецепты:","Факел: палка + уголь","Печь: 4 камня","Камнерез: 2 камня + 2 доски",
                    "Стол выплавки: 2 слитка + 2 доски","Сварка: слиток + палка + 2 бревна","Инструмент: палка + навершие"};
            for (int i=0;i<recipes.length;i++) textAt(recipes[i],11,230f,-174f-i*18,new ColorRGBA(.82f,.86f,.82f,1));
        }

        panel.attachChild(Hud.rect(assets,200f,30f,new ColorRGBA(.13f,.20f,.24f,1),-60f,-220f,2));
        text("Закрыть (E)",14,-212f,ColorRGBA.White);
        resize(width,height);
    }
    void showStonecutter(Inventory inventory,World.MachineSlots machine,String message) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,640,560,new ColorRGBA(.035f,.065f,.09f,.97f),-320,-280,1));
        textAt("Камнерез",24,0f,226f,ColorRGBA.White);
        textAt("Выбери навершие",16,0f,198f,new ColorRGBA(.83f,.86f,.82f,1));
        ItemType[] recipes={ItemType.AXE_HEAD,ItemType.PICKAXE_HEAD,ItemType.SHOVEL_HEAD,
                ItemType.IRON_AXE_HEAD,ItemType.IRON_PICKAXE_HEAD,ItemType.IRON_SHOVEL_HEAD};
        String[] recipeLabels={"Кам. топор","Кам. кирка","Кам. лопата","Жел. топор","Жел. кирка","Жел. лопата"};
        for (int i=0;i<recipes.length;i++) {
            float x=18f+(i%3)*98f,y=158f-(i/3)*34f;
            ColorRGBA color=machine.recipe()==recipes[i]
                ? new ColorRGBA(.46f,.36f,.18f,1) : new ColorRGBA(.13f,.20f,.24f,1);
            Geometry button=Hud.rect(assets,88,26,color,x,y,2);
            panel.attachChild(button);
            hits.add(new Hit(new Button("recipe:"+recipes[i].name(),recipeLabels[i]),x,y,88,26,button));
            textAt(recipeLabels[i],12,x+44,y+7,ColorRGBA.White);
        }
        textAt("Выбрано: "+machine.recipe().title,14,0f,86f,new ColorRGBA(.92f,.74f,.38f,1));
        ItemType material=World.stonecutterMaterial(machine.recipe());
        String materialLabel=material==ItemType.IRON_INGOT?"Слитки ×5":"Камень";
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
        String[] labels={"Уголь","Железо / песок","Слитки / стекло"};
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
        background=Hud.rect(assets,w,h,new ColorRGBA(.025f,.05f,.075f,.91f),0,0,0);
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
        for (Hit hit:hits) hit.background().getMaterial().setColor("Color",hit.button().id().equals(id)?HOVER:NORMAL);
    }
    Vector2f buttonCenter(String id) {
        for (Hit hit:hits) if (hit.button().id().equals(id)) return new Vector2f(width/2f + hit.x()*scale, height/2f + (hit.y()+hit.height()/2f)*scale);
        throw new IllegalArgumentException("No menu button: "+id);
    }
}
