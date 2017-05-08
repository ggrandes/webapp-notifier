package org.javastack.webappnotifier.util;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class NotifierRunner extends GenericNotifier implements Runnable {
	private static final NotifierRunner singleton = new NotifierRunner();
	private static final String PROP_QUEUE_KEY = "9fd4e95e-2195-4847-a450-59ad858ce8d0";
	private static final int SHUTDOWN_TIMEOUT = 10000;
	private Thread runner = null;

	private NotifierRunner() {
		super();
	}

	public static NotifierRunner getInstance() {
		return singleton;
	}

	@Override
	public void run() {
		final Log log = LogFactory.getLog(NotifierRunner.class);
		Queue<String[]> queue = null;
		while ((queue = getQueue()) != null) {
			final String[] e = queue.poll();
			try {
				if (e != null) {
					final String trace = e[0];
					final String body = e[1];
					final int ret = notify(body);
					if (ret < 0) {
						log.error(trace + " retCode=" + ret + " (error)");
					} else {
						log.info(trace + " retCode=" + ret + " (ok)");
					}
				} else {
					Thread.sleep(100);
				}
			} catch (Exception ex) {
			}
		}
	}

	private Queue<String[]> getQueue() {
		@SuppressWarnings("unchecked")
		final Queue<String[]> queue = (Queue<String[]>) System.getProperties().get(PROP_QUEUE_KEY);
		return queue;
	}

	private void setQueue(final Queue<String> queue) {
		if (queue != null) {
			System.getProperties().put(PROP_QUEUE_KEY, queue);
		} else {
			System.getProperties().remove(PROP_QUEUE_KEY);
		}
	}

	public void init() {
		synchronized (System.class) {
			if (!isReady()) {
				setQueue(new ArrayBlockingQueue<String>(1024));
				runner = new Thread(this, NotifierRunner.class.getSimpleName());
				runner.setDaemon(true);
				runner.setPriority(Thread.NORM_PRIORITY);
				runner.start();
			}
		}
	}

	public boolean isReady() {
		synchronized (System.class) {
			final Queue<String[]> queue = getQueue();
			return (queue != null);
		}
	}

	public void submit(final String trace, final String task) {
		synchronized (System.class) {
			final Queue<String[]> queue = getQueue();
			if (queue != null) {
				queue.offer(new String[] { trace, task });
			}
		}
	}

	public boolean destroy() {
		synchronized (System.class) {
			final Queue<String[]> queue = getQueue();
			if (queue == null) {
				return true;
			}
			try {
				return awaitTermination(queue);
			} finally {
				setQueue(null);
			}
		}
	}

	private boolean awaitTermination(final Queue<String[]> queue) {
		final long expire = System.currentTimeMillis() + SHUTDOWN_TIMEOUT;
		while (!queue.isEmpty() && (System.currentTimeMillis() < expire)) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e1) {
			}
		}
		return queue.isEmpty();
	}
}