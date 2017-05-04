package org.javastack.webappnotifier;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Notify about start and stop of WebApp in a Servlet Container (like Tomcat) to a remote URL
 * 
 * <pre>
 * &lt;listener&gt;
 * &lt;description&gt;Notify about start and stop of WebApp to a remote RESTful service&lt;/description&gt;
 * &lt;listener-class&gt;org.javastack.webappnotifier.WebAppNotifierContextListener&lt;/listener-class&gt;
 * &lt;/listener&gt;
 * </pre>
 */
@WebListener
public class WebAppNotifierContextListener implements ServletContextListener {
	private static final String ENCODING = "ISO-8859-1";

	private static final RuntimeMXBean jmx = ManagementFactory.getRuntimeMXBean();
	private static final String BASE_PROP = "org.javastack.webappnotifier.";

	/**
	 * Constant for: <b>org.javastack.webapp.notifier.url</b>
	 */
	public static final String URL_PROP = BASE_PROP + "url";
	/**
	 * Constant for: <b>org.javastack.webapp.notifier.defaultConnectTimeout</b>
	 */
	public static final String CONNECT_PROP = BASE_PROP + "defaultConnectTimeout";
	/**
	 * Constant for: <b>org.javastack.webapp.notifier.defaultReadTimeout</b>
	 */
	public static final String READ_PROP = BASE_PROP + "defaultReadTimeout";
	/**
	 * Constant for: <b>org.javastack.webapp.notifier.retryCount</b>
	 */
	public static final String RETRY_PROP = BASE_PROP + "retryCount";
	/**
	 * Constant for: <b>org.javastack.webapp.notifier.customValue</b>
	 */
	public static final String CUSTOM_PROP = BASE_PROP + "customValue";

	/**
	 * Default connect timeout: 5sec
	 */
	public static final int DEF_CONNECT_TIMEOUT = 5000; // fail-fast
	/**
	 * Default read timeout: 5sec
	 */
	public static final int DEF_READ_TIMEOUT = 5000; // fail-fast
	/**
	 * Default retry count: 2 retries
	 */
	public static final int DEF_RETRY_COUNT = 2;

	private final String notifyURL = System.getProperty(URL_PROP);
	private final int connectTimeout = Math.max(Integer.getInteger(CONNECT_PROP, DEF_CONNECT_TIMEOUT), 1000);
	private final int readTimeout = Math.max(Integer.getInteger(READ_PROP, DEF_READ_TIMEOUT), 1000);
	private final int tries = 1 + Math.max(Integer.getInteger(RETRY_PROP, DEF_RETRY_COUNT), 0);
	private final String customValue = System.getProperty(CUSTOM_PROP, "");

	@Override
	public void contextInitialized(final ServletContextEvent contextEvent) {
		doEnqueOrRun(contextEvent, true);
	}

	@Override
	public void contextDestroyed(final ServletContextEvent contextEvent) {
		doEnqueOrRun(contextEvent, false);
	}

	private final void doEnqueOrRun(final ServletContextEvent contextEvent, final boolean initOrDestroy) {
		final ServletContext ctx = contextEvent.getServletContext();
		final boolean enqueue = Runner.getInstance().isReady();
		ctx.log(getClass().getName() + " " + (enqueue ? "ENQUEUE" : "BLOCKING") + " mode");
		if (enqueue) {
			Runner.getInstance().submit(new Thread() {
				public void run() {
					contextNotify(contextEvent, initOrDestroy);
				}
			});
		} else {
			contextNotify(contextEvent, initOrDestroy);
		}
	}

