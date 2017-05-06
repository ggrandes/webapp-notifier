# webapp-notifier

Notify about start and stop of WebApp in a Servlet Container (like Tomcat) to a remote URL. Open Source Java project under Apache License v2.0

### Current Stable Version is [1.1.0](https://search.maven.org/#search|ga|1|g%3Aorg.javastack%20a%3Awebapp-notifier)

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
  <!-- Background Runner Thread for notifier -->
  <Listener className="org.javastack.webappnotifier.RunnerLifecycleListener" />
  <!-- Notify about endpoints in Tomcat -->
  <Listener className="org.javastack.webappnotifier.TomcatLifecycleListener" 
            resolveHostname="false" />
...
```

###### Notifies are blocking, unless you enable the RunnerLifecycleListener 
###### By default only context are notified, unless you enable the TomcatLifecycleListener

#### Configuration (system properties)

* **org.javastack.webappnotifier.url** (String): like http://api.acme.com/notifier, no default
* **org.javastack.webappnotifier.defaultConnectTimeout** (milliseconds): default 5000 (5secs)
* **org.javastack.webappnotifier.defaultReadTimeout** (milliseconds): default 5000 (5secs)
* **org.javastack.webappnotifier.retryCount** (int): default 2 retries
* **org.javastack.webappnotifier.customValue** (String): no default

#### HTTP request API

* **Method**: POST
* **Content-Type**: application/x-www-form-urlencoded
* Request Parameters:
  * **ts** (long): Timestamp Unix Epoch in milliseconds (UTC). see [System.currentTimeMillis()](https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#currentTimeMillis()).
  * **jvmid** (String): The name representing the running Java virtual machine (like **pid**@**hostname**). Can be any arbitrary string and a Java virtual machine implementation can choose to embed platform-specific useful information in the returned name string. see [RuntimeMXBean.getName](http://docs.oracle.com/javase/7/docs/api/java/lang/management/RuntimeMXBean.html#getName()) 
  * **service** (String): like "Catalina". see [Tomcat Service](https://tomcat.apache.org/tomcat-8.5-doc/config/service.html#Common_Attributes)
  * **custom** (String): Value from Configuration (`org.javastack.webappnotifier.customValue`)
  * **type** (String): "I" for Initialized, "D" for Destroyed
  * **event** (String): "C" for Context, "E" for EndPoint
    * _Context params_:
      * **path** (String): like "/test" or "" (empty string, for root context)
      * **basename** (String): normalized path. see [Tomcat Basenames](https://tomcat.apache.org/tomcat-8.5-doc/config/context.html#Naming)
    * _EndPoint params_:
      * **http** (String Array): like "http://api3.acme.com:8080"
      * **https** (String Array): like "https://api4.acme.com:8443"
      * **ajp** (String Array): like "ajp://api5.acme.com:8009"
      * **jvmroute** (String): like "jvm1" or "" (empty string if not defined). see [Tomcat Engine](https://tomcat.apache.org/tomcat-8.5-doc/config/engine.html#Common_Attributes)

###### * String Array in x-www-form-urlencoded are like: k=v1&k=v2&k=v3 (in a servlet you can get the `String[]` with: `request.getParameterValues("k")`)

---

## MAVEN

    <dependency>
        <groupId>org.javastack</groupId>
        <artifactId>webapp-notifier</artifactId>
        <version>1.1.0</version>
    </dependency>

---
Inspired in [ServletContextListener](http://docs.oracle.com/javaee/7/api/javax/servlet/ServletContextListener.html), this code is Java-minimalistic version.
