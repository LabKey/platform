/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2010-2014 LabKey Corporation
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

/**
 * @description Portal class to allow programmatic administration of portal pages.
 * @class Portal class to allow programmatic administration of portal pages.
 *            <p>Additional Documentation:
 *              <ul>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=projects">
					        Project and Folder Administration</a></li>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=addModule">
					        Add Web Parts</a></li>
 *                  <li><a href= "https://www.labkey.org/wiki/home/Documentation/page.view?name=manageWebParts">
					        Manage Web Parts</a></li>
 *              </ul>
 *           </p>
 */
LABKEY.Portal = new function()
{
    // private methods:
    var MOVE_ACTION = 'move';
    var REMOVE_ACTION = 'remove';
    var MOVE_UP = 0;
    var MOVE_DOWN = 1;
    var MOVE_LEFT = 0;
    var MOVE_RIGHT = 1;

    function wrapSuccessCallback(userSuccessCallback, action, webPartId, direction)
    {
        return function(webparts, responseObj, options)
        {
           updateDOM(webparts, action, webPartId, direction);
            Ext4.Msg.hide();
            // after update, call the user's success function:
            if (userSuccessCallback)
                userSuccessCallback(webparts, responseObj, options);
        }
    }

    function updateDOM(webparts, action, webPartId, direction)
    {
        // First build up a list of valid webpart table DOM IDs.  This allows us to skip webpart tables that are embedded
        // within others (as in the case of using the APIs to asynchronously render nested webparts).  This ensures that
        // we only rearrange top-level webparts.
        var validWebpartTableIds = {};
        for (var region in webparts)
        {
            var regionParts = webparts[region];
            for (var regionIndex = 0; regionIndex < regionParts.length; regionIndex++)
            {
                var regionId = 'webpart_' + regionParts[regionIndex].webPartId;
                validWebpartTableIds[regionId] = true;
            }
        }

        // would be nice to use getElementsByName('webpart') here, but this isn't supported in IE.
        var tables = document.getElementsByTagName('table');
        var webpartTables = [];
        var targetTable;
        var targetTableIndex;
        for (var tableIndex = 0; tableIndex < tables.length; tableIndex++)
        {
            var table = tables[tableIndex];
            // a table is possibly affected by a delete action if it's if type 'webpart' (whether it's in the current set
            // of active webparts or not).  It's possibly affected by a move action only if it's in the active set of webparts.
            var possiblyAffected = ((action == REMOVE_ACTION && table.getAttribute('name') == 'webpart') || validWebpartTableIds[table.id]);
            if (possiblyAffected)
            {
                webpartTables[webpartTables.length] = table;
                if (table.id == 'webpart_' + webPartId)
                {
                    targetTableIndex = webpartTables.length - 1;
                    targetTable = table;
                }
            }
        }

        if (targetTable)
        {
            if (action == MOVE_ACTION)
            {
                var swapTable = webpartTables[direction == MOVE_UP ? targetTableIndex - 1 : targetTableIndex + 1];
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
                }
            }
            else if (action == REMOVE_ACTION)
            {
                var breakEl   = targetTable.previousElementSibling;
                var breakNode = targetTable.previousSibling;
                targetTable.parentNode.removeChild(breakEl || breakNode); // TODO: Does not properly remove in IE7
                targetTable.parentNode.removeChild(targetTable);
            }
        }
        updateButtons(webparts);
    }


    function removeImgHref(imageEl, newImgSrc)
    {
        var href = imageEl.parentNode;
        var hrefParent = href.parentNode;
        imageEl.src = newImgSrc;
        // replace href with imageEl to remove the link entirely:
        hrefParent.replaceChild(imageEl, href);
        hrefParent.className = "labkey-wp-icon-button-inactive";
    }

    function addImgHref(imageEl, href, newImgSrc)
    {
        var hrefEl = document.createElement("a");
        hrefEl.href = href;
        imageEl.src = newImgSrc;
        imageEl.parentNode.className = "labkey-wp-icon-button-active";
        imageEl.parentNode.replaceChild(hrefEl, imageEl);
        hrefEl.appendChild(imageEl);
    }

    function updateButtons(webparts)
    {
        var moveUpImage = LABKEY.ActionURL.getContextPath() + '/_images/partup.png';
        var moveUpDisabledImage = LABKEY.ActionURL.getContextPath() + '/_images/partupg.png';
        var moveDownImage = LABKEY.ActionURL.getContextPath() + '/_images/partdown.png';
        var moveDownDisabledImage = LABKEY.ActionURL.getContextPath() + '/_images/partdowng.png';

        for (var region in webparts)
        {
            var regionParts = webparts[region];

            // get the webpart table elements from the DOM here; it's possible that some configured webparts may
            // not actually be in the document (if the webpartfactory returns null for security reasons, for example.)
            var confirmedWebparts = [];
            var confirmedWebpartTables = [];
            var index;
            for (index = 0; index < regionParts.length; index++)
            {
                var testWebpart = regionParts[index];
                var testTable = document.getElementById('webpart_' + testWebpart.webPartId);
                if (testTable)
                {
                    confirmedWebparts[confirmedWebparts.length] = testWebpart;
                    confirmedWebpartTables[confirmedWebpartTables.length] = testTable;
                }
            }

            for (index = 0; index < confirmedWebpartTables.length; index++)
            {
                var webpartTable = confirmedWebpartTables[index];
                var webpart = confirmedWebparts[index];
                var disableUp = index == 0;
                var disableDown = index == confirmedWebparts.length - 1;
                var imgChildren = webpartTable.getElementsByTagName("img");

                for (var imageIndex = 0; imageIndex < imgChildren.length; imageIndex++)
                {
                    var imageEl = imgChildren[imageIndex];
                    if (imageEl.src.indexOf(moveUpImage) >= 0 && disableUp)
                        removeImgHref(imageEl, moveUpDisabledImage);
                    else if (imageEl.src.indexOf(moveUpDisabledImage) >= 0 && !disableUp)
                        addImgHref(imageEl, "javascript:LABKEY.Portal.moveWebPartUp({webPartId:" + webpart.webPartId + ",updateDOM:true});", moveUpImage);
                    else if (imageEl.src.indexOf(moveDownImage) >= 0 && disableDown)
                        removeImgHref(imageEl, moveDownDisabledImage);
                    else if (imageEl.src.indexOf(moveDownDisabledImage) >= 0 && !disableDown)
                        addImgHref(imageEl, "javascript:LABKEY.Portal.moveWebPartDown({webPartId:" + webpart.webPartId + ",updateDOM:true});", moveDownImage);
                }
            }
        }
    }

    function wrapErrorCallback(userErrorCallback)
    {
        return function(exceptionObj, responseObj, options)
        {
            // hide the UI message box:
            Ext4.Msg.hide();
            // after update, call the user's success function:
            return userErrorCallback(exceptionObj, responseObj, options);
        }
    }

    function startUIUpdate()
    {
        Ext4.Msg.wait("Saving...");
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

            editTabWindow.show(false, function(){nameTextField.focus();}, this);
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
            LABKEY.requiresExt4Sandbox(true, function() {
                LABKEY.requiresScript('WebPartPermissionsPanel.js', true, display, this);
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
                url: LABKEY.ActionURL.buildURL('project', 'getWebParts', config.containerPath),
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
            if (config.updateDOM)
                startUIUpdate();
            var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_UP);
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
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
            if (config.updateDOM)
                startUIUpdate();
            var callConfig = mapIndexConfigParameters(config, MOVE_ACTION, MOVE_DOWN);
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
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
            if (config.updateDOM)
                startUIUpdate();
            var callConfig = mapIndexConfigParameters(config, REMOVE_ACTION, undefined);
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'deleteWebPartAsync', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getOnSuccess(callConfig),
                failure: LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure(callConfig), callConfig.scope, true),
                params: callConfig.params
            });
        },

        /**
         * Move a folder tab to the left.
         * @param config An object which contains the following configuration properties.
         * @param {String} [config.pageId] The pageId of the tab to be moved.
         * @param {String} [config.folderTabCaption] The caption of the tab to be moved.
         */
        moveTabLeft : function(config)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin', 'moveTab', LABKEY.container.path),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                    if(config.domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                        var tabAnchor = Ext4.query('#' + config.domId)[0];
                        if (tabAnchor) {
                            var tabEl = Ext4.get(tabAnchor.parentElement);
                            var prev = tabAnchor.parentNode.previousElementSibling;
                            tabEl.insertBefore(prev);
                        }
                    }
                }, this, false),
                failure: function(response){
                    // Currently no-op when failure occurs.
                },
                params: {
                    pageId: config.pageId,
                    direction: MOVE_LEFT
                }
            });
        },

        /**
         * Move a folder tab to the right.
         * @param config An object which contains the following configuration properties.
         * @param {String} [config.pageId] Reserved for a time when multiple portal pages are allowed per container.
         */
        moveTabRight : function(config)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin', 'moveTab', LABKEY.container.path),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(response, options) {
                    if(config.domId && response.pageIdToSwap && response.pageIdToSwap !== response.pageId) {
                        var tabAnchor = Ext4.query('#' + config.domId)[0];
                        if (tabAnchor) {
                            var tabEl = Ext4.get(tabAnchor.parentElement);
                            var next = tabAnchor.parentNode.nextElementSibling;
                            tabEl.insertAfter(next);
                        }
                    }
                }, this, false),
                failure: function(response, options){
                    // Currently no-op when failure occurs.
                },
                params: {
                    pageId: config.pageId,
                    direction: MOVE_RIGHT
                }
            });
        },

        /**
         * Toggle tab edit mode. Enables or disables tab edit mode. When in tab edit mode an administrator
         * can manage tabs (i.e. change order, add, remove, etc.)
         */
        toggleTabEditMode : function()
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin', 'toggleTabEditMode', LABKEY.container.path),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function(response, options){
                    var classToSearchFor = response.tabEditMode ? 'tab-edit-mode-disabled' : 'tab-edit-mode-enabled';
                    var classToReplaceWith = response.tabEditMode ? 'tab-edit-mode-enabled' : 'tab-edit-mode-disabled';
                    var tabDiv = document.getElementsByClassName(classToSearchFor)[0];

                    if (tabDiv) {
                        // Navigate to the start URL if the current active tab is also hidden.
                        if (response.startURL && tabDiv.querySelector('li.tab-nav-active.tab-nav-hidden'))
                            window.location = response.startURL;
                        else
                            tabDiv.setAttribute('class', tabDiv.getAttribute('class').replace(classToSearchFor, classToReplaceWith));
                    }
                })
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
                    url: LABKEY.ActionURL.buildURL('admin', 'addTab'),
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
                        var errorHTML;
                        if (jsonResp && jsonResp.errors)
                            errorHTML = jsonResp.errors[0].message;
                        else
                            errorHTML = 'An unknown error occured. Please contact your administrator.';
                        Ext4.Msg.alert('Error', '<div class="labkey-error">' + errorHTML + '</div>');
                    }
                });
            };

            showEditTabWindow("Add Tab", addTabHandler, null);
        },

        /**
         * Shows a hidden tab.
         * @param pageId the pageId of the tab.
         */
        showTab : function(pageId)
        {
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('admin', 'showTab'),
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
                        var errorHTML = '<div class="labkey-error">' + jsonResp.errors[0].message + '</div>';
                        Ext4.Msg.alert('Error', errorHTML);
                    }
                }
            });
        },

        /**
         * Allows an administrator to rename a tab.
         * @param pageId the pageId of the tab to rename
         * @param urlId the id of the anchor tag of the tab to be renamed.
         */
        renameTab : function(pageId, urlId)
        {
            var tabLinkEl = document.getElementById(urlId);

            if (tabLinkEl)
            {
                var currentName = tabLinkEl.textContent;

                var renameHandler = function(name, editWindow)
                {
                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL('admin', 'renameTab'),
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
                            var errorHTML;
                            if (jsonResp.errors)
                                errorHTML = jsonResp.errors[0].message;
                            else
                                errorHTML = 'An unknown error occured. Please contact your administrator.';
                            Ext4.Msg.alert('Error', '<div class="labkey-error">' + errorHTML + '</div>');
                        }
                    });
                };

                showEditTabWindow("Rename Tab", renameHandler, currentName);
            }
        },

        _showPermissions : showPermissions
    };
};

