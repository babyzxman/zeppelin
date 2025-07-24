package org.apache.zeppelin.service.bde.error;

import java.sql.Timestamp;

public class HeraErrorResponse {
    private Timestamp timestamp;
    private long status;
    private String error;
    private String message;
    private String path;
    private String errorId;

    private String moduleCode;

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public long getStatus() {
        return status;
    }

    public void setStatus(long status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setErrorId(String errorId) {
        this.errorId = errorId;
    }

    public String getErrorId() {
        return errorId;
    }

    public void setModuleCode(String moduleCode) {
        this.moduleCode = moduleCode;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
