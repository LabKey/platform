/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

LABKEY.requiresScript('vis/src/geom.js');
LABKEY.requiresScript('vis/src/stat.js');
LABKEY.requiresScript('vis/src/position.js');
LABKEY.requiresScript('vis/src/scale.js');
LABKEY.requiresScript('vis/src/utils.js');
LABKEY.requiresScript('vis/src/layer.js');
LABKEY.requiresScript('vis/src/plot.js');
LABKEY.requiresScript('vis/lib/d3-2.0.4.js');
LABKEY.requiresScript('vis/lib/raphael-min.js');

LABKEY.requiresScript("vis/temp/overviewOptionsPanel.js");
LABKEY.requiresScript("vis/temp/saveOptionsPanel.js");
LABKEY.requiresScript("vis/temp/measureOptionsPanel.js");
LABKEY.requiresScript("vis/temp/yAxisOptionsPanel.js");
LABKEY.requiresScript("vis/temp/xAxisOptionsPanel.js");
LABKEY.requiresScript("vis/temp/chartsOptionsPanel.js");
LABKEY.requiresScript("vis/temp/statisticsOptionsPanel.js");
LABKEY.requiresScript("vis/temp/participantSelector.js");
LABKEY.requiresScript("vis/temp/groupSelector.js");

LABKEY.requiresCss("_images/icons.css");

Ext4.tip.QuickTipManager.init();
$h = Ext4.util.Format.htmlEncode;

