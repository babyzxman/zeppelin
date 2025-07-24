package org.apache.zeppelin.service.bde.hera.view;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;

public class TokenServiceResponse {
    private int status;
    private String message;
    private String permission;
    private String username;
    private String redirectURL;
    private String nbToken;
    private String moduleNotebookName;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRedirectURL() {
        return redirectURL;
    }

    public void setRedirectURL(String redirectURL) {
        this.redirectURL = redirectURL;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getNbToken() {
        return nbToken;
    }

    public void setNbToken(String nbToken) {
        this.nbToken = nbToken;
    }

    public void setModuleNotebookName(String moduleNotebookName) {
        this.moduleNotebookName = moduleNotebookName;
    }

    public String getModuleNotebookName() {
        return moduleNotebookName;
    }
}
