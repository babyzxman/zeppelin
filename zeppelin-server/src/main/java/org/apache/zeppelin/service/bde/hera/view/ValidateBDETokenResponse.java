package org.apache.zeppelin.service.bde.hera.view;

public class ValidateBDETokenResponse {

    private String notebookToken;
    private String tokenReference;
    private String notebookSessionRef;
    private Notebook notebook;
    private String sessionOwner;
    private String roleLevel;

    public String getSessionOwner() {
        return sessionOwner;
    }

    public void setSessionOwner(String sessionOwner) {
        this.sessionOwner = sessionOwner;
    }

    public String getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(String roleLevel) {
        this.roleLevel = roleLevel;
    }

    public String getNotebookToken() {
        return notebookToken;
    }

    public void setNotebookToken(String notebookToken) {
        this.notebookToken = notebookToken;
    }

    public String getTokenReference() {
        return tokenReference;
    }

    public void setTokenReference(String tokenReference) {
        this.tokenReference = tokenReference;
    }

    public String getNotebookSessionRef() {
        return notebookSessionRef;
    }

    public void setNotebookSessionRef(String notebookSessionRef) {
        this.notebookSessionRef = notebookSessionRef;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public void setNotebook(Notebook notebook) {
        this.notebook = notebook;
    }
}
