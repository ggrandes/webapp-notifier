package org.javastack.webappnotifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.javastack.webappnotifier.util.GenericNotifier;
import org.javastack.webappnotifier.util.NotifierRunner;

/**
 * <pre>
 * &lt;-- In server.xml --&gt;
 * &lt;Listener className="org.javastack.webappnotifier.TomcatLifecycleListener" 
 *           resolveHostname="false" /&gt;
 * </pre>
 */
public class TomcatLifecycleListener extends GenericNotifier implements LifecycleListener {
	private static final Log log = LogFactory.getLog(TomcatLifecycleListener.class);
	/**
	 * Resolve hostname to IPs
	 */
	private boolean resolveHostname = false;

	private Set<String> https, http, ajp;

	public boolean getResolveHostname() {
		return resolveHostname;
	}

	public void setResolveHostname(final boolean resolveHostname) {
		this.resolveHostname = resolveHostname;
	}

	@Override
	public void lifecycleEvent(final LifecycleEvent event) {
		final String type = event.getType();
		if (Lifecycle.BEFORE_START_EVENT.equals(type)) {
			log.info("Init " + TomcatLifecycleListener.class.getName());
			if (notifyURL == null) {
				log.error("Invalid System Property: " + URL_PROP + " (null)");
				return;
			}
			final Set<String> https = new LinkedHashSet<String>();
			final Set<String> http = new LinkedHashSet<String>();
			final Set<String> ajp = new LinkedHashSet<String>();
			try {
				getEndPoints(https, http, ajp);
			} catch (Exception e) {
				log.error("Unable to discover endpoints: " + e, e);
			} finally {
				this.https = Collections.unmodifiableSet(https);
				this.http = Collections.unmodifiableSet(http);
				this.ajp = Collections.unmodifiableSet(ajp);
			}
			endpointNotify(https, http, ajp, true);
		} else if (Lifecycle.BEFORE_DESTROY_EVENT.equals(type)) {
			log.info("Destroy " + TomcatLifecycleListener.class.getName());
			endpointNotify(https, http, ajp, false);
		}
	}

