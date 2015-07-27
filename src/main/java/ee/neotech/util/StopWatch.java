package ee.neotech.util;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;

public class StopWatch implements AutoCloseable {

    private String name;
    private long started;
    private long reportThreshold;
    private Object[] reportArgs;

    private StopWatch(String name, long reportThreshold, Object... reportArgs) {
        this.name = name;
        this.reportThreshold = reportThreshold;
        this.reportArgs = reportArgs;
        this.started = System.currentTimeMillis();
    }

    @Override
    public void close() {
        long stopped = System.currentTimeMillis();

        if (stopped - started > reportThreshold) {
            synchronized (System.err) {
                String msg;
                if (reportArgs.length > 0) {
                    msg = MessageFormat.format("# {0, time, yyyy-MM-dd HH:mm:ss.SSS} # StopWatch [{1}] = {2}ms. Thread = {3}. Args = {4}", new Date(
                            stopped), name, stopped - started, Thread.currentThread().getName(), Arrays.toString(reportArgs));                    
                } else {
                    msg = MessageFormat.format("# {0, time, yyyy-MM-dd HH:mm:ss.SSS} # StopWatch [{1}] = {2}ms. Thread={3}", new Date(stopped), name,
                            stopped - started, Thread.currentThread().getName());
                }
                System.err.println(msg);
            }
        }
    }

    public static StopWatch start(String name, long reportThreshold, Object... reportArgs) {
        return new StopWatch(name, reportThreshold, reportArgs);
    }
}
