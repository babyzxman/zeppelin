package org.apache.zeppelin.service.bde.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.rest.bde.view.spark.SparkUsedResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BDESparkServices {
    private static final Logger LOG = LoggerFactory.getLogger(BDESparkServices.class);
    private SparkSession spark = null;
    ZeppelinConfiguration zeppelinConf = ZeppelinConfiguration.create();

    private SparkConf getSparkConfig() throws Exception{
        SparkConf conf = new SparkConf();
        String configPath = zeppelinConf.getString(ConfVars.SPARK_LOCAL_CONFIG_FILE);
        File file = new File(configPath);
        if (!file.exists()) {
            conf.set("spark.submit.deployMode","client");
            conf.set("spark.driver.cores","1");
            conf.set("spark.driver.memory","1g");
            conf.set("spark.executor.cores","1");
            conf.set("spark.executor.memory","1g");
            conf.set("spark.sql.extensions","io.delta.sql.DeltaSparkSessionExtension");
            conf.set("spark.sql.catalog.spark_catalog","org.apache.spark.sql.delta.catalog.DeltaCatalog");
            conf.set("spark.cores.max","1");
        }
        else {
            LOG.info("Using spark conf file path: " + configPath);
            try (BufferedReader br = new BufferedReader(new FileReader(configPath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && line.contains(" = ")) {
                        String[] parts = line.split(" = ", 2);
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        LOG.info("Added spark config key = " + key + " value = " + value);
                        conf.set(key, value);
                    }
                }
            } catch (IOException e) {
                LOG.error("Error when try to read file path: " + configPath);
                throw new IOException(e.getMessage(), e);
            }
        }
        return conf;
    }

    private SparkSession getSparkSession() throws Exception {
        spark = SparkSession
                .builder()
                .master("local[*]")
                .appName("Zeppelin-Backend")
                .config(getSparkConfig())
                .enableHiveSupport()
                .getOrCreate();
        return spark;
    }

    public void runSparkSqlJob(String sql) throws Exception{
        LOG.info("Run spark sql job : {}",sql);
        SparkSession spark = getSparkSession();
        spark.sql(sql);
    }

    public void runSparkCreateTable(String sql) throws Exception{
        LOG.info("Run spark create table with sql : {}",sql);
        SparkSession spark = getSparkSession();
        spark.sql(sql);
    }

    public void runRecoveryPartition(String tableName)  throws Exception{
        SparkSession spark = getSparkSession();
        spark.catalog().recoverPartitions(tableName);
    }

    public void refreshTable(String tableName) throws Exception{
        LOG.info("Refresh table : {}",tableName);
        SparkSession spark = getSparkSession();
        spark.catalog().refreshTable(tableName);
    }

    public void addPartition(String tableName, List<String> partitions) throws Exception{
        String sql = "ALTER TABLE " + tableName + " ADD IF NOT EXISTS " + this.convertPartitionsToPartitionSqlString(partitions);
        SparkSession spark = getSparkSession();
        spark.sql(sql);
    }

    public String convertPartitionsToPartitionSqlString(List<String> partitions) {
        String response = "";
        for (String partition : partitions) {
            response += "PARTITION";
            response += " (";
            response += partition;
            response += ") ";
        }
        return response;
    }

    public String getSparkConfigValue(String configName) throws Exception{

        JSONParser jsonParser = new JSONParser();
        String sparkFile = zeppelinConf.getString(ConfVars.SPARK_INTERPRETER_FILE);

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
