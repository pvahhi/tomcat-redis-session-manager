package ee.neotech.tomcat.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import ee.neotech.util.ExpirableCache;
import ee.neotech.util.ExpirableCache.Expirable;
import ee.neotech.util.ExpirableCache.ValueProvider;

public abstract class NonStickySessionManager extends ManagerBase implements Lifecycle {

    private final Log log = LogFactory.getLog(NonStickySessionManager.class);

    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);
    private ClassLoader loader;

    static class CachedSession extends Expirable {
        private byte[] binary;
        private NonStickySession session;

        public CachedSession() {}

        public CachedSession(byte[] binary, NonStickySession session) {
            this.binary = binary;
            this.session = session;
        }

        @Override
        public String toString() {
            return "CachedSession [ID=" + (session != null ? session.getId() : "null") + ", timestamp=" + timestamp + ", accessedBy="
                    + getAccessedBy() + "]";
        }
    }

    private ExpirableCache<CachedSession> sessionCache;

    protected int keepSessionDuration = 10; // in seconds
    protected int cacheClearupDelay = 60; // in seconds

    public final void setKeepSessionDuration(int keepSessionDuration) {
        this.keepSessionDuration = keepSessionDuration;
    }

    public final void setCacheClearupDelay(int cacheClearupDelay) {
        this.cacheClearupDelay = cacheClearupDelay;
    }

    @Override
    public int getRejectedSessions() {
        return 0; // non-sticky sessions are never rejected
    }

    @Override
    public void load() throws ClassNotFoundException, IOException {
        // sessions are loaded on demand only
    }

    @Override
    public void unload() throws IOException {
        // sessions are unloaded on access end
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processExpires() {
        // sessions are not stored within manager -> do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *            that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();

        setDistributable(true);
        loader = getContainer() != null ? getContainer().getLoader().getClassLoader() : null;
        sessionCache = new ExpirableCache<NonStickySessionManager.CachedSession>(1000 * keepSessionDuration, 1000 * cacheClearupDelay);

        setState(LifecycleState.STARTING);
    }

    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *            that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        if (log.isDebugEnabled()) log.debug("Stopping");

        this.sessionCache.destroy();

        setState(LifecycleState.STOPPING);

        super.stopInternal();
    }

    @Override
    public Session createSession(String requestedSessionId) {
        // do not reuse provided id -> always generate new for non existing sessions.
        return super.createSession(null);
    }

    @Override
    public NonStickySession createEmptySession() {
        return new NonStickySession(this);
    }

    @Override
    public final void add(Session session) {
        try {
            String id = session.getId();

            CachedSession cachedSession = sessionCache.get(id, new ValueProvider<NonStickySessionManager.CachedSession>() {
                @Override
                public CachedSession get(String key) {
                    return new CachedSession();
                }
            });

            synchronized (cachedSession) {
                NonStickySession nss = (NonStickySession) session;

                if (nss.isDirty()) {
                    boolean modified = nss.isModified();
                    
                    // session modified/dirty states must be reset before serialization
                    nss.resetStates();

                    byte[] binary = toBinary(nss);

                    modified = modified || !Arrays.equals(cachedSession.binary, binary);

                    cachedSession.binary = binary;
                    cachedSession.session = nss;

                    if (modified) {
                        save(id, binary, nss.getMaxInactiveInterval());
                    }
                }

            }

        } catch (Exception ex) {
            log.fatal("Failed to persist session (id=" + session.getId() + ")", ex);
        }
    }

    protected void endAccess(NonStickySession sess) {
        try {
            if (sess.isValid()) {
                add(sess);
            } else {
                remove(sess, true);
            }
        } catch (Exception e) {
            log.error("Failed to end session access", e);
        } finally {
            sessionCache.release(sess.getId());
        }
    }

    @Override
    public final Session findSession(String id) throws IOException {
        if (id == null) return null;

        CachedSession cachedSession = sessionCache.get(id, new ValueProvider<NonStickySessionManager.CachedSession>() {
            @Override
            public CachedSession get(String key) {

                byte[] data = null;
                try {
                    data = load(key);
                } catch (Exception ex) {
                    log.fatal("Failed to load session (id=" + key + ")", ex);
                }

                if (data != null) {

                    try {
                        NonStickySession nss = fromBinary(data);

                        expire(key, nss.getMaxInactiveInterval());

                        return new CachedSession(data, nss);
                    } catch (Throwable e) {
                        log.warn("Failed to deserialize session id=" + key + ". Session data will be reset", e);
                        try {
                            delete(key);
                        } catch (Exception ex) {
                            log.error("Failed to delete session (id=" + key + ")", ex);
                        }
                    }
                }

                return null;
            }
        });

        return cachedSession != null ? cachedSession.session : null;
    }

    @Override
    public void remove(Session session, boolean update) {
        String id = session.getId();
        try {
            super.remove(session, update);

            delete(id);

        } catch (Exception ex) {
            log.error("Failed to delete session (id=" + session.getId() + ")", ex);
        } finally {
            sessionCache.remove(id);
        }
    }

    protected final NonStickySession fromBinary(byte[] binary) throws ClassNotFoundException, IOException {
        try (ObjectInputStream ois = new CustomObjectInputStream(new ByteArrayInputStream(binary), loader)) {
            NonStickySession session = createEmptySession();
            session.readObjectData(ois);
            session.setManager(this);
            return session;
        }
    }

    protected final byte[] toBinary(NonStickySession session) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(1024); ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            session.writeObjectData(oos);
            oos.flush();
            return bos.toByteArray();
        }
    }

    protected abstract byte[] load(String id) throws Exception;

    protected abstract void save(String id, byte[] data, int expireSeconds) throws Exception;

    protected abstract void expire(String id, int expireSeconds) throws Exception;

    protected abstract void delete(String id) throws Exception;
}