	private void getEndPoints(final Set<String> https, final Set<String> http, final Set<String> ajp)
			throws MalformedObjectNameException, NullPointerException, UnknownHostException,
			AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException {
		final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		final QueryExp subQuery1 = Query.match(Query.attr("protocol"), Query.value("HTTP/1.1"));
		final QueryExp subQuery2 = Query.anySubString(Query.attr("protocol"), Query.value("Http11"));
		final QueryExp subQuery3 = Query.match(Query.attr("protocol"), Query.value("AJP/1.3"));
		final QueryExp subQuery4 = Query.anySubString(Query.attr("protocol"), Query.value("Ajp"));
		final QueryExp query = Query.or(Query.or(subQuery1, subQuery2), Query.or(subQuery3, subQuery4));
		final Set<ObjectName> objs = mbs.queryNames(new ObjectName("*:type=Connector,*"), query);
		final String hostname = InetAddress.getLocalHost().getHostName();
		final InetAddress[] addresses = InetAddress.getAllByName(hostname);
		for (Iterator<ObjectName> i = objs.iterator(); i.hasNext();) {
			final ObjectName obj = i.next();
			String protocol = String.valueOf(mbs.getAttribute(obj, "protocol"));
			String scheme = String.valueOf(mbs.getAttribute(obj, "scheme"));
			String port = String.valueOf(obj.getKeyProperty("port"));
			if (protocol.toUpperCase().contains("AJP")) {
				scheme = "ajp";
			}
			if (resolveHostname) {
				for (final InetAddress addr : addresses) {
					if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isMulticastAddress()) {
						continue;
					}
					final String host = addr.getHostAddress();
					final String ep = scheme + "://" + host + ":" + port;
					log.info("Discovered endpoint (" + protocol + ")(resolve): " + ep);
					if (scheme.equalsIgnoreCase("ajp")) {
						ajp.add(ep);
					} else if (scheme.equalsIgnoreCase("http")) {
						http.add(ep);
					} else if (scheme.equalsIgnoreCase("https")) {
						https.add(ep);
					}
				}
			} else {
				final String ep = scheme + "://" + hostname + ":" + port;
				log.info("Discovered endpoint (" + protocol + ")(noresolve): " + ep);
				if (scheme.equalsIgnoreCase("ajp")) {
					ajp.add(ep);
				} else if (scheme.equalsIgnoreCase("http")) {
					http.add(ep);
				} else if (scheme.equalsIgnoreCase("https")) {
					https.add(ep);
				}
			}
		}
	}

	private void endpointNotify(final Set<String> https, final Set<String> http, final Set<String> ajp,
			final boolean initOrDestroy) {
		if (notifyURL == null) {
			return;
		}
		final boolean enqueue = NotifierRunner.getInstance().isReady();
		log.info(getClass().getName() + " Endpoint " + (enqueue ? "ENQUEUE" : "BLOCKING") + " mode");
		if (enqueue) {
			NotifierRunner.getInstance().submit(new Thread() {
				public void run() {
					doEndpointNotify(https, http, ajp, initOrDestroy);
				}
			});
		} else {
			doEndpointNotify(https, http, ajp, initOrDestroy);
		}
	}

	private final void doEndpointNotify(final Set<String> https, final Set<String> http,
			final Set<String> ajp, final boolean initOrDestroy) {
		for (int i = 0; i < tries; i++) {
			final String trace = getClass().getName() + " endpoint: " + //
					(initOrDestroy ? "Initialized" : "Destroyed") + //
					" https=" + https + //
					" http=" + http + //
					" ajp=" + ajp + //
					" connect=" + connectTimeout + "ms" + //
					" read=" + readTimeout + "ms" + //
					" try=" + (i + 1) + "/" + tries + //
					" notifyURL=" + notifyURL;
			final boolean needSleep = ((i + 1) < tries);
			try {
				final URL url = new URL(notifyURL);
				final StringBuilder sb = new StringBuilder();
				sb.append("ts=").append(System.currentTimeMillis()).append('&');
				sb.append("jvmid=").append(URLEncoder.encode(jmx.getName(), ENCODING)).append('&');
				if (customValue != null) {
					sb.append("custom=").append(URLEncoder.encode(customValue, ENCODING)).append('&');
				}
				sb.append("event=").append("E").append('&');
				sb.append("type=").append(initOrDestroy ? "I" : "D").append('&');
				for (final String p : https) {
					sb.append("https=").append(URLEncoder.encode(p, ENCODING)).append('&');
				}
				for (final String p : http) {
					sb.append("http=").append(URLEncoder.encode(p, ENCODING)).append('&');
				}
				for (final String p : ajp) {
					sb.append("ajp=").append(URLEncoder.encode(p, ENCODING)).append('&');
				}
				final byte[] body = sb.toString().getBytes(ENCODING);
				final int retCode = request(url, connectTimeout, readTimeout, "POST",
						"application/x-www-form-urlencoded", new ByteArrayInputStream(body), body.length);
				// Dont retry: Info (1xx), OK (2xx), Redir (3xx), Client Error (4xx)
				if ((retCode >= 100) && (retCode <= 499)) {
					log.info(trace + " retCode=" + retCode + " (noretry)");
					break;
				} else {
					final long sleep = getRandomSleep(needSleep, 100, 3000);
					log.info(trace + " retCode=" + retCode + " sleep=" + sleep + "ms");
					doSleep(sleep);
				}
			} catch (IOException e) {
				final long sleep = getRandomSleep(needSleep, 100, 3000);
				log.error(trace + " sleep=" + sleep + "ms IOException: " + e, e);
				doSleep(sleep);
			}
		}
	}
}
