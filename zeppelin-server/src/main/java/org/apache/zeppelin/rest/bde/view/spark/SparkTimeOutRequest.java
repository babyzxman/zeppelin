package org.apache.zeppelin.rest.bde.view.spark;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

import java.util.Date;

public class SparkTimeOutRequest implements JsonSerializable {
    private static final Gson GSON = new Gson();
    String interpreterGroupId;

    long newTimeOut;

    public void setInterpreterGroupId(String interpreterGroupId) {
        this.interpreterGroupId = interpreterGroupId;
    }

    public String getInterpreterGroupId() {
        return interpreterGroupId;
    }

    public void setNewTimeOut(long newTimeOut) {
        this.newTimeOut = newTimeOut;
    }

    public long getNewTimeOut() {
        return newTimeOut;
    }


    @Override
    public String toJson() { return GSON.toJson(this);}

    public static SparkTimeOutRequest fromJson(String json) {
        return  GSON.fromJson(json, SparkTimeOutRequest.class);
    }
}
