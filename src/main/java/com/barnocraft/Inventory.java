package com.barnocraft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Survival inventory with stack limits and simple slot drag/drop. */
final class Inventory {
    static final int MAX_STACK = 64;
    static final int SLOT_COUNT = 20;
    static final int HOTBAR_SIZE = 10;
    static final int CRAFT_SIZE = 4;
    private final List<Slot> slots = new ArrayList<>(SLOT_COUNT);
    private final List<Slot> craft = new ArrayList<>(CRAFT_SIZE);
    private final java.util.Map<ItemType,Integer> lastLeftover = new java.util.HashMap<>();

    private boolean creative;
    boolean creative() { return creative; }
    void setCreative(boolean value) { creative=value; }
    void creativePick(int index,ItemType item) {
        if(!creative || index<0 || index>=HOTBAR_SIZE || item==null || item.retired())return;
        slots.set(index,new Slot(item,item.stackLimit()));
    }
    Inventory() {
        for (int i = 0; i < SLOT_COUNT; i++) slots.add(new Slot(null, 0));
        for (int i = 0; i < CRAFT_SIZE; i++) craft.add(new Slot(null, 0));
    }

    record Slot(ItemType item, int count, int durability) {
        Slot {
            if (count < 0) throw new IllegalArgumentException("Negative stack count");
            if (durability < 0 || durability > (item == null ? 0 : item.maxDurability())) {
                throw new IllegalArgumentException("Invalid item durability");
            }
        }
        Slot(ItemType item,int count) { this(item,count,item!=null && item.tool() && count==1?item.maxDurability():0); }
    }

    record Snapshot(List<Slot> slots, List<Slot> craft) {
        Snapshot {
            slots = List.copyOf(slots);
            craft = List.copyOf(craft);
            if (slots.size() != SLOT_COUNT || craft.size() != CRAFT_SIZE) {
                throw new IllegalArgumentException("Invalid inventory snapshot size");
            }
        }
    }

    Snapshot snapshot() { return new Snapshot(slots, craft); }

    void restore(Snapshot snapshot) {
        if (snapshot == null) snapshot = emptySnapshot();
        validate(snapshot.slots());
        validate(snapshot.craft());
        for (int i = 0; i < SLOT_COUNT; i++) slots.set(i, restoreSlot(snapshot.slots().get(i)));
        for (int i = 0; i < CRAFT_SIZE; i++) craft.set(i, restoreSlot(snapshot.craft().get(i)));
    }

    private static Slot restoreSlot(Slot slot) {
        return slot.item()!=null && slot.item().retired()?new Slot(ItemType.IRON_INGOT,slot.count()):slot;
    }
    static Snapshot emptySnapshot() {
        return new Snapshot(java.util.Collections.nCopies(SLOT_COUNT, new Slot(null, 0)),
                java.util.Collections.nCopies(CRAFT_SIZE, new Slot(null, 0)));
    }

    static void validate(List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot.count() > (slot.item()!=null && slot.item().tool()?1:MAX_STACK)
                    || (slot.item() == null) != (slot.count() == 0)
                    || (slot.item()==null && slot.durability()!=0)
                    || (slot.item()!=null && slot.item().tool() && slot.count()!=1 && slot.durability()!=0)) {
                throw new IllegalArgumentException("Invalid inventory stack");
            }
        }
    }

    int count(ItemType type) {
        int total = 0;
        for (Slot slot : slots) if (slot.item() == type) total += slot.count();
        return total;
    }

    int countAt(int index) {
        if (index < 0 || index >= slots.size()) return 0;
        return slots.get(index).count();
    }

    Slot slotAtIndex(int index) {
        return index < 0 || index >= slots.size() ? null : slots.get(index);
    }

    boolean consumeAt(int index, ItemType expected) {
        if (index < 0 || index >= slots.size()) return false;
        Slot slot = slots.get(index);
        if (expected == null || slot.item() != expected || slot.count() == 0) return false;
        if(creative)return true;
        slots.set(index, slot.count() == 1 ? new Slot(null, 0) : new Slot(expected, slot.count() - 1));
        return true;
    }
    boolean acceptSlotAt(int index, Slot source) {
        if(index<0 || index>=slots.size() || source==null || source.item()==null) return false;
        Slot current=slots.get(index);
        if(current.item()==null) { slots.set(index,source); return true; }
        if(current.item()!=source.item() || current.count()>=source.item().stackLimit()) return false;
        slots.set(index,new Slot(current.item(),current.count()+source.count(),current.durability())); return true;
    }
    boolean replaceAt(int index,ItemType expected,ItemType replacement) {
        if(index<0 || index>=slots.size() || slots.get(index).item()!=expected) return false;
        slots.set(index,new Slot(replacement,1)); return true;
    }

    int durabilityAt(int index) {
        Slot slot=slotAtIndex(index);
        return slot==null?0:slot.durability();
    }

    boolean damageToolAt(int index) {
        if(creative)return false;
        if (index<0 || index>=slots.size()) return false;
        Slot slot=slots.get(index);
        if (slot.item()==null || !slot.item().tool() || slot.durability()==0) return false;
        int remaining=slot.durability()-1;
        slots.set(index,remaining==0?new Slot(null,0):new Slot(slot.item(),slot.count(),remaining));
        return true;
    }

    boolean acceptAt(int index,ItemType item) {
        if (index<0 || index>=slots.size() || item==null) return false;
        Slot slot=slots.get(index);
        if (slot.item()!=null && slot.item()!=item || slot.count()>=MAX_STACK) return false;
        slots.set(index,new Slot(item,slot.count()+1));
        return true;
    }

    int hotbarCountAt(int index) {
        if (index < 0 || index >= HOTBAR_SIZE) return 0;
        return countAt(index);
    }

    int craftCountAt(int index) {
        if (index < 0 || index >= craft.size()) return 0;
        return craft.get(index).count();
    }

    ItemType itemAt(int index) {
        if (index < 0 || index >= slots.size()) return null;
        return slots.get(index).item();
    }

    ItemType hotbarItemAt(int index) {
        if (index < 0 || index >= HOTBAR_SIZE) return null;
        return itemAt(index);
    }

    ItemType craftItemAt(int index) {
        if (index < 0 || index >= craft.size()) return null;
        return craft.get(index).item();
    }

    Slot craftSlotAt(int index) {
        return index < 0 || index >= craft.size() ? null : craft.get(index);
    }

    static Block toBlock(ItemType type) {
        if (type == null) return null;
        return switch (type.name()) {
            case "HELL_SOIL" -> Block.HELL_SOIL;
            case "HELL_STONE" -> Block.HELL_STONE;
            case "HELL_BASALT" -> Block.HELL_BASALT;
            case "HELL_LOG" -> Block.HELL_LOG;
            case "HELL_LEAVES" -> Block.HELL_LEAVES;
            case "EMBER_CRYSTAL" -> Block.EMBER_CRYSTAL;
            case "DIRT" -> Block.GRASS;
            case "STONE" -> Block.STONE;
            case "SAND" -> Block.SAND;
            case "PLANK" -> Block.PLANKS;
            case "GLASS" -> Block.GLASS;
            case "WOOD" -> Block.LOG;
            case "COAL" -> Block.COAL_ORE;
            case "IRON" -> Block.IRON_ORE;
            case "GOLD" -> Block.GOLD_ORE;
            case "ROTATOR" -> Block.ROTATOR;
            case "DEEPSTONE" -> Block.DEEPSTONE;
            case "DOOR" -> Block.DOOR;
            case "STAIRS" -> Block.STAIRS;
            case "STONECUTTER" -> Block.STONECUTTER;
            case "FURNACE" -> Block.FURNACE;
            case "CHEST" -> Block.CHEST;
            case "LANTERN" -> Block.LANTERN;
            case "BED" -> Block.BED;
            case "WATER" -> Block.WATER;

            default -> null;
        };
    }

    int countLeftover(ItemType type) {
        return lastLeftover.getOrDefault(type, 0);
    }

    int add(ItemType type) {
        return add(type, 1);
    }

    int add(ItemType type, int amount) {
        if (type == null || amount <= 0) return 0;
        int remaining = amount;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            Slot slot = slots.get(i);
            int stackLimit=type.tool()?1:MAX_STACK;
            if (!type.tool() && slot.item() == type && slot.count() < stackLimit) {
                int canAdd = Math.min(stackLimit - slot.count(), remaining);
                slots.set(i, new Slot(type, slot.count() + canAdd));
                remaining -= canAdd;
            }
        }

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            Slot slot = slots.get(i);
            if (slot.item() == null) {
                int canAdd = Math.min(type.tool()?1:MAX_STACK, remaining);
                slots.set(i, new Slot(type, canAdd));
                remaining -= canAdd;
            }
        }

        lastLeftover.put(type, remaining);
        return remaining;
    }

    int addBlock(Block block) {
        return addBlock(block, 1);
    }

    int addBlock(Block block, int amount) {
        if (block == Block.LEAVES) {
            if (Math.random() < 0.10) add(ItemType.STICK, 1);
            if(Math.random()<.16)add(ItemType.WHEAT_SEEDS,1);
            return 0;
        }

        ItemType item = switch (block) {
            case HELL_SOIL -> ItemType.HELL_SOIL;
            case HELL_STONE -> ItemType.HELL_STONE;
            case HELL_BASALT -> ItemType.HELL_BASALT;
            case HELL_LOG -> ItemType.HELL_LOG;
            case HELL_LEAVES -> ItemType.HELL_LEAVES;
            case EMBER_CRYSTAL -> ItemType.EMBER_CRYSTAL;

            case GRASS -> ItemType.DIRT;
            case STONE -> ItemType.STONE;
            case SAND -> ItemType.SAND;
            case PLANKS -> ItemType.PLANK;
            case GLASS -> ItemType.GLASS;
            case LOG -> ItemType.WOOD;
            case COAL_ORE -> ItemType.COAL;
            case IRON_ORE -> ItemType.IRON;
            case GOLD_ORE -> ItemType.GOLD;
            case DEEPSTONE -> ItemType.DEEPSTONE;
            case DOOR -> ItemType.DOOR;
            case DOOR_TOP -> ItemType.DOOR;
            case STAIRS -> ItemType.STAIRS;
            case STONECUTTER -> ItemType.STONECUTTER;
            case FURNACE -> ItemType.FURNACE;
            case TORCH -> ItemType.TORCH;
            case LANTERN -> ItemType.LANTERN;
            case BED, BED_HEAD -> ItemType.BED;
            default -> null;
        };
        if (item == null) return amount;
        return add(item, amount);
    }

    boolean take(ItemType type, int amount) {
        if (type == null || amount <= 0 || count(type) < amount) return false;
        int remaining = amount;
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            Slot slot = slots.get(i);
            if (slot.item() != type) continue;
            int take = Math.min(slot.count(), remaining);
            if (take == slot.count()) {
                slots.set(i, new Slot(null, 0));
            } else {
                slots.set(i, new Slot(type, slot.count() - take));
            }
            remaining -= take;
        }
        return true;
    }

    ItemType craftPreview() {
        ItemType modRecipe=matchingModRecipe();if(modRecipe!=null)return modRecipe;
        int occupied = 0;
        for (Slot slot : craft) if (slot.item() != null) occupied++;
        if(occupied==4 && craft.stream().filter(slot->slot.item()==ItemType.PLANK).count()==3
            && craft.stream().anyMatch(slot->slot.item()==ItemType.STICK))return ItemType.CHEST;
        if(occupied==4 && craft.stream().filter(slot->slot.item()==ItemType.PLANK).count()==3
            && craft.stream().anyMatch(slot->slot.item()==ItemType.STICK))return ItemType.CHEST;
        if(occupied==3 && craft.stream().filter(slot->slot.item()==ItemType.WHEAT).count()==3)return ItemType.BREAD;
        if(occupied==4 && craft.stream().filter(slot->slot.item()==ItemType.PLANK).count()==2
            && craft.stream().filter(slot->slot.item()==ItemType.STICK).count()==2)return ItemType.STONE_HOE;
        int plankCount=craft.stream().filter(slot->slot.item()==ItemType.PLANK).mapToInt(Slot::count).sum();
        if(occupied<=2 && occupied>0 && occupied==craft.stream().filter(slot->slot.item()==ItemType.PLANK).count() && plankCount==2)return ItemType.STICK;
        if (occupied == 1 && craft.stream().anyMatch(slot -> slot.item() == ItemType.GOLD_INGOT)) return ItemType.ROTATOR;
        if (occupied == 1 && craft.stream().anyMatch(slot -> slot.item() == ItemType.STICK)) return ItemType.SHARP_STICK;
        if (occupied == 2 && craft.stream().anyMatch(slot -> slot.item()==ItemType.COAL)
            && craft.stream().anyMatch(slot -> slot.item()==ItemType.STICK)) return ItemType.TORCH;
        if (occupied == 1 && craft.stream().anyMatch(slot -> slot.item() == ItemType.WOOD)) return ItemType.PLANK;
        if (occupied == 4 && craft.stream().allMatch(slot -> slot.item() == ItemType.PLANK)) return ItemType.DOOR;
        if (occupied == 3 && craft.stream().filter(slot -> slot.item() == ItemType.PLANK).count()==3) return ItemType.STAIRS;
        if (occupied == 4 && craft.stream().allMatch(slot -> slot.item() == ItemType.STONE)) return ItemType.FURNACE;
        if (occupied == 2 && craft.stream().anyMatch(slot -> slot.item() == ItemType.STICK)) {
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.AXE_HEAD)) return ItemType.STONE_AXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.PICKAXE_HEAD)) return ItemType.STONE_PICKAXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.SHOVEL_HEAD)) return ItemType.STONE_SHOVEL;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.IRON_AXE_HEAD)) return ItemType.IRON_AXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.IRON_PICKAXE_HEAD)) return ItemType.IRON_PICKAXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.IRON_SHOVEL_HEAD)) return ItemType.IRON_SHOVEL;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.GOLD_AXE_HEAD)) return ItemType.GOLD_AXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.GOLD_PICKAXE_HEAD)) return ItemType.GOLD_PICKAXE;
            if (craft.stream().anyMatch(slot -> slot.item() == ItemType.GOLD_SHOVEL_HEAD)) return ItemType.GOLD_SHOVEL;
        }
        if (craft.get(0).item() == ItemType.STONE && craft.get(1).item() == ItemType.STONE
                && craft.get(2).item() == ItemType.PLANK && craft.get(3).item() == ItemType.PLANK) {
            return ItemType.STONECUTTER;
        }
        if (occupied==4 && craft.stream().filter(slot -> slot.item()==ItemType.WOOL).count()==2
                && craft.stream().filter(slot -> slot.item()==ItemType.PLANK).count()==2) return ItemType.BED;
        if (occupied==3 && craft.stream().anyMatch(slot -> slot.item()==ItemType.TORCH)
                && craft.stream().anyMatch(slot -> slot.item()==ItemType.GLASS)
                && craft.stream().anyMatch(slot -> slot.item()==ItemType.IRON_INGOT)) return ItemType.LANTERN;
        if (occupied==3 && craft.stream().filter(slot -> slot.item()==ItemType.IRON_INGOT).count()==3) return ItemType.BUCKET;
        return null;
    }

    boolean craftFromGrid() {
        ItemType result = craftPreview();
        if (result == null) return false;
        ItemType.CraftRecipe modRecipe=matchingModRecipeDefinition();
        int outputCount=modRecipe!=null?modRecipe.count():result==ItemType.PLANK?4:result==ItemType.STICK?16:1;
        if (!canAdd(result,outputCount)) return false;
        if(modRecipe!=null) {
            for(var ingredient:modRecipe.ingredients().entrySet()) {
                int remaining=ingredient.getValue();
                for(int i=0;i<craft.size() && remaining>0;i++) {
                    Slot slot=craft.get(i);if(slot.item()!=ingredient.getKey())continue;
                    int used=Math.min(remaining,slot.count());craft.set(i,slot.count()==used?new Slot(null,0):new Slot(slot.item(),slot.count()-used));remaining-=used;
                }
            }
            add(result,outputCount);return true;
        }
        if(result==ItemType.STICK) {
            int remaining=2;
            for(int i=0;i<craft.size() && remaining>0;i++)if(craft.get(i).item()==ItemType.PLANK) {
                Slot slot=craft.get(i);int used=Math.min(remaining,slot.count());
                craft.set(i,slot.count()==used?new Slot(null,0):new Slot(ItemType.PLANK,slot.count()-used));remaining-=used;
            }
            add(result,outputCount);return true;
        }
        for (int i = 0; i < craft.size(); i++) {
            Slot slot = craft.get(i);
            if (slot.item() == null) continue;
            craft.set(i, slot.count() == 1 ? new Slot(null, 0) : new Slot(slot.item(), slot.count() - 1));
        }
        add(result,outputCount);
        return true;
    }

    private ItemType matchingModRecipe() {
        ItemType.CraftRecipe recipe=matchingModRecipeDefinition();return recipe==null?null:recipe.result();
    }
    private ItemType.CraftRecipe matchingModRecipeDefinition() {
        for(ItemType.CraftRecipe recipe:ItemType.craftRecipes()) {
            java.util.Map<ItemType,Integer> present=new java.util.HashMap<>();int occupied=0;
            for(Slot slot:craft)if(slot.item()!=null){occupied++;present.merge(slot.item(),slot.count(),Integer::sum);}
            if(occupied==recipe.ingredients().size() && present.equals(recipe.ingredients()))return recipe;
        }
        return null;
    }
    int craftPreviewCount() {
        ItemType result=craftPreview();if(result==null)return 0;
        ItemType.CraftRecipe recipe=matchingModRecipeDefinition();
        return recipe!=null?recipe.count():result==ItemType.PLANK?4:result==ItemType.STICK?16:1;
    }

    private boolean canAdd(ItemType type,int amount) {
        int capacity = 0;
        for (Slot slot : slots) {
            int stackLimit=type.tool()?1:MAX_STACK;
            capacity += !type.tool() && slot.item() == type ? stackLimit - slot.count() : slot.item() == null ? stackLimit : 0;
        }
        return capacity >= amount;
    }

    boolean craft(ItemType result) {
        if (result == craftPreview()) return craftFromGrid();
        if (result == ItemType.STONE_AXE && take(ItemType.AXE_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.STONE_PICKAXE && take(ItemType.PICKAXE_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.STONE_SHOVEL && take(ItemType.SHOVEL_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.IRON_AXE && take(ItemType.IRON_AXE_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.IRON_PICKAXE && take(ItemType.IRON_PICKAXE_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.IRON_SHOVEL && take(ItemType.IRON_SHOVEL_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.PICKAXE && take(ItemType.PICKAXE_HEAD, 1) && take(ItemType.STICK, 1)) {
            add(result); return true;
        }
        if (result == ItemType.AXE_HEAD && has(ItemType.STONECUTTER) && take(ItemType.STONE, 5)) {
            add(result); return true;
        }
        if (result == ItemType.PICKAXE_HEAD && has(ItemType.STONECUTTER) && take(ItemType.STONE, 5)) {
            add(result); return true;
        }
        if (result == ItemType.AXE && take(ItemType.STICK, 1) && take(ItemType.AXE_HEAD, 1)) {
            add(result); return true;
        }
        if (result == ItemType.PICKAXE && take(ItemType.STICK, 1) && take(ItemType.PICKAXE_HEAD, 1)) {
            add(result); return true;
        }
        return false;
    }

    boolean has(ItemType type) {
        return count(type) > 0;
    }

    boolean moveSlot(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= slots.size() || toIndex >= slots.size()) return false;
        if (fromIndex == toIndex) return true;
        Slot from = slots.get(fromIndex);
        Slot to = slots.get(toIndex);
        if (from.item() == null && to.item() == null) return true;

        if (to.item() == null) {
            slots.set(toIndex, from);
            slots.set(fromIndex, new Slot(null, 0));
            return true;
        }

        if (from.item() == to.item() && from.item() != null && !from.item().tool()) {
            int space = MAX_STACK - to.count();
            if (space <= 0) return false;
            int moved = Math.min(space, from.count());
            slots.set(toIndex, new Slot(to.item(), to.count() + moved));
            int remaining = from.count() - moved;
            slots.set(fromIndex, remaining > 0 ? new Slot(from.item(), remaining) : new Slot(null, 0));
            return true;
        }

        slots.set(fromIndex, to);
        slots.set(toIndex, from);
        return true;
    }

    boolean moveRef(String fromRef, String toRef) {
        return moveRef(fromRef,toRef,false);
    }

    boolean moveRef(String fromRef, String toRef, boolean one) {
        if (fromRef == null || toRef == null || fromRef.equals(toRef)) return true;
        Slot from = slotAt(fromRef);
        Slot to = slotAt(toRef);
        if (from == null || to == null || from.item() == null) return false;

        if (one) {
            if (to.item()!=null && to.item()!=from.item()) return false;
            if (to.count()>=MAX_STACK) return false;
            setSlotAt(toRef,new Slot(from.item(),to.count()+1));
            setSlotAt(fromRef,from.count()==1?new Slot(null,0):new Slot(from.item(),from.count()-1,from.durability()));
            return true;
        }

        if (toRef.startsWith("craft:") && (fromRef.startsWith("slot:") || fromRef.startsWith("hotbar:"))
                && from.count() > 1 && (to.item() == null || to.item() == from.item())
                && to.count() < MAX_STACK) {
            setSlotAt(toRef, new Slot(from.item(), to.count() + 1));
            setSlotAt(fromRef, new Slot(from.item(), from.count() - 1));
            return true;
        }

        if (to.item() == null) {
            setSlotAt(toRef, from);
            clearSlotAt(fromRef);
            return true;
        }

        if (from.item() == to.item() && !from.item().tool()) {
            int space = MAX_STACK - to.count();
            if (space <= 0) return false;
            int moved = Math.min(space, from.count());
            setSlotAt(toRef, new Slot(to.item(), to.count() + moved));
            int remaining = from.count() - moved;
            if (remaining > 0) setSlotAt(fromRef, new Slot(from.item(), remaining));
            else clearSlotAt(fromRef);
            return true;
        }

        setSlotAt(fromRef, to);
        setSlotAt(toRef, from);
        return true;
    }

    boolean hasItemRef(String ref) {
        Slot slot = slotAt(ref);
        return slot != null && slot.item() != null;
    }

    private Slot slotAt(String ref) {
        if (ref == null || ref.isBlank()) return null;
        int colon = ref.indexOf(':');
        if (colon < 0 || colon == ref.length() - 1) return null;
        String kind = ref.substring(0, colon);
        int index = Integer.parseInt(ref.substring(colon + 1));
        return switch (kind) {
            case "slot" -> index >= 0 && index < slots.size() ? slots.get(index) : null;
            case "hotbar" -> index >= 0 && index < HOTBAR_SIZE ? slots.get(index) : null;
            case "craft" -> index >= 0 && index < craft.size() ? craft.get(index) : null;
            default -> null;
        };
    }

    private void setSlotAt(String ref, Slot value) {
        if (ref == null || ref.isBlank()) return;
        int colon = ref.indexOf(':');
        if (colon < 0 || colon == ref.length() - 1) return;
        String kind = ref.substring(0, colon);
        int index = Integer.parseInt(ref.substring(colon + 1));
        switch (kind) {
            case "slot" -> {
                if (index >= 0 && index < slots.size()) slots.set(index, value);
            }
            case "hotbar" -> {
                if (index >= 0 && index < HOTBAR_SIZE) slots.set(index, value);
            }
            case "craft" -> {
                if (index >= 0 && index < craft.size()) craft.set(index, value);
            }
            default -> { }
        }
    }

    private void clearSlotAt(String ref) {
        setSlotAt(ref, new Slot(null, 0));
    }

    void clear() {
        for (int i = 0; i < slots.size(); i++) slots.set(i, new Slot(null, 0));
        for (int i = 0; i < craft.size(); i++) craft.set(i, new Slot(null, 0));
        lastLeftover.clear();
    }
}
