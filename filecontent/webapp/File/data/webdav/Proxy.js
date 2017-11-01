/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.data.webdav.Proxy', {
    extend : 'Ext.data.proxy.Ajax',
    alias : 'proxy.webdav',
    alternateClassName : ['Ext.data.WebdavProxy'],

    requestFiles : false,

    propNames : ["creationdate", "displayname", "createdby", "getlastmodified", "modifiedby", "getcontentlength",
        "getcontenttype", "getetag", "resourcetype", "source", "path", "iconHref", "options",
        "directget", "directput", "absolutePath"],

    doRequest : function(operation, callback, scope) {
        var writer = this.getWriter(),
                request = this.buildRequest(operation, callback, scope);

        if (operation.allowWrite()) {
            request = writer.write(request);
        }

        // This allows for the URL path to be used
        if (operation.node && !operation.node.data.root) {
            if (operation.node.data.href) {
                request.url = operation.node.data.href;
            }
            else if (operation.node.data.uri) {
                request.url = operation.node.data.uri;
            }
        }

        Ext4.apply(request, {
            headers : this.headers,
            timeout : this.timeout,
            callback : this.createRequestCallback(request, operation, callback, scope),
            method : this.getMethod(request),
            params : this.getPropParams(operation),
            scope : this
        });

        Ext4.Ajax.request(request);

        return request;
    },

    /**
     * @private
     * Generate a string of parameters
     */
    getPropParams : function(operation) {
        var params = '';

        if (operation.action == 'read') {
            params += '&method=PROPFIND';

            for (var p=0; p < this.propNames.length; p++) {
                params += '&propname=' + this.propNames[p];
            }

            if (!this.requestFiles) {
                params += '&isCollection=1';
            }

            Ext4.iterate(operation.params, function(p, v) {
                if (p === 'depth') {
                    params += '&depth=' + v;
                }
            });
        }

        return params;
    }
});
