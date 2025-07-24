package org.apache.zeppelin.rest.bde.view.datasource;

import java.util.List;

public class TableWSRequest {
    private String tableName;

    private List<String> partitions;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getPartitions() {
        return partitions;
    }

    public void setPartitions(List<String> partitions) {
        this.partitions = partitions;
    }
}
