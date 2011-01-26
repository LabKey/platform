/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var {AppProps} = org.labkey.api.settings;
var {
    ActionURL,
    HttpView,
    ViewServlet
} = org.labkey.api.view;

var console = require("console");
var Ext = require("Ext").Ext;

const CONTENTTYPE = "Content-Type";

function createExceptionObject(response, callbackArg, isAbort, isTimeout)
{
    return {
        status: isAbort ? -1 : 0,
        statusText: isAbort ? 'transaction aborted' : 'communication failure',
        isAbort: isAbort,
        isTimeout: isTimeout,
        argument: callbackArg
    }
}

function createResponseObject(response, callbackArg)
{
    var o = {
        status: response.status,
        statusText: "xxx",
        getResponseHeader: function (header) { return response.getHeader(header); },
        getAllResponseHeaders: function () { return response.headers },
        responseText: response.getContentAsString(),
        responseJSON: null,
        responseXML: null,
        argument: callbackArg
    };

    if (o.getResponseHeader(CONTENTTYPE) && o.getResponseHeader(CONTENTTYPE).indexOf("text/xml") >= 0)
        o.responseXML = new XML(o.responseText);

    if (o.getResponseHeader(CONTENTTYPE) && o.getResponseHeader(CONTENTTYPE).indexOf("application/json") >= 0)
        o.responseJSON = JSON.parse(o.responseText);

    return o;
}

function handleTransactionResponse(response, callback, isAbort, isTimeout)
{
    var httpStatus = response.status;
    var responseObject;

    if (httpStatus >= 200 && httpStatus < 300)
    {
        responseObject = createResponseObject(response, callback.argument);
        if (callback.success)
            callback.success.call(callback.scope, responseObject);
    }
    else
    {
        responseObject = createExceptionObject(response, callback.argument, isAbort || false, isTimeout);
        if (callback.error)
            callback.error.call(callback.scope, responseObject);
    }

    return responseObject;
}

function mockRequest(method, uri, headers, callback, postData)
{
    var actionUrl = new ActionURL(uri);

    var context = HttpView.currentContext();
    var request = AppProps.instance.createMockRequest();
    request.userPrincipal = context.user;
    for (var prop in headers)
        request.addHeader(prop, headers[prop]);

    var response;
    if (method == "GET")
    {
        request.method = "GET";
        response = ViewServlet.GET(request, actionUrl, null);
    }
    else
    {
        request.method = "POST";
        throw new Error("not yet supported");
    }

    return handleTransactionResponse(response, callback);
}

/**
 * Modeled on ext-base-ajax.js Ext.lib.Ajax.request
 * except perform a <b>synchronous</b> request and returns
 * the success or error object rather than a transaction id.
 *
 * @return The success or error response object.
 */
exports.request = function (method, uri, cb, data, options)
{
    if (options)
    {
        var xmlData = options.xmlData,
            jsonData = options.jsonData,
            hs = options.headers;

        if (xmlData || jsonData) {
            if (!hs || !hs[CONTENTTYPE]) {
                hs[CONTENTTYPE] = xmlData ? "text/xml" : "application/json";
            }
            data = xmlData || (!Ext.isPrimitive(jsonData) ? Ext.encode(jsonData) : jsonData);
        }
    }
    return mockRequest(method || options.method || "POST", uri, hs, cb, data);
};

exports.serializeForm = function ()
{
    throw new Error("Unsupported operation");
};

exports.abort = function ()
{
    throw new Error("Unsupported operation");
};

exports.isCallInProgress = function ()
{
    return false;
};

exports.useDefaultHeader = true;
exports.defaultPostHeader = 'application/x-www-form-urlencoded; charset=UTF-8';
exports.useDefaultXhrHeader = true;
exports.defaultXhrHeader = 'XMLHttpRequest';
exports.transactionId = 0;

