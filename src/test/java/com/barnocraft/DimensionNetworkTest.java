package com.barnocraft;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class DimensionNetworkTest {
    static class Instance implements Multiplayer.Listener,AutoCloseable {
        World world;Multiplayer net;Inventory inventory=new Inventory();NetworkPlayer position;String error;
        Instance(NetworkSession transport,World world,NetworkPlayer position)throws Exception{this.world=world;this.position=position;net=new Multiplayer(transport,0,this);}
        public void joined(SaveStore.Decoded state){world=World.restore(state.world());var p=state.pose();position=new NetworkPlayer(net.localId(),0,p.x(),p.y(),p.z(),0,0,false,false,false,world.dimension());}
        public void transferred(SaveStore.Decoded state,Inventory.Snapshot items){joined(state);inventory.restore(items);}
        public void changed(Set<Integer> cells){}
        public void result(Inventory.Snapshot items,NetworkGameplay.Reply reply){inventory.restore(items);}
        public void disconnected(String reason){error=reason;}
        void tick(){net.tick(.05f,world,new SaveStore.Pose(position.x(),position.y(),position.z(),position.yaw(),position.pitch(),0),"Измерения",position,inventory,false);}
        void request(String kind){net.request(NetworkGameplay.Command.of(kind),position,inventory,0,false);}
        public void close(){net.close();}
    }
    static void pump(BooleanSupplier condition,Instance... instances)throws Exception {
        long deadline=System.nanoTime()+15_000_000_000L;
        while(!condition.getAsBoolean() && System.nanoTime()<deadline){for(var instance:instances){instance.tick();assertNull(instance.error,instance.error);}Thread.sleep(5);}
        assertTrue(condition.getAsBoolean(),"Network dimension operation timed out");
    }
    @Test void guestsTravelIndependentlyAndEditsStayInTheirDimension() throws Exception {
        World surface=new World(71),hell=new World(71,Dimension.HELL_FOREST);Portal.Site portal=surface.portals().get(0);
        int sy=portal.floor(71,Dimension.SURFACE)+1,hy=portal.floor(71,Dimension.HELL_FOREST)+1;
        var spawn=new NetworkPlayer(0,0,portal.x()+.5f,sy,portal.z()+.5f,0,0,false,false,false);
        int port=MultiplayerTest.port();
        try(Instance host=new Instance(NetworkSession.host(port),surface,spawn);
            Instance guest=new Instance(NetworkSession.connect("127.0.0.1",port),null,spawn);
            Instance observer=new Instance(NetworkSession.connect("127.0.0.1",port),null,spawn)) {
            host.net.worldProvider(d->d==Dimension.SURFACE?surface:hell);
            pump(()->guest.world!=null && observer.world!=null,host,guest,observer);
            guest.inventory.add(ItemType.COAL,3);guest.request("portal");
            pump(()->guest.world.dimension()==Dimension.HELL_FOREST,host,guest,observer);
            assertEquals(3,guest.inventory.count(ItemType.COAL));assertEquals(Dimension.SURFACE,host.world.dimension());assertEquals(Dimension.SURFACE,observer.world.dimension());
            int x=portal.x()+8,z=portal.z();
            for(int dz=0;dz<=5;dz++)for(int dy=0;dy<4;dy++)hell.set(x,hy+dy,z+dz,Block.AIR);
            hell.set(x,hy+1,z+1,Block.HELL_STONE);surface.set(x,hy+1,z+1,Block.GLASS);
            guest.position=new NetworkPlayer(guest.net.localId(),0,x+.5f,hy,z+4.5f,0,0,false,false,false,Dimension.HELL_FOREST);
            guest.request("mine");
            pump(()->!guest.net.busy() && hell.get(x,hy+1,z+1)==Block.AIR,host,guest,observer);
            assertEquals(Block.GLASS,surface.get(x,hy+1,z+1));assertEquals(1,guest.inventory.count(ItemType.HELL_STONE));
            guest.position=new NetworkPlayer(guest.net.localId(),0,portal.x()+.5f,hy,portal.z()+.5f,0,0,false,false,false,Dimension.HELL_FOREST);
            guest.request("portal");pump(()->guest.world.dimension()==Dimension.SURFACE,host,guest,observer);
            assertEquals(1,guest.inventory.count(ItemType.HELL_STONE));assertNull(Portal.touching(guest.world,guest.position.position()));
        }
    }
}
