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

    var displayModal = function(title, msg, fn, args, disableBackdrop) {
        var modal = $('#lk-utils-modal');

        if (modal.length === 0) {
            $('body').append([
                '<div id="lk-utils-modal" class="modal fade" role="dialog">',
                '<div class="modal-dialog"><div class="modal-content"></div></div>',
                '</div>'
            ].join(''));

            modal = $('#lk-utils-modal');
        }
        var html = [
            '<div class="modal-header">',
                '<button type="button" class="close" data-dismiss="modal">&times;</button>',
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

        // Some views may not be able to access the modal.modal() method
        if (!LABKEY.Utils.isFunction(modal.modal)) {
            console.warn('LABKEY.Utils.displayModal() unable to display modal.');
            console.warn(title, msg);
            return;
        }

        // prevent the modal from being closed by clicking outside the dialog
        if (disableBackdrop) {
            modal.modal({backdrop: 'static'});
        }

        modal.modal('show');
    };

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
     * Documentation available in core/Utils.js -- search for "@name displayAjaxErrorResponse"
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
     * Documentation available in core/Utils.js -- search for "@name convertToExcel"
     */
    impl.convertToExcel = function(spreadsheet) {
        var formData = { 'json': JSON.stringify(spreadsheet) };
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel"), formData);
    };

    /**
     * Documentation available in core/Utils.js -- search for "@name convertToTable"
     */
    impl.convertToTable = function(config) {
        var formData = { 'json': JSON.stringify(config) };
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToTable"), formData);
    };

    /**
     * Documentation available in core/Util.js -- search for "@name postToAction"
     */
    impl.postToAction = function (href, formData) {
        formSubmit(href, formData);
    };

    /**
     * Documentation available in core/Util.js -- search for "@name confirmAndPost"
     */
    impl.confirmAndPost = function (message, href, formData) {
        if (confirm(message))
            formSubmit(href, formData);

        return false;
    };

    /**
     * Documentation specified in core/Utils.js -- search for "@name alert"
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
     * Documentation specified in core/Utils.js -- search for "@name modal"
     */
    impl.modal = function(title, msg, fn, args, disableBackdrop) {
      displayModal(title, msg, fn, args, disableBackdrop);
    };

    /**
     * Documentation specified in core/Utils.js -- search for "@name onError"
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
     * Documentation specified in core/Utils.js -- search for "@name setWebpartTitle"
     */
    impl.setWebpartTitle = function(title, webPartId)
    {
        $('#webpart_' + webPartId + ' span.labkey-wp-title-text').html(LABKEY.Utils.encodeHtml(title));
    };

    /**
     * Documentation specified in core/Utils.js -- search for "@name onReady"
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

    return impl;

}(LABKEY.Utils || new function() { return {}; }, jQuery);
