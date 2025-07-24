package org.apache.zeppelin.rest.message;

import java.util.List;

public class PushNoteRequest {
    List<String> noteIds;

    String message;

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }

    public List<String> getNoteIds() {
        return noteIds;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
