import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;


public final class ServerConfig {
    private static final ServerConfig INSTANCE = new ServerConfig();
    

    private static final int DEFAULT_PORT = 5555;
    private static final int DEFAULT_MAX_CLIENTS = 1000;
    private static final int DEFAULT_MAX_ROOM_SIZE = 100;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 30; 
    private static final int DEFAULT_MESSAGE_HISTORY_SIZE = 100;
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 1000;
    private static final int DEFAULT_MAX_NICK_LENGTH = 16;
    private static final int DEFAULT_MIN_NICK_LENGTH = 2;
    
    private int port;
    private int maxClients;
    private int maxRoomSize;
    private int heartbeatInterval;
    private int messageHistorySize;
    private int maxMessageLength;
    private int maxNickLength;
    private int minNickLength;
    private String serverName;
    private boolean enableLogging;
    
    private ServerConfig() {
        loadDefaults();
        loadFromFile();
    }
    
    public static ServerConfig getInstance() {
        return INSTANCE;
    }
    
    private void loadDefaults() {
        this.port = DEFAULT_PORT;
        this.maxClients = DEFAULT_MAX_CLIENTS;
        this.maxRoomSize = DEFAULT_MAX_ROOM_SIZE;
        this.heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
        this.messageHistorySize = DEFAULT_MESSAGE_HISTORY_SIZE;
        this.maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
        this.maxNickLength = DEFAULT_MAX_NICK_LENGTH;
        this.minNickLength = DEFAULT_MIN_NICK_LENGTH;
        this.serverName = "ChatServer";
        this.enableLogging = true;
    }
    
    private void loadFromFile() {
        try (FileInputStream fis = new FileInputStream("server.properties")) {
            Properties props = new Properties();
            props.load(fis);
            
            port = Integer.parseInt(props.getProperty("port", String.valueOf(DEFAULT_PORT)));
            maxClients = Integer.parseInt(props.getProperty("maxClients", String.valueOf(DEFAULT_MAX_CLIENTS)));
            maxRoomSize = Integer.parseInt(props.getProperty("maxRoomSize", String.valueOf(DEFAULT_MAX_ROOM_SIZE)));
            heartbeatInterval = Integer.parseInt(props.getProperty("heartbeatInterval", String.valueOf(DEFAULT_HEARTBEAT_INTERVAL)));
            messageHistorySize = Integer.parseInt(props.getProperty("messageHistorySize", String.valueOf(DEFAULT_MESSAGE_HISTORY_SIZE)));
            maxMessageLength = Integer.parseInt(props.getProperty("maxMessageLength", String.valueOf(DEFAULT_MAX_MESSAGE_LENGTH)));
            maxNickLength = Integer.parseInt(props.getProperty("maxNickLength", String.valueOf(DEFAULT_MAX_NICK_LENGTH)));
            minNickLength = Integer.parseInt(props.getProperty("minNickLength", String.valueOf(DEFAULT_MIN_NICK_LENGTH)));
            serverName = props.getProperty("serverName", "ChatServer");
            enableLogging = Boolean.parseBoolean(props.getProperty("enableLogging", "true"));
        } catch (IOException e) {
          
        }
    }
    
    public int getPort() { return port; }
    public int getMaxClients() { return maxClients; }
    public int getMaxRoomSize() { return maxRoomSize; }
    public int getHeartbeatInterval() { return heartbeatInterval; }
    public int getMessageHistorySize() { return messageHistorySize; }
    public int getMaxMessageLength() { return maxMessageLength; }
    public int getMaxNickLength() { return maxNickLength; }
    public int getMinNickLength() { return minNickLength; }
    public String getServerName() { return serverName; }
    public boolean isLoggingEnabled() { return enableLogging; }
}

