package org.apache.zeppelin.rest.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CheckoutNotebookRequest {

    String name;

    @JsonProperty(value = "isHardCheckout")
    boolean isHardCheckout;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setHardCheckout(boolean hardCheckout) {
        isHardCheckout = hardCheckout;
    }

    public boolean isHardCheckout() {
        return isHardCheckout;
    }
}
