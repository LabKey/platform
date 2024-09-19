/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Type definition not provided for event codes so here we provide our own
// Source: https://www.iana.org/assignments/websocket/websocket.xml#close-code-number
var CloseEventCode = {
    NORMAL_CLOSURE: 1000,
    GOING_AWAY: 1001,
    PROTOCOL_ERROR: 1002,
    UNSUPPORTED_DATA: 1003,
    RESERVED: 1004,
    NO_STATUS_RCVD: 1005,
    ABNORMAL_CLOSURE: 1006,
    INVALID_FRAME_PAYLOAD_DATA: 1007,
    POLICY_VIOLATION: 1008,
    MESSAGE_TOO_BIG: 1009,
    MISSING_EXT: 1010,
    INTERNAL_ERROR: 1011,
    SERVICE_RESTART: 1012,
    TRY_AGAIN_LATER: 1013,
    BAD_GATEWAY: 1014,
    TLS_HANDSHAKE: 1015
};

if (!LABKEY.WebSocket) {
    LABKEY.WebSocket = {};
}

LABKEY.WebSocket = new function ()
{
    var $ = jQuery;
    var _websocket = null;
    var _callbacks = {};
    var _modalShowing = false;
    var _shouldAddFocusListener = true;

    function openWebsocket() {
        if (_websocket !== null || !('WebSocket' in window)) return;
        _websocket = new WebSocket((window.location.protocol==="http:"?"ws:":"wss:") + "//" + window.location.host + LABKEY.contextPath + "/_websocket/notifications");
        _websocket.onmessage = websocketOnMessage;
        _websocket.onclose = websocketOnClose;
        _websocket.onerror = websocketConnectionFailure;

        // Issue 51021: Add a focus listener to check for session expiration when the user returns to the tab,
        // this was done in the apps for issue 47024, but it should be done here for usages of WebSocket.js.
        if (_shouldAddFocusListener) {
            window.addEventListener('focus', function() {
                checkForExpiredSession();
            });
            _shouldAddFocusListener = false;
        }
    }

    function websocketConnectionFailure() {
        if (!LABKEY.user.isGuest) {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("core", "webSocketConnection.api"),
                method: 'POST',
                params: { connected: false }
            });
        }
    }

    function websocketOnMessage(evt) {
        var json = JSON.parse(evt.data);
        var event = json.event;

        var list = _callbacks[event] || [];
        list.forEach(function(cb){cb(json)});
    }

    function websocketOnClose(evt) {
        _websocket = null;
        if (evt) {
            // first chance at handling the event goes to any registered callback listeners
            if (_callbacks[evt.code]) {
                _callbacks[evt.code].forEach(function(cb){cb(evt)});
            }
            else if (_callbacks[evt.reason]) {
                _callbacks[evt.reason].forEach(function(cb){cb(evt)});
            }
            else if (evt.code === CloseEventCode.NORMAL_CLOSURE || evt.code === CloseEventCode.UNSUPPORTED_DATA) {
                // normal close
                if (evt.reason === "org.labkey.api.security.AuthNotify#SessionLogOut") {
                    setTimeout(function(){
                        displayModal('Logged Out', 'You have been logged out.', true);
                    }, 1000);
                }
            }
            else if (evt.code === CloseEventCode.GOING_AWAY && evt.reason) {
                // 1001 sent when server is shutdown normally (AND on page reload in FireFox, but that one doesn't have a reason)
                if (evt.reason !== "" && evt.reason.indexOf("The current thread was interrupted") === -1) {
                    setTimeout(function () {
                        showDisconnectedMessage(false, evt.reason);
                    }, 1000);
                }
            }
            else if (evt.code === CloseEventCode.POLICY_VIOLATION) {
                // Tomcat closes the websocket with "1008 Policy Violation" code when the session has expired.
                // evt.reason === "This connection was established under an authenticated HTTP session that has ended."
                setTimeout(function() {
                    checkForExpiredSession();

                    // Note the extra second (5s in this case) for the timeout before we query whoami, this
                    // is to allow time for the server login from the other tab to take hold.
                }, 5000);
            }
        }
    }

    function checkForExpiredSession() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("login", "whoami.api"),
            success: LABKEY.Utils.getCallbackWrapper(function(response) {
                // If the user was previously a guest, don't warn them about session expiration as they
                // just logged in. See issue 39337
                if (LABKEY.user.id !== response.id && !LABKEY.user.isGuest) {
                    displayModal("Session Expired", 'Your session has expired.', true);
                } else {
                    openWebsocket(); // re-establish the websocket connection, if not already open
                    hideModal();
                }
            }),
            failure: function (reason) {
                setTimeout(function() {
                    // Issue 51021: check for null _websocket before showing unavailable message as this can be triggered
                    // in FF by having the API call aborted because of a page navigation
                    if (_websocket === null) showDisconnectedMessage();
                }, 1000);
            }
        });
    }

    function showDisconnectedMessage(skipReopen, reason) {
        displayModal("Server Unavailable", "The server is currently unavailable. " + (reason ?? '') + " Please try reloading the page to continue.", false, skipReopen);
        // CONSIDER: Periodically attempt to reestablish connection until the server comes back up.
    }

    function isSessionInvalidBackgroundHideEnabled() {
        // Issue 43334: JS error if this is called when the moduleContext isn't available, err on the side of compliance
        if (!LABKEY.moduleContext || !LABKEY.moduleContext.api) {
            return true;
        } else if (LABKEY.moduleContext.api.compliance) {
            return LABKEY.moduleContext.api.compliance.sessionInvalidBackgroundHideEnabled
        }
        return false;
    }

    function toggleBackgroundBlurred(shouldBlur) {
        if (isSessionInvalidBackgroundHideEnabled()) {
            var divClsSelectors = ['.lk-header-ct', '.lk-body-ct', '.footer-block', '.x4-window', '.x-window', "div[role='dialog'] > .modal"];
            for (var i = 0; i < divClsSelectors.length; i++) {
                var divEls = $(divClsSelectors[i]);
                if (divEls) {
                    if (shouldBlur) {
                        divEls.addClass('lk-content-blur');
                    }
                    else {
                        divEls.removeClass('lk-content-blur');
                    }
                }
            }
        }
    }

    function hideModal() {
        if (!_modalShowing) return;

        toggleBackgroundBlurred(false);

        var modal = $('#lk-utils-modal');
        if (LABKEY.Utils.isFunction(modal.modal)) {
            modal.modal('hide');
        } else {
            $('#lk-utils-modal-backdrop').remove();
            modal.hide();
        }

        _modalShowing = false;
    }

    function displayModal(title, message, showReloadMessage, skipReopen) {
        if (!skipReopen) {
            openWebsocket(); // re-establish the websocket connection for the new guest user session
        }

        if (_modalShowing) return;
        _modalShowing = true;

        if (showReloadMessage) {
            message += ' Please reload the page or '
                + '<a href="login-login.view" target="_blank" rel="noopener noreferrer">'
                + 'log in via another browser tab</a> to continue.'
        }

        toggleBackgroundBlurred(true);

        if (LABKEY.Utils.modal) {
            LABKEY.Utils.modal(title, null, function() {
                $("#modal-fn-body").html([
                    '<div style="margin-bottom: 40px;">',
                    '<p>' + message + '<br></p>',
                    '<a class="btn btn-default" style="float: right" id="lk-websocket-reload">Reload Page</a>',
                    '</div>',
                ].join(''));

                // make sure that this modal is in front of any other dialogs (Ext, Ext4, etc.) on the page
                $('#lk-utils-modal').css('z-index', 99999);
                setTimeout(function() {
                    $('#lk-utils-modal-backdrop').css('z-index', 99998);
                }, 100);

                // add the on click handler for the reload page button
                $('#lk-websocket-reload').on('click', function() {
                    window.location.reload();
                });
            }, null, true, isSessionInvalidBackgroundHideEnabled());
        }
        else {
            // fall back to using standard alert message if for some reason the jQuery modal isn't available
            setTimeout(function() {
                alert(message);
            }, 500);
        }
    }

    /** Add a general purpose listener for server events. */
    var addServerEventListener = function(event, cb) {
        if (LABKEY.user.isSignedIn) {
            initWebSocket();

            var list = _callbacks[event] || [];
            list.push(cb);
            _callbacks[event] = list;
        }
    };

    // initial call will open the WebSocket to at least handle the logout and session timeout events
    // other apps or code can register their own event listeners as well via addServerEventListener
    var initWebSocket = function() {
        openWebsocket();
    };

    return {
        initWebSocket: initWebSocket,
        addServerEventListener: addServerEventListener,
        checkForExpiredSession: checkForExpiredSession
    };
};
