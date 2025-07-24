package org.apache.zeppelin.spark.bde.services.license;

import org.apache.spark.SparkContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.spark.SparkInterpreter;
import org.apache.zeppelin.spark.bde.services.spark.SparkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;
import scala.collection.JavaConverters;

import java.util.Date;
import java.util.Map;

public class HealthStatusRunnable implements Runnable{
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthStatusRunnable.class);
    SparkContext sc;
    LicenseService bdeLicenseServices;

    SparkService sparkService;
    long startTime;

    Double core;

    int executorCores;

    String[] sparkUrls;

    String memory;


    SparkInterpreter sparkInterpreter;

    public HealthStatusRunnable(SparkContext sc,long startTime, Double core, int executorCores, String[] sparkUrls,
                                String memory, SparkInterpreter sparkInterpreter){
        this.sc = sc;
        this.bdeLicenseServices = new LicenseService();
        this.sparkService = new SparkService();
        this.startTime = startTime;
        this.core = core;
        this.executorCores = executorCores;
        this.sparkUrls = sparkUrls;
        this.memory = memory;
        this.sparkInterpreter = sparkInterpreter;
    }

    @Override
    public void run(){
        String sparkAppName = sc.appName();
        String jobId = sparkAppName.contains("-") ? (sparkAppName.split("-"))[1] + "-" + this.startTime : null;
        Date currentStartTime = new Date(System.currentTimeMillis());
        Date currentTime = currentStartTime;
        LOGGER.info("Start sending health status (license statement) of spark : {} , jobId : {} ...",sparkAppName,jobId);
        try {
            //For first time to send health status of time
            currentTime = startJobSendSparkHealthStatus(jobId, currentStartTime, currentTime);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(),e);
        }
        while (!sc.isStopped()) {
            try {
                Thread.sleep(10000);
                scala.collection.Map<String, Tuple2<Object, Object>> scalaExecutorMemoryStatus = sc.getExecutorMemoryStatus();
                Map<String, Tuple2<Object, Object>> executorMemoryStatus = JavaConverters.mapAsJavaMap(scalaExecutorMemoryStatus);
                int totalExecutors = executorMemoryStatus.size() - 1;
                double currentCore = (double) totalExecutors * executorCores;
//                core = (double) totalExecutors * executorCores;
                if (currentCore != core) {
                    Thread.sleep(10000);
                    sparkService.sentAddedSparkPortToInterpreterGroup(System.getenv("INTERPRETER_GROUP_ID"),
                            sparkUrls[sparkUrls.length -1], currentCore, memory, false);
                    if (currentCore > core) {
                        bdeLicenseServices.callValidateLicenseCpu();
                    }
                    core = currentCore;
                }
                currentTime = startJobSendSparkHealthStatus(jobId, currentStartTime, currentTime);
            } catch (IllegalAccessException e) {
                try {
                    LOGGER.error(e.getMessage(),e);
                    sparkInterpreter.close();
                    break;
                } catch (InterpreterException ex) {
                    throw new RuntimeException(ex);
                }
            }
            catch (Throwable e) {
                LOGGER.error(e.getMessage(),e);
            }
        }
    }


    private Date startJobSendSparkHealthStatus(String jobId, Date startTime, Date currentTime) throws Exception {
            //For first time to send health status of time
            LOGGER.debug("Begin to send health status of time : {} jobId : {}",currentTime,jobId);
            Date previousTime = currentTime;
            currentTime = new Date(System.currentTimeMillis());
            bdeLicenseServices.sendSparkJobHealthStatus(jobId,startTime,currentTime,previousTime, false);
            return currentTime;
    }
}
