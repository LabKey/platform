/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.data.webdav.URI', {

    statics : {
        options:
        {
            strictMode : false,
            key : [
                "source","protocol","host","userInfo","user",
                "password","hostname","port","relative","pathname",
                "directory","file","search","hash"
            ],
            q : {
                name:   "query",
                parser: /(?:^|&)([^&=]*)=?([^&]*)/g
            },
            parser : {
                strict: /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
                loose:  /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*):?([^:@]*))?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
            }
        }
    },

    constructor : function(u) {

        if (typeof u == "string")
            this.parse(u);
        else if (typeof u == "object")
            Ext4.apply(this,u);

        this.options = Ext4.apply({}, File.data.webdav.URI.options);  // clone
    },

    toString : function() {
        return this.protocol + "://" + this.host + this.pathname + this.search;
    },

    parse : function(str) {
        var	o   = Ext4.clone(File.data.webdav.URI.options);
        var m   = o.parser[o.strictMode ? "strict" : "loose"].exec(str);
        var uri = this || {};
        var i   = 14;

        while (i--)
            uri[o.key[i]] = m[i] || "";

        if (!uri.protocol)
        {
            var l = window.location;
            uri.protocol = uri.protocol || l.protocol;
            uri.port = uri.port || l.port;
            uri.hostname = uri.hostname || l.hostname;
            uri.host = uri.host || l.host;
        }
        if (uri.protocol && uri.protocol.charAt(uri.protocol.length-1) == ":")
            uri.protocol = uri.protocol.substr(0,uri.protocol.length - 1);

        uri[o.q.name] = {};
        uri[o.key[12]].replace(o.q.parser, function ($0, $1, $2)
        {
            if ($1) uri[o.q.name][$1] = $2;
        });
        uri.href = this.protocol + "://" + this.host + this.pathname + this.search;
        return uri;
    }
});