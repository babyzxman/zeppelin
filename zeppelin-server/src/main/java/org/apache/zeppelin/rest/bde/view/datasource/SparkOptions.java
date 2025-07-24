package org.apache.zeppelin.rest.bde.view.datasource;

import java.util.ArrayList;

public class SparkOptions {
    private ArrayList<Schema> schema;

    private String path;

    public ArrayList<Schema> getSchema() {
        return schema;
    }

    public void setSchema(ArrayList<Schema> schema) {
        this.schema = schema;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
