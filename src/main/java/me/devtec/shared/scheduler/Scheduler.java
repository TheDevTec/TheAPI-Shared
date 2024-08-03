package me.devtec.shared.scheduler;

import me.devtec.shared.API;

public class Scheduler {
	private static final ThreadManager thread = new ThreadManager();

	public static ThreadManager getManager() {
		return thread;
	}

	public static void cancelAll() {
		Scheduler.thread.kill();
	}

	public static void cancelTask(int task) {
		if (Scheduler.thread.isAlive(task))
			Scheduler.thread.destroy(task);
	}

	public static boolean isCancelled(int task) {
		return !Scheduler.thread.isAlive(task) && API.isEnabled();
	}

	public static int run(Runnable r) {
		if (r == null || !API.isEnabled())
			return -1;
		return Scheduler.thread.executeAndGet(() -> {
			try {
				r.run();
			} catch (Throwable err) {
				err.printStackTrace();
			}
		});
	}

	public static int later(long delay, Runnable r) {
		if (r == null || !API.isEnabled())
			return -1;
		int id = Scheduler.thread.incrementAndGet();
		return Scheduler.thread.executeWithId(id, () -> {
			try {
				if (delay > 0)
					Thread.sleep(delay * 50);
				if (!Scheduler.isCancelled(id))
					r.run();
			} catch (InterruptedException | ThreadDeath hide) {
			} catch (Throwable err) {
				err.printStackTrace();
			}
		});
	}

	public static int repeating(long delay, long period, Runnable r) {
		if (r == null || !API.isEnabled() || period < 0)
			return -1;
		int id = Scheduler.thread.incrementAndGet();
		return Scheduler.thread.executeWithId(id, () -> {
			try {
				if (delay > 0)
					Thread.sleep(delay * 50);
				while (!Scheduler.isCancelled(id)) {
					r.run();
					Thread.sleep(period * 50);
				}
			} catch (InterruptedException | ThreadDeath hide) {
			} catch (Throwable err) {
				err.printStackTrace();
			}
		});
	}

	public static int timer(long delay, long period, long times, Runnable r) {
		return Scheduler.repeatingTimes(delay, period, times, r, null);
	}

	public static int repeatingTimes(long delay, long period, long times, Runnable runnable) {
		return Scheduler.repeatingTimes(delay, period, times, runnable, null);
	}

	public static int repeatingTimes(long delay, long period, long times, Runnable runnable, Runnable onFinish) {
		if (runnable == null || !API.isEnabled() || period < 0 || times < 0)
			return -1;
		int id = Scheduler.thread.incrementAndGet();
		return Scheduler.thread.executeWithId(id, new Runnable() {
			int run = 0;

			@Override
			public void run() {
				try {
					if (delay > 0)
						Thread.sleep(delay * 50);
					while (!Scheduler.isCancelled(id) && run++ < times) {
						runnable.run();
						Thread.sleep(period * 50);
					}
					if (onFinish != null && !Scheduler.isCancelled(id))
						onFinish.run();
				} catch (InterruptedException | ThreadDeath hide) {
				} catch (Throwable err) {
					err.printStackTrace();
				}
			}
		});
	}
}