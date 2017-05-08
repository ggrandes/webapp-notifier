package org.javastack.webappnotifier;

import java.io.UnsupportedEncodingException;
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
		final String service = getServiceName(ctx);
		final String path = ctx.getContextPath();
		final String basename = getContextBaseName(ctx);
		final String trace = getClass().getName() + " context(" + (enqueue ? "QUEUE" : "BLOCKING") + "): " + //
				(initOrDestroy ? "Initialized" : "Destroyed") + //
				" path=" + path + //
				" basename=" + basename + //
				" service=" + service + //
				" connect=" + connectTimeout + "ms" + //
				" read=" + readTimeout + "ms" + //
				" notifyURL=" + notifyURL;
		ctx.log(trace);
		//
		final String body;
		try {
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
			body = sb.toString();
		} catch (UnsupportedEncodingException ex) {
			ctx.log(trace + " UnsupportedEncodingException: " + ex);
			return;
		}
		//
		if (enqueue) {
			NotifierRunner.getInstance().submit(trace, body);
		} else {
			final int ret = notify(body);
			ctx.log(trace + " retCode=" + ret + (ret < 0 ? " (error)" : " (ok)"));
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
}
