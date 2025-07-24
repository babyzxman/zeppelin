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

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.gable.templar.heaven.util.ObjectUtil;
import com.gable.templar.heaven.util.RestTemplateFactoryUtil;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.bde.services.hera.BDEHeraServices;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.jupyter.JupyterUtil;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.AuthorizationService;
import org.apache.zeppelin.notebook.repo.NotebookRepoWithVersionControl;
import org.apache.zeppelin.notebook.scheduler.SchedulerService;
import org.apache.zeppelin.rest.bde.view.CloneNotebookRequest;
import org.apache.zeppelin.rest.exception.BadRequestException;
import org.apache.zeppelin.rest.exception.ForbiddenException;
import org.apache.zeppelin.rest.exception.NoteNotFoundException;
import org.apache.zeppelin.rest.exception.ParagraphNotFoundException;
import org.apache.zeppelin.rest.message.*;
import org.apache.zeppelin.rest.response.NotebookMultipleNoteExportResponse;
import org.apache.zeppelin.rest.response.NotebookExportResponse;
import org.apache.zeppelin.search.SearchService;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.service.AuthenticationService;
import org.apache.zeppelin.service.JobManagerService;
import org.apache.zeppelin.service.NotebookService;
import org.apache.zeppelin.service.ServiceContext;
import org.apache.zeppelin.service.bde.git.NotebookGitService;
import org.apache.zeppelin.service.bde.hera.DirectoryCopy;
import org.apache.zeppelin.service.bde.hera.UserContext;
import org.apache.zeppelin.service.bde.hera.view.ValidateUserNotebookRequest;
import org.apache.zeppelin.socket.NotebookServer;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.apache.zeppelin.util.IdHashes;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import static org.apache.zeppelin.common.Message.MSG_ID_NOT_DEFINED;

/**
 * Rest api endpoint for the notebook.
 */
