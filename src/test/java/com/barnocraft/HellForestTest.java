package com.barnocraft;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class HellForestTest {
    @TempDir Path directory;
    private SaveStore.Pose pose(){return new SaveStore.Pose(528.5f,40,346.5f,0,0,1);}
    @Test void portalCheatFindsNearestInBothDimensionsAndStopsFalling() {
        for(Dimension dimension:Dimension.values()) {
            World world=new World(42,dimension);Player player=new Player();
            Portal.Site site=world.portals().get(world.portals().size()-1);
            player.position.set(site.x()+3,90,site.z()+4);player.velocityY=-20;
            assertEquals("Телепортировано к порталу",Cheats.execute("/TP PORTAL",new Inventory(),world,player));
            assertEquals(site.exit(world),player.position);assertEquals(0,player.velocityY);
            assertFalse(player.collides(world));assertNull(Portal.touching(world,player.position));
        }
        Player player=new Player();Vector3f before=player.position.clone();
        assertEquals("Портал не найден",Cheats.execute("tp portal",new Inventory(),World.flat(),player));
        assertEquals(before,player.position);
        assertEquals("Формат: tp river|portal",Cheats.execute("tp portal extra",new Inventory(),World.flat(),player));
    }
    @Test void naturalPortalsArePairedUnbreakableAndHaveNoItemOrRecipe() {
        World surface=new World(42),hell=new World(42,Dimension.HELL_FOREST);
        assertEquals(surface.portals(),hell.portals());assertTrue(surface.portals().size()>5);
        for(World world:List.of(surface,hell)) {
            Portal.Site site=world.portals().get(0);int floor=site.floor(world.seed(),world.dimension());
            assertEquals(Block.PORTAL_FRAME,world.get(site.x()-2,floor+2,site.z()));
            assertEquals(Block.FOREST_PORTAL,world.get(site.x(),floor+2,site.z()));
            assertFalse(world.set(site.x()-2,floor+2,site.z(),Block.AIR));
            assertFalse(world.set(site.x(),floor+2,site.z(),Block.STONE));
            assertEquals(site,Portal.touching(world,new Vector3f(site.x()+.5f,floor+1,site.z()+.5f)));
            Vector3f exit=Portal.safeExit(world,site);Player player=new Player();player.position.set(exit);
            assertFalse(player.collides(world));assertNull(Portal.touching(world,exit));
        }
        assertFalse(surface.set(40,100,40,Block.FOREST_PORTAL));
        assertFalse(surface.set(40,100,40,Block.PORTAL_FRAME));
        assertThrows(IllegalArgumentException.class,()->ItemType.valueOf("FOREST_PORTAL"));
        Inventory inv=new Inventory();inv.addBlock(Block.PORTAL_FRAME);inv.addBlock(Block.FOREST_PORTAL);
        assertEquals(Inventory.emptySnapshot(),inv.snapshot());
    }
    @Test void hellHasDistinctTerrainTreesLavaAndGlowingCrystals() {
        World hell=new World(912,Dimension.HELL_FOREST),same=new World(912,Dimension.HELL_FOREST);
        Set<Block> found=EnumSet.noneOf(Block.class);
        for(int x=400;x<480;x++)for(int z=240;z<320;z++)for(int y=0;y<100;y++) {
            Block block=hell.get(x,y,z);found.add(block);
            if((x+y+z)%151==0)assertEquals(block,same.get(x,y,z));
        }
        for(Block block:List.of(Block.HELL_SOIL,Block.HELL_STONE,Block.HELL_BASALT,Block.HELL_LOG,Block.HELL_LEAVES,Block.EMBER_CRYSTAL))assertTrue(found.contains(block),block.name());
        boolean lava=false;
        for(int x=0;x<World.WIDTH;x+=12)for(int z=0;z<World.DEPTH;z+=12)if(HellTerrain.height(912,x,z)<HellTerrain.LAVA_LEVEL) {assertEquals(Block.HELL_LAVA,hell.get(x,HellTerrain.LAVA_LEVEL,z));lava=true;break;}
        assertTrue(lava);assertTrue(hell.sheep().isEmpty());assertTrue(hell.zombies().isEmpty());
    }
    @Test void bothDimensionsAndMobStatesPersistWithoutMixingEdits() throws Exception {
        SaveStore saves=new SaveStore(directory);World surface=new World(42),hell=new World(42,Dimension.HELL_FOREST);
        surface.set(40,100,40,Block.GLASS);hell.set(40,100,40,Block.HELL_LOG);
        HellMob mob=HellMob.spawn(HellMob.Kind.BOAR,50,35,50);hell.addHellMob(mob);
        Inventory inv=new Inventory();inv.add(ItemType.EMBER_CRYSTAL,3);
        saves.saveUniverse(1,List.of(surface.snapshot(),hell.snapshot()),hell.snapshot(),pose(),inv.snapshot(),"Порталы");
        SaveStore.Saved loaded=saves.load(1);
        assertEquals(Dimension.HELL_FOREST,loaded.world().dimension());assertEquals(Block.HELL_LOG,loaded.world().get(40,100,40));
        assertEquals(List.of(mob),loaded.world().hellMobs());assertEquals(inv.snapshot(),loaded.inventory());
        assertEquals(Block.GLASS,saves.loadDimension(1,Dimension.SURFACE).world().get(40,100,40));
        assertEquals("Порталы",saves.worldName(1));assertTrue(saves.delete(1));
        for(Dimension dimension:Dimension.values())assertFalse(Files.exists(saves.dimensionPath(1,dimension)));
    }
    @Test void legacyVersion21StillLoadsAsSurface() throws Exception {
        byte[] current=SaveStore.encode(new World(42).snapshot(),pose(),Inventory.emptySnapshot(),"Старый мир");
        var data=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(current)));
        int magic=data.readInt();data.readInt();String name=data.readUTF();data.readUTF();byte[] rest=data.readAllBytes();
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(directory.resolve("world-1.dat"))))) {
            out.writeInt(magic);out.writeInt(21);out.writeUTF(name);out.write(rest,0,rest.length-4);
        }
        assertEquals(Dimension.SURFACE,new SaveStore(directory).load(1).world().dimension());
    }
    @Test void hostileMobsAttackCanBeKilledAndDropResources() {
        World world=new World(42,Dimension.HELL_FOREST);Player player=new Player();
        for(int x=38;x<44;x++)for(int z=38;z<44;z++)for(int y=40;y<45;y++)world.set(x,y,z,y==40?Block.HELL_BASALT:Block.AIR);
        player.position.set(40.5f,41,40.5f);
        HellMob imp=HellMob.spawn(HellMob.Kind.IMP,40.5f,41.6f,39.5f);
        world.addHellMob(imp);world.updateHellMobs(.1f,List.of(player));assertTrue(player.health()<20);
        Inventory inventory=new Inventory();Vector3f origin=player.position.add(0,Player.EYE,0),direction=new Vector3f(0,0,-1);
        for(int i=0;i<3;i++)assertTrue(world.attackHellMob(origin,direction,5,inventory));
        assertTrue(world.hellMobs().stream().noneMatch(m->m.id().equals(imp.id())));assertEquals(2,inventory.count(ItemType.EMBER_CRYSTAL));
    }
    @Test void populationIsSparseAndOldCrowdsAreTrimmed() {
        World world=new World(42,Dimension.HELL_FOREST);Player player=new Player();
        player.position.set(world.portals().get(0).exit(world));
        world.updateHellMobs(24,List.of(player));assertTrue(world.hellMobs().isEmpty());
        for(int tick=0;tick<30;tick++) {
            var before=world.hellMobs().stream().map(HellMob::id).toList();
            world.advanceTime(25);world.updateHellMobs(25,List.of(player));
            assertTrue(world.hellMobs().size()<=4);
            var spawned=world.hellMobs().stream().filter(m->!before.contains(m.id())).toList();
            assertTrue(spawned.size()<=1);
            for(var mob:spawned)for(var portal:world.portals())
                assertTrue(Math.hypot(mob.x()-portal.x(),mob.z()-portal.z())>19);
        }
        for(int i=0;i<12;i++)world.addHellMob(HellMob.spawn(HellMob.Kind.BOAR,player.position.x+i,player.position.y,player.position.z));
        world.updateHellMobs(.01f,List.of(player));assertEquals(4,world.hellMobs().size());
        player.position.set(20,40,20);world.updateHellMobs(.01f,List.of(player));assertTrue(world.hellMobs().isEmpty());
    }
    @Test void opaqueHellLeavesHideInteriorFacesAndPortalUsesSinglePlane() {
        World flat=World.flat();
        for(int x=20;x<23;x++)for(int y=10;y<13;y++)for(int z=20;z<23;z++)flat.set(x,y,z,Block.HELL_LEAVES);
        assertEquals(108,ChunkMesh.build(flat,1,1)[30].getTriangleCount());
        World world=new World(42,Dimension.HELL_FOREST);var portal=world.portals().get(0);
        int cx=portal.x()/World.CHUNK,cz=portal.z()/World.CHUNK,count=0;
        for(int x=cx*World.CHUNK;x<(cx+1)*World.CHUNK;x++)for(int z=cz*World.CHUNK;z<(cz+1)*World.CHUNK;z++)
            for(int y=0;y<World.HEIGHT;y++)if(world.get(x,y,z)==Block.FOREST_PORTAL)count++;
        assertEquals(count*2,ChunkMesh.build(world,cx,cz)[34].getTriangleCount());
    }
    @Test void newBlocksHavePackagedPngTexturesAndRenderMeshes() throws Exception {
        for(Block block:List.of(Block.HELL_SOIL,Block.HELL_STONE,Block.HELL_BASALT,Block.HELL_LOG,Block.HELL_LEAVES,Block.EMBER_CRYSTAL,Block.HELL_LAVA,Block.PORTAL_FRAME,Block.FOREST_PORTAL)) {
            try(var stream=getClass().getResourceAsStream("/Textures/"+block.texture)) {
                assertNotNull(stream,block.texture);assertNotNull(javax.imageio.ImageIO.read(stream));
            }
        }
        World world=new World(42,Dimension.HELL_FOREST);Portal.Site portal=world.portals().get(0);
        var meshes=ChunkMesh.build(world,portal.x()/World.CHUNK,portal.z()/World.CHUNK);
        assertNotNull(meshes[33]);assertNotNull(meshes[34]);
    }
}
