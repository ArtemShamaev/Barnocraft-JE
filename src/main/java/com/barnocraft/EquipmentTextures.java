package com.barnocraft;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Pixel-art equipment: shared silhouettes, separate material palettes and head-only icons. */
final class EquipmentTextures {
    private EquipmentTextures() {}
    static boolean supports(String file) {
        return file.matches("(stone|iron|gold)_(axe|pickaxe|shovel)(_head)?\\.png")
                || file.equals("gold.png") || file.equals("gold_ingot.png") || file.equals("iron_ingot.png")
                || file.equals("rotator.png") || file.equals("sharp_stick.png") || file.equals("stone_blade.png");
    }
    static BufferedImage create(String file) {
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics(); // Integer coordinates and no antialiasing retain crisp pixels.
        int dark=file.startsWith("gold")?0x765019:file.startsWith("iron")?0x354552:0x343c42;
        int mid=file.startsWith("gold")?0xdba52c:file.startsWith("iron")?0x96abb9:0x78848a;
        int light=file.startsWith("gold")?0xffe58a:file.startsWith("iron")?0xe4f0f1:0xb6c0bb;
        if(file.equals("gold.png")) {
            for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
                int value=91+Math.floorMod(x*71+y*43+x*y*3,32);
                image.setRGB(x,y,0xff000000 | value<<16 | (value+4)<<8 | value+7);
            }
            int[][] nuggets={{5,5},{20,4},{12,14},{24,23},{4,25}};
            for(int[] n:nuggets) {
                g.setColor(new Color(dark));g.fillRect(n[0]-1,n[1]-1,7,6);
                g.setColor(new Color(mid));g.fillRect(n[0],n[1],5,4);
                g.setColor(new Color(light));g.fillRect(n[0],n[1],3,1);g.fillRect(n[0],n[1]+1,1,2);
            }
        } else if(file.endsWith("ingot.png")) {
            polygon(g,dark,3,16,10,8,25,8,29,15,26,23,6,23);
            polygon(g,mid,5,16,10,10,24,10,26,16,24,21,7,21);
            polygon(g,light,7,15,11,10,23,10,25,15);
            line(g,0xfff7cf,11,11,21,11);
            line(g,dark,7,17,24,17);
        } else if(file.equals("rotator.png")) {
            handle(g);
            polygon(g,0x674917,10,4,20,2,28,9,28,18,23,23,15,23,10,18,10,12,15,12,15,17,18,19,22,18,24,15,23,11,20,8,15,9);
            polygon(g,0xe5b63d,12,5,20,4,26,10,26,17,22,21,16,21,12,17,12,14,14,14,14,18,18,21,23,19,26,15,24,10,20,6,14,8);
            polygon(g,0xffe99c,10,3,10,11,18,10);
        } else if(file.equals("sharp_stick.png")) {
            handle(g);
            polygon(g,0x483523,17,13,27,3,24,16,20,19);
            polygon(g,0xdac193,20,13,26,5,23,15);
        } else if(file.equals("stone_blade.png")) {
            polygon(g,dark,4,13,8,6,18,3,27,10,29,20,21,28,10,27,3,21);
            polygon(g,mid,6,14,10,8,18,5,25,11,27,19,20,26,11,25,5,20);
            polygon(g,light,7,13,11,9,18,6,24,11,16,11);
            g.setColor(new Color(dark));g.fillRect(14,14,5,5);
        } else {
            boolean head=file.contains("_head");
            if(!head) handle(g); else g.translate(-3,5);
            if(file.contains("pickaxe")) {
                polygon(g,dark,7,10,12,5,21,3,26,6,29,12,30,18,27,17,24,12,20,9,14,10,8,13);
                polygon(g,mid,9,10,13,7,21,5,25,8,27,12,28,15,24,10,20,7,14,8);
                line(g,light,13,6,21,4);line(g,light,22,5,25,8);
            } else if(file.contains("shovel")) {
                polygon(g,dark,16,7,23,3,29,9,25,17,21,20,14,13);
                polygon(g,mid,17,8,23,5,27,9,24,15,21,17,16,12);
                polygon(g,light,23,6,26,9,23,13,20,15,18,12);
            } else {
                polygon(g,dark,14,5,22,3,29,6,30,13,26,19,21,17,21,12,16,14,12,10);
                polygon(g,mid,15,6,22,5,27,7,28,12,25,16,23,15,23,9,17,12,14,9);
                polygon(g,light,26,7,28,8,29,12,26,17,24,16,26,12);
            }
            if(!head) {
                g.setColor(new Color(0x553c27));g.fillRect(17,12,5,2);
                g.setColor(new Color(0xc3a16a));g.fillRect(18,12,3,1);
            }
        }
        g.dispose(); return image;
    }
    private static void handle(Graphics2D g) {
        polygon(g,0x35271f,3,25,19,9,23,13,7,29,3,29);
        polygon(g,0x926137,5,25,20,10,22,13,7,27,5,27);
        line(g,0xc99551,6,25,20,11);
        line(g,0x493224,6,23,9,26);line(g,0x493224,9,20,12,23);
    }
    private static void line(Graphics2D g,int color,int x,int y,int x2,int y2) {
        g.setColor(new Color(color));g.drawLine(x,y,x2,y2);
    }
    private static void polygon(Graphics2D g,int color,int... xy) {
        int[] x=new int[xy.length/2],y=new int[x.length];
        for(int i=0;i<x.length;i++) { x[i]=xy[i*2];y[i]=xy[i*2+1]; }
        g.setColor(new Color(color));g.fillPolygon(x,y,x.length);
    }
}
