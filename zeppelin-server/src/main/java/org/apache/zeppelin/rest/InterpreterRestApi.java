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

package org.apache.zeppelin.rest;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gable.templar.heaven.util.ObjectUtil;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.dep.Repository;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.common.Message;
import org.apache.zeppelin.common.Message.OP;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.rest.bde.view.InterpreterNotebookResponse;
import org.apache.zeppelin.rest.bde.view.spark.AddSparkPortRequest;
import org.apache.zeppelin.rest.bde.view.spark.SparkScopedResponse;
import org.apache.zeppelin.rest.bde.view.spark.SparkTimeOutRequest;
import org.apache.zeppelin.rest.bde.view.spark.SparkUsedResponse;
import org.apache.zeppelin.rest.message.InterpreterInstallationRequest;
import org.apache.zeppelin.rest.message.NewInterpreterSettingRequest;
import org.apache.zeppelin.rest.message.RestartInterpreterRequest;
import org.apache.zeppelin.rest.message.UpdateInterpreterSettingRequest;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.InterpreterService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.SimpleServiceCallback;
import org.apache.zeppelin.service.bde.spark.SparkObject;
import org.apache.zeppelin.socket.NotebookServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.repository.RemoteRepository;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.*;

/**
 * Interpreter Rest API.
 */
