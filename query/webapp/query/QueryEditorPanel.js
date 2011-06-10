/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.query");

LABKEY.requiresScript("editarea/edit_area_full.js");
LABKEY.requiresCss("_images/icons.css");

Ext.QuickTips.init();

LABKEY.query.SourceEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.applyIf(config, {
            title: 'Source',
            bodyStyle: 'padding:5px',
            monitorValid: true
        });

        this.addEvents('beforeExecute', 'execute', 'executeTab', 'beforeSave', 'loaded');
        
        LABKEY.query.SourceEditorPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {

        var items = [];

        items.push({
            xtype   : 'button',
            text    : 'Save & Finish',
            cls     : 'query-button',
            handler : function() { this.onSave(true); },
            disabled: !this.query.canEdit,
            scope   : this
        },{
            xtype   : 'button',
            text    : 'Save',
            cls     : 'query-button',
            handler : this.onSave,
            disabled: !this.query.canEdit,
            scope   : this
        },{
            xtype   : 'button',
            text    : 'Execute Query',
            cls     : 'query-button',
            handler : function(btn) { this.execute(true); },
            scope   : this
        });
        
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
            xtype   : 'button',
            text    : 'SQL Help',
            cls     : 'query-button',
            style   : 'float: none;',
            target  : '_blank',
            href    : this.query.help,
            handler : function(btn) {
                window.location = this.query.help;
            },
            scope   : this
        });

        this.editorId = 'queryText';

        this.editor = new Ext.Panel({
            border: false, frame : false,
            autoHeight : true,          
            items : [
                {
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        style: 'width: 100%;',
                        wrap : 'off',
                        html : this.query.queryText
                    }
                }
            ],
            listeners : {
                afterrender : function(){
                    this.eal = editAreaLoader;
                    this.eal.init({
                        id     : this.editorId,
                        syntax : 'sql',
                        start_highlight: true,
                        plugins : "save"
                    });
                    this.doLayout(false, true);
                },
                scope : this
            },
            scope : this
        });
        
        items.push(this.editor);

        this.display = 'query-response-panel';
        this.queryResponse = new Ext.Panel({
            autoScroll : true,
            border: false, frame: false,
            items : [{
                layout : 'fit',
                id : this.display,
                border : false, frame : false
            }]
        });

        items.push(this.queryResponse);

        this.items = items;

        LABKEY.query.SourceEditorPanel.superclass.initComponent.apply(this, arguments);
    },

    setDisplay : function(id) {
        if (id) this.display = id;           
    },

    focusEditor : function() {
        this.eal.execCommand(this.editorId, 'focus');
    },

    execute : function(force) {
        this.onExecuteQuery(this.query.schema, this.eal.getValue(this.editorId), force);
    },

    onExecuteQuery : function(schema, sql, force) {

        this.fireEvent('beforeExecute', schema, sql);

        if (this.isQueryDirty() || force) {
            this.cachedSql = sql;
            var el = Ext.get(this.display);
            if (el) { el.update(''); }
            var config = {
                renderTo     : this.display,
                schemaName   : schema,
                errorType    : 'json',
                allowChooseQuery : false,
                allowChooseView  : false,
                frame     : 'none',
                title     : '',
                masking   : false,
                success   : function(response) {
                    this.showErrors();
                    this.fireEvent('loaded', true);
                },
                failure   : function(response) {
                    this.fireEvent('loaded', false);
                    if (response && response.parseErrors) {
                        var errors = [];
                        for (var e=0; e < response.parseErrors.length; e++) {
                            errors.push(response.parseErrors[e]);
                        }
                        this.showErrors(errors, this.display);
                    }
                },
                scope : this
            };

            // Choose queryName or SQL as source
            if (!this.isSaveDirty()) {
                config.queryName = this.query.query;
            }
            else { config.sql = sql; }

            // Apply Metadata Override
            if (this.metadata) {
                var _meta = this.getMetadata();
                if (_meta && _meta.length > 0) {
                    config.metadata = {
                        type : 'xml',
                        value: this.getMetadata()
                    };
                }
            }

            var qwp = new LABKEY.QueryWebPart(config);
        }
        else { this.fireEvent('loaded', true); }
    },

    getMetadata : function() {
        var el = Ext.get('metadataText'); // really  bad
        var _meta = this.query.metadataText;
        if (el) {
            _meta = el.getValue();
        }
        return _meta;
    },

    save : function(showView) {
        this.onSave(showView);
    },
    
    onSave : function(showView) {

        if (!this.query.canEdit) { return; }
        
        var _sql = this.eal.getValue(this.editorId);

        this.fireEvent('beforeSave', this.query.schema, _sql);

        if (!this.isSaveDirty()) {
            this.fireEvent('save', true);
            return;
        }
        
        Ext.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method : 'POST',
            success: onSuccess,
            jsonData : Ext.encode({
                schemaName   : this.query.schema,
                queryName    : this.query.query,
                ff_queryText : _sql,
                ff_metadataText  : this.getMetadata()
            }),
            failure: onError,
            headers : {
                'Content-Type' : 'application/json'
            },
            scope : this
        });

        function onSuccess(response, opts) {

            var json = Ext.decode(response.responseText);
            if (json.parseErrors) {
                // There are errors
                var msgs = [];
                var $ = json.parseErrors;
                for (var i=0; i<$.length;i++){
                    msgs.push($[i]);
                }
                this.showErrors(msgs);
            }
            else {
                this.showErrors();
                if (showView === true) {
                    window.location = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {
                        schemaName : this.query.schema,
                        'query.queryName' : this.query.query
                    });
                }
            }

            this.query.queryText = this.eal.getValue(this.editorId);
            
            this.fireEvent('save', true);
        }

        function onError() { this.fireEvent('save', false); }
    },

    onShow : function() {

        LABKEY.query.SourceEditorPanel.superclass.onShow.call(this);
        
        this.eal.show(this.editorId);
    },

    onHide : function() {

        this.eal.hide(this.editorId);
        
        LABKEY.query.SourceEditorPanel.superclass.onHide.call(this);
    },

    isSaveDirty : function() {
        
        return this.eal.getValue(this.editorId).toLowerCase() != this.query.queryText.toLowerCase();
    },

    isQueryDirty : function() {
        if (!this.cachedSql) {
            return true;
        }

        return this.cachedSql != this.eal.getValue(this.editorId);
    },

    showErrors : function(errors, elementId) {

        var errorEl = Ext.get(elementId);
        var queryEl = Ext.get('query-response-panel');

        if (!errors){
            if (errorEl) errorEl.update('');
            if (queryEl) queryEl.update('');
            return;
        }
        
        var inner = '<div class="labkey-error error-container"><ul>';
        for (var e = 0; e < errors.length; e++) {
            inner += '<li>' + errors[e].msg + '</li>'
        }
        inner += '</ul></div>';

        if (errorEl) {
            errorEl.update('');
            errorEl.update(inner);
        }

        if (queryEl) {
            queryEl.update('');
            queryEl.update(inner);
            
            var cmd = this.eal.execCommand;
            if (cmd) {
                // First highlight the line
                this.eal.execCommand(this.editorId, 'resync_highlight', true);
                if (errors[0] && errors[0].line) cmd(this.editorId, 'go_to_line', errors[0].line.toString());

                // Highlight selected text
                var val = this.eal.getValue(this.editorId);
                var _s = val.search(errors[0].errorStr);
                if (_s >= 0) {
                    var end = _s + errors[0].errorStr.length;
                    this.eal.setSelectionRange(this.editorId, _s, end);
                }
                this.eal.getSelectedText(this.editorId);
            }
        }
    }
});

