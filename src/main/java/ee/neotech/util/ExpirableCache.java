package ee.neotech.util;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import ee.neotech.util.IdentityLock.Lock;

public class ExpirableCache<T extends ExpirableCache.Expirable> {

    private final Log log = LogFactory.getLog(ExpirableCache.class);

    public static class Expirable {
        protected long timestamp;
        protected final ConcurrentHashMap<Long, Thread> accessedBy = new ConcurrentHashMap<>();

        public final long getTimestamp() {
            return timestamp;
        }

        public String getAccessedBy() {
            StringBuilder result = null;
            for (Thread t : accessedBy.values()) {
                if (result == null) {
                    result = new StringBuilder();
                } else {
                    result.append(", ");
                }

                result.append(t.getName());
            }
            return result == null ? "none" : result.toString();
        }
    }

    public interface ValueProvider<T> {
        public T get(String key);
    }

    private final Map<String, T> items = new ConcurrentHashMap<>();
    private final IdentityLock<String> itemLocks = new IdentityLock<String>();

    private final long duration;
    private final long evictionTimeout;

    private final Thread evictionThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {

                long at = System.currentTimeMillis();

                for (Iterator<Entry<String, T>> i = items.entrySet().iterator(); i.hasNext();) {
                    Entry<String, T> cs = i.next();
                    String key = cs.getKey();
                    T expirable = cs.getValue();
                    long itemDur = at - expirable.timestamp;

                    try (Lock lock = itemLocks.lock(key)) {
                        if (expirable.accessedBy.size() == 0 && itemDur > duration) {
                            i.remove();
                        } else if (expirable.accessedBy.size() > 0 && (itemDur > 10 * duration)) {
                            log.warn(expirable.toString() + " is stuck in expirable cache. Current duration: "
                                    + TimeUnit.MILLISECONDS.toSeconds(itemDur) + "seconds.");
                        }
                    }

                }

                try {
                    Thread.sleep(evictionTimeout);
                } catch (InterruptedException e) {
                    break;
                }
            }

        }
    }, "ExpirableCache-eviction-thread");

    public ExpirableCache(long duration, long evictionTimeout) {
        this.duration = duration;
        this.evictionTimeout = evictionTimeout;
        this.evictionThread.start();
    }

    public T get(String key, ValueProvider<T> valueProvider) {
        try (Lock lock = itemLocks.lock(key)) {

            T result = items.get(key);

            long at = System.currentTimeMillis();

            if (result != null && result.accessedBy.size() == 0 && (at - result.timestamp > duration)) {
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

            Thread ct = Thread.currentThread();
            result.accessedBy.put(ct.getId(), ct);

            return result;
        }
    }

    /**
     * Item is locked in cache when it is acquired via get(). Should be released later by the same thread using release() or it will stuck in cache
     * forever.
     * 
     * @param key
     */
    public void release(String key) {
        try (Lock lock = itemLocks.lock(key)) {
            T item = items.get(key);
            if (item != null) {
                item.accessedBy.remove(Thread.currentThread().getId());
            }
        }
    }

    public void remove(String key) {
        try (Lock lock = itemLocks.lock(key)) {
            items.remove(key);
        }
    }

    public void destroy() {
        this.evictionThread.interrupt();
    }
}