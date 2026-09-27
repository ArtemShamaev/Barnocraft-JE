package com.barnocraft;

enum ItemType {
    PEBBLE("Камушек", "pebble.png"),
    COAL("Уголь", "coal.png"),
    IRON("Железо", "iron.png"),
    IRON_INGOT("Железный слиток", "iron_ingot.png"),
    MUTTON("Баранина", "mutton.png"),
    WOOL("Шерсть", "wool.png"),
    TORCH("Факел", "torch.png"),
    ROTTEN_FLESH("Гнилая плоть", "mutton.png"),
    STICK("Палка", "stick.png"),
    PLANK("Доска", "tree_planks.png"),
    WOOD("Дерево", "tree.png"),
    DIRT("Земля", "grass_2.png"),
    SAND("Песок", "pesok.png"),
    STONE("Камень", "stone.png"),
    DEEPSTONE("Глубокий камень", "deeprock.png"),
    GLASS("Стекло", "glass.png"),
    DOOR("Дверь", "door.png"),
    STAIRS("Ступеньки", "stairs.png"),
    STONE_BLADE("Лезвие камнереза", "stone.png"),
    SHARP_STICK("Острая палка", "sharp_stick.png"),
    STONECUTTER("Камнерез", "stonecutter.png"),
    FURNACE("Печь", "furnace.png"),
    AXE_HEAD("Навершие каменного топора", "tree.png"),
    PICKAXE_HEAD("Навершие каменной кирки", "stone.png"),
    SHOVEL_HEAD("Навершие каменной лопаты", "stone.png"),
    IRON_AXE_HEAD("Навершие железного топора", "iron_axe.png"),
    IRON_PICKAXE_HEAD("Навершие железной кирки", "iron_pickaxe.png"),
    IRON_SHOVEL_HEAD("Навершие железной лопаты", "iron_shovel.png"),
    STONE_AXE("Каменный топор", "stone_axe.png"),
    STONE_PICKAXE("Каменная кирка", "stone_pickaxe.png"),
    STONE_SHOVEL("Каменная лопата", "stone_shovel.png"),
    IRON_AXE("Железный топор", "iron_axe.png"),
    IRON_PICKAXE("Железная кирка", "iron_pickaxe.png"),
    IRON_SHOVEL("Железная лопата", "iron_shovel.png"),
    AXE("Топор", "tree.png"),
    PICKAXE("Кирка", "stone.png"),
    WATER("Вода", "water.png"), CASTING_TABLE("Стол для выплавки", "casting_table.png"),
    WELDER("Сварочный аппарат", "welder.png"), BUCKET_MOLD("Литейная форма ведра", "bucket_mold.png"),
    AXE_MOLD("Литейная форма топора", "axe_mold.png"), PICKAXE_MOLD("Литейная форма кирки", "pickaxe_mold.png"),
    SHOVEL_MOLD("Литейная форма лопаты", "shovel_mold.png"), BUCKET("Ведро", "bucket.png"),
    WATER_BUCKET("Ведро воды", "water_bucket.png");
    final String title, texture;
    ItemType(String title, String texture) { this.title=title; this.texture=texture; }

    int stackLimit() { return tool() || mold() || this==BUCKET || this==WATER_BUCKET ? 1 : Inventory.MAX_STACK; }
    boolean mold() { return this==BUCKET_MOLD || this==AXE_MOLD || this==PICKAXE_MOLD || this==SHOVEL_MOLD; }

    boolean tool() {
        return switch (this) {
            case SHARP_STICK, STONE_AXE, STONE_PICKAXE, STONE_SHOVEL,
                    IRON_AXE, IRON_PICKAXE, IRON_SHOVEL -> true;
            default -> false;
        };
    }

    int maxDurability() {
        return switch (this) {
            case BUCKET_MOLD, AXE_MOLD, PICKAXE_MOLD, SHOVEL_MOLD -> 32;
            case SHARP_STICK -> 20;
            case STONE_AXE, STONE_PICKAXE, STONE_SHOVEL -> 80;
            case IRON_AXE, IRON_PICKAXE, IRON_SHOVEL -> 250;
            default -> 0;
        };
    }
}
