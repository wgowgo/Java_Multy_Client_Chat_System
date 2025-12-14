import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public final class ClientSession {
    private static final Logger logger = Logger.getLogger(ClientSession.class);
    
    public final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    
    private final BlockingQueue<Frame> sendQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger seqOut = new AtomicInteger(1);
    private final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
    
    public volatile String nick = null;
    public volatile String room = null;
    private volatile boolean running = true;
    
    private final Thread readerThread;
    private final Thread writerThread;
    
    private final ChatServer server;
    
    public ClientSession(ChatServer server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        
        String addr = socket.getRemoteSocketAddress().toString();
        this.readerThread = new Thread(this::readerLoop, "Reader-" + addr);
        this.writerThread = new Thread(this::writerLoop, "Writer-" + addr);
        
        readerThread.setDaemon(true);
        writerThread.setDaemon(true);
    }
    
    public void start() {
        writerThread.start();
        readerThread.start();
        logger.debug(String.format("Session started for %s", socket.getRemoteSocketAddress()));
    }
    
    public void stop(String reason) {
        if (!running) return;
        
        running = false;
        logger.debug(String.format("Stopping session for %s: %s", 
            nick != null ? nick : socket.getRemoteSocketAddress(), reason));
        
        try {
            socket.close();
        } catch (IOException e) {
            logger.warn("Error closing socket", e);
        }
        
    
        sendQueue.offer(new Frame((byte) 0, (short) 0, 0, new byte[0]));
    }
    
    public int nextSeq() {
        return seqOut.getAndIncrement();
    }
    
    public void send(Frame f) {
        if (!running || f == null) return;
        boolean offered = sendQueue.offer(f);
        if (!offered) {
            logger.warn(String.format("Send queue full for %s, dropping frame", 
                nick != null ? nick : "unknown"));
        }
    }
    
    public boolean isRunning() {
        return running && !socket.isClosed();
    }
    
    public void updateLastActivity() {
        lastActivityTime.set(System.currentTimeMillis());
    }
    
    public long getLastActivityTime() {
        return lastActivityTime.get();
    }
    
    public String getNick() {
        return nick;
    }
    
    private void writerLoop() {
        try {
            while (running) {
                try {
                    Frame f = sendQueue.take();
                    
               
                    if (f.type == 0 && f.payload.length == 0 && !running) {
                        break;
                    }
                    
                    synchronized (out) {
                        f.writeTo(out);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.warn(String.format("Writer error for %s: %s", 
                    nick != null ? nick : socket.getRemoteSocketAddress(), e.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in writer loop", e);
        } finally {
            running = false;
            server.onDisconnect(this);
        }
    }
    
    private void readerLoop() {
        try {
            while (running) {
                try {
                    Frame f = Frame.readFrom(in);
                    updateLastActivity();
                    server.onFrame(this, f);
                } catch (java.net.SocketTimeoutException e) {
                    
                    continue;
                } catch (EOFException e) {
                    logger.debug(String.format("EOF for %s", 
                        nick != null ? nick : socket.getRemoteSocketAddress()));
                    break;
                } catch (IOException e) {
                    if (running) {
                        logger.warn(String.format("Reader error for %s: %s", 
                            nick != null ? nick : socket.getRemoteSocketAddress(), e.getMessage()));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected error in reader loop", e);
        } finally {
            running = false;
            server.onDisconnect(this);
        }
    }
}
