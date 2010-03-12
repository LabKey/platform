/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 10.1
 * @license Copyright (c) 2008-2010 LabKey Corporation
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


    /** @scope LABKEY.Utils */
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
         * @example &lt;script language="javascript"&gt;
LABKEY.Utils.convertToExcel(
{
	fileName: 'output.xls',
	sheets:
	[
		{
			name: 'FirstSheet',
			data:
			[
				['Row1Col1', 'Row1Col2'],
				['Row2Col1', 'Row2Col2']
			]
		},
		{
			name: 'SecondSheet',
			data:
			[
				['Col1Header', 'Col2Header'],
				[{value: 1000.5, formatString: '0,000.00'}, {value: '5 Mar 2009 05:14:17', formatString: 'yyyy MMM dd'}],
				[{value: 2000.6, formatString: '0,000.00'}, {value: '6 Mar 2009 07:17:10', formatString: 'yyyy MMM dd'}]

			]
		}
	]
});
&lt;/script&gt;
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
                    json = {exception: (response && response.statusText ? response.statusText : "Communication failure.")};

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

            var scrollBarWidth = 20;
            if (viewportWidth - scrollBarWidth + scrollLeft < box.width + box.x) {
                boxComponent.setPosition(viewportWidth + scrollLeft - box.width - scrollBarWidth);
            }
        },

        /**
         * Sets a client-side cookie.  Useful for saving non-essential state to provide a better
         * user experience.  Note that some browser settings may prevent cookies from being saved,
         * and users can clear browser cookies at any time, so cookies are not a substitute for
         * database persistence.
         * @param {String} name The name of the cookie to be saved.
         * @param {String} value The value of the cookie to be saved.
         * @param {Boolean} pageonly Whether this cookie should be scoped to the entire site, or just this page.
         * Page scoping considers the entire URL without parameters; all URL contents after the '?' are ignored.
         * @param {int} days The number of days the cookie should be saved on the client.
         */
        setCookie : function(name, value, pageonly, days) {
            var expires;
            if (days)
            {
                var date = new Date();
                date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
                expires = "; expires=" + date.toGMTString();
            }
            else
                expires = "";
            var path = "/";
            if (pageonly)
                path = location.pathname.substring(0, location.pathname.lastIndexOf('/'));
            var cookieString = name + "=" + value + expires + "; path=" + path;
            document.cookie = cookieString;
        },

        /**
         * Retrieves a client-side cookie.  Useful for retrieving non-essential state to provide a better
         * user experience.  Note that some browser settings may prevent cookies from being saved,
         * and users can clear browser cookies at any time, so previously saved cookies should not be assumed
         * to be available.
         * @param {String} name The name of the cookie to be retrieved.
         * @param {String} defaultvalue The value to be returned if no cookie with the specified name is found on the client.
         */
        getCookie : function(name, defaultvalue) {
            var nameEQ = name + "=";
            var ca = document.cookie.split(';');
            for(var i=0; i < ca.length; i++)
            {
                var c = ca[i];
                while (c.charAt(0) == ' ')
                    c = c.substring(1, c.length);
                if (c.indexOf(nameEQ) == 0)
                    return c.substring(nameEQ.length, c.length);
            }
            return defaultvalue;
        },

        /**
         * Deletes a client-side cookie.  Note that 'name' and 'pageonly' should be exactly the same as when the cookie
         * was set.
         * @param {String} name The name of the cookie to be deleted.
         * @param {Boolean} pageonly Whether the cookie is scoped to the entire site, or just this page.
         * Deleting a site-level cookie has no impact on page-level cookies, and deleting page-level cookies
         * has no impact on site-level cookies, even if the cookies have the same name.
         */
        deleteCookie : function (name, pageonly)
        {
            LABKEY.Utils.setCookie(name, "", pageonly, -1);
        },

        /**
         * Includes a script file into the page. If the file was already included by some other code, this
         * function will simply ignore the call. This may be used to include files defined in your module's web/ directory
         * or any existing script file in the code web application (e.g., FileUploadField.js) 
         * @param {String} filePath The path to the script file to include. This path should be relative to the web application
         * root. So for example, if you wanted to include a file in your module's web/mymodule/scripts/ directory,
         * the path would be "mymodule/scripts/myscript.js"
         * @param {Boolean} [immediate] Set to false to indicate that the script is not needed until the page is fully
         * loaded (defaults to true). If true, a script element referencing this path will be added immediately
         * following the script block from which this function is called.
         */
        requiresScript : function(filePath, immediate)
        {
            LABKEY.requiresScript(filePath, immediate);
        },

        /**
         * Includes a Cascading Style Sheet (CSS) file into the page. If the file was already included by some other code, this
         * function will simply ignore the call. This may be used to include CSS files defined in your module's web/ directory.
         * @param {String} filePath The path to the script file to include. This path should be relative to the web application
         * root. So for example, if you wanted to include a file in your module's web/mymodule/styles/ directory,
         * the path would be "mymodule/styles/mystyles.css"
         */
        requiresCSS : function(filePath)
        {
            LABKEY.requiresCss(filePath);
        },

        /**
         * Returns true if value ends with ending
         * @param value the value to examine
         * @param ending the ending to look for
         */
        endsWith : function(value, ending)
        {
            if (!value || !ending)
                return false;
            if (value.length < ending.length)
                return false;
            return value.substring(value.length - ending.length) == ending;
        },

        /**
         * Iteratively calls a tester function you provide, calling another callback function once the
         * tester function returns true. This function is useful for advanced JavaScript scenarios, such
         * as cases where you are including common script files dynamically using the requiresScript()
         * method, and need to wait until classes defined in those files are parsed and ready for use.
         *  
         * @param {Object} config a configuration object with the following properties:
         * @param {Function} config.testCallback A function that returns true or false. This will be called every
         * ten milliseconds until it returns true or the maximum number of tests have been made.
         * @param {Array} [config.testArguments] An optional array of arguments to pass to the testCallback function
         * @param {Function} config.successCallback The function to call when the testCallback returns true.
         * @param {Array} [config.successArguments] An optional array of arguments to pass to the successCallback function
         * @param {Object} [config.errorCallback] A function to call when the testCallback throws an exception, or when
         * the maximum number of tests have been made.
         * @param {Array} [config.errorArguments] An optional array of arguments to pass to the errorCallback function
         * @param {Object} [config.scope] A scope to use when calling any of the callback methods (defaults to this)
         * @param {int} [config.maxTests] Maximum number of tests before the errorCallback is called (defaults to 1000).
         *
         * @example
&lt;script&gt;
    LABKEY.Utils.requiresScript("FileUploadField.js");
    LABKEY.Utils.requiresCSS("FileUploadField.css");
&lt;/script&gt;

&lt;script&gt;
    function tester()
    {
        return undefined != Ext.form.FileUploadField;
    }

    function onTrue(msg)
    {
        //this alert is merely to demonstrate the successArguments config property
        alert(msg);

        //use the file upload field...
    }

    function onError(msg)
    {
        alert("ERROR: " + msg);
    }

    LABKEY.Utils.onTrue({
        testCallback: tester,
        successCallback: onTrue,
        successArguments: ['FileUploadField is ready to use!'],
        errorCallback: onError,
        maxTests: 100
    });
&lt;/script&gt;
         */
        onTrue : function(config)
        {
            config.maxTests = config.maxTests || 1000;
            try
            {
                if(config.testCallback.apply(config.scope || this, config.testArguments))
                    config.successCallback.apply(config.scope || this, config.successArguments);
                else
                {
                    if (config.maxTests <= 0)
                        throw "Maximum number of tests reached!";
                    else
                    {
                        --config.maxTests;
                        LABKEY.Utils.onTrue.defer(10, this, [config]);
                    }
                }
            }
            catch(e)
            {
                if (config.errorCallback)
                    config.errorCallback.apply(config.scope || this, [e,config.errorArguments]);
            }
        }
    };
};
