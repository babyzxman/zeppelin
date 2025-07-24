package org.apache.zeppelin.rest.message;

public class CreateBranchRequest {

    String name;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
