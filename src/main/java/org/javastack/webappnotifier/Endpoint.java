package org.javastack.webappnotifier;

import java.util.Collections;
import java.util.Set;

public class Endpoint {
	public final Set<String> https, http, ajp;
	public final String jvmRoute;

	public Endpoint(final Set<String> https, final Set<String> http, final Set<String> ajp,
			final String jvmRoute) {
		this.https = Collections.unmodifiableSet(https);
		this.http = Collections.unmodifiableSet(http);
		this.ajp = Collections.unmodifiableSet(ajp);
		this.jvmRoute = jvmRoute;
	}

	@Override
	public String toString() {
		return "https=" + https + " http=" + http + " ajp=" + ajp + " jvmRoute=" + jvmRoute;
	}
}
