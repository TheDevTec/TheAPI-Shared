package me.devtec.shared.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import me.devtec.shared.API;

public class ThreadManager implements Executor {
	protected final List<Runnable> onKill = new ArrayList<>();

	private final ThreadPoolExecutor executorService;
	private final Map<Integer, Future<?>> taskMap = new ConcurrentHashMap<>();
	private final AtomicInteger idCounter = new AtomicInteger();

	public ThreadManager() {
		executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5L, TimeUnit.SECONDS, new SynchronousQueue<>());
		executorService.setKeepAliveTime(5, TimeUnit.SECONDS);
		new Thread(this::monitorThreads).start();
	}

	private void monitorThreads() {
		while (API.isEnabled())
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				break;
			}
		shutdownAndAwaitTermination();
	}

	private void shutdownAndAwaitTermination() {
		try {
			// Ukončení nových úloh
			executorService.shutdown();

			if (!executorService.awaitTermination(5, TimeUnit.SECONDS))
				// Přerušení zbývajících běžících úloh
				executorService.shutdownNow();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	public void kill() {
		for (int i : new ArrayList<>(taskMap.keySet()))
			destroy(i);
		for (Runnable runnable : onKill)
			runnable.run();
		onKill.clear();
	}

	public ThreadManager register(Runnable runnable) {
		onKill.add(runnable);
		return this;
	}

	public ThreadManager unregister(Runnable runnable) {
		onKill.remove(runnable);
		return this;
	}

	public boolean isAlive(int id) {
		Future<?> future = taskMap.get(id);
		return future != null && !future.isDone() && !future.isCancelled();
	}

	public int incrementAndGet() {
		return idCounter.incrementAndGet();
	}

	public void destroy(int id) {
		Future<?> future = taskMap.remove(id);
		if (future != null)
			future.cancel(true);
	}

	public void kill(int id) {
		destroy(id);
	}

	public int executeWithId(int id, Runnable command) {
		Future<Void> future = executorService.submit(() -> {
			try {
				command.run();
			} finally {
				taskMap.remove(id);
			}
			return null;
		});
		taskMap.put(id, future);
		return id;
	}

	public int executeAndGet(Runnable command) {
		return executeWithId(incrementAndGet(), command);
	}

	@Override
	public void execute(Runnable command) {
		executeWithId(incrementAndGet(), command);
	}
}