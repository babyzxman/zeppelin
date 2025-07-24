package org.apache.zeppelin.rest.bde.view.spark;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

public class AddSparkPortRequest implements JsonSerializable {

    private static final Gson GSON = new Gson();
    private String interpreterGroupId;

    private String port;

    private Double core;

    private String memory;

    @JsonProperty("isCron")
    private boolean isCron;

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

    @Override
    public String toJson() {
        return GSON.toJson(this);
    }


    public static AddSparkPortRequest fromJson(String json) {
        return  GSON.fromJson(json, AddSparkPortRequest.class);
    }
}
