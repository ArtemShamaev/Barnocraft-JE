package com.barnocraft;

record DroppedItem(ItemType type,int x,int y,int z,int durability) {
    DroppedItem(ItemType type,int x,int y,int z) { this(type,x,y,z,type.maxDurability()); }
}
