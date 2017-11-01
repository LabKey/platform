/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// http://stackoverflow.com/questions/494035/how-do-you-pass-a-variable-to-a-regular-expression-javascript/494122#494122
if (window.RegExp && !window.RegExp.quote) {
    RegExp.quote = function(str) {
        return (str+'').replace(/([.?*+^$[\]\\(){}|-])/g, "\\$1");
    };
}

Ext4.define('LABKEY.query.SourceEditorPanel', {
    extend: 'Ext.panel.Panel',

    title: 'Source',

    bodyStyle: 'padding: 5px',

    monitorValid: true,

    layout: {
        type: 'vbox',
        align: 'stretch'
    },

    display: 'query-response-panel',

    initComponent : function() {

        this.editorBoxId = Ext4.id();
        this.editorId = 'queryText';
        
        this.editor = Ext4.create('Ext.panel.Panel', {
            padding : '10px 0 0 0',
            border: true,
            frame: false,
            autoHeight: true,
            items : [
                {
                    id    : this.editorBoxId,
                    xtype : 'box',
                    html: '<textarea id="queryText" rows="25" cols="80" wrap="off">' // save some space at the bottom to display parse errors
                        + Ext4.util.Format.htmlEncode(this.query.queryText) + '</textarea>'
                }
            ],
            listeners : {
                afterrender : function(cmp)
                {
                    var code = Ext4.get(this.editorId);
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
                scope : this
            },
            scope : this
        });

        this.items = [
            this.initButtonBar(),
            this.editor,
            Ext4.create('Ext.panel.Panel', {
                autoScroll: true,
                flex: 1,
                border: false,
                frame: false,
                items: [{
                    id: this.display,
                    xtype : 'box',
                    border: false,
                    frame: false
                }]
            })
        ];

        this.executeTask = new Ext4.util.DelayedTask(function(args){
            this.onExecuteQuery(args.force);
        }, this, []);

        this.callParent();
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
            menu  : Ext4.create('Ext.menu.Menu', {
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
        this.callParent();
    },

    onHide : function()
    {
        this.callParent();
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
     * Returns false if the query has been executed in its current state. Otherwise, true.
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


Ext4.define('LABKEY.query.MetadataXMLEditorPanel', {
    extend: 'Ext.panel.Panel',

    constructor : function(config)
    {
        Ext4.applyIf(config, {
            title: 'XML Metadata',
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            
            // Panel Specific
            save       : function() {}
        });
        
        this.callParent([config]);
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


        this.editorBoxId = Ext4.id();
        this.editorId = 'metadataText';
        
        this.editor = Ext4.create('Ext.panel.Panel', {
            padding : '10px 0 0 0',
            border: true, frame : false,
            autoHeight: true,
            items : [
                {
                    id : this.editorBoxId,
                    xtype : 'box',
                    html: '<textarea id="metadataText" rows="25" cols="80" wrap="off">' // save some space at the bottom to display parse errors
                        + Ext4.util.Format.htmlEncode(this.query.metadataText) + '</textarea>'
                }
            ],
            listeners :
            {
                afterrender : function(cmp)
                {
                    var code = Ext4.get(this.editorId);
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
                scope : this
            },
            scope : this
        });

        items.push(this.editor);

        this.display = 'xml-response-panel';
        this.queryResponse = Ext4.create('Ext.panel.Panel', {
            autoScroll : true,
            flex : 1,
            border : false,
            frame : false,
            items : [{
                id : this.display,
                xtype: 'box',
                border : false,
                frame : false
            }]
        });

        items.push(this.queryResponse);


        this.items = items;
        
        this.callParent();
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
            var el = Ext4.get('metadataText');
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

Ext4.define('LABKEY.query.QueryEditorPanel', {
    extend: 'Ext.panel.Panel',

    constructor : function(config)
    {
        Ext4.apply(this, config);

        this._executeSucceeded = false;
        this._executing = false;

        if (!Ext4.isDefined(this.query.canEdit))
            this.query.canEdit = true;
        if (!Ext4.isDefined(this.query.canEditSql))
            this.query.canEditSql = this.query.canEdit;
        if (!Ext4.isDefined(this.query.canEditMetaData))
            this.query.canEditMetaData = this.canEdit;

        this.callParent([config]);
    },

    initComponent : function()
    {
        var items = [];

        this._dataTabId = Ext4.id();
        this.dataTab = Ext4.create('Ext.panel.Panel', {
            title : 'Data',
            autoScroll : true,
            border: false,
            items : [{
                id    : this._dataTabId,
                xtype : 'box',
                border: false,
                frame : false
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

        var _metaId = Ext4.id();
        this.sourceEditor = Ext4.create('LABKEY.query.SourceEditorPanel', {
            query : this.query,
            metadata : true,
            metaId : _metaId,
            queryEditorPanel : this // back pointer for onSave()
        });


        this.metaEditor = Ext4.create('LABKEY.query.MetadataXMLEditorPanel', {
            id    : _metaId,
            query : this.query,
            queryEditorPanel : this // back pointer for onSave()
        });

        this.tabPanel = Ext4.create('Ext.tab.Panel', {
            activeTab : this.activeTab,
            items     : [this.sourceEditor, this.dataTab, this.metaEditor]
        });

        items.push(this.tabPanel);

        this.items = items;

        this.callParent();
    },

    onRender : function()
    {
        this.callParent();
        
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

        Ext4.Ajax.request(
        {
            url    : LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method : 'POST',
            success: LABKEY.Utils.getCallbackWrapper(onSuccess, this),
            jsonData : Ext4.encode(json),
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
        var errorEls = Ext4.DomQuery.select('.error-container');
        for (var i=0; i < errorEls.length; i++) {
            Ext4.get(errorEls[i]).parent().update('');
        }
    },

    showWarnings : function(errors)
    {
        this.showErrors(errors, "labkey-warning-messages error-container");
    },

    showErrors : function(errors, css)
    {
        if (errors && errors.length > 0) {
            this.gotoError(errors[0]);
        }

        var xmlEl = Ext4.get('xml-response-panel');
        var queryEl = Ext4.get('query-response-panel');

        if (xmlEl) {
            xmlEl.update('');
        }
        if (queryEl) {
            queryEl.update('');
        }

        if (!errors || errors.length == 0) {
            return;
        }

        var inner = [];
        inner.push('<div class="' + (css || "labkey-error error-container") + '"><ul>');
        for (var e = 0; e < errors.length; e++)
        {
            var err = errors[e];
            inner.push('<li>' + err.msg);
            if (err.decorations && err.decorations.ResolveText && err.decorations.ResolveURL)
                inner.push('&nbsp;<a href="' + err.decorations.ResolveURL + '">' + Ext4.util.Format.htmlEncode(err.decorations.ResolveText) + '</a>');
            inner.push('</li>');
        }
        inner.push('</ul></div>');

        if (xmlEl) {
            xmlEl.update(inner.join(''));
        }
        if (queryEl) {
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

        var qwpEl = Ext4.get(this.getSourceEditor().display);
        if (qwpEl) { qwpEl.update(''); }

        // QueryWebPart Configuration
        var config = {
            renderTo     : qwpEl,
            schemaName   : this.query.schema,
            errorType    : 'json',
            allowChooseQuery : false,
            allowChooseView  : false,
            allowHeaderLock  : false,
            disableAnalytics : true,
            frame     : 'none',
            title     : '',
            masking   : false,
            timeout   : Ext4.Ajax.timeout, // 12451 -- control the timeout
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
        new LABKEY.QueryWebPart(config);
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