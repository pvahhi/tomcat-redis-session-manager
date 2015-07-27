package ee.neotech.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;


/**
 * The class allows locking by an identity (id) adding minimum of additional synchronization.
 * <p>
 * The purpose of the class is to enable synchronisation by a value (e.g. Id of an entity, etc.).
 * If you only need to prevent multiple concurrent invocations of a method for a single entity instance,
 * but you want to allow to invoke a method concurrently for different entities, then you
 * can use this class to create simple synchronisation almost without global locks.
 * </p>
 *
 * @param <T> Type of an entity identity
 */
public class IdentityLock<T> {

    private final Map<T, Reference<T>> referenceMap = new HashMap<>();
    
    /**
     * Obtains a lock on the given identity instance.
     * <p>
     * If another thread invokes this method with the same identity value 
     * (equality is checked using {@link Object#equals(Object)} method), 
     * then the thread will be blocked until the first thread closes the {@link Lock} instance.
     * </p>
     * Typical usage example:
     * <pre><code>
     * IdentityLock&lt;Long&gt; myLock = new IdentityLock&lt;&gt;();
     * ...
     * void updateValue(Long entityId, String value) {
     *   try (Lock lock = myLock.lock(entityId)) {
     *     // perform the update that should not be done concurrently on the same entityId:
     *     ...
     *   }
     * }
     * </code></pre>
     * @param identity
     * @return {@link Lock} object that must be closed at exit from synchronization scope.
     */
    public Lock lock(T identity) {
        Reference<T> ref = getReference(identity);
        
        ref.lock.lock();
        return new Lock(this, ref);
    }
    
    private Reference<T> getReference(T identity) {
        Reference<T> ref;
        synchronized (referenceMap) {
            ref = referenceMap.get(identity);
            if (ref == null) {
                ref = new Reference<T>(identity);
                referenceMap.put(identity, ref);
            }
            ref.counter++;
        }
        return ref;
    }
    
    private void releaseReference(Reference<T> ref) {
        synchronized (referenceMap) {
            ref.counter--;
            if (ref.counter == 0) {
                referenceMap.remove(ref.identity);
            }
        }
    }

    private void unlock(Reference<T> ref) {
        ref.lock.unlock();
        
        releaseReference(ref);
    }
    
    private static class Reference<T> {
        private final T identity;
        private final ReentrantLock lock = new ReentrantLock(true); // a "fair" lock will honor longest-waiting threads
        private int counter = 0;

        public Reference(T identity) {
            this.identity = identity;
        }
    }
    
    @SuppressWarnings("rawtypes")
    public static class Lock implements AutoCloseable {
        private final Reference ref;
        private final IdentityLock ilock;

        private Lock(IdentityLock ilock, Reference ref) {
            this.ilock = ilock;
            this.ref = ref;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public void close() {
            ilock.unlock(ref);
        }
    }
}

