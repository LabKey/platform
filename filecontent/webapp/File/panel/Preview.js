/*
 * Copyright (c) 2015-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('File.panel.Preview',
{
    extend : "Ext.tip.ToolTip",

    baseCls : 'labkey-panel',
    minWidth: 40,
    maxWidth: 800,
    frame: true,
    connection : new Ext4.data.Connection({
        autoAbort: true,
        method: 'GET',
        disableCaching: false
    }),

    status : 'notloaded',
    previewMarkup : null,

    initComponent : function()
    {
        this.callParent(arguments);

        this.on('beforeshow', this.loadResource, this);
    },

    show : function()
    {
        if (this.loadResource())
            this.callParent(arguments);
    },

    // we're not really ready to show anything, we have to get the resource still
    showAt : function(xy)
    {
        if (this.loadResource())
            this.callParent(arguments);
    },

    continueShow : function()
    {
        this.html = Ext4.DomHelper.markup(this.previewMarkup);
        this.update(this.html);
    },

    loadResource : function()
    {
        if (this.status == 'loaded' || this.status == 'loading')
            return true;

        var me = this;
        var record = this.record;
        var name = record.get('name');
        var uri = record.get('href');
        var contentType = record.get('contentType')||record.get('contenttype');
        var size = record.data.size;
        var headers = {};

        if (!uri || !contentType || !size)
            return false;

        if (Ext4.String.startsWith(contentType,'image/') && contentType != 'image/tiff')
        {
            this.status = 'loading';
            var image = new Image();
            image.onload = function()
            {
                me.status = "loaded";
                var img = {tag:'img', src:uri, border:'0', width:image.width, height:image.height};
                me.constrain(img, 400, 400);
                me.previewMarkup = img;
                me.continueShow();
            };
            image.src = uri;
            return true;
        }
        //IFRAME
        else if (contentType == 'text/html')
        {
            this.status = 'loading';
            var base = uri.substr(0, uri.lastIndexOf('/') + 1);
            this.connection.request({
                autoAbort:true,
                url:uri,
                headers:headers,
                method:'GET',
                disableCaching:false,
                success : function(response)
                {
                    me.status="loaded";
                    var contentType = response.getResponseHeader("Content-Type") || "text/html";
                    if (Ext4.String.startsWith(contentType,"text/"))
                    {
                        var id = 'iframePreview' + (++Ext4.Component.AUTO_ID);
                        var body = response.responseText;
                        body = Ext4.util.Format.stripScripts(body);
                        me.previewMarkup = {
                            tag: 'iframe',
                            id: id,
                            name: id,
                            width: 600,
                            height: 400,
                            frameborder: 'no',
                            src: (Ext4.isIE ? Ext4.SSL_SECURE_URL : "javascript:;")
                        };
                        me.continueShow();
                        var frame = Ext4.getDom(id);
                        if (!frame)
                        {
                            me.hide();
                        }
                        else
                        {
                            var doc = Ext4.isIE ? frame.contentWindow.document : frame.contentDocument || window.frames[id].document;
                            doc.open();
                            if (base)
                                body = '<base href="' + Ext4.util.Format.htmlEncode(base) + '" />' + body;
                            doc.write(body);
                            doc.close();
                        }
                    }
                }
            });
            return true;
        }
        // DIV
        else if (Ext4.String.startsWith(contentType,'text/') || contentType == 'application/javascript' || Ext4.String.endsWith(name,".log"))
        {
            this.status = "loading";
            if (contentType != 'text/html' && size > 10000)
                headers['Range'] = 'bytes 0-10000';
            this.connection.request({
                autoAbort:true,
                url:uri,
                headers:headers,
                method:'GET',
                disableCaching:false,
                success : function(response)
                {
                    this.status = 'loaded';
                    var contentType = response.getResponseHeader('Content-Type') || 'text/plain';
                    if (Ext4.String.startsWith(contentType, 'text/'))
                    {
                        var text = response.responseText;

                        if (headers['Range']) {
                            text += '\n. . .';
                        }

                        this.previewMarkup = {
                            tag:'div',
                            style: {
                                width: '600px',
                                height: '400px',
                                overflow: 'auto'
                            },
                            children: {
                                tag: 'pre',
                                children: Ext4.htmlEncode(text)
                            }
                        };
                        this.continueShow();
                    }
                },
                scope: this
            });
            return true;
        }
        else
        {
            this.status = 'no preview';
            return false;
        }
    },

    constrain : function(img, w, h)
    {
        var X = img.width;
        var Y = img.height;
        if (X > w)
        {
            img.width = w;
            img.height = Math.round(Y * (1.0*w/X));
        }
        X = img.width;
        Y = img.height;
        if (Y > h)
        {
            img.height = h;
            img.width = Math.round(X * (1.0*h/Y));
        }
    }
});
