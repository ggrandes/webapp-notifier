package org.javastack.webappnotifier;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * <pre>
 * &lt;-- In server --&gt;
 * &lt;Listener className="org.javastack.webappnotifier.RunnerLifecycleListener" /&gt;
 * </pre>
 */
public class RunnerLifecycleListener implements LifecycleListener {
	private static final Log log = LogFactory.getLog(RunnerLifecycleListener.class);

	@Override
	public void lifecycleEvent(final LifecycleEvent event) {
		final String type = event.getType();
		if (Lifecycle.BEFORE_INIT_EVENT.equals(type)) {
			log.info("Init " + RunnerLifecycleListener.class.getName());
			Runner.getInstance().init();
		} else if (Lifecycle.AFTER_DESTROY_EVENT.equals(type)) {
			log.info("Destroy " + RunnerLifecycleListener.class.getName());
			if (!Runner.getInstance().destroy()) {
				log.error("Destroy Failed");
			}
		}
	}
}
