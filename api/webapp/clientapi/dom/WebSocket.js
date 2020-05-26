/*
 * Copyright (c) 2016-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
if(!LABKEY.WebSocket) {
    LABKEY.WebSocket = {};
}

LABKEY.WebSocket = new function ()
{
    var $ = jQuery;
    var _websocket = null;
    var _callbacks = {};

    function openWebsocket() {
        _websocket = new WebSocket((window.location.protocol==="http:"?"ws:":"wss:") + "//" + window.location.host + LABKEY.contextPath + "/_websocket/notifications");
        _websocket.onmessage = websocketOnMessage;
        _websocket.onclose = websocketOnclose;
    }

    function websocketOnMessage (evt) {
        var json = JSON.parse(evt.data);
        var event = json.event;

        var list = _callbacks[event] || [];
        list.forEach(function(cb){cb(json)});
    }

    function websocketOnclose(evt) {
        if (evt.wasClean) {
            // first chance at handling the event goes to any registered callback listeners
            if (_callbacks[evt.code]) {
                _callbacks[evt.code].forEach(function(cb){cb(evt)});
            }
            else if (_callbacks[evt.reason]) {
                _callbacks[evt.reason].forEach(function(cb){cb(evt)});
            }
            else if (evt.code === 1000 || evt.code === 1003) {
                // normal close
                if (evt.reason === "org.labkey.api.security.AuthNotify#LoggedOut") {
                    setTimeout(function(){
                        displayModal('Logged Out', 'You have been logged out. Please reload the page to continue.');
                    }, 1000);
                }
            }
            else if (evt.code === 1001 && evt.reason && evt.reason !== "") {
                // 1001 sent when server is shutdown normally (AND on page reload in FireFox, but that one doesn't have a reason)
                setTimeout(showDisconnectedMessage, 1000);
            }
            else if (evt.code === 1006) {
                // 1006 abnormal close (e.g, server process died)
                setTimeout(showDisconnectedMessage, 1000);
            }
            else if (evt.code === 1008) {
                // Tomcat closes the websocket with "1008 Policy Violation" code when the session has expired.
                // evt.reason === "This connection was established under an authenticated HTTP session that has ended."
                setTimeout(function() {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("login", "whoami.api"),
                        success: LABKEY.Utils.getCallbackWrapper(function(response) {
                            // If the user was previously a guest, don't warn them about session expiration as they
                            // just logged in. See issue 39337
                            if (LABKEY.user.id !== response.id && !LABKEY.user.isGuest) {
                                displayModal("Session Expired", 'Your session has expired. Please reload the page to continue.');
                            }
                        }),
                        failure: function () {
                            setTimeout(showDisconnectedMessage, 1000);
                        }
                    });
                }, 1000);
            }
        }
    }

    function showDisconnectedMessage() {
        displayModal("Server Unavailable", "The server is currently unavailable. Please try reloading the page to continue.");
        // CONSIDER: Periodically attempt to reestablish connection until the server comes back up.
        // CONSIDER: Once reconnected, reload the page unless page is dirty -- LABKEY.isDirty()
    }

    function displayModal(title, message) {
        if (LABKEY.Utils && LABKEY.Utils.modal) {
            LABKEY.Utils.modal(title, null, function() {
                $("#modal-fn-body").html([
                    '<div style="margin-bottom: 40px;">',
                    '<p>' + message + '<br></p>',
                    '<a class="btn btn-default" style="float: right" id="lk-websocket-reload">Reload Page</a>',
                    '</div>',
                ].join(''));

                // add the on click handler for the reload page button
                $('#lk-websocket-reload').on('click', function() {
                    window.location.reload();
                });
            }, null, true);
        }
        else {
            // fall back to using standard alert message if for some reason the jQuery modal isn't available
            alert(message);
        }
    }

    /** Add a general purpose listener for server events. */
    var addServerEventListener = function(event, cb) {
        if (LABKEY.user.isSignedIn && 'WebSocket' in window) {
            if (null === _websocket) {
                openWebsocket();
            }

            var list = _callbacks[event] || [];
            list.push(cb);
            _callbacks[event] = list;
        }
    };

    // initial call open the WebSocket to at least handle the logout and session timeout events
    // other apps or code can register their own event listeners as well
    openWebsocket();

    return {
        addServerEventListener: addServerEventListener
    };
};
