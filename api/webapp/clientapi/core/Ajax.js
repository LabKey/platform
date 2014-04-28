/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2011-2012 LabKey Corporation
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
 * Adapter for <a href="http://dev.sencha.com/deploy/dev/docs/?class=Ext.Ajax">Ext.Ajax</a>.
 */
LABKEY.Ajax = new function ()
{
    var configureOptions = function(config) {
        var url, params, method, jsonData;

        if (!config.hasOwnProperty('url') || config.url === null) {
            throw new Error("a URL is required to make a request");
        }

        url = config.url;
        params = config.params;
        jsonData = JSON.stringify(config.jsonData);

        if (config.hasOwnProperty('method') && config.method !== null) {
            method = config.method.toUpperCase();
        } else {
            method = jsonData === null ? 'GET' : 'POST';
        }

        if (params !== undefined || params !== null) {
            params = LABKEY.ActionURL.queryString(params);
        }

        if ((method == 'GET' || jsonData) && params) {
            // Put the params on the URL if the request is a GET or if we have jsonData.
            url = url + (url.indexOf('?') === -1 ? '?' : '&') + params;
            params = null;
        }

        return {
            url: url,
            method: method,
            data: jsonData || params || null
        };
    };

    var configureHeaders = function(xhr, headers, jsonData) {
        var contentTypeHeader = 'application/x-www-form-urlencoded; charset=UTF-8',
            xhrHeader = 'XMLHttpRequest', k;

        if (headers === undefined || headers === null) {
            headers = {};
        }

        if (!headers['Content-Type']) {
            if (jsonData !== undefined && jsonData !== null) {
                contentTypeHeader = 'application/json';
            }

            headers['Content-Type'] = contentTypeHeader;
        }

        if (!headers['X-Requested-With']) {
            headers['X-Requested-With'] = xhrHeader;
        }

        for (k in LABKEY.Ajax.DEFAULT_HEADERS) {
            if (LABKEY.Ajax.DEFAULT_HEADERS.hasOwnProperty(k)) {
                xhr.setRequestHeader(k, LABKEY.Ajax.DEFAULT_HEADERS[k]);
            }
        }

        for (k in headers) {
            if (headers.hasOwnProperty(k)) {
                xhr.setRequestHeader(k, headers[k]);
            }
        }

        return headers;
    };

    /** @scope LABKEY.Ajax */
    return {
        DEFAULT_HEADERS : {'X-LABKEY-CSRF': LABKEY.CSRF},

        request : function(config) {
            var options, callback, successCB, failureCB, scope, xhr;
            options = configureOptions(config);
            scope = config.hasOwnProperty('scope') && config.scope !== null ? config.scope : this;

            if (config.hasOwnProperty('callback') && config.callback !== null) {
                callback = config.callback;
            }

            if (config.hasOwnProperty('success') && config.success !== null) {
                successCB = config.success;
            }

            if (config.hasOwnProperty('failure') && config.failure !== null) {
                failureCB = config.failure;
            }

            xhr = new XMLHttpRequest();
            xhr.onreadystatechange = function() {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200) {
                        if (successCB) {
                            successCB.call(scope, xhr, config);
                        } else if (callback) {
                            callback.call(scope, config, true, xhr);
                        }
                    } else {
                        console.log('FAILURE', xhr.status);
                        if (failureCB) {
                            failureCB.call(scope, xhr, config);
                        } else if (callback) {
                            callback.call(scope, config, false, xhr);
                        }
                    }
                }
            };

            if (config.hasOwnProperty('timeout') && config.timeout !== null) {
                xhr.timeout = config.timeout;
                xhr.ontimeout = function() {
                    if (failureCB) {
                        failureCB.call(scope, xhr, config);
                    } else if (callback) {
                        callback.call(scope, config, false, xhr);
                    }
                };
            }

            xhr.open(options.method, options.url, true);
            configureHeaders(xhr, config.headers, config.jsonData);
            xhr.send(options.data);

            return xhr;
        }
    }
};