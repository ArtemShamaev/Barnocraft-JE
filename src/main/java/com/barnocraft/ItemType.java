package com.barnocraft;

import java.util.*;

/** Stable named registry; built-in constants and namespaced mod items share inventory/save code. */
final class ItemType {
    private static final Map<String,ItemType> REGISTRY=new LinkedHashMap<>();
    private static final Map<String,CraftRecipe> CRAFT_RECIPES=new LinkedHashMap<>();
    record CraftRecipe(Map<ItemType,Integer> ingredients,ItemType result,int count) {}
    static final ItemType PEBBLE=new ItemType("PEBBLE","Камушек","pebble.png");
    static final ItemType COAL=new ItemType("COAL","Уголь","coal.png");
    static final ItemType IRON=new ItemType("IRON","Железо","iron.png");
    static final ItemType IRON_INGOT=new ItemType("IRON_INGOT","Железный слиток","iron_ingot.png");
    static final ItemType MUTTON=new ItemType("MUTTON","Баранина","mutton.png");
    static final ItemType COOKED_MUTTON=new ItemType("COOKED_MUTTON","Жареная баранина","cooked_mutton.png");
    static final ItemType WOOL=new ItemType("WOOL","Шерсть","wool.png");
    static final ItemType TORCH=new ItemType("TORCH","Факел","torch.png");
    static final ItemType ROTTEN_FLESH=new ItemType("ROTTEN_FLESH","Гнилая плоть","mutton.png");
    static final ItemType WHEAT_SEEDS=new ItemType("WHEAT_SEEDS","Семена пшеницы","wheat_seeds.png");
    static final ItemType WHEAT=new ItemType("WHEAT","Пшеница","wheat.png");
    static final ItemType BREAD=new ItemType("BREAD","Хлеб","bread.png");
    static final ItemType STICK=new ItemType("STICK","Палка","stick.png");
    static final ItemType PLANK=new ItemType("PLANK","Доска","tree_planks.png");
    static final ItemType WOOD=new ItemType("WOOD","Дерево","tree.png");
    static final ItemType DIRT=new ItemType("DIRT","Земля","grass_2.png");
    static final ItemType SAND=new ItemType("SAND","Песок","pesok.png");
    static final ItemType STONE=new ItemType("STONE","Камень","stone.png");
    static final ItemType DEEPSTONE=new ItemType("DEEPSTONE","Глубокий камень","deeprock.png");
    static final ItemType GLASS=new ItemType("GLASS","Стекло","glass.png");
    static final ItemType DOOR=new ItemType("DOOR","Дверь","door.png");
    static final ItemType STAIRS=new ItemType("STAIRS","Ступеньки","stairs.png");
    static final ItemType STONE_BLADE=new ItemType("STONE_BLADE","Лезвие камнереза","stone_blade.png");
    static final ItemType SHARP_STICK=new ItemType("SHARP_STICK","Острая палка","sharp_stick.png");
    static final ItemType STONECUTTER=new ItemType("STONECUTTER","Камнерез","stonecutter.png");
    static final ItemType FURNACE=new ItemType("FURNACE","Печь","furnace.png");
    static final ItemType CHEST=new ItemType("CHEST","Сундук","chest.png");
    static final ItemType AXE_HEAD=new ItemType("AXE_HEAD","Навершие каменного топора","stone_axe_head.png");
    static final ItemType PICKAXE_HEAD=new ItemType("PICKAXE_HEAD","Навершие каменной кирки","stone_pickaxe_head.png");
    static final ItemType SHOVEL_HEAD=new ItemType("SHOVEL_HEAD","Навершие каменной лопаты","stone_shovel_head.png");
    static final ItemType IRON_AXE_HEAD=new ItemType("IRON_AXE_HEAD","Навершие железного топора","iron_axe_head.png");
    static final ItemType IRON_PICKAXE_HEAD=new ItemType("IRON_PICKAXE_HEAD","Навершие железной кирки","iron_pickaxe_head.png");
    static final ItemType IRON_SHOVEL_HEAD=new ItemType("IRON_SHOVEL_HEAD","Навершие железной лопаты","iron_shovel_head.png");
    static final ItemType STONE_AXE=new ItemType("STONE_AXE","Каменный топор","stone_axe.png");
    static final ItemType STONE_PICKAXE=new ItemType("STONE_PICKAXE","Каменная кирка","stone_pickaxe.png");
    static final ItemType STONE_SHOVEL=new ItemType("STONE_SHOVEL","Каменная лопата","stone_shovel.png");
    static final ItemType STONE_HOE=new ItemType("STONE_HOE","Каменная мотыга","stone_hoe.png");
    static final ItemType IRON_AXE=new ItemType("IRON_AXE","Железный топор","iron_axe.png");
    static final ItemType IRON_PICKAXE=new ItemType("IRON_PICKAXE","Железная кирка","iron_pickaxe.png");
    static final ItemType IRON_SHOVEL=new ItemType("IRON_SHOVEL","Железная лопата","iron_shovel.png");
    static final ItemType AXE=new ItemType("AXE","Топор","stone_axe.png");
    static final ItemType PICKAXE=new ItemType("PICKAXE","Кирка","stone_pickaxe.png");
    static final ItemType WATER=new ItemType("WATER","Вода","water.png");
    static final ItemType CASTING_TABLE=new ItemType("CASTING_TABLE","Стол для выплавки","casting_table.png");
    static final ItemType WELDER=new ItemType("WELDER","Сварочный аппарат","welder.png");
    static final ItemType BUCKET_MOLD=new ItemType("BUCKET_MOLD","Литейная форма ведра","bucket_mold.png");
    static final ItemType AXE_MOLD=new ItemType("AXE_MOLD","Литейная форма топора","axe_mold.png");
    static final ItemType PICKAXE_MOLD=new ItemType("PICKAXE_MOLD","Литейная форма кирки","pickaxe_mold.png");
    static final ItemType SHOVEL_MOLD=new ItemType("SHOVEL_MOLD","Литейная форма лопаты","shovel_mold.png");
    static final ItemType BUCKET=new ItemType("BUCKET","Ведро","bucket.png");
    static final ItemType WATER_BUCKET=new ItemType("WATER_BUCKET","Ведро воды","water_bucket.png");
    static final ItemType LANTERN=new ItemType("LANTERN","Фонарь","lantern.png");
    static final ItemType BED=new ItemType("BED","Кровать","bed.png");
    static final ItemType GOLD=new ItemType("GOLD","Золото","gold.png");
    static final ItemType GOLD_INGOT=new ItemType("GOLD_INGOT","Золотой слиток","gold_ingot.png");
    static final ItemType GOLD_AXE_HEAD=new ItemType("GOLD_AXE_HEAD","Навершие золотого топора","gold_axe_head.png");
    static final ItemType GOLD_PICKAXE_HEAD=new ItemType("GOLD_PICKAXE_HEAD","Навершие золотой кирки","gold_pickaxe_head.png");
    static final ItemType GOLD_SHOVEL_HEAD=new ItemType("GOLD_SHOVEL_HEAD","Навершие золотой лопаты","gold_shovel_head.png");
    static final ItemType GOLD_AXE=new ItemType("GOLD_AXE","Золотой топор","gold_axe.png");
    static final ItemType GOLD_PICKAXE=new ItemType("GOLD_PICKAXE","Золотая кирка","gold_pickaxe.png");
    static final ItemType GOLD_SHOVEL=new ItemType("GOLD_SHOVEL","Золотая лопата","gold_shovel.png");
    static final ItemType ROTATOR=new ItemType("ROTATOR","Поворачиватель","rotator.png");
    static final ItemType HELL_SOIL=new ItemType("HELL_SOIL","Пепельная почва","hell_soil.png");
    static final ItemType HELL_STONE=new ItemType("HELL_STONE","Адский камень","hell_stone.png");
    static final ItemType HELL_BASALT=new ItemType("HELL_BASALT","Базальт","hell_basalt.png");
    static final ItemType HELL_LOG=new ItemType("HELL_LOG","Багровый ствол","hell_log.png");
    static final ItemType HELL_LEAVES=new ItemType("HELL_LEAVES","Багровая листва","hell_leaves.png");
    static final ItemType EMBER_CRYSTAL=new ItemType("EMBER_CRYSTAL","Жар-кристалл","ember_crystal.png");
    String title, texture;
    private final String id;
    private int customDurability;
    Map<String,Object> definition=Map.of();
    private ItemType(String id,String title,String texture) { this.id=id;this.title=title;this.texture=texture;REGISTRY.put(id,this); }
    String name() { return id; }
    @Override public String toString() { return id; }
    static synchronized ItemType[] values() { return REGISTRY.values().toArray(ItemType[]::new); }
    static synchronized ItemType valueOf(String id) {
        ItemType item=REGISTRY.get(id);
        if(item==null && id.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_]*")) {
            item=new ItemType(id,id,"mod/"+id); item.customDurability=1000000;
        }
        if(item==null) throw new IllegalArgumentException("Unknown item: "+id);
        return item;
    }
    static synchronized ItemType register(String id,Map<String,Object> definition) {
        if(!id.contains(":")) throw new IllegalArgumentException("Mod items need a namespace");
        ItemType item=valueOf(id); item.title=Json.str(definition,"title",id);
        item.texture="mod/"+id;item.customDurability=Json.integer(definition,"durability",0,0,10000);
        item.definition=Map.copyOf(definition); return item;
    }
    static synchronized void registerRecipe(String namespace,Map<String,Object> definition) {
        String resultId=ModWorld.owned(namespace,Json.str(definition,"result",""));
        ItemType result=valueOf(resultId);if(result.definition.isEmpty())throw new IllegalArgumentException("Unregistered recipe result: "+resultId);
        Map<ItemType,Integer> ingredients=new LinkedHashMap<>();
        for(var entry:Json.obj(definition.get("ingredients")).entrySet()) {
            ItemType ingredient=valueOf(entry.getKey());
            if(entry.getKey().contains(":") && ingredient.definition.isEmpty())throw new IllegalArgumentException("Unregistered recipe ingredient: "+entry.getKey());
            int count=((Number)entry.getValue()).intValue();if(count<1 || count>64)throw new IllegalArgumentException("Recipe ingredient count");
            ingredients.put(ingredient,count);
        }
        if(ingredients.isEmpty())throw new IllegalArgumentException("Recipe needs ingredients");
        int count=Json.integer(definition,"count",1,1,64);
        CRAFT_RECIPES.put(resultId,new CraftRecipe(Map.copyOf(ingredients),result,count));
    }
    static synchronized List<CraftRecipe> craftRecipes() { return List.copyOf(CRAFT_RECIPES.values()); }

    boolean retired() { return this==CASTING_TABLE || this==WELDER || mold(); }
    int foodValue() {
        if(definition.containsKey("food"))return Json.integer(definition,"food",0,0,20);
        return this==MUTTON?3:this==COOKED_MUTTON?6:this==BREAD?2:0;
    }
    int nutrition() {
        if(definition.containsKey("hunger"))return Json.integer(definition,"hunger",0,0,20);
        return this==MUTTON?5:this==COOKED_MUTTON?8:this==ROTTEN_FLESH?2:this==BREAD?7:0;
    }
    String cookingResult() {
        if(this==MUTTON)return COOKED_MUTTON.name();
        return Json.str(definition,"cooking_result","");
    }
    boolean cookingProduct() {
        if(this==IRON_INGOT || this==GOLD_INGOT || this==GLASS || this==COOKED_MUTTON
            || Boolean.TRUE.equals(definition.get("cooked")))return true;
        synchronized(ItemType.class){return REGISTRY.values().stream().anyMatch(item->item.cookingResult().equals(name()));}
    }
    int stackLimit() { return tool() || mold() || this==BUCKET || this==WATER_BUCKET ? 1 : Inventory.MAX_STACK; }
    boolean mold() { return this==BUCKET_MOLD || this==AXE_MOLD || this==PICKAXE_MOLD || this==SHOVEL_MOLD; }

    boolean tool() {
        if(id.contains(":")) return customDurability>0;
        return switch (id) {
            case "SHARP_STICK", "STONE_AXE", "STONE_PICKAXE", "STONE_SHOVEL", "STONE_HOE", "IRON_AXE", "IRON_PICKAXE", "IRON_SHOVEL", "GOLD_AXE", "GOLD_PICKAXE", "GOLD_SHOVEL", "ROTATOR" -> true;
            default -> false;
        };
    }

    int maxDurability() {
        if(id.contains(":")) return customDurability;
        return switch (id) {
            case "BUCKET_MOLD", "AXE_MOLD", "PICKAXE_MOLD", "SHOVEL_MOLD" -> 32;
            case "SHARP_STICK" -> 20;
            case "STONE_AXE", "STONE_PICKAXE", "STONE_SHOVEL", "STONE_HOE" -> 80;
            case "IRON_AXE", "IRON_PICKAXE", "IRON_SHOVEL", "GOLD_AXE", "GOLD_PICKAXE", "GOLD_SHOVEL", "ROTATOR" -> 250;
            default -> 0;
        };
    }
}
