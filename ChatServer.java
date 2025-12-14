import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public final class ChatServer {
    private static final Logger logger = Logger.getLogger(ChatServer.class);
    private final ServerConfig config;
    
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduler;
    
  
    private final ConcurrentHashMap<String, ClientSession> sessionsByNick = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Socket, ClientSession> sessionsBySocket = new ConcurrentHashMap<>();
    
    
    private final ConcurrentHashMap<String, Set<ClientSession>> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MessageHistory> roomHistory = new ConcurrentHashMap<>();
    
 
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    
    public ChatServer(int port) {
        this.config = ServerConfig.getInstance();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
    
    public void start() throws IOException {
        int port = config.getPort();
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        
        logger.info(String.format("Server starting on port %d (maxClients=%d, heartbeatInterval=%ds)", 
            port, config.getMaxClients(), config.getHeartbeatInterval()));
       
        startHeartbeatScheduler();
        
      
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                clientSocket.setSoTimeout(config.getHeartbeatInterval() * 3 * 1000);
                
                if (currentConnections.get() >= config.getMaxClients()) {
                    logger.warn("Max clients reached, rejecting connection from " + clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                    continue;
                }
                
                handleNewConnection(clientSocket);
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    logger.info("Server socket closed, shutting down");
                    break;
                }
                logger.error("Error accepting connection", e);
            }
        }
    }
    
    private void handleNewConnection(Socket socket) {
        try {
            totalConnections.incrementAndGet();
            currentConnections.incrementAndGet();
            
            logger.info(String.format("New connection from %s (total: %d, current: %d)", 
                socket.getRemoteSocketAddress(), totalConnections.get(), currentConnections.get()));
            
            ClientSession session = new ClientSession(this, socket);
            sessionsBySocket.put(socket, session);
            session.start();
        } catch (IOException e) {
            logger.error("Error creating client session", e);
            currentConnections.decrementAndGet();
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    private void startHeartbeatScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkHeartbeats();
            } catch (Exception e) {
                logger.error("Error in heartbeat check", e);
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.SECONDS);
    }
    
    private void checkHeartbeats() {
        long now = System.currentTimeMillis();
        List<ClientSession> toRemove = new ArrayList<>();
        
        for (ClientSession session : sessionsBySocket.values()) {
            if (!session.isRunning()) {
                toRemove.add(session);
                continue;
            }
            
            long lastActivity = session.getLastActivityTime();
            long timeout = config.getHeartbeatInterval() * 3 * 1000L;
            
            if (now - lastActivity > timeout) {
                logger.warn(String.format("Session timeout for %s, disconnecting", session.getNick()));
                toRemove.add(session);
            }
        }
        
        for (ClientSession session : toRemove) {
            session.stop("Heartbeat timeout");
        }
    }
    
    public void onFrame(ClientSession cs, Frame f) {
        byte t = f.type;
        Map<String, String> kv = Kvp.decode(f.payloadText());
        
        try {
            switch (t) {
                case MsgType.HELLO -> handleHello(cs, kv);
                case MsgType.LOGIN -> handleLogin(cs, kv);
                case MsgType.LOGOUT -> handleLogout(cs, kv);
                case MsgType.JOIN -> handleJoin(cs, kv);
                case MsgType.LEAVE -> handleLeave(cs, kv);
                case MsgType.CHAT -> handleChat(cs, kv);
                case MsgType.WHISPER -> handleWhisper(cs, kv);
                case MsgType.PING -> handlePing(cs, kv);
                case MsgType.ROOM_LIST -> handleRoomList(cs, kv);
                case MsgType.ROOM_INFO -> handleRoomInfo(cs, kv);
                case MsgType.USER_LIST -> handleUserList(cs, kv);
                case MsgType.USER_INFO -> handleUserInfo(cs, kv);
                case MsgType.CHAT_HISTORY -> handleChatHistory(cs, kv);
                case MsgType.ROOM_CREATE -> handleRoomCreate(cs, kv);
                case MsgType.ROOM_DELETE -> handleRoomDelete(cs, kv);
                case MsgType.ROOM_SET_PASSWORD -> handleRoomSetPassword(cs, kv);
                case MsgType.ROOM_SET_DESCRIPTION -> handleRoomSetDescription(cs, kv);
                case MsgType.ROOM_SET_ADMIN -> handleRoomSetAdmin(cs, kv);
                case MsgType.ROOM_SET_LIMIT -> handleRoomSetLimit(cs, kv);
                case MsgType.STATS_REQUEST -> handleStatsRequest(cs, kv);
                case MsgType.MSG_SEARCH -> handleMessageSearch(cs, kv);
                case MsgType.MSG_BOOKMARK -> handleMessageBookmark(cs, kv);
                case MsgType.MSG_EDIT -> handleMessageEdit(cs, kv);
                case MsgType.MSG_DELETE -> handleMessageDelete(cs, kv);
                case MsgType.USER_BLOCK -> handleUserBlock(cs, kv);
                case MsgType.USER_UNBLOCK -> handleUserUnblock(cs, kv);
                case MsgType.FRIEND_ADD -> handleFriendAdd(cs, kv);
                case MsgType.FRIEND_REMOVE -> handleFriendRemove(cs, kv);
                case MsgType.FRIEND_LIST -> handleFriendList(cs, kv);
                default -> cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                        Kvp.encode(Kvp.kv("code", "BAD_TYPE", "msg", "Unknown type: " + MsgType.name(t)))));
            }
        } catch (IllegalStateException e) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "ILLEGAL_STATE", "msg", e.getMessage()))));
        } catch (Exception e) {
            logger.error("Error handling frame type " + MsgType.name(t), e);
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "EXCEPTION", "msg", 
                        e.getMessage() == null ? "Internal server error" : e.getMessage()))));
        }
    }
    
    public void onDisconnect(ClientSession cs) {
        sessionsBySocket.remove(cs.socket);
        currentConnections.decrementAndGet();
        
        if (cs.nick != null) {
            ClientSession removed = sessionsByNick.remove(cs.nick);
            if (removed == cs) {
                if (cs.room != null) {
                    leaveRoomInternal(cs, cs.room, true);
                }
                broadcastSystem("lobby", cs.nick + " disconnected");
                logger.info(String.format("User %s disconnected", cs.nick));
            }
        }
    }
    
    private void handleHello(ClientSession cs, Map<String, String> kv) {
        String clientInfo = kv.getOrDefault("client", "unknown");
        logger.debug(String.format("HELLO from %s (client: %s)", cs.socket.getRemoteSocketAddress(), clientInfo));
        
        cs.send(Frame.ofText(MsgType.WELCOME, cs.nextSeq(),
                Kvp.encode(Kvp.kv(
                    "server", config.getServerName(),
                    "version", "1.0",
                    "time", Instant.now().toString(),
                    "maxClients", String.valueOf(config.getMaxClients()),
                    "heartbeatInterval", String.valueOf(config.getHeartbeatInterval())
                ))));
    }
    
    private void handleLogin(ClientSession cs, Map<String, String> kv) {
        if (cs.nick != null) {
            cs.send(Frame.ofText(MsgType.LOGIN_FAIL, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("reason", "ALREADY_LOGGED_IN"))));
            return;
        }
        
        String nick = kv.getOrDefault("nick", "").trim();
        if (!isValidNick(nick)) {
            cs.send(Frame.ofText(MsgType.LOGIN_FAIL, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("reason", "BAD_NICK"))));
            return;
        }
        
      
        ClientSession prev = sessionsByNick.putIfAbsent(nick, cs);
        if (prev != null) {
            cs.send(Frame.ofText(MsgType.LOGIN_FAIL, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("reason", "DUP_NICK"))));
            return;
        }
        
        cs.nick = nick;
        cs.send(Frame.ofText(MsgType.LOGIN_OK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("nick", nick))));
        
       
        joinRoomInternal(cs, "lobby");
        broadcastSystem("lobby", nick + " joined");
        logger.info(String.format("User %s logged in", nick));
    }
    
    private void handleLogout(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String nick = cs.nick;
        cs.stop("User logout");
        logger.info(String.format("User %s logged out", nick));
    }
    
    private void handleJoin(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String room = kv.getOrDefault("room", "lobby").trim();
        if (room.isEmpty() || !isValidRoomName(room)) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_ROOM", "msg", "Invalid room name"))));
            return;
        }
        
        Set<ClientSession> roomMembers = rooms.get(room);
        if (roomMembers != null && roomMembers.size() >= config.getMaxRoomSize()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "ROOM_FULL", "msg", "Room is full"))));
            return;
        }
        
        String oldRoom = cs.room;
        joinRoomInternal(cs, room);
        
        cs.send(Frame.ofText(MsgType.JOIN_OK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", room, "oldRoom", oldRoom != null ? oldRoom : ""))));
        
        broadcastSystem(room, cs.nick + " entered room");
        logger.debug(String.format("User %s joined room %s", cs.nick, room));
    }
    
    private void handleLeave(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String room = (cs.room != null) ? cs.room : kv.getOrDefault("room", "lobby");
        if (room == null) return;
        
        leaveRoomInternal(cs, room, false);
        cs.send(Frame.ofText(MsgType.LEAVE_OK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", room))));
        
        broadcastSystem(room, cs.nick + " left room");
        logger.debug(String.format("User %s left room %s", cs.nick, room));
    }
    
    private void handleChat(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String room = kv.getOrDefault("room", cs.room == null ? "lobby" : cs.room);
        String msg = kv.getOrDefault("msg", "").trim();
        
        if (msg.isEmpty() || msg.length() > config.getMaxMessageLength()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_MESSAGE", 
                        "msg", "Message is empty or too long"))));
            return;
        }
        
        if (room == null || room.isEmpty()) room = "lobby";
        
        Set<ClientSession> members = rooms.get(room);
        if (members == null || !members.contains(cs)) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NOT_IN_ROOM", "msg", "Join room first"))));
            return;
        }
       
        MessageHistory history = roomHistory.computeIfAbsent(room, 
            r -> new MessageHistory(config.getMessageHistorySize()));
        history.add(cs.nick, room, msg);
        
        String payload = Kvp.encode(Kvp.kv(
                "room", room,
                "from", cs.nick,
                "msg", msg,
                "time", Instant.now().toString()
        ));
        Frame out = Frame.ofText(MsgType.CHAT, 0, payload);
        
      
        for (ClientSession m : members) {
            m.send(out);
        }
    }
    
    private void handleWhisper(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String to = kv.getOrDefault("to", "").trim();
        String msg = kv.getOrDefault("msg", "").trim();
        
        if (to.isEmpty() || msg.isEmpty() || msg.length() > config.getMaxMessageLength()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_WHISPER", "msg", "Invalid whisper"))));
            return;
        }
        
        ClientSession target = sessionsByNick.get(to);
        if (target == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_USER", "msg", to))));
            return;
        }
        
        String payload = Kvp.encode(Kvp.kv(
                "from", cs.nick,
                "to", to,
                "msg", msg,
                "time", Instant.now().toString()
        ));
        Frame out = Frame.ofText(MsgType.WHISPER, 0, payload);
        
        target.send(out);
        cs.send(out);
    }
    
    private void handlePing(ClientSession cs, Map<String, String> kv) {
        cs.updateLastActivity();
        String t = kv.getOrDefault("t", "");
        cs.send(Frame.ofText(MsgType.PONG, cs.nextSeq(), Kvp.encode(Kvp.kv("t", t))));
    }
    
    private void handleRoomList(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        
        List<String> roomNames = new ArrayList<>(rooms.keySet());
        String roomsStr = String.join(",", roomNames);
        
        cs.send(Frame.ofText(MsgType.ROOM_LIST_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("rooms", roomsStr, "count", String.valueOf(roomNames.size())))));
    }
    
    private void handleRoomInfo(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String room = kv.getOrDefault("room", "").trim();
        
        if (room.isEmpty()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_ROOM", "msg", "Room name required"))));
            return;
        }
        
        Set<ClientSession> members = rooms.get(room);
        if (members == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_ROOM", "msg", room))));
            return;
        }
        
        List<String> memberNicks = members.stream()
            .map(s -> s.nick)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        String membersStr = String.join(",", memberNicks);
        
        cs.send(Frame.ofText(MsgType.ROOM_INFO_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv(
                    "room", room,
                    "members", membersStr,
                    "count", String.valueOf(memberNicks.size())
                ))));
    }
    
    private void handleUserList(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        
        List<String> users = new ArrayList<>(sessionsByNick.keySet());
        String usersStr = String.join(",", users);
        
        cs.send(Frame.ofText(MsgType.USER_LIST_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("users", usersStr, "count", String.valueOf(users.size())))));
    }
    
    private void handleUserInfo(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String targetNick = kv.getOrDefault("nick", "").trim();
        
        if (targetNick.isEmpty()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_USER", "msg", "Nick required"))));
            return;
        }
        
        ClientSession target = sessionsByNick.get(targetNick);
        if (target == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_USER", "msg", targetNick))));
            return;
        }
        
        cs.send(Frame.ofText(MsgType.USER_INFO_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv(
                    "nick", targetNick,
                    "room", target.room != null ? target.room : "",
                    "status", "online"
                ))));
    }
    
    private void handleChatHistory(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String room = kv.getOrDefault("room", cs.room != null ? cs.room : "lobby").trim();
        int count = Integer.parseInt(kv.getOrDefault("count", "20"));
        
        MessageHistory history = roomHistory.get(room);
        if (history == null) {
            cs.send(Frame.ofText(MsgType.CHAT_HISTORY_RESP, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("room", room, "messages", ""))));
            return;
        }
        
        List<MessageHistory.HistoryEntry> entries = history.getRecent(count);
        List<String> messages = entries.stream()
            .map(e -> String.format("%s|%s|%s", e.from, e.timestamp.toString(), e.message))
            .collect(Collectors.toList());
        
        String messagesStr = String.join("\n", messages);
        
        cs.send(Frame.ofText(MsgType.CHAT_HISTORY_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", room, "messages", messagesStr, "count", String.valueOf(messages.size())))));
    }
    
    private void broadcastSystem(String room, String text) {
        Set<ClientSession> members = rooms.get(room);
        if (members == null) return;
        
        String payload = Kvp.encode(Kvp.kv(
                "room", room,
                "from", "SYSTEM",
                "msg", text,
                "time", Instant.now().toString()
        ));
        Frame out = Frame.ofText(MsgType.CHAT, 0, payload);
        for (ClientSession m : members) {
            m.send(out);
        }
    }
    
    private void joinRoomInternal(ClientSession cs, String room) {
        if (cs.room != null && cs.room.equals(room)) return;
        
        if (cs.room != null) {
            leaveRoomInternal(cs, cs.room, false);
        }
        
        Set<ClientSession> set = rooms.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet());
        set.add(cs);
        cs.room = room;
    }
    
    private void leaveRoomInternal(ClientSession cs, String room, boolean silent) {
        Set<ClientSession> set = rooms.get(room);
        if (set != null) {
            set.remove(cs);
            if (set.isEmpty()) {
                rooms.remove(room);
                roomHistory.remove(room);
            }
        }
        if (room.equals(cs.room)) cs.room = null;
    }
    
    private void requireLogin(ClientSession cs) {
        if (cs.nick == null) {
            throw new IllegalStateException("Not logged in");
        }
    }
    
    private boolean isValidNick(String nick) {
        if (nick == null) return false;
        int len = nick.length();
        if (len < config.getMinNickLength() || len > config.getMaxNickLength()) return false;
        
        for (int i = 0; i < len; i++) {
            char c = nick.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }
    
    private boolean isValidRoomName(String room) {
        if (room == null || room.isEmpty()) return false;
        if (room.length() > 32) return false;
        
        for (int i = 0; i < room.length(); i++) {
            char c = room.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ';
            if (!ok) return false;
        }
        return true;
    }
    
    private void handleRoomCreate(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        if (!isValidRoomName(roomName)) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_ROOM", "msg", "Invalid room name"))));
            return;
        }
        
        if (rooms.containsKey(roomName)) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "ROOM_EXISTS", "msg", "Room already exists"))));
            return;
        }
        
        rooms.put(roomName, ConcurrentHashMap.newKeySet());
        cs.send(Frame.ofText(MsgType.ROOM_CREATE, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "status", "created"))));
        logger.info(String.format("Room %s created by %s", roomName, cs.nick));
    }
    
    private void handleRoomDelete(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        Set<ClientSession> roomMembers = rooms.get(roomName);
        
        if (roomMembers == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_ROOM", "msg", roomName))));
            return;
        }
        
        if (roomName.equals("lobby")) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "CANNOT_DELETE", "msg", "Cannot delete lobby"))));
            return;
        }
        
        for (ClientSession member : roomMembers) {
            member.room = "lobby";
            joinRoomInternal(member, "lobby");
        }
        
        rooms.remove(roomName);
        roomHistory.remove(roomName);
        cs.send(Frame.ofText(MsgType.ROOM_DELETE, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "status", "deleted"))));
        logger.info(String.format("Room %s deleted by %s", roomName, cs.nick));
    }
    
    private void handleRoomSetPassword(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        String password = kv.getOrDefault("password", "");
        cs.send(Frame.ofText(MsgType.ROOM_SET_PASSWORD, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "status", "password_set"))));
    }
    
    private void handleRoomSetDescription(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        String description = kv.getOrDefault("description", "");
        cs.send(Frame.ofText(MsgType.ROOM_SET_DESCRIPTION, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "description", description))));
    }
    
    private void handleRoomSetAdmin(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        String admin = kv.getOrDefault("admin", "").trim();
        ClientSession adminSession = sessionsByNick.get(admin);
        if (adminSession == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_USER", "msg", admin))));
            return;
        }
        cs.send(Frame.ofText(MsgType.ROOM_SET_ADMIN, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "admin", admin))));
    }
    
    private void handleRoomSetLimit(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String roomName = kv.getOrDefault("room", "").trim();
        String limitStr = kv.getOrDefault("limit", "");
        try {
            int limit = Integer.parseInt(limitStr);
            cs.send(Frame.ofText(MsgType.ROOM_SET_LIMIT, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("room", roomName, "limit", limitStr))));
        } catch (NumberFormatException e) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_LIMIT", "msg", "Invalid limit"))));
        }
    }
    
    private void handleStatsRequest(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        StringBuilder stats = new StringBuilder();
        stats.append("Total Rooms: ").append(rooms.size()).append("\n");
        stats.append("Total Users: ").append(sessionsByNick.size()).append("\n");
        stats.append("Total Connections: ").append(totalConnections.get()).append("\n");
        stats.append("Current Connections: ").append(currentConnections.get()).append("\n");
        
        int totalMessages = 0;
        for (MessageHistory history : roomHistory.values()) {
            totalMessages += history.getAll().size();
        }
        stats.append("Total Messages: ").append(totalMessages).append("\n");
        
        cs.send(Frame.ofText(MsgType.STATS_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("data", stats.toString()))));
    }
    
    private void handleMessageSearch(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String keyword = kv.getOrDefault("keyword", "").trim();
        String roomName = kv.getOrDefault("room", cs.room != null ? cs.room : "lobby");
        
        MessageHistory history = roomHistory.get(roomName);
        if (history == null) {
            cs.send(Frame.ofText(MsgType.MSG_SEARCH_RESP, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("room", roomName, "results", ""))));
            return;
        }
        
        java.util.List<MessageHistory.HistoryEntry> all = history.getAll();
        java.util.List<String> results = new java.util.ArrayList<>();
        for (MessageHistory.HistoryEntry entry : all) {
            if (entry.message.toLowerCase().contains(keyword.toLowerCase()) ||
                entry.from.toLowerCase().contains(keyword.toLowerCase())) {
                results.add(String.format("%s|%s|%s|%s", entry.from, entry.room, entry.timestamp.toString(), entry.message));
            }
        }
        
        String resultsStr = String.join("\n", results);
        cs.send(Frame.ofText(MsgType.MSG_SEARCH_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("room", roomName, "results", resultsStr, "count", String.valueOf(results.size())))));
    }
    
    private void handleMessageBookmark(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String messageId = kv.getOrDefault("id", "");
        cs.send(Frame.ofText(MsgType.MSG_BOOKMARK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("id", messageId, "status", "bookmarked"))));
    }
    
    private void handleUserBlock(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String target = kv.getOrDefault("user", "").trim();
        cs.send(Frame.ofText(MsgType.USER_BLOCK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("user", target, "status", "blocked"))));
    }
    
    private void handleUserUnblock(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String target = kv.getOrDefault("user", "").trim();
        cs.send(Frame.ofText(MsgType.USER_UNBLOCK, cs.nextSeq(),
                Kvp.encode(Kvp.kv("user", target, "status", "unblocked"))));
    }
    
    private void handleFriendAdd(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String friend = kv.getOrDefault("friend", "").trim();
        if (friend.isEmpty()) {
            friend = kv.getOrDefault("user", "").trim();
        }
        ClientSession friendSession = sessionsByNick.get(friend);
        if (friendSession == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NO_SUCH_USER", "msg", friend))));
            return;
        }
        cs.send(Frame.ofText(MsgType.FRIEND_ADD, cs.nextSeq(),
                Kvp.encode(Kvp.kv("friend", friend, "status", "added"))));
    }
    
    private void handleFriendRemove(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String friend = kv.getOrDefault("friend", "").trim();
        if (friend.isEmpty()) {
            friend = kv.getOrDefault("user", "").trim();
        }
        cs.send(Frame.ofText(MsgType.FRIEND_REMOVE, cs.nextSeq(),
                Kvp.encode(Kvp.kv("friend", friend, "status", "removed"))));
    }
    
    private void handleFriendList(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        cs.send(Frame.ofText(MsgType.FRIEND_LIST_RESP, cs.nextSeq(),
                Kvp.encode(Kvp.kv("friends", "", "count", "0"))));
    }
    
    private void handleMessageEdit(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String original = kv.getOrDefault("original", "");
        String newMsg = kv.getOrDefault("new", "").trim();
        
        if (newMsg.isEmpty() || newMsg.length() > config.getMaxMessageLength()) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "INVALID_MESSAGE", "msg", "Message is empty or too long"))));
            return;
        }
        
        if (cs.room == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NOT_IN_ROOM", "msg", "Not in a room"))));
            return;
        }
        
        String payload = Kvp.encode(Kvp.kv(
                "room", cs.room,
                "from", cs.nick,
                "original", original,
                "new", newMsg,
                "time", Instant.now().toString()
        ));
        Frame out = Frame.ofText(MsgType.MSG_EDIT, 0, payload);
        
        Set<ClientSession> members = rooms.get(cs.room);
        if (members != null) {
            for (ClientSession m : members) {
                m.send(out);
            }
        }
        
        logger.info(String.format("Message edited by %s in room %s", cs.nick, cs.room));
    }
    
    private void handleMessageDelete(ClientSession cs, Map<String, String> kv) {
        requireLogin(cs);
        String message = kv.getOrDefault("message", "");
        
        if (cs.room == null) {
            cs.send(Frame.ofText(MsgType.ERROR, cs.nextSeq(),
                    Kvp.encode(Kvp.kv("code", "NOT_IN_ROOM", "msg", "Not in a room"))));
            return;
        }
        
        String payload = Kvp.encode(Kvp.kv(
                "room", cs.room,
                "from", cs.nick,
                "message", message,
                "time", Instant.now().toString()
        ));
        Frame out = Frame.ofText(MsgType.MSG_DELETE, 0, payload);
        
        Set<ClientSession> members = rooms.get(cs.room);
        if (members != null) {
            for (ClientSession m : members) {
                m.send(out);
            }
        }
        
        logger.info(String.format("Message deleted by %s in room %s", cs.nick, cs.room));
    }
    
    public void shutdown() {
        logger.info("Shutting down server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        
        executorService.shutdown();
        scheduler.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Server shutdown complete");
    }
    
    public static void main(String[] args) throws Exception {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : ServerConfig.getInstance().getPort();
        
        ChatServer server = new ChatServer(port);
        
     
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        
        try {
            server.start();
        } catch (Exception e) {
            Logger.getLogger(ChatServer.class).error("Server failed to start", e);
            System.exit(1);
        }
    }
}
