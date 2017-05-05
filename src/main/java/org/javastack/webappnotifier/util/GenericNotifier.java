package org.javastack.webappnotifier.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.HttpURLConnection;
import java.net.URL;

public class GenericNotifier {
	protected static final RuntimeMXBean jmx = ManagementFactory.getRuntimeMXBean();
	protected static final String ENCODING = "ISO-8859-1";
	protected static final String BASE_PROP = "org.javastack.webappnotifier.";

	/**
	 * Constant for: <b>org.javastack.webappnotifier.url</b>
	 */
	public static final String URL_PROP = BASE_PROP + "url";
	/**
	 * Constant for: <b>org.javastack.webappnotifier.defaultConnectTimeout</b>
	 */
	public static final String CONNECT_PROP = BASE_PROP + "defaultConnectTimeout";
	/**
	 * Constant for: <b>org.javastack.webappnotifier.defaultReadTimeout</b>
	 */
	public static final String READ_PROP = BASE_PROP + "defaultReadTimeout";
	/**
	 * Constant for: <b>org.javastack.webappnotifier.retryCount</b>
	 */
	public static final String RETRY_PROP = BASE_PROP + "retryCount";
	/**
	 * Constant for: <b>org.javastack.webappnotifier.customValue</b>
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

	/**
	 * URL to invoke for notification
	 */
	protected final String notifyURL;
	/**
	 * Connection timeout (millis)
	 */
	protected final int connectTimeout;
	/**
	 * Read timeout (millis)
	 */
	protected final int readTimeout;
	/**
	 * Retry count (total)
	 */
	protected final int tries;
	/**
	 * Custom value used in notification
	 */
	protected final String customValue;

	protected GenericNotifier() {
		notifyURL = System.getProperty(URL_PROP);
		connectTimeout = Math.max(Integer.getInteger(CONNECT_PROP, DEF_CONNECT_TIMEOUT), 1000);
		readTimeout = Math.max(Integer.getInteger(READ_PROP, DEF_READ_TIMEOUT), 1000);
		tries = Math.max(Integer.getInteger(RETRY_PROP, DEF_RETRY_COUNT), 0) + 1;
		customValue = System.getProperty(CUSTOM_PROP, "");
	}

	protected final int getRandomSleep(final boolean needSleep, final int min, final int max) {
		return (needSleep ? Math.max(min, (int) (Math.random() * 1000000) % max) : 0);
	}

	protected final void doSleep(final long millis) {
		try {
			if (millis > 0) {
				Thread.sleep(millis);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	protected final int request(final URL url, final int connectTimeout, final int readTimeout,
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
				closeQuietly(urlIs);
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

	protected static final void closeQuietly(final Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Exception ign) {
			}
		}
	}
}
