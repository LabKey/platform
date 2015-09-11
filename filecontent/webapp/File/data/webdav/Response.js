/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.data.webdav.XMLResponse', {

    extend : 'Ext.data.Model',

//    requires : [ 'File.data.webdav.URI' ],

    statics : {
        readOptions : function(val) {
            var h = val.replace(/\s/g, '');
            h = h.split(',');
            var ops = {};
            for (var i=0; i < h.length; i++) {
                ops[h[i]] = true;
            }
            return ops;
        },
        getURI : function(v, rec) {
            var uri = rec.uriOBJECT || Ext4.create('File.data.webdav.URI', v);
            if (!Ext4.isIE && !rec.uriOBJECT)
                try {rec.uriOBJECT = uri;} catch (e) {}
            return uri;
        }
    },

    fields : [
        {
            name : 'uri', mapping : 'href', convert : function(v, rec) {
            var uri = File.data.webdav.XMLResponse.getURI(v,rec);
            return uri ? uri.href : '';
        }},
        {
            name : 'id', mapping : 'path', convert : function(val) { return val.replace('/_webdav', '')}
        },{
            name : 'path'
        },{
            name : 'name', mapping : 'propstat/prop/displayname', sortType: function (s) { return String(s).toLowerCase(); }
        },{
            name: 'file', mapping: 'href', type: 'boolean',
                convert : function (v, rec)
                {
                    // UNDONE: look for <collection>
                    var uri = File.data.webdav.XMLResponse.getURI(v, rec);
                    var path = uri.pathname;
                    return path.length > 0 && path.charAt(path.length-1) != '/';
                }
        },
        {name: 'href', convert : function() {return '';}},
        {name: 'text',        mapping: 'propstat/prop/displayname', convert : function(val) { return Ext4.String.htmlEncode(val); }},
        {name: 'icon',        mapping: 'propstat/prop/iconHref'},
        {name: 'created',     mapping: 'propstat/prop/creationdate',     type: 'date', dateFormat : "c"},
        {name: 'createdBy',   mapping: 'propstat/prop/createdby', convert : function(val) { return Ext4.String.htmlEncode(val); }},
        {name: 'modified',    mapping: 'propstat/prop/getlastmodified',  type: 'date'},
        {name: 'modifiedBy',  mapping: 'propstat/prop/modifiedby'},
        {name: 'size',        mapping: 'propstat/prop/getcontentlength', type: 'int'},
        {name: 'iconHref'},
        {name: 'contentType', mapping: 'propstat/prop/getcontenttype'},
        {name: 'options',     mapping: 'propstat/prop/options', convert : function(val) { return File.data.webdav.XMLResponse.readOptions(val); }},
        {name: 'directget',   mapping: 'propstat/prop/directget'},
        {name: 'directput',   mapping: 'propstat/prop/directput'}
    ]
});

Ext4.define('File.data.webdav.JSONResponse', {

    extend : 'Ext.data.Model',

    fields : [
        {name : 'creationdate', type : 'date'},
        {name : 'contentlength', type : 'int'},
        {name : 'collection', type : 'boolean'},
        {name : 'createdby'},
        {name : 'contenttype'},
        {name : 'description'},
        {name : 'actions'},
        {name : 'etag'},
        {name : 'href'},
        {name : 'id', convert : function(val) { return val.replace('/_webdav', '')}},
        {name : 'lastmodified', type : 'date'},
        {name : 'leaf', type : 'boolean'},
        {name : 'size', type : 'int'},
        {name : 'name', mapping : 'text'},
        {name : 'icon', mapping : 'iconHref'},
        {name : 'options', convert : function(val) { return File.data.webdav.XMLResponse.readOptions(val); }},
        {name : 'directget'},
        {name : 'directput'},
        {
            name: 'fileExt', mapping: 'text',
            convert : function (v, rec)
            {
                // parse the file extension from the file name
                var idx = v.lastIndexOf('.');
                if (idx != -1)
                    return v.substring(idx+1);
                return '';
            }
        },
        {
            name : 'fileLink', mapping : 'href',
            convert : function(v, rec) {
                var uri = File.data.webdav.XMLResponse.getURI(v,rec);

                if (uri && uri.file) {
                    return Ext4.DomHelper.markup({
                        tag  :'a',
                        href : Ext4.util.Format.htmlEncode(uri.href + '?contentDisposition=attachment'),
                        html : Ext4.util.Format.htmlEncode(decodeURIComponent(uri.file))
                    });
                }

                return '';
            }
        }
    ]
});
