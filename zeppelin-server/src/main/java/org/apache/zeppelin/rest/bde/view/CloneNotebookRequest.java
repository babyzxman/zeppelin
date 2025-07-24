package org.apache.zeppelin.rest.bde.view;

import java.util.Map;

public class CloneNotebookRequest {

    private String noteId;

    private Map<String, Map<String, String>> addParameterMap;

    private Map<String, String> specificInterpreterGroupName;

    private String name;

    private String revisionId;

    private String language;


    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setAddParameterMap(Map<String, Map<String, String>> addParameterMap) {
        this.addParameterMap = addParameterMap;
    }

    public Map<String, Map<String, String>> getAddParameterMap() {
        return addParameterMap;
    }

    public void setRevisionId(String revisionId) {
        this.revisionId = revisionId;
    }

    public String getRevisionId() {
        return revisionId;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    public void setSpecificInterpreterGroupName(Map<String, String> specificInterpreterGroupName) {
        this.specificInterpreterGroupName = specificInterpreterGroupName;
    }

    public Map<String, String> getSpecificInterpreterGroupName() {
        return specificInterpreterGroupName;
    }
}
