package ee.neotech.tomcat.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.Manifest;

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

import ee.neotech.util.SharedAccessCache;

public abstract class NonStickySessionManager extends ManagerBase implements Lifecycle {

    private final Log log = LogFactory.getLog(NonStickySessionManager.class);

    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);
    private ClassLoader loader;

    class SessionCache extends SharedAccessCache<String, CachedSession> {

        @Override
        protected CachedSession load(String key) {
            byte[] data = null;
            if (log.isDebugEnabled()) {
                log.debug("Loading session data: "+key);
            }
            try {
                data = NonStickySessionManager.this.load(key);
            } catch (Throwable ex) {
                log.fatal("Failed to load session (id=" + key + ")", ex);
            }

            if (data != null) {
                NonStickySession nss = null;
                try {
                    nss = fromBinary(data);
                } catch (Throwable e) {
                    log.warn("Failed to deserialize session id=" + key + ". Session data will be reset", e);
                    try {
                        NonStickySessionManager.this.delete(key);
                    } catch (Exception ex) {
                        log.error("Failed to delete session (id=" + key + ")", ex);
                    }
                    return null;
                }

                if (nss.isActualValid()) {
                    if (updateExpireOnAccess) {
                        try {
                            if (log.isDebugEnabled()) {
                                log.debug("Updating session id="+key+" expiration. Will expire in "+nss.getMaxInactiveInterval()+" seconds");
                            }
                            NonStickySessionManager.this.expire(key, nss.getMaxInactiveInterval());
                        } catch (Throwable ex) {
                            log.error("Failed to set session (id=" + key + ") expiration", ex);
                        }
                    }
                    
                    return new CachedSession(data, nss);
                } else {
                    log.warn("Invalid session is loaded: " + nss + ". Discarding, invalid sessions must not be saved.");
                }
            }

            return null;
        }

        @Override
        protected void unload(String key, CachedSession cachedSession) {
            NonStickySession nss = cachedSession.session;
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Unloading session: "+nss);
                }
                
                if (nss.isActualValid()) { // valid modified/new sessions are saved on unload
                    if (nss.isDirty() || cachedSession.binary == null) {
                        boolean modified = nss.isModified();
                        byte[] binary = toBinary(nss);
                        modified = modified || !Arrays.equals(cachedSession.binary, binary);
                        
                        if (modified) {
                            if (log.isDebugEnabled()) {
                                log.debug("Saving modified session: "+nss+" new = "+(cachedSession.binary == null));
                            }
                            NonStickySessionManager.this.save(nss.getId(), binary, nss.getMaxInactiveInterval());
                        } 
                    }
                } else {
                    if (log.isDebugEnabled()) {                        
                        log.debug("Deleting invalid session: "+nss);
                    }
                    NonStickySessionManager.this.delete(nss.getId());
                }
            } catch (Exception e) {
                log.error("Failed to unload session " + nss, e);
            }
        }
    }

    static class CachedSession {
        private byte[] binary;
        private final NonStickySession session;

        public CachedSession(byte[] binary, NonStickySession session) {
            if (session == null) {
                throw new IllegalStateException("Attempt to put NULL session into cache");
            }
            this.binary = binary;
            this.session = session;
        }

        @Override
        public String toString() {
            return "CachedSession [size=" + (binary != null ? binary.length : "null") + " " + session + "]";
        }
    }

    private SessionCache sessionCache;

    protected boolean updateExpireOnAccess = false;

    public final void setUpdateExpireOnAccess(boolean updateExpireOnAccess) {
        this.updateExpireOnAccess = updateExpireOnAccess;
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
    
    protected String getJarVersion() {
        try {
            for (Enumeration<URL> urls = this.getClass().getClassLoader().getResources("META-INF/MANIFEST.MF"); urls.hasMoreElements();) {
                URL url = urls.nextElement();
                Manifest manifest = new Manifest(url.openStream());
                
                if (Objects.equals(manifest.getMainAttributes().getValue("Implementation-Title"), "tomcat-redis-session-manager")) {
                    return manifest.getMainAttributes().getValue("Implementation-Version");        
                }                        
            }
        } catch (Exception e) {}
        return "(version unknown)";
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
        sessionCache = new SessionCache();

        setState(LifecycleState.STARTING);
        
        log.info("Staring session manager: "+this.getClassName()+" "+getJarVersion());
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
        log.info("Stopping session manager: "+this.getClassName());

        setState(LifecycleState.STOPPING);

        super.stopInternal();
    }

    @Override
    public Session createSession(String requestedSessionId) {
        // do not reuse provided id -> always generate new for non existing sessions.
        if (log.isDebugEnabled()) {
            log.debug("Requested to create new session with id="+requestedSessionId+". discarding, creating new id");
        }  
        return super.createSession(null);
    }

    @Override
    public NonStickySession createEmptySession() {
        return new NonStickySession(this);
    }

    @Override
    public final void add(final Session session) {
        String id = session.getId();
        
        if (log.isDebugEnabled()) {
            log.debug("Creating new empty session: "+session);
        }
        
        CachedSession cachedSession = sessionCache.putnx(id, new CachedSession(null, (NonStickySession) session));

        if (cachedSession.session != session) {
            log.fatal("Another cached session is present on add: cached session: "+ cachedSession.session+" while adding session: "+session);
        }
    }

    protected void endAccess(NonStickySession sess) {
        if (log.isDebugEnabled()) {
            log.debug("Ending access on "+sess);
        }
        sessionCache.release(sess.getId());
    }

    @Override
    public final Session findSession(String id) throws IOException {
        if (id == null) return null;
        
        if (log.isDebugEnabled()) {
            log.debug("Trying to find session: id="+id);
        }
        
        CachedSession cachedSession = sessionCache.get(id);
        
        if (cachedSession == null) {
            if (log.isDebugEnabled()) {
                log.debug("No session found: id="+id);
            }    
            return null;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Session found: "+cachedSession.session);
            }
            return cachedSession.session;
        }
    }

    @Override
    public void remove(Session session, boolean update) {
        super.remove(session, update);
        if (log.isDebugEnabled()) {
            log.debug("Removing session: "+session);
        }
        session.setValid(false);
        sessionCache.remove(session.getId());
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
