package ee.neotech.tomcat.session;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;

import org.apache.catalina.session.StandardSession;

public class NonStickySession extends StandardSession {
    private static final long serialVersionUID = 7661325540126449709L;
    
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
    
    /**
     * Some implementations of {@link #isValidInternal} do not return isValid immediately.
     * @return isValid field value 
     */
    public final boolean isActualValid() {
        return isValid;
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
    public void writeObjectData(ObjectOutputStream stream) throws IOException {
        long lat = this.lastAccessedTime;
        long tat = this.thisAccessedTime;
        try {
            // prevent serialization of constantly changing data
            this.lastAccessedTime = 0;
            this.thisAccessedTime = 0;
            super.writeObjectData(stream);
        } finally {
            this.lastAccessedTime = lat;
            this.thisAccessedTime = tat;
        }
    }
    
    @Override
    public void readObjectData(ObjectInputStream stream) throws ClassNotFoundException, IOException { 
        super.readObjectData(stream);
        this.lastAccessedTime = this.thisAccessedTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "NonStickySession ["+(isValid?"V":"I") + (dirty?"D":"") + (modified?"M":"") + (expiring?"E":"") + (isNew?"N":"")+" "+id+"]";
    }
}