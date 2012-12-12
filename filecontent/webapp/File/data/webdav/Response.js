Ext4.define('File.data.webdav.Response', {

    extend : 'Ext.data.Model',

//    requires : [ 'File.data.webdav.URI' ],

    statics : {
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
            var uri = File.data.webdav.Response.getURI(v,rec);
            return uri ? uri.href : '';
        }},
        {
            name : 'fileLink', mapping : 'href', convert : function(v, rec) {
            var uri = File.data.webdav.Response.getURI(v,rec);

            if (uri && uri.file) {
                return Ext4.DomHelper.markup({
                    tag  :'a',
                    href : Ext4.util.Format.htmlEncode(uri.href + '?contentDisposition=attachment'),
                    html : Ext4.util.Format.htmlEncode(decodeURIComponent(uri.file))
                });
            }

            return '';
        }},
        {
            name : 'name', mapping : 'propstat/prop/displayname'
        },{
            name : 'text', mapping : 'propstat/prop/displayname'
        },{
            name : 'icon', mapping : 'propstat/prop/iconHref'
        },{
            name : 'href', convert : function(v,rec) { return ''; }
        }
    ]
});
