package com.barnocraft;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

final class GameOptions {
    int distance = 3, fov = 75;
    float sensitivity = 1;
    int texturePack = 0;
    void load(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (var in = Files.newInputStream(path)) { p.load(in); }
        try {
            distance = Math.max(2,Math.min(6,Integer.parseInt(p.getProperty("distance","3"))));
            fov = Math.max(60,Math.min(100,Integer.parseInt(p.getProperty("fov","75"))));
            sensitivity = Float.parseFloat(p.getProperty("sensitivity","1"));
            if (!Float.isFinite(sensitivity)) sensitivity = 1;
            sensitivity = Math.max(.5f,Math.min(2,sensitivity));
            texturePack = Math.max(0, Math.min(1, Integer.parseInt(p.getProperty("texturePack", "0"))));
        } catch (NumberFormatException e) { throw new IOException("Некорректные настройки",e); }
    }
    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties p = new Properties();
        p.setProperty("distance",Integer.toString(distance)); p.setProperty("fov",Integer.toString(fov));
        p.setProperty("sensitivity",Float.toString(sensitivity));
        p.setProperty("texturePack",Integer.toString(texturePack));
        Path temp = Files.createTempFile(path.getParent(),"settings-",".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { p.store(out,"Barnocraft"); }
            SaveStore.replace(temp,path);
        } finally { Files.deleteIfExists(temp); }
    }
}
