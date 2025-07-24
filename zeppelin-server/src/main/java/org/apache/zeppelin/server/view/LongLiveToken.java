package org.apache.zeppelin.server.view;

public class LongLiveToken {

    private String tenantId;
    private String userName;
    private String validationJwtToken;

    public LongLiveToken() {
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getValidationJwtToken() {
        return this.validationJwtToken;
    }

    public void setValidationJwtToken(String validationJwtToken) {
        this.validationJwtToken = validationJwtToken;
    }
}
