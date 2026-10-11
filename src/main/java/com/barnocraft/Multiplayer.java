package com.barnocraft;

import java.io.*;
import java.util.*;

/** Protocol coordinator; called exclusively on the render/game thread. */
final class Multiplayer implements AutoCloseable {
    interface Listener {
        void joined(SaveStore.Decoded state);
        void changed(Set<Integer> cells);
        void result(Inventory.Snapshot inventory,NetworkGameplay.Reply reply);
        void disconnected(String reason);
        default void transferred(SaveStore.Decoded state,Inventory.Snapshot inventory){joined(state);}
        default void damage(int amount){}

    }
    final NetworkSession transport;
    final Map<Integer,NetworkPlayer> players=new HashMap<>();
    private final Listener listener;
    private java.util.function.Function<Dimension,World> worldProvider;
    private final Map<Integer,Boolean> creativePlayers=new HashMap<>();
    void worldProvider(java.util.function.Function<Dimension,World> provider){worldProvider=provider;}
    private World worldFor(Dimension dimension,World fallback) {
        if(fallback.dimension()==dimension)return fallback;
        World found=worldProvider==null?null:worldProvider.apply(dimension);
        if(found==null)throw new IllegalArgumentException("Измерение недоступно");return found;
    }

    private final Map<Integer,Long> lastAction=new HashMap<>();
    private int localId;
    private float poseClock,stateClock,collectClock;
    private long nextRequest,pending;
    private long connectedAt=System.nanoTime();
    private boolean welcomed;
    Multiplayer(NetworkSession transport,int skin,Listener listener) throws IOException {
        this.transport=transport;this.listener=listener;welcomed=transport.hosting();
        if(!hosting())transport.send(0,NetworkSession.HELLO,NetworkSession.packet(out->out.writeByte(skin)));
    }
    boolean hosting(){return transport.hosting();}
    boolean busy(){return pending!=0;}
    int localId(){return localId;}
    int port(){return transport.port();}
    void request(NetworkGameplay.Command command,NetworkPlayer pose,Inventory inventory,int selected,boolean creative) {
        if(hosting() || !welcomed || busy())return;
        try {
            long id=++nextRequest;
            byte[] bytes=NetworkSession.packet(out->{
                out.writeLong(id);pose.write(out);out.writeInt(selected);out.writeBoolean(creative);
                SaveStore.writeInventory(out,inventory.snapshot());out.writeUTF(command.kind());
                out.writeInt(command.x());out.writeInt(command.y());out.writeInt(command.z());
                out.writeInt(command.a());out.writeInt(command.b());out.writeBoolean(command.one());out.writeUTF(command.value());
            });
            pending=id;transport.send(0,NetworkSession.ACTION,bytes);
        } catch(IOException e){listener.disconnected(e.getMessage());}
    }
    void tick(float dt,World world,SaveStore.Pose hostPose,String name,NetworkPlayer local,Inventory inventory,boolean active) {
        for(int count=0;count<64;count++) {
            NetworkSession.Event event=transport.poll();if(event==null)break;
            try {
                if(event.type()==0) {
                    players.remove(event.peer());lastAction.remove(event.peer());
                    if(!hosting()){listener.disconnected(event.error());return;}
                    continue;
                }
                if(hosting())handleHost(event,world,hostPose,name);else handleClient(event,world);
                if(event.type()==NetworkSession.TRANSFER)return;
            } catch(IOException | RuntimeException e) {
                if(hosting())transport.reject(event.peer(),e.getMessage());else transport.disconnect(event.peer());
                if(!hosting()){listener.disconnected("Ошибка сети: "+e.getMessage());return;}
            }
        }
        if(!welcomed) {
            if(System.nanoTime()-connectedAt>90_000_000_000L)listener.disconnected("Соединение установлено, но хозяин не передал мир за 90 секунд");
            return;
        }
        if(world==null)return;
        poseClock+=dt;stateClock+=dt;collectClock+=dt;
        if(poseClock>=.05f) {
            poseClock=0;
            try {
                if(hosting()) {
                    players.put(0,local);
                    byte[] poses=NetworkSession.packet(out->{out.writeInt(players.size());for(NetworkPlayer p:players.values())p.write(out);});
                    for(int peer:players.keySet())if(peer!=0)transport.send(peer,NetworkSession.POSES,poses);
                } else transport.send(0,NetworkSession.POSES,NetworkSession.packet(local::write));
            } catch(IOException e){listener.disconnected(e.getMessage());}
        }
        if(hosting() && stateClock>=.3f) {
            stateClock=0;
            try { broadcastState(world,hostPose,name); } catch(IOException e){listener.disconnected(e.getMessage());}
        } else if(!hosting() && active && collectClock>=.5f && !busy()) {
            collectClock=0;
            request(NetworkGameplay.Command.of("collect"),local,inventory,hostPose.selected(),hostPose.creative());
        }
    }
    private void handleHost(NetworkSession.Event event,World world,SaveStore.Pose pose,String name) throws IOException {
        if(world==null)throw new IOException("Мир закрыт");
        World activeWorld=world;
        var in=NetworkSession.input(event.data());int peer=event.peer();
        if(players.containsKey(peer))world=worldFor(players.get(peer).dimension(),activeWorld);
        switch(event.type()) {
            case NetworkSession.HELLO -> {
                if(players.containsKey(peer))throw new IOException("Повторное подключение");
                int skin=in.readUnsignedByte();if(skin>1)throw new IOException("Неизвестный персонаж");
                players.put(peer,new NetworkPlayer(peer,skin,pose.x(),pose.y(),pose.z(),pose.yaw(),pose.pitch(),false,false,false,world.dimension()));
                byte[] state=state(world,pose,name);
                transport.send(peer,NetworkSession.WELCOME,NetworkSession.packet(out->{out.writeInt(peer);out.write(state);}));
            }
            case NetworkSession.POSES -> {
                if(!players.containsKey(peer))throw new IOException("Сначала подключитесь");
                NetworkPlayer updated=NetworkPlayer.read(in,peer);
                if(updated.dimension()==players.get(peer).dimension())players.put(peer,updated);
            }
            case NetworkSession.ACTION -> {
                if(!players.containsKey(peer))throw new IOException("Сначала подключитесь");
                long id=in.readLong();if(id<=lastAction.getOrDefault(peer,0L))throw new IOException("Повтор команды");
                NetworkPlayer actor=NetworkPlayer.read(in,peer);int selected=in.readInt();boolean creative=in.readBoolean();
                if(selected<0 || selected>=Block.HOTBAR.length)throw new IOException("Некорректный слот");
                Inventory inventory=new Inventory();inventory.restore(SaveStore.readInventory(in,true,true));
                var command=new NetworkGameplay.Command(in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readBoolean(),in.readUTF());
                if(in.available()!=0)throw new IOException("Лишние данные команды");
                if(actor.dimension()!=players.get(peer).dimension())throw new IOException("Измерение игрока изменилось");
                players.put(peer,actor);lastAction.put(peer,id);creativePlayers.put(peer,creative);
                if(command.kind().equals("portal")) {
                    Portal.Site portal=Portal.touching(world,actor.position());
                    if(portal!=null) {
                        World destination=worldFor(world.dimension().other(),activeWorld);
                        var exit=Portal.safeExit(destination,portal);
                        NetworkPlayer moved=new NetworkPlayer(peer,actor.skin(),exit.x,exit.y,exit.z,actor.yaw(),actor.pitch(),false,false,false,destination.dimension());
                        players.put(peer,moved);
                        SaveStore.Pose arrival=new SaveStore.Pose(exit.x,exit.y,exit.z,actor.yaw(),actor.pitch(),selected);
                        byte[] transfer=state(destination,arrival,name);
                        transport.send(peer,NetworkSession.TRANSFER,NetworkSession.packet(out->{SaveStore.writeInventory(out,inventory.snapshot());out.write(transfer);}));
                        return;
                    }
                }
                World.Snapshot before=world.snapshot();
                NetworkGameplay.Reply reply=NetworkGameplay.execute(world,actor,inventory,selected,creative,command);
                listener.changed(changedCells(before,world.snapshot()));
                // The authoritative state precedes the inventory response on the same TCP stream.
                broadcastState(activeWorld,pose,name);
                transport.send(peer,NetworkSession.RESULT,NetworkSession.packet(out->{
                    out.writeLong(id);SaveStore.writeInventory(out,inventory.snapshot());
                    out.writeUTF(reply.screen());out.writeInt(reply.x());out.writeInt(reply.y());out.writeInt(reply.z());out.writeUTF(reply.notice());
                }));
            }
            default -> throw new IOException("Неизвестный тип пакета");
        }
    }
    private void handleClient(NetworkSession.Event event,World world) throws IOException {
        var in=NetworkSession.input(event.data());
        switch(event.type()) {
            case NetworkSession.DAMAGE -> { int damage=in.readInt();if(damage<0 || damage>20)throw new IOException("Некорректный урон");listener.damage(damage); }
            case NetworkSession.TRANSFER -> {
                Inventory.Snapshot inventory=SaveStore.readInventory(in,true,true);
                SaveStore.Decoded decoded=readState(in,null);pending=0;listener.transferred(decoded,inventory);
            }
            case NetworkSession.ERROR -> throw new IOException("Сервер: "+in.readUTF());
            case NetworkSession.WELCOME -> {
                if(welcomed)throw new IOException("Повтор приветствия");localId=in.readInt();
                SaveStore.Decoded decoded=readState(in,null);welcomed=true;listener.joined(decoded);
            }
            case NetworkSession.STATE -> {
                if(!welcomed || world==null)return;
                SaveStore.Decoded decoded=readState(in,world);
                if(decoded.world().dimension()==world.dimension())listener.changed(world.applyNetwork(decoded.world()));
            }
            case NetworkSession.POSES -> {
                int count=in.readInt();if(count<1 || count>NetworkSession.MAX_PLAYERS)throw new IOException("Некорректное число игроков");
                Map<Integer,NetworkPlayer> next=new HashMap<>();
                for(int i=0;i<count;i++){NetworkPlayer player=NetworkPlayer.read(in,-1);next.put(player.id(),player);}
                players.clear();players.putAll(next);
            }
            case NetworkSession.RESULT -> {
                if(in.readLong()!=pending || pending==0)throw new IOException("Неожиданный ответ");
                Inventory.Snapshot inventory=SaveStore.readInventory(in,true,true);
                var reply=new NetworkGameplay.Reply(in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readUTF());
                pending=0;listener.result(inventory,reply);
            }
            default -> throw new IOException("Неизвестный тип пакета");
        }
    }
    private static byte[] state(World world,SaveStore.Pose pose,String name) throws IOException {
        byte[] save=SaveStore.encode(world.snapshot(),pose,Inventory.emptySnapshot(),name);
        return NetworkSession.packet(out->{
            out.writeInt(save.length);out.write(save);
            out.writeInt(world.droppedItems().size());
            for(DroppedItem item:world.droppedItems()){out.writeUTF(item.type().name());out.writeInt(item.x());out.writeInt(item.y());out.writeInt(item.z());out.writeInt(item.durability());}
            out.writeInt(world.fish().size());
            for(Fish fish:world.fish()){out.writeFloat(fish.x());out.writeFloat(fish.y());out.writeFloat(fish.z());out.writeFloat(fish.dx());out.writeFloat(fish.dz());}
        });
    }
    private static SaveStore.Decoded readState(DataInputStream in,World world) throws IOException {
        int length=in.readInt();if(length<1 || length>16*1024*1024 || length>in.available())throw new IOException("Некорректный размер мира");
        SaveStore.Decoded state=SaveStore.decode(in.readNBytes(length));
        int count=in.readInt();if(count<0 || count>100000)throw new IOException("Слишком много предметов");
        List<DroppedItem> drops=new ArrayList<>();
        for(int i=0;i<count;i++)drops.add(new DroppedItem(ItemType.valueOf(in.readUTF()),in.readInt(),in.readInt(),in.readInt(),in.readInt()));
        count=in.readInt();if(count<0 || count>1000)throw new IOException("Слишком много рыб");
        List<Fish> fish=new ArrayList<>();for(int i=0;i<count;i++)fish.add(new Fish(in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat()));
        if(world!=null && world.dimension()==state.world().dimension())world.applyNetworkEntities(drops,fish);
        return state;
    }
    private void broadcastState(World world,SaveStore.Pose pose,String name) throws IOException {
        if(players.keySet().stream().noneMatch(id->id!=0))return;
        Map<Dimension,byte[]> states=new EnumMap<>(Dimension.class);
        for(int peer:players.keySet())if(peer!=0) {
            Dimension dimension=players.get(peer).dimension();
            byte[] bytes=states.get(dimension);
            if(bytes==null){bytes=state(worldFor(dimension,world),pose,name);states.put(dimension,bytes);}
            transport.send(peer,NetworkSession.STATE,bytes);
        }
    }
    void simulateDimensions(float dt,World localWorld,Player localPlayer) {
        Set<Dimension> dimensions=new HashSet<>();dimensions.add(localWorld.dimension());
        for(NetworkPlayer pose:players.values())dimensions.add(pose.dimension());
        for(Dimension dimension:dimensions) {
            World simulated=worldFor(dimension,localWorld);List<Player> targets=new ArrayList<>();Map<Integer,Player> guests=new HashMap<>();
            if(dimension==localWorld.dimension())targets.add(localPlayer);
            for(NetworkPlayer pose:players.values())if(pose.id()!=0 && pose.dimension()==dimension) {
                Player player=new Player();player.position.set(pose.position());player.setCreative(creativePlayers.getOrDefault(pose.id(),false));
                targets.add(player);guests.put(pose.id(),player);
            }
            if(simulated!=localWorld){simulated.advanceTime(dt);simulated.updateFurnaces(dt);simulated.updateWater(dt);simulated.updateCrops(dt);}
            simulated.updateHellMobs(dt,targets);
            for(var entry:guests.entrySet())if(entry.getValue().health()<Player.MAX_HEALTH)try {
                int damage=Player.MAX_HEALTH-entry.getValue().health();transport.send(entry.getKey(),NetworkSession.DAMAGE,NetworkSession.packet(out->out.writeInt(damage)));
            } catch(IOException e){transport.reject(entry.getKey(),e.getMessage());}
        }
    }
    static Set<Integer> changedCells(World.Snapshot before,World.Snapshot after) {
        Set<Integer> keys=new HashSet<>(before.edits().keySet());keys.addAll(after.edits().keySet());
        keys.removeIf(k->Objects.equals(before.edits().get(k),after.edits().get(k)) && Objects.equals(before.rotations().get(k),after.rotations().get(k)));
        if(!before.doors().equals(after.doors())) { for(Door door:before.doors())keys.add((door.y()*World.WIDTH+door.x())*World.DEPTH+door.z());for(Door door:after.doors())keys.add((door.y()*World.WIDTH+door.x())*World.DEPTH+door.z()); }
        return keys;
    }
    @Override public void close(){transport.close();players.clear();}
}
