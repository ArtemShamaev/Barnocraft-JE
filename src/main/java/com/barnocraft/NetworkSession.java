package com.barnocraft;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded TCP transport. World and scene state are only accessed by the game thread. */
final class NetworkSession implements AutoCloseable {
    static final int DEFAULT_PORT=25565, MAX_PLAYERS=8;
    static final int HELLO=1, WELCOME=2, STATE=3, POSES=4, ACTION=5, RESULT=6;
    private static final int MAGIC=0x42434E54, VERSION=1, MAX_FRAME=16*1024*1024;
    record Event(int peer,int type,byte[] data,String error) {}
    private final Map<Integer,Peer> peers=new ConcurrentHashMap<>();
    private final BlockingQueue<Event> events=new ArrayBlockingQueue<>(128);
    private final AtomicInteger ids=new AtomicInteger(1);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ServerSocket server;
    private Thread acceptThread;
    private NetworkSession(ServerSocket server) { this.server=server; }
    boolean hosting() { return server!=null; }
    int port() { return server==null?0:server.getLocalPort(); }
    static int parsePort(String text) {
        if(text==null || !text.matches("[0-9]{1,5}"))throw new IllegalArgumentException("Порт: число от 1 до 65535");
        int port=Integer.parseInt(text);
        if(port<1 || port>65535)throw new IllegalArgumentException("Порт: число от 1 до 65535");
        return port;
    }
    static NetworkSession host(int port) throws IOException {
        if(port<1 || port>65535)throw new IllegalArgumentException("Порт: число от 1 до 65535");
        ServerSocket socket=new ServerSocket();
        socket.setReuseAddress(true);
        try { socket.bind(new InetSocketAddress(port)); }
        catch(IOException e) { socket.close();throw e; }
        NetworkSession session=new NetworkSession(socket);
        session.acceptThread=thread("lan-accept",()->{
            while(!session.closed.get())try {
                Socket client=socket.accept();
                if(session.peers.size()>=MAX_PLAYERS-1) { client.close();continue; }
                session.add(session.ids.getAndIncrement(),client);
            } catch(IOException e) { if(!session.closed.get())session.offer(new Event(0,0,null,"Сервер остановлен: "+e.getMessage())); }
        });
        return session;
    }
    static NetworkSession connect(String address,int port) throws IOException {
        if(address.isBlank() || address.length()>253)throw new IOException("Введите IP-адрес или имя сервера");
        Socket socket=new Socket();
        try {
            socket.connect(new InetSocketAddress(address,port),5000);
            NetworkSession session=new NetworkSession(null);session.add(0,socket);return session;
        } catch(IOException | RuntimeException e) { socket.close();throw e; }
    }
    private void add(int id,Socket socket) throws IOException {
        socket.setTcpNoDelay(true);socket.setSoTimeout(15000);
        Peer peer=new Peer(id,socket);
        if(closed.get()) { socket.close();return; }
        peers.put(id,peer);
        thread("lan-write-"+id,peer::writeLoop);thread("lan-read-"+id,peer::readLoop);
    }
    Event poll() { return events.poll(); }
    Set<Integer> peerIds() { return Set.copyOf(peers.keySet()); }
    void send(int peer,int type,byte[] data) {
        Peer p=peers.get(peer);
        if(p!=null && (data.length>MAX_FRAME-1 || !p.outgoing.offer(new Event(peer,type,data,null))))p.close("Соединение слишком медленное");
    }
    void disconnect(int peer) { Peer p=peers.get(peer);if(p!=null)p.close("Соединение закрыто"); }
    private void offer(Event event) {
        if(!events.offer(event)) { Peer p=peers.get(event.peer());if(p!=null)p.close("Слишком много сетевых сообщений"); }
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        if(server!=null)try { server.close(); } catch(IOException ignored) {}
        if(acceptThread!=null && acceptThread!=Thread.currentThread())try { acceptThread.join(1000); } catch(InterruptedException e){Thread.currentThread().interrupt();}
        for(Peer peer:List.copyOf(peers.values()))peer.close("Соединение закрыто");
        events.clear();
    }
    private static Thread thread(String name,Runnable task) { Thread t=new Thread(task,name);t.setDaemon(true);t.start();return t; }
    private final class Peer {
        final int id; final Socket socket;
        final BlockingQueue<Event> outgoing=new ArrayBlockingQueue<>(32);
        final AtomicBoolean stopped=new AtomicBoolean();
        Peer(int id,Socket socket){this.id=id;this.socket=socket;}
        void writeLoop() {
            try(var out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                out.writeInt(MAGIC);out.writeInt(VERSION);out.flush();
                while(!stopped.get()) {
                    Event e=outgoing.poll(1,TimeUnit.SECONDS);if(e==null)continue;
                    out.writeInt(e.data().length+1);out.writeByte(e.type());out.write(e.data());out.flush();
                }
            } catch(IOException | InterruptedException e) { close("Соединение прервано"); }
        }
        void readLoop() {
            try(var in=new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
                if(in.readInt()!=MAGIC || in.readInt()!=VERSION)throw new IOException("Несовместимая версия игры");
                while(!stopped.get()) {
                    int size=in.readInt();if(size<1 || size>MAX_FRAME)throw new IOException("Некорректный сетевой пакет");
                    int type=in.readUnsignedByte();byte[] bytes=in.readNBytes(size-1);
                    if(bytes.length!=size-1)throw new EOFException();
                    if(!events.offer(new Event(id,type,bytes,null)))throw new IOException("Слишком много сетевых сообщений");
                }
            } catch(IOException e) { close(e instanceof SocketTimeoutException?"Сервер не отвечает":e.getMessage()==null?"Соединение закрыто":e.getMessage()); }
        }
        void close(String reason) {
            if(!stopped.compareAndSet(false,true))return;
            peers.remove(id,this);outgoing.clear();
            try { socket.close(); } catch(IOException ignored) {}
            // Do not recurse if the application event queue is already full.
            events.offer(new Event(id,0,null,reason));
        }
    }
    @FunctionalInterface interface Writer { void write(DataOutputStream out) throws IOException; }
    static byte[] packet(Writer writer) throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.write(out);}return bytes.toByteArray();
    }
    static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
}