Ext4.define('LABKEY.vis.TimeChartPanel', {

    extend : 'Ext.panel.Panel',

    constructor : function(config){
        // properties for this panel
        Ext4.apply(config, {
            layout: 'border',
            bodyStyle: 'background-color: white;',
            monitorResize: true,
            maxCharts: 30
        });

        // support backwards compatibility for charts saved prior to chartInfo reconfig (2011-08-31)
        if (config.chartInfo)
        {
            Ext4.applyIf(config.chartInfo, {
                axis: [],
                //This is for charts saved prior to 2011-10-07
                chartSubjectSelection: config.chartInfo.chartLayout == 'per_group' ? 'groups' : 'subjects',
                displayIndividual: true,
                displayAggregate: false,
                saveThumbnail: true
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
                    if (this.getAxisIndex(config.chartInfo.axis, md.axis.name, md.axis.side) == -1)
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
        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");

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
                    this.chart.add(Ext4.create('LABKEY.vis.OverviewOptionsPanel', {
                        listeners: {
                            scope: this,
                            'initialMeasuresStoreLoaded': function(data) {
                                // pass the measure store JSON data object to the measures panel
                                this.editorMeasurePanel.setMeasuresStoreData(data);
                            },
                            'initialMeasureSelected': function(initMeasure) {
                                this.editorMeasurePanel.addMeasure(initMeasure);
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

        this.subjectSelector = Ext4.create('LABKEY.vis.ParticipantSelector', {
            subject: (this.chartInfo.chartSubjectSelection != "groups" ? this.chartInfo.subject : {}),
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectColumn: this.viewInfo.subjectColumn,
            collapsed: this.chartInfo.chartSubjectSelection != "subjects",
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
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
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                }
            }
        });

        this.filtersPanel = Ext4.create('Ext.panel.Panel', {
            region: 'west',
            layout: 'accordion',
            fill: false,
            width: 220,
            border: true,
            split: true,
            collapsible: true,
            floatable: false,
            title: 'Filters',
            titleCollapse: true,
            items: [
                this.subjectSelector,
                this.groupsSelector
            ],
            listeners: {
                scope: this,
                'afterRender': function(){
                    this.setOptionsForGroupLayout(this.chartInfo.chartSubjectSelection == "groups");
                }
            }
        });
        items.push(this.filtersPanel);

        this.editorSavePanel = Ext4.create('LABKEY.vis.SaveOptionsPanel', {
            reportInfo: this.saveReportInfo,
            saveThumbnail: this.chartInfo.saveThumbnail == undefined ? true : this.chartInfo.saveThumbnail,
            canEdit: this.canEdit,
            canShare: this.canShare,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'saveChart': this.saveChart
            }
        });

        this.editorMeasurePanel = Ext4.create('LABKEY.vis.MeasureOptionsPanel', {
            origMeasures: this.chartInfo.measures,
            filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.chartInfo.filterQuery ? this.chartInfo.filterQuery : this.getFilterQuery(),
            viewInfo: this.viewInfo,
            filtersParentPanel: this.filtersPanel,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh) {
                    if(requiresDataRefresh)
                    {
                        this.measureSelectionChange(true);
                        this.refreshChart.delay(100);
                    }
                    else
                    {
                        this.loaderFn();
                    }
                }
            }
        });

        this.editorXAxisPanel = Ext4.create('LABKEY.vis.XAxisOptionsPanel', {
            axis: this.chartInfo.axis[xAxisIndex] ? this.chartInfo.axis[xAxisIndex] : {},
            zeroDateCol: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.zeroDateCol : {},
            interval: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.interval : "Days",
            time: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].time ? this.chartInfo.measures[0].time : 'date',
            timepointType: this.viewInfo.TimepointType,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                },
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete,
                'noDemographicData': this.disableOptionElements
            }
        });

        this.editorYAxisLeftPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            axis: this.chartInfo.axis[leftAxisIndex] ? this.chartInfo.axis[leftAxisIndex] : {side: "left"},
            defaultLabel: this.editorMeasurePanel.getDefaultLabel("left"),
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                }
            }
        });
        //Set radio/textfield names to aid with TimeChartTest.
        this.editorYAxisLeftPanel.rangeManualRadio.name = "leftaxis_range";
        this.editorYAxisLeftPanel.rangeAutomaticRadio.name = "leftaxis_range";
        this.editorYAxisLeftPanel.labelTextField.name = "left-axis-label-textfield";

        this.editorYAxisRightPanel = Ext4.create('LABKEY.vis.YAxisOptionsPanel', {
            axis: this.chartInfo.axis[rightAxisIndex] ? this.chartInfo.axis[rightAxisIndex] : {side: "right"},
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                }
            }
        });
        //Set radio/textfield names to aid with TimeChartTest.
        this.editorYAxisRightPanel.rangeManualRadio.name = "rightaxis_range";
        this.editorYAxisRightPanel.rangeAutomaticRadio.name = "rightaxis_range";
        this.editorYAxisRightPanel.labelTextField.name = "right-axis-label-textfield";

        this.editorChartsPanel = Ext4.create('LABKEY.vis.ChartsOptionsPanel', {
            chartLayout: this.chartInfo.chartLayout,
            chartSubjectSelection: this.chartInfo.chartSubjectSelection,
            mainTitle: this.chartInfo.title,
            lineWidth: this.chartInfo.lineWidth,
            hideDataPoints: this.chartInfo.hideDataPoints,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(this.editorChartsPanel.groupLayoutChanged == true){
                        this.editorChartsPanel.groupLayoutChanged = false;
                    }
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                },
                'groupLayoutSelectionChanged': this.setOptionsForGroupLayout
            }
        });

        this.editorStatisticsPanel = Ext4.create('LABKEY.vis.StatisticsOptionsPanel', {
            displayIndividual: this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
            displayAggregate: this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
            errorBars: this.chartInfo.errorBars != undefined ? this.chartInfo.errorBars : "None",
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(requiresDataRefresh){
                        this.refreshChart.delay(100);
                    }
                    else{
                        this.loaderFn();
                    }
                }
            }
        });

        this.loaderFn = this.renderLineChart;  // default is to show the chart
        this.loaderName = 'renderLineChart';
        this.viewGridBtn = Ext4.create('Ext.button.Button', {text: "View Data", handler: this.viewDataGrid, scope: this, disabled: true});
        this.viewChartBtn = Ext4.create('Ext.button.Button', {text: "View Chart(s)", handler: this.renderLineChart, scope: this, hidden: true});
        this.refreshChart = new Ext4.util.DelayedTask(function(){
            this.getChartData();
        }, this);

        // setup exportPDF button and menu (items to be added later)
        // the single button will be used for "single" chart layout
        // and the menu button will be used for multi-chart layouts
        this.exportPdfMenu = Ext4.create('Ext.menu.Menu', {cls: 'extContainer'});
        this.exportPdfMenuBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            menu: this.exportPdfMenu,
            hidden: true,
            scope: this
        });
        this.exportPdfSingleBtn = new Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
            disabled: true,
            scope: this
        });

        // setup buttons for the charting options panels (items to be added to the toolbar)
        this.measuresButton = Ext4.create('Ext.button.Button', {text: 'Measures', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorMeasurePanel);}, scope: this});
        this.chartsButton = Ext4.create('Ext.button.Button', {text: 'Chart(s)', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorChartsPanel);}, scope: this});
        this.xAxisButton = Ext4.create('Ext.button.Button', {text: 'X-Axis', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorXAxisPanel);}, scope: this});
        this.leftAxisButton = Ext4.create('Ext.button.Button', {text: 'Left-Axis', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorYAxisLeftPanel, 700);}, scope: this});
        this.rightAxisButton = Ext4.create('Ext.button.Button', {text: 'Right-Axis', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorYAxisRightPanel, 700);}, scope: this});
        this.statisticsButton = Ext4.create('Ext.button.Button', {text: 'Statistics', disabled: true,
                                handler: function(){this.showOptionsWindow(this.editorStatisticsPanel, 220);}, scope: this});

        this.saveButton = Ext4.create('Ext.button.Button', {text: 'Save', disabled: true, hidden: !this.canEdit, handler: function(){
                                this.editorSavePanel.setSaveAs(false);
                                this.showOptionsWindow(this.editorSavePanel);
                        }, scope: this});
        this.saveAsButton = Ext4.create('Ext.button.Button', {text: 'Save AS', disabled: true, hidden: !this.editorSavePanel.isSavedReport(), handler: function(){
                                this.editorSavePanel.setSaveAs(true);
                                this.showOptionsWindow(this.editorSavePanel);
                        }, scope: this});

        this.chart = Ext4.create('Ext.panel.Panel', {
            region: 'center',
            border: true,
            autoScroll: true,
            frame: false,
            tbar: [
                    this.viewGridBtn,
                    this.viewChartBtn,
                    this.exportPdfSingleBtn,
                    this.exportPdfMenuBtn,
                    this.measuresButton,
                    this.chartsButton,
                    this.xAxisButton,
                    this.leftAxisButton,
                    this.rightAxisButton,
                    this.statisticsButton,
                    '->',
                    this.saveButton,
                    this.saveAsButton
            ],
            items: [],
            listeners: {
                scope: this,
                'resize': this.resizeCharts
            }
        });
        items.push(this.chart);

        Ext4.applyIf(this, {
            autoResize: true
        });

        if (this.autoResize)
        {
            Ext4.EventManager.onWindowResize(function(w,h){
                this.resizeToViewport(w,h);
            }, this);
        }

        this.items = items;

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        this.callParent();
    },

    resizeCharts : function(){
        // only call loader if the data object is available and the loader equals renderLineChart
        if((this.individualData || this.aggregateData) && this.loaderName == 'renderLineChart') {
            this.loaderFn();
        }
    },

    showOptionsWindow : function(panel, width) {
        var optionWindow = Ext4.create('Ext.window.Window', {
            floating: true,
            cls: 'extContainer',
            bodyStyle: 'background-color: white;',
            padding: 10,
            header: false,
            frame: true,
            closable: false,
            shadow: false,
            layout: 'fit',
            width: width || 850,
            autoHeight: true,
            modal: true,
            closeAction: 'close',
            items: [panel],
            listeners: {
                'closeOptionsWindow': function() {     // TODO: fix the z-index issue with reshowing combobox in window
                    optionWindow.close();
                },
                scope: this
            }
        });
        optionWindow.show(this);
    },

    setOptionsForGroupLayout : function(groupLayoutSelected){
        if (groupLayoutSelected)
        {
            this.subjectSelector.hide();
            this.groupsSelector.show();
            this.statisticsButton.enable();
        }
        else
        {
            this.groupsSelector.hide();
            this.subjectSelector.show();
            this.statisticsButton.disable();
        }
    },

    resizeToViewport : function(w,h) {
        if (!this.rendered)
            return;

        var padding = [20,0];
        var xy = this.el.getXY();
        var size = {
            width : Math.max(800,w-xy[0]-padding[0])
        };
        this.setWidth(size);
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
            this.subjectSelector.getSubjectValues();
            this.editorXAxisPanel.setZeroDateStore();
        }

        // these method calls should be made for all measure selections
        this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
        this.editorChartsPanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());

        // if all of the measures have been removed, disable any non-relevant elements
        if (this.editorMeasurePanel.getNumMeasures() == 0)
            this.disableNonMeasureOptionButtons();
        else
            this.enableOptionButtons();
    },

    enableOptionButtons: function(){
        this.measuresButton.enable();
        this.chartsButton.enable();
        this.xAxisButton.enable();
        this.saveButton.enable();
        this.saveAsButton.enable();
    },

    disableNonMeasureOptionButtons: function(){
        this.chartsButton.disable();
        this.xAxisButton.disable();
        this.leftAxisButton.disable();
        this.rightAxisButton.disable();
        this.statisticsButton.disable();

        this.exportPdfMenuBtn.disable();
        this.exportPdfSingleBtn.disable();
        this.viewGridBtn.disable();
        this.viewChartBtn.disable();
    },

    disableOptionElements: function(){
        this.measuresButton.disable();
        this.chartsButton.disable();
        this.xAxisButton.disable();
        this.leftAxisButton.disable();
        this.rightAxisButton.disable();
        this.statisticsButton.disable();
        this.saveButton.disable();
        this.saveAsButton.disable();
        this.filtersPanel.disable();
        this.chart.ownerCt.getEl().mask("There are no demographic date options available in this study. "
                            + "Please contact an administrator to have them configure the study to work with the Time Chart wizard.");
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
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
        // mask panel and remove the chart(s)
        this.chart.getEl().mask("loading...");
        this.clearChartPanel();

        // Clear previous chart data.
        this.individualData = undefined;
        this.aggregateData = undefined;

        this.loaderCount = 0; //Used to prevent the loader from running until we have recieved all necessary callbacks.
        if (this.statisticsButton.disabled || (!this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayIndividual()))
            this.loaderCount++;

        if (!this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayAggregate())
            this.loaderCount++;

        if (this.loaderCount == 0)
        {
            this.clearChartPanel("Please select either \"Show Individual Lines\" or \"Show Mean\".");
            return;
        }

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();

        if (this.chartInfo.measures.length == 0)
        {
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
           return;
        }

        if (this.statisticsButton.disabled || (!this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayIndividual()))
        {
            //Get data for individual lines.
            LABKEY.Visualization.getData({
                success: function(data){
                    // store the data in an object by subject for use later when it comes time to render the line chart
                    this.individualData = data;
                    this.markDirty(!this.editorSavePanel.isSavedReport()); // only mark when editing unsaved report
                    var gridSortCols = [];

                    // make sure each measure/dimension has at least some data
                    var seriesList = this.getSeriesList();
                    this.individualHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.individualHasData[s.name] = false;
                    }, this);

                    // store the temp schema name, query name, etc. for the data grid
                    this.tempGridInfo = {schema: this.individualData.schemaName, query: data.queryName,
                        subjectCol: data.measureToColumn[this.viewInfo.subjectColumn],
                        sortCols: this.editorXAxisPanel.getTime() == "date" ? gridSortCols : [this.individualData.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]]
                    };

                    // now that we have the temp grid info, enable the View Data button
                    // and make sure that the view charts button is hidden
                    this.viewGridBtn.setDisabled(false);
                    this.viewChartBtn.hide();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                sorts: this.getDataSortArray(),
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }

        if (!this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayAggregate())
        {
            //Get data for Aggregates.
            var groups = [];
            for(var i = 0; i < this.chartInfo.subject.groups.length; i++){
                groups.push(this.chartInfo.subject.groups[i].id);
            }

            LABKEY.Visualization.getData({
                success: function(data){
                    this.aggregateData = data;
                    // make sure each measure/dimension has at least some data
                    var seriesList = this.getSeriesList();
                    this.aggregateHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.aggregateHasData[s.name] = false;
                    }, this);

                    // now that we have the temp grid info, enable the View Data button
                    // and make sure that the view charts button is hidden
                    this.viewGridBtn.setDisabled(false);
                    this.viewChartBtn.hide();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                groupBys: [{schemaName: 'study', queryName: this.viewInfo.subjectNounSingular + 'GroupMap', name: 'GroupId/CategoryId', values: groups}],
                sorts: this.getDataSortArray(),
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }
    },

    getSimplifiedConfig: function(config)
    {
        // Here we generate a config that is similar, but strips out info that isnt neccessary.
        // We use this to compare two configs to see if the user made any changes to the chart.
        var simplified = {};
        simplified.axis = config.axis;
        simplified.chartLayout = config.chartLayout;
        simplified.chartSubjectSelection = config.chartSubjectSelection;
        simplified.displayAggregate = config.displayAggregate;
        simplified.displayIndividual = config.displayIndividual;
        simplified.filterUrl = config.filterUrl;
        simplified.hideDataPoints = config.hideDataPoints;
        simplified.lineWidth = config.lineWidth;
        simplified.saveThumbnail = config.saveThumbnail;
        simplified.subject = config.subject;
        simplified.title = config.title;

        simplified.measures = [];
        for(var i = 0; i < config.measures.length; i++){
            var measure = {};
            measure = config.measures[i]
            //Delete id's, they're just for the Ext components ordering.
            if(measure.dateOptions){
                delete measure.dateOptions.zeroDateCol.id;
                delete measure.dateOptions.dateCol.id;
            }
            simplified.measures.push(measure);
        }

        return Ext4.encode(simplified);
    },

    renderLineChart: function(force)
    {
        // mask panel and remove the chart(s)
        this.chart.getEl().mask("loading...");
        this.clearChartPanel("loading...");

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
                //Don't mark dirty if the user can't edit the report, that's just mean.
                if (this.canEdit)
                {
                    this.markDirty(true);
                }
            }
        }

        // enable/disable the left and right axis panels
        (this.getAxisIndex(this.chartInfo.axis, "y-axis", "left") > -1 ? enableAndSetRangeOptions(this.leftAxisButton, this.editorYAxisLeftPanel) : this.leftAxisButton.disable());
        (this.getAxisIndex(this.chartInfo.axis, "y-axis", "right") > -1 ? enableAndSetRangeOptions(this.rightAxisButton, this.editorYAxisRightPanel) : this.rightAxisButton.disable());

        function enableAndSetRangeOptions(yAxisButton, yAxisPanel){
            //We have to call setRangeFormOptions AFTER we enable the panel, otherwise all fields get enabled.
            yAxisButton.enable();
            yAxisPanel.setRangeFormOptions(yAxisPanel.axis.range.type);
        }

        if (!this.editorYAxisLeftPanel.userEditedLabel)
            this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        if (!this.editorYAxisRightPanel.userEditedLabel)
            this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));

        if (this.individualData && this.individualData.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.individualData.filterDescription);

        if(this.chartInfo.measures.length == 0){
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
           return;
        }

        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");
        if(xAxisIndex == -1){
           Ext4.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();
        this.loaderFn = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // TODO: report if a series doesn't have any data.

	    // one series per y-axis subject/measure/dimensionvalue combination
	    var seriesList = this.getSeriesList();

        // TODO: Use the same max/min for every chart if displaying more than one chart.

//        if (this.chartInfo.chartLayout != "single")
//        {
//            //ISSUE In multi-chart case, we need to precompute the default axis ranges so that all charts share them.
//            //Should have more of this capability in ChartComponent (essentially need to build a single chart with all data)
//            //but didn't want to refactor that code substantially..
//            var allX = [];
//            var allLeft = [];
//            var allRight = [];

//            Ext4.each(series, function (ser) {
//                Ext4.each(ser.data, function(row) {
//                    var xValue = row.interval;
//                    var yValue = row.dataValue;
//                    if (xValue != null && typeof xValue == "object")
//                        xValue = xValue.value;
//                    if (yValue != null && typeof yValue == "object")
//                        yValue = yValue.value;
//                    if (xValue != null && yValue != null) {
//                        allX.push(xValue);
//                        if(ser.axis == "left"){
//                            allLeft.push(yValue);
//                        } else {
//                            allRight.push(yValue);
//                        }
//                    }
//                })
//            });
//            this.autoAxisRange = {
//                x:LABKEY.vis.getAxisRange(allX, this.chartInfo.axis[xAxisIndex].scale)
//            };
//            if (leftAxisIndex > -1) {
//                this.autoAxisRange.left = LABKEY.vis.getAxisRange(allLeft, this.chartInfo.axis[leftAxisIndex].scale);
//            }
//            if (rightAxisIndex > -1) {
//                this.autoAxisRange.right = LABKEY.vis.getAxisRange(allRight, this.chartInfo.axis[rightAxisIndex].scale);
//            }
//        }
//        else   //Use an undefined min & max so that chart computes it
//            this.autoAxisRange = {x:{}, left:{}, right:{}}; //Let the chart compute this

        // remove any existing charts, purge listeners from exportPdfSingleBtn, and remove items from the exportPdfMenu button
        this.chart.removeAll();
        this.firstChartComponent = null;
        this.exportPdfSingleBtn.removeListener('click');
        this.exportPdfMenu.removeAll();

        var charts = [];
        var generateParticipantSeries = function(data, subjectColumn){
            // subjectColumn is this.viewInfo.subjectColumn
            subjectColumn = data.measureToColumn[subjectColumn];
            var rows = data.rows;
            var dataByParticipant = {};

            for(var i = 0; i < rows.length; i++){
                var rowSubject = rows[i][subjectColumn].value;
                // Split the data into groups.
                if(!dataByParticipant[rowSubject]){
                    dataByParticipant[rowSubject] = [];
                }

                dataByParticipant[rowSubject].push(rows[i]);

            }
            return dataByParticipant;
        };

        var generateGroupSeries = function(data, groups, subjectColumn){
            // subjectColumn is this.viewInfo.subjectColumn
            // groups is this.chartInfo.subject.groups
            var rows = data.rows;
            var subject = data.measureToColumn[subjectColumn];
            var dataByGroup = {};

            for(var i = 0; i < rows.length; i++){
                var rowSubject = rows[i][subject].value;
                for(var j = 0; j < groups.length; j++){
                    if(groups[j].participantIds.indexOf(rowSubject) > -1){
                        if(!dataByGroup[groups[j].label]){
                            dataByGroup[groups[j].label] = [];
                        }
                        dataByGroup[groups[j].label].push(rows[i]);
                    }
                }
            }

            return dataByGroup;
        };

        var createExportMenuHandler = function(id){
            return function(){
                LABKEY.vis.SVGConverter.convert(Ext4.get(id).child('svg').dom, 'pdf');
            }
        };

        // four options: all series on one chart, one chart per subject, one chart per group, or one chart per measure/dimension
        if (this.chartInfo.chartLayout == "per_subject")
        {
            // warn if user doesn't have an subjects selected
            if (this.chartInfo.subject.values.length == 0)
            {
                this.clearChartPanel("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + ".");
            }
            else
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.values.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                var dataPerParticipant = generateParticipantSeries(this.individualData, this.viewInfo.subjectColumn);
                for(var participant in dataPerParticipant){
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + participant,
                            seriesList,
                            dataPerParticipant[participant],
                            this.individualData.measureToColumn,
                            this.individualData.visitMap,
                            null,
                            null,
                            null,
                            this.chartInfo.subject.values.length > 1 ? 380 : 600  // chart height
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + participant,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
        }
        else if (this.chartInfo.chartLayout == "per_group")
        {
            // warn if use doesn't have any groups selected
            if (this.chartInfo.subject.groups.length == 0)
            {
                this.clearChartPanel("Please select at least one group.");
            }
            else
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.groups.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                //Display individual lines
                var groupData = generateGroupSeries(this.individualData, this.chartInfo.subject.groups, this.viewInfo.subjectColumn);

                for (var i = 0; i < (this.chartInfo.subject.groups.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.groups.length); i++)
                {
                    var group = this.chartInfo.subject.groups[i];
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + group.label,
                            seriesList,
                            groupData[group.label],
                            this.individualData.measureToColumn,
                            this.individualData.visitMap,
                            null,
                            null,
                            null,
                            this.chartInfo.subject.groups.length > 1 ? 380 : 600 // chart height
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + group.label,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
        }
        else if (this.chartInfo.chartLayout == "per_dimension")
        {
            if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0)
            {
                this.clearChartPanel("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + '.');
            } else if(this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1){
                this.clearChartPanel("Please select at least one group.");
            } else {
                // warn if user doesn't have an dimension values selected or if the max number has been exceeded
                if (seriesList.length == 0)
                {
                    this.clearChartPanel("Please select at least one dimension value.");
                }
                else if (seriesList.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts");
                }
                for (var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++)
                {
                    var newChart = this.generatePlot(
                            this.chart,
                            this.editorXAxisPanel.getTime(),
                            this.viewInfo,
                            this.chartInfo,
                            this.chartInfo.title + ': ' + seriesList[i].name,
                            [seriesList[i]],
                            this.individualData.rows,
                            this.individualData.measureToColumn,
                            this.individualData.visitMap,
                            null,
                            null,
                            null,
                            seriesList.length > 1 ? 380 : 600  // chart height
                        );
                    charts.push(newChart);

                    if(!this.firstChartComponent){
                        this.firstChartComponent = newChart.renderTo;
                    }

                    this.exportPdfMenu.add({
                        text: this.chartInfo.title + ': ' + seriesList[i].name,
                        handler: createExportMenuHandler(newChart.renderTo),
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);

                    if(charts.length > this.maxCharts){
                        break;
                    }
                }
            }
        }
        else if (this.chartInfo.chartLayout == "single")
        {
            if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0){
                this.clearChartPanel("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + '.');
            } else if(this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1){
                this.clearChartPanel("Please select at least one group.");
            } else {
                //Single Line Chart, with all participants or groups.
                var newChart = this.generatePlot(
                        this.chart,
                        this.editorXAxisPanel.getTime(),
                        this.viewInfo,
                        this.chartInfo,
                        this.chartInfo.title,
                        seriesList,
                        this.individualData ? this.individualData.rows : null,
                        this.individualData ? this.individualData.measureToColumn : null,
                        this.individualData ? this.individualData.visitMap : null,
                        this.aggregateData ? this.aggregateData.rows : null,
                        this.aggregateData ? this.aggregateData.measureToColumn : null,
                        this.aggregateData ? this.aggregateData.visitMap : null,
                        600    // chart height
                );
                charts.push(newChart);

                this.firstChartComponent = newChart.renderTo;

                this.exportPdfSingleBtn.addListener('click', function(){
                    LABKEY.vis.SVGConverter.convert(Ext4.get(newChart.renderTo).child('svg').dom, 'pdf');
                }, this);

                this.toggleExportPdfBtns(true);
            }
        }

        // if the user has selected more charts than the max allowed, or nothing at all, show warning
        if(this.warningText.length > 0){
            this.chart.add(Ext4.create('Ext.form.field.Display', {
                autoHeight: 25,
                autoWidth: true,
                value: this.warningText,
                style: "font-style:italic;width:100%;padding:5px;text-align:center;"
            }));
        }

        // unmask the panel if needed
        if (this.chart.getEl().isMasked()){
            this.chart.getEl().unmask();
        }
    },

    generatePlot: function(chart, studyType, viewInfo, chartInfo, mainTitle, seriesList, individualData, individualMeasureToColumn, individualVisitMap, aggregateData, aggregateMeasureToColumn, aggregateVisitMap, chartHeight){
        // This function generates a plot config and renders a plot for given data.
        // Should be used in per_subject, single, per_measure, and per_group
        var generateLayerAes = function(name, yAxisSide, columnName, intervalKey, subjectColumn, hoverText){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return parseFloat(row[columnName].value)}; // Have to parseFlot because for some reason ObsCon from Luminex was returning strings not floats/ints.
            if(hoverText){
                aes.hoverText = function(row){return row[subjectColumn].value + ' '  + name + ', ' + intervalKey + ' ' + row[intervalKey].value + ', ' + row[columnName].value};
            }
            return aes;
        };

        var generateAggregateLayerAes = function(name, yAxisSide, columnName, intervalKey, subjectColumn, hoverText, errorColumn, errorType){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return parseFloat(row[columnName].value)}; // Have to parseFlot because for some reason ObsCon from Luminex was returning strings not floats/ints.
            if(hoverText){
                if(errorColumn){
                    aes.hoverText = function(row){
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return row[subjectColumn].displayValue + ' ' + name + ', '
                                + intervalKey + ' ' + row[intervalKey].value + ', ' + row[columnName].value + ' '
                                + errorType + ': ' + errorVal
                    };
                } else {
                    aes.hoverText = function(row){return row[subjectColumn].displayValue + ' '  + name + ', ' + intervalKey + ' ' + row[intervalKey].value + ', ' + row[columnName].value};
                }
            }
            aes.group = aes.color = aes.shape = function(row){return row[subjectColumn].displayValue};
            aes.error = function(row){return row[errorColumn].value};
            return aes;
        };

        var layers = [];
        var xTitle = '', yLeftTitle = '', yRightTitle = '';
        var yLeftMin = null, yLeftMax = null, yLeftTrans = null;
        var yRightMin = null, yRightMax = null, yRightTrans = null;
        var xMin = null, xMax = null, xTrans = null;
        var intervalKey = null;
        var individualSubjectColumn = individualMeasureToColumn ? individualMeasureToColumn[viewInfo.subjectColumn] : null;
        var aggregateSubjectColumn = "CategoryId";
        var newChartDiv = Ext4.create('Ext.container.Container', {
            height: chartHeight,
            border: 1,
            autoEl: {tag: 'div'}
        });
        chart.add(newChartDiv);

        for(var i = 0; i < chartInfo.axis.length; i++){
            var axis = chartInfo.axis[i];
            if(axis.name == "y-axis"){
                if(axis.side == "left"){
                    yLeftTitle = axis.label;
                    yLeftMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yLeftMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yLeftTrans = axis.scale ? axis.scale : "linear";
                } else {
                    yRightTitle = axis.label;
                    yRightMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yRightMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yRightTrans = axis.scale ? axis.scale : "linear";
                }
            } else {
                xTitle = axis.label;
                xMin = typeof axis.range.min == "number" ? axis.range.min : null;
                xMax = typeof axis.range.max == "number" ? axis.range.max : null;
                xTrans = axis.scale ? axis.scale : "linear";
            }
        }

        for(var i = seriesList.length -1; i >= 0; i--){
            var chartSeries = seriesList[i];
            intervalKey = studyType == "date" ?
                    chartInfo.measures[chartSeries.measureIndex].dateOptions.interval :
                    individualMeasureToColumn ?
                            individualMeasureToColumn[viewInfo.subjectNounSingular + "Visit/Visit"] :
                            aggregateMeasureToColumn[viewInfo.subjectNounSingular + "Visit/Visit"];

            var columnName = individualMeasureToColumn ? individualMeasureToColumn[chartSeries.name] : aggregateMeasureToColumn[chartSeries.name];
            if(individualData && individualMeasureToColumn){
                var pathLayerConfig = {
                    name: chartSeries.name,
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateLayerAes(chartSeries.name, chartSeries.yAxisSide, columnName, intervalKey, individualSubjectColumn, false)
                };
                layers.push(new LABKEY.vis.Layer(pathLayerConfig));

                if(!chartInfo.hideDataPoints){
                    var pointLayerConfig = {
                        name: chartSeries.name,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateLayerAes(chartSeries.name, chartSeries.yAxisSide, columnName, intervalKey, individualSubjectColumn, true)
                    };
                    layers.push(new LABKEY.vis.Layer(pointLayerConfig));
                }
            }

            if(aggregateData && aggregateMeasureToColumn){
                var errorBarType = null;
                if(chartInfo.errorBars == 'SD'){
                    errorBarType = '_STDDEV';
                } else if(chartInfo.errorBars == 'SEM'){
                    errorBarType = '_STDERR';
                }
                var errorColumnName = errorBarType ? aggregateMeasureToColumn[chartSeries.name] + errorBarType : null;

                var aggregatePathLayerConfig = {
                    name: chartSeries.name,
                    data: aggregateData,
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateAggregateLayerAes(chartSeries.name, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, false, errorColumnName, chartInfo.errorBars)
                };
                layers.push(new LABKEY.vis.Layer(aggregatePathLayerConfig));

                if(errorColumnName){
                    var aggregateErrorLayerConfig = {
                        name: chartSeries.name,
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.ErrorBar(),
                        aes: generateAggregateLayerAes(chartSeries.name, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, false, errorColumnName, chartInfo.errorBars)
                    };
                    layers.push(new LABKEY.vis.Layer(aggregateErrorLayerConfig));
                }

                if(!chartInfo.hideDataPoints){
                    var aggregatePointLayerConfig = {
                        name: chartSeries.name,
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateAggregateLayerAes(chartSeries.name, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, true, errorColumnName, chartInfo.errorBars)
                    };
                    layers.push(new LABKEY.vis.Layer(aggregatePointLayerConfig));
                }
            }
        }

        var xAes, xTickFormat, tickMap = {};
        var visitMap = individualVisitMap ? individualVisitMap : aggregateVisitMap;

        for(var rowId in visitMap){
            var visitDisplayOrder = visitMap[rowId].displayOrder;
            var visitLabel = visitMap[rowId].displayName;
            tickMap[visitDisplayOrder] = visitLabel;
        }

        if(studyType == 'date'){
            xAes = function(row){return row[intervalKey].value}
        } else {
            xAes = function(row){
                console.log(intervalKey, row)
                return individualVisitMap[row[intervalKey].value].displayOrder;
            };
            xTickFormat = function(value){
                return tickMap[value];
            }
        }

        var plotConfig = {
            renderTo: newChartDiv.getId(),
            labels: {
                main: {value: mainTitle},
                x: {value: xTitle},
                yLeft: {value: yLeftTitle},
                yRight: {value: yRightTitle}
            },
            layers: layers,
            aes: {
                x: xAes,
                color: function(row){return row[individualSubjectColumn].value},
                group: function(row){return row[individualSubjectColumn].value},
                shape: function(row){
                    return row[individualSubjectColumn].value
                }
            },
            scales: {
                x: {
                    scaleType: 'continuous',
                    trans: xTrans,
                    min: xMin,
                    max: xMax,
                    tickFormat: xTickFormat ? xTickFormat : null
                },
                yLeft: {scaleType: 'continuous',
                    trans: yLeftTrans,
                    min: yLeftMin,
                    max: yLeftMax
                },
                yRight: {
                    scaleType: 'continuous',
                    trans: yRightTrans,
                    min: yRightMin,
                    max: yRightMax
                },
                shape: {
                    scaleType: 'discrete'
                }
            },
            width: newChartDiv.getWidth() - 20, // -20 prevents horizontal scrollbars in cases with multiple charts.
            height: newChartDiv.getHeight() - 20, // -20 prevents vertical scrollbars in cases with one chart.
            data: individualData ? individualData : aggregateData // There is currently a bug where if there is no data at the plot level then the chart doesn't render properly.
        };

        var plot = new LABKEY.vis.Plot(plotConfig);
        plot.render();
        return plot;
    },

    getDataSortArray: function(){
        var arr = [this.chartInfo.subject]
        var md = this.chartInfo.measures[0];

        var sort1 = {
            schemaName: md.dateOptions? md.dateOptions.dateCol.schemaName : md.measure.schemaName,
            queryName: md.dateOptions ? md.dateOptions.dateCol.queryName : md.measure.queryName,
            name: this.editorXAxisPanel.getTime() == "date" ? md.dateOptions.dateCol.name : this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"
        };
        arr.push(sort1);

        var sort2 = {
            schemaName: md.dateOptions? md.dateOptions.dateCol.schemaName : md.measure.schemaName,
            queryName: md.dateOptions ? md.dateOptions.dateCol.queryName : md.measure.queryName,
            name: this.viewInfo.subjectNounSingular + "Visit/Visit"
        };
        arr.push(sort2);

        return arr;
    },

    getSeriesList: function(){
        var arr = [];
        for (var i = 0; i < this.chartInfo.measures.length; i++)
        {
            md = this.chartInfo.measures[i];

            if(md.dimension && md.dimension.values) {
                Ext4.each(md.dimension.values, function(val) {
                    arr.push({
                        name: val,
                        measureIndex: i,
                        yAxisSide: md.measure.yAxis
                    });
                });
            }
            else {
                arr.push({
                    name: md.measure.name,
                    measureIndex: i,
                    yAxisSide: md.measure.yAxis
                });
            }
        }
        return arr;
    },

    toggleExportPdfBtns: function(showSingle) {
        if(showSingle){
            this.exportPdfSingleBtn.show();
            this.exportPdfSingleBtn.setDisabled(false);
            this.exportPdfMenuBtn.hide();
            this.exportPdfMenuBtn.setDisabled(true);
        }
        else{
            this.exportPdfSingleBtn.hide();
            this.exportPdfSingleBtn.setDisabled(true);
            this.exportPdfMenuBtn.show();
            this.exportPdfMenuBtn.setDisabled(false);
        }
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            // mask panel and remove the chart(s)
            this.chart.getEl().mask("loading...");
            this.clearChartPanel();
            this.loaderFn = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.disable();
            this.viewChartBtn.show();
            this.viewGridBtn.hide();

            // add a panel to put the queryWebpart in
            var qwpPanelDiv = Ext4.create('Ext.container.Container', {
                autoHeight: true,
                anchor: '100%',
                autoEl: {tag: 'div'}
            });
            var dataGridPanel = Ext4.create('Ext.panel.Panel', {
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
                sort: this.tempGridInfo.subjectCol + ', ' + this.tempGridInfo.sortCols.join(", "),
                allowChooseQuery: false,
                allowChooseView: false,
                title: "",
                frame: "none"
            });

            // re-enable the View Charts button once the QWP has rendered
            chartQueryWebPart.on('render', function(){
                this.viewChartBtn.enable();

                // redo the layout of the qwp panel to set reset the auto height
                qwpPanelDiv.doLayout();

                // unmask the panel if needed
                if (this.chart.getEl().isMasked())
                    this.chart.getEl().unmask();
            }, this);

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
        }
    },

    getInitializedChartInfo: function(){
        return {
            measures: [],
            axis: [],
            chartLayout: 'single',
            chartSubjectSelection: 'subjects',
            lineWidth: 3,
            hideDataPoints: false,
            subject: {},
            title: '',
            filterUrl: LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.getFilterQuery()
        }
    },

    getChartInfoFromOptionPanels: function(){
        var config = {
            title: this.editorChartsPanel.getMainTitle(),
            chartLayout: this.editorChartsPanel.getChartLayout(),
            chartSubjectSelection: this.editorChartsPanel.getChartSubjectSelection(),
            lineWidth: this.editorChartsPanel.getLineWidth() || 3, //Default line width of 3.
            hideDataPoints: this.editorChartsPanel.getHideDataPoints(),
            measures: [],
            axis: [this.editorXAxisPanel.getAxis()],
            filterUrl: this.editorMeasurePanel.getDataFilterUrl(),
            filterQuery: this.editorMeasurePanel.getDataFilterQuery(),
            saveThumbnail: this.editorSavePanel.getSaveThumbnail()
        };

        // get the subject info based on the selected chart layout
        if (config.chartSubjectSelection == 'groups')
            config.subject = this.groupsSelector.getSubject();
        else
            config.subject = this.subjectSelector.getSubject();

        // get the measure and dimension information for the y-axis (can be > 1 measure)
        var hasLeftAxis = false;
        var hasRightAxis = false;
        var yAxisMeauresDimensions = this.editorMeasurePanel.getMeasuresAndDimensions();
        for(var i = 0; i < yAxisMeauresDimensions.length; i++){
            var tempMD = {
                measure: yAxisMeauresDimensions[i].measure,
                dimension: yAxisMeauresDimensions[i].dimension,
                time: this.editorXAxisPanel.getTime()
            };

            if (tempMD.time == "date")
            {
                tempMD.dateOptions = {
                    dateCol: yAxisMeauresDimensions[i].dateCol,
                    zeroDateCol: this.editorXAxisPanel.getZeroDateCol(),
                    interval: this.editorXAxisPanel.getInterval()
                };
            }

            config.measures.push(tempMD);

            // add the left/right axis information to the config accordingly
            if (yAxisMeauresDimensions[i].measure.yAxis == 'right' && !hasRightAxis)
            {
                config.axis.push(this.editorYAxisRightPanel.getAxis());
                hasRightAxis = true;
            }
            else if (yAxisMeauresDimensions[i].measure.yAxis == 'left' && !hasLeftAxis)
            {
                config.axis.push(this.editorYAxisLeftPanel.getAxis());
                hasLeftAxis = true;
            }
        }

        // the subject column is used in the sort, so it needs to be applied to one of the measures
        if (config.measures.length > 0)
        {
            Ext4.apply(config.subject, {
                name: this.viewInfo.subjectColumn,
                schemaName: config.measures[0].measure.schemaName,
                queryName: config.measures[0].measure.queryName
            });
        }

        // get the statistics/aggregate information (dependent on the enabled state of the statistics button
        config.displayIndividual = this.statisticsButton.disabled || (!this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayIndividual());
        config.displayAggregate = !this.statisticsButton.disabled && this.editorStatisticsPanel.getDisplayAggregate();
        config.errorBars = this.statisticsButton.disabled ? "None" : this.editorStatisticsPanel.getErrorBars();

        return config;
    },

    getAxisIndex: function(axes, axisName, side){
        var index = -1;
        for(var i = 0; i < axes.length; i++){
            if (!side && axes[i].name == axisName)
            {
                index = i;
                break;
            }
            else if (axes[i].name == axisName && axes[i].side == side)
            {
                index = i;
                break;
            }
        }
        return index;
    },

    saveChart: function(saveChartInfo) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = this.saveReportInfo ? this.saveReportInfo.schemaName : (LABKEY.ActionURL.getParameter("schemaName") || null);
        var query = this.saveReportInfo ? this.saveReportInfo.queryName : (LABKEY.ActionURL.getParameter("queryName") || null);

        var reportSvg = (this.firstChartComponent && Raphael.svg ? LABKEY.vis.SVGConverter.svgToStr(Ext4.get(this.firstChartComponent).child('svg').dom) : null);

        var config = {
            replace: saveChartInfo.replace,
            reportName: saveChartInfo.reportName,
            reportDescription: saveChartInfo.reportDescription,
            reportShared: saveChartInfo.reportShared,
            reportSaveThumbnail: saveChartInfo.saveThumbnail,
            reportSvg: saveChartInfo.saveThumbnail ? reportSvg : null,
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
        LABKEY.Visualization.get({
            name: config.reportName,
            success: function(result, request, options){
                // a report by that name already exists within the container, if the user can update, ask if they would like to replace
                if(this.canEdit && config.replace){
                    Ext4.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists. Would you like to replace it?',
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
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists.  Please choose a different name.',
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

        LABKEY.Visualization.save({
            name: config.reportName,
            description: config.reportDescription,
            shared: config.reportShared,
            visualizationConfig: this.chartInfo,
            saveThumbnail: config.reportSaveThumbnail,
            svg: config.reportSvg,
            replace: config.replace,
            type: LABKEY.Visualization.Type.TimeChart,
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
            if (this.chart.getEl().isMasked)
                this.chart.getEl().unmask();
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
