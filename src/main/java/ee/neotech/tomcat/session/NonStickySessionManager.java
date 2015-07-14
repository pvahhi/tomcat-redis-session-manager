package ee.neotech.tomcat.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Objects;

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

public abstract class NonStickySessionManager extends ManagerBase implements Lifecycle {

    private final Log log = LogFactory.getLog(NonStickySessionManager.class);

    /**
     * The lifecycle event support for this component.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);
    private ClassLoader loader;

    private final ThreadLocal<SessionContext> context = new ThreadLocal<NonStickySessionManager.SessionContext>();

    private static class SessionContext {
        byte[] binary;
        NonStickySession session;

        public SessionContext(byte[] binary, NonStickySession session) {
            this.binary = binary;
            this.session = session;
        }
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

        setState(LifecycleState.STOPPING);

        super.stopInternal();
    }

    @Override
    public Session createSession(String requestedSessionId) {
        //log.info("New session requested (id="+requestedSessionId+" - discarded)");
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
            NonStickySession nss = (NonStickySession) session;
                                               
            if (nss.isDirty()) {
                byte[] binary = toBinary(nss);
                SessionContext sessionContext = context.get();
                boolean modified = nss.isModified() || sessionContext == null || !Arrays.equals(sessionContext.binary, binary);

                context.set(new SessionContext(binary, nss));
                
                if (modified) {

                    //log.info("Saving session id=" + session.getId() + " thread=" + Thread.currentThread().getName() + " modified = "
                    //        + nss.isModified() + " size = " + binary.length);
                    save(session.getId(), binary, nss);
                }
                
                nss.resetStates();
            } else {
                context.set(new SessionContext(null, nss));
            }
                        
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist session (id=" + session.getId() + ")", ex);
        }
    }
    
    protected void endAccess(NonStickySession sess) {
        try {
            //log.trace("Ending access for session id=" + sess.getId());
            if (sess.isValid()) {
                add(sess);
            } else {
                remove(sess, true);
            }
        } catch (Exception e) {
            log.error("Failed to end session access", e);
        }
        
        context.set(null);
    }

    @Override
    public final Session findSession(String id) throws IOException {
        SessionContext sc = context.get();
        if (sc != null) {
            if (Objects.equals(sc.session.getId(), id)) {
                return context.get().session;
            }
        }

        //log.trace("Finding session by id=" + id + " thread=" + Thread.currentThread().getName());

        if (id == null) return (null);

        byte[] data;
        try {
            data = load(id);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load session (id=" + id + ")", ex);
        }

        if (data != null) {
            try {
                NonStickySession result = fromBinary(data);

                context.set(new SessionContext(data, result));

                return result;
            } catch (Throwable e) {
                log.warn("Failed to deserialize session id=" + id + ". Session data will be reset", e);
                try {
                    delete(id);
                } catch (Exception ex) {
                    log.error("Failed to delete session (id=" + id + ")", ex);
                }
                return null;
            }
        }
        
        return null;
    }

    @Override
    public void remove(Session session, boolean update) {
        try {
            //log.info("Removing session id=" + session.getId() + " thread=" + Thread.currentThread().getName());
            super.remove(session, update);

            delete(session.getId());
        } catch (Exception ex) {
            log.error("Failed to delete session (id=" + session.getId() + ")", ex);
        } finally {
            context.set(null);
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

    protected abstract void save(String id, byte[] data, NonStickySession session) throws Exception;

    protected abstract void delete(String id) throws Exception;
}
