/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2011-2017 LabKey Corporation
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
 * @namespace
 * Utility for making XHR.
 */
LABKEY.Ajax = new function () {
    'use strict';

    var DEFAULT_HEADERS = LABKEY.defaultHeaders;

    var callback = function(fn, scope, args) {
        if (fn) {
            fn.apply(scope, args);
        }
    };

    /**
     * Returns true iff obj contains case-insensitive key
     * @param {object} obj
     * @param {string} key
     */
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

        request : function(config) {
            var options = configureOptions(config),
                scope = config.hasOwnProperty('scope') && config.scope !== null ? config.scope : this,
                xhr = new XMLHttpRequest();

            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    var success = (xhr.status >= 200 && xhr.status < 300) || xhr.status == 304;

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
