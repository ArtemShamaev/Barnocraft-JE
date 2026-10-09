package com.barnocraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Mod API v2: bounded JSON-RPC queues; Python IO never runs on the render thread. */
public final class PythonModApi implements AutoCloseable {
    static final int API_VERSION=2,MAX_LINE=262144;
    private final Path directory;private final World world;private final Player player;private final Inventory inventory;
    private final ExecutorService io=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"mod-io");t.setDaemon(true);return t;});
    private final List<Mod> mods=new ArrayList<>();private boolean started;private float clock;
    private static final class Mod {
        final String id;final Process process;final BlockingQueue<String> incoming=new ArrayBlockingQueue<>(256),outgoing=new ArrayBlockingQueue<>(128);
        final AtomicReference<String> tick=new AtomicReference<>();volatile boolean failed;boolean exitReported;float surfaceClock;
        Mod(String id,Process process){this.id=id;this.process=process;}
        void send(Object message){if(!outgoing.offer(Json.stringify(message)))fail("output queue overflow");}
        void fail(String reason){if(!failed)System.err.println("[mod "+id+"] disabled: "+reason);failed=true;process.destroy();}
    }
    public PythonModApi(Path directory,World world,Player player,Inventory inventory){this.directory=directory.toAbsolutePath().normalize();this.world=world;this.player=player;this.inventory=inventory;}
    public static Path defaultDirectory(){
        String configured=System.getProperty("barnocraft.mods");
        if(configured!=null && !configured.isBlank())return Path.of(configured).toAbsolutePath().normalize();
        try {
            return directoryBesideProject(Path.of(PythonModApi.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch(Exception ignored) { return Path.of("mods").toAbsolutePath().normalize(); }
    }
    static Path directoryBesideProject(Path location) {
        Path base=location.toAbsolutePath().normalize();
        if(!Files.isDirectory(base))base=base.getParent();
        if(base.getFileName()!=null && (base.getFileName().toString().equals("classes") || base.getFileName().toString().equals("test-classes")))base=base.getParent();
        if(base.getFileName()!=null && base.getFileName().toString().equals("target"))base=base.getParent();
        return base.resolve("mods");
    }
    public static String standaloneId(Path script){
        String filename=script.getFileName().toString();
        String id=filename.substring(0,filename.length()-3).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]","_").replaceAll("_+","_");
        return id.isEmpty() || id.charAt(0)<'a' || id.charAt(0)>'z' ? "mod_"+id : id;
    }
    public void start(){if(started)return;started=true;
        if(!Files.isDirectory(directory))return;
        try(var entries=Files.list(directory)){for(Path path:entries.sorted().toList()){
            boolean singleFile=Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".py");
            if(!singleFile && !Files.isDirectory(path))continue;
            Path moduleDirectory=singleFile?directory:path;
            Path file=singleFile?null:path.resolve("mod.json");
            if(!singleFile && !Files.isRegularFile(file))continue;
            try{Map<String,Object> manifest;
                if(singleFile){String filename=path.getFileName().toString();String id=standaloneId(path);
                    manifest=new LinkedHashMap<>();manifest.put("id",id);manifest.put("name",id);manifest.put("version","1.0.0");manifest.put("api",API_VERSION);manifest.put("entry",filename);manifest.put("enabled",true);
                }else{if(Files.size(file)>1048576)throw new IOException("manifest too large");manifest=Json.obj(Json.parse(Files.readString(file)));}
                if(Boolean.FALSE.equals(manifest.get("enabled")))continue;
                String id=Json.str(manifest,"id","");if(!id.matches("[a-z][a-z0-9_]*") || mods.stream().anyMatch(m->m.id.equals(id)))throw new IOException("invalid/duplicate mod id");
                if(Json.integer(manifest,"api",0,0,100)!=API_VERSION)throw new IOException("requires API 2");
                Path script=moduleDirectory.resolve(Json.str(manifest,"entry","main.py")).normalize();if(!script.startsWith(moduleDirectory) || !Files.isRegularFile(script))throw new IOException("invalid entry script");
                world.mods.register(id,manifest);Process process=launch(script,moduleDirectory);Mod mod=new Mod(id,process);mods.add(mod);
                io.submit(()->read(mod));io.submit(()->write(mod));
                mod.send(Map.of("event","load","data",Map.of("api",API_VERSION,"mod",id,"state",world.mods.storage.getOrDefault(id,Map.of()))));
            }catch(Exception e){System.err.println("[mods] "+path.getFileName()+": "+e.getMessage());world.mods.notice="Не загружен мод "+path.getFileName()+": "+e.getMessage();}
        }}catch(IOException e){System.err.println("[mods] "+e.getMessage());}
    }
    private Process launch(Path script,Path cwd)throws IOException {
        String override=System.getenv("BARNOCRAFT_PYTHON");
        List<List<String>> candidates=override!=null?List.of(List.of(override)):List.of(List.of("python3"),List.of("python"),List.of("py","-3"));
        IOException failure=null;
        for(List<String> command:candidates){try{List<String> args=new ArrayList<>(command);args.add("-u");args.add(script.toString());ProcessBuilder pb=new ProcessBuilder(args).directory(cwd.toFile());
            pb.environment().put("PYTHONPATH",directory.resolve("sdk").toString());pb.environment().put("PYTHONIOENCODING","utf-8");
            pb.redirectError(ProcessBuilder.Redirect.appendTo(cwd.resolve("mod.log").toFile()));return pb.start();
        }catch(IOException e){failure=e;}}
        throw new IOException("Python 3 not found; set BARNOCRAFT_PYTHON",failure);
    }
    private void read(Mod mod){try(var r=new InputStreamReader(mod.process.getInputStream(),StandardCharsets.UTF_8)){
        StringBuilder line=new StringBuilder();int c;while((c=r.read())!=-1){if(c=='\n'){if(!line.isEmpty() && !mod.incoming.offer(line.toString())){mod.fail("request queue overflow");break;}line.setLength(0);}else if(c!='\r'){line.append((char)c);if(line.length()>MAX_LINE){mod.fail("message too large");break;}}}
    }catch(IOException e){if(mod.process.isAlive())mod.fail(e.getMessage());}}
    private void write(Mod mod){try(var w=new BufferedWriter(new OutputStreamWriter(mod.process.getOutputStream(),StandardCharsets.UTF_8))){while(mod.process.isAlive() && !Thread.currentThread().isInterrupted()){
        String message=mod.outgoing.poll(40,TimeUnit.MILLISECONDS);if(message==null)message=mod.tick.getAndSet(null);if(message!=null){w.write(message);w.newLine();w.flush();}
    }}catch(IOException|InterruptedException e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();if(mod.process.isAlive())mod.fail("writer stopped");}}
    public void tick(float dt,int selected,float yaw){if(!started)start();clock+=dt;int budget=128;
        for(Mod mod:mods){mod.surfaceClock=Math.max(0,mod.surfaceClock-dt);if(!mod.process.isAlive() || mod.failed){if(!mod.exitReported){mod.exitReported=true;world.mods.notice="Мод "+mod.id+" остановлен. Подробности в mod.log";}world.mods.overlays.keySet().removeIf(k->k.startsWith(mod.id+":"));continue;}
            for(int i=0;i<32 && budget>0;i++,budget--){String line=mod.incoming.poll();if(line==null)break;Object requestId=null;
                try{var request=Json.obj(Json.parse(line));requestId=request.get("id");if(!(requestId instanceof Number || requestId instanceof String))throw new IllegalArgumentException("request id required");
                    String method=Json.str(request,"method","");Object result=call(mod,method,Json.obj(request.getOrDefault("params",Map.of())));
                    mod.send(Map.of("id",requestId,"result",result==null?Map.of():result));
                }catch(Exception e){mod.send(Map.of("id",requestId==null?0:requestId,"error",Map.of("code","invalid_request","message",String.valueOf(e.getMessage()))));}
            }
        }
        List<Map<String,Object>> events=world.mods.drainEvents();for(Mod mod:mods)if(mod.process.isAlive() && !mod.failed)for(var event:events)mod.send(event);
        if(clock>=.1f){clock=0;String message=Json.stringify(Map.of("event","tick","data",world.mods.snapshot(player,inventory,selected,yaw)));for(Mod mod:mods)if(mod.process.isAlive() && !mod.failed)mod.tick.set(message);}
    }
    private Object call(Mod mod,String method,Map<String,Object> p){ModWorld state=world.mods;
        return switch(method){
            case "api.info" -> Map.of("version",API_VERSION,"mod",mod.id,"tick_hz",10);
            case "state.set" -> {String data=Json.stringify(p);if(data.length()>65536)throw new IllegalArgumentException("state too large");state.storage.put(mod.id,Json.parse(data));yield true;}
            case "entity.spawn" -> {String type=ModWorld.owned(mod.id,Json.str(p,"type",""));float x=(float)Json.num(p,"x",player.position.x),z=(float)Json.num(p,"z",player.position.z);
                if(Math.hypot(x-player.position.x,z-player.position.z)>64)throw new IllegalArgumentException("Spawn must be within 64 blocks");yield state.spawn(type,x,z).data();}
            case "entity.goal" -> {var e=state.controlled(mod.id,Json.str(p,"id",""));e.goalX=(float)ModWorld.bounded(p,"x",e.x,0,world.width()-1);e.goalZ=(float)ModWorld.bounded(p,"z",e.z,0,world.depth()-1);e.stop=(float)ModWorld.bounded(p,"stop",1,0,20);e.goalTime=1.5f;yield true;}
            case "entity.teleport" -> {var e=state.controlled(mod.id,Json.str(p,"id",""));float x=(float)Json.num(p,"x",e.x),z=(float)Json.num(p,"z",e.z);
                if(Math.hypot(x-player.position.x,z-player.position.z)>32)throw new IllegalArgumentException("Teleport must stay near the player");
                float y=state.surface((int)x,(int)z)+1;if(!state.fits(e,x,y,z) || world.get((int)x,(int)y-1,(int)z)==Block.WATER)yield false;
                e.x=x;e.y=y;e.z=z;e.vy=0;e.goalTime=0;yield true;}
            case "entity.attack", "entity.shoot" -> {var e=state.controlled(mod.id,Json.str(p,"id",""));yield state.attack(e,Json.str(p,"target",""),method.endsWith("shoot"),player);}
            case "entity.remove" -> {var e=state.controlled(mod.id,Json.str(p,"id",""));state.entities.remove(e.id);yield true;}
            case "world.get_block" -> world.get(Json.integer(p,"x",0,0,world.width()-1),Json.integer(p,"y",0,0,World.HEIGHT-1),Json.integer(p,"z",0,0,world.depth()-1)).name();
            case "world.surface" -> {
                if(mod.surfaceClock>0)throw new IllegalArgumentException("Surface query limited to 2 Hz");mod.surfaceClock=.5f;
                int size=Json.integer(p,"size",25,3,33),step=Json.integer(p,"step",2,1,4);
                int ox=(int)player.position.x-size/2*step,oz=(int)player.position.z-size/2*step;List<Object> blocks=new ArrayList<>(),heights=new ArrayList<>();
                for(int z=0;z<size;z++){List<String> row=new ArrayList<>();List<Integer> hs=new ArrayList<>();for(int x=0;x<size;x++){int bx=ox+x*step,bz=oz+z*step,y=state.surface(bx,bz);row.add(world.get(bx,y,bz).name());hs.add(y);}blocks.add(row);heights.add(hs);}
                yield Map.of("x",ox,"z",oz,"step",step,"size",size,"blocks",blocks,"heights",heights);
            }
            case "ui.image" -> {String id=ModWorld.owned(mod.id,Json.str(p,"id",""));ModWorld.validatePixels(Json.list(p.get("pixels")),64);if(!state.overlays.containsKey(id) && state.overlays.size()>=4)throw new IllegalArgumentException("Overlay limit");if(Json.str(p,"title","").length()>64)throw new IllegalArgumentException("Title too long");state.overlays.put(id,Map.copyOf(p));yield true;}
            case "ui.remove" -> {state.overlays.remove(ModWorld.owned(mod.id,Json.str(p,"id","")));yield true;}
            case "log" -> {System.err.println("[mod "+mod.id+"] "+Json.str(p,"message",""));yield true;}
            default -> throw new IllegalArgumentException("Unknown API method: "+method);
        };
    }
    public int loadedMods(){return (int)mods.stream().filter(m->!m.failed && m.process.isAlive()).count();}
    @Override public void close(){for(Mod m:mods){m.send(Map.of("event","unload","data",Map.of()));m.process.destroy();io.submit(()->{try{if(!m.process.waitFor(500,TimeUnit.MILLISECONDS)){m.process.descendants().forEach(ProcessHandle::destroyForcibly);m.process.destroyForcibly();}}catch(InterruptedException e){Thread.currentThread().interrupt();}});}mods.clear();io.shutdown();}
}
