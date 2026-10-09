package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModApiTest {
    @TempDir Path directory;
    @Test void packagedAndIdeLaunchesUseRootModsEvenWhenTargetModsExists() throws Exception {
        Path root=directory.resolve("game");Files.createDirectories(root.resolve("target/classes"));
        Files.createDirectories(root.resolve("target/mods"));Files.createDirectories(root.resolve("mods"));
        assertEquals(root.resolve("mods"),PythonModApi.directoryBesideProject(root.resolve("target/barnocraft.jar")));
        assertEquals(root.resolve("mods"),PythonModApi.directoryBesideProject(root.resolve("target/classes")));
        assertEquals(root.resolve("mods"),PythonModApi.directoryBesideProject(root.resolve("barnocraft.jar")));
    }
    Map<String,Object> manifest() throws Exception {return Json.obj(Json.parse(Files.readString(Path.of("mods/archery_wolves/mod.json"))));}
    World world() throws Exception {World w=World.flat();w.mods.register("archery_wolves",manifest());return w;}
    @Test void jsonRejectsBrokenMessagesAndRoundTripsEscapes(){
        var input=Map.of("name","волк\n\"\\","number",1.5,"items",List.of(true,false));
        assertEquals(input,Json.parse(Json.stringify(input)));
        for(String bad:List.of("{\"a\":1,\"a\":2}","[NaN]","1e999","[1,]","{x:1}","{} garbage"))assertThrows(IllegalArgumentException.class,()->Json.parse(bad));
    }
    @Test void skeletonDropsRealItemsAndBoneTamesWolf() throws Exception {
        World w=world();Player player=new Player();player.position.set(20.5f,6,20.5f);Inventory inv=new Inventory();
        var skeleton=w.mods.spawn("archery_wolves:skeleton",24.5f,20.5f);
        assertTrue(w.mods.damage(skeleton.id,8,"player",player));
        assertEquals(1,w.droppedItems().stream().filter(d->d.type().name().endsWith(":bone")).count());
        assertEquals(1,w.droppedItems().stream().filter(d->d.type().name().endsWith(":bow")).count());
        assertEquals(4,w.droppedItems().stream().filter(d->d.type().name().endsWith(":arrow")).count());
        var wolf=w.mods.spawn("archery_wolves:wolf",20.5f,23.5f);
        ItemType bone=ItemType.valueOf("archery_wolves:bone");inv.add(bone,2);
        Vector3f eye=player.position.add(0,.8f,0);
        assertTrue(w.mods.playerAction(true,eye,Vector3f.UNIT_Z,player,inv,0));
        assertEquals("player",wolf.owner);assertEquals(1,inv.count(bone));
        w.mods.playerAction(true,eye,Vector3f.UNIT_Z,player,inv,0);assertEquals(1,inv.count(bone),"A tamed wolf does not eat another bone");
        assertTrue(w.mods.drainEvents().stream().anyMatch(e->e.get("event").equals("entity_tamed")));
    }
    @Test void arrowsConsumeAmmoDamageTargetsAndStopAtWalls() throws Exception {
        World w=world();Player p=new Player();p.position.set(20.5f,6,20.5f);Inventory inv=new Inventory();
        ItemType bow=ItemType.valueOf("archery_wolves:bow"),arrow=ItemType.valueOf("archery_wolves:arrow");inv.add(bow);inv.add(arrow,4);
        var skeleton=w.mods.spawn("archery_wolves:skeleton",20.5f,24.5f);
        Vector3f eye=p.position.add(0,1,0);
        assertTrue(w.mods.playerAction(true,eye,Vector3f.UNIT_Z,p,inv,0));
        assertEquals(3,inv.count(arrow));assertEquals(179,inv.slotAtIndex(0).durability());
        for(int i=0;i<50;i++)w.mods.tick(.02f,p);
        assertEquals(4,skeleton.health);
        w.set(20,6,22,Block.STONE);w.set(20,7,22,Block.STONE);
        w.mods.playerAction(true,eye,Vector3f.UNIT_Z,p,inv,0);
        for(int i=0;i<50;i++)w.mods.tick(.02f,p);
        assertEquals(4,skeleton.health,"Solid wall blocks arrows");
        assertEquals(2,inv.count(arrow));assertTrue(w.mods.projectiles.isEmpty());
    }
    @Test void woodAndFeatherRecipesProduceSixteenSticksAndTenArrows() throws Exception {
        world();Inventory sticks=new Inventory();sticks.add(ItemType.PLANK,2);
        assertTrue(sticks.moveRef("slot:0","craft:0",true));
        assertTrue(sticks.moveRef("slot:0","craft:0",true));
        assertEquals(ItemType.STICK,sticks.craftPreview());assertEquals(16,sticks.craftPreviewCount());
        assertTrue(sticks.craftFromGrid());assertEquals(16,sticks.count(ItemType.STICK));assertEquals(0,sticks.count(ItemType.PLANK));

        Inventory arrows=new Inventory();ItemType feather=ItemType.valueOf("archery_wolves:feather");
        arrows.add(feather);arrows.add(ItemType.STICK);
        assertTrue(arrows.moveRef("slot:0","craft:0",true));assertTrue(arrows.moveRef("slot:1","craft:1",true));
        ItemType arrow=ItemType.valueOf("archery_wolves:arrow");
        assertEquals(arrow,arrows.craftPreview());assertEquals(10,arrows.craftPreviewCount());
        assertTrue(arrows.craftFromGrid());assertEquals(10,arrows.count(arrow));
        assertEquals(0,arrows.count(feather));assertEquals(0,arrows.count(ItemType.STICK));
    }
    @Test void chickenDropsMeatAndFeathers() throws Exception {
        World w=world();var chicken=w.mods.spawn("archery_wolves:chicken",24.5f,20.5f);
        assertTrue(w.mods.damage(chicken.id,4,"player",new Player()));
        assertEquals(1,w.droppedItems().stream().filter(d->d.type().name().endsWith(":chicken")).count());
        assertEquals(2,w.droppedItems().stream().filter(d->d.type().name().endsWith(":feather")).count());
    }
    @Test void damagedMobsReceiveBriefHitFlashState() throws Exception {
        World w=world();Player p=new Player();p.position.set(20.5f,6,20.5f);
        var wolf=w.mods.spawn("archery_wolves:wolf",24.5f,20.5f);
        assertTrue(w.mods.damage(wolf.id,1,"player",p));assertTrue(wolf.hurtTime>0);
        Zombie zombie=new Zombie(25.5f,6,20.5f,8,0);w.addZombie(zombie);
        assertTrue(w.damageZombie(zombie.id(),1,"player"));assertTrue(w.hurtFlash(zombie.id()));
        w.addSheep(new Sheep(26.5f,6,20.5f,3));String sheepId=w.sheep().get(0).id();
        assertTrue(w.damageSheep(sheepId,1,"player"));assertTrue(w.hurtFlash(sheepId));
        w.advanceTime(.3f);assertFalse(w.hurtFlash(zombie.id()));
    }
    @Test void chickenCanBeEatenAndCookedInTheFurnace() throws Exception {
        World w=world();ItemType raw=ItemType.valueOf("archery_wolves:chicken");
        ItemType cooked=ItemType.valueOf("archery_wolves:cooked_chicken");
        assertEquals(cooked,World.smeltingResult(raw));
        assertEquals(ItemType.COOKED_MUTTON,World.smeltingResult(ItemType.MUTTON));
        Player player=new Player();player.damage(10);player.restoreHunger(10);Inventory inventory=new Inventory();inventory.add(cooked);
        assertTrue(player.eatFood(inventory,0));assertEquals(16,player.health());assertEquals(18,player.hunger());assertEquals(0,inventory.count(cooked));
        w.set(20,6,20,Block.FURNACE);inventory.add(ItemType.COAL);inventory.add(raw);
        assertTrue(w.putFurnaceItem(20,6,20,0,inventory,0));
        assertTrue(w.putFurnaceItem(20,6,20,1,inventory,1));
        for(int tick=0;tick<19;tick++)w.updateFurnaces(.1f);
        assertEquals(0,w.furnaceAt(20,6,20).output().count());
        assertTrue(w.furnaceProgress(20,6,20)>.9f);
        w.updateFurnaces(.1f);assertEquals(cooked,w.furnaceAt(20,6,20).output().item());
        assertEquals(1,w.furnaceAt(20,6,20).output().count());
        World savedWorld=new World(42);savedWorld.mods.register("archery_wolves",manifest());
        assertTrue(savedWorld.set(20,30,20,Block.FURNACE));Inventory furnaceInput=new Inventory();
        furnaceInput.add(ItemType.COAL);furnaceInput.add(raw);
        assertTrue(savedWorld.putFurnaceItem(20,30,20,0,furnaceInput,0));
        assertTrue(savedWorld.putFurnaceItem(20,30,20,1,furnaceInput,1));
        assertTrue(savedWorld.smeltIron(20,30,20));
        SaveStore saves=new SaveStore(directory);saves.save(1,savedWorld.snapshot(),
            new SaveStore.Pose(20.5f,30,20.5f,0,0,0),furnaceInput.snapshot());
        assertEquals(cooked,saves.load(1).world().furnaceAt(20,30,20).output().item());
    }
    @Test void combatEventsCoverOwnerHitsAndZombieHitsAndPetsDamageZombies() throws Exception {
        World w=world();Player p=new Player();p.position.set(20.5f,6,20.5f);
        Zombie zombie=new Zombie(21.2f,6,20.5f,8,0);w.addZombie(zombie);
        w.updateZombies(.02f,p);
        assertTrue(w.mods.drainEvents().stream().anyMatch(e->Json.obj(e.get("data")).get("target").equals("player")));
        assertTrue(w.attackZombie(p.position.add(0,.9f,0),Vector3f.UNIT_X,5));
        assertTrue(w.mods.drainEvents().stream().anyMatch(e->Json.obj(e.get("data")).get("source").equals("player")));
        var wolf=w.mods.spawn("archery_wolves:wolf",22.1f,20.5f);wolf.owner="player";
        assertTrue(w.mods.attack(wolf,zombie.id(),false,p));assertEquals(5,w.zombies().get(0).health());
        assertFalse(w.mods.attack(wolf,"player",false,p));
    }
    @Test void ownedWolfCustomStacksDurabilityAndZombieIdsSurviveSave() throws Exception {
        World w=new World(42);w.mods.register("archery_wolves",manifest());
        var wolf=w.mods.spawn("archery_wolves:wolf",480.5f,320.5f);wolf.owner="player";
        w.mods.storage.put("archery_wolves",Map.of("example",7));
        Zombie z=new Zombie(470,40,320,8,1.5f);w.addZombie(z);
        Inventory inv=new Inventory();inv.add(ItemType.valueOf("archery_wolves:bow"));inv.damageToolAt(0);inv.add(ItemType.valueOf("archery_wolves:arrow"),12);
        SaveStore store=new SaveStore(directory);store.save(1,w.snapshot(),new SaveStore.Pose(480,40,320,0,0,0),inv.snapshot());
        var saved=store.load(1);var restored=saved.world().mods.entities.get(wolf.id);
        assertNotNull(restored);assertEquals("player",restored.owner);assertEquals(179,saved.inventory().slots().get(0).durability());
        assertEquals(12,saved.inventory().slots().get(1).count());assertEquals(z.id(),saved.world().zombies().get(0).id());
    }
    @Test void actualPythonExamplesLoadRenderMinimapAndSpawnDaytimeWolf() throws Exception {
        assertEquals(Path.of("mods").toAbsolutePath().normalize(),PythonModApi.defaultDirectory());
        Path root=directory.resolve("mods");Files.createDirectories(root);
        for(String mod:List.of("sdk","minimap","archery_wolves")){
            Files.createDirectories(root.resolve(mod));try(var paths=Files.list(Path.of("mods",mod))){for(Path f:paths.filter(p->p.toString().endsWith(".py")||p.toString().endsWith(".json")).toList())Files.copy(f,root.resolve(mod).resolve(f.getFileName()));}
        }
        Files.writeString(root.resolve("flat-demo.py"),"""
                from barnocraft import Mod
                api = Mod()
                @api.on('load')
                def loaded(event):
                    api.show_image('flat_demo:badge', 'Flat demo', [['33aa55']])
                api.run()
                """);
        World w=World.flat();Player p=new Player();Inventory inv=new Inventory();
        try(PythonModApi api=new PythonModApi(root,w,p,inv)){
            api.start();assertEquals(3,api.loadedMods());
            long until=System.nanoTime()+5_000_000_000L;
            while(System.nanoTime()<until && (!w.mods.overlays.containsKey("minimap:map") || !w.mods.overlays.containsKey("flat_demo:badge") || w.mods.entities.isEmpty())){api.tick(.1f,0,0);Thread.sleep(15);}
            assertTrue(w.mods.overlays.containsKey("minimap:map"));
            assertTrue(w.mods.overlays.containsKey("flat_demo:badge"),"Top-level Python file loads through the SDK");
            w.mods.event("ui.click",Map.of("id","minimap:map","x",12,"y",12,"button","left"));
            long markerUntil=System.nanoTime()+2_000_000_000L;
            while(System.nanoTime()<markerUntil && !w.mods.storage.containsKey("minimap")){api.tick(.1f,0,0);Thread.sleep(10);}
            assertTrue(w.mods.storage.containsKey("minimap"),"Minimap click stores a marker");
            assertEquals(1,Json.list(Json.obj(w.mods.storage.get("minimap")).get("markers")).size());
                Map<String,Object> marker=Json.obj(Json.list(Json.obj(w.mods.storage.get("minimap")).get("markers")).get(0));
                assertEquals(40.0,Json.num(marker,"x",0));assertEquals(40.0,Json.num(marker,"z",0),
                    "The center pixel marks the center of the sampled world surface");
            w.mods.event("ui.click",Map.of("id","minimap:map","x",12,"y",12,"button","right"));
            markerUntil=System.nanoTime()+2_000_000_000L;
            while(System.nanoTime()<markerUntil && !Json.list(Json.obj(w.mods.storage.get("minimap")).get("markers")).isEmpty()){api.tick(.1f,0,0);Thread.sleep(10);}
            assertTrue(Json.list(Json.obj(w.mods.storage.get("minimap")).get("markers")).isEmpty(),"Right click removes the nearest marker");
            assertTrue(w.mods.entities.values().stream().anyMatch(e->e.type.equals("archery_wolves:wolf")));
            assertTrue(w.mods.entities.values().stream().anyMatch(e->e.type.equals("archery_wolves:chicken")));
            assertTrue(w.mods.entities.values().stream().noneMatch(e->e.type.endsWith(":skeleton")),"No daytime skeleton spawning");
            int health=p.health();for(int i=0;i<20;i++){api.tick(.1f,0,0);w.mods.tick(.1f,p);Thread.sleep(10);}assertEquals(health,p.health());
                var chicken=w.mods.entities.values().stream().filter(e->e.type.equals("archery_wolves:chicken")).findFirst().orElseThrow();
                assertTrue(chicken.goalTime>0,"Chicken wanders like a passive animal");
                w.mods.event("damage",Map.of("source","player","target",chicken.id,"amount",1));
                for(int i=0;i<30;i++){api.tick(.1f,0,0);w.mods.tick(.1f,p);Thread.sleep(10);}
                float fleeX=chicken.goalX-chicken.x,fleeZ=chicken.goalZ-chicken.z;
                assertTrue(fleeX*(chicken.x-p.position.x)+fleeZ*(chicken.z-p.position.z)>0,
                    "Chicken flees away from its attacker");
        }
    }
}
