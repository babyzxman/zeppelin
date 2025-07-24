package org.apache.zeppelin.service.bde.hera.view;

public class UpdateNoteRenameRequest {
    private String noteId;
    private String newNoteName;
    private String token;
    private String sessionId;

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNewNoteName() {
        return newNoteName;
    }

    public void setNewNoteName(String newNoteName) {
        this.newNoteName = newNoteName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