LABKEY.query.MetadataXMLEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.applyIf(config, {
            title: 'XML',
            bodyStyle: 'padding:5px',
            autoScroll: true,
            monitorValid: true,
            
            // Panel Specific
            editorId   : 'metadataText',
            save       : function() {}
        });

        this.addEvents('beforeSave');
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {

        var items = [];

        items.push({
            xtype   : 'button',
            text    : 'Save',
            cls     : 'query-button',
            style   : 'float: none;',
            disabled: !this.query.canEdit,
            handler : this.onSave,
            scope   : this
        });

        this.editor = new Ext.Panel({
            border: false, frame : false,
            autoScroll : true,
            layout : 'fit',
            flex  : 1,
            items : [
                {
                    xtype : 'box',
                    autoEl: {
                        tag  : 'textarea',
                        id   : this.editorId,
                        rows : 17,
                        cols : 80,
                        wrap : 'off',
                        style: 'width: 100%; height: 100%;',
                        html : this.query.metadataText
                    }
                }
            ],
            listeners : {
                afterrender : function(){
                    this.eal = editAreaLoader;
                    this.eal.init({
                        id     : this.editorId,
                        syntax : 'xml',
                        start_highlight: true                        
                    });
                },
                scope : this
            },
            scope : this
        });

        items.push(this.editor);

        this.items = items;
        
        LABKEY.query.MetadataXMLEditorPanel.superclass.initComponent.apply(this, arguments);
    },

    onShow : function() {

        // Doing this due to faulty editor when tabbing back
        this.eal = editAreaLoader;
        this.eal.init({
            id     : this.editorId,
            syntax : 'xml',
            start_highlight: true
        });

        LABKEY.query.MetadataXMLEditorPanel.superclass.onShow.call(this);
    },

    onHide : function() {

        this.eal.delete_instance(this.editorId);

        LABKEY.query.MetadataXMLEditorPanel.superclass.onHide.call(this);
    },

    onSave : function() {

        var _xml = this.eal.getValue(this.editorId);

        this.fireEvent('beforeSave', this.query.schema, _xml);

        Ext.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method : 'POST',
            success: onSuccess,
            jsonData : Ext.encode({
                schemaName   : this.query.schema,
                queryName    : this.query.query,
                ff_queryText : Ext.get('queryText').getValue(),  // really, really bad
                ff_metadataText  : _xml
            }),
            failure: onError,
            headers : {
                'Content-Type' : 'application/json'
            },
            scope : this
        });

        function onSuccess(response, opts) {
            
            this.fireEvent('save', true);
        }

        function onError(x,y,z) {

            this.fireEvent('save', false);
        }
    }    
});

