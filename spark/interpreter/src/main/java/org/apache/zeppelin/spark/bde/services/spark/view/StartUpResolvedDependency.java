package org.apache.zeppelin.spark.bde.services.spark.view;

import co.blendata.view.DataImportRequest;

import java.util.List;

public class StartUpResolvedDependency {

    private List<DataImportRequest> importDataCallingList;

    public List<DataImportRequest> getImportDataCallingList() {
        return importDataCallingList;
    }

    public void setImportDataCallingList(List<DataImportRequest> importDataCallingList) {
        this.importDataCallingList = importDataCallingList;
    }
}