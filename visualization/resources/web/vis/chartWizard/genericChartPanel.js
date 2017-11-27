/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.GenericChartPanel', {
    extend : 'Ext.panel.Panel',

    cls : 'generic-chart-panel',
    layout : 'fit',
    editable : false,
    minWidth : 900,

    initialSelection : null,
    savedReportInfo : null,
    hideViewData : false,
    reportLoaded : true,
    hideSave: false,
    dataPointLimit: 10000,

    constructor : function(config)
    {
        Ext4.QuickTips.init();
        this.callParent([config]);
    },

    queryGenericChartColumns : function()
    {
        LABKEY.vis.GenericChartHelper.getQueryColumns(this, function(columnMetadata)
        {
            this.getChartTypePanel().loadQueryColumns(columnMetadata);
            this.requestData();
        }, this);
    },

    initComponent : function()
    {
        this.measures = {};
        this.options = {};

        // boolean to check if we should allow things like export to PDF
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8);

        var params = LABKEY.ActionURL.getParameters();
        this.editMode = params.edit == "true" || !this.savedReportInfo;
        this.useRaphael = params.useRaphael != null ? params.useRaphael : false;
        this.parameters = LABKEY.Filter.getQueryParamsFromUrl(params['filterUrl'], this.dataRegionName);

        // Issue 19163
        Ext4.each(['autoColumnXName', 'autoColumnYName', 'autoColumnName'], function(autoColPropName)
        {
            if (this[autoColPropName])
                this[autoColPropName] = LABKEY.FieldKey.fromString(this[autoColPropName]);
        }, this);

        // for backwards compatibility, map auto_plot to box_plot
        if (this.renderType === 'auto_plot')
            this.setRenderType('box_plot');

        this.chartDefinitionChanged = new Ext4.util.DelayedTask(function(){
            this.markDirty(true);
            this.requestRender();
        }, this);

        // delayed task to redraw the chart
        this.updateChartTask = new Ext4.util.DelayedTask(function()
        {
            if (this.hasConfigurationChanged())
            {
                this.getEl().mask('Loading Data...');

                if (this.editMode && this.getChartTypePanel().getQueryColumnNames().length == 0)
                    this.queryGenericChartColumns();
                else
                    this.requestData();
            }

        }, this);

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

        this.items = [this.getCenterPanel()];

        this.callParent();

        if (this.savedReportInfo)
            this.loadSavedConfig();
        else
            this.loadInitialSelection();

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getViewPanel : function()
    {
        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                autoScroll  : true,
                ui          : 'custom',
                listeners   : {
                    scope: this,
                    activate: function()
                    {
                        this.updateChartTask.delay(500);
                    },
                    resize: function(p)
                    {
                        // only re-render after the initial chart rendering
                        if (this.hasChartData())
                            this.requestRender();
                    }
                }
            });
        }

        return this.viewPanel;
    },

    getDataPanel : function()
    {
        if (!this.dataPanel)
        {
            this.dataPanel = Ext4.create('Ext.panel.Panel', {
                flex        : 1,
                layout      : 'fit',
                border      : false,
                items       : [
                    Ext4.create('Ext.Component', {
                        autoScroll  : true,
                        listeners   : {
                            scope : this,
                            render : function(cmp){
                                this.renderDataGrid(cmp.getId());
                            }
                        }
                    })
                ]
            });
        }

        return this.dataPanel;
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
                dockedItems: [this.getTopButtonBar(), this.getMsgPanel()]
            });
        }

        return this.centerPanel;
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

    getChartLayoutBtn : function()
    {
        if (!this.chartLayoutBtn)
        {
            this.chartLayoutBtn = Ext4.create('Ext.button.Button', {
                text: 'Chart Layout',
                disabled: true,
                handler: this.showChartLayoutWindow,
                scope: this
            });
        }

        return this.chartLayoutBtn;
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
                        text: 'Bar Plots',
                        iconCls: 'fa fa-bar-chart',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=barchart'
                    },{
                        text: 'Box Plots',
                        iconCls: 'fa fa-sliders fa-rotate-90',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=boxplot'
                    },{
                        text: 'Pie Charts',
                        iconCls: 'fa fa-pie-chart',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=piechart'
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
                hidden: LABKEY.user.isGuest || this.hideSave,
                disabled: true,
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
                hidden  : this.isNew() || LABKEY.user.isGuest || this.hideSave,
                disabled: true,
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
                hidden: this.hideViewData,
                scope: this,
                handler: function()
                {
                    if (this.getViewPanel().isHidden())
                    {
                        this.getCenterPanel().getLayout().setActiveItem(0);
                        this.toggleViewBtn.setText('View Data');

                        this.getChartTypeBtn().show();
                        this.getChartLayoutBtn().show();

                        if (Ext4.isArray(this.customButtons))
                        {
                            for (var i = 0; i < this.customButtons.length; i++)
                                this.customButtons[i].show();
                        }
                    }
                    else
                    {
                        this.getCenterPanel().getLayout().setActiveItem(1);
                        this.toggleViewBtn.setText('View Chart');

                        this.getMsgPanel().removeAll();
                        this.getChartTypeBtn().hide();
                        this.getChartLayoutBtn().hide();

                        if (Ext4.isArray(this.customButtons))
                        {
                            for (var i = 0; i < this.customButtons.length; i++)
                                this.customButtons[i].hide();
                        }
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

    getTopButtonBar : function()
    {
        if (!this.topButtonBar)
        {
            this.topButtonBar = Ext4.create('Ext.toolbar.Toolbar', {
                dock: 'top',
                items: this.initTbarItems()
            });
        }

        return this.topButtonBar;
    },

    initTbarItems : function()
    {
        var tbarItems = [];
        tbarItems.push(this.getToggleViewBtn());
        tbarItems.push(this.getHelpBtn());
        tbarItems.push('->'); // rest of buttons will be right aligned

        if (this.editMode)
        {
            tbarItems.push(this.getChartTypeBtn());
            tbarItems.push(this.getChartLayoutBtn());

            if (Ext4.isArray(this.customButtons))
            {
                tbarItems.push(''); // horizontal spacer
                for (var i = 0; i < this.customButtons.length; i++)
                {
                    var btn = this.customButtons[i];
                    btn.scope = this;
                    tbarItems.push(btn);
                }
            }

            if (!LABKEY.user.isGuest && !this.hideSave)
                tbarItems.push(''); // horizontal spacer
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

    getMsgPanel : function() {
        if (!this.msgPanel) {
            this.msgPanel = Ext4.create('Ext.panel.Panel', {
                hidden: true,
                bodyStyle: 'border-width: 1px 0 0 0',
                listeners: {
                    add: function(panel) {
                        panel.show();
                    },
                    remove: function(panel) {
                        if (panel.items.items.length == 0) {
                            panel.hide();
                        }
                    }
                }
            });
        }

        return this.msgPanel;
    },

    showChartTypeWindow : function()
    {
        // make sure the chartTypePanel is shown in the window
        if (this.getChartTypeWindow().items.items.length == 0)
            this.getChartTypeWindow().add(this.getChartTypePanel());

        this.getChartTypeWindow().show();
    },

    showChartLayoutWindow : function()
    {
        this.getChartLayoutWindow().show();
    },

    isNew : function()
    {
        return !this.savedReportInfo;
    },

    getChartTypeWindow : function()
    {
        if (!this.chartTypeWindow)
        {
            var panel = this.getChartTypePanel();

            this.chartTypeWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                panelToMask: this,
                onEsc: function() {
                    panel.cancelHandler.call(panel);
                },
                items: [panel]
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
                chartTypesToHide: ['time_chart'],
                selectedType: this.getSelectedChartType(),
                selectedFields: this.measures,
                restrictColumnsEnabled: this.restrictColumnsEnabled,
                customRenderTypes: this.customRenderTypes,
                baseQueryKey: this.schemaName + '.' + this.queryName,
                studyQueryName: this.schemaName == 'study' ? this.queryName : null
            });
        }

        if (!this.hasAttachedChartTypeListeners)
        {
            this.chartTypePanel.on('cancel', this.closeChartTypeWindow, this);
            this.chartTypePanel.on('apply', this.applyChartTypeSelection, this);
            this.hasAttachedChartTypeListeners = true;
        }

        return this.chartTypePanel;
    },

    closeChartTypeWindow : function(panel)
    {
        if (this.getChartTypeWindow().isVisible())
            this.getChartTypeWindow().hide();
    },

    applyChartTypeSelection : function(panel, values, skipRender)
    {
        // close the window and clear any previous charts
        this.closeChartTypeWindow();
        this.clearChartPanel(true);

        // only apply the values for the applicable chart type
        if (Ext4.isObject(values) && values.type == 'time_chart')
            return;

        this.setRenderType(values.type);
        this.measures = values.fields;
        if (values.fields.xSub) {
            this.measures.color = this.measures.xSub;
        }

        this.getChartLayoutPanel().onMeasuresChange(this.measures, this.renderType);
        this.getChartLayoutPanel().updateVisibleLayoutOptions(this.getSelectedChartTypeData(), this.measures);
        this.ensureChartLayoutOptions();

        if (!skipRender)
            this.renderChart();
    },

    getSelectedChartTypeData : function()
    {
        var selectedChartType = this.getChartTypePanel().getSelectedType();
        return selectedChartType ? selectedChartType.data : null;
    },

    getChartLayoutWindow : function()
    {
        if (!this.chartLayoutWindow)
        {
            var panel = this.getChartLayoutPanel();

            this.chartLayoutWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                panelToMask: this,
                onEsc: function() {
                    panel.cancelHandler.call(panel);
                },
                items: [panel]
            });

            // propagate the show event to the panel so it can stash the initial values
            this.chartLayoutWindow.on('show', function(window)
            {
                this.getChartLayoutPanel().fireEvent('show', this.getChartLayoutPanel(), this.getSelectedChartTypeData(), this.measures);
            }, this);
        }

        return this.chartLayoutWindow;
    },

    getChartLayoutPanel : function()
    {
        if (!this.chartLayoutPanel)
        {
            this.chartLayoutPanel = Ext4.create('LABKEY.vis.ChartLayoutPanel', {
                options: this.options,
                isDeveloper: this.isDeveloper,
                renderType: this.renderType,
                initMeasures: this.measures,
                defaultChartLabel: this.getDefaultTitle(),
                defaultOpacity: this.renderType == 'bar_chart' || this.renderType == 'line_plot' ? 100 : undefined,
                defaultLineWidth: this.renderType == 'line_plot' ? 3 : undefined,
                isSavedReport: !this.isNew(),
                listeners: {
                    scope: this,
                    cancel: function(panel)
                    {
                        this.getChartLayoutWindow().hide();
                    },
                    apply: function(panel, values)
                    {
                        // note: this event will only fire if a change was made in the Chart Layout panel
                        this.ensureChartLayoutOptions();
                        this.clearChartPanel(true);
                        this.renderChart();
                        this.getChartLayoutWindow().hide();
                    }
                }
            });
        }

        return this.chartLayoutPanel;
    },

    getExportScriptWindow : function()
    {
        if (!this.exportScriptWindow)
        {
            this.exportScriptWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                panelToMask: this,
                items: [this.getExportScriptPanel()]
            });
        }

        return this.exportScriptWindow;
    },

    getExportScriptPanel : function()
    {
        if (!this.exportScriptPanel)
        {
            this.exportScriptPanel = Ext4.create('LABKEY.vis.GenericChartScriptPanel', {
                width: Math.max(this.getViewPanel().getWidth() - 100, 800),
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

    getSaveWindow : function()
    {
        if (!this.saveWindow)
        {
            this.saveWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
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
                canShare: this.canShare,
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

    ensureChartLayoutOptions : function()
    {
        // Make sure that we have the latest chart layout panel values.
        // This will get the initial default values if the user has not yet opened the chart layout dialog.
        // This will also preserve the developer pointClickFn if the user is not a developer.
        Ext4.apply(this.options, this.getChartLayoutPanel().getValues());
    },

    setRenderType : function(newRenderType)
    {
        if (this.renderType != newRenderType)
            this.renderType = newRenderType;
    },

    renderDataGrid : function(renderTo)
    {
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
            columns     : this.savedColumns,
            parameters  : this.parameters,
            frame       : 'none',
            disableAnalytics      : true,
            removeableFilters     : userFilters,
            removeableSort        : userSort,
            showSurroundingBorder : false,
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
                Ext4.apply(this.parameters, wp.parameters);
        }, this);

        wp.render(renderTo);
    },

    // Returns a configuration based on the baseUrl plus any filters applied on the dataregion panel
    // the configuration can be used to make a selectRows request
    getQueryConfig : function(serialize)
    {
        var dataRegion = LABKEY.DataRegions[this.panelDataRegionName];

        var config = {
            schemaName  : this.schemaName,
            queryName   : this.queryName,
            viewName    : this.viewName,
            dataRegionName: this.dataRegionName,
            queryLabel  : this.queryLabel,
            parameters  : this.parameters,
            requiredVersion : 13.2,
            maxRows: -1,
            sort: 'lsid', // needed to keep expected ordering for legend data
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
                    padding: 10,
                    html: '<h3 style="color:red;">An unexpected error occurred while retrieving data.</h2>' + error,
                    autoScroll: true
                });

                // Issue 18157
                this.getChartTypeBtn().disable();
                this.getChartLayoutBtn().disable();
                this.getToggleViewBtn().disable();
                this.getSaveBtn().disable();
                this.getSaveAsBtn().disable();

                this.getViewPanel().add(errorDiv);
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

            for (var i=0; i < filters.length; i++)
            {
                var f = filters[i];
                newFilters.push({name : f.getColumnName(), value : f.getValue(), type : f.getFilterType().getURLSuffix()});
            }

            filters = newFilters;
        }
        config['filterArray'] = filters;

        return config;
    },

    getQueryConfigColumns : function()
    {
        var columns = null;

        if (!this.editMode)
        {
            // If we're not in edit mode or if this is the first load we need to only load the minimum amount of data.
            columns = [];
            var measures = this.getChartConfig().measures;

            if (measures.x)
            {
                this.addMeasureForColumnQuery(columns, measures.x);
            }
            else if (this.autoColumnXName)
            {
                columns.push(this.autoColumnXName.toString());
            }
            else
            {
                // Check if we have cohorts available
                var queryColumnNames = this.getChartTypePanel().getQueryColumnNames();
                for (var i = 0; i < queryColumnNames.length; i++)
                {
                    if (queryColumnNames[i].indexOf('Cohort') > -1)
                        columns.push(queryColumnNames[i]);
                }
            }

            if (measures.y)
                this.addMeasureForColumnQuery(columns, measures.y);
            else if (this.autoColumnYName)
                columns.push(this.autoColumnYName.toString());

            if (this.autoColumnName)
                columns.push(this.autoColumnName.toString());

            Ext4.each(['color', 'shape', 'series'], function(name) {
                if (measures[name]) {
                    this.addMeasureForColumnQuery(columns, measures[name]);
                }
            }, this);
        }
        else
        {
            // If we're in edit mode then we can load all of the columns.
            columns = this.getChartTypePanel().getQueryColumnFieldKeys();
        }

        return columns;
    },

    addMeasureForColumnQuery : function(columns, measure)
    {
        if (Ext4.isObject(measure))
        {
            columns.push(measure.name);

            // Issue 27814: names with slashes need to be queried by encoded name
            var encodedName = LABKEY.QueryKey.encodePart(measure.name);
            if (measure.name != encodedName)
                columns.push(encodedName);
        }
    },

    getChartConfig : function()
    {
        var config = {};

        config.renderType = this.renderType;
        config.measures = Ext4.apply({}, this.measures);
        config.scales = {};
        config.labels = {};
        
        this.ensureChartLayoutOptions();
        if (this.options.general)
        {
            config.width = this.options.general.width;
            config.height = this.options.general.height;
            config.pointType = this.options.general.pointType;
            config.labels.main = this.options.general.label;
            config.labels.subtitle = this.options.general.subtitle;
            config.labels.footer = this.options.general.footer;

            config.geomOptions = Ext4.apply({}, this.options.general);
            config.geomOptions.showOutliers = config.pointType ? config.pointType == 'outliers' : true;
            config.geomOptions.pieInnerRadius = this.options.general.pieInnerRadius;
            config.geomOptions.pieOuterRadius = this.options.general.pieOuterRadius;
            config.geomOptions.showPiePercentages = this.options.general.showPiePercentages;
            config.geomOptions.piePercentagesColor = this.options.general.piePercentagesColor;
            config.geomOptions.pieHideWhenLessThanPercentage = this.options.general.pieHideWhenLessThanPercentage;
            config.geomOptions.gradientPercentage = this.options.general.gradientPercentage;
            config.geomOptions.gradientColor = this.options.general.gradientColor;
            config.geomOptions.colorPaletteScale = this.options.general.colorPaletteScale;
            config.geomOptions.binShape = this.options.general.binShapeGroup;
            config.geomOptions.binThreshold = this.options.general.binThreshold;
            config.geomOptions.colorRange = this.options.general.binColorGroup;
            config.geomOptions.binSingleColor = this.options.general.binSingleColor;
        }

        if (this.options.x)
        {
            if (!config.labels.x) {
                config.labels.x = this.options.x.label;
                config.scales.x = {
                    trans: this.options.x.trans || this.options.x.scaleTrans
                };
            }
            if (this.options.x.scaleRangeType == "manual" && this.options.x.scaleRange) {
                config.scales.x.min = this.options.x.scaleRange.min;
                config.scales.x.max = this.options.x.scaleRange.max;
            }

            if (this.measures.xSub) {
                config.labels.xSub = this.measures.xSub.label;
            }
        }

        if (this.options.y)
        {
            if (!config.labels.y) {
                config.labels.y = this.options.y.label;
                config.scales.y = {
                    trans: this.options.y.trans || this.options.y.scaleTrans
                };
            }
            if (this.options.y.scaleRangeType == "manual" && this.options.y.scaleRange) {
                config.scales.y.min = this.options.y.scaleRange.min;
                config.scales.y.max = this.options.y.scaleRange.max;
            }
        }

        if (this.options.developer)
            config.measures.pointClickFn = this.options.developer.pointClickFn;

        if (this.curveFit)
            config.curveFit = this.curveFit;

        if (this.getCustomChartOptions)
            config.customOptions = this.getCustomChartOptions();

        return config;
    },

    markDirty : function(dirty)
    {
        this.dirty = dirty;
        LABKEY.Utils.signalWebDriverTest("genericChartDirty", dirty);
    },

    isDirty : function()
    {
        return !LABKEY.user.isGuest && !this.hideSave && this.canEdit && this.dirty;
    },

    beforeUnload : function()
    {
        if (this.isDirty()) {
            return 'please save your changes';
        }
    },

    getCurrentReportConfig : function()
    {
        var reportConfig = {
            reportId    : this.savedReportInfo ? this.savedReportInfo.reportId : undefined,
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

        var chartConfig = reportConfig.jsonData.chartConfig;
        LABKEY.vis.GenericChartHelper.removeNumericConversionConfig(chartConfig);

        return reportConfig;
    },

    saveReport : function(data)
    {
        var reportConfig = this.getCurrentReportConfig();
        reportConfig.name = data.reportName;
        reportConfig.description = data.reportDescription;

        reportConfig["public"] = data.shared;
        reportConfig.thumbnailType =  data.thumbnailType;
        reportConfig.svg = this.chartSVG;

        if (data.isSaveAs)
            reportConfig.reportId = null;

        LABKEY.Ajax.request({
            url     : LABKEY.ActionURL.buildURL('visualization', 'saveGenericReport.api'),
            method  : 'POST',
            headers : {
                'Content-Type' : 'application/json'
            },
            jsonData: reportConfig,
            success : function(resp)
            {
                this.getSaveWindow().close();
                this.markDirty(false);

                // show success message and then fade the window out
                var msgbox = Ext4.create('Ext.window.Window', {
                    html     : '<span class="labkey-message">Report saved successfully.</span>',
                    cls      : 'chart-wizard-dialog',
                    bodyStyle : 'background: transparent;',
                    header   : false,
                    border   : false,
                    padding  : 20,
                    resizable: false,
                    draggable: false
                });

                msgbox.show();
                msgbox.getEl().fadeOut({
                    delay : 1500,
                    duration: 1000,
                    callback : function()
                    {
                        msgbox.hide();
                    }
                });

                // if a new report was created, we need to refresh the page with the correct report id on the URL
                if (this.isNew() || data.isSaveAs)
                {
                    var o = Ext4.decode(resp.responseText);
                    window.location = LABKEY.ActionURL.buildURL('reports', 'runReport', null, {reportId: o.reportId});
                }
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    onFailure : function(resp)
    {
        var error = Ext4.isString(resp.responseText) ? Ext4.decode(resp.responseText).exception : resp.exception;
        Ext4.Msg.show({
            title: 'Error',
            msg: error || 'An unknown error has occurred.',
            buttons: Ext4.MessageBox.OK,
            icon: Ext4.MessageBox.ERROR,
            scope: this
        });
    },

    loadReportFromId : function(reportId)
    {
        this.reportLoaded = false;

        LABKEY.Query.Visualization.get({
            reportId: reportId,
            scope: this,
            success: function(result)
            {
                this.savedReportInfo = result;
                this.loadSavedConfig();
            }
        });
    },

    loadSavedConfig : function()
    {
        var config = this.savedReportInfo,
            queryConfig = {},
            chartConfig = {};

        if (config.type == LABKEY.Query.Visualization.Type.GenericChart)
        {
            queryConfig = config.visualizationConfig.queryConfig;
            chartConfig = config.visualizationConfig.chartConfig;
        }

        this.schemaName = queryConfig.schemaName;
        this.queryName = queryConfig.queryName;
        this.viewName = queryConfig.viewName;
        this.dataRegionName = queryConfig.dataRegionName;

        if (this.reportName)
            this.reportName.setValue(config.name);

        if (this.reportDescription && config.description != null)
            this.reportDescription.setValue(config.description);

        // TODO is this needed/used anymore?
        if (this.reportPermission)
            this.reportPermission.setValue({"public" : config.shared});

        this.getSavePanel().setReportInfo({
            name: config.name,
            description: config.description,
            shared: config.shared,
            reportProps: config.reportProps,
            thumbnailURL: config.thumbnailURL
        });

        this.loadQueryInfoFromConfig(queryConfig);
        this.loadMeasuresFromConfig(chartConfig);
        this.loadOptionsFromConfig(chartConfig);

        // if the renderType was not saved with the report info, get it based off of the x-axis measure type
        this.renderType = chartConfig.renderType || this.getRenderType();

        this.markDirty(false);
        this.reportLoaded = true;
        this.updateChartTask.delay(500);
    },

    loadQueryInfoFromConfig : function(queryConfig)
    {
        if (Ext4.isObject(queryConfig))
        {
            if (Ext4.isArray(queryConfig.filterArray))
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
            if (Ext4.isObject(chartConfig.measures))
            {
                Ext4.each(['x', 'y', 'xSub', 'color', 'shape', 'series'], function(name) {
                    if (chartConfig.measures[name]) {
                        this.measures[name] = chartConfig.measures[name];
                    }
                }, this);
            }
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
            if (chartConfig.pointType)
                this.options.general.pointType = chartConfig.pointType;
            if (chartConfig.geomOptions)
                Ext4.apply(this.options.general, chartConfig.geomOptions);

            if (chartConfig.labels && chartConfig.labels.main)
                this.options.general.label = chartConfig.labels.main;
            else
                this.options.general.label = this.getDefaultTitle();

            if (chartConfig.labels && chartConfig.labels.subtitle)
                this.options.general.subtitle = chartConfig.labels.subtitle;
            if (chartConfig.labels && chartConfig.labels.footer)
                this.options.general.footer = chartConfig.labels.footer;

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

    loadInitialSelection : function()
    {
        if (Ext4.isObject(this.initialSelection))
        {
            this.applyChartTypeSelection(this.getChartTypePanel(), this.initialSelection, true);
            // clear the initial selection object so it isn't loaded again
            this.initialSelection = undefined;

            this.markDirty(false);
            this.reportLoaded = true;
            this.updateChartTask.delay(500);
        }
    },

    handleNoData : function(errorMsg)
    {
        // Issue 18339
        this.setRenderRequested(false);
        var errorDiv = Ext4.create('Ext.container.Container', {
            border: 1,
            autoEl: {tag: 'div'},
            html: '<h3 style="color:red;">An unexpected error occurred while retrieving data.</h2>' + errorMsg,
            autoScroll: true
        });

        this.getChartTypeBtn().disable();
        this.getChartLayoutBtn().disable();
        this.getSaveBtn().disable();
        this.getSaveAsBtn().disable();

        // Keep the toggle button enabled so the user can remove filters
        this.getToggleViewBtn().enable();

        this.clearChartPanel(true);
        this.getViewPanel().add(errorDiv);
        this.getEl().unmask();
    },

    renderPlot : function()
    {
        // Don't attempt to render if the view panel isn't visible or the chart type window is visible.
        if (!this.isVisible() || this.getViewPanel().isHidden() || this.getChartTypeWindow().isVisible())
            return;

        // initMeasures returns false and opens the Chart Type panel if a required measure is not chosen by the user.
        if (!this.initMeasures())
            return;

        this.clearChartPanel(false);

        var chartConfig = this.getChartConfig(),
            renderType = this.getRenderType();

        this.renderGenericChart(renderType, chartConfig);

        // We just rendered the plot, we don't need to request another render.
        this.setRenderRequested(false);
    },

    getRenderType : function()
    {
        var xAxisType = this.getXAxisType(this.measures.x);
        return LABKEY.vis.GenericChartHelper.getChartType(this.renderType, xAxisType);
    },

    renderGenericChart : function(chartType, chartConfig)
    {
        var aes, scales, plot, plotConfig, customRenderType, hasNoDataMsg, newChartDiv, valueConversionResponse;

        hasNoDataMsg = LABKEY.vis.GenericChartHelper.validateResponseHasData(this.getMeasureStore(), true);
        if (hasNoDataMsg != null)
            this.addWarningText(hasNoDataMsg);

        this.getEl().mask('Rendering Chart...');

        aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, this.getSchemaName(), this.getQueryName());

        valueConversionResponse = LABKEY.vis.GenericChartHelper.doValueConversion(chartConfig, aes, this.renderType, this.getMeasureStoreRecords());
        if (!Ext4.Object.isEmpty(valueConversionResponse.processed))
        {
            Ext4.Object.merge(chartConfig.measures, valueConversionResponse.processed);
            //re-generate aes based on new converted values
            aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, this.getSchemaName(), this.getQueryName());
            if (valueConversionResponse.warningMessage) {
                this.addWarningText(valueConversionResponse.warningMessage);
            }
        }

        customRenderType = this.customRenderTypes ? this.customRenderTypes[this.renderType] : undefined;
        if (customRenderType && customRenderType.generateAes)
            aes = customRenderType.generateAes(this, chartConfig, aes);

        scales = LABKEY.vis.GenericChartHelper.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, this.getMeasureStore(), this.defaultNumberFormat);
        if (customRenderType && customRenderType.generateScales)
            scales = customRenderType.generateScales(this, chartConfig, scales);

        if (!this.isChartConfigValid(chartType, chartConfig, aes, scales))
            return;

        if (chartType == 'scatter_plot' && this.getMeasureStoreRecords().length > chartConfig.geomOptions.binThreshold) {
            chartConfig.geomOptions.binned = true;
            this.addWarningText("The number of individual points exceeds "
                + Ext4.util.Format.number(chartConfig.geomOptions.binThreshold, '0,000')
                + ". The data is now grouped by density, which overrides some layout options.");
        }
        else if (chartType == 'line_plot' && this.getMeasureStoreRecords().length > this.dataPointLimit) {
            this.addWarningText("The number of individual points exceeds "
                    + Ext4.util.Format.number(this.dataPointLimit, '0,000')
                + ". Data points will not be shown on this line plot.");
        }

        this.beforeRenderPlotComplete();

        if (!Ext4.isDefined(chartConfig.width) || chartConfig.width == null)
            chartConfig.width = LABKEY.vis.GenericChartHelper.getChartTypeBasedWidth(chartType, chartConfig.measures, this.getMeasureStore(), this.getViewPanel().getWidth());
        if (!Ext4.isDefined(chartConfig.height) || chartConfig.height == null)
            chartConfig.height = this.getViewPanel().getHeight() - 25;

        newChartDiv = this.getNewChartDisplayDiv();
        this.getViewPanel().add(newChartDiv);

        plotConfig = this.getPlotConfig(newChartDiv, chartType, chartConfig, aes, scales, customRenderType);

        if (this.renderType == 'pie_chart')
        {
            new LABKEY.vis.PieChart(plotConfig);
        }
        else
        {
            plot = new LABKEY.vis.Plot(plotConfig);
            plot.render();
        }

        this.afterRenderPlotComplete(newChartDiv, plot);
    },

    getNewChartDisplayDiv : function()
    {
        return Ext4.create('Ext.container.Container', {
            cls: 'chart-render-div',
            autoEl: {tag: 'div'}
        });
    },

    beforeRenderPlotComplete : function()
    {
        // add the warning msg before the plot so the plot has the proper height
        if (this.warningText !== null)
            this.addWarningMsg(this.warningText, true);
    },

    afterRenderPlotComplete : function(chartDiv, plot)
    {
        this.getTopButtonBar().enable();
        this.getChartTypeBtn().enable();
        this.getChartLayoutBtn().enable();
        this.getSaveBtn().enable();
        this.getSaveAsBtn().enable();
        this.attachExportIcons(chartDiv);
        this.getEl().unmask();

        if (this.editMode && this.supportedBrowser)
            this.updateSaveChartThumbnail(chartDiv);
    },

    addWarningMsg : function(warningText, allowDismiss)
    {
        var warningDivId = Ext4.id();
        var dismissLink = allowDismiss ? '<a id="dismiss-link-' + warningDivId + '" class="labkey-text-link">dismiss</a>' : '';

        var warningCmp = Ext4.create('Ext.container.Container', {
            padding: 10,
            cls: 'chart-warning',
            html: warningText + ' ' + dismissLink,
            listeners: {
                scope: this,
                render: function(cmp) {
                    Ext4.get('dismiss-link-' + warningDivId).on('click', function() {
                        // removing the warning message which will adjust the view panel height, so suspend events temporarily
                        this.getViewPanel().suspendEvents();
                        this.getMsgPanel().remove(cmp);
                        this.getViewPanel().resumeEvents();
                    }, this);
                }
            }
        });

        // add the warning message which will adjust the view panel height, so suspend events temporarily
        this.getViewPanel().suspendEvents();
        this.getMsgPanel().add(warningCmp);
        this.getViewPanel().resumeEvents();
    },

    updateSaveChartThumbnail : function(chartDiv)
    {
        if (chartDiv.getEl()) {
            this.chartSVG = LABKEY.vis.SVGConverter.svgToStr(chartDiv.getEl().child('svg').dom);
            this.getSavePanel().updateCurrentChartThumbnail(this.chartSVG);
        }
    },

    isChartConfigValid : function(chartType, chartConfig, aes, scales)
    {
        var selectedMeasureNames = Object.keys(this.measures),
            hasXMeasure = selectedMeasureNames.indexOf('x') > -1 && Ext4.isDefined(aes.x),
            hasXSubMeasure = selectedMeasureNames.indexOf('xSub') > -1 && Ext4.isDefined(aes.xSub),
            hasYMeasure = selectedMeasureNames.indexOf('y') > -1 && Ext4.isDefined(aes.y),
            requiredMeasureNames = this.getChartTypePanel().getRequiredFieldNames();

        // validate that all selected measures still exist by name in the query/dataset
        if (!this.validateMeasuresExist(selectedMeasureNames, requiredMeasureNames))
            return false;

        // validate that the x axis measure exists and data is valid
        if (hasXMeasure && !this.validateAxisMeasure(chartType, chartConfig, 'x', aes, scales, this.getMeasureStoreRecords()))
            return false;

        // validate that the x subcategory axis measure exists and data is valid
        if (hasXSubMeasure && !this.validateAxisMeasure(chartType, chartConfig, 'xSub', aes, scales, this.getMeasureStoreRecords()))
            return false;

        // validate that the y axis measure exists and data is valid
        if (hasYMeasure && !this.validateAxisMeasure(chartType, chartConfig, 'y', aes, scales, this.getMeasureStoreRecords(), this.measures['y'].converted))
            return false;

        return true;
    },

    getPlotConfig : function(newChartDiv, chartType, chartConfig, aes, scales, customRenderType)
    {
        var plotConfig, geom, labels, data = this.getMeasureStoreRecords(), me = this;

        geom = LABKEY.vis.GenericChartHelper.generateGeom(chartType, chartConfig.geomOptions);
        if (chartType == 'line_plot' && (chartConfig.geomOptions.hideDataPoints || data.length > this.dataPointLimit)){
            geom = null;
        }
        
        labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);

        if (chartType == 'bar_chart' || chartType == 'pie_chart')
        {
            var dimName = null, measureName = null, subDimName = null,
                aggType = 'COUNT';

            if (chartConfig.measures.x) {
                dimName = chartConfig.measures.x.converted ? chartConfig.measures.x.convertedName : chartConfig.measures.x.name;
            }
            if (chartConfig.measures.xSub) {
                subDimName = chartConfig.measures.xSub.converted ? chartConfig.measures.xSub.convertedName : chartConfig.measures.xSub.name;
            }
            if (chartConfig.measures.y) {
                measureName = chartConfig.measures.y.converted ? chartConfig.measures.y.convertedName : chartConfig.measures.y.name;

                if (Ext4.isDefined(chartConfig.measures.y.aggregate)) {
                    aggType = chartConfig.measures.y.aggregate.value || chartConfig.measures.y.aggregate;
                }
                // backwards compatibility for bar charts saved prior to aggregate method selection UI
                else if (measureName != null) {
                    aggType = 'SUM';
                }
            }

            data = LABKEY.vis.getAggregateData(data, dimName, subDimName, measureName, aggType, '[Blank]', false);

            // convert any undefined values to zero for display purposes in Bar and Pie chart case
            Ext4.each(data, function(d) {
                if (d.hasOwnProperty('value') && (!Ext4.isDefined(d.value) || isNaN(d.value))) {
                    d.value = 0;
                }
            });
        }

        if (customRenderType && Ext4.isFunction(customRenderType.generatePlotConfig))
        {
            plotConfig = customRenderType.generatePlotConfig(
                    this, chartConfig, newChartDiv.id,
                    chartConfig.width, chartConfig.height,
                    data, aes, scales, labels
            );

            plotConfig.rendererType = 'd3';
        }
        else
        {
            plotConfig = LABKEY.vis.GenericChartHelper.generatePlotConfig(newChartDiv.id, chartConfig, labels, aes, scales, geom, data);

            if (this.renderType == 'pie_chart')
            {
                if (this.checkForNegativeData(data))
                {
                    // adding warning text without shrinking height cuts off the footer text
                    plotConfig.height = Math.floor(plotConfig.height * 0.95);
                }

                plotConfig.callbacks = {
                    onload: function(){
                        // because of the load delay, need to reset the thumbnail svg for pie charts
                        me.updateSaveChartThumbnail(newChartDiv);
                    }
                };
            }
            // if client has specified a line type (only applicable for scatter plot), apply that as another layer
            else if (this.curveFit && this.measures.x && this.isScatterPlot(this.renderType, this.getXAxisType(this.measures.x)))
            {
                var factory = this.lineRenderers[this.curveFit.type];
                if (factory)
                {
                    plotConfig.layers.push(
                        new LABKEY.vis.Layer({
                            geom: new LABKEY.vis.Geom.Path({}),
                            aes: {x: 'x', y: 'y'},
                            data: LABKEY.vis.Stat.fn(factory.createRenderer(this.curveFit.params), this.curveFit.points, this.curveFit.min, this.curveFit.max)
                        })
                    );
                }
            }
        }

        if (!this.supportedBrowser || this.useRaphael)
            plotConfig.rendererType = 'raphael';

        return plotConfig;
    },

    checkForNegativeData : function(data) {
        var negativesFound = [];
        Ext4.each(data, function(entry) {
            if (entry.value < 0) {
                negativesFound.push(entry.label)
            }
        });

        if (negativesFound.length > 0)
        {
            this.addWarningText('There are negative values in the data that the Pie Chart cannot display. '
                    + 'Omitted: ' + negativesFound.join(', '));
        }

        return negativesFound.length > 0;
    },

    initMeasures : function()
    {
        // Initialize the x and y measures on first chart load. Returns false if we're missing the x or y measure.
        var measure, fk,
            queryColumnStore = this.getChartTypePanel().getQueryColumnsStore(),
            requiredFieldNames = this.getChartTypePanel().getRequiredFieldNames(),
            requiresX = requiredFieldNames.indexOf('x') > -1,
            requiresY = requiredFieldNames.indexOf('y') > -1;

        if (!this.measures.y)
        {
            if (this.autoColumnYName || (requiresY && this.autoColumnName))
            {
                fk = this.autoColumnYName || this.autoColumnName;
                measure = this.getMeasureFromFieldKey(fk);
                if (measure)
                    this.setYAxisMeasure(measure);
            }

            if (requiresY && !this.measures.y)
            {
                this.getEl().unmask();
                this.showChartTypeWindow();
                return false;
            }
        }

        if (!this.measures.x)
        {
            if (this.renderType !== "box_plot" && this.renderType !== "auto_plot")
            {
                if (this.autoColumnXName || (requiresX && this.autoColumnName))
                {
                    fk = this.autoColumnXName || this.autoColumnName;
                    measure = this.getMeasureFromFieldKey(fk);
                    if (measure)
                        this.setXAxisMeasure(measure);
                }

                if (requiresX && !this.measures.x)
                {
                    this.getEl().unmask();
                    this.showChartTypeWindow();
                    return false;
                }
            }
            else if (this.autoColumnYName != null)
            {
                measure = queryColumnStore.findRecord('label', 'Study: Cohort', 0, false, true, true);
                if (measure)
                    this.setXAxisMeasure(measure);

                this.autoColumnYName = null;
            }
        }

        return true;
    },

    getMeasureFromFieldKey : function(fk)
    {
        var queryColumnStore = this.getChartTypePanel().getQueryColumnsStore();

        // first search by fk.toString(), for example Analyte.Name -> Analyte$PName
        var measure = queryColumnStore.findRecord('fieldKey', fk.toString(), 0, false, true, true);
        if (measure != null) {
            return measure;
        }

        // second look by fk.getName()
        return queryColumnStore.findRecord('fieldKey', fk.getName(), 0, false, true, true);
    },

    setYAxisMeasure : function(measure)
    {
        if (measure)
        {
            this.measures.y = measure.data ? measure.data : measure;
            this.getChartTypePanel().setFieldSelection('y', this.measures.y);
            this.getChartLayoutPanel().onMeasuresChange(this.measures, this.renderType);
        }
    },

    setXAxisMeasure : function(measure)
    {
        if (measure)
        {
            this.measures.x = measure.data ? measure.data : measure;
            this.getChartTypePanel().setFieldSelection('x', this.measures.x);
            this.getChartLayoutPanel().onMeasuresChange(this.measures, this.renderType);
        }
    },

    validateMeasuresExist: function(measureNames, requiredMeasureNames)
    {
        var store = this.getChartTypePanel().getQueryColumnsStore(),
            valid = true,
            message = null,
            sep = '';

        // Checks to make sure the measures are still available, if not we show an error.
        Ext4.each(measureNames, function(propName) {
            if (this.measures[propName]) {
                var indexByFieldKey = store.find('fieldKey', this.measures[propName].fieldKey, null, null, null, true),
                    indexByName = store.find('fieldKey', this.measures[propName].name, null, null, null, true);

                if (indexByFieldKey === -1 && indexByName === -1) {
                    if (message == null)
                        message = '';

                    message += sep + 'The saved ' + propName + ' measure, ' + this.measures[propName].label + ', is not available. It may have been renamed or removed.';
                    sep = ' ';

                    delete this.measures[propName];
                    this.getChartTypePanel().setToForceApplyChanges();

                    if (requiredMeasureNames.indexOf(propName) > -1)
                        valid = false;
                }
            }
        }, this);

        this.handleValidation({success: valid, message: Ext4.util.Format.htmlEncode(message)});

        return valid;
    },

    validateAxisMeasure : function(chartType, chartConfig, measureName, aes, scales, data, dataConversionHappened)
    {
        var validation = LABKEY.vis.GenericChartHelper.validateAxisMeasure(chartType, chartConfig, measureName, aes, scales, data, dataConversionHappened);
        if (!validation.success)
            delete this.measures[measureName];

        this.handleValidation(validation);
        return validation.success;
    },

    handleValidation : function(validation)
    {
        if (validation.success === true)
        {
            if (validation.message != null)
                this.addWarningText(validation.message);
        }
        else
        {
            this.getEl().unmask();
            this.setRenderRequested(false);

            if (this.editMode)
            {
                this.getChartTypePanel().setToForceApplyChanges();

                Ext4.Msg.show({
                    title: 'Error',
                    msg: Ext4.util.Format.htmlEncode(validation.message),
                    buttons: Ext4.MessageBox.OK,
                    icon: Ext4.MessageBox.ERROR,
                    fn: this.showChartTypeWindow,
                    scope: this
                });
            }
            else
            {
                this.clearChartPanel(true);
                var errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
                    padding: 10,
                    html: '<h3 style="color:red;">Error rendering chart:</h2>' + validation.message,
                    autoScroll: true
                });
                this.getViewPanel().add(errorDiv);
            }
        }
    },

    isScatterPlot : function(renderType, xAxisType)
    {
        if (renderType === 'scatter_plot')
            return true;

        return (renderType === 'auto_plot' && LABKEY.vis.GenericChartHelper.isNumericType(xAxisType));
    },

    isBoxPlot: function(renderType, xAxisType)
    {
        if (renderType === 'box_plot')
            return true;

        return (renderType == 'auto_plot' && !LABKEY.vis.GenericChartHelper.isNumericType(xAxisType));
    },

    getSelectedChartType : function()
    {
        if (Ext4.isString(this.renderType) && this.renderType !== 'auto_plot')
            return this.renderType;
        else if (this.measures.x && this.isBoxPlot(this.renderType, this.getXAxisType(this.measures.x)))
            return 'box_plot';
        else if (this.measures.x && this.isScatterPlot(this.renderType, this.getXAxisType(this.measures.x)))
            return 'scatter_plot';

        return 'bar_plot';
    },

    getXAxisType : function(xMeasure)
    {
        return xMeasure ? (xMeasure.normalizedType || xMeasure.type) : null;
    },

    clearChartPanel : function(clearMessages)
    {
        this.clearWarningText();
        this.getViewPanel().removeAll();
        if (clearMessages) {
            this.getViewPanel().suspendEvents();
            this.getMsgPanel().removeAll();
            this.getViewPanel().resumeEvents();
        }
    },

    clearWarningText : function()
    {
        this.warningText = null;
    },

    addWarningText : function(warning)
    {
        if (!this.warningText)
            this.warningText = Ext4.util.Format.htmlEncode(warning);
        else
            this.warningText = this.warningText + '&nbsp;&nbsp;' + Ext4.util.Format.htmlEncode(warning);
    },

    attachExportIcons : function(chartDiv)
    {
        if (this.supportedBrowser)
        {
            chartDiv.add(this.createExportIcon('fa-file-pdf-o', 'Export to PDF', 0, function(){
                this.exportChartToImage(chartDiv, LABKEY.vis.SVGConverter.FORMAT_PDF);
            }));

            chartDiv.add(this.createExportIcon('fa-file-image-o', 'Export to PNG', 1, function(){
                this.exportChartToImage(chartDiv, LABKEY.vis.SVGConverter.FORMAT_PNG);
            }));
        }
        if (this.isDeveloper)
        {
            chartDiv.add(this.createExportIcon('fa-file-code-o', 'Export as Script', this.supportedBrowser ? 2 : 0, function(){
                this.exportChartToScript();
            }));
        }
    },

    createExportIcon : function(iconCls, tooltip, indexFromLeft, callbackFn)
    {
        return Ext4.create('Ext.Component', {
            cls: 'export-icon',
            style: 'right: ' + ((indexFromLeft*30) + 20) + 'px;',
            html: '<i class="fa ' + iconCls + '"></i>',
            listeners: {
                scope: this,
                render: function(cmp)
                {
                    Ext4.create('Ext.tip.ToolTip', {
                        target: cmp.getEl(),
                        constrainTo: this.getEl(),
                        width: 110,
                        html: tooltip
                    });

                    cmp.getEl().on('click', callbackFn, this);
                }
            }
        });
    },

    exportChartToImage : function(chartDiv, type)
    {
        var fileName = this.getChartConfig().labels.main,
            exportType = type || LABKEY.vis.SVGConverter.FORMAT_PDF;
        LABKEY.vis.SVGConverter.convert(chartDiv.getEl().child('svg').dom, exportType, fileName);
    },

    exportChartToScript : function()
    {
        var chartConfig = LABKEY.vis.GenericChartHelper.removeNumericConversionConfig(this.getChartConfig());
        var queryConfig = this.getQueryConfig(true);

        // Only push the required columns.
        queryConfig.columns = [];

        Ext4.each(['x', 'y', 'color', 'shape', 'series'], function(name) {
            if (chartConfig.measures[name]) {
                queryConfig.columns.push(chartConfig.measures[name].name);
            }
        }, this);

        var templateConfig = {
            chartConfig: chartConfig,
            queryConfig: queryConfig
        };

        this.getExportScriptPanel().setScriptValue(templateConfig);
        this.getExportScriptWindow().show();
    },

    createFilterString : function(filterArray)
    {
        var filterParams = [];
        for (var i = 0; i < filterArray.length; i++){
            filterParams.push(filterArray[i].getURLParameterName() + '=' + filterArray[i].getURLParameterValue());
        }

        return filterParams.join('&');
    },

    hasChartData : function()
    {
        return Ext4.isDefined(this.getMeasureStore()) && Ext4.isArray(this.getMeasureStoreRecords());
    },

    onSelectRowsSuccess : function(measureStore)
    {
        this.measureStore = measureStore;

        // when not in edit mode, we'll use the column metadata from the data query
        if (!this.editMode)
            this.getChartTypePanel().loadQueryColumns(this.getMeasureStoreMetadata().fields);

        this.setDataLoading(false);
        
        this.getMsgPanel().removeAll();

        // If it's already been requested then we just need to request it again, since this time we have the data to render.
        if (this.isRenderRequested())
            this.requestRender();
    },

    getMeasureStore : function()
    {
        return this.measureStore;
    },

    getMeasureStoreRecords : function()
    {
        if (!this.getMeasureStore())
            console.error('No measureStore object defined.');

        return this.getMeasureStore().records();
    },

    getMeasureStoreMetadata : function()
    {
        if (!this.getMeasureStore())
            console.error('No measureStore object defined.');

        return this.getMeasureStore().getResponseMetadata();
    },

    getSchemaName : function()
    {
        if (this.getMeasureStoreMetadata() && this.getMeasureStoreMetadata().schemaName)
        {
            if (Ext4.isArray(this.getMeasureStoreMetadata().schemaName))
                return this.getMeasureStoreMetadata().schemaName[0];

            return this.getMeasureStoreMetadata().schemaName;
        }

        return null;
    },

    getQueryName : function()
    {
        if (this.getMeasureStoreMetadata())
            return this.getMeasureStoreMetadata().queryName;

        return null;
    },

    getDefaultTitle : function()
    {
        if (this.defaultTitleFn)
            return this.defaultTitleFn(this.queryName, this.queryLabel, this.measures.y ? this.measures.y.label : null, this.measures.x ? this.measures.x.label : null);

        return this.queryLabel || this.queryName;
    },

    /**
     * used to determine if the new chart options are different from the currently rendered options
     */
    hasConfigurationChanged : function()
    {
        var queryCfg = this.getQueryConfig();

        if (!queryCfg.schemaName || !queryCfg.queryName)
            return false;

        // ugly race condition, haven't loaded a saved report yet
        if (!this.reportLoaded)
            return false;

        if (!this.hasChartData())
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

    setRenderRequested : function(requested)
    {
        this.renderRequested = requested;
    },

    isRenderRequested : function()
    {
        return this.renderRequested;
    },

    setDataLoading : function(loading)
    {
        this.dataLoading = loading;
    },

    isDataLoading : function()
    {
        return this.dataLoading;
    },

    requestData : function()
    {
        this.setDataLoading(true);

        var config = this.getQueryConfig();
        LABKEY.Query.MeasureStore.selectRows(config);

        this.requestRender();
    },

    requestRender : function()
    {
        if (this.isDataLoading())
            this.setRenderRequested(true);
        else
            this.renderPlot();
    },

    renderChart : function()
    {
        this.getEl().mask('Rendering Chart...');
        this.chartDefinitionChanged.delay(500);
    },

    resizeToViewport : function() {
        console.warn('DEPRECATED: As of Release 17.3 ' + this.$className + '.resizeToViewport() is no longer supported.');
    },

    onSaveBtnClicked : function(isSaveAs)
    {
        this.getSavePanel().setNoneThumbnail(this.getChartTypePanel().getImgUrl());
        this.getSavePanel().setSaveAs(isSaveAs);
        this.getSavePanel().setMainTitle(isSaveAs ? "Save as" : "Save");
        this.getSaveWindow().show();
    }
});
