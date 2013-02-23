/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("study/DataViewsPanel.css");

Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-genericchartpanel',

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
                    var params = {
                        schemaName  : this.schemaName,
                        queryName   : this.queryName,
                        viewName    : this.viewName,
                        dataRegionName : this.dataRegionName,
                        includeCohort : true,
                        includeParticipantCategory : true
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

                            this.subject = o.subject;
                            this.requestData();
                            this.requestRender(false);
                        },
                        failure : this.onFailure,
                        scope   : this
                    });

                }
                else {
                    this.requestData();
                    this.requestRender(false);
                }
            }

        }, this);

        this.reportLoaded = true;
        this.typeToLabel = {
            auto_plot : 'Auto Plot Report',
            scatter_plot : 'Scatter Plot Report',
            box_plot : 'Box Plot Report'
        };

        // only linear for now but could expand in the future
        this.lineRenderers = {
            linear : {
                createRenderer : function(params){
                    if (params && params.length >= 2) {
                        return function(x){return x * params[0] + params[1];}
                    }
                    return function(x) {return x;}
                }
            }
        };
        this.callParent([config]);
    },

    initComponent : function() {

        // boolean to check if we should allow things like export to PDF
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8);

        this.editMode = (LABKEY.ActionURL.getParameter("edit") == "true" || !this.reportId) && this.allowEditMode;

        this.items = [];

        this.showOptionsBtn = Ext4.create('Ext.button.Button', {
            text: 'Options',
            handler: function(){
                this.optionsWindow.setVisible(this.optionsWindow.isHidden());
            },
            scope: this
        });

        this.groupingBtn = Ext4.create('Ext.button.Button', {
            text: 'Grouping',
            handler: function(){
                this.groupingWindow.setVisible(this.groupingWindow.isHidden());
            },
            scope: this
        });

        this.developerBtn = Ext4.create('Ext.button.Button', {
            text: 'Developer',
            hidden: !this.isDeveloper,
            disabled: !this.supportedBrowser,
            tooltip: !this.supportedBrowser ? "Developer options not supported for IE6, IE7, or IE8." : null,
            handler: function(){
                this.developerWindow.setVisible(this.developerWindow.isHidden());
            },
            scope: this
        });

        this.exportPdfBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            disabled: true,
            tooltip: !this.supportedBrowser ? "Export to PDF not supported for IE6, IE7, or IE8." : null, 
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
                    this.groupingBtn.show();
                    this.exportPdfBtn.show();

                    if(this.customButtons){
                        for(var i = 0; i < this.customButtons.length; i++){
                            this.customButtons[i].show();
                        }
                    }

                    if (this.isDeveloper)
                        this.developerBtn.show();
                } else {
                    this.centerPanel.getLayout().setActiveItem(1);
                    this.toggleBtn.setText('View Chart');
                    this.showOptionsBtn.hide();
                    this.groupingBtn.hide();
                    this.exportPdfBtn.hide();

                    if(this.customButtons){
                        for(var i = 0; i < this.customButtons.length; i++){
                            this.customButtons[i].hide();
                        }
                    }

                    if (this.isDeveloper)
                        this.developerBtn.hide();
                }
            },
            scope: this
        });

        var tbarItems = [
            this.toggleBtn,
            this.exportPdfBtn
        ];

        if (this.editMode)
        {
            tbarItems.push(this.showOptionsBtn);
            tbarItems.push(this.groupingBtn);
            tbarItems.push(this.developerBtn);

            if(this.customButtons){
                for(var i = 0; i < this.customButtons.length; i++){
                    var btn = this.customButtons[i];
                    btn.scope = this;
                    tbarItems.push(btn);
                }
            }

            if(this.canEdit){
                tbarItems.push('->');
                tbarItems.push(this.saveBtn);
            }
            
            tbarItems.push(this.saveAsBtn);
        }
        else if (this.allowEditMode)
        {
            // add an "edit" button if the user is allowed to toggle to edit mode for this report
            tbarItems.push('->');
            tbarItems.push({
                xtype: 'button',
                text: 'Edit',
                handler: function() {
                    var params = LABKEY.ActionURL.getParameters();
                    Ext4.apply(params, {edit: "true"});
                    window.location = LABKEY.ActionURL.buildURL(LABKEY.ActionURL.getController(), LABKEY.ActionURL.getAction(), null, params);
                }
            });
        }

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
            tbar: tbarItems
        });

        var typeConvert = function(value, record){
            // We take the displayFieldJSONType if available because if the column is a look up the record.type will
            // always be INT. The displayFieldJSONType is the actual type of the lookup.

            if(record.data.displayFieldJsonType){
                return record.data.displayFieldJsonType;
            }
            return record.data.type;
        };

        Ext4.define('MeasureModel',{
            extend: 'Ext.data.Model',
            fields: [
                {name: 'label', mapping: 'shortCaption', type: 'string'},
                {name: 'name', type: 'string'},
                {name: 'hidden', type: 'boolean'},
                {name: 'measure', type: 'boolean'},
                {name: 'type'},
                {name: 'displayFieldJsonType'},
                {name: 'normalizedType', convert: typeConvert}
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
                        var type = record.get('normalizedType');
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
                {header: 'Measure', dataIndex: 'label', flex: 1, renderer: function(value){return Ext4.util.Format.htmlEncode(value)}}
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
                {header: 'Measure', dataIndex: 'label', flex: 1, renderer: function(value){return Ext4.util.Format.htmlEncode(value)}}
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
                    if (this.initialPanelValues && this.initialPanelValues.measure)
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
                    if (this.initialPanelValues && this.initialPanelValues.measure)
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
            customRenderTypes: this.customRenderTypes,
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

        this.groupingMeasureStore = Ext4.create ('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store){
                    this.groupingMeasureStore.filterBy(function(record, id){
                        var normalizedType = record.get('normalizedType');
                        return (!record.get('hidden') && normalizedType !== 'float' && normalizedType !== 'int' && normalizedType !== 'double');
                    });

                    var firstVal = this.groupingMeasureStore.getAt(0);

                    this.groupingPanel.supressEvents = true;

                    if(this.groupingPanel.getColorMeasure().name == null){
                        if(firstVal){
                            this.groupingPanel.setColorMeasure(firstVal.data);
                        }
                    }

                    if(this.groupingPanel.getPointMeasure().name == null){
                        if(firstVal){
                            this.groupingPanel.setPointMeasure(firstVal.data);
                        }
                    }

                    this.groupingPanel.supressEvents = false;
                },
                scope: this
            }
        });

        this.groupingPanel = Ext4.create('LABKEY.vis.GenericChartGroupingPanel', {
            width: '100%',
            store: this.groupingMeasureStore,
            listeners: {
                chartDefinitionChanged: function(){
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.chartDefinitionChanged.delay(500);
                },
                'closeOptionsWindow': function(canceling){
                    if (canceling)
                        this.groupingWindow.fireEvent('beforeclose');
                    this.groupingWindow.hide();
                },
                scope: this
            }
        });

        this.groupingWindow = Ext4.create('Ext.window.Window', {
            title: 'Grouping Options',
            hidden: true,
            border: 1,
            width: 420,
            cls: 'data-window',
            resizable: false,
            modal: true,
            draggable: false,
            closable: true,
            closeAction: 'hide',
            expandOnShow: false,
            relative: this.groupingBtn,
            items: [this.groupingPanel],
            listeners: {
                scope: this,
                show: function(){
                    this.initialPanelValues = this.groupingPanel.getPanelOptionValues();
                    this.groupingWindow.alignTo(this.groupingBtn, 'tl-tr', [-175, 30]);
                },
                beforeclose: function(){
                    if(this.initialPanelValues){
                        this.groupingPanel.restoreValues(this.initialPanelValues);
                    }
                }
            }
        });

        this.developerPanel = Ext4.create('LABKEY.vis.DeveloperOptionsPanel', {
            isDeveloper: this.isDeveloper || false,
            pointClickFn: null,
            defaultPointClickFn: this.getDefaultPointClickFn(),
            pointClickFnHelp: this.getPointClickFnHelp(),
            listeners: {
                chartDefinitionChanged: function(){
                    this.viewPanel.getEl().mask('Rendering Chart...');
                    this.chartDefinitionChanged.delay(500);
                },
                'closeOptionsWindow': function(canceling){
                    if (canceling)
                        this.developerWindow.fireEvent('beforeclose');
                    this.developerWindow.hide();
                },
                scope: this
            }
        });

        this.developerWindow = Ext4.create('Ext.window.Window', {
            title: 'Developer Options',
            hidden: true,
            border: 1,
            width: 800,
            cls: 'data-window',
            resizable: false,
            modal: true,
            draggable: false,
            closable: true,
            closeAction: 'hide',
            expandOnShow: false,
            relative: this.developerBtn,
            items: [this.developerPanel],
            listeners: {
                scope: this,
                show: function(){
                    this.initialPanelValues = this.developerPanel.getPanelOptionValues();
                    this.developerWindow.alignTo(this.developerBtn, 'tl-tr', [-175, 30]);
                },
                beforeclose: function(){
                    if(this.initialPanelValues){
                        this.developerPanel.restoreValues(this.initialPanelValues);
                    }
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
                    this.mainTitlePanel.setMainTitle(this.queryName + ' - ' + Ext4.util.Format.htmlEncode(this.yAxisMeasure.label))
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
            this.requestRender(false);
        }, this);

        this.items.push(this.optionsWindow);
        this.items.push(this.groupingWindow);
        this.items.push(this.developerWindow);
        this.items.push(this.centerPanel);

        this.callParent();

        this.on('tabchange', this.onTabChange, this);

        if (this.reportId) {
            this.markDirty(false);
            this.loadReport(this.reportId);
        } else {
            this.markDirty(false);
            this.on('render', this.ensureQuerySettings, this);
        }

        this.centerPanel.on('resize', function(){
            if(!this.optionsWindow.hidden){
                this.optionsWindow.alignTo(this.showOptionsBtn, 'tl-tr', [-175, 30]);
            }
        }, this);

        this.on('dataRequested', this.requestData, this);

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
                this.requestRender();
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
            },
            listeners : {
                render : function() {
                    var dr = LABKEY.DataRegions[this.dataRegionName];
                    if (dr) {
                        dr.disableHeaderLock();
                    }
                },
                scope  : wp
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
            requiredVersion : 12.1,
            method: 'POST'
        };

        config.columns = this.getQueryConfigColumns();

        if(!serialize){
            config.success = this.onSelectRowsSuccess;
            config.failure = function(response, opts){
                var error, errorDiv;

                this.viewPanel.getEl().unmask();

                if(response.exception){
                    error = '<p>' + response.exception + '</p>';
                    if(response.exceptionClass == 'org.labkey.api.view.NotFoundException'){
                        error = error + '<p>The source dataset, list, or query may have been deleted.</p>'
                    }
                }

                errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
                    html: '<h3 style="color:red;">An unexpected error occurred while retrieving data.</h2>' + error,
                    autoScroll: true
                });

                this.viewPanel.add(errorDiv);
            };
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

    getQueryConfigColumns: function(){
        var columns = null;

        if(!this.editMode || this.firstLoad){
            // If we're not in edit mode or if this is the first load we need to only load the minimum amount of data.
            columns = [];
            var groupingData = this.getChartConfig().chartOptions.grouping;

            if(this.xAxisMeasure){
                columns.push(this.xAxisMeasure.name);
            } else if(this.autoColumnXName){
                columns.push(this.autoColumnXName);
            } else {
                // Check if we have cohorts available.
                if(this.initialColumnList){
                    for(var i = 0; i < this.initialColumnList.length; i++){
                        if(this.initialColumnList[i].indexOf('Cohort') > -1){
                            columns.push(this.initialColumnList[i]);
                        }
                    }
                }
            }

            if(this.yAxisMeasure){
                columns.push(this.yAxisMeasure.name);
            } else if(this.autoColumnYName){
                columns.push(this.autoColumnYName);
            }

            if(groupingData.colorType === "measure"){
                columns.push(groupingData.colorMeasure.name)
            }

            if(groupingData.pointType === "measure"){
                columns.push(groupingData.pointMeasure.name);
            }
        } else {
            // If we're in edit mode then we can load all of the columns.
            if (this.initialColumnList){
                columns = this.initialColumnList;
            }
        }

        return columns;
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
                type    : this.xAxisMeasure.type,
                displayFieldJsonType : this.xAxisMeasure.displayFieldJsonType,
                normalizedType       : this.xAxisMeasure.normalizedType
            }
        }

        if (this.yAxisMeasure)
        {
            config.yAxisMeasure = {
                label   : this.yAxisMeasure.label,
                name    : this.yAxisMeasure.name,
                hidden  : this.yAxisMeasure.hidden,
                measure : this.yAxisMeasure.measure,
                type    : this.yAxisMeasure.type,
                displayFieldJsonType : this.yAxisMeasure.displayFieldJsonType,
                normalizedType       : this.yAxisMeasure.normalizedType
            }
        }

        if (this.curveFit)
            config.curveFit = this.curveFit;

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

            queryStore.addListener('beforeload', function(){
                saveQuerySettingsBtn.setDisabled(true);
            }, this);

            var saveQuerySettingsBtn = Ext4.create('Ext.button.Button', {
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
                buttons     : [ saveQuerySettingsBtn, {
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
            "public"    : this.reportPermission.getValue()["public"] || false,
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            viewName    : this.viewName,
            dataRegionName: this.dataRegionName,
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
                                data["public"] = values["public"] || false;
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
        this.viewName = config.viewName;
        this.dataRegionName = config.dataRegionName;

        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        if (this.reportPermission)
            this.reportPermission.setValue({"public" : config["public"]});

        var json = Ext4.decode(config.jsonData);
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
                if(json.chartConfig.chartOptions.mainTitle != null && json.chartConfig.chartOptions.mainTitle != undefined){
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

                if(json.chartConfig.chartOptions.grouping){
                    this.groupingPanel.setPanelOptionValues(json.chartConfig.chartOptions.grouping);
                }

                if (json.chartConfig.chartOptions.developer)
                    this.developerPanel.setPanelOptionValues(json.chartConfig.chartOptions.developer);
            }

            if (json.chartConfig.curveFit)
                this.curveFit = json.chartConfig.curveFit;
        }

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    renderPlot: function(forExport) {
        var measure;
        var customRenderType = null;
        var getFormatFn = function(field){
            return field.extFormatFn ? eval(field.extFormatFn) : this.defaultNumberFormat;
        };

        if(this.customRenderTypes && this.customRenderTypes[this.renderType]){
            customRenderType = this.customRenderTypes[this.renderType];
        }

        if (!forExport)
        {
            this.viewPanel.getEl().mask('Rendering Chart...');
            this.clearChartPanel();
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
            if(this.renderType !== "box_plot" && this.renderType !== "auto_plot"){
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
        var measures = this.initMeasures(chartOptions, this.chartData, this.xAxisMeasure, this.yAxisMeasure);
        var pointClickFn = null;

        if(measures.color && this.groupingMeasureStore.find('name', measures.color.name) === -1){
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point color, "' +
                    measures.color.label +
                    '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.color = undefined;
        }

        if(measures.shape && this.groupingMeasureStore.find('name', measures.shape.name) === -1){
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point shape, "' +
                    measures.shape.label +
                    '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.shape = undefined;
        }

        // Check if y axis actually has data first, if not show error message and have user select new measure.
        var yDataIsNull = true;
        var invalidYLogValues = false;
        var yHasZeroes = false;

        for(var i = 0; i < this.chartData.rows.length; i++){
            var yValue = measures.y.acc(this.chartData.rows[i]);
            if(yValue != null){
                yDataIsNull = false;
            }

            if(yValue < 0 || yValue === null || yValue === undefined){
                invalidYLogValues = true;
            }

            if(yValue === 0){
                yHasZeroes = true;
            }
        }

        if(yDataIsNull){
            this.viewPanel.getEl().unmask();
            Ext.MessageBox.alert('Error', 'All data values for ' + Ext4.util.Format.htmlEncode(this.yAxisMeasure.label) + ' are null. Please choose a different measure', this.showYMeasureWindow, this);
            return;
        }

        if(chartOptions.yAxis.scaleType === 'log'){
            if(invalidYLogValues){
                this.addWarningText("Unable to use a log scale on the y-axis. All y-axis values must be >= 0. Reverting to linear scale on y-axis.");
                chartOptions.yAxis.scaleType = 'linear';
                this.yMeasurePanel.setScaleType('linear');
            } else if(yHasZeroes){
                this.addWarningText("Some y-axis values are 0. Plotting all y-axis values as y+1");
            }
        }

        // create a new function from the pointClickFn string provided by the developer
        if (chartOptions.developer.pointClickFn){
            // the developer is expected to return a function, so we encapalate it within the anonymous function
            // (note: the function should have already be validated in a try/catch when applied via the developerOptionsPanel)
            var devPointClickFn = new Function("", "return " + this.developerPanel.removeLeadingComments(chartOptions.developer.pointClickFn));

            pointClickFn = function(measureInfo) {
                return function(clickEvent, data) {
                    // call the developers function, within the anonymous function, with the params as defined for the developer
                    devPointClickFn().call(this, data, measureInfo, clickEvent);
                }
            };
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
            autoEl: {tag: 'div'},
            autoScroll: true
        });

        this.viewPanel.add(newChartDiv);

        // TODO: make line charts render if this.xAxisMeasure.type == "date"
        if(!this.xAxisMeasure || this.isBoxPlot(this.renderType, this.xAxisMeasure.normalizedType)) {
            this.configureBoxPlotAxes(chartOptions, measures, scales);
            
            geom = new LABKEY.vis.Geom.Boxplot({
                lineWidth: chartOptions.lineWidth,
                outlierOpacity: chartOptions.opacity,
                outlierFill: '#' + chartOptions.pointColor,
                outlierSize: chartOptions.pointSize,
                color: '#' + chartOptions.lineColor,
                fill: '#' + chartOptions.fillColor
            });
        } else if(this.isScatterPlot(this.renderType, this.xAxisMeasure.normalizedType)){
            this.configureAxes(chartOptions, measures, scales);

            geom = new LABKEY.vis.Geom.Point({
                opacity: chartOptions.opacity,
                size: chartOptions.pointSize,
                color: '#' + chartOptions.pointColor
            });
        } else {
            if(customRenderType){
                if(customRenderType.configureAxes){
                    customRenderType.configureAxes(this, chartOptions, measures, scales);
                } else {
                    this.configureAxes(chartOptions, measures, scales);
                }
            } else {
                // If the render type is not found it's probably a custom one that is no longer supported.
                // So display an error to the user so they can change the plot type.
                this.viewPanel.getEl().unmask();

                this.addWarningText(
                        "The requested plot type, " +
                        this.renderType +
                        ", was not found. Please try choosing a different plot type from the options panel."
                );

                newChartDiv.insert(0, Ext4.create('Ext.container.Container', {
                    autoEl: 'div',
                    style: 'color: red; text-align: center;',
                    html: this.warningText
                }));

                return;
            }
        }

        for(var i = 0; i < this.chartData.metaData.fields.length; i++){
            var field = this.chartData.metaData.fields[i];
            var type = field.displayFieldJsonType ? field.displayFieldJsonType : field.type;

            if(type == 'int' || type == 'float'){
                if(field.name == this.yAxisMeasure.name){
                    scales.y.tickFormat = getFormatFn.call(this, field);
                }

                if(this.xAxisMeasure && field.name == this.xAxisMeasure.name){
                    scales.x.tickFormat = getFormatFn.call(this, field);
                }
            }
        }

        var mainLabel = chartOptions.mainTitle;
        var yLabel = (chartOptions.yAxis.label != null && chartOptions.yAxis.label != undefined) ?
                chartOptions.yAxis.label :
                Ext4.util.Format.htmlEncode(this.yAxisMeasure.label);
        var xLabel = chartOptions.xAxis.label ? chartOptions.xAxis.label : "";

        if(!forExport && this.editMode){
            if(mainLabel == null || Ext4.util.Format.trim(mainLabel) == ""){
                mainLabel = "Edit Title";
            }
            
            if(yLabel == null || Ext4.util.Format.trim(yLabel) == ""){
                yLabel = "Edit Axis Label";
            }
            
            if(xLabel == null || Ext4.util.Format.trim(xLabel) == ""){
                if(this.xAxisMeasure){
                    xLabel = "Edit Axis Label";
                } else {
                    xLabel = "Choose a column";
                }
            }
        }

        labels = {
            main: {
                value: mainLabel,
                lookClickable: !forExport && this.editMode,
                listeners: {
                    click: this.editMode ? mainTitleClickHandler(this) : null
                }
            },
            y: {
                value: yLabel,
                lookClickable: !forExport && this.editMode,
                listeners: {
                    click: this.editMode ? yClickHandler(this) : null
                }
            },
            x: {
                value: xLabel,
                lookClickable: !forExport && this.editMode,
                listeners: {
                    click: this.editMode ? xClickHandler(this) : null
                }
            }

        };

        var width = chartOptions.width ? chartOptions.width : !forExport ? newChartDiv.getWidth() : 1200;
        var height = chartOptions.height ? chartOptions.height : !forExport ? newChartDiv.getHeight() - 25 : 600;

        if(customRenderType){
            plotConfig = customRenderType.generatePlotConfig(this, chartOptions, measures, newChartDiv.id, width, height, this.chartData.rows, labels, scales);
            plotConfig.aes = customRenderType.generateAes(this, chartOptions, measures, pointClickFn);
        } else {
            plotConfig = this.generatePlotConfig(
                    geom,
                    newChartDiv.id,
                    width,
                    height,
                    this.chartData.rows,
                    labels,
                    scales
            );

            plotConfig.aes = this.generateAes(geom, measures, pointClickFn);
        }

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();

        if(this.chartData.rowCount > 5000){
            this.addWarningText('<p style="text-align: center;">The 5,000 row limit for plotting has been reached. Consider filtering your data.</p>');
        }

        newChartDiv.insert(0, Ext4.create('Ext.container.Container', {
            autoEl: 'div',
            style: 'color: red; text-align: center;',
            html: this.warningText
        }));

        if (!forExport){
            this.exportPdfBtn.addListener('click', this.exportChartToPdf, this);
            this.exportPdfBtn.setDisabled(!this.supportedBrowser);
            this.viewPanel.getEl().unmask();
        } else{
            return newChartDiv.id;
        }

        this.setRenderRequested(false); // We just rendered the plot, we don't need to request another render.
    },

    isScatterPlot: function(renderType, xAxisType){
        if(renderType === 'scatter_plot'){
            return true;
        }

        return (renderType === 'auto_plot' && (xAxisType == 'int' || xAxisType == 'float' || xAxisType == 'date'));
    },

    isBoxPlot: function(renderType, xAxisType){
        if(renderType === 'box_plot'){
            return true;
        }

        return (renderType == 'auto_plot' && (xAxisType == 'string' || xAxisType == 'boolean'));
    },

    getDiscreteXAcc: function(measures){
        return function(row){
            var valueObj = row[measures.x.name];
            var value = null;

            if(valueObj){
                value = valueObj.displayValue ? valueObj.displayValue : valueObj.value;
            }

            if(value === null){
                value = "Not in " + measures.x.label;
            }

            return value;
        };
    },

    getContinuousXAcc: function(measures){
        return function(row){
            var value = null;

            if(row[measures.x.name]){
                value = row[measures.x.name].value;

                if(Math.abs(value) === Infinity){
                    value = null;
                }
            }

            return value;
        }
    },

    configureAxes: function(chartOptions, measures, scales){
        if(this.xAxisMeasure.normalizedType == 'int' || this.xAxisMeasure.normalizedType == 'float' || this.xAxisMeasure.normalizedType == 'double'){
            measures.x.acc = this.getContinuousXAcc(measures);
            scales.x = {scaleType: 'continuous', trans: chartOptions.xAxis.scaleType};
            var hasNegative = false;
            var hasZero = false;
            var allXDataIsNull = true;

            // Check for values < 0, if log scale show error accordingly.
            for(var i = 0; i < this.chartData.rows.length; i++){
                var value = measures.x.acc(this.chartData.rows[i]);

                if(value != null){
                    allXDataIsNull = false;
                }

                if(value < 0 || value === null){
                    hasNegative = true;
                } else if(value === 0){
                    hasZero = true;
                }
            }

            if(allXDataIsNull){
                this.viewPanel.getEl().unmask();
                Ext.MessageBox.alert('Error', 'All data values for ' + Ext4.util.Format.htmlEncode(this.xAxisMeasure.label) + ' are null. Please choose a different measure', this.showXMeasureWindow, this);
                return;
            }

            if(scales.x.trans === 'log'){
                if(hasNegative){
                    this.addWarningText("Unable to use a log scale on the x-axis. All x-axis values must be >= 0. Reverting to linear scale on x-axis.");
                    scales.x.trans = 'linear';
                    this.xMeasurePanel.setScaleType('linear');
                }

                if(hasZero && !hasNegative){
                    this.addWarningText("Some x-axis values are 0. Plotting all x-axis values as x+1");
                }
            }
        } else {
            measures.x.acc = this.getDiscreteXAcc(measures);
            scales.x = {scaleType: 'discrete'};
        }

        scales.y = {scaleType: 'continuous', trans: chartOptions.yAxis.scaleType};
    },

    configureBoxPlotAxes: function(chartOptions, measures, scales){
        if(this.xAxisMeasure){
            measures.x.acc = this.getDiscreteXAcc(measures);
        } else {
            measures.x.acc = function(row){return measures.x.name};
        }

        scales.x = {scaleType: 'discrete'};
        yMin = d3.min(this.chartData.rows, measures.y.acc);
        yMax = d3.max(this.chartData.rows, measures.y.acc);
        yPadding = ((yMax - yMin) * .1);

        if (chartOptions.yAxis.scaleType == "log"){
            // Issue 15760: Quick Chart -- Log Data Renders Incorrectly
            // When subtracting padding we have to make sure we still produce valid values for a log scale.
            // log([value less than 0]) = NaN.
            // log(0) = -Infinity.
            if(yMin - yPadding > 0){
                yMin = yMin - yPadding;
            }
        } else {
            yMin = yMin - yPadding;
        }

        scales.y = {min: yMin, max: yMax + yPadding, scaleType: 'continuous', trans: chartOptions.yAxis.scaleType};
    },

    clearChartPanel: function(){
        this.clearWarningText();
        this.viewPanel.removeAll();
        this.exportPdfBtn.removeListener('click', this.exportChartToPdf);
        this.exportPdfBtn.disable();
    },

    clearWarningText: function(){
        this.warningText = '';
    },

    addWarningText: function(warning){
        if(!this.warningText){
            this.warningText = warning;
        } else {
            this.warningText = this.warningText + '<br />' + warning;
        }
    },

    exportChartToPdf: function() {
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            // export the temp chart as a pdf with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'pdf', this.getChartOptions().mainTitle);
            Ext4.getCmp(tempDivId).destroy();
        }
    },

    initMeasures: function(chartOptions, chartData, xMeasure, yMeasure){
        var measures = {};

        if(chartOptions.grouping.colorType === 'measure'){
            measures.color = {
                name: chartOptions.grouping.colorMeasure.name,
                label: chartOptions.grouping.colorMeasure.label,
                acc: function(row){
                    var valueObj = row[measures.color.name];
                    var value;
                    if(valueObj){
                        value = valueObj.displayValue ? valueObj.displayValue : valueObj.value;
                    } else {
                        value = null;
                    }

                    if(value === null || value === undefined){
                        value = "n/a";
                    }

                    return value;
                }
            };
        }

        if(chartOptions.grouping.pointType === 'measure'){
            measures.shape = {
                name: chartOptions.grouping.pointMeasure.name,
                label: chartOptions.grouping.pointMeasure.label,
                acc: function(row){
                    var valueObj = row[measures.shape.name];
                    var value = null;

                    if(valueObj){
                        value = valueObj.displayValue ? valueObj.displayValue : valueObj.value;
                    }

                    if(value === null || value === undefined){
                        value = "n/a";
                    }

                    return value;
                }
            };
        }

        measures.x = {
            name: xMeasure ? xMeasure.name : chartData.queryName,
            label: xMeasure ? xMeasure.label : chartData.queryName,
            acc: null // The x-axis accessor depends on the render type. This will be set later.
        };

        measures.y = {
            name: yMeasure.name,
            label: yMeasure.label,
            acc: function(row){
                var value = null;

                if(row[yMeasure.name]){
                    value = row[yMeasure.name].value;

                    if(Math.abs(value) === Infinity){
                        value = null;
                    }

                    if(value === false || value === true){
                        value = value.toString();
                    }
                }

                return value;
            }
        };

        return measures;
    },

    generateAes: function(geom, measures, pointClickFn){
        var aes = {
            y: measures.y.acc,
            x: measures.x.acc
        };

        if(geom.type == "Boxplot"){
            aes.hoverText = function(x, stats){
                return x + ':\nMin: ' + stats.min + '\nMax: ' + stats.max + '\nQ1: ' + stats.Q1 + '\nQ2: ' + stats.Q2 +
                        '\nQ3: ' + stats.Q3;
            };

            aes.outlierHoverText = function(row){
                if(row[measures.x.name]){
                    var hover = measures.x.label + ': ';

                    if(row[measures.x.name].displayValue){
                        hover = hover + row[measures.x.name].displayValue;
                    } else {
                        hover = hover + row[measures.x.name].value;
                    }

                    return hover + ', \n' + measures.y.label + ': ' + row[measures.y.name].value;
                } else {
                    return measures.x.label + ', \n' + measures.y.label + ': ' + row[measures.y.name].value;
                }
            };

            if(measures.color){
                aes.outlierColor = measures.color.acc;
            }

            if(measures.shape){
                aes.outlierShape = measures.shape.acc;
            }

        } else if(geom.type == "Point"){
            aes.hoverText = function(row){
                var hover = measures.x.label + ': ';

                if(row[measures.x.name].displayValue){
                    hover = hover + row[measures.x.name].displayValue;
                } else {
                    hover = hover + row[measures.x.name].value;
                }


                hover = hover + ', \n' + measures.y.label + ': ' + row[measures.y.name].value;

                if(measures.color){
                    hover = hover +  ', \n' + measures.color.label + ': ';
                    if(row[measures.color.name]){
                        if(row[measures.color.name].displayValue){
                            hover = hover + row[measures.color.name].displayValue;
                        } else {
                            hover = hover + row[measures.color.name].value;
                        }
                    }
                }

                if(measures.shape && !(measures.color && measures.color.name == measures.shape.name)){
                    hover = hover +  ', \n' + measures.shape.label + ': ';
                    if(row[measures.shape.name]){
                        if(row[measures.shape.name].displayValue){
                            hover = hover + row[measures.shape.name].displayValue;
                        } else {
                            hover = hover + row[measures.shape.name].value;
                        }
                    }
                }
                return hover;
            };

            if(measures.color){
                aes.color = measures.color.acc;
            }

            if(measures.shape){
                aes.shape = measures.shape.acc;
            }
        }

        if (pointClickFn != null)
        {
            var pointInfo = {
                schemaName: this.schemaName,
                queryName: this.queryName,
                xAxis: measures.x.name,
                yAxis: measures.y.name
            };
            if (measures.color)
                pointInfo.colorName = measures.color.name;
            if (measures.shape)
                pointInfo.pointName = measures.shape.name;

            aes.pointClickFn = pointClickFn(pointInfo);
        }

        return aes;
    },

    generatePlotConfig: function(geom, renderTo, width, height, data, labels, scales){
        var layers = [];

        layers.push(new LABKEY.vis.Layer({geom: geom, data: data}));

        // client has specified a line type
        if (this.curveFit) {
            var factory = this.lineRenderers[this.curveFit.type];
            if (factory) {
                layers.push(
                    new LABKEY.vis.Layer({
                        geom: new LABKEY.vis.Geom.Path(),
                        aes: {x: 'x', y: 'y'},
                        data: LABKEY.vis.Stat.fn(factory.createRenderer(this.curveFit.params),
                                this.curveFit.points, this.curveFit.min, this.curveFit.max)})
                );
            }
        }
        var plotConfig = {
            renderTo: renderTo,
            width: width,
            height: height,
            labels: labels,
            layers: layers,
            scales: scales,
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
        var sortedFields = this.sortFields(this.chartData.metaData.fields);
        this.yMeasureStore.loadRawData(sortedFields);
        this.xMeasureStore.loadRawData(sortedFields);
        this.groupingMeasureStore.loadRawData(this.chartData.metaData.fields);

        this.setDataLoading(false);

        if(this.isRenderRequested()){
            // If it's already been requested then we just need to request it again, since
            // this time we have the data to render.
            this.requestRender(false);
        }

        if(this.firstLoad){
            // Set first load to false after our first sucessful callback.
            this.firstLoad = false;
            this.fireEvent('dataRequested');
        }
    },

    sortFields: function(fields){
        // Sorts fields by their shortCaption, but puts
        // participant groups/categories/cohort at the beginning.
        var otherFields = [],
            participantFields = [],
            sortFunction = function(a, b){
                if(a.shortCaption < b.shortCaption){
                    return -1;
                } else if(a.shortCaption > b.shortCaption) {
                    return 1;
                }
                return 0;
            };

        if(this.subject){
            for(var i = 0; i < fields.length; i++){
                if(fields[i].name.indexOf(this.subject.column) > -1){
                    participantFields.push(fields[i]);
                } else {
                    otherFields.push(fields[i]);
                }
            }
            
            participantFields.sort(sortFunction);
            otherFields.sort(sortFunction);

            return participantFields.concat(otherFields);
        } else {
            return fields.sort(sortFunction);
        }
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
        var mainTitle = this.mainTitlePanel.getPanelOptionValues().title;

        Ext.apply(chartOptions, this.optionsPanel.getPanelOptionValues());

        if(!this.mainTitlePanel.userEditedTitle || mainTitle == null || mainTitle == undefined){
            chartOptions.mainTitle = this.getDefaultTitle();
            this.mainTitlePanel.setMainTitle(chartOptions.mainTitle, true);
        } else {
            chartOptions.mainTitle = mainTitle;
        }

        chartOptions.yAxis = this.yMeasurePanel.getPanelOptionValues();
        chartOptions.xAxis = this.xMeasurePanel.getPanelOptionValues();

        chartOptions.grouping = this.groupingPanel.getPanelOptionValues();

        chartOptions.developer = this.developerPanel.getPanelOptionValues();

        if(this.getCustomChartOptions){
            chartOptions.customOptions = this.getCustomChartOptions();
        }

        return chartOptions;
    },

    getDefaultTitle: function(){
        return this.queryName + (this.yAxisMeasure ? ' - ' + this.yAxisMeasure.label : '');
    },

    getDefaultYAxisLabel: function(){
        return this.yAxisMeasure ? this.yAxisMeasure.label : 'y-axis';
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
    },

    getDefaultPointClickFn: function() {
        return "function (data, measureInfo, clickEvent) {\n"
            + "   // use LABKEY.ActionURL.buildURL to generate a link to a different controller/action within LabKey server\n"
            + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', LABKEY.container.path, \n"
            + "                      {schemaName: measureInfo[\"schemaName\"], \"query.queryName\": measureInfo[\"queryName\"]});\n\n"
            + "   // display an Ext message box with some information from the function parameters\n"
            + "   Ext4.Msg.alert('Data Point Information',\n"
            + "       'Schema:' + measureInfo[\"schemaName\"]\n"
            + "       + '<br/> Query: <a href=\"' + queryHref + '\">' + measureInfo[\"queryName\"] + '</a>'\n"
            + "       + '<br/>' + measureInfo[\"xAxis\"] + ': ' + (data[measureInfo[\"xAxis\"]].displayValue ? data[measureInfo[\"xAxis\"]].displayValue : data[measureInfo[\"xAxis\"]].value)\n"
            + "       + '<br/>' + measureInfo[\"yAxis\"] + ': ' + (data[measureInfo[\"yAxis\"]].displayValue ? data[measureInfo[\"yAxis\"]].displayValue : data[measureInfo[\"yAxis\"]].value)\n"
            + "   );\n\n"
            + "   // you could also directly navigate away from the chart using window.location\n"
            + "   // window.location = queryHref;\n"
            + "}";
    },

    getPointClickFnHelp: function() {
        return 'Your code should define a single function to be called when a data point in the chart is clicked. '
            + 'The function will be called with the following parameters:<br/><br/>'
            + '<ul style="margin-left:20px;">'
            + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 50px;">YAxisMeasure: {displayValue: "250", value: 250},<br/>XAxisMeasure: {displayValue: "0.45", value: 0.45000},<br/>ColorMeasure: {value: "Color Value 1"},<br/>PointMeasure: {value: "Point Value 1"}</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>measureInfo:</b> the schema name, query name, and measure names selected for the plot. Example:</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 50px;">schemaName: "study",<br/>queryName: "Dataset1",<br/>yAxis: "YAxisMeasure",<br/>xAxis: "XAxisMeasure",<br/>colorName: "ColorMeasure",<br/>pointName: "PointMeasure"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>';
    },

    setRenderRequested: function(requested){
        this.renderRequested = requested;
    },

    isRenderRequested: function(){
        return this.renderRequested;
    },

    setDataLoading: function(loading){
        this.dataLoading = loading;
    },

    isDataLoading: function(){
        return this.dataLoading;
    },

    requestData: function(){
        this.setDataLoading(true);
        LABKEY.Query.selectRows(this.getQueryConfig());
    },

    requestRender: function(forExport){
        if(this.isDataLoading()){
            this.setRenderRequested(true);
        } else {
            this.renderPlot(forExport);
        }
    }
});
