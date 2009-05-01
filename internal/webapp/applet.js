/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.Applet = Ext.extend(Ext.BoxComponent,
{
    initComponent : function()
    {
        Ext.BoxComponent.superclass.initComponent.call(this);
        this.autoEl = true;
        this.appletId = this.appletId || this.id + 'APPLET';
        this.addEvents("ready");
    },

    setSize : function(size)
    {
        if (!this.rendered)
        {
            this.width = size.x;
            this.height = size.y;
        }
        else
        {
            this.el.setSize()
            var app = Ext.get(this.appletId);
            app.dom.width = size.x;
            app.dom.height = size.y;
        }
    },

    markup : function(config)
    {
        var applet = config || this;
        var tag = 'applet';
        var p;
        var html = '';

        var archive = applet.archive + '?' + LABKEY.hash + (LABKEY.devMode ? Math.random() : '');

//	if (navigator.appName.substr(0,8) == 'Netscape')
//		tag = 'embed';
//	if (navigator.appName.substr(0,9) == 'Microsoft')
//		tag = 'object';

        if (tag == 'embed')
        {
            html +=
                '<EMBED id="'+ applet.appletId + '"' +
                ' code="' + applet.code + '"' +
                ' archive="' + archive + '"' +
                ' width=' + (applet.width || 200) +
                ' height=' + (applet.height || 200) +
                ' type="application/x-java-applet;version=1.5.0"';
//		    ' pluginspage="http://java.sun.com/j2se/1.5.0/download.html">');
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
            html += '<NOEMBED>Applet not supported</NOEMBED></EMBED>';
        }
        else if (tag == 'object')
        {
            html +=
                '<OBJECT id="' + applet.appletId + '"' +
                ' classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"' +
                ' width=' + (applet.width ? applet.width : 200) +
                ' height=' + (applet.height ? applet.height : 200) +
                ' codebase="http://java.sun.com/products/plugin/autodl/jinstall-1_5_0-windows-i586.cab#Version=1,5,0,0">' +
                '<PARAM name="code" value="' + applet.code + '">' +
                '<PARAM name="arhive" value="' + archive + '">';
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
            html += 'Applet not supported</OBJECT>';
        }
        else if (tag == 'applet')
        {
            html +=
                '<APPLET id="' + applet.appletId + '"' +
                ' code="' + applet.code + '"' +
                ' archive="' + archive + '"' +
                ' width=' + (applet.width ? applet.width : 200) +
                ' height=' + (applet.height ? applet.height : 200) +
                ' MAYSCRIPT="true" SCRIPTABLE="true">\n';
            for (p in applet.params)
                html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
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
        LABKEY.Applet.superclass.onRender.call(this, ct, position);
        var html = this.markup();
        this.el.insertHtml("BeforeEnd", html);
    }
});
