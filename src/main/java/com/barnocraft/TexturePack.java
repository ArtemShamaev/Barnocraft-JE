package com.barnocraft;

/** Texture sets available in the settings screen. */
enum TexturePack {
    ORIGINAL("Оригинальные", ""),
    MEADOW("Мягкий пиксель", "soft");
    final String title, folder;
    TexturePack(String title, String folder) { this.title=title; this.folder=folder; }
    static TexturePack from(int value) { return value == 1 ? MEADOW : ORIGINAL; }
    String path(String file) { return folder.isEmpty() ? file : folder + "/" + file; }
}
