package org.apache.zeppelin.rest.message;

import java.util.List;

public class UploadNotebookRequest {

    boolean skipExitId;

    List<NotebookJsonPath> notebookJsonPathList;

    public void setSkipExitId(boolean skipExitId) {
        this.skipExitId = skipExitId;
    }

    public boolean isSkipExitId() {
        return skipExitId;
    }

    public void setNotebookJsonPathList(List<NotebookJsonPath> notebookJsonPathList) {
        this.notebookJsonPathList = notebookJsonPathList;
    }

    public List<NotebookJsonPath> getNotebookJsonPathList() {
        return notebookJsonPathList;
    }
}
