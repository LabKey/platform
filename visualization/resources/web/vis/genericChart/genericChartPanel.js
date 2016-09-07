/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.GenericChartPanel', {

    extend : 'Ext.panel.Panel',

    cls : 'generic-chart-panel',
    layout : 'fit',
    editable : false,
    minWidth : 800,

    dataLimit : 5000,
    hideViewData : false,

    constructor : function(config)
    {

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
        Ext4.each(['autoColumnXName', 'autoColumnYName', 'autoColumnName'], function(autoColPropName)
        {
            if (this[autoColPropName])
            {
                fk = LABKEY.FieldKey.fromParts(this[autoColPropName]);
                this[autoColPropName] = fk.toString();
            }
        }, this);

        // for backwards compatibility, map auto_plot to box_plot
        if (this.renderType === 'auto_plot')
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

    getViewPanel : function()
    {
        if (!this.viewPanel)
        {
            this.viewPanel = Ext4.create('Ext.panel.Panel', {
                overflowY   : 'auto',
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

    getDataPanel : function()
    {
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
                border      : false,
                items       : dataGrid
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
                    if (this.viewPanel.isHidden())
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
        return !this.reportId;
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

                        this.getChartLayoutPanel().onMeasuresChange(this.measures);
                        this.getChartLayoutPanel().updateVisibleLayoutOptions(this.getSelectedChartTypeData());
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
                this.getChartLayoutPanel().fireEvent('show', this.getChartLayoutPanel(), this.getSelectedChartTypeData());
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
                defaultChartLabel: this.getDefaultTitle(),
                listeners: {
                    scope: this,
                    cancel: function(panel)
                    {
                        this.getChartLayoutWindow().hide();
                    },
                    apply: function(panel, values)
                    {
                        // note: this event will only fire if a change was made in the Chart Type panel
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
                width: 800,
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
            showPagination        : false, // TODO why don't we show pagination on this QWP?
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
                this.getExportBtn().disable();
                this.getChartTypeBtn().disable();
                this.getChartLayoutBtn().disable();
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

            for (var i=0; i < filters.length; i++)
            {
                var f = filters[i];
                newFilters.push({name : f.getColumnName(), value : f.getValue(), type : f.getFilterType().getURLSuffix()});
            } filters = newFilters;
        }
        config['filterArray'] = filters;

        return config;
    },

    getQueryConfigColumns : function()
    {
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
                columns.push(measures.y.name);
            else if (this.autoColumnYName)
                columns.push(this.autoColumnYName);

            if (this.autoColumnName)
                columns.push(this.autoColumnName);

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

    getChartConfig : function()
    {
        var config = {};

        config.measures = Ext4.apply({}, this.measures);
        config.scales = {};
        config.labels = {};

        this.ensureChartLayoutOptions();
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

            var dialog = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                panelToMask: this,
                items : [this.getQuerySettingsPanel()]
            });

            dialog.show();
        }
    },

    getQuerySettingsPanel : function()
    {
        if (!this.querySettingsPanel)
        {
            var schemaCombo, queryCombo;

            schemaCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel  : 'Schema',
                name        : 'schema',
                store       : this.initializeSchemaStore(),
                editable    : false,
                value       : this.schemaName,
                queryMode      : 'local',
                displayField   : 'name',
                valueField     : 'name',
                emptyText      : 'None',
                padding     : '10px 10px 0 10px',
                listeners   : {
                    scope : this,
                    change : function(cmp, newValue)
                    {
                        this.schemaName = newValue;

                        var proxy = queryCombo.getStore().getProxy();
                        if (proxy)
                        {
                            proxy.extraParams = {schemaName : newValue};
                            queryCombo.getStore().load();
                        }

                        queryCombo.clearValue();
                        okBtn.disable();
                    }
                }
            });

            queryCombo = Ext4.create('Ext.form.field.ComboBox', {
                fieldLabel  : 'Query',
                name        : 'query',
                store       : this.initializeQueryStore(),
                editable    : false,
                allowBlank  : false,
                displayField   : 'queryLabel',
                triggerAction  : 'all',
                typeAhead      : true,
                valueField     : 'name',
                emptyText      : 'None',
                padding     : 10,
                listeners   : {
                    scope : this,
                    change : function(cmp, newValue)
                    {
                        var selected = cmp.getStore().getAt(cmp.getStore().find('name', newValue));
                        this.queryLabel = selected ? selected.data.title : null;
                        this.queryName = selected ? selected.data.name : null;
                        okBtn.enable();
                    }
                }
            });

            queryCombo.getStore().addListener('beforeload', function(){
                okBtn.disable();
            }, this);


            var okBtn = Ext4.create('Ext.button.Button', {
                text : 'OK',
                disabled: true,
                handler : function(btn) {
                    var dialog = btn.up('window');
                    var form = dialog.down('form').getForm();

                    if (form.isValid())
                    {
                        dialog.hide();
                        this.updateChartTask.delay(500);
                    }
                },
                scope   : this
            });

            var cancelBtn = Ext4.create('Ext.button.Button', {
                text : 'Cancel',
                handler : function(btn) {
                    window.history.back()
                }
            });

            this.querySettingsPanel = Ext4.create('LABKEY.vis.ChartWizardPanel', {
                cls: 'chart-wizard-panel chart-query-panel',
                height: 210,
                width: 440,
                items: [
                    Ext4.create('Ext.panel.Panel', {
                        region: 'north',
                        cls: 'region-panel title-panel',
                        border: false,
                        html: 'Select a query'
                    }),
                    Ext4.create('Ext.form.Panel', {
                        region: 'center',
                        cls: 'region-panel',
                        fieldDefaults: {
                            labelWidth: 75,
                            width: 375
                        },
                        items : [schemaCombo, queryCombo]
                    }),
                    Ext4.create('Ext.toolbar.Toolbar', {
                        region: 'south',
                        cls: 'region-panel button-bar',
                        border: false,
                        ui: 'footer',
                        defaults: {width: 65},
                        items: ['->', cancelBtn, okBtn]
                    })
                ]
            });
        }

        return this.querySettingsPanel;
    },

    /**
     * Create the store for the schema
     */
    initializeSchemaStore : function()
    {

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
    initializeQueryStore : function()
    {

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
                this.reportId = o.reportId;
                this.loadReport(this.reportId);

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
        var error = Ext4.decode(resp.responseText).exception;
        if (error) {
            Ext4.Msg.alert('Error', error);
        }
        else {
            Ext4.Msg.alert('Error', 'An unknown error has occurred, unable to save the chart.');
        }
    },

    loadReport : function(reportId)
    {
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

    loadSavedConfig : function(config)
    {
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
            shared: config["public"], // note: config.public does not work with yuicompressor
            reportProps: config.reportProps,
            thumbnailURL: config.thumbnailURL
        });

        var json = Ext4.decode(config.jsonData);
        this.loadQueryInfoFromConfig(json.queryConfig);
        this.loadMeasuresFromConfig(json.chartConfig);
        this.loadOptionsFromConfig(json.chartConfig);

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
            if (chartConfig.pointType)
                this.options.general.pointType = chartConfig.pointType;
            if (chartConfig.geomOptions)
                Ext4.apply(this.options.general, chartConfig.geomOptions);

            if (chartConfig.labels && chartConfig.labels.main)
                this.options.general.label = chartConfig.labels.main;
            else
                this.options.general.label = this.getDefaultTitle();

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

    handleNoData : function()
    {
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
        this.getChartLayoutBtn().disable();
        this.getSaveBtn().disable();
        this.getSaveAsBtn().disable();

        this.disableExport();

        // Keep the toggle button enabled so the user can remove filters
        this.getToggleViewBtn().enable();

        this.clearChartPanel();
        this.viewPanel.add(errorDiv);
        this.getEl().unmask();
    },

    renderPlot : function(forExport)
    {
        // Don't attempt to render if the view panel isn't visible or the chart type window is visible.
        if (this.viewPanel.isHidden() || this.getChartTypeWindow().isVisible())
            return;

        if (!forExport)
        {
            this.getEl().mask('Rendering Chart...');
            this.clearChartPanel();
        }

        if (this.chartData.rows.length === 0)
        {
            this.getEl().unmask();
            this.handleNoData();
            return;
        }

        // initMeasures returns false and opens the Chart Type panel if a required measure is not chosen by the user.
        if (!this.initMeasures(forExport))
            return;

        var newChartDiv = Ext4.create('Ext.container.Container', {
            border: 1,
            autoEl: {tag: 'div'}
        });
        this.viewPanel.add(newChartDiv);

        var chartType, aes, scales, plotConfig,
            chartConfig = this.getChartConfig(),
            customRenderType = this.customRenderTypes ? this.customRenderTypes[this.renderType] : undefined,
            xAxisType = chartConfig.measures.x ? chartConfig.measures.x.normalizedType : null;

        chartType = LABKEY.vis.GenericChartHelper.getChartType(this.renderType, xAxisType);

        aes = LABKEY.vis.GenericChartHelper.generateAes(chartType, chartConfig.measures, this.chartData.schemaName, this.chartData.queryName);
        if (customRenderType && customRenderType.generateAes)
            aes = customRenderType.generateAes(this, chartConfig, aes);

        scales = LABKEY.vis.GenericChartHelper.generateScales(chartType, chartConfig.measures, chartConfig.scales, aes, this.chartData, this.defaultNumberFormat);
        if (customRenderType && customRenderType.generateScales)
            scales = customRenderType.generateScales(this, chartConfig, scales);

        if (!Ext4.isDefined(chartConfig.width) || chartConfig.width == null)
            chartConfig.width = !forExport ? this.viewPanel.getWidth() : 1200;
        if (!Ext4.isDefined(chartConfig.height) || chartConfig.height == null)
            chartConfig.height = !forExport ? this.viewPanel.getHeight() - 25 : 600;

        if (!this.isChartConfigValid(chartType, chartConfig, aes, scales))
            return;

        plotConfig = this.getPlotConfig(newChartDiv, chartType, chartConfig, aes, scales, customRenderType);

        if (this.renderType == 'pie_chart')
        {
            new LABKEY.vis.PieChart(plotConfig);
        }
        else
        {
            var plot = new LABKEY.vis.Plot(plotConfig);
            plot.render();
        }

        if (this.chartData.rows.length == this.dataLimit)
            this.addWarningText("The data limit for plotting has been reached. Consider filtering your data.");

        if (this.warningText !== null)
        {
            var warningDivId = Ext4.id();
            var dismissLink = LABKEY.Utils.textLink({text: 'dismiss', onClick: 'Ext4.get(\'' + warningDivId + '\').destroy();'});

            var warningDiv = document.createElement('div');
            warningDiv.setAttribute('id', warningDivId);
            warningDiv.setAttribute('style', 'padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;');
            warningDiv.innerHTML = this.warningText + ' ' + dismissLink;
            newChartDiv.getEl().insertFirst(warningDiv);
        }

        if (!forExport)
        {
            this.getTopButtonBar().enable();

            this.getChartTypeBtn().enable();
            this.getChartLayoutBtn().enable();
            this.getSaveBtn().enable();
            this.getSaveAsBtn().enable();

            this.enableExport();

            this.getEl().unmask();
            if (this.editMode && this.supportedBrowser)
            {
                // TODO we shouldn't have to call this a second time anymore since we remove the clickable axis/title labels
                // Update thumbnail
                var thumbnail = this.renderPlot(true);
                if (thumbnail)
                {
                    this.chartSVG = LABKEY.vis.SVGConverter.svgToStr(Ext4.get(thumbnail).child('svg').dom);
                    this.getSavePanel().updateCurrentChartThumbnail(this.chartSVG);
                    // destroy the temp chart element
                    Ext4.getCmp(thumbnail).destroy();
                }
            }
        }
        else
        {
            return newChartDiv.id;
        }

        // We just rendered the plot, we don't need to request another render.
        this.setRenderRequested(false);
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
        var plotConfig, geom, labels, layers = [],
            dimName = chartConfig.measures.x ? chartConfig.measures.x.name : null,
            measureName = chartConfig.measures.y ? chartConfig.measures.y.name : null,
            aggType = measureName != null ? 'SUM' : 'COUNT',
            data = this.chartData.rows;

        geom = LABKEY.vis.GenericChartHelper.generateGeom(chartType, chartConfig.geomOptions);
        labels = LABKEY.vis.GenericChartHelper.generateLabels(chartConfig.labels);

        plotConfig = {
            renderTo: newChartDiv.id,
            width: chartConfig.width,
            height: chartConfig.height
        };

        if (customRenderType && Ext4.isFunction(customRenderType.generatePlotConfig))
        {
            plotConfig = customRenderType.generatePlotConfig(
                    this, chartConfig, newChartDiv.id,
                    chartConfig.width, chartConfig.height,
                    data, aes, scales, labels
            );
        }
        else if (this.renderType == 'pie_chart')
        {
            data = LABKEY.vis.GenericChartHelper.generateAggregateData(data, dimName, measureName, aggType, '[Blank]');

            plotConfig = Ext4.apply(plotConfig, {
                data: data,
                header: {
                    title: { text: labels.main.value },
                    subtitle: { text: labels.x.value },
                    titleSubtitlePadding: 1
                },
                labels: {
                    mainLabel: { fontSize: 14 },
                    percentage: { fontSize: 14 },
                    outer: { pieDistance: 20 },
                    inner: { hideWhenLessThanPercentage: 5 }
                },
                //misc: { colors: { segments: LABKEY.vis.Scale.DarkColorDiscrete() } },
                effects: { highlightSegmentOnMouseover: false },
                tooltips: { enabled: true }
            });
        }
        else
        {
            if (chartConfig.pointType == 'all')
            {
                layers.push(
                    new LABKEY.vis.Layer({
                        data: data,
                        geom: LABKEY.vis.GenericChartHelper.generatePointGeom(chartConfig.geomOptions),
                        aes: {hoverText: LABKEY.vis.GenericChartHelper.generatePointHover(chartConfig.measures)}
                    })
                );
            }
            else if (this.renderType == 'bar_chart')
            {
                data = LABKEY.vis.GenericChartHelper.generateAggregateData(data, dimName, measureName, aggType, '[Blank]');
                aes = { x: 'label', y: 'value' };
                scales.y = {domain: [0, null]}; // TODO what about if the SUM is negative?
            }

            layers.push(
                new LABKEY.vis.Layer({
                    data: data,
                    geom: geom
                })
            );

            // client has specified a line type (only applicable for scatter plot)
            if (this.curveFit && this.measures.x && this.isScatterPlot(this.renderType, this.measures.x.normalizedType))
            {
                var factory = this.lineRenderers[this.curveFit.type];
                if (factory)
                {
                    layers.push(
                        new LABKEY.vis.Layer({
                            geom: new LABKEY.vis.Geom.Path({}),
                            aes: {x: 'x', y: 'y'},
                            data: LABKEY.vis.Stat.fn(factory.createRenderer(this.curveFit.params),
                                    this.curveFit.points, this.curveFit.min, this.curveFit.max)
                        })
                    );
                }
            }

            plotConfig = Ext4.apply(plotConfig, {
                data: data,
                labels: labels,
                aes: aes,
                scales: scales,
                layers: layers
            });
        }

        if (this.supportedBrowser && !this.useRaphael)
            plotConfig.rendererType = 'd3';

        return plotConfig;
    },

    initMeasures : function(forExport)
    {
        // Initialize the x and y measures on first chart load. Returns false if we're missing the x or y measure.
        var measure, fk,
            measureStore = this.getChartTypePanel().getStore(),
            requiredFieldNames = this.getChartTypePanel().getRequiredFieldNames(),
            requiresX = requiredFieldNames.indexOf('x') > -1,
            requiresY = requiredFieldNames.indexOf('y') > -1;

        if (!this.measures.y && !forExport)
        {
            if (this.autoColumnYName || (requiresY && this.autoColumnName))
            {
                // In some cases the column name is escaped, so we need to unescape it when searching.
                fk = LABKEY.FieldKey.fromString(this.autoColumnYName || this.autoColumnName);
                measure = measureStore.findRecord('name', fk.name, 0, false, true, true);
                if (measure)
                    this.setYAxisMeasure(measure, true);
            }

            if (requiresY && !this.measures.y)
            {
                this.getEl().unmask();
                this.showChartTypeWindow();
                return false;
            }
        }

        if (!this.measures.x && !forExport)
        {
            if (this.renderType !== "box_plot" && this.renderType !== "auto_plot")
            {
                if (this.autoColumnXName || (requiresX && this.autoColumnName))
                {
                    fk = LABKEY.FieldKey.fromString(this.autoColumnXName || this.autoColumnName);
                    measure = measureStore.findRecord('name', fk.name, 0, false, true, true);
                    if (measure)
                        this.setXAxisMeasure(measure, true);
                }

                if (requiresX && !this.measures.x)
                {
                    this.getEl().unmask();
                    this.showChartTypeWindow();
                    return false;
                }
            }
            else if (Ext4.isString(this.autoColumnYName))
            {
                measure = measureStore.findRecord('label', 'Study: Cohort', 0, false, true, true);
                if (measure)
                    this.setXAxisMeasure(measure, true);

                this.autoColumnYName = null;
            }
        }

        return true;
    },

    setYAxisMeasure : function(measure, suppressEvents)
    {
        if (measure)
        {
            this.measures.y = measure.data ? measure.data : measure;
            this.getChartLayoutPanel().onMeasuresChange(this.measures);
        }
    },

    setXAxisMeasure : function(measure, suppressEvents)
    {
        if (measure)
        {
            this.measures.x = measure.data ? measure.data : measure;
            this.getChartLayoutPanel().onMeasuresChange(this.measures);
        }
    },

    validateMeasuresExist: function(measureNames, requiredMeasureNames)
    {
        var store = this.getChartTypePanel().getStore(),
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
        if (!validation.success || validation.message != null)
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
                this.viewPanel.add(errorDiv);
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
        else if (!this.measures.x || this.isBoxPlot(this.renderType, this.measures.x.normalizedType))
            return 'box_plot';
        else if (this.measures.x && this.isScatterPlot(this.renderType, this.measures.x.normalizedType))
            return 'scatter_plot';

        return null;
    },

    clearChartPanel : function()
    {
        this.clearWarningText();
        this.viewPanel.removeAll();
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
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            // export the temp chart as a pdf with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'pdf', this.getChartConfig().labels.main);
            Ext4.getCmp(tempDivId).destroy();
        }
    },

    exportChartToPng : function()
    {
        var tempDivId = this.renderPlot(true);
        if (tempDivId)
        {
            // export the temp chart as a png with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempDivId).child('svg').dom, 'png', this.getChartConfig().labels.main);
            Ext4.getCmp(tempDivId).destroy();
        }
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

    viewPanelActivate : function()
    {
        this.updateChartTask.delay(500);
    },

    createFilterString : function(filterArray)
    {
        var filterParams = [];
        for (var i = 0; i < filterArray.length; i++){
            filterParams.push(filterArray[i].getURLParameterName() + '=' + filterArray[i].getURLParameterValue());
        }

        return filterParams.join('&');
    },

    onSelectRowsSuccess : function(response)
    {
        this.chartData = response;

        var queryFields = this.getQueryFields(this.chartData.metaData.fields);
        this.getChartTypePanel().loadQueryColumns(queryFields);

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

    getDefaultTitle : function()
    {
        if (this.defaultTitleFn)
            return this.defaultTitleFn(this.queryName, this.queryLabel, this.measures.y ? this.measures.y.label : null, this.measures.x ? this.measures.x.label : null);

        return this.queryLabel || this.queryName;
    },

    /**
     * used to determine if the new chart options are different from the
     * currently rendered options
     */
    isConfigurationChanged : function()
    {

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
    },

    requestRender : function(forExport)
    {
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

    onSaveBtnClicked : function(isSaveAs)
    {
        this.getSavePanel().setNoneThumbnail(this.getChartTypePanel().getImgUrl());
        this.getSavePanel().setSaveAs(isSaveAs);
        this.getSavePanel().setMainTitle(isSaveAs ? "Save as" : "Save");
        this.getSaveWindow().show();
    }
});
