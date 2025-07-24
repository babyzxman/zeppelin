package org.apache.zeppelin.rest.bde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gable.templar.heaven.util.ObjectUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.google.gson.internal.LinkedTreeMap;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterSettingManager;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.rest.bde.view.datasource.*;
import org.apache.zeppelin.rest.bde.view.health.RunningJobHealthStatus;
import org.apache.zeppelin.rest.bde.view.spark.SparkUsedResponse;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.bde.git.NotebookGitService;
import org.apache.zeppelin.service.bde.hera.BDEHeraServices;
import org.apache.zeppelin.service.bde.spark.BDESparkServices;
import org.apache.zeppelin.service.bde.spark.SparkObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.*;

/**
 * Rest api endpoint blendata enterprise operation.
 */
@Path("/bde")
@Produces("application/json")
@Singleton
public class BDERestApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(BDERestApi.class);
    private static final Gson GSON = new Gson();

    private String SUCCESS_LIST_NAME = "successes";
    private String FAIL_LIST_NAME = "fails";
    private BDESparkServices bdeSparkService = new BDESparkServices();
    private BDEHeraServices bdeHeraServices = new BDEHeraServices();
    private NotebookGitService notebookGitService = new NotebookGitService();
    private ZeppelinConfiguration zeppelinConfig = ZeppelinConfiguration.create();
    private List<String> runningSparkJob = new ArrayList<>();
    private ObjectMapper mapper = new ObjectMapper();
    private final SparkObject sparkObject;
    private final InterpreterSettingManager interpreterSettingManager;
    private Long lastSentHealthStatus;
    private Date startTime;
    private Date previousTime;

    @Inject
    protected BDERestApi(SparkObject sparkObject, InterpreterSettingManager interpreterSettingManager) {
        this.sparkObject = sparkObject;
        this.interpreterSettingManager = interpreterSettingManager;
    }


    /**
     * Create table in metastore to use with spark interpreter
     *
     * @return Json with result of creating table
     * @throws Exception
     */
    @POST
    @Path("table/createTable")
    @ZeppelinApi
    public Response createTable(String message) throws Exception {
        LOGGER.info("Create Table in spark metastore by JSON {}", message);
        ArrayList<DatasourceProfile> datasourceProfilesList = GSON.fromJson(message,new TypeToken<ArrayList<DatasourceProfile>>(){}.getType());

        List<String> exitSchemaNames = new ArrayList<>();
        if (datasourceProfilesList == null || datasourceProfilesList.isEmpty()) {
            LOGGER.error("Table schema can not be empty");
            throw new BadRequestException("Table schema can not be empty");
        }

        Map<String, List<String>> result = new HashMap<>();
        List<String> successes = new ArrayList<>();
        List<String> fails = new ArrayList<>();
//        String dataStorePath = zeppelinConfig.getString(ConfVars.ZEPPELIN_APP_DATA_STORE_DIR);

        for(DatasourceProfile dataProfile : datasourceProfilesList){
            String tableName = dataProfile.getName();
            String schemaName = null;
            try{
                if (tableName.contains(".")) {
                    String[] tableNames  = tableName.split("\\.");
                    schemaName = tableNames[0];
                    if (!exitSchemaNames.contains(schemaName) && !dataProfile.isAlreadyExitSchema()) {
                        String sql = "CREATE SCHEMA IF NOT EXISTS " + schemaName ;
                        bdeSparkService.runSparkSqlJob(sql);
                        exitSchemaNames.add(schemaName);
                    }
                }
                DataDict dataDict = GSON.fromJson(dataProfile.getDataDict(),DataDict.class);
                DataSourceOptions dataSourceOptions = GSON.fromJson(dataProfile.getDataSourceOptions(), DataSourceOptions.class);
                String dataStorePath = dataProfile.getDefaultPath();
                if (dataSourceOptions.getSparkOptions() != null && !ObjectUtil.isNullOrEmpty(dataSourceOptions.getSparkOptions().getPath())) {
                    dataStorePath = dataSourceOptions.getSparkOptions().getPath();
                }
                LOGGER.info("data store path is: " + dataStorePath);
                ArrayList<Row> rows = dataDict.getRows();

                //Check if delta table
                String format = "";
                if(dataProfile.getSourceType()!=null){
                    format = dataProfile.getSourceType();
                }

                if(dataSourceOptions.getPartitions() instanceof ArrayList && !((ArrayList<?>) dataSourceOptions.getPartitions()).isEmpty()){
                    //partitioned table

                    //create partitions list
                    ArrayList<Partition> partitions = new ArrayList<>();
                    ArrayList<Object> partitionsObjList = (ArrayList<Object>) dataSourceOptions.getPartitions();
                    for(Object partitionObj : partitionsObjList){
                        LinkedTreeMap<?,?> treeMapper = (LinkedTreeMap<?, ?>) partitionObj;
                        partitions.add(GSON.fromJson((GSON.toJsonTree(treeMapper).getAsJsonObject()).toString(),Partition.class));
                    }

                    if(format.equalsIgnoreCase("delta")) {
                        //delta table
                        createDeltaTable(tableName, dataStorePath, schemaName);
                    }else if(dataSourceOptions.getIsState()){
                        //state table
                        createStateTable(rows, tableName, dataStorePath, partitions, schemaName);
                    }else{
                        //partitioned table
                        createPartitionTable(rows, tableName, dataStorePath, partitions, schemaName);
                    }

                }else if(format.equalsIgnoreCase("delta")) {
                    //delta table
                    createDeltaTable(tableName, dataStorePath, schemaName);
                }else{
                    createExternalTable(rows, tableName, dataStorePath, schemaName);
                }

                successes.add(tableName);
            }catch (Exception e){
                e.printStackTrace();
                LOGGER.error(e.getMessage(), e);
                fails.add(tableName+"(error message: " + e.getMessage() + ")");
            }

        }

        result.put(SUCCESS_LIST_NAME, successes);
        result.put(FAIL_LIST_NAME, fails);

        LOGGER.info("Response status : {} , result : {}",Response.Status.OK,result);

        return new JsonResponse<>(Response.Status.OK, result).build();
    }

    private void createStateTable(ArrayList<Row> rows, String tableName,String dataStorePath, List<Partition> partitionByList, String schemaName) throws Exception {

        List<String> tableNames = new ArrayList<>();
        tableNames.add(tableName+"_hot");
        tableNames.add(tableName+"_warm");
        tableNames.add(tableName+"_cold");

        //create hot, warm, cold table
        for(String name : tableNames){
            String sql = "CREATE EXTERNAL TABLE " + name + " (";
            for (Row row : rows){
                if(row.getCol_name().startsWith("#"))
                    break;
                String columnValue = " " + row.getCol_name() + " " + row.getData_type() + ",";
                sql += columnValue;
            }
            sql = sql.substring(0,sql.length()-1);
            sql += ") PARTITIONED BY (";
            for(Partition partitionBy : partitionByList){
                sql += partitionBy.getName() + ",";
            }

            sql = sql.substring(0,sql.length()-1);
            if (!ObjectUtil.isNullOrEmpty(schemaName)) {
                sql += ") STORED AS PARQUET LOCATION '" + dataStorePath + File.separator + "_scm_" + schemaName + File.separator + tableName.split("\\.")[1]  + File.separator + name.split("\\.")[1]+"'";
            }
            else {
                sql +=") STORED AS PARQUET LOCATION '" + dataStorePath+ File.separator + tableName + File.separator + name+"'";
            }
//            sql += ") STORED AS PARQUET LOCATION '"+dataStorePath+File.separator+tableName+File.separator+name+"'";

            bdeSparkService.runSparkCreateTable(sql);
            bdeSparkService.runRecoveryPartition(name);
        }

        String sqlCreateViewTable = "CREATE VIEW " + tableName + " AS " +
                "SELECT * FROM " +tableName+"_hot" + " UNION ALL " +
                "SELECT * FROM " +tableName+"_warm" + " UNION ALL " +
                "SELECT * FROM " +tableName+"_cold";
        bdeSparkService.runSparkSqlJob(sqlCreateViewTable);
    }

    private void createDeltaTable(String tableName,String dataStorePath, String schemaName) throws Exception{
        String sql = "CREATE EXTERNAL TABLE default." + tableName + " USING DELTA LOCATION '" + dataStorePath + File.separator + tableName + "'";
        if (!ObjectUtil.isNullOrEmpty(schemaName)) {
            tableName = tableName.split("\\.")[1];
            sql = "CREATE EXTERNAL TABLE " + schemaName + "." + tableName + " USING DELTA LOCATION '" + dataStorePath + File.separator + "_scm_" + schemaName + File.separator + tableName + "'";
        }
        bdeSparkService.runSparkCreateTable(sql);
    }

    private void createPartitionTable(ArrayList<Row> rows, String tableName,String dataStorePath,List<Partition> partitionByList, String schemaName) throws Exception{
        String sql = "CREATE EXTERNAL TABLE " + tableName + " (";
        for (Row row : rows){
            if(row.getCol_name().startsWith("#"))
                break;
            String columnValue = " " + row.getCol_name() + " " + row.getData_type() + ",";
            sql += columnValue;
        }
        sql = sql.substring(0,sql.length()-1);
        sql += ") PARTITIONED BY (";

        for(Partition partitionBy : partitionByList){
            sql += partitionBy.getName() + ",";
        }
        sql = sql.substring(0,sql.length()-1);
        if (!ObjectUtil.isNullOrEmpty(schemaName)) {
            sql += ") STORED AS PARQUET LOCATION '" + dataStorePath + File.separator + "_scm_" + schemaName + File.separator + tableName.split("\\.")[1] + "'";
        }
        else {
            sql +=") STORED AS PARQUET LOCATION '" + dataStorePath+ File.separator + tableName + "'";
        }

        bdeSparkService.runSparkCreateTable(sql);
        bdeSparkService.runRecoveryPartition(tableName);
    }

    private void createExternalTable(ArrayList<Row> rows, String tableName,String dataStorePath, String schemaName) throws Exception {

        String sql = "CREATE EXTERNAL TABLE " + tableName + " (";

        for (Row row : rows){
            if(row.getCol_name().startsWith("#"))
                break;
            String columnValue = " " + row.getCol_name() + " " + row.getData_type() + ",";
            sql += columnValue;
        }
        sql = sql.substring(0,sql.length()-1);
        if (!ObjectUtil.isNullOrEmpty(schemaName)) {
            sql += ") STORED AS PARQUET LOCATION '" + dataStorePath + File.separator + "_scm_" + schemaName + File.separator + tableName.split("\\.")[1] + "'";
        }
        else {
            sql +=") STORED AS PARQUET LOCATION '" + dataStorePath+ File.separator + tableName + "'";
        }
//        sql += ") STORED AS PARQUET LOCATION '"+dataStorePath+File.separator+tableName+"'";

        bdeSparkService.runSparkCreateTable(sql);
    }

    /**
     * Drop table in metastore
     *
     * @return Json with result of dropping table
     * @throws Exception
     */
    @POST
    @Path("table/dropTable")
    @ZeppelinApi
    public Response dropTable(String message) throws Exception {

        LOGGER.info("Drop Table in spark metastore by JSON {}", message);
        ArrayList<DatasourceProfile> datasourceProfilesList = GSON.fromJson(message,new TypeToken<ArrayList<DatasourceProfile>>(){}.getType());
        if (datasourceProfilesList == null || datasourceProfilesList.isEmpty()) {
            LOGGER.error("Table schema can not be empty");
            throw new BadRequestException("Table schema can not be empty");
        }

        Map<String, List<String>> result = new HashMap<>();
        List<String> successes = new ArrayList<>();
        List<String> fails = new ArrayList<>();

        for(DatasourceProfile dataProfile : datasourceProfilesList){
            String tableName = dataProfile.getName();
            try{
                DataSourceOptions dataSourceOptions = GSON.fromJson(dataProfile.getDataSourceOptions(),DataSourceOptions.class);
                if(dataSourceOptions.getIsState()){
                    bdeSparkService.runSparkSqlJob("DROP TABLE "+ tableName+"_hot");
                    bdeSparkService.runSparkSqlJob("DROP TABLE "+ tableName+"_warm");
                    bdeSparkService.runSparkSqlJob("DROP TABLE "+ tableName+"_cold");
                    bdeSparkService.runSparkSqlJob("DROP VIEW "+ tableName);
                }else{
                    bdeSparkService.runSparkSqlJob("DROP TABLE "+ tableName);
                }
                successes.add(tableName);
            }catch (Exception e){
                LOGGER.error(e.getMessage());
                e.printStackTrace();
                fails.add(tableName+"(error message: " + e.getMessage() + ")");
            }

        }

//        LOGGER.info("Drop Table in spark metastore by JSON {}", message);
//        ArrayList<String> tableNameList = GSON.fromJson(message,new TypeToken<ArrayList<String>>(){}.getType());
//        if (tableNameList == null || tableNameList.isEmpty()) {
//            LOGGER.error("Table name can not be empty");
//            throw new BadRequestException("Table name can not be empty");
//        }
//
//        Map<String, List<String>> result = new HashMap<String, List<String>>();
//        List<String> successes = new ArrayList<String>();
//        List<String> fails = new ArrayList<String>();
//
//        for(String tableName : tableNameList){
//            try{
//                String sql = "DROP TABLE "+ tableName;
//                bdeSparkService.runSparkSqlJob(sql);
//                successes.add(tableName);
//            }catch (Exception e){
//                fails.add(tableName+"(error message: " + e.getMessage() + ")");
//            }
//        }

        result.put(SUCCESS_LIST_NAME, successes);
        result.put(FAIL_LIST_NAME, fails);

        LOGGER.info("Response status : {} , result : {}",Response.Status.OK,result);

        return new JsonResponse<>(Response.Status.OK,result).build();
    }

    /**
     * List all running spark job
     *
     * @return Json with list of running spark job
     * @throws Exception
     */
    @GET
    @Path("sparkJob")
    @ZeppelinApi
    public Response getAllRunningSparkJob() throws Exception {
        LOGGER.info("Get all running spark job");
        return new JsonResponse<>(Response.Status.OK,this.runningSparkJob).build();
    }

    /**
     * Get spark session health status by jobId
     *
     * @return Json with status of spark session
     * @throws Exception
     */
    @GET
    @Path("sparkJob/healthStatus/{jobId}")
    @ZeppelinApi
    public Response getRunningSparkJobByJobId(@PathParam("jobId") String jobId) throws Exception {
        LOGGER.info("Get spark job health status by job id : {}",jobId);
        Map<String,String> result = new HashMap<>();
        result.put("status",runningSparkJob.contains(jobId) ? "running" : "stopped");
        return new JsonResponse<>(Response.Status.OK,result).build();
    }

    /**
     * Set spark job health status by jobId
     *
     * @return Json with status of spark job
     * @throws Exception
     */
    @PUT
    @Path("sparkJob/healthStatus")
    @ZeppelinApi
    public Response setRunningSparkJob(RunningJobHealthStatus runningJob) throws Exception {
        //add jobId to runningSparkJobList
        if(runningJob.getJobId() != null) {
            LOGGER.debug("Received request to send health status : {}",mapper.writerWithDefaultPrettyPrinter().writeValueAsString(runningJob));
            if (!runningSparkJob.contains(runningJob.getJobId())) {
                LOGGER.info("Add spark job : {} to running list.",runningJob.getJobId());
                this.runningSparkJob.add(runningJob.getJobId());
            }
            //forward old job health status to hera
            bdeHeraServices.forwardCronHealthStatusToBDE(runningJob);
        }
        else {
            if (lastSentHealthStatus != null && (System.currentTimeMillis() - lastSentHealthStatus) < 10000L && !runningJob.isLastTime()) {
                return new JsonResponse<>(Response.Status.OK).build();
            }
            double totalCore = 0.0;
            Date timeout = null;
            List<ManagedInterpreterGroup> managedInterpreterGroup = interpreterSettingManager.getAllInterpreterGroup();
            for (ManagedInterpreterGroup managedInterpreter : managedInterpreterGroup) {
                if (managedInterpreter.getInterpreterProcess() != null
                        && sparkObject.sparkInterpreterGroupNamePort.containsKey(managedInterpreter.getId()))  {
                    SparkUsedResponse spark = sparkObject.sparkInterpreterGroupNamePort.get(managedInterpreter.getId());
                    if (!spark.isCron()) {
                        if (timeout == null || spark.getTimeOut().after(timeout)) {
                            timeout = spark.getTimeOut();
                        }
                        totalCore += spark.getCore();
                    }
                }
            }
            if (startTime == null && runningJob.isLastTime() && totalCore == 0) {
                return new JsonResponse<>(Response.Status.OK).build();
            }
            if (startTime != null) {
                runningJob.setStartTime(startTime);
            }
            if (totalCore == 0) {
                startTime = null;
            }
            if (startTime == null) {
                startTime = runningJob.getStartTime();
            }
            if (runningJob.getPreviousTime() == null) {
                runningJob.setPreviousTime(previousTime);
            }
            else {
                previousTime = runningJob.getPreviousTime();
            }
            lastSentHealthStatus = System.currentTimeMillis();
            //forward job health status to hera
            runningJob.setTimeout(timeout == null ? null : new Date(timeout.getTime() + 5 * 60 * 1000));
            runningJob.setCpuCores((int)totalCore);
            bdeHeraServices.forwardHealthStatusToBDE(runningJob);
        }

        return new JsonResponse<>(Response.Status.OK).build();
    }

    @DELETE
    @Path("sparkJob/healthStatus/{jobId}")
    @ZeppelinApi
    public Response deleteRunningSparkJob(@PathParam("jobId") String jobId) throws Exception {

        if(!runningSparkJob.contains(jobId)){
            throw new BadRequestException("spark job is not exist.");
        }
        this.runningSparkJob.remove(jobId);
        LOGGER.info("Remove spark job : {} from running list.",jobId);
        return new JsonResponse<>(Response.Status.NO_CONTENT).build();
    }

    @POST
    @Path("sparkJob/license/holdCpu/{jobId}")
    @ZeppelinApi
    public Response validateLicenseBeforeRunSparkCronJob(@PathParam("jobId") String jobId) throws Exception {

        LOGGER.info("Validate license and hold cpu for running cronjob, jobId : {}",jobId);
        bdeHeraServices.forwardLicenseValidateAndHoldCpuRequestToBDE(jobId);
        return new JsonResponse<>(Response.Status.OK).build();
    }

    @POST
    @Path("sparkJob/license/releaseCpu/{jobId}")
    @ZeppelinApi
    public Response releaseCpuAfterRunSparkCronJob(@PathParam("jobId") String jobId) throws Exception {

        LOGGER.info("Release cpu after run cronjob, jobId : {}",jobId);
        bdeHeraServices.forwardReleaseCpuRequestToBDE(jobId);
        return new JsonResponse<>(Response.Status.OK).build();
    }
    
    @POST
    @Path("table/recoverPartition")
    @ZeppelinApi
    public Response recoverPartition(String message) throws Exception {
        LOGGER.info("Recovery partition Table in spark metastore by JSON {}", message);
        TableWSRequest request = GSON.fromJson(message,new TypeToken<TableWSRequest>(){}.getType());
        LOGGER.info("System will recover partitions in table -> {}", request.getTableName());
        bdeSparkService.runRecoveryPartition(request.getTableName());
        LOGGER.info("Recover partition in table -> {} completed!", request.getTableName());
        return new JsonResponse<>(Response.Status.OK).build();
    }

    @POST
    @Path("table/addPartition")
    @ZeppelinApi
    public Response addPartition(String message) throws Exception {
        LOGGER.info("Add partition Table in spark metastore by JSON {}", message);
        TableWSRequest request = GSON.fromJson(message,new TypeToken<TableWSRequest>(){}.getType());
        bdeSparkService.addPartition(request.getTableName(), request.getPartitions());
        return new JsonResponse<>(Response.Status.OK).build();
    }

    @GET
    @Path("validate/cpu")
    @ZeppelinApi
    public Response checkComputeCpuUsageLimit() throws Exception {
        LOGGER.info("Validate cpu core to run interpreter");
        int totalCore = 0;
        List<ManagedInterpreterGroup> managedInterpreterGroup = interpreterSettingManager.getAllInterpreterGroup();
        for (ManagedInterpreterGroup managedInterpreter : managedInterpreterGroup) {
            if (managedInterpreter.getInterpreterProcess() != null
                    && sparkObject.sparkInterpreterGroupNamePort.containsKey(managedInterpreter.getId()))  {
                SparkUsedResponse spark = sparkObject.sparkInterpreterGroupNamePort.get(managedInterpreter.getId());
                if (!spark.isCron()) {
                    totalCore += spark.getCore();
                }
            }
        }
        bdeHeraServices.forwardCheckCpuToHeraLicenseOverLimit(totalCore);
        return new JsonResponse<>(Response.Status.OK).build();
    }

    @POST
    @Path("schema/{name}")
    @ZeppelinApi
    public Response createNewSchema(@PathParam("name") String name) throws Exception {
        String sql = "CREATE SCHEMA IF NOT EXISTS " + name ;
        bdeSparkService.runSparkSqlJob(sql);
        return new JsonResponse<>(Response.Status.OK).build();
    }

}
