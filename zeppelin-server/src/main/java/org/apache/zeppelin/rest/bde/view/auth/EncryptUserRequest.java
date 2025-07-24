package org.apache.zeppelin.rest.bde.view.auth;

import com.google.gson.Gson;
import org.apache.zeppelin.common.JsonSerializable;

public class EncryptUserRequest implements JsonSerializable {

    private static final Gson GSON = new Gson();
    String username;

    String password;


    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toJson() {
        return GSON.toJson(this);
    }

    public static EncryptUserRequest fromJson(String json) {
        return GSON.fromJson(json, EncryptUserRequest.class);
    }
}
