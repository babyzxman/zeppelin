package org.apache.zeppelin.rest.bde.view.spark;

import java.util.Date;

public class SparkScopedResponse {

    String interpreterName;

    Date clearResourceTime;

    public void setInterpreterName(String interpreterName) {
        this.interpreterName = interpreterName;
    }

    public String getInterpreterName() {
        return interpreterName;
    }


    public void setClearResourceTime(Date clearResourceTime) {
        this.clearResourceTime = clearResourceTime;
    }

    public Date getClearResourceTime() {
        return clearResourceTime;
    }
}
