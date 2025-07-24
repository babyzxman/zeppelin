package org.apache.zeppelin.rest.message;

import java.util.List;

public class ClearCacheNotebookRequest {

    String noteId;
    List<String> noteIds;

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }

    public List<String> getNoteIds() {
        return noteIds;
    }
}
