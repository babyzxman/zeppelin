package org.apache.zeppelin.notebook;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.resource.ResourcePool;

/**
 * Periodically pushes server side resources into every running interpreter's
 * {@link ResourcePool}. A resource is only broadcast when its value changes to
 * avoid repeatedly serialising identical objects.
 */
public class ResourceBroadcastService implements AutoCloseable {

  private final InterpreterSettingManager interpreterSettingManager;
  private final ScheduledExecutorService executor;
  private final Map<String, Object> resources = new ConcurrentHashMap<>();
  private final Map<String, Object> lastSent = new ConcurrentHashMap<>();

  public ResourceBroadcastService(InterpreterSettingManager manager) {
    this.interpreterSettingManager = manager;
    this.executor = Executors.newSingleThreadScheduledExecutor();
    this.executor.scheduleAtFixedRate(this::broadcast, 0, 1, TimeUnit.SECONDS);
  }

  /**
   * Update or insert a named resource to be broadcast.
   */
  public void updateResource(String name, Object value) {
    resources.put(name, value);
  }

  void broadcast() {
    for (Map.Entry<String, Object> entry : resources.entrySet()) {
      String name = entry.getKey();
      Object value = entry.getValue();
      Object previous = lastSent.get(name);
      if (Objects.equals(previous, value)) {
        continue;
      }
      lastSent.put(name, value);
      for (ManagedInterpreterGroup group : interpreterSettingManager.getAllInterpreterGroup()) {
        ResourcePool pool = group.getResourcePool();
        if (pool != null) {
          pool.put(name, value);
        }
      }
    }
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
