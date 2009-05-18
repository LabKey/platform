/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.2
 * @license Copyright (c) 2008-2009 LabKey Corporation
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
 * @namespace Utils static class to provide miscellaneous utility functions.
 */
LABKEY.Utils = new function()
{
    // private member variables
    var _extParamMapping = {
        "start" : "query.offset",
        "limit" : "query.maxRows",
        "sort" : "query.sort",
        "dir" : "query.sortdir"
        };

    // private functions
    function handleLoadError(This, o, arg, e)
    {
        LABKEY.Utils.displayAjaxErrorResponse(arg, e);
    }

    function mapQueryParameters(store, options)
    {
        // map all parameters from ext names to labkey names:
        for (var param in options)
        {
            if (_extParamMapping[param])
                options[_extParamMapping[param]] = options[param];
        }

        // fix up any necessary parameter values:
        if ("DESC" == options['query.sortdir'])
        {
            var sortCol = options['query.sort'];
            options['query.sort'] = "-" + sortCol;
        }
    }
    
    function createHttpProxyImpl(containerPath, errorListener)
    {
        var proxy = new Ext.data.HttpProxy(new Ext.data.Connection({
                //where to retrieve data
                url: LABKEY.ActionURL.buildURL("query", "selectRows", containerPath), //url to data object (server side script)
                method: 'GET'
            }));

        if (errorListener)
            proxy.on("loadexception", errorListener);

        proxy.on("beforeload", mapQueryParameters);

        return proxy;
    }


    /** @scope LABKEY.Utils.prototype */
    return {
        // public functions

        /**
        * Encodes the html passed in so that it will not be interpreted as HTML by the browser.
        * For example, if your input string was "&lt;p&gt;Hello&lt;/p&gt;" the output would be
        * "&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;". If you set an element's innerHTML property
        * to this string, the HTML markup will be displayed as literal text rather than being
        * interpreted as HTML.
        *
        * @param {String} html The HTML to encode
		* @return {String} The encoded HTML
		*/
        encodeHtml : function(html)
        {
            var div = document.createElement('div');
            var text = document.createTextNode(html);
            div.appendChild(text);
            return div.innerHTML;
        },

        /**
        * Shows an error dialog box to the user in response to an error from an AJAX request, including
        * any error messages from the server.
        * @param {XMLHttpRequest} responseObj The XMLHttpRequest object containing the response data.
        * @param {Error} [exceptionObj] A JavaScript Error object caught by the calling code.
        * The error dialog will display the Error's name and message, if available. Ext.data.DataReader implementations
        * may throw this type of error object.
        */
        displayAjaxErrorResponse: function(responseObj, exceptionObj)
        {
            var error;
            if (responseObj &&
                responseObj.responseText &&
                responseObj.getResponseHeader['Content-Type'] &&
                responseObj.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
            {
                var jsonResponse = Ext.util.JSON.decode(responseObj.responseText);
                if (jsonResponse && jsonResponse.exception)
                {
                    error = "An error occurred trying to load:\n" + jsonResponse.exception;
                    error += "\n(" + (jsonResponse.exceptionClass ? jsonResponse.exceptionClass : "Exception class unknown") + ")";
                }
            }
            if (!error)
                error = "An error occurred trying to load.\nStatus: " + responseObj.statusText + " (" + responseObj.status + ")";
            if (exceptionObj && exceptionObj.message)
                error += "\n" + exceptionObj.name + ": " + exceptionObj.message;
            Ext.Msg.alert("Load Error", error);
        },
        
        /**
        * Creates an Ext.data.Store that queries the LabKey Server database and can be used as the data source
        * for various components, including GridViews, ComboBoxes, and so forth.
        *
        * @param {Object} config Describes the GridView's properties.
        * @param {String} config.schemaName Name of a schema defined within the current
        *                 container.  Example: 'study'.  See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} config.queryName Name of a query defined within the specified schema
        *                 in the current container.  Example: 'SpecimenDetail'. See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {String} [config.containerPath] The container path in which the schemaName and queryName are defined.
        * @param {String} [config.viewName] Name of a custom view defined over the specified query.
        *                 in the current container. Example: 'SpecimenDetail'.  See also: <a class="link"
                          href="https://www.labkey.org/wiki/home/Documentation/page.view?name=findNames">
                          How To Find schemaName, queryName &amp; viewName</a>.
        * @param {Object} [config.allowNull] If specified, this configuration will be used to insert a blank
        *                 entry as the first entry in the store.
        * @param {String} [config.allowNull.keyColumn] If specified, the name of the column in the underlying database
        *                 that holds the key.
        * @param {String} [config.allowNull.displayColumn] If specified, the name of the column in the underlying database
        *                 that holds the value to be shown by default in the display component.
        * @param {String} [config.allowNull.emptyName] If specified, what to show in the list for the blank entry.
        *                 Defaults to '[None]'.
        * @param {String} [config.allowNull.emptyValue] If specified, the value to be used for the blank entry.
        *                 Defaults to the empty string.
        *
		* @return {Ext.data.Store} The initialized Store object
		*/
        createExtStore: function (storeConfig)
        {
            if (!storeConfig)
                storeConfig = {};
            if (!storeConfig.baseParams)
                storeConfig.baseParams = {};
            storeConfig.baseParams['query.queryName'] = storeConfig.queryName;
            storeConfig.baseParams['schemaName'] = storeConfig.schemaName;
            if (storeConfig.viewName)
                storeConfig.baseParams['query.viewName'] = storeConfig.viewName;

            if (!storeConfig.proxy)
                storeConfig.proxy = createHttpProxyImpl(storeConfig.containerPath);

            if (!storeConfig.remoteSort)
                storeConfig.remoteSort = true;

            if (!storeConfig.listeners || !storeConfig.listeners.loadexception)
                storeConfig.listeners = { loadexception : { fn : handleLoadError } };

            storeConfig.reader = new Ext.data.JsonReader();

            var result = new Ext.data.Store(storeConfig);

            if (storeConfig.allowNull)
            {
                var emptyValue = storeConfig.allowNull.emptyValue;
                if (!emptyValue)
                {
                    emptyValue = "";
                }
                var emptyName = storeConfig.allowNull.emptyName;
                if (!emptyName)
                {
                    emptyName = "[None]";
                }
                result.on("load", function(store)
                    {
                    var emptyRecordConstructor = Ext.data.Record.create([storeConfig.allowNull.keyColumn, storeConfig.allowNull.displayColumn]);
                    var recordData = {};
                    recordData[storeConfig.allowNull.keyColumn] = emptyValue;
                    recordData[storeConfig.allowNull.displayColumn] = emptyName;
                    var emptyRecord = new emptyRecordConstructor(recordData);
                    store.insert(0, emptyRecord);
                    });
            }

            return result;
        },

        /**
         * Returns a URL to the appropriate file icon image based on the specified file name.
         * Note that file name can be a full path or just the file name and extension.
         * If the file name does not include an extension, the URL for a generic image will be returned
         * @param {String} fileName The file name.
         * @return {String} The URL suitable for use in the src attribute of an img element.
         */
        getFileIconUrl : function(fileName) {
            var idx = fileName.lastIndexOf(".");
            var extension = (idx >= 0) ? fileName.substring(idx + 1) : "_generic";
            return LABKEY.ActionURL.buildURL("core", "getAttachmentIcon", "", {extension: extension});
        },

        /**
         * Sends a JSON object to the server which turns it into an Excel file and returns it to the browser to be saved or opened.
         * @param {Object} spreadsheet the JavaScript representation of the data
         * @param {String} spreadsheet.fileName name to suggest to the browser for saving the file
         * @param {String} spreadsheet.sheets array of sheets, which are objects with properties:
         * <ul>
         * <li><b>name:</b> name of the Excel sheet</li>
         * <li><b>data:</b> two dimensional array of values</li>
         * </ul>
         * The value array may be either primitives (booleans, numbers, Strings, and dates), or may be a map with
         * the following structure:
         * <ul>
         * <li><b>value:</b> the boolean, number, String, or date value of the cell</li>
         * <li><b>formatString:</b> for dates and numbers, the Java format string used with SimpleDateFormat
         * or DecimalFormat to control how the value is formatted</li>
         * <li><b>timeOnly:</b> for dates, whether the date part should be ignored and only the time value is important</li>
         * </ul>
         */
        convertToExcel : function(spreadsheet) {
            // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
            // will make the browser pop up a dialog
            var newForm = Ext.DomHelper.append(document.getElementsByTagName('body')[0],
                '<form method="POST" action="' + LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel") + '">' +
                '<input type="hidden" name="json" value="' + Ext.util.Format.htmlEncode(Ext.util.JSON.encode(spreadsheet)) + '" />' +
                '</form>');
            newForm.submit();
        },

        /**
         * This is used internally by other class methods to automatically parse returned JSON
         * and call another success function passing that parsed JSON.
         * @param fn The callback function to wrap
         * @param scope The scope for the callback function
         * @param isErrorCallback Set to true if the function is an error callback
         */
        getCallbackWrapper : function(fn, scope, isErrorCallback) {
            return function(response, options)
            {
                //ensure response is JSON before trying to decode
                var json = null;
                if(response && response.getResponseHeader && response.getResponseHeader['Content-Type']
                        && response.getResponseHeader['Content-Type'].indexOf('application/json') >= 0)
                    json = Ext.util.JSON.decode(response.responseText);

                if(!json && isErrorCallback)
                    json = {exeption: (response && response.statusText ? response.statusText : "Communication failure.")};

                if(fn)
                    fn.call(scope || this, json, response, options);
                else if(isErrorCallback)
                    Ext.Msg.alert("Error", json.exception);
            };
        },

        /**
         * Applies properties from the source object to the target object, translating
         * the property names based on the translation map. The translation map should
         * have an entry per property that you wish to rename when it is applied on
         * the target object. The key should be the name of the property on the source object
         * and the value should be the desired name on the target object. The value may
         * also be set to null or false to prohibit that property from being applied.
         * By default, this function will also apply all other properties on the source
         * object that are not listed in the translation map, but you can override this
         * by supplying false for the applyOthers paramer.
         * @param target The target object
         * @param source The source object
         * @param translationMap A map listing property name translations
         * @param applyOthers Set to false to prohibit application of properties
         * not explicitly mentioned in the translation map.
         */
        applyTranslated : function(target, source, translationMap, applyOthers)
        {
            if(undefined === target)
                target = {};
            if(undefined === applyOthers)
                applyOthers = true;
            var targetPropName;
            for(var prop in source)
            {
                targetPropName = translationMap[prop];
                if(targetPropName)
                    target[translationMap[prop]] = source[prop];
                else if(undefined === targetPropName && applyOthers)
                    target[prop] = source[prop];
            }
        },

        /**
         * Ensure BoxComponent is visible on the page.  
         * @param boxComponent
         */
        ensureBoxVisible : function (boxComponent)
        {
            var box = boxComponent.getBox(true);
            var viewportWidth = Ext.lib.Dom.getViewWidth();
            var scrollLeft = Ext.dd.DragDropMgr.getScrollLeft();

            if (viewportWidth + scrollLeft < box.width + box.x) {
                var scrollBarWidth = 18;
                boxComponent.setPosition(viewportWidth + scrollLeft - box.width - scrollBarWidth);
            }
        }
    };
};