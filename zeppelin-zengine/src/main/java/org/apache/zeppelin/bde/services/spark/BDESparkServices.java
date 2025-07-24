package org.apache.zeppelin.bde.services.spark;

import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;

public class BDESparkServices {

    private static final Logger LOG = LoggerFactory.getLogger(BDESparkServices.class);
    ZeppelinConfiguration zeppelinConf = ZeppelinConfiguration.create();

    public String getSparkConfigValue(String configName) throws Exception{

        JSONParser jsonParser = new JSONParser();
        String sparkFile = zeppelinConf.getString(ZeppelinConfiguration.ConfVars.SPARK_INTERPRETER_FILE);

        FileReader reader = new FileReader(sparkFile);
        JSONObject settings = (JSONObject) jsonParser.parse(reader);

        JSONObject intpSettting = (JSONObject) settings.get("interpreterSettings");
        JSONObject sparkSettings = (JSONObject) intpSettting.get("spark");
        JSONObject sparkConfObject = (JSONObject) sparkSettings.get("properties");
        JSONObject confName = (JSONObject) sparkConfObject.get(configName);
        String confValue = (String) confName.get("value");

        return confValue;

    }
}
