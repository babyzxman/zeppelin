package org.apache.zeppelin.spark.bde.services.spark.view;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class AddSparkPortRequest {
    private String interpreterGroupId;

    private String port;

    private Double core;

    private String memory;

    @JsonProperty("isCron")
    private boolean isCron;

    private Date timeout;

    public void setInterpreterGroupId(String interpreterGroupId) {
        this.interpreterGroupId = interpreterGroupId;
    }

    public String getInterpreterGroupId() {
        return interpreterGroupId;
    }

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

    public void setTimeout(Date timeout) {
        this.timeout = timeout;
    }

    public Date getTimeout() {
        return timeout;
    }
}
