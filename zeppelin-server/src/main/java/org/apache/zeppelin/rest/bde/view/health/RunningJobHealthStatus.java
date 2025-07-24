package org.apache.zeppelin.rest.bde.view.health;

import java.util.Date;

public class RunningJobHealthStatus {
    private Date startTime;
    private Date previousTime;
    private Date currentTime;
    private Long moduleId;
    private String moduleName;
    private String moduleReferenceKey;
    private String jobId;
    private int cpuCores;
    private Date timeout;
    private boolean isLastTime;

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getPreviousTime() {
        return previousTime;
    }

    public void setPreviousTime(Date previousTime) {
        this.previousTime = previousTime;
    }

    public Date getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(Date currentTime) {
        this.currentTime = currentTime;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public String getModuleReferenceKey() {
        return moduleReferenceKey;
    }

    public void setModuleReferenceKey(String moduleReferenceKey) {
        this.moduleReferenceKey = moduleReferenceKey;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public void setTimeout(Date timeout) {
        this.timeout = timeout;
    }

    public Date getTimeout() {
        return timeout;
    }

    public void setLastTime(boolean lastTime) {
        isLastTime = lastTime;
    }

    public boolean isLastTime() {
        return isLastTime;
    }
}
