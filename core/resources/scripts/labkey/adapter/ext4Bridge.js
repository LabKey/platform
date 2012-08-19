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
var Ext = require("Ext4").Ext4;

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
console.log(headers)
console.log(postData)
    var request = ViewServlet.mockRequest(method, actionUrl, context.getUser(), headers, postData);
    var response = ViewServlet.mockDispatch(request, null);

    return handleTransactionResponse(response, callback);
}

exports.request = function(options){
    var url = options.url,
        method = options.method,
        params = options.params,
        data = '';

    var xmlData = options.xmlData,
        jsonData = options.jsonData,
        hs = options.headers;

    if (xmlData || jsonData) {
        if (!hs || !hs[CONTENTTYPE]) {
            hs[CONTENTTYPE] = xmlData ? "text/xml" : "application/json";
        }
        data = xmlData || (!Ext.isPrimitive(jsonData) ? Ext.encode(jsonData) : jsonData);
    }

    if((method == 'GET' || options.xmlData || options.jsonData) && params){
        url = Ext.String.urlAppend(url, Ext.urlEncode(params));
        params = '';
        console.log(url);
    }

    var cb = {
        scope: options.scope || this,
        success: options.success,
        failure: options.failure
    }
    return mockRequest(method || options.method || "POST", url, hs, cb, data);

//    var o = LABKEY.ExtAdapter.Ajax.request(config);
//    if (LABKEY.ExtAdapter.isObject(o))
//        return o.responseJSON || o.responseXML || o.responseText;
//    return o;
}