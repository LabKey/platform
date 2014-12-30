/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.query");

// http://stackoverflow.com/questions/494035/how-do-you-pass-a-variable-to-a-regular-expression-javascript/494122#494122
if (window.RegExp && !window.RegExp.quote) {
    RegExp.quote = function(str) {
        return (str+'').replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1");
    };
}

LABKEY.query.SourceEditorPanel = Ext.extend(Ext.Panel, {

    title: 'Source',

    bodyStyle: 'padding: 5px',

    monitorValid: true,

    layout: {
        type: 'vbox',
        align: 'stretch'
    },

    display: 'query-response-panel',

    initComponent : function() {

        this.editorId = 'queryText';
        this.editorBoxId = Ext.id();
        
        this.editor = new Ext.Panel({
            border: true, frame: false,
            autoHeight: true,
            items : [
                {
                    id    : this.editorBoxId,
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        style: 'width: 100%; height: 100%;',
                        wrap : 'off',
                        html : Ext.util.Format.htmlEncode(this.query.queryText)
                    }
                }
            ],
            listeners : {
                afterrender : function(cmp)
                {
                    var code = Ext.get(this.editorId);
                    var size = cmp.getSize();

                    if (code) {

                        this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                            mode            : 'text/x-plsql',
                            lineNumbers     : true,
                            lineWrapping    : true,
                            readOnly        : this.query.builtIn || !this.query.canEditSql,
                            indentUnit      : 3
                        });

                        this.codeMirror.setSize(null, size.height + 'px');
                        LABKEY.codemirror.RegisterEditorInstance(this.editorId, this.codeMirror);
                        this.doLayout(false, true);
                    }
                },
                resize : function(x)
                {
                    if (!x)
                    {
                        var h = this.getHeight() - 125;
                        this.editor.setHeight(h);
                        var box = Ext.getCmp(this.editorBoxId);
                        if (box)
                        {
                            box.setHeight(h);
                            var _f = Ext.get('frame_' + this.editorId);
                            if (_f)
                                _f.setHeight(h, false);

                            if (this.codeMirror && h > 0)
                                this.codeMirror.setSize(null, h + 'px');
                        }
                        this.doLayout(false, true);
                    }
                },
                scope : this
            },
            scope : this
        });

        this.items = [
            this.initButtonBar(),
            this.editor,
            {
                xtype: 'panel',
                autoScroll: true,
                flex: 1,
                border: false, frame: false,
                items: [{
                    id: this.getDisplay(),
                    layout: 'fit',
                    border: false, frame: false
                }]
            }
        ];

        this.executeTask = new Ext.util.DelayedTask(function(args){
            this.onExecuteQuery(args.force);
        }, this, []);

        LABKEY.query.SourceEditorPanel.superclass.initComponent.apply(this, arguments);

        this.on('resize', function(){
            this.editor.fireEvent('resize');
        });
    },

    initButtonBar : function() {
        var items = [];
        if (this.query.canEdit)
        {
            items.push({
                xtype   : 'button',
                text    : 'Save & Finish',
                cls     : 'query-button',
                tooltip : 'Save & View Results',
                handler : function() { this.onSave(true); },
                scope   : this
            });
        }
        else
        {
            items.push({
                xtype   : 'button',
                text    : 'Done',
                cls     : 'query-button',
                tooltip : 'Save & View Results',
                handler : this.onDone,
                scope   : this
            });
        }
        items.push({
            xtype   : 'button',
            text    : 'Save',
            cls     : 'query-button',
            tooltip : 'Ctrl+S',
            handler : this.onSave,
            disabled: !this.query.canEdit,
            scope   : this
        },{
            xtype   : 'button',
            text    : 'Execute Query',
            tooltip : 'Ctrl+Enter',
            cls     : 'query-button',
            handler : function(btn) { this.execute(true); },
            scope   : this
        });

        if (this.query.propEdit) {
            items.push({
                xtype  : 'button',
                text   : 'Edit Properties',
                tooltip: 'Name, Description, Sharing',
                cls    : 'query-button',
                handler: function(btn) {
                    var url = LABKEY.ActionURL.buildURL('query', 'propertiesQuery', null, {
                        schemaName : this.query.schema,
                        'query.queryName' : this.query.query
                    });
                    window.location = url;
                },
                scope : this
            });
        }

        if (this.query.metadataEdit) {
            items.push({
                xtype   : 'button',
                text    : 'Edit Metadata',
                cls     : 'query-button',
                handler : function(btn) {
                    var url = LABKEY.ActionURL.buildURL('query', 'metadataQuery', null, {
                        schemaName : this.query.schema,
                        'query.queryName' : this.query.query
                    });
                    window.location = url;
                },
                scope   : this
            });
        }

        items.push({
            xtype : 'button',
            text  : 'Help',
            cls   : 'query-button',
            style : 'float: none;',
            menu  : new Ext.menu.Menu({
                id : 'keyboard-menu',
                cls : 'extContainer',
                items : [{
                    text  : 'Shortcuts',
                    menu  : {
                        id   : '',
                        cls  : 'extContainer',
                        items: [{
                            text : 'Save&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Ctrl+S',
                            handler : this.onSave,
                            scope : this
                        },{
                            text : 'Execute&nbsp;&nbsp;&nbsp;Ctrl+Enter',
                            handler : function() { this.execute(true); },
                            scope   : this
                        },{
                            text : 'Edit&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Ctrl+E',
                            handler : function() { this.focusEditor(); },
                            scope : this
                        }]
                    }
                },'-',{
                    text  : 'SQL Reference',
                    handler : function() { window.open(this.query.help, '_blank'); },
                    scope : this
                }]
            })
        });

        return {
            xtype: 'container',
            layout: 'hbox',
            items: items
        };
    },

    getDisplay : function()
    {
        return this.display;
    },

    setDisplay : function(id)
    {
        if (id) this.display = id;           
    },

    focusEditor : function()
    {
        this.codeMirror.focus();
    },

    execute : function(force)
    {
        this.executeTask.delay(200, null, null, [{force: force}]);
    },

    onExecuteQuery : function(force)
    {
        this.queryEditorPanel.onExecuteQuery(force);
    },

    save : function(showView) {
        this.onSave(showView);
    },

    onDone : function()
    {
        this.queryEditorPanel.onDone();
    },

    onSave : function(showView)
    {
        this.queryEditorPanel.onSave(showView);
    },

    onShow : function()
    {
        LABKEY.query.SourceEditorPanel.superclass.onShow.call(this);
    },

    onHide : function()
    {
        LABKEY.query.SourceEditorPanel.superclass.onHide.call(this);
    },


    saved : function()
    {
        this.query.queryText = this.getValue();
    },

    /**
     * Returns whether the query has changed from its last saved state.
     */
    isSaveDirty : function()
    {
        if (!this.codeMirror)
            return false;
        // 12607: Prompt to confirm leaving when page isn't dirty -- watch for \s\r\n
        return this.codeMirror.getValue().toLowerCase().replace(/(\s)/g, '') != this.query.queryText.toLowerCase().replace(/(\s)/g, '');
    },

    /**
     * Returns false if the query has been executed in it's current state. Otherwise, true.
     */
    isQueryDirty : function(update)
    {
        var res = !this.cachedSql || this.cachedSql != this.getValue();
        if (update)
            this.cachedSql = this.getValue();
        return res;
    },

    getValue : function()
    {
        var queryText = this.query.queryText;
        if (this.codeMirror)
            queryText = this.codeMirror.getValue();
        return queryText;
    }
});



