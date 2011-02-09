/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

var ONE_DAY = 1000 * 60 * 60 * 24;
var ONE_WEEK = ONE_DAY * 7;
var ONE_MONTH = ONE_WEEK * (52/12);
var ONE_YEAR = ONE_DAY * 365;

LABKEY.vis.ChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){

        Ext.apply(this, config);

        LABKEY.vis.ChartPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {

        var items = [];

        if (this.viewInfo.isGrid)
        {
            // if it's just a grid view type, show the data in an editor grid panel.
            var store = new Ext.data.Store({
                data: this.data,
                reader: new LABKEY.ext.ExtendedJsonReader()});

            var grid = new Ext.grid.EditorGridPanel({
                store: store,
                region: 'center',
                columns: this.data.columnModel,
                enableFilters: true,
                viewConfig: {forceFit: true, scrollOffset: 0},
                stripeRows: true,
                autoHeight: true,
                selModel: new Ext.grid.RowSelectionModel(),
                editable: false
            });

            items.push(grid);
        }
        else if(this.viewInfo.type == "line")
        {
		// initialize a couple of charting options
		this.chartPlotChar = "circle";
		this.chartAxisScale = "linear";
		this.chartLayout = 'single';
		this.chartWidthFactor = 0.6; // default to 60% of panel width to allow for chart options panel

		// get the information from the various measures (by axis)
		var axisInfo = new Object();
		Ext.each(this.measures, function(m) {
		    if(m.axis.name == "x-axis")
		    {
			axisInfo.xAxisDateCol = m.measure;
			axisInfo.zeroDateCol = m.dateOptions.zeroDateCol;
			axisInfo.interval = m.dateOptions.interval.substring(0,1).toUpperCase() + m.dateOptions.interval.substring(1); // capitalize the first letter for later
		    }
		    else
		    {
			axisInfo.yAxisMeasure = m.measure;
			axisInfo.yAxisSeriesList = [];
			if(m.dimension)
				axisInfo.yAxisSeriesList = m.dimension.values;
			else
				axisInfo.yAxisSeriesList = [m.measure.name];
		    }
		});
		// store the information to be accessed from the renderLineChart function
		this.chartInfo = {data: this.data, axisInfo: axisInfo};

		// calculate the x-axis time interval (i.e. Days, Weeks)
		var rows = this.data.rows;
		Ext.each(rows, function(row) {
		    var date = new Date(row[this.data.measureToColumn[axisInfo.xAxisDateCol.name]].value);
		    var zdate = new Date(row[this.data.measureToColumn[axisInfo.zeroDateCol.name]].value);
		    row.interval = (date - zdate) / (axisInfo.interval == 'Days' ? ONE_DAY : (axisInfo.interval == 'Weeks' ? ONE_WEEK : (axisInfo.interval == 'Months' ? ONE_MONTH : ONE_YEAR)));
		}, this);

		// create a panel where the final chart will be rendered
		this.chart = new Ext.Panel({
			region: 'center',
			layout: 'fit',
			autoScroll: true,
			frame: false,
			items: [],
			listeners: {
				scope: this,
				'resize': function(cmp){this.renderLineChart(false);}
			}
		});
		items.push(this.chart);

		// the measure/dimension list view in the charting option panel below will need an array of measures/dimensions
		var seriesStoreData = [];
		for(var i = 0; i < this.chartInfo.axisInfo.yAxisSeriesList.length; i++)
			seriesStoreData.push([this.chartInfo.axisInfo.yAxisSeriesList[i]]);

		// the subject list view in the charting option panel below will need an array of subjects
		axisInfo.subjectListing = [];
        var subjectColName = this.viewInfo.subjectColumn;
		Ext.each(this.chartInfo.data.rows, function(row){
			var ptid = row[this.chartInfo.data.measureToColumn[subjectColName]];
			if(ptid.value) ptid = ptid.value;

			if(axisInfo.subjectListing.indexOf(ptid) == -1)
				axisInfo.subjectListing[axisInfo.subjectListing.length] = ptid;
		}, this);
		var subjectStoreData = [];
		Ext.each(axisInfo.subjectListing, function(subject){
			subjectStoreData.push([subject]);
		});

	    // create the charting options panel with the list view items created above and some other options
            var options = new Ext.Panel({
                region: 'east',
                width: 275,
                padding: 5,
                split: true,
                boxMinWidth: 200,
                collapsible: true,
                collapseMode: 'mini',
                animCollapse: false,
                autoScroll: true,
                title: '<center><b>Charting Options</b></center>',
                titleCollapse: true,
                items:[{
                    xtype:'panel',
                    frame:false,
                    padding: 5,
                    header: false,
                    items:[
                            new Ext.Panel({
                                border: false,
                                layout: 'form',
                                labelWidth: 75,
                                defaults: {width: 150},
                                items: [
                                    {
                                        xtype:'combo',
                                        mode: 'local',
                                        fieldLabel: 'Axis Scale',
                                        triggerAction: 'all',
                                        editable: false,
                                        store: [['linear', 'Linear'], ['log', 'Log']],
                                        value: 'linear',
                                        allowBlank: false,
                                        listeners: {
                                            scope: this,
                                            'select': function(cmp, record, index){
                                                this.chartAxisScale = cmp.getValue();
                                                this.renderLineChart(true);
                                            }
                                        }
                                    },
                                    {xtype: 'label', html: '<br/>'},
                                    {
                                        xtype:'combo',
                                        mode: 'local',
                                        fieldLabel: 'Plot Character',
                                        triggerAction: 'all',
                                        editable: false,
                                        store: [['circle', 'Circle'], ['cross', 'Cross'], ['square', 'Square'], ['triangle', 'Triangle']],
                                        value: 'circle',
                                        allowBlank: false,
                                        listeners: {
                                            scope: this,
                                            'select': function(cmp, record, index){
                                                this.chartPlotChar = cmp.getValue();
                                                this.renderLineChart(true);
                                            }
                                        }
                                    },
                                    {xtype: 'label', html: '<br/>'},
                                    {
                                        xtype:'combo',
                                        mode: 'local',
                                        fieldLabel: 'Layout',
                                        triggerAction: 'all',
                                        editable: false,
                                        minListWidth: 250,
                                        store: [['single', 'Single Chart'], ['per-measure-dimension', 'One Chart Per Measure/Dimension'], ['per-participant', 'One Chart Per ' + this.viewInfo.subjectNounSingular]],
                                        value: 'single',
                                        listeners: {
                                            scope: this,
                                            'select': function(cmp, record, index){
                                                this.chartLayout = cmp.getValue();
                                                this.renderLineChart(true);
                                            }
                                        }
                                    }
                                ]
                            }),
                        {xtype: 'label', html: '<br/>'},
                        new Ext.Panel({
			    collapsible:true,
			    layout:'fit',
			    title:'Select Measures/Dimensions:',
			    titleCollapse: true,
			    items: [new Ext.list.ListView({
					store: new Ext.data.ArrayStore({
						fields: [{name: 'series'}],
						data: seriesStoreData
					}),
					border: true,
					frame: true,
					multiSelect: true,
					simpleSelect: true,
					columns: [
					    {header: 'Measures/Dimensions', dataIndex:'series'}
					],
					columnSort: false,
                    boxMaxHeight: 125,
					header: false,
					listeners: {
						scope: this,
						'selectionChange': function(dataView, selections){

							var tempSelections = dataView.getSelectedRecords();
							axisInfo.yAxisSeriesList = [];
							for(i = 0; i < tempSelections.length; i++)
								axisInfo.yAxisSeriesList.push(tempSelections[i].get('series'));

							this.renderLineChart(true);
						}
					}
				    })
			     ]
			}),
			{xtype: 'label', html: '<br/>'},
                        new Ext.Panel({
			    collapsible:true,
			    layout:'fit',
			    title:'Select ' + this.viewInfo.subjectNounPlural + ':',
			    titleCollapse: true,
			    items: [new Ext.list.ListView({
					store: new Ext.data.ArrayStore({
						fields: [{name: 'ptid'}],
						data: subjectStoreData
					}),
					border: true,
					frame: true,
					multiSelect: true,
					simpleSelect: true,
					columns: [
					    {header: this.viewInfo.subjectNounPlural, dataIndex:'ptid'}
					],
					columnSort: false,
                    boxMaxHeight: 125,
					header: false,
					listeners: {
						scope: this,
						'selectionChange': function(dataView, selections){

							var tempSelections = dataView.getSelectedRecords();
							axisInfo.subjectListing = [];
							for(i = 0; i < tempSelections.length; i++)
								axisInfo.subjectListing.push(tempSelections[i].get('ptid'));

							this.renderLineChart(true);
						}
					}
				     })
			     ]
			})
                        ]
                    }]
            });
            items.push(options);
        }
        else
        {
            // other chart types
        }

        // properties for this panel
        this.layout = 'border';
        this.items = items;

        LABKEY.vis.MeasuresPanel.superclass.initComponent.call(this);
    },

    renderLineChart: function(layoutNeeded)
    {
        // clear the components from the chart panel
        //Ext.get(this.chart.getId()).update("");
        this.chart.removeAll();

	// one series per ptid/yAxisSeriesList combination
        var series = [];
        for(var j = 0; j < this.chartInfo.axisInfo.subjectListing.length; j++)
        {
        	for(var i = 0; i < this.chartInfo.axisInfo.yAxisSeriesList.length; i++)
        	{
		    var yAxisSeries = this.chartInfo.axisInfo.yAxisSeriesList[i];
		    var ptid = this.chartInfo.axisInfo.subjectListing[j];
		    var singleSeriesData = [];

            var subjectColName = this.viewInfo.subjectColumn;
		    Ext.each(this.chartInfo.data.rows, function(row){
		    	var rowPtid = row[this.chartInfo.data.measureToColumn[subjectColName]];
		    	if(rowPtid.value ) rowPtid = rowPtid.value;

		    	if(rowPtid == ptid)
		    	{
		    		singleSeriesData.push({
		    			interval: row.interval,
		    			dataValue: row[this.chartInfo.data.measureToColumn[yAxisSeries]]
		    		});
		    	}
		    }, this);

		    series.push({
		    	participant: ptid,
		    	yAxisSeries: yAxisSeries,
			caption: ptid + " " + yAxisSeries,
			data: singleSeriesData,
			xProperty:"interval",
			yProperty: "dataValue",
			dotShape: this.chartPlotChar || 'circle'
		    });
		}
        }

        var size = {width: (this.chart.getInnerWidth() * .9), height: (this.chart.getInnerHeight() * .9)};
        
	    // three options: all series on one chart, one chart per ptid, or one chart per measure/dimension
        var charts = [];
        if(this.chartLayout == "per-participant") {
        	Ext.each(this.chartInfo.axisInfo.subjectListing, function(ptid) {
        		charts.push(this.newLineChart(size, series, {parameter: "participant", value: ptid}, ptid));
        	}, this);
        }
        else if(this.chartLayout == "per-measure-dimension") {
        	Ext.each(this.chartInfo.axisInfo.yAxisSeriesList, function(md) {
        		charts.push(this.newLineChart(size, series, {parameter: "yAxisSeries", value: md}, md));
        	}, this);
        }
        else if(this.chartLayout == "single")
            charts.push(this.newLineChart(size, series, null, null));

        this.chart.add(charts);
        this.chart.doLayout();
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
            listeners: {
            	render: {
            		scope: this,
            		fn: function(cmp){
				new LABKEY.vis.LineChart({renderTo: cmp.getId(),
                    width: size.width,
                    height: size.height,
/*
					width: size.width * this.chartWidthFactor,
					height: size.height * 0.65, // 65% of parent panel height
*/
                    axes: {y: {/*min: 0, max: 100,*/caption: this.chartInfo.axisInfo.yAxisMeasure.label, scale: (this.chartAxisScale || 'linear')},
                        x: {/*min: 0, max: 5000,*/ caption: this.chartInfo.axisInfo.interval + " Since " + this.chartInfo.axisInfo.zeroDateCol.label}},
					series: tempSeries.length > 0 ? tempSeries : series,
					main: {title: title}
				});
			}
		}
            }
        });
    }
});