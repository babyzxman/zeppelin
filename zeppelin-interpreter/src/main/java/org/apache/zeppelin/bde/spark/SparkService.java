package org.apache.zeppelin.bde.spark;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

public class SparkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SparkService.class);

    ZeppelinConfiguration zConf = ZeppelinConfiguration.create();

    public void sendSparkMemOverLimit(String name) throws Exception {
        String addr = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_ADDR) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_ADDR);
        String addPort = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).isEmpty() || zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT).equals("var") ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_PORT) : zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_LOCAL_PORT);

        String tmpUri = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_PREFIX_URL)+"://"+ addr
                +":"+(zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL) ? zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SSL_PORT) : addPort)
                +zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_SERVER_SPARK_MEM_OVER_LIMIT_URL);
        String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(name).toUriString();
        LOGGER.info(restUri);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(restUri);
            httpPost.setHeader("Content-Type", "application/json");
//                    ObjectMapper objectMapper = new ObjectMapper();
//                    String jsonInputString = objectMapper.writeValueAsString(request);

            httpClient.execute(httpPost);
        }
    }
}
