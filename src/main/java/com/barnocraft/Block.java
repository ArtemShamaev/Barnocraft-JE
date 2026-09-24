package com.barnocraft;

public enum Block {
    AIR(null), GRASS("grass_2.png"), STONE("stone.png"), SAND("pesok.png"),
    PLANKS("tree_planks.png"), GLASS("glass.png"), LOG("tree.png"), LEAVES("listia.png"), COAL_ORE("coal.png"), IRON_ORE("iron.png"), DEEPSTONE("deeprock.png"), DOOR("door.png"), DOOR_TOP("door.png"), STAIRS("stairs.png"), ROTATOR("rotator.png");

    public static final Block[] HOTBAR = {GRASS, STONE, SAND, PLANKS, GLASS, LOG, DEEPSTONE, DOOR, STAIRS, ROTATOR};
    public final String texture;

    Block(String texture) { this.texture = texture; }
    public boolean door() { return this == DOOR || this == DOOR_TOP; }
    public boolean rock() { return this == STONE || this == DEEPSTONE; }
    public boolean solid() { return this != AIR && this != ROTATOR; }
}
