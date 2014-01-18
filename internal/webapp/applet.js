/*
 * Copyright (c) 2008-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.Applet = Ext.extend(Ext.BoxComponent,
{
    applet : null,
    appletId: null,

    initComponent : function()
    {
        Ext.BoxComponent.superclass.initComponent.call(this);
        this.autoEl = 'div';
        this.addEvents("ready");
    },

    setSize : function(width,height)
    {
        if (typeof width == "object")
        {
            height = width.height;
            width = width.width;
        }
        if (!this.rendered)
        {
            this.width = width;
            this.height = height;
        }
        else
        {
            this.el.dom.width = width;
            this.el.dom.height = height;
            //this.el.setSize(width, height);
        }
    },

    markup : function(config)
    {
        var applet = config || this;

        var tag = 'applet';
        var p;
        var html = '';
        var h = Ext.util.Format.htmlEncode;

        var archive = applet.archive + '?' + LABKEY.hash + (LABKEY.devMode ? Math.random() : '');

//	if (navigator.appName.substr(0,8) == 'Netscape')
//		tag = 'embed';
//	if (navigator.appName.substr(0,9) == 'Microsoft')
//		tag = 'object';

        if (!applet.appletId)
        {
            applet.appletId = Ext.id();
        }

        this.appletId = applet.appletId;

        if (tag == 'embed')
        {
            html +=
                '<EMBED id="'+ applet.appletId + '"' +
                ' code="' + applet.code + '"' +
                ' archive="' + archive + '"' +
                ' width=' + applet.width +
                ' height=' + applet.height +
                ' type="application/x-java-applet;version=1.5.0"';
//		    ' pluginspage="http://java.sun.com/j2se/1.5.0/download.html">');
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + h(applet.params[p]) + '">\n';
            html += '<NOEMBED>Applet not supported</NOEMBED></EMBED>';
        }
        else if (tag == 'object')
        {
            html +=
                '<OBJECT id="' + applet.appletId + '"' +
                ' classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"' +
                ' width=' + applet.width +
                ' height=' + applet.height +
                ' codebase="http://java.sun.com/products/plugin/autodl/jinstall-1_5_0-windows-i586.cab#Version=1,5,0,0">' +
                '<PARAM name="code" value="' + applet.code + '">' +
                '<PARAM name="archive" value="' + archive + '">';
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + h(applet.params[p]) + '">\n';
            html += 'Applet not supported</OBJECT>';
        }
        else if (tag == 'applet')
        {
            html +=
                '<APPLET id="' + applet.appletId + '"' +
                ' code="' + applet.code + '"' +
                ' archive="' + archive + '"' +
                ' width=' + applet.width +
                ' height=' + applet.height +
                ' MAYSCRIPT="true" SCRIPTABLE="true">\n' +
                        '<param name="permissions" value="all-permissions" /> \n';
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + h(applet.params[p]) + '">\n';
            html += 'This feature requires the Java browser plug-in.</APPLET>';
        }
        return html;
    },

    write : function()
    {
        document.write(this.markup());
    },

    onRender : function(ct, position)
    {
        if (!this.width)
            this.width = ct.getWidth();
         if (!this.height)
            this.height = ct.getHeight();

        var html = this.markup();
        Ext.DomHelper.insertHtml(position||'beforeEnd',ct.dom,html);
        this.el = Ext.get(this.appletId);

        var task =
        {
            interval:100,
            applet:this,
            run : function()
            {
                if (this.applet.isActive())
                {
                    this.applet.fireEvent("ready");
                    Ext.TaskMgr.stop(this);
                }
            }
        };
        Ext.TaskMgr.start(task);
    },

    onReady : function(fn, scope)
    {
        if (!this.isActive())
            this.on("ready", fn, scope);
        else
            fn.call(scope);
    },

    isActive : function()
    {
        if (!this.rendered)
            return false;
        try
        {
            return this.el && this.el.dom &&'isActive' in this.el.dom && this.el.dom.isActive();
        }
        catch (x)
        {
            return false;
        }
    },

    getApplet : function()
    {
        return this.isActive() ? this.el.dom : null;
    }
});
