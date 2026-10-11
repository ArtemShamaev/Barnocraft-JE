package com.barnocraft;

import com.jme3.math.*;

/** Guest world interactions run serially on the host, including shared container transfers. */
final class NetworkGameplay {
    record Command(String kind,int x,int y,int z,int a,int b,boolean one,String value) {
        static Command of(String kind) { return new Command(kind,0,0,0,0,0,false,""); }
    }
    record Reply(String screen,int x,int y,int z,String notice) {
        static Reply none() { return new Reply("",0,0,0,""); }
    }
    static Reply execute(World world,NetworkPlayer pose,Inventory inv,int selected,boolean creative,Command command) {
        Player player=new Player();player.position.set(pose.position());player.setCreative(creative);inv.setCreative(creative);
        Vector3f origin=pose.position().add(0,Player.EYE,0),direction=pose.direction();
        String kind=command.kind();
        if(kind.equals("collect")) { world.collectIntoInventory(player.position,1.25f,inv);return Reply.none(); }
        if(kind.equals("attack")) {
            if(world.attackHellMob(origin,direction,Player.REACH,inv) || world.attackSheep(origin,direction,Player.REACH,inv) || world.attackZombie(origin,direction,Player.REACH))inv.damageToolAt(selected);
            return Reply.none();
        }
        if(kind.startsWith("container"))return container(world,player,inv,command);
        World.Hit hit=world.raycast(origin,direction,Player.REACH);
        ItemType held=inv.hotbarItemAt(selected);Block placed=Inventory.toBlock(held);
        if(kind.equals("use") && (held==ItemType.BUCKET || held==ItemType.WATER_BUCKET)) {
            world.useBucket(inv,selected,origin,direction);return Reply.none();
        }
        if(hit==null)return Reply.none();
        int x=hit.x(),y=hit.y(),z=hit.z();Block block=world.get(x,y,z);
        if(kind.equals("mine")) {
            if(block.immutable())return Reply.none();
            if(!block.solid() && !block.crop() && block!=Block.LANTERN && block!=Block.TORCH)return Reply.none();
            if(block.crop() && !creative)world.harvestCrop(x,y,z,inv);
            else if(world.set(x,y,z,Block.AIR) && !creative) { inv.addBlock(block);inv.damageToolAt(selected); }
            return Reply.none();
        }
        if(!kind.equals("use"))return Reply.none();
        if(placed==Block.ROTATOR) { if(world.rotate(x,y,z,player))inv.damageToolAt(selected);return Reply.none(); }
        if(block==Block.CHEST || block==Block.FURNACE || block==Block.STONECUTTER) {
            if(block==Block.CHEST && !world.openChest(x,y,z))return new Reply("",0,0,0,"Этот сундук уже открывали");
            return new Reply(block.name(),x,y,z,"");
        }
        if(block.bed())return new Reply("BED",x,y,z,world.sleepAt(x,y,z));
        if(held==ItemType.STONE_HOE && block==Block.GRASS) { if(world.till(x,y,z))inv.damageToolAt(selected);return Reply.none(); }
        if(held==ItemType.WHEAT_SEEDS && block==Block.FARMLAND) { if(world.plantCrop(x,y,z))inv.consumeAt(selected,held);return Reply.none(); }
        if(world.doorAt(x,y,z)!=null) { world.toggleDoor(x,y,z,player);return Reply.none(); }
        x+=hit.nx();y+=hit.ny();z+=hit.nz();
        if(placed==null || placed==Block.TORCH || held==null)return Reply.none();
        boolean success;
        if(placed==Block.DOOR)success=world.placeDoor(x,y,z,Math.floorMod(Math.round(pose.yaw()/FastMath.HALF_PI),4),player);
        else if(placed==Block.BED)success=world.placeBed(x,y,z,Math.floorMod(Math.round(-pose.yaw()/FastMath.HALF_PI),4),player);
        else if(placed==Block.STAIRS)success=world.placeStairs(x,y,z,Math.floorMod(Math.round(-pose.yaw()/FastMath.HALF_PI),4),player);
        else success=!player.intersects(x,y,z) && world.get(x,y,z)==Block.AIR
                && (placed!=Block.LANTERN || world.get(x,y-1,z).solid()) && world.set(x,y,z,placed);
        if(success) {
            if(placed==Block.FURNACE)for(int i=0;i<Math.floorMod(Math.round(-pose.yaw()/FastMath.HALF_PI),4);i++)world.rotate(x,y,z,player);
            inv.consumeAt(selected,held);
        }
        return Reply.none();
    }
    private static Reply container(World world,Player player,Inventory inv,Command c) {
        int x=c.x(),y=c.y(),z=c.z();
        if(!world.inside(x,y,z) || player.position.distanceSquared(new Vector3f(x+.5f,y+.5f,z+.5f))>49)return Reply.none();
        Block block=world.get(x,y,z);int slot=c.a(),index=c.b();
        if(index<0 || index>=Inventory.SLOT_COUNT)return Reply.none();
        switch(c.kind()) {
            case "container-close" -> { if(block==Block.CHEST)world.closeChest(x,y,z); }
            case "container-chest-take", "container-chest-put" -> {
                if(block==Block.CHEST && slot>=0 && slot<27)world.moveChestItem(x,y,z,slot,inv,index,c.kind().endsWith("take"),c.one());
            }
            case "container-recipe" -> { if(block==Block.STONECUTTER)world.selectStonecutterRecipe(x,y,z,ItemType.valueOf(c.value())); }
            case "container-craft" -> { if(block==Block.STONECUTTER)world.craftStonecutterHead(x,y,z); }
            case "container-take", "container-put" -> {
                if(slot<0 || slot>2)return Reply.none();
                boolean take=c.kind().endsWith("take");
                for(int i=0;i<(c.one()?1:Inventory.MAX_STACK);i++) {
                    boolean moved=block==Block.FURNACE?(take?world.takeFurnaceItem(x,y,z,slot,inv,index):world.putFurnaceItem(x,y,z,slot,inv,index))
                        :block==Block.STONECUTTER && (take?world.takeStonecutterItem(x,y,z,slot,inv,index):world.putStonecutterItem(x,y,z,slot,inv,index));
                    if(!moved)break;
                }
            }
            default -> { }
        }
        return Reply.none();
    }
}
