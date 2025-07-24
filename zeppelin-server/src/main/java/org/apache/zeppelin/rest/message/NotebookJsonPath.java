package org.apache.zeppelin.rest.message;

public class NotebookJsonPath {

    String path;

    String noteJson;


    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setNoteJson(String noteJson) {
        this.noteJson = noteJson;
    }

    public String getNoteJson() {
        return noteJson;
    }
}
