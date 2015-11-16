package ee.neotech.tomcat.session;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DiskSessionManager extends NonStickySessionManager {

    protected String path;

    @Override
    protected byte[] load(String id) throws Exception {
        File file = new File(path, id);

        if (file.exists()) {
            try (FileInputStream is = new FileInputStream(file); ByteArrayOutputStream bos = new ByteArrayOutputStream();) {
                copyLarge(is, bos, new byte[1024]);
                return bos.toByteArray();
            }
        } else {
            return null;
        }

    }

    private static long copyLarge(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    @Override
    protected void save(String id, byte[] data, int expireSeconds) throws Exception {
        File file = new File(path, id);

        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(data);
            fos.flush();
        }
    }

    @Override
    protected void expire(String id, int expireSeconds) throws Exception {
        // not implemented
    }

    @Override
    protected synchronized void delete(String id) throws Exception {
        File file = new File(path, id);
        file.delete();
    }

    @Override
    protected String generateSessionId() {
        String result;
        do {
            result = sessionIdGenerator.generateSessionId();
        } while (new File(path, result).exists());
        return result;
    }

    public final String getPath() {
        return path;
    }

    public final void setPath(String path) {
        this.path = path;
    }

}
