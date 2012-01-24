/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);

Ext4.define('LABKEY.ext4.ParticipantReport', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            layout : 'border',
            frame  : false,
            border : false,
            editable : false,
            allowCustomize : false
        });

        this.callParent([config]);
    },

    initComponent : function() {

        this.customMode = false;
        this.reportConfig = {};

        this.items = [];

        this.previewPanel = Ext4.create('Ext.panel.Panel', {
            bodyPadding : 20,
            autoScroll  : true,
            border : false, frame : false,
            html   : '<span style="width: 400px; display: block; margin-left: auto; margin-right: auto">' +
                    ((!this.reportId && !this.allowCustomize) ? 'Unable to initialize report. Please provide a Report Identifier.' : 'Preview Area') +
                    '</span>'
        });

        this.exportForm = Ext4.create('Ext.form.Panel', {
            border : false, frame : false,
            standardSubmit  : true,
            items           : [
                {xtype : 'hidden', name : 'htmlFragment'},
                {xtype : 'hidden', name : 'X-LABKEY-CSRF'}
            ]
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false, frame : false,
            layout   : 'fit',
            region   : 'center',
            tbar     :  [{
                text    : 'Export',
                menu    : [{
                    text    : 'To Excel',
                    handler : function(){this.exportToXls();},
                    scope   : this}]
            }],
            items    : [this.previewPanel, this.exportForm]
        });
        this.items.push(this.centerPanel);

        if (this.allowCustomize) {
            this.northPanel = Ext4.create('Ext.panel.Panel', {
                bodyPadding : 20,
                hidden   : true,
                preventHeader : true,
                frame : false,
                region   : 'north',
                layout   : 'hbox',
                buttons  : [{
                    text    : 'Cancel',
                    handler : function() {
                        this.customize();
                        // revert back to the last config (if any)
                        if (this.storedTemplateConfig)
                            this.onLoadReport(this.storedTemplateConfig);
                    },
                    scope   : this
                },{
                    text    : 'Save',
                    handler : function() {
                        this.saveReport();
                    },
                    scope   : this
                }]
            });
            this.items.push(this.northPanel);
        }

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        // call generateTemplateConfig() to use this task
        this.generateTask = new Ext4.util.DelayedTask(function(){

            // TODO: Get rid of all the hard codes, might consider just not rendering until a pageField is specified
            // TODO: Make pageBreakInfo and rowBreakInfo work (not sure about a proper UI for this) -- might not be necessary for sprint 2
            var config = {
                pageFields : !this.pageFieldStore.getCount() ? ['ParticipantId'] : [],
                pageBreakInfo : !this.pageFieldStore.getCount() ? [{name: 'ParticipantId', rowspans: false}] : [],
                gridFields : [],
                rowBreakInfo : [],
                reportTemplate : SIMPLE_PAGE_TEMPLATE,
                queryConfig : {
                    requiredVersion : 12.1,
                    schemaName : 'Study',
                    queryName  : this.northPanel.getComponent('selectionForm').getValues().dataset,
                    columns    : '',
                    sort       : 'ParticipantId,',
                    includeStyle : true
                }
            };

            for (var i=0; i < this.pageFieldStore.getCount(); i++) {
                if (i==0)
                    config.pageBreakInfo.push({name : this.pageFieldStore.getAt(i).data.name, rowspan: false});
                config.pageFields.push(this.pageFieldStore.getAt(i).data.name);
            }

            for (i=0; i < this.gridFieldStore.getCount(); i++) {
                config.gridFields.push(this.gridFieldStore.getAt(i).data.name);
            }

            var sep = '';
            for (i=0; i < this.queryStore.getCount(); i++) {
                config.queryConfig.columns += sep + this.queryStore.getAt(i).data.name;
                sep = ','
            }

            this.templateConfig = config;
            this.renderReport();
        }, this);

        if (this.reportId) {
            this.loadReport(this.reportId);
        }
        else if (this.allowCustomize) {
            this.customize();
        }

        this.callParent([arguments]);
    },

    initNorthPanel : function(queryResults) {

        if (this.allowCustomize) {
            var formItems = [];

            var config = {
                autoLoad: true,
                data: queryResults.rows,
                fields : [
                    {name : 'DataSetId',       type : 'int', mapping : 'DataSetId.value'},
                    {name : 'Name',                          mapping : 'Name.value'},
                    {name : 'DemographicData', type : 'boolean'},
                    {name : 'KeyPropertyName'}
                ]
            };
            var store = Ext4.create('Ext.data.Store', config);

            formItems.push({
                xtype      : 'textfield',
                fieldLabel : 'Name',
                width      : 300,
                allowBlank : false,
                value      : this.reportConfig.name,
                listeners  : {change : function(cmp, value) {this.reportConfig.name = value;}, scope : this}
            },{
                xtype       : 'combo',
                fieldLabel  : 'Dataset',
                name        : 'dataset',
                store       : store,
                width       : 300,
                editable    : false,
                queryMode      : 'local',
                value          : this.reportConfig.queryName,
                displayField   : 'Name',
                valueField     : 'Name',
                triggerAction  : 'all',
                emptyText      : 'Unknown',
                listeners      : {
                    change : {fn: this.onChangeQuery, scope : this},
                    render : {fn: function(cmp){
                        if (this.reportConfig.queryName)
                            this.onChangeQuery(cmp, this.reportConfig.queryName, null);
                        },
                        scope : this
                    }
                }
            });

            var panel = Ext4.create('Ext.form.Panel', {
                itemId : 'selectionForm',
                region : 'west',
                border : false, frame : false,
                defaults : {
                    labelSeparator : ''
                },
                items  : formItems,
                width  : 320
            });

            this.designerPanel = Ext4.create('Ext.panel.Panel', {
                height : 225,
                border : false, frame : false,
                region : 'center',
                layout : 'hbox',
                defaults : {
                    style : 'padding-left: 20px'
                },
                flex   : 4,
                items  : [{
                    border : false, frame: false,
                    html : 'Choose a Dataset to get Started'
                }],
                listeners : {
                    render : function() {
                        if (this.templateConfig) {
                            console.log('here is where we can load a stored config');
                        }
                    },
                    scope : this
                },
                scope : this
            });

            this.northPanel.add(panel, this.designerPanel);
            this.northPanel.show(); // might be hidden
        }

        return this.northPanel;
    },

    loadReport : function(reportId) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('study-reports', 'getParticipantReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                this.onLoadReport(Ext4.decode(response.responseText));
            },
            failure : function(response){
                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
            },
            scope : this
        });
    },

    onLoadReport : function(report) {

        this.templateConfig = report.reportConfig.json;
        this.templateConfig.reportTemplate = SIMPLE_PAGE_TEMPLATE;

        this.reportConfig = report.reportConfig;
        this.reportConfig.json = undefined;

        this.renderReport();
/*
        if (this.openCustomize) {
            this.openCustomize = false; // just do this initially
            this.customize();
        }
*/
    },

    renderReport : function() {

        var template = Ext4.clone(this.templateConfig);
        template.renderTo = this.previewEl || this.previewPanel.getEl().id + '-body';

        Ext.get(template.renderTo).update('');
        this.templateReport = Ext4.create('LABKEY.TemplateReport', template);
    },

    isCustomizable : function() {
        return this.allowCustomize;
    },

    // private
    _inCustomMode : function() {
        return this.customMode;
    },

    customize : function() {

        if (!this.isCustomizable())
            return false;

        this.fireEvent((this._inCustomMode() ? 'disableCustomMode' : 'enableCustomMode'), this);
    },

    onEnableCustomMode : function() {

        // stash away the current config
        if (this.templateConfig && this.reportConfig)
        {
            var report = {
                reportConfig : this.reportConfig
            };
            report.reportConfig.json = this.templateConfig;
            this.storedTemplateConfig = report;
        }

        // if the north panel hasn't been fully populated, initialize the dataset store, else
        // just show the panel
        if (!this.designerPanel)
        {
            // offer a choice of non-demographic datasets without an external key field
            LABKEY.Query.selectRows({
                requiredVersion : 12.1,
                schemaName      : 'study',
                queryName       : 'Datasets',
                scope           : this,
                filterArray : [
                    LABKEY.Filter.create('DemographicData', false),
                    LABKEY.Filter.create('KeyPropertyName', false, LABKEY.Filter.Types.ISBLANK)
                ],
                success : this.initNorthPanel
            });
        }
        else
            this.northPanel.show();
        
        this.customMode = true;
    },

    onDisableCustomMode : function() {

        this.customMode = false;

        if (this.northPanel)
            this.northPanel.hide();

//        this.setHeight(this.templateReport.getHeight());
//        this.setWidth(this.templateReport.getWidth());  This screws up the north panel if opened again
    },

    generateTemplateConfig : function() {
        this.generateTask.delay(500);
    },

    onChangeQuery : function(cmp, newVal, oldVal) {

        this.reportConfig.schemaName = 'study';
        this.reportConfig.queryName = newVal;

        LABKEY.Query.getQueryDetails({
            schemaName: 'study',
            queryName: newVal,
            scope: this,
            success: function(selectRowsResult) {

                if (!this.queryStore) {

                    // This model is 'modelled' after a column object found in LABKEY.Query.SelectRowsResults
                    var model = Ext4.define('LABKEY.query.Column', {
                        extend : 'Ext.data.Model',
                        fields : [
                            {name : 'fieldKey'},
                            {name : 'name'},
                            {name : 'caption'},
                            {name : 'description'},
                            {name : 'displayField'},
                            {name : 'measure',   type : 'boolean'},
                            {name : 'dimension', type : 'boolean'}
                        ],
                        proxy : {
                            type : 'memory',
                            reader : {
                                type : 'json',
                                root : 'defaultView.columns'
                            }
                        }
                    });

                    this.queryStore     = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });
                    this.pageFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });
                    this.gridFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });

                    // TODO: figure out infos

                    this.columnSelectionGrid = Ext4.create('Ext.grid.Panel', {
                        //title   : 'Complete Dataset',
                        store   : this.queryStore,
                        flex    : 1.4,
                        columns : [
                            { header : 'Columns', dataIndex : 'name',         flex : 1}
                        ],
                        tbar     :  [{
                            text    : 'Select Columns For',
                            menu    : [{
                                text    : 'Grid Fields',
                                handler : function(){
                                    var recs = this.columnSelectionGrid.getSelectionModel().getSelection();
                                    this.gridFieldStore.loadData(recs);
                                    this.generateTemplateConfig();
                                },
                                scope   : this
                            },{
                                text    : 'Page Fields',
                                handler : function(){
                                    var recs = this.columnSelectionGrid.getSelectionModel().getSelection();
                                    this.pageFieldStore.loadData(recs);
                                    this.generateTemplateConfig();
                                },
                                scope   : this
                            }]
                        }],
                        multiSelect : true,
                        selType     : 'checkboxmodel',
                        style       : 'padding-left: 0px',
                        height      : 200
                    });

                    var pageGrid = Ext4.create('Ext.grid.Panel', {
                        title   : 'Page Fields',
                        store   : this.pageFieldStore,
                        flex    : 1.2,
                        columns : [
                            { header : 'Columns', dataIndex : 'name', flex : 1},
                            {
                                xtype : 'actioncolumn',
                                width : 40,
                                align : 'center',
                                sortable : false,
                                items : [{
                                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                                    tooltip : 'Delete'
                                }],
                                listeners : {
                                    click : function(col, grid, idx) {
                                        this.pageFieldStore.removeAt(idx);
                                        this.generateTemplateConfig();
                                    },
                                    scope : this
                                },
                                scope : this
                            }
                        ],
                        height      : 200,
                        viewConfig  : {
                            emptyText : 'Defaults to ParticipantId',
                            plugins   : [{
                                ddGroup : 'ColumnSelection',
                                ptype   : 'gridviewdragdrop',
                                dragText: 'Drag and drop to reorder'
                            }],
                            listeners : {
                                drop : function(node, data, model, pos) {
                                    this.generateTemplateConfig();
                                },
                                scope: this
                            },
                            scope : this
                        }
                    });

                    var fieldGrid = Ext4.create('Ext.grid.Panel', {
                        title   : 'Grid Fields',
                        store   : this.gridFieldStore,
                        flex    : 1.2,
                        columns : [
                            { header : 'Columns', dataIndex : 'name', flex : 1},
                            {
                                xtype : 'actioncolumn',
                                width : 40,
                                align : 'center',
                                sortable : false,
                                items : [{
                                    icon    : LABKEY.contextPath + '/' + LABKEY.extJsRoot_40 + '/resources/themes/images/access/qtip/close.gif',
                                    tooltip : 'Delete'
                                }],
                                listeners : {
                                    click : function(col, grid, idx) {
                                        this.gridFieldStore.removeAt(idx);
                                        this.generateTemplateConfig();
                                    },
                                    scope : this
                                },
                                scope : this
                            }
                        ],
                        width       : 250,
                        height      : 200,
                        viewConfig  : {
                            plugins   : [{
                                ddGroup  : 'ColumnSelection',
                                ptype    : 'gridviewdragdrop',
                                dragText : 'Drag and drop to reorder',
                                copy : true
                            }],
                            listeners : {
                                drop : function() {
                                    this.generateTemplateConfig();
                                },
                                drag : function() {
                                    console.log('dragging');
                                },
                                scope: this
                            },
                            scope : this
                        }
                    });

