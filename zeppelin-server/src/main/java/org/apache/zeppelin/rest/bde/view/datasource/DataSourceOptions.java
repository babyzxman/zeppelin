package org.apache.zeppelin.rest.bde.view.datasource;

import org.codehaus.jackson.JsonNode;

import java.util.List;

public class DataSourceOptions {
    private SparkOptions sparkOptions;
    private SinkOption sinkOption;
    private Object partitions;
    private boolean isState;


    public boolean getIsState() {
        return isState;
    }

    public void setIsState(boolean state) {
        isState = state;
    }

    public Object getPartitions() {
        return partitions;
    }

    public void setPartitions(Object partitions) {
        this.partitions = partitions;
    }

    public boolean isState() {
        return isState;
    }

    public void setState(boolean state) {
        isState = state;
    }

    public SinkOption getSinkOption() {
        return sinkOption;
    }

    public void setSinkOption(SinkOption sinkOption) {
        this.sinkOption = sinkOption;
    }

    public SparkOptions getSparkOptions() {
        return sparkOptions;
    }

    public void setSparkOptions(SparkOptions sparkOptions) {
        this.sparkOptions = sparkOptions;
    }


}
