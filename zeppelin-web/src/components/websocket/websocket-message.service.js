/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

angular.module('zeppelinWebApp').service('websocketMsgSrv', WebsocketMessageService);

function WebsocketMessageService($rootScope, websocketEvents, $window, $routeParams) {
  'ngInject';

  return {
    getBlendataURL: function() {
      websocketEvents.sendNewEvent({op: 'GET_BLENDATA_URL'});
    },

    validateBDEToken: function(noteId) {
      checkExitingAndSetLocalStorageTokens($window, $routeParams);

      let sessionId = $window.localStorage.getItem('sessionId');
      let token = $window.localStorage.getItem(noteId);
      if(token.startsWith('public-')) {
        let bdePublicToken = token.replace(new RegExp('public-'), '');
        websocketEvents.sendNewEvent({
          op: 'VALIDATE_BDE_TOKEN', data: {id: noteId, bdePublicToken: bdePublicToken, sessionId: sessionId}});
      } else {
        let bdeToken = $window.localStorage.getItem('bdeToken');
        websocketEvents.sendNewEvent({
          op: 'VALIDATE_BDE_TOKEN', data: {id: noteId, bdeToken: bdeToken, sessionId: sessionId}});
      }
    },

    getHomeNote: function() {
      websocketEvents.sendNewEvent({op: 'GET_HOME_NOTE'});
    },

    createNotebook: function(noteName, defaultInterpreterGroup) {
      websocketEvents.sendNewEvent({
        op: 'NEW_NOTE',
        data: {
          name: noteName,
          defaultInterpreterGroup: defaultInterpreterGroup,
        },
      });
    },

    moveNoteToTrash: function(noteId) {
      websocketEvents.sendNewEvent({op: 'MOVE_NOTE_TO_TRASH', data: {id: noteId}});
    },

    moveFolderToTrash: function(folderPath) {
      websocketEvents.sendNewEvent({op: 'MOVE_FOLDER_TO_TRASH', data: {id: folderPath}});
    },

    restoreNote: function(noteId) {
      websocketEvents.sendNewEvent({op: 'RESTORE_NOTE', data: {id: noteId}});
    },

    restoreFolder: function(folderPath) {
      websocketEvents.sendNewEvent({op: 'RESTORE_FOLDER', data: {id: folderPath}});
    },

    restoreAll: function() {
      websocketEvents.sendNewEvent({op: 'RESTORE_ALL'});
    },

    deleteNote: function(noteId) {
      websocketEvents.sendNewEvent({op: 'DEL_NOTE', data: {id: noteId}});
    },

    removeFolder: function(folderPath) {
      websocketEvents.sendNewEvent({op: 'REMOVE_FOLDER', data: {id: folderPath}});
    },

    emptyTrash: function() {
      websocketEvents.sendNewEvent({op: 'EMPTY_TRASH'});
    },

    cloneNote: function(noteIdToClone, newNoteName) {
      websocketEvents.sendNewEvent({op: 'CLONE_NOTE', data: {id: noteIdToClone, name: newNoteName}});
    },

    getNoteList: function() {
      websocketEvents.sendNewEvent({op: 'LIST_NOTES'});
    },

    reloadAllNotesFromRepo: function() {
      websocketEvents.sendNewEvent({op: 'RELOAD_NOTES_FROM_REPO'});
    },

    getNote: function(noteId) {
      websocketEvents.sendNewEvent({op: 'GET_NOTE', data: {id: noteId}});
    },

    reloadNote: function(noteId) {
      websocketEvents.sendNewEvent({op: 'RELOAD_NOTE', data: {id: noteId}});
    },

    updateNote: function(noteId, noteName, noteConfig) {
      noteConfig['activatedBy'] = $window.localStorage.getItem('user');
      websocketEvents.sendNewEvent({op: 'NOTE_UPDATE', data: {id: noteId, name: noteName, config: noteConfig}});
    },

    updatePersonalizedMode: function(noteId, modeValue) {
      websocketEvents.sendNewEvent({op: 'UPDATE_PERSONALIZED_MODE', data: {id: noteId, personalized: modeValue}});
    },

    renameNote: function(noteId, noteName, relative) {
      let bdeToken = $window.localStorage.getItem('bdeToken');
      let sessionId = $window.localStorage.getItem('sessionId');
      websocketEvents.sendNewEvent({op: 'NOTE_RENAME',
        data: {id: noteId, name: noteName, relative: relative, bdeToken: bdeToken, sessionId: sessionId}});
    },

    renameFolder: function(folderId, folderPath) {
      websocketEvents.sendNewEvent({op: 'FOLDER_RENAME',
        data: {id: folderId, name: folderPath}});
    },

    moveParagraph: function(paragraphId, newIndex) {
      websocketEvents.sendNewEvent({op: 'MOVE_PARAGRAPH',
        data: {id: paragraphId, index: newIndex}});
    },

    insertParagraph: function(newIndex) {
      websocketEvents.sendNewEvent({op: 'INSERT_PARAGRAPH',
        data: {index: newIndex}});
    },

    copyParagraph: function(newIndex, paragraphTitle, paragraphData,
                            paragraphConfig, paragraphParams) {
      websocketEvents.sendNewEvent({
        op: 'COPY_PARAGRAPH',
        data: {
          index: newIndex,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams,
        },
      });
    },

    updateAngularObject: function(noteId, paragraphId, name, value, interpreterGroupId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_UPDATED',
        data: {
          noteId: noteId,
          paragraphId: paragraphId,
          name: name,
          value: value,
          interpreterGroupId: interpreterGroupId,
        },
      });
    },

    clientBindAngularObject: function(noteId, name, value, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_BIND',
        data: {
          noteId: noteId,
          name: name,
          value: value,
          paragraphId: paragraphId,
        },
      });
    },

    clientUnbindAngularObject: function(noteId, name, paragraphId) {
      websocketEvents.sendNewEvent({
        op: 'ANGULAR_OBJECT_CLIENT_UNBIND',
        data: {
          noteId: noteId,
          name: name,
          paragraphId: paragraphId,
        },
      });
    },

    cancelParagraphRun: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'CANCEL_PARAGRAPH', data: {id: paragraphId}});
    },

    paragraphExecutedBySpell: function(paragraphId, paragraphTitle,
                                        paragraphText, paragraphResultsMsg,
                                        paragraphStatus, paragraphErrorMessage,
                                        paragraphConfig, paragraphParams,
                                        paragraphDateStarted, paragraphDateFinished) {
      websocketEvents.sendNewEvent({
        op: 'PARAGRAPH_EXECUTED_BY_SPELL',
        data: {
          id: paragraphId,
          title: paragraphTitle,
          paragraph: paragraphText,
          results: {
            code: paragraphStatus,
            msg: paragraphResultsMsg.map((dataWithType) => {
              let serializedData = dataWithType.data;
              return {type: dataWithType.type, data: serializedData};
            }),
          },
          status: paragraphStatus,
          errorMessage: paragraphErrorMessage,
          config: paragraphConfig,
          params: paragraphParams,
          dateStarted: paragraphDateStarted,
          dateFinished: paragraphDateFinished,
        },
      });
    },

    runParagraph: function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams) {
      // short circuit update paragraph status for immediate visual feedback without waiting for server response
      $rootScope.$broadcast('updateStatus', {
        id: paragraphId,
        status: 'PENDING',
      });
      checkExitingAndSetLocalStorageTokens($window, $routeParams);

      let bdeToken = $window.localStorage.getItem('bdeToken');
      let sessionId = $window.localStorage.getItem('sessionId');
      // send message to server
      websocketEvents.sendNewEvent({
        op: 'RUN_PARAGRAPH',
        data: {
          id: paragraphId,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams,
          bdeToken: bdeToken,
          sessionId: sessionId,
          user: $window.localStorage.getItem('user'),
        },
      });
    },

    runAllParagraphs: function(noteId, paragraphs) {
      // short circuit update paragraph status for immediate visual feedback without waiting for server response
      paragraphs.forEach((p) => {
        $rootScope.$broadcast('updateStatus', {
          id: p.id,
          status: 'PENDING',
        });
      });
      checkExitingAndSetLocalStorageTokens($window, $routeParams);
      let bdeToken = $window.localStorage.getItem('bdeToken');
      let sessionId = $window.localStorage.getItem('sessionId');
      // send message to server
      websocketEvents.sendNewEvent({
        op: 'RUN_ALL_PARAGRAPHS',
        data: {
          noteId: noteId,
          paragraphs: JSON.stringify(paragraphs),
          bdeToken: bdeToken,
          sessionId: sessionId,
          user: $window.localStorage.getItem('user')
        },
      });
    },

    removeParagraph: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_REMOVE', data: {id: paragraphId}});
    },

    clearParagraphOutput: function(paragraphId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_OUTPUT', data: {id: paragraphId}});
    },

    clearAllParagraphOutput: function(noteId) {
      websocketEvents.sendNewEvent({op: 'PARAGRAPH_CLEAR_ALL_OUTPUT', data: {id: noteId}});
    },

    completion: function(paragraphId, buf, cursor) {
      websocketEvents.sendNewEvent({
        op: 'COMPLETION',
        data: {
          id: paragraphId,
          buf: buf,
          cursor: cursor,
        },
      });
    },

    commitParagraph: function(paragraphId, paragraphTitle, paragraphData, paragraphConfig, paragraphParams, noteId) {
      return websocketEvents.sendNewEvent({
        op: 'COMMIT_PARAGRAPH',
        data: {
          id: paragraphId,
          noteId: noteId,
          title: paragraphTitle,
          paragraph: paragraphData,
          config: paragraphConfig,
          params: paragraphParams,
        },
      });
    },

    patchParagraph: function(paragraphId, noteId, patch) {
      // javascript add "," if change contains several patches
      // but java library requires patch list without ","
      patch = patch.replace(/,@@/g, '@@');
      return websocketEvents.sendNewEvent({
        op: 'PATCH_PARAGRAPH',
        data: {
          id: paragraphId,
          noteId: noteId,
          patch: patch,
        },
      });
    },

    importNote: function(note) {
      websocketEvents.sendNewEvent({
        op: 'IMPORT_NOTE',
        data: {
          note: note,
        },
      });
    },

    convertNote: function(noteId, noteName) {
      websocketEvents.sendNewEvent({
        op: 'CONVERT_NOTE_NBFORMAT',
        data: {
          noteId: noteId,
          noteName: noteName,
        },
      });
    },

    checkpointNote: function(noteId, commitMessage) {
      websocketEvents.sendNewEvent({
        op: 'CHECKPOINT_NOTE',
        data: {
          noteId: noteId,
          commitMessage: commitMessage,
          user: $window.localStorage.getItem('user'),
        },
      });
    },

    setNoteRevision: function(noteId, revisionId) {
      websocketEvents.sendNewEvent({
        op: 'SET_NOTE_REVISION',
        data: {
          noteId: noteId,
          revisionId: revisionId,
        },
      });
    },

    listRevisionHistory: function(noteId) {
      websocketEvents.sendNewEvent({
        op: 'LIST_REVISION_HISTORY',
        data: {
          noteId: noteId,
        },
      });
    },

    getNoteByRevision: function(noteId, revisionId) {
      websocketEvents.sendNewEvent({
        op: 'NOTE_REVISION',
        data: {
          noteId: noteId,
          revisionId: revisionId,
        },
      });
    },

    getNoteByRevisionForCompare: function(noteId, revisionId, position) {
      websocketEvents.sendNewEvent({
        op: 'NOTE_REVISION_FOR_COMPARE',
        data: {
          noteId: noteId,
          revisionId: revisionId,
          position: position,
        },
      });
    },

    getEditorSetting: function(paragraphId, pararaphText) {
      websocketEvents.sendNewEvent({
        op: 'EDITOR_SETTING',
        data: {
          paragraphId: paragraphId,
          paragraphText: pararaphText,
        },
      });
    },

    isConnected: function() {
      return websocketEvents.isConnected();
    },

    getJobs: function() {
      websocketEvents.sendNewEvent({op: 'LIST_NOTE_JOBS'});
    },

    disconnectJobEvent: function() {
      websocketEvents.sendNewEvent({op: 'UNSUBSCRIBE_UPDATE_NOTE_JOBS'});
    },

    getUpdateNoteJobsList: function(lastUpdateServerUnixTime) {
      websocketEvents.sendNewEvent(
        {op: 'LIST_UPDATE_NOTE_JOBS', data: {lastUpdateUnixTime: lastUpdateServerUnixTime * 1}}
      );
    },

    getInterpreterBindings: function(noteId) {
      websocketEvents.sendNewEvent({op: 'GET_INTERPRETER_BINDINGS', data: {noteId: noteId}});
    },

    saveInterpreterBindings: function(noteId, selectedSettingIds) {
      websocketEvents.sendNewEvent({op: 'SAVE_INTERPRETER_BINDINGS',
        data: {noteId: noteId, selectedSettingIds: selectedSettingIds}});
    },

    listConfigurations: function() {
      websocketEvents.sendNewEvent({op: 'LIST_CONFIGURATIONS'});
    },

    getInterpreterSettings: function() {
      websocketEvents.sendNewEvent({op: 'GET_INTERPRETER_SETTINGS'});
    },

    saveNoteForms: function(note) {
      websocketEvents.sendNewEvent({op: 'SAVE_NOTE_FORMS',
        data: {
          noteId: note.id,
          noteParams: note.noteParams,
        },
      });
    },

    removeNoteForms: function(note, formName) {
      websocketEvents.sendNewEvent({op: 'REMOVE_NOTE_FORMS',
        data: {
          noteId: note.id,
          formName: formName,
        },
      });
    },

  };

  function checkExitingAndSetLocalStorageTokens($window, $routeParams) {
    if ($window.localStorage.getItem('sessionId') === undefined || $window.localStorage.getItem('sessionId') === null) {
      // $window.localStorage.setItem('sessionId', generateUUID());
      console.error('sessionId in local storage lost');
    }

    if ($routeParams.pubNToken !== undefined ) {
      $window.localStorage.setItem($routeParams.noteId, 'public-' + $routeParams.pubNToken);
    } else if ($routeParams.nbToken !== undefined) {
      $window.localStorage.setItem('bdeToken', $routeParams.nbToken);
      $window.localStorage.setItem($routeParams.noteId, $routeParams.nbToken);
    }
  }

  // eslint-disable-next-line no-unused-vars
  function generateUUID() {
    let d = new Date().getTime();
    let uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      // let r = Math.floor((d + Math.random() * 16) % 16);
      // d = Math.floor(d/16);
      // return (c === 'x' ? r : (r&0x3|0x8)).toString(16);
      let r = Math.floor((d + Math.random() * 16) % 16);
      d = Math.floor(d / 16);
      return (c === 'x' ? r : (r % 4 + 8)).toString(16);
    });
    return uuid;
  }
}