/*
                    this.columnSelectionGrid.on('selectionchange', function(model, recs) {
                        this.gridFieldStore.loadData(recs);
                        this.generateTemplateConfig();
                    }, this);
*/

                    this.designerPanel.removeAll();
                    this.designerPanel.add(this.columnSelectionGrid, fieldGrid, pageGrid);
                }

                this.queryStore.loadRawData(selectRowsResult);
            }
        }, this);

    },

    saveReport : function() {

        console.log('Saving Report Configuration.');
        var form = this.northPanel.getComponent('selectionForm').getForm();
        
        if (form.isValid())
        {
            var data = this.reportConfig;
            data.json = this.templateConfig;
            //data = Ext4.copyTo(data, this.templateConfig, 'gridFields,pageBreakInfo,pageFields,rowBreakInfo');

            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('study-reports', 'saveParticipantReport.api'),
                method  : 'POST',
                jsonData: data,
                success : function(resp){
                    Ext4.Msg.alert('Success', 'Report : ' + this.reportConfig.name + ' saved successfully', function(){
                        var o = Ext4.decode(resp.responseText);

                        // Modify Title (hack: hardcode the webpart id since this is really not a webpart, just
                        // using a webpart frame, will need to start passing in the real id if this ever
                        // becomes a true webpart
                        var titleEl = Ext.query('th[class=labkey-wp-title-left]:first', 'webpart_-1');
                        if (titleEl && (titleEl.length >= 1))
                        {
                            titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(this.reportConfig.name);
                        }

                        this.reportId = o.reportId;
                        this.customize();
                        
                    }, this);
                },
                failure : function(resp){
                    Ext4.Msg.alert('Failure', Ext4.decode(resp.responseText).exception);
                },
                scope : this
            });
        }
        else
            Ext4.Msg.alert('Save failed', 'Please enter all required information.');

    },

    exportToXls : function() {

        var markup = this.templateReport.getMarkup();
        if (markup)
        {
            this.exportForm.getForm().setValues({htmlFragment : markup, 'X-LABKEY-CSRF' : LABKEY.CSRF});
            this.exportForm.submit({
                scope: this,
                url    : LABKEY.ActionURL.buildURL('experiment', 'convertHtmlToExcel'),
                failure: function(response, opts){
                    Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
                }
            });
        }
    }
});
