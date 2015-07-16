package ee.neotech.tomcat.session;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.catalina.LifecycleException;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.util.Pool;

public abstract class GenericRedisSessionManager extends NonStickySessionManager {

    private final Log log = LogFactory.getLog(GenericRedisSessionManager.class);

    protected String host = Protocol.DEFAULT_HOST;
    protected int port = Protocol.DEFAULT_PORT;
    protected int database = Protocol.DEFAULT_DATABASE;
    protected String password = null;
    protected int timeout = Protocol.DEFAULT_TIMEOUT;
    protected String sentinelMaster = null;
    Set<String> sentinelSet = Collections.emptySet();

    protected Pool<Jedis> jedisPool;

    protected JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();

    /**
     * Set the value for the {@code lifo} configuration attribute for pools
     * created with this configuration instance.
     *
     * @param lifo The new setting of {@code lifo} for this configuration instance
     *
     * @see GenericObjectPool#getLifo()
     * @see GenericKeyedObjectPool#getLifo()
     */
    public void setPoolLifo(boolean lifo) {
        jedisPoolConfig.setLifo(lifo);
    }

    /**
     * Set the value for the {@code fairness} configuration attribute for pools
     * created with this configuration instance.
     *
     * @param fairness The new setting of {@code fairness} for this configuration instance
     *
     * @see GenericObjectPool#getFairness()
     * @see GenericKeyedObjectPool#getFairness()
     */
    public void setPoolFairness(boolean fairness) {
        jedisPoolConfig.setFairness(fairness);
    }

    /**
     * Set the value for the {@code maxWait} configuration attribute for pools
     * created with this configuration instance.
     *
     * @param maxWaitMillis The new setting of {@code maxWaitMillis} for this configuration instance
     *
     * @see GenericObjectPool#getMaxWaitMillis()
     * @see GenericKeyedObjectPool#getMaxWaitMillis()
     */
    public void setPoolMaxWaitMillis(long maxWaitMillis) {
        jedisPoolConfig.setMaxWaitMillis(maxWaitMillis);
    }

    /**
     * Set the value for the {@code minEvictableIdleTimeMillis} configuration
     * attribute for pools created with this configuration instance.
     *
     * @param minEvictableIdleTimeMillis The new setting of {@code minEvictableIdleTimeMillis} for this configuration instance
     *
     * @see GenericObjectPool#getMinEvictableIdleTimeMillis()
     * @see GenericKeyedObjectPool#getMinEvictableIdleTimeMillis()
     */
    public void setPoolMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        jedisPoolConfig.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
    }

    /**
     * Set the value for the {@code softMinEvictableIdleTimeMillis} configuration attribute for pools created with this configuration
     * instance.
     *
     * @param softMinEvictableIdleTimeMillis The new setting of {@code softMinEvictableIdleTimeMillis} for this configuration
     *        instance
     *
     * @see GenericObjectPool#getSoftMinEvictableIdleTimeMillis()
     * @see GenericKeyedObjectPool#getSoftMinEvictableIdleTimeMillis()
     */
    public void setPoolSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
        jedisPoolConfig.setSoftMinEvictableIdleTimeMillis(softMinEvictableIdleTimeMillis);
    }

    /**
     * Set the value for the {@code numTestsPerEvictionRun} configuration
     * attribute for pools created with this configuration instance.
     *
     * @param numTestsPerEvictionRun The new setting of {@code numTestsPerEvictionRun} for this configuration instance
     *
     * @see GenericObjectPool#getNumTestsPerEvictionRun()
     * @see GenericKeyedObjectPool#getNumTestsPerEvictionRun()
     */
    public void setPoolNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
        jedisPoolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
    }

    /**
     * Set the value for the {@code testOnCreate} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param testOnCreate The new setting of {@code testOnCreate} for this configuration instance
     *
     * @see GenericObjectPool#getTestOnCreate()
     * @see GenericKeyedObjectPool#getTestOnCreate()
     *
     * @since 2.2
     */
    public void setPoolTestOnCreate(boolean testOnCreate) {
        jedisPoolConfig.setTestOnCreate(testOnCreate);
    }

    /**
     * Set the value for the {@code testOnBorrow} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param testOnBorrow The new setting of {@code testOnBorrow} for this configuration instance
     *
     * @see GenericObjectPool#getTestOnBorrow()
     * @see GenericKeyedObjectPool#getTestOnBorrow()
     */
    public void setPoolTestOnBorrow(boolean testOnBorrow) {
        jedisPoolConfig.setTestOnBorrow(testOnBorrow);
    }

    /**
     * Set the value for the {@code testOnReturn} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param testOnReturn The new setting of {@code testOnReturn} for this configuration instance
     *
     * @see GenericObjectPool#getTestOnReturn()
     * @see GenericKeyedObjectPool#getTestOnReturn()
     */
    public void setPoolTestOnReturn(boolean testOnReturn) {
        jedisPoolConfig.setTestOnReturn(testOnReturn);
    }

    /**
     * Set the value for the {@code testWhileIdle} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param testWhileIdle The new setting of {@code testWhileIdle} for this configuration instance
     *
     * @see GenericObjectPool#getTestWhileIdle()
     * @see GenericKeyedObjectPool#getTestWhileIdle()
     */
    public void setPoolTestWhileIdle(boolean testWhileIdle) {
        jedisPoolConfig.setTestWhileIdle(testWhileIdle);
    }

    /**
     * Set the value for the {@code timeBetweenEvictionRunsMillis} configuration
     * attribute for pools created with this configuration instance.
     *
     * @param timeBetweenEvictionRunsMillis The new setting of {@code timeBetweenEvictionRunsMillis} for this configuration
     *        instance
     *
     * @see GenericObjectPool#getTimeBetweenEvictionRunsMillis()
     * @see GenericKeyedObjectPool#getTimeBetweenEvictionRunsMillis()
     */
    public void setPoolTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        jedisPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
    }

    /**
     * Set the value for the {@code evictionPolicyClassName} configuration
     * attribute for pools created with this configuration instance.
     *
     * @param evictionPolicyClassName The new setting of {@code evictionPolicyClassName} for this configuration instance
     *
     * @see GenericObjectPool#getEvictionPolicyClassName()
     * @see GenericKeyedObjectPool#getEvictionPolicyClassName()
     */
    public void setPoolEvictionPolicyClassName(String evictionPolicyClassName) {
        jedisPoolConfig.setEvictionPolicyClassName(evictionPolicyClassName);
    }

    /**
     * Set the value for the {@code blockWhenExhausted} configuration attribute
     * for pools created with this configuration instance.
     *
     * @param blockWhenExhausted The new setting of {@code blockWhenExhausted} for this configuration instance
     *
     * @see GenericObjectPool#getBlockWhenExhausted()
     * @see GenericKeyedObjectPool#getBlockWhenExhausted()
     */
    public void setPoolBlockWhenExhausted(boolean blockWhenExhausted) {
        jedisPoolConfig.setBlockWhenExhausted(blockWhenExhausted);
    }

    /**
     * Sets the value of the flag that determines if JMX will be enabled for
     * pools created with this configuration instance.
     *
     * @param jmxEnabled The new setting of {@code jmxEnabled} for this configuration instance
     */
    public void setPoolJmxEnabled(boolean jmxEnabled) {
        jedisPoolConfig.setJmxEnabled(jmxEnabled);
    }

    /**
     * Sets the value of the JMX name base that will be used as part of the
     * name assigned to JMX enabled pools created with this configuration
     * instance. A value of <code>null</code> means that the pool will define
     * the JMX name base.
     *
     * @param jmxNameBase The new setting of {@code jmxNameBase} for this configuration instance
     */
    public void setPoolJmxNameBase(String jmxNameBase) {
        jedisPoolConfig.setJmxNameBase(jmxNameBase);
    }

    /**
     * Sets the value of the JMX name prefix that will be used as part of the
     * name assigned to JMX enabled pools created with this configuration
     * instance.
     *
     * @param jmxNamePrefix The new setting of {@code jmxNamePrefix} for this configuration instance
     */
    public void setPoolJmxNamePrefix(String jmxNamePrefix) {
        jedisPoolConfig.setJmxNamePrefix(jmxNamePrefix);
    }

    /* GenericObjectPoolConfig properties */

    /**
     * Set the value for the {@code maxTotal} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxTotal The new setting of {@code maxTotal} for this configuration instance
     *
     * @see GenericObjectPool#setMaxTotal(int)
     */
    public void setPoolMaxTotal(int maxTotal) {
        jedisPoolConfig.setMaxTotal(maxTotal);
    }

    /**
     * Set the value for the {@code maxIdle} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxIdle The new setting of {@code maxIdle} for this configuration instance
     *
     * @see GenericObjectPool#setMaxIdle(int)
     */
    public void setPoolMaxIdle(int maxIdle) {
        jedisPoolConfig.setMaxIdle(maxIdle);
    }

    /**
     * Set the value for the {@code minIdle} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param minIdle The new setting of {@code minIdle} for this configuration instance
     *
     * @see GenericObjectPool#setMinIdle(int)
     */
    public void setPoolMinIdle(int minIdle) {
        jedisPoolConfig.setMinIdle(minIdle);
    }

    public final void setHost(String host) {
        this.host = host;
    }

    public final void setPort(int port) {
        this.port = port;
    }

    public final void setDatabase(int database) {
        this.database = database;
    }

    public final void setPassword(String password) {
        this.password = password;
    }

    public final void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public final void setSentinelMaster(String sentinelMaster) {
        this.sentinelMaster = sentinelMaster;
    }

    public final void setSentinels(String sentinels) {
        if (sentinels == null) {
            this.sentinelSet = Collections.emptySet();
        } else {
            List<String> asList = Arrays.asList(sentinels.split(","));
            for (int i = 0; i < asList.size(); i++) {
                asList.set(i, asList.get(i).trim());
            }
            this.sentinelSet = new LinkedHashSet<String>(asList);
        }
    }

    @Override
    protected synchronized void startInternal() throws LifecycleException {
        long start = System.currentTimeMillis();
        
        log.info("Initializing Redis session manager with session cache duration "+ this.keepSessionDuration + " seconds.");
        
        super.startInternal();

        try {
            if (sentinelMaster != null) {

                if (sentinelSet != null && sentinelSet.size() > 0) {
                    jedisPool = new JedisSentinelPool(sentinelMaster, sentinelSet, this.jedisPoolConfig, timeout, password);
                } else {
                    throw new LifecycleException(
                            "Error configuring Redis Sentinel connection pool: expected both `sentinelMaster` and `sentiels` to be configured");
                }
            } else {
                jedisPool = new JedisPool(this.jedisPoolConfig, host, port, timeout, password);
            }
        } catch (Exception e) {
            log.info("Redis session manager failed to initialize");
            throw new LifecycleException("Error connecting to Redis", e);
        }
        
        log.info("Redis session manager initialized in "+(System.currentTimeMillis()-start)+"ms.");
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        log.info("Stopping Redis session manager");
        
        super.stopInternal();

        try {
            if (jedisPool != null) {
                jedisPool.destroy();
            }
        } catch (Exception e) {}
    }
    
}
