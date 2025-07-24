package org.apache.zeppelin.spark.bde.services.spark;

import co.blendata.view.DataImportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.spark.bde.services.license.view.HealthStatusRequest;
import org.apache.zeppelin.spark.bde.services.spark.view.AddSparkPortRequest;
import org.apache.zeppelin.spark.bde.services.spark.view.ApplicationInfoRequest;
import org.apache.zeppelin.spark.bde.services.spark.view.StartUpResolvedDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

public class SparkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SparkService.class);
    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();

    public void sentAddedSparkPortToInterpreterGroup(String interpreterGroupId, String port, Double core, String memory, boolean isCron) throws Exception {
        AddSparkPortRequest request = new AddSparkPortRequest();
        request.setInterpreterGroupId(interpreterGroupId);
        request.setPort(port);
        request.setCore(core);
        request.setMemory(memory);
        request.setCron(isCron);

        String addr = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ADDR) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR);
        String addPort = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_PORT) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT);

        String url = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL) ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL_PORT) : addPort)
                +zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_ADD_SPARK_PORT);

        RestTemplateFactoryUtil.getRestTemplar().put(url,request);

        LOGGER.debug("Send add spark port to interpreter group id : {}",interpreterGroupId);
    }

    public StartUpResolvedDependency getListDataCalling() throws Exception {
        String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
                + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_SERVICE_IMPORT_CALLING);
        String moduleRefKey = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_MODULE_REF_KEY);

        ApplicationInfoRequest request = new ApplicationInfoRequest();
        ResponseEntity<StartUpResolvedDependency> response = RestTemplateFactoryUtil.getRestTemplar(moduleRefKey,true)
                .postForEntity(url,request, StartUpResolvedDependency.class);
        LOGGER.info("BDE return code : {}",response.getStatusCodeValue());
        return response.getBody();
    }

    public String sendSparkMemOverLimit(String name) throws Exception {
        String addr = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ADDR) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR);
        String addPort = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_PORT) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT);

        String tmpUri = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL) ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL_PORT) : addPort)
                +zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_SPARK_MEM_OVER_LIMIT_URL);
        String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(name).toUriString();
        LOGGER.info(restUri);
        ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(null,true)
                .postForEntity(restUri,null, String.class);
        if (response.getStatusCodeValue() != 200) {
            throw new IllegalAccessException("Validate user error");
        }
        return response.getBody();
    }
}
