package me.devtec.shared.scheduler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadManager implements Executor {
	protected final Map<Integer, Thread> threads = new ConcurrentHashMap<>();
	protected final List<Runnable> onKill = new ArrayList<>();
	protected final AtomicInteger i = new AtomicInteger();

	public void kill() {
		List<Thread> check = new ArrayList<>();
		Iterator<Thread> it = threads.values().iterator();
		while (it.hasNext()) {
			Thread tht = it.next();
			it.remove();
			if (tht != null && tht.isAlive()) {
				tht.interrupt(); // safe destroy of thread
				check.add(tht);
			}
		}
		for (Runnable runnable : onKill)
			runnable.run();
		onKill.clear();
		if (!check.isEmpty())
			new Thread(() -> {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					return;
				}
				for (Thread thread : check)
					if (thread.isAlive()) {
						thread.stop();
						System.out.println("Stopped thread that was not interrupted normally (Infinity loop?)");
					}
				check.clear();
			}).start();
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
		return threads.containsKey(id) && threads.get(id).isAlive();
	}

	public Map<Integer, Thread> getThreads() {
		return threads;
	}

	public int incrementAndGet() {
		return i.incrementAndGet();
	}

	public void destroy(int id) {
		Thread t = threads.remove(id);
		if (t == null)
			return;
		t.interrupt(); // safe destroy of thread
	}

	public void kill(int id) {
		Thread t = threads.remove(id);
		if (t == null)
			return;
		t.interrupt(); // safe destroy of thread
		new Thread(() -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				return;
			}
			if (t.isAlive()) {
				t.stop();
				System.out.println("Stopped thread that was not interrupted normally (Infinity loop?)");
			}
		}).start();
	}

	public int executeWithId(int id, Runnable command) {
		Thread t = new Thread(command, "AsyncThreadWorker-" + id);
		threads.put(id, t);
		t.start();
		return id;
	}

	public int executeAndGet(Runnable command) {
		int id = i.incrementAndGet();
		return executeWithId(id, command);
	}

	@Override
	public void execute(Runnable command) {
		int id = i.incrementAndGet();
		executeWithId(id, command);
	}
}