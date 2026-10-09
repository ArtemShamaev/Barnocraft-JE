package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CreativeTest {
    @Test void commandsSwitchProtectionFlightAndConsumptionBackToSurvival() {
        World world=World.flat();Player player=new Player();Inventory inventory=new Inventory();
        player.position.set(20,12,20);
        assertTrue(Cheats.execute("gamemode 1",inventory,world,player).startsWith("Креатив"));
        inventory.creativePick(0,ItemType.PLANK);
        int amount=inventory.countAt(0);
        assertTrue(inventory.consumeAt(0,ItemType.PLANK));assertEquals(amount,inventory.countAt(0));
        inventory.creativePick(1,ItemType.STONE_PICKAXE);
        int durability=inventory.durabilityAt(1);
        inventory.damageToolAt(1);assertEquals(durability,inventory.durabilityAt(1));
        assertFalse(player.damage(100));player.tickHunger(1000);
        assertEquals(Player.MAX_HEALTH,player.health());assertEquals(Player.MAX_HUNGER,player.hunger());
        player.update(world,.1f,new Vector3f(0,1,0),false);assertEquals(13,player.position.y,.01);
        player.update(world,.1f,Vector3f.ZERO,false);assertEquals(13,player.position.y,.01);
        player.update(world,.1f,new Vector3f(0,-1,0),false);assertEquals(12,player.position.y,.01);
        Cheats.execute("gamemode 0",inventory,world,player);
        assertFalse(player.creative());assertTrue(player.damage(1));
        inventory.consumeAt(0,ItemType.PLANK);assertEquals(amount-1,inventory.countAt(0));
        inventory.damageToolAt(1);assertEquals(durability-1,inventory.durabilityAt(1));
        player.update(world,.1f,Vector3f.ZERO,false);assertTrue(player.position.y<12);
    }
    @Test void invalidModeDoesNotChangeModeOrInventory() {
        var world=World.flat();var player=new Player();var inventory=new Inventory();
        for(String text:new String[]{"gamemode", "gamemode 2", "gamemode 1 extra", "gamemode -1"})
            assertEquals("Формат: gamemode 0|1",Cheats.execute(text,inventory,world,player));
        assertFalse(player.creative());inventory.creativePick(0,ItemType.PLANK);assertNull(inventory.itemAt(0));
        Cheats.execute("/GaMeMoDe 1",inventory,world,player);assertTrue(player.creative());
    }
    @Test void creativeFlightStillCollidesWithWalls() {
        var world=World.flat();var player=new Player();player.setCreative(true);player.position.set(20.5f,10,20.5f);
        world.set(21,10,20,Block.STONE);world.set(21,11,20,Block.STONE);
        player.update(world,.2f,new Vector3f(1,0,0),false);
        assertTrue(player.position.x<=20.701f);assertFalse(player.collides(world));
    }
    @Test void modeSurvivesSaveAndLoad(@TempDir Path directory) throws Exception {
        SaveStore saves=new SaveStore(directory);World world=new World(42);
        for(boolean creative:new boolean[]{true,false}) {
            var pose=new SaveStore.Pose(40,80,40,0,0,0,20,20,-1,-1,-1,creative);
            saves.save(1,world.snapshot(),pose);assertEquals(pose,saves.load(1).pose());
        }
    }
}
