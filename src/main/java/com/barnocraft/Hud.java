package com.barnocraft;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Native GUI scene with Russian text rendered using the JVM's logical font. */
public final class Hud {
    public final Node node = new Node("HUD");
    private final Node bar = new Node("Hotbar"), crosshair = new Node("Crosshair");
    private final Node commandOverlay = new Node("Command input");
    private final Node healthOverlay = new Node("Health");
    private final Geometry selection;
    private final Geometry[] names = new Geometry[Block.HOTBAR.length];
    private final Geometry[] icons = new Geometry[Block.HOTBAR.length];
    private final Geometry[] durabilityBack = new Geometry[Block.HOTBAR.length];
    private final Geometry[] durabilityFill = new Geometry[Block.HOTBAR.length];
    private final ItemType[] shownItems = new ItemType[Block.HOTBAR.length];
    private final AssetManager assets;
    private TexturePack texturePack = TexturePack.from(0);
    private int selectedIndex;
    private final float barWidth = Block.HOTBAR.length * 48 + 10;
    private final float slotStart = -barWidth / 2 + 6;
    private int width = -1, height = -1;
    private String commandText="";

    public Hud(AssetManager assets) {
        this.assets=assets;
        node.attachChild(bar); node.attachChild(crosshair); node.attachChild(commandOverlay); node.attachChild(healthOverlay);
        commandOverlay.setCullHint(Node.CullHint.Always);
        setHealth(Player.MAX_HEALTH);
        bar.attachChild(rect(assets, barWidth, 56, new ColorRGBA(0,0,0,.6f), -barWidth / 2, 0, 0));
        selection = rect(assets, 44, 44, ColorRGBA.White, 0, 6, 1);
        bar.attachChild(selection);
        for (int i = 0; i < Block.HOTBAR.length; i++) {
            float x = slotStart + i * 48;
            bar.attachChild(rect(assets, 40, 40, new ColorRGBA(.2f,.2f,.2f,1), x + 2, 8, 2));
            Geometry name = label(assets, (i + 1) + " — Пусто", 20);
            int labelWidth = name.getUserData("width");
            name.setLocalTranslation(-labelWidth / 2f, 62, 1);
            names[i] = name; bar.attachChild(name);
        }
        crosshair.attachChild(rect(assets, 2, 14, ColorRGBA.White, -1, -7, 5));
        crosshair.attachChild(rect(assets, 14, 2, ColorRGBA.White, -7, -1, 5));
        select(1); active(false);
    }
    public void setTexturePack(TexturePack pack) {
        texturePack = pack;
        for (int i=0;i<shownItems.length;i++) shownItems[i] = null;
    }
    public void refreshHotbar(Inventory inventory) {
        for (int i=0;i<icons.length;i++) {
            ItemType item = inventory.hotbarItemAt(i);
            if (item != shownItems[i]) {
                shownItems[i] = item;
                if (icons[i] != null) {
                    icons[i].removeFromParent();
                    icons[i] = null;
                }
                if (item != null) {
                    float x = slotStart + i * 48;
                    Geometry icon = rect(assets, 32, 32, ColorRGBA.White, x + 6, 12, 3);
                    icon.getMaterial().setTexture("ColorMap", WorldView.texture(assets, texturePack.path(item.texture)));
                    icons[i] = icon;
                    bar.attachChild(icon);
                }
                Geometry name = names[i];
                name.removeFromParent();
                String label = item == null ? "Пусто" : item.title;
                name = label(assets, (i + 1) + " — " + label, 20);
                int labelWidth = name.getUserData("width");
                name.setLocalTranslation(-labelWidth / 2f, 62, 1);
                name.setCullHint(i == selectedIndex ? Node.CullHint.Never : Node.CullHint.Always);
                names[i] = name;
                bar.attachChild(name);
            }
            int durability=inventory.durabilityAt(i);
            if (item!=null && item.tool()) {
                float x=slotStart+i*48;
                if (durabilityBack[i]==null) {
                    durabilityBack[i]=rect(assets,32,3,new ColorRGBA(.08f,.08f,.08f,1),x+4,4,4);
                    durabilityFill[i]=rect(assets,32,3,new ColorRGBA(.34f,.78f,.34f,1),x+4,4,5);
                    bar.attachChild(durabilityBack[i]); bar.attachChild(durabilityFill[i]);
                }
                float fraction=(float)durability/item.maxDurability();
                durabilityFill[i].setLocalScale(Math.max(.01f,fraction),1,1);
                ColorRGBA color=fraction>.5f?new ColorRGBA(.34f,.78f,.34f,1)
                        :fraction>.2f?new ColorRGBA(.91f,.68f,.22f,1):new ColorRGBA(.88f,.28f,.22f,1);
                durabilityFill[i].getMaterial().setColor("Color",color);
            } else if (durabilityBack[i]!=null) {
                durabilityBack[i].removeFromParent(); durabilityBack[i]=null;
                durabilityFill[i].removeFromParent(); durabilityFill[i]=null;
            }
        }
    }
    public void select(int index) {
        selectedIndex = index;
        selection.setLocalTranslation(slotStart + index * 48, 6, 1);
        for (int i = 0; i < names.length; i++) names[i].setCullHint(i == index ? Node.CullHint.Never : Node.CullHint.Always);
    }
    public void commandLine(String text) {
        if (text.equals(commandText)) return;
        commandText=text;
        commandOverlay.detachAllChildren();
        if (text.isEmpty()) {
            commandOverlay.setCullHint(Node.CullHint.Always);
            return;
        }
        Geometry label=label(assets,text,16);
        int labelWidth=label.getUserData("width");
        commandOverlay.attachChild(rect(assets,labelWidth+20,30,new ColorRGBA(.02f,.025f,.03f,.88f),0,0,0));
        label.setLocalTranslation(10,7,1);
        commandOverlay.attachChild(label);
        commandOverlay.setCullHint(Node.CullHint.Never);
        commandOverlay.setLocalTranslation(16,Math.max(12,height-48),20);
    }
    public void setHealth(int health) {
        healthOverlay.detachAllChildren();
        Geometry backing=rect(assets,164,28,new ColorRGBA(.02f,.025f,.03f,.66f),0,0,0);
        healthOverlay.attachChild(backing);
        Geometry value=label(assets,"♥ "+health+" / "+Player.MAX_HEALTH,18);
        value.getMaterial().setColor("Color",health<=6?new ColorRGBA(1f,.35f,.30f,1):new ColorRGBA(1f,.76f,.68f,1));
        value.setLocalTranslation(8,4,1); healthOverlay.attachChild(value);
        healthOverlay.setLocalTranslation(14,Math.max(12,height-40),20);
    }
    public void active(boolean active) {
        crosshair.setCullHint(active ? Node.CullHint.Never : Node.CullHint.Always);
    }
    public void resize(int w, int h) {
        if (w == width && h == height) return;
        width = w; height = h;
        bar.setLocalTranslation(w / 2f, 16, 0);
        crosshair.setLocalTranslation(w / 2f, h / 2f, 0);
        commandOverlay.setLocalTranslation(16,Math.max(12,h-48),20);
        healthOverlay.setLocalTranslation(14,Math.max(12,h-40),20);
        bar.setLocalScale(Math.min(1,w/(barWidth+20)));
    }
    static Geometry label(AssetManager assets, String text, int size) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        g.setFont(font);
        int w = g.getFontMetrics().stringWidth(text) + 8, h = g.getFontMetrics().getHeight() + 4;
        int baseline = g.getFontMetrics().getAscent() + 2;
        g.dispose();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics(); g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(java.awt.Color.BLACK); g.drawString(text, 5, baseline + 1);
        g.setColor(java.awt.Color.WHITE); g.drawString(text, 4, baseline); g.dispose();
        Geometry result = rect(assets, w, h, ColorRGBA.White, 0, 0, 0);
        result.getMaterial().setTexture("ColorMap", new Texture2D(new AWTLoader().load(image, true)));
        result.setUserData("width", w);
        return result;
    }
    static Geometry rect(AssetManager assets, float w, float h, ColorRGBA color, float x, float y, float z) {
        Geometry result = new Geometry("HUD element", new Quad(w, h));
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        result.setMaterial(material); result.setLocalTranslation(x, y, z);
        return result;
    }
}
