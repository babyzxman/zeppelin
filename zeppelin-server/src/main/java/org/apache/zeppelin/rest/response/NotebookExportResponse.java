package org.apache.zeppelin.rest.response;

public class NotebookExportResponse {

    String notebookId;

    String exportJson;

    public void setNotebookId(String notebookId) {
        this.notebookId = notebookId;
    }

    public String getNotebookId() {
        return notebookId;
    }

    public void setExportJson(String exportJson) {
        this.exportJson = exportJson;
    }

    public String getExportJson() {
        return exportJson;
    }
}
