package com.barnocraft;

import com.jme3.math.Vector3f;
import java.util.Locale;

final class Cheats {
    private Cheats() {}

    static String execute(String line,Inventory inventory,World world,Player player) {
        String command=line.trim();
        if(command.startsWith("/"))command=command.substring(1);
        String[] args=command.split("\\s+");
        if(args[0].equalsIgnoreCase("gamemode")) {
            if(args.length!=2 || !(args[1].equals("0") || args[1].equals("1")))return "Формат: gamemode 0|1";
            boolean creative=args[1].equals("1");
            player.setCreative(creative); inventory.setCreative(creative);
            return creative?"Креатив: E — предметы, Пробел / Shift — полёт":"Режим выживания";
        }
        String result=execute(command,inventory,world,player.position);
        if(args[0].equalsIgnoreCase("tp") && result.startsWith("Телепортировано")) {
            player.velocityY=0;player.grounded=false;
        }
        return result;
    }
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
            default -> "Команды: gamemode 0|1, give, spawn sheep, kill, keepInventory, time, setblock, tp river|portal";
        };
    }

    private static String teleport(String[] args,World world,Vector3f player) {
        if(args.length!=2) return "Формат: tp river|portal";
        if(args[1].equalsIgnoreCase("portal")) {
            Portal.Site nearest=null;double best=Double.POSITIVE_INFINITY;
            for(Portal.Site site:world.portals()) {
                double dx=site.x()+.5-player.x,dz=site.z()+.5-player.z,distance=dx*dx+dz*dz;
                if(distance<best){best=distance;nearest=site;}
            }
            if(nearest==null)return "Портал не найден";
            player.set(Portal.safeExit(world,nearest));
            return "Телепортировано к порталу";
        }
        if(!args[1].equalsIgnoreCase("river")) return "Формат: tp river|portal";
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
            if(type.retired()) continue;
            if(type.name().substring(type.name().indexOf(':')+1).toLowerCase(Locale.ROOT).replace("_","").equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).replace("_","").equals(normalized)
                    || type.title.toLowerCase(Locale.ROOT).replace(" ","").equals(normalized)) return type;
        }
        return null;
    }

    private static String spawn(String[] args,World world,Vector3f player) {
        if(args.length<2) return "Формат: spawn <тип> [количество]";
        if(!(args[1].equalsIgnoreCase("sheep") || args[1].equalsIgnoreCase("овца"))) {
            String type=world.mods.definitions.keySet().stream().filter(k->k.equals(args[1]) || k.endsWith(":"+args[1])).findFirst().orElse(null);
            if(type==null) return "Неизвестный тип существа";
            int count;try{count=args.length>2?Integer.parseInt(args[2]):1;}catch(NumberFormatException e){return "Количество должно быть числом";}
            if(count<1 || count>8)return "Количество должно быть от 1 до 8";
            int made=0;for(int i=0;i<count;i++)try{world.mods.spawn(type,player.x+3+i,player.z+2);made++;}catch(IllegalArgumentException ignored){}
            return "Создано: "+made;
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
