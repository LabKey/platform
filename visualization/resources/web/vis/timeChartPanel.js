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
LABKEY.requiresCss("_images/icons.css");


Ext.QuickTips.init();

LABKEY.vis.TimeChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.apply(this, config);

        LABKEY.vis.TimeChartPanel.superclass.constructor.call(this);
    },

    initComponent : function() {
        // properties for this panel
        this.layout = 'border';
        this.isMasked = false;
        this.maxCharts = 30;

        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
        }

        // hold on to the x and y axis measure index
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // add a listener to call measureSelected on render if this is a saved chart
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object") {
                    this.measureSelected(this.chartInfo.measures[yAxisMeasureIndex].measure, false);
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        if(this.viewInfo.type == "line") {
            this.editorOverviewPanel = new LABKEY.vis.ChartEditorOverviewPanel({
                reportInfo: this.saveReportInfo,
                listeners: {
                    scope: this,
                    'initialMeasureSelected': function(initMeasure) {
                        Ext.getCmp('chart-editor-tabpanel').activate(this.editorMeasurePanel.getId());
                        this.measureSelected(initMeasure, true);
                    },
                    'saveChart': function(saveBtnType, replace, reportName, reportDescription, reportShared){
                        this.saveChart(saveBtnType, replace, reportName, reportDescription, reportShared);
                     }
                }
            });

            this.editorMeasurePanel = new LABKEY.vis.ChartEditorMeasurePanel({
                disabled: true,
                measure: this.chartInfo.measures[yAxisMeasureIndex].measure,
                dimension: this.chartInfo.measures[yAxisMeasureIndex].dimension || null,
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'measureSelected': this.measureSelected,
                    'dimensionSelected': function(hasDimension){
                        this.editorChartsPanel.disableDimensionOption(hasDimension);
                    },
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorXAxisPanel = new LABKEY.vis.ChartEditorXAxisPanel({
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
                            this.loader();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorYAxisPanelVar = new LABKEY.vis.ChartEditorYAxisPanel({
                disabled: true,
                axis: this.chartInfo.measures[yAxisMeasureIndex].axis,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    }
                }
            });

            this.editorChartsPanel = new LABKEY.vis.ChartEditorChartsPanel({
                disabled: true,
                chartLayout: this.chartInfo.chartLayout,
                mainTitle: this.chartInfo.title,
                subjectNounSingular: this.viewInfo.subjectNounSingular,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    }
                }
            });

            this.chartEditor = new Ext.Panel({
                layout: 'fit',
                header: false,
                region: 'north',
                height: 210,
                border: false,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                hideCollapseTool: true,
                items: [
                    new Ext.TabPanel({
                        id: 'chart-editor-tabpanel',
                        padding: 4,
                        activeTab: 0,
                        items: [
                            this.editorOverviewPanel,
                            this.editorMeasurePanel,
                            this.editorXAxisPanel,
                            this.editorYAxisPanelVar,
                            this.editorChartsPanel
                        ]
                    })
                ],
                tbar: {
                    height: 25,
                    style:{backgroundColor :'#ffffff'},
                    items:[
                        '->',
                        {
                            iconCls:'iconClose',
                            tooltip:'Close the chart editor tab panel.',
                            handler: function(){
                                this.chartEditor.collapse();
                            },
                            scope: this
                        }
                    ]
                }
            });
            items.push(this.chartEditor);

            this.subjectSelector = new LABKEY.vis.SubjectSeriesSelector({
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
                            this.loader();
                        }
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.seriesSelector = new Ext.Panel({
                region: 'east',
                layout: 'fit',
                width: 200,
                border: false,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                hideCollapseTool: true,
                header: false,
                items: [
                    new Ext.TabPanel({
                        id: 'series-selector-tabpanel',
                        activeTab: 0,
                        padding: 5,
                        enablePanelScroll: true,
                        items: [this.subjectSelector]
                    })
                ],
                tbar: {
                    height: 25,
                    style:{backgroundColor :'#ffffff'},
                    items:[
                        '->',
                        {
                            iconCls:'iconClose',
                            tooltip:'Close the series selector tab panel.',
                            handler: function(){
                                this.seriesSelector.collapse();
                            },
                            scope: this
                        }
                    ]
                }
            });
            items.push(this.seriesSelector);

            this.loader = this.renderLineChart;  // default is to show the chart
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

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        LABKEY.vis.TimeChartPanel.superclass.initComponent.apply(this, arguments);
    },

    measureSelected: function(measure, userSelectedMeasure) {
        // if this is a measure selection from the "Change" or "Choose a Measure" button, then initialize the chartInfo object
        if(userSelectedMeasure){
            this.chartInfo = this.getInitializedChartInfo();
        }

        // get the axis measure index for easier access
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");

        // enable the charteditor tabs in the tabPanel
        this.editorMeasurePanel.enable();
        this.editorXAxisPanel.enable();
        this.editorYAxisPanelVar.enable();
        this.editorChartsPanel.enable();

        // update chart definition information based on measure selection
        this.editorMeasurePanel.measure = measure;
        this.subjectSelector.getSubjectValues(measure.schemaName, measure.queryName);

        // update chart editor form values based on selected measure
        this.editorMeasurePanel.setMeasureLabel(measure.label + " from " + measure.queryName)
        this.editorMeasurePanel.setDimensionStore(this.chartInfo.measures[yAxisMeasureIndex].dimension);
        this.editorXAxisPanel.setZeroDateStore(measure.schemaName);
        this.editorXAxisPanel.setMeasureDateStore(measure.schemaName, measure.queryName);
        this.editorXAxisPanel.setRange(this.chartInfo.measures[xAxisMeasureIndex].axis.range.type);
        this.editorYAxisPanelVar.setRange(this.chartInfo.measures[yAxisMeasureIndex].axis.range.type);
        this.editorYAxisPanelVar.setScale(this.chartInfo.measures[yAxisMeasureIndex].axis.scale);
        this.editorChartsPanel.setChartLayout(this.chartInfo.chartLayout);
        this.editorChartsPanel.disableDimensionOption((this.chartInfo.measures[yAxisMeasureIndex].dimension ? true : false));
        if(userSelectedMeasure){
            this.editorOverviewPanel.updateOverview(this.saveReportInfo);
            this.editorYAxisPanelVar.setLabel(measure.label);
            this.editorChartsPanel.setMainTitle(measure.queryName);
        }
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
    },

    measureMetadataRequestPending:  function() {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        // increase the request counter
        this.measureMetadataRequestCounter++;
    },

    measureMetadataRequestComplete: function() {
        // decrease the request counter
        this.measureMetadataRequestCounter--;

        // if all requests are complete, call getChartData
        if(this.measureMetadataRequestCounter == 0) {
            this.getChartData();
        }
    },

    getChartData: function() {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        if(xAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        LABKEY.Visualization.getData({
            success: function(data){
                // store the data in an object by subject for use later when it comes time to render the line chart
                this.chartSubjectData = new Object();
                this.markDirty(true);

                // make sure each measure has at least some data
                this.hasData = {};
                Ext.iterate(data.measureToColumn, function(key, value, obj){
                    this.hasData[key] = false;
                }, this);
                
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

                        // This measure has data
                        if (dataValue.value) this.hasData[key] = true;

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
                this.loader();              
            },
            failure : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            measures: this.chartInfo.measures,
            viewInfo: this.viewInfo,
            sorts: [this.chartInfo.subject, this.chartInfo.measures[xAxisMeasureIndex].measure],
            scope: this
        });
    },

    renderLineChart: function(force)
    {
        // mask panel and remove the chart(s)
        this.maskChartPanel();

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();
        var yAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "y-axis");
        var xAxisMeasureIndex = this.getMeasureIndex(this.chartInfo.measures, "x-axis");
        if(yAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find y-axis in chart measure information.");
           return;
        }
        else if(yAxisMeasureIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();
        this.loader = this.renderLineChart;

        if (force !== true) {
            var msg = ""; var sep = "";
            Ext.iterate(this.hasData, function(key, value, obj){
                if (!value && key == this.chartInfo.measures[yAxisMeasureIndex].measure.name) {
                    msg += sep + this.chartInfo.measures[yAxisMeasureIndex].measure.label + " (Y-Axis)";
                    sep = ", ";
                }
                else if (!value && key == this.chartInfo.measures[xAxisMeasureIndex].measure.name) {
                    msg += sep + this.chartInfo.measures[xAxisMeasureIndex].measure.label + " (X-Axis)";
                    sep = ", ";
                }
            }, this);
            if (msg.length > 0) { this.maskChartPanel("No data found in: " + msg + " for the selected " + this.viewInfo.subjectNounPlural + "."); return; }
        }

	    // one series per subject/measure/dimensionvalue combination
	    var seriesList = [];
	    if(this.chartInfo.measures[yAxisMeasureIndex].dimension && this.chartInfo.measures[yAxisMeasureIndex].dimension.values) {
	        seriesList = this.chartInfo.measures[yAxisMeasureIndex].dimension.values;
	    }
	    else {
	        seriesList = [this.chartInfo.measures[yAxisMeasureIndex].measure.name];
	    }

        var series = [];
        for(var j = 0; j < this.chartInfo.subject.values.length; j++)
        {
        	for(var i = 0; i < seriesList.length; i++)
        	{
                var yAxisSeries = seriesList[i];
                var subject = this.chartInfo.subject.values[j];

                var caption = subject;
                if(this.chartInfo.measures[yAxisMeasureIndex].dimension || this.chartInfo.chartLayout != "single"){
                    caption += " " + yAxisSeries;
                }

                series.push({
                    subject: subject,
                    yAxisSeries: yAxisSeries,
                    caption: caption,
                    data: this.chartSubjectData[subject] ? this.chartSubjectData[subject][yAxisSeries] : [],
                    xProperty:"interval",
                    yProperty: "dataValue",
                    dotShape: 'circle'
                });
            }
        }

        var size = {width: (this.chart.getInnerWidth() * .95), height: (this.chart.getInnerHeight() * .97)};

	    // three options: all series on one chart, one chart per subject, or one chart per measure/dimension
        var charts = [];
        var warningText = null;
        if(this.chartInfo.chartLayout == "per_subject") {
        	for(var i = 0; i < (this.chartInfo.subject.values.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.values.length); i++){
        	    var subject = this.chartInfo.subject.values[i];
        		charts.push(this.newLineChart(size, series, {parameter: "subject", value: subject}, subject));
        	}

            // warn if user doesn't have an subjects selected or if the max number has been exceeded
            if(this.chartInfo.subject.values.length == 0){
                warningText = "Please select at least one subject.";
            }
        	else if(this.chartInfo.subject.values.length > this.maxCharts){
        	    warningText = "Only showing the first " + this.maxCharts + " charts.";
        	}
        }
        else if(this.chartInfo.chartLayout == "per_dimension") {
        	for(var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++){
        	    var md = seriesList[i];
        		charts.push(this.newLineChart(size, series, {parameter: "yAxisSeries", value: md}, md));
        	}

            // warn if user doesn't have an dimension values selected or if the max number has been exceeded
            if(seriesList.length == 0){
                warningText = "Please select at least one dimension value.";
            }
        	else if(seriesList.length > this.maxCharts){
        	    warningText = "Only showing the first " + this.maxCharts + " charts";
        	}
        }
        else if(this.chartInfo.chartLayout == "single")
            charts.push(this.newLineChart(size, series, null, null));

        this.chart.removeAll();

        // if the user has selected more charts than the max allowed, show warning
        if(warningText){
            this.chart.add(new Ext.form.DisplayField({
                autoHeight: 25,
                autoWidth: true,
                value: warningText,
                style: "font-style:italic;width:100%;padding:5px;text-align:center;"
            }));
        };
        
        this.chart.add(charts);
        this.chart.doLayout();
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

    	// set the title for this chart based on the Chart Title entered by the user and the ptid/dimension layout option
    	var mainTitle = this.chartInfo.title + (this.chartInfo.title != "" && title != null ? ": " : "") + (title ? title : "");

        var chartComponent = new LABKEY.vis.LineChart({
            width: size.width,
            height: size.height - 25,
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
            title: mainTitle
        });

        var chartItems = [chartComponent];
        if (chartComponent.canExport())
            chartItems.push(new Ext.Button({
                text:"Export PDF",
                handler:function(btn) {chartComponent.exportImage("pdf");},
                scope:this
            }));
        
        return new Ext.Panel({
            items: chartItems
        });
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            // mask panel and remove the chart(s)
            this.maskChartPanel();
            this.loader = this.viewDataGrid;

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.show();
            this.viewGridBtn.hide();

            // add a panel to put the queryWebpart in
            var gridPanelId = Ext.id();
            var dataGridPanel = new Ext.Panel({
                autoScroll: true,
                padding: 10,
                items: [{
                    xtype: 'displayfield',
                    value: 'Note: filters applied to the data grid will not be reflected in the chart view.',
                    style: 'font-style:italic;padding:10px'
                },
                {
                    xtype: 'panel',
                    id: gridPanelId
                }]
            });

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

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
            this.chart.doLayout();
        }
    },

    getInitializedChartInfo: function(){
        return {
            measures: [
                {axis: {multiSelect: "false", name: "x-axis", label: "", timeAxis: "true", range: {type: 'automatic'}}, dateOptions: {interval: 'Days'}, measure: {}},
                {axis: {multiSelect: "false", name: "y-axis", label: "", scale: "linear", range: {type: 'automatic'}}, measure: {}}
            ],
            chartLayout: 'single',
            subject: {},
            title: ''
        }
    },

    getChartInfoFromEditorTabs: function(){
        return {
            title: this.editorChartsPanel.getMainTitle(),
            chartLayout: this.editorChartsPanel.getChartLayout(),
            subject: this.subjectSelector.getSubject(),
            measures: [
                { // x-axis information
                    axis: this.editorXAxisPanel.getAxis(),
                    dateOptions: this.editorXAxisPanel.getDateOptions(),
                    measure: this.editorXAxisPanel.getMeasure()
                },
                { // y-axis information
                    axis: this.editorYAxisPanelVar.getAxis(),
                    measure: this.editorMeasurePanel.getMeasure(),
                    dimension: this.editorMeasurePanel.getDimension()
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

    saveChart: function(saveBtnName, replace, reportName, reportDescription, reportShared) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = LABKEY.ActionURL.getParameter("schemaName") || null;
        var query = LABKEY.ActionURL.getParameter("queryName") || null;

        // if the Save button was clicked, save the report using the name and description provided
        if(saveBtnName == 'Save'){
            var config = {
                replace: replace,
                reportName: reportName,
                reportDescription: reportDescription,
                reportShared: reportShared,
                query: query,
                schema: schema
            };

            // if user clicked save button to replace an existing report, execute the save chart call
            // otherwise, the user clicked save for a new report so check if the report name already exists
            if(replace){
                this.executeSaveChart(config);
            }
            else{
                this.checkSaveChart(config);
            }
        }
        // if the Save As button was clicked, open a window for user to enter new report name and description
        else if(saveBtnName == 'Save As'){
            // basic form to get the name and description from the user
            var vizSaveForm = new Ext.FormPanel({
                monitorValid: true,
                border: false,
                frame: false,
                labelWidth: 125,
                items: [{
                    xtype: 'textfield',
                    fieldLabel: 'Report Name',
                    name: 'reportName',
                    value: reportName || null,
                    width: 300,
                    allowBlank: false,
                    maxLength: 50
                },
                {
                    xtype: 'textarea',
                    fieldLabel: 'Report Description',
                    name: 'reportDescription',
                    value: reportDescription || null,
                    width: 300,
                    height: 70,
                    allowBlank: true
                },
                new Ext.form.RadioGroup({
                    name: 'reportShared',
                    fieldLabel: 'Viewable by',
                    anchor: '100%',
                    items : [
                            { name: 'reportShared', boxLabel: 'All readers', inputValue: 'true', checked: reportShared },
                            { name: 'reportShared', boxLabel: 'Only me', inputValue: 'false', checked: !reportShared }
                        ]
                })],
                buttons: [{
                    text: 'Save',
                    formBind: true,
                    handler: function(btn, evnt){
                        var formValues = vizSaveForm.getForm().getValues();
                        var shared = typeof formValues.reportShared == "string" ? 'true' == formValues.reportShared : new Boolean(formValues.reportShared);

                        // call fnctn to check if a report of that name already exists
                        this.checkSaveChart({
                            replace: replace,
                            reportName: formValues.reportName,
                            reportDescription: formValues.reportDescription,
                            reportShared: shared,
                            query: query,
                            schema: schema
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
                height:230,
                closeAction:'hide',
                modal: true,
                padding: 15,
                title: saveBtnName,
                items: vizSaveForm
            });
            win.show(this);
        }
    },

    checkSaveChart: function(config){
        // see if a report by this name already exists within this container
        LABKEY.Visualization.get({
            name: config.reportName,
            success: function(result, request, options){
                // a report by that name already exists within the container, if the user can update, ask if they would like to replace
                if(LABKEY.Security.currentUser.canUpdate){
                    Ext.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + config.reportName + '\' already exists within this container. Would you like to replace it?',
                        buttons: Ext.Msg.YESNO,
                        fn: function(btnId, text, opt){
                            if(btnId == 'yes'){
                                config.replace = true;
                                this.executeSaveChart(config);
                            }
                        },
                        icon: Ext.MessageBox.WARNING,
                        scope: this
                    });
                }
                else{
                    Ext.Msg.show({
                        title:'Error',
                        msg: 'A report by the name \'' + config.reportName + '\' already exists within this container.',
                        buttons: Ext.Msg.OK,
                        icon: Ext.MessageBox.ERROR
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
        LABKEY.Visualization.save({
            name: config.reportName,
            description: config.reportDescription,
            shared: config.reportShared,
            visualizationConfig: this.chartInfo,
            replace: config.replace,
            type: LABKEY.Visualization.Type.TimeChart,
            success: this.saveChartSuccess(config.reportName, config.reportDescription, config.reportShared),
            schemaName: config.schema,
            queryName: config.query,
            scope: this
        });
    },

    saveChartSuccess: function (reportName, reportDescription, reportShared){
        return function(result, request, options) {
            this.markDirty(false);
            this.editorOverviewPanel.updateOverview({name: reportName, description: reportDescription, shared: reportShared});
            Ext.Msg.alert("Success", "The chart has been successfully saved.");
        }
    },

    // WORKAROUND: this is a workaround way of masking the chart panel since the ExtJS mask/unmask
    // results in the chart image being masked in IE (even after unmask is called)
    maskChartPanel: function(message){
        if(!this.isMasked){
            var msg = message || "Loading...";
            this.chart.removeAll();
            this.chart.add(new Ext.Panel({
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + msg + "</td></tr></table>"
            }));
            this.chart.doLayout();
        }
    }
});