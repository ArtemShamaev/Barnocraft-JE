package com.barnocraft;

public enum Block {
    AIR(null), GRASS("grass_2.png"), STONE("stone.png"), SAND("pesok.png"),
    PLANKS("tree_planks.png"), GLASS("glass.png"), LOG("tree.png"), LEAVES("listia.png"), COAL_ORE("coal.png"), IRON_ORE("iron.png"), DEEPSTONE("deeprock.png"), DOOR("door.png"), DOOR_TOP("door.png"), STAIRS("stairs.png"), ROTATOR("rotator.png"), STONECUTTER("stonecutter.png"), FURNACE("furnace.png"), TORCH("torch.png"), WATER("water.png"), CASTING_TABLE("casting_table.png"), WELDER("welder.png"), LANTERN("lantern.png"), BED("bed.png"), BED_HEAD("bed.png"), GOLD_ORE("gold.png"), FARMLAND("farmland.png"), CROP_0("listia.png"), CROP_1("listia.png"), CROP_2("listia.png"), CROP_3("listia.png"), CHEST("chest.png");

    public static final Block[] HOTBAR = new Block[10];
    public final String texture;

    Block(String texture) { this.texture = texture; }
    public boolean bed() { return this == BED || this == BED_HEAD; }
    public boolean door() { return this == DOOR || this == DOOR_TOP; }
    public boolean rock() { return this == STONE || this == DEEPSTONE; }
    public boolean crop() { return this==CROP_0 || this==CROP_1 || this==CROP_2 || this==CROP_3; }
    public int cropStage() { return crop()?ordinal()-CROP_0.ordinal():-1; }
    public boolean woodMaterial() {
        return switch (this) {
            case LOG, PLANKS, DOOR, DOOR_TOP, STAIRS, LEAVES, BED, BED_HEAD, CHEST -> true;
            default -> false;
        };
    }
    public boolean stoneMaterial() {
        return switch (this) {
            case STONE, DEEPSTONE, COAL_ORE, IRON_ORE, GOLD_ORE, STONECUTTER, FURNACE, CASTING_TABLE, WELDER -> true;
            default -> false;
        };
    }
    public boolean earthMaterial() { return this == GRASS || this == SAND; }
    /** Partial models admit light through the empty space in their voxel. */
    public boolean blocksLight() {
        return solid() && !door() && !bed() && this!=STAIRS && this!=STONECUTTER && this!=GLASS && this!=LEAVES;
    }
    public boolean solid() { return this != AIR && this != WATER && this != ROTATOR && this != TORCH && this != LANTERN && !crop(); }
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
            case CROP_0, CROP_1, CROP_2, CROP_3 -> 0.2f;
            case COAL_ORE -> 0.90f;
            case IRON_ORE, GOLD_ORE -> 1.00f;
            case DEEPSTONE -> 1.20f;
            case DOOR, DOOR_TOP -> 1.50f;
            case STAIRS -> 0.70f;
            case ROTATOR -> 0.0f;
            default -> 0.75f;
        };
    }
    public float breakTime(ItemType tool) {
        if(crop() && tool==ItemType.STONE_HOE)return breakTime()*.5f;
        if ((this == STONE || this == DEEPSTONE) && tool == ItemType.SHARP_STICK) {
            return breakTime() * 0.5f;
        }
        if (woodMaterial() && tool == ItemType.STONE_AXE) return breakTime() * 0.35f;
        if (woodMaterial() && (tool == ItemType.IRON_AXE || tool == ItemType.GOLD_AXE)) return breakTime() * 0.18f;
        if (stoneMaterial() && tool == ItemType.STONE_PICKAXE) return breakTime() * 0.35f;
        if (stoneMaterial() && (tool == ItemType.IRON_PICKAXE || tool == ItemType.GOLD_PICKAXE)) return breakTime() * 0.18f;
        if (earthMaterial() && tool == ItemType.STONE_SHOVEL) return breakTime() * 0.30f;
        if (earthMaterial() && (tool == ItemType.IRON_SHOVEL || tool == ItemType.GOLD_SHOVEL)) return breakTime() * 0.16f;
        return breakTime();
    }
}
