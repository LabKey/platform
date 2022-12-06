/*
 * Copyright (c) 2011-2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var LABKEY = require("./init");
LABKEY.ExtAdapter = require("ExtAdapter").Adapter;

/**
 * @namespace
 * Utility for making XHR.
 */
LABKEY.Ajax = new function () {
    'use strict';

    var DEFAULT_HEADERS = LABKEY.defaultHeaders;

    // Download the file from the ajax response.
    // For now, we assume we are in a browser environment and use the browser's download
    // file prompt by clicking an <a> element or navigating by updating window.location.
    var downloadFile = function (xhr, config) {
        var filename = "";
        if (typeof config.downloadFile === 'string') {
            filename = config.downloadFile;
        }
        else {
            // parse the filename out of the Content-Disposition header. Example:
            //   Content-Disposition: attachment; filename=data.xlsx
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition
            var disposition = xhr.getResponseHeader('Content-Disposition');
            if (disposition && disposition.indexOf('attachment') !== -1) {
                var filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
                var matches = filenameRegex.exec(disposition);
                if (matches != null && matches[1]) {
                    filename = matches[1].replace(/['"]/g, '');
                }
            }
        }

        var type = xhr.getResponseHeader('Content-Type');

        var blob = xhr.response;
        var downloadUrl = URL.createObjectURL(blob);

        if (filename) {
            // use HTML5 a[download] attribute to specify filename
            var a = document.createElement("a");
            a.href = downloadUrl;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
        } else {
            window.location.href = downloadUrl;
        }

        setTimeout(function () { URL.revokeObjectURL(downloadUrl); }, 100); // cleanup
    };

    var callback = function(fn, scope, args) {
        if (fn) {
            fn.apply(scope, args);
        }
    };

    // Returns true iff obj contains case-insensitive key
    var contains = function(obj, key) {
        if (key) {
            var lowerKey = key.toLowerCase();
            for (var k in obj) {
                if (obj.hasOwnProperty(k) && k.toLowerCase() === lowerKey) {
                    return true;
                }
            }
        }
        return false;
    };

    var configureOptions = function(config) {
        var url, params, method = 'GET', data, isForm = false;

        if (!config.hasOwnProperty('url') || config.url === null) {
            throw new Error("a URL is required to make a request");
        }

        url = config.url;
        params = config.params;

        // configure data
        if (config.form) {
            data = config.form instanceof FormData ? config.form : new FormData(config.form);
            isForm = true;
        }
        else if (config.jsonData) {
            data = JSON.stringify(config.jsonData);
        }
        else {
            data = null;
        }

        // configure method
        if (config.hasOwnProperty('method') && config.method !== null) {
            method = config.method.toUpperCase();
        }
        else if (data) {
            method = 'POST';
        }

        // configure params
        if (params !== undefined && params !== null) {

            var qs = LABKEY.ActionURL.queryString(params);

            // 26617: backwards compatibility to append params to the body in the case of a POST without form/jsonData
            if (method === 'POST' && (data === undefined || data === null)) {
                data = qs;
            }
            else {
                url += (url.indexOf('?') === -1 ? '?' : '&') + qs;
            }
        }

        return {
            url: url,
            method: method,
            data: data,
            isForm: isForm
        };
    };

    var configureHeaders = function(xhr, config, options) {
        var headers = config.headers,
            jsonData = config.jsonData;

        if (headers === undefined || headers === null) {
            headers = {};
        }

        // only set Content-Type if this is not FormData and it has not been set explicitly
        if (!options.isForm && !contains(headers, 'Content-Type')) {
            if (jsonData !== undefined && jsonData !== null) {
                headers['Content-Type'] = 'application/json';
            }
            else {
                headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
            }
        }

        if (!contains(headers, 'X-Requested-With')) {
            headers['X-Requested-With'] = 'XMLHttpRequest';
        }

        for (var k in DEFAULT_HEADERS) {
            if (DEFAULT_HEADERS.hasOwnProperty(k)) {
                xhr.setRequestHeader(k, DEFAULT_HEADERS[k]);
            }
        }

        for (var k in headers) {
            if (headers.hasOwnProperty(k)) {
                xhr.setRequestHeader(k, headers[k]);
            }
        }

        return headers;
    };

    /** @scope LABKEY.Ajax */
    return {
        DEFAULT_HEADERS : DEFAULT_HEADERS,

        /**
         * Make a XMLHttpRequest nominally to a LabKey instance. Includes success/failure callback mechanism,
         * HTTP header configuration, support for FormData, and parameter encoding amongst other features.
         * @param config An object which contains the following configuration properties.
         * @param {String} config.url the url used for the XMLHttpRequest. If you are making a request to the
         *              LabKey Server instance see {@link LABKEY.ActionURL.buildURL} for helpful URL construction.
         * @param {String} [config.method] the HTTP request method used for the XMLHttpRequest. Examples are "GET", "PUSH, "DELETE", etc.
         *              Defaults to "GET" unless jsonData is supplied then the default is changed to "POST". For more information,
         *              see this <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods">HTTP request method documentation</a>.
         * @param {Object} [config.jsonData] data provided to the XMLHttpRequest.send(data) function. If the request is method "POST" this is the body of the request.
         * @param {Object} [config.params] An object representing URL parameters that will be added to the URL.
         *                 Note, that if the request is method "POST" and jsonData is not provided these params will be sent via the body of the request.
         * @param {Object} [config.headers] Object specifying additional HTTP headers to add the request.
         * @param {Mixed} [config.form] FormData or Object consumable by FormData that can be used to POST key/value pairs of form information.
         *              For more information, see <a href="https://developer.mozilla.org/en-US/docs/Web/API/FormData">FormData documentation</a>.
         * @param {Boolean|String} [config.downloadFile] Save the response as a file.
         *              When <code>downloadFile</code> is <code>true</code>, the download filename should be included in the response header (e.g, <code>Content-Disposition: attachment; filename=data.xlsx</code>).
         *              When <code>downloadFile</code> is a string, the download value will be used as the download filename.
         *              The success or failure functions will still be called.
         * @param {Function} [config.success] A function called when a successful response is received (determined by XHR readyState and status).
         *              It will be passed the following arguments:
         * <ul>
         * <li><b>xhr:</b> The XMLHttpRequest where the text of the response can be found on xhr.responseText amongst other properties</li>
         * <li><b>originalConfig:</b> The config originally supplied to LABKEY.Ajax.request</li>
         * </ul>
         * @param {Function} [config.failure] A function called when a failure response is received (determined by
         *              XHR readyState, status, or ontimeout if supplied). It will be passed the following arguments:
         * <ul>
         * <li><b>xhr:</b> The XMLHttpRequest where the text of the response can be found on xhr.responseText amongst other properties</li>
         * <li><b>originalConfig:</b> The config originally supplied to LABKEY.Ajax.request</li>
         * </ul>
         * @param {Function} [config.callback] A function called after any success/failure response is received. It will
         *              be passed the following arguments:
         * <ul>
         * <li><b>originalConfig:</b> The config originally supplied to LABKEY.Ajax.request</li>
         * <li><b>success:</b> boolean value that is true if the request was successful</li>
         * <li><b>xhr:</b> The XMLHttpRequest where the text of the response can be found on xhr.responseText amongst other properties</li>
         * </ul>
         * @param {Mixed} [config.scope] A scope for the callback functions. Defaults to "this".
         * @param {Mixed} [config.timeout] If a non-null value is supplied then XMLHttpRequest.ontimeout will be hooked to failure.
         * @returns {XMLHttpRequest}
         */
        request : function(config) {
            var options = configureOptions(config),
                scope = config.hasOwnProperty('scope') && config.scope !== null ? config.scope : this,
                xhr = new XMLHttpRequest();

            if (config.downloadFile) {
                xhr.responseType = 'blob';
            }

            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    var success = (xhr.status >= 200 && xhr.status < 300) || xhr.status == 304;

                    if (success && config.downloadFile) {
                        downloadFile(xhr, config);
                    }
                    callback(success ? config.success : config.failure, scope, [xhr, config]);
                    callback(config.callback, scope, [config, success, xhr]);
                }
            };

            xhr.open(options.method, options.url, true);

            // configure headers after request is open
            configureHeaders(xhr, config, options);

            // configure timeout after request is open
            if (config.hasOwnProperty('timeout') && config.timeout !== null) {
                xhr.ontimeout = function() {
                    callback(config.failure, scope, [xhr, config]);
                    callback(config.callback, scope, [config, false /* success */, xhr]);
                };
            }

            xhr.send(options.data);

            return xhr;
        }
    }
};

// Rhino does not support XMLHttpRequest, so here we wrap the one from our ExtAdapter.
exports.Ajax = {
    request : function (config)
    {
        var o = LABKEY.ExtAdapter.Ajax.request(config);
        if (LABKEY.ExtAdapter.isObject(o))
            return o.responseJSON || o.responseXML || o.responseText;
        return o;
    }
};
