/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2019 LabKey Corporation
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

LABKEY.Utils = new function(impl, $) {

    // Insert a hidden html FORM into to page, put the form values into it, and submit it - the server's response will
    // make the browser pop up a dialog
    var formSubmit = function(url, formData)
    {
        if (!formData)
            formData = {};
        if (!formData['X-LABKEY-CSRF'])
            formData['X-LABKEY-CSRF'] = LABKEY.CSRF;

        var formId = LABKEY.Utils.generateUUID();

        var html = [];
        html.push('<f');   // avoid form tag, it causes skipfish false positive
        html.push('orm method="POST" id="' + formId + '"action="' + url + '">');
        for (var name in formData)
        {
            if (!formData.hasOwnProperty(name))
                continue;

            var value = formData[name];
            if (value === undefined)
                continue;

            html.push( '<input type="hidden"' +
                    ' name="' + LABKEY.Utils.encodeHtml(name) + '"' +
                    ' value="' + LABKEY.Utils.encodeHtml(value) + '" />');
        }
        html.push("</form>");

        $('body').append(html.join(''));
        $('form#' + formId).submit();
    };

    var displayModalAlert = function(title, msg) {
       displayModal(title, msg, undefined, true);
    };

    var displayModal = function(title, msg, fn, args, disableBackdrop, disableCloseBtn) {
        var modal = $('#lk-utils-modal');

        if (modal.length === 0) {
            $('body').append([
                '<div id="lk-utils-modal" class="modal fade in" role="dialog">',
                    '<div class="modal-dialog"><div class="modal-content"></div></div>',
                '</div>'
            ].join(''));

            modal = $('#lk-utils-modal');
        }
        var html = [
            '<div class="modal-header">',
                (!disableCloseBtn && supportsModal() ? '<button type="button" class="close" data-dismiss="modal">&times;</button>' : ''),
                '<h4 class="modal-title">' + LABKEY.Utils.encodeHtml(title) + '</h4>',
            '</div>',
            '<div class="modal-body">'
        ];
        if (msg) {
            html.push('<br><p>' + LABKEY.Utils.encodeHtml(msg) + '<br></p>');
        }
         html.push(
             '<div id="modal-fn-body"></div>',
             '</div>'
         );

        modal.find('.modal-content').html(html.join(''));
        if (LABKEY.Utils.isFunction(fn)) {
            fn.apply(this, args);
        }

        // prevent the modal from being closed by clicking outside the dialog
        if (disableBackdrop && LABKEY.Utils.isFunction(modal.modal)) {
            modal.modal({backdrop: 'static'});
        }

        showModal()
    };

    var supportsModal = function() {
        var modal = $('#lk-utils-modal');
        return LABKEY.Utils.isFunction(modal.modal);
    }

    var showModal = function() {
        var modal = $('#lk-utils-modal');
        if (supportsModal()) {
            modal.modal('show');
        } else {
            $('body').append('<div id="lk-utils-modal-backdrop" class="fade modal-backdrop in"></div>');
            modal.show();
        }
    }

    var getNextRow = function(rowElem, targetTagName)
    {
        if (null == rowElem)
            return null;


        var nextRow = rowElem.nextSibling;
        while (nextRow != null && !nextRow.tagName)
            nextRow = nextRow.nextSibling;

        if (nextRow == null)
            return null;

        if (targetTagName)
        {
            if (nextRow.tagName != targetTagName)
                return null;
        }
        else
        {
            if (nextRow.tagName != "TR")
                return null;
        }

        return nextRow;
    };

    /**
     * Shows an error dialog box to the user in response to an error from an AJAX request, including
     * any error messages from the server.
     *
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name displayAjaxErrorResponse
     * @param {XMLHttpRequest} responseObj The XMLHttpRequest object containing the response data.
     * @param {Error} [exceptionObj] A JavaScript Error object caught by the calling code.
     * @param {boolean} [showExceptionClass] Flag to display the java class of the exception.
     * @param {String} [msgPrefix] Prefix to the error message (defaults to: 'An error occurred trying to load:')
     * The error dialog will display the Error's name and message, if available.
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
        LABKEY.Utils.alert("Error", error);
    };

    /**
     * Sends a JSON object to the server which turns it into an Excel file and returns it to the browser to be saved or opened.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name convertToExcel
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
        var formData = { 'json': JSON.stringify(spreadsheet) };
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel"), formData);
    };

    /**
     * Sends a JSON object to the server which turns it into an TSV or CSV file and returns it to the browser to be saved or opened.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name convertToTable
     * @param {Object} config.  The config object
     * @param {String} config.fileNamePrefix name to suggest to the browser for saving the file. The appropriate extension (either ".txt" or ".csv", will be appended based on the delim character used (see below).  Defaults to 'Export'
     * @param {String} config.delim The separator between fields.  Allowable values are 'COMMA' or 'TAB'.
     * @param {String} config.quoteChar The character that will be used to quote each field.  Allowable values are 'DOUBLE' (ie. double-quote character), 'SINGLE' (ie. single-quote character) or 'NONE' (ie. no character used).  Defaults to none.
     * @param {String} config.newlineChar The character that will be used to separate each line.  Defaults to '\n'
     * @param {String} config.rows array of rows, which are arrays with values for each cell.
     * @example &lt;script type="text/javascript"&gt;
     LABKEY.Utils.convertToTable({
         fileNamePrefix: 'output',
         rows: [
             ['Row1Col1', 'Row1Col2'],
             ['Row2Col1', 'Row2Col2']
         ],
         delim: 'COMMA'
     });
     &lt;/script&gt;
     */
    impl.convertToTable = function(config) {
        var formData = { 'json': JSON.stringify(config) };
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToTable"), formData);
    };

    /**
     * POSTs to the given href, including CSRF token. Taken from PageFlowUtil.postOnClickJavascript .
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name postToAction
     * @param {String} href containing action and parameters to be POSTed.
     * @param {Object} formData values to include on the hidden form
     */
    impl.postToAction = function (href, formData) {
        formSubmit(href, formData);
    };

    /**
     * Displays a confirmation dialog with the specified message and then, if confirmed, POSTs to the href, using the method above
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name confirmAndPost
     * @param {String} message confirmation message to display.
     * @param {String} href containing action and parameters to be POSTed.
     * @param {Object} formData values to include on the hidden form
     */
    impl.confirmAndPost = function (message, href, formData) {
        if (confirm(message))
            formSubmit(href, formData);

        return false;
    };

    /**
     * Display an error dialog.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name alert
     * @param title
     * @param msg
     */
    impl.alert = function(title, msg) {
        if (window.Ext4) {
            Ext4.Msg.alert(title?Ext4.htmlEncode(title):"", msg?Ext4.htmlEncode(msg):"")
        }
        else if (window.Ext) {
            Ext.Msg.alert(title?Ext.util.Format.htmlEncode(title):"", msg?Ext.util.Format.htmlEncode(msg):"");
        }
        else {
            displayModalAlert(title, msg);
        }
    };

    /**
     * Display a modal dialog.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name modal
     * @param title Title of the modal dialog.
     * @param msg Message to be included in the dialog body. Can be null if the function generates its own content.
     * @param fn {function} This will be called with the provided argument list {args} after the modal is shown. You can generate content in
     * the modal via the following empty div: &lt;div id="modal-fn-body">&lt;/div>
     * @param args {array} Array of arguments to be applied to the function when it is called.
     * @param disableBackdrop {boolean} True to disable closing the modal on background click. Defaults to false.
     * @param disableCloseBtn {boolean} True to disable closing the modal on close button (i.e. X in upper right corner) click. Defaults to false
     *
     * @example &lt;script type="text/javascript"&gt;
     *
     * var myFN = function(arg1) {
     *     document.getElementById('modal-fn-body').innerHTML = "Hello " + LABKEY.Security.currentUser[arg1] + "!";
     * }
     * LABKEY.Utils.modal("Hello", null, myFN, ["displayName"]);
     * &lt;/script&gt;
     */
    impl.modal = function(title, msg, fn, args, disableBackdrop, disableCloseBtn) {
      displayModal(title, msg, fn, args, disableBackdrop, disableCloseBtn);
    };

    /**
     * Provides a generic error callback.  This helper show a modal dialog, log the error to the console
     * and will log the error to the audit log table. The user must have insert permissions on the selected container for
     * this to work.  By default, it will insert the error into the Shared project.  A containerPath param can be passed to
     * use a different container.  The intent of this helper is to provide site admins with a mechanism to identify errors associated
     * with client-side code.  If noAuditLog=true is used, the helper will not log the error.
     *
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name onError
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
    impl.onError = function(error) {

        if (!error)
            return;

        console.log('ERROR: ' + error.exception);
        console.log(error);

        if (!error.noAuditLog)
        {
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
                success: function() {},
                failure: function(error){
                    console.log('Problem logging error');
                    console.log(error);
                }
            });
        }
    };

    /**
     * Sets the title of the webpart on the page.  This change is not sticky, so it will be reverted on refresh.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name setWebpartTitle
     * @param {string} title The title string
     * @param {integer} webPartId The ID of the webpart
     */
    impl.setWebpartTitle = function(title, webPartId)
    {
        $('#webpart_' + webPartId + ' span.labkey-wp-title-text').html(LABKEY.Utils.encodeHtml(title));
    };

    /**
     * Adds new listener to be executed when all required scripts are fully loaded.
     * @memberOf LABKEY.Utils
     * @function
     * @static
     * @name onReady
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

        if (LABKEY.Utils.isFunction(config))
        {
            scope = this;
            callback = config;
            scripts = null;
        }
        else if (LABKEY.Utils.isObject(config) && LABKEY.Utils.isFunction(config.callback))
        {
            scope = config.scope || this;
            callback = config.callback;
            scripts = config.scripts;
        }
        else
        {
            LABKEY.Utils.alert("Configuration Error", "Improper configuration for LABKEY.onReady()");
            return;
        }

        if (scripts)
        {
            LABKEY.requiresScript(scripts, callback, scope, true);
        }
        else
        {
            $(function() { callback.call(scope); });
        }
    };

    impl.addClass = function(element, cls)
    {
        if (LABKEY.Utils.isDefined(element))
        {
            if (LABKEY.Utils.isDefined(element.classList))
            {
                element.classList.add(cls);
            }
            else
            {
                element.className += " " + cls;
            }
        }
    };

    impl.removeClass = function(element, cls)
    {
        if (LABKEY.Utils.isDefined(element))
        {
            if (LABKEY.Utils.isDefined(element.classList))
            {
                element.classList.remove(cls);
            }
            else
            {
                // http://stackoverflow.com/questions/195951/change-an-elements-css-class-with-javascript
                var reg = new RegExp("(?:^|\\s)" + cls + "(?!\\S)/g");
                element.className.replace(reg, '');
            }
        }
    };

    impl.replaceClass = function(element, removeCls, addCls)
    {
        LABKEY.Utils.removeClass(element, removeCls);
        LABKEY.Utils.addClass(element, addCls);
    };

    //private
    impl.loadAjaxContent = function(response, targetEl, success, scope, useReplace) {
        var json = LABKEY.Utils.decode(response.responseText);
        if (!json)
            return;

        if (json.moduleContext)
            LABKEY.applyModuleContext(json.moduleContext);

        if (json.requiredCssScripts)
            LABKEY.requiresCss(json.requiredCssScripts);

        if (json.implicitCssIncludes)
        {
            for (var i=0;i<json.implicitCssIncludes.length;i++)
            {
                LABKEY.requestedCssFiles(json.implicitCssIncludes[i]);
            }
        }

        if (json.requiredJsScripts && json.requiredJsScripts.length)
        {
            LABKEY.requiresScript(json.requiredJsScripts, onLoaded, this, true);
        }
        else
        {
            onLoaded();
        }

        function onLoaded()
        {
            if (json.html)
            {
                if (LABKEY.Utils.isString(targetEl)) {
                    targetEl = $('#'+targetEl);
                }

                if (useReplace === true) {
                    targetEl.replaceWith(json.html);
                }
                else {
                    targetEl.html(json.html); // execute scripts...so bad
                }

                if (LABKEY.Utils.isFunction(success)) {
                    success.call(scope || window);
                }

                if (json.implicitJsIncludes)
                    LABKEY.loadedScripts(json.implicitJsIncludes);
            }
        }
    };

    impl.tabInputHandler = function(elementSelector) {
        // http://stackoverflow.com/questions/1738808/keypress-in-jquery-press-tab-inside-textarea-when-editing-an-existing-text
        $(elementSelector).keydown(function (e) {
            if (e.keyCode == 9) {
                var myValue = "\t";
                var startPos = this.selectionStart;
                var endPos = this.selectionEnd;
                var scrollTop = this.scrollTop;
                this.value = this.value.substring(0, startPos) + myValue + this.value.substring(endPos,this.value.length);
                this.focus();
                this.selectionStart = startPos + myValue.length;
                this.selectionEnd = startPos + myValue.length;
                this.scrollTop = scrollTop;

                e.preventDefault();
            }
        });
    };

    /**
     * Event handler that can be attached to text areas to let them handle indent/outdent with TAB/SHIFT-TAB.
     * Handles region selection for multi-line indenting as well.
     * Note that this overrides the browser's standard focus traversal keystrokes.
     * Based off of postings from http://ajaxian.com/archives/handling-tabs-in-textareas
     * @param event a KeyboardEvent or an Ext.EventObject for the keydown event
     *
     * @example
     *     Ext.EventManager.on('queryText', 'keydown', LABKEY.Utils.handleTabsInTextArea);
     * @example
     *     textareaEl.addEventListener('keydown', LABKEY.Utils.handleTabsInTextArea);
     */
    impl.handleTabsInTextArea = function(event) {
        // unwrap the browser native event from Ext event object
        event = event.browserEvent || event;

        // Check if the user hit TAB or SHIFT-TAB
        if (event.key === 'Tab' && !event.ctrlKey && !event.altKey)
        {
            var t = event.target;

            // IE supports createRange
            if (document.selection && document.selection.createRange)
            {
                var range = document.selection.createRange();
                var stored_range = range.duplicate();
                stored_range.moveToElementText(t);
                stored_range.setEndPoint('EndToEnd', range);
                t.selectionStart = stored_range.text.length - range.text.length;
                t.selectionEnd = t.selectionStart + range.text.length;
                t.setSelectionRange = function(start, end)
                {
                    var range = this.createTextRange();
                    range.collapse(true);
                    range.moveStart("character", start);
                    range.moveEnd("character", end - start);
                    range.select();
                };
            }

            var ss = t.selectionStart;
            var se = t.selectionEnd;
            var newSelectionStart = ss;
            var scrollTop = t.scrollTop;

            if (ss !== se)
            {
                // In case selection was not the entire line (e.g. selection begins in the middle of a line)
                // we need to tab at the beginning as well as at the start of every following line.
                var pre = t.value.slice(0,ss);
                var sel = t.value.slice(ss,se);
                var post = t.value.slice(se,t.value.length);

                // If our selection starts in the middle of the line, include the full line
                if (pre.length > 0 && pre.lastIndexOf('\n') !== pre.length - 1)
                {
                    // Add the beginning of the line to the indented area
                    sel = pre.slice(pre.lastIndexOf('\n') + 1, pre.length).concat(sel);
                    // Remove it from the prefix
                    pre = pre.slice(0, pre.lastIndexOf('\n') + 1);
                    if (!event.shiftKey)
                    {
                        // Add one to the starting index since we're going to add a tab before it
                        newSelectionStart++;
                    }
                }
                // If our last selected character is a new line, don't add a tab after it since that's
                // part of the next line
                if (sel.lastIndexOf('\n') === sel.length - 1)
                {
                    sel = sel.slice(0, sel.length - 1);
                    post = '\n' + post;
                }

                // Shift means remove indentation
                if (event.shiftKey)
                {
                    // Remove one tab after each newline
                    sel = sel.replace(/\n\t/g,"\n");
                    if (sel.indexOf('\t') === 0)
                    {
                        // Remove one leading tab, if present
                        sel = sel.slice(1, sel.length);
                        // We're stripping out a tab before the selection, so march it back one character
                        newSelectionStart--;
                    }
                }
                else
                {
                    pre = pre.concat('\t');
                    sel = sel.replace(/\n/g,"\n\t");
                }

                var originalLength = t.value.length;
                t.value = pre.concat(sel).concat(post);
                t.setSelectionRange(newSelectionStart, se + (t.value.length - originalLength));
            }
            // No text is selected
            else
            {
                // Shift means remove indentation
                if (event.shiftKey)
                {
                    // Figure out where the current line starts
                    var lineStart = t.value.slice(0, ss).lastIndexOf('\n');
                    if (lineStart < 0)
                    {
                        lineStart = 0;
                    }
                    // Look for the first tab
                    var tabIndex = t.value.slice(lineStart, ss).indexOf('\t');
                    if (tabIndex !== -1)
                    {
                        // The line has a tab - need to remove it
                        tabIndex += lineStart;
                        t.value = t.value.slice(0, tabIndex).concat(t.value.slice(tabIndex + 1, t.value.length));
                        if (ss === se)
                        {
                            ss--;
                            se = ss;
                        }
                        else
                        {
                            ss--;
                            se--;
                        }
                    }
                }
                else
                {
                    // Shove a tab in at the cursor
                    t.value = t.value.slice(0,ss).concat('\t').concat(t.value.slice(ss,t.value.length));
                    if (ss == se)
                    {
                        ss++;
                        se = ss;
                    }
                    else
                    {
                        ss++;
                        se++;
                    }
                }
                t.setSelectionRange(ss, se);
            }
            t.scrollTop = scrollTop;

            // Don't let the browser treat it as a focus traversal
            event.preventDefault();
        }
    };

    impl.signalWebDriverTest = function(signalName, signalResult)
    {
        var signalContainerId = 'testSignals';
        var signalContainerSelector = '#' + signalContainerId;
        var signalContainer = $(signalContainerSelector);
        var formHTML = '<div id="' + signalContainerId + '"/>';

        if (!signalContainer.length)
        {
            $('body').append(formHTML);
            signalContainer = $(signalContainerSelector);
            signalContainer.hide();
        }

        signalContainer.find('div[name=' + LABKEY.Utils.encode(signalName) + ']').remove();
        signalContainer.append('<div name="' + LABKEY.Utils.encodeHtml(signalName) + '" id="' + LABKEY.Utils.id() + '"/>');
        if (signalResult !== undefined)
        {
            signalContainer.find('div[name="' + LABKEY.Utils.encodeHtml(signalName) + '"]').attr("value", LABKEY.Utils.encodeHtml(signalResult));
        }
    };

    /**
     * Returns a string containing an absolute URL to a specific labkey.org documentation page. Modeled after HelpTopic.java getHelpTopicHref().
     * <li>topic (required) The documentation page name</li>
     */
    impl.getHelpTopicHref = function(topic)
    {
        return LABKEY.helpLinkPrefix + topic;
    };

    /**
     * Returns a string containing a well-formed html anchor that opens a link to a specific labkey.org documentation
     * page in a separate tab, using the standard target name. Modeled after HelpTopic.java getSimpleLinkHtml().
     * <li>topic (required) The documentation page name</li>
     * <li>displayText (required) The text to display inside the anchor</li>
     */
    impl.getSimpleLinkHtml = function(topic, displayText)
    {
        return '<a href="' + LABKEY.Utils.encodeHtml(LABKEY.Utils.getHelpTopicHref(topic)) + '" target="labkeyHelp">' + LABKEY.Utils.encodeHtml(displayText) + "</a>";
    };

    impl.notifyExpandCollapse = function(url, collapse)
    {
        if (url) {
            if (collapse)
                url += "&collapse=true";
            LABKEY.Ajax.request({url: url});
        }
    };

    impl.collapseExpand = function(elem, notify, targetTagName)
    {
        var collapse = false;
        var url = elem.href;
        if (targetTagName)
        {
            while (elem.tagName != targetTagName)
                elem = elem.parentNode;
        }
        else
        {
            while (elem.tagName != 'TR')
                elem = elem.parentNode;
        }

        var nextRow = getNextRow(elem, targetTagName);
        if (null != nextRow && nextRow.style.display != "none")
            collapse = true;

        while (nextRow != null)
        {
            if (nextRow.className.indexOf("labkey-header") != -1)
                break;
            if (nextRow.style.display != "none")
                nextRow.style.display = "none";
            else
                nextRow.style.display = "";
            nextRow = getNextRow(nextRow, targetTagName);
        }

        if (null != url && notify)
            impl.notifyExpandCollapse(url, collapse);
        return false;
    };

    impl.toggleLink = function(link, notify, targetTagName)
    {
        impl.collapseExpand(link, notify, targetTagName);
        var i = 0;
        while (typeof(link.childNodes[i].src) == "undefined")
            i++;

        if (link.childNodes[i].src.search("plus.gif") >= 0)
            link.childNodes[i].src = link.childNodes[i].src.replace("plus.gif", "minus.gif");
        else
            link.childNodes[i].src = link.childNodes[i].src.replace("minus.gif", "plus.gif");
        return false;
    };

    impl.attachListener = function(id, eventName, handler, immediate)
    {
        if (!id || !eventName || !handler)
            return;
        const fn = function()
        {
            const el = document.getElementById(id);
            if (el)
                el.addEventListener(eventName, handler);
        };
        (immediate || document.readyState!=="loading") ? fn() : document.addEventListener('load', fn);
    }

    // attach handlers to element events e.g. onclick=fn() etc.
    impl.attachEventHandler = function(id, eventName, handler, immediate)
    {
        if (!id || !eventName || !handler)
            return;
        const fn = function()
        {
            const el = document.getElementById(id);
            if (el)
                el['on' + eventName] = handler;
        };
        (immediate || document.readyState!=="loading") ? fn() : document.addEventListener('load', fn);
    }
    return impl;

}(LABKEY.Utils || new function() { return {}; }, jQuery);
