package com.barnocraft;

public enum Block {
    AIR(null), GRASS("grass_2.png"), STONE("stone.png"), SAND("pesok.png"),
    PLANKS("tree_planks.png"), GLASS("glass.png"), LOG("tree.png"), LEAVES("listia.png"), COAL_ORE("coal.png"), IRON_ORE("iron.png"), DEEPSTONE("deeprock.png"), DOOR("door.png"), DOOR_TOP("door.png"), STAIRS("stairs.png"), ROTATOR("rotator.png"), STONECUTTER("stonecutter.png"), FURNACE("furnace.png"), TORCH("torch.png"), WATER("water.png"), CASTING_TABLE("casting_table.png"), WELDER("welder.png");

    public static final Block[] HOTBAR = new Block[10];
    public final String texture;

    Block(String texture) { this.texture = texture; }
    public boolean door() { return this == DOOR || this == DOOR_TOP; }
    public boolean rock() { return this == STONE || this == DEEPSTONE; }
    public boolean woodMaterial() {
        return switch (this) {
            case LOG, PLANKS, DOOR, DOOR_TOP, STAIRS, LEAVES -> true;
            default -> false;
        };
    }
    public boolean stoneMaterial() {
        return switch (this) {
            case STONE, DEEPSTONE, COAL_ORE, IRON_ORE, STONECUTTER, FURNACE, CASTING_TABLE, WELDER -> true;
            default -> false;
        };
    }
    public boolean earthMaterial() { return this == GRASS || this == SAND; }
    public boolean solid() { return this != AIR && this != ROTATOR && this != TORCH; }
    public float breakTime() {
        return switch (this) {
            case AIR -> 0.0f;
            case GRASS -> 0.60f;
            case STONE -> 0.80f;
            case SAND -> 0.45f;
            case PLANKS -> 0.55f;
            case GLASS -> 1.10f;
            case LOG -> 0.75f;
            case LEAVES -> 0.45f;
            case COAL_ORE -> 0.90f;
            case IRON_ORE -> 1.00f;
            case DEEPSTONE -> 1.20f;
            case DOOR, DOOR_TOP -> 1.50f;
            case STAIRS -> 0.70f;
            case ROTATOR -> 0.0f;
            default -> 0.75f;
        };
    }
    public float breakTime(ItemType tool) {
        if ((this == STONE || this == DEEPSTONE) && tool == ItemType.SHARP_STICK) {
            return breakTime() * 0.5f;
        }
        if (woodMaterial() && tool == ItemType.STONE_AXE) return breakTime() * 0.35f;
        if (woodMaterial() && tool == ItemType.IRON_AXE) return breakTime() * 0.18f;
        if (stoneMaterial() && tool == ItemType.STONE_PICKAXE) return breakTime() * 0.35f;
        if (stoneMaterial() && tool == ItemType.IRON_PICKAXE) return breakTime() * 0.18f;
        if (earthMaterial() && tool == ItemType.STONE_SHOVEL) return breakTime() * 0.30f;
        if (earthMaterial() && tool == ItemType.IRON_SHOVEL) return breakTime() * 0.16f;
        return breakTime();
    }
}
