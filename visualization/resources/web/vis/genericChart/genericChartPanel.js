/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',

    cls : 'generic-chart-panel',
    layout : 'fit',
    editable : false,
    minWidth : 800,

    hideViewData : false,
    reportLoaded : true,

    constructor : function(config)
    {
        Ext4.QuickTips.init();
        this.callParent([config]);
    },

    queryGenericChartColumns : function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('visualization', 'getGenericReportColumns.api'),
            method: 'GET',
            params: {
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName,
                dataRegionName: this.dataRegionName,
                includeCohort: true,
                includeParticipantCategory : true
            },
            success : function(response){
                var o = Ext4.decode(response.responseText);
                this.initialColumnList = o.columns.all;
                this.columnTypes = o.columns;
                this.subject = o.subject;

                this.queryColumnMetadata();
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    queryColumnMetadata : function()
    {
        // use maxRows 0 so that we just get the query metadata
        LABKEY.Query.selectRows({
            maxRows: 0,
            schemaName: this.schemaName,
            queryName: this.queryName,
            viewName: this.viewName,
            parameters: this.parameters,
            requiredVersion: 9.1,
            columns: this.initialColumnList,
            success: function(response){
                this.queryColumnList = response.metaData.fields;
                this.columnQueriesComplete();
            },
            failure : function(response) {
                // this likely means that the query no longer exists, the other selectRows call with show the
                // proper error message to the user
                this.queryColumnList = [];
                this.columnQueriesComplete();
            },
            scope   : this
        });
    },

    queryStudyMeasures : function()
    {
        // NOTE: disable loading of study measures until time chart incorporated
        if (false && LABKEY.vis.GenericChartHelper.getStudyTimepointType() != null)
        {
            LABKEY.Query.Visualization.getMeasures({
                filters: ['study|~'],
                dateMeasures: false,
                success: function (measures, response)
                {
                    var o = Ext4.JSON.decode(response.responseText);
                    this.studyMeasureList = o.measures;
                    this.columnQueriesComplete();
                },
                failure: this.onFailure,
                scope: this
            });
        }
        else
        {
            this.studyMeasureList = [];
            this.columnQueriesComplete();
        }
    },

    columnQueriesComplete : function()
    {
        if (Ext4.isDefined(this.queryColumnList) && Ext4.isDefined(this.studyMeasureList))
        {
            this.loadQueryColumns();
            this.loadStudyColumns();

            this.requestData();
        }
    },

    initComponent : function()
    {
        this.measures = {};
        this.options = {};

        // boolean to check if we should allow things like export to PDF
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8);

        var params = LABKEY.ActionURL.getParameters();
        this.editMode = (params.edit == "true" || !this.savedReportInfo) && this.allowEditMode;
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

                if (this.editMode && !this.initialColumnList)
                {
                    this.queryGenericChartColumns();
                    this.queryStudyMeasures();
                }
                else
                {
                    this.requestData();
                }
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
        {
            this.markDirty(false);
            this.loadSavedConfig();
        }
        else
        {
            this.markDirty(false);
            this.on('render', this.ensureQuerySettings, this);
        }

        window.onbeforeunload = LABKEY.beforeunload(this.beforeUnload, this);
    },

    getViewPanel : function()
    {
        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                overflowY   : 'auto',
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
                dockedItems: [this.getTopButtonBar()]
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
                hidden  : this.isNew() || this.hideSave,
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
                        this.getExportBtn().show();

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

                        this.getChartTypeBtn().hide();
                        this.getChartLayoutBtn().hide();
                        this.getExportBtn().hide();

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
        if (!this.hideViewData)
            tbarItems.push(''); // horizontal spacer
        tbarItems.push(this.getExportBtn());
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

            if (!this.hideSave)
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

    showChartTypeWindow : function()
    {
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
                selectedType: this.getSelectedChartType(),
                selectedFields: this.measures,
                restrictColumnsEnabled: this.restrictColumnsEnabled,
                customRenderTypes: this.customRenderTypes,
                baseQueryKey: this.schemaName + '|' + this.queryName,
                studyQueryName: this.schemaName == 'study' ? this.queryName : null,
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

                        this.getChartLayoutPanel().onMeasuresChange(this.measures, this.renderType);
                        this.getChartLayoutPanel().updateVisibleLayoutOptions(this.getSelectedChartTypeData(), this.measures);
                        this.ensureChartLayoutOptions();

                        this.renderChart();
                        this.getChartTypeWindow().hide();
                    }
                }
            });
        }

        return this.chartTypePanel;
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
                defaultChartLabel: this.getDefaultTitle(),
                defaultOpacity: this.renderType == 'bar_chart' ? 100 : undefined,
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
            columns     : this.savedColumns,        // TODO, qwp does not support passing in a column list
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
                this.updateQueryParameters(wp.parameters);
        }, this);

        wp.render(renderTo);
    },

    updateQueryParameters : function(updatedParams)
    {
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
            requiredVersion : 9.1,
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
                this.getExportBtn().disable();
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
                // Check if we have cohorts available.
                if (Ext4.isArray(this.initialColumnList))
                {
                    for (var i = 0; i < this.initialColumnList.length; i++)
                    {
                        if (this.initialColumnList[i].indexOf('Cohort') > -1)
                            columns.push(this.initialColumnList[i]);
                    }
                }
            }

            if (measures.y)
                this.addMeasureForColumnQuery(columns, measures.y);
            else if (this.autoColumnYName)
                columns.push(this.autoColumnYName.toString());

            if (this.autoColumnName)
                columns.push(this.autoColumnName.toString());

            if (measures.color)
                this.addMeasureForColumnQuery(columns, measures.color);

            if (measures.shape)
                this.addMeasureForColumnQuery(columns, measures.shape);
        }
        else
        {
            // If we're in edit mode then we can load all of the columns.
            if (this.initialColumnList)
                columns = this.initialColumnList;
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

    ensureQuerySettings : function()
    {
        if (!this.schemaName || !this.queryName)
        {
            this.schemaName = 'study';
            this.getChartQueryWindow().show();
        }
    },

    getChartQueryWindow : function()
    {
        if (!this.chartQueryWindow)
        {
            var panel = this.getChartQueryPanel();

            this.chartQueryWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                panelToMask: this,
                onEsc: function() {
                    panel.cancelHandler.call(panel);
                },
                items: [panel]
            });
        }

        return this.chartQueryWindow;
    },

    getChartQueryPanel : function()
    {
        if (!this.querySettingsPanel)
        {
            this.querySettingsPanel = Ext4.create('LABKEY.vis.ChartQueryPanel', {
                schemaName: this.schemaName,
                listeners: {
                    scope: this,
                    ok: function(panel, schemaName, queryName, queryLabel)
                    {
                        this.schemaName = schemaName;
                        this.queryName = queryName;
                        this.queryLabel = queryLabel;

                        this.updateChartTask.delay(500);
                        this.getChartQueryWindow().hide();
                    }
                }
            });
        }

        return this.querySettingsPanel;
    },

    markDirty : function(dirty)
    {
        this.dirty = dirty;
        LABKEY.Utils.signalWebDriverTest("genericChartDirty", dirty);
    },

    isDirty : function()
    {
        return this.dirty;
    },

    beforeUnload : function()
    {
        if (!this.hideSave && this.isDirty()) {
            return 'please save your changes';
        }
    },

    getCurrentReportConfig : function()
    {
        return {
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
            success : function(resp){
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

                this.updateNavTrailTitle(reportConfig.name);

                var o = Ext4.decode(resp.responseText);
                this.loadReportFromId(o.reportId);

                this.getSaveWindow().close();
            },
            failure : this.onFailure,
            scope   : this
        });
    },

    updateNavTrailTitle : function(title)
    {
        var navTitle = Ext4.query('*[class=labkey-nav-trail] *[class=labkey-nav-page-header]');
        if (navTitle && (navTitle.length >= 1))
            navTitle[0].innerHTML = LABKEY.Utils.encodeHtml(title);
    },

    onFailure : function(resp)
    {
        var error = Ext4.isString(resp) ? Ext4.decode(resp.responseText).exception : resp.exception;
        if (error) {
            Ext4.Msg.alert('Error', error);
        }
        else {
            Ext4.Msg.alert('Error', 'An unknown error has occurred.');
        }
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
                this.measures.x = chartConfig.measures.x;
                this.measures.y = chartConfig.measures.y;
                if (chartConfig.measures.color)
                    this.measures.color = chartConfig.measures.color;
                if (chartConfig.measures.shape)
                    this.measures.shape = chartConfig.measures.shape;
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

        this.disableExport();

        // Keep the toggle button enabled so the user can remove filters
        this.getToggleViewBtn().enable();

        this.clearChartPanel();
        this.getViewPanel().add(errorDiv);
        this.getEl().unmask();
    },

    renderPlot : function()
    {
        // Don't attempt to render if the view panel isn't visible or the chart type window is visible.
        if (this.getViewPanel().isHidden() || this.getChartTypeWindow().isVisible())
            return;

        // initMeasures returns false and opens the Chart Type panel if a required measure is not chosen by the user.
        if (!this.initMeasures())
            return;

        this.clearChartPanel();

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
        var aes, scales, plot, plotConfig, customRenderType, hasNoDataMsg, newChartDiv, droppedValues, measuresForProcessing = {};

        hasNoDataMsg = LABKEY.vis.GenericChartHelper.validateResponseHasData(this.chartData, true);
        if (hasNoDataMsg != null)
        {
            this.handleNoData(hasNoDataMsg);
            return;
        }

        this.getEl().mask('Rendering Chart...');

        aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, this.chartData.schemaName, this.chartData.queryName);

        customRenderType = this.customRenderTypes ? this.customRenderTypes[this.renderType] : undefined;
        if (customRenderType && customRenderType.generateAes)
            aes = customRenderType.generateAes(this, chartConfig, aes);

        if (chartConfig.measures.x.measure) {
            measuresForProcessing.x = {};
            measuresForProcessing.x.name = chartConfig.measures.x.name;
            measuresForProcessing.x.label = chartConfig.measures.x.label;
        }
        if (chartConfig.measures.y.measure) {
            measuresForProcessing.y = {};
            measuresForProcessing.y.name = chartConfig.measures.y.name;
            measuresForProcessing.y.label = chartConfig.measures.y.label;
        }
        if (!Ext4.Object.isEmpty(measuresForProcessing)) {
            droppedValues = this.processMeasureData(this.chartData.rows, aes, measuresForProcessing);

            for (var measure in droppedValues) {
                if (droppedValues.hasOwnProperty(measure)) {
                    if (droppedValues[measure].numDropped) {
                        this.addWarningText("The "
                                + measure + "-axis measure '"
                                + droppedValues[measure].label + "' had "
                                + droppedValues[measure].numDropped +
                                " value(s) that could not be converted to a number and are not included in the plot.");
                    }
                }
            }
        }

        scales = LABKEY.vis.GenericChartHelper.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, this.chartData, this.defaultNumberFormat);
        if (customRenderType && customRenderType.generateScales)
            scales = customRenderType.generateScales(this, chartConfig, scales);

        if (!Ext4.isDefined(chartConfig.width) || chartConfig.width == null)
            chartConfig.width = this.getViewPanel().getWidth();
        if (!Ext4.isDefined(chartConfig.height) || chartConfig.height == null)
            chartConfig.height = this.getViewPanel().getHeight() - 25;

        if (!this.isChartConfigValid(chartType, chartConfig, aes, scales))
            return;

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

        this.afterRenderPlotComplete(newChartDiv);
    },

    processMeasureData : function (rows, aes, measuresForProcessing)
    {
        var droppedValues = {};

        for (var i = 0; i < rows.length; i++) {
            for (var measure in measuresForProcessing) {
                if (measuresForProcessing.hasOwnProperty(measure)) {
                    droppedValues[measure] = {};
                    droppedValues[measure].label = measuresForProcessing[measure].label;
                    droppedValues[measure].numDropped = 0;

                    if (aes.hasOwnProperty(measure)) {
                        var value = aes[measure](rows[i]);

                        if (typeof value !== 'number' && value !== null) {

                            //only try to convert strings to numbers
                            if (typeof value === 'string') {
                                value = value.trim();
                            } else {
                                //dates, objects, booleans etc. to be assigned value: NULL
                                value = '';
                            }

                            var n = Number(value);
                            // empty strings convert to 0, which we must explicitly deny
                            if (value === '' || isNaN(n)) {
                                rows[i][measuresForProcessing[measure].name].value = null;
                                droppedValues[measure].numDropped++;
                            } else {
                                rows[i][measuresForProcessing[measure].name].value = n;
                            }
                        }
                    }
                }
            }
        }

        return droppedValues;
    },

    getNewChartDisplayDiv : function()
    {
        return Ext4.create('Ext.container.Container', {
            border: 1,
            cls: 'chart-display',
            autoEl: {tag: 'div'}
        });
    },

    afterRenderPlotComplete : function(chartDiv)
    {
        this.addWarningMsg(chartDiv);

        this.getTopButtonBar().enable();
        this.getChartTypeBtn().enable();
        this.getChartLayoutBtn().enable();
        this.getSaveBtn().enable();
        this.getSaveAsBtn().enable();
        this.enableExport();
        this.getEl().unmask();

        if (this.editMode && this.supportedBrowser)
            this.updateSaveChartThumbnail(chartDiv);
    },

    addWarningMsg : function(chartDiv)
    {
        if (this.warningText !== null)
        {
            var warningDivId = Ext4.id();
            var dismissLink = LABKEY.Utils.textLink({text: 'dismiss', onClick: 'Ext4.get(\'' + warningDivId + '\').destroy();'});

            var warningDiv = document.createElement('div');
            warningDiv.setAttribute('id', warningDivId);
            warningDiv.setAttribute('style', 'padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;');
            warningDiv.innerHTML = this.warningText + ' ' + dismissLink;
            chartDiv.getEl().insertFirst(warningDiv);
        }
    },

    updateSaveChartThumbnail : function(chartDiv)
    {
        this.chartSVG = LABKEY.vis.SVGConverter.svgToStr(chartDiv.getEl().child('svg').dom);
        this.getSavePanel().updateCurrentChartThumbnail(this.chartSVG);
    },

    isChartConfigValid : function(chartType, chartConfig, aes, scales)
    {
        var selectedMeasureNames = Object.keys(this.measures),
            hasXMeasure = selectedMeasureNames.indexOf('x') > 0 && Ext4.isDefined(aes.x),
            hasYMeasure = selectedMeasureNames.indexOf('y') > 0 && Ext4.isDefined(aes.y),
            requiredMeasureNames = this.getChartTypePanel().getRequiredFieldNames();

        // validate that all selected measures still exist by name in the query/dataset
        if (!this.validateMeasuresExist(selectedMeasureNames, requiredMeasureNames))
            return false;

        // validate that the x axis measure exists and data is valid
        if (hasXMeasure && !this.validateAxisMeasure(chartType, chartConfig, 'x', aes, scales, this.chartData.rows))
            return false;

        // validate that the y axis measure exists and data is valid
        if (hasYMeasure && !this.validateAxisMeasure(chartType, chartConfig, 'y', aes, scales, this.chartData.rows))
            return false;

        return true;
    },

    getPlotConfig : function(newChartDiv, chartType, chartConfig, aes, scales, customRenderType)
    {
        var plotConfig, geom, labels, data = this.chartData.rows, me = this;

        if (chartType == 'scatter_plot' && data.length > chartConfig.geomOptions.binThreshold)
        {
            chartConfig.geomOptions.binned = true;
            this.addWarningText(
                "The number of individual points exceeds the limit set in the Chart Layout options. " +
                "The data will be displayed according to point density in a heat map."
            );
        }

        geom = LABKEY.vis.GenericChartHelper.generateGeom(chartType, chartConfig.geomOptions);
        labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);

        if (chartType == 'bar_chart' || chartType == 'pie_chart')
        {
            var dimName = chartConfig.measures.x ? chartConfig.measures.x.name : null;
            var measureName = chartConfig.measures.y ? chartConfig.measures.y.name : null;
            var aggType = measureName != null ? 'SUM' : 'COUNT';
            data = LABKEY.vis.GenericChartHelper.generateAggregateData(data, dimName, measureName, aggType, '[Blank]');
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
            measureStore = this.getChartTypePanel().getQueryColumnsStore(),
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
                measure = measureStore.findRecord('label', 'Study: Cohort', 0, false, true, true);
                if (measure)
                    this.setXAxisMeasure(measure);

                this.autoColumnYName = null;
            }
        }

        return true;
    },

    getMeasureFromFieldKey : function(fk)
    {
        var measureStore = this.getChartTypePanel().getQueryColumnsStore(),
            fkName = fk.getParts().length > 1 ? fk.toString() : fk.getName();
        return measureStore.findRecord('name', fkName, 0, false, true, true);
    },

    setYAxisMeasure : function(measure)
    {
        if (measure)
        {
            this.measures.y = measure.data ? measure.data : measure;
            this.getChartLayoutPanel().onMeasuresChange(this.measures, this.renderType);
        }
    },

    setXAxisMeasure : function(measure)
    {
        if (measure)
        {
            this.measures.x = measure.data ? measure.data : measure;
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
        Ext4.each(measureNames, function(propName)
        {
            if (this.measures[propName] && store.find('name', this.measures[propName].name, null, null, null, true) === -1)
            {
                if (message == null)
                    message = '';

                message += sep + 'The saved ' + propName + ' measure, ' + this.measures[propName].label + ', is not available. It may have been renamed or removed.';
                sep = ' ';

                delete this.measures[propName];
                this.getChartTypePanel().setToForceApplyChanges();

                if (requiredMeasureNames.indexOf(propName) > -1)
                    valid = false;
            }
        }, this);

        this.handleValidation({success: valid, message: Ext4.util.Format.htmlEncode(message)});

        return valid;
    },

    validateAxisMeasure : function(chartType, chartConfig, measureName, aes, scales, data)
    {
        var validation = LABKEY.vis.GenericChartHelper.validateAxisMeasure(chartType, chartConfig, measureName, aes, scales, data);
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
                this.clearChartPanel();
                var errorDiv = Ext4.create('Ext.container.Container', {
                    border: 1,
                    autoEl: {tag: 'div'},
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

        return (renderType === 'auto_plot' && (xAxisType == 'int' || xAxisType == 'float' || xAxisType == 'date'));
    },

    isBoxPlot: function(renderType, xAxisType)
    {
        if (renderType === 'box_plot')
            return true;

        return (renderType == 'auto_plot' && (xAxisType == 'string' || xAxisType == 'boolean'));
    },

    getSelectedChartType : function()
    {
        if (Ext4.isString(this.renderType) && this.renderType !== 'auto_plot')
            return this.renderType;
        else if (!this.measures.x || this.isBoxPlot(this.renderType, this.getXAxisType(this.measures.x)))
            return 'box_plot';
        else if (this.measures.x && this.isScatterPlot(this.renderType, this.getXAxisType(this.measures.x)))
            return 'scatter_plot';

        return null;
    },

    getXAxisType : function(xMeasure)
    {
        return xMeasure ? (xMeasure.normalizedType || xMeasure.type) : null;
    },

    clearChartPanel : function()
    {
        this.clearWarningText();
        this.getViewPanel().removeAll();
        this.disableExport();
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

    getExportItem : function(menuLabel)
    {
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

    disableExport : function()
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

    enableExport : function()
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

    exportChartToPdf : function()
    {
        this.exportChartToImage(LABKEY.vis.SVGConverter.FORMAT_PDF);
    },

    exportChartToPng : function()
    {
        this.exportChartToImage(LABKEY.vis.SVGConverter.FORMAT_PNG);
    },

    exportChartToImage : function(type)
    {
        var chartContainer = this.getViewPanel().down('container[cls~=chart-display]');
        if (chartContainer != null)
            LABKEY.vis.SVGConverter.convert(chartContainer.getEl().child('svg').dom, (type || LABKEY.vis.SVGConverter.FORMAT_PDF), this.getChartConfig().labels.main);
    },

    exportChartToScript : function()
    {
        var chartConfig = this.getChartConfig();
        var queryConfig = this.getQueryConfig(true);

        // Only push the required columns.
        queryConfig.columns = [];

        if (chartConfig.measures.x)
            queryConfig.columns.push(chartConfig.measures.x.name);
        if (chartConfig.measures.y)
            queryConfig.columns.push(chartConfig.measures.y.name);
        if (chartConfig.measures.color)
            queryConfig.columns.push(chartConfig.measures.color.name);
        if (chartConfig.measures.shape)
            queryConfig.columns.push(chartConfig.measures.shape.name);

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
        return Ext4.isDefined(this.chartData) && Ext4.isArray(this.chartData.rows);
    },

    onSelectRowsSuccess : function(response)
    {
        this.chartData = response;

        // when not in edit mode, we'll use the column metadata from the data query
        if (!this.editMode)
        {
            this.queryColumnList = this.chartData.metaData.fields;
            this.loadQueryColumns();
        }

        this.setDataLoading(false);

        // If it's already been requested then we just need to request it again, since this time we have the data to render.
        if (this.isRenderRequested())
            this.requestRender();
    },

    loadQueryColumns : function()
    {
        var queryFields = this.getQueryFields(this.queryColumnList);
        this.getChartTypePanel().loadQueryColumns(queryFields);
    },

    loadStudyColumns : function()
    {
        this.getChartTypePanel().loadStudyColumns(this.studyMeasureList);
    },

    getQueryFields : function(fields)
    {
        var queryFields = [];

        Ext4.each(fields, function(field)
        {
            var f = Ext4.clone(field);
            f.schemaName = this.schemaName;
            f.queryName = this.queryName;
            f.isCohortColumn = false;
            f.isSubjectGroupColumn = false;

            // issue 23224: distinguish cohort and subject group fields in the list of query columns
            if (Ext4.isDefined(this.columnTypes))
            {
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
        LABKEY.Query.selectRows(this.getQueryConfig());
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

    onSaveBtnClicked : function(isSaveAs)
    {
        this.getSavePanel().setNoneThumbnail(this.getChartTypePanel().getImgUrl());
        this.getSavePanel().setSaveAs(isSaveAs);
        this.getSavePanel().setMainTitle(isSaveAs ? "Save as" : "Save");
        this.getSaveWindow().show();
    }
});
