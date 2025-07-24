/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.spark;

import co.blendata.BatchManageService;
import co.blendata.spark.VerifyRule;
import co.blendata.view.DataImportRequest;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkStatusTracker;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SQLContext;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.spark.sql.SparkSession;
import org.apache.zeppelin.interpreter.AbstractInterpreter;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.spark.bde.services.license.HealthStatusCronRunnable;
import org.apache.zeppelin.spark.bde.services.license.HealthStatusRunnable;
import org.apache.zeppelin.spark.bde.services.runnable.StopSparkRunnable;
import org.apache.zeppelin.spark.bde.services.spark.SparkService;
import org.apache.zeppelin.spark.bde.services.spark.view.StartUpResolvedDependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.zeppelin.spark.bde.services.license.LicenseService;
import scala.Tuple2;
import scala.collection.JavaConverters;

/**
 * SparkInterpreter of Java implementation. It delegates to different scala version AbstractSparkScalaInterpreter.
 *
 */
public class SparkInterpreter extends AbstractInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(SparkInterpreter.class);
  private static File scalaShellOutputDir;
  private static ZeppelinConfiguration zConf = ZeppelinConfiguration.create();
  private LicenseService licenseService = new LicenseService();

  private SparkService sparkService = new SparkService();

  static {
    try {
      // scala shell output will be shared between multiple spark scala shell, so use static field
      scalaShellOutputDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "spark")
              .toFile();
      scalaShellOutputDir.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Fail to create scala shell output dir", e);
    }
  }

  private static AtomicInteger SESSION_NUM = new AtomicInteger(0);
  private static Class<?> innerInterpreterClazz;
  private AbstractSparkScalaInterpreter innerInterpreter;
  private Map<String, String> innerInterpreterClassMap = new HashMap<>();
  private SparkContext sc;
  private JavaSparkContext jsc;
  private SQLContext sqlContext;
  private SparkSession sparkSession;

  private SparkVersion sparkVersion;
  private String scalaVersion;
  private boolean enableSupportedVersionCheck;
  private long scStartTime;

  public SparkInterpreter(Properties properties) {

    super(properties);
    // set scala.color
    if (Boolean.parseBoolean(properties.getProperty("zeppelin.spark.scala.color", "true"))) {
      System.setProperty("scala.color", "true");
    }

    this.enableSupportedVersionCheck = java.lang.Boolean.parseBoolean(
        properties.getProperty("zeppelin.spark.enableSupportedVersionCheck", "true"));
    innerInterpreterClassMap.put("2.12", "org.apache.zeppelin.spark.SparkScala212Interpreter");
    innerInterpreterClassMap.put("2.13", "org.apache.zeppelin.spark.SparkScala213Interpreter");

    this.scStartTime = System.currentTimeMillis();
  }

  @Override
  public void open() throws InterpreterException {
    LOGGER.info("Test Default Spark Interpreter...");
    RestTemplateFactoryUtil.setHttpConnectTimeout(zConf.getInt(ConfVars.ZEPPELIN_CONNECTION_TIMEOUT));
    try {
      SparkConf conf = new SparkConf();
      boolean isCron = false;
      for (Map.Entry<Object, Object> entry : getProperties().entrySet()) {
        if (!StringUtils.isBlank(entry.getValue().toString())) {
          conf.set(entry.getKey().toString(), entry.getValue().toString());
        }
        // zeppelin.spark.useHiveContext & zeppelin.spark.concurrentSQL are legacy zeppelin
        // properties, convert them to spark properties here.
        if (entry.getKey().toString().equals("zeppelin.spark.useHiveContext")) {
          conf.set("spark.useHiveContext", entry.getValue().toString());
        }
        if (entry.getKey().toString().equals("zeppelin.spark.concurrentSQL")
            && entry.getValue().toString().equals("true")) {
          conf.set(SparkStringConstants.SCHEDULER_MODE_PROP_NAME, "FAIR");
        }
      }
      // use local mode for embedded spark mode when spark.master is not found
      if (!conf.contains(SparkStringConstants.MASTER_PROP_NAME)) {
        if (conf.contains("master")) {
          conf.set(SparkStringConstants.MASTER_PROP_NAME, conf.get("master"));
        } else {
          String masterEnv = System.getenv(SparkStringConstants.MASTER_ENV_NAME);
          conf.set(SparkStringConstants.MASTER_PROP_NAME,
                  masterEnv == null ? SparkStringConstants.DEFAULT_MASTER_VALUE : masterEnv);
        }
      }

      //Validate license and hold cpu before create spark session for cronJob
      if(conf.get("spark.app.name").contains("cron")){
        String jobId = (conf.get("spark.app.name")).split("-")[1] + "-" + this.scStartTime;
//        licenseService.callValidateLicenseAndHoldCpu(jobId);
        isCron = true;
      }

      this.innerInterpreter = loadSparkScalaInterpreter(conf);
      this.innerInterpreter.open();
      sc = this.innerInterpreter.getSparkContext();
      scala.collection.Map<String, Tuple2<Object, Object>> scalaExecutorMemoryStatus = sc.getExecutorMemoryStatus();
      // Convert Scala Map to Java Map
      Map<String, Tuple2<Object, Object>> executorMemoryStatus = JavaConverters.mapAsJavaMap(scalaExecutorMemoryStatus);
      int totalExecutors = executorMemoryStatus.size() - 1;

      jsc = JavaSparkContext.fromSparkContext(sc);
      sparkVersion = SparkVersion.fromVersionString(sc.version());
      if (enableSupportedVersionCheck && sparkVersion.isUnsupportedVersion()) {
        throw new Exception("This is not officially supported spark version: " + sparkVersion
            + "\nYou can set zeppelin.spark.enableSupportedVersionCheck to false if you really" +
            " want to try this version of spark.");
      }
      sqlContext = this.innerInterpreter.getSqlContext();
      sparkSession = this.innerInterpreter.getSparkSession();
//      if (zConf.getBoolean(ConfVars.ZEPPELIN_ENABLE_SPARK_PERMISSION)) {
//        List<Rule<LogicalPlan>> rules = new ArrayList<>();
//        rules.add(new VerifyRule());
//        sparkSession.experimental().extraOptimizations_$eq(JavaConverters.collectionAsScalaIterable(rules).toSeq());
//      }

      String[] sparkUrls = this.innerInterpreter.getSparkUrl().split(":");
      int executorCores = Integer.parseInt(conf.get("spark.executor.cores"));
      Double core = (double) totalExecutors * executorCores;
      String memory = conf.get("spark.executor.memory");
      sparkService.sentAddedSparkPortToInterpreterGroup(System.getenv("INTERPRETER_GROUP_ID"),
              sparkUrls[sparkUrls.length -1], core, memory, isCron);
      licenseService.callValidateLicenseCpu();

      if (zConf.getBoolean(ConfVars.ZEPPELIN_ENABLE_SAME_METASTORE) && zConf.getBoolean(ConfVars.ZEPPELIN_ENABLE_VIEW_TABLE) && SESSION_NUM.get() == 0) {
        LOGGER.info("Start create view table");
        //call hera api
        BatchManageService batchManageService = new BatchManageService();
        StartUpResolvedDependency callDataResponse = sparkService.getListDataCalling();
        for (DataImportRequest dataImportRequest: callDataResponse.getImportDataCallingList()) {
          try {
            batchManageService.saveAsTempView(dataImportRequest, sparkSession);
          } catch (Exception e) {
            LOGGER.error(e.getMessage() ,e);
          }
        }
      }

      if (SESSION_NUM.get() == 0) {
        //Create thread to send health status to BDE to calculate pay per use license model
        if (isCron) {
          Thread healthStatusCronRunner = new Thread(new HealthStatusCronRunnable(sc,this.scStartTime, core, executorCores, sparkUrls, memory,  this));
          healthStatusCronRunner.start();
        }
        else {
          Thread healthStatusRunner = new Thread(new HealthStatusRunnable(sc,this.scStartTime, core, executorCores, sparkUrls, memory, this));
          healthStatusRunner.start();
        }
      }

      SESSION_NUM.incrementAndGet();
    }catch (Exception e) {
      LOGGER.error("Fail to open SparkInterpreter", e);
      throw new InterpreterException("Fail to open SparkInterpreter", e);
    }
  }

  /**
   * Load AbstractSparkScalaInterpreter based on the runtime scala version.
   * Load AbstractSparkScalaInterpreter from the following location:
   *
   * SparkScala211Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.11
   * SparkScala212Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.12
   * SparkScala213Interpreter   ZEPPELIN_HOME/interpreter/spark/scala-2.13
   *
   * @param conf
   * @return AbstractSparkScalaInterpreter
   * @throws Exception
   */
  private AbstractSparkScalaInterpreter loadSparkScalaInterpreter(SparkConf conf) throws Exception {
    scalaVersion = extractScalaVersion(conf);
    // Make sure the innerInterpreter Class is loaded only once into JVM
    // Use double lock to ensure thread safety
    if (innerInterpreterClazz == null) {
      synchronized (SparkInterpreter.class) {
        if (innerInterpreterClazz == null) {
          LOGGER.debug("innerInterpreterClazz is null, thread:{}", Thread.currentThread().getName());
          ClassLoader scalaInterpreterClassLoader = Thread.currentThread().getContextClassLoader();
          String zeppelinHome = System.getenv("ZEPPELIN_HOME");
          if (zeppelinHome != null) {
            // ZEPPELIN_HOME is null in yarn-cluster mode, load it directly via current ClassLoader.
            // otherwise, load from the specific folder ZEPPELIN_HOME/interpreter/spark/scala-<version>
            File scalaJarFolder = new File(zeppelinHome + "/interpreter/spark/scala-" + scalaVersion);
            List<URL> urls = new ArrayList<>();
            for (File file : scalaJarFolder.listFiles()) {
              LOGGER.debug("Add file {} to classpath of spark scala interpreter: {}", file.getAbsolutePath(),
                scalaJarFolder);
              urls.add(file.toURI().toURL());
            }
            scalaInterpreterClassLoader = new URLClassLoader(urls.toArray(new URL[0]),
                    Thread.currentThread().getContextClassLoader());
          }
          String innerIntpClassName = innerInterpreterClassMap.get(scalaVersion);
          innerInterpreterClazz = scalaInterpreterClassLoader.loadClass(innerIntpClassName);
        }
      }
    }
    return (AbstractSparkScalaInterpreter)
            innerInterpreterClazz.getConstructor(SparkConf.class, List.class, Properties.class, InterpreterGroup.class, URLClassLoader.class, File.class)
                    .newInstance(conf, getDependencyFiles(), getProperties(), getInterpreterGroup(), innerInterpreterClazz.getClassLoader(), scalaShellOutputDir);
  }

  @Override
  public void close() throws InterpreterException {
    LOGGER.info("Close SparkInterpreter");
    LOGGER.info("SessionNum: " + SESSION_NUM.get());
    LicenseService bdeLicenseService = new LicenseService();
    boolean isCron = false;
    try {
      if (sc != null) {
        String sparkAppName = sc.appName();
        String jobId = sparkAppName.contains("-") ? (sparkAppName.split("-"))[1] + "-" + this.scStartTime : null;
        if(jobId != null){
          isCron = true;
          LOGGER.info("Stop sending health status (license statement) of spark : {} , jobId : {} ...",sparkAppName,jobId);
          bdeLicenseService.deleteSparkJobHealthStatus(jobId);
          bdeLicenseService.callReleaseCpu(jobId);
        }
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
    }

    if (SESSION_NUM.decrementAndGet() == 0 && innerInterpreter != null) {
      LOGGER.info("Close Inner Interpreter");
      innerInterpreter.close();
      innerInterpreterClazz = null;
//      Thread sparkSparkPidRunner = new Thread(new StopSparkRunnable(SESSION_NUM));
//      sparkSparkPidRunner.start();
    }

    else if (sc == null || sc.isStopped()) {
      LOGGER.info("Spark context close. Close spark interpreter");
      innerInterpreter.close();
      innerInterpreterClazz = null;
//      Thread sparkSparkPidRunner = new Thread(new StopSparkRunnable(SESSION_NUM));
//      sparkSparkPidRunner.start();
    }

    else {
        if (innerInterpreter != null) {
          innerInterpreter.closeSparkILoop();
        }
    }
    innerInterpreter = null;
    //change update license to service managedInterpreter
    if (SESSION_NUM.get() == 0 && !isCron) {
      try {
        bdeLicenseService.sendSparkJobHealthStatus(null,null, new Date(System.currentTimeMillis()), null, true);
      } catch (Exception e) {
        LOGGER.error(e.getMessage());
      }
    }

  }

  @Override
  public InterpreterResult internalInterpret(String st,
                                             InterpreterContext context) throws InterpreterException {
    context.out.clear();
    sc.setJobGroup(Utils.buildJobGroupId(context), Utils.buildJobDesc(context), false);
    // set spark.scheduler.pool to null to clear the pool assosiated with this paragraph
    // sc.setLocalProperty("spark.scheduler.pool", null) will clean the pool
    sc.setLocalProperty("spark.scheduler.pool", context.getLocalProperties().get("pool"));

    if (context.getLocalProperties().get("runBy") != null) {
      String user = context.getLocalProperties().get("runBy");
      if (user != null && !user.equals("UNKNOWN")) {
        LOGGER.info("Run spark by: " + user);
        sc.setLocalProperty("username", user);
      } else if (user.toLowerCase().equals("public")) {
        LOGGER.info("Get Public User in NotebookId: " + context.getNoteId());
      } else {
        LOGGER.info("Run spark in noteId: " + context.getNoteId());
        sc.setLocalProperty("notebookId", context.getNoteId());
      }
    }
    String heraUrl = zConf.getString(ConfVars.HERA_ADDR)+":"+zConf.getString(ConfVars.HERA_PORT);
    sc.setLocalProperty("heraUrl", heraUrl);
    sc.setLocalProperty("refKey", zConf.getString(ConfVars.ZEPPELIN_APP_MODULE_REF_KEY));
    sc.setLocalProperty("tenantId", zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    return innerInterpreter.interpret(st, context);
  }

  @Override
  public void cancel(InterpreterContext context) throws InterpreterException {
    innerInterpreter.cancel(context);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf,
                                                int cursor,
                                                InterpreterContext interpreterContext) throws InterpreterException {
    return innerInterpreter.completion(buf, cursor, interpreterContext);
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public int getProgress(InterpreterContext context) throws InterpreterException {
    return innerInterpreter.getProgress(context);
  }

  @Override
  public ZeppelinContext getZeppelinContext() {
    if (this.innerInterpreter == null) {
      throw new RuntimeException("innerInterpreterContext is null");
    }
    return this.innerInterpreter.getZeppelinContext();
  }

  public SparkContext getSparkContext() {
    return this.sc;
  }

  public SQLContext getSQLContext() {
    return sqlContext;
  }

  public JavaSparkContext getJavaSparkContext() {
    return this.jsc;
  }

  public SparkSession getSparkSession() {
    return sparkSession;
  }

  public SparkVersion getSparkVersion() {
    return sparkVersion;
  }

  private String extractScalaVersion(SparkConf conf) throws InterpreterException {
    // Use the scala version if SparkLauncher pass it by name of "zeppelin.spark.scala.version".

    // If not, detect scala version by resource file library.version on classpath.
    // Library.version is sometimes inaccurate and it is mainly used for unit test.
    String scalaVersionString;
    if (conf.contains("zeppelin.spark.scala.version")) {
      scalaVersionString = conf.get("zeppelin.spark.scala.version");
    } else {
      scalaVersionString = scala.util.Properties.versionString();
    }
    LOGGER.info("Using Scala: {}", scalaVersionString);

    if (StringUtils.isEmpty(scalaVersionString)) {
      throw new InterpreterException("Scala Version is empty");
    } else if (scalaVersionString.contains("2.12")) {
      return "2.12";
    } else if (scalaVersionString.contains("2.13")) {
      return "2.13";
    } else {
      throw new InterpreterException("Unsupported scala version: " + scalaVersionString);
    }
  }


  public boolean isScala212() {
    return scalaVersion.equals("2.12");
  }

  public boolean isScala213() {
    return scalaVersion.equals("2.13");
  }

  private List<String> getDependencyFiles() throws InterpreterException {
    List<String> depFiles = new ArrayList<>();
    // add jar from local repo
    String localRepo = getProperty("zeppelin.interpreter.localRepo");
    if (localRepo != null) {
      File localRepoDir = new File(localRepo);
      if (localRepoDir.exists()) {
        File[] files = localRepoDir.listFiles();
        if (files != null) {
          for (File f : files) {
            depFiles.add(f.getAbsolutePath());
          }
        }
      }
    }
    return depFiles;
  }

  public ClassLoader getScalaShellClassLoader() {
    return innerInterpreter.getScalaShellClassLoader();
  }

  public boolean isUnsupportedSparkVersion() {
    return enableSupportedVersionCheck  && sparkVersion.isUnsupportedVersion();
  }

  public AbstractSparkScalaInterpreter getInnerInterpreter() {
    return innerInterpreter;
  }

}
