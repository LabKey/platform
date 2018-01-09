/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2017 LabKey Corporation
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

var console = console || {warn : function(){}};

/**
 * @namespace Utils static class to provide miscellaneous utility functions.
 */
LABKEY.Utils = new function()
{
    // Private array of chars to use for UUID generation
    var CHARS = '0123456789abcdefghijklmnopqrstuvwxyz'.split('');
    var idSeed = 100;

    //When using Ext dateFields you can use DATEALTFORMATS for the altFormat: config option.
    var DATEALTFORMATS_Either =
            'j-M-y g:i a|j-M-Y g:i a|j-M-y G:i|j-M-Y G:i|' +
            'j-M-y|j-M-Y|' +
            'Y-n-d H:i:s|Y-n-d|' +
            'Y/n/d H:i:s|Y/n/d|' +
            'j M Y G:i:s O|' +  // 10 Sep 2009 11:24:12 -0700
            'j M Y H:i:s|c';
    var DATEALTFORMATS_MonthDay =
            'n/j/y g:i:s a|n/j/Y g:i:s a|n/j/y G:i:s|n/j/Y G:i:s|' +
            'n-j-y g:i:s a|n-j-Y g:i:s a|n-j-y G:i:s|n-j-Y G:i:s|' +
            'n/j/y g:i a|n/j/Y g:i a|n/j/y G:i|n/j/Y G:i|' +
            'n-j-y g:i a|n-j-Y g:i a|n-j-y G:i|n-j-Y G:i|' +
            'n/j/y|n/j/Y|' +
            'n-j-y|n-j-Y|' + DATEALTFORMATS_Either;
    var DATEALTFORMATS_DayMonth =
            'j/n/y g:i:s a|j/n/Y g:i:s a|j/n/y G:i:s|j/n/Y G:i:s|' +
            'j-n-y g:i:s a|j-n-Y g:i:s a|j-n-y G:i:s|j-n-Y G:i:s|' +
            'j/n/y g:i a|j/n/Y g:i a|j/n/y G:i|j/n/Y G:i|' +
            'j-n-y g:i a|j-n-Y g:i a|j-n-y G:i|j-n-Y G:i|' +
            'j/n/y|j/n/Y|' +
            'j-n-y|j-n-Y|' +
            'j-M-y|j-M-Y|' + DATEALTFORMATS_Either;

    function isObject(v)
    {
        return typeof v == "object" && Object.prototype.toString.call(v) === '[object Object]';
    }


    function _copy(o, depth)
    {
        if (depth==0 || !isObject(o))
            return o;
        var copy = {};
        for (var key in o)
            copy[key] = _copy(o[key], depth-1);
        return copy;
    }


    // like a general version of Ext.apply() or mootools.merge()
    function _merge(to, from, overwrite, depth)
    {
        for (var key in from)
        {
            if (from.hasOwnProperty(key))
            {
                if (isObject(to[key]) && isObject(from[key]))
                {
                    _merge(to[key], from[key], overwrite, depth-1);
                }
                else if (undefined === to[key] || overwrite)
                {
                    to[key] = _copy(from[key], depth-1);
                }
            }
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

    var collapseExpand = function(elem, notify, targetTagName)
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
            notifyExpandCollapse(url, collapse);
        return false;
    };

    var notifyExpandCollapse = function(url, collapse)
    {
        if (url) {
            if (collapse)
                url += "&collapse=true";
            LABKEY.Ajax.request({url: url});
        }
    };

    var toggleLink = function(link, notify, targetTagName)
    {
        collapseExpand(link, notify, targetTagName);
        var i = 0;
        while (typeof(link.childNodes[i].src) == "undefined" )
            i++;

        if (link.childNodes[i].src.search("plus.gif") >= 0)
            link.childNodes[i].src = link.childNodes[i].src.replace("plus.gif", "minus.gif");
        else
            link.childNodes[i].src = link.childNodes[i].src.replace("minus.gif", "plus.gif");
        return false;
    };

    /** @scope LABKEY.Utils */
    return {
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
            // https://stackoverflow.com/a/7124052
            return String(html)
                    .replace(/&/g, '&amp;')
                    .replace(/"/g, '&quot;')
                    .replace(/'/g, '&#39;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;')
                    .replace(/\//g, '&#x2F;');
        },

        isArray: function(value) {
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/isArray
            return Object.prototype.toString.call(value) === "[object Array]";
        },

        isDate: function(value) {
            return Object.prototype.toString.call(value) === '[object Date]';
        },

        isDefined: function(value) {
            return typeof value !== 'undefined';
        },

        isFunction: function(value) {
            // http://stackoverflow.com/questions/5999998/how-can-i-check-if-a-javascript-variable-is-function-type
            var getType = {};
            return value !== null && value !== undefined && getType.toString.call(value) === '[object Function]';
        },

        isObject: isObject,

        /**
         * Returns date formats for use in an Ext.form.DateField. Useful when using a DateField in an Ext object,
         * it contains a very large set of date formats, which helps make a DateField more robust. For example, a
         * user would be allowed to enter dates like 6/1/2011, 06/01/2011, 6/1/11, etc.
         */
        getDateAltFormats : function()
        {
            return LABKEY.useMDYDateParsing ? DATEALTFORMATS_MonthDay : DATEALTFORMATS_DayMonth;
        },

        displayAjaxErrorResponse: function() {
            console.warn('displayAjaxErrorResponse: This is just a stub implementation, request the dom version of the client API : clientapi_dom.lib.xml to get the concrete implementation');
        },

        /**
         * Generates a display string from the response to an error from an AJAX request
         * @private
         * @ignore
         * @param {XMLHttpRequest} responseObj The XMLHttpRequest object containing the response data.
         * @param {Error} [exceptionObj] A JavaScript Error object caught by the calling code.
         * @param {Object} [config] An object with additional configuration properties.  It supports the following:
         * <li>msgPrefix: A string that will be used as a prefix in the error message.  Default to: 'An error occurred trying to load'</li>
         * <li>showExceptionClass: A boolean flag to display the java class of the exception.</li>
         */
        getMsgFromError: function(responseObj, exceptionObj, config){
            config = config || {};
            var error;
            var prefix = config.msgPrefix || 'An error occurred trying to load:\n';

            if (responseObj && responseObj.responseText && responseObj.getResponseHeader('Content-Type'))
            {
                var contentType = responseObj.getResponseHeader('Content-Type');
                if (contentType.indexOf('application/json') >= 0)
                {
                    var jsonResponse = LABKEY.Utils.decode(responseObj.responseText);
                    if (jsonResponse && jsonResponse.exception) {
                        error = prefix + jsonResponse.exception;
                        if (config.showExceptionClass)
                            error += "\n(" + (jsonResponse.exceptionClass ? jsonResponse.exceptionClass : "Exception class unknown") + ")";
                    }
                }
                else if (contentType.indexOf('text/html') >= 0 && jQuery)
                {
                    var html = jQuery(responseObj.responseText);
                    var el = html.find('.exception-message');
                    if (el && el.length === 1)
                        error = prefix + el.text().trim();
                }
            }
            if (!error)
                error = prefix + "Status: " + responseObj.statusText + " (" + responseObj.status + ")";
            if (exceptionObj && exceptionObj.message)
                error += "\n" + exceptionObj.name + ": " + exceptionObj.message;

            return error;
        },

        /**
         * This method has been migrated to specific instances for both Ext 3.4.1 and Ext 4.2.1.
         * For Ext 3.4.1 see LABKEY.ext.Utils.resizeToViewport
         * For Ext 4.2.1 see LABKEY.ext4.Util.resizeToViewport
         */
        resizeToViewport : function()
        {
            console.warn('LABKEY.Utils.resizeToViewport has been migrated. See JavaScript API documentation for details.');
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
         * This is used internally by other class methods to automatically parse returned JSON
         * and call another success function passing that parsed JSON.
         * @param fn The callback function to wrap
         * @param scope The scope for the callback function
         * @param {boolean} [isErrorCallback=false] Set to true if the function is an error callback.  If true, and you do not provide a separate callback, alert will popup showing the error message
         */
        getCallbackWrapper : function(fn, scope, isErrorCallback) {
            return function(response, options)
            {
                var json = response.responseJSON;
                if (!json)
                {
                    //ensure response is JSON before trying to decode
                    if (response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                            && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0){
                        try {
                            json = LABKEY.Utils.decode(response.responseText);
                        }
                        catch (error){
                            //we still want to proceed even if we cannot decode the JSON
                        }

                    }

                    response.responseJSON = json;
                }

                if (!json && isErrorCallback)
                {
                    json = {};
                }

                if (json && !json.exception && isErrorCallback)
                {
                    // Try to make sure we don't show an empty error message
                    json.exception = (response && response.statusText ? response.statusText : "Communication failure.");
                }

                if (fn)
                    fn.call(scope || this, json, response, options);
                else if (isErrorCallback && response.status != 0)
                {
                    // Don't show an error dialog if the user cancelled the request in the browser, like navigating
                    // to another page
                    LABKEY.Utils.alert("Error", json.exception);
                }
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
         * by supplying false for the applyOthers parameter.
         * @param target The target object
         * @param source The source object
         * @param translationMap A map listing property name translations
         * @param applyOthers Set to false to prohibit application of properties
         * not explicitly mentioned in the translation map.
         * @param applyFunctions Set to false to prohibit application of properties
         * that are functions
         */
        applyTranslated : function(target, source, translationMap, applyOthers, applyFunctions)
        {
            if (undefined === target)
                target = {};
            if (undefined === applyOthers)
                applyOthers = true;
            if (undefined == applyFunctions && applyOthers)
                applyFunctions = true;
            var targetPropName;
            for (var prop in source)
            {
                //special case: Ext adds a "constructor" property to every object, which we don't want to apply
                if (prop == "constructor" || LABKEY.Utils.isFunction(prop))
                    continue;
                
                targetPropName = translationMap[prop];
                if (targetPropName)
                    target[translationMap[prop]] = source[prop];
                else if (undefined === targetPropName && applyOthers && (applyFunctions || !LABKEY.Utils.isFunction(source[prop])))
                    target[prop] = source[prop];
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
            document.cookie = name + "=" + value + expires + "; path=" + path;
        },

        /**
         * Retrieves a cookie.  Useful for retrieving non-essential state to provide a better
         * user experience.  Note that some browser settings may prevent cookies from being saved,
         * and users can clear browser cookies at any time, so previously saved cookies should not be assumed
         * to be available.
         * @param {String} name The name of the cookie to be retrieved.
         * @param {String} defaultvalue The value to be returned if no cookie with the specified name is found on the client.
         */
        getCookie : function(name, defaultvalue) {
            var nameEQ = name + "=";
            var ca = document.cookie.split(';');
            for (var i=0; i < ca.length; i++)
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
         * Retrieves the current LabKey Server session ID. Note that this may only be made available when the
         * session ID cookie is marked as httpOnly = false.
         * @returns {String} sessionid The current session id. Defaults to ''.
         * @see {@link https://www.owasp.org/index.php/HttpOnly|OWASP HttpOnly}
         * @see {@link https://tomcat.apache.org/tomcat-7.0-doc/config/context.html#Common_Attributes|Tomcat Attributes}
         */
        getSessionID : function()
        {
            return LABKEY.Utils.getCookie('JSESSIONID', '');
        },

        /**
         * Deletes a cookie.  Note that 'name' and 'pageonly' should be exactly the same as when the cookie
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
         * Loads JavaScript file(s) from the server.
         * @function
         * @param {(string|string[])} file - A file or Array of files to load.
         * @param {Function} [callback] - Callback for when all dependencies are loaded.
         * @param {Object} [scope] - Scope of callback.
         * @param {boolean} [inOrder=false] - True to load the scripts in the order they are passed in. Default is false.
         * @example
         &lt;script type="text/javascript"&gt;
         LABKEY.requiresScript("myModule/myScript.js", true, function() {
                    // your script is loaded
                });
         &lt;/script&gt;
         */
        requiresScript : function(file, callback, scope, inOrder)
        {
            LABKEY.requiresScript.apply(this, arguments);
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

        pluralBasic : function(count, singular)
        {
            return LABKEY.Utils.pluralize(count, singular, singular + 's');
        },

        pluralize : function(count, singular, plural)
        {
            return count.toLocaleString() + " " + (1 == count ? singular : plural);
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
         * @param {Array} [config.testArguments] A array of arguments to pass to the testCallback function
         * @param {Function} config.success The function to call when the testCallback returns true.
         * @param {Array} [config.successArguments] A array of arguments to pass to the successCallback function
         * @param {Object} [config.failure] A function to call when the testCallback throws an exception, or when
         * the maximum number of tests have been made.
         * @param {Array} [config.errorArguments] A array of arguments to pass to the errorCallback function
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

    function onFailure(msg)
    {
        alert("ERROR: " + msg);
    }

    LABKEY.Utils.onTrue({
        testCallback: tester,
        success: onTrue,
        successArguments: ['FileUploadField is ready to use!'],
        failure: onFailure,
        maxTests: 100
    });
&lt;/script&gt;
         */
        onTrue : function(config) {
            config.maxTests = config.maxTests || 1000;
            try
            {
                if (config.testCallback.apply(config.scope || this, config.testArguments || []))
                    LABKEY.Utils.getOnSuccess(config).apply(config.scope || this, config.successArguments || []);
                else
                {
                    if (config.maxTests <= 0)
                    {
                        throw "Maximum number of tests reached!";
                    }
                    else
                    {
                        --config.maxTests;
                        LABKEY.Utils.onTrue.defer(10, this, [config]);
                    }
                }
            }
            catch(e)
            {
                if (LABKEY.Utils.getOnFailure(config))
                {
                    LABKEY.Utils.getOnFailure(config).apply(config.scope || this, [e,config.errorArguments]);
                }
            }
        },

        ensureBoxVisible : function() {
            console.warn('LABKEY.Utils.ensureBoxVisible has been migrated to the appropriate Ext scope. Consider LABKEY.ext.Utils.ensureBoxVisible or LABKEY.ext4.Util.ensureBoxVisible');
        },

        /**
         * Will generate a unique id. If you provide a prefix, consider making it DOM safe so it can be used as
         * an element id.
         * @param {string} [prefix=lk-gen] - Optional prefix to start the identifier.
         * @returns {*}
         */
        id : function(prefix) {
            return (prefix || "lk-gen") + (++idSeed);
        },

        /**
          * Returns a universally unique identifier, of the general form: "92329D39-6F5C-4520-ABFC-AAB64544E172"
          * NOTE: Do not use this for DOM id's as it does not meet the requirements for DOM id specification.
          * Based on original Math.uuid.js (v1.4)
          * http://www.broofa.com
          * mailto:robert@broofa.com
          * Copyright (c) 2010 Robert Kieffer
          * Dual licensed under the MIT and GPL licenses.
        */
        generateUUID : function() {
            // First see if there are any server-generated UUIDs available to return
            if (LABKEY && LABKEY.uuids && LABKEY.uuids.length > 0)
            {
                return LABKEY.uuids.pop();
            }
            // From the original Math.uuidFast implementation
            var chars = CHARS, uuid = new Array(36), rnd = 0, r;
            for (var i = 0; i < 36; i++)
            {
                if (i == 8 || i == 13 || i == 18 || i == 23)
                {
                    uuid[i] = '-';
                }
                else if (i == 14)
                {
                    uuid[i] = '4';
                }
                else
                {
                    if (rnd <= 0x02) rnd = 0x2000000 + (Math.random() * 0x1000000) | 0;
                    r = rnd & 0xf;
                    rnd = rnd >> 4;
                    uuid[i] = chars[(i == 19) ? (r & 0x3) | 0x8 : r];
                }
            }
            return uuid.join('');
        },

        /**
         * Returns a string containing a well-formed html anchor that will apply theme specific styling. The configuration
         * takes any property value pair and places them on the anchor.
         * @param {Object} config a configuration object that models html anchor properties:
         * @param {String} config.href (required if config.onClick not specified) the reference the anchor will use.
         * @param {String} config.onClick (required if config.href not specified) script called when the onClick event is fired by
         * the anchor.
         * @param {String} config.text text that is rendered inside the anchor element.
         */
        textLink : function(config)
        {
            if (config.href === undefined && !config.onClick === undefined)
            {
                throw "href AND/OR onClick required in call to LABKEY.Utils.textLink()";
            }
            var attrs = " ";
            if (config)
            {
                for (var i in config)
                {
                    if (config.hasOwnProperty(i))
                    {
                        if (i.toString() != "text" && i.toString() != "class")
                        {
                            attrs += i.toString() + '=\"' + config[i] + '\" ';
                        }
                    }
                }

                return '<a class="labkey-text-link"' + attrs + '>' + (config.text != null ? config.text : "") + '</a>';
            }
            throw "Config object not found for textLink.";
        },

        /**
         *
         * Standard documented name for error callback arguments is "failure" but various other names have been employed in past.
         * This function provides reverse compatibility by picking the failure callback argument out of a config object
         * be it named failure, failureCallback or errorCallback.
         *
         * @param config
         */
        getOnFailure : function(config)
        {
            return config.failure || config.errorCallback || config.failureCallback;
            // maybe it be desirable for this fall all the way back to returning LABKEY.Utils.displayAjaxErrorResponse?
        },

        /**
         *
         * Standard documented name for success callback arguments is "success" but various names have been employed in past.
         * This function provides reverse compatibility by picking the success callback argument out of a config object,
         * be it named success or successCallback.
         *
         * @param config
         */
        getOnSuccess : function(config)
        {
            return config.success || config.successCallback
        },


        /**
         * Apply properties from b, c, ...  to a.  Properties of each subsequent
         * object overwrites the previous.
         *
         * The first object is modified.
         *
         * Use merge({}, o) to create a deep copy of o.
         */
        merge : function(a, b, c)
        {
            var o = a;
            for (var i=1 ; i<arguments.length ; i++)
                _merge(o, arguments[i], true, 50);
            return o;
        },


        /**
         * Apply properties from b, c, ... to a.  Properties are not overwritten.
         *
         * The first object is modified.
         */
        mergeIf : function(a, b, c)
        {
            var o = arguments[0];
            for (var i=1 ; i<arguments.length ; i++)
                _merge(o, arguments[i], false, 50);
            return o;
        },

        onError : function(error){
            console.warn('onError: This is just a stub implementation, request the dom version of the client API : clientapi_dom.lib.xml to get the concrete implementation');
        },

        /**
         * Returns true if the passed object is empty (ie. {}) and false if not.
         *
         * @param {Object} ob The object to test
         * @return {Boolean} the result of the test
        */
        isEmptyObj : function(ob){
           for (var i in ob){ return false;}
           return true;
        },

        /**
         * Rounds the passed number to the specified number of decimals
         *
         * @param {Number} input The number to round
         * @param {Number} dec The number of decimal places to use
         * @return {Number} The rounded number
        */
        roundNumber : function(input, dec){
            return Math.round(input*Math.pow(10,dec))/Math.pow(10,dec);
        },

        /**
         * Will pad the input string with zeros to the desired length.
         *
         * @param {Number/String} input The input string / number
         * @param {Integer} length The desired length
         * @param {String} padChar The character to use for padding.
         * @return {String} The padded string
        **/
        padString : function(input, length, padChar){
            if (typeof input != 'string')
                input = input.toString();

            var pd = '';
            if (length > input.length){
                for (var i=0; i < (length-input.length); i++){
                    pd += padChar;
                }
            }
            return pd + input;
        },

        /**
         * Returns true if the arguments are case-insensitive equal.  Note: the method converts arguments to strings for the purposes of comparing numbers, which means that it will return odd behaviors with objects (ie. LABKEY.Utils.caseInsensitiveEquals({t: 3}, '[object Object]') returns true)
         *
         * @param {String/Number} a The first item to test
         * @param {String/Number} b The second item to test
         * @return {boolean} True if arguments are case-insensitive equal, false if not
        */
        caseInsensitiveEquals: function(a, b) {
            return String(a).toLowerCase() == String(b).toLowerCase();
        },

        /**
         * Tests whether the passed value can be used as boolean, using a loose definition.  Acceptable values for true are: 'true', 'yes', 1, 'on' or 't'.  Acceptable values for false are: 'false', 'no', 0, 'off' or 'f'.  Values are case-insensitive.
         * @param value The value to test
         */
        isBoolean: function(value){
            var upperVal = value.toString().toUpperCase();
            if (upperVal == "TRUE" || value == "1" || upperVal == "Y" || upperVal == "YES" || upperVal == "ON" || upperVal == "T"
                    || upperVal == "FALSE" || value == "0" || upperVal == "N" || upperVal == "NO" || upperVal == "OFF" || upperVal == "F"){
                return true;
            }
        },

        /**
         * Returns true if the passed value is a string.
         * @param {Object} value The value to test
         * @return {Boolean}
         */
        isString: function(value) {
            return typeof value === 'string';
        },

        onReady: function(config) {
            console.warn('onReady: This is just a stub implementation, request the dom version of the client API : clientapi_dom.lib.xml to get the concrete implementation');
            if (typeof config === "function") {
                config();
            }
        },

        /**
         * Decodes (parses) a JSON string to an object.
         *
         * @param {String} data The JSON string
         * @return {Object} The resulting object
         */
        decode : function(data) {
            return JSON.parse(data + "");
        },

        /**
         * Encodes an Object to a string.
         *
         * @param {Object} data the variable to encode.
         * @return {String} The JSON string.
         */
        encode : function(data) {
            return JSON.stringify(data);
        },

        /**
         * Applies config properties to the specified object.
         * @param object
         * @param config
         * @returns {*}
         */
        apply : function(object, config) {
            var enumerables = ['hasOwnProperty', 'valueOf', 'isPrototypeOf', 'propertyIsEnumerable',
                'toLocaleString', 'toString', 'constructor'];

            if (object && config && typeof config === 'object') {
                var i, j, k;

                for (i in config) {
                    object[i] = config[i];
                }

                if (enumerables) {
                    for (j = enumerables.length; j--;) {
                        k = enumerables[j];
                        if (config.hasOwnProperty(k)) {
                            object[k] = config[k];
                        }
                    }
                }
            }
            return object;
        },

        /**
         * Display an error dialog
         * @param title
         * @param msg
         */
        alert : function(title, msg) {
            console.warn('alert: This is just a stub implementation, request the dom version of the client API : clientapi_dom.lib.xml to get the concrete implementation');
            console.warn(title + ":", msg);
        },


        escapeRe : function(s) {
            return s.replace(/([-.*+?\^${}()|\[\]\/\\])/g, "\\$1");
        },

        getMeasureAlias : function(measure, override) {
            if (measure.alias && !override) {
                return measure.alias;
            }
            else {
                var alias = measure.schemaName + '_' + measure.queryName + '_' + measure.name;
                return alias.replace(/\//g, '_');
            }
        },

        // private
        collapseExpand: collapseExpand,
        notifyExpandCollapse: notifyExpandCollapse,
        toggleLink: toggleLink
    };
};

/** docs for methods defined in dom/Utils.js - primarily here to ensure API docs get generated with combined core/dom versions */

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

/**
 * Sends a JSON object to the server which turns it into an TSV or CSV file and returns it to the browser to be saved or opened.
 * @memberOf LABKEY.Utils
 * @function
 * @static
 * @name convertToTable
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

/**
 * Display an error dialog
 * @memberOf LABKEY.Utils
 * @function
 * @static
 * @name alert
 * @param title
 * @param msg
 */

/**
 * Sets the title of the webpart on the page.  This change is not sticky, so it will be reverted on refresh.
 * @memberOf LABKEY.Utils
 * @function
 * @static
 * @name setWebpartTitle
 * @param {string} title The title string
 * @param {integer} webPartId The ID of the webpart
 */

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