@Path("/notebook")
@Produces("application/json")
@Singleton
public class NotebookRestApi extends AbstractRestApi {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotebookRestApi.class);

  private final ZeppelinConfiguration zConf;
  private final Notebook notebook;
  private final NotebookServer notebookServer;
  private final SearchService noteSearchService;
  private final AuthorizationService authorizationService;
  private final NotebookService notebookService;
  private final JobManagerService jobManagerService;
  private final SchedulerService schedulerService;
  private static ZeppelinConfiguration zeppelinConfig = ZeppelinConfiguration.create();

  @Inject
  public NotebookRestApi(
      Notebook notebook,
      NotebookServer notebookServer,
      NotebookService notebookService,
      SearchService search,
      AuthorizationService authorizationService,
      ZeppelinConfiguration zConf,
      AuthenticationService authenticationService,
      JobManagerService jobManagerService,
      SchedulerService schedulerService) {
    super(authenticationService);
    this.notebook = notebook;
    this.notebookServer = notebookServer;
    this.notebookService = notebookService;
    this.jobManagerService = jobManagerService;
    this.noteSearchService = search;
    this.authorizationService = authorizationService;
    this.zConf = zConf;
    this.schedulerService = schedulerService;
  }

  /**
   * Get note authorization information.
   */
  @GET
  @Path("{noteId}/permissions")
  @ZeppelinApi
  public Response getNotePermissions(@PathParam("noteId") String noteId) {
    checkIfUserIsAnon(getBlockNotAuthenticatedUserErrorMsg());
    checkIfUserCanRead(noteId,
            "Insufficient privileges you cannot get the list of permissions for this note");
    HashMap<String, Set<String>> permissionsMap = new HashMap<>();
    permissionsMap.put("owners", authorizationService.getOwners(noteId));
    permissionsMap.put("readers", authorizationService.getReaders(noteId));
    permissionsMap.put("writers", authorizationService.getWriters(noteId));
    permissionsMap.put("runners", authorizationService.getRunners(noteId));
    return new JsonResponse<>(Status.OK, "", permissionsMap).build();
  }

  private String ownerPermissionError(Set<String> current, Set<String> allowed) {
    LOGGER.info("Cannot change permissions. Connection owners {}. Allowed owners {}",
        current.toString(), allowed.toString());
    return "Insufficient privileges to change permissions.\n\n" +
        "Allowed owners: " + allowed.toString() + "\n\n" +
        "User belongs to: " + current.toString();
  }

  private String getBlockNotAuthenticatedUserErrorMsg() {
    return "Only authenticated user can set the permission.";
  }

  /*
   * Set of utils method to check if current user can perform action to the note.
   * Since we only have security on notebook level, from now we keep this logic in this class.
   * In the future we might want to generalize this for the rest of the api enmdpoints.
   */

  /**
   * Check if the current user is not authenticated(anonymous user) or not.
   */
  private void checkIfUserIsAnon(String errorMsg) {
    boolean isAuthenticated = authenticationService.isAuthenticated();
    if (isAuthenticated && authenticationService.getPrincipal().equals("anonymous")) {
      LOGGER.info("Anonymous user cannot set any permissions for this note.");
      throw new ForbiddenException(errorMsg);
    }
  }

  /**
   * Check if the current user own the given note.
   */
  private void checkIfUserIsOwner(String noteId, String errorMsg) {
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(authenticationService.getPrincipal());
    userAndRoles.addAll(authenticationService.getAssociatedRoles());
    if (!authorizationService.isOwner(userAndRoles, noteId)) {
     throw new ForbiddenException(errorMsg);
    }
  }

  /**
   * Check if the current user is either Owner or Writer for the given note.
   */
  private void checkIfUserCanWrite(String noteId, String errorMsg) {
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(authenticationService.getPrincipal());
    userAndRoles.addAll(authenticationService.getAssociatedRoles());
    if (!authorizationService.hasWritePermission(userAndRoles, noteId)) {
     throw new ForbiddenException(errorMsg);
    }
  }

  /**
   * Check if the current user can access (at least he have to be reader) the given note.
   */
  private void checkIfUserCanRead(String noteId, String errorMsg) {
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(authenticationService.getPrincipal());
    userAndRoles.addAll(authenticationService.getAssociatedRoles());
    if (!authorizationService.hasReadPermission(userAndRoles, noteId)) {
      throw new ForbiddenException(errorMsg);
    }
  }

  /**
   * Check if the current user can run the given note.
   */
  private void checkIfUserCanRun(String noteId, String errorMsg) {
    Set<String> userAndRoles = new HashSet<>();
    userAndRoles.add(authenticationService.getPrincipal());
    userAndRoles.addAll(authenticationService.getAssociatedRoles());
    if (!authorizationService.hasRunPermission(userAndRoles, noteId)) {
     throw new ForbiddenException(errorMsg);
    }
  }

  private void checkIfNoteIsNotNull(Note note, String noteId) {
    if (note == null) {
      throw new NoteNotFoundException(noteId);
    }
  }

  private void checkIfNoteSupportsCron(Note note) {
    if (!note.isCronSupported(notebook.getConf())) {
      LOGGER.error("Cron is not enabled from Zeppelin server");
      throw new ForbiddenException("Cron is not enabled from Zeppelin server");
    }
  }

  private void checkIfParagraphIsNotNull(Paragraph paragraph, String paragraphId) {
    if (paragraph == null) {
      throw new ParagraphNotFoundException(paragraphId);
    }
  }

  /**
   * Set note authorization information.
   */
  @PUT
  @Path("{noteId}/permissions")
  @ZeppelinApi
  public Response putNotePermissions(@PathParam("noteId") String noteId, String req)
      throws IOException {

    String principal = authenticationService.getPrincipal();
    Set<String> roles = authenticationService.getAssociatedRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);

    checkIfUserIsAnon(getBlockNotAuthenticatedUserErrorMsg());
    checkIfUserIsOwner(noteId,
            ownerPermissionError(userAndRoles, authorizationService.getOwners(noteId)));

    PermissionRequest permMap = GSON.fromJson(req, PermissionRequest.class);
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        Set<String> readers = permMap.getReaders();
        Set<String> runners = permMap.getRunners();
        Set<String> owners = permMap.getOwners();
        Set<String> writers = permMap.getWriters();

        LOGGER.info("Set permissions to note: {} with current user:{}, owners:{}, readers:{}, runners:{}, writers:{}",
                noteId, principal, owners, readers, runners, writers);

        // Set readers, if runners, writers and owners is empty -> set to user requesting the change
        if (readers != null && !readers.isEmpty()) {
          if (runners.isEmpty()) {
            runners = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
          if (writers.isEmpty()) {
            writers = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
          if (owners.isEmpty()) {
            owners = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
        }
        // Set runners, if writers and owners is empty -> set to user requesting the change
        if (runners != null && !runners.isEmpty()) {
          if (writers.isEmpty()) {
            writers = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
          if (owners.isEmpty()) {
            owners = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
        }
        // Set writers, if owners is empty -> set to user requesting the change
        if (writers != null && !writers.isEmpty()) {
          if (owners.isEmpty()) {
            owners = new HashSet<>(Arrays.asList(authenticationService.getPrincipal()));
          }
        }

        authorizationService.setReaders(noteId, readers);
        authorizationService.setRunners(noteId, runners);
        authorizationService.setWriters(noteId, writers);
        authorizationService.setOwners(noteId, owners);
        LOGGER.debug("After set permissions {} {} {} {}", authorizationService.getOwners(noteId),
                authorizationService.getReaders(noteId), authorizationService.getRunners(noteId),
                authorizationService.getWriters(noteId));
        AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
        authorizationService.saveNoteAuth();
        notebookServer.broadcastNote(note);
        notebookServer.broadcastNoteList(subject, userAndRoles);
        return new JsonResponse<>(Status.OK).build();
      });
  }

  /**
   * Return noteinfo list for the current user who has reader permission.
   *
   * @return
   * @throws IOException
   */
  @GET
  @ZeppelinApi
  public Response getNoteList() throws IOException {
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK, "", notesInfo).build();
  }

  @POST
  @Path("reload/clear")
  @ZeppelinApi
  public Response clearCatch() throws IOException {
    notebookService.clearNoteCatch();
    return new JsonResponse<>(Status.OK, "", "success").build();
  }

  @POST
  @Path("reload/clear/id")
  public Response clearCatchByNoteId(String message) throws IOException {
    List<String> noteIdList = new ArrayList<>();
    ClearCacheNotebookRequest request = GSON.fromJson(message, ClearCacheNotebookRequest.class);
    if (request.getNoteIds() != null) {
      noteIdList = request.getNoteIds();
    }
    if (request.getNoteId() != null) {
      noteIdList.add(request.getNoteId());
    }
    notebookService.clearNoteCatchById(noteIdList);
    return new JsonResponse<>(Status.OK, "", "success").build();
  }


  /**
   * Get note of this specified noteId.
   *
   * @param noteId
   * @param reload
   * @return
   * @throws IOException
   */
  @GET
  @Path("{noteId}")
  @ZeppelinApi
  public Response getNote(@PathParam("noteId") String noteId,
                          @QueryParam("reload") boolean reload) throws IOException {
    return notebookService.getNote(noteId, reload,  getServiceContext(), new RestServiceCallback<>(),
      note -> new JsonResponse<>(Status.OK, "", note).build());
  }


  /**
   * Get revision history of a note.
   *
   * @param noteId
   * @return
   * @throws IOException
   */
  @GET
  @Path("{noteId}/revision")
  @ZeppelinApi
  public Response getNoteRevisionHistory(@PathParam("noteId") String noteId) throws IOException {
    LOGGER.info("Get revision history of note {}", noteId);
    List<NotebookRepoWithVersionControl.Revision> revisions = notebookService.listRevisionHistory(noteId, getServiceContext(), new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK, revisions).build();
  }


  /**
   * Save a revision for the a note
   *
   * @param message
   * @param noteId
   * @return
   * @throws IOException
   */
  @POST
  @Path("{noteId}/revision")
  @ZeppelinApi
  public Response checkpointNote(String message,
                                 @PathParam("noteId") String noteId) throws IOException {
    LOGGER.info("Commit note by JSON {}", message);
    CheckpointNoteRequest request = GSON.fromJson(message, CheckpointNoteRequest.class);
    if (request == null || StringUtils.isEmpty(request.getCommitMessage())) {
      LOGGER.warn("Trying to commit notebook {} with empty commitMessage", noteId);
      throw new BadRequestException("commitMessage can not be empty");
    }
    NotebookRepoWithVersionControl.Revision revision = notebookService.checkpointNote(noteId, request.getCommitMessage(), getServiceContext(), new RestServiceCallback<>());
    if (revision == null || StringUtils.isEmpty(revision.id)) {
      return new JsonResponse<>(Status.OK, "Couldn't checkpoint note revision: possibly no changes found or storage doesn't support versioning. "
              + "Please check the logs for more details.").build();
    }
    return new JsonResponse<>(Status.OK, "", revision.id).build();
  }


  /**
   * Get a specified revision of a note.
   *
   * @param noteId
   * @param revisionId
   * @param reload
   * @return
   * @throws IOException
   */
  @GET
  @Path("{noteId}/revision/{revisionId}")
  @ZeppelinApi
  public Response getNoteByRevison(@PathParam("noteId") String noteId,
                                   @PathParam("revisionId") String revisionId,
                                   @QueryParam("reload") boolean reload) throws IOException {
    LOGGER.info("Get note {} by the revision {}", noteId, revisionId);
    Note noteRevision = notebookService.getNotebyRevision(noteId, revisionId, getServiceContext(), new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK, "", noteRevision).build();
  }


  /**
   * Revert a note to the specified version
   *
   * @param noteId
   * @param revisionId
   * @return
   * @throws IOException
   */
  @PUT
  @Path("{noteId}/revision/{revisionId}")
  @ZeppelinApi
  public Response setNoteRevision(@PathParam("noteId") String noteId,
                                  @PathParam("revisionId") String revisionId) throws IOException {
    LOGGER.info("Revert note {} to the revision {}", noteId, revisionId);
    notebookService.setNoteRevision(noteId, revisionId, getServiceContext(), new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK).build();
  }


  /**
   * Get note of this specified notePath.
   *
   * @param message - JSON containing notePath
   * @param reload
   * @return
   * @throws IOException
   */

  @POST
  @Path("getByPath")
  @ZeppelinApi
  public Response getNoteByPath(String message,
                                @QueryParam("reload") boolean reload) throws IOException {
    // notePath may contains special character like space.
    // it should be in http body instead of in url
    // to avoid problem of url conversion by external service like knox
    GetNoteByPathRequest request = GSON.fromJson(message, GetNoteByPathRequest.class);
    String notePath = request.getNotePath();
    return notebookService.getNoteByPath(notePath, reload, getServiceContext(), new RestServiceCallback<>(),
            note -> new JsonResponse<>(Status.OK, "", note).build());
  }


  /**
   * Export note REST API.
   *
   * @param noteId ID of Note
   * @return note JSON with status.OK
   * @throws IOException
   */
  @GET
  @Path("export/{noteId}")
  @ZeppelinApi
  public Response exportNote(@PathParam("noteId") String noteId) throws IOException {
    checkIfUserCanRead(noteId, "Insufficient privileges you cannot export this note");
    String exportJson = notebook.exportNote(noteId);
    return new JsonResponse<>(Status.OK, "", exportJson).build();
  }

  /**
   * Export note REST API.
   *
   * @param noteId ID of Note
   * @return note JSON with status.OK
   * @throws IOException
   */
  @GET
  @Path("export/ipynb/{noteId}")
  @ZeppelinApi
  public Response exportIpynbNote(@PathParam("noteId") String noteId) throws IOException {
    checkIfUserCanRead(noteId, "Insufficient privileges you cannot export this note");
    String exportJson = notebook.exportIpynbNote(noteId);
    return new JsonResponse<>(Status.OK, "", exportJson).build();
  }

  @POST
  @Path("export/list")
  @ZeppelinApi
  public Response exportNoteList(String message) throws IOException {
//    checkIfUserCanRead(noteId, "Insufficient privileges you cannot export this note");
    ExportNotebookListRequest request = GSON.fromJson(message, ExportNotebookListRequest.class);
    List<NotebookExportResponse> exportJsonList = new ArrayList<>();
    for (String noteId: request.getNoteIds()) {
      NotebookExportResponse response = new NotebookExportResponse();
      response.setNotebookId(noteId);
      if (request.getFormat().equals("ipynb")) {
        response.setExportJson(notebook.exportIpynbNote(noteId));
      }
      else {
        response.setExportJson(notebook.exportNote(noteId));
      }
      exportJsonList.add(response);
    }
    return new JsonResponse<>(Status.OK, "", exportJsonList).build();
  }


  /**
   * Import new note REST API.
   * TODO(zjffdu) support to import jupyter note.
   *
   * @param noteJson - note Json
   * @return JSON with new note ID
   * @throws IOException
   */
  @POST
  @Path("import")
  @ZeppelinApi
  public Response importNote(@QueryParam("notePath") String notePath, String noteJson) throws IOException, ParseException {
    JSONParser jsonParser = new JSONParser();
    JSONObject noteJsonObject = (JSONObject) jsonParser.parse(noteJson);
    String noteId;
    // Checking whether the notebook data is from a Jupyter or a Zeppelin Notebook.
    // Jupyter notebooks have paragraphs under the "cells" field.
    if ( noteJsonObject.get("cells") != null) {
      if(isDatabricksNote(noteJson)){
        String noteLanguage = getNoteLanguageFromJson(noteJson);
        noteJson = new JupyterUtil().getJson(
                GSON.toJson(noteJsonObject), IdHashes.generateId(), "", "%md");
        noteId = notebookService.importNote(notePath,noteLanguage, noteJson, getServiceContext(),
                new RestServiceCallback<>());
      }else{
        String codeReplace = "%python";
        if (!zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_PYTHON_ADD_INTERPRETER)) {
          codeReplace = "";
        }
        //if note is not from databricks then add %python to every paragraph
        noteJson = new JupyterUtil().getJson(
                GSON.toJson(noteJsonObject), IdHashes.generateId(), codeReplace, "%md");
        noteId = notebookService.importNote(notePath, noteJson, getServiceContext(),
                new RestServiceCallback<>());
      }
    }else{
      noteId = notebookService.importNote(notePath, noteJson, getServiceContext(),
              new RestServiceCallback<>());
    }

    return new JsonResponse<>(Status.OK, "", noteId).build();
  }

  @POST
  @Path("import/multiple")
  @ZeppelinApi
  public Response importNoteMultipleNote(String message) throws IOException, ParseException {
    UploadNotebookRequest uploadNotebookRequest = GSON.fromJson(message, UploadNotebookRequest.class);
    List<Map<String, String>> failNoteList = new ArrayList<>();
    List<Map<String, String>> successNoteList = new ArrayList<>();
    NotebookMultipleNoteExportResponse response = new NotebookMultipleNoteExportResponse();
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    for (NotebookJsonPath notebookJsonPath: uploadNotebookRequest.getNotebookJsonPathList()) {
      Map<String, String> failList = new HashMap<>();
      Map<String, String> successList = new HashMap<>();
      String noteJson = notebookJsonPath.getNoteJson();
      String notePath = notebookJsonPath.getPath();
      Note oldNote = Note.fromJson(null, noteJson);
      //check name exit
      boolean isNameExits = notesInfo.stream().anyMatch(note -> note.getNoteName().equals(oldNote.getName()));
      if (isNameExits) {
        failList.put("name", oldNote.getName());
        failList.put("id", oldNote.getId());
        failList.put("reason", "Notebook name: " + oldNote.getName() + " already exits in Environment");
        failNoteList.add(failList);
        continue;
      }
      if (uploadNotebookRequest.isSkipExitId()) {
        boolean isNoteExits = notesInfo.stream().anyMatch(note -> note.getId().equals(oldNote.getId()));
        if (isNoteExits) {
          failList.put("name", oldNote.getName());
          failList.put("id", oldNote.getId());
          failList.put("reason", "Notebook id: " + oldNote.getId() + " already exits in Environment");
          failNoteList.add(failList);
          continue;
        }
      }
      try {
        JSONParser jsonParser = new JSONParser();
        JSONObject noteJsonObject = (JSONObject) jsonParser.parse(noteJson);
        String noteId;
        // Checking whether the notebook data is from a Jupyter or a Zeppelin Notebook.
        // Jupyter notebooks have paragraphs under the "cells" field.
        if ( noteJsonObject.get("cells") != null) {
          if(isDatabricksNote(noteJson)){
            String noteLanguage = getNoteLanguageFromJson(noteJson);
            noteJson = new JupyterUtil().getJson(
                    GSON.toJson(noteJsonObject), IdHashes.generateId(), "", "%md");
            noteId = notebookService.importNote(notePath,noteLanguage, noteJson, getServiceContext(),
                    new RestServiceCallback<>());
          }else{
            String codeReplace = "%python";
            if (!zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_PYTHON_ADD_INTERPRETER)) {
              codeReplace = "";
            }
            //if note is not from databricks then add %python to every paragraph
            noteJson = new JupyterUtil().getJson(
                    GSON.toJson(noteJsonObject), IdHashes.generateId(), codeReplace, "%md");
            noteId = notebookService.importNote(notePath, noteJson, getServiceContext(),
                    new RestServiceCallback<>());
          }
        }else{
          noteId = notebookService.importNote(notePath, noteJson, getServiceContext(),
                  new RestServiceCallback<>());
        }
        successList.put("name", notePath);
        successList.put("id", noteId);
        successNoteList.add(successList);
      }
      catch (Exception e){
        LOGGER.error(e.getMessage(), e);
        failList.put("name", oldNote.getName());
        failList.put("id", oldNote.getId());
        failList.put("reason", e.getMessage());
        failNoteList.add(failList);
      }
    }
    response.setFailNoteList(failNoteList);
    response.setSuccessNoteList(successNoteList);
    return new JsonResponse<>(Status.OK, "", response).build();
  }

  @POST
  @Path("import/exit")
  @ZeppelinApi
  public Response isExitNoteFromJson(String noteJson) throws IOException, ParseException {
    JSONParser jsonParser = new JSONParser();
    JSONObject noteJsonObject = (JSONObject) jsonParser.parse(noteJson);
    if ( noteJsonObject.get("cells") != null) {
      if(isDatabricksNote(noteJson)) {
        noteJson = new JupyterUtil().getJson(
                GSON.toJson(noteJsonObject), IdHashes.generateId(), "", "%md");
      }else {
        String codeReplace = "%python";
        if (!zConf.getBoolean(ZeppelinConfiguration.ConfVars.ZEPPELIN_PYTHON_ADD_INTERPRETER)) {
          codeReplace = "";
        }
        //if note is not from databricks then add %python to every paragraph
        noteJson = new JupyterUtil().getJson(
                GSON.toJson(noteJsonObject), IdHashes.generateId(), codeReplace, "%md");
      }
    }
    Note oldNote = Note.fromJson(null, noteJson);
    String oldNoteId = oldNote.getId();
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    boolean isExits = notesInfo.stream().anyMatch(note -> note.getId().equals(oldNoteId));
    return new JsonResponse<>(Status.OK, "", isExits).build();
  }

  /**
   * Create new note REST API with note json.
   *
   * @param message - JSON with new note name
   * @return JSON with new note ID
   * @throws IOException
   */
  @POST
  @ZeppelinApi
  public Response createNote(String message) throws IOException {
    String user = authenticationService.getPrincipal();
    LOGGER.info("Creating new note by JSON {}", message);
    NewNoteRequest request = GSON.fromJson(message, NewNoteRequest.class);
    String defaultInterpreterGroup = request.getDefaultInterpreterGroup();
    if (StringUtils.isBlank(defaultInterpreterGroup)) {
      defaultInterpreterGroup = zConf.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_INTERPRETER_GROUP_DEFAULT);
    }
    String noteId = notebookService.createNote(
            request.getName(),
            defaultInterpreterGroup,
            request.getAddingEmptyParagraph(),
            getServiceContext(),
            new RestServiceCallback<>());
    return notebook.processNote(noteId,
      note -> {
        AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
        if (request.getParagraphs() != null) {
          for (NewParagraphRequest paragraphRequest : request.getParagraphs()) {
            Paragraph p = note.addNewParagraph(subject);
            initParagraph(p, paragraphRequest, user);
          }
        }
        return new JsonResponse<>(Status.OK, "", note.getId()).build();
      });
  }

  @POST
  @Path("/upload/replace")
  @ZeppelinApi
  public Response uploadFile(@QueryParam("fileName") String fileName, String noteJson) throws Exception {
      JSONParser jsonParser = new JSONParser();
      String filePath = zeppelinConfig.getNotebookDir() + "/" + fileName;
//      JSONObject noteJsonObject = (JSONObject) jsonParser.parse(noteJson);

      try (FileWriter file = new FileWriter(filePath)) {
        file.write(noteJson);
      } catch (IOException e) {
        throw new ForbiddenException(e.getMessage());
      }
      return new JsonResponse<>(Status.OK, "", "").build();
    }


  /**
   * Delete note REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   */
  @DELETE
  @Path("{noteId}")
  @ZeppelinApi
  public Response deleteNote(@PathParam("noteId") String noteId) throws Exception {
    LOGGER.info("Delete note {} ", noteId);

    String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
            + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
    ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
    validateRequest.setNotebookId(noteId);
    validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
    validateRequest.setUserName(UserContext.getUsername());
    ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
            .postForEntity(url, validateRequest, String.class);
    if (response.getStatusCodeValue() != 200) {
      throw new IllegalAccessException("Validate user error");
    }

    notebookService.removeNote(noteId,
            getServiceContext(),
            new RestServiceCallback<String>() {
              @Override
              public void onSuccess(String message, ServiceContext context) {
                notebookServer.broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
              }
            });

    return new JsonResponse<>(Status.OK, "").build();
  }

  /**
   * Clone note REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   * @throws CloneNotSupportedException
   * @throws IllegalArgumentException
   */
  @POST
  @Path("{noteId}")
  @ZeppelinApi
  public Response cloneNote(@PathParam("noteId") String noteId, String message)
      throws IOException, IllegalArgumentException {

    LOGGER.info("Clone note by JSON {}", message);
    checkIfUserCanWrite(noteId, "Insufficient privileges you cannot clone this note");
    NewNoteRequest request = GSON.fromJson(message, NewNoteRequest.class);
    String newNoteName = null;
    String revisionId = null;
    if (request != null) {
      newNoteName = request.getName();
      revisionId = request.getRevisionId();
    }
    AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
    String newNoteId = notebookService.cloneNote(noteId, revisionId, newNoteName, getServiceContext(),
            new RestServiceCallback<Note>() {
              @Override
              public void onSuccess(Note newNote, ServiceContext context) throws IOException {
                notebookServer.broadcastNote(newNote);
                notebookServer.broadcastNoteList(subject, context.getUserAndRoles());
              }
            });
    return new JsonResponse<>(Status.OK, "", newNoteId).build();
  }

  @POST
  @Path("/clone/parameter")
  @ZeppelinApi
  public Response cloneNotebookAndAddParameter(String message) throws Exception {
    LOGGER.info("Clone note by JSON {}", message);
//        checkIfUserCanWrite(noteId, "Insufficient privileges you cannot clone this note");
    CloneNotebookRequest request = GSON.fromJson(message, CloneNotebookRequest.class);
//    CloneNotebookRequest request = CloneNotebookRequest.fromJson(message);
    String newNoteName = null;
    String revisionId = null;
    String noteId = null;
    String language = null;
    Map<String, Map<String, String>> parameters = null;
    Map<String, String> newInterpreterGroupName = null;
    if (request != null) {
      newNoteName = request.getName();
      revisionId = request.getRevisionId();
      noteId = request.getNoteId();
      parameters = request.getAddParameterMap();
      language = request.getLanguage();
      newInterpreterGroupName = request.getSpecificInterpreterGroupName();
    }
    AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
    String newNoteId = notebookService.cloneNoteWithParameter(noteId, revisionId, newNoteName, parameters, newInterpreterGroupName, language, getServiceContext(),
            new RestServiceCallback<Note>() {
              @Override
              public void onSuccess(Note newNote, ServiceContext context) throws IOException {
                notebookServer.broadcastNote(newNote);
                notebookServer.broadcastNoteList(subject, context.getUserAndRoles());
              }
            });
    return new JsonResponse<>(Status.OK, "", newNoteId).build();
  }

  /**
   * Rename note REST API
   *
   * @param message - JSON containing new name
   * @return JSON with status.OK
   * @throws IOException
   */
  @PUT
  @Path("{noteId}/rename")
  @ZeppelinApi
  public Response renameNote(@PathParam("noteId") String noteId,
                             String message) throws IOException {

    LOGGER.info("Rename note by JSON {}", message);
    RenameNoteRequest request = GSON.fromJson(message, RenameNoteRequest.class);
    String newName = request.getName();
    if (newName.isEmpty()) {
      LOGGER.warn("Trying to rename notebook {} with empty name parameter", noteId);
      throw new BadRequestException("name can not be empty");
    }
    notebookService.renameNote(noteId, request.getName(), false, getServiceContext(),false,null,null,
            new RestServiceCallback<Note>() {
              @Override
              public void onSuccess(Note note, ServiceContext context) throws IOException {
                notebookServer.broadcastNote(note);
                notebookServer.broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
              }
            });
    return new JsonResponse<>(Status.OK, "").build();
  }

  /**
   * Insert paragraph REST API.
   *
   * @param message - JSON containing paragraph's information
   * @return JSON with status.OK
   * @throws IOException
   */
  @POST
  @Path("{noteId}/paragraph")
  @ZeppelinApi
  public Response insertParagraph(@PathParam("noteId") String noteId, String message)
      throws IOException {

    String user = authenticationService.getPrincipal();
    LOGGER.info("Insert paragraph {} {}", noteId, message);
    AuthenticationInfo subject = new AuthenticationInfo(user);
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanWrite(noteId, "Insufficient privileges you cannot add paragraph to this note");
        NewParagraphRequest request = GSON.fromJson(message, NewParagraphRequest.class);
        Paragraph p;
        Double indexDouble = request.getIndex();
        if (indexDouble == null) {
          p = note.addNewParagraph(subject);
        } else {
          p = note.insertNewParagraph(indexDouble.intValue(), subject);
        }
        initParagraph(p, request, user);
        notebook.saveNote(note, subject);
        notebookServer.broadcastNote(note);
        return new JsonResponse<>(Status.OK, "", p.getId()).build();
      });
  }

  /**
   * Get paragraph REST API.
   *
   * @param noteId ID of Note
   * @return JSON with information of the paragraph
   * @throws IOException
   */
  @GET
  @Path("{noteId}/paragraph/{paragraphId}")
  @ZeppelinApi
  public Response getParagraph(@PathParam("noteId") String noteId,
                               @PathParam("paragraphId") String paragraphId) throws IOException {

    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRead(noteId, "Insufficient privileges you cannot get this paragraph");
        Paragraph p = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(p, paragraphId);
        return new JsonResponse<>(Status.OK, "", p).build();
      });
  }

  /**
   * Update paragraph. Only update title and text is supported.
   *
   * @param message json containing the "text" and optionally the "title" of the paragraph, e.g.
   *                {"text" : "updated text", "title" : "Updated title" }
   */
  @PUT
  @Path("{noteId}/paragraph/{paragraphId}")
  @ZeppelinApi
  public Response updateParagraph(@PathParam("noteId") String noteId,
                                  @PathParam("paragraphId") String paragraphId,
                                  String message) throws IOException {

    String user = authenticationService.getPrincipal();
    LOGGER.info("{} will update paragraph {} {}", user, noteId, paragraphId);
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanWrite(noteId, "Insufficient privileges you cannot update this paragraph");
        Paragraph p = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(p, paragraphId);

        UpdateParagraphRequest updatedParagraph = GSON.fromJson(message, UpdateParagraphRequest.class);
        p.setText(updatedParagraph.getText());

        if (updatedParagraph.getTitle() != null) {
          p.setTitle(updatedParagraph.getTitle());
        }

        AuthenticationInfo subject = new AuthenticationInfo(user);
        notebook.saveNote(note, subject);
        notebookServer.broadcastParagraph(note, p, MSG_ID_NOT_DEFINED);
        return new JsonResponse<>(Status.OK, "").build();
      });
  }

  /**
   * Update paragraph config rest api.
   *
   * @param noteId
   * @param paragraphId
   * @param message
   * @return
   * @throws IOException
   */
  @PUT
  @Path("{noteId}/paragraph/{paragraphId}/config")
  @ZeppelinApi
  public Response updateParagraphConfig(@PathParam("noteId") String noteId,
                                        @PathParam("paragraphId") String paragraphId,
                                        String message) throws IOException {

    String user = authenticationService.getPrincipal();
    LOGGER.info("{} will update paragraph config {} {}", user, noteId, paragraphId);
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanWrite(noteId, "Insufficient privileges you cannot update this paragraph config");
        Paragraph p = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(p, paragraphId);

        Map<String, Object> newConfig = GSON.fromJson(message, HashMap.class);
        configureParagraph(p, newConfig, user);
        AuthenticationInfo subject = new AuthenticationInfo(user);
        notebook.saveNote(note, subject);
        return new JsonResponse<>(Status.OK, "", p).build();
      });
  }

  /**
   * Move paragraph REST API.
   *
   * @param newIndex - new index to move
   * @return JSON with status.OK
   * @throws IOException
   */
  @POST
  @Path("{noteId}/paragraph/{paragraphId}/move/{newIndex}")
  @ZeppelinApi
  public Response moveParagraph(@PathParam("noteId") String noteId,
                                @PathParam("paragraphId") String paragraphId,
                                @PathParam("newIndex") String newIndex)
      throws IOException {

    LOGGER.info("Move paragraph {} {} {}", noteId, paragraphId, newIndex);
    notebookService.moveParagraph(noteId, paragraphId, Integer.parseInt(newIndex),
            getServiceContext(),
            new RestServiceCallback<Paragraph>() {
              @Override
              public void onSuccess(Paragraph result, ServiceContext context) throws IOException {
                notebookServer.broadcastNote(result.getNote());
              }
            });
    return new JsonResponse<>(Status.OK, "").build();
  }

  /**
   * Delete paragraph REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   */
  @DELETE
  @Path("{noteId}/paragraph/{paragraphId}")
  @ZeppelinApi
  public Response deleteParagraph(@PathParam("noteId") String noteId,
                                  @PathParam("paragraphId") String paragraphId) throws Exception {

    LOGGER.info("Delete paragraph {} {}", noteId, paragraphId);

    String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
            + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
    ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
    validateRequest.setNotebookId(noteId);
    validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
    validateRequest.setUserName(UserContext.getUsername());
    ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
            .postForEntity(url, validateRequest, String.class);
    if (response.getStatusCodeValue() != 200) {
      throw new IllegalAccessException("Validate user error");
    }

    notebookService.removeParagraph(noteId, paragraphId, getServiceContext(),
            new RestServiceCallback<Paragraph>() {
              @Override
              public void onSuccess(Paragraph p, ServiceContext context) throws IOException {
                notebookServer.broadcastNote(p.getNote());
              }
            });

    return new JsonResponse<>(Status.OK, "").build();
  }

  @POST
  @Path("{noteId}/paragraph/next")
  public Response nextSessionParagraph(@PathParam("noteId") String noteId,
                                       @QueryParam("maxParagraph") int maxParagraph) throws IOException {

    String paragraphId = notebookService.getNextSessionParagraphId(noteId, maxParagraph,
            getServiceContext(),
            new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK, paragraphId).build();
  }

  /**
   * Clear result of all paragraphs REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.ok
   */
  @PUT
  @Path("{noteId}/clear")
  @ZeppelinApi
  public Response clearAllParagraphOutput(@PathParam("noteId") String noteId)
      throws IOException {
    LOGGER.info("Clear all paragraph output of note {}", noteId);
    notebookService.clearAllParagraphOutput(noteId, getServiceContext(),
            new RestServiceCallback<>());
    return new JsonResponse<>(Status.OK, "").build();
  }

  /**
   * Run note jobs REST API.
   *
   * @param noteId ID of Note
   * @param blocking blocking until jobs are done
   * @param isolated use isolated interpreter for running this note
   * @param message any parameters passed to note
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @POST
  @Path("job/{noteId}")
  @ZeppelinApi
  public Response runNoteJobs(@PathParam("noteId") String noteId,
                              @DefaultValue("false") @QueryParam("blocking") boolean blocking,
                              @DefaultValue("false") @QueryParam("isolated") boolean isolated,
                              String message)
      throws Exception, IllegalArgumentException {

    Map<String, Object> params = new HashMap<>();
    Map<String, String> newInterpreterGroupName = new HashMap<>();
    if (UserContext.getUsername() == null || UserContext.getToken() == null) {
      throw new IllegalArgumentException("User can not be null");
    }
    String runBy = UserContext.getUsername();

    if (!StringUtils.isEmpty(message)) {
      ParametersRequest request = GSON.fromJson(message, ParametersRequest.class);
      if (request.getParams() != null) {
        params.putAll(request.getParams());
      }
      if (!ObjectUtil.isNullOrEmpty(request.getSpecificInterpreterGroupName())) {
        newInterpreterGroupName.putAll(request.getSpecificInterpreterGroupName());
      }
      runBy = request.getRunBy() != null ? request.getRunBy(): runBy;
    }


    BDEHeraServices bdeHeraServices = new BDEHeraServices();
    bdeHeraServices.callLicenseValidate();

    LOGGER.info("Run note jobs, noteId: {}, blocking: {}, isolated: {}, params: {}", noteId, blocking, isolated, params);
    String finalRunBy = runBy;
    return notebook.processNote(noteId,
      note -> {
        String name = note.getName();
        //check format of parallel notebook
        String patternString = "parallel_([A-Za-z0-9~_\\-]+)_index:(\\d+)_ref:([A-Za-z0-9]+)";
        Pattern pattern = Pattern.compile(patternString);
        if (!pattern.matcher(name).find()) {
          try {
            LOGGER.info("Validate Note");
            String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR) + ":" + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
                    + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
            ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
            validateRequest.setNotebookId(noteId);
            validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
            validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
            validateRequest.setUserName(UserContext.getUsername());
            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
                    .postForEntity(url, validateRequest, String.class);
            if (response.getStatusCodeValue() != 200) {
              LOGGER.error("Validate user error");
              throw new IllegalAccessException("Validate user error");
            }
          } catch (Exception e) {
            return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to run note").build();
          }
        }
        AuthenticationInfo subject = new AuthenticationInfo(authenticationService.getPrincipal());
        subject.setRoles(authenticationService.getAssociatedRoles());
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRun(noteId, "Insufficient privileges you cannot run job for this note");
        //TODO(zjffdu), can we run a note via rest api when cron is enabled ?
        try {
          note.runAll(subject, blocking, isolated, finalRunBy,false, params, newInterpreterGroupName);
          return new JsonResponse<>(Status.OK).build();
        } catch (Exception e) {
          return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to run note").build();
        }
      });

  }

  /**
   * Stop(delete) note jobs REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @DELETE
  @Path("job/{noteId}")
  @ZeppelinApi
  public Response stopNoteJobs(@PathParam("noteId") String noteId)
      throws IOException, IllegalArgumentException {

    LOGGER.info("Stop note jobs {} ", noteId);
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRun(noteId, "Insufficient privileges you cannot stop this job for this note");
        for (Paragraph p : note.getParagraphs()) {
          if (!p.isTerminated()) {
            p.abort();
          }
        }
        return new JsonResponse<>(Status.OK).build();
      });
  }

  /**
   * Get note job status REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @GET
  @Path("job/{noteId}")
  @ZeppelinApi
  public Response getNoteJobStatus(@PathParam("noteId") String noteId)
      throws IOException, IllegalArgumentException {

    LOGGER.info("Get note job status.");
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRead(noteId, "Insufficient privileges you cannot get job status");
        return new JsonResponse<>(Status.OK, null, new NoteJobStatus(note)).build();
      });
  }

  /**
   * Get note paragraph job status REST API.
   *
   * @param noteId      ID of Note
   * @param paragraphId ID of Paragraph
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @GET
  @Path("job/{noteId}/{paragraphId}")
  @ZeppelinApi
  public Response getNoteParagraphJobStatus(@PathParam("noteId") String noteId,
                                            @PathParam("paragraphId") String paragraphId)
      throws IOException, IllegalArgumentException {

    LOGGER.info("Get note paragraph job status.");
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRead(noteId, "Insufficient privileges you cannot get job status");

        Paragraph paragraph = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(paragraph, paragraphId);
        return new JsonResponse<>(Status.OK, null, new ParagraphJobStatus(paragraph)).build();
      });
  }

  /**
   * Run asynchronously paragraph job REST API.
   *
   * @param message - JSON with params if user wants to update dynamic form's value
   *                null, empty string, empty json if user doesn't want to update
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @POST
  @Path("job/{noteId}/{paragraphId}")
  @ZeppelinApi
  public Response runParagraph(@PathParam("noteId") String noteId,
                               @PathParam("paragraphId") String paragraphId,
                               @QueryParam("sessionId") String sessionId,
                               String message)
      throws Exception {

    LOGGER.info("Run paragraph job asynchronously {} {} {}", noteId, paragraphId, message);

    String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
            + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
    ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
    validateRequest.setNotebookId(noteId);
    validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
    validateRequest.setUserName(UserContext.getUsername());
    ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
            .postForEntity(url, validateRequest, String.class);
    if (response.getStatusCodeValue() != 200) {
      throw new IllegalAccessException("Validate user error");
    }
    String runBy = UserContext.getUsername();

    Map<String, Object> params = new HashMap<>();
    if (!StringUtils.isEmpty(message)) {
      ParametersRequest request = GSON.fromJson(message, ParametersRequest.class);
      if (request.getParams() != null) {
        params.putAll(request.getParams());
      }
      runBy = request.getRunBy() != null ? request.getRunBy(): runBy;
    }

    BDEHeraServices bdeHeraServices = new BDEHeraServices();
    Map<String, Object> finalParams = params;
    String finalRunBy = runBy;
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        Paragraph paragraph = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(paragraph, paragraphId);


        String name = note.getName();
        //check format of parallel notebook
        String id = noteId;
        String patternString = "parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)";
        Pattern pattern = Pattern.compile(patternString);
        if (pattern.matcher(name).find()) {
          id = name.split("_")[1];
        }
        String scriptText = null;
        scriptText = bdeHeraServices.addTokenToHeraApiText(paragraph.getText(),null, id, finalRunBy, noteId);
        notebookService.runParagraph(note, paragraphId, paragraph.getTitle(),
                paragraph.getText(), scriptText, finalParams, new HashMap<>(), sessionId, finalRunBy,
                false, false, getServiceContext(), new RestServiceCallback<>());
        return new JsonResponse<>(Status.OK).build();
      });
  }

  /**
   * Run synchronously a paragraph REST API.
   *
   * @param noteId      - noteId
   * @param paragraphId - paragraphId
   * @param message     - JSON with params if user wants to update dynamic form's value
   *                    null, empty string, empty json if user doesn't want to update
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @POST
  @Path("run/{noteId}/{paragraphId}")
  @ZeppelinApi
  public Response runParagraphSynchronously(@PathParam("noteId") String noteId,
                                            @PathParam("paragraphId") String paragraphId,
                                            @QueryParam("sessionId") String sessionId,
                                            String message)
      throws Exception {
    LOGGER.info("Run paragraph synchronously {} {} {}", noteId, paragraphId, message);

    String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
            + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
    ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
    validateRequest.setNotebookId(noteId);
    validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
    validateRequest.setUserName(UserContext.getUsername());
    ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
            .postForEntity(url, validateRequest, String.class);
    if (response.getStatusCodeValue() != 200) {
      throw new IllegalAccessException("Validate user error");
    }

    BDEHeraServices bdeHeraServices = new BDEHeraServices();
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        Paragraph paragraph = note.getParagraph(paragraphId);
        checkIfParagraphIsNotNull(paragraph, paragraphId);

        Map<String, Object> params = new HashMap<>();
        if (!StringUtils.isEmpty(message)) {
          ParametersRequest request = GSON.fromJson(message, ParametersRequest.class);
          if (request.getParams() != null) {
            params.putAll(request.getParams());
          }
        }
        String name = note.getName();
        //check format of parallel notebook
        String id = noteId;
        String patternString = "parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)";
        Pattern pattern = Pattern.compile(patternString);
        if (pattern.matcher(name).find()) {
          id = name.split("_")[1];
        }
        String scriptText = null;
          scriptText = bdeHeraServices.addTokenToHeraApiText(paragraph.getText(),null, id, UserContext.getUsername(), noteId);
        if (notebookService.runParagraph(note, paragraphId, paragraph.getTitle(),
                paragraph.getText(), scriptText, params,
                new HashMap<>(), sessionId, UserContext.getUsername(),false, true, getServiceContext(), new RestServiceCallback<>())) {
          return notebookService.getNote(noteId, getServiceContext(), new RestServiceCallback<>(),
            noteRun -> {
              Paragraph p = noteRun.getParagraph(paragraphId);
              InterpreterResult result = p.getReturn();
              return new JsonResponse<>(Status.OK, result).build();
            });
        } else {
          return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to run paragraph").build();
        }
      });
  }

  /**
   * Stop(delete) paragraph job REST API.
   *
   * @param noteId      ID of Note
   * @param paragraphId ID of Paragraph
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @DELETE
  @Path("job/{noteId}/{paragraphId}")
  @ZeppelinApi
  public Response cancelParagraph(@PathParam("noteId") String noteId,
                                  @PathParam("paragraphId") String paragraphId)
      throws IOException, IllegalArgumentException {
    LOGGER.info("stop paragraph job {} ", noteId);
    notebookService.cancelParagraph(noteId, paragraphId, getServiceContext(),
            new RestServiceCallback<Paragraph>());
    return new JsonResponse<>(Status.OK).build();
  }

  /**
   * Register cron job REST API.
   *
   * @param message - JSON with cron expressions.
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @POST
  @Path("cron/{noteId}")
  @ZeppelinApi
  public Response registerCronJob(@PathParam("noteId") String noteId, String message)
          throws Exception {

    LOGGER.info("Register cron job note={} request cron msg={}", noteId, message);

    CronRequest request = GSON.fromJson(message, CronRequest.class);

    // use write lock, because config is overwritten
    return notebook.processNote(noteId,
      note -> {
        String name = note.getName();
        //check format of parallel notebook
        String patternString = "parallel_([A-Za-z0-9~_\\-]+)_index:(\\d+)_ref:([A-Za-z0-9]+)";
        Pattern pattern = Pattern.compile(patternString);
        if (!pattern.matcher(name).find()) {
          try {
            LOGGER.info("Validate Note");
            String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR) + ":" + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
                    + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
            ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
            validateRequest.setNotebookId(noteId);
            validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
            validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
            validateRequest.setUserName(UserContext.getUsername());
            ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
                    .postForEntity(url, validateRequest, String.class);
            if (response.getStatusCodeValue() != 200) {
              LOGGER.error("Validate user error");
              throw new IllegalAccessException("Validate user error");
            }
          } catch (Exception e) {
            return new JsonResponse<>(Status.INTERNAL_SERVER_ERROR, "Fail to run note").build();
          }
        }
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRun(noteId, "Insufficient privileges you cannot set a cron job for this note");
        checkIfNoteSupportsCron(note);

        if (!CronExpression.isValidExpression(request.getCronString())) {
          return new JsonResponse<>(Status.BAD_REQUEST, "wrong cron expressions.").build();
        }

        Map<String, Object> config = note.getConfig();
        config.put("cron", request.getCronString());
        config.put("releaseresource", request.getReleaseResource());
        config.put("activatedBy", request.getRunBy() != null ? request.getRunBy() : UserContext.getUsername());
        note.setConfig(config);
        schedulerService.refreshCron(note.getId());

        return new JsonResponse<>(Status.OK).build();
      });
  }

  /**
   * Remove cron job REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @DELETE
  @Path("cron/{noteId}")
  @ZeppelinApi
  public Response removeCronJob(@PathParam("noteId") String noteId)
      throws Exception {

    LOGGER.info("Remove cron job note {}", noteId);

    String url = zConf.getString(ZeppelinConfiguration.ConfVars.HERA_ADDR)+":"+zConf.getString(ZeppelinConfiguration.ConfVars.HERA_PORT)
            + zConf.getString(ZeppelinConfiguration.ConfVars.HERA_AUTH_NOTEBOOK_URL);
    ValidateUserNotebookRequest validateRequest = new ValidateUserNotebookRequest();
    validateRequest.setNotebookId(noteId);
    validateRequest.setTenantId(zeppelinConfig.getLong(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_TENANT_ID));
    validateRequest.setModuleNotebookName(zeppelinConfig.getString(ZeppelinConfiguration.ConfVars.ZEPPELIN_APP_NAME));
    validateRequest.setUserName(UserContext.getUsername());
    ResponseEntity<String> response = RestTemplateFactoryUtil.getRestTemplar(UserContext.getToken(), true)
            .postForEntity(url, validateRequest, String.class);
    if (response.getStatusCodeValue() != 200) {
      throw new IllegalAccessException("Validate user error");
    }

    // use write lock because config is overwritten
    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserIsOwner(noteId,
                "Insufficient privileges you cannot remove this cron job from this note");
        checkIfNoteSupportsCron(note);

        Map<String, Object> config = note.getConfig();
        config.remove("cron");
        config.remove("releaseresource");
        config.remove("activatedBy");
        note.setConfig(config);
        schedulerService.refreshCron(note.getId());

        return new JsonResponse<>(Status.OK).build();
      });
  }

  /**
   * Get cron job REST API.
   *
   * @param noteId ID of Note
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @GET
  @Path("cron/{noteId}")
  @ZeppelinApi
  public Response getCronJob(@PathParam("noteId") String noteId)
      throws IOException, IllegalArgumentException {

    LOGGER.info("Get cron job note {}", noteId);

    return notebook.processNote(noteId,
      note -> {
        checkIfNoteIsNotNull(note, noteId);
        checkIfUserCanRead(noteId, "Insufficient privileges you cannot get cron information");
        checkIfNoteSupportsCron(note);
        Map<String, Object> response = new HashMap<>();
        response.put("cron", note.getConfig().get("cron"));
        response.put("releaseResource", note.getConfig().get("releaseresource"));
        if (note.getConfig().get("activatedBy") != null) {
          response.put("activatedBy", note.getConfig().get("activatedBy"));
        }
        return new JsonResponse<>(Status.OK, response).build();
      });
  }

  /**
   * Get note jobs for job manager.
   *
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @GET
  @Path("jobmanager/")
  @ZeppelinApi
  public Response getJobListforNote() throws IOException, IllegalArgumentException {
    LOGGER.info("Get note jobs for job manager");
    List<JobManagerService.NoteJobInfo> noteJobs = jobManagerService
            .getNoteJobInfoByUnixTime(0, getServiceContext(), new RestServiceCallback<>());
    Map<String, Object> response = new HashMap<>();
    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", noteJobs);
    return new JsonResponse<>(Status.OK, response).build();
  }

  /**
   * Get updated note jobs for job manager
   * <p>
   * Return the `Note` change information within the post unix timestamp.
   *
   * @return JSON with status.OK
   * @throws IOException
   * @throws IllegalArgumentException
   */
  @GET
  @Path("jobmanager/{lastUpdateUnixtime}/")
  @ZeppelinApi
  public Response getUpdatedJobListforNote(@PathParam("lastUpdateUnixtime") long lastUpdateUnixTime)
      throws IOException, IllegalArgumentException {
    LOGGER.info("Get updated note jobs lastUpdateTime {}", lastUpdateUnixTime);
    List<JobManagerService.NoteJobInfo> noteJobs =
            jobManagerService.getNoteJobInfoByUnixTime(lastUpdateUnixTime, getServiceContext(),
                    new RestServiceCallback<>());
    Map<String, Object> response = new HashMap<>();
    response.put("lastResponseUnixTime", System.currentTimeMillis());
    response.put("jobs", noteJobs);
    return new JsonResponse<>(Status.OK, response).build();
  }

  /**
   * Search for a Notes with permissions.
   */
  @GET
  @Path("search")
  @ZeppelinApi
  public Response search(@QueryParam("q") String queryTerm) {
    LOGGER.info("Searching notes for: {}", queryTerm);
    String principal = authenticationService.getPrincipal();
    Set<String> roles = authenticationService.getAssociatedRoles();
    HashSet<String> userAndRoles = new HashSet<>();
    userAndRoles.add(principal);
    userAndRoles.addAll(roles);
    List<Map<String, String>> notesFound = noteSearchService.query(queryTerm);
    for (int i = 0; i < notesFound.size(); i++) {
      String[] ids = notesFound.get(i).get("id").split("/", 2);
      String noteId = ids[0];
      if (!authorizationService.isOwner(noteId, userAndRoles) &&
              !authorizationService.isReader(noteId, userAndRoles) &&
              !authorizationService.isWriter(noteId, userAndRoles) &&
              !authorizationService.isRunner(noteId, userAndRoles)) {
        notesFound.remove(i);
        i--;
      }
    }
    LOGGER.info("{} notes found", notesFound.size());
    return new JsonResponse<>(Status.OK, notesFound).build();
  }


  private void initParagraph(Paragraph p, NewParagraphRequest request, String user) {
    LOGGER.info("Init Paragraph for user {}", user);
    checkIfParagraphIsNotNull(p, "");
    p.setTitle(request.getTitle());
    p.setText(request.getText());
    Map<String, Object> config = request.getConfig();
    if (config != null && !config.isEmpty()) {
      configureParagraph(p, config, user);
    }
  }

  private void configureParagraph(Paragraph p, Map<String, Object> newConfig, String user) {
    LOGGER.info("Configure Paragraph for user {}", user);
    if (newConfig == null || newConfig.isEmpty()) {
      LOGGER.warn("{} is trying to update paragraph {} of note {} with empty config",
          user, p.getId(), p.getNote().getId());
      throw new BadRequestException("paragraph config cannot be empty");
    }
    Map<String, Object> origConfig = p.getConfig();
    for (final Map.Entry<String, Object> entry : newConfig.entrySet()) {
      origConfig.put(entry.getKey(), entry.getValue());
    }

    p.setConfig(origConfig);
  }

  private boolean isDatabricksNote(String noteJson){
    JsonParser jsonParser = new JsonParser();
    JsonObject noteObject = (JsonObject) jsonParser.parse(noteJson);
    JsonObject metadataObj = (JsonObject) noteObject.get("metadata");
    return metadataObj.has(zConf.getString(ZeppelinConfiguration.ConfVars.IPYNB_LANGUAGE_PARENT_KEY));
  }
