package org.apache.zeppelin.rest.bde.view.datasource;

public class DatasourceProfile {
    String name;
    String dataSourceOptions;
    String dataDict;
    String sourceType;
    String defaultPath;
    boolean alreadyExitSchema = false;

    public String getDataSourceOptions() {
        return dataSourceOptions;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public void setDataSourceOptions(String dataSourceOptions) {
        this.dataSourceOptions = dataSourceOptions;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataDict() {
        return dataDict;
    }

    public void setDataDict(String dataDict) {
        this.dataDict = dataDict;
    }

    public void setAlreadyExitSchema(boolean alreadyExitSchema) {
        this.alreadyExitSchema = alreadyExitSchema;
    }

    public boolean isAlreadyExitSchema() {
        return alreadyExitSchema;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }

    public String getDefaultPath() {
        return defaultPath;
    }
}
