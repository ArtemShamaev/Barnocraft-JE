package com.barnocraft;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded TCP transport. World and scene state are only accessed by the game thread. */
final class NetworkSession implements AutoCloseable {
    static final int DEFAULT_PORT=25565, MAX_PLAYERS=8;
    static final int HELLO=1, WELCOME=2, STATE=3, POSES=4, ACTION=5, RESULT=6, ERROR=7, TRANSFER=8, DAMAGE=9;
    private static final int MAGIC=0x42434E54, VERSION=3, MAX_FRAME=16*1024*1024;
    record Event(int peer,int type,byte[] data,String error) {}
    private final Map<Integer,Peer> peers=new ConcurrentHashMap<>();
    private final Map<Integer,Event> latestStates=new ConcurrentHashMap<>(),latestPoses=new ConcurrentHashMap<>();
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
        try { socket.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"),port)); }
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
        Socket socket=new Socket(Proxy.NO_PROXY);
        try {
            socket.connect(new InetSocketAddress(address,port),15000);
            NetworkSession session=new NetworkSession(null);session.add(0,socket);return session;
        } catch(IOException | RuntimeException e) {
            socket.close();
            if(e instanceof SocketTimeoutException)throw new IOException("Нет ответа от "+address+":"+port+". Откройте мир для сети и проверьте порт / доступность LAN.",e);
            if(e instanceof ConnectException)throw new IOException("Сервер "+address+":"+port+" не запущен или порт закрыт. Нажмите у хозяина «Открыть для сети».",e);
            if(e instanceof UnknownHostException)throw new IOException("Адрес сервера не найден: "+address,e);
            throw e;
        }
    }
    static String lanAddresses() {
        List<String> addresses=new ArrayList<>();
        try {
            for(NetworkInterface adapter:Collections.list(NetworkInterface.getNetworkInterfaces())) {
                String name=adapter.getName();
                if(!adapter.isUp() || adapter.isLoopback() || adapter.isVirtual() || name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth") || name.startsWith("tun"))continue;
                for(InetAddress address:Collections.list(adapter.getInetAddresses()))if(address instanceof Inet4Address && !address.isLoopbackAddress())addresses.add(address.getHostAddress());
            }
        } catch(SocketException ignored) { }
        return addresses.isEmpty()?"127.0.0.1 (этот компьютер)":String.join(", ",addresses);
    }
    private void add(int id,Socket socket) throws IOException {
        socket.setTcpNoDelay(true);socket.setSoTimeout(15000);
        Peer peer=new Peer(id,socket);
        if(closed.get()) { socket.close();return; }
        peers.put(id,peer);
        thread("lan-write-"+id,peer::writeLoop);thread("lan-read-"+id,peer::readLoop);
    }
    Event poll() {
        Event next=events.peek();
        if(next!=null && (next.type()==0 || next.type()==ERROR || next.type()==HELLO || next.type()==WELCOME || next.type()==TRANSFER))return events.poll();
        Event state=takeLatest(latestStates);if(state!=null)return state;
        next=events.poll();return next!=null?next:takeLatest(latestPoses);
    }
    private static Event takeLatest(Map<Integer,Event> map) {
        for(var entry:map.entrySet())if(map.remove(entry.getKey(),entry.getValue()))return entry.getValue();
        return null;
    }
    Set<Integer> peerIds() { return Set.copyOf(peers.keySet()); }
    void send(int peer,int type,byte[] data) {
        Peer p=peers.get(peer);
        if(p==null || (p.rejectAt!=0 && type!=ERROR))return;
        if(data.length>MAX_FRAME-1){p.close("Мир слишком большой для сетевой передачи");return;}
        Event event=new Event(peer,type,data,null);
        if(type==STATE)p.state.set(event);
        else if(type==POSES)p.pose.set(event);
        else if(!p.outgoing.offer(event))p.close("Слишком много команд в очереди");
    }
    void disconnect(int peer) { Peer p=peers.get(peer);if(p!=null)p.close("Соединение закрыто"); }
    void reject(int peer,String reason) {
        Peer p=peers.get(peer);if(p==null || p.rejectAt!=0)return;
        String detail=reason==null?"Сервер отклонил запрос":reason;
        System.err.println("[LAN] Rejected "+p.socket.getRemoteSocketAddress()+": "+detail);
        p.rejectAt=System.nanoTime()+2_000_000_000L;p.state.set(null);p.pose.set(null);p.outgoing.clear();
        try { send(peer,ERROR,packet(out->out.writeUTF(detail))); }
        catch(IOException e){p.close(detail);}
    }
    private void offer(Event event) {
        if(!events.offer(event)) { Peer p=peers.get(event.peer());if(p!=null)p.close("Слишком много сетевых сообщений"); }
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        if(server!=null)try { server.close(); } catch(IOException ignored) {}
        if(acceptThread!=null && acceptThread!=Thread.currentThread())try { acceptThread.join(1000); } catch(InterruptedException e){Thread.currentThread().interrupt();}
        for(Peer peer:List.copyOf(peers.values()))peer.close("Соединение закрыто");
        events.clear();latestStates.clear();latestPoses.clear();
    }
    private static Thread thread(String name,Runnable task) { Thread t=new Thread(task,name);t.setDaemon(true);t.start();return t; }
    private final class Peer {
        final int id; final Socket socket;
        final BlockingQueue<Event> outgoing=new ArrayBlockingQueue<>(32);
        final AtomicBoolean stopped=new AtomicBoolean();
        final AtomicReference<Event> state=new AtomicReference<>(),pose=new AtomicReference<>();
        volatile long rejectAt;
        Peer(int id,Socket socket){this.id=id;this.socket=socket;}
        void writeLoop() {
            try(var out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                out.writeInt(MAGIC);out.writeInt(VERSION);out.flush();
                long lastWrite=System.nanoTime();
                while(!stopped.get()) {
                    if(rejectAt!=0 && System.nanoTime()>=rejectAt){close("Сервер отклонил запрос");return;}
                    Event event=outgoing.poll(25,TimeUnit.MILLISECONDS);
                    // Welcome must precede updates; state must precede its inventory acknowledgement.
                    if(event!=null && (event.type()==HELLO || event.type()==WELCOME || event.type()==TRANSFER)){write(out,event);event=null;lastWrite=System.nanoTime();}
                    Event update=state.getAndSet(null);if(update!=null){write(out,update);lastWrite=System.nanoTime();}
                    if(event!=null){write(out,event);lastWrite=System.nanoTime();}
                    update=pose.getAndSet(null);if(update!=null){write(out,update);lastWrite=System.nanoTime();}
                    if(System.nanoTime()-lastWrite>=1_000_000_000L) {
                        out.writeInt(1);out.writeByte(0);lastWrite=System.nanoTime();
                    }
                    out.flush();
                }
            } catch(IOException | InterruptedException e) { close("Соединение прервано"); }
        }
        private void write(DataOutputStream out,Event event) throws IOException {
            out.writeInt(event.data().length+1);out.writeByte(event.type());out.write(event.data());
        }
        void readLoop() {
            try(var in=new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
                if(in.readInt()!=MAGIC || in.readInt()!=VERSION)throw new IOException("Разные версии игры. Обновите игру на обоих компьютерах");
                while(!stopped.get()) {
                    int size=in.readInt();if(size<1 || size>MAX_FRAME)throw new IOException("Некорректный сетевой пакет");
                    int type=in.readUnsignedByte();byte[] bytes=in.readNBytes(size-1);
                    if(bytes.length!=size-1)throw new EOFException();
                    if(type==0){if(size!=1)throw new IOException("Некорректная проверка связи");continue;}
                    if(rejectAt!=0)continue;
                    Event event=new Event(id,type,bytes,null);
                    if(type==STATE)latestStates.put(id,event);
                    else if(type==POSES)latestPoses.put(id,event);
                    else if(!events.offer(event))throw new IOException("Слишком много сетевых команд");
                }
            } catch(IOException e) { close(e instanceof SocketTimeoutException?"Сервер не отвечает":e.getMessage()==null?"Соединение закрыто":e.getMessage()); }
        }
        void close(String reason) {
            if(!stopped.compareAndSet(false,true))return;
            peers.remove(id,this);outgoing.clear();state.set(null);pose.set(null);latestStates.remove(id);latestPoses.remove(id);
            try { socket.close(); } catch(IOException ignored) {}
            if(!closed.get())System.err.println("[LAN] "+socket.getRemoteSocketAddress()+": "+reason);
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
