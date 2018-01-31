/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2010-2017 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */
(function($) {

    /**
     * @description Portal class to allow programmatic administration of portal pages.
     * @class Portal class to allow programmatic administration of portal pages.
     *            <p>Additional Documentation:
     *              <ul>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=projects">Project and Folder Administration</a></li>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=addModule">Add Web Parts</a></li>
     *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=manageWebParts">Manage Web Parts</a></li>
     *              </ul>
     *           </p>
     */
    LABKEY.Portal = new function()
    {
        // private methods:
        var MOVE_ACTION = 'move';
        var REMOVE_ACTION = 'remove';
        var TOGGLE_FRAME_ACTION = 'toggle_frame';
        var MOVE_UP = 0;
        var MOVE_DOWN = 1;
        var MOVE_LEFT = 0;
        var MOVE_RIGHT = 1;

        function wrapSuccessCallback(userSuccessCallback, action, webPartId, direction)
        {
            return function(webparts, responseObj, options)
            {
                updateDOM(webparts, action, webPartId, direction);
                // after update, call the user's success function:
                if (userSuccessCallback)
                    userSuccessCallback(webparts, responseObj, options);
            }
        }

        function updateDOM(webparts, action, webPartId, direction)
        {
            var targetTable = document.getElementById('webpart_' + webPartId);

            if (targetTable)
            {
                if (action === MOVE_ACTION)
                {
                    var swapTable;
                    swapTable = direction === MOVE_UP ?
                            getAdjacentWebparts(targetTable).above :
                            getAdjacentWebparts(targetTable).below;

                    if (swapTable)
                    {
                        var parentEl = targetTable.parentNode;
                        var insertPoint = swapTable.nextSibling;
                        var swapPoint = targetTable.nextSibling;

                        // Need to make sure the element is actually a child before trying to remove
                        for (var node = 0; node < parentEl.childNodes.length; node++) {
                            if (parentEl.childNodes[node] === swapTable) {
                                parentEl.removeChild(targetTable);
                                parentEl.removeChild(swapTable);
                                parentEl.insertBefore(targetTable, insertPoint);
                                parentEl.insertBefore(swapTable, swapPoint);
                                break;
                            }
                        }

                        var targetUpButtonClass = getUpDownButtons(targetTable).upButton.className;
                        var targetDownButtonClass= getUpDownButtons(targetTable).downButton.className;
                        updateUpDownButtons(targetTable, getUpDownButtons(swapTable).upButton.className, getUpDownButtons(swapTable).downButton.className);
                        updateUpDownButtons(swapTable, targetUpButtonClass, targetDownButtonClass);
                    }
                }
                else if (action === REMOVE_ACTION)
                {
                    var adjacentWebparts = getAdjacentWebparts(targetTable);

                    if (adjacentWebparts.below)
                    {
                        updateUpDownButtons(adjacentWebparts.below, getUpDownButtons(targetTable).upButton.className, undefined);
                    }

                    if (adjacentWebparts.above)
                    {
                        updateUpDownButtons(adjacentWebparts.above, undefined, getUpDownButtons(targetTable).downButton.className);
                    }

                    targetTable.parentNode.removeChild(targetTable);
                }
            }
        }

        function getAdjacentWebparts(webpart)
        {
            var above = webpart.previousElementSibling;
            var below = webpart.nextElementSibling;

            return {
                above: above && above.getAttribute("name") === "webpart" ? above : undefined,
                below: below && below.getAttribute("name") === "webpart" ? below : undefined
            }
        }

        function getUpDownButtons(webpart)
        {
            var moveUpImage = 'fa fa-caret-square-o-up labkey-fa-portal-nav';
            var moveUpDisabledImage = 'fa fa-caret-square-o-up labkey-btn-default-toolbar-small-disabled labkey-fa-portal-nav';
            var moveDownImage = 'fa fa-caret-square-o-down labkey-fa-portal-nav';
            var moveDownDisabledImage = 'fa fa-caret-square-o-down labkey-btn-default-toolbar-small-disabled labkey-fa-portal-nav';

            var getImageEl = function(webpart, imgClass)
            {
                if (webpart){
                    var imgChildren = webpart.getElementsByClassName('labkey-fa-portal-nav');
                    for (var imageIndex = 0; imageIndex < imgChildren.length; imageIndex++)
                    {
                        if (imgChildren[imageIndex].className.indexOf(imgClass) >= 0)
                        {
                            return imgChildren[imageIndex];
                        }
                    }
                }
            };

            return {
                upButton: getImageEl(webpart, moveUpImage) || getImageEl(webpart, moveUpDisabledImage),
                downButton: getImageEl(webpart, moveDownImage) || getImageEl(webpart, moveDownDisabledImage)
            }
        }


        function updateUpDownButtons(webpart, upButtonClass, downButtonClass)
        {
            if (upButtonClass)
                getUpDownButtons(webpart).upButton.className = upButtonClass;
            if (downButtonClass)
                getUpDownButtons(webpart).downButton.className = downButtonClass;
        }

        function wrapErrorCallback(userErrorCallback)
        {
            return function(exceptionObj, responseObj, options)
            {
                // after update, call the user's success function:
                return userErrorCallback(exceptionObj, responseObj, options);
            }
        }

        function defaultErrorHandler(exceptionObj, responseObj, options)
        {
            LABKEY.Utils.displayAjaxErrorResponse(responseObj, exceptionObj);
        }

        function mapIndexConfigParameters(config, action, direction)
        {
            var params = {};

            LABKEY.Utils.applyTranslated(params, config, {
                success: false,
                failure: false,
                scope: false
            });

            if (direction == MOVE_UP || direction == MOVE_DOWN)
                params.direction = direction;

            // These layered callbacks are confusing.  The outermost (second wrapper, below) de-JSONs the response, passing
            // native javascript objects to the success wrapper function defined by wrapErrorCallback (wrapSuccessCallback
            // below).  The wrapErrorCallback/wrapSuccessCallback function is responsible for updating the DOM, if necessary,
            // closing the wait dialog, and then calling the API developer's success callback function, if one exists.  If
            // no DOM update is requested, we skip the middle callback layer.
            var errorCallback = LABKEY.Utils.getOnFailure(config) || defaultErrorHandler;

            if (config.updateDOM)
                errorCallback = wrapErrorCallback(errorCallback);
            errorCallback = LABKEY.Utils.getCallbackWrapper(errorCallback, config.scope, true);

            // do the same double-wrap with the success callback as with the error callback:
            var successCallback = config.success;
            if (config.updateDOM)
                successCallback = wrapSuccessCallback(LABKEY.Utils.getOnSuccess(config), action, config.webPartId, direction);
            successCallback = LABKEY.Utils.getCallbackWrapper(successCallback, config.scope);

            return {
                params: params,
                success: successCallback,
                error: errorCallback
            };
        }

        // TODO: This should be considered 'Native UI' and be migrated away from ExtJS
        var showEditTabWindow = function(title, handler, name)
        {
            LABKEY.requiresExt4Sandbox(function() {
                Ext4.onReady(function() {
                    var nameTextField = Ext4.create('Ext.form.field.Text', {
                        xtype: 'textfield',
                        fieldLabel: 'Name',
                        labelWidth: 50,
                        width: 250,
                        name: 'tabName',
                        value: name ? name : '',
                        maxLength: 64,
                        enforceMaxLength: true,
                        enableKeyEvents: true,
                        labelSeparator: '',
                        selectOnFocus: true,
                        listeners: {
                            scope: this,
                            keypress: function(field, event){
                                if (event.getKey() == event.ENTER) {
                                    handler(nameTextField.getValue(), editTabWindow);
                                }
                            }
                        }
                    });

                    var editTabWindow = Ext4.create('Ext.window.Window', {
                        title: title,
                        closeAction: 'destroy',
                        modal: true,
                        border: false,
                        items: [{
                            xtype: 'panel',
                            border: false,
                            frame: false,
                            bodyPadding: 5,
                            items: [nameTextField]
                        }],
                        buttons: [{
                            text: 'Ok',
                            scope: this,
                            handler: function(){handler(nameTextField.getValue(), editTabWindow);}
                        },{
                            text: 'Cancel',
                            scope: this,
                            handler: function(){
                                editTabWindow.close();
                            }
                        }]
                    });

                    // TODO: Until async CSS load blocking is complete give style a moment to load
                    setTimeout(function() {
                        editTabWindow.show(false, function(){nameTextField.focus();}, this);
                    }, 100);
                });
            });
        };

        var showPermissions = function(webpartID, permission, containerPath) {

            var display = function() {
                Ext4.onReady(function() {
                    Ext4.create('LABKEY.Portal.WebPartPermissionsPanel', {
                        webPartId: webpartID,
                        permission: permission,
                        containerPath: containerPath,
                        autoShow: true
                    });
                });
            };

            var loader = function() {
                LABKEY.requiresExt4Sandbox(function() {
                    LABKEY.requiresScript('WebPartPermissionsPanel.js', display, this);
                }, this);
            };

            // Require a webpartID for any action
            if (webpartID) {
                if (LABKEY.Portal.WebPartPermissionsPanel) {
                    display();
                }
                else {
                    loader();
                }
            }
        };

        // public methods:
        /** @scope LABKEY.Portal.prototype */
        return {

            /**
             * Move an existing web part up within its portal page, identifying the web part by its unique web part ID.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be queried.
             * @param {String} [config.containerPath] Specifies the container in which the web part query should be performed.
             * If not provided, the method will operate on the current container.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            getWebParts : function(config)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'getWebParts.api', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnSuccess(config), config.scope),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(config), config.scope, true),
                    params: config
                });
            },

            /**
             * Move an existing web part up within its portal page, identifying the web part by index.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            moveWebPartUp : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_UP);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync.api', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },


            /**
             * Move an existing web part down within its portal page, identifying the web part by the unique ID of the containing span.
             * This span will have name 'webpart'.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            moveWebPartDown : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_DOWN);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync.api', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },
            /**
             * Remove an existing web part within its portal page.
             * @param config An object which contains the following configuration properties.
             * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
             * If not provided, main portal page for the container will be modified.
             * @param {String} [config.containerPath] Specifies the container in which the web part modification should be performed.
             * If not provided, the method will operate on the current container.
             * @param {String} config.webPartId The unique integer ID of the web part to be moved.
             * @param {Boolean} [config.updateDOM] Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
             * Defaults to false.
             * @param {Function} config.success
             Function called when the this function completes successfully.
             This function will be called with the following arguments:
             <ul>
             <li>webparts: an object with one property for each page region, generally 'body' and 'right'.  The value
             of each property is an ordered array of objects indicating the current web part configuration
             on the page.  Each object has the following properties:
             <ul>
             <li>name: the name of the web part</li>
             <li>index: the index of the web part</li>
             <li>webPartId: the unique integer ID of this web part.</li>
             </ul>
             </li>
             <li>responseObj: the XMLHttpResponseObject instance used to make the AJAX request</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             * @param {Function} [config.failure] Function called when execution fails.
             *       This function will be called with the following arguments:
             <ul>
             <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
             <li>responseObj: The XMLHttpRequest object containing the response data.</li>
             <li>options: the options used for the AJAX request</li>
             </ul>
             */
            removeWebPart : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, REMOVE_ACTION, undefined);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'deleteWebPartAsync.api', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },

            toggleWebPartFrame : function(config)
            {
                var callConfig = mapIndexConfigParameters(config, TOGGLE_FRAME_ACTION, undefined);
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('project', 'toggleWebPartFrameAsync.api', config.containerPath),
                    method : 'GET',
                    success: LABKEY.Utils.getOnSuccess(callConfig),
                    failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                    params: callConfig.params
                });
            },

            /**
             * Move a folder tab to the left.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             */
            moveTabLeft : function(pageId, domId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'moveTab.api', LABKEY.container.path),
                    method: 'GET',
                    params: {
                        pageId: pageId,
                        direction: MOVE_LEFT
                    },
                    success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                        if(domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                            var tabAnchor = $('#' + domId)[0];
                            if (tabAnchor) {
                                $(tabAnchor.parentElement).insertBefore(tabAnchor.parentNode.previousElementSibling);
                            }
                        }
                    }, this, false),
                    failure: function(response){
                        // Currently no-op when failure occurs.
                    }
                });
            },

            /**
             * Move a folder tab to the right.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             */
            moveTabRight : function(pageId, domId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'moveTab.api', LABKEY.container.path),
                    method: 'GET',
                    params: {
                        pageId: pageId,
                        direction: MOVE_RIGHT
                    },
                    success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                        if(domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                            var tabAnchor = $('#' + domId)[0];
                            if (tabAnchor) {
                                $(tabAnchor.parentElement).insertAfter(tabAnchor.parentNode.nextElementSibling);
                            }
                        }
                    }, this, false),
                    failure: function(response, options){
                        // Currently no-op when failure occurs.
                    }
                });
            },

            /**
             * Allows an administrator to add a new portal page tab.
             */
            addTab : function()
            {
                var addTabHandler = function(name, editWindow)
                {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('admin', 'addTab.api'),
                        method: 'POST',
                        jsonData: {tabName: name},
                        success: function(response)
                        {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            if (jsonResp && jsonResp.success)
                            {
                                if (jsonResp.url)
                                    window.location = jsonResp.url;
                            }
                        },
                        failure: function(response)
                        {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            var errorMsg;
                            if (jsonResp && jsonResp.errors)
                                errorMsg = jsonResp.errors[0].message;
                            else
                                errorMsg = 'An unknown error occurred. Please contact your administrator.';
                            Ext4.Msg.alert(errorMsg);
                        }
                    });
                };

                if (LABKEY.pageAdminMode) {
                    showEditTabWindow("Add Tab", addTabHandler, null);
                }
            },

            /**
             * Shows a hidden tab.
             * @param pageId the pageId of the tab.
             */
            showTab : function(pageId)
            {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL('admin', 'showTab.api'),
                    method: 'POST',
                    jsonData: {tabPageId: pageId},
                    success: function(response)
                    {
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp && jsonResp.success)
                        {
                            if (jsonResp.url)
                                window.location = jsonResp.url;
                        }
                    },
                    failure: function(response)
                    {
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp && jsonResp.errors)
                        {
                            alert(jsonResp.errors[0].message);
                        }
                    }
                });
            },

            /**
             * Allows an administrator to rename a tab.
             * @param pageId the pageId of the tab.
             * @param domId the id of the anchor tag of the tab.
             * @param currentLabel the current label of the tab.
             */
            renameTab : function(pageId, domId, currentLabel)
            {
                var tabLinkEl = document.getElementById(domId);

                if (tabLinkEl)
                {
                    var renameHandler = function(name, editWindow)
                    {
                        LABKEY.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('admin', 'renameTab.api'),
                            method: 'POST',
                            jsonData: {
                                tabPageId: pageId,
                                tabName: name
                            },
                            success: function(response)
                            {
                                var jsonResp = LABKEY.Utils.decode(response.responseText);
                                if (jsonResp.success)
                                    tabLinkEl.textContent = name;
                                editWindow.close();
                            },
                            failure: function(response)
                            {
                                var jsonResp = LABKEY.Utils.decode(response.responseText);
                                var errorMsg;
                                if (jsonResp.errors)
                                    errorMsg = jsonResp.errors[0].message;
                                else
                                    errorMsg = 'An unknown error occured. Please contact your administrator.';
                                Ext4.Msg.alert('Oops', errorMsg);
                            }
                        });
                    };

                    if (LABKEY.pageAdminMode) {
                        showEditTabWindow("Rename Tab", renameHandler, currentLabel);
                    }
                }
            },

            _showPermissions : showPermissions
        };
    };

})(jQuery);

