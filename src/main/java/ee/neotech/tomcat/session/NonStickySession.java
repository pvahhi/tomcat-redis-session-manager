package ee.neotech.tomcat.session;

import java.util.Objects;

import org.apache.catalina.session.StandardSession;

public class NonStickySession extends StandardSession {
    private static final long serialVersionUID = 7661325540126449709L;

    //private final Log log = LogFactory.getLog(NonStickySession.class);
    
    /** true if session data was exposed and could be modified */
    private volatile boolean dirty;
    
    /** true if session data is modified */
    private volatile boolean modified;

    public NonStickySession(NonStickySessionManager manager) {
        super(manager);
        this.dirty = false;
        this.modified = false;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.dirty = true;
        this.modified = true;
        super.setMaxInactiveInterval(interval);
    }
    
    @Override
    public void setId(String id) {
        this.dirty = true;
        this.modified = true;
        super.setId(id);
    }

    @Override
    public void setAttribute(String key, Object value) {
        Object oldValue = getAttribute(key);
        super.setAttribute(key, value);

        this.dirty = true;
        
        if (!Objects.equals(value, oldValue)) {
            this.modified = true;
        }
    }
    
    @Override
    public Object getAttribute(String name) {
        this.dirty = true;
        return super.getAttribute(name);
    }

    @Override
    public void removeAttribute(String name) {
        if (this.getAttribute(name) != null) {
            super.removeAttribute(name);
            this.dirty = true;
            this.modified = true;
        }
    }

    public final boolean isDirty() {
        return dirty;
    }
    
    public final boolean isModified() {
        return modified;
    }
    
    @Override
    public NonStickySessionManager getManager() {
        return (NonStickySessionManager)super.getManager();
    }

    @Override
    public void setCreationTime(long time) {
        // do not modify lastAccessedTime and thisAccessedTime and keep them equal to 0 (this is done so serialized binary data is not affected by this fields)
        this.creationTime = time;
    }
    
    @Override
    public void access() {
        // do not modify lastAccessedTime and thisAccessedTime and keep them equal to 0 (this is done so serialized binary data is not affected by this fields)
        
        if (ACTIVITY_CHECK) {
            accessCount.incrementAndGet();
        }
    }
    
    @Override
    public void endAccess() {
        // do not modify lastAccessedTime and thisAccessedTime and keep them equal to 0 (this is done so serialized binary data is not affected by this fields)
        
        isNew = false;

        if (ACTIVITY_CHECK) {
            accessCount.decrementAndGet();
        }

        getManager().endAccess(this);
    }
    
    @Override
    public boolean isValid() {
        if (!this.isValid) {
            return false;
        }

        if (this.expiring) {
            return true;
        }

        if (ACTIVITY_CHECK && accessCount.get() > 0) {
            return true;
        }       
        
        // do not call expire. expiration is done with external mechanism

        return this.isValid;
    }

    public void resetStates() {
        dirty = false;
        modified = false;
    }
}
