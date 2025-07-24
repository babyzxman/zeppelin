package org.apache.zeppelin.rest.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PullNoteRequest {

    @JsonProperty(value = "isHardPull")
    boolean isHardPull;



    public void setHardPull(boolean hardPull) {
        isHardPull = hardPull;
    }

    public boolean isHardPull() {
        return isHardPull;
    }

}
