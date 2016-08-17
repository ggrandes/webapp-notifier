package org.javastack.webappnotifier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Runner {
	private static final Runner singleton = new Runner();
	private ExecutorService pool = null;

	private Runner() {
	}

	public static Runner getInstance() {
		return singleton;
	}

	public synchronized void init() {
		if (!isReady()) {
			this.pool = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
			this.pool.submit(new Runnable() {
				@Override
				public void run() {
				}
			});
		}
	}

	public synchronized boolean isReady() {
		return ((pool != null) && !pool.isShutdown());
	}

	public synchronized Future<?> submit(final Runnable task) {
		if (isReady()) {
			return pool.submit(task);
		}
		return null;
	}

	public synchronized boolean destroy() {
		if (pool != null) {
			try {
				return shutdownAndAwaitTermination(pool);
			} finally {
				pool = null;
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
			final Thread t = new Thread(r, Runner.class.getSimpleName() + "-"
					+ threadNumber.getAndIncrement());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
}