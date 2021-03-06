# tomcat-redis-session-manager
Non-sticky session manager for Apache Tomcat 7.xx with Redis and disk store implementations.

Build
---

Compile using maven

	mvn compile package

Install
---

Copy tomcat-redis-session-manager-{version}.jar to TOMCAT_BASE/lib directory.

Redis store
---

Configure Tomcat by adding the following block to context.xml (or context block of server.xml)

	<!-- RedisSessionManager 
	     Single host config attributes:
            host - defaults to localhost
            port - defaults to 6379
        Sentinels config attributes:
            sentinelMaster - master name
            sentinels - comma separated list of [sentinel-host:port]
        Config attributes:
            database - Redis DB to use (defaults to 0)
            timeout  - Redis connection and so timeout (in milliseconds, defaults to 2000)
            password - Redis auth password
            connectionAttempts - Number of attempts to connect to redis (defaults to 20).
            connectionAttemptDelay - Delay between attempts (in milliseconds, defaults to 500). NB: Make sure that total duration of connectionAttempts*attemptDelay is enough for new master to be chosen. 
            operationAttempts - Number of attempts to retry redis operation if it fails.  (defaults to 3)
            maxInactiveInterval - The default maximum inactive interval for Sessions. Is overridden by web.xml session-timeout setting (in seconds defaults to 1800) 
            sessionIdLength - The session id length of Sessions. (defaults to 16)
            keepSessionDuration - Cached sessions will be reused for specified duration after being cached. Will greatly speed up concurrent requests to the same session, but at a price of possible desync if used in cluster environment with dynamic load balancing (defaults to 10)
            cacheClearupDelay - Delay between executions of session cache clearup task, in seconds. (defaults to 60)
        Jedis pool config attributes (optional):
            poolXXX - where XXX are properties from GenericObjectPoolConfig see (https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPoolConfig.html)
	-->
	
	<Manager className="ee.neotech.tomcat.session.RedisSessionManager"  
		host="?"  
		port="?"  
		sentinelMaster="?"  
		sentinels="?"  
		password="?"/>  
	         
Disk store (experimental)
---

Configure Tomcat by adding the following block to context.xml (or context block of server.xml)

	<!-- DiskSessionManager 
		path - disk path to use as session storage -->
	<Manager className="ee.neotech.tomcat.session.DiskSessionManager" path="?"/>