/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
var LABKEY = {};
LABKEY.ExtAdapter = require("ExtAdapter").Adapter;

const CONTENTTYPE = "Content-Type";

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

function handleTransactionResponse(response, callback)
{
    var httpStatus = response.status;
    var responseObject = createResponseObject(response, callback.argument);
    var cb = (httpStatus >= 200 && httpStatus < 300) ? callback.success : callback.failure;
    if (cb)
        cb.call(callback.scope, responseObject);

    return responseObject;
}

function mockRequest(method, uri, headers, callback, postData)
{
    var actionUrl = new ActionURL(uri);

    var context = HttpView.currentContext(); 
    var request = ViewServlet.mockRequest(method, actionUrl, context.getUser(), headers, postData);
    var response = ViewServlet.mockDispatch(request, null);

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
            data = xmlData || (!LABKEY.ExtAdapter.isPrimitive(jsonData) ? LABKEY.ExtAdapter.encode(jsonData) : jsonData);
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

