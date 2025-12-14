import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public final class ChatClient {
    private static final Logger logger = Logger.getLogger(ChatClient.class);
    
    private final String host;
    private final int port;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    
    private final BlockingQueue<Frame> inbound = new LinkedBlockingQueue<>();
    private final AtomicInteger seq = new AtomicInteger(1);
    
    private volatile boolean running = true;
    private volatile String nick = null;
    private volatile String room = "lobby";
    
    private Thread readerThread;
    private ScheduledExecutorService heartbeatScheduler;
    
    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    private int nextSeq() {
        return seq.getAndIncrement();
    }
    
    public void start() throws Exception {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000); 
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            readerThread = new Thread(this::readerLoop, "ClientReader");
            readerThread.setDaemon(true);
            readerThread.start();
            
            heartbeatScheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
            heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
            
        
            send(Frame.ofText(MsgType.HELLO, nextSeq(), Kvp.encode(Kvp.kv("client", "java"))));
            Frame welcome = takeType(MsgType.WELCOME, 5, TimeUnit.SECONDS);
            if (welcome == null) {
                throw new IOException("No WELCOME from server");
            }
            
            Map<String, String> welcomeKv = Kvp.decode(welcome.payloadText());
            System.out.println(String.format("[SERVER] %s v%s - %s", 
                welcomeKv.getOrDefault("server", "ChatServer"),
                welcomeKv.getOrDefault("version", "1.0"),
                welcomeKv.getOrDefault("time", "")));
            
          
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("nick (2~16, [a-zA-Z0-9_-]): ");
            String n = br.readLine();
            
            if (n == null || n.trim().isEmpty()) {
                System.out.println("[ERROR] Nickname required");
                shutdown();
                return;
            }
            
            send(Frame.ofText(MsgType.LOGIN, nextSeq(), Kvp.encode(Kvp.kv("nick", n.trim()))));
            
            Frame loginResp = takeAnyOf(5, TimeUnit.SECONDS, MsgType.LOGIN_OK, MsgType.LOGIN_FAIL);
            if (loginResp == null) {
                throw new IOException("No LOGIN response");
            }
            
            if (loginResp.type == MsgType.LOGIN_FAIL) {
                Map<String, String> failKv = Kvp.decode(loginResp.payloadText());
                System.out.println("[LOGIN_FAIL] " + failKv.getOrDefault("reason", "Unknown error"));
                shutdown();
                return;
            }
            
            Map<String, String> ok = Kvp.decode(loginResp.payloadText());
            nick = ok.getOrDefault("nick", n.trim());
            System.out.println("[LOGIN_OK] nick=" + nick);
            
            printHelp();
         

            while (running) {
                String line = br.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                
                try {
                    handleCommand(line);
                } catch (Exception e) {
                    System.out.println("[ERROR] " + e.getMessage());
                }
            }
        } finally {
            shutdown();
        }
    }
    
    private void printHelp() {
        System.out.println("\n=== Commands ===");
        System.out.println("/join <room>     - Join a room");
        System.out.println("/leave           - Leave current room");
        System.out.println("/rooms           - List all rooms");
        System.out.println("/roominfo <room> - Get room information");
        System.out.println("/users           - List all users");
        System.out.println("/userinfo <nick> - Get user information");
        System.out.println("/history [room] [count] - Get chat history");
        System.out.println("/w <nick> <msg>  - Send whisper");
        System.out.println("/ping            - Send ping");
        System.out.println("/quit            - Quit");
        System.out.println("\nType message to chat in current room: " + room);
        System.out.println("=====================================\n");
    }
    
    private void handleCommand(String line) throws IOException {
        if (line.startsWith("/quit")) {
            send(Frame.ofText(MsgType.LOGOUT, nextSeq(), Kvp.encode(Kvp.kv())));
            shutdown();
        } else if (line.startsWith("/join ")) {
            String roomName = line.substring(6).trim();
            if (roomName.isEmpty()) roomName = "lobby";
            send(Frame.ofText(MsgType.JOIN, nextSeq(), Kvp.encode(Kvp.kv("room", roomName))));
            try {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.JOIN_OK, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.JOIN_OK) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    room = kv.getOrDefault("room", roomName);
                    System.out.println("[JOIN_OK] Joined room: " + room);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.startsWith("/leave")) {
            send(Frame.ofText(MsgType.LEAVE, nextSeq(), Kvp.encode(Kvp.kv("room", room))));
            try {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.LEAVE_OK, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.LEAVE_OK) {
                    System.out.println("[LEAVE_OK] Left room: " + room);
                    room = "lobby";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.equals("/rooms")) {
            send(Frame.ofText(MsgType.ROOM_LIST, nextSeq(), Kvp.encode(Kvp.kv())));
            try {
                Frame resp = takeType(MsgType.ROOM_LIST_RESP, 3, TimeUnit.SECONDS);
                if (resp != null) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    String roomsStr = kv.getOrDefault("rooms", "");
                    int count = Integer.parseInt(kv.getOrDefault("count", "0"));
                    System.out.println(String.format("[ROOMS] Total: %d", count));
                    if (!roomsStr.isEmpty()) {
                        String[] rooms = roomsStr.split(",");
                        for (String r : rooms) {
                            System.out.println("  - " + r);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.startsWith("/roominfo ")) {
            String roomName = line.substring(10).trim();
            send(Frame.ofText(MsgType.ROOM_INFO, nextSeq(), Kvp.encode(Kvp.kv("room", roomName))));
            try {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.ROOM_INFO_RESP, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.ROOM_INFO_RESP) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    System.out.println(String.format("[ROOM_INFO] %s - Members: %s (count: %s)", 
                        kv.getOrDefault("room", ""),
                        kv.getOrDefault("members", ""),
                        kv.getOrDefault("count", "0")));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.equals("/users")) {
            send(Frame.ofText(MsgType.USER_LIST, nextSeq(), Kvp.encode(Kvp.kv())));
            try {
                Frame resp = takeType(MsgType.USER_LIST_RESP, 3, TimeUnit.SECONDS);
                if (resp != null) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    String usersStr = kv.getOrDefault("users", "");
                    int count = Integer.parseInt(kv.getOrDefault("count", "0"));
                    System.out.println(String.format("[USERS] Total: %d", count));
                    if (!usersStr.isEmpty()) {
                        String[] users = usersStr.split(",");
                        for (String u : users) {
                            System.out.println("  - " + u);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.startsWith("/userinfo ")) {
            String targetNick = line.substring(10).trim();
            send(Frame.ofText(MsgType.USER_INFO, nextSeq(), Kvp.encode(Kvp.kv("nick", targetNick))));
            try {
                Frame resp = takeAnyOf(3, TimeUnit.SECONDS, MsgType.USER_INFO_RESP, MsgType.ERROR);
                if (resp != null && resp.type == MsgType.USER_INFO_RESP) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    System.out.println(String.format("[USER_INFO] %s - Room: %s, Status: %s", 
                        kv.getOrDefault("nick", ""),
                        kv.getOrDefault("room", ""),
                        kv.getOrDefault("status", "")));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.startsWith("/history")) {
            String[] parts = line.split(" ", 3);
            String roomName = parts.length > 1 ? parts[1].trim() : room;
            String countStr = parts.length > 2 ? parts[2].trim() : "20";
            int count = Integer.parseInt(countStr);
            
            send(Frame.ofText(MsgType.CHAT_HISTORY, nextSeq(), 
                Kvp.encode(Kvp.kv("room", roomName, "count", String.valueOf(count)))));
            try {
                Frame resp = takeType(MsgType.CHAT_HISTORY_RESP, 3, TimeUnit.SECONDS);
                if (resp != null) {
                    Map<String, String> kv = Kvp.decode(resp.payloadText());
                    String messagesStr = kv.getOrDefault("messages", "");
                    System.out.println(String.format("[HISTORY] Room: %s, Messages: %s", 
                        kv.getOrDefault("room", ""), kv.getOrDefault("count", "0")));
                    if (!messagesStr.isEmpty()) {
                        String[] messages = messagesStr.split("\n");
                        for (String msg : messages) {
                            String[] parts2 = msg.split("\\|", 3);
                            if (parts2.length == 3) {
                                System.out.println(String.format("  [%s] %s: %s", 
                                    parts2[1], parts2[0], parts2[2]));
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (line.startsWith("/w ")) {
            String rest = line.substring(3).trim();
            int sp = rest.indexOf(' ');
            if (sp <= 0) {
                System.out.println("usage: /w nick msg");
                return;
            }
            String to = rest.substring(0, sp);
            String msg = rest.substring(sp + 1);
            send(Frame.ofText(MsgType.WHISPER, nextSeq(), Kvp.encode(Kvp.kv("to", to, "msg", msg))));
        } else if (line.startsWith("/ping")) {
            send(Frame.ofText(MsgType.PING, nextSeq(), Kvp.encode(Kvp.kv("t", Instant.now().toString()))));
        } else {
         
            send(Frame.ofText(MsgType.CHAT, nextSeq(), Kvp.encode(Kvp.kv("room", room, "msg", line))));
        }
    }
    
    private void sendHeartbeat() {
        if (running) {
            try {
                send(Frame.ofText(MsgType.PING, nextSeq(), Kvp.encode(Kvp.kv("t", Instant.now().toString()))));
            } catch (IOException e) {
                logger.warn("Error sending heartbeat", e);
            }
        }
    }
    
    private void send(Frame f) throws IOException {
        if (!running || f == null) return;
        synchronized (out) {
            f.writeTo(out);
        }
    }
    
    private void readerLoop() {
        try {
            while (running) {
                try {
                    Frame f = Frame.readFrom(in);
                    inbound.offer(f);
                    renderFrame(f);
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                } catch (EOFException e) {
                    logger.debug("EOF received");
                    break;
                } catch (IOException e) {
                    if (running) {
                        logger.warn("Reader error: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error in reader loop", e);
        } finally {
            running = false;
            if (running) {
                System.out.println("[DISCONNECTED]");
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    private void renderFrame(Frame f) {
        Map<String, String> kv = Kvp.decode(f.payloadText());
        
        switch (f.type) {
            case MsgType.CHAT -> {
                String roomName = kv.getOrDefault("room", "?");
                String from = kv.getOrDefault("from", "?");
                String msg = kv.getOrDefault("msg", "");
                System.out.println(String.format("[%s] %s: %s", roomName, from, msg));
            }
            case MsgType.WHISPER -> {
                String from = kv.getOrDefault("from", "?");
                String to = kv.getOrDefault("to", "?");
                String msg = kv.getOrDefault("msg", "");
                System.out.println(String.format("[WHISPER] %s -> %s: %s", from, to, msg));
            }
            case MsgType.ERROR -> {
                String code = kv.getOrDefault("code", "UNKNOWN");
                String msg = kv.getOrDefault("msg", "");
                System.out.println(String.format("[ERROR] %s: %s", code, msg));
            }
            case MsgType.PONG -> {
                String t = kv.getOrDefault("t", "");
                System.out.println(String.format("[PONG] t=%s", t));
            }
            default -> {
                logger.debug("Received frame type: " + MsgType.name(f.type));
            }
        }
    }
    
    private Frame takeType(byte type, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Frame f = inbound.poll(50, TimeUnit.MILLISECONDS);
            if (f == null) continue;
            if (f.type == type) return f;
        }
        return null;
    }
    
    private Frame takeAnyOf(long timeout, TimeUnit unit, byte... types) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Frame f = inbound.poll(50, TimeUnit.MILLISECONDS);
            if (f == null) continue;
            for (byte t : types) {
                if (f.type == t) return f;
            }
        }
        return null;
    }
    
    private void shutdown() {
        running = false;
        
        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        
        System.out.println("[QUIT]");
    }
    
    public static void main(String[] args) throws Exception {
        String host = (args.length >= 1) ? args[0] : "127.0.0.1";
        int port = (args.length >= 2) ? Integer.parseInt(args[1]) : 5555;
        
        try {
            new ChatClient(host, port).start();
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
