import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public final class Logger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final boolean ENABLED = ServerConfig.getInstance().isLoggingEnabled();
    
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    private final String name;
    
    private Logger(String name) {
        this.name = name;
    }
    
    public static Logger getLogger(Class<?> clazz) {
        return new Logger(clazz.getSimpleName());
    }
    
    public static Logger getLogger(String name) {
        return new Logger(name);
    }
    
    private void log(Level level, String message, Throwable throwable) {
        if (!ENABLED) return;
        
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logMessage = String.format("[%s] [%s] [%s] %s", 
            timestamp, level, name, message);
        
        System.out.println(logMessage);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    public void debug(String message) {
        log(Level.DEBUG, message, null);
    }
    
    public void info(String message) {
        log(Level.INFO, message, null);
    }
    
    public void warn(String message) {
        log(Level.WARN, message, null);
    }
    
    public void warn(String message, Throwable throwable) {
        log(Level.WARN, message, throwable);
    }
    
    public void error(String message) {
        log(Level.ERROR, message, null);
    }
    
    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message, throwable);
    }
}

