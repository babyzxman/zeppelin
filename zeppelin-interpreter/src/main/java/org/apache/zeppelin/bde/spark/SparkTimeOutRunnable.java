package org.apache.zeppelin.bde.spark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SparkTimeOutRunnable implements Runnable{
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkTimeOutRunnable.class);

    String interpreterGroupId;

    SparkTimeOutService sparkTimeOutService;

    long lastBusyTimeInMillis;

    public SparkTimeOutRunnable(String interpreterGroupId, long lastBusyTimeInMillis) {
        this.interpreterGroupId = interpreterGroupId;
        this.sparkTimeOutService = new SparkTimeOutService();
        this.lastBusyTimeInMillis = lastBusyTimeInMillis;
    }

    @Override
    public void run() {
        sparkTimeOutService.sendSparkTimeOut(interpreterGroupId,lastBusyTimeInMillis);
    }
}
