package ee.neotech.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import ee.neotech.util.IdentityLock.Lock;

/**
 * Cache for items that are concurrently accessed by several threads. Item loading/unloading mechanism must be provided by inherited classes.
 *
 * @param <K>
 * @param <T>
 */
public abstract class SharedAccessCache<K,T> {
    
    private final Log log = LogFactory.getLog(SharedAccessCache.class);
    
    private final static long CRITICAL_AGE = TimeUnit.MINUTES.toMillis(2);
    
    static class CacheItem<T> {
        final T data;
        final Set<Thread> accessedBy;
        final long timestamp;
        
        public CacheItem(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.accessedBy = new HashSet<>();
        }
    }

    private final Map<K, CacheItem<T>> items = new ConcurrentHashMap<>();
    private final IdentityLock<K> itemLocks = new IdentityLock<K>();

    public T get(K key) {
        try (Lock lock = itemLocks.lock(key)) {
            CacheItem<T> cacheItem = items.get(key);

            if (cacheItem == null) {
                T data = load(key);

                if (data == null) {
                    return null;
                }

                cacheItem = new CacheItem<>(data);
                items.put(key, cacheItem);
            } else {
                long age = System.currentTimeMillis() - cacheItem.timestamp;
                if (age > CRITICAL_AGE) {
                    log.error("Critical age ("+age+"ms) for cache item "+cacheItem.data+" is reached. Accessed by threads: "+cacheItem.accessedBy);
                }
            }
            
            cacheItem.accessedBy.add(Thread.currentThread());

            return cacheItem.data;
        }
    }
    
    /**
     * puts value in a cache if it is not there yet. Otherwise return current value in cache
     * @param key
     * @param value
     * @return
     */
    public T putnx(K key, T value) {
        try (Lock lock = itemLocks.lock(key)) {

            CacheItem<T> cacheItem = items.get(key);

            if (cacheItem == null) {
                cacheItem = new CacheItem<>(value);
                items.put(key, cacheItem);
            }

            cacheItem.accessedBy.add(Thread.currentThread());
            
            return cacheItem.data;
        }
    }

    /**
     * Item is locked in cache by get()/putnx(). Should be released later by the same thread using release() or it will stuck in cache forever.
     * 
     * @param key
     */
    public void release(K key) {
        try (Lock lock = itemLocks.lock(key)) {
            CacheItem<T> cacheItem = items.get(key);
            if (cacheItem != null) {
                cacheItem.accessedBy.remove(Thread.currentThread());
                
                if (cacheItem.accessedBy.isEmpty()) {                    
                    items.remove(key);
                    unload(key, cacheItem.data);
                }
            }
        }
    }

    public void remove(K key) {
        try (Lock lock = itemLocks.lock(key)) {
            CacheItem<T> cacheItem = items.remove(key);
            if (cacheItem != null) {                
                unload(key, cacheItem.data);
            }
        }
    }
    
    /** called to load non-existing item in cache.
     * <p>Synchronized by key
     * 
     * @param key
     * @return null, if item can not be found for specified key
     */
    protected abstract T load(K key);
    
    /**
     * called when item is removed from cache (either by calling remove() or when all accessing threads have released that item)
     * <p>Synchronized by key
     * @param item
     */
    protected abstract void unload(K key, T item);
}