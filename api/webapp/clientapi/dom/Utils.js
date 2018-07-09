/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2014-2017 LabKey Corporation
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

    // Insert a hidden <form> into to page, put the JSON into it, and submit it - the server's response will
    // make the browser pop up a dialog
    var formSubmit = function(url, value)
    {
        var formId = LABKEY.Utils.generateUUID();
        var formHTML = '<form method="POST" id="' + formId + '" action="' + url + '">' +
                '<input type="hidden" name="json" value="' + LABKEY.Utils.encodeHtml(LABKEY.Utils.encode(value)) + '" />' +
                '<input type="hidden" name="X-LABKEY-CSRF" value="' + LABKEY.CSRF + '" />' +
                '</form>';
        $('body').append(formHTML);
        $('#'+formId).submit();
    };

    var displayModal = function(title, msg) {
        var modal = $('#lk-utils-modal');

        if (modal.length === 0) {
            $('body').append([
                '<div id="lk-utils-modal" class="modal fade" role="dialog">',
                    '<div class="modal-dialog"><div class="modal-content"></div></div>',
                '</div>'
            ].join(''));

            modal = $('#lk-utils-modal');
        }

        modal.find('.modal-content').html([
            '<div class="modal-header">',
                '<button type="button" class="close" data-dismiss="modal">&times;</button>',
                '<h4 class="modal-title">' + LABKEY.Utils.encodeHtml(title) + '</h4>',
            '</div>',
            '<div class="modal-body">',
                '<br>',
                '<p>' + LABKEY.Utils.encodeHtml(msg) + '</p>',
                '<br>',
            '</div>'
        ].join(''));

        modal.modal('show');
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
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToExcel"), spreadsheet);
    };

    /**
     * Documentation available in core/Utils.js -- search for "@name convertToTable"
     */
    impl.convertToTable = function(config) {
        formSubmit(LABKEY.ActionURL.buildURL("experiment", "convertArraysToTable"), config);
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
            displayModal(title, msg);
        }
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

    return impl;

}(LABKEY.Utils || new function() { return {}; }, jQuery);
