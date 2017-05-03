# webapp-notifier

Notify about start and stop of WebApp in a Servlet Container (like Tomcat) to a remote URL. Open Source Java project under Apache License v2.0

### Current Stable Version is [1.0.1](https://search.maven.org/#search|ga|1|g%3Aorg.javastack%20a%3Awebapp-notifier)

---

## DOC

#### Installation:

* Place `webapp-notifier-x.x.x.jar` in `tomcat/lib/`

#### Usage Example

```xml
<!-- Context Listener for Servlet Container -->
<!-- tomcat/conf/web.xml or WEB-INF/web.xml -->
<listener>
	<description>Notify about start and stop of WebApp to a remote URL</description>
	<listener-class>org.javastack.webappnotifier.WebAppNotifierContextListener</listener-class>
</listener>
```

```xml
<!-- tomcat/conf/server.xml (recomended, but optional) -->
<Server ...>
  <Listener className="org.javastack.webappnotifier.RunnerLifecycleListener" />
...
```

###### Notifies are blocking, unless you enable the RunnerLifecycleListener 

#### Configuration (system properties)

* **org.javastack.webappnotifier.url** (String): like http://api.acme.com/notifier
* **org.javastack.webappnotifier.defaultConnectTimeout** (milliseconds): default 5000 (5secs)
* **org.javastack.webappnotifier.defaultReadTimeout** (milliseconds): default 5000 (5secs)
* **org.javastack.webappnotifier.retryCount** (int): default 2 retries

#### HTTP request API

* **Method**: POST
* **Content-Type**: application/x-www-form-urlencoded
* Request Parameters:
  * **type** (String): "I" for Initialized, "D" for Destroyed
  * **ts** (long): Timestamp Unix Epoch in milliseconds (UTC). see [System.currentTimeMillis()](https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#currentTimeMillis()).
  * **jvmid** (String): The name representing the running Java virtual machine (like **pid**@**hostname**). Can be any arbitrary string and a Java virtual machine implementation can choose to embed platform-specific useful information in the returned name string. see [RuntimeMXBean.getName](http://docs.oracle.com/javase/7/docs/api/java/lang/management/RuntimeMXBean.html#getName()) 
  * **path** (String): like "/test" or "" (empty string, for root context)
  * **basename** (String): normalized path. see [Tomcat Basenames](https://tomcat.apache.org/tomcat-7.0-doc/config/context.html#Naming)

---

## MAVEN

    <dependency>
        <groupId>org.javastack</groupId>
        <artifactId>webapp-notifier</artifactId>
        <version>1.0.1</version>
    </dependency>

---
Inspired in [ServletContextListener](http://docs.oracle.com/javaee/7/api/javax/servlet/ServletContextListener.html), this code is Java-minimalistic version.
