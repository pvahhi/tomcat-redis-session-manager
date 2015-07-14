# tomcat-redis-session-manager
Non-sticky session manager for Apache Tomcat with Redis and disk store implementations.

1. Compile using maven: 

	mvn compile package

2. Copy tomcat-redis-session-manager-{version}.jar to TOMCAT_BASE/lib directory.

3. For Redis store configure Tomcat by adding the following block to context.xml (or context block of server.xml)

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
        Jedis pool config attributes (optional):
            poolXXX - where XXX are properties from GenericObjectPoolConfig see (https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPoolConfig.html)
	-->
	<Manager className="ee.neotech.tomcat.session.RedisSessionManager"
		 host="?"
		 port="?"
	         sentinelMaster="?"
        	 sentinels="?"
	         password="?"/>

4. For Disk store (experimental) configure Tomcat by adding the following block to context.xml (or context block of server.xml)

	<!-- DiskSessionManager 
		path - disk path to use as session storage -->
	<Manager className="ee.neotech.tomcat.session.DiskSessionManager" path="?"/>