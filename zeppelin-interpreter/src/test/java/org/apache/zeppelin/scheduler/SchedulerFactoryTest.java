package org.apache.zeppelin.scheduler;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerFactoryTest {

  @Test
  void testNoZombieSchedulerOnExecutionRejection() {
    SchedulerFactory factory = SchedulerFactory.singleton();
    ExecutorService mockExecutor = mock(ExecutorService.class);

    // Inject mock executor
    ExecutorService originalExecutor = factory.executor;
    factory.executor = mockExecutor;

    String schedulerName = "test-zombie";
    doThrow(new RejectedExecutionException("Pool full")).when(mockExecutor).execute(any(Runnable.class));

    try {
      assertThrows(RuntimeException.class, () -> {
        factory.createOrGetFIFOScheduler(schedulerName);
      });

      // Verify scheduler is NOT in the map
      assertFalse(factory.schedulers.containsKey(schedulerName), "Scheduler should not be in the map if execution failed");

    } finally {
      // Restore original executor
      factory.executor = originalExecutor;
    }
  }
}
