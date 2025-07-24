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

angular.module('zeppelinWebApp').factory('websocketEvents', WebsocketEventFactory);

function WebsocketEventFactory($rootScope, $websocket, $location, baseUrlSrv, saveAsService, ngToast, $routeParams) {
  'ngInject';

  let websocketCalls = {};
  let pingIntervalId;
  const uniqueClientId = Math.random().toString(36).substring(2, 7);
  let lastMsgIdSeqSent = 0;

  websocketCalls.ws = $websocket(baseUrlSrv.getWebsocketUrl());
  websocketCalls.ws.reconnectIfNotNormalClose = true;

  websocketCalls.ws.onOpen(function() {
    console.log('Websocket created');
    $rootScope.$broadcast('setConnectedStatus', true);
    pingIntervalId = setInterval(function() {
      websocketCalls.sendNewEvent({op: 'PING'});
    }, 10000);
  });

  websocketCalls.sendNewEvent = function(data) {
    if ($rootScope.ticket !== undefined) {
      data.principal = $rootScope.ticket.principal;
      data.ticket = $rootScope.ticket.ticket;
      data.roles = $rootScope.ticket.roles;
    } else {
      data.principal = '';
      data.ticket = '';
      data.roles = '';
    }

    data.msgId = uniqueClientId + '-' + ++lastMsgIdSeqSent;
    console.log('Send >> %o, %o, %o, %o, %o', data.op, data.principal, data.ticket, data.roles, data);
    return websocketCalls.ws.send(JSON.stringify(data));
  };

  websocketCalls.isConnected = function() {
    return (websocketCalls.ws.socket.readyState === 1);
  };

  websocketCalls.ws.onMessage(function(event) {
    let payload;
    if (event.data) {
      payload = angular.fromJson(event.data);
    }

    console.log('Receive << %o, %o', payload.op, payload);

    let op = payload.op;
    let data = payload.data;
    let msgId = payload.msgId;
    const uniqueClientId = msgId ? msgId.split('-')[0] : undefined;
    const msgIdSeqReceived = msgId ? parseInt(msgId.split('-')[1]) : undefined;
    const isResponseForRequestFromThisClient = uniqueClientId === uniqueClientId;

    if (op === 'NOTE' && !$rootScope.isReconnectTriggered) {
      $rootScope.$broadcast('setNoteContent', data.note);
    } else if (op === 'NEW_NOTE') {
      $location.path('/notebook/' + data.note.id);
    } else if (op === 'NOTES_INFO') {
      $rootScope.$broadcast('setNoteMenu', data.notes);
    } else if (op === 'NOTE_RUNNING_STATUS') {
      $rootScope.$broadcast('noteRunningStatus', data.status);
    } else if (op === 'LIST_NOTE_JOBS') {
      $rootScope.$emit('jobmanager:set-jobs', data.noteJobs);
    } else if (op === 'LIST_UPDATE_NOTE_JOBS') {
      $rootScope.$emit('jobmanager:update-jobs', data.noteRunningJobs);
    } else if (op === 'AUTH_INFO') {
      let btn = [];
      if ($rootScope.ticket.roles === '[]') {
        btn = [{
          label: 'Close',
          action: function(dialog) {
            dialog.close();
          },
        }];
      } else {
        btn = [{
          label: 'Login',
          action: function(dialog) {
            dialog.close();
            angular.element('#loginModal').modal({
              show: 'true',
            });
          },
        }, {
          label: 'Cancel',
          action: function(dialog) {
            dialog.close();
            // using $rootScope.apply to trigger angular digest cycle
            // changing $location.path inside bootstrap modal wont trigger digest
            $rootScope.$apply(function() {
              $location.path('/');
            });
          },
        }];
      }

      BootstrapDialog.show({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: 'Insufficient privileges',
        message: _.escape(data.info.toString()),
        buttons: btn,
      });
    } else if (op === 'PARAGRAPH') {
      if (isResponseForRequestFromThisClient &&
          lastMsgIdSeqSent > msgIdSeqReceived
      ) {
        // paragraph is already updated by short circuit.
        console.log('PARAPGRAPH is already updated by shortcircuit');
      } else {
        $rootScope.$broadcast('updateParagraph', data);
      }
    } else if (op === 'PATCH_PARAGRAPH') {
      $rootScope.$broadcast('patchReceived', data);
    } else if (op === 'COLLABORATIVE_MODE_STATUS') {
      $rootScope.$broadcast('collaborativeModeStatus', data);
    } else if (op === 'RUN_PARAGRAPH_USING_SPELL') {
      $rootScope.$broadcast('runParagraphUsingSpell', data);
    } else if (op === 'PARAGRAPH_APPEND_OUTPUT') {
      $rootScope.$broadcast('appendParagraphOutput', data);
    } else if (op === 'PARAGRAPH_UPDATE_OUTPUT') {
      $rootScope.$broadcast('updateParagraphOutput', data);
    } else if (op === 'PROGRESS') {
      $rootScope.$broadcast('updateProgress', data);
    } else if (op === 'COMPLETION_LIST') {
      $rootScope.$broadcast('completionList', data);
    } else if (op === 'EDITOR_SETTING') {
      $rootScope.$broadcast('editorSetting', data);
    } else if (op === 'ANGULAR_OBJECT_UPDATE') {
      $rootScope.$broadcast('angularObjectUpdate', data);
    } else if (op === 'ANGULAR_OBJECT_REMOVE') {
      $rootScope.$broadcast('angularObjectRemove', data);
    } else if (op === 'APP_APPEND_OUTPUT') {
      $rootScope.$broadcast('appendAppOutput', data);
    } else if (op === 'APP_UPDATE_OUTPUT') {
      $rootScope.$broadcast('updateAppOutput', data);
    } else if (op === 'APP_LOAD') {
      $rootScope.$broadcast('appLoad', data);
    } else if (op === 'APP_STATUS_CHANGE') {
      $rootScope.$broadcast('appStatusChange', data);
    } else if (op === 'LIST_REVISION_HISTORY') {
      $rootScope.$broadcast('listRevisionHistory', data);
    } else if (op === 'NOTE_REVISION') {
      $rootScope.$broadcast('noteRevision', data);
    } else if (op === 'NOTE_REVISION_FOR_COMPARE') {
      $rootScope.$broadcast('noteRevisionForCompare', data);
    } else if (op === 'INTERPRETER_BINDINGS') {
      $rootScope.$broadcast('interpreterBindings', data);
    } else if (op === 'SAVE_NOTE_FORMS') {
      $rootScope.$broadcast('saveNoteForms', data);
    } else if (op === 'ERROR_INFO') {
      let msg = _.escape(data.info.toString());
      let subject = msg.substr(0, msg.indexOf(':'));
      let description = msg.substr(msg.indexOf(': ')+1);
      BootstrapDialog.showEx({
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: '',
        confirmButtonText: 'Close',
        message:
          `
          <img style="border: 4px solid red;border-radius: 30px;padding: 8px;" src="assets/images/dialog/error.svg"/>
          <h3>${subject}</h3>
          <div class="desc-dialog">${description}</div>`,
        // buttons: [{
        //   // close all the dialogs when there are error on running all paragraphs
        //   label: 'Close',
        //   action: function() {
        //     BootstrapDialog.closeAll();
        //   },
        // }],
        callback: function(result) {
          if (result) {
            console.log('close all');
            BootstrapDialog.closeAll();
          }
        },
      });
    } else if (op === 'SESSION_LOGOUT') {
      $rootScope.$broadcast('session_logout', data);
    } else if (op === 'CONFIGURATIONS_INFO') {
      const config = data.configurations || {};
      $rootScope.reconnectConfig = {
        maxAttempts: parseInt(config['zeppelin.websocket.total.retry'], 10) || 0,
        interval: parseInt(config['zeppelin.websocket.retry.time.sleep'], 10) * 1000 || 2000,
      };
      $rootScope.$broadcast('configurationsInfo', data);
    } else if (op === 'INTERPRETER_SETTINGS') {
      $rootScope.$broadcast('interpreterSettings', data);
    } else if (op === 'PARAGRAPH_ADDED') {
      $rootScope.$broadcast('addParagraph', data.paragraph, data.index);
    } else if (op === 'PARAGRAPH_REMOVED') {
      $rootScope.$broadcast('removeParagraph', data.id);
    } else if (op === 'PARAGRAPH_MOVED') {
      $rootScope.$broadcast('moveParagraph', data.id, data.index);
    } else if (op === 'NOTE_UPDATED') {
      $rootScope.$broadcast('updateNote', data.name, data.config, data.info);
    } else if (op === 'SET_NOTE_REVISION') {
      $rootScope.$broadcast('setNoteRevisionResult', data);
    } else if (op === 'PARAS_INFO') {
      $rootScope.$broadcast('updateParaInfos', data);
    } else if (op === 'CONVERTED_NOTE_NBFORMAT') {
      saveAsService.saveAs(data.nbformat, data.noteName, '.ipynb');
    } else if (op === 'INTERPRETER_INSTALL_STARTED') {
      ngToast.info(data.message);
    } else if (op === 'INTERPRETER_INSTALL_RESULT') {
      ngToast.info(data.message);
    } else if (op === 'NOTICE') {
      ngToast.info(data.notice);
    } else if (op === 'GET_BLENDATA_URL') {
      $rootScope.$broadcast('getBlendataURL', data.result);
    } else if (op === 'VALIDATE_BDE_TOKEN') {
      //data.result.status = 0;
      // data.result.permission = 'viewer';
      $rootScope.statusToken = data.result.status;
      $rootScope.redirectURL = data.result.redirectURL;
      $rootScope.permission = data.result.permission;
      if(data.result && data.result.status !== 'undefined' && data.result.status === 1 && $rootScope.getNote) {
        BootstrapDialog.expired({
          closable: true,
          title: '',
          message: `
            <img src="assets/images/dialog/session-expired.svg"/>
            <h3>Session Expired</h3>
            <div class="desc-dialog">Your session ended. Notebook autosaves your work.\nYou will be
            <span style="font-weight: bold;">‘Redirected to the Login page’</span>.</div>`,
          callback: function(result) {
            window.location.href = data.result.redirectURL;
          },
        });
      } else if(data.result && data.result.status !== 'undefined' && data.result.status === 2 && $rootScope.getNote) {
        BootstrapDialog.expired({
          closable: true,
          title: '',
          message: `
            <img src="assets/images/dialog/session-invalid.svg"/>
            <h3>Token or Session Invalid</h3>
            <div class="desc-dialog">
              Please go back to the <span style="font-weight: bold;">‘Blendata Enterprise’</span> to start over.
            </div>`,
          callback: function(result) {
            window.location.href = data.result.redirectURL;
          },
        });
      }
      $rootScope.$broadcast('validateBDEToken', data.result);
    } else {
      console.error(`unknown websocket op: ${op}`);
    }
  });

  websocketCalls.ws.onError(function(event) {
    console.log('error message: ', event);
    $rootScope.$broadcast('setConnectedStatus', false);
  });

  websocketCalls.ws.onClose(function(event) {
    console.log('close message: ', event);
    if (pingIntervalId !== undefined) {
      clearInterval(pingIntervalId);
      pingIntervalId = undefined;
    }
    $rootScope.$broadcast('setConnectedStatus', false);

    if (event.currentTarget.extensions === 'permessage-deflate') {
      attemptReconnect(); // เรียกฟังก์ชัน reconnect
    }
  });

  // websocketCalls.ws.onClose(function(event) {
  //   console.log('close message: ', event);
  //   if (pingIntervalId !== undefined) {
  //     clearInterval(pingIntervalId);
  //     pingIntervalId = undefined;
  //   }
  //   $rootScope.$broadcast('setConnectedStatus', false);
  //
  //   if (event.currentTarget.extensions === 'permessage-deflate') {//extensions for session timeout only
  //     window.location.reload();
  //
  //     BootstrapDialog.show({
  //       closable: false,
  //       closeByBackdrop: false,
  //       closeByKeyboard: false,
  //       title: '',
  //       message: `<img src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNzYiIGhlaWdodD0iNzYiIHZpZXdCb3g9IjAgMCA3NiA3NiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3QgeD0iMiIgeT0iMiIgd2lkdGg9IjcyIiBoZWlnaHQ9IjcyIiByeD0iMzYiIHN0cm9rZT0iI0Y3OEUxRSIgc3Ryb2tlLXdpZHRoPSI0Ii8+CjxwYXRoIGQ9Ik00MC4xNjYgMTkuMDMxMkwzOS44MzU5IDQ1LjU2NDVIMzUuNTk1N0wzNS4yNDAyIDE5LjAzMTJINDAuMTY2Wk0zNS4wODc5IDUzLjYzODdDMzUuMDg3OSA1Mi44NzcgMzUuMzE2NCA1Mi4yMzM3IDM1Ljc3MzQgNTEuNzA5QzM2LjI0NzQgNTEuMTg0MiAzNi45NDE0IDUwLjkyMTkgMzcuODU1NSA1MC45MjE5QzM4Ljc1MjYgNTAuOTIxOSAzOS40MzgyIDUxLjE4NDIgMzkuOTEyMSA1MS43MDlDNDAuNDAzIDUyLjIzMzcgNDAuNjQ4NCA1Mi44NzcgNDAuNjQ4NCA1My42Mzg3QzQwLjY0ODQgNTQuMzY2NSA0MC40MDMgNTQuOTkyOCAzOS45MTIxIDU1LjUxNzZDMzkuNDM4MiA1Ni4wNDIzIDM4Ljc1MjYgNTYuMzA0NyAzNy44NTU1IDU2LjMwNDdDMzYuOTQxNCA1Ni4zMDQ3IDM2LjI0NzQgNTYuMDQyMyAzNS43NzM0IDU1LjUxNzZDMzUuMzE2NCA1NC45OTI4IDM1LjA4NzkgNTQuMzY2NSAzNS4wODc5IDUzLjYzODdaIiBmaWxsPSIjRjc4RTFFIi8+Cjwvc3ZnPgo="/>
  //       <h3>Session Timeout</h3><div class="desc-dialog">Your session has been inactive for too long and has timed out. Please refresh the page to continue.</div>`,//use base64 for fixed can't display image on server has stop.
  //       buttons: [{
  //         label: 'Dismiss',
  //         cssClass: 'btn-cancel',
  //         action: function(dialog) {
  //           dialog.close();
  //           $rootScope.$broadcast('noteRunningStatus', true);//for readonly
  //         },
  //       },
  //       {
  //         label: 'Refresh',
  //         cssClass: 'btn-primary',
  //         action: function(dialog) {
  //           dialog.close();
  //           window.location.reload();
  //         },
  //       }],
  //     });
  //   }
  // });

  function attemptReconnect() {
    const config = $rootScope.reconnectConfig || {};
    let attemptCount = 0;
    let maxAttempts = config.maxAttempts;
    let interval = config.interval;
    let reconnectSucceeded = false;
    let reconnectIntervalId;
    let reconnectSuccessDialog = null;

    if (maxAttempts === 0) {
      console.warn('Reconnect is disabled (maxAttempts = 0)');
      handleReconnectFailure();
      return;
    }

    if(maxAttempts > 0) {
      // แจ้งเตือนเมื่อ socket ปิด และ มีจำนวนรอบที่สามารถลอง reconnect ได้
      toastr.warning('Connection Lost! We\'re working on getting you reconnected.');
    }

    function handleReconnectSuccess() {
      reconnectSucceeded = true;

      clearInterval(reconnectIntervalId);
      $rootScope.isReconnectTriggered = true;
      console.log(' WebSocket reconnected successfully');

      const noteId = $routeParams.noteId;
      if (noteId) {
        websocketCalls.sendNewEvent({
          op: 'GET_NOTE',
          data: {id: noteId},
        });
      }

      BootstrapDialog.closeAll();

      reconnectSuccessDialog = BootstrapDialog.show({
        className: 'bootstrap-reconnect-disconnect-dialog',
        closable: false,
        closeByBackdrop: false,
        closeByKeyboard: false,
        title: '',
        message: `<img src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNzYiIGhlaWdodD0iNzYiIHZpZXdCb3g9IjAgMCA3NiA3NiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3QgeD0iMiIgeT0iMiIgd2lkdGg9IjcyIiBoZWlnaHQ9IjcyIiByeD0iMzYiIHN0cm9rZT0iI0Y3OEUxRSIgc3Ryb2tlLXdpZHRoPSI0Ii8+CjxwYXRoIGQ9Ik00MC4xNjYgMTkuMDMxMkwzOS44MzU5IDQ1LjU2NDVIMzUuNTk1N0wzNS4yNDAyIDE5LjAzMTJINDAuMTY2Wk0zNS4wODc5IDUzLjYzODdDMzUuMDg3OSA1Mi44NzcgMzUuMzE2NCA1Mi4yMzM3IDM1Ljc3MzQgNTEuNzA5QzM2LjI0NzQgNTEuMTg0MiAzNi45NDE0IDUwLjkyMTkgMzcuODU1NSA1MC45MjE5QzM4Ljc1MjYgNTAuOTIxOSAzOS40MzgyIDUxLjE4NDIgMzkuOTEyMSA1MS43MDlDNDAuNDAzIDUyLjIzMzcgNDAuNjQ4NCA1Mi44NzcgNDAuNjQ4NCA1My42Mzg3QzQwLjY0ODQgNTQuMzY2NSA0MC40MDMgNTQuOTkyOCAzOS45MTIxIDU1LjUxNzZDMzkuNDM4MiA1Ni4wNDIzIDM4Ljc1MjYgNTYuMzA0NyAzNy44NTU1IDU2LjMwNDdDMzYuOTQxNCA1Ni4zMDQ3IDM2LjI0NzQgNTYuMDQyMyAzNS43NzM0IDU1LjUxNzZDMzUuMzE2NCA1NC45OTI4IDM1LjA4NzkgNTQuMzY2NSAzNS4wODc5IDUzLjYzODdaIiBmaWxsPSIjRjc4RTFFIi8+Cjwvc3ZnPgo="/>
              <h3>You're Back Online</h3>
              <div class="desc-dialog">The WebSocket connection dropped for a moment,
              but you're reconnected now.
              Please run paragraphs to keep your code.</div>`,
        buttons: [
          {
            label: 'Close',
            cssClass: 'btn-cancel',
            action: function(dialog) {
              dialog.close();
            },
          },
        ],
      });

      // รอ DOM ถูกแสดงก่อนแล้วค่อยเพิ่ม class
      setTimeout(() => {
        const dialogEl = document.querySelector('.bootstrap-dialog');
        if (dialogEl) {
          dialogEl.classList.add('bootstrap-reconnect-disconnect-dialog');
        }
      }, 0);

    }

    function handleReconnectFailure() {
      if (!reconnectSucceeded) {
        console.warn('WebSocket reconnect failed after ${maxAttempts}');

        BootstrapDialog.closeAll();

        BootstrapDialog.show({
          closable: false,
          closeByBackdrop: false,
          closeByKeyboard: false,
          title: '',
          message: `<img src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNzYiIGhlaWdodD0iNzYiIHZpZXdCb3g9IjAgMCA3NiA3NiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHJlY3QgeD0iMiIgeT0iMiIgd2lkdGg9IjcyIiBoZWlnaHQ9IjcyIiByeD0iMzYiIHN0cm9rZT0iI0Y3OEUxRSIgc3Ryb2tlLXdpZHRoPSI0Ii8+CjxwYXRoIGQ9Ik00MC4xNjYgMTkuMDMxMkwzOS44MzU5IDQ1LjU2NDVIMzUuNTk1N0wzNS4yNDAyIDE5LjAzMTJINDAuMTY2Wk0zNS4wODc5IDUzLjYzODdDMzUuMDg3OSA1Mi44NzcgMzUuMzE2NCA1Mi4yMzM3IDM1Ljc3MzQgNTEuNzA5QzM2LjI0NzQgNTEuMTg0MiAzNi45NDE0IDUwLjkyMTkgMzcuODU1NSA1MC45MjE5QzM4Ljc1MjYgNTAuOTIxOSAzOS40MzgyIDUxLjE4NDIgMzkuOTEyMSA1MS43MDlDNDAuNDAzIDUyLjIzMzcgNDAuNjQ4NCA1Mi44NzcgNDAuNjQ4NCA1My42Mzg3QzQwLjY0ODQgNTQuMzY2NSA0MC40MDMgNTQuOTkyOCAzOS45MTIxIDU1LjUxNzZDMzkuNDM4MiA1Ni4wNDIzIDM4Ljc1MjYgNTYuMzA0NyAzNy44NTU1IDU2LjMwNDdDMzYuOTQxNCA1Ni4zMDQ3IDM2LjI0NzQgNTYuMDQyMyAzNS43NzM0IDU1LjUxNzZDMzUuMzE2NCA1NC45OTI4IDM1LjA4NzkgNTQuMzY2NSAzNS4wODc5IDUzLjYzODdaIiBmaWxsPSIjRjc4RTFFIi8+Cjwvc3ZnPgo="/>
              <h3>Unable to Connect</h3>
              <div class="desc-dialog">The connection to the server has been lost.
              Please refresh page to continue.</div>`, //use base64 for fixed can't display image on server has stop.
          buttons: [
            {
              label: 'Dismiss',
              cssClass: 'btn-cancel',
              action: function(dialog) {
                dialog.close();
                $rootScope.$broadcast('noteRunningStatus', true);//for readonly
              },
            },
            {
              label: 'Refresh',
              cssClass: 'btn-primary',
              action: function(dialog) {
                dialog.close();
                window.location.reload();
              },
            },
          ],
        });
      }
    }

    // Listen for reconnect success
    websocketCalls.ws.onOpen(handleReconnectSuccess);

    // First reconnect attempt immediately
    attemptCount++;
    console.log(`Reconnect attempt ${attemptCount}/${maxAttempts}`);
    toastr.warning(`Reconnecting... Attempting to reconnect (${attemptCount}/${maxAttempts})`);
    websocketCalls.ws.reconnect();

    // Then schedule retries
    // set timeout for attempt and timer
    reconnectIntervalId = setInterval(() => {
      if (reconnectSucceeded) {
        clearInterval(reconnectIntervalId);
        return;
      }

      if (attemptCount >= maxAttempts) {
        clearInterval(reconnectIntervalId);
        handleReconnectFailure();
        return;
      }

      attemptCount++;
      console.log(` Reconnect attempt ${attemptCount}/${maxAttempts}`);
      toastr.warning(`Reconnecting... Attempting to reconnect (${attemptCount}/${maxAttempts})`);
      websocketCalls.ws.reconnect();
    }, interval);
  }

  websocketCalls.testclose = function() {
    console.log('Test close called');
    websocketCalls.ws.close(10000, 'test closed');
  };

  return websocketCalls;
}

/* global toastr */
// eslint-disable-next-line max-len