//  TODO : change this function to find note language without specify key name
  private String getNoteLanguageFromJson(String noteJson){
    String language;
    JsonParser jsonParser = new JsonParser();
    JsonObject noteObject = (JsonObject) jsonParser.parse(noteJson);
    JsonObject metadataObj = (JsonObject) noteObject.get("metadata");
    JsonObject languageParentObj = (JsonObject) metadataObj.get(zConf.getString(ZeppelinConfiguration.ConfVars.IPYNB_LANGUAGE_PARENT_KEY));
    language = String.valueOf(languageParentObj.get("language"));
    language = language.substring(1,language.length()-1);
    LOGGER.info("Note language : {}",language);
    return language;
  }

  @DELETE
  @Path("public/day/{retentionDay}")
  public Response deletePublicNotebooksOverRetentionDay(@PathParam("retentionDay") int retentionDay) throws IOException, InterruptedException {
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    String patternString = "parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)";
    Pattern pattern = Pattern.compile(patternString);
    List<String> publicNoteIdList = new ArrayList<>();
    int totalError = 0;
    for (NoteInfo noteInfo : notesInfo) {
      if (pattern.matcher(noteInfo.getNoteName()).find()) {
        publicNoteIdList.add(noteInfo.getId());
      }
    }
    for (String noteId: publicNoteIdList) {
      String startTime = notebook.processNote(noteId,
              note -> {
          if (note == null) {
            LOGGER.error("No such note: " + noteId);
            return null;
          }
          if (!ObjectUtil.isNullOrEmpty(note.getStartTime())) {
            return note.getStartTime();
          }
          else if (note.getParagraphCount() != 0){
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            return  formatter.format(note.getLastParagraph().getDateCreated());
          }
          return null;
          });
      if (!ObjectUtil.isNullOrEmpty(startTime)) {
        try {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
          LocalDate initialDate = LocalDate.parse(startTime.split("_")[0], formatter);
          LocalDate retentionDate = initialDate.plusDays(retentionDay);
          LocalDate today = LocalDate.now();
          if (today.isAfter(retentionDate)) {
            LOGGER.info("Delete note id: " + noteId);
            notebookService.removeNote(noteId,
                    getServiceContext(),
                    new RestServiceCallback<String>() {
                      @Override
                      public void onSuccess(String message, ServiceContext context) {
                        notebookServer.broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
                      }
                    });
          }
        } catch (Exception e) {
          LOGGER.error(e.getMessage(), e);
          if (totalError < 60) {
            Thread.sleep(1000);
            totalError += 1;
          } else {
            throw new IOException(e.getMessage(), e);
          }
        }
      }
      else {
        LOGGER.info("Delete note id: " + noteId);
        notebookService.removeNote(noteId,
                getServiceContext(),
                new RestServiceCallback<String>() {
                  @Override
                  public void onSuccess(String message, ServiceContext context) {
                    notebookServer.broadcastNoteList(context.getAutheInfo(), context.getUserAndRoles());
                  }
                });
      }
    }
    return new JsonResponse<>(Status.OK, "").build();
  }

  @POST
  @Path("/sync/git")
  public Response syncNotebookFromGit(String message) throws Exception {
    PullNoteRequest request = GSON.fromJson(message, PullNoteRequest.class);
    NotebookGitService service = new NotebookGitService();
    if (zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      service.pullGit(request);
    }
    clearCatch();
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    Pattern pattern = Pattern.compile("parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)");
    notesInfo.removeIf(noteInfo -> pattern.matcher(noteInfo.getPath()).find());
    return new JsonResponse<>(Status.OK, "", notesInfo).build();
  }


    @POST
    @Path("/backup")
    public Response backupNotebook(String message) throws Exception {
      DirectoryCopy directoryService = new DirectoryCopy();
      directoryService.backupNotebook();

      return new JsonResponse<>(Status.OK, "", null).build();
    }

    @GET
    @Path("/git")
    public Response isEnableGit() throws Exception {
      if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
        return new JsonResponse<>(Status.OK, "not enable", null).build();
      }
      return new JsonResponse<>(Status.OK, "enable", null).build();
    }

    @GET
    @Path("name/{noteName}")
    @ZeppelinApi
    public Response getNoteNameIdByName(@PathParam("noteName") String noteName) throws IOException {
      return notebookService.getNoteByPath("/" + noteName, true,  getServiceContext(), new RestServiceCallback<>(),
              note -> new JsonResponse<>(Status.OK, "", note.getId()).build());
    }
    @GET
    @Path("/git/branch")
    public Response getBranch() throws Exception {
      if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
        return new JsonResponse<>(Status.OK, "", null).build();
      }
      NotebookGitService service = new NotebookGitService();
      return new JsonResponse<>(Status.OK, "", service.getBranch()).build();
    }

  @GET
  @Path("/git/branch/all")
  public Response getAllBranch() throws Exception {
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", null).build();
    }
    NotebookGitService service = new NotebookGitService();
    return new JsonResponse<>(Status.OK, "", service.getBranchList()).build();
  }

  @GET
  @Path("/git/change")
  public Response getChange() throws Exception {
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", new HashMap<>()).build();
    }
    NotebookGitService service = new NotebookGitService();
    Map<String, List<String>> response = service.getChange();
    return new JsonResponse<>(Status.OK, "", response).build();
  }

  @POST
  @Path("/git/branch/create")
  public Response createBranch(String message) throws Exception {
    CreateBranchRequest request = GSON.fromJson(message, CreateBranchRequest.class);
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", null).build();
    }
    NotebookGitService service = new NotebookGitService();
    service.createBranch(request);
    return new JsonResponse<>(Status.OK, "", "success").build();
  }

  @DELETE
  @Path("/git/branch/delete/{branchName}")
  public Response deleteBranch(@PathParam("branchName") String branchName) throws Exception {
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", null).build();
    }
    NotebookGitService service = new NotebookGitService();
    service.deleteBranch(branchName);
    return new JsonResponse<>(Status.OK, "", "success").build();
  }

  @POST
  @Path("/git/branch/checkout")
  public Response checkoutBranch(String message) throws Exception {
    CheckoutNotebookRequest request = GSON.fromJson(message, CheckoutNotebookRequest.class);
    NotebookGitService service = new NotebookGitService();
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", null).build();
    }
    service.checkoutBranch(request.getName(), request.isHardCheckout());
    clearCatch();
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false, getServiceContext(),
            new RestServiceCallback<>());
    Pattern pattern = Pattern.compile("parallel_([A-Za-z0-9]+)_index:(\\d+)_ref:([A-Za-z0-9]+)");
    notesInfo.removeIf(noteInfo -> pattern.matcher(noteInfo.getPath()).find());
    return new JsonResponse<>(Status.OK, "", notesInfo).build();
  }

  @POST
  @Path("/git/push")
  public Response pushNote(String message) throws Exception {
    PushNoteRequest request = GSON.fromJson(message, PushNoteRequest.class);
    NotebookGitService service = new NotebookGitService();
    if (!zeppelinConfig.getNotebookStorageClass().equals("org.apache.zeppelin.notebook.repo.GitHubNotebookRepo")) {
      return new JsonResponse<>(Status.OK, "", null).build();
    }
    String username = UserContext.getUsername();
    request.setMessage(username + ": " + request.getMessage());
    List<String> notebookFilePath = new ArrayList<>();
    List<NoteInfo> notesInfo = notebookService.listNotesInfo(false,getServiceContext(),
            new RestServiceCallback<>());
    for (NoteInfo noteInfo: notesInfo) {
      if (request.getNoteIds().contains(noteInfo.getId())) {
        String noteFilePath = (noteInfo.getPath() + "_" + noteInfo.getId() + ".zpln").substring(1);
        notebookFilePath.add(noteFilePath);
      }
    }
    service.pushGit(request.getMessage(), notebookFilePath);
    return new JsonResponse<>(Status.OK, "", "success").build();
  }
}
