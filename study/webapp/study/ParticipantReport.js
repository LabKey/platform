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

        this.items = [];

        this.previewPanel = Ext4.create('Ext.panel.Panel', {
            bodyPadding : 20,
            border : false, frame : false,
            html   : '<span style="width: 400px; display: block; margin-left: auto; margin-right: auto">' +
                    ((!this.reportId && !this.allowCustomize) ? 'Unable to initialize report. Please provide a Report Identifier.' : 'Preview Area') +
                    '</span>'
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false, frame : false,
            layout   : 'fit',
            region   : 'center',
            items    : [this.previewPanel]
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
                        if (this.storedTemplateConfig)
                            this.loadReport(this.storedTemplateConfig);
                    },
                    scope   : this
                },{
                    text    : 'Save',
                    handler : function() {
                        console.log('Saving Report Configuration...not really.');
                        this.customize();
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
            this.loadReport();
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
                fieldLabel : 'Name'
            },{
                xtype       : 'combo',
                fieldLabel  : 'Dataset',
                name        : 'dataset',
                store       : store,
                editable    : false,
                queryMode      : 'local',
                displayField   : 'Name',
                valueField     : 'Name',
                triggerAction  : 'all',
                emptyText      : 'Unknown',
                listeners      : {change : this.onChangeQuery, scope : this}
            });

            var panel = Ext4.create('Ext.form.Panel', {
                itemId : 'selectionForm',
                region : 'west',
                border : false, frame : false,
                defaults : {
                    labelSeparator : ''
                },
                items  : formItems,
                width  : 300
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

    loadReport : function() {

        // TODO : This selectRows is just a stub call to simulate ajax. This needs to be replaced by a call to the getReport API using the this.reportId
        LABKEY.Query.selectRows({
            schemaName : 'core',
            queryName  : 'users',
            success    : this.onLoadReport,
            scope      : this
        });

    },

    onLoadReport : function(reportConfig) {

        // TODO : Remove this once the call in loadReport is not a stub
        reportConfig = {
            pageFields     : ['AssignedTo', {name:'AssignedTo/UserId', style:"color:purple;"}],
            pageBreakInfo  : [{name:'AssignedTo', rowspans:false}],
            gridFields     : ['Status', 'IssueId', 'Created', 'Priority', 'Title', 'Type', 'CreatedBy', 'Area', 'Milestone'],
            rowBreakInfo   : [{name:'Status', rowspans:true}],
            reportTemplate : SIMPLE_PAGE_TEMPLATE,
            queryConfig    : {
                requiredVersion : 12.1,
                schemaName      : 'issues',
                queryName       : 'Issues',
                columns         : 'AssignedTo,Status,XY,IssueId,Created,Priority,Title,Type,AssignedTo/DisplayName,AssignedTo/UserId,CreatedBy,Area,Milestone,Triage',
                sort            : 'AssignedTo/DisplayName,Status,-IssueId',
                maxRows         : 20,
                includeStyle    : true
            },
            scope : this
        };

        this.templateConfig = reportConfig;

        this.renderReport();
        if (this.openCustomize) {
            this.openCustomize = false; // just do this initially
            this.customize();
        }
    },

    renderReport : function() {

        this.templateConfig.renderTo = this.previewEl || this.previewPanel.getEl().id + '-body';

        Ext.get(this.templateConfig.renderTo).update('');
        this.templateReport = Ext4.create('LABKEY.TemplateReport', this.templateConfig);
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

        if (this.templateConfig)
            this.storedTemplateConfig = this.templateConfig;

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

        this.customMode = true;
    },

    onDisableCustomMode : function() {

        this.customMode = false;

        if (this.northPanel)
            this.northPanel.hide();

        this.setHeight(this.templateReport.getHeight());
//        this.setWidth(this.templateReport.getWidth());  This screws up the north panel if opened again
    },

    generateTemplateConfig : function() {
        this.generateTask.delay(500);
    },

    onChangeQuery : function(cmp, newVal, oldVal) {

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
                                root : 'columns'
                            }
                        }
                    });

                    this.queryStore     = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });
                    this.pageFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });
                    this.gridFieldStore = Ext4.create('Ext.data.Store', { model : 'LABKEY.query.Column' });

                    // TODO: figure out infos

                    this.columnSelectionGrid = Ext4.create('Ext.grid.Panel', {
                        title   : 'Complete Dataset',
                        store   : this.queryStore,
                        columns : [
                            { header : 'Columns', dataIndex : 'name',         flex : 1}
                        ],
                        multiSelect : true,
                        selType     : 'checkboxmodel',
                        style       : 'padding-left: 0px',
                        width       : 350,
                        height      : 200
                    });

                    var pageGrid = Ext4.create('Ext.grid.Panel', {
                        title   : 'Page Fields',
                        store   : this.pageFieldStore,
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
                        width       : 250,
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

                    this.columnSelectionGrid.on('selectionchange', function(model, recs) {
                        this.gridFieldStore.loadData(recs);
                        this.generateTemplateConfig();
                    }, this);

                    this.designerPanel.removeAll();
                    this.designerPanel.add(this.columnSelectionGrid, pageGrid, fieldGrid);
                }

                this.queryStore.loadRawData(selectRowsResult);
            }
        }, this);

    }
});
