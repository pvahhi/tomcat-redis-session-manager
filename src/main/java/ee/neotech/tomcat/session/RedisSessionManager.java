package ee.neotech.tomcat.session;

import java.util.Arrays;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisSessionManager extends GenericRedisSessionManager {

    private final Log log = LogFactory.getLog(RedisSessionManager.class);

    protected byte[] NEW_SESSION = "new".getBytes();

    private int connectionAttempts = 20;
    private int connectionAttemptDelay = 500;
    private int operationAttempts = 3;

    interface JedisOp<T> {
        T execute(Jedis jedis);
    }

    private <T> T withJedis(JedisOp<T> operation) {

        Throwable unrecoverable = null;

        for (int operationAttempt = 0; operationAttempt < operationAttempts; operationAttempt++) {

            Jedis jedis = null;
            for (int connectionAttempt = 0; connectionAttempt < connectionAttempts; connectionAttempt++) {
                try {
                    jedis = jedisPool.getResource();
                    break;
                } catch (JedisConnectionException e) {
                    log.warn("Could not get Redis connection from the pool (attempt: " + (connectionAttempt + 1) + ", retry in "
                            + connectionAttemptDelay + "ms): " + getMessageWithCauses(e));
                }

                try {
                    Thread.sleep(connectionAttemptDelay);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("Thread interrupted", e);
                }
            }

            if (jedis == null) {
                throw new IllegalStateException("Failed to get Redis connection after " + connectionAttempts + " attempts");
            }

            if (database != 0) {
                jedis.select(database);
            }

            try {
                return operation.execute(jedis);
            } catch (JedisConnectionException e) {
                log.warn("Failed to perform Redis operation - attempt #" + (operationAttempt + 1) + ": " + getMessageWithCauses(e));
                unrecoverable = e;
            } catch (Throwable e) {
                log.error("Failed to perform Redis operation - attempt #" + (operationAttempt + 1), e);
                unrecoverable = e;
            } finally {
                jedis.close();
            }
        }

        throw new IllegalStateException("Failed to execute Redis operation after " + operationAttempts + " attempts", unrecoverable);
    }

    public final void setConnectionAttempts(int connectionAttempts) {
        this.connectionAttempts = connectionAttempts;
    }

    public final void setConnectionAttemptDelay(int connectionAttemptDelay) {
        this.connectionAttemptDelay = connectionAttemptDelay;
    }

    public final void setOperationAttempts(int operationAttempts) {
        this.operationAttempts = operationAttempts;
    }

    @Override
    protected byte[] load(final String id) throws Exception {
        byte[] result = withJedis(new JedisOp<byte[]>() {
            @Override
            public byte[] execute(Jedis jedis) {
                return jedis.get(id.getBytes());
            }
        });

        if (Arrays.equals(NEW_SESSION, result)) {
            log.warn("Stub session token <new> is not supposed to be loaded (id=" + id + ")");
            return null;
        }

        if (log.isDebugEnabled()) {
            log.debug("Session sid=" + id + " data size=" + (result == null ? "null" : result.length) + " loaded from redis");
        }
        return result;
    }

    @Override
    protected void save(final String id, final byte[] data, final int expireSeconds) throws Exception {
        withJedis(new JedisOp<String>() {
            @Override
            public String execute(Jedis jedis) {
                return jedis.setex(id.getBytes(), expireSeconds, data);
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("Session sid=" + id + " data size = " + data.length + " saved to Redis with TTL=" + expireSeconds);
        }
    }

    @Override
    protected void expire(final String id, final int expireSeconds) throws Exception {
        withJedis(new JedisOp<Long>() {
            @Override
            public Long execute(Jedis jedis) {
                return jedis.expire(id.getBytes(), expireSeconds);
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("Session sid=" + id + " set Redis TTL=" + expireSeconds);
        }
    }

    @Override
    protected void delete(final String id) throws Exception {
        withJedis(new JedisOp<Long>() {
            @Override
            public Long execute(Jedis jedis) {
                return jedis.del(id.getBytes());
            }
        });
        if (log.isDebugEnabled()) {
            log.debug("Session sid=" + id + " deleted from Redis");
        }
    }

    @Override
    protected String generateSessionId() {
        // Ensure generation of a unique session identifier.
        return withJedis(new JedisOp<String>() {
            @Override
            public String execute(Jedis jedis) {
                String result;
                do {
                    result = sessionIdGenerator.generateSessionId();
                } while (jedis.setnx(result.getBytes(), NEW_SESSION) == 0L);
                return result;
            }
        });
    }

    private static String getMessageWithCauses(Throwable e) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            sb.append(e.getMessage());
            if (e.getCause() != null) {
                sb.append(" caused by ");
                e = e.getCause();
            } else {
                break;
            }
        }
        return sb.toString();
    }

}
