package org.apache.zeppelin.rest.bde.view.datasource;

public class Schema {
    private String originName;
    private String originDataType;
    private String customName;
    private String customDataType;
    private String dataFormat;
    private String buddhistEra;

    public String getOriginName() {
        return originName;
    }

    public void setOriginName(String originName) {
        this.originName = originName;
    }

    public String getOriginDataType() {
        return originDataType;
    }

    public void setOriginDataType(String originDataType) {
        this.originDataType = originDataType;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getCustomDataType() {
        return customDataType;
    }

    public void setCustomDataType(String customDataType) {
        this.customDataType = customDataType;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getBuddhistEra() {
        return buddhistEra;
    }

    public void setBuddhistEra(String buddhistEra) {
        this.buddhistEra = buddhistEra;
    }
}
