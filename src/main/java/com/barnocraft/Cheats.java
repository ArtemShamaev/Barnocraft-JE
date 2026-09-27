package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.Locale;

final class Cheats {
    private Cheats() {}

    static String execute(String line,Inventory inventory,World world,Vector3f player) {
        String[] args=line.trim().split("\\s+");
        if (args.length==0 || args[0].isBlank()) return "Введите команду";
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(args,inventory);
            case "spawn" -> spawn(args,world,player);
            case "kill" -> kill(args,inventory,world);
            case "keepinventory" -> keepInventory(args,world);
            case "time" -> time(args,world);
            case "setblock" -> setBlock(args,world);
            case "tp" -> teleport(args,world,player);
            default -> "Команды: give, spawn sheep, kill, keepInventory, time, setblock";
        };
    }

    private static String teleport(String[] args,World world,Vector3f player) {
        if(args.length!=2 || !args[1].equalsIgnoreCase("river")) return "Формат: tp river";
        int px=Math.round(player.x), pz=Math.round(player.z);
        for(int radius=1;radius<Math.max(world.width(),world.depth());radius++) {
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
                int x=px+dx,z=pz+dz;
                if(world.inside(x,World.HEIGHT-1,z) && world.get(x,Terrain.WATER_LEVEL,z)==Block.WATER) {
                    player.set(x+.5f,Terrain.WATER_LEVEL+.35f,z+.5f);
                    return "Телепортировано к реке";
                }
            }
        }
        return "Река не найдена";
    }

    private static String time(String[] args,World world) {
        if(args.length!=3 || !args[1].equalsIgnoreCase("set")) return "Формат: time set day|night";
        if(args[2].equalsIgnoreCase("day")) world.setDayTime(.35f);
        else if(args[2].equalsIgnoreCase("night")) world.setDayTime(.80f);
        else return "Формат: time set day|night";
        return "Время установлено: "+args[2].toLowerCase(Locale.ROOT);
    }

    private static String setBlock(String[] args,World world) {
        if(args.length!=5) return "Формат: setblock x y z block";
        try {
            int x=Integer.parseInt(args[1]),y=Integer.parseInt(args[2]),z=Integer.parseInt(args[3]);
            Block block=Block.valueOf(args[4].toUpperCase(Locale.ROOT));
            return world.set(x,y,z,block)?"Блок установлен":"Не удалось установить блок";
        } catch (IllegalArgumentException e) { return "Неизвестный блок или координаты"; }
    }

    private static String keepInventory(String[] args,World world) {
        if(args.length!=2 || !(args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
            return "Формат: keepInventory true|false";
        }
        world.keepInventory(Boolean.parseBoolean(args[1]));
        return "keepInventory = "+world.keepInventory();
    }

    private static String give(String[] args,Inventory inventory) {
        if (args.length<2) return "Формат: give <предмет> [количество]";
        ItemType type=findItem(args[1]);
        if (type==null) return "Неизвестный предмет: "+args[1];
        int amount=1;
        try { if(args.length>2) amount=Integer.parseInt(args[2]); }
        catch(NumberFormatException e) { return "Количество должно быть числом"; }
        if (amount<1 || amount>9999) return "Количество должно быть от 1 до 9999";
        int leftover=inventory.add(type,amount);
        return "Выдано "+(amount-leftover)+" × "+type.title+(leftover>0?"; инвентарь заполнен":"");
    }

    private static ItemType findItem(String value) {
        String normalized=value.toLowerCase(Locale.ROOT).replace("_","").replace("-","");
        for(ItemType type:ItemType.values()) {
            if(type.name().toLowerCase(Locale.ROOT).replace("_","").equals(normalized)
                    || type.title.toLowerCase(Locale.ROOT).replace(" ","").equals(normalized)) return type;
        }
        return null;
    }

    private static String spawn(String[] args,World world,Vector3f player) {
        if(args.length<2 || !(args[1].equalsIgnoreCase("sheep") || args[1].equalsIgnoreCase("овца"))) {
            return "Формат: spawn sheep [количество]";
        }
        int amount=1;
        try { if(args.length>2) amount=Integer.parseInt(args[2]); }
        catch(NumberFormatException e) { return "Количество должно быть числом"; }
        if(amount<1 || amount>100) return "Количество овец должно быть от 1 до 100";
        world.spawnSheepNear(player,amount);
        return "Создано овец: "+amount;
    }

    private static String kill(String[] args,Inventory inventory,World world) {
        if(args.length>1 && !(args[1].equalsIgnoreCase("sheep") || args[1].equalsIgnoreCase("овца"))) {
            return "Сейчас доступно: kill sheep";
        }
        return "Убито овец: "+world.killSheep(inventory);
    }
}
