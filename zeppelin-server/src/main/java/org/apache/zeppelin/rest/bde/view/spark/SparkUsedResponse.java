package org.apache.zeppelin.rest.bde.view.spark;

import java.util.Date;

public class SparkUsedResponse {

    private String port;

    private Double core;

    private String memory;

    private boolean isCron;

    private Date timeOut;

    public void setPort(String port) {
        this.port = port;
    }

    public String getPort() {
        return port;
    }

    public void setCore(Double core) {
        this.core = core;
    }

    public Double getCore() {
        return core;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getMemory() {
        return memory;
    }

    public void setCron(boolean cron) {
        isCron = cron;
    }

    public boolean isCron() {
        return isCron;
    }

    public void setTimeOut(Date timeOut) {
        this.timeOut = timeOut;
    }

    public Date getTimeOut() {
        return timeOut;
    }
}
