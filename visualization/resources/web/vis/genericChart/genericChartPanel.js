/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',
    alias: 'widget.labkey-genericchartpanel',

    measures: {},

    constructor : function(config) {

        Ext4.QuickTips.init();

        Ext4.applyIf(config, {
            frame   : false,
            border  : false,
            layout    : 'border',
            editable  : false,
            minWidth  : 800,
            dataLimit : 5000
        });

        // delayed task to redraw the chart
        this.updateChartTask = new Ext4.util.DelayedTask(function(){

            if (this.isConfigurationChanged())
            {
                this.centerPanel.getEl().mask('loading data...');

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
        var fk, params;
        // boolean to check if we should allow things like export to PDF
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8);

        params = LABKEY.ActionURL.getParameters();
        this.editMode = (params.edit == "true" || !this.reportId) && this.allowEditMode;
        this.useRaphael = params.useRaphael != null ? params.useRaphael : false;
        this.parameters = LABKEY.Filter.getQueryParamsFromUrl(params['filterUrl'], this.dataRegionName);

        // Issue 19163
        if (this.autoColumnXName) {
            fk = LABKEY.FieldKey.fromParts(this.autoColumnXName);
            this.autoColumnXName = fk.toString();
        }

        if (this.autoColumnYName) {
            fk = LABKEY.FieldKey.fromParts(this.autoColumnYName);
            this.autoColumnYName = fk.toString();
        }

        var typeConvert = function(value, record){
            // We take the displayFieldJSONType if available because if the column is a look up the record.type will
            // always be INT. The displayFieldJSONType is the actual type of the lookup.

            if(record.data.displayFieldJsonType){
                return record.data.displayFieldJsonType;
            }
            return record.data.type;
        };

        if (!Ext4.ModelManager.isRegistered('MeasureModel')) {
            Ext4.define('MeasureModel',{
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'label', mapping: 'shortCaption', type: 'string'},
                    {name: 'name', type: 'string'},
                    {name: 'hidden', type: 'boolean'},
                    {name: 'measure', type: 'boolean'},
                    {name: 'dimension', type: 'boolean'},
                    {name: 'type'},
                    {name: 'displayFieldJsonType'},
                    {name: 'normalizedType', convert: typeConvert}
                ]
            });
        }

        this.chartDefinitionChanged = new Ext4.util.DelayedTask(function(){
            this.markDirty(true);
            this.requestRender(false);
        }, this);

        // Push the windows into the items list so the modal part only covers the generic chart panel.
        this.items = [
            this.initXMeasureWindow(),
            this.initYMeasureWindow(),
            this.initMainTitleWindow(),
            this.initHelpWindow(),
            this.initSavePanel(),
            this.initOptionsWindow(),
            this.initGroupingWindow(),
            this.initDeveloperWindow(),
            this.initExportScriptWindow(),
            this.initCenterPanel()
        ];

        this.callParent();

        this.on('tabchange', this.onTabChange, this);

        if (this.reportId) {
            this.markDirty(false);
            this.loadReport(this.reportId);
        } else {
            this.markDirty(false);
            this.on('render', this.ensureQuerySettings, this);
        }

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
                autoScroll  : true,
                ui          : 'custom',
                listeners   : {
                    activate  : this.viewPanelActivate,
                    resize    : function(p) { if (this.chartData) { this.requestRender(); }}, // only re-render after the initial chart rendering
                    scope: this
                }
            });
        }

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

    initTbarItems: function(){
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

        this.exportBtn = Ext4.create('Ext.button.Button', {
            text: 'Export',
            scope: this,
            menu: {
                items: [
                    {
                        text: 'Export as PDF',
                        icon: LABKEY.contextPath + '/_icons/pdf.gif',
                        disabled: true,
                        tooltip: !this.supportedBrowser ? "Export to PDF not supported for IE6, IE7, or IE8." : null
                    },
                    {
                        text: 'Export as Script',
                        icon: LABKEY.contextPath + '/_icons/text.png',
                        hidden: !this.isDeveloper,
                        disabled: true
                    }
                ]
            }
        });

        this.helpBtn = Ext4.create('Ext.button.Button', {
            text: "Help",
            scope: this,
            handler: function(){
                this.helpWindow.show();
            }
        });

        this.saveBtn =  Ext4.create('Ext.button.Button', {
            text: "Save",
            hidden: this.hideSave,
            handler: function(){
                this.onSaveBtnClicked(false)
            },
            scope: this
        });

        this.saveAsBtn = Ext4.create('Ext.button.Button', {
            text: "Save As",
            hidden  : this.isNew() || this.hideSave,
            handler: function(){
                this.onSaveBtnClicked(true);
            },
            scope: this
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
                    this.exportBtn.show();

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
                    this.exportBtn.hide();

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
            this.exportBtn
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

            tbarItems.push('->');
            tbarItems.push(this.helpBtn);

            if(this.canEdit){
                tbarItems.push(this.saveBtn);
            }

            tbarItems.push(this.saveAsBtn);
        }
        else if (this.allowEditMode && this.editModeURL != null)
        {
            // add an "edit" button if the user is allowed to toggle to edit mode for this report
            tbarItems.push('->');
            tbarItems.push({
                xtype: 'button',
                text: 'Edit',
                scope: this,
                handler: function() {
                    window.location = this.editModeURL;
                }
            });
        }

        return tbarItems;
    },

    initXMeasureWindow: function(){
        this.xMeasureStore = Ext4.create('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store) {
                    if (!this.restrictColumnsEnabled)
                    {
                        // NOTE: why no normalizedType check here?
                        store.filterBy(function(record, id){
                            return !record.get('hidden');
                        });
                    }
                    else
                    {
                        store.filterBy(function(record, id){
                            return !record.get('hidden') && (record.get('measure') || record.get('dimension'));
                        });
                    }
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
                render : function(grid) {
                    if (grid.getStore().getCount() == 0)
                        grid.getEl().mask("loading...");
                },
                viewready: function(grid) {
                    if(this.measures.x){
                        var measure = grid.getStore().findRecord('name', this.measures.x.name, 0, false, true, true);
                        if (measure) {
                            grid.getSelectionModel().select(measure, false, true);
                            this.xMeasurePanel.measure = measure.data;
                            this.xMeasurePanel.selectionChange(true);
                        }
                    }
                },
                select: function(selModel, record, index){
                    this.measures.x = selModel.getSelection()[0].data;
                    this.xMeasurePanel.measure = selModel.getSelection()[0].data;
                    this.xOkBtn.setDisabled(false);
                    this.xMeasurePanel.selectionChange();
                },
                scope: this
            }
        });

        this.xOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.xMeasureWindow.hide();
                this.centerPanel.getEl().unmask();
                this.xMeasurePanel.checkForChangesAndFireEvents();
            },
            scope: this
        });

        this.xCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.centerPanel.getEl().unmask();
                this.xMeasureWindow.close();
            },
            scope: this
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
                    this.centerPanel.getEl().mask('Rendering Chart...');
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
            closable: false,
            items: [this.xMeasurePanel],
            listeners: {
                show: function(){
                    this.initialPanelValues = this.xMeasurePanel.getPanelOptionValues();
                    this.initialPanelValues.measure = this.xMeasureGrid.getSelectionModel().getLastSelected();

                    this.xOkBtn.setDisabled(!this.xMeasureGrid.getSelectionModel().hasSelection());

                    if (!this.measures.x)
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
                    this.centerPanel.getEl().unmask();
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

        return this.xMeasureWindow;
    },

    initYMeasureWindow: function(){
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
                    if (!this.restrictColumnsEnabled)
                    {
                        store.filterBy(function (record, id){
                            var type = record.get('normalizedType');
                            return !record.get('hidden') && (type == 'int' || type == 'float' || type == 'double');
                        });
                    }
                    else
                    {
                        store.filterBy(function(record, id){
                            var type = record.get('normalizedType');
                            return record.get('measure') && !record.get('hidden') && (type == 'int' || type == 'float' || type == 'double');
                        });
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
                render : function(grid) {
                    if (grid.getStore().getCount() == 0)
                        grid.getEl().mask("loading...");
                },
                viewready: function(grid) {
                    if (this.measures.y) {
                        var measure = grid.getStore().findRecord('name', this.measures.y.name, 0, false, true, true);
                        if(measure){
                            grid.getSelectionModel().select(measure, false, true);
                            this.yMeasurePanel.measure = measure.data;
                            this.yMeasurePanel.selectionChange(true);
                        }
                    }
                },
                select: function(selModel, record, index){
                    this.measures.y = selModel.getSelection()[0].data;
                    this.yMeasurePanel.measure = selModel.getSelection()[0].data;
                    this.yOkBtn.setDisabled(false);
                    this.yMeasurePanel.selectionChange();
                },
                scope: this
            }
        });

        this.yOkBtn = Ext4.create("Ext.Button", {
            text: 'Ok',
            disabled: true,
            handler: function(){
                this.yMeasureWindow.hide();
                this.centerPanel.getEl().unmask();
                this.yMeasurePanel.checkForChangesAndFireEvents();
            },
            scope: this
        });

        this.yCancelBtn = Ext4.create("Ext.Button", {
            text: 'Cancel',
            handler: function(){
                this.centerPanel.getEl().unmask();
                this.yMeasureWindow.close();
            },
            scope: this
        });

        this.yMeasurePanel = Ext4.create('LABKEY.vis.GenericChartAxisPanel',{
            border: false,
            frame: false,
            store: this.yMeasureStore,
            queryName: this.queryName,
            measureGrid: this.yMeasureGrid,
            buttons: [this.yOkBtn, this.yCancelBtn],
            listeners: {
                'chartDefinitionChanged': function(){
                    this.centerPanel.getEl().mask('Rendering Chart...');
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
            closable: false,
            closeAction: 'hide',
            items: [
                this.yMeasurePanel
            ],
            listeners: {
                show: function(){
                    this.initialPanelValues = this.yMeasurePanel.getPanelOptionValues();
                    this.initialPanelValues.measure = this.yMeasureGrid.getSelectionModel().getLastSelected();

                    this.yOkBtn.setDisabled(!this.yMeasureGrid.getSelectionModel().hasSelection());

                    if (!this.measures.y)
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
                    this.centerPanel.getEl().unmask();
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

        return this.yMeasureWindow;
    },

    initMainTitleWindow: function(){
        this.mainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
            listeners: {
                closeOptionsWindow : function(canceling){
                    if (canceling)
                        this.mainTitleWindow.fireEvent('beforeclose');
                    this.mainTitleWindow.hide();
                },
                chartDefinitionChanged: function(){
                    this.centerPanel.getEl().mask('Rendering Chart...');
                    this.chartDefinitionChanged.delay(250);
                },
                resetTitle: function() {
                    // need a reset title function.
                    this.mainTitlePanel.setMainTitle((this.queryLabel || this.queryName) + ' - ' + Ext4.util.Format.htmlEncode(this.measures.y.label))
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
                    this.centerPanel.getEl().mask();
                },
                hide: function(){
                    this.initialPanelValues = null;
                    this.centerPanel.getEl().unmask();
                },
                beforeclose: function(){
                    // on close, we don't apply changes and let the panel restore its state
                    if (this.initialPanelValues)
                        this.mainTitlePanel.restoreValues(this.initialPanelValues);
                },
                scope: this
            }
        });

        return this.mainTitleWindow;
    },

    initCenterPanel: function() {
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
            tbar: this.initTbarItems(),
            listeners : {
                resize: function() {
                    if (!this.optionsWindow.hidden) {
                        this.optionsWindow.alignTo(this.showOptionsBtn, 'tl-tr', [-175, 30]);
                    }
                },
                scope: this
            }
        });

        return this.centerPanel;
    },

    isNew : function() {
        return !this.reportId;
    },

    initHelpWindow: function() {
        this.helpWindow = Ext4.create('Ext.window.Window', {
            title: 'Help',
            width: 300,
            autoHeight : true,
            closeAction: 'hide',
            cls: 'data-window',
            layout: 'fit',
            items: [this.getHelpPanel()],
            listeners: {
                scope: this,
                show: function(){
                    this.centerPanel.getEl().mask();
                    this.helpWindow.alignTo(this.helpBtn, 'tl-tr', [-275, 30]);
                },
                hide: function(){
                    this.centerPanel.getEl().unmask();
                }
            }
        });

        return this.helpWindow;
    },

    getHelpPanel: function(){
        var helpHtml = '<ul>';
        helpHtml += '<li><a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=reportsAndViews">Reports, Views, and Charts</a></li>';
        helpHtml += '<li><a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=boxplot">Box Plots</a></li>';
        helpHtml += '<li><a target="_blank" href="https://www.labkey.org/wiki/home/Documentation/page.view?name=scatterplot">Scatter Plots</a></li>';
        helpHtml += '</ul>';

        this.helpPanel = Ext4.create('Ext.panel.Panel', {
            html        : helpHtml,
            hidden      : false,
            preventHeader : true,
            border      : false,
            frame       : false,
            buttons     : [{
                text: 'close',
                handler: function(){
                    this.helpWindow.hide();
                },
                scope: this
            }]
        });

        return this.helpPanel;
    },

    initGroupingWindow: function(){
        this.groupingMeasureStore = Ext4.create ('Ext.data.Store', {
            model: 'MeasureModel',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json'
                }
            },
            listeners: {
                load: function(store) {
                    if (!this.restrictColumnsEnabled)
                    {
                        store.filterBy(function (record, id){
                            var type = record.get('normalizedType');
                            return !record.get('hidden') && type !== 'float' && type !== 'int' && type !== 'double';
                        });
                    }
                    else
                    {
                        store.filterBy(function(record, id){
                            return record.get('dimension') && !record.get('hidden');
                        });
                    }

                    var firstVal = store.getAt(0);

                    if (firstVal) {

                        this.groupingPanel.supressEvents = true;

                        if (this.groupingPanel.getColorMeasure().name == null) {
                            this.groupingPanel.setColorMeasure(firstVal.data);
                        }
                        if (this.groupingPanel.getPointMeasure().name == null) {
                            this.groupingPanel.setPointMeasure(firstVal.data);
                        }

                        this.groupingPanel.supressEvents = false;
                    }
                },
                scope: this
            }
        });

        this.groupingPanel = Ext4.create('LABKEY.vis.GenericChartGroupingPanel', {
            width: '100%',
            store: this.groupingMeasureStore,
            listeners: {
                chartDefinitionChanged: function(){
                    this.centerPanel.getEl().mask('Rendering Chart...');
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

        return this.groupingWindow;
    },

    initOptionsWindow: function(){
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
                    this.centerPanel.getEl().mask('Rendering Chart...');
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
            width: 330,
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

        return this.optionsWindow;
    },

    initDeveloperWindow: function(){
        this.developerPanel = Ext4.create('LABKEY.vis.DeveloperOptionsPanel', {
            isDeveloper: this.isDeveloper || false,
            pointClickFn: null,
            defaultPointClickFn: this.getDefaultPointClickFn(),
            pointClickFnHelp: this.getPointClickFnHelp(),
            listeners: {
                chartDefinitionChanged: function(){
                    this.centerPanel.getEl().mask('Rendering Chart...');
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

        return this.developerWindow;
    },

    initExportScriptWindow: function(){
        if (!this.exportScriptWindow) {
            this.scriptPanel = Ext4.create('LABKEY.vis.GenericChartScriptPanel', {
                listeners: {
                    scope: this,
                    'closeOptionsWindow': function(){
                        this.exportScriptWindow.hide();
                    }
                }
            });

            this.exportScriptWindow = Ext4.create('Ext.window.Window', {
                title: "Export Script",
                cls: 'data-window',
                border: false,
                frame: false,
                modal: true,
                relative: this.exportBtn,
                width: 800,
                resizable: false,
                closeAction: 'hide',
                items: [{
                    xtype: 'panel',
                    items: [this.scriptPanel]
                }]
            });
        }

        return this.exportScriptWindow;
    },

    renderDataGrid : function(renderTo) {
        var urlParams = LABKEY.ActionURL.getParameters(this.baseUrl);
        var filterUrl = urlParams['filterUrl'];
        var userFilters, wpConfig, wp;

        if (this.userFilters)
            userFilters = this.userFilters;
        else
            userFilters = LABKEY.Filter.getFiltersFromUrl(filterUrl, this.dataRegionName);

        var userSort = LABKEY.Filter.getSortFromUrl(filterUrl, this.dataRegionName);

        this.currentFilterStr = this.createFilterString(userFilters);
        this.currentParameterStr = Ext4.JSON.encode(this.parameters);

        wpConfig = {
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            viewName    : this.viewName,
            columns     : this.savedColumns,        // TODO, qwp does not support passing in a column list
            parameters  : this.parameters,
            frame       : 'none',
            removeableFilters     : userFilters,
            removeableSort        : userSort,
            showSurroundingBorder : false,
            showPagination        : false,
            maxRows               : this.dataLimit,
            buttonBar   : {
                includeStandardButton: false,
                items: [LABKEY.QueryWebPart.standardButtons.exportRows]
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
        };

        if (this.dataRegionName) {
            wpConfig.dataRegionName = this.dataRegionName;
        }

        wp = new LABKEY.QueryWebPart(wpConfig);
        // save the dataregion
        this.panelDataRegionName = wp.dataRegionName;

        // issue 21418: support for parameterized queries
        wp.on('render', function(){
            if (wp.parameters)
                this.updateQueryParameters(wp.parameters);
        }, this);

        wp.render(renderTo);
    },

    updateQueryParameters : function(updatedParams) {
        for (var param in updatedParams)
        {
            var pref = this.panelDataRegionName + ".param.";
            if (param.indexOf(pref) == 0) {
                this.parameters[param.replace(pref, "")] = updatedParams[param];
            }
        }
    },

    // Returns a configuration based on the baseUrl plus any filters applied on the dataregion panel
    // the configuration can be used to make a selectRows request
    getQueryConfig : function(serialize) {
        var dataRegion = LABKEY.DataRegions[this.panelDataRegionName];

        var config = {
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            viewName    : this.viewName,
            dataRegionName: this.dataRegionName,
            queryLabel  : this.queryLabel,
            parameters  : this.parameters,
            maxRows     : this.dataLimit, // TODO: should only limit rows for scatter plot, not box plot
            requiredVersion : 12.1,
            method: 'POST'
        };

        config.columns = this.getQueryConfigColumns();

        if(!serialize){
            config.success = this.onSelectRowsSuccess;
            config.failure = function(response, opts){
                var error, errorDiv;

                this.centerPanel.getEl().unmask();

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

                // Issue 18157
                this.showOptionsBtn.setDisabled(true);
                this.groupingBtn.setDisabled(true);
                this.developerBtn.setDisabled(true);
                this.toggleBtn.setDisabled(true);
                this.saveBtn.setDisabled(true);
                this.saveAsBtn.setDisabled(true);

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
            var measures = this.getChartConfig().measures;

            if(measures.x){
                columns.push(measures.x.name);
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

            if(measures.y){
                columns.push(measures.y.name);
            } else if(this.autoColumnYName){
                columns.push(this.autoColumnYName);
            }

            if(measures.color){
                columns.push(measures.color.name)
            }

            if(measures.shape){
                columns.push(measures.shape.name);
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
        var xAxisOptions = this.xMeasurePanel.getPanelOptionValues();
        var yAxisOptions = this.yMeasurePanel.getPanelOptionValues();
        var chartOptions = this.optionsPanel.getPanelOptionValues();
        var groupingOptions = this.groupingPanel.getPanelOptionValues();
        var developerOptions = this.developerPanel.getPanelOptionValues();
        var mainTitle = this.mainTitlePanel.getPanelOptionValues().title;

        config.width = chartOptions.width;
        config.height = chartOptions.height;
        config.renderType = chartOptions.renderType;
        config.pointType = chartOptions.pointType;
        config.geomOptions = {
            boxFillColor: chartOptions.boxFillColor,
            lineColor: chartOptions.lineColor,
            lineWidth: chartOptions.lineWidth,
            opacity: chartOptions.opacity,
            pointFillColor: chartOptions.pointFillColor,
            pointSize: chartOptions.pointSize,
            position: chartOptions.position
        };
        config.scales = {
            x: {trans: xAxisOptions.scaleTrans},
            y: {trans: yAxisOptions.scaleTrans}
        };
        config.labels = {
            x: xAxisOptions.label,
            y: yAxisOptions.label
        };

        if(!this.mainTitlePanel.userEditedTitle || mainTitle == null || mainTitle == undefined){
            config.labels.main = this.getDefaultTitle();
            this.mainTitlePanel.setMainTitle(config.labels.main, true);
        } else {
            config.labels.main = mainTitle;
        }

        config.measures = Ext4.apply({}, this.measures);

        if (groupingOptions.color) {
            config.measures.color = groupingOptions.color;
        }

        if (groupingOptions.shape) {
            config.measures.shape = groupingOptions.shape;
        }

        if (developerOptions.pointClickFn) {
            config.measures.pointClickFn = this.developerPanel.removeLeadingComments(developerOptions.pointClickFn);
        }

        if (this.curveFit) {
            config.curveFit = this.curveFit;
        }

        if (this.getCustomChartOptions) {
            config.customOptions = this.getCustomChartOptions();
        }

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
                        var proxy = queryStore.getProxy();
                        if (proxy) {
                            proxy.extraParams = {schemaName : newValue};
                            queryStore.load();
                        }

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
                displayField   : 'queryLabel',
                triggerAction  : 'all',
                typeAhead      : true,
                valueField     : 'name',
                emptyText      : 'None',
                listeners   : {
                    change : {fn : function(cmp, newValue) {
                        var selected = cmp.getStore().getAt(cmp.getStore().find('name', newValue));
                        this.queryLabel = selected ? selected.data.title : null;
                        this.queryName = selected ? selected.data.name : null;
                    }, scope : this}
                }
            });

            queryStore.addListener('beforeload', function(){
                selectQuerySettingsBtn.setDisabled(true);
            }, this);

            var selectQuerySettingsBtn = Ext4.create('Ext.button.Button', {
                text : 'Ok',
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
                    labelWidth : 75,
                    width      : 375,
                    style      : 'padding: 4px 0',
                    labelSeparator : ''
                },
                items   : formItems,
                buttonAlign : 'right',
                buttons     : [ selectQuerySettingsBtn, {
                    text : 'Cancel',
                    handler : function(btn) {window.history.back()}
                }]
            });

            var dialog = Ext4.create('Ext.window.Window', {
                layout : 'fit',
                border : false,
                frame  : false,
                closable : false,
                draggable : false,
                modal  : true,
                cls: 'data-window',
                title  : 'Select Query',
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
                {name : 'assay'},
                {name : 'lists'},
                {name : 'study'}
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
                {name : 'title'},
                {name : 'queryLabel', convert: function(value, record){
                    return record.data.name != record.data.title ? record.data.name + ' (' + record.data.title + ')' : record.data.title;
                }},
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
            },
            sorters : [{property: 'title', direction: 'ASC'}]
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
            reportId    : this.reportId,
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
        var reportConfig = this.getCurrentReportConfig();
        reportConfig.name = data.reportName;
        reportConfig.description = data.reportDescription;

        reportConfig["public"] = data.shared;
        reportConfig.thumbnailType =  data.thumbnailType;
        reportConfig.svg = this.chartSVG;

        if(data.isSaveAs){
            reportConfig.reportId = null;
        }

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'saveGenericReport.api'),
            method  : 'POST',
            headers : {
                'Content-Type' : 'application/json'
            },
            jsonData: reportConfig,
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

                this.updateWebpartTitle(reportConfig.name);

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
            Ext.MessageBox.alert('Error', 'An unknown error has occurred, unable to save the chart.');
        }
    },

    loadReport : function(reportId) {
        this.reportLoaded = false;
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'getGenericReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                this.saveAsBtn.setVisible(!this.isNew() && !this.hideSave);
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

        this.editorSavePanel.setReportInfo({
            name: config.name,
            description: config.description,
            shared: config["public"],
            reportProps: config.reportProps,
            thumbnailURL: config.thumbnailURL
        });

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

        if (json.queryConfig.queryLabel)
            this.queryLabel = json.queryConfig.queryLabel;

        if (json.queryConfig.parameters)
            this.parameters = json.queryConfig.parameters;

        if (json.chartConfig)
        {
            this.measures = {};
            this.measures.x = json.chartConfig.measures.x;
            this.measures.y = json.chartConfig.measures.y;

            if(!json.chartConfig){
                this.optionsPanel.setPanelOptionValues({renderType: this.renderType});
            } else {
                this.optionsPanel.setPanelOptionValues(json.chartConfig);
                if(json.chartConfig.labels.main != null && json.chartConfig.labels.main != undefined){
                    this.mainTitlePanel.setMainTitle(json.chartConfig.labels.main, true);
                    if(this.getDefaultTitle() != this.mainTitlePanel.getPanelOptionValues().title){
                        this.mainTitlePanel.userEditedTitle = true;
                    }
                } else {
                    this.mainTitlePanel.setMainTitle(this.getDefaultTitle(), true);
                }

                if(json.chartConfig.scales.x || json.chartConfig.labels.x){
                    this.xMeasurePanel.setPanelOptionValues({
                        label: json.chartConfig.labels.x,
                        scaleTrans: json.chartConfig.scales.x ? json.chartConfig.scales.x.trans : null
                    });
                    this.xMeasurePanel.measure = this.measures.x;
                    if(this.measures.x && this.xMeasurePanel.getAxisLabel() != this.getDefaultXAxisLabel()){
                        this.xMeasurePanel.userEditedLabel = true;
                    }
                }

                if(json.chartConfig.labels.y || json.chartConfig.scales.y){
                    this.yMeasurePanel.setPanelOptionValues({
                        label: json.chartConfig.labels.y,
                        scaleTrans: json.chartConfig.scales.y ? json.chartConfig.scales.y.trans : null
                    });
                    this.yMeasurePanel.measure = this.measures.y;
                    if(this.measures.y && this.yMeasurePanel.getAxisLabel() != this.getDefaultYAxisLabel()){
                        this.yMeasurePanel.userEditedLabel = true;
                    }
                }

                if(json.chartConfig.measures.color || json.chartConfig.measures.shape){
                    this.groupingPanel.setPanelOptionValues(json.chartConfig.measures);
                }

                if (json.chartConfig.measures.pointClickFn){
                    this.developerPanel.setPanelOptionValues(json.chartConfig.measures);
                }
            }

            if (json.chartConfig.curveFit)
                this.curveFit = json.chartConfig.curveFit;
        }

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    handleNoData: function(){
        // Issue 18339
        this.setRenderRequested(false);
        var error = 'The response returned 0 rows of data. The query may be empty or the applied filters may be too strict. Try removing or adjusting any filters if possible.';
        var errorDiv = Ext4.create('Ext.container.Container', {
            border: 1,
            autoEl: {tag: 'div'},
            html: '<h3 style="color:red;">An unexpected error occurred while retrieving data.</h2>' + error,
            autoScroll: true
        });
        this.showOptionsBtn.setDisabled(true);
        this.groupingBtn.setDisabled(true);
        this.developerBtn.setDisabled(true);
        this.saveBtn.setDisabled(true);
        this.saveAsBtn.setDisabled(true);
        this.disableExportPdf();
        this.disableExportScript();
        this.toggleBtn.setDisabled(false); // Keep the toggle button enabled so the user can remove filters
        this.clearChartPanel();
        this.viewPanel.add(errorDiv);
    },

    renderPlot: function(forExport){
        if (this.viewPanel.isHidden()) {
            // Don't attempt to render if the view panel isn't visible.
            return;
        }

        if (!forExport) {
            this.centerPanel.getEl().mask('Rendering Chart...');
            this.clearChartPanel();
        }

        if (this.chartData.rows.length === 0) {
            this.centerPanel.getEl().unmask();
            this.handleNoData();
            return;
        }

        if (!this.initMeasures(forExport)) {
            // initMeasures returns false and opens the x/y measure panel if a measure is not chosen by the user.
            return;
        }

        var newChartDiv = Ext4.create('Ext.container.Container', {
            border: 1,
            autoEl: {tag: 'div'}
        });
        this.viewPanel.add(newChartDiv);

        var GCH = LABKEY.vis.GenericChartHelper;
        var chartConfig = this.getChartConfig();
        var customRenderType, chartType, geom, aes, scales, labels, layerConfig, plotConfig, width, height;

        chartConfig.measures.x = this.measures.x;
        chartConfig.measures.y = this.measures.y;
        chartConfig.geomOptions.showOutliers = chartConfig.pointType ? chartConfig.pointType == 'outliers' : true;

        if (this.customRenderTypes && this.customRenderTypes[this.renderType]) {
            customRenderType = this.customRenderTypes[this.renderType];
        }

        chartType = GCH.getChartType(this.renderType, chartConfig.measures.x ? chartConfig.measures.x.normalizedType : null);
        aes = GCH.generateAes(chartType, chartConfig.measures, this.chartData.schemaName, this.chartData.queryName);
        scales = GCH.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, this.chartData, this.defaultNumberFormat);
        geom = GCH.generateGeom(chartType, chartConfig.geomOptions);
        labels = this.configureLabels(forExport, chartConfig);
        width = chartConfig.width ? chartConfig.width : !forExport ? this.viewPanel.getWidth() : 1200;
        height = chartConfig.height ? chartConfig.height : !forExport ? this.viewPanel.getHeight() - 25 : 600;

        if (customRenderType) {
            if (customRenderType.generateAes) {
                aes = customRenderType.generateAes(this, chartConfig, aes);
            }

            if (customRenderType.generateScales) {
                scales = customRenderType.generateScales(this, chartConfig, scales);
            }
        }

        if (this.chartData.rows.length == this.dataLimit) {
            this.addWarningText("The data limit for plotting has been reached. Consider filtering your data.");
        }

        this.validateGroupingMeasures(chartConfig.measures);

        if (!this.validateXAxis(chartType, chartConfig, aes, scales, this.chartData.rows)) {
            return;
        }

        if (!this.validateYAxis(chartType, chartConfig, aes, scales, this.chartData.rows)) {
            return;
        }

        if(this.warningText !== null) {
            height = height - 15;
        }

        if (customRenderType && customRenderType.generatePlotConfig) {
            plotConfig = customRenderType.generatePlotConfig(this, chartConfig, newChartDiv.id, width, height, this.chartData.rows, aes, scales, labels);
        } else {
            var layers = [];

            if(chartConfig.pointType == 'all') {
                layers.push(new LABKEY.vis.Layer({
                    data: this.chartData.rows,
                    geom: GCH.generatePointGeom(chartConfig.geomOptions),
                    aes: {hoverText: GCH.generatePointHover(chartConfig.measures)}
                }));
            }

            layers.push(new LABKEY.vis.Layer({data: this.chartData.rows, geom: geom}));

            // client has specified a line type
            if (this.curveFit) {
                var factory = this.lineRenderers[this.curveFit.type];
                if (factory) {
                    layers.push(
                            new LABKEY.vis.Layer({
                                geom: new LABKEY.vis.Geom.Path({}),
                                aes: {x: 'x', y: 'y'},
                                data: LABKEY.vis.Stat.fn(factory.createRenderer(this.curveFit.params),
                                        this.curveFit.points, this.curveFit.min, this.curveFit.max)})
                    );
                }
            }

            plotConfig = {
                renderTo: newChartDiv.id,
                width: width,
                height: height,
                labels: labels,
                aes: aes,
                scales: scales,
                layers: layers,
                data: this.chartData.rows
            };
        }

        if(this.supportedBrowser && !this.useRaphael) {
            plotConfig.rendererType = 'd3';
        }

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();

        if(this.warningText !== null) {
            var warningDiv = document.createElement('div');
            warningDiv.setAttribute('class', 'labkey-error');
            warningDiv.setAttribute('style', 'text-align: center');
            warningDiv.innerHTML = this.warningText;
            newChartDiv.getEl().insertFirst(warningDiv);
        }

        if (!forExport){
            this.showOptionsBtn.setDisabled(false);
            this.groupingBtn.setDisabled(false);
            this.developerBtn.setDisabled(false);
            this.saveBtn.setDisabled(false);
            this.saveAsBtn.setDisabled(false);
            this.enableExportPdf();
            this.enableExportScript();
            this.centerPanel.getEl().unmask();
            if(this.editMode && this.supportedBrowser){
                // Update thumbnail
                var thumbnail = this.renderPlot(true);
                if (thumbnail)
                {
                    this.chartSVG = LABKEY.vis.SVGConverter.svgToStr(Ext4.get(thumbnail).child('svg').dom);
                    this.editorSavePanel.updateCurrentChartThumbnail(this.chartSVG, Ext4.get(thumbnail).getSize());
                    // destroy the temp chart element
                    Ext4.getCmp(thumbnail).destroy();
                }
            }
        } else{
            return newChartDiv.id;
        }

        this.setRenderRequested(false); // We just rendered the plot, we don't need to request another render.
    },

    initMeasures: function(forExport){
        // Initialize the x and y measures on first chart load. Returns false if we're missing the x or y measure.
        var measure, fk;

        if (!this.measures.y && !forExport) {
            if (this.autoColumnYName)
            {
                // In some cases the column name is escaped, so we need to unescape it when searching.
                fk = LABKEY.FieldKey.fromString(this.autoColumnYName);
                measure = this.yMeasureStore.findRecord('name', fk.name, 0, false, true, true);
                if (measure){
                    this.measures.y = measure.data;
                    this.yMeasurePanel.measure = measure.data;
                    this.yMeasureGrid.getSelectionModel().select(measure, false, true);
                    this.yMeasurePanel.selectionChange(true);
                }
            }

            if (!this.measures.y)
            {
                this.centerPanel.getEl().unmask();
                this.showYMeasureWindow();
                return false;
            }
        }

        if (!this.measures.x && !forExport) {
            if(this.renderType !== "box_plot" && this.renderType !== "auto_plot"){
                if (this.autoColumnXName)
                {
                    fk = LABKEY.FieldKey.fromString(this.autoColumnXName);
                    measure = this.xMeasureStore.findRecord('name', fk.name, 0, false, true, true);
                    if (measure) {
                        this.measures.x = measure.data;
                        this.xMeasurePanel.measure = measure.data;
                        this.xMeasureGrid.getSelectionModel().select(measure, false, true);
                        this.xMeasurePanel.selectionChange(true);
                    }
                }

                if (!this.measures.x)
                {
                    this.centerPanel.getEl().unmask();
                    this.showXMeasureWindow();
                    return false;
                }
            } else {
                measure = this.xMeasureStore.findRecord('label', 'Cohort', 0, false, true, true);
                if (measure) {
                    this.measures.x = measure.data;
                    this.xMeasurePanel.measure = measure.data;
                    this.xMeasureGrid.getSelectionModel().select(measure, false, true);
                    this.xMeasurePanel.selectionChange(true);
                }
            }
        }

        return true;
    },

    validateYAxis: function(chartType, chartConfig, aes, scales, data){
        var validation = LABKEY.vis.GenericChartHelper.validateYAxis(chartType, chartConfig, aes, scales, data);

        if(validation.success === true) {
            if(validation.message != null) {
                this.addWarningText(validation.message);
            }

            return true;
        } else {
            this.centerPanel.getEl().unmask();
            this.setRenderRequested(false);

            if (this.editMode) {
                Ext.MessageBox.alert('Error', validation.message, this.showYMeasureWindow, this);
            } else {
                this.clearChartPanel();
                var errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
                    html: '<h3 style="color:red;">Error rendering chart:</h2>' + validation.message,
                    autoScroll: true
                });
                this.viewPanel.add(errorDiv);
            }
            return false;
        }
    },

    validateXAxis: function(chartType, chartConfig, aes, scales, data){
        var validation = LABKEY.vis.GenericChartHelper.validateXAxis(chartType, chartConfig, aes, scales, data);

        if(validation.success === true) {
            if(validation.message != null) {
                this.addWarningText(validation.message);
            }

            return true;
        } else {
            this.centerPanel.getEl().unmask();
            this.setRenderRequested(false);

            if (this.editMode) {
                Ext.MessageBox.alert('Error', validation.message, this.showXMeasureWindow, this);
            } else {
                this.clearChartPanel();
                var errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
                    html: '<h3 style="color:red;">Error rendering chart:</h2>' + validation.message,
                    autoScroll: true
                });
                this.viewPanel.add(errorDiv);
            }

            return false;
        }
    },

    validateGroupingMeasures: function(measures){
        // Checks to make sure the grouping measures are still available, if not we show an error.

        if(measures.color && this.groupingMeasureStore.find('name', measures.color.name, null, null, null, true) === -1){
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point color, "' +
                            measures.color.label +
                            '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.color = undefined;
        }

        if(measures.shape && this.groupingMeasureStore.find('name', measures.shape.name, null, null, null, true) === -1){
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point shape, "' +
                            measures.shape.label +
                            '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.shape = undefined;
        }
    },

    configureLabels: function(forExport, chartConfig){
        var labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);

        if (!forExport && this.editMode) {
            labels.x.lookClickable = true;
            labels.x.listeners = {
                click: this.getXClickHandler(this)
            };

            labels.y.lookClickable = true;
            labels.y.listeners = {
                click: this.getYClickHandler(this)
            };

            labels.main.lookClickable = true;
            labels.main.listeners = {
                click: this.getMainClickHandler(this)
            };

            if(labels.main.value == null || Ext4.util.Format.trim(labels.main.value) == '') {
                labels.main.value = "Edit Title";
            }

            if(labels.y.value == null || Ext4.util.Format.trim(labels.y.value) == '') {
                labels.y.value = "Edit Axis Label";
            }

            if(labels.x.value == null || Ext4.util.Format.trim(labels.x.value) == '') {
                if (chartConfig.measures.x) {
                    labels.x.value = "Edit Axis Label";
                } else {
                    labels.x.value = "Choose a column";
                }
            }
        }

        return labels;
    },

    getXClickHandler: function(scopedThis){
        return function(){
            scopedThis.showXMeasureWindow();
        }
    },
    getYClickHandler: function(scopedThis){
        return function(){
            scopedThis.showYMeasureWindow();
        }
    },
    getMainClickHandler: function(scopedThis){
        return function(){
            scopedThis.showMainTitleWindow();
        }
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

    clearChartPanel: function(){
        this.clearWarningText();
        this.viewPanel.removeAll();

        this.disableExportPdf();
        this.disableExportScript();
    },

    clearWarningText: function(){
        this.warningText = null;
    },

    addWarningText: function(warning){
        if(!this.warningText){
            this.warningText = warning;
        } else {
            this.warningText = this.warningText + '<br />' + warning;
        }
    },

    getExportItem: function(menuLabel) {
        var menuItems = this.exportBtn.menu.items.items;
        var exportPdfItem;

        for (var i = 0; i < menuItems.length; i++) {
            if (menuItems[i].text == menuLabel) {
                exportPdfItem = menuItems[i];
                break;
            }
        }

        return exportPdfItem;
    },

    enableExportPdf: function() {
        var exportPdfItem = this.getExportItem("Export as PDF");

        if(exportPdfItem){
            exportPdfItem.setDisabled(!this.supportedBrowser);
            exportPdfItem.addListener('click', this.exportChartToPdf, this);
        }
    },

    disableExportPdf: function() {
        var exportPdfItem = this.getExportItem("Export as PDF");

        if(exportPdfItem){
            exportPdfItem.setDisabled(true);
            exportPdfItem.removeListener('click', this.exportChartToPdf);
        }
    },

    enableExportScript: function() {
        var exportPdfItem = this.getExportItem("Export as Script");

        if(exportPdfItem){
            exportPdfItem.setDisabled(false);
            exportPdfItem.addListener('click', this.exportChartToScript, this);
        }
    },

    disableExportScript: function() {
        var exportPdfItem = this.getExportItem("Export as Script");

        if(exportPdfItem){
            exportPdfItem.setDisabled(true);
            exportPdfItem.removeListener('click', this.exportChartToScript);
        }
    },

    exportChartToPdf: function() {
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            // export the temp chart as a pdf with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'pdf', this.getChartConfig().labels.main);
            Ext4.getCmp(tempDivId).destroy();
        }
    },

    exportChartToScript: function() {
        var chartConfig = this.getChartConfig();
        var queryConfig = this.getQueryConfig(true);

        // Only push the required columns.
        queryConfig.columns = [];
        queryConfig.columns.push(chartConfig.measures.x.name);
        queryConfig.columns.push(chartConfig.measures.y.name);

        if (chartConfig.measures.color) {
            queryConfig.columns.push(chartConfig.measures.color.name);
        }

        if (chartConfig.measures.shape) {
            queryConfig.columns.push(chartConfig.measures.shape.name);
        }

        var templateConfig = {
            chartConfig: chartConfig,
            queryConfig: queryConfig
        };

        this.scriptPanel.setScriptValue(templateConfig);
        this.exportScriptWindow.show();
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
        if(!this.yMeasureWindow.isVisible() || !this.xMeasureWindow.isVisible() || !this.mainTitleWindow.isVisible()) {
            this.centerPanel.getEl().unmask();
        }

        this.chartData = response;
        var sortedFields = this.sortFields(this.chartData.metaData.fields);
        this.yMeasureStore.loadRawData(sortedFields);
        this.xMeasureStore.loadRawData(sortedFields);
        this.groupingMeasureStore.loadRawData(this.chartData.metaData.fields);
        this.groupingPanel.loadStore(this.groupingMeasureStore);

        if(this.yMeasureGrid.getEl() && this.yMeasureGrid.getEl().isMasked()){
            this.yMeasureGrid.getEl().unmask();
        }

        if(this.xMeasureGrid.getEl() && this.xMeasureGrid.getEl().isMasked()){
            this.xMeasureGrid.getEl().unmask();
        }

        this.setDataLoading(false);

        if (response.rows.length === 0) {
            this.handleNoData();
        } else {
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
        this.centerPanel.getEl().mask();
        this.yMeasureWindow.show();

        if(this.isDataLoading()){
            this.yMeasureGrid.getEl().mask("Loading measures...");
        }
    },

    showXMeasureWindow: function(){
        this.centerPanel.getEl().mask();
        this.xMeasureWindow.show();

        if(this.isDataLoading()){
            this.xMeasureGrid.getEl().mask("Loading measures...");
        }
    },

    showMainTitleWindow: function(){
        this.mainTitleWindow.show();
    },

    getDefaultTitle: function(){
        if(this.defaultTitleFn){
            return this.defaultTitleFn(this.queryName, this.queryLabel, this.measures.y ? this.measures.y.label : null, this.measures.x ? this.measures.x.label : null);
        }
        return (this.queryLabel || this.queryName) + (this.measures.y ? ' - ' + this.measures.y.label : '');
    },

    getDefaultYAxisLabel: function(){
        return this.measures.y ? this.measures.y.label : 'y-axis';
    },

    getDefaultXAxisLabel: function(){
        return this.measures.x.label;
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

        var filterStr = this.createFilterString(queryCfg.filterArray);

        if (this.currentFilterStr != filterStr) {
            this.currentFilterStr = filterStr;
            return true;
        }

        var parameterStr = Ext4.JSON.encode(queryCfg.parameters);
        if (this.currentParameterStr != parameterStr) {
            this.currentParameterStr = parameterStr;
            return true;
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
            + "       'Schema: ' + measureInfo[\"schemaName\"]\n"
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
            + 'The function will be called with the following parameters:<br/>'
            + '<ul>'
            + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">YAxisMeasure: {displayValue: "250", value: 250},<br/>XAxisMeasure: {displayValue: "0.45", value: 0.45000},<br/>ColorMeasure: {value: "Color Value 1"},<br/>PointMeasure: {value: "Point Value 1"}</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>measureInfo:</b> the schema name, query name, and measure names selected for the plot. Example:</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">schemaName: "study",<br/>queryName: "Dataset1",<br/>yAxis: "YAxisMeasure",<br/>xAxis: "XAxisMeasure",<br/>colorName: "ColorMeasure",<br/>pointName: "PointMeasure"</div>'
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
    },

    onSaveBtnClicked: function(isSaveAs){
        this.editorSavePanel.setNoneThumbnail(this.getNoneThumbnailURL());
        this.editorSavePanel.setSaveAs(isSaveAs);
        this.saveWindow.setTitle(isSaveAs ? "Save As" : "Save");
        this.saveWindow.show();
    },

    initSavePanel: function(){
         this.editorSavePanel = Ext4.create('LABKEY.vis.SaveOptionsPanel', {
            canEdit: this.canEdit,
            canShare: this.allowShare,
            listeners: {
                scope: this,
                'closeOptionsWindow': function(){this.saveWindow.close()},
                'saveChart': this.saveReport
            }
        });

        this.saveWindow = Ext4.create('Ext.window.Window', {
            title: "Save",
            width  : 500,
            autoHeight : true,
            cls: 'data-window',
            layout : 'fit',
            draggable : false,
            closeAction: 'hide',
            modal: true,
            items: [this.editorSavePanel]
        });

        return this.saveWindow;
    },

    getNoneThumbnailURL: function(){
        if(!this.measures.x || this.isBoxPlot(this.renderType, this.measures.x.normalizedType)){
            return LABKEY.contextPath + '/visualization/report/box_plot.png';
        }

        if(this.measures.x && this.isScatterPlot(this.renderType, this.measures.x.normalizedType)){
            return LABKEY.contextPath + '/visualization/report/scatter_plot.png';
        }

        return LABKEY.contextPath + '/visualization/report/box_plot.png';
    }
});
