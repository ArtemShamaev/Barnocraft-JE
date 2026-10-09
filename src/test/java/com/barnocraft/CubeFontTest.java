package com.barnocraft;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CubeFontTest {
    @Test void allRussianLatinAndInterfaceSymbolsHavePixelGlyphs() throws Exception {
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя0123456789♥→←×·…—–№°";
        for(char c:alphabet.toCharArray())assertTrue(CubeFont.supports(c),"Missing glyph "+c);
        for(int size:new int[]{12,15,20,32}) {
            var image=CubeFont.render(alphabet,size);
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
                int alpha=image.getRGB(x,y)>>>24;assertTrue(alpha==0 || alpha==255,"No blurred edges");
            }
        }
        ImageIO.write(CubeFont.render("Barnocraft Java Edition · Привет, мир! Ёжик 0123456789",20),"png",Path.of("target/cube-font-review.png").toFile());
    }
}
