package com.barnocraft;

import java.util.EnumMap;

/** Survival inventory and the small 2x2 crafting rules. */
final class Inventory {
    private final EnumMap<ItemType,Integer> items=new EnumMap<>(ItemType.class);
    int count(ItemType type) { return items.getOrDefault(type,0); }
    void add(ItemType type) { items.merge(type,1,Integer::sum); }
    boolean take(ItemType type,int amount) { if(count(type)<amount)return false; items.put(type,count(type)-amount); return true; }
    boolean craft(ItemType result) {
        if (result==ItemType.STONE_BLADE && take(ItemType.PEBBLE,4)) { add(result); return true; }
        if (result==ItemType.PLANK && take(ItemType.STICK,4)) { add(result); return true; }
        if (result==ItemType.STONECUTTER && take(ItemType.STONE_BLADE,1) && take(ItemType.PLANK,2)) { add(result); return true; }
        if (result==ItemType.AXE_HEAD && has(ItemType.STONECUTTER) && take(ItemType.STONE,5)) { add(result); return true; }
        if (result==ItemType.PICKAXE_HEAD && has(ItemType.STONECUTTER) && take(ItemType.STONE,5)) { add(result); return true; }
        if (result==ItemType.AXE && take(ItemType.STICK,1) && take(ItemType.AXE_HEAD,1)) { add(result); return true; }
        if (result==ItemType.PICKAXE && take(ItemType.STICK,1) && take(ItemType.PICKAXE_HEAD,1)) { add(result); return true; }
        return false;
    }
    boolean has(ItemType type) { return count(type)>0; }
    void clear() { items.clear(); }
}
