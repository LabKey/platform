/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

LABKEY.requiresScript("vis/chartEditorOverviewPanel.js");
LABKEY.requiresScript("vis/chartEditorMeasurePanel.js");
LABKEY.requiresScript("vis/chartEditorYAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorXAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorChartsPanel.js");
LABKEY.requiresScript("vis/subjectSeriesSelector.js");


Ext.QuickTips.init();

var ONE_DAY = 1000 * 60 * 60 * 24;
var ONE_WEEK = ONE_DAY * 7;
var ONE_MONTH = ONE_WEEK * (52/12);
var ONE_YEAR = ONE_DAY * 365;

LABKEY.vis.TimeChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.apply(this, config);

        LABKEY.vis.TimeChartPanel.superclass.constructor.call(this);
    },

    initComponent : function() {
        // properties for this panel
        this.layout = 'border';

        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
        }
        console.log(this.chartInfo);

        // hold on to the x and y axis measure index
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // add a listener to call measureSelected on render if this is a saved chart
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object") {
                    // todo: change this to not have to pass a measure
                    this.measureSelected(this.chartInfo.measures[yAxisMeasureIndex].measure, false);
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        if(this.viewInfo.type == "line") {
            this.chartEditorOverviewPanel = new LABKEY.vis.ChartEditorOverviewPanel({
                listeners: {
                    scope: this,
                    'initialMeasureSelected': function(initMeasure) {
                        this.measureSelected(initMeasure, true);
                    },
                    'saveChart': function(saveBtnText, replace){
                        this.saveChart(saveBtnText, replace);
                     }
                }
            });

            this.chartEditorMeasurePanel = new LABKEY.vis.ChartEditorMeasurePanel({
                disabled: true,
                measure: this.chartInfo.measures[yAxisMeasureIndex].measure,
                dimension: this.chartInfo.measures[yAxisMeasureIndex].dimension || null,
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'measureSelected': this.measureSelected,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.renderLineChart();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.chartEditorXAxisPanel = new LABKEY.vis.ChartEditorXAxisPanel({
                disabled: true,
                axis: this.chartInfo.measures[xAxisMeasureIndex].axis,
                dateOptions: this.chartInfo.measures[xAxisMeasureIndex].dateOptions,
                measure: this.chartInfo.measures[xAxisMeasureIndex].measure,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.renderLineChart();
                        }
                    },
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.chartEditorYAxisPanel = new LABKEY.vis.ChartEditorYAxisPanel({
                disabled: true,
                axis: this.chartInfo.measures[yAxisMeasureIndex].axis,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.renderLineChart();
                        }
                    }
                }
            });

            this.chartEditorChartsPanel = new LABKEY.vis.ChartEditorChartsPanel({
                disabled: true,
                chartLayout: this.chartInfo.layout,
                mainTitle: this.chartInfo.title,
                subjectNounSingular: this.viewInfo.subjectNounSingular,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.renderLineChart();
                        }
                    }
                }
            });

            this.chartEditor = new Ext.TabPanel({
                id: 'chart-editor-tabpanel',
                region: 'north',
                height: 200,
                padding: 4,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                title: '',
                activeTab: 0,
                items: [
                    this.chartEditorOverviewPanel,
                    this.chartEditorMeasurePanel,
                    this.chartEditorXAxisPanel,
                    this.chartEditorYAxisPanel,
                    this.chartEditorChartsPanel
                ]
            });
            items.push(this.chartEditor);

            this.subjectSeriesSelector = new LABKEY.vis.SubjectSeriesSelector({
                subject: this.chartInfo.subject,
                subjectNounPlural: this.viewInfo.subjectNounPlural,
                subjectColumn: this.viewInfo.subjectColumn,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.renderLineChart();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.seriesSelector = new Ext.TabPanel({
                id: 'series-selector-tabpanel',
                activeTab: 0,
                region: 'east',
                width: 200,
                padding: 5,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                title: '',
                enablePanelScroll: true,
                items: [this.subjectSeriesSelector]
            });
            items.push(this.seriesSelector);

            this.viewGridBtn = new Ext.Button({text: "View Data", handler: this.viewDataGrid, scope: this, disabled: true});
            this.viewChartBtn = new Ext.Button({text: "View Chart(s)", handler: this.renderLineChart, scope: this, hidden: true});
            this.chart = new Ext.Panel({
                id: 'chart-tabpanel',
                region: 'center',
                layout: 'fit',
                frame: false,
                autoScroll: true,
                tbar: [this.viewGridBtn, this.viewChartBtn],
                items: [],
                listeners: {
                    scope: this,
                    'resize': function(cmp){
                        // only call renderLineChart if the data object is available
                        if(this.chartSubjectData) {
                            this.renderLineChart();
                        }
                    }
                }
            });
            items.push(this.chart);
        }
        else
        {
            // other chart types
        }

        this.items = items;

        LABKEY.vis.TimeChartPanel.superclass.initComponent.call(this);
    },

    measureSelected: function(measure, initializeChartInfo) {
        console.log("inside measureSelected");

        // if this is a measure selection from the "Change" or "Choose a Measure" button, then initialize the chartInfo object
        if(initializeChartInfo){
            this.chartInfo = this.getInitializedChartInfo();
        }

        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // todo: change these actions to not use getCmp by IDs

        // udpate the subject series selector
        this.subjectSeriesSelector.getSubjectValues(measure.schemaName, measure.queryName);

        // update the information in the measure tab
        this.chartEditorMeasurePanel.enable();
        this.chartEditorMeasurePanel.measure = measure;
        // update the measure label
        Ext.getCmp('measure-label').setText(measure.label + " from " + measure.queryName);
        // update the measure dimension combo box
        this.measureMetadataRequestPending();
        var newDStore = this.chartEditorMeasurePanel.newDimensionStore();
        Ext.getCmp('measure-dimension-combo').bindStore(newDStore);
        // if this is a saved chart with a dimension selected, show dimension selector tab
        if(this.chartInfo.measures[yAxisMeasureIndex].dimension){
            this.chartEditorMeasurePanel.measureDimensionSelected(false);
        }

        // update the information in the x-axis tab
        this.chartEditorXAxisPanel.enable();
        // reset the store for the x-axis zero date combo
        this.measureMetadataRequestPending();
        var newZStore = this.chartEditorXAxisPanel.newZeroDateStore(measure.schemaName);
        Ext.getCmp('zero-date-combo').bindStore(newZStore);
        // reset the store for the x-axis measure date combo
        this.measureMetadataRequestPending();
        var newMStore = this.chartEditorXAxisPanel.newMeasureDateStore(measure.schemaName, measure.queryName);
        Ext.getCmp('measure-date-combo').bindStore(newMStore);

        // update the information in the y-axis tab
        this.chartEditorYAxisPanel.enable();
        this.chartEditorYAxisPanel.setLabel(measure.label);

        // update the information in the charts tab
        this.chartEditorChartsPanel.enable();

        // update the information in the overview tab
        this.chartEditorOverviewPanel.updateOverview(this.saveReportInfo);


        // update the y-axis label
//        Ext.getCmp('y-axis-label-textfield').setValue(measure.label);
//        this.chartInfo.measures[yAxisMeasureIndex].axis.label = measure.label;
        //reset the x-axis interval value to days
//        Ext.getCmp('x-axis-interval-combo').setValue('Days');
//        this.chartInfo.measures[xAxisMeasureIndex].dateOptions.interval = 'Days';
        // reset the x-axis range to automatic
//        Ext.getCmp('x-axis-range-automatic-radio').setValue(true);
        // set the series radio selection to one per participant
//        Ext.getCmp('series-per-subject-radio').setValue(true);
        // reset y-axis scale to linear
//        Ext.getCmp('y-axis-scale-combo').setValue('linear');
//        this.chartInfo.measures[yAxisMeasureIndex].axis.scale = 'linear';
        // reset the y-axis range to automatic
//        Ext.getCmp('y-axis-range-automatic-radio').setValue(true);

        // reset the chart layout radio option to One Chart
//        Ext.getCmp('chart-layout-radiogroup').reset();
    },

    measureMetadataRequestPending:  function() {
        this.getEl().mask("Loading...", "x-mask-loading");
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        this.measureMetadataRequestCounter--;
        if(this.measureMetadataRequestCounter == 0) {
            if(this.getEl().isMasked()) {
                this.getEl().unmask();
            }
            this.getChartData();
        }
    },

    getChartData: function() {
        console.log("getChartData");

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        if(xAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }
        console.log(this.chartInfo);

        this.chart.getEl().mask("Loading...", "x-mask-loading");
        LABKEY.Visualization.getData({
            successCallback: function(data){
                // store the data in an object by subject for use later when it comes time to render the line chart
                this.chartSubjectData = new Object();
                Ext.each(data.rows, function(row){
                    // get the subject id from the data row
                    var rowSubject = row[data.measureToColumn[this.viewInfo.subjectColumn]];
                    if(rowSubject.value ) rowSubject = rowSubject.value;

                    // if this is a new subject to the chartSubjectData object, then initialize it
                    if(!this.chartSubjectData[rowSubject]) {
                        this.chartSubjectData[rowSubject] = new Object();

                        // initialize an array for each element in measureToColumn
                        Ext.iterate(data.measureToColumn, function(key, value, obj){
                            this.chartSubjectData[rowSubject][key] = new Array();
                        }, this);
                    }

                    // add the data value and interval value to the appropriate place in the chartSubjectData object
                    Ext.iterate(data.measureToColumn, function(key, value, obj){
                        var dataValue = row[value];
                        if(typeof dataValue != "object") {
                            dataValue = {value: dataValue};
                        }

                        this.chartSubjectData[rowSubject][key].push({
                            interval: row[this.chartInfo.measures[xAxisMeasureIndex].dateOptions.interval],
                            dataValue: dataValue
                        });
                    }, this);
                }, this);

                // store the temp schema and query name for the data grid
                this.tempGridInfo = {schema: data.schemaName, query: data.queryName};

                // now that we have the temp grid info, enable the View Data button
                // and make sure that the view charts button is hidden
                this.viewGridBtn.setDisabled(false);
                this.viewChartBtn.hide();

                // ready to render the chart
                this.renderLineChart();
            },
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            measures: this.chartInfo.measures,
            viewInfo: this.viewInfo,
            sorts: [this.chartInfo.subject, this.chartInfo.measures[xAxisMeasureIndex].measure],
            scope: this
        });
    },

    renderLineChart: function()
    {
        console.log("renderLineChart");

        // if the call to this function is coming from the getChartData success callback, then the panel is already masked
        // but if not, mask the panel
        if(!this.chart.getEl().isMasked()) {
            this.chart.getEl().mask("Loading...", "x-mask-loading");
        }

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");
        if(yAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find y-axis in chart measure information.");
           return;
        }
        console.log(this.chartInfo);

        // clear the components from the chart panel
        this.chart.removeAll();

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();

	    // one series per subject/measure/dimensionvalue combination
	    var seriesList = [];
	    if(this.chartInfo.measures[yAxisMeasureIndex].dimension && this.chartInfo.measures[yAxisMeasureIndex].dimension.selected) {
	        seriesList = this.chartInfo.measures[yAxisMeasureIndex].dimension.selected;
	    }
	    else {
	        seriesList = [this.chartInfo.measures[yAxisMeasureIndex].measure.name];
	    }

        var series = [];
        for(var j = 0; j < this.chartInfo.subject.selected.length; j++)
        {
        	for(var i = 0; i < seriesList.length; i++)
        	{
                var yAxisSeries = seriesList[i];
                var subject = this.chartInfo.subject.selected[j];

                series.push({
                    subject: subject,
                    yAxisSeries: yAxisSeries,
                    caption: subject + " " + yAxisSeries,
                    data: this.chartSubjectData[subject][yAxisSeries],
                    xProperty:"interval",
                    yProperty: "dataValue",
                    dotShape: 'circle'
                });
            }
        }

        var size = {width: (this.chart.getInnerWidth() * .95), height: (this.chart.getInnerHeight() * .97)};
        //var size = {width: this.chart.getInnerWidth(), height: this.chart.getInnerHeight()};

	    // three options: all series on one chart, one chart per subject, or one chart per measure/dimension
        var charts = [];
        if(this.chartInfo.layout == "per_subject") {
        	Ext.each(this.chartInfo.subject.selected, function(subject) {
        		charts.push(this.newLineChart(size, series, {parameter: "subject", value: subject}, subject));
        	}, this);
        }
        else if(this.chartInfo.layout == "per_dimension") {
        	Ext.each(seriesList, function(md) {
        		charts.push(this.newLineChart(size, series, {parameter: "yAxisSeries", value: md}, md));
        	}, this);
        }
        else if(this.chartInfo.layout == "single")
            charts.push(this.newLineChart(size, series, null, null));

        this.chart.add(charts);
        this.chart.doLayout();

        if(this.chart.getEl().isMasked()) {
            this.chart.getEl().unmask();
        }
    },

    newLineChart: function(size, series, seriesFilter, title)
    {
        // hold on to the x and y axis measure index
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

    	// if seriesFilter is not null, then create a sub-array for that filter
    	var tempSeries = [];
    	if(seriesFilter) {
            for(var i = 0; i < series.length; i++) {
                if(series[i][seriesFilter.parameter] == seriesFilter.value) {
                    series[i].caption = series[i].caption.replace(seriesFilter.value, "");
                    tempSeries.push(series[i]);
                }
            }
    	}

        var chartComponent = new LABKEY.vis.LineChart(
        {
            width: size.width,
            height: size.height - 25, // todo: find a better way to size the chart (leaving room for export button)
            axes: {
                y: {
                    min: (this.chartInfo.measures[yAxisMeasureIndex].axis.range.min ? this.chartInfo.measures[yAxisMeasureIndex].axis.range.min : undefined),
                    max: (this.chartInfo.measures[yAxisMeasureIndex].axis.range.max ? this.chartInfo.measures[yAxisMeasureIndex].axis.range.max : undefined),
                    caption: this.chartInfo.measures[yAxisMeasureIndex].axis.label,
                    scale: this.chartInfo.measures[yAxisMeasureIndex].axis.scale
                },
                x: {
                    min: (this.chartInfo.measures[xAxisMeasureIndex].axis.range.min ? this.chartInfo.measures[xAxisMeasureIndex].axis.range.min : undefined),
                    max: (this.chartInfo.measures[xAxisMeasureIndex].axis.range.max ? this.chartInfo.measures[xAxisMeasureIndex].axis.range.max : undefined),
                    caption: this.chartInfo.measures[xAxisMeasureIndex].axis.label
                }
            },
            series: tempSeries.length > 0 ? tempSeries : series,
            title: this.chartInfo.title + (title != null ? ": " + title : "")
        });

    	var chartPanel = new Ext.Panel({
            items: [
                chartComponent,
                new Ext.Button({
                    text:"Export PDF",
                    handler:function(btn) {chartComponent.exportImage("pdf");},
                    scope:this
                })
            ]
        });

        return chartPanel;
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            // mask panel and remove the chart(s)
            this.chart.getEl().mask("Loading...", "x-mask-loading");
            this.chart.removeAll();

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.show();
            this.viewGridBtn.hide();

            // add a panel to put the queryWebpart in
            var gridPanelId = Ext.id();
            this.chart.add(new Ext.Panel({
                id: gridPanelId,
                padding: 20, //todo: why isn't this padding working for the panel?
                items: []
            }));
            this.chart.doLayout();

            // create the queryWebpart using the temp grid schema and query name
            var chartQueryWebPart = new LABKEY.QueryWebPart({
                renderTo: gridPanelId,
                schemaName: this.tempGridInfo.schema,
                queryName: this.tempGridInfo.query,
                allowChooseQuery: false,
                allowChooseView: false,
                title: "",
                frame: "none"
            });

            // unmask the panel
            this.chart.getEl().unmask();
        }
    },

    getInitializedChartInfo: function(){
        return {
            measures: [
                {axis: {multiSelect: "false", name: "x-axis", label: "", timeAxis: "true", range: {}}, dateOptions: {interval: 'Days'}, measure: {}},
                {axis: {multiSelect: "false", name: "y-axis", label: "", scale: "linear", range: {}}, measure: {}}
            ],
            layout: 'single',
            subject: {},
            title: ''
        }
    },

    getChartInfoFromEditorTabs: function(){
        return {
            title: this.chartEditorChartsPanel.getMainTitle(),
            layout: this.chartEditorChartsPanel.getChartLayout(),
            subject: this.subjectSeriesSelector.getSubject(),
            measures: [
                { // x-axis information
                    axis: this.chartEditorXAxisPanel.getAxis(),
                    dateOptions: this.chartEditorXAxisPanel.getDateOptions(),
                    measure: this.chartEditorXAxisPanel.getMeasure()
                },
                { // y-axis information
                    axis: this.chartEditorYAxisPanel.getAxis(),
                    measure: this.chartEditorMeasurePanel.getMeasure(),
                    dimension: this.chartEditorMeasurePanel.getDimension()
                }
            ]
        }
    },

    getMeasureIndex: function(measures, axis){
        var index = -1;
        for(var i = 0; i < measures.length; i++){
            if(measures[i].axis.name == axis){
                index = i;
                break;
            }
        }
        return index;
    },

    saveChart: function(saveBtnText, replace) {
        // basic form to get the name and description from the user
        var vizSaveForm = new Ext.FormPanel({
            monitorValid: true,
            border: false,
            frame: false,
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Name',
                name: 'reportName',
                disabled: replace,
                value: (this.saveReportInfo ? this.saveReportInfo.name : ""),
                width: 300,
                allowBlank: false
            },
            {
                xtype: 'textarea',
                fieldLabel: 'Description',
                name: 'reportDescription',
                value: (this.saveReportInfo ? this.saveReportInfo.description : ""),
                width: 300,
                height: 70,
                allowBlank: true
            }],
            buttons: [{
                text: saveBtnText,
                formBind: true,
                handler: function(btn, evnt){
                    var formValues = vizSaveForm.getForm().getFieldValues();

                    // if queryName and schemaName are set on the URL then save them with the chart info
                    var schema = LABKEY.ActionURL.getParameter("schemaName") || null;
                    var query = LABKEY.ActionURL.getParameter("queryName") || null;

                    LABKEY.Visualization.save({
                        name: formValues.reportName,
                        description: formValues.reportDescription,
                        visualizationConfig: this.chartInfo,
                        replace: replace,
                        type: LABKEY.Visualization.Type.TimeChart,
                        success: this.saveChartSuccess,
                        schemaName: schema,
                        queryName:query
                    });

                    win.hide();
                },
                scope: this
            },
            {
                text: 'Cancel',
                handler: function(){
                    win.hide();
                }
            }]
        });

        // pop-up window for user to enter viz name and description for saving
        var win = new Ext.Window({
            layout:'fit',
            width:475,
            height:200,
            closeAction:'hide',
            modal: true,
            padding: 15,
            title: saveBtnText + "...",
            items: vizSaveForm
        });
        win.show(this);
    },

    saveChartSuccess: function(result, request, options) {
        //this.chartEditorOverviewPanel.updateOverview(this.saveReportInfo);
        Ext.Msg.alert("Success", "Chart Saved Successfully.");
    }
});