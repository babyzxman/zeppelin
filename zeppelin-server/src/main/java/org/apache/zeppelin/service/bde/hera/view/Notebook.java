package org.apache.zeppelin.service.bde.hera.view;

import java.sql.Timestamp;

public class Notebook {
    private String id;
    private String status;
    private String createdBy;
    private Timestamp createdDate;
    private String modifiedBy;
    private int optLock;
    private String name;
    private String description;
    private ModuleNotebook moduleNotebook;
    private int realmId;
    private String notebookIdRef;
    private boolean active;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public int getOptLock() {
        return optLock;
    }

    public void setOptLock(int optLock) {
        this.optLock = optLock;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ModuleNotebook getModuleNotebook() {
        return moduleNotebook;
    }

    public void setModuleNotebook(ModuleNotebook moduleNotebook) {
        this.moduleNotebook = moduleNotebook;
    }

    public int getRealmId() {
        return realmId;
    }

    public void setRealmId(int realmId) {
        this.realmId = realmId;
    }

    public String getNotebookIdRef() {
        return notebookIdRef;
    }

    public void setNotebookIdRef(String notebookIdRef) {
        this.notebookIdRef = notebookIdRef;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
