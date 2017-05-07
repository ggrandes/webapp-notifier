package org.javastack.webappnotifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.javastack.webappnotifier.util.GenericNotifier;
import org.javastack.webappnotifier.util.NotifierRunner;
import org.javastack.webappnotifier.util.TomcatHelper;

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
public class WebAppNotifierContextListener extends GenericNotifier implements ServletContextListener {
	private static final String KEY_CACHE = WebAppNotifierContextListener.class.getName()
			+ ".serviceName.cache";

	@Override
	public void contextInitialized(final ServletContextEvent contextEvent) {
		if (notifyURL == null) {
			contextEvent.getServletContext().log("Invalid System Property: " + URL_PROP + " (null)");
		}
		contextNotify(contextEvent, true);
	}

	@Override
	public void contextDestroyed(final ServletContextEvent contextEvent) {
		contextNotify(contextEvent, false);
	}

	private void contextNotify(final ServletContextEvent contextEvent, final boolean initOrDestroy) {
		if (notifyURL == null) {
			return;
		}
		final boolean enqueue = NotifierRunner.getInstance().isReady();
		final ServletContext ctx = contextEvent.getServletContext();
		ctx.log(getClass().getName() + " Context " + (enqueue ? "ENQUEUE" : "BLOCKING") + " mode");
		final String service = getServiceName(ctx);
		if (enqueue) {
			NotifierRunner.getInstance().submit(new Thread() {
				public void run() {
					doContextNotify(ctx, service, initOrDestroy);
				}
			});
		} else {
			doContextNotify(ctx, service, initOrDestroy);
		}
	}

	private final String getContextBaseName(final ServletContext ctx) {
		final String path = ctx.getContextPath();
		return (path.isEmpty() ? "ROOT" : path.substring(1).replace('/', '#'));
	}

	private final String getServiceName(final ServletContext ctx) {
		String service = (String) ctx.getAttribute(KEY_CACHE);
		if (service != null) {
			return service;
		}
		try {
			service = new TomcatHelper().getServiceNameFromClassLoader(ctx);
			if (service != null) {
				ctx.log(getClass().getName() + " context: getServiceName(classloader): " + service);
				ctx.setAttribute(KEY_CACHE, service);
				return service;
			}
		} catch (Throwable t) {
			ctx.log(getClass().getName() + " context: unable to getServiceName(classloader): " + t);
		}
		try {
			service = new TomcatHelper().getEngineNameByReflect(ctx);
			if (service != null) {
				ctx.log(getClass().getName() + " context: getEngineName(reflect): " + service);
				ctx.setAttribute(KEY_CACHE, service);
				return service;
			}
		} catch (Throwable t) {
			ctx.log(getClass().getName() + " context: unable to getEngineName(reflect): " + t);
		}
		service = "";
		ctx.setAttribute(KEY_CACHE, service);
		return service;
	}

	private final void doContextNotify(final ServletContext ctx, final String service, final boolean initOrDestroy) {
		final String path = ctx.getContextPath();
		final String basename = getContextBaseName(ctx);
		for (int i = 0; i < tries; i++) {
			final String trace = getClass().getName() + " context: " + //
					(initOrDestroy ? "Initialized" : "Destroyed") + //
					" path=" + path + //
					" basename=" + basename + //
					" service=" + service + //
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
				sb.append("type=").append(initOrDestroy ? "I" : "D").append('&');
				sb.append("path=").append(URLEncoder.encode(path, ENCODING)).append('&');
				sb.append("basename=").append(URLEncoder.encode(basename, ENCODING)).append('&');
				sb.append("service=").append(URLEncoder.encode(service, ENCODING)).append('&');
				sb.append("event=").append("C");
				final byte[] body = sb.toString().getBytes(ENCODING);
				final int retCode = request(url, connectTimeout, readTimeout, "POST",
						"application/x-www-form-urlencoded", new ByteArrayInputStream(body), body.length);
				// Dont retry: Info (1xx), OK (2xx), Redir (3xx), Client Error (4xx)
				if ((retCode >= 100) && (retCode <= 499)) {
					ctx.log(trace + " retCode=" + retCode + (retCode < 400 ? " (ok)" : " (noretry)"));
					break;
				} else {
					final long sleep = getRandomSleep(needSleep, 100, 3000);
					ctx.log(trace + " retCode=" + retCode + " sleep=" + sleep + "ms");
					doSleep(sleep);
				}
			} catch (IOException e) {
				final long sleep = getRandomSleep(needSleep, 100, 3000);
				ctx.log(trace + " sleep=" + sleep + "ms IOException: " + e, e);
				doSleep(sleep);
			}
		}
	}
}
