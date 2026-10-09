package com.barnocraft;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentTexturesTest {
    @Test void equipmentHasDistinctReadableIconsAndBothTexturePacksResolve() throws Exception {
        String[] files={"stone_axe.png","stone_pickaxe.png","stone_shovel.png","stone_axe_head.png","stone_pickaxe_head.png","stone_shovel_head.png",
                "iron_axe.png","iron_pickaxe.png","iron_shovel.png","iron_axe_head.png","iron_pickaxe_head.png","iron_shovel_head.png",
                "gold_axe.png","gold_pickaxe.png","gold_shovel.png","gold_axe_head.png","gold_pickaxe_head.png","gold_shovel_head.png",
                "gold.png","gold_ingot.png","iron_ingot.png","rotator.png","sharp_stick.png","stone_blade.png"};
        BufferedImage sheet=new BufferedImage(6*144,4*154,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setColor(new Color(0x26343b));g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        HashSet<Integer> signatures=new HashSet<>();
        for(int i=0;i<files.length;i++) {
            BufferedImage img=EquipmentTextures.create(files[i]); int visible=0;
            int[] pixels=img.getRGB(0,0,32,32,null,0,32);
            for(int pixel:pixels) if((pixel>>>24)!=0) visible++;
            assertTrue(visible>60); assertTrue(signatures.add(java.util.Arrays.hashCode(pixels)),"Distinct icon "+files[i]);
            for(String prefix:new String[]{"","soft/"}) assertEquals(32,WorldView.texture(null,prefix+files[i]).getImage().getWidth());
            int x=i%6*144,y=i/6*154;g.drawImage(img,x+8,y+4,128,128,null);
            g.setColor(Color.WHITE);g.drawString(files[i].replace(".png",""),x+3,y+147);
        }
        g.dispose();ImageIO.write(sheet,"png",Path.of("target","equipment-review.png").toFile());
    }
}
