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
    private final Geometry selection;
    private final Geometry[] names = new Geometry[Block.HOTBAR.length];
    private final Geometry[] icons = new Geometry[Block.HOTBAR.length];
    private final AssetManager assets;
    private final float barWidth = Block.HOTBAR.length * 48 + 10;
    private final float slotStart = -barWidth / 2 + 6;
    private int width = -1, height = -1;

    public Hud(AssetManager assets) {
        this.assets=assets;
        node.attachChild(bar); node.attachChild(crosshair);
        bar.attachChild(rect(assets, barWidth, 56, new ColorRGBA(0,0,0,.6f), -barWidth / 2, 0, 0));
        selection = rect(assets, 44, 44, ColorRGBA.White, 0, 6, 1);
        bar.attachChild(selection);
        String[] labels = {"Дёрн", "Камень", "Песок", "Доски", "Стекло", "Бревно", "Дверь", "Ступеньки", "Поворачиватель"};
        for (int i = 0; i < Block.HOTBAR.length; i++) {
            float x = slotStart + i * 48;
            bar.attachChild(rect(assets, 40, 40, new ColorRGBA(.2f,.2f,.2f,1), x + 2, 8, 2));
            boolean door = Block.HOTBAR[i] == Block.DOOR;
            Geometry icon = rect(assets, door ? 16 : 32, 32, ColorRGBA.White, x + (door ? 14 : 6), 12, 3);
            icon.getMaterial().setTexture("ColorMap", WorldView.texture(assets, Block.HOTBAR[i].texture));
            icons[i]=icon;
            bar.attachChild(icon);
            Geometry name = label(assets, (i + 1) + " — " + labels[i], 20);
            int labelWidth = name.getUserData("width");
            name.setLocalTranslation(-labelWidth / 2f, 62, 1);
            names[i] = name; bar.attachChild(name);
        }
        crosshair.attachChild(rect(assets, 2, 14, ColorRGBA.White, -1, -7, 5));
        crosshair.attachChild(rect(assets, 14, 2, ColorRGBA.White, -7, -1, 5));
        select(1); active(false);
    }
    public void setTexturePack(TexturePack pack) {
        for (int i=0;i<icons.length;i++) icons[i].getMaterial().setTexture("ColorMap", WorldView.texture(assets, pack.path(Block.HOTBAR[i].texture)));
    }
    public void select(int index) {
        selection.setLocalTranslation(slotStart + index * 48, 6, 1);
        for (int i = 0; i < names.length; i++) names[i].setCullHint(i == index ? Node.CullHint.Never : Node.CullHint.Always);
    }
    public void active(boolean active) {
        crosshair.setCullHint(active ? Node.CullHint.Never : Node.CullHint.Always);
    }
    public void resize(int w, int h) {
        if (w == width && h == height) return;
        width = w; height = h;
        bar.setLocalTranslation(w / 2f, 16, 0);
        crosshair.setLocalTranslation(w / 2f, h / 2f, 0);
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
