package ee.neotech.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Test;

public class SharedAccessCacheTest {

    private final static long TOTAL_WORK_TIME = 2000;
    private final static long THREAD_WORK_TIME = 10;
    private final static long THREAD_SLEEP_TIME = 10;
    private final static long THREADS_PER_KEY = 15;
    private final static long DISTINCT_KEYS = 100;

    static class Item {
        final AtomicLong workTimes = new AtomicLong(0);
        final AtomicLong inworkCounter = new AtomicLong(0);
        UUID uuid;

        Item(UUID uuid) {
            this.uuid = uuid;
        }

        @Override
        public String toString() {
            return "Item [workTimes=" + workTimes + ", inworkCounter=" + inworkCounter + ", " + (uuid != null ? "uuid=" + uuid : "")
                    + "]";
        }
    }

    static class Stats {
        double avgCounter;
        int unloadTimes;
        int loadTimes;

        @Override
        public String toString() {
            return "Stats [avgCounter=" + avgCounter + ", unloadTimes=" + unloadTimes + ", loadTimes=" + loadTimes + "]";
        }
    }

    Map<UUID, Stats> stats = new ConcurrentHashMap<>();

    private Stats getStats(UUID uuid) {
        Stats result = stats.get(uuid);
        if (result == null) {
            result = new Stats();
            stats.put(uuid, result);
        }
        return result;
    }

    final SharedAccessCache<UUID, Item> cache = new SharedAccessCache<UUID, Item>() {

        @Override
        protected Item load(UUID key) {
            getStats(key).loadTimes++;
            return new Item(key);
        }

        @Override
        protected void unload(UUID key, Item item) {
            Stats stats = getStats(key);
            stats.avgCounter = (stats.avgCounter * stats.unloadTimes + item.workTimes.get()) / (stats.unloadTimes + 1);
            stats.unloadTimes++;
            Assert.assertEquals(key, item.uuid);
            Assert.assertEquals("Inwork counter must be zero on unload " + item, 0, item.inworkCounter.get());
        }
    };

    class SACThread extends Thread {

        private final UUID uuid;
        private boolean success;

        SACThread(UUID uuid) {
            this.uuid = uuid;
            this.success = false;
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();

            while (!this.isInterrupted() && (System.currentTimeMillis() - start < TOTAL_WORK_TIME)) {
                try {
                    Item item = cache.get(uuid);
                    item.workTimes.incrementAndGet();
                    item.inworkCounter.incrementAndGet();
                    try {
                        Thread.sleep(THREAD_WORK_TIME);
                    } finally {
                        item.inworkCounter.decrementAndGet();
                        cache.release(uuid);
                    }

                    Thread.sleep(THREAD_SLEEP_TIME);
                } catch (InterruptedException e) {
                    break;
                }
            }

            this.success = true;
        }

        public final boolean isSuccess() {
            return success;
        }
    }

    @Test
    public void test() throws InterruptedException {

        List<SACThread> threads = new ArrayList<>();
        List<UUID> uuids = new ArrayList<>();
        for (int i = 0; i < DISTINCT_KEYS; i++) {
            UUID uuid = UUID.randomUUID();
            uuids.add(uuid);

            for (int t = 0; t < THREADS_PER_KEY; t++) {
                SACThread thr = new SACThread(uuid);
                thr.start();
                threads.add(thr);
            }
        }

        for (SACThread t : threads) {
            t.join();

            Assert.assertEquals("Thread didnt finish successfully", true, t.success);
        }

        for (UUID uuid : uuids) {
            Item ti1 = new Item(uuid);
            Item cti = cache.putnx(uuid, ti1);
            Assert.assertEquals(ti1, cti);

            Item ti2 = new Item(uuid);
            cti = cache.putnx(uuid, ti2);
            Assert.assertEquals(ti1, cti);

            Stats stats = getStats(uuid);
            System.out.println(uuid + " " + stats);

            Assert.assertEquals(stats.loadTimes, stats.unloadTimes);

            cache.remove(uuid);
            cti = cache.putnx(uuid, ti2);
            Assert.assertEquals(ti2, cti);
        }
    }

}
