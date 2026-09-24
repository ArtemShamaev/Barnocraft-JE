package com.barnocraft;

enum ItemType {
    PEBBLE("Камушек", "pebble.png"), STICK("Палка", "stick.png"), PLANK("Доска", "tree_planks.png"),
    STONE("Камень", "stone.png"), DEEPSTONE("Глубокий камень", "deeprock.png"),
    STONE_BLADE("Лезвие камнереза", "stone.png"), STONECUTTER("Камнерез", "stone.png"),
    AXE_HEAD("Навершие топора", "tree.png"), PICKAXE_HEAD("Навершие кирки", "stone.png"),
    AXE("Топор", "tree.png"), PICKAXE("Кирка", "stone.png");
    final String title, texture;
    ItemType(String title, String texture) { this.title=title; this.texture=texture; }
}
