/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('File.panel.Applet', {

    extend  : 'Ext.Component',

    applet  : null,

    appletId    : null,

    border  : false,

    initComponent: function(){
        this.html = this.markup();

        this.callParent();
    },

    setSize : function(width, height){},

    markup  : function(config){
        var applet = config || this;

        var tag = 'applet';
        var p;
        var html = '';
        var h = Ext4.util.Format.htmlEncode;

        var archive = applet.archive + '?' + LABKEY.hash + (LABKEY.devMode ? Math.random() : '');

        if (!applet.appletId)
        {
            applet.appletId = Ext4.id();
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

    onRender: function(){
        var task = {
            interval:100,
            applet:this,
            run : function()
            {
                if (this.applet.isActive())
                {
                    this.applet.fireEvent("ready");
                    Ext4.util.TaskManager.stop(this);
                }
            }
        };

        Ext4.util.TaskManager.start(task);

        this.callParent(this);
    },

    onReady: function(fn, scope){
        if(!this.isActive()){
            this.on('ready', fn, scope);
        } else {
            fn.call(scope);
        }
    },

    isActive: function(){
        if(!this.rendered){
            return false;
        }


        try
        {
            var appletContainer = this.getEl();
            var appletEl = null;
            if(appletContainer.dom){
                appletEl = Ext4.query('applet')[0];
            }
            return appletEl &&'isActive' in appletEl && appletEl.isActive();
        }
        catch (x)
        {
            return false;
        }

    },

    getApplet: function(){
        var el = this.getEl();
        var applet = null;

        if(el.dom){
            applet = Ext4.query('applet')[0];
        }

        return this.isActive() && applet ? applet : null;
    }
});
