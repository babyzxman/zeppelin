package org.apache.zeppelin.service.bde.hera.view;

public class ValidateUserNotebookRequest {
    String notebookId;

    String moduleNotebookName;

    String userName;

    Long tenantId;


    public void setNotebookId(String notebookId) {
        this.notebookId = notebookId;
    }

    public String getNotebookId() {
        return notebookId;
    }

    public void setModuleNotebookName(String moduleNotebookName) {
        this.moduleNotebookName = moduleNotebookName;
    }

    public String getModuleNotebookName() {
        return moduleNotebookName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTenantId() {
        return tenantId;
    }
}