@Path("/interpreter")
@Produces("application/json")
@Singleton
public class InterpreterRestApi extends AbstractRestApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(InterpreterRestApi.class);

  private final AuthorizationService authorizationService;
  private final InterpreterService interpreterService;
  private final InterpreterSettingManager interpreterSettingManager;
  private final NotebookServer notebookServer;
  private final SparkObject sparkObject;

  private ZeppelinConfiguration zeppelinConfig = ZeppelinConfiguration.create();

  @Inject
  public InterpreterRestApi(
      AuthenticationService authenticationService,
      AuthorizationService authorizationService,
      InterpreterService interpreterService,
      InterpreterSettingManager interpreterSettingManager,
      NotebookServer notebookWsServer,
      SparkObject sparkObject) {
    super(authenticationService);
    this.authorizationService = authorizationService;
    this.interpreterService = interpreterService;
    this.interpreterSettingManager = interpreterSettingManager;
    this.notebookServer = notebookWsServer;
    this.sparkObject = sparkObject;
  }

  /**
   * List all interpreter settings.
   */
  @GET
  @Path("setting")
  @ZeppelinApi
  public Response listSettings() {
    return new JsonResponse<>(Status.OK, "", interpreterSettingManager.get()).build();
  }

  /**
   * Get a setting.
   */
  @GET
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response getSetting(@PathParam("settingId") String settingId) {
    try {
      InterpreterSetting setting = interpreterSettingManager.get(settingId);
      if (setting == null) {
        return new JsonResponse<>(Status.NOT_FOUND).build();
      } else {
        return new JsonResponse<>(Status.OK, "", setting).build();
      }
    } catch (NullPointerException e) {
      LOGGER.error("Exception in InterpreterRestApi while creating ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
              ExceptionUtils.getStackTrace(e)).build();
    }
  }

  /**
   * Add new interpreter setting.
   *
   * @param message NewInterpreterSettingRequest
   */
  @POST
  @Path("setting")
  @ZeppelinApi
  public Response newSettings(String message) {
    try {
      NewInterpreterSettingRequest request = GSON.fromJson(message, NewInterpreterSettingRequest.class);
      if (request == null) {
        return new JsonResponse<>(Status.BAD_REQUEST).build();
      }

      InterpreterSetting interpreterSetting = interpreterSettingManager
              .createNewSetting(request.getName(), request.getGroup(), request.getDependencies(),
                      request.getOption(), request.getProperties());
      LOGGER.info("new setting created with {}", interpreterSetting.getId());
      return new JsonResponse<>(Status.OK, "", interpreterSetting).build();
    } catch (IOException e) {
      LOGGER.error("Exception in InterpreterRestApi while creating ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
              .build();
    }
  }

  @PUT
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response updateSetting(String message, @PathParam("settingId") String settingId) {
    LOGGER.info("Update interpreterSetting {}", settingId);

    try {
      UpdateInterpreterSettingRequest request = GSON.fromJson(message, UpdateInterpreterSettingRequest.class);
      interpreterSettingManager
              .setPropertyAndRestart(settingId, request.getOption(), request.getProperties(),
                      request.getDependencies());
    } catch (InterpreterException e) {
      LOGGER.error("Exception in InterpreterRestApi while updateSetting ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
              .build();
    } catch (IOException e) {
      LOGGER.error("Exception in InterpreterRestApi while updateSetting ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
              ExceptionUtils.getStackTrace(e)).build();
    }
    InterpreterSetting setting = interpreterSettingManager.get(settingId);
    if (setting == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse<>(Status.OK, "", setting).build();
  }

  /**
   * Remove interpreter setting.
   */
  @DELETE
  @Path("setting/{settingId}")
  @ZeppelinApi
  public Response removeSetting(@PathParam("settingId") String settingId) throws IOException {
    LOGGER.info("Remove interpreterSetting {}", settingId);
    interpreterSettingManager.remove(settingId);
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Restart interpreter setting.
   */
  @PUT
  @Path("setting/restart/{settingId}")
  @ZeppelinApi
  public Response restartSetting(String message, @PathParam("settingId") String settingId) {
    LOGGER.info("Restart interpreterSetting {}, msg={}, user={}", settingId, message, authenticationService.getPrincipal());

    InterpreterSetting setting = interpreterSettingManager.get(settingId);
    try {
      RestartInterpreterRequest request = GSON.fromJson(message, RestartInterpreterRequest.class);

      String noteId = request == null ? null : request.getNoteId();
      if (null == noteId) {
        interpreterSettingManager.close(settingId);
      } else {
        Set<String> entities = new HashSet<>();
        entities.add(authenticationService.getPrincipal());
        entities.addAll(authenticationService.getAssociatedRoles());
        if (authorizationService.hasRunPermission(entities, noteId) ||
                authorizationService.hasWritePermission(entities, noteId) ||
                authorizationService.isOwner(entities, noteId)) {
          interpreterSettingManager.restart(settingId, authenticationService.getPrincipal(), noteId);
        } else {
          return new JsonResponse<>(Status.FORBIDDEN, "No privilege to restart interpreter")
                  .build();
        }
      }
    } catch (InterpreterException e) {
      LOGGER.error("Exception in InterpreterRestApi while restartSetting ", e);
      return new JsonResponse<>(Status.NOT_FOUND, e.getMessage(), ExceptionUtils.getStackTrace(e))
              .build();
    }
    if (setting == null) {
      return new JsonResponse<>(Status.NOT_FOUND, "", settingId).build();
    }
    return new JsonResponse<>(Status.OK, "", setting).build();
  }

  @PUT
  @Path("setting/restart/all/spark")
  @ZeppelinApi
  public Response restartAllSparkInterpreter() {
    Map<String, SparkUsedResponse> sparkInterpreterGroups = sparkObject.sparkInterpreterGroupNamePort;
//    LOGGER.info("Restart interpreterSetting {}, msg={}, user={}", settingId, message, authenticationService.getPrincipal());
    for (Map.Entry<String, SparkUsedResponse> sparkInterpreterGroup : sparkInterpreterGroups.entrySet()) {
      String sparkInterpreterName = sparkInterpreterGroup.getKey();
//      String sparkGroupName = sparkInterpreterName;
      String noteId = null;
      String[] interpreterGroup = sparkInterpreterName.split("-");
      String sparkGroupName = interpreterGroup[0];
      if (!sparkInterpreterName.contains("shared_process")) {
        noteId = interpreterGroup[1];
        if (noteId.equals("anonymous")) {
          noteId = interpreterGroup[2];
        }
      }
      try {
        if (null == noteId) {
          interpreterSettingManager.close(sparkGroupName);
        } else {
          Set<String> entities = new HashSet<>();
          entities.add(authenticationService.getPrincipal());
          entities.addAll(authenticationService.getAssociatedRoles());
          if (authorizationService.hasRunPermission(entities, noteId) ||
                  authorizationService.hasWritePermission(entities, noteId) ||
                  authorizationService.isOwner(entities, noteId)) {
            interpreterSettingManager.restart(sparkGroupName, authenticationService.getPrincipal(), noteId);
          } else {
            LOGGER.error("No privilege to restart interpreter " + sparkGroupName);
          }
        }
      } catch (InterpreterException e) {
        LOGGER.error("Exception in InterpreterRestApi while restartSetting ", e);
      }
    }
    return new JsonResponse<>(Status.OK, "", "").build();
  }


  /**
   * List all available interpreters by group.
   */
  @GET
  @ZeppelinApi
  public Response listInterpreter() {
    Map<String, InterpreterSetting> m = interpreterSettingManager.getInterpreterSettingTemplates();
    return new JsonResponse<>(Status.OK, "", m).build();
  }

  @GET
  @Path("/group/notebooks")
  @ZeppelinApi
  public Response getAllInterpreterGroup() {
    List<InterpreterNotebookResponse> responses = new ArrayList<>();
    List<ManagedInterpreterGroup> managedInterpreterGroup = interpreterSettingManager.getAllInterpreterGroup();
    Notebook notebook = interpreterSettingManager.getNotebook();
    for (ManagedInterpreterGroup managedInterpreter : managedInterpreterGroup) {
      if (managedInterpreter.getInterpreterProcess() != null) {
        InterpreterNotebookResponse interpreterNotebookResponse = new InterpreterNotebookResponse();
        interpreterNotebookResponse.setStartTime(managedInterpreter.getInterpreterProcess().getStartTime());
        String interpreterGroupId = managedInterpreter.getId();
        interpreterNotebookResponse.setInterpreterName(interpreterGroupId);
        interpreterNotebookResponse.setTimeOut(managedInterpreter.getInterpreterProcess().getConnectTimeout());
        interpreterNotebookResponse.setType("shared");
        if (!interpreterGroupId.contains("shared_process")) {
          interpreterNotebookResponse.setType("isolated");
          String[] interpreterGroupStr = interpreterGroupId.split("-");
          int index = 1;
          if (interpreterGroupId.contains("anonymous")) {
            index += 1;
          }
          if (interpreterGroupStr.length > 4) {
            index += 1;
          }
          String noteId = interpreterGroupStr[index];
          interpreterNotebookResponse.setNoteId(noteId);
          String finalNoteId = noteId;
          Optional<NoteInfo> noteInfoOptional = notebook.getNotesInfo().stream()
                  .filter(noteInfo -> noteInfo.getId().equals(finalNoteId))
                  .findFirst();
          String noteName = noteInfoOptional.map(NoteInfo::getNoteName)
                  .orElse(null);
          interpreterNotebookResponse.setNoteName(noteName);

        }
        if (sparkObject.sparkInterpreterGroupNamePort.containsKey(managedInterpreter.getId())) {
          SparkUsedResponse response = sparkObject.sparkInterpreterGroupNamePort.get(managedInterpreter.getId());
          interpreterNotebookResponse.setSparkUrl(response.getPort());
          interpreterNotebookResponse.setCore(response.getCore());
          interpreterNotebookResponse.setMemory(response.getMemory());
          interpreterNotebookResponse.setCron(response.isCron());
        }
        responses.add(interpreterNotebookResponse);
      }
    }
    return new JsonResponse<>(Status.OK, "", responses).build();
  }

  @PUT
  @Path("/restart/all/{noteId}")
  @ZeppelinApi
  public Response restartAllInterpreter(@PathParam("noteId") String noteId) throws Exception {
    List<InterpreterNotebookResponse> responses = new ArrayList<>();
    List<ManagedInterpreterGroup> managedInterpreterGroup = interpreterSettingManager.getAllInterpreterGroup();
    for (ManagedInterpreterGroup managedInterpreter : managedInterpreterGroup) {
      if (managedInterpreter.getInterpreterProcess() != null) {
        String interpreterGroupId = managedInterpreter.getId();
        if (!interpreterGroupId.contains("shared_process")) {
          String[] interpreterGroupStr = interpreterGroupId.split("-");
          int index = 1;
          if (interpreterGroupId.contains("anonymous")) {
            index += 1;
          }
          if (interpreterGroupStr.length > 4) {
            index += 1;
          }
          String interpreterNoteId = interpreterGroupStr[index];
          if (interpreterNoteId.equals(noteId)) {
            String interpreterName = interpreterGroupId.split("-")[0];
            interpreterSettingManager.restart(interpreterName, authenticationService.getPrincipal(), noteId);
          }
        }
      }
    }
    return new JsonResponse<>(Status.OK, "", responses).build();
  }



  @PUT
  @Path("/spark/port")
  @ZeppelinApi
  public void addSparkPort(String message) {
    AddSparkPortRequest request = AddSparkPortRequest.fromJson(message);
    for (Map.Entry<String, SparkUsedResponse> entry : sparkObject.sparkInterpreterGroupNamePort.entrySet()) {
      if (entry.getValue().getPort().equals(request.getPort())) {
        sparkObject.sparkInterpreterGroupNamePort.remove(entry.getKey());
      }
    }
    SparkUsedResponse response = new SparkUsedResponse();
    response.setPort(request.getPort());
    response.setCore(request.getCore());
    response.setMemory(request.getMemory());
    response.setCron(request.isCron());
    response.setTimeOut(new Date(System.currentTimeMillis() + zeppelinConfig.getTime(
            ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_LIFECYCLE_MANAGER_TIMEOUT_THRESHOLD)));
    sparkObject.sparkInterpreterGroupNamePort.put(request.getInterpreterGroupId(), response);
  }

  @PUT
  @Path("/spark/timeout")
  @ZeppelinApi
  public void updateSparkTimeOut(String message) {
    SparkTimeOutRequest request =  SparkTimeOutRequest.fromJson(message);
//    LOGGER.info("Request Id: " + request.getInterpreterGroupId());
    if (sparkObject.sparkInterpreterGroupNamePort.containsKey(request.getInterpreterGroupId())) {
//      LOGGER.info("new time out =" + request.getNewTimeOut());
      sparkObject.sparkInterpreterGroupNamePort.get(request.getInterpreterGroupId()).setTimeOut(new Date(request.getNewTimeOut()));
    }
  }

  /**
   * List of dependency resolving repositories.
   */
  @GET
  @Path("repository")
  @ZeppelinApi
  public Response listRepositories() {
    List<RemoteRepository> interpreterRepositories = interpreterSettingManager.getRepositories();
    return new JsonResponse<>(Status.OK, "", interpreterRepositories).build();
  }

  /**
   * Add new repository.
   *
   * @param message Repository
   */
  @POST
  @Path("repository")
  @ZeppelinApi
  public Response addRepository(String message) {
    try {
      Repository request = Repository.fromJson(message);
      interpreterSettingManager.addRepository(request.getId(), request.getUrl(),
              request.isSnapshot(), request.getAuthentication(), request.getProxy());
      LOGGER.info("New repository {} added", request.getId());
    } catch (Exception e) {
      LOGGER.error("Exception in InterpreterRestApi while adding repository ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
              ExceptionUtils.getStackTrace(e)).build();
    }
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Delete repository.
   *
   * @param repoId ID of repository
   */
  @DELETE
  @Path("repository/{repoId}")
  @ZeppelinApi
  public Response removeRepository(@PathParam("repoId") String repoId) {
    LOGGER.info("Remove repository {}", repoId);
    try {
      interpreterSettingManager.removeRepository(repoId);
    } catch (Exception e) {
      LOGGER.error("Exception in InterpreterRestApi while removing repository ", e);
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, e.getMessage(),
              ExceptionUtils.getStackTrace(e)).build();
    }
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Get available types for property
   */
  @GET
  @Path("property/types")
  public Response listInterpreterPropertyTypes() {
    return new JsonResponse<>(Status.OK, InterpreterPropertyType.getTypes()).build();
  }

  @POST
  @Path("spark/setting/{interpretGroupId}")
  @ZeppelinApi
  public Response switchSparkScopeInterpreter(@PathParam("interpretGroupId") String interpretGroupId) throws Exception {
    String scopedInterpreterStr = zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_SPARK_SCOPED_LIST);
    List<String> scopedInterpreterList = new ArrayList<>();
    if (!ObjectUtil.isNullOrEmpty(scopedInterpreterStr)) {
      scopedInterpreterList = Arrays.asList(scopedInterpreterStr.split(","));
    }
    List<InterpreterSetting> interpreterSettings =  interpreterSettingManager.get();
    String interpreterName;
    if (interpretGroupId.contains("shared_process")) {
      int lastDash = interpretGroupId.lastIndexOf("-");
      interpreterName = interpretGroupId.substring(0, lastDash);
    } else {
      interpreterName = "";
    }
    for (String scopedInterpreterName: scopedInterpreterList) {
      if (!scopedInterpreterName.equals(interpreterName)) {
        Optional<InterpreterSetting> optionalSetting = interpreterSettings.stream()
                .filter(interpreterSetting -> interpreterSetting.getName().equals(scopedInterpreterName))
                .findFirst();
        if (optionalSetting.isPresent()) {
          SparkScopedResponse sparkScopedResponse = sparkObject.sparkScopedMap.get(scopedInterpreterName);
          Date date = new Date();
          if (!ObjectUtil.isNullOrEmpty(sparkScopedResponse) && date.before(sparkScopedResponse.getClearResourceTime())) {
            continue;
          }
          InterpreterSetting setting = optionalSetting.get();
          LOGGER.info("switch spark scoped id: " + interpretGroupId + " to: " + setting.getName());
          String tmpUri = zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
                  + zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.HERA_SERVICE_SET_DEFAULT_SPARK_INTERPRETER_URL);
          String restUri = UriComponentsBuilder.fromUriString(tmpUri).buildAndExpand(setting.getName()).toUriString();
          String moduleRefKey = zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_MODULE_REF_KEY);

          LOGGER.debug("Request with module reference key : {}",moduleRefKey);
          RestTemplateFactoryUtil.getRestTemplar(moduleRefKey,true).put(restUri,null);
          SparkScopedResponse currentSparkScoped = new SparkScopedResponse();
          currentSparkScoped.setInterpreterName(interpreterName);
          currentSparkScoped.setClearResourceTime( new Date(date.getTime()  + zeppelinConfig.getTime(
                  ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_SPARK_SCOPED_CLEAR_LIFECYCLE)));
          sparkObject.sparkScopedMap.put(interpreterName, currentSparkScoped);
          return new JsonResponse<>(Status.OK, setting.getName()).build();
        }
      }
    }
    return new JsonResponse<>(Status.OK, "").build();

  }

  /** Install interpreter */
  @POST
  @Path("install")
  @ZeppelinApi
  public Response installInterpreter(@NotNull String message) {
    LOGGER.info("Install interpreter: {}", message);
    InterpreterInstallationRequest request = GSON.fromJson(message, InterpreterInstallationRequest.class);
    try {
      interpreterService.installInterpreter(
              request,
              new SimpleServiceCallback<String>() {
                @Override
                public void onStart(String message, ServiceContext context) {
                  Message m = new Message(OP.INTERPRETER_INSTALL_STARTED);
                  Map<String, Object> data = new HashMap<>();
                  data.put("result", "Starting");
                  data.put("message", message);
                  m.data = data;
                  notebookServer.broadcast(m);
                }

                @Override
                public void onSuccess(String message, ServiceContext context) {
                  Message m = new Message(OP.INTERPRETER_INSTALL_RESULT);
                  Map<String, Object> data = new HashMap<>();
                  data.put("result", "Succeed");
                  data.put("message", message);
                  m.data = data;
                  notebookServer.broadcast(m);
                }

                @Override
                public void onFailure(Exception ex, ServiceContext context) {
                  Message m = new Message(OP.INTERPRETER_INSTALL_RESULT);
                  Map<String, Object> data = new HashMap<>();
                  data.put("result", "Failed");
                  data.put("message", ex.getMessage());
                  m.data = data;
                  notebookServer.broadcast(m);
                }
              });
    } catch (Throwable t) {
      return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, t.getMessage()).build();
    }

    return new JsonResponse<>(Status.OK).build();
  }
}