LABKEY.query.MetadataXMLEditorPanel = Ext.extend(Ext.Panel, {
    constructor : function(config)
    {
        Ext.applyIf(config, {
            title: 'XML Metadata',
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            
            // Panel Specific
            editorId   : 'metadataText',
            save       : function() {}
        });
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.constructor.call(this, config);
    },

    initComponent : function()
    {
        var items = [];

        if (this.query.canEdit)
        {
            items.push({
                    xtype   : 'button',
                    text    : 'Save & Finish',
                    cls     : 'query-button',
                    tooltip : 'Save & View Results',
                    handler : function() { this.onSave(true); },
                    scope   : this
                }
            );
        }
        else
        {
            items.push({
                    xtype   : 'button',
                    text    : 'Done',
                    cls     : 'query-button',
                    tooltip : 'View Results',
                    handler : this.onDone,
                    scope   : this
                }
            );
        }
        items.push({
                xtype   : 'button',
                text    : 'Save',
                cls     : 'query-button',
                disabled: !this.query.canEdit,
                handler : this.onSave,
                scope   : this
            },{
                xtype   : 'button',
                text    : 'Help',
                cls     : 'query-button',
                style   : 'float: none;',
                handler : function() { window.open(this.query.metadataHelp, '_blank'); },
                scope   : this
            }
        );


        this.editorBoxId = Ext.id();
        
        this.editor = new Ext.Panel({
            padding : '10px 0 0 0',
            border: true, frame : false,
            autoHeight: true,
            items : [
                {
                    id : this.editorBoxId,
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        wrap : 'off',
                        style: 'width: 100%; height: 100%;',
                        html : Ext.util.Format.htmlEncode(this.query.metadataText)
                    }
                }
            ],
            listeners :
            {
                afterrender : function(cmp)
                {
                    var code = Ext.get(this.editorId);
                    var size = cmp.getSize();

                    if (code) {

                        this.codeMirror = CodeMirror.fromTextArea(code.dom, {
                            mode            : 'text/xml',
                            lineNumbers     : true,
                            lineWrapping    : true,
                            indentUnit      : 2
                        });

                        this.codeMirror.setSize(null, size.height + 'px');
                        LABKEY.codemirror.RegisterEditorInstance(this.editorId, this.codeMirror);
                    }
                },
                resize : function(x)
                {
                    if (!x) {
                        var h = this.getHeight() - 160;
                        this.editor.setHeight(h);
                        var box = Ext.getCmp(this.editorBoxId);
                        if (box) {
                            box.setHeight(h);
                            var _f = Ext.get('frame_' + this.editorId);
                            if (_f) {
                                _f.setHeight(h, false);
                            }
                        }
                        this.doLayout(false, true);
                    }
                },
                scope : this
            },
            scope : this
        });

        items.push(this.editor);

        this.display = 'xml-response-panel';
        this.queryResponse = new Ext.Panel({
            autoScroll : true,
            border     : false, frame: false,
            items      : [{
                layout : 'fit',
                id     : this.display,
                border : false, frame : false
            }]
        });

        items.push(this.queryResponse);


        this.items = items;
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.initComponent.apply(this, arguments);

        this.on('resize', function()
        {
            this.editor.fireEvent('resize');
        });
    },

    setDisplay : function(id)
    {
        if (id) this.display = id;
    },

    saved : function()
    {
        this.query.metadataText = this.getValue();
    },

    isSaveDirty : function()
    {
        var _val = this.getValue().toLowerCase().replace(/(\s)/g, '');
        var _meta = this.query.metadataText.toLowerCase().replace(/(\s)/g, '');
        return _val != _meta;
    },

    getValue : function()
    {
        if (this.codeMirror)
        {
            return this.codeMirror.getValue();
        }
        else
        {
            var el = Ext.get('metadataText');
            if (el)
                return el.getValue();
        }
        return this.query.metadataText;
    },

    isQueryDirty : function(update)
    {
        if ((this.cachedMeta === undefined || this.cachedMeta.length == 0) && this.getValue().length == 0)
            return false;
        var res = !this.cachedMeta || (this.cachedMeta != this.getValue());
        if (update)
            this.cachedMeta = this.getValue();
        return res;
    },

    onDone : function()
    {
        this.queryEditorPanel.onDone();
    },

    onSave : function(showView)
    {
        this.queryEditorPanel.onSave(showView);
    }
});

