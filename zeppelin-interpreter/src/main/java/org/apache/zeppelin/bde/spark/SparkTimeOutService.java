package org.apache.zeppelin.bde.spark;



import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class SparkTimeOutService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SparkTimeOutService.class);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();

    public void sendSparkTimeOut(String interpreterGroupId, long lastBusyTimeInMillis) {
        try {
            if (interpreterGroupId.contains("spark")) {
                long timeoutThreshold = zConf.getTime(
                        ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD);
                //update time out in spark interpreter
                String addr = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ADDR) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR);
                String port = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_PORT) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT);
                String url = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                        +":"+(zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL) ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL_PORT) : port)
                        +zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_UPDATE_SPARK_TIMEOUT);
//                RestTemplateFactoryUtil.getRestTemplar().put(url, request);
                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                    HttpPut httpPut = new HttpPut(url);
                    httpPut.setHeader("Content-Type", "application/json");
//                    ObjectMapper objectMapper = new ObjectMapper();
//                    String jsonInputString = objectMapper.writeValueAsString(request);
                    String jsonInputString = String.format("{\"interpreterGroupId\": \"%s\", \"newTimeOut\": \"%s\"}", interpreterGroupId, lastBusyTimeInMillis + timeoutThreshold);
                    LOGGER.info(jsonInputString);
                    StringEntity entity = new StringEntity(jsonInputString);
                    httpPut.setEntity(entity);
                    httpClient.execute(httpPut);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Update spark timeout fail case: " + e);
        }
    }
}