LABKEY.query.QueryEditorPanel = Ext.extend(Ext.Panel, {

    constructor : function(config) {

        Ext.apply(this, config);

        LABKEY.query.QueryEditorPanel.superclass.constructor.call(this);
    },

    initComponent : function() {

        var items = [];

        var _dataTabId = Ext.id();
        this.dataTab = new Ext.Panel({
            title : 'Data',
            autoScroll : true,
            items : [{
                id    : _dataTabId,
                xtype : 'panel',
                border: false, frame : false
            }],
            listeners : {
                activate : function(p) {
                    this.sourceEditor.setDisplay(_dataTabId);
                    this.sourceEditor.execute();
                },
                scope : this
            }
        });
        
        this.sourceEditor = new LABKEY.query.SourceEditorPanel({
            query : this.query,
            metadata : true
        });

        this.sourceEditor.on('beforeExecute', function(schema, sql){
            this.sourceEditor.setDisplay(_dataTabId);
            this.tabPanel.setActiveTab(this.dataTab);
            this.dataTab.getEl().mask('Loading Custom Query...');
        }, this);

        this.sourceEditor.on('loaded', function(loaded){
            this.dataTab.getEl().unmask();
            if (!loaded) this.openSourceEditor();
        }, this);

        this.metaEditor = new LABKEY.query.MetadataXMLEditorPanel({
            query : this.query
        });

        this.tabPanel = new Ext.TabPanel({
            activeTab : 0,
            width     : '100%',
            items     : [this.sourceEditor, this.dataTab, this.metaEditor]
        });

        items.push(this.tabPanel);

        this.items = items;

        LABKEY.query.QueryEditorPanel.superclass.initComponent.apply(this, arguments);
    },

    addTab : function(config, makeActive) {

        this.tabPanel.add(config);
        this.tabPanel.doLayout();
        this.doLayout();
        
        this.tabPanel.setActiveTab(this.tabPanel.items.length-1);
    },

    save : function() {
        this.tabPanel.getActiveTab().onSave();
    },

    openSourceEditor : function(focus) {
        this.tabPanel.setActiveTab(this.sourceEditor);
        if (focus) {
            this.sourceEditor.focusEditor();
        }
    },

    // Allows others to hook events on sourceEditor
    getSourceEditor : function() { return this.sourceEditor; },

    getMetadataEditor : function() { return this.metaEditor; }
});