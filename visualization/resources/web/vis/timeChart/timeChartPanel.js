/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresCss("dataview/DataViewsPanel.css");

LABKEY.requiresScript("study/ParticipantFilterPanel.js");
LABKEY.requiresScript("study/MeasurePicker.js");

Ext4.define('LABKEY.vis.TimeChartPanel', {

    extend : 'Ext.panel.Panel',

    alias : 'widget.timechartpanel',

    // study subject noun information
    SUBJECT : {
        tableName: LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject ? LABKEY.moduleContext.study.subject.tableName : 'Participant',
        columnName: LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject ? LABKEY.moduleContext.study.subject.columnName : 'ParticipantId',
        nounPlural: LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject ? LABKEY.moduleContext.study.subject.nounPlural : 'Participants',
        nounSingular: LABKEY.moduleContext.study && LABKEY.moduleContext.study.subject ? LABKEY.moduleContext.study.subject.nounSingular : 'Participant'
    },

    constructor : function(config){
        // properties for this panel
        Ext4.apply(config, {
            layout: 'border',
            bodyStyle: 'background-color: white;',
            monitorResize: true,
            maxCharts: 30,
            dataLimit: 10000
        });

        // support backwards compatibility for charts saved prior to chartInfo reconfig (2011-08-31)
        if (config.chartInfo)
        {
            Ext4.applyIf(config.chartInfo, {
                axis: [],
                //This is for charts saved prior to 2011-10-07
                chartSubjectSelection: config.chartInfo.chartLayout == 'per_group' ? 'groups' : 'subjects',
                displayIndividual: true,
                displayAggregate: false
            });
            for (var i = 0; i < config.chartInfo.measures.length; i++)
            {
                var md = config.chartInfo.measures[i];

                Ext4.applyIf(md.measure, {yAxis: "left"});

                // if the axis info is in md, move it to the axis array
                if (md.axis)
                {
                    // default the y-axis to the left side if not specified
                    if (md.axis.name == "y-axis")
                            Ext4.applyIf(md.axis, {side: "left"});

                    // move the axis info to the axis array
                    if (LABKEY.vis.TimeChartHelper.getAxisIndex(config.chartInfo.axis, md.axis.name, md.axis.side) == -1)
                        config.chartInfo.axis.push(Ext4.apply({}, md.axis));

                    // if the chartInfo has an x-axis measure, move the date info it to the related y-axis measures
                    if (md.axis.name == "x-axis")
                    {
                        for (var j = 0; j < config.chartInfo.measures.length; j++)
                        {
                            var schema = md.measure.schemaName;
                            var query = md.measure.queryName;
                            if (config.chartInfo.measures[j].axis && config.chartInfo.measures[j].axis.name == "y-axis"
                                    && config.chartInfo.measures[j].measure.schemaName == schema
                                    && config.chartInfo.measures[j].measure.queryName == query)
                            {
                                config.chartInfo.measures[j].dateOptions = {
                                    dateCol: Ext4.apply({}, md.measure),
                                    zeroDateCol: Ext4.apply({}, md.dateOptions.zeroDateCol),
                                    interval: md.dateOptions.interval
                                };
                            }
                        }

                        // remove the x-axis date measure from the measures array
                        config.chartInfo.measures.splice(i, 1);
                        i--;
                    }
                    else
                    {
                        // remove the axis property from the measure
                        delete md.axis;
                    }
                }
            } // end of : for
        } // end of : if (config.chartInfo)

        // backwards compatibility for save thumbnail options (2012-06-19)
        if(typeof config.saveReportInfo == "object" && config.chartInfo.saveThumbnail != undefined)
        {
            if (config.saveReportInfo.reportProps == null)
                config.saveReportInfo.reportProps = {};

            Ext4.applyIf(config.saveReportInfo.reportProps, {
                thumbnailType: !config.chartInfo.saveThumbnail ? 'NONE' : 'AUTO'
            });
        }

        this.callParent([config]);
    },

    initComponent : function() {

        if(this.viewInfo.type != "line")
            return;
        
        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
            this.savedChartInfo = null;
        } else {
            // If we have a saved chart we want to save a copy of config.chartInfo so we know if any chart settings
            // get changed. This way we can set the dirty bit. Ext.encode gives us a copy not a reference.
            this.savedChartInfo = Ext4.encode(this.chartInfo);
        }

        // hold on to the x and y axis measure index
        var xAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = LABKEY.vis.TimeChartHelper.getAxisIndex(this.chartInfo.axis, "y-axis", "right");

        // add a listener to call measureSelectionChange on render if this is a saved chart
        // otherwise add the overview panel to the chart area to select the initial measure
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object")
                {
                    this.measureSelectionChange(false);
                    this.editorMeasurePanel.initializeDimensionStores();
                }
                else
                {
                    this.toggleOptionButtons(true);
                    this.chart.add(Ext4.create('LABKEY.vis.InitialMeasurePanel', {
                        helpText: this.getMeasurePickerHelpText(),
                        listeners: {
                            scope: this,
                            'initialMeasuresStoreLoaded': function(data) {
                                // pass the measure store JSON data object to the measures panel
                                this.editorMeasurePanel.setMeasuresStoreData(data);
                            },
                            'initialMeasureSelected': function(initMeasure) {
                                this.maskAndRemoveCharts();
                                this.editorMeasurePanel.addMeasure(initMeasure, true);
                                this.measureSelectionChange(false);
                            }
                        }
                    }));
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        this.participantSelector = Ext4.create('LABKEY.vis.ParticipantSelector', {
            subject: (this.chartInfo.chartSubjectSelection != "groups" ? this.chartInfo.subject : {}),
            collapsed: this.chartInfo.chartSubjectSelection != "subjects",
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'switchToGroupLayout': function(){
                    this.editorGroupingPanel.setChartSubjectSelection(true);
                    this.setOptionsForGroupLayout(true);
                    this.chartDefinitionChanged(true);
                },
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.groupsSelector = Ext4.create('LABKEY.vis.GroupSelector', {
            subject: (this.chartInfo.chartSubjectSelection == "groups" ? this.chartInfo.subject : {}),
            collapsed: this.chartInfo.chartSubjectSelection != "groups",
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.filtersPanel = Ext4.create('Ext.panel.Panel', {
            region: 'east',
            layout: 'accordion',
            fill: false,
            width: 220,
            minWidth: 220,
            border: true,
            split: true,
            collapsible: true,
            floatable: false,
            title: 'Filters',
            titleCollapse: true,
            items: [
                this.participantSelector,
                this.groupsSelector
            ],
            listeners: {
                scope: this,
                'afterRender': function(){
                    this.setOptionsForGroupLayout(this.chartInfo.chartSubjectSelection == "groups");
                },
                'expand': function(){
                    this.setOptionsForGroupLayout(this.editorGroupingPanel.getChartSubjectSelection() == "groups");
                }
            }
        });
        items.push(this.filtersPanel);

        this.editorSavePanel = Ext4.create('LABKEY.vis.SaveOptionsPanel', {
            title: 'Save',
            reportInfo: this.saveReportInfo,
            canEdit: this.canEdit,
            canShare: this.canShare,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'saveChart': this.saveChart
            }
        });

        this.editorMeasurePanel = Ext4.create('LABKEY.vis.MeasureOptionsPanel', {
            title: 'Measure Options',
            origMeasures: this.chartInfo.measures,
            filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Query.Visualization.getDataFilterFromURL(),
            filterQuery: this.chartInfo.filterQuery ? this.chartInfo.filterQuery : this.getFilterQuery(),
            filtersParentPanel: this.filtersPanel,
            helpText: this.getMeasurePickerHelpText(),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh) {
                    this.measureSelectionChange(true);
                    this.chartDefinitionChanged(requiresDataRefresh);
                },
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.editorXAxisPanel = Ext4.create('LABKEY.vis.XAxisOptionsPanel', {
            title: 'X Axis',
            multipleCharts: this.chartInfo.chartLayout != "single",
            axis: this.chartInfo.axis[xAxisIndex] ? this.chartInfo.axis[xAxisIndex] : {},
            zeroDateCol: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.zeroDateCol : {},
            interval: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.interval : "Days",
            time: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].time ? this.chartInfo.measures[0].time : 'date',
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete,
                'noDemographicData': this.disableOptionElements
            }
        });

        this.editorYAxisLeftPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            title: 'Y Axis',
            inputPrefix: "left",
            multipleCharts: this.chartInfo.chartLayout != "single",
            axis: this.chartInfo.axis[leftAxisIndex] ? this.chartInfo.axis[leftAxisIndex] : {side: "left"},
            defaultLabel: this.editorMeasurePanel.getDefaultLabel("left"),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetLabel': function() {
                    this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
                }
            }
        });
        //Set radio/textfield cls/IDs to aid with TimeChartTest.
        this.editorYAxisLeftPanel.labelResetButton.cls = "revertleftAxisLabel";
        this.editorYAxisLeftPanel.rangeManualRadio.id = "leftaxis_range_manual";
        this.editorYAxisLeftPanel.rangeAutomaticRadio.id = "leftaxis_range_automatic";
        this.editorYAxisLeftPanel.rangeAutomaticPerChartRadio.id = "leftaxis_range_automatic_per_chart";
        this.editorYAxisLeftPanel.scaleCombo.id = "leftaxis_scale";

        this.editorYAxisRightPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            title: 'Y Axis',
            inputPrefix: "right",
            multipleCharts: this.chartInfo.chartLayout != "single",
            axis: this.chartInfo.axis[rightAxisIndex] ? this.chartInfo.axis[rightAxisIndex] : {side: "right"},
            defaultLabel: this.editorMeasurePanel.getDefaultLabel("right"),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetLabel': function() {
                    this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
                }
            }
        });
        //Set radio/textfield cls/IDs to aid with TimeChartTest.
        this.editorYAxisRightPanel.labelResetButton.cls = "revertrightAxisLabel";
        this.editorYAxisRightPanel.rangeManualRadio.id = "rightaxis_range_manual";
        this.editorYAxisRightPanel.rangeAutomaticRadio.id = "rightaxis_range_automatic";
        this.editorYAxisRightPanel.rangeAutomaticPerChartRadio.id = "rightaxis_range_automatic_per_chart";
        this.editorYAxisRightPanel.scaleCombo.id = "rightaxis_scale";

        this.editorGroupingPanel = Ext4.create('LABKEY.vis.GroupingOptionsPanel', {
            title: 'Grouping Options',
            chartLayout: this.chartInfo.chartLayout,
            chartSubjectSelection: this.chartInfo.chartSubjectSelection,
            displayIndividual: this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
            displayAggregate: this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
            errorBars: this.chartInfo.errorBars != undefined ? this.chartInfo.errorBars : "None",
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'groupLayoutSelectionChanged': function(groupLayoutSelected) {
                    // if the filters panel is collapsed, first open it up so the user sees that the filter options have changed
                    if (this.filtersPanel.collapsed)
                        this.filtersPanel.expand();
                    else
                        this.setOptionsForGroupLayout(groupLayoutSelected);
                },
                'numChartsSelectionChanged': function(chartLayout) {
                    this.editorYAxisLeftPanel.setRangeAutomaticOptions(chartLayout, true);
                    this.editorYAxisRightPanel.setRangeAutomaticOptions(chartLayout, true);
                    this.editorXAxisPanel.setRangeAutomaticOptions(chartLayout, false);
                }
            }
        });

        this.editorAestheticsPanel = Ext4.create('LABKEY.vis.AestheticOptionsPanel', {
            title: 'Plot Options',
            lineWidth: this.chartInfo.lineWidth,
            hideDataPoints: this.chartInfo.hideDataPoints,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged
            }
        });

        this.editorMainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
            title: 'Main Title',
            mainTitle: this.chartInfo.title,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'resetTitle': function() {
                    this.editorMainTitlePanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());
                }
            }
        });

        this.editorDeveloperPanel = Ext4.create('LABKEY.vis.DeveloperOptionsPanel', {
            title: 'Developer Options',
            isDeveloper: this.isDeveloper || false,
            pointClickFn: this.chartInfo.pointClickFn || null,
            defaultPointClickFn: this.getDefaultPointClickFn(),
            pointClickFnHelp: this.getPointClickFnHelp(),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged
            }
        });

        // put the options panels in an array for easier access
        this.optionPanelsArray = [
            this.editorGroupingPanel,
            this.editorAestheticsPanel,
            this.editorDeveloperPanel,
            this.editorMainTitlePanel,
            this.editorXAxisPanel,
            this.editorYAxisLeftPanel,
            this.editorYAxisRightPanel,
            this.editorSavePanel
        ];

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
            if (this.parameters) {
                this.loaderFn = this.renderLineChart;
                this.loaderName = 'renderLineChart';
                this.getChartData();
            }
            else {
                this.renderLineChart();
            }

        }, scope: this, hidden: true});
        this.refreshChart = new Ext4.util.DelayedTask(function(){
            this.getChartData();
        }, this);

        // boolean to check if we should allow things like export and the developer panel
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8); // issue 15372

        // setup export menu (items to be added later)
        this.exportMenu = Ext4.create('Ext.menu.Menu', {cls: 'extContainer'});
        this.exportMenuBtn = Ext4.create('Ext.button.Button', {
            text: 'Export',
            menu: this.exportMenu,
            disabled: true,
            scope: this
        });

        // setup buttons for the charting options panels (items to be added to the toolbar)
        this.measuresButton = Ext4.create('Ext.button.Button', {text: 'Measures',
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorMeasurePanel, 860, 210, 'left');}, scope: this});
        
        this.groupingButton = Ext4.create('Ext.button.Button', {text: 'Grouping',
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorGroupingPanel, 600, 210, 'left');}, scope: this});

        this.aestheticsButton = Ext4.create('Ext.button.Button', {text: 'Options',
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorAestheticsPanel, 300, 125, 'center');}, scope: this});

        this.developerButton = Ext4.create('Ext.button.Button', {text: 'Developer', hidden: !this.isDeveloper,
                                handler: function(btn){this.optionsButtonClicked(btn, this.editorDeveloperPanel, 800, 500, 'center');}, scope: this,
                                disabled: !this.supportedBrowser});

        if (!this.supportedBrowser)
        {
            this.developerButton.setTooltip("Developer options not supported for IE6, IE7, or IE8.");
            this.exportMenuBtn.setTooltip("Export not supported for IE6, IE7, or IE8.");
        }

        this.saveButton = Ext4.create('Ext.button.Button', {text: 'Save', hidden: !this.canEdit,
                        handler: function(btn){
                                this.editorSavePanel.setSaveAs(false);
                                this.optionsButtonClicked(btn, this.editorSavePanel, 850, 420, 'right');
                        }, scope: this});


        this.saveAsButton = Ext4.create('Ext.button.Button', {text: 'Save As', hidden: !this.editorSavePanel.isSavedReport() || LABKEY.Security.currentUser.isGuest,
                        handler: function(btn){
                                this.editorSavePanel.setSaveAs(true);
                                this.optionsButtonClicked(btn, this.editorSavePanel, 850, 420, 'right');
                        }, scope: this});

        var params = LABKEY.ActionURL.getParameters();
        this.useRaphael = params.useRaphael != null ? params.useRaphael : false;

        // issue 21418: support for parameterized queries
        var urlQueryParams = LABKEY.Filter.getQueryParamsFromUrl(params['filterUrl']);
        this.parameters = this.chartInfo.parameters ? this.chartInfo.parameters : urlQueryParams;
        this.chartInfo.parameters = this.parameters;

        // if edit mode, then add the editor panel buttons and save buttons
        this.editMode = (params.edit == "true" || !this.editorSavePanel.isSavedReport()) && this.allowEditMode;
        var toolbarButtons = [
            this.viewGridBtn,
            this.viewChartBtn,
            this.exportMenuBtn
        ];
        if (this.editMode)
        {
            toolbarButtons.push(this.measuresButton);
            toolbarButtons.push(this.groupingButton);
            toolbarButtons.push(this.aestheticsButton);
            toolbarButtons.push(this.developerButton);
            toolbarButtons.push('->');
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
            border: true,
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
        items.push(this.chart);

        Ext4.applyIf(this, {autoResize: true});
        if (this.autoResize)
        {
            Ext4.EventManager.onWindowResize(function(w,h){
                this.resizeToViewport(w,h);
            }, this, {buffer: 500});
        }

        this.items = items;

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        this.callParent();
    },

    resizeCharts : function(){
        // only call loader if the data object is available and the loader equals renderLineChart
        if (this.chartData && (this.chartData.individual || this.chartData.aggregate) && this.loaderName == 'renderLineChart')
            this.loaderFn();
    },

    optionsButtonClicked : function(button, panel, width, height, align) {
        var pos = button.getEl().getXY();
        var pLeft = pos[0];
        var pTop = pos[1] + 20;

        if (align == 'center')
            pLeft = pLeft - width/2 + button.getWidth()/2;
        else if (align == 'right')
            pLeft = pLeft - width + button.getWidth();
        else if (align == 'left')
            pLeft = pLeft - button.getWidth();        

        this.showOptionsWindow(panel, width, pLeft, pTop);
    },

    getClickXY : function(event) {
        if (event.pageX && event.pageY)
            return [event.pageX, event.pageY];
        else
            return [event.clientX, event.clientY];
    },

    chartElementClicked : function(panel, clickXY, width, height, align) {
        var pLeft = clickXY[0];
        var pTop = clickXY[1];

        if (align == 'above')
        {
            pLeft = pLeft - width/2;
            pTop = pTop - height - 10;
        }
        else if (align == 'below')
        {
            pLeft = pLeft - width/2;
            pTop = pTop + 12;
        }
        else if (align == 'right')
        {
            pLeft = pLeft - width - 10;
            pTop = pTop - height/2;
        }
        else if (align == 'left')
        {
            pLeft = pLeft + 10;
            pTop = pTop - height/2;
        }

        // Issue 17851: make sure the left or top location are not off the screen
        if (pLeft < 0) pLeft = this.chart.getEl().getX();
        if (pTop < 0) pTop = this.chart.getEl().getY();

        this.showOptionsWindow(panel, width, pLeft, pTop);
    },

    showOptionsWindow : function(panel, width, positionLeft, positionTop) {
        if (!this.optionWindow)
        {
            this.optionWindow = Ext4.create('Ext.window.Window', {
                floating: true,
                cls: 'data-window',
                draggable : false,
                resizable: false,
                width: 860,
                autoHeight: true,
                modal: true,
                closeAction: 'hide',
                layout: 'card',
                items: this.optionPanelsArray,
                onEsc: function(){
                    this.fireEvent('beforeclose');
                    this.hide();
                },
                listeners: {
                    'closeOptionsWindow': function(canceling) {
                        if (canceling)
                            this.optionWindow.fireEvent('beforeclose');
                        this.optionWindow.hide();
                    },
                    scope: this
                }
            });
        }

        this.optionWindowPanel = panel;

        if (typeof panel.getPanelOptionValues === 'function')
        {
            this.initialPanelValues = panel.getPanelOptionValues();

            // reset the before close event handler for the given panel
            this.optionWindow.un('beforeclose', this.restorePanelValues, this);
            this.optionWindow.on('beforeclose', this.restorePanelValues, this);
        }

        this.optionWindow.setWidth(width);
        if (positionLeft && positionTop)
            this.optionWindow.setPosition(positionLeft, positionTop, false);

        this.optionWindow.setTitle(panel.title);
        this.optionWindow.getLayout().setActiveItem(this.optionWindowPanel);
        this.optionWindow.show();
    },

    restorePanelValues : function()
    {
        // on close, we don't apply changes and let the panel restore its state
        this.optionWindowPanel.restoreValues(this.initialPanelValues);
    },

    setOptionsForGroupLayout : function(groupLayoutSelected){
        if (groupLayoutSelected)
        {
            this.groupsSelector.show();
            this.groupsSelector.expand();
            this.participantSelector.hide();
        }
        else
        {
            this.participantSelector.show();
            this.participantSelector.expand();
            this.groupsSelector.hide();
        }
    },

    resizeToViewport : function(w,h) {
        if (!this.rendered)
            return;

        var padding = [20,0];
        var xy = this.el.getXY();
        var width = Math.max(875,w-xy[0]-padding[0]);
        this.setWidth(width);
    },

    getFilterQuery :  function()
    {
        var schemaName = LABKEY.ActionURL.getParameter("schemaName");
        var queryName = LABKEY.ActionURL.getParameter("queryName");
        if (schemaName && queryName)
            return schemaName + "." + queryName;
        else
            return undefined;
    },

    measureSelectionChange: function(fromMeasurePanel) {
        // these method calls should only be made for chart initialization
        // (i.e. showing saved chart or first measure selected for new chart)
        if(!fromMeasurePanel){
            this.participantSelector.getSubjectValues();
            this.groupsSelector.getGroupValues();
            this.editorXAxisPanel.setZeroDateStore();
        }

        // these method calls should be made for all measure selections
        this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
        this.editorMainTitlePanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());

        // if all of the measures have been removed, disable any non-relevant elements
        if (this.editorMeasurePanel.getNumMeasures() == 0)
            this.disableNonMeasureOptionButtons();
        else
            this.toggleOptionButtons(false);
    },

    toggleOptionButtons: function(disable){
        this.measuresButton.setDisabled(disable);
        this.groupingButton.setDisabled(disable);
        this.aestheticsButton.setDisabled(disable);
        this.developerButton.setDisabled(!this.supportedBrowser || disable);
        this.saveButton.setDisabled(disable);
        this.saveAsButton.setDisabled(disable);
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
            if (!this.developerButton.isHidden())
                this.buttonsToShow.push(this.developerButton.hide());
            if (!this.aestheticsButton.isHidden())
                this.buttonsToShow.push(this.aestheticsButton.hide());
            if (!this.groupingButton.isHidden())
                this.buttonsToShow.push(this.groupingButton.hide());
            if (!this.measuresButton.isHidden())
                this.buttonsToShow.push(this.measuresButton.hide());
            if (!this.exportMenuBtn.isHidden())
                this.buttonsToShow.push(this.exportMenuBtn.hide());
        }
    },

    disableNonMeasureOptionButtons: function(){
        this.groupingButton.disable();
        this.aestheticsButton.disable();
        this.developerButton.disable();
        this.exportMenuBtn.disable();
        this.viewGridBtn.disable();
        this.viewChartBtn.disable();
    },

    disableOptionElements: function(){
        this.toggleOptionButtons(true);
        this.filtersPanel.disable();
        this.clearChartPanel("There are no demographic date options available in this study.<br/>"
                            + "Please contact an administrator to have them configure the study to work with the Time Chart wizard.");
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
    },

    chartDefinitionChanged: function(requiresDataRefresh) {
        if (requiresDataRefresh)
            this.refreshChart.delay(100);
        else
            this.loaderFn();
    },

    measureMetadataRequestPending:  function() {
        // increase the request counter
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        // decrease the request counter
        this.measureMetadataRequestCounter--;

        // if all requests are complete, call getChartData
        if(this.measureMetadataRequestCounter == 0)
            this.getChartData();
    },

    getChartData: function() {
        this.maskAndRemoveCharts();

        // Clear previous chart data, number formats, etc.
        this.chartData = undefined;

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        // validate the chart config to display either warning or error messages
        var validation = LABKEY.vis.TimeChartHelper.validateChartConfig(this.chartInfo);
        if (!validation.success)
        {
            this.clearChartPanel(validation.message);
            this.exportMenuBtn.disable();
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
            if (this.canEdit && !this.editorSavePanel.isSavedReport())
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
                        (this.editorXAxisPanel.getTime() == "date"
                            ? LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.chartInfo.measures[0].dateOptions.dateCol.name)
                            : LABKEY.vis.getColumnAlias(this.chartData.individual.columnAliases, this.SUBJECT.nounSingular + "Visit/Visit/DisplayOrder"))
                    ]
                };
            }

            // now that we have the temp grid info, enable the View Data button
            this.viewGridBtn.enable();

            this.loaderFn();
        };

        queryConfig.failure = function(info) {
            this.clearChartPanel("Error: " + info.exception);
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
        simplified.chartLayout = config.chartLayout;
        simplified.chartSubjectSelection = config.chartSubjectSelection;
        simplified.displayAggregate = config.displayAggregate;
        simplified.displayIndividual = config.displayIndividual;
        simplified.errorBars = config.errorBars ? config.errorBars : defaultConfig.errorBars;
        simplified.aggregateType = config.aggregateType ? config.aggregateType : defaultConfig.aggregateType;
        simplified.filterUrl = config.filterUrl;
        simplified.parameters = config.parameters;
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
            if (currentAxis.scale) tempAxis.scale = currentAxis.scale;
            if (currentAxis.side) tempAxis.side = currentAxis.side;
            simplified.axis.push(tempAxis);
        }

        // compare subject information (this is the standard set for both participant and group selections)
        simplified.subject = this.getSchemaQueryInfo(config.subject);
        simplified.subject.values = config.subject.values;
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
        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        if (this.savedChartInfo)
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
            this.exportMenuBtn.disable();
            this.viewGridBtn.disable();
            this.viewChartBtn.disable();
            this.toggleSaveButtons(true);
            return;
        }
        else if (validation.message != null && validation.message.length > 0)
        {
            this.addWarningText(validation.message);
        }

        if (this.chartData.individual && this.chartData.individual.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.chartData.individual.filterDescription);

        this.loaderFn = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // one series per y-axis subject/measure/dimensionvalue combination
        var seriesList = LABKEY.vis.TimeChartHelper.generateSeriesList(this.chartInfo.measures);

        // validate the chart data to either display warnings or error messages
        validation = LABKEY.vis.TimeChartHelper.validateChartData(this.chartData, this.chartInfo, seriesList, this.dataLimit);
        if (!validation.success)
        {
            this.clearChartPanel(validation.message);
            this.exportMenuBtn.disable();
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

        // remove any existing charts, remove items from the exportMenu button
        this.chart.removeAll();
        this.chart.removeListener('resize', this.resizeCharts);
        this.plotConfigInfoArr = [];
        this.exportMenu.removeAll();

        var charts = [];

        this.toggleSaveButtons(false);

        this.plotConfigInfoArr = LABKEY.vis.TimeChartHelper.generatePlotConfigs(this.chartInfo, this.chartData, seriesList, applyClipRect, this.maxCharts, this.SUBJECT.columnName);

        // warn if the max number of charts has been exceeded
        if (this.plotConfigInfoArr.length >= this.maxCharts)
            this.addWarningText("Only showing the first " + this.maxCharts + " charts.");

        for (var configIndex = 0; configIndex < this.plotConfigInfoArr.length; configIndex++)
        {
            var newChart = this.generatePlot(
                    configIndex,
                    this.plotConfigInfoArr[configIndex].height || (this.plotConfigInfoArr.length > 1 ? 380 : 600),
                    this.plotConfigInfoArr[configIndex].style,
                    false // forExport param
                );
            charts.push(newChart);

            this.exportMenu.add({
                text: this.plotConfigInfoArr[configIndex].title,
                icon: LABKEY.contextPath + '/_icons/pdf.gif',
                configIndex: configIndex,
                handler: this.exportChartToPdf,
                scope: this
            });
        }

        // add the export to script menu item for developers
        if (this.isDeveloper)
        {
            this.exportMenu.add({
                text: 'Export as Script',
                icon: LABKEY.contextPath + '/_icons/text.png',
                handler: this.exportChartToScript,
                scope: this
            });
        }
        this.exportMenuBtn.setDisabled(!this.supportedBrowser);

        // show warning message, if there is one
        if (this.warningText.length > 0)
        {
            this.chart.insert(0, Ext4.create('Ext.panel.Panel', {
                border: false,
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + this.warningText + "</td></tr></table>"
            }));
        }

        // if in edit mode, pass the svg for the first chart component to the save options panel for use in the thumbnail preview
        this.chartThumbnailSvgStr = null;
        if (this.editMode && Raphael.svg && this.plotConfigInfoArr.length > 0)
        {
            // create a temp chart that does not have the clickable looking elements
            var tempChart = this.generatePlot(0, 610, null, true);
            if (tempChart)
            {
                // set the svg string for the save dialog thumbnail
                this.chartThumbnailSvgStr = LABKEY.vis.SVGConverter.svgToStr(Ext4.get(tempChart.renderTo).child('svg').dom);
                this.editorSavePanel.updateCurrentChartThumbnail(this.chartThumbnailSvgStr, Ext4.get(tempChart.renderTo).getSize());
                // destroy the temp chart element
                Ext4.getCmp(tempChart.renderTo).destroy();
            }
        }

        this.unmaskPanel();

        this.chart.addListener('resize', this.resizeCharts, this, {buffer: 500});
    },

    exportChartToPdf : function(item) {
        if (item.configIndex == undefined)
        {
            console.error("The item to be exported does not reference a plot config index.");
            return;
        }

        // create a temp chart which does not have the clickable looking elements
        var tempChart = this.generatePlot(item.configIndex, 610, null, true);
        if (tempChart)
        {
            // export the temp chart as a pdf with the chart title as the file name
            LABKEY.vis.SVGConverter.convert(Ext4.get(tempChart.renderTo).child('svg').dom, 'pdf', this.plotConfigInfoArr[item.configIndex].title);
            Ext4.getCmp(tempChart.renderTo).destroy();
        }
    },

    exportChartToScript : function() {
        if (!this.exportScriptWindow)
        {
            this.editorExportScriptPanel = Ext4.create('LABKEY.vis.TimeChartScriptPanel', {
                listeners: {
                    scope: this,
                    closeOptionsWindow: function() {
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
                width: 800,
                resizable: false,
                closeAction: 'hide',
                items: [this.editorExportScriptPanel]
            });
        }

        var chartInfo = this.getChartInfoFromOptionPanels();

        // remove the leading comment from the developer point click function before generating the export script
        if (chartInfo.pointClickFn)
            chartInfo.pointClickFn = this.editorDeveloperPanel.removeLeadingComments(chartInfo.pointClickFn);

        var templateConfig = { chartConfig: chartInfo };
        this.editorExportScriptPanel.setScriptValue(templateConfig);

        this.exportScriptWindow.show();
    },

    generatePlot: function(configIndex, chartHeight, chartStyle, forExport){
        // This function generates a plot config and renders a plot for given data.
        // Should be used in per_subject, single, per_measure, and per_group
        var mainTitle = this.plotConfigInfoArr[configIndex].title;
        var seriesList = this.plotConfigInfoArr[configIndex].series;
        var individualData = this.plotConfigInfoArr[configIndex].individualData;
        var aggregateData = this.plotConfigInfoArr[configIndex].aggregateData;
        var applyClipRect = this.plotConfigInfoArr[configIndex].applyClipRect;

        var newChartDiv = Ext4.create('Ext.container.Container', {
            style: chartStyle ? chartStyle : 'border: none;',
            autoEl: {tag: 'div'}
        });
        this.chart.add(newChartDiv);

        // remove the leading comment from the developer point click function before generating the layers
        if (this.chartInfo.pointClickFn)
            this.chartInfo.pointClickFn = this.editorDeveloperPanel.removeLeadingComments(this.chartInfo.pointClickFn);

        var individualColumnAliases = this.chartData.individual ? this.chartData.individual.columnAliases : null;
        var aggregateColumnAliases = this.chartData.aggregate ? this.chartData.aggregate.columnAliases : null;
        var intervalKey = LABKEY.vis.TimeChartHelper.generateIntervalKey(this.chartInfo, individualColumnAliases, aggregateColumnAliases, this.SUBJECT.nounSingular);
        var visitMap = this.chartData.individual ? this.chartData.individual.visitMap : this.chartData.aggregate.visitMap;
        var tickMap = LABKEY.vis.TimeChartHelper.generateTickMap(visitMap);

        var plotConfig = {
            renderTo: newChartDiv.getId(),
            clipRect: applyClipRect,
            labels: this.generateLabels(mainTitle, forExport),
            layers: LABKEY.vis.TimeChartHelper.generateLayers(this.chartInfo, visitMap, individualColumnAliases, aggregateColumnAliases, aggregateData, seriesList, intervalKey, this.SUBJECT.columnName),
            aes: LABKEY.vis.TimeChartHelper.generateAes(this.chartInfo, visitMap, individualColumnAliases, intervalKey, this.SUBJECT.columnName),
            scales: LABKEY.vis.TimeChartHelper.generateScales(this.chartInfo, tickMap, this.chartData.numberFormats),
            width: newChartDiv.getWidth() - 20, // -20 prevents horizontal scrollbars in cases with multiple charts.
            height: chartHeight - 20, // -20 prevents vertical scrollbars in cases with one chart.
            data: individualData ? individualData : aggregateData
        };

        if(this.supportedBrowser && !this.useRaphael) {
            plotConfig.rendererType = 'd3';
        }

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();
        return plot;
    },

    // time chart has its own generateLabels that uses the one from the TimeChartHelper because it adds the ability to
    // click on a label to view the options panel for that axis/title
    generateLabels: function(mainTitle, forExport) {
        // functions to call on click of axis labels to open the options panel (need to be closures to correctly handle scoping of this)
        var xAxisLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorXAxisPanel, scopedThis.getClickXY(event), 800, 250, 'above');
            }
        };
        var yAxisLeftLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorYAxisLeftPanel, scopedThis.getClickXY(event), 320, 220, 'left');
            }
        };
        var yAxisRightLabelClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorYAxisRightPanel, scopedThis.getClickXY(event), 320, 220, 'right');
            }
        };
        var mainTitleClickFn = function(scopedThis){
            return function(event){
                scopedThis.chartElementClicked(scopedThis.editorMainTitlePanel, scopedThis.getClickXY(event), 300, 130, 'below');
            }
        };

        var labels = LABKEY.vis.TimeChartHelper.generateLabels(mainTitle, this.chartInfo.axis);
        if (this.editMode && !forExport)
        {
            labels = {
                main : { // issue 16602: allow blank titles, but default to "Edit XXXX" for edit mode
                    value: labels.main.value == null || Ext4.util.Format.trim(labels.main.value) == "" ? "Edit Title" : labels.main.value,
                    lookClickable: this.editMode && !forExport,
                    listeners: { click: this.editMode ? mainTitleClickFn(this) : null }
                },
                x : {
                    value: labels.x.value == null || Ext4.util.Format.trim(labels.x.value) == "" ? "Edit Axis Label" : labels.x.value,
                    lookClickable: this.editMode && !forExport,
                    listeners: { click: this.editMode ? xAxisLabelClickFn(this) : null }
                },
                yLeft : {
                    value: labels.yLeft.value == null || Ext4.util.Format.trim(labels.yLeft.value) == "" ? "Edit Axis Label" : labels.yLeft.value,
                    lookClickable: this.editMode && !forExport,
                    listeners: { click: this.editMode ? yAxisLeftLabelClickFn(this) : null }
                },
                yRight : {
                    value: labels.yRight.value == null || Ext4.util.Format.trim(labels.yRight.value) == "" ? "Edit Axis Label" : labels.yRight.value,
                    lookClickable: this.editMode && !forExport,
                    listeners: { click: this.editMode ? yAxisRightLabelClickFn(this) : null }
                }
            };
        }

        return labels;
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            this.maskAndRemoveCharts();
            this.loaderFn = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

            // add a panel to put the queryWebpart in
            var qwpPanelDiv = Ext4.create('Ext.container.Container', {
                autoEl: {tag: 'div'}
            });
            var dataGridPanel = Ext4.create('Ext.panel.Panel', {
                minHeight: 620,
                autoScroll: true,
                border: false,
                padding: 10,
                items: [
                    {
                        xtype: 'displayfield',
                        value: 'Note: filters applied to the data grid will not be reflected in the chart view.',
                        style: 'font-style:italic;padding:10px'
                    },
                    qwpPanelDiv
                ]
            });

            // create the queryWebpart using the temp grid schema and query name
            var chartQueryWebPart = new LABKEY.QueryWebPart({
                renderTo: qwpPanelDiv.getId(),
                schemaName: this.tempGridInfo.schema,
                queryName: this.tempGridInfo.query,
                sort: this.tempGridInfo.sortCols ? this.tempGridInfo.sortCols.join(", ") : null,
                parameters: this.chartInfo.parameters,
                allowChooseQuery : false,
                allowChooseView  : false,
                allowHeaderLock  : false,
                title: "",
                frame: "none"
            });

            // re-enable the View Charts button once the QWP has rendered
            chartQueryWebPart.on('render', function(){
                this.viewChartBtn.enable();

                // redo the layout of the qwp panel to set reset the auto height
                qwpPanelDiv.doLayout();

                if (chartQueryWebPart.parameters)
                    this.updateQueryParameters(chartQueryWebPart);

                this.unmaskPanel();
            }, this);

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
        }
    },

    updateQueryParameters : function(qwp) {
        for (var param in qwp.parameters)
        {
            var pref = qwp.dataRegionName + ".param.";
            if (param.indexOf(pref) == 0) {
                this.parameters[param.replace(pref, "")] = qwp.parameters[param];
            }
        }
    },

    maskAndRemoveCharts: function() {
        // mask panel and remove the chart(s)
        if (!this.chart.getEl().isMasked())
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

    getChartInfoFromOptionPanels: function(){
        var config = {};

        // get the chart grouping information
        Ext4.apply(config, this.editorGroupingPanel.getPanelOptionValues());

        // get the chart aesthetic options information
        Ext4.apply(config, this.editorAestheticsPanel.getPanelOptionValues());

        // get the developer options information
        Ext4.apply(config, this.editorDeveloperPanel.getPanelOptionValues());

        // get the main title from the option panel
        Ext4.apply(config, this.editorMainTitlePanel.getPanelOptionValues());

        // get the measure panel information
        var measurePanelValues = this.editorMeasurePanel.getPanelOptionValues();

        config.measures = [];
        config.axis = [];
        config.filterUrl = measurePanelValues.dataFilterUrl;
        config.filterQuery = measurePanelValues.dataFilterQuery;

        // get the subject info based on the selected chart layout
        if (config.chartSubjectSelection == 'groups')
            config.subject = this.groupsSelector.getSubject(config.displayIndividual);
        else
            config.subject = this.participantSelector.getSubject();

        // get the x-axis information (including zero date column info)
        var xAxisValues = this.editorXAxisPanel.getPanelOptionValues();
        config.axis.push(xAxisValues.axis);

        // get the measure and dimension information for the y-axis (can be > 1 measure)
        var hasLeftAxis = false;
        var hasRightAxis = false;
        for(var i = 0; i < measurePanelValues.measuresAndDimensions.length; i++){
            var tempMD = {
                measure: measurePanelValues.measuresAndDimensions[i].measure,
                dimension: measurePanelValues.measuresAndDimensions[i].dimension,
                time: xAxisValues.time
            };

            if (tempMD.time == "date")
            {
                tempMD.dateOptions = {
                    dateCol: measurePanelValues.measuresAndDimensions[i].dateCol,
                    zeroDateCol: xAxisValues.zeroDateCol,
                    interval: xAxisValues.interval
                };
            }

            config.measures.push(tempMD);

            // add the left/right axis information to the config accordingly
            if (measurePanelValues.measuresAndDimensions[i].measure.yAxis == 'right' && !hasRightAxis)
            {
                config.axis.push(this.editorYAxisRightPanel.getPanelOptionValues());
                hasRightAxis = true;
            }
            else if (measurePanelValues.measuresAndDimensions[i].measure.yAxis == 'left' && !hasLeftAxis)
            {
                config.axis.push(this.editorYAxisLeftPanel.getPanelOptionValues());
                hasLeftAxis = true;
            }
        }

        // the subject column is used in the sort, so it needs to be applied to one of the measures
        if (config.measures.length > 0)
        {
            Ext4.apply(config.subject, {
                name: this.SUBJECT.columnName,
                schemaName: config.measures[0].measure.schemaName,
                queryName: config.measures[0].measure.queryName
            });
        }

        config.parameters = this.parameters;

        return config;
    },

    saveChart: function(saveChartInfo) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = this.saveReportInfo ? this.saveReportInfo.schemaName : (LABKEY.ActionURL.getParameter("schemaName") || null);
        var query = this.saveReportInfo ? this.saveReportInfo.queryName : (LABKEY.ActionURL.getParameter("queryName") || null);

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

            var msgBox = Ext4.create('Ext.window.Window', {
                title    : 'Success',
                html     : '<div style="margin-left: auto; margin-right: auto;"><span class="labkey-message">The chart has been successfully saved.</span></div>',
                modal    : false,
                closable : false,
                width    : 300,
                height   : 100
            });
            msgBox.show();
            msgBox.getEl().fadeOut({duration : 2250, callback : function(){
                msgBox.hide();
            }});

            // if a new chart was created, we need to refresh the page with the correct report name on the URL
            if (!this.editorSavePanel.isSavedReport() || !replace)
            {
                window.location = LABKEY.ActionURL.buildURL("visualization", "timeChartWizard",
                                    LABKEY.ActionURL.getContainer(),
                                    Ext4.apply(LABKEY.ActionURL.getParameters(), {
                                        reportId: result.visualizationId,
                                        name: reportName
                                    }));
            }
        }
    },

    // clear the chart panel of any messages, charts, or grids
    // if displaying a message, also make sure to unmask the time chart wizard element
    clearChartPanel: function(message){
        this.chart.removeAll();
        this.clearWarningText();
        if (message)
        {
            this.chart.add(Ext4.create('Ext.panel.Panel', {
                border: false,
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + message + "</td></tr></table>"
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
    },

    getDefaultPointClickFn: function() {
        return "function (data, columnMap, measureInfo, clickEvent) {\n"
            + "   var participant = columnMap[\"participant\"] ? \n"
            + "                     data[columnMap[\"participant\"]].value : null;\n"
            + "   var group = columnMap[\"group\"] ? \n"
            + "                     data[columnMap[\"group\"]].displayValue : null;\n\n"
            + "   // use LABKEY.ActionURL.buildURL to generate a link to a different \n"
            + "   // controller/action within LabKey server\n"
            + "   var ptidHref = LABKEY.ActionURL.buildURL('study', 'participant', \n"
            + "                  LABKEY.container.path, {participantId: participant});\n"
            + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', \n"
            + "                   LABKEY.container.path, {schemaName: measureInfo[\"schemaName\"], \n"
            + "                   \"query.queryName\": measureInfo[\"queryName\"]});\n\n"
            + "   // display a message box with some information from the function parameters\n"
            + "   var subjectLabel = (group != null ? 'Group: ' + group : \n"
            + "                'Participant: <a href=\"' + ptidHref + '\">' + participant + '</a>');\n"
            + "   Ext4.Msg.alert('Data Point Information',\n"
            + "       subjectLabel\n"
            + "       + '<br/> Interval: ' + data[columnMap[\"interval\"]].value\n"
            + "       + '<br/> Value: ' + data[columnMap[\"measure\"]].value\n"
            + "       + '<br/> Measure Name: ' + measureInfo[\"name\"]\n"
            + "       + '<br/> Schema Name: ' + measureInfo[\"schemaName\"]\n"
            + "       + '<br/> Query Name: <a href=\"' + queryHref + '\">' + \n"
            + "                    measureInfo[\"queryName\"] + '</a>'\n"
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
            + '<div style="margin-left: 60px;">Days: {value: 10},<br/>study_Dataset1_Measure1: {value: 250}<br/>study_Dataset1_ParticipantId: {value: "123456789"}</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>columnMap:</b> a mapping from participant, interval, and measure to use when looking up values in the data object</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">participant: "study_Dataset1_ParticipantId",<br/>measure: "study_Dataset1_Measure1"<br/>interval: "Days"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>measureInfo:</b> the schema name, query name, and measure name for the selected series</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 60px;">name: "Measure1",<br/>queryName: "Dataset1"<br/>schemaName: "study"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>';
    },

    getMeasurePickerHelpText: function() {
        return {title: 'Which measures are included?', text: 'This grid contains dataset columns that have been designated as measures from the dataset definition. '
            + '<br/><br/>It also includes measures from queries in the study schema that contain both the ' + this.SUBJECT.nounSingular + 'Id and ' + this.SUBJECT.nounSingular + 'Visit columns.'
            + '<br/><br/>You can filter the measures in the grid using the filter textbox to the left. The filtered results will contain measures that have a match in the dataset, measure, or description column. '
            + 'You can get back to the full list of measures at any time by removing the filter.'};
    }
});
