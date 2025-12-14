import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class MessageHistory {
    private final ConcurrentLinkedQueue<HistoryEntry> history;
    private final int maxSize;
    
    public MessageHistory(int maxSize) {
        this.maxSize = maxSize;
        this.history = new ConcurrentLinkedQueue<>();
    }
    
    public void add(String from, String room, String message) {
        history.offer(new HistoryEntry(from, room, message, Instant.now()));
        
        while (history.size() > maxSize) {
            history.poll();
        }
    }
    
    public List<HistoryEntry> getRecent(int count) {
        List<HistoryEntry> result = new ArrayList<>();
        Iterator<HistoryEntry> it = history.iterator();
        int collected = 0;
        
        List<HistoryEntry> all = new ArrayList<>(history);
        Collections.reverse(all);
        
        for (HistoryEntry entry : all) {
            if (collected >= count) break;
            result.add(entry);
            collected++;
        }
        
        Collections.reverse(result); 
        return result;
    }
    
    public List<HistoryEntry> getAll() {
        return new ArrayList<>(history);
    }
    
    public void clear() {
        history.clear();
    }
    
    public static final class HistoryEntry {
        public final String from;
        public final String room;
        public final String message;
        public final Instant timestamp;
        
        public HistoryEntry(String from, String room, String message, Instant timestamp) {
            this.from = from;
            this.room = room;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}

