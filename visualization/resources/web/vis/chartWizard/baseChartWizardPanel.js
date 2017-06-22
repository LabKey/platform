/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.ext4.BaseChartWizardPanel', {
    extend: 'Ext.panel.Panel',

    layout: 'card',
    border: false,
    bodyStyle: 'background-color: transparent;',

    renderTo: null,
    savedReportInfo: null,
    canEdit: false,
    canShare: false,
    isDeveloper: false,
    defaultNumberFormat: null,
    allowEditMode: false,
    editModeURL: null,
    baseUrl: null,
    schemaName: null,
    queryName: null,
    queryLabel: null,
    viewName: null,
    dataRegionName: null,
    renderType: null,
    autoColumnName: null,
    autoColumnYName: null,
    autoColumnXName: null,
    restrictColumnsEnabled: false,

    initComponent: function()
    {
        this.items = [];
        this.callParent();
        this.ensureQuerySettings();
    },

    ensureQuerySettings : function()
    {
        if (this.isNew() && (!this.schemaName || !this.queryName))
            this.getChartQueryWindow().show();
        else
            this.initializeChartPanels();
    },

    getChartQueryWindow : function()
    {
        if (!this.chartQueryWindow)
        {
            this.chartQueryWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                items: [this.getChartQueryPanel()]
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

                        this.initializeChartPanels();
                        this.getChartQueryWindow().hide();
                    }
                }
            });
        }

        return this.querySettingsPanel;
    },

    isNew : function()
    {
        return !Ext4.isDefined(this.savedReportInfo);
    },

    initializeChartPanels : function()
    {
        var autoColumnParams = Ext4.Array.intersect(['autoColumnXName', 'autoColumnYName', 'autoColumnName'], Object.keys(LABKEY.ActionURL.getParameters()));
        this.savedReportIsGenericChart = !this.isNew() && this.savedReportInfo.type == LABKEY.Query.Visualization.Type.GenericChart;
        this.savedReportIsTimeChart = !this.isNew() && this.savedReportInfo.type == LABKEY.Query.Visualization.Type.TimeChart;

        if (this.savedReportIsGenericChart)
            this.add(this.getGenericChartPanel());
        else if (this.savedReportIsTimeChart)
            this.add(this.getTimeChartPanel());
        else if (autoColumnParams.length > 0)
            this.applyInitialChartTypeSelection(this.getChartTypePanel());
        else if (this.isNew())
            this.getChartTypeWindow().show();
    },

    getGenericChartPanel : function(chartTypeWindow, chartTypePanel, initialSelection)
    {
        if (!this.genericChartPanel)
        {
            this.genericChartPanel = Ext4.create('LABKEY.ext4.GenericChartPanel', {
                height: 650,
                chartTypeWindow: chartTypeWindow,
                chartTypePanel: chartTypePanel,
                initialSelection: initialSelection,
                savedReportInfo: this.savedReportIsGenericChart ? this.savedReportInfo : null,
                canEdit: this.canEdit,
                canShare: this.canShare,
                isDeveloper: this.isDeveloper,
                defaultNumberFormat: this.defaultNumberFormat,
                allowEditMode: this.allowEditMode,
                editModeURL: this.editModeURL,

                baseUrl: this.baseUrl,
                schemaName: this.schemaName,
                queryName: this.queryName,
                queryLabel: this.queryLabel,
                viewName: this.viewName,
                dataRegionName: this.dataRegionName,

                renderType: this.renderType,
                autoColumnName  : this.autoColumnName,
                autoColumnYName  : this.autoColumnYName,
                autoColumnXName  : this.autoColumnXName,
                restrictColumnsEnabled: this.restrictColumnsEnabled
            });
        }

        return this.genericChartPanel;
    },

    getTimeChartPanel : function(chartTypeWindow, chartTypePanel, initialSelection)
    {
        if (!this.timeChartPanel)
        {
            this.timeChartPanel = Ext4.create('LABKEY.vis.TimeChartPanel', {
                height: 650,
                chartTypeWindow: chartTypeWindow,
                chartTypePanel: chartTypePanel,
                initialSelection: initialSelection,
                savedReportInfo: this.savedReportIsTimeChart ? this.savedReportInfo : null,
                canEdit: this.canEdit,
                canShare: this.canShare,
                isDeveloper: this.isDeveloper,
                defaultNumberFormat: this.defaultNumberFormat,
                allowEditMode: this.allowEditMode,
                editModeURL: this.editModeURL,

                baseUrl: this.baseUrl,
                schemaName: this.schemaName,
                queryName: this.queryName
            });
        }

        return this.timeChartPanel;
    },

    getChartTypeWindow : function()
    {
        if (!this.chartTypeWindow)
        {
            // if they cancel from this window, we don't have anything to show so just send them back
            this.getChartTypePanel().on('cancel', this.back, this, {single: true});

            // on apply from this window, we need to display the correct chart wizard panel and call its apply
            this.getChartTypePanel().on('apply', this.applyInitialChartTypeSelection, this, {single: true});

            this.chartTypeWindow = Ext4.create('LABKEY.vis.ChartWizardWindow', {
                items: [this.getChartTypePanel()],
                listeners: {
                    scope: this,
                    show: function()
                    {
                        // propagate the show event to the panel so it can stash the initial values
                        this.getChartTypePanel().fireEvent('show', this.getChartTypePanel());

                        // if we have an active item, mask it
                        var activeItem = this.getLayout().getActiveItem();
                        if (activeItem != null)
                            activeItem.getEl().mask();
                    },
                    hide: function()
                    {
                        // if we have an active item, unmask it
                        var activeItem = this.getLayout().getActiveItem();
                        if (activeItem != null)
                            activeItem.getEl().unmask();
                    },
                    close: function()
                    {
                        // Issue 28992: Hitting 'ESC' from initial Charting Dialog leaves user kind of stranded
                        if (this.getLayout().getActiveItem() == null) {
                            this.back();
                        }
                    }
                }
            });
        }

        return this.chartTypeWindow;
    },

    getChartTypePanel : function()
    {
        if (!this.chartTypePanel)
        {
            this.chartTypePanel = Ext4.create('LABKEY.vis.ChartTypePanel', {
                selectedType: this.renderType,
                chartTypesToHide: this.schemaName != 'study' ? ['time_chart'] : undefined,
                restrictColumnsEnabled: this.restrictColumnsEnabled,
                baseQueryKey: this.schemaName + '.' + this.queryName,
                studyQueryName: this.schemaName == 'study' ? this.queryName : null,
                schemaName: this.schemaName,
                queryName: this.queryName,
                viewName: this.viewName,
                dataRegionName: this.dataRegionName,
                parameters: LABKEY.Filter.getQueryParamsFromUrl(LABKEY.ActionURL.getParameter('filterUrl'), this.dataRegionName)
            });
        }

        return this.chartTypePanel;
    },

    applyInitialChartTypeSelection : function(panel, values)
    {
        // hide the window and remove the panel listeners
        this.getChartTypeWindow().hide();
        this.getChartTypePanel().removeListener('cancel', this.back, this);
        this.getChartTypePanel().removeListener('apply', this.applyInitialChartTypeSelection, this);

        // update the active chart type panel, adding if necessary
        this.updateActiveChartPanel(panel, values);

        // attach the apply listener so the active panel will be toggled accordingly
        this.getChartTypePanel().on('apply', this.updateActiveChartPanel, this);
    },

    updateActiveChartPanel : function(chartTypePanel, values)
    {
        var chartRenderPanel;
        if (Ext4.isObject(values) && values.type == 'time_chart')
            chartRenderPanel = this.getTimeChartPanel(this.getChartTypeWindow(), chartTypePanel, values);
        else
            chartRenderPanel = this.getGenericChartPanel(this.getChartTypeWindow(), chartTypePanel, values);

        // if we haven't yet added this render panel add it, then make it active in this card layout
        if (!this.contains(chartRenderPanel))
            this.add(chartRenderPanel);
        this.getLayout().setActiveItem(chartRenderPanel);
    },

    back : function()
    {
        window.history.back();
    }
});