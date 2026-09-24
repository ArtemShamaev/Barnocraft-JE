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
    private record Hit(Button button, float y, Geometry background) {}
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
            panel.attachChild(rect); hits.add(new Hit(button,y,rect));
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
            Button b=new Button(slider.id(),slider.label()); Geometry hit=Hud.rect(assets,400,42,new ColorRGBA(0,0,0,0),-200,y-18,4); panel.attachChild(hit); hits.add(new Hit(b,y-18,hit));
            y-=64;
        }
        for (Button button:buttons) { Geometry rect=Hud.rect(assets,400,44,NORMAL.clone(),-200,y,2); panel.attachChild(rect); hits.add(new Hit(button,y,rect)); text(button.label(),20,y+9,ColorRGBA.White); y-=54; }
        resize(width,height);
    }
    void showInventory(Inventory inv) {
        panel.detachAllChildren(); hits.clear(); node.setCullHint(Node.CullHint.Never);
        panel.attachChild(Hud.rect(assets,500,600,new ColorRGBA(.035f,.065f,.09f,.97f),-250,-300,1));
        text("Инвентарь",30,190,ColorRGBA.White);
        text("Крафт 2 × 2",21,145,new ColorRGBA(.8f,.85f,.86f,1));
        for(int row=0;row<2;row++) for(int col=0;col<2;col++)
            panel.attachChild(Hud.rect(assets,62,62,new ColorRGBA(.16f,.24f,.27f,1),-100+col*70,45-row*70,2));
        text("Камушек: "+inv.count(ItemType.PEBBLE)+"   Палка: "+inv.count(ItemType.STICK)+"   Доска: "+inv.count(ItemType.PLANK),17,-45,ColorRGBA.White);
        text("Лезвие: "+inv.count(ItemType.STONE_BLADE)+"   Камнерез: "+inv.count(ItemType.STONECUTTER),16,-72,new ColorRGBA(.8f,.82f,.83f,1));
        text("Камнерез • слоты камня: "+inv.count(ItemType.STONE)+" / 5",15,-94,new ColorRGBA(.84f,.76f,.52f,1));
        text("Топор: "+inv.count(ItemType.AXE)+"   Кирка: "+inv.count(ItemType.PICKAXE),15,-112,new ColorRGBA(.8f,.82f,.83f,1));
        List<Button> recipes=List.of(new Button("craft_blade","4 камушка → Лезвие"),new Button("craft_plank","4 палки → Доска"),new Button("craft_cutter","Лезвие + 2 доски → Камнерез"),new Button("cutter_axe","Камнерез: Навершие топора"),new Button("cutter_pick","Камнерез: Навершие кирки"),new Button("craft_axe","Палка + навершие → Топор"),new Button("craft_pick","Палка + навершие → Кирка"));
        float y=-128;
        for(Button button:recipes){ Geometry rect=Hud.rect(assets,400,25,NORMAL.clone(),-200,y,2); panel.attachChild(rect); hits.add(new Hit(button,y,rect)); text(button.label(),13,y+6,ColorRGBA.White); y-=27; }
        panel.attachChild(Hud.rect(assets,400,25,NORMAL.clone(),-200,-286,2)); text("Закрыть (E)",14,-280,ColorRGBA.White);
        resize(width,height);
    }
    private void text(String value,int size,float y,ColorRGBA color) {
        Geometry label=Hud.label(assets,value,size); int w=label.getUserData("width");
        label.getMaterial().setColor("Color",color); label.setLocalTranslation(-w/2f,y,3); panel.attachChild(label);
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
        if (x < -200 || x > 200) return null;
        for (Hit hit:hits) if (y>=hit.y() && y<=hit.y()+44) return hit.button().id();
        return null;
    }
    void hover(Vector2f cursor) {
        String id=click(cursor);
        for (Hit hit:hits) hit.background().getMaterial().setColor("Color",hit.button().id().equals(id)?HOVER:NORMAL);
    }
    Vector2f buttonCenter(String id) {
        for (Hit hit:hits) if (hit.button().id().equals(id)) return new Vector2f(width/2f,height/2f+(hit.y()+22)*scale);
        throw new IllegalArgumentException("No menu button: "+id);
    }
}
