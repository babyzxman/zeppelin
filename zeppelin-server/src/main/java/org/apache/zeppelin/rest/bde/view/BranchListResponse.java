package org.apache.zeppelin.rest.bde.view;

public class BranchListResponse {

    String name;

    boolean local;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isLocal() {
        return local;
    }
}
