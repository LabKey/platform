/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4Sandbox(true);

LABKEY.requiresCss("study/DataViewsPanel.css");
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresScript("vis/genericOptionsPanel.js");
LABKEY.requiresScript("vis/genericChartOptionsPanel.js");
LABKEY.requiresScript("vis/mainTitleOptionsPanel.js");
LABKEY.requiresScript("vis/genericChartAxisPanel.js");

Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            layout    : 'border',
            editable  : false,
            minWidth  : 800
        });

        // delayed task to redraw the chart
        this.updateChartTask = new Ext4.util.DelayedTask(function(){

            if (this.isConfigurationChanged())
            {
                this.viewPanel.getEl().mask('loading data...');

                if (!this.initialColumnList)
                {
                    params = {
                        schemaName  : this.schemaName,
                        queryName   : this.queryName,
                        includeCohort : true,
                        includeParticipantCategory : true,
                        includeDefault : this.savedColumns ? false : true
                    };
                    Ext4.Ajax.request({
                        url     : LABKEY.ActionURL.buildURL('visualization', 'getGenericReportColumns.api'),
                        method  : 'GET',
                        params  : params,
                        success : function(response){

                            var o = Ext4.decode(response.responseText);
                            if (this.savedColumns)
                                this.initialColumnList = o.columns.concat(this.savedColumns);
                            else
                                this.initialColumnList = o.columns;
                            LABKEY.Query.selectRows(this.getQueryConfig());
                        },
                        failure : this.onFailure,
                        scope   : this
                    });

                }
                else
                    LABKEY.Query.selectRows(this.getQueryConfig());
            }

        }, this);

        this.reportLoaded = true;
        this.typeToLabel = {
            auto_plot : 'Auto Plot Report',
            scatter_plot : 'Scatter Plot Report',
            box_plot : 'Box Plot Report'
        }
        this.callParent([config]);
    },

    initComponent : function() {

        this.items = [];
        this.showOptionsBtn = Ext4.create('Ext.button.Button', {
            text: 'Options',
            handler: function(){
                this.optionsWindow.setVisible(this.optionsWindow.isHidden());
            },
            scope: this
        });

        this.exportPdfBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            disabled: true,
            scope: this
        });

        this.saveBtn = Ext4.create('Ext.button.Button', {
            text: "Save",
            hidden: this.hideSave,
            handler: function(){
                this.saveWindow.show();
            },
            scope: this
        });

        this.saveAsBtn = Ext4.create('Ext.button.Button', {
            text: "Save As",
            hidden  : this.isNew() || this.hideSave,
            handler: function(){
                this.onSaveAs();
            },
            scope: this
        });

        this.saveWindow = Ext4.create('Ext.window.Window', {
            title: 'Save Chart',
            width: 500,
            autoHeight : true,
            closeAction: 'hide',
            cls: 'data-window',
            layout: 'fit',
            items: [this.getSavePanel()],
            listeners: {
                scope: this,
                show: function(){
                    this.viewPanel.getEl().mask();
                },
                hide: function(){
                    this.viewPanel.getEl().unmask();
                }
            }
        });

        this.toggleBtn = Ext4.create('Ext.button.Button', {
            text:'View Data',
            width: 95,
            handler: function(){
                if(this.viewPanel.isHidden()){
                    this.centerPanel.getLayout().setActiveItem(0);
                    this.toggleBtn.setText('View Data');
                    this.showOptionsBtn.show();
                    this.exportPdfBtn.show();
                } else {
                    this.centerPanel.getLayout().setActiveItem(1);
                    this.toggleBtn.setText('View Chart');
                    this.showOptionsBtn.hide();
                    this.exportPdfBtn.hide();
                }
            },
            scope: this
        });

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            border   : false, frame : false,
            region   : 'center',
            header : false,
            headerPosition : 'left',
            layout: {
                type:'card',
                deferredRender: true
            },
            activeItem: 0,
            items    : [this.getViewPanel(), this.getDataPanel()],
            tbar: [
                this.toggleBtn,
                this.exportPdfBtn,
                this.showOptionsBtn,
                '->',
                this.saveBtn,
                this.saveAsBtn
            ]
        });

        Ext4.define('MeasureModel',{
            extend: 'Ext.data.Model',
            fields: [
                {name: 'label', mapping: 'shortCaption', type: 'string'},
                {name: 'name', type: 'string'},
                {name: 'hidden', type: 'boolean'},
                {name: 'measure', type: 'boolean'},
                {name: 'type'}
            ]
        });

        this.yMeasureStore = Ext4.create('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store){
                    this.yMeasureStore.filterBy(function(record, id){
                        var type = record.get('type');
                        var hidden = record.get('hidden');
                        return (!hidden && (type == 'int' || type == 'float' || type == 'double'))
                    });
                    if(this.yAxisMeasure){
                        var measure = this.yMeasureStore.findRecord('name', this.yAxisMeasure.name);
                        if(measure){
                            this.yMeasureGrid.getSelectionModel().select(measure, false, true);
                            this.yMeasureChoice = this.yMeasureGrid.getSelectionModel().getSelection()[0].data;
                            this.yMeasurePanel.selectionChange(true);
                        }
                    }
                },
                scope: this
            }
        });

        this.xMeasureStore = Ext4.create('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store){
                    this.xMeasureStore.filterBy(function(record, id){
                        var hidden = record.get('hidden');
                        return !hidden
                    });
                    if(this.xAxisMeasure){
                        var measure = this.xMeasureStore.findRecord('name', this.xAxisMeasure.name);
                        if(measure){
                            this.xMeasureGrid.getSelectionModel().select(measure, false, true);
                            this.xMeasureChoice = this.xMeasureGrid.getSelectionModel().getSelection()[0].data;
                            this.xMeasurePanel.selectionChange(true);
                        }
                    }
                },
                scope: this
            }
        });

        this.yMeasureGrid = Ext4.create('Ext.grid.Panel', {
            store: this.yMeasureStore,
            width: 360,
            height: 200,
            sortableColumns: false,
            enableColumnHide: false,
            columns: [
                {header: 'Measure', dataIndex: 'label', flex: 1}
            ],
            listeners: {
                select: function(selModel, record, index){
                    this.yMeasureChoice = selModel.getSelection()[0].data;
                    this.yOkBtn.setDisabled(false);
                    this.yMeasurePanel.selectionChange();
                },
                scope: this
            }
        });

        this.xMeasureGrid = Ext4.create('Ext.grid.Panel', {
            store: this.xMeasureStore,
            width: 360,
            height: 200,
            sortableColumns: false,
            enableColumnHide: false,
            columns: [
                {header: 'Measure', dataIndex: 'label', flex: 1}
            ],
            listeners: {
                select: function(selModel, record, index){
                    this.xMeasureChoice = selModel.getSelection()[0].data;
                    this.xOkBtn.setDisabled(false);
                    this.xMeasurePanel.selectionChange();
                },
                scope: this
            }
        });

        this.yOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.yMeasureWindow.hide();
                this.viewPanel.getEl().unmask();
                this.yMeasurePanel.checkForChangesAndFireEvents();
            },
            scope: this
        });

        this.xOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.xMeasureWindow.hide();
                this.viewPanel.getEl().unmask();
                this.xMeasurePanel.checkForChangesAndFireEvents();
            },
            scope: this
        });

        this.yCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.viewPanel.getEl().unmask();
                this.yMeasureWindow.close();
            },
            scope: this
        });

        this.xCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.viewPanel.getEl().unmask();
                this.xMeasureWindow.close();
            },
            scope: this
        });

        // TODO: pull out all of the measure window relate components and make something like:
        // Labkey.vis.GenericMeasurePanel so we have less duplicate code.

        this.yMeasurePanel = Ext4.create('LABKEY.vis.GenericChartAxisPanel',{
            border: false,
            frame: false,
            store: this.yMeasureStore,
            queryName: this.queryName,
            measureGrid: this.yMeasureGrid,
            buttons: [this.yOkBtn, this.yCancelBtn],
            listeners: {
                'chartDefinitionChanged': function(){
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.yAxisMeasure = this.yMeasureChoice;
                    this.chartDefinitionChanged.delay(250);
                },
                scope: this
            }
        });

        this.yMeasureWindow = Ext4.create('Ext.window.Window', {
            title: 'Y Axis',
            cls: 'data-window',
            border: false,
            frame: false,
            width: 400,
            resizable: false,
            closeAction: 'hide',
            items: [
                this.yMeasurePanel
            ],
            listeners: {
                show: function(){
                    this.initialPanelValues = this.yMeasurePanel.getPanelOptionValues();
                    this.initialPanelValues.measure = this.yMeasureGrid.getSelectionModel().getLastSelected();

                    this.yOkBtn.setDisabled(!this.yMeasureGrid.getSelectionModel().hasSelection());

                    if (!this.yAxisMeasure)
                    {
                        this.yCancelBtn.hide();
                        this.yMeasurePanel.hideNonMeasureElements();
                    }
                    else
                    {
                        this.yCancelBtn.show();
                        this.yMeasurePanel.showNonMeasureElements();
                        this.yMeasurePanel.disableScaleAndRange();
                    }
                },
                hide: function(){
                    this.initialPanelValues = null;
                    this.viewPanel.getEl().unmask();
                },
                beforeclose: function(){
                    // on close, we don't apply changes and let the panel restore its state
                    if (this.initialPanelValues)
                    {
                        this.yMeasureGrid.getSelectionModel().select([this.initialPanelValues.measure], false, true);
                        this.yMeasurePanel.restoreValues(this.initialPanelValues);
                    }
                },
                scope: this
            }
        });

        this.xMeasurePanel = Ext4.create('LABKEY.vis.GenericChartAxisPanel',{
            border: false,
            frame: false,
            store: this.xMeasureStore,
            measureGrid: this.xMeasureGrid,
            queryName: this.queryName,
            buttons: [this.xOkBtn, this.xCancelBtn],
            listeners: {
                'chartDefinitionChanged': function(){
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.xAxisMeasure = this.xMeasureChoice;
                    this.chartDefinitionChanged.delay(250);
                },
                scope: this
            }
        });

        this.xMeasureWindow = Ext4.create('Ext.window.Window', {
            title: 'X Axis',
            cls: 'data-window',
            border: false,
            frame: false,
            width: 400,
            resizable: false,
            closeAction: 'hide',
            items: [this.xMeasurePanel],
            listeners: {
                show: function(){
                    this.initialPanelValues = this.xMeasurePanel.getPanelOptionValues();
                    this.initialPanelValues.measure = this.xMeasureGrid.getSelectionModel().getLastSelected();

                    this.xOkBtn.setDisabled(!this.xMeasureGrid.getSelectionModel().hasSelection());

                    if (!this.xAxisMeasure)
                    {
                        this.xCancelBtn.hide();
                        this.xMeasurePanel.hideNonMeasureElements();
                    }
                    else
                    {
                        this.xCancelBtn.show();
                        this.xMeasurePanel.showNonMeasureElements();
                        this.xMeasurePanel.disableScaleAndRange();
                    }
                },
                hide: function(){
                    this.initialPanelValues = null;
                    this.viewPanel.getEl().unmask();
                },
                beforeclose: function(){
                    // on close, we don't apply changes and let the panel restore its state
                    if (this.initialPanelValues)
                    {
                        this.xMeasureGrid.getSelectionModel().select([this.initialPanelValues.measure], false, true);
                        this.xMeasurePanel.restoreValues(this.initialPanelValues);
                    }
                },
                scope: this
            }
        });

        this.optionsPanel = Ext4.create('LABKEY.vis.GenericChartOptionsPanel', {
            renderType: this.renderType,
            width: '100%',
            defaults: {
                labelAlign: 'left',
                labelWidth: 45,
                labelSeparator: ''
            },
            listeners: {
                chartDefinitionChanged: function(){
                    var renderType = this.optionsPanel.getRenderType();
                    if(this.renderType != renderType){
                        this.renderType = renderType;

                        if (!this.reportId)
                            this.updateWebpartTitle(this.typeToLabel[renderType]);
                    }
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.chartDefinitionChanged.delay(500);
                },
                'closeOptionsWindow': function(canceling){
                    if (canceling)
                        this.optionsWindow.fireEvent('beforeclose');    
                    this.optionsWindow.hide();
                },
                scope: this
            }
        });

        this.optionsWindow = Ext4.create('Ext.window.Window', {
            title: 'Plot Options',
            hidden: true,
            border: 1,
            width: 325,
            resizable: false,
            cls: 'data-window',
            modal: true,
            closable: true,
            closeAction: 'hide',
            expandOnShow: false,
            items:[this.optionsPanel],
            relative: this.showOptionsBtn,
            draggable: false,
            listeners: {
                scope: this,
                show: function(){
                    this.initialPanelValues = this.optionsPanel.getPanelOptionValues();
                    this.optionsWindow.alignTo(this.showOptionsBtn, 'tl-tr', [-175, 30]);
                },
                hide: function(){
                    this.initialPanelValues = null;
                },
                beforeclose: function(){
                    // on close, we don't apply changes and let the panel restore its state
                    if (this.initialPanelValues)
                        this.optionsPanel.restoreValues(this.initialPanelValues);
                }
            }
        });

        this.mainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
            listeners: {
                closeOptionsWindow : function(canceling){
                    if (canceling)
                        this.mainTitleWindow.fireEvent('beforeclose');
                    this.mainTitleWindow.hide();
                },
                chartDefinitionChanged: function(){
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.chartDefinitionChanged.delay(250);
                },
                resetTitle: function() {
                    // need a reset title function.
                    this.mainTitlePanel.setMainTitle(this.queryName + ' - ' + this.yAxisMeasure.label)
                },
                scope: this
            }
        });

        this.mainTitleWindow = Ext4.create('Ext.window.Window', {
            title: 'Main Title',
            layout: 'fit',
            cls: 'data-window',
            resizable: false,
            width: 300,
            closeAction: 'hide',
            items: [this.mainTitlePanel],
            listeners: {
                show: function(){
                    this.initialPanelValues = this.mainTitlePanel.getPanelOptionValues();
                    this.viewPanel.getEl().mask();
                },
                hide: function(){
                    this.initialPanelValues = null;
                    this.viewPanel.getEl().unmask();
                },
                beforeclose: function(){
                    // on close, we don't apply changes and let the panel restore its state
                    if (this.initialPanelValues)
                        this.mainTitlePanel.restoreValues(this.initialPanelValues);
                },
                scope: this
            }
        });

        this.chartDefinitionChanged = new Ext4.util.DelayedTask(function(){
            this.markDirty(true);
            this.renderPlot();
        }, this);

        this.items.push(this.optionsWindow);
        this.items.push(this.centerPanel);

        this.callParent();

        this.on('tabchange', this.onTabChange, this);
        this.on('renderPlot', this.renderPlot, this);

        if (this.reportId) {
            this.markDirty(false);
            this.loadReport(this.reportId);
        } else {
            this.markDirty(true);
            this.on('render', this.ensureQuerySettings, this);
        }

        this.centerPanel.on('resize', function(){
            if(!this.optionsWindow.hidden){
                this.optionsWindow.alignTo(this.showOptionsBtn, 'tl-tr', [-175, 30]);
            }
        }, this);

        // customization
        this.on('enableCustomMode',  this.onEnableCustomMode,  this);
        this.on('disableCustomMode', this.onDisableCustomMode, this);

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getViewPanel : function() {

        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                ui          : 'custom',
                listeners   : {
                    activate  : this.viewPanelActivate,
                    scope: this
                }
            });
        }
        this.viewPanel.on('resize', function(cmp) {
            // only re-render after the initial chart rendering
            if (this.chartData)
                this.renderPlot();
        }, this);

        return this.viewPanel;
    },

    getDataPanel : function() {

        if (!this.dataPanel)
        {
            var dataGrid = Ext4.create('Ext.Component', {
                autoScroll  : true,
                cls         : 'iScroll',
                listeners   : {
                    render : {fn : function(cmp){this.renderDataGrid(cmp.getId());}, scope : this}
                }
            });

            this.dataPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                padding     : '10',
                border      : false,
                frame       : false,
                cls         : 'iScroll',
                items       : dataGrid
            });
        }
        return this.dataPanel;
    },

    isNew : function() {
        return !this.reportId;
    },

    getSavePanel : function() {

        var formItems = [];

        this.reportName = Ext4.create('Ext.form.field.Text', {
            fieldLabel : 'Report Name',
            allowBlank : false,
            listeners : {
                change : function() {this.markDirty(true);},
                scope : this
            }
        });

        this.reportDescription = Ext4.create('Ext.form.field.TextArea', {
            fieldLabel : 'Report Description',
            listeners : {
                change : function() {this.markDirty(true);},
                scope : this
            }
        });

        this.reportPermission = Ext4.create('Ext.form.RadioGroup', {
            xtype      : 'radiogroup',
            width      : 300,
            hidden     : !this.allowShare,
            fieldLabel : 'Viewable By',
            items      : [
                {boxLabel : 'All readers',  width : 100, name : 'public', checked : this.allowShare, inputValue : true},
                {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
        });
        formItems.push(this.reportName, this.reportDescription, this.reportPermission);

        this.formPanel = Ext4.create('Ext.form.Panel', {
            bodyPadding : 20,
            itemId      : 'selectionForm',
            hidden      : this.hideSave,
            flex        : 1,
            items       : formItems,
            border      : false, frame : false,
            fieldDefaults : {
                anchor  : '100%',
                maxWidth : 650,
                labelWidth : 150,
                labelSeparator : ''
            }
        });

        this.saveButton = Ext4.create('Ext.button.Button', {
            text    : 'Save',
            hidden  : this.hideSave,
            handler : function() {
                var form = this.savePanel.getComponent('selectionForm').getForm();

                if (form.isValid()) {
                    var data = this.getCurrentReportConfig();
                    this.saveReport(data);
                }
                else {
                    var msg = 'Please enter all the required information.';

                    if (!this.reportName.getValue()) {
                        msg = 'Report name must be specified.';
                    }
                    Ext4.Msg.show({
                         title: "Error",
                         msg: msg,
                         buttons: Ext4.MessageBox.OK,
                         icon: Ext4.MessageBox.ERROR
                    });
                }
            },
            scope   : this
        });

        this.savePanel = Ext4.create('Ext.panel.Panel', {
            hidden      : false,
            preventHeader : true,
            border      : false,
            frame       : false,
            items       : this.formPanel,
            buttons  : [
                this.saveButton, this.saveAsButton,
                {
                    text    : 'Cancel',
                    handler : function() {
                        this.saveWindow.hide();
                    },
                    scope   : this
                }
            ]
        });

        return this.savePanel;
    },

    renderDataGrid : function(renderTo) {
        var urlParams = LABKEY.ActionURL.getParameters(this.baseUrl);
        var filterUrl = urlParams['filterUrl'];

        var userFilters;
        if (this.userFilters)
            userFilters = this.userFilters;
        else
            userFilters = LABKEY.Filter.getFiltersFromUrl(filterUrl, this.dataRegionName);

        var userSort = LABKEY.Filter.getSortFromUrl(filterUrl, this.dataRegionName);

        var wp = new LABKEY.QueryWebPart({
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            columns     : this.savedColumns,        // TODO, qwp does not support passing in a column list
            frame       : 'none',
            showBorders : false,
            removeableFilters       : userFilters,
            removeableSort          : userSort,
            showSurroundingBorder   : false,
            showDetailsColumn       : false,
            showUpdateColumn        : false,
            showRecordSelectors     : false,
            parameters  : {
                quickChartDisabled : true
            },
            buttonBar   : {
                position : 'none'
            }
        });

        // save the dataregion
        this.panelDataRegionName = wp.dataRegionName;

        wp.render(renderTo);
    },

    // Returns a configuration based on the baseUrl plus any filters applied on the dataregion panel
    // the configuration can be used to make a selectRows request
    getQueryConfig : function(serialize) {

        var dataRegion = LABKEY.DataRegions[this.panelDataRegionName];
        var config = {
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            maxRows     : 5000,
            requiredVersion : 12.1
        };

        if (this.initialColumnList)
            config.columns = this.initialColumnList;

        if(!serialize){
            config.success = this.onSelectRowsSuccess;
            config.failure = function(){this.viewPanel.getEl().unmask();};
            config.scope = this;
        }

        var filters;

        if (dataRegion)
            filters = dataRegion.getUserFilterArray();
        else if (this.userFilters)
            filters = this.userFilters || [];
        else
        {
            var urlParams = LABKEY.ActionURL.getParameters(this.baseUrl);
            var filterUrl = urlParams['filterUrl'];

            // lastly check if there is a filter on the url
            filters = LABKEY.Filter.getFiltersFromUrl(filterUrl, this.dataRegionName);
        }

        if (serialize)
        {
            var newFilters = [];

            for (var i=0; i < filters.length; i++) {
                var f = filters[i];
                newFilters.push({name : f.getColumnName(), value : f.getValue(), type : f.getFilterType().getURLSuffix()});
            } filters = newFilters;
        }
        config['filterArray'] = filters;

        return config;
    },

    getChartConfig : function() {

        var config = {};

        if (this.xAxisMeasure)
        {
            config.xAxisMeasure = {
                label   : this.xAxisMeasure.label,
                name    : this.xAxisMeasure.name,
                hidden  : this.xAxisMeasure.hidden,
                measure : this.xAxisMeasure.measure,
                type    : this.xAxisMeasure.type
            }
        }

        if (this.yAxisMeasure)
        {
            config.yAxisMeasure = {
                label   : this.yAxisMeasure.label,
                name    : this.yAxisMeasure.name,
                hidden  : this.yAxisMeasure.hidden,
                measure : this.yAxisMeasure.measure,
                type    : this.yAxisMeasure.type
            }
        }

        config.chartOptions = this.getChartOptions();

        return config;
    },

    ensureQuerySettings : function() {

        if (!this.schemaName || !this.queryName)
        {
            var formItems = [];
            var queryStore = this.initializeQueryStore();
            var queryId = Ext.id();

            this.schemaName = 'study';

            formItems.push({
                xtype       : 'combo',
                fieldLabel  : 'Schema',
                name        : 'schema',
                store       : this.initializeSchemaStore(),
                editable    : false,
                value       : this.schemaName,
                queryMode      : 'local',
                displayField   : 'name',
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {
                        this.schemaName = newValue;
                        this.queryName = null;
                        var proxy = queryStore.getProxy();
                        if (proxy)
                            queryStore.load({params : {schemaName : newValue}});

                        var queryCombo = Ext4.getCmp(queryId);
                        if (queryCombo)
                            queryCombo.clearValue();
                    }, scope : this}
                }
            });

            formItems.push({
                xtype       : 'combo',
                id          : queryId,
                fieldLabel  : 'Query',
                name        : 'query',
                store       : queryStore,
                editable    : false,
                allowBlank  : false,
                displayField   : 'name',
                triggerAction  : 'all',
                typeAhead      : true,
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {this.queryName = newValue;}, scope : this}
                }
            });

            var formPanel = Ext4.create('Ext.form.Panel', {
                border  : false,
                frame   : false,
                fieldDefaults  : {
                    labelWidth : 100,
                    width      : 375,
                    style      : 'padding: 4px 0',
                    labelSeparator : ''
                },
                items   : formItems,
                buttonAlign : 'right',
                buttons     : [{
                    text : 'Save',
                    formBind: true,
                    handler : function(btn) {
                        var form = btn.up('form').getForm();
                        if (form.isValid())
                        {
                            dialog.hide();
                            this.updateChartTask.delay(500);
                        }
                    },
                    scope   : this
                },{
                    text : 'Cancel',
                    handler : function(btn) {window.history.back()}
                }]
            });

            var dialog = Ext4.create('Ext.window.Window', {
                width  : 450,
                height : 200,
                layout : 'fit',
                border : false,
                frame  : false,
                closable : false,
                draggable : false,
                modal  : true,
                title  : 'Select Chart Query',
                bodyPadding : 20,
                items : formPanel,
                scope : this
            });

            dialog.show();
        }
    },

    /**
     * Create the store for the schema
     */
    initializeSchemaStore : function() {

        Ext4.define('LABKEY.data.Schema', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'}
            ]
        });

        // manually define for now, we could query at some point
        var schemaStore = Ext4.create('Ext.data.Store', {
            model : 'LABKEY.data.Schema',
            data  : [
                {name : 'study'},
                {name : 'assay'},
                {name : 'lists'}
            ]
        });

        return schemaStore;
    },

    /**
     * Create the store for the schema
     */
    initializeQueryStore : function() {

        Ext4.define('LABKEY.data.Queries', {
            extend : 'Ext.data.Model',
            fields : [
                {name : 'name'},
                {name : 'description'},
                {name : 'isUserDefined', type : 'boolean'}
            ]
        });

        var config = {
            model   : 'LABKEY.data.Queries',
            autoLoad: false,
            pageSize: 10000,
            proxy   : {
                type   : 'ajax',
                url : LABKEY.ActionURL.buildURL('query', 'getQueries'),
                extraParams : {
                    schemaName  : 'study'
                },
                reader : {
                    type : 'json',
                    root : 'queries'
                }
            }
        };

        return Ext4.create('Ext.data.Store', config);
    },

    markDirty : function(dirty) {
        this.dirty = dirty;
    },

    isDirty : function() {
        return this.dirty;
    },

    beforeUnload : function() {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    },

    getCurrentReportConfig : function() {

        var config = {
            name        : this.reportName.getValue(),
            reportId    : this.reportId,
            description : this.reportDescription.getValue(),
            public      : this.reportPermission.getValue().public || false,
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            renderType  : this.renderType,
            jsonData    : {
                queryConfig : this.getQueryConfig(true),
                chartConfig : this.getChartConfig()
            }
        };

        return config;
    },

    saveReport : function(data) {

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'saveGenericReport.api'),
            method  : 'POST',
            jsonData: data,
            success : function(resp){
                // if you want to stay on page, you need to refresh anyway to update attachments
                var msgbox = Ext4.create('Ext.window.Window', {
                    title    : 'Saved',
                    html     : '<div style="margin-left: auto; margin-right: auto;"><span class="labkey-message">Report Saved successfully</span></div>',
                    modal    : false,
                    closable : false,
                    width    : 300,
                    height   : 100
                });
                msgbox.show();
                msgbox.getEl().fadeOut({duration : 2250, callback : function(){
                    msgbox.hide();
                }});

                var o = Ext4.decode(resp.responseText);

                this.updateWebpartTitle(data.name);

                this.reportId = o.reportId;
                this.loadReport(this.reportId);
                this.saveWindow.close();
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    updateWebpartTitle : function(title) {

        // Modify Title (hack: hardcode the webpart id since this is really not a webpart, just
        // using a webpart frame, will need to start passing in the real id if this ever
        // becomes a true webpart
        var titleEl = Ext4.query('span[class=labkey-wp-title-text]:first', 'webpart_-1');
        if (titleEl && (titleEl.length >= 1))
        {
            titleEl[0].innerHTML = LABKEY.Utils.encodeHtml(title);
        }

        var navTitle = Ext4.query('table[class=labkey-nav-trail] span[class=labkey-nav-page-header]');
        if (navTitle && (navTitle.length >= 1))
        {
            navTitle[0].innerHTML = LABKEY.Utils.encodeHtml(title);
        }
    },

    onFailure : function(resp){
        var error = Ext4.decode(resp.responseText).exception;
        if(error){
            Ext.MessageBox.alert('Error', error);
        } else {
            Ext.MessageBox.alert('Error', 'An unknown error has ocurred, unable to save the chart.');
        }
    },

    onSaveAs : function() {
        var formItems = [];

        formItems.push(Ext4.create('Ext.form.field.Text', {name : 'name', fieldLabel : 'Report Name', allowBlank : false}));
        formItems.push(Ext4.create('Ext.form.field.TextArea', {name : 'description', fieldLabel : 'Report Description'}));

        var permissions = Ext4.create('Ext.form.RadioGroup', {
            xtype      : 'radiogroup',
            width      : 300,
            hidden     : !this.allowShare,
            fieldLabel : 'Viewable By',
            items      : [
                {boxLabel : 'All readers',  width : 100, name : 'public', checked : this.allowShare, inputValue : true},
                {boxLabel : 'Only me',   width : 100, name : 'public', inputValue : false}]
        });
        formItems.push(permissions);

        var saveAsWindow = Ext4.create('Ext.window.Window', {
            width  : 500,
            autoHeight : true,
            cls: 'data-window',
            layout : 'fit',
            draggable : false,
            title  : 'Save As',
            defaults: {
                border: false, frame: false
            },
            items  : [{
                xtype : 'form',
                bodyPadding: 20,
                fieldDefaults : {
                    anchor  : '100%',
                    maxWidth : 450,
                    labelWidth : 150,
                    labelSeparator : ''
                },
                items       : formItems,
                buttonAlign : 'right',
                buttons     : [
                    {
                        text : 'Save',
                        formBind: true,
                        handler : function(btn) {
                            var form = btn.up('form').getForm();

                            if (form.isValid()) {
                                var data = this.getCurrentReportConfig();
                                var values = form.getValues();

                                data.name = values.name;
                                data.description = values.description;
                                data.public = values.public || false;
                                data.reportId = null;

                                this.saveReport(data);
                            }
                            else {
                                Ext4.Msg.show({
                                    title: "Error",
                                    msg: 'Report name must be specified.',
                                    buttons: Ext4.MessageBox.OK,
                                    icon: Ext4.MessageBox.ERROR
                                });
                            }
                            saveAsWindow.close();
                        },
                        scope   : this
                    }, {
                        text: 'Cancel',
                        handler: function(btn) {
                            saveAsWindow.close();
                        },
                        scope: this
                    }]
            }],
            listeners: {
                scope: this,
                show: function(){
                    this.viewPanel.getEl().mask();
                },
                close: function(){
                    this.viewPanel.getEl().unmask();
                }
            },
            scope : this
        });
        saveAsWindow.show();
    },

    loadReport : function(reportId) {

        this.reportLoaded = false;
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'getGenericReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                this.saveAsBtn.setVisible(true);
                this.loadSavedConfig(Ext4.decode(response.responseText).reportConfig);
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    loadSavedConfig : function(config) {

        this.allowCustomize = config.editable;

        this.schemaName = config.schemaName;
        this.queryName = config.queryName;
        this.renderType = config.renderType;

        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        if (this.reportPermission)
            this.reportPermission.setValue({public : config.public});

        var json = Ext4.decode(config.jsonData)
        if (json.queryConfig.filterArray)
        {
            this.userFilters = [];

            for (var i=0; i < json.queryConfig.filterArray.length; i++)
            {
                var f = json.queryConfig.filterArray[i];
                this.userFilters.push(LABKEY.Filter.create(f.name,  f.value, LABKEY.Filter.getFilterTypeForURLSuffix(f.type)));
            }
        }

        if (json.queryConfig.columns)
            this.savedColumns = json.queryConfig.columns;

        if (json.chartConfig)
        {
            this.yAxisMeasure = json.chartConfig.yAxisMeasure;
            this.xAxisMeasure = json.chartConfig.xAxisMeasure;
            if(!json.chartConfig.chartOptions){
                this.optionsPanel.setPanelOptionValues({renderType: this.renderType});
            } else {
                this.optionsPanel.setPanelOptionValues(json.chartConfig.chartOptions);
                if(json.chartConfig.chartOptions.mainTitle){
                    this.mainTitlePanel.setMainTitle(json.chartConfig.chartOptions.mainTitle, true);
                    if(this.getDefaultTitle() != this.mainTitlePanel.getPanelOptionValues().title){
                        this.mainTitlePanel.userEditedTitle = true;
                    }
                } else {
                    this.mainTitlePanel.setMainTitle(this.getDefaultTitle(), true);
                }

                if(json.chartConfig.chartOptions.xAxis){
                    this.xMeasurePanel.setPanelOptionValues(json.chartConfig.chartOptions.xAxis)
                    if(this.xAxisMeasure && this.xMeasurePanel.getAxisLabel() != this.getDefaultXAxisLabel()){
                        this.xMeasurePanel.userEditedLabel = true;
                    }
                }

                if(json.chartConfig.chartOptions.yAxis){
                    this.yMeasurePanel.setPanelOptionValues(json.chartConfig.chartOptions.yAxis)
                    if(this.yAxisMeasure && this.yMeasurePanel.getAxisLabel() != this.getDefaultYAxisLabel()){
                        this.yMeasurePanel.userEditedLabel = true;
                    }
                }
            }
        }

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    renderPlot: function(forExport) {
        var measure;
        var getFormatFn = function(field){
            return field.extFormatFn ? eval(field.extFormatFn) : this.defaultNumberFormat;
        };

        if (!forExport)
        {
            this.viewPanel.getEl().mask('Rendering Chart...');
            this.viewPanel.removeAll();
            this.exportPdfBtn.removeListener('click', this.exportChartToPdf);
            this.exportPdfBtn.disable();
        }

        if (!this.yAxisMeasure && !forExport)
        {
            if (this.autoColumnYName)
            {
                measure = this.yMeasureStore.findRecord('name', this.autoColumnYName);
                if (measure){
                    this.yMeasureGrid.getSelectionModel().select(measure, false, true);
                    this.yMeasurePanel.selectionChange(true);
                    this.yAxisMeasure = measure.data;
                }
            }

            if (!this.yAxisMeasure)
            {
                this.viewPanel.getEl().unmask();
                this.showYMeasureWindow();
                return;
            }
        }

        if (!this.xAxisMeasure && !forExport)
        {
            if(this.renderType == "scatter_plot"){
                if (this.autoColumnXName)
                {
                    measure = this.xMeasureStore.findRecord('name', this.autoColumnXName);
                    if (measure) {
                        this.xMeasureGrid.getSelectionModel().select(measure, false, true);
                        this.xMeasurePanel.selectionChange(true);
                        this.xAxisMeasure = measure.data;
                    }
                }

                if (!this.xAxisMeasure)
                {
                    this.viewPanel.getEl().unmask();
                    this.showXMeasureWindow();
                    return;
                }
            } else {
                measure = this.xMeasureStore.findRecord('label', 'Cohort');
                if (measure) {
                    this.xMeasureGrid.getSelectionModel().select(measure, false, true);
                    this.xMeasurePanel.selectionChange(true);
                    this.xAxisMeasure = measure.data;
                }
           }
        }

        var chartOptions = this.getChartOptions();
        var scales = {}, geom, plotConfig, newChartDiv, labels, yMin, yMax, yPadding;
        var xMeasureName = this.xAxisMeasure ? this.xAxisMeasure.name : this.chartData.queryName;
        var xMeasureLabel = this.xAxisMeasure ? this.xAxisMeasure.label : this.chartData.queryName;
        var yMeasureName = this.yAxisMeasure.name;
        var yAcc = function(row){
            var value = row[yMeasureName].value;
            if(value === false || value === true){
                value = value.toString();
            }
            return value;
        };
        var xAcc = null;

        // Check if y axis actually has data first, if not show error message and have user select new measure.
        var yDataIsNull = true;
        for(var i = 0; i < this.chartData.rows.length; i++){
            var value = yAcc(this.chartData.rows[i]);
            if(value != null){
                yDataIsNull = false;
            }
        }

        if(yDataIsNull){
            this.viewPanel.getEl().unmask();
            Ext.MessageBox.alert('Error', 'All data values for ' + this.yAxisMeasure.label + ' are null. Please choose a different measure', this.showYMeasureWindow, this);
            return;
        }

        if(this.xAxisMeasure){
            xAcc = function(row){
                var value = row[xMeasureName].displayValue ? row[xMeasureName].displayValue : row[xMeasureName].value;
                if(value === null){
                    value = "Not in " + xMeasureLabel;
                }
                return value;
            };
        } else {
            xAcc = function(row){return xMeasureName};
        }

        var xClickHandler = function(scopedThis){
            return function(){
                scopedThis.showXMeasureWindow();
            }
        };

        var yClickHandler = function(scopedThis){
            return function(){
                scopedThis.showYMeasureWindow();
            }
        };

        var mainTitleClickHandler = function(scopedThis){
            return function(){
                scopedThis.showMainTitleWindow();
            }
        };

        newChartDiv = Ext4.create('Ext.container.Container', {
            border: 1,
            autoEl: {tag: 'div'}
        });
        this.viewPanel.add(newChartDiv);

        if(this.xAxisMeasure && this.xAxisMeasure.type === 'int' && xMeasureLabel == 'Cohort'){
            this.xAxisMeasure.type = 'string';
        }

        // TODO: make line charts render if this.xAxisMeasure.type == "date"
        if(this.renderType == 'box_plot' ||
                (this.renderType == 'auto_plot' && (!this.xAxisMeasure || this.xAxisMeasure.type == 'string' || this.xAxisMeasure.type == 'boolean'))) {
            scales.x = {scaleType: 'discrete'};
            yMin = d3.min(this.chartData.rows, yAcc);
            yMax = d3.max(this.chartData.rows, yAcc);
            yPadding = ((yMax - yMin) * .1);
            scales.y = {min: yMin - yPadding, max: yMax + yPadding, scaleType: 'continuous', trans: chartOptions.yAxis.scaleType};
            geom = new LABKEY.vis.Geom.Boxplot({
                lineWidth: chartOptions.lineWidth,
                outlierOpacity: chartOptions.opacity,
                outlierFill: '#' + chartOptions.pointColor,
                outlierSize: chartOptions.pointSize
            });
        } else if(this.renderType == 'scatter_plot' ||
                (this.renderType == 'auto_plot' && this.xAxisMeasure.type == 'int' || this.xAxisMeasure.type == 'float' || this.xAxisMeasure.type == 'date')){
            scales.x = (this.xAxisMeasure.type == 'int' || this.xAxisMeasure.type == 'float' || this.xAxisMeasure.type == 'double') ?
                {scaleType: 'continuous', trans: chartOptions.xAxis.scaleType} :
                {scaleType: 'discrete'};
            scales.y = {scaleType: 'continuous', trans: chartOptions.yAxis.scaleType};
            geom = new LABKEY.vis.Geom.Point({
                opacity: chartOptions.opacity,
                size: chartOptions.pointSize,
                color: '#' + chartOptions.pointColor
            });
        } else {
            return;
        }

        for(var i = 0; i < this.chartData.metaData.fields.length; i++){
            var field = this.chartData.metaData.fields[i];
            if(field.type == 'int' || field.type == 'float'){
                if(field.name == this.yAxisMeasure.name){
                    scales.y.tickFormat = getFormatFn.call(this, field);
                }

                if(this.xAxisMeasure && field.name == this.xAxisMeasure.name && field.caption != 'Cohort'){
                    scales.x.tickFormat = getFormatFn.call(this, field);
                }
            }
        }

        labels = {
            main: {
                value: chartOptions.mainTitle,
                lookClickable: !forExport,
                listeners: {
                    click: mainTitleClickHandler(this)
                }
            },
            y: {
                value: chartOptions.yAxis.label ? chartOptions.yAxis.label : this.yAxisMeasure.label,
                lookClickable: !forExport,
                listeners: {
                    click: yClickHandler(this)
                }
            },
            x: {
                value: chartOptions.xAxis.label ? chartOptions.xAxis.label : "Choose a column",
                lookClickable: !forExport,
                listeners: {
                    click: xClickHandler(this)
                }
            }

        };

        plotConfig = this.generatePlotConfig(
                geom,
                newChartDiv.id,
                !forExport ? newChartDiv.getWidth() : 1200,
                !forExport ? newChartDiv.getHeight() - 25 : 600,
                this.chartData.rows,
                labels,
                scales,
                xMeasureName,
                this.yAxisMeasure.name,
                xAcc,
                yAcc
        );
        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();

        if (!forExport)
        {
            this.exportPdfBtn.addListener('click', this.exportChartToPdf, this);
            this.exportPdfBtn.enable();
            this.viewPanel.getEl().unmask();
        }
        else
        {
            return newChartDiv.id;
        }
    },

    exportChartToPdf: function() {
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'pdf');
            Ext4.getCmp(tempDivId).destroy();
        }
    },

    generatePlotConfig: function(geom, renderTo, width, height, data, labels, scales, xAxisName, yAxisName, xAcc, yAcc){

        var aes = {
            y: yAcc,
            x: xAcc
        };
        if(geom.type == "Boxplot"){
            aes.hoverText = function(x, stats){
                return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                        '\nQ3: ' + stats.Q3;
            };
            aes.outlierHoverText = function(row){
                if(row[xAxisName]){
                    return xAxisName + ': ' + row[xAxisName].value + ', ' + yAxisName + ': ' + row[yAxisName].value;
                } else {
                    return xAxisName + ', ' + yAxisName + ': ' + row[yAxisName].value;
                }
            }
        } else if(geom.type == "Point"){
            aes.hoverText = function(row){
                return xAxisName + ': ' + row[xAxisName].value + ', ' + yAxisName + ': ' + row[yAxisName].value;
            };
        }

        var plotConfig = {
            renderTo: renderTo,
            width: width,
            height: height,
            legendPos: 'none',
            labels: labels,
            layers: [new LABKEY.vis.Layer({geom: geom})],
            scales: scales,
            aes: aes,
            data: data
        };

        return plotConfig;
    },

    viewPanelActivate: function(){

        this.updateChartTask.delay(500);
    },

    createFilterString: function(filterArray){
        var filterParams = [];
        for (var i = 0; i < filterArray.length; i++){
            filterParams.push(filterArray[i].getURLParameterName() + '=' + filterArray[i].getURLParameterValue());
        }

        return filterParams.join('&');
    },

    onSelectRowsSuccess: function(response){
        this.viewPanel.getEl().unmask();
        this.chartData = response;
        this.yMeasureStore.loadRawData(this.chartData.metaData.fields);
        this.xMeasureStore.loadRawData(this.chartData.metaData.fields);

        this.renderPlot();
    },

    showYMeasureWindow: function(){
        this.viewPanel.getEl().mask();
        this.yMeasureWindow.show();
    },

    showXMeasureWindow: function(){
        this.viewPanel.getEl().mask();
        this.xMeasureWindow.show();
    },

    showMainTitleWindow: function(){
        this.mainTitleWindow.show();
    },

    getChartOptions: function(){
        var chartOptions = {};

        Ext.apply(chartOptions, this.optionsPanel.getPanelOptionValues());

        if(!this.mainTitlePanel.userEditedTitle || this.mainTitlePanel.getPanelOptionValues().title == ''){
            chartOptions.mainTitle = this.getDefaultTitle();
            this.mainTitlePanel.setMainTitle(chartOptions.mainTitle, true);
        } else {
            chartOptions.mainTitle = this.mainTitlePanel.getPanelOptionValues().title;
        }

        chartOptions.yAxis = this.yMeasurePanel.getPanelOptionValues();
        chartOptions.xAxis = this.xMeasurePanel.getPanelOptionValues();

        return chartOptions;
    },

    getDefaultTitle: function(){
        return this.queryName + ' - ' + this.yAxisMeasure.label
    },

    getDefaultYAxisLabel: function(){
        return this.yAxisMeasure.label;
    },

    getDefaultXAxisLabel: function(){
        return this.xAxisMeasure.label
    },

    /**
     * used to determine if the new chart options are different from the
     * currently rendered options
     */
    isConfigurationChanged : function() {

        var queryCfg = this.getQueryConfig();

        if (!queryCfg.schemaName || !queryCfg.queryName)
            return false;

        // ugly race condition, haven't loaded a saved report yet
        if (!this.reportLoaded)
            return false;

        if (!this.chartData)
            return true;

        // check if the user filters have changed
        if (this.currentFilterStr == null)
        {
            this.currentFilterStr = this.createFilterString(queryCfg.filterArray);
            return true;
        }
        else
        {
            var filterStr = this.createFilterString(queryCfg.filterArray);

            if (this.currentFilterStr != filterStr)
            {
                this.currentFilterStr = filterStr;
                return true;
            }
        }

        return false;
    }
});
