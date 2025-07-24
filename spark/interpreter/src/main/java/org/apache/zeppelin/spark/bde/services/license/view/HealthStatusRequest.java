package org.apache.zeppelin.spark.bde.services.license.view;

import java.util.Date;

public class HealthStatusRequest {
    private Date startTime;
    private Date previousTime;
    private Date currentTime;
    private String jobId;
    private boolean isLastTime;

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

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setLastTime(boolean lastTime) {
        isLastTime = lastTime;
    }

    public boolean isLastTime() {
        return isLastTime;
    }
}
