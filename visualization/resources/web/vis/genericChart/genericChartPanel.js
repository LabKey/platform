/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

LABKEY.vis.USE_NEW_CHART_WIZARD = false;

Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',

    alias: 'widget.labkey-genericchartpanel',

    cls : 'generic-chart-panel',
    layout : 'fit',
    border : false,
    editable : false,
    minWidth : 800,

    dataLimit : 5000,

    constructor : function(config) {

        Ext4.QuickTips.init();

        // delayed task to redraw the chart
        this.updateChartTask = new Ext4.util.DelayedTask(function(){

            if (this.isConfigurationChanged())
            {
                this.getEl().mask('Loading Data...');

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
                            this.initialColumnList = o.columns.all;
                            this.columnTypes = o.columns;

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
            auto_plot : 'Auto Plot',
            scatter_plot : 'Scatter Plot',
            box_plot : 'Box Plot'
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

    initComponent : function()
    {
        this.measures = {};
        this.options = {};

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

        if (LABKEY.vis.USE_NEW_CHART_WIZARD && this.renderType === 'auto_plot')
            this.setRenderType('box_plot');

        this.chartDefinitionChanged = new Ext4.util.DelayedTask(function(){
            this.markDirty(true);
            this.requestRender(false);
        }, this);

        this.items = [this.getCenterPanel()];

        this.callParent();

        this.on('tabchange', this.onTabChange, this);

        if (this.reportId)
        {
            this.markDirty(false);
            this.loadReport(this.reportId);
        }
        else
        {
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
                items       : dataGrid
            });
        }

        return this.dataPanel;
    },

    getOptionsBtn : function()
    {
        if (!this.optionsBtn)
        {
            this.optionsBtn = Ext4.create('Ext.button.Button', {
                text: 'Options',
                handler: function(){
                    this.getOptionsWindow().setVisible(this.getOptionsWindow().isHidden());
                },
                scope: this
            });
        }

        return this.optionsBtn;
    },

    getGroupingBtn : function()
    {
        if (!this.groupingBtn)
        {
            this.groupingBtn = Ext4.create('Ext.button.Button', {
                text: 'Grouping',
                handler: function(){
                    this.getGroupingWindow().setVisible(this.getGroupingWindow().isHidden());
                },
                scope: this
            });
        }

        return this.groupingBtn;
    },

    getDeveloperBtn : function()
    {
        if (!this.developerBtn)
        {
            this.developerBtn = Ext4.create('Ext.button.Button', {
                text: 'Developer',
                hidden: !this.isDeveloper,
                disabled: !this.supportedBrowser,
                tooltip: !this.supportedBrowser ? "Developer options not supported for IE6, IE7, or IE8." : null,
                handler: function(){
                    this.getDeveloperWindow().setVisible(this.getDeveloperWindow().isHidden());
                },
                scope: this
            });
        }

        return this.developerBtn;
    },

    getChartTypeBtn : function()
    {
        if (!this.chartTypeBtn)
        {
            this.chartTypeBtn = Ext4.create('Ext.button.Button', {
                text: 'Chart Type',
                handler: this.showChartTypeWindow,
                scope: this
            });
        }

        return this.chartTypeBtn;
    },

    showChartTypeWindow : function()
    {
        this.getChartTypeWindow().show();
    },

    getLookAndFeelBtn : function()
    {
        if (!this.lookAndFeelBtn)
        {
            this.lookAndFeelBtn = Ext4.create('Ext.button.Button', {
                text: 'Look & Feel',
                handler: this.showLookAndFeelWindow,
                scope: this
            });
        }

        return this.lookAndFeelBtn;
    },

    showLookAndFeelWindow : function()
    {
        this.getLookAndFeelWindow().show();
    },

    getExportBtn : function()
    {
        if (!this.exportBtn)
        {
            this.exportBtn = Ext4.create('Ext.button.Button', {
                text: 'Export',
                scope: this,
                menu: {
                    showSeparator: false,
                    items: [
                        {
                            text: 'PDF',
                            iconCls: 'fa fa-file-pdf-o',
                            disabled: true,
                            tooltip: !this.supportedBrowser ? "Export to PDF not supported for IE6, IE7, or IE8." : null
                        },
                        {
                            text: 'PNG',
                            iconCls: 'fa fa-file-image-o',
                            disabled: true,
                            tooltip: !this.supportedBrowser ? "Export to PNG not supported for IE6, IE7, or IE8." : null
                        },
                        {
                            text: 'Script',
                            iconCls: 'fa fa-file-text-o',
                            hidden: !this.isDeveloper,
                            disabled: true
                        }
                    ]
                }
            });
        }

        return this.exportBtn;
    },

    getHelpBtn : function()
    {
        if (!this.helpBtn)
        {
            this.helpBtn = Ext4.create('Ext.button.Button', {
                text: 'Help',
                scope: this,
                menu: {
                    showSeparator: false,
                    items: [{
                        text: 'Reports and Visualizations',
                        iconCls: 'fa fa-table',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=reportsAndViews'
                    },{
                        text: 'Box Plots',
                        iconCls: 'fa fa-sliders fa-rotate-90',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=boxplot'
                    },{
                        text: 'Scatter Plots',
                        iconCls: 'fa fa-area-chart',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=scatterplot'
                    }]
                }
            });
        }

        return this.helpBtn;
    },

    getSaveBtn : function()
    {
        if (!this.saveBtn)
        {
            this.saveBtn =  Ext4.create('Ext.button.Button', {
                text: "Save",
                hidden: this.hideSave,
                handler: function(){
                    this.onSaveBtnClicked(false)
                },
                scope: this
            });
        }

        return this.saveBtn;
    },

    getSaveAsBtn : function()
    {
        if (!this.saveAsBtn)
        {
            this.saveAsBtn = Ext4.create('Ext.button.Button', {
                text: "Save As",
                hidden  : this.isNew() || this.hideSave,
                handler: function(){
                    this.onSaveBtnClicked(true);
                },
                scope: this
            });
        }

        return this.saveAsBtn;
    },

    getToggleViewBtn : function()
    {
        if (!this.toggleViewBtn)
        {
            this.toggleViewBtn = Ext4.create('Ext.button.Button', {
                text:'View Data',
                scope: this,
                handler: function()
                {
                    if (this.viewPanel.isHidden())
                    {
                        this.getCenterPanel().getLayout().setActiveItem(0);
                        this.toggleViewBtn.setText('View Data');

                        this.getChartTypeBtn().show();
                        this.getLookAndFeelBtn().show();
                        this.getOptionsBtn().show();
                        this.getGroupingBtn().show();
                        this.getExportBtn().show();

                        if (this.customButtons)
                        {
                            for (var i = 0; i < this.customButtons.length; i++)
                                this.customButtons[i].show();
                        }

                        if (this.isDeveloper)
                            this.getDeveloperBtn().show();
                    }
                    else
                    {
                        this.getCenterPanel().getLayout().setActiveItem(1);
                        this.toggleViewBtn.setText('View Chart');

                        this.getChartTypeBtn().hide();
                        this.getLookAndFeelBtn().hide();
                        this.getOptionsBtn().hide();
                        this.getGroupingBtn().hide();
                        this.getExportBtn().hide();

                        if (this.customButtons)
                        {
                            for (var i = 0; i < this.customButtons.length; i++)
                                this.customButtons[i].hide();
                        }

                        if (this.isDeveloper)
                            this.getDeveloperBtn().hide();
                    }
                }
            });
        }

        return this.toggleViewBtn;
    },

    getEditBtn : function()
    {
        if (!this.editBtn)
        {
            this.editBtn = Ext4.create('Ext.button.Button', {
                xtype: 'button',
                text: 'Edit',
                scope: this,
                handler: function() {
                    window.location = this.editModeURL;
                }
            });
        }

        return this.editBtn;
    },

    initTbarItems: function()
    {
        var tbarItems = [
            this.getToggleViewBtn(),
            '', // horizontal spacer
            this.getExportBtn(),
            this.getHelpBtn(),
            '->'
        ];

        if (this.editMode)
        {
            if (LABKEY.vis.USE_NEW_CHART_WIZARD)
            {
                tbarItems.push(this.getChartTypeBtn());
                tbarItems.push(this.getLookAndFeelBtn());
                tbarItems.push(''); // horizontal spacer
            }
            else
            {
                tbarItems.push(this.getOptionsBtn());
                tbarItems.push(this.getGroupingBtn());
                tbarItems.push(this.getDeveloperBtn());
                tbarItems.push(''); // horizontal spacer
            }

            if (this.customButtons)
            {
                for (var i = 0; i < this.customButtons.length; i++)
                {
                    var btn = this.customButtons[i];
                    btn.scope = this;
                    tbarItems.push(btn);
                }
            }

            if (this.canEdit)
                tbarItems.push(this.getSaveBtn());
            tbarItems.push(this.getSaveAsBtn());
        }
        else if (this.allowEditMode && this.editModeURL != null)
        {
            // add an "edit" button if the user is allowed to toggle to edit mode for this report
            tbarItems.push(this.getEditBtn());
        }

        return tbarItems;
    },

    getXMeasureWindow : function()
    {
        if (!this.xMeasureWindow)
        {
            this.xOkBtn = Ext4.create("Ext.Button", {
                text: 'Ok',
                disabled: !LABKEY.vis.USE_NEW_CHART_WIZARD,
                handler: function ()
                {
                    this.xMeasureWindow.hide();
                    this.getXMeasurePanel().checkForChangesAndFireEvents();
                },
                scope: this
            });

            this.xCancelBtn = Ext4.create("Ext.Button", {
                text: 'Cancel',
                handler: function ()
                {
                    this.xMeasureWindow.close();
                },
                scope: this
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
                items: [this.getXMeasurePanel()],
                buttons: [this.xOkBtn, this.xCancelBtn],
                listeners: {
                    show: function ()
                    {
                        this.getEl().mask();
                        this.initialPanelValues = this.getXMeasurePanel().getPanelOptionValues();
                        this.initialPanelValues.measure = this.getXMeasureGrid().getSelectionModel().getLastSelected();

                        if (!LABKEY.vis.USE_NEW_CHART_WIZARD)
                            this.xOkBtn.setDisabled(!this.getXMeasureGrid().getSelectionModel().hasSelection());

                        if (!this.measures.x)
                        {
                            this.xCancelBtn.hide();
                            this.getXMeasurePanel().hideNonMeasureElements();
                        }
                        else
                        {
                            this.xCancelBtn.show();
                            this.getXMeasurePanel().showNonMeasureElements();
                            this.getXMeasurePanel().disableScaleAndRange();
                        }
                    },
                    hide: function ()
                    {
                        this.initialPanelValues = null;
                        this.getEl().unmask();
                    },
                    beforeclose: function ()
                    {
                        // on close, we don't apply changes and let the panel restore its state
                        if (this.initialPanelValues && this.initialPanelValues.measure)
                        {
                            this.getXMeasureGrid().getSelectionModel().select([this.initialPanelValues.measure], false, true);
                            this.getXMeasurePanel().restoreValues(this.initialPanelValues);
                        }
                    },
                    scope: this
                }
            });
        }

        return this.xMeasureWindow;
    },

    getXMeasurePanel : function()
    {
        if (!this.xMeasurePanel)
        {
            this.xMeasurePanel = Ext4.create('LABKEY.vis.GenericChartAxisPanel', {
                store: this.getXMeasureStore(),
                measureGrid: this.getXMeasureGrid(),
                queryName: this.queryName,
                listeners: {
                    chartDefinitionChanged: this.renderChart,
                    scope: this
                }
            });
        }

        return this.xMeasurePanel;
    },

    getXMeasureGrid : function()
    {
        if (!this.xMeasureGrid)
        {
            this.xMeasureGrid = Ext4.create('Ext.grid.Panel', {
                store: this.getXMeasureStore(),
                width: 360,
                height: 200,
                sortableColumns: false,
                enableColumnHide: false,
                columns: [
                    {header: 'Measure', dataIndex: 'label', flex: 1, renderer: function(value){return Ext4.util.Format.htmlEncode(value)}}
                ],
                listeners: {
                    render : function(grid)
                    {
                        if (grid.getStore().getCount() == 0)
                            grid.getEl().mask("Loading...");
                    },
                    viewready: function(grid)
                    {
                        if (this.measures.x)
                        {
                            var measure = grid.getStore().findRecord('name', this.measures.x.name, 0, false, true, true);
                            if (measure)
                            {
                                grid.getSelectionModel().select(measure, false, true);
                                this.getXMeasurePanel().measure = measure.data;
                                this.getXMeasurePanel().selectionChange(true);
                            }
                        }
                    },
                    select: function(selModel, record, index)
                    {
                        this.setXAxisMeasure(selModel.getSelection()[0], false);
                        this.xOkBtn.enable();
                    },
                    scope: this
                }
            });
        }

        return this.xMeasureGrid;
    },

    getXMeasureStore : function()
    {
        if (!this.xMeasureStore)
        {
            this.xMeasureStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
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
        }

        return this.xMeasureStore;
    },

    getYMeasureWindow : function()
    {
        if (!this.yMeasureWindow)
        {
            this.yOkBtn = Ext4.create("Ext.Button", {
                text: 'Ok',
                disabled: !LABKEY.vis.USE_NEW_CHART_WIZARD,
                handler: function ()
                {
                    this.yMeasureWindow.hide();
                    this.getYMeasurePanel().checkForChangesAndFireEvents();
                },
                scope: this
            });

            this.yCancelBtn = Ext4.create("Ext.Button", {
                text: 'Cancel',
                handler: function ()
                {
                    this.yMeasureWindow.close();
                },
                scope: this
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
                items: [this.getYMeasurePanel()],
                buttons: [this.yOkBtn, this.yCancelBtn],
                listeners: {
                    show: function ()
                    {
                        this.getEl().mask();
                        this.initialPanelValues = this.getYMeasurePanel().getPanelOptionValues();
                        this.initialPanelValues.measure = this.getYMeasureGrid().getSelectionModel().getLastSelected();

                        if (!LABKEY.vis.USE_NEW_CHART_WIZARD)
                            this.yOkBtn.setDisabled(!this.getYMeasureGrid().getSelectionModel().hasSelection());

                        if (!this.measures.y)
                        {
                            this.yCancelBtn.hide();
                            this.getYMeasurePanel().hideNonMeasureElements();
                        }
                        else
                        {
                            this.yCancelBtn.show();
                            this.getYMeasurePanel().showNonMeasureElements();
                            this.getYMeasurePanel().disableScaleAndRange();
                        }
                    },
                    hide: function ()
                    {
                        this.initialPanelValues = null;
                        this.getEl().unmask();
                    },
                    beforeclose: function ()
                    {
                        // on close, we don't apply changes and let the panel restore its state
                        if (this.initialPanelValues && this.initialPanelValues.measure)
                        {
                            this.getYMeasureGrid().getSelectionModel().select([this.initialPanelValues.measure], false, true);
                            this.getYMeasurePanel().restoreValues(this.initialPanelValues);
                        }
                    },
                    scope: this
                }
            });
        }

        return this.yMeasureWindow;
    },

    getYMeasurePanel : function()
    {
        if (!this.yMeasurePanel)
        {
            this.yMeasurePanel = Ext4.create('LABKEY.vis.GenericChartAxisPanel',{
                border: false,
                frame: false,
                store: this.getYMeasureStore(),
                queryName: this.queryName,
                measureGrid: this.getYMeasureGrid(),
                listeners: {
                    chartDefinitionChanged: this.renderChart,
                    scope: this
                }
            });
        }

        return this.yMeasurePanel;
    },

    getYMeasureGrid : function()
    {
        if (!this.yMeasureGrid)
        {
            this.yMeasureGrid = Ext4.create('Ext.grid.Panel', {
                store: this.getYMeasureStore(),
                width: 360,
                height: 200,
                sortableColumns: false,
                enableColumnHide: false,
                columns: [
                    {header: 'Measure', dataIndex: 'label', flex: 1, renderer: function(value){return Ext4.util.Format.htmlEncode(value)}}
                ],
                listeners: {
                    render : function(grid)
                    {
                        if (grid.getStore().getCount() == 0)
                            grid.getEl().mask("Loading...");
                    },
                    viewready: function(grid)
                    {
                        if (this.measures.y)
                        {
                            var measure = grid.getStore().findRecord('name', this.measures.y.name, 0, false, true, true);
                            if (measure)
                            {
                                grid.getSelectionModel().select(measure, false, true);
                                this.getYMeasurePanel().measure = measure.data;
                                this.getYMeasurePanel().selectionChange(true);
                            }
                        }
                    },
                    select: function(selModel, record, index)
                    {
                        this.setYAxisMeasure(selModel.getSelection()[0], false);
                        this.yOkBtn.enable();
                    },
                    scope: this
                }
            });
        }

        return this.yMeasureGrid;
    },

    getYMeasureStore : function()
    {
        if (!this.yMeasureStore)
        {
            this.yMeasureStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
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
        }

        return this.yMeasureStore;
    },

    getMainTitleWindow : function()
    {
        if (!this.mainTitleWindow)
        {
            this.mainTitleWindow = Ext4.create('Ext.window.Window', {
                title: 'Main Title',
                layout: 'fit',
                cls: 'data-window',
                resizable: false,
                width: 300,
                closeAction: 'hide',
                items: [this.getMainTitlePanel()],
                listeners: {
                    show: function ()
                    {
                        this.initialPanelValues = this.getMainTitlePanel().getPanelOptionValues();
                        this.getEl().mask();
                    },
                    hide: function ()
                    {
                        this.initialPanelValues = null;
                        this.getEl().unmask();
                    },
                    beforeclose: function ()
                    {
                        // on close, we don't apply changes and let the panel restore its state
                        if (this.initialPanelValues)
                            this.getMainTitlePanel().restoreValues(this.initialPanelValues);
                    },
                    scope: this
                }
            });
        }

        return this.mainTitleWindow;
    },

    getMainTitlePanel : function()
    {
        if (!this.mainTitlePanel)
        {
            this.mainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
                listeners: {
                    closeOptionsWindow : function(canceling){
                        if (canceling)
                            this.getMainTitleWindow().fireEvent('beforeclose');
                        this.getMainTitleWindow().hide();
                    },
                    chartDefinitionChanged: this.renderChart,
                    resetTitle: function() {
                        // need a reset title function.
                        this.mainTitlePanel.setMainTitle((this.queryLabel || this.queryName) + ' - ' + Ext4.util.Format.htmlEncode(this.measures.y.label))
                    },
                    scope: this
                }
            });
        }

        return this.mainTitlePanel;
    },

    getCenterPanel : function()
    {
        if (!this.centerPanel)
        {
            this.centerPanel = Ext4.create('Ext.panel.Panel', {
                border: false,
                layout: {
                    type: 'card',
                    deferredRender: true
                },
                activeItem: 0,
                items: [this.getViewPanel(), this.getDataPanel()],
                tbar: this.initTbarItems()
            });
        }

        return this.centerPanel;
    },

    isNew : function() {
        return !this.reportId;
    },

    getGroupingWindow: function()
    {
        if (!this.groupingWindow)
        {
            this.groupingWindow = Ext4.create('Ext.window.Window', {
                title: 'Grouping Options',
                hidden: true,
                border: 1,
                width: 420,
                cls: 'data-window',
                resizable: false,
                draggable: false,
                closable: true,
                closeAction: 'hide',
                expandOnShow: false,
                items: [this.getGroupingPanel()],
                listeners: {
                    scope: this,
                    show: function()
                    {
                        this.initialPanelValues = this.getGroupingPanel().getPanelOptionValues();
                        this.getEl().mask();
                    },
                    beforeclose: function()
                    {
                        if (this.initialPanelValues)
                            this.getGroupingPanel().restoreValues(this.initialPanelValues);
                        this.getEl().unmask();
                    }
                }
            });
        }

        return this.groupingWindow;
    },

    getGroupingPanel : function()
    {
        if (!this.groupingPanel)
        {
            this.groupingPanel = Ext4.create('LABKEY.vis.GenericChartGroupingPanel', {
                width: '100%',
                store: this.getGroupingMeasureStore(),
                listeners: {
                    chartDefinitionChanged: this.renderChart,
                    closeOptionsWindow: function(canceling)
                    {
                        if (canceling)
                            this.getGroupingWindow().fireEvent('beforeclose');
                        this.getGroupingWindow().hide();
                    },
                    scope: this
                }
            });
        }

        return this.groupingPanel;
    },

    getGroupingMeasureStore : function()
    {
        if (!this.groupingMeasureStore)
        {
            this.groupingMeasureStore = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json'
                    }
                },
                listeners: {
                    load: function (store)
                    {
                        if (!this.restrictColumnsEnabled)
                        {
                            store.filterBy(function (record, id)
                            {
                                var type = record.get('normalizedType');
                                return !record.get('hidden') && type !== 'float' && type !== 'int' && type !== 'double';
                            });
                        }
                        else
                        {
                            store.filterBy(function (record, id)
                            {
                                return record.get('dimension') && !record.get('hidden');
                            });
                        }

                        var firstVal = store.getAt(0);

                        if (firstVal)
                        {
                            this.getGroupingPanel().supressEvents = true;
                            if (this.getGroupingPanel().getColorMeasure().name == null)
                                this.getGroupingPanel().setColorMeasure(firstVal.data);
                            if (this.getGroupingPanel().getPointMeasure().name == null)
                                this.getGroupingPanel().setPointMeasure(firstVal.data);
                            this.getGroupingPanel().supressEvents = false;
                        }
                    },
                    scope: this
                }
            });
        }

        return this.groupingMeasureStore;
    },

    getChartTypeWindow : function()
    {
        if (!this.chartTypeWindow)
        {
            this.chartTypeWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                header: false,
                panelToMask: this,
                items: [this.getChartTypePanel()]
            });

            // propagate the show event to the panel so it can stash the initial values
            this.chartTypeWindow.on('show', function(window)
            {
                this.getChartTypePanel().fireEvent('show', this.getChartTypePanel());
            }, this);
        }

        return this.chartTypeWindow;
    },

    getChartTypePanel : function()
    {
        if (!this.chartTypePanel)
        {
            this.chartTypePanel = Ext4.create('LABKEY.vis.ChartTypePanel', {
                restrictColumnsEnabled: this.restrictColumnsEnabled,
                selectedType: this.getSelectedChartType(),
                selectedFields: this.measures,
                listeners: {
                    scope: this,
                    cancel: function(panel)
                    {
                        this.getChartTypeWindow().hide();
                    },
                    apply: function(panel, values)
                    {
                        // note: this event will only fire if a change was made in the Chart Type panel
                        this.setRenderType(values.type);
                        this.measures = values.fields;

                        // if we haven't set the initial chart layout options yet, get the default values now
                        if (Object.keys(this.options).length == 0)
                            this.options = this.getLookAndFeelPanel().getValues();

                        this.renderChart();
                        this.getChartTypeWindow().hide();
                    }
                }
            });
        }

        return this.chartTypePanel;
    },

    getLookAndFeelWindow : function()
    {
        if (!this.lookAndFeelWindow)
        {
            this.lookAndFeelWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                header: false,
                panelToMask: this,
                items: [this.getLookAndFeelPanel()]
            });

            // propagate the show event to the panel so it can stash the initial values
            this.lookAndFeelWindow.on('show', function(window)
            {
                this.getLookAndFeelPanel().fireEvent('show', this.getLookAndFeelPanel());
            }, this);
        }

        return this.lookAndFeelWindow;
    },

    getLookAndFeelPanel : function()
    {
        if (!this.lookAndFeelPanel)
        {
            this.lookAndFeelPanel = Ext4.create('LABKEY.vis.LookAndFeelPanel', {
                options: this.options,
                isDeveloper: this.isDeveloper,
                defaultPointClickFn: this.getDefaultPointClickFn(),
                pointClickFnHelp: this.getPointClickFnHelp(),
                listeners: {
                    scope: this,
                    cancel: function(panel)
                    {
                        this.getLookAndFeelWindow().hide();
                    },
                    apply: function(panel, values)
                    {
                        // note: this event will only fire if a change was made in the Chart Type panel
                        this.options = values;
                        this.renderChart();
                        this.getLookAndFeelWindow().hide();
                    }
                }
            });
        }

        return this.lookAndFeelPanel;
    },

    getOptionsWindow: function()
    {
        if (!this.optionsWindow)
        {
            this.optionsWindow = Ext4.create('Ext.window.Window', {
                title: 'Plot Options',
                hidden: true,
                border: 1,
                resizable: false,
                cls: 'data-window',
                closable: true,
                closeAction: 'hide',
                expandOnShow: false,
                items: [this.getOptionsPanel()],
                draggable: false,
                listeners: {
                    scope: this,
                    show: function ()
                    {
                        this.initialPanelValues = this.getOptionsPanel().getPanelOptionValues();
                        this.getEl().mask();
                    },
                    hide: function ()
                    {
                        this.initialPanelValues = null;
                        this.getEl().unmask();
                    },
                    beforeclose: function ()
                    {
                        // on close, we don't apply changes and let the panel restore its state
                        if (this.initialPanelValues)
                            this.getOptionsPanel().restoreValues(this.initialPanelValues);
                    }
                }
            });
        }

        return this.optionsWindow;
    },

    getOptionsPanel : function()
    {
        if (!this.optionsPanel)
        {
            this.optionsPanel = Ext4.create('LABKEY.vis.GenericChartOptionsPanel', {
                renderType: this.renderType,
                customRenderTypes: this.customRenderTypes, // TODO how to handle this for the new Look and Feel dialog?
                listeners: {
                    chartDefinitionChanged: function ()
                    {
                        this.setRenderType(this.optionsPanel.getRenderType());
                        this.renderChart();
                    },
                    'closeOptionsWindow': function (canceling)
                    {
                        if (canceling)
                            this.getOptionsWindow().fireEvent('beforeclose');
                        this.getOptionsWindow().hide();
                    },
                    scope: this
                }
            });
        }

        return this.optionsPanel;
    },

    setRenderType : function(newRenderType)
    {
        if (this.renderType != newRenderType)
        {
            this.renderType = newRenderType;

            if (!this.reportId)
                this.updateWebpartTitle(this.typeToLabel[this.renderType]);
        }
    },

    getDeveloperWindow: function()
    {
        if (!this.developerWindow)
        {
            this.developerWindow = Ext4.create('Ext.window.Window', {
                title: 'Developer Options',
                hidden: true,
                border: 1,
                width: 800,
                cls: 'data-window',
                resizable: false,
                draggable: false,
                closable: true,
                closeAction: 'hide',
                expandOnShow: false,
                items: [this.getDeveloperPanel()],
                listeners: {
                    scope: this,
                    show: function ()
                    {
                        this.initialPanelValues = this.getDeveloperPanel().getPanelOptionValues();
                        this.getEl().mask();
                    },
                    beforeclose: function ()
                    {
                        if (this.initialPanelValues)
                            this.getDeveloperPanel().restoreValues(this.initialPanelValues);
                        this.getEl().unmask();
                    }
                }
            });
        }

        return this.developerWindow;
    },

    getDeveloperPanel : function()
    {
        if (!this.developerPanel)
        {
            this.developerPanel = Ext4.create('LABKEY.vis.DeveloperOptionsPanel', {
                isDeveloper: this.isDeveloper || false,
                pointClickFn: null,
                defaultPointClickFn: this.getDefaultPointClickFn(), // TODO how to handle this for Look and Feel dialog?
                pointClickFnHelp: this.getPointClickFnHelp(), // TODO how to handle this for Look and Feel dialog?
                listeners: {
                    chartDefinitionChanged: this.renderChart,
                    'closeOptionsWindow': function(canceling){
                        if (canceling)
                            this.getDeveloperWindow().fireEvent('beforeclose');
                        this.getDeveloperWindow().hide();
                    },
                    scope: this
                }
            });
        }

        return this.developerPanel;
    },

    getExportScriptWindow : function()
    {
        if (!this.exportScriptWindow)
        {
            this.exportScriptWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                title: "Export Script",
                cls: 'data-window',
                width: 800,
                panelToMask: this,
                items: [{
                    xtype: 'panel',
                    items: [this.getExportScriptPanel()]
                }]
            });
        }

        return this.exportScriptWindow;
    },

    getExportScriptPanel : function()
    {
        if (!this.exportScriptPanel)
        {
            this.exportScriptPanel = Ext4.create('LABKEY.vis.GenericChartScriptPanel', {
                listeners: {
                    scope: this,
                    closeOptionsWindow: function(){
                        this.getExportScriptWindow().hide();
                    }
                }
            });
        }

        return this.exportScriptPanel;
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
            disableAnalytics      : true,
            removeableFilters     : userFilters,
            removeableSort        : userSort,
            showSurroundingBorder : false,
            showPagination        : false,
            maxRows               : this.dataLimit,
            allowHeaderLock       : false,
            buttonBar   : {
                includeStandardButton: false,
                items: [LABKEY.QueryWebPart.standardButtons.exportRows]
            }
        };

        if (this.dataRegionName) {
            wpConfig.dataRegionName = this.dataRegionName + '-chartdata';
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

        if (!serialize)
        {
            config.success = this.onSelectRowsSuccess;
            config.failure = function(response, opts){
                var error, errorDiv;

                this.getEl().unmask();

                if (response.exception)
                {
                    error = '<p>' + response.exception + '</p>';
                    if (response.exceptionClass == 'org.labkey.api.view.NotFoundException')
                        error = error + '<p>The source dataset, list, or query may have been deleted.</p>'
                }

                errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
                    html: '<h3 style="color:red;">An unexpected error occurred while retrieving data.</h2>' + error,
                    autoScroll: true
                });

                // Issue 18157
                this.getChartTypeBtn().disable();
                this.getLookAndFeelBtn().disable();
                this.getOptionsBtn().disable();
                this.getGroupingBtn().disable();
                this.getDeveloperBtn().disable();
                this.getToggleViewBtn().disable();
                this.getSaveBtn().disable();
                this.getSaveAsBtn().disable();

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

        if (!this.editMode || this.firstLoad)
        {
            // If we're not in edit mode or if this is the first load we need to only load the minimum amount of data.
            columns = [];
            var measures = this.getChartConfig().measures;

            if (measures.x)
            {
                columns.push(measures.x.name);
            }
            else if (this.autoColumnXName)
            {
                columns.push(this.autoColumnXName);
            }
            else
            {
                // Check if we have cohorts available.
                if (this.initialColumnList)
                {
                    for (var i = 0; i < this.initialColumnList.length; i++)
                    {
                        if (this.initialColumnList[i].indexOf('Cohort') > -1)
                            columns.push(this.initialColumnList[i]);
                    }
                }
            }

            if (measures.y)
                columns.push(measures.y.name);
            else if (this.autoColumnYName)
                columns.push(this.autoColumnYName);

            if (measures.color)
                columns.push(measures.color.name);

            if (measures.shape)
                columns.push(measures.shape.name);
        }
        else
        {
            // If we're in edit mode then we can load all of the columns.
            if (this.initialColumnList)
                columns = this.initialColumnList;
        }

        return columns;
    },

    getChartConfig : function() {
        var config = {};

        if (LABKEY.vis.USE_NEW_CHART_WIZARD)
        {
            config.measures = Ext4.apply({}, this.measures);
            config.scales = {};
            config.labels = {};

            if (this.options.general)
            {
                config.width = this.options.general.width;
                config.height = this.options.general.height;
                config.pointType = this.options.general.pointType;
                config.geomOptions = Ext4.apply({}, this.options.general);
                config.geomOptions.showOutliers = config.pointType ? config.pointType == 'outliers' : true;
                config.labels.main = this.options.general.label;
            }

            if (this.options.x)
            {
                config.labels.x = this.options.x.label;
                config.scales.x = {
                    trans: this.options.x.trans || this.options.x.scaleTrans
                };
            }

            if (this.options.y)
            {
                config.labels.y = this.options.y.label;
                config.scales.y = {
                    trans: this.options.y.trans || this.options.y.scaleTrans
                };
            }

            if (this.options.developer)
            {
                // TODO this.getDeveloperPanel().removeLeadingComments()
                config.measures.pointClickFn = this.options.developer.pointClickFn;
            }
        }
        else
        {
            var chartOptions = this.getOptionsPanel().getPanelOptionValues();
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
                position: chartOptions.position,
                showOutliers: chartOptions.pointType ? chartOptions.pointType == 'outliers' : true
            };

            var xAxisOptions = this.getXMeasurePanel().getPanelOptionValues();
            var yAxisOptions = this.getYMeasurePanel().getPanelOptionValues();
            config.scales = {
                x: {trans: xAxisOptions.scaleTrans},
                y: {trans: yAxisOptions.scaleTrans}
            };
            config.labels = {
                x: xAxisOptions.label,
                y: yAxisOptions.label
            };

            var mainTitle = this.getMainTitlePanel().getPanelOptionValues().title;
            if (!this.getMainTitlePanel().userEditedTitle || mainTitle == null || mainTitle == undefined)
            {
                config.labels.main = this.getDefaultTitle();
                this.getMainTitlePanel().setMainTitle(config.labels.main, true);
            }
            else
            {
                config.labels.main = mainTitle;
            }

            config.measures = {
                x: this.measures.x,
                y: this.measures.y
            };

            var groupingOptions = this.getGroupingPanel().getPanelOptionValues();
            if (groupingOptions.color)
                config.measures.color = groupingOptions.color;
            if (groupingOptions.shape)
                config.measures.shape = groupingOptions.shape;

            var developerOptions = this.getDeveloperPanel().getPanelOptionValues();
            if (developerOptions.pointClickFn) {
                config.measures.pointClickFn = this.getDeveloperPanel().removeLeadingComments(developerOptions.pointClickFn);
            }
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
            var queryId = Ext4.id();

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
                selectQuerySettingsBtn.disable();
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

        if (!Ext4.ModelManager.isRegistered('LABKEY.data.Schema')) {
            Ext4.define('LABKEY.data.Schema', {
                extend: 'Ext.data.Model',
                fields: [
                    {name: 'name'},
                    {name: 'description'}
                ]
            });
        }

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
        LABKEY.Utils.signalWebDriverTest("genericChartDirty", dirty);
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

        if (data.isSaveAs)
            reportConfig.reportId = null;

        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'saveGenericReport.api'),
            method  : 'POST',
            headers : {
                'Content-Type' : 'application/json'
            },
            jsonData: reportConfig,
            success : function(resp){
                // show success message and then fade the window out
                var msgbox = Ext4.create('Ext.window.Window', {
                    html     : '<span class="labkey-message">Report Saved successfully</span>',
                    cls      : 'data-window',
                    header   : false,
                    padding  : 20,
                    resizable: false,
                    draggable: false
                });

                msgbox.show();
                msgbox.getEl().fadeOut({
                    delay : 1000,
                    duration: 1000,
                    callback : function()
                    {
                        msgbox.hide();
                    }
                });

                this.updateWebpartTitle(reportConfig.name);

                var o = Ext4.decode(resp.responseText);
                this.reportId = o.reportId;
                this.loadReport(this.reportId);

                this.getSaveWindow().close();
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
        if (error) {
            Ext4.Msg.alert('Error', error);
        }
        else {
            Ext4.Msg.alert('Error', 'An unknown error has occurred, unable to save the chart.');
        }
    },

    loadReport : function(reportId) {
        this.reportLoaded = false;
        Ext4.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'getGenericReport.api'),
            method  : 'GET',
            params  : {reportId : reportId},
            success : function(response){
                this.getSaveAsBtn().setVisible(!this.isNew() && !this.hideSave);
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

        this.getSavePanel().setReportInfo({
            name: config.name,
            description: config.description,
            shared: config["public"],
            reportProps: config.reportProps,
            thumbnailURL: config.thumbnailURL
        });

        var json = Ext4.decode(config.jsonData);
        this.loadQueryInfoFromConfig(json.queryConfig);
        this.loadMeasuresFromConfig(json.chartConfig);
        this.loadOptionsFromConfig(json.chartConfig);

        if (!LABKEY.vis.USE_NEW_CHART_WIZARD && json.chartConfig)
        {
            this.getOptionsPanel().setPanelOptionValues(json.chartConfig);
            if (json.chartConfig.labels.main != null && json.chartConfig.labels.main != undefined)
            {
                this.getMainTitlePanel().setMainTitle(json.chartConfig.labels.main, true);
                if (this.getDefaultTitle() != this.getMainTitlePanel().getPanelOptionValues().title)
                    this.getMainTitlePanel().userEditedTitle = true;
            }
            else
            {
                this.getMainTitlePanel().setMainTitle(this.getDefaultTitle(), true);
            }

            if (json.chartConfig.scales.x || json.chartConfig.labels.x)
            {
                this.getXMeasurePanel().setPanelOptionValues({
                    label: json.chartConfig.labels.x,
                    scaleTrans: json.chartConfig.scales.x ? json.chartConfig.scales.x.trans : null
                });
                this.getXMeasurePanel().measure = this.measures.x;
                if (this.measures.x && this.getXMeasurePanel().getAxisLabel() != this.getDefaultXAxisLabel())
                    this.getXMeasurePanel().userEditedLabel = true;
            }

            if (json.chartConfig.labels.y || json.chartConfig.scales.y)
            {
                this.getYMeasurePanel().setPanelOptionValues({
                    label: json.chartConfig.labels.y,
                    scaleTrans: json.chartConfig.scales.y ? json.chartConfig.scales.y.trans : null
                });
                this.getYMeasurePanel().measure = this.measures.y;
                if (this.measures.y && this.getYMeasurePanel().getAxisLabel() != this.getDefaultYAxisLabel())
                    this.getYMeasurePanel().userEditedLabel = true;
            }

            if (json.chartConfig.measures.color || json.chartConfig.measures.shape)
                this.getGroupingPanel().setPanelOptionValues(json.chartConfig.measures);

            if (json.chartConfig.measures.pointClickFn)
                this.getDeveloperPanel().setPanelOptionValues(json.chartConfig.measures);
        }

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    loadQueryInfoFromConfig : function(queryConfig)
    {
        if (Ext4.isObject(queryConfig))
        {
            if (queryConfig.filterArray)
            {
                this.userFilters = [];
                for (var i=0; i < queryConfig.filterArray.length; i++)
                {
                    var f = queryConfig.filterArray[i];
                    this.userFilters.push(LABKEY.Filter.create(f.name,  f.value, LABKEY.Filter.getFilterTypeForURLSuffix(f.type)));
                }
            }

            if (queryConfig.columns)
                this.savedColumns = queryConfig.columns;

            if (queryConfig.queryLabel)
                this.queryLabel = queryConfig.queryLabel;

            if (queryConfig.parameters)
                this.parameters = queryConfig.parameters;
        }
    },

    loadMeasuresFromConfig : function(chartConfig)
    {
        this.measures = {};

        if (Ext4.isObject(chartConfig))
        {
            this.measures.x = chartConfig.measures.x;
            this.measures.y = chartConfig.measures.y;
            if (chartConfig.measures.color)
                this.measures.color = chartConfig.measures.color;
            if (chartConfig.measures.shape)
                this.measures.shape = chartConfig.measures.shape;
        }
    },

    loadOptionsFromConfig : function(chartConfig)
    {
        this.options = {};

        if (Ext4.isObject(chartConfig))
        {
            this.options.general = {};
            if (chartConfig.height)
                this.options.general.height = chartConfig.height;
            if (chartConfig.width)
                this.options.general.width = chartConfig.width;
            if (chartConfig.labels && chartConfig.labels.main)
                this.options.general.label = chartConfig.labels.main;
            if (chartConfig.pointType)
                this.options.general.pointType = chartConfig.pointType;
            if (chartConfig.geomOptions)
                Ext4.apply(this.options.general, chartConfig.geomOptions);

            this.options.x = {};
            if (chartConfig.labels && chartConfig.labels.x)
                this.options.x.label = chartConfig.labels.x;
            if (chartConfig.scales && chartConfig.scales.x)
                Ext4.apply(this.options.x, chartConfig.scales.x);

            this.options.y = {};
            if (chartConfig.labels && chartConfig.labels.y)
                this.options.y.label = chartConfig.labels.y;
            if (chartConfig.scales && chartConfig.scales.y)
                Ext4.apply(this.options.y, chartConfig.scales.y);

            this.options.developer = {};
            if (chartConfig.measures && chartConfig.measures.pointClickFn)
                this.options.developer.pointClickFn = chartConfig.measures.pointClickFn;

            if (chartConfig.curveFit)
                this.curveFit = chartConfig.curveFit;
        }
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

        this.getChartTypeBtn().disable();
        this.getLookAndFeelBtn().disable();
        this.getOptionsBtn().disable();
        this.getGroupingBtn().disable();
        this.getDeveloperBtn().disable();
        this.getSaveBtn().disable();
        this.getSaveAsBtn().disable();

        this.disableExport();

        // Keep the toggle button enabled so the user can remove filters
        this.getToggleViewBtn().enable();

        this.clearChartPanel();
        this.viewPanel.add(errorDiv);
    },

    renderPlot: function(forExport){
        if (this.viewPanel.isHidden()) {
            // Don't attempt to render if the view panel isn't visible.
            return;
        }

        if (!forExport) {
            this.getEl().mask('Rendering Chart...');
            this.clearChartPanel();
        }

        if (this.chartData.rows.length === 0) {
            this.getEl().unmask();
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

        var GCH = LABKEY.vis.GenericChartHelper,
            customRenderType, chartType, geom, aes,
            scales, labels, plotConfig, width, height;

        var chartConfig = this.getChartConfig();

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

        if (this.warningText !== null) {
            height = height - 15;
        }

        if (customRenderType && customRenderType.generatePlotConfig)
        {
            plotConfig = customRenderType.generatePlotConfig(this, chartConfig, newChartDiv.id, width, height, this.chartData.rows, aes, scales, labels);
        }
        else
        {
            var layers = [];

            if (chartConfig.pointType == 'all')
            {
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

        if (this.supportedBrowser && !this.useRaphael)
            plotConfig.rendererType = 'd3';

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();

        if (this.warningText !== null)
        {
            var warningDiv = document.createElement('div');
            warningDiv.setAttribute('class', 'labkey-error');
            warningDiv.setAttribute('style', 'text-align: center');
            warningDiv.innerHTML = this.warningText;
            newChartDiv.getEl().insertFirst(warningDiv);
        }

        if (!forExport)
        {
            this.getChartTypeBtn().enable();
            this.getLookAndFeelBtn().enable();
            this.getOptionsBtn().enable();
            this.getGroupingBtn().enable();
            this.getDeveloperBtn().enable();
            this.getSaveBtn().enable();
            this.getSaveAsBtn().enable();

            this.enableExport();

            this.getEl().unmask();
            if (this.editMode && this.supportedBrowser)
            {
                // Update thumbnail
                var thumbnail = this.renderPlot(true);
                if (thumbnail)
                {
                    this.chartSVG = LABKEY.vis.SVGConverter.svgToStr(Ext4.get(thumbnail).child('svg').dom);
                    this.getSavePanel().updateCurrentChartThumbnail(this.chartSVG, Ext4.get(thumbnail).getSize());
                    // destroy the temp chart element
                    Ext4.getCmp(thumbnail).destroy();
                }
            }
        }
        else
        {
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
                measure = this.getYMeasureStore().findRecord('name', fk.name, 0, false, true, true);
                if (measure)
                    this.setYAxisMeasure(measure, true);
            }

            if (!this.measures.y)
            {
                this.getEl().unmask();

                if (LABKEY.vis.USE_NEW_CHART_WIZARD)
                    this.showChartTypeWindow();
                else
                    this.showYMeasureWindow();

                return false;
            }
        }

        if (!this.measures.x && !forExport)
        {
            if (this.renderType !== "box_plot" && this.renderType !== "auto_plot")
            {
                if (this.autoColumnXName)
                {
                    fk = LABKEY.FieldKey.fromString(this.autoColumnXName);
                    measure = this.getXMeasureStore().findRecord('name', fk.name, 0, false, true, true);
                    if (measure)
                        this.setXAxisMeasure(measure, true);
                }

                if (!this.measures.x)
                {
                    this.getEl().unmask();

                    if (LABKEY.vis.USE_NEW_CHART_WIZARD)
                        this.showChartTypeWindow();
                    else
                        this.showXMeasureWindow();

                    return false;
                }
            }
            else if (!LABKEY.vis.USE_NEW_CHART_WIZARD || Ext4.isString(this.autoColumnYName))
            {
                if (LABKEY.vis.USE_NEW_CHART_WIZARD)
                    measure = this.getChartTypePanel().getStore().findRecord('label', 'Study: Cohort', 0, false, true, true);
                else
                    measure = this.getXMeasureStore().findRecord('label', 'Cohort', 0, false, true, true);

                if (measure)
                    this.setXAxisMeasure(measure, true);
            }
        }

        return true;
    },

    setYAxisMeasure : function(measure, suppressEvents)
    {
        if (measure)
        {
            this.measures.y = measure.data ? measure.data : measure;

            this.getYMeasurePanel().measure = this.measures.y;
            if (suppressEvents)
                this.getYMeasureGrid().getSelectionModel().select(measure, false, true);
            this.getYMeasurePanel().selectionChange(suppressEvents);
        }
    },

    setXAxisMeasure : function(measure, suppressEvents)
    {
        if (measure)
        {
            this.measures.x = measure.data ? measure.data : measure;

            this.getXMeasurePanel().measure = this.measures.x;
            if (suppressEvents)
                this.getXMeasureGrid().getSelectionModel().select(measure, false, true);
            this.getXMeasurePanel().selectionChange(suppressEvents);
        }
    },

    validateYAxis: function(chartType, chartConfig, aes, scales, data){
        var validation = LABKEY.vis.GenericChartHelper.validateYAxis(chartType, chartConfig, aes, scales, data);

        if (validation.success === true)
        {
            if (validation.message != null)
                this.addWarningText(validation.message);

            return true;
        }
        else
        {
            this.getEl().unmask();
            this.setRenderRequested(false);

            if (this.editMode)
            {
                Ext4.Msg.alert('Error', validation.message, LABKEY.vis.USE_NEW_CHART_WIZARD ? this.showChartTypeWindow : this.showYMeasureWindow, this);
            }
            else
            {
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

        if (validation.success === true)
        {
            if (validation.message != null)
                this.addWarningText(validation.message);

            return true;
        }
        else
        {
            this.getEl().unmask();
            this.setRenderRequested(false);

            if (this.editMode)
            {
                Ext4.Msg.alert('Error', validation.message, LABKEY.vis.USE_NEW_CHART_WIZARD ? this.showChartTypeWindow : this.showXMeasureWindow, this);
            }
            else
            {
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

        if (measures.color && this.getGroupingMeasureStore().find('name', measures.color.name, null, null, null, true) === -1)
        {
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point color, "' +
                            measures.color.label + '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.color = undefined;
        }

        if (measures.shape && this.getGroupingMeasureStore().find('name', measures.shape.name, null, null, null, true) === -1)
        {
            this.addWarningText(
                    '<p style="color: red; text-align: center;">The saved category for point shape, "' +
                            measures.shape.label + '", is not available. It may have been deleted or renamed. </p>'
            );

            measures.shape = undefined;
        }
    },

    configureLabels: function(forExport, chartConfig){
        var labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);

        if (!LABKEY.vis.USE_NEW_CHART_WIZARD && !forExport && this.editMode) {
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

            if (labels.main.value == null || Ext4.util.Format.trim(labels.main.value) == '')
                labels.main.value = "Edit Title";

            if (labels.y.value == null || Ext4.util.Format.trim(labels.y.value) == '')
                labels.y.value = "Edit Axis Label";

            if (labels.x.value == null || Ext4.util.Format.trim(labels.x.value) == '')
            {
                if (chartConfig.measures.x)
                    labels.x.value = "Edit Axis Label";
                else
                    labels.x.value = "Choose a column";
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
        if (renderType === 'scatter_plot')
            return true;

        return (renderType === 'auto_plot' && (xAxisType == 'int' || xAxisType == 'float' || xAxisType == 'date'));
    },

    isBoxPlot: function(renderType, xAxisType){
        if (renderType === 'box_plot')
            return true;

        return (renderType == 'auto_plot' && (xAxisType == 'string' || xAxisType == 'boolean'));
    },

    getSelectedChartType : function()
    {
        if (Ext4.isString(this.renderType) && this.renderType !== 'auto_plot')
            return this.renderType;
        else if (!this.measures.x || this.isBoxPlot(this.renderType, this.measures.x.normalizedType))
            return 'box_plot';
        else if (this.measures.x && this.isScatterPlot(this.renderType, this.measures.x.normalizedType))
            return 'scatter_plot';

        return null;
    },

    clearChartPanel: function(){
        this.clearWarningText();
        this.viewPanel.removeAll();
        this.disableExport();
    },

    clearWarningText: function(){
        this.warningText = null;
    },

    addWarningText: function(warning){
        if (!this.warningText)
            this.warningText = warning;
        else
            this.warningText = this.warningText + '<br />' + warning;
    },

    getExportItem: function(menuLabel) {
        var menuItems = this.getExportBtn().menu.items.items;
        var exportMenuItem;

        for (var i = 0; i < menuItems.length; i++)
        {
            if (menuItems[i].text == menuLabel)
            {
                exportMenuItem = menuItems[i];
                break;
            }
        }

        return exportMenuItem;
    },

    disableExport: function()
    {
        var exportPdfItem = this.getExportItem("Export as PDF");
        if (exportPdfItem)
        {
            exportPdfItem.disable();
            exportPdfItem.removeListener('click', this.exportChartToPdf);
        }

        var exportPngItem = this.getExportItem("Export as PNG");
        if (exportPngItem)
        {
            exportPngItem.disable();
            exportPngItem.removeListener('click', this.exportChartToPng);
        }

        var exportScriptItem = this.getExportItem("Export as Script");
        if (exportScriptItem)
        {
            exportScriptItem.disable();
            exportScriptItem.removeListener('click', this.exportChartToScript);
        }

    },

    enableExport: function()
    {
        var exportPdfItem = this.getExportItem("PDF");
        if (exportPdfItem)
        {
            exportPdfItem.setDisabled(!this.supportedBrowser);
            exportPdfItem.addListener('click', this.exportChartToPdf, this);
        }

        var exportPngItem = this.getExportItem("PNG");
        if (exportPngItem)
        {
            exportPngItem.setDisabled(!this.supportedBrowser);
            exportPngItem.addListener('click', this.exportChartToPng, this);
        }

        var exportScriptItem = this.getExportItem("Script");
        if (exportScriptItem)
        {
            exportScriptItem.enable();
            exportScriptItem.addListener('click', this.exportChartToScript, this);
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

    exportChartToPng: function() {
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            // export the temp chart as a png with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'png', this.getChartConfig().labels.main);
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

        this.getExportScriptPanel().setScriptValue(templateConfig);
        this.getExportScriptWindow().show();
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
        if (!this.getYMeasureWindow().isVisible() || !this.getXMeasureWindow().isVisible() || !this.getMainTitleWindow().isVisible())
            this.getEl().unmask();

        this.chartData = response;

        var queryFields = this.getQueryFields(this.chartData.metaData.fields);
        this.getChartTypePanel().loadQueryColumns(queryFields);

        var sortedFields = this.sortFields(this.chartData.metaData.fields);
        this.getYMeasureStore().loadRawData(sortedFields);
        this.getXMeasureStore().loadRawData(sortedFields);

        this.getGroupingMeasureStore().loadRawData(this.chartData.metaData.fields);
        this.getGroupingPanel().loadStore(this.getGroupingMeasureStore());

        if (this.getYMeasureGrid().getEl() && this.getYMeasureGrid().getEl().isMasked())
            this.getYMeasureGrid().getEl().unmask();

        if (this.getXMeasureGrid().getEl() && this.getXMeasureGrid().getEl().isMasked())
            this.getXMeasureGrid().getEl().unmask();

        this.setDataLoading(false);

        if (response.rows.length === 0)
        {
            this.handleNoData();
        }
        else
        {
            if (this.isRenderRequested())
            {
                // If it's already been requested then we just need to request it again, since
                // this time we have the data to render.
                this.requestRender(false);
            }

            if (this.firstLoad)
            {
                // Set first load to false after our first sucessful callback.
                this.firstLoad = false;
                this.fireEvent('dataRequested');
            }
        }
    },

    getQueryFields : function(fields)
    {
        var queryFields = [];

        Ext4.each(fields, function(field)
        {
            var f = Ext4.clone(field);
            f.isCohortColumn = false;
            f.isSubjectGroupColumn = false;

            // issue 23224: distinguish cohort and subject group fields in the list of query columns
            if (this.columnTypes.cohort && this.columnTypes.cohort.indexOf(f.name) > -1)
            {
                f.shortCaption = 'Study: ' + f.shortCaption;
                f.isCohortColumn = true;
            }
            else if (this.columnTypes.subjectGroup && this.columnTypes.subjectGroup.indexOf(f.name) > -1)
            {
                f.shortCaption = this.subject.nounSingular + ' Group: ' + f.shortCaption;
                f.isSubjectGroupColumn = true;
            }

            queryFields.push(f);
        }, this);

        // Sorts fields by their shortCaption, but put subject groups/categories/cohort at the end.
        queryFields.sort(function(a, b)
        {
            if (a.isSubjectGroupColumn != b.isSubjectGroupColumn)
                return a.isSubjectGroupColumn ? 1 : -1;
            else if (a.isCohortColumn != b.isCohortColumn)
                return a.isCohortColumn ? 1 : -1;
            else if (a.shortCaption != b.shortCaption)
                return a.shortCaption < b.shortCaption ? -1 : 1;

            return 0;
        });

        return queryFields;
    },

    sortFields: function(fields){
        // Sorts fields by their shortCaption, but puts
        // subject groups/categories/cohort at the beginning.
        var otherFields = [],
            subjectFields = [],
            sortFunction = function(a, b)
            {
                if (a.shortCaption < b.shortCaption)
                    return -1;
                else if (a.shortCaption > b.shortCaption)
                    return 1;

                return 0;
            };

        if (this.subject)
        {
            for (var i = 0; i < fields.length; i++)
            {
                if (fields[i].name.indexOf(this.subject.column) > -1)
                    subjectFields.push(fields[i]);
                else
                    otherFields.push(fields[i]);
            }

            subjectFields.sort(sortFunction);
            otherFields.sort(sortFunction);

            return subjectFields.concat(otherFields);
        }
        else
        {
            return fields.sort(sortFunction);
        }
    },

    showYMeasureWindow: function(){
        this.getYMeasureWindow().show();

        if (this.isDataLoading())
            this.getYMeasureGrid().getEl().mask("Loading Measures...");
    },

    showXMeasureWindow: function(){
        this.getXMeasureWindow().show();

        if (this.isDataLoading())
            this.getXMeasureGrid().getEl().mask("Loading Measures...");
    },

    showMainTitleWindow: function(){
        this.getMainTitleWindow().show();
    },

    getDefaultTitle: function(){
        if (this.defaultTitleFn)
            return this.defaultTitleFn(this.queryName, this.queryLabel, this.measures.y ? this.measures.y.label : null, this.measures.x ? this.measures.x.label : null);

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
        if (this.isDataLoading())
            this.setRenderRequested(true);
        else
            this.renderPlot(forExport);
    },

    renderChart : function()
    {
        this.getEl().mask('Rendering Chart...');
        this.chartDefinitionChanged.delay(500);
    },

    onSaveBtnClicked: function(isSaveAs){
        this.getSavePanel().setNoneThumbnail(this.getNoneThumbnailURL());
        this.getSavePanel().setSaveAs(isSaveAs);
        this.getSaveWindow().setTitle(isSaveAs ? "Save As" : "Save");
        this.getSaveWindow().show();
    },

    getSaveWindow: function()
    {
        if (!this.saveWindow)
        {
            this.saveWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                title: "Save",
                width: 500,
                autoHeight: true,
                cls: 'data-window',
                layout: 'fit',
                panelToMask: this,
                items: [this.getSavePanel()]
            });
        }

        return this.saveWindow;
    },

    getSavePanel : function()
    {
        if (!this.savePanel)
        {
            this.savePanel = Ext4.create('LABKEY.vis.SaveOptionsPanel', {
                canEdit: this.canEdit,
                canShare: this.allowShare,
                listeners: {
                    scope: this,
                    closeOptionsWindow: function()
                    {
                        this.getSaveWindow().close()
                    },
                    saveChart: this.saveReport
                }
            });
        }

        return this.savePanel;
    },

    getNoneThumbnailURL: function(){
        //if (!this.measures.x || this.isBoxPlot(this.renderType, this.measures.x.normalizedType))
        //    return LABKEY.contextPath + '/visualization/images/boxplot.png';

        if (this.measures.x && this.isScatterPlot(this.renderType, this.measures.x.normalizedType))
            return LABKEY.contextPath + '/visualization/images/scatterplot.png';

        return LABKEY.contextPath + '/visualization/images/boxplot.png';
    }
});

Ext4.define('LABKEY.vis.ChartWizardWindow', {
    extend: 'Ext.window.Window',
    cls: 'data-window chart-wizard-dialog',
    //header: false,
    resizable: false,
    closeAction: 'hide',
    panelToMask: null,
    listeners: {
        show: function()
        {
            if (this.panelToMask)
                this.panelToMask.getEl().mask();
        },
        hide: function()
        {
            if (this.panelToMask)
                this.panelToMask.getEl().unmask();
        }
    }
});
