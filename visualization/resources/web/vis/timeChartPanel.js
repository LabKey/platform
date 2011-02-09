/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

LABKEY.requiresScript("vis/chartEditorMeasurePanel.js");
LABKEY.requiresScript("vis/chartEditorYAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorXAxisPanel.js");
LABKEY.requiresScript("vis/chartEditorChartsPanel.js");


Ext.QuickTips.init();

var ONE_DAY = 1000 * 60 * 60 * 24;
var ONE_WEEK = ONE_DAY * 7;
var ONE_MONTH = ONE_WEEK * (52/12);
var ONE_YEAR = ONE_DAY * 365;

LABKEY.vis.TimeChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        Ext.apply(this, config);

        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        // including some defauts for things like plot chart and axis scale
        this.chartInfo = {
            measures: [
                {axis: {multiSelect: "false", name: "x-axis", label: "", timeAxis: "true"}, dateOptions: {}, measure: {}},
                {axis: {multiSelect: "false", name: "y-axis", label: ""}, measure: {}}
            ],
            yAxisScale: 'linear',
            plotChar: 'circle',
            layout: 'single',
            subject: {selection: []},
            title: '',
            data: null
        };

        LABKEY.vis.TimeChartPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // properties for this panel
        this.layout = 'border';

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        if(this.viewInfo.type == "line") {

            // get the information from the y-axis measure
            for(var i = 0; i < this.chartInfo.measures.length; i++) {
                if(this.chartInfo.measures[i].axis.name == "x-axis") {
                    this.xAxisMeasureIndex = i;
                }
                else if(this.chartInfo.measures[i].axis.name == "y-axis") {
                    this.yAxisMeasureIndex = i;
                    this.chartInfo.measures[this.yAxisMeasureIndex].axis.label = this.chartInfo.measures[this.yAxisMeasureIndex].measure.label;
                }
            }

            this.chartEditorMeasurePanel = new LABKEY.vis.ChartEditorMeasurePanel({
                measure: this.chartInfo.measures[this.yAxisMeasureIndex].measure,
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'measureSelected': this.measureSelected,
                    'subjectDimensionIdentified': this.subjectDimensionIdentified,
                    'measureDimensionSelected': this.measureDimensionSelected,
                    'seriesPerSubjectChecked': this.seriesPerSubjectChecked,
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.chartEditorXAxisPanel = new LABKEY.vis.ChartEditorXAxisPanel({
                yAxisMeasure: this.chartInfo.measures[this.yAxisMeasureIndex].measure,
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'xAxisIntervalChanged': function(interval, readyForChartData) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].dateOptions.interval = interval;
                        if(readyForChartData) {
                            this.getChartData();
                        }
                    },
                    'xAxisLabelChanged': function(label, readyForLineChart) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].axis.label = label;
                        if(readyForLineChart) {
                            this.renderLineChart();
                        }
                    },
                    'measureDateChanged': function(measureDateCol, readyForChartData) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].measure =  measureDateCol;
                        if(readyForChartData) {
                            this.getChartData();
                        }
                    },
                    'zeroDateChanged': function(zeroDateCol, readyForChartData) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].dateOptions.zeroDateCol =  zeroDateCol;
                        if(readyForChartData) {
                            this.getChartData();
                        }
                    },
                    'xAxisRangeAutomaticChecked': function() {
                        delete this.chartInfo.measures[this.xAxisMeasureIndex].axis.min;
                        delete this.chartInfo.measures[this.xAxisMeasureIndex].axis.max;
                        this.renderLineChart();
                    },
                    'xAxisRangeManualMinChanged': function(newValue) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].axis.min = newValue;
                        this.renderLineChart();
                    },
                    'xAxisRangeManualMaxChanged': function(newValue) {
                        this.chartInfo.measures[this.xAxisMeasureIndex].axis.max = newValue;
                        this.renderLineChart();
                    },
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.chartEditorYAxisPanel = new LABKEY.vis.ChartEditorYAxisPanel({
                title: 'Y-Axis',
                axis: this.chartInfo.measures[this.yAxisMeasureIndex].axis,
                listeners: {
                    scope: this,
                    'yAxisScaleChanged': function(newValue) {
                        this.chartInfo.yAxisScale = newValue;
                        this.renderLineChart();
                    },
                    'yAxisLabelChanged': function(newValue) {
                        this.chartInfo.measures[this.yAxisMeasureIndex].axis.label = newValue;
                        this.renderLineChart();
                    },
                    'yAxisRangeAutomaticChecked': function() {
                        delete this.chartInfo.measures[this.yAxisMeasureIndex].axis.min;
                        delete this.chartInfo.measures[this.yAxisMeasureIndex].axis.max;
                        this.renderLineChart();
                    },
                    'yAxisRangeManualMinChanged': function(newValue) {
                        this.chartInfo.measures[this.yAxisMeasureIndex].axis.min = newValue;
                        this.renderLineChart();
                    },
                    'yAxisRangeManualMaxChanged': function(newValue) {
                        this.chartInfo.measures[this.yAxisMeasureIndex].axis.max = newValue;
                        this.renderLineChart();
                    }
                }
            });

            this.chartEditorChartsPanel = new LABKEY.vis.ChartEditorChartsPanel({
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'chartLayoutChanged': function(layout) {
                        this.chartInfo.layout = layout;
                        this.renderLineChart();
                    },
                    'chartTitleChanged': function(title) {
                        this.chartInfo.title = title;
                        this.renderLineChart();
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
                    this.chartEditorMeasurePanel,
                    this.chartEditorXAxisPanel,
                    this.chartEditorYAxisPanel,
                    this.chartEditorChartsPanel
                ]
            });
            items.push(this.chartEditor);

            var sm = new  Ext.grid.CheckboxSelectionModel({
                listeners: {
                    scope: this,
                    'selectionChange': function(selModel){
                        // add the selected subjects to the chartInfo
                        this.chartInfo.subject.selection = [];
                        var selectedRecords = selModel.getSelections();
                        for(var i = 0; i < selectedRecords.length; i++) {
                            this.chartInfo.subject.selection.push(selectedRecords[i].get('value'));
                        }
                        // redraw the line chart
                        this.renderLineChart();
                    }
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
                items: [
                    {
                        xtype: 'panel',
                        title: this.viewInfo.subjectNounPlural,
                        autoScroll: true,
                        items: [
                            new Ext.grid.GridPanel({
                                id: 'subject-list-view',
                                autoHeight: true,
                                hidden: true, // initally hidden until subject values are loaded into the store
                                store: new Ext.data.JsonStore({
                                    root: 'values',
                                    fields: ['value']
                                }),
                                viewConfig: {forceFit: true},
                                border: false,
                                frame: false,
                                columns: [
                                    sm,
                                    {header: this.viewInfo.subjectNounPlural, dataIndex:'value'}
                                ],
                                selModel: sm,
                                header: false
                             })
                        ]
                    }
                ]
            });
            items.push(this.seriesSelector);

            this.chart = new Ext.Panel({
                id: 'chart-tabpanel',
                region: 'center',
                layout: 'fit',
                frame: false,
                autoScroll: true,
                items: [],
                listeners: {
                    scope: this,
                    'resize': function(cmp){
                        this.renderLineChart();
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

    measureSelected: function(measure) {
        this.chartInfo.measures[this.yAxisMeasureIndex].measure = measure;

        // todo: change these actions to not use getCmp by IDs

        // remove all of the subjects from the series selector panel
        Ext.getCmp('subject-list-view').getStore().removeAll();

        // reset the x-axis interval value to days
        Ext.getCmp('x-axis-interval-combo').setValue('Days');
        this.chartInfo.measures[this.xAxisMeasureIndex].dateOptions.interval = 'Days';
        // reset the store for the x-axis zero date combo
        this.measureMetadataRequestPending();
        var newZStore = this.chartEditorXAxisPanel.newZeroDateStore(measure.schemaName);
        Ext.getCmp('zero-date-combo').bindStore(newZStore);
        // reset the store for the x-axis measure date combo
        this.measureMetadataRequestPending();
        var newMStore = this.chartEditorXAxisPanel.newMeasureDateStore(measure.schemaName, measure.queryName);
        Ext.getCmp('measure-date-combo').bindStore(newMStore);
        // reset the x-axis range to automatic
        Ext.getCmp('x-axis-range-automatic-radio').setValue(true);

        // update the y-axis label
        Ext.getCmp('y-axis-label-textfield').setValue(measure.label);
        this.chartInfo.measures[this.yAxisMeasureIndex].axis.label = measure.label;
        // reset y-axis scale to linear
        Ext.getCmp('y-axis-scale-combo').setValue('linear');
        this.chartInfo.yAxisScale = 'linear';
        // reset the y-axis range to automatic
        Ext.getCmp('y-axis-range-automatic-radio').setValue(true);

        // reset the chart layout radio option to One Chart
        Ext.getCmp('chart-layout-radiogroup').reset();
    },

    subjectDimensionIdentified: function(subjectDimension) {
        Ext.apply(this.chartInfo.subject, subjectDimension);

        // this is the query/column we need to get the subject IDs for the selected measure
        this.measureMetadataRequestPending();
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, subjectDimension),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                this.renderSubjects(response, e, subjectDimension);

                // this is one of the requests being tracked, see if the rest are done
                this.measureMetadataRequestComplete();
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    measureDimensionSelected: function(measureDimension) {
        this.chartInfo.measures[this.yAxisMeasureIndex].dimension = measureDimension;

        // if there was a different dimension selection, remove that list view from the series selector
        Ext.getCmp('series-selector-tabpanel').remove('dimension-series-selector-panel', true);
        Ext.getCmp('series-selector-tabpanel').doLayout();

        // todo: review this section
        // get the dimension values for the selected dimension/grouping
        Ext.Ajax.request({
            url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, measureDimension),
            method:'GET',
            disableCaching:false,
            success : function(response, e){
                var reader = new Ext.data.JsonReader({root:'values'}, [{name:'value'}]);
                var o = reader.read(response);

                // put the dimension values into the chartInfo object for the given measure
                // note: 2 version are needed. the values version is needed for the getData call for pivoting the data
                //          and the jsonValues version is needed for the Ext GridPanel
                this.chartInfo.measures[this.yAxisMeasureIndex].dimension.values = new Array();
                this.chartInfo.measures[this.yAxisMeasureIndex].dimension.jsonValues = new Array();
                for(var i = 0; i < o.records.length; i++) {
                    this.chartInfo.measures[this.yAxisMeasureIndex].dimension.values.push(o.records[i].data.value);
                    this.chartInfo.measures[this.yAxisMeasureIndex].dimension.jsonValues.push({value: o.records[i].data.value});
                }
                // initially selected = all values
                this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected = this.chartInfo.measures[this.yAxisMeasureIndex].dimension.values;

                // put the dimension values into a list view for the user to enable/disable series
                var sm = new  Ext.grid.CheckboxSelectionModel({
                    listeners: {
                        scope: this,
                        'selectionChange': function(selModel){
                            // add the selected dimension values to the chartInfo
                            this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected = new Array();
                            var selectedRecords = selModel.getSelections();
                            for(var i = 0; i < selectedRecords.length; i++) {
                                this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected.push(selectedRecords[i].get('value'));
                            }
                            // redraw the line chart with new selections
                            this.renderLineChart();                                        }
                    }
                });
                var newSeriesSelectorPanel = new Ext.Panel({
                    id: 'dimension-series-selector-panel',
                    title: this.chartInfo.measures[this.yAxisMeasureIndex].dimension.label,
                    autoScroll: true,
                    items: [
                        new Ext.grid.GridPanel({
                            id: 'dimension-list-view',
                            autoHeight: true,
                            store: new Ext.data.JsonStore({
                                root: 'jsonValues',
                                fields: ['value'],
                                data: this.chartInfo.measures[this.yAxisMeasureIndex].dimension
                            }),
                            viewConfig: {forceFit: true},
                            border: false,
                            frame: false,
                            hidden: true,
                            columns: [
                                sm,
                                {header: this.chartInfo.measures[this.yAxisMeasureIndex].dimension.label, dataIndex:'value'}
                            ],
                            selModel: sm,
                            header: false,
                            listeners: {
                                scope: this,
                                'viewready': function(grid) {
                                    // initially select all of the deimension values (but suspend events during selection)
                                    var dimSelModel = grid.getSelectionModel();
                                    dimSelModel.suspendEvents(false);
                                    dimSelModel.selectRange(0, grid.getStore().getTotalCount()-1, false);
                                    this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected = this.chartInfo.measures[this.yAxisMeasureIndex].dimension.values;
                                    dimSelModel.resumeEvents();
                                    grid.show();
                                }
                            }
                         })
                    ]
                });
                Ext.getCmp('series-selector-tabpanel').add(newSeriesSelectorPanel);
                Ext.getCmp('series-selector-tabpanel').activate('dimension-series-selector-panel');
                Ext.getCmp('series-selector-tabpanel').doLayout();

                // this change requires a call to getChartData
                this.getChartData();
            },
            failure: function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    seriesPerSubjectChecked: function() {
        // remove any dimension selection/values that were added to the yaxis measure
        delete this.chartInfo.measures[this.yAxisMeasureIndex].dimension;

        // if there was a different dimension selection, remove that list view from the series selector
        Ext.getCmp('series-selector-tabpanel').remove('dimension-series-selector-panel', true);
        Ext.getCmp('series-selector-tabpanel').doLayout();

        // hide the 3rd option for the layout radio group on the chart(s) tab
        //if(Ext.get('chart-layout-per-dimension').checked())
        //Ext.get('chart-layout-per-dimension').hide();

        // this change requires a call to getChartData]
        this.getChartData();
     },

    measureMetadataRequestPending:  function() {
        this.getEl().mask("Loading...", "x-mask-loading");
        this.measureMetadataRequestCounter++;
        console.log(this.measureMetadataRequestCounter);
    },

    measureMetadataRequestComplete: function() {
        this.measureMetadataRequestCounter--;
        console.log(this.measureMetadataRequestCounter);
        if(this.measureMetadataRequestCounter == 0) {
            if(this.getEl().isMasked()) {
                this.getEl().unmask();
            }
            this.getChartData();
        }
    },

    renderSubjects: function(response, e, subjectField) {
        var reader = new Ext.data.JsonReader({root:'values'}, [{name:'value'}]);
        var o = reader.read(response);

        // add all of the values to the subject list view store
        Ext.getCmp('subject-list-view').getStore().add(o.records);
        // initially select all of the subject values from the list view (but suspend events during selection)
        var subjectSelModel = Ext.getCmp('subject-list-view').getSelectionModel();
        subjectSelModel.suspendEvents(false);
        subjectSelModel.selectRange(0, o.records.length, false);
        for(var i = 0; i < o.records.length; i++) {
            this.chartInfo.subject.selection.push(o.records[i].data.value != undefined ? o.records[i].data.value : o.records[i].data);
        }
        subjectSelModel.resumeEvents();
        // now that the subject values are loaded, show the list view
        Ext.getCmp('subject-list-view').show();
        //Ext.getCmp('subject-list-view').setAutoScroll(true);
    },

    getChartData: function() {
        this.chart.getEl().mask("Loading...", "x-mask-loading");
        LABKEY.Visualization.getData({
            successCallback: function(data){
                this.chartInfo.data = data;
                this.renderLineChart();
            },
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            measures: this.chartInfo.measures,
            viewInfo: this.viewInfo,
            sorts: [this.chartInfo.subject, this.chartInfo.measures[this.xAxisMeasureIndex].measure],
            scope: this
        });
    },

    renderLineChart: function()
    {
        // clear the components from the chart panel
        //Ext.get(this.chart.getId()).update("");
        this.chart.removeAll();

	    // one series per subject/measure/dimensionvalue combination
	    var seriesList = [];
	    if(this.chartInfo.measures[this.yAxisMeasureIndex].dimension && this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected) {
	        seriesList = this.chartInfo.measures[this.yAxisMeasureIndex].dimension.selected;
	    }
	    else {
	        seriesList = [this.chartInfo.measures[this.yAxisMeasureIndex].measure.name];
	    }

        var series = [];
        for(var j = 0; j < this.chartInfo.subject.selection.length; j++)
        {
        	for(var i = 0; i < seriesList.length; i++)
        	{
                var yAxisSeries = seriesList[i];
                var subject = this.chartInfo.subject.selection[j];
                var singleSeriesData = [];

                var subjectColName = this.viewInfo.subjectColumn;
                Ext.each(this.chartInfo.data.rows, function(row){
                    var rowSubject = row[this.chartInfo.data.measureToColumn[subjectColName]];
                    if(rowSubject.value ) rowSubject = rowSubject.value;

                    if(rowSubject == subject)
                    {
                        var dataValue = row[this.chartInfo.data.measureToColumn[yAxisSeries]];
                        if(typeof dataValue != "object") {
                            dataValue = {value: dataValue};
                        }

                        singleSeriesData.push({
                            interval: row[this.chartInfo.measures[this.xAxisMeasureIndex].dateOptions.interval],
                            dataValue: dataValue
                        });
                    }
                }, this);

                series.push({
                    subject: subject,
                    yAxisSeries: yAxisSeries,
                    caption: subject + " " + yAxisSeries,
                    data: singleSeriesData,
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
        	Ext.each(this.chartInfo.subject.selection, function(subject) {
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

    	return new Ext.Panel({
            layout: 'fit',
            tbar:[{text:"Export PDF", handler:function(btn) {this.chartComponent.exportImage("pdf");},scope:this}],
            listeners: {
            	render: {
                    scope: this,
                    fn: function(cmp){
                        this.chartComponent = new LABKEY.vis.LineChart({renderTo: cmp.getId(),
                            width: size.width,
                            height: size.height,
                            axes: {
                                y: {
                                    min: (this.chartInfo.measures[this.yAxisMeasureIndex].axis.min ? this.chartInfo.measures[this.yAxisMeasureIndex].axis.min : undefined),
                                    max: (this.chartInfo.measures[this.yAxisMeasureIndex].axis.max ? this.chartInfo.measures[this.yAxisMeasureIndex].axis.max : undefined),
                                    caption: this.chartInfo.measures[this.yAxisMeasureIndex].axis.label,
                                    scale: this.chartInfo.yAxisScale
                                },
                                x: {
                                    min: (this.chartInfo.measures[this.xAxisMeasureIndex].axis.min ? this.chartInfo.measures[this.xAxisMeasureIndex].axis.min : undefined),
                                    max: (this.chartInfo.measures[this.xAxisMeasureIndex].axis.max ? this.chartInfo.measures[this.xAxisMeasureIndex].axis.max : undefined),
                                    caption: this.chartInfo.measures[this.xAxisMeasureIndex].axis.label
                                }
                            },
                            series: tempSeries.length > 0 ? tempSeries : series,
                            main: {title: this.chartInfo.title + (title != null ? ": " + title : "")}
                        });
                    }
                }
            }
        });
    }
});