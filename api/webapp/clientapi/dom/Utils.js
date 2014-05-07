/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014 LabKey Corporation
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

LABKEY.Utils = new function(impl) {

    /**
     * Shows an error dialog box to the user in response to an error from an AJAX request, including
     * any error messages from the server.
     * @param {XMLHttpRequest} responseObj The XMLHttpRequest object containing the response data.
     * @param {Error} [exceptionObj] A JavaScript Error object caught by the calling code.
     * @param {boolean} [showExceptionClass] Flag to display the java class of the exception.
     * @param {String} [msgPrefix] Prefix to the error message (defaults to: 'An error occurred trying to load:')
     * The error dialog will display the Error's name and message, if available. Ext.data.DataReader implementations
     * may throw this type of error object.
     */
    impl.displayAjaxErrorResponse = function(responseObj, exceptionObj, showExceptionClass, msgPrefix)
    {
        if (responseObj.status == 0)
        {
            // Don't show an error dialog if the user cancelled the request in the browser, like navigating
            // to another page
            return;

        }

        var error = LABKEY.Utils.getMsgFromError(responseObj, exceptionObj, {
            msgPrefix: msgPrefix,
            showExceptionClass: showExceptionClass
        });
        Ext4.Msg.alert("Error", Ext4.htmlEncode(error));
    };

    /**
     * Sends a JSON object to the server which turns it into an Excel file and returns it to the browser to be saved or opened.
     * @param {Object} spreadsheet the JavaScript representation of the data
     * @param {String} spreadsheet.fileName name to suggest to the browser for saving the file. If the fileName is
     * specified and ends with ".xlsx", it will be returned in Excel 2007 format.
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
     * <li><b>forceString:</b> force the value to be treated as a string (i.e. prevent attempt to convert it to a date)</li>
     * </ul>
     * @example &lt;script type="text/javascript"&gt;
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
    impl.convertToExcel = function(spreadsheet) {
        // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
        // will make the browser pop up a dialog
        var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                        '<form method="POST" action="' + LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel") + '">' +
                        '<input type="hidden" name="json" value="' + Ext4.htmlEncode(LABKEY.Utils.encode(spreadsheet)) + '" />' +
                        '</form>');
        newForm.submit();
    };

    /**
     * Sends a JSON object to the server which turns it into an TSV or CSV file and returns it to the browser to be saved or opened.
     * @param {Object} config.  The config object
     * @param {String} config.fileNamePrefix name to suggest to the browser for saving the file. The appropriate extension (either ".txt" or ".csv", will be appended based on the delim character used (see below).  Defaults to 'Export'
     * @param {String} config.delim The separator between fields.  Allowable values are 'COMMA' or 'TAB'.
     * @param {String} config.quoteChar The character that will be used to quote each field.  Allowable values are 'DOUBLE' (ie. double-quote character), 'SINLGE' (ie. single-quote character) or 'NONE' (ie. no character used).  Defaults to none.
     * @param {String} config.newlineChar The character that will be used to separate each line.  Defaults to '\n'
     * @param {String} config.rows array of rows, which are arrays with values for each cell.
     * @example &lt;script type="text/javascript"&gt;
     LABKEY.Utils.convertToTable(
     {
         fileName: 'output.csv',
         rows:
         [
             ['Row1Col1', 'Row1Col2'],
             ['Row2Col1', 'Row2Col2']
         ],
         delim: 'COMMA'
     });
     &lt;/script&gt;
     */
    impl.convertToTable = function(config) {
        // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response
        // will make the browser pop up a dialog
        var newForm = Ext4.DomHelper.append(document.getElementsByTagName('body')[0],
                        '<form method="POST" action="' + LABKEY.ActionURL.buildURL("experiment", "convertArraysToTable") + '">' +
                        '<input type="hidden" name="json" value="' + Ext4.htmlEncode(Ext4.encode(config)) + '" />' +
                        '</form>');
        newForm.submit();
    };

    /**
     * Display an error dialog
     * @param title
     * @param msg
     */
    impl.alert = function(title, msg) {

        Ext4.Msg.alert(title, msg);
    };

    /**
     * Provides a generic error callback.  This helper will call Ext.Msg.hide(), log the error to the console
     * and will log the error to the audit log table. The user must have insert permissions on the selected container for
     * this to work.  By default, it will insert the error into the Shared project.  A containerPath param can be passed to
     * use a different container.  The intent of this helper is to provide site admins with a mechanism to identify errors associated
     * with client-side code.  If noAuditLog=true is used, the helper will not log the error.
     *
     * @param {Object} error The error object passed to the callback function
     * @param {String} [error.containerPath] Container where errors will be logged. Defaults to /shared
     * @param {Boolean} [error.noAuditLog] If false, the errors will not be logged in the audit table.  Defaults to true
     *
     * @example &lt;script type="text/javascript"&gt;
     //basic usage
     LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'users',
            success: function(){},
            failure: LABKEY.Utils.onError
        });

     //custom container and turning off logging
     LABKEY.Query.selectRows({
            schemaName: 'core',
            queryName: 'users',
            success: function(){},
            failure: function(error){
                 error.containerPath = 'myContainer';
                 error.noAuditLog = true;
                 LABKEY.Utils.onError(error);
            }
        });
     &lt;/script&gt;
     */
    impl.onError = function(error){
        if (Ext4.Msg.isVisible())
            Ext4.Msg.hide();

        if(!error)
            return;

        console.log('ERROR: ' + error.exception);
        console.log(error);

        if(!error.noAuditLog){
            LABKEY.Query.insertRows({
                //it would be nice to store them in the current folder, but we cant guarantee the user has write access..
                containerPath: error.containerPath || '/shared',
                schemaName: 'auditlog',
                queryName: 'Client API Actions',
                rows: [{
                    EventType: "Client API Actions",
                    Key1: 'Client Error',
                    //NOTE: labkey should automatically crop these strings to the allowable length for that field
                    Key2: window.location.href,
                    Key3: (error.stackTrace && LABKEY.Utils.isArray(error.stackTrace) ? error.stackTrace.join('\n') : null),
                    Comment: (error.exception || error.statusText || error.message),
                    Date: new Date()
                }],
                success: function(){
                    console.log('Error successfully logged');
                },
                failure: function(error){
                    console.log('Problem logging error');
                    console.log(error);
                }
            });
        }
    };

    /**
     * Sets the title of the webpart on the page.  This change is not sticky, so it will be reverted on refresh.
     * @param {string} title The title string
     * @param {integer} webPartId The ID of the webpart
     */
    impl.setWebpartTitle = function(title, webPartId)
    {
        var titleEl = Ext4.query('table#webpart_'+webPartId+' span[class=labkey-wp-title-text]');//, 'webpart_' + webPartId);
        if (titleEl && (titleEl.length >= 1))
        {
            titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(title);
        }
    };

    /**
     * Adds new listener to be executed when all required scripts are fully loaded.
     * @param {Mixed} config Either a callback function, or an object with the following properties:
     *
     * <li>callback (required) A function that will be called when required scripts are loaded.</li>
     * <li>scope (optional) The scope to be used for the callback function.  Defaults to the current scope.</li>
     * <li>scripts (optional) A string with a single script or an array of script names to load.  This will be passed to LABKEY.requiresScript().</li>
     * @example &lt;script type="text/javascript"&gt;
     //simple usage
     LABKEY.onReady(function(){
                //your code here.  will be executed once scripts have loaded
            });

     //
     LABKEY.Utils.onReady({
                scope: this,
                scripts: ['/myModule/myScript.js', 'AnotherScript.js],
                callback: function(){
                    //your code here.  will be executed once scripts have loaded
                });
            });
     &lt;/script&gt;
     */
    impl.onReady = function(config)
    {
        var scope;
        var callback;
        var scripts;

        if (LABKEY.Utils.isFunction(config)){
            scope = this;
            callback = config;
            scripts = null;
        }
        else if (Ext4.isObject(config) && LABKEY.Utils.isFunction(config.callback))
        {
            scope = config.scope || this;
            callback = config.callback;
            scripts = config.scripts;
        }
        else
        {
            alert("Improper configuration for LABKEY.onReady()");
            return;
        }

        if (scripts)
        {
            LABKEY.requiresScript(scripts, true, callback, scope, true);
        }
        else
        {
            Ext4.onReady(callback, scope);
        }
    };

    return impl;

}(LABKEY.Utils);
