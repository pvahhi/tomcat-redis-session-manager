package ee.neotech.tomcat.session;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.apache.catalina.session.StandardSession;

public class NonStickySession extends StandardSession {
    private static final long serialVersionUID = 7661325540126449709L;

    //private final Log log = LogFactory.getLog(NonStickySession.class);
    
    /** true if session data was exposed and could be modified */
    private boolean dirty;
    
    /** true if session data is modified */
    private boolean modified;

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
            //log.info("SET attr "+key+" = " + value);
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
            //log.info("REMOVE attr "+name);
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
    public void endAccess() {
        super.endAccess();

        getManager().endAccess(this);
    }
    
    @Override
    protected void writeObject(ObjectOutputStream stream) throws IOException {
        long lat = lastAccessedTime;
        long tat = thisAccessedTime;
        try {
            // do not serialize lat and tat so binary session data will change only when there are real changes
            lastAccessedTime = thisAccessedTime = 0; 
            super.writeObject(stream);
        } finally {
            lastAccessedTime = lat;
            thisAccessedTime = tat;
        }
    }
    
    @Override
    protected void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
        super.readObject(stream);
        
        lastAccessedTime = thisAccessedTime = System.currentTimeMillis();
    }

    public void resetStates() {
        dirty = false;
        modified = false;
    }
}
