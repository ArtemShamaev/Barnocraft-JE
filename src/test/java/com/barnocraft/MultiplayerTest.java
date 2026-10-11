package com.barnocraft;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MultiplayerTest {
    static int port() throws IOException { try(ServerSocket socket=new ServerSocket(0)){return socket.getLocalPort();} }
    static final SaveStore.Pose POSE=new SaveStore.Pose(40.5f,100,40.5f,0,0,0);
    static NetworkPlayer player(int skin){return new NetworkPlayer(0,skin,40.5f,100,40.5f,0,0,false,false,false);}
    static class Client implements Multiplayer.Listener,AutoCloseable {
        World world;
        Inventory inventory=new Inventory();
        Multiplayer network;
        String error;
        int replies;
        Client(NetworkSession socket,int skin) throws IOException {network=new Multiplayer(socket,skin,this);}
        public void joined(SaveStore.Decoded state){world=World.restore(state.world());}
        public void changed(Set<Integer> cells){}
        public void result(Inventory.Snapshot items,NetworkGameplay.Reply reply){inventory.restore(items);replies++;}
        public void disconnected(String reason){error=reason;}
        void tick(){network.tick(.05f,world,POSE,"Тест сети",player(1),inventory,false);}
        public void close(){network.close();}
    }
    static void pump(BooleanSupplier condition,Client... clients) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(!condition.getAsBoolean() && System.nanoTime()<deadline){for(Client client:clients)client.tick();Thread.sleep(5);}
        for(Client client:clients)assertNull(client.error,client.error);
        assertTrue(condition.getAsBoolean(),"Network operation timed out");
    }
    @Test void validatesPortsAndReportsOccupiedPort() throws Exception {
        for(String value:List.of("0","65536","-1","abc","", "25.5"," 80"))assertThrows(IllegalArgumentException.class,()->NetworkSession.parsePort(value));
        assertEquals(65535,NetworkSession.parsePort("65535"));assertEquals(1,NetworkSession.parsePort("1"));
        int port=port();try(NetworkSession server=NetworkSession.host(port)){assertThrows(BindException.class,()->NetworkSession.host(port));}
        try(NetworkSession reopened=NetworkSession.host(port)){assertEquals(port,reopened.port());}
    }
    @Test void twoGuestsJoinBuildMineSeeEachOtherAndDisconnect() throws Exception {
        int port=port();
        try(Client host=new Client(NetworkSession.host(port),0);
            Client first=new Client(NetworkSession.connect("127.0.0.1",port),1);
            Client second=new Client(NetworkSession.connect("localhost",port),0)) {
            host.world=new World(123);
            host.world.set(40,101,37,Block.STONE);
            pump(()->first.world!=null && second.world!=null && first.network.players.size()==3,host,first,second);
            assertEquals(host.world.seed(),first.world.seed());
            assertEquals(Block.STONE,second.world.get(40,101,37));
            first.inventory.add(ItemType.GLASS,2);
            first.network.request(NetworkGameplay.Command.of("use"),player(1),first.inventory,0,false);
            pump(()->!first.network.busy() && second.world.get(40,101,38)==Block.GLASS,host,first,second);
            assertEquals(Block.GLASS,host.world.get(40,101,38));assertEquals(1,first.inventory.count(ItemType.GLASS));
            second.network.request(NetworkGameplay.Command.of("mine"),player(0),second.inventory,0,false);
            pump(()->!second.network.busy() && first.world.get(40,101,38)==Block.AIR,host,first,second);
            assertEquals(Block.AIR,host.world.get(40,101,38));
            int leavingId=second.network.localId();second.close();
            pump(()->!first.network.players.containsKey(leavingId),host,first);
            assertEquals(2,first.network.players.size());
        }
    }
    @Test void clientDetectsHostShutdown() throws Exception {
        int port=port();try(Client host=new Client(NetworkSession.host(port),0);Client guest=new Client(NetworkSession.connect("localhost",port),1)) {
            host.world=new World(45);pump(()->guest.world!=null,host,guest);host.close();
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(guest.error==null && System.nanoTime()<deadline){guest.tick();Thread.sleep(5);}
            assertNotNull(guest.error);
        }
    }
    @Test void rejectsWrongProtocolAndOversizedFrames() throws Exception {
        int port=port();try(NetworkSession host=NetworkSession.host(port);Socket socket=new Socket("localhost",port)) {
            var out=new DataOutputStream(socket.getOutputStream());out.writeInt(0x42434E54);out.writeInt(999);out.flush();
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();NetworkSession.Event event=null;
            while(event==null && System.nanoTime()<deadline){event=host.poll();Thread.sleep(5);}
            assertNotNull(event);assertEquals(0,event.type());assertTrue(event.error().contains("версии"));
        }
        port=port();try(NetworkSession host=NetworkSession.host(port);Socket socket=new Socket("localhost",port)) {
            var out=new DataOutputStream(socket.getOutputStream());out.writeInt(0x42434E54);out.writeInt(3);out.writeInt(Integer.MAX_VALUE);out.flush();
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();NetworkSession.Event event=null;
            while(event==null && System.nanoTime()<deadline){event=host.poll();Thread.sleep(5);}
            assertNotNull(event);assertEquals(0,event.type());
        }
    }
    @Test void backgroundHeartbeatsSurviveSixteenSecondsWithoutGameTicks() throws Exception {
        int port=port();
        try(NetworkSession host=NetworkSession.host(port);NetworkSession client=NetworkSession.connect("127.0.0.1",port)) {
            // The previous transport timed out after 15 s when the window/loading thread stalled.
            Thread.sleep(16000);
            assertEquals(1,host.peerIds().size());assertEquals(1,client.peerIds().size());
            assertNull(host.poll());assertNull(client.poll(),"Heartbeats must not fill the game event queue");
            client.send(0,NetworkSession.HELLO,new byte[]{1});
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();NetworkSession.Event event=null;
            while(event==null && System.nanoTime()<deadline){event=host.poll();Thread.sleep(5);}
            assertNotNull(event);assertEquals(NetworkSession.HELLO,event.type());
        }
    }
    @Test void latestUpdatesReplaceStaleFramesWhileGameThreadIsBusy() throws Exception {
        int port=port();try(NetworkSession host=NetworkSession.host(port);NetworkSession client=NetworkSession.connect("127.0.0.1",port)) {
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(host.peerIds().isEmpty() && System.nanoTime()<deadline)Thread.sleep(5);
            int peer=host.peerIds().iterator().next();
            for(int i=0;i<500;i++) {
                host.send(peer,NetworkSession.STATE,new byte[]{(byte)i});
                host.send(peer,NetworkSession.POSES,new byte[]{(byte)i});
            }
            Thread.sleep(200);
            host.send(peer,NetworkSession.STATE,new byte[]{42});
            host.send(peer,NetworkSession.RESULT,new byte[]{99});
            Thread.sleep(200);
            NetworkSession.Event update=client.poll();assertNotNull(update);assertEquals(NetworkSession.STATE,update.type());assertEquals(42,update.data()[0]);
            NetworkSession.Event result=client.poll();assertNotNull(result);assertEquals(NetworkSession.RESULT,result.type());
            assertEquals(1,client.peerIds().size());
        }
    }
    @Test void serverReportsRejectionInsteadOfResettingConnection() throws Exception {
        int port=port();try(NetworkSession host=NetworkSession.host(port);NetworkSession client=NetworkSession.connect("127.0.0.1",port)) {
            long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();
            while(host.peerIds().isEmpty() && System.nanoTime()<deadline)Thread.sleep(5);
            host.reject(host.peerIds().iterator().next(),"Некорректный слот");
            NetworkSession.Event event=null;
            while(event==null && System.nanoTime()<deadline){event=client.poll();Thread.sleep(5);}
            assertNotNull(event);assertEquals(NetworkSession.ERROR,event.type());
            assertEquals("Некорректный слот",NetworkSession.input(event.data()).readUTF());
        }
    }
    @Test void sharedChestTransfersDoNotDuplicateItems() {
        World world=new World(42);world.set(40,100,38,Block.CHEST);
        Inventory source=new Inventory();source.add(ItemType.STONE,9);
        NetworkGameplay.execute(world,player(0),source,0,false,new NetworkGameplay.Command("container-chest-put",40,100,38,0,0,false,""));
        assertEquals(0,source.count(ItemType.STONE));
        Inventory first=new Inventory(),second=new Inventory();
        var take=new NetworkGameplay.Command("container-chest-take",40,100,38,0,0,false,"");
        NetworkGameplay.execute(world,player(0),first,0,false,take);
        NetworkGameplay.execute(world,player(1),second,0,false,take);
        assertEquals(9,first.count(ItemType.STONE));assertEquals(0,second.count(ItemType.STONE));
    }
    @Test void snapshotsPreserveDoorsRotationsAndDoNotResetClientInventory() throws Exception {
        World host=new World(42);Player p=new Player();host.set(40,99,38,Block.STONE);host.set(40,100,38,Block.AIR);host.set(40,101,38,Block.AIR);
        assertTrue(host.placeDoor(40,100,38,0,p));
        byte[] bytes=SaveStore.encode(host.snapshot(),POSE,Inventory.emptySnapshot(),"Сеть");
        SaveStore.Decoded decoded=SaveStore.decode(bytes);World client=World.restore(decoded.world());
        assertTrue(host.toggleDoor(40,100,38,p));
        assertFalse(client.applyNetwork(SaveStore.decode(SaveStore.encode(host.snapshot(),POSE,Inventory.emptySnapshot(),"Сеть")).world()).isEmpty());
        assertTrue(client.doorAt(40,100,38).open());
        assertTrue(client.applyNetwork(host.snapshot()).isEmpty());
    }
}