LABKEY.query.QueryEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config)
    {
        Ext.apply(this, config);

        this._executeSucceeded = false;
        this._executing = false;

        if (!Ext.isDefined(this.query.canEdit))
            this.query.canEdit = true;
        if (!Ext.isDefined(this.query.canEditSql))
            this.query.canEditSql = this.query.canEdit;
        if (!Ext.isDefined(this.query.canEditMetaData))
            this.query.canEditMetaData = this.canEdit;

        LABKEY.query.QueryEditorPanel.superclass.constructor.call(this);
    },

    initComponent : function()
    {
        var items = [];

        this._dataTabId = Ext.id();
        this.dataTab = new Ext.Panel({
            title : 'Data',
            autoScroll : true,
            items : [{
                id    : this._dataTabId,
                xtype : 'panel',
                border: false, frame : false
            }],
            listeners : {
                activate : function(p)
                {
                    this.sourceEditor.setDisplay(this._dataTabId);

                    // difference between clicking on tab and clicking execute -- avoid repeating execution
                    if (!this._executing)
                        this.sourceEditor.execute();
                },
                scope : this
            }
        });

        var _metaId = Ext.id();
        this.sourceEditor = new LABKEY.query.SourceEditorPanel({
            query : this.query,
            metadata : true,
            metaId : _metaId,
            queryEditorPanel : this // back pointer for onSave()
        });


        this.metaEditor = new LABKEY.query.MetadataXMLEditorPanel({
            id    : _metaId,
            query : this.query,
            queryEditorPanel : this // back pointer for onSave()
        });

        this.tabPanel = new Ext.TabPanel({
            activeTab : this.activeTab,
            items     : [this.sourceEditor, this.dataTab, this.metaEditor]
        });

        items.push(this.tabPanel);

        this.items = items;

        LABKEY.query.QueryEditorPanel.superclass.initComponent.apply(this, arguments);
    },

    onRender : function()
    {
        LABKEY.query.QueryEditorPanel.superclass.onRender.apply(this, arguments);
        
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);
    },

    isDirty : function()
    {
        return this.sourceEditor.isSaveDirty() || this.metaEditor.isSaveDirty();
    },


    onDone : function()
    {
        if (this.query.executeUrl) {
            window.location = this.query.executeUrl;
        }
    },

    onSave : function(showView)
    {
        if (!this.query.canEdit)
            return;

        this.fireEvent('beforesave', this);

        var json = {
            schemaName   : this.query.schema,
            queryName    : this.query.query,
            ff_metadataText : this.getMetadataEditor().getValue()
        };

        if (this.query.builtIn)
            json.ff_queryText = null;
        else
            json.ff_queryText = this.getSourceEditor().getValue();

        Ext.Ajax.request(
        {
            url    : LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(onSuccess, this),
            jsonData : Ext.encode(json),
            failure: LABKEY.Utils.getCallbackWrapper(onError, this, true),
            headers : {
                'Content-Type' : 'application/json'
            },
            scope : this
        });

        function onSuccess(json, response, opts)
        {
            this.clearErrors();

            if (json.parseErrors || json.parseWarnings)
            {
                // There are errors
                var msgs = [];
                var errors = json.parseErrors || json.parseWarnings;
                for (var i=0; i<errors.length;i++)
                    msgs.push(errors[i]);
                if (errors === json.parseErrors)
                    this.showErrors(msgs);
                else
                    this.showWarnings(msgs);
            }
            else
                this.clearErrors();

            this.getMetadataEditor().saved();
            this.getSourceEditor().saved();
            this.fireEvent('save', this, true, json);

            if (showView === true && this.query.executeUrl)
                this.onDone();
        }

        function onError(json, response, opts)
        {
            this.fireEvent('save', this, false, json);
        }
    },

    clearErrors : function()
    {
        var errorEls = Ext.DomQuery.select('.error-container');
        for (var i=0; i < errorEls.length; i++) {
            Ext.get(errorEls[i]).parent().update('');
        }
    },

    showWarnings : function(errors)
    {
        this.showErrors(errors, "labkey-warning-messages error-container");
    },

    showErrors : function(errors, css)
    {
        var tabEl;
        if (errors && errors.length > 0)
        {
            this.gotoError(errors[0]);

            // default to showing errors are source tab
            var tabEl = errors[0].type == 'xml' ? this.getMetadataEditor().display : this.getSourceEditor().display;
        }

        var errorEl = tabEl ? Ext.get(tabEl) : undefined;
        var queryEl = Ext.get('query-response-panel');

        if (!errors || errors.length == 0)
        {
            if (errorEl) errorEl.update('');
            if (queryEl) queryEl.update('');
            return;
        }

        var inner = [];
        inner.push('<div class="' + (css || "labkey-error error-container") + '"><ul>');
        for (var e = 0; e < errors.length; e++)
        {
            var err = errors[e];
            inner.push('<li>' + err.msg);
            if (err.decorations && err.decorations.ResolveText && err.decorations.ResolveURL)
                inner.push('&nbsp;<a href="' + err.decorations.ResolveURL + '">' + Ext.util.Format.htmlEncode(err.decorations.ResolveText) + '</a>');
            inner.push('</li>');
        }
        inner.push('</ul></div>');

        if (errorEl)
        {
            errorEl.update('');
            errorEl.update(inner.join(''));
        }

        if (queryEl)
        {
            queryEl.update('');
            queryEl.update(inner.join(''));
        }
    },

    gotoError : function(error)
    {
        var _editor = error.type == 'xml' ? this.metaEditor : this.sourceEditor;
        this.tabPanel.setActiveTab(_editor);
        if (_editor && _editor.codeMirror && (error.line || error.col))
        {
            var pos = {ch : 0};
            if (error && error.line)
                pos.line = Math.max(0,error.line-1);
            if (error && error.col)
                pos.ch = Math.max(0,error.col);
            _editor.codeMirror.setCursor(pos);
            _editor.codeMirror.scrollIntoView(pos);

            // Highlight selected text
            if (error && error.errorStr)
                _editor.codeMirror.setSelection({ch:pos.ch, line:pos.line}, {ch: (pos.ch + error.errorStr.length), line:pos.line});
            else
                _editor.codeMirror.setSelection({ch:0, line:pos.line}, pos);
        }
    },

    onExecuteQuery : function(force)
    {
        this._executing = true;
        var sourceDirty = !this.query.builtIn && this.getSourceEditor().isQueryDirty(true);
        var metaDirty   = this.getMetadataEditor().isQueryDirty(true);
        var dirty = sourceDirty || metaDirty;
        if (!dirty && !force && this._dataLoaded) {
            this._executing = false;
            return;
        }
        this.clearErrors();
        this.getSourceEditor().setDisplay(this._dataTabId);
        this.tabPanel.setActiveTab(this.dataTab);
        this.dataTab.getEl().mask('Loading Query...', 'loading-indicator indicator-helper');

        var qwpEl = Ext.get(this.getSourceEditor().display);
        if (qwpEl) { qwpEl.update(''); }

        // QueryWebPart Configuration
        var config = {
            renderTo     : qwpEl,
            schemaName   : this.query.schema,
            errorType    : 'json',
            allowChooseQuery : false,
            allowChooseView  : false,
            allowHeaderLock  : false,
            frame     : 'none',
            title     : '',
            masking   : false,
            timeout   : Ext.Ajax.timeout, // 12451 -- control the timeout
            buttonBarPosition : 'top',    // 12644 -- only have a top toolbar
            success   : function(response) {
                this._executeSucceeded = true;
                this.dataTab.getEl().unmask();
                this._executing = false;
            },
            failure   : function(response) {
                this._executeSucceeded = false;
                this.dataTab.getEl().unmask();
                if (response && response.parseErrors)
                {
                    var errors = [];
                    for (var e=0; e < response.parseErrors.length; e++)
                        errors.push(response.parseErrors[e]);
                    this.showErrors(errors);
                }
                this._executing = false;
            },
            scope : this
        };

        // Choose queryName or SQL as source
        if (this.query.builtIn)
        {
            config.queryName = this.query.query;
        }
        else
        {
            config.sql = this.getSourceEditor().getValue();
        }

        // Apply Metadata Override
        var _meta = this.getMetadataEditor().getValue();
        if (_meta && _meta.length > 0)
            config.metadata = { type : 'xml', value: _meta };

        this._dataLoaded = true;
        var qwp = new LABKEY.QueryWebPart(config);
    },

    addTab : function(config, makeActive)
    {
        this.tabPanel.add(config);
        this.tabPanel.doLayout();
        this.doLayout();
        
        this.tabPanel.setActiveTab(this.tabPanel.items.length-1);
    },

    save : function()
    {
        this.tabPanel.getActiveTab().onSave();
    },

    openSourceEditor : function(focus)
    {
        this.tabPanel.setActiveTab(this.sourceEditor);
        if (focus) {
            this.sourceEditor.focusEditor();
        }
    },

    // Allows others to hook events on sourceEditor
    getSourceEditor : function() { return this.sourceEditor; },

    // Allows others to hook events on metadataEditor
    getMetadataEditor : function() { return this.metaEditor; }
});

Ext.reg("labkey-query-editor", LABKEY.query.QueryEditorPanel);