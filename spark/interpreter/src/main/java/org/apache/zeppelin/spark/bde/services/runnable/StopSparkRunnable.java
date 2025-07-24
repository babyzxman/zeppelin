package org.apache.zeppelin.spark.bde.services.runnable;

import org.apache.zeppelin.spark.bde.services.license.HealthStatusCronRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class StopSparkRunnable implements Runnable{

    AtomicInteger SESSION_NUM;

    public StopSparkRunnable(AtomicInteger sessionNum) {
        this.SESSION_NUM = sessionNum;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StopSparkRunnable.class);

    @Override
    public void run() {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        LOGGER.info("Pid: " + pid);
        String killCmd = "kill -9 " + pid;
        try {
            Thread.sleep(30000);
            LOGGER.info("session num = " + SESSION_NUM.get());
            if (SESSION_NUM.get() == 0) {
                LOGGER.info("Kill process spark pid: " + pid);
//                Runtime.getRuntime().exec(killCmd);
            }
        } catch (Exception e) {
            LOGGER.error("Exception when try to kill process Pid: " + pid, e);
            throw new RuntimeException(e);
        }

    }
}
