package org.apache.zeppelin.rest.message;

import java.util.List;

public class ExportNotebookListRequest {
    List<String> noteIds;

    String format;

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }

    public List<String> getNoteIds() {
        return noteIds;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
}
