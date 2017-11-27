/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.define('LABKEY.vis.TimeChartPanel', {
    extend : 'Ext.panel.Panel',

    cls : 'time-chart-panel',
    minWidth: 900,
    border : false,
    layout: 'border',
    bodyStyle: 'background-color: white;',

    renderType: 'time_chart',
    initialSelection: null,
    savedReportInfo: null,
    chartInfo: null,
    maxCharts: 30,
    dataLimit: 10000,
    measureMetadataRequestCounter: 0,

    initComponent : function()
    {
        this.SUBJECT = LABKEY.vis.TimeChartHelper.getStudySubjectInfo();

        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8); // issue 15372

        if (this.savedReportInfo != null)
            this.chartInfo = this.savedReportInfo.visualizationConfig;

        var chartInfoAxisArr = Ext4.isObject(this.chartInfo) ? this.chartInfo.axis : [],
            axisIndexMap = {
                x: LABKEY.vis.TimeChartHelper.getAxisIndex(chartInfoAxisArr, "x-axis"),
                left: LABKEY.vis.TimeChartHelper.getAxisIndex(chartInfoAxisArr, "y-axis", "left"),
                right: LABKEY.vis.TimeChartHelper.getAxisIndex(chartInfoAxisArr, "y-axis", "right")
            };

        // chartInfo will be all of the information needed to render the time chart (axis info and data)
        if (!Ext4.isObject(this.chartInfo))
        {
            this.savedChartInfo = null;
            this.chartInfo = this.getInitializedChartInfo();
            this.chartTypeOptions = this.getInitializedChartTypeOptions();
            this.chartTypeMeasures = [];
            this.chartLayoutOptions = {};
        }
        else
        {
            // If we have a saved chart we want to save a copy of config.chartInfo so we know if any chart settings
            // get changed. This way we can set the dirty bit. Ext.encode gives us a copy not a reference.
            this.savedChartInfo = Ext4.encode(this.chartInfo);
            this.loadOptionsFromConfig(this.chartInfo, axisIndexMap);
        }

        this.loaderFn = this.renderLineChart;  // default is to show the chart
        this.loaderName = 'renderLineChart';

        this.viewGridBtn = Ext4.create('Ext.button.Button', {text: "View Data", handler: function(){
            // hide/show the appropriate buttons in the top toolbar
            this.toggleButtonsForGrid(false);
            this.viewDataGrid();
        }, scope: this, disabled: true});

        this.viewChartBtn = Ext4.create('Ext.button.Button', {text: "View Chart(s)", handler: function(){
            // hide/show the appropriate buttons in the top toolbar
            this.toggleButtonsForGrid(true);

            // issue 21418: support for parameterized queries
            if (Ext4.isObject(this.parameters) && Object.keys(this.parameters).length > 0)
            {
                this.loaderFn = this.renderLineChart;
                this.loaderName = 'renderLineChart';
                this.getChartData();
            }
            else
            {
                this.renderLineChart();
            }

        }, scope: this, hidden: true});

        this.refreshChart = new Ext4.util.DelayedTask(function(){
            this.getChartData();
        }, this);

        // setup buttons for the charting options panels (items to be added to the toolbar)
        this.saveButton = Ext4.create('Ext.button.Button', {text: 'Save',
                        hidden: !this.canEdit || LABKEY.user.isGuest,
                        handler: function(btn){
                                this.getSavePanel().setSaveAs(false);
                                this.getSavePanel().setMainTitle("Save");
                                this.getSaveWindow().show();
                        }, scope: this});

        this.saveAsButton = Ext4.create('Ext.button.Button', {text: 'Save As',
                        hidden: !this.getSavePanel().isSavedReport() || LABKEY.user.isGuest,
                        handler: function(btn){
                                this.getSavePanel().setSaveAs(true);
                                this.getSavePanel().setMainTitle("Save As");
                            this.getSaveWindow().show();
                        }, scope: this});

        var params = LABKEY.ActionURL.getParameters();
        this.editMode = params.edit == "true" || !this.getSavePanel().isSavedReport();
        this.useRaphael = params.useRaphael != null ? params.useRaphael : false;

        // issue 21418: support for parameterized queries
        var urlQueryParams = LABKEY.Filter.getQueryParamsFromUrl(params['filterUrl']);
        this.parameters = this.chartInfo.parameters ? this.chartInfo.parameters : urlQueryParams;
        this.chartInfo.parameters = this.parameters;

        // if edit mode, then add the relevant buttons
        var toolbarButtons = [
            this.viewGridBtn,
            this.viewChartBtn,
            this.getHelpBtn()
        ];
        if (this.editMode)
        {
            toolbarButtons.push('->');
            toolbarButtons.push(this.getChartTypeBtn());
            toolbarButtons.push(this.getChartLayoutBtn());
            toolbarButtons.push(this.saveButton);
            toolbarButtons.push(this.saveAsButton);
        }
        else if (this.allowEditMode && this.editModeURL != null)
        {
            // add an "edit" button if the user is allowed to toggle to edit mode for this report
            toolbarButtons.push('->');
            toolbarButtons.push({
                xtype: 'button',
                text: 'Edit',
                scope: this,
                handler: function() {
                    window.location = this.editModeURL;
                }
            });
        }

        this.chart = Ext4.create('Ext.panel.Panel', {
            region: 'center',
            autoScroll: true,
            frame: false,
            minWidth: 650,
            dockedItems : [{
                xtype: 'toolbar',
                dock: 'top',
                style: 'border-color: #b4b4b4;',
                items: toolbarButtons
            }],
            items: []
        });

        this.items = [
            this.getFiltersPanel(),
            this.chart
        ];

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        this.callParent();

        // add a render listener load measure panel stores for saved report and to show chart type dialog if new chart
        this.on('render', function()
        {
            if (Ext4.isObject(this.savedReportInfo))
            {
                this.loadFiltersPanelValues();
                this.initializeMeasureDimensionPanels();
            }
            else
            {
                this.toggleOptionButtons(true);
                this.loadInitialSelection();
            }
        }, this);
    },

    getFiltersPanel : function()
    {
        if (!this.filtersPanel)
        {
            this.filtersPanel = Ext4.create('LABKEY.vis.ChartFilterPanel', {
                region: 'west',
                border: true,
                subject: this.chartInfo.subject,
                subjectSelection: this.chartInfo.chartSubjectSelection,
                listeners: {
                    scope: this,
                    chartDefinitionChanged: this.chartDefinitionChanged,
                    measureMetadataRequestPending: this.measureMetadataRequestPending,
                    measureMetadataRequestComplete: function()
                    {
                        this.chartFilterPanelLoadComplete = true;
                        this.measureMetadataRequestComplete();
                    },
                    expand: function ()
                    {
                        var hasGroupsSelected = this.chartLayoutOptions.general.chartSubjectSelection == 'groups';
                        this.filtersPanel.setOptionsForGroupLayout(hasGroupsSelected);
                    },
                    switchToGroupLayout: function ()
                    {
                        this.filtersPanel.setOptionsForGroupLayout(true);
                        this.getChartLayoutPanel().onChartSubjectSelectionChange(true);
                        this.ensureChartLayoutOptions();
                        this.chartDefinitionChanged(true);
                    }
                }
            });
        }

        return this.filtersPanel;
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
                reportInfo: this.savedReportInfo,
                canEdit: this.canEdit,
                canShare: this.canShare,
                listeners: {
                    scope: this,
                    closeOptionsWindow: function()
                    {
                        this.getSaveWindow().close()
                    },
                    saveChart: this.saveChart
                }
            });
        }

        return this.savePanel;
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
                        text: 'Time Charts',
                        iconCls: 'fa fa-line-chart',
                        hrefTarget: '_blank',
                        href: 'https://www.labkey.org/wiki/home/Documentation/page.view?name=timeChart'
                    }]
                }
            });
        }

        return this.helpBtn;
    },

    getChartTypeBtn : function()
    {
        if (!this.chartTypeBtn)
        {
            this.chartTypeBtn = Ext4.create('Ext.button.Button', {
                text: 'Chart Type',
                scope: this,
                handler: this.showChartTypeWindow
            });
        }

        return this.chartTypeBtn;
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
            var measures = {
                    x: this.chartTypeOptions,
                    y: this.chartTypeMeasures
                },
                baseSchemaName = measures.y.length > 0 ? measures.y[0].schemaName : this.getFilterSchemaName(),
                baseQueryName = measures.y.length > 0 ? measures.y[0].queryName : this.getFilterQueryName();

            this.chartTypePanel = Ext4.create('LABKEY.vis.ChartTypePanel', {
                selectedType: 'time_chart',
                hideNonSelectedType: true,
                selectedFields: measures,
                baseQueryKey: baseSchemaName + '.' + baseQueryName,
                studyQueryName: baseSchemaName == 'study' ? baseQueryName : null
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

    showChartTypeWindow : function()
    {
        // make sure the chartTypePanel is shown in the window
        if (this.getChartTypeWindow().items.items.length == 0)
            this.getChartTypeWindow().add(this.getChartTypePanel());

        this.getChartTypeWindow().show();
    },

    closeChartTypeWindow : function(panel)
    {
        if (this.getChartTypeWindow().isVisible())
            this.getChartTypeWindow().hide();
    },

    applyChartTypeSelection : function(panel, values)
    {
        // close the window and clear any previous charts
        this.closeChartTypeWindow();
        this.clearChartPanel();

        // only apply the values for the applicable chart type
        if (Ext4.isObject(values) && values.type != 'time_chart')
            return;

        this.maskAndRemoveCharts();
        this.loadFiltersPanelValues();

        this.chartTypeOptions = values.altValues.x;
        this.chartTypeMeasures = !Ext4.isArray(values.fields.y) ? [values.fields.y] : values.fields.y;

        // make sure that each measure has a y-axis side, default to left
        Ext4.each(this.chartTypeMeasures, function(measure){
            if (!Ext4.isString(measure.yAxis))
                measure.yAxis = 'left';
        }, this);

        this.measureSelectionChange();
        this.ensureChartLayoutOptions();
        this.chartDefinitionChanged(true);
    },

    getChartLayoutBtn : function()
    {
        if (!this.chartLayoutBtn)
        {
            this.chartLayoutBtn = Ext4.create('Ext.button.Button', {
                text: 'Chart Layout',
                scope: this,
                handler: function(){
                    this.getChartLayoutWindow().show();
                }
            });
        }

        return this.chartLayoutBtn;
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
                this.getChartLayoutPanel().fireEvent('show', this.getChartLayoutPanel(), this.getSelectedChartTypeData(), this.chartTypeMeasures);
            }, this);
        }

        return this.chartLayoutWindow;
    },

    getChartLayoutPanel : function()
    {
        if (!this.chartLayoutPanel)
        {
            this.chartLayoutPanel = Ext4.create('LABKEY.vis.ChartLayoutPanel', {
                options: this.chartLayoutOptions,
                isDeveloper: this.isDeveloper,
                renderType: this.renderType,
                multipleCharts: this.chartInfo.chartLayout != 'single',
                defaultChartLabel: this.getDefaultTitle(),
                defaultLineWidth: 3,
                isSavedReport: !this.isNew(),
                listeners: {
                    scope: this,
                    cancel: function(panel)
                    {
                        this.getChartLayoutWindow().hide();
                    },
                    apply: function(panel, values, requiresDataRefresh)
                    {
                        var newChartSubjectSelection = values.general.chartSubjectSelection;
                        if (this.chartLayoutOptions.general.chartSubjectSelection != newChartSubjectSelection)
                            this.getFiltersPanel().setOptionsForGroupLayout(newChartSubjectSelection == 'groups');

                        this.updateMeasureDimensionPanels();
                        this.ensureChartLayoutOptions();
                        this.chartDefinitionChanged(requiresDataRefresh);
                        this.getChartLayoutWindow().hide();
                    }
                }
            });
        }

        return this.chartLayoutPanel;
    },

    initializeMeasureDimensionPanels : function()
    {
        Ext4.each(this.chartInfo.measures, function (initialMeasure)
        {
            this.createYMeasureField(initialMeasure);

            if (Ext4.isObject(initialMeasure.dimension) && Ext4.isString(initialMeasure.dimension.name))
                this.createDimensionFilterPanel(initialMeasure.dimension, false);
        }, this);

        // initialize the Chart Layout x-axis range/scale display
        var xAxisLayoutPanel = this.getChartLayoutPanel().getOptionPanelByName('x');
        if (Ext4.isDefined(xAxisLayoutPanel))
        {
            xAxisLayoutPanel.setRangeOptionVisible(this.chartTypeOptions.time != 'visit');
            xAxisLayoutPanel.setScaleTypeOptionVisible(false);
        }
    },

    updateMeasureDimensionPanels : function()
    {
        if (this.editMode)
        {
            var existingYMeasureFieldMap = this.getYMeasureFieldMap(),
                existingDimensionFilterPanelMap = this.getDimensionFilterPanelMap(),
                inUseYMeasureFieldAliases = [],
                inUseDimensionFilterPanelAliases = [];

            Ext4.each(this.chartTypeMeasures, function(measure)
            {
                // if this is a new measure, then create a new YMeasureField,
                // otherwise check if we need to add a new DimensionFilterPanel
                var alias = LABKEY.vis.TimeChartHelper.getMeasureAlias(measure),
                    yMeasureField = existingYMeasureFieldMap[alias];

                if (!Ext4.isDefined(yMeasureField))
                {
                    this.createYMeasureField({measure: measure});
                }
                else
                {
                    // check if the measure side has change so the field cmp needs to be moved
                    this.moveYMeasureField(yMeasureField, measure.yAxis);

                    // create any dimension filter panels as selected for this measure
                    var dimension = yMeasureField.getValues().dimension;
                    if (Ext4.isObject(dimension) && Ext4.isString(dimension.name))
                    {
                        this.createDimensionFilterPanel(dimension, true);
                        inUseDimensionFilterPanelAliases.push(LABKEY.vis.TimeChartHelper.getMeasureAlias(dimension));
                    }
                }

                inUseYMeasureFieldAliases.push(alias);
            }, this);

            // delete unused YMeasureFields
            var yMeasureFieldsToRemove = Ext4.Array.difference(Object.keys(existingYMeasureFieldMap), inUseYMeasureFieldAliases);
            Ext4.each(yMeasureFieldsToRemove, function(alias){
                existingYMeasureFieldMap[alias].destroy();
            }, this);

            // delete unused DimensionFilterPanels
            var dimensionFilterPanelsToRemove = Ext4.Array.difference(Object.keys(existingDimensionFilterPanelMap), inUseDimensionFilterPanelAliases);
            Ext4.each(dimensionFilterPanelsToRemove, function(alias){
                var isExpanded = !existingDimensionFilterPanelMap[alias].collapsed;
                if (isExpanded)
                    this.getFiltersPanel().expandDefaultPanel();

                existingDimensionFilterPanelMap[alias].destroy();
            }, this);
        }
    },

    createYMeasureField : function(measureDimension)
    {
        var existingYMeasureFieldMap = this.getYMeasureFieldMap(),
            alias = LABKEY.vis.TimeChartHelper.getMeasureAlias(measureDimension.measure);

        // verify that a y measure field doesn't already exist for this measure column selection
        if (!Ext4.isDefined(existingYMeasureFieldMap[alias]))
        {
            var side = measureDimension.measure.yAxis == 'right' ? 'yRight' : 'y',
                yAxisOptionPanel = this.getChartLayoutPanel().getOptionPanelByName(side);

            if (yAxisOptionPanel != null)
            {
                var yMeasureFields = Ext4.create('LABKEY.vis.TimeChartYMeasureField', {
                    selection: measureDimension,
                    listeners: {
                        scope: this,
                        measureMetadataRequestPending: this.measureMetadataRequestPending,
                        measureMetadataRequestComplete: this.measureMetadataRequestComplete
                    }
                });

                yAxisOptionPanel.add(yMeasureFields);
            }
        }
    },

    moveYMeasureField : function(yMeasureField, newSide)
    {
        var yAxisSide = newSide == 'right' ? 'yRight' : 'y',
            otherYAxisSide = yAxisSide == 'y' ? 'yRight' : 'y',
            yAxisOptionPanel = this.getChartLayoutPanel().getOptionPanelByName(yAxisSide);

        // if the yAxisSide panel doesn't contain the field, move it
        if (!yAxisOptionPanel.contains(yMeasureField))
        {
            this.getChartLayoutPanel().getOptionPanelByName(otherYAxisSide).remove(yMeasureField, false);
            yAxisOptionPanel.add(yMeasureField);
        }
    },

    createDimensionFilterPanel : function(dimension, expandOnCreate)
    {
        var existingDimensionFilterPanelMap = this.getDimensionFilterPanelMap(),
            alias = LABKEY.vis.TimeChartHelper.getMeasureAlias(dimension);

        // verify that a dimension filter panel doesn't already exist for this dimension column selection
        if (!Ext4.isDefined(existingDimensionFilterPanelMap[alias]))
        {
            var dimensionFilterPanel = Ext4.create('LABKEY.vis.DimensionFilterPanel', {
                dimension: dimension,
                listeners: {
                    scope: this,
                    measureMetadataRequestPending: this.measureMetadataRequestPending,
                    measureMetadataRequestComplete: this.measureMetadataRequestComplete
                }
            });

            this.getFiltersPanel().add(dimensionFilterPanel);

            if (expandOnCreate)
                dimensionFilterPanel.expand();
        }
    },

    getYMeasureFieldMap : function()
    {
        var aliasMap = {};
        Ext4.each(this.getChartLayoutPanel().query('.timechartymeasurefield'), function(yMeasureField)
        {
            aliasMap[yMeasureField.getMeasureAlias()] = yMeasureField;
        }, this);
        return aliasMap;
    },

    getYMeasureFieldValuesByAlias : function(measureAlias)
    {
        var yMeasureFieldAliasMap = this.getYMeasureFieldMap();

        if (Ext4.isDefined(yMeasureFieldAliasMap[measureAlias]))
            return yMeasureFieldAliasMap[measureAlias].getValues();

        return {};
    },

    getDimensionFilterPanelMap : function()
    {
        var aliasMap = {};
        Ext4.each(this.getFiltersPanel().query('.dimensionfilerpanel'), function(dimensionPanel)
        {
            aliasMap[dimensionPanel.getDimensionAlias()] = dimensionPanel;
        }, this);
        return aliasMap;
    },

    getDimensionValuesByAlias : function(dimensionAlias)
    {
        var dimensionFilterPanelMap = this.getDimensionFilterPanelMap();

        if (Ext4.isDefined(dimensionFilterPanelMap[dimensionAlias]))
            return dimensionFilterPanelMap[dimensionAlias].getValues();

        return [];
    },

    isNew : function()
    {
        return this.savedChartInfo == null;
    },

    loadOptionsFromConfig : function(chartConfig, axisIndexMap)
    {
        this.chartLayoutOptions = {};
        this.chartTypeOptions = {};
        this.chartTypeMeasures = [];

        if (Ext4.isObject(chartConfig))
        {
            var firstMeasure = null;
            Ext4.each(chartConfig.measures, function(configMeasure){
                this.chartTypeMeasures.push(Ext4.clone(configMeasure.measure));

                if (firstMeasure == null)
                    firstMeasure = configMeasure;
            }, this);

            if (firstMeasure != null)
            {
                this.chartTypeOptions = {};
                this.chartTypeOptions.time = firstMeasure.time;
                if (this.chartTypeOptions.time == 'date' && Ext4.isObject(firstMeasure.dateOptions))
                {
                    this.chartTypeOptions.interval = firstMeasure.dateOptions.interval;
                    this.chartTypeOptions.zeroDateCol = firstMeasure.dateOptions.zeroDateCol;
                }
            }
            else
            {
                this.chartTypeOptions = Ext4.clone(this.getInitializedChartTypeOptions());
            }

            this.chartLayoutOptions.general = {};

            if (Ext4.isString(chartConfig.title))
                this.chartLayoutOptions.general.label = chartConfig.title;
            else
                this.chartLayoutOptions.general.label = this.getDefaultTitle();

            if (Ext4.isNumber(chartConfig.height))
                this.chartLayoutOptions.general.height = chartConfig.height;

            if (Ext4.isNumber(chartConfig.width))
                this.chartLayoutOptions.general.width = chartConfig.width;

            if (Ext4.isNumber(chartConfig.lineWidth))
                this.chartLayoutOptions.general.lineWidth = chartConfig.lineWidth;

            if (Ext4.isBoolean(chartConfig.hideDataPoints))
                this.chartLayoutOptions.general.hideDataPoints = chartConfig.hideDataPoints;

            if (Ext4.isString(chartConfig.chartLayout))
                this.chartLayoutOptions.general.chartLayout = chartConfig.chartLayout;

            if (Ext4.isString(chartConfig.chartSubjectSelection))
                this.chartLayoutOptions.general.chartSubjectSelection = chartConfig.chartSubjectSelection;

            if (Ext4.isBoolean(chartConfig.displayIndividual))
                this.chartLayoutOptions.general.displayIndividual = chartConfig.displayIndividual;

            if (Ext4.isBoolean(chartConfig.displayAggregate))
                this.chartLayoutOptions.general.displayAggregate = chartConfig.displayAggregate;

            if (Ext4.isString(chartConfig.errorBars))
                this.chartLayoutOptions.general.errorBars = chartConfig.errorBars;

            if (axisIndexMap.x > -1)
            {
                var axisInfo = chartConfig.axis[axisIndexMap.x];
                this.chartLayoutOptions.x = {};
                this.chartLayoutOptions.x.label = axisInfo.label;
                this.chartLayoutOptions.x.range = Ext4.clone(axisInfo.range);
            }

            if (axisIndexMap.left > -1)
            {
                var axisInfo = chartConfig.axis[axisIndexMap.left];
                this.chartLayoutOptions.y = {};
                this.chartLayoutOptions.y.label = axisInfo.label;
                this.chartLayoutOptions.y.trans = axisInfo.scale;
                this.chartLayoutOptions.y.range = Ext4.clone(axisInfo.range);
            }

            if (axisIndexMap.right > -1)
            {
                var axisInfo = chartConfig.axis[axisIndexMap.right];
                this.chartLayoutOptions.yRight = {};
                this.chartLayoutOptions.yRight.label = axisInfo.label;
                this.chartLayoutOptions.yRight.trans = axisInfo.scale;
                this.chartLayoutOptions.yRight.range = Ext4.clone(axisInfo.range);
            }

            this.chartLayoutOptions.developer = {};
            if (Ext4.isDefined(chartConfig.pointClickFn))
                this.chartLayoutOptions.developer.pointClickFn = chartConfig.pointClickFn;
        }
    },

    ensureChartLayoutOptions : function()
    {
        // Make sure that we have the latest chart layout panel values.
        // This will get the initial default values if the user has not yet opened the chart layout dialog.
        // This will also preserve the developer pointClickFn if the user is not a developer.
        Ext4.apply(this.chartLayoutOptions, this.getChartLayoutPanel().getValues());
    },

    getSelectedChartTypeData : function()
    {
        if (!this.selectedTypeData)
        {
            Ext4.each(LABKEY.vis.GenericChartHelper.getRenderTypes(), function(renderType)
            {
                if (renderType.name == this.renderType)
                {
                    this.selectedTypeData = renderType;
                    return false; // break
                }
            }, this);
        }

        return this.selectedTypeData;
    },

    getDefaultTitle : function()
    {
        return LABKEY.vis.GenericChartHelper.getTitleFromMeasures(this.renderType, {y: this.chartTypeMeasures});
    },

    resizeCharts : function(){
        // only call loader if the data object is available and the loader equals renderLineChart
        if (this.chartData && (this.chartData.individual || this.chartData.aggregate) && this.loaderName == 'renderLineChart')
            this.loaderFn();
    },

    resizeToViewport : function() {
        console.warn('DEPRECATED: As of Release 17.3 ' + this.$className + '.resizeToViewport() is no longer supported.');
    },

    getFilterSchemaName : function()
    {
        return this.schemaName || LABKEY.ActionURL.getParameter('schemaName');
    },

    getFilterQueryName : function()
    {
        return this.queryName || LABKEY.ActionURL.getParameter('queryName');
    },

    getFilterQuery :  function()
    {
        var schemaName = this.getFilterSchemaName();
        var queryName = this.getFilterQueryName();
        if (schemaName && queryName)
            return schemaName + "." + queryName;
        else
            return undefined;
    },

    loadFiltersPanelValues : function()
    {
        if (!this.chartFilterPanelLoadComplete)
        {
            this.getFiltersPanel().getSubjectValues();
            this.getFiltersPanel().getGroupValues();
        }
    },

    loadInitialSelection : function()
    {
        // if we have an initial selection from the BaseChartWizardPanel apply it
        if (Ext4.isDefined(this.initialSelection))
        {
            this.applyChartTypeSelection(this.getChartTypePanel(), this.initialSelection);
            // clear the initial selection object so it isn't loaded again
            this.initialSelection = undefined;
        }
    },

    measureSelectionChange: function()
    {
        this.updateMeasureDimensionPanels();

        var measures = {x: this.chartTypeOptions, y: this.chartTypeMeasures};
        this.getChartLayoutPanel().onMeasuresChange(measures, this.renderType);

        // if all of the measures have been removed, disable any non-relevant elements
        if (measures.y.length == 0)
            this.disableNonMeasureOptionButtons();
        else
            this.toggleOptionButtons(false);
    },

    toggleOptionButtons: function(disable){
        this.saveButton.setDisabled(disable);
        this.saveAsButton.setDisabled(disable);
        this.getChartLayoutBtn().setDisabled(disable);
    },

    toggleSaveButtons: function(disable)
    {
        this.saveButton.setDisabled(disable);
        this.saveAsButton.setDisabled(disable);
    },

    toggleButtonsForGrid: function(show)
    {
        if (show && this.buttonsToShow)
        {
            this.viewGridBtn.show();
            this.viewChartBtn.hide();

            while (this.buttonsToShow.length > 0)
            {
                var btn = this.buttonsToShow.pop();
                btn.show();
            }
        }
        else if (!show)
        {
            this.viewGridBtn.hide();
            this.viewChartBtn.disable();
            this.viewChartBtn.show();

            this.buttonsToShow = [];
            if (!this.getChartTypeBtn().isHidden())
                this.buttonsToShow.push(this.getChartTypeBtn().hide());
            if (!this.getChartLayoutBtn().isHidden())
                this.buttonsToShow.push(this.getChartLayoutBtn().hide());
        }
    },

    disableNonMeasureOptionButtons: function(){
        this.viewGridBtn.disable();
        this.viewChartBtn.disable();
        this.getChartLayoutBtn().disable();
    },

    displayErrorMsg: function(msg){
        this.toggleOptionButtons(true);
        this.getChartTypeBtn().disable();
        this.getFiltersPanel().disable();
        this.clearChartPanel(msg);
    },

    isDirty : function() {
        return !LABKEY.user.isGuest && this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
        LABKEY.Utils.signalWebDriverTest("timeChartDirty", value);
    },

    chartDefinitionChanged: function(requiresDataRefresh) {
        if (requiresDataRefresh)
            this.refreshChart.delay(100);
        else
            this.loaderFn();
    },

    measureMetadataRequestPending:  function() {
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        this.measureMetadataRequestCounter--;
        this.refreshChart.delay(100);
    },

    getChartData: function()
    {
        this.maskAndRemoveCharts();

        // if all requests are complete, then we are ready to get the chart data
        if (this.measureMetadataRequestCounter > 0)
            return;

        // Clear previous chart data, number formats, etc.
        this.chartData = undefined;

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        // validate the chart config to display either warning or error messages
        var validation = LABKEY.vis.TimeChartHelper.validateChartConfig(this.chartInfo);
        if (!validation.success)
        {
            this.clearChartPanel(validation.message);
            this.viewGridBtn.disable();
            this.viewChartBtn.disable();
            this.toggleSaveButtons(true);
            return;
        }
        else if (validation.message != null && validation.message.length > 0)
        {
            this.addWarningText(validation.message);
        }

        var queryConfig = {
            nounSingular: this.SUBJECT.nounSingular,
            chartInfo: this.chartInfo,
            dataLimit: this.dataLimit,
            defaultNumberFormat: this.defaultNumberFormat,
            scope: this
        };

        // on successful retrieval of data, we mark dirty, store the view data grid info, and call the loader function
        queryConfig.success = function(data) {
            this.chartData = data;

            // set the dirty state for non-saved time chart once data is requested
            if (this.canEdit && !this.getSavePanel().isSavedReport())
                this.markDirty(true);

            // store the temp schema name, query name, etc. for the data grid
            this.tempGridInfo = {};
            if (this.chartData.aggregate)
            {
                this.tempGridInfo = {
                    schema: this.chartData.aggregate.schemaName,
                    query: this.chartData.aggregate.queryName,
                    sortCols : ["GroupingOrder", "UniqueId"]
                };
            }
            else
            {
                this.tempGridInfo = {
                    schema: this.chartData.individual.schemaName,
                    query: this.chartData.individual.queryName,
                    subjectCol: LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.SUBJECT.columnName),
                    sortCols: [LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.SUBJECT.columnName),
                        (
                            this.chartTypeOptions.time == 'date'
                            ? LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.chartInfo.measures[0].dateOptions.dateCol.name)
                            : LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.SUBJECT.nounSingular + "Visit/Visit/DisplayOrder")
                        )
                    ]
                };
            }

            // now that we have the temp grid info, enable the View Data button
            this.viewGridBtn.enable();

            this.loaderFn();
        };

        queryConfig.failure = function(info, response) {
            var exception = response && response.exception ? response.exception : info.exception;
            this.clearChartPanel("Error: " + exception);
            this.disableNonMeasureOptionButtons();
        };

        LABKEY.vis.TimeChartHelper.getChartData(queryConfig);
    },

    getSimplifiedConfig: function(config)
    {
        var defaultConfig = this.getInitializedChartInfo();

        // Here we generate a config that is similar, but strips out info that isnt neccessary.
        // We use this to compare two configs to see if the user made any changes to the chart.
        var simplified = {};
        simplified.height = config.height;
        simplified.width = config.width;
        simplified.chartLayout = config.chartLayout;
        simplified.chartSubjectSelection = config.chartSubjectSelection;
        simplified.displayAggregate = config.displayAggregate;
        simplified.displayIndividual = config.displayIndividual;
        simplified.errorBars = config.errorBars ? config.errorBars : defaultConfig.errorBars;
        simplified.aggregateType = config.aggregateType ? config.aggregateType : defaultConfig.aggregateType;
        simplified.filterUrl = config.filterUrl;
        simplified.parameters = config.parameters ? config.parameters : defaultConfig.parameters;
        simplified.title = config.title;
        simplified.hideDataPoints = config.hideDataPoints ? config.hideDataPoints : defaultConfig.hideDataPoints;
        simplified.lineWidth = config.lineWidth ? config.lineWidth : defaultConfig.lineWidth;
        simplified.pointClickFn = config.pointClickFn ? config.pointClickFn : defaultConfig.pointClickFn;

        // compare the relevant axis information
        simplified.axis = [];
        for (var i = 0; i < config.axis.length; i++)
        {
            var currentAxis = config.axis[i];
            var tempAxis = {label: currentAxis.label, name: currentAxis.name};
            tempAxis.range = {type: currentAxis.range.type};
            if (currentAxis.range.type == "manual")
            {
                tempAxis.range.min = currentAxis.range.min;
                tempAxis.range.max = currentAxis.range.max;
            }
            if (currentAxis.scale && currentAxis.name != 'x-axis') tempAxis.scale = currentAxis.scale;
            if (currentAxis.side) tempAxis.side = currentAxis.side;
            simplified.axis.push(tempAxis);
        }

        // compare subject information (this is the standard set for both participant and group selections)
        simplified.subject = this.getSchemaQueryInfo(config.subject);
        // compare groups by labels and participantIds (not id and created date)
        if (config.subject.groups)
        {
            simplified.subject.groups = [];
            for(var i = 0; i < config.subject.groups.length; i++)
                simplified.subject.groups.push({
                    label: config.subject.groups[i].label,
                    participantIds: config.subject.groups[i].participantIds.sort()
                });
        }
        else
        {
            simplified.subject.values = config.subject.values;
        }

        simplified.measures = [];
        for (var i = 0; i < config.measures.length; i++)
        {
            var currentMeasure = config.measures[i];
            var tempMeasure = {time: currentMeasure.time ? currentMeasure.time : "date"};

            tempMeasure.measure = this.getSchemaQueryInfo(currentMeasure.measure);
            if (currentMeasure.measure.aggregate) tempMeasure.measure.aggregate = currentMeasure.measure.aggregate;
            if (currentMeasure.measure.yAxis) tempMeasure.measure.yAxis = currentMeasure.measure.yAxis;

            if (currentMeasure.dimension)
            {
                tempMeasure.dimension = this.getSchemaQueryInfo(currentMeasure.dimension);
                tempMeasure.dimension.values = currentMeasure.dimension.values; 
            }

            if (currentMeasure.dateOptions)
            {
                tempMeasure.dateOptions = {interval: currentMeasure.dateOptions.interval};
                tempMeasure.dateOptions.dateCol = this.getSchemaQueryInfo(currentMeasure.dateOptions.dateCol);
                tempMeasure.dateOptions.zeroDateCol = this.getSchemaQueryInfo(currentMeasure.dateOptions.zeroDateCol);
            }

            simplified.measures.push(tempMeasure);
        }

        return Ext4.encode(simplified);
    },

    getSchemaQueryInfo: function(obj)
    {
        return {name: obj.name, queryName: obj.queryName, schemaName: obj.schemaName};
    },

    renderLineChart: function()
    {
        this.maskAndRemoveCharts();

        // add a delay to make sure the loading mask shows before next charts start to render
        new Ext4.util.DelayedTask(function(){
            this._renderLineChart();
        }, this).delay(100);
    },

    _renderLineChart: function()
    {
        // if the chart data hasn't loaded yet, return without rendering
        if (!Ext4.isObject(this.chartData))
            return;

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        if (!this.isNew())
        {
            if (this.getSimplifiedConfig(Ext4.decode(this.savedChartInfo)) == this.getSimplifiedConfig(this.chartInfo))
            {
                this.markDirty(false);
            }
            else
            {
                //Don't mark dirty if the user can't edit the report or if this is the view mode, that's just mean.
                if (this.canEdit && this.editMode)
                {
                    this.markDirty(true);
                }
            }
        }

        // validate the chart config to display either warning or error messages
        var validation = LABKEY.vis.TimeChartHelper.validateChartConfig(this.chartInfo);
        if (!validation.success)
        {
            this.clearChartPanel(validation.message);
            this.viewGridBtn.disable();
            this.viewChartBtn.disable();
            this.toggleSaveButtons(true);
            return;
        }
        else if (validation.message != null && validation.message.length > 0)
        {
            this.addWarningText(validation.message);
        }

        this.addURLFilterPanel();

        this.loaderFn = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // one series per y-axis subject/measure/dimensionvalue combination
        var seriesList = LABKEY.vis.TimeChartHelper.generateSeriesList(this.chartInfo.measures);

        // validate the chart data to either display warnings or error messages
        validation = LABKEY.vis.TimeChartHelper.validateChartData(this.chartData, this.chartInfo, seriesList, this.dataLimit);
        if (!validation.success)
        {
            this.clearChartPanel(validation.message);
            this.viewGridBtn.disable();
            this.viewChartBtn.disable();
            this.toggleSaveButtons(true);
            return;
        }
        else if (validation.message != null && validation.message.length > 0)
        {
            this.addWarningText(validation.message);
        }

        // issue 17132: only apply clipRect if the user set a custom axis range
        var applyClipRect = LABKEY.vis.TimeChartHelper.generateApplyClipRect(this.chartInfo);

        LABKEY.vis.TimeChartHelper.generateAcrossChartAxisRanges(this.chartInfo, this.chartData, seriesList, this.SUBJECT.nounSingular);

        LABKEY.vis.TimeChartHelper.generateNumberFormats(this.chartInfo, this.chartData, this.defaultNumberFormat);

        // remove any existing charts
        this.chart.removeAll();
        this.chart.removeListener('resize', this.resizeCharts);
        this.plotConfigInfoArr = [];
        var charts = [];

        this.toggleSaveButtons(false);

        this.plotConfigInfoArr = LABKEY.vis.TimeChartHelper.generatePlotConfigs(this.chartInfo, this.chartData, seriesList, applyClipRect, this.maxCharts, this.SUBJECT.columnName);

        // warn if the max number of charts has been exceeded
        if (this.plotConfigInfoArr.length >= this.maxCharts)
            this.addWarningText("Only showing the first " + this.maxCharts + " charts.");

        for (var configIndex = 0; configIndex < this.plotConfigInfoArr.length; configIndex++)
        {
            var newChart = this.generatePlot(configIndex, this.plotConfigInfoArr[configIndex].height || (this.plotConfigInfoArr.length > 1 ? 380 : 600));
            charts.push(newChart);
        }

        // show warning message, if there is one
        if (this.warningText.length > 0)
        {
            this.chart.insert(0, Ext4.create('Ext.panel.Panel', {
                border: false,
                html : "<div style='padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;'>" + this.warningText + "</div>"
            }));
        }

        // if in edit mode, pass the svg for the first chart component to the save options panel for use in the thumbnail preview
        this.chartThumbnailSvgStr = null;
        var firstChartEl = this.chart.down('container[cls~=chart-render-div]');
        if (this.editMode && this.supportedBrowser && firstChartEl != null)
        {
            this.chartThumbnailSvgStr = LABKEY.vis.SVGConverter.svgToStr(firstChartEl.getEl().child('svg').dom);
            this.getSavePanel().updateCurrentChartThumbnail(this.chartThumbnailSvgStr, firstChartEl.getEl().getSize());
        }

        this.unmaskPanel();

        this.chart.addListener('resize', this.resizeCharts, this, {buffer: 500});
    },

    addURLFilterPanel : function()
    {
        var filterDescription;
        if (Ext4.isObject(this.chartData) && this.chartData.individual)
            filterDescription = this.chartData.individual.filterDescription;
        if (Ext4.isObject(this.chartData) && this.chartData.aggregate)
            filterDescription = this.chartData.aggregate.filterDescription;

        // create and add the URL filter panel to the filters panel or remove it if the filter has been removed
        var hasFilter = Ext4.isDefined(filterDescription),
            hasUrlFilterPanel = Ext4.isDefined(this.urlFilterPanel);

        if (hasFilter && !hasUrlFilterPanel)
        {
            filterDescription = filterDescription.substring(1, filterDescription.length-1);
            var filterTxtArr = filterDescription.split(') AND ('),
                html = 'The following filters are being applied to this chart:<br/>'
                    + '<ul><li>' + filterTxtArr.join('</li><li>') + '</li></ul>';

            this.urlFilterPanel = Ext4.create('Ext.panel.Panel', {
                title: 'Base Query Filter',
                border: false,
                items: [Ext4.create('Ext.panel.Panel', {
                    header: false,
                    padding: 5,
                    border: false,
                    cls: 'query-filter',
                    items: [{xtype: 'box', html: html}],
                    buttons: [{
                        text: 'Remove Filter',
                        scope: this,
                        handler: function()
                        {
                            this.chartInfo.filterUrl = undefined;
                            this.chartInfo.filterQuery = undefined;
                            this.chartDefinitionChanged(true);
                        }
                    }],
                    buttonAlign: 'left'
                })]
            });

            this.getFiltersPanel().insert(0, this.urlFilterPanel);
        }
        else if (!hasFilter && hasUrlFilterPanel)
        {
            var isExpanded = !this.urlFilterPanel.collapsed;
            if (isExpanded)
                this.getFiltersPanel().expandDefaultPanel();

            this.urlFilterPanel.destroy();
        }
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
            this.exportScriptPanel = Ext4.create('LABKEY.vis.TimeChartScriptPanel', {
                width: Math.max(this.chart.getWidth() - 100, 800),
                listeners: {
                    scope: this,
                    closeOptionsWindow: function() {
                        this.getExportScriptWindow().hide();
                    }
                }
            });
        }

        return this.exportScriptPanel;
    },

    exportChartToScript : function()
    {
        var chartInfo = this.getChartInfoFromOptionPanels();
        var templateConfig = { chartConfig: chartInfo };
        this.getExportScriptPanel().setScriptValue(templateConfig);
        this.getExportScriptWindow().show();
    },

    generatePlot: function(configIndex, chartHeight){
        // This function generates a plot config and renders a plot for given data.
        // Should be used in per_subject, single, per_measure, and per_group
        var mainTitle = this.plotConfigInfoArr[configIndex].title;
        var subtitle = this.plotConfigInfoArr[configIndex].subtitle;
        var seriesList = this.plotConfigInfoArr[configIndex].series;
        var individualData = this.plotConfigInfoArr[configIndex].individualData;
        var aggregateData = this.plotConfigInfoArr[configIndex].aggregateData;
        var applyClipRect = this.plotConfigInfoArr[configIndex].applyClipRect;

        var newChartDiv = Ext4.create('Ext.container.Container', {
            cls: 'chart-render-div',
            autoEl: {tag: 'div'},
            mainTitle: mainTitle + (subtitle ? ': ' + subtitle : '')
        });
        this.chart.add(newChartDiv);

        var individualColumnAliases = this.chartData.individual ? this.chartData.individual.columnAliases : null;
        var aggregateColumnAliases = this.chartData.aggregate ? this.chartData.aggregate.columnAliases : null;
        var intervalKey = LABKEY.vis.TimeChartHelper.generateIntervalKey(this.chartInfo, individualColumnAliases, aggregateColumnAliases, this.SUBJECT.nounSingular);
        var visitMap = this.chartData.individual ? this.chartData.individual.visitMap : this.chartData.aggregate.visitMap;
        var tickMap = LABKEY.vis.TimeChartHelper.generateTickMap(visitMap);

        var plotConfig = {
            renderTo: newChartDiv.getId(),
            clipRect: applyClipRect,
            labels: LABKEY.vis.TimeChartHelper.generateLabels(mainTitle, this.chartInfo.axis, subtitle),
            layers: LABKEY.vis.TimeChartHelper.generateLayers(this.chartInfo, visitMap, individualColumnAliases, aggregateColumnAliases, aggregateData, seriesList, intervalKey, this.SUBJECT.columnName),
            aes: LABKEY.vis.TimeChartHelper.generateAes(this.chartInfo, visitMap, individualColumnAliases, intervalKey, this.SUBJECT.columnName),
            scales: LABKEY.vis.TimeChartHelper.generateScales(this.chartInfo, tickMap, this.chartData.numberFormats),
            width: this.chartInfo.width || (newChartDiv.getWidth() - 20), // -20 prevents horizontal scrollbars in cases with multiple charts.
            height: this.chartInfo.height || (chartHeight - 20), // -20 prevents vertical scrollbars in cases with one chart.
            data: individualData ? individualData : aggregateData
        };

        if(this.supportedBrowser && !this.useRaphael) {
            plotConfig.rendererType = 'd3';
        }

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();

        if (this.supportedBrowser)
        {
            newChartDiv.add(this.createExportIcon('fa-file-pdf-o', 'Export to PDF', 0, function(){
                this.exportChartToImage(newChartDiv, LABKEY.vis.SVGConverter.FORMAT_PDF);
            }));

            newChartDiv.add(this.createExportIcon('fa-file-image-o', 'Export to PNG', 1, function(){
                this.exportChartToImage(newChartDiv, LABKEY.vis.SVGConverter.FORMAT_PNG);
            }));
        }
        if (this.isDeveloper)
        {
            newChartDiv.add(this.createExportIcon('fa-file-code-o', 'Export as Script', this.supportedBrowser ? 2 : 0, function(){
                this.exportChartToScript();
            }));
        }

        return plot;
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
                        html: tooltip
                    });

                    cmp.getEl().on('click', callbackFn, this);
                }
            }
        });
    },

    exportChartToImage : function(chartDiv, type)
    {
        var exportType = type || LABKEY.vis.SVGConverter.FORMAT_PDF;
        LABKEY.vis.SVGConverter.convert(chartDiv.getEl().child('svg').dom, exportType, chartDiv.mainTitle);
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if (Ext4.isObject(this.tempGridInfo)) {
            this.maskAndRemoveCharts();
            this.loaderFn = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

            var dataGridPanel = Ext4.create('Ext.panel.Panel', {
                minHeight: 620,
                autoScroll: true,
                border: false,
                layout: {
                    type: 'anchor',
                    reserveScrollbar: true
                },
                items: [
                    {
                        // add container to place QWP into
                        xtype: 'container',
                        autoEl: {tag: 'div'},
                        listeners: {
                            afterrender: {
                                fn : function(ct) {

                                    // create the queryWebpart using the temp grid schema and query name
                                    var chartQueryWebPart = new LABKEY.QueryWebPart({
                                        renderTo: ct.getId(),
                                        schemaName: this.tempGridInfo.schema,
                                        queryName: this.tempGridInfo.query,
                                        sort: this.tempGridInfo.sortCols ? this.tempGridInfo.sortCols.join(", ") : null,
                                        parameters: this.chartInfo.parameters,
                                        allowHeaderLock: false,
                                        disableAnalytics: true,
                                        frame: 'none',
                                        buttonBar   : {
                                            includeStandardButton: false,
                                            items: [LABKEY.QueryWebPart.standardButtons.exportRows]
                                        },
                                        messages: {
                                            chartFilters: 'Note: filters applied to the data grid will not be reflected in the chart(s).'
                                        }
                                    });

                                    // re-enable the View Charts button once the QWP has rendered
                                    chartQueryWebPart.on('render', function() {
                                        this.viewChartBtn.enable();

                                        chartQueryWebPart.addMessage('Note: filters applied to the data grid will not be reflected in the chart(s).', 'chartFilters');

                                        // redo the layout of the qwp panel to reset the auto height
                                        ct.doLayout();

                                        if (Ext4.isObject(chartQueryWebPart.parameters)) {
                                            Ext4.apply(this.parameters, chartQueryWebPart.parameters);
                                        }

                                        this.unmaskPanel();
                                    }, this);
                                },
                                scope: this,
                                single: true
                            }
                        }
                    }
                ]
            });

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
        }
    },

    maskAndRemoveCharts: function() {
        // mask panel and remove the chart(s)
        if (this.chart.getEl() && !this.chart.getEl().isMasked())
        {
            this.chart.getEl().mask("loading...");
            this.clearChartPanel();
        }
    },

    unmaskPanel: function() {
        // unmask the panel if needed
        if (this.chart.getEl().isMasked())
            this.chart.getEl().unmask();
    },

    getInitializedChartInfo: function(){
        return {
            measures: [],
            axis: [],
            chartLayout: 'single',
            chartSubjectSelection: 'subjects',
            lineWidth: 3,
            hideDataPoints: false,
            pointClickFn: null,
            errorBars: "None",
            aggregateType: "Mean",
            subject: {},
            filterUrl: LABKEY.Query.Visualization.getDataFilterFromURL(),
            filterQuery: this.getFilterQuery()
        }
    },

    getInitializedChartTypeOptions: function()
    {
        return {
            time: 'date',
            interval: 'Days',
            zeroDateCol: null
        };
    },

    getChartInfoFromOptionPanels: function()
    {
        // use the saved report config if not in edit mode
        // TODO we should be able to just use the saved report chartInfo if not in edit mode
        //if (!this.editMode && !this.isNew())
        //    return this.chartInfo;

        var config = {};
        config.measures = [];
        config.axis = [];

        this.ensureChartLayoutOptions();
        config.title = this.chartLayoutOptions.general.label;
        config.height = this.chartLayoutOptions.general.height;
        config.width = this.chartLayoutOptions.general.width;
        config.lineWidth = this.chartLayoutOptions.general.lineWidth;
        config.hideDataPoints = this.chartLayoutOptions.general.hideDataPoints;
        config.chartLayout = this.chartLayoutOptions.general.chartLayout;
        config.chartSubjectSelection = this.chartLayoutOptions.general.chartSubjectSelection;
        config.displayIndividual = this.chartLayoutOptions.general.displayIndividual;
        config.displayAggregate = this.chartLayoutOptions.general.displayAggregate;
        config.errorBars = this.chartLayoutOptions.general.errorBars;

        if (Ext4.isObject(this.chartLayoutOptions.developer))
            config.pointClickFn = this.chartLayoutOptions.developer.pointClickFn;

        // get the url filter info
        config.filterUrl = this.chartInfo.filterUrl;
        config.filterQuery = this.chartInfo.filterQuery;

        // get the subject info based on the selected chart layout
        var hasGroupsSelected = config.chartSubjectSelection == 'groups';
        config.subject = this.getFiltersPanel().getSubject(hasGroupsSelected, config.displayIndividual);

        // get the measure and dimension information for the y-axis (can be > 1 measure)
        Ext4.each(this.chartTypeMeasures, function(measure)
        {
            var alias = LABKEY.vis.TimeChartHelper.getMeasureAlias(measure),
                yMeasureProps = this.getYMeasureFieldValuesByAlias(alias);

            var props = {
                measure: Ext4.clone(measure),
                dimension: Ext4.clone(yMeasureProps.dimension),
                time: this.chartTypeOptions.time
            };

            if (Ext4.isObject(props.dimension) && Ext4.isString(props.dimension.name))
            {
                var dimAlias = LABKEY.vis.TimeChartHelper.getMeasureAlias(props.dimension);
                props.dimension.values = this.getDimensionValuesByAlias(dimAlias);
                props.measure.aggregate = yMeasureProps.dimensionAggregate;
            }

            if (props.time == "date")
            {
                props.dateOptions = {
                    dateCol: Ext4.clone(yMeasureProps.dateCol),
                    zeroDateCol: Ext4.clone(this.chartTypeOptions.zeroDateCol),
                    interval: this.chartTypeOptions.interval
                };
            }

            config.measures.push(props);
        }, this);

        // the subject column is used in the sort, so it needs to be applied to one of the measures
        if (config.measures.length > 0)
        {
            Ext4.apply(config.subject, {
                name: this.SUBJECT.columnName,
                schemaName: config.measures[0].measure.schemaName,
                queryName: config.measures[0].measure.queryName
            });
        }

        // get the axis array (x plus both left and right based on measure configuration)
        config.axis.push(this.convertAxisPanelOptions('x-axis', null, this.chartLayoutOptions.x));
        var sides = LABKEY.vis.TimeChartHelper.getDistinctYAxisSides(this.chartTypeMeasures);
        Ext4.each(sides, function(yAxisSide)
        {
            var axisName = yAxisSide == 'right' ? 'yRight' : 'y';
            if (Ext4.isDefined(this.chartLayoutOptions[axisName]))
                config.axis.push(this.convertAxisPanelOptions('y-axis', yAxisSide, this.chartLayoutOptions[axisName]));
        }, this);

        config.parameters = this.parameters;

        return config;
    },

    convertAxisPanelOptions : function(name, side, options)
    {
        var config = {
            name: name,
            label: options.label,
            scale: options.scaleTrans,
            range: {
                type: options.scaleRangeType,
                min: options.scaleRange.min,
                max: options.scaleRange.max
            }
        };

        if (Ext4.isString(side))
            config.side = side;

        return config;
    },

    saveChart: function(saveChartInfo) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = this.savedReportInfo ? this.savedReportInfo.schemaName : (this.getFilterSchemaName() || null);
        var query = this.savedReportInfo ? this.savedReportInfo.queryName : (this.getFilterQueryName() || null);

        var config = {
            replace: saveChartInfo.replace,
            reportName: saveChartInfo.reportName,
            reportDescription: saveChartInfo.reportDescription,
            reportShared: saveChartInfo.shared,
            reportThumbnailType: saveChartInfo.thumbnailType,
            reportSvg: saveChartInfo.thumbnailType == 'AUTO' ? this.chartThumbnailSvgStr : null,
            createdBy: saveChartInfo.createdBy,
            query: query,
            schema: schema
        };

        // if user clicked save button to replace an existing report, execute the save chart call
        // otherwise, the user clicked save for a new report (or save as) so check if the report name already exists
        if(!saveChartInfo.isSaveAs && saveChartInfo.replace){
            this.executeSaveChart(config);
        }
        else{
            this.checkSaveChart(config);
        }
    },

    checkSaveChart: function(config){
        // see if a report by this name already exists within this container
        LABKEY.Query.Visualization.get({
            name: config.reportName,
            success: function(result, request, options){
                // a report by that name already exists within the container, if the user can update, ask if they would like to replace
                if(this.canEdit && config.replace){
                    Ext4.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + Ext4.util.Format.htmlEncode(config.reportName) + '\' already exists. Would you like to replace it?',
                        buttons: Ext4.Msg.YESNO,
                        fn: function(btnId, text, opt){
                            if(btnId == 'yes'){
                                config.replace = true;
                                this.executeSaveChart(config);
                            }
                        },
                        icon: Ext4.MessageBox.WARNING,
                        scope: this
                    });
                }
                else{
                    Ext4.Msg.show({
                        title:'Error',
                        msg: 'A report by the name \'' + Ext4.util.Format.htmlEncode(config.reportName) + '\' already exists.  Please choose a different name.',
                        buttons: Ext4.Msg.OK,
                        icon: Ext4.MessageBox.ERROR
                    });
                }
            },
            failure: function(errorInfo, response){
                // no report exists with that name
                this.executeSaveChart(config);
            },
            scope: this
        });
    },

    executeSaveChart: function(config){
        // get the chart info to be saved
        this.chartInfo = this.getChartInfoFromOptionPanels();

        LABKEY.Query.Visualization.save({
            name: config.reportName,
            description: config.reportDescription,
            shared: config.reportShared,
            visualizationConfig: this.chartInfo,
            thumbnailType: config.reportThumbnailType,
            svg: config.reportSvg,
            replace: config.replace,
            type: LABKEY.Query.Visualization.Type.TimeChart,
            success: this.saveChartSuccess(config.replace, config.reportName),
            schemaName: config.schema,
            queryName: config.query,
            scope: this
        });
    },

    saveChartSuccess: function (replace, reportName){
        return function(result, request, options) {
            this.markDirty(false);

            var msgbox = Ext4.create('Ext.window.Window', {
                html     : '<span class="labkey-message">Report saved successfully.</span>',
                style      : 'background-color: #eeeeee; border-color: #b4b4b4; box-shadow: none;',
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
            if (!this.getSavePanel().isSavedReport() || !replace)
                window.location = LABKEY.ActionURL.buildURL('reports', 'runReport', null, {reportId: result.visualizationId});
        }
    },

    // clear the chart panel of any messages, charts, or grids
    // if displaying a message, also make sure to unmask the chart wizard element
    clearChartPanel: function(message){
        this.chart.removeAll();
        this.clearWarningText();
        if (message)
        {
            this.chart.add(Ext4.create('Ext.panel.Panel', {
                border: false,
                html : "<div style='padding: 10px; background-color: #ffe5e5; color: #d83f48; font-weight: bold;'>" + message + "</div>"
            }));
            this.unmaskPanel();
        }
    },

    clearWarningText: function(){
        this.warningText = "";
    },

    addWarningText: function(message){
        if (this.warningText.length > 0)
            this.warningText += "<BR/>";
        this.warningText += message;
    }
});

Ext4.define('LABKEY.vis.MeasureModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'id', type: 'integer'},
        {name: 'label', type: 'string'},
        {name: 'name', type: 'string'},
        {name: 'queryName', type: 'string'},
        {name: 'queryLabel', type: 'string'}
    ]
});

Ext4.define('LABKEY.vis.DimensionModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'id'},
        {name:'name'},
        {name:'label'},
        {name:'longlabel'},
        {name:'description'},
        {name:'isUserDefined'},
        {name:'queryName'},
        {name:'schemaName'},
        {name:'type'}
    ]
});

Ext4.define('LABKEY.vis.SimpleValueModel', {
    extend: 'Ext.data.Model',
    fields: ['value']
});