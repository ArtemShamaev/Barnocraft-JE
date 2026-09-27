package com.barnocraft;

import java.util.*;

/** Immutable station inventories and atomic recipes, independent of the GUI. */
final class Metalworking {
    static final int FUEL=9,OUTPUT=10;
    static final Inventory.Slot EMPTY=new Inventory.Slot(null,0);
    static final List<ItemType> CASTING_RECIPES=List.of(ItemType.BUCKET_MOLD,ItemType.AXE_MOLD,
            ItemType.PICKAXE_MOLD,ItemType.SHOVEL_MOLD);
    record State(Block block,List<Inventory.Slot> slots,ItemType recipe) {
        State {
            slots=List.copyOf(slots);
            if((block!=Block.CASTING_TABLE && block!=Block.WELDER) || slots.size()!=11
                    || !CASTING_RECIPES.contains(recipe)) throw new IllegalArgumentException("Invalid workshop");
            Inventory.validate(slots);
            for(int i=0;i<OUTPUT;i++) if(slots.get(i).item()!=null && !accepts(block,i,slots.get(i).item()))
                throw new IllegalArgumentException("Invalid workshop ingredient");
            ItemType output=slots.get(OUTPUT).item();
            if(output!=null && !(block==Block.CASTING_TABLE?CASTING_RECIPES.contains(output)
                    :output==ItemType.BUCKET || moldFor(output)!=null || output==ItemType.IRON_AXE || output==ItemType.IRON_PICKAXE || output==ItemType.IRON_SHOVEL))
                throw new IllegalArgumentException("Invalid workshop output");
        }
        static State empty(Block block) { return new State(block,Collections.nCopies(11,EMPTY),ItemType.BUCKET_MOLD); }
        Inventory.Slot at(int slot) { return slot>=0 && slot<slots.size()?slots.get(slot):null; }
        State with(int index,Inventory.Slot slot) {
            List<Inventory.Slot> copy=new ArrayList<>(slots); copy.set(index,slot); return new State(block,copy,recipe);
        }
    }
    static boolean accepts(Block block,int slot,ItemType item) {
        if(item==null || slot<0 || slot>=OUTPUT) return false;
        if(block==Block.WELDER) return slot<9 && (item==ItemType.COAL || item==ItemType.IRON_INGOT
                || item==ItemType.STICK || item.mold() || moldFor(item)!=null);
        return slot==FUEL?item==ItemType.COAL:slot==0 && item==ItemType.IRON;
    }
    static ItemType moldFor(ItemType head) {
        return switch(head) {
            case IRON_AXE_HEAD -> ItemType.AXE_MOLD;
            case IRON_PICKAXE_HEAD -> ItemType.PICKAXE_MOLD;
            case IRON_SHOVEL_HEAD -> ItemType.SHOVEL_MOLD;
            default -> null;
        };
    }
    static ItemType preview(State state) {
        if(state.block()==Block.CASTING_TABLE) {
            return state.at(FUEL).count()>=1 && state.at(0).item()==ItemType.IRON && state.at(0).count()>=3?state.recipe():null;
        }
        // Each occupied grid cell contributes one item; stacked items never replace separate cells.
        EnumMap<ItemType,Integer> counts=new EnumMap<>(ItemType.class);
        for(int i=0;i<9;i++) if(state.at(i).item()!=null) counts.merge(state.at(i).item(),1,Integer::sum);
        if(counts.equals(Map.of(ItemType.BUCKET_MOLD,3,ItemType.IRON_INGOT,3,ItemType.COAL,1))) return ItemType.BUCKET;
        for(ItemType head:List.of(ItemType.IRON_AXE_HEAD,ItemType.IRON_PICKAXE_HEAD,ItemType.IRON_SHOVEL_HEAD)) {
            if(counts.equals(Map.of(moldFor(head),3,ItemType.IRON_INGOT,3,ItemType.COAL,1))) return head;
            if(counts.equals(Map.of(head,1,ItemType.STICK,1))) return switch(head) {
                case IRON_AXE_HEAD -> ItemType.IRON_AXE;
                case IRON_PICKAXE_HEAD -> ItemType.IRON_PICKAXE;
                default -> ItemType.IRON_SHOVEL;
            };
        }
        return null;
    }
    static State craft(State state) {
        ItemType result=preview(state); Inventory.Slot output=state.at(OUTPUT);
        if(result==null || output.item()!=null && output.item()!=result || output.count()>=result.stackLimit()) return null;
        List<Inventory.Slot> slots=new ArrayList<>(state.slots());
        if(state.block()==Block.CASTING_TABLE) {
            slots.set(0,decrement(slots.get(0),3));
            slots.set(FUEL,decrement(slots.get(FUEL),1));
        } else {
            for(int i=0;i<9;i++) if(slots.get(i).item()!=null)
                slots.set(i,slots.get(i).item().mold()?wear(slots.get(i)):decrement(slots.get(i),1));
        }
        slots.set(OUTPUT,new Inventory.Slot(result,output.count()+1));
        return new State(state.block(),slots,state.recipe());
    }
    private static Inventory.Slot wear(Inventory.Slot mold) {
        return mold.durability()<=1?EMPTY:new Inventory.Slot(mold.item(),1,mold.durability()-1);
    }
    static Inventory.Slot decrement(Inventory.Slot slot,int count) {
        return slot.count()==count?EMPTY:new Inventory.Slot(slot.item(),slot.count()-count,slot.durability());
    }
}
