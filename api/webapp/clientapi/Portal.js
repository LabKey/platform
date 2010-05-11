/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.2
 * @license Copyright (c) 2010 LabKey Corporation
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

    function wrapSuccessCallback(userSuccessCallback, action, webPartId, direction)
    {
        return function(webparts, responseObj, options)
        {
           updateDOM(webparts, action, webPartId, direction);
            Ext.Msg.hide();
            // after update, call the user's success function:
            if (userSuccessCallback)
                userSuccessCallback(webparts, responseObj, options);
        }
    }

    function updateDOM(webparts, action, webPartId, direction)
    {
        // would be nice to use getElementsByName('webpart') here, but this isn't supported in IE.
        var tables = document.getElementsByTagName('table');
        var webpartTables = [];
        var targetTable;
        var targetTableIndex;
        for (var tableIndex = 0; tableIndex < tables.length; tableIndex++)
        {
            var table = tables[tableIndex];
            if (table.getAttribute('name') == 'webpart')
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

                    parentEl.removeChild(targetTable);
                    parentEl.removeChild(swapTable);
                    parentEl.insertBefore(targetTable, insertPoint);
                    parentEl.insertBefore(swapTable, swapPoint);
                }
            }
            else if (action == REMOVE_ACTION)
            {
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
    }

    function addImgHref(imageEl, href, newImgSrc)
    {
        var hrefEl = document.createElement("a");
        hrefEl.href = href;
        imageEl.src = newImgSrc;
        imageEl.parentNode.replaceChild(hrefEl, imageEl);
        hrefEl.appendChild(imageEl);
    }

    function updateButtons(webparts)
    {
        var moveUpImage = LABKEY.ActionURL.getContextPath() + '/_images/partup.gif';
        var moveUpDisabledImage = LABKEY.ActionURL.getContextPath() + '/_images/partupg.gif';
        var moveDownImage = LABKEY.ActionURL.getContextPath() + '/_images/partdown.gif';
        var moveDownDisabledImage = LABKEY.ActionURL.getContextPath() + '/_images/partdowng.gif';

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
                        addImgHref(imageEl, "javascript:LABKEY.Portal.moveWebPartUp({webPartId:" + webpart.webPartId + ", updateDOM:true});", moveUpImage);
                    else if (imageEl.src.indexOf(moveDownImage) >= 0 && disableDown)
                        removeImgHref(imageEl, moveDownDisabledImage);
                    else if (imageEl.src.indexOf(moveDownDisabledImage) >= 0 && !disableDown)
                        addImgHref(imageEl, "javascript:LABKEY.Portal.moveWebPartDown({webPartId:" + webpart.webPartId + ", updateDOM:true});", moveDownImage);
                }
            }
        }
    }

    function wrapErrorCallback(userErrorCallback)
    {
        return function(exceptionObj, responseObj, options)
        {
            // hide the UI message box:
            Ext.Msg.hide();
            // after update, call the user's success function:
            return userErrorCallback(exceptionObj, responseObj, options);
        }
    }

    function startUIUpdate()
    {
        Ext.Msg.wait("Saving...");
    }

    function defaultErrorHandler(exceptionObj, responseObj, options)
    {
        LABKEY.Utils.displayAjaxErrorResponse(responseObj, exceptionObj);
    }

    function mapIndexConfigParameters(config, action, direction)
    {
        var params = {};

        LABKEY.Utils.applyTranslated(params, config, {
                successCallback: false,
                errorCallback: false,
                scope: false
            });

        if (direction == MOVE_UP || direction == MOVE_DOWN)
            params.direction = direction;

        // These layered callbacks are confusing.  The outermost (second wrapper, below) de-JSONs the response, passing
        // native javascript objects to the success wrapper function defined by wrapErrorCallback (wrapSuccessCallback
        // below).  The wrapErrorCallback/wrapSuccessCallback function is responsible for updating the DOM, if necessary,
        // closing the wait dialog, and then calling the API developer's success callback function, if one exists.  If
        // no DOM update is requested, we skip the middle callback layer.
        var errorCallback = config.errorCallback ? config.errorCallback : defaultErrorHandler;

        if (config.updateDOM)
             errorCallback = wrapErrorCallback(errorCallback);
        errorCallback = LABKEY.Utils.getCallbackWrapper(errorCallback, config.scope, true);

        // do the same double-wrap with the success callback as with the error callback:
        var successCallback = config.successCallback;
        if (config.updateDOM)
            successCallback = wrapSuccessCallback(config.successCallback, action, config.webPartId, direction);
        successCallback = LABKEY.Utils.getCallbackWrapper(successCallback, config.scope);

        return {
            params: params,
            successCallback: successCallback,
            errorCallback: errorCallback
        };
    }

    // public methods:
    /** @scope LABKEY.Portal.prototype */
    return {

        /**
         * Move an existing web part up within its portal page, identifying the web part by its unique web part ID.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.pageId Optional, reserved for a time when multiple portal pages are allowed per container.
         * If not provided, main portal page for the container will be queried.
         * @param {String} config.containerPath Optional.  Specifies the container in which the web part query should be performed.
         * If not provided, the method will operate on the current container.
         * @param {Function} config.successCallback
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
        * @param {Function} [config.errorCallback] Function called when execution fails.
        *       This function will be called with the following arguments:
                <ul>
                    <li>exceptionObj: A JavaScript Error object caught by the calling code.</li>
                    <li>responseObj: The XMLHttpRequest object containing the response data.</li>
                    <li>options: the options used for the AJAX request</li>
                </ul>
         */
        getWebParts : function(config)
        {
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'getWebParts', config.containerPath),
                method : 'GET',
                success: LABKEY.Utils.getCallbackWrapper(config.successCallback, config.scope),
                failure: LABKEY.Utils.getCallbackWrapper(config.errorCallback, config.scope, true),
                params: config
            });
        },

        /**
         * Move an existing web part up within its portal page, identifying the web part by index.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.pageId Optional, reserved for a time when multiple portal pages are allowed per container.
         * If not provided, main portal page for the container will be modified.
         * @param {String} config.containerPath Optional.  Specifies the container in which the web part modification should be performed.
         * If not provided, the method will operate on the current container.
         * @param {String} config.webPartId The unique integer ID of the web part to be moved.
         * @param {Boolean} config.updateDOM Optional, defaults to false.  Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
         * @param {Function} config.successCallback
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
        * @param {Function} [config.errorCallback] Function called when execution fails.
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
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
                method : 'GET',
                success: callConfig.successCallback,
                failure: callConfig.errorCallback,
                params: callConfig.params
            });
        },


        /**
         * Move an existing web part up within its portal page, identifying the web part by the unique ID of the containing span.
         * This span will have name 'webpart'.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.pageId Optional, reserved for a time when multiple portal pages are allowed per container.
         * If not provided, main portal page for the container will be modified.
         * @param {String} config.containerPath Optional.  Specifies the container in which the web part modification should be performed.
         * If not provided, the method will operate on the current container.
         * @param {String} config.webPartId The unique integer ID of the web part to be moved.
         * @param {Boolean} config.updateDOM Optional, defaults to false.  Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
         * @param {Function} config.successCallback
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
        * @param {Function} [config.errorCallback] Function called when execution fails.
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
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'moveWebPartAsync', config.containerPath),
                method : 'GET',
                success: callConfig.successCallback,
                failure: callConfig.errorCallback,
                params: callConfig.params
            });
        },
        /**
         * Move an existing web part up within its portal page.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.pageId Optional, reserved for a time when multiple portal pages are allowed per container.
         * If not provided, main portal page for the container will be modified.
         * @param {String} config.containerPath Optional.  Specifies the container in which the web part modification should be performed.
         * If not provided, the method will operate on the current container.
         * @param {String} config.webPartId The unique integer ID of the web part to be moved.
         * @param {Boolean} config.updateDOM Optional, defaults to false.  Indicates whether the current page's DOM should be updated to reflect changes to web part layout.
         * @param {Function} config.successCallback
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
        * @param {Function} [config.errorCallback] Function called when execution fails.
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
            Ext.Ajax.request({
                url: LABKEY.ActionURL.buildURL('project', 'deleteWebPartAsync', config.containerPath),
                method : 'GET',
                success: callConfig.successCallback,
                failure: callConfig.errorCallback,
                params: callConfig.params
            });
        }
    };
};

