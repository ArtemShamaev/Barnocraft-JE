package com.barnocraft;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Original Barnocraft 5 x 7 bitmap alphabet. No system fonts or smoothing. */
final class CubeFont {
    private static final Map<Character,int[]> GLYPHS=new HashMap<>();
    static {
        glyph('A',"0E 11 11 1F 11 11 11"); glyph('B',"1E 11 11 1E 11 11 1E");
        glyph('C',"0F 10 10 10 10 10 0F"); glyph('D',"1E 11 11 11 11 11 1E");
        glyph('E',"1F 10 10 1E 10 10 1F"); glyph('F',"1F 10 10 1E 10 10 10");
        glyph('G',"0F 10 10 17 11 11 0F"); glyph('H',"11 11 11 1F 11 11 11");
        glyph('I',"1F 04 04 04 04 04 1F"); glyph('J',"07 02 02 02 12 12 0C");
        glyph('K',"11 12 14 18 14 12 11"); glyph('L',"10 10 10 10 10 10 1F");
        glyph('M',"11 1B 15 15 11 11 11"); glyph('N',"11 19 19 15 13 13 11");
        glyph('O',"0E 11 11 11 11 11 0E"); glyph('P',"1E 11 11 1E 10 10 10");
        glyph('Q',"0E 11 11 11 15 12 0D"); glyph('R',"1E 11 11 1E 14 12 11");
        glyph('S',"0F 10 10 0E 01 01 1E"); glyph('T',"1F 04 04 04 04 04 04");
        glyph('U',"11 11 11 11 11 11 0E"); glyph('V',"11 11 11 11 11 0A 04");
        glyph('W',"11 11 11 15 15 1B 11"); glyph('X',"11 11 0A 04 0A 11 11");
        glyph('Y',"11 11 0A 04 04 04 04"); glyph('Z',"1F 01 02 04 08 10 1F");
        glyph('0',"0E 11 13 15 19 11 0E"); glyph('1',"04 0C 04 04 04 04 0E");
        glyph('2',"0E 11 01 02 04 08 1F"); glyph('3',"1E 01 01 0E 01 01 1E");
        glyph('4',"02 06 0A 12 1F 02 02"); glyph('5',"1F 10 10 1E 01 01 1E");
        glyph('6',"0E 10 10 1E 11 11 0E"); glyph('7',"1F 01 02 04 08 08 08");
        glyph('8',"0E 11 11 0E 11 11 0E"); glyph('9',"0E 11 11 0F 01 01 0E");
        String cyr="АВСЕНКМОРТХ", lat="ABCEHKMOPTX";
        for(int i=0;i<cyr.length();i++)GLYPHS.put(cyr.charAt(i),GLYPHS.get(lat.charAt(i)));
        glyph('Б',"1F 10 10 1E 11 11 1E"); glyph('Г',"1F 10 10 10 10 10 10");
        glyph('Д',"06 0A 0A 0A 0A 1F 11"); glyph('Ё',"0A 00 1F 10 1E 10 1F");
        glyph('Ж',"15 15 0E 04 0E 15 15"); glyph('З',"1E 01 01 0E 01 01 1E");
        glyph('И',"11 11 13 15 19 11 11"); glyph('Й',"0A 04 11 13 15 19 11");
        glyph('Л',"07 09 09 09 09 09 11"); glyph('П',"1F 11 11 11 11 11 11");
        glyph('У',"11 11 11 0F 01 01 1E"); glyph('Ф',"04 0E 15 15 15 0E 04");
        glyph('Ц',"12 12 12 12 12 1F 01"); glyph('Ч',"11 11 11 0F 01 01 01");
        glyph('Ш',"15 15 15 15 15 15 1F"); glyph('Щ',"15 15 15 15 15 1F 01");
        glyph('Ъ',"18 08 08 0E 09 09 0E"); glyph('Ы',"11 11 11 19 15 15 19");
        glyph('Ь',"10 10 10 1E 11 11 1E"); glyph('Э',"1E 01 01 0F 01 01 1E");
        glyph('Ю',"12 15 15 1D 15 15 12"); glyph('Я',"0F 11 11 0F 05 09 11");
        glyph(' ',"00 00 00 00 00 00 00"); glyph('?',"0E 11 01 02 04 00 04");
        glyph('!',"04 04 04 04 04 00 04"); glyph('.',"00 00 00 00 00 00 04");
        glyph(',',"00 00 00 00 00 04 08"); glyph(':',"00 04 00 00 04 00 00");
        glyph(';',"00 04 00 00 04 04 08"); glyph('-',"00 00 00 1F 00 00 00");
        glyph('_',"00 00 00 00 00 00 1F"); glyph('+',"00 04 04 1F 04 04 00");
        glyph('=',"00 00 1F 00 1F 00 00"); glyph('/',"01 01 02 04 08 10 10");
        glyph('\\',"10 10 08 04 02 01 01"); glyph('|',"04 04 04 04 04 04 04");
        glyph('(',"02 04 08 08 08 04 02"); glyph(')',"08 04 02 02 02 04 08");
        glyph('[',"0E 08 08 08 08 08 0E"); glyph(']',"0E 02 02 02 02 02 0E");
        glyph('<',"01 02 04 08 04 02 01"); glyph('>',"10 08 04 02 04 08 10");
        glyph('"',"0A 0A 0A 00 00 00 00"); glyph('\'',"04 04 08 00 00 00 00");
        glyph('%',"19 19 02 04 08 13 13"); glyph('#',"0A 0A 1F 0A 1F 0A 0A");
        glyph('*',"00 15 0E 1F 0E 15 00"); glyph('@',"0E 11 17 15 17 10 0F");
        glyph('&',"0C 12 14 08 15 12 0D"); glyph('$',"04 0F 14 0E 05 1E 04");
        glyph('^',"04 0A 11 00 00 00 00"); glyph('~',"00 00 09 16 00 00 00");
        glyph('`',"08 04 00 00 00 00 00"); glyph('{',"02 04 04 08 04 04 02");
        glyph('}',"08 04 04 02 04 04 08");
        glyph('♥',"00 0A 1F 1F 0E 04 00"); glyph('→',"00 04 02 1F 02 04 00");
        glyph('←',"00 04 08 1F 08 04 00"); glyph('×',"00 11 0A 04 0A 11 00");
        glyph('·',"00 00 00 04 00 00 00"); glyph('…',"00 00 00 00 00 00 15");
        glyph('№',"11 19 15 13 11 00 1F"); glyph('°',"0C 12 0C 00 00 00 00");
        for(char c:new char[]{'—','–'})GLYPHS.put(c,GLYPHS.get('-'));
        GLYPHS.put('•',GLYPHS.get('·')); GLYPHS.put('«',GLYPHS.get('<')); GLYPHS.put('»',GLYPHS.get('>'));
        for(char c:new char[]{'“','”','„'})GLYPHS.put(c,GLYPHS.get('"'));
    }
    private static void glyph(char c,String data) {
        GLYPHS.put(c,java.util.Arrays.stream(data.split(" ")).mapToInt(v->Integer.parseInt(v,16)).toArray());
    }
    static boolean supports(char c) { return GLYPHS.containsKey(Character.toUpperCase(c)); }
    static BufferedImage render(String text,int size) {
        int scale=Math.max(1,Math.round(size/10f));
        int width=Math.max(1,text.length()*6*scale+4),height=8*scale+4;
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int pass=0;pass<2;pass++)for(int i=0;i<text.length();i++) {
            int[] rows=GLYPHS.getOrDefault(Character.toUpperCase(text.charAt(i)),GLYPHS.get('?'));
            int offset=pass==0?scale:0,color=pass==0?0xff17202a:0xffffffff;
            for(int y=0;y<7;y++)for(int x=0;x<5;x++)if((rows[y]&(1<<(4-x)))!=0)
                for(int sy=0;sy<scale;sy++)for(int sx=0;sx<scale;sx++)
                    image.setRGB(2+i*6*scale+x*scale+sx+offset,2+y*scale+sy+offset,color);
        }
        return image;
    }
}