	private final void contextNotify(final ServletContextEvent contextEvent, final boolean initOrDestroy) {
		final ServletContext ctx = contextEvent.getServletContext();
		final String path = ctx.getContextPath();
		final String basename = getContextBaseName(ctx);
		if (notifyURL == null) {
			ctx.log("[ERROR] Invalid System Property: " + URL_PROP + " (null)");
			return;
		}
		for (int i = 0; i < tries; i++) {
			final String trace = getClass().getName() + " context: " + //
					(initOrDestroy ? "Initialized" : "Destroyed") + //
					" path=" + path + //
					" basename=" + basename + //
					" connect=" + connectTimeout + "ms" + //
					" read=" + readTimeout + "ms" + //
					" try=" + (i + 1) + "/" + tries + //
					" notifyURL=" + notifyURL;
			final boolean needSleep = ((i + 1) < tries);
			try {
				final URL url = new URL(notifyURL);
				final StringBuilder sb = new StringBuilder();
				sb.append("type=").append(initOrDestroy ? "I" : "D").append('&');
				sb.append("ts=").append(System.currentTimeMillis()).append('&');
				sb.append("jvmid=").append(URLEncoder.encode(jmx.getName(), ENCODING)).append('&');
				if (customValue != null) {
					sb.append("custom=").append(URLEncoder.encode(customValue, ENCODING)).append('&');
				}
				sb.append("path=").append(URLEncoder.encode(path, ENCODING)).append('&');
				sb.append("basename=").append(URLEncoder.encode(basename, ENCODING));
				final byte[] body = sb.toString().getBytes(ENCODING);
				final int retCode = request(url, connectTimeout, readTimeout, "POST",
						"application/x-www-form-urlencoded", new ByteArrayInputStream(body), body.length);
				// Dont retry: Info (1xx), OK (2xx), Redir (3xx), Client Error (4xx)
				if ((retCode >= 100) && (retCode <= 499)) {
					ctx.log(trace + " retCode=" + retCode);
					break;
				} else {
					final long sleep = getRandomSleep(needSleep, 100, 3000);
					ctx.log(trace + " retCode=" + retCode + " sleep=" + sleep + "ms");
					doSleep(sleep);
				}
			} catch (IOException e) {
				final long sleep = getRandomSleep(needSleep, 100, 3000);
				ctx.log(trace + " sleep=" + sleep + "ms", e);
				doSleep(sleep);
			}
		}
	}

	private final int getRandomSleep(final boolean needSleep, final int min, final int max) {
		return (needSleep ? Math.max(min, (int) (Math.random() * 1000000) % max) : 0);
	}

	private final void doSleep(final long millis) {
		try {
			if (millis > 0) {
				Thread.sleep(millis);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private final String getContextBaseName(final ServletContext ctx) {
		final String path = ctx.getContextPath();
		return (path.isEmpty() ? "ROOT" : path.substring(1).replace('/', '#'));
	}

	private final int request(final URL url, final int connectTimeout, final int readTimeout,
			final String method, final String contentType, final InputStream doOutput,
			final int contentLength) throws IOException {
		HttpURLConnection conn = null;
		InputStream urlIs = null;
		OutputStream urlOs = null;
		//
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod(method);
			conn.setDoInput(true);
			if (doOutput != null) {
				conn.setRequestProperty("Content-Type", contentType);
				conn.setRequestProperty("Cache-Control", "no-cache; max-age=0");
				conn.setRequestProperty("Pragma", "no-cache");
				conn.setDoOutput(true);
				if (contentLength > 0)
					conn.setFixedLengthStreamingMode(contentLength);
			}
			conn.setConnectTimeout(connectTimeout);
			conn.setReadTimeout(readTimeout);
			final byte[] buf = new byte[512];
			if (doOutput != null) {
				conn.connect();
				int len = 0;
				urlOs = conn.getOutputStream();
				while ((len = doOutput.read(buf)) > 0) {
					urlOs.write(buf, 0, len);
				}
				urlOs.flush();
			} else {
				conn.connect();
			}
			// Get the response
			final int resCode = conn.getResponseCode();
			try {
				urlIs = conn.getInputStream();
			} catch (Exception e) {
				urlIs = conn.getErrorStream();
			}
			// Consume response
			if (urlIs != null) {
				while (urlIs.read(buf) != -1) {
				}
			} else {
				throw new IOException("HTTP(" + resCode + ")");
			}
			return resCode;
		} finally {
			closeQuietly(urlIs);
			closeQuietly(urlOs);
		}
	}

	private static final void closeQuietly(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception ign) {
			}
		}
	}
}
