package org.apache.zeppelin.rest.bde.view;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InterpreterNotebookResponse {
    String noteId;

    String noteName;

    String interpreterName;

    String type;

    String startTime;

    String sparkUrl;

    String memory;

    Double core;

    Integer timeOut;

    @JsonProperty("isCron")
    boolean isCron;


    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteName(String noteName) {
        this.noteName = noteName;
    }

    public String getNoteName() {
        return noteName;
    }

    public void setInterpreterName(String interpreterName) {
        this.interpreterName = interpreterName;
    }

    public String getInterpreterName() {
        return interpreterName;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setSparkUrl(String sparkUrl) {
        this.sparkUrl = sparkUrl;
    }

    public String getSparkUrl() {
        return sparkUrl;
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

    public void setTimeOut(Integer timeOut) {
        this.timeOut = timeOut;
    }

    public Integer getTimeOut() {
        return timeOut;
    }

    public void setCron(boolean cron) {
        isCron = cron;
    }

    public boolean isCron() {
        return isCron;
    }
}
