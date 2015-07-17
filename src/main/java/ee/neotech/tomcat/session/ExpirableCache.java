package ee.neotech.tomcat.session;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class ExpirableCache<T extends ExpirableCache.Expirable> {

    public static class Expirable {
        long timestamp;
        
        public final long getTimestamp() {
            return timestamp;
        }
    }
    
    public interface ValueProvider<T> {
        public T get(String key);
    }       

    private final Map<String, T> items = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    
    private final long duration;
    private final long evictionTimeout;

    private final Thread evictionThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                long at = System.currentTimeMillis();
                
                for (Iterator<Entry<String, T>> i = items.entrySet().iterator(); i.hasNext(); ) {
                    Entry<String, T> cs = i.next();
                    
                    if (at - cs.getValue().timestamp > duration) {
                        String key = cs.getKey();
                        i.remove();
                        locks.remove(key);
                    }
                }               
                
                try {
                    Thread.sleep(evictionTimeout);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
        }}, "SessionCache-eviction-thread");
    
    public ExpirableCache(long duration, long evictionTimeout) {
        this.duration = duration;
        this.evictionTimeout = evictionTimeout;
        this.evictionThread.start();
    }

    public T get(String key, ValueProvider<T> valueProvider) {
        synchronized (getKeyLock(key)) {
            
            T result = items.get(key);
            
            long at = System.currentTimeMillis();

            if (result != null && (at - result.timestamp > duration)) {
                items.remove(key);
                result = null;
            }
            
            if (result == null) {                
                result = valueProvider.get(key);
                
                if (result == null) {
                    return null;
                }
                
                items.put(key, result);
                result.timestamp = at;
            }                       
            
            return result;
        }
    }
    
    public void remove(String key) {
        items.remove(key);
        locks.remove(key);
    }
    
    private Object getKeyLock(String key) {
        Object lock = locks.get(key);
        
        if (lock == null) {
            synchronized (locks) {
                lock = locks.get(key);
                if (lock == null) {
                    lock = new Object();
                    locks.put(key, lock);
                }
            }
        }
        
        return lock;
    }
    
    public void destroy() {
        this.evictionThread.interrupt();
    }
}