package org.javastack.webappnotifier.util;

import java.lang.reflect.Field;

import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.loader.WebappClassLoaderBase;

public class TomcatHelper {
	public final String getServiceNameFromClassLoader(final ServletContext ctx) {
		// Tomcat 8.5+
		final ClassLoader cl = getClass().getClassLoader();
		if (cl instanceof WebappClassLoaderBase) {
			@SuppressWarnings("resource")
			final WebappClassLoaderBase wcl = (WebappClassLoaderBase) cl;
			return wcl.getServiceName();
		}
		return null;
	}

	public final String getEngineNameByReflect(final ServletContext ctx)
			throws ReflectiveOperationException {
		// Tomcat 7, Try reflect
		final Field[] fldsFacade = ctx.getClass().getDeclaredFields();
		for (final Field fldFacade : fldsFacade) {
			if (fldFacade.getType().isAssignableFrom(ApplicationContext.class)) {
				fldFacade.setAccessible(true);
				final ApplicationContext appctx = (ApplicationContext) fldFacade.get(ctx);
				final Field[] fldsApp = appctx.getClass().getDeclaredFields();
				for (final Field fldApp : fldsApp) {
					if (fldApp.getType().isAssignableFrom(StandardContext.class)) {
						fldApp.setAccessible(true);
						final StandardContext stdctx = (StandardContext) fldApp.get(appctx);
						Container c = stdctx;
						int cx = 50;
						while ((c.getParent() != null) && (--cx > 0)) {
							c = c.getParent();
							if (c instanceof StandardEngine) {
								return c.getName();
							}
						}
					}
				}
			}
		}
		return null;
	}
}
