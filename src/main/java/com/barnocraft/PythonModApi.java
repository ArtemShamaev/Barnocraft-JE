package com.barnocraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Minimal line-based bridge for Python mods in the mods/ directory. */
public final class PythonModApi implements AutoCloseable {
    @FunctionalInterface public interface Host { String execute(String command); }
    private final Path directory; private final Host host;
    private final ExecutorService readers=Executors.newCachedThreadPool(r->{Thread t=new Thread(r,"python-mod-reader");t.setDaemon(true);return t;});
    private final List<Mod> mods=new ArrayList<>(); private boolean started;
    private static final class Mod {
        final Process process; final BufferedWriter input; final BlockingQueue<String> output=new LinkedBlockingQueue<>();
        Mod(Process process) throws IOException { this.process=process; input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8)); }
        void send(String line) { try { input.write(line); input.newLine(); input.flush(); } catch(IOException ignored) {} }
        void close() { try { input.close(); } catch(IOException ignored) {} process.destroy(); }
    }
    public PythonModApi(Path directory,Host host) { this.directory=Objects.requireNonNull(directory);this.host=Objects.requireNonNull(host); }
    public synchronized void start() {
        if(started)return; started=true;
        try { Files.createDirectories(directory); } catch(IOException ignored) { return; }
        try(var files=Files.list(directory)) { files.filter(p->p.toString().endsWith(".py")).sorted().forEach(this::startOne); } catch(IOException ignored) {}
    }
    private void startOne(Path script) {
        try {
            Process process=new ProcessBuilder("python3","-u",script.toAbsolutePath().toString()).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            Mod mod=new Mod(process); mods.add(mod);
            readers.submit(()->{ try(var reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) { String line; while((line=reader.readLine())!=null) if(!line.isBlank()) mod.output.offer(line.trim()); } catch(IOException ignored) {} });
            mod.send("ready");
        } catch(IOException ignored) {}
    }
    public synchronized void tick(World world,Player player) {
        if(!started)start();
        String event="tick "+Math.round(player.position.x)+" "+Math.round(player.position.y)+" "+Math.round(player.position.z)+" "+world.dayTime();
        for(Mod mod:mods) { if(mod.process.isAlive())mod.send(event); String command; while((command=mod.output.poll())!=null)host.execute(command); }
    }
    public synchronized int loadedMods() { return mods.size(); }
    @Override public synchronized void close() { for(Mod mod:mods)mod.close(); mods.clear(); readers.shutdownNow(); }
}
