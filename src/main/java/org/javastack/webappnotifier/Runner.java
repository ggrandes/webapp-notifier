package org.javastack.webappnotifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Runner {
	private static final Runner singleton = new Runner();
	private static final String PROP_EXECUTOR_KEY = "ec26e2b1-02c6-4639-a679-b3576ba6cb4f";

	private Runner() {
	}

	public static Runner getInstance() {
		return singleton;
	}

	private ExecutorService getPool() {
		return (ExecutorService) System.getProperties().get(PROP_EXECUTOR_KEY);
	}

	private void setPool(final ExecutorService pool) {
		if (pool != null) {
			System.getProperties().put(PROP_EXECUTOR_KEY, pool);
		} else {
			System.getProperties().remove(PROP_EXECUTOR_KEY);
		}
	}

	public void init() {
		synchronized (System.class) {
			if (!isReady()) {
				final ExecutorService pool = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
				pool.submit(new Runnable() {
					@Override
					public void run() {
					}
				});
				setPool(pool);
			}
		}
	}

	public boolean isReady() {
		synchronized (System.class) {
			final ExecutorService pool = getPool();
			return ((pool != null) && !pool.isShutdown());
		}
	}

	public Future<?> submit(final Runnable task) {
		synchronized (System.class) {
			if (isReady()) {
				final ExecutorService pool = getPool();
				return pool.submit(task);
			}
		}
		return null;
	}

	public boolean destroy() {
		synchronized (System.class) {
			final ExecutorService pool = getPool();
			if (pool != null) {
				try {
					return shutdownAndAwaitTermination(pool);
				} finally {
					setPool(null);
				}
			}
		}
		return true;
	}

	private boolean shutdownAndAwaitTermination(final ExecutorService pool) {
		if (pool == null)
			return true;
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				pool.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
		return pool.isTerminated();
	}

	private static class DaemonThreadFactory implements ThreadFactory {
		private final AtomicInteger threadNumber = new AtomicInteger(1);

		public Thread newThread(final Runnable r) {
			final Thread t = new Thread(r,
					Runner.class.getSimpleName() + "-" + threadNumber.getAndIncrement());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
}