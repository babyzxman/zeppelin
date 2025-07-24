package org.apache.zeppelin.rest.response;

import java.util.List;
import java.util.Map;

public class NotebookMultipleNoteExportResponse {

    List<Map<String, String>> failNoteList;

    List<Map<String, String>>  successNoteList;


    public void setFailNoteList(List<Map<String, String>> failNoteList) {
        this.failNoteList = failNoteList;
    }

    public List<Map<String, String>> getFailNoteList() {
        return failNoteList;
    }

    public void setSuccessNoteList(List<Map<String, String>> successNoteList) {
        this.successNoteList = successNoteList;
    }

    public List<Map<String, String>> getSuccessNoteList() {
        return successNoteList;
    }
}
