package org.apache.zeppelin.scheduler;

import org.apache.zeppelin.util.ExecutorUtil;

import java.util.concurrent.*;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;

public class ExecutorTimeoutFactory {

  private final Map<String, ExecutorService> executors = new HashMap<>();
  private final Map<String, ScheduledExecutorService> scheduledExecutors = new HashMap<>();

  private ExecutorTimeoutFactory() {}

  // Singleton instance using Initialization-on-demand holder idiom
  private static final class InstanceHolder {
    private static final ExecutorTimeoutFactory INSTANCE = new ExecutorTimeoutFactory();
  }

  public static ExecutorTimeoutFactory singleton() {
    return InstanceHolder.INSTANCE;
  }

  /**
   * Create or get dynamic ThreadPoolExecutor that allows idle thread timeout.
   */
  public ExecutorService createOrGet(String name, int maxThreads) {
    synchronized (executors) {
      if (!executors.containsKey(name)) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                maxThreads,
                300L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new SchedulerThreadFactory(name));
        executor.allowCoreThreadTimeOut(true);
        executors.put(name, executor);
      }
      return executors.get(name);
    }
  }

  /**
   * Create or get ScheduledExecutorService (used for scheduleAtFixedRate tasks).
   */
  public ScheduledExecutorService createOrGetScheduled(String name, int numThreads) {
    synchronized (scheduledExecutors) {
      if (!scheduledExecutors.containsKey(name)) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(
                numThreads,
                new SchedulerThreadFactory(name)
        );
        scheduledExecutors.put(name, executor);
      }
      return scheduledExecutors.get(name);
    }
  }

  /**
   * Get default note job executor (fixed size).
   */
  public ExecutorService getNoteJobExecutor() {
    return createOrGet("NoteJobThread-", 50);
  }

  /**
   * Shutdown specific executor by name.
   */
  public void shutdown(String name) {
    synchronized (executors) {
      ExecutorService e = executors.remove(name);
      if (e != null) {
        ExecutorUtil.softShutdown(name, e, 1, TimeUnit.MINUTES);
      }
    }
    synchronized (scheduledExecutors) {
      ScheduledExecutorService e = scheduledExecutors.remove(name);
      if (e != null) {
        ExecutorUtil.softShutdown(name, e, 1, TimeUnit.MINUTES);
      }
    }
  }

  public void shutdownAll() {
    synchronized (executors) {
      for (Entry<String, ExecutorService> executor : executors.entrySet()) {
        ExecutorUtil.softShutdown(executor.getKey(), executor.getValue(), 1, TimeUnit.MINUTES);
      }
      executors.clear();
    }
    synchronized (scheduledExecutors) {
      for (Entry<String, ScheduledExecutorService> scheduledExecutor : scheduledExecutors.entrySet()) {
        ExecutorUtil.softShutdown(scheduledExecutor.getKey(), scheduledExecutor.getValue(), 1, TimeUnit.MINUTES);
      }
      scheduledExecutors.clear();
    }
  }
}
