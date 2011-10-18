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
LABKEY.requiresScript("vis/groupSelector.js");
LABKEY.requiresCss("_images/icons.css");


Ext.QuickTips.init();
$h = Ext.util.Format.htmlEncode;

LABKEY.vis.TimeChartPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){
        // properties for this panel
        Ext.apply(config, {
            layout: 'border',
            monitorResize: true,
            maxCharts: 30
        });

        // support backwards compatibility for charts saved prior to chartInfo reconfig (2011-08-31)
        if (config.chartInfo)
        {
            Ext.applyIf(config.chartInfo, {
                axis: [],
                chartSubjectSelection: config.chartInfo.chartLayout == 'per_group' ? 'groups' : 'subjects'
            });
            for (var i = 0; i < config.chartInfo.measures.length; i++)
            {
                var md = config.chartInfo.measures[i];

                // if the axis info is in md, move it to the axis array
                if (md.axis)
                {
                    // default the y-axis to the left side if not specified
                    if (md.axis.name == "y-axis")
                            Ext.applyIf(md.axis, {side: "left"});

                    // move the axis info to the axis array
                    if (this.getAxisIndex(config.chartInfo.axis, md.axis.name, md.axis.side) == -1)
                        config.chartInfo.axis.push(Ext.apply({}, md.axis));

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
                                    dateCol: Ext.apply({}, md.measure),
                                    zeroDateCol: Ext.apply({}, md.dateOptions.zeroDateCol),
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

        Ext.apply(this, config);

        LABKEY.vis.TimeChartPanel.superclass.constructor.call(this);
    },

    initComponent : function() {
        // chartInfo will be all of the information needed to render the line chart (axis info and data)
        if(typeof this.chartInfo != "object") {
            this.chartInfo = this.getInitializedChartInfo();
        }

        // hold on to the x and y axis measure index
        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");

        // add a listener to call measureSelected on render if this is a saved chart
        this.listeners = {
            scope: this,
            'render': function(){
                if(typeof this.saveReportInfo == "object") {
                    this.measureSelected(this.chartInfo.measures[0].measure, false);
                    this.editorMeasurePanel.initializeDimensionStores();
                }
            }
        };

        // keep track of requests for measure metadata ajax calls to know when all are complete
        this.measureMetadataRequestCounter = 0;

        var items = [];

        if(this.viewInfo.type == "line") {
            this.editorOverviewPanel = new LABKEY.vis.ChartEditorOverviewPanel({
                reportInfo: this.saveReportInfo,
                saveThumbnail: this.chartInfo.saveThumbnail == undefined ? true : this.chartInfo.saveThumbnail,
                listeners: {
                    scope: this,
                    'initialMeasuresStoreLoaded': function(data) {
                        // pass the measure store JSON data object to the measures panel
                        this.editorMeasurePanel.setMeasuresStoreData(data);
                    },
                    'initialMeasureSelected': function(initMeasure) {
                        Ext.getCmp('chart-editor-tabpanel').activate(this.editorMeasurePanel.getId());
                        this.measureSelected(initMeasure, true);
                    },
                    'saveThumbnailChecked': function(checked) {
                        this.chartInfo.saveThumbnail = checked;
                    },
                    'saveChart': this.saveChart
                }
            });

            this.editorMeasurePanel = new LABKEY.vis.ChartEditorMeasurePanel({
                disabled: true,
                origMeasures: this.chartInfo.measures, 
                filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Visualization.getDataFilterFromURL(),
                filterQuery: this.chartInfo.filterQuery ? this.chartInfo.filterQuery : this.getFilterQuery(),
                viewInfo: this.viewInfo,
                listeners: {
                    scope: this,
                    'measureSelected': this.measureSelected,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'measureRemoved': function(){
                        this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
                        this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
                        this.editorChartsPanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());
                        this.getChartData();

                        // if all of the measures have been removed, disable any non-relevant elements
                        if (this.editorMeasurePanel.getNumMeasures() == 0)
                        {
                            this.disableNonMeasureTabPanels();
                            this.exportPdfMenuBtn.disable();
                            this.exportPdfSingleBtn.disable();
                            this.viewGridBtn.disable();
                            this.viewChartBtn.disable();
                        }
                    },
                    'filterCleared': function () {
                        // remove the filter and refresh the data
                        this.chartInfo.filterUrl = null;
                        this.chartInfo.filterQuery = null;
                        this.getChartData();
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorXAxisPanel = new LABKEY.vis.ChartEditorXAxisPanel({
                disabled: true,
                axis: this.chartInfo.axis[xAxisIndex] ? this.chartInfo.axis[xAxisIndex] : {},
                zeroDateCol: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.zeroDateCol : {},
                interval: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.interval : "Days",
                time: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].time ? this.chartInfo.measures[0].time : 'date',
                timepointType: this.viewInfo.TimepointType,
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
                    },
                    'measureMetadataRequestPending': this.measureMetadataRequestPending,
                    'measureMetadataRequestComplete': this.measureMetadataRequestComplete
                }
            });

            this.editorYAxisLeftPanel = new LABKEY.vis.ChartEditorYAxisPanel({
                disabled: !this.chartInfo.axis[leftAxisIndex],
                title: "Left-Axis",
                axis: this.chartInfo.axis[leftAxisIndex] ? this.chartInfo.axis[leftAxisIndex] : {side: "left"},
                defaultLabel: this.editorMeasurePanel.getDefaultLabel("left"),
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
            //Set radio/textfield names to aid with TimeChartTest.
            this.editorYAxisLeftPanel.rangeManualRadio.name = "leftaxis_range";
            this.editorYAxisLeftPanel.rangeAutomaticRadio.name = "leftaxis_range";
            this.editorYAxisLeftPanel.labelTextField.name = "left-axis-label-textfield";

            this.editorYAxisRightPanel = new LABKEY.vis.ChartEditorYAxisPanel({
                disabled: !this.chartInfo.axis[rightAxisIndex],
                title: "Right-Axis",
                axis: this.chartInfo.axis[rightAxisIndex] ? this.chartInfo.axis[rightAxisIndex] : {side: "right"},
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
            //Set radio/textfield names to aid with TimeChartTest.
            this.editorYAxisRightPanel.rangeManualRadio.name = "rightaxis_range";
            this.editorYAxisRightPanel.rangeAutomaticRadio.name = "rightaxis_range";
            this.editorYAxisRightPanel.labelTextField.name = "right-axis-label-textfield";

            this.editorChartsPanel = new LABKEY.vis.ChartEditorChartsPanel({
                disabled: true,
                chartLayout: this.chartInfo.chartLayout,
                chartSubjectSelection: this.chartInfo.chartSubjectSelection,
                mainTitle: this.chartInfo.title,
                lineWidth: this.chartInfo.lineWidth,
                hideDataPoints: this.chartInfo.hideDataPoints,
                subjectNounSingular: this.viewInfo.subjectNounSingular,
                subjectNounPlural: this.viewInfo.subjectNounPlural,
                listeners: {
                    scope: this,
                    'chartDefinitionChanged': function(requiresDataRefresh){
                        if(this.editorChartsPanel.groupLayoutChanged == true){
                            this.editorChartsPanel.groupLayoutChanged = false;
                        }
                        if(requiresDataRefresh){
                            this.getChartData();
                        }
                        else{
                            this.loader();
                        }
                    },
                    'groupLayoutSelectionChanged': function(groupLayoutSelected){
                        if (groupLayoutSelected)
                        {
                            this.seriesSelectorTabPanel.activate(this.groupsSelector.getId());
                            this.seriesSelectorTabPanel.unhideTabStripItem(this.groupsSelector);
                            this.seriesSelectorTabPanel.hideTabStripItem(this.subjectSelector);
                            this.disableAggregateSelection(false);
                        }
                        else
                        {
                            this.seriesSelectorTabPanel.activate(this.subjectSelector.getId());
                            this.seriesSelectorTabPanel.unhideTabStripItem(this.subjectSelector);
                            this.seriesSelectorTabPanel.hideTabStripItem(this.groupsSelector);
                            this.disableAggregateSelection(true);
                        }
                    }
                }
            });

            this.chartEditor = new Ext.Panel({
                layout: 'fit',
                header: false,
                region: 'north',
                height: 220,
                border: false,
                split: true,
                collapsible: true,
                collapseMode: 'mini',
                hideCollapseTool: true,
                items: [
                    new Ext.TabPanel({
                        id: 'chart-editor-tabpanel',
                        autoScroll: true,
                        activeTab: 0,
                        items: [
                            this.editorOverviewPanel,
                            this.editorMeasurePanel,
                            this.editorXAxisPanel,
                            this.editorYAxisLeftPanel,
                            this.editorYAxisRightPanel,
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
                subject: (this.chartInfo.chartSubjectSelection != "groups" ? this.chartInfo.subject : {}),
                subjectNounPlural: this.viewInfo.subjectNounPlural,
                subjectNounSingular: this.viewInfo.subjectNounSingular,
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

            this.groupsSelector = new LABKEY.vis.GroupSelector({
                subject: (this.chartInfo.chartSubjectSelection == "groups" ? this.chartInfo.subject : {}),
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

            this.seriesSelectorTabPanel = new Ext.TabPanel({
                id: 'series-selector-tabpanel',
                activeTab: (this.chartInfo.chartSubjectSelection == "subjects" ? 0 : 1),
                padding: 5,
                enablePanelScroll: true,
                enableTabScroll: true,
                items: [
                    this.subjectSelector,
                    this.groupsSelector
                ],
                listeners: {
                    scope: this,
                    'afterRender': function(){
                        if(this.chartInfo.chartSubjectSelection == "groups"){
                            this.seriesSelectorTabPanel.unhideTabStripItem(this.groupsSelector);
                            this.seriesSelectorTabPanel.hideTabStripItem(this.subjectSelector);
                            this.disableAggregateSelection(false);
                        } else {
                            this.seriesSelectorTabPanel.unhideTabStripItem(this.subjectSelector);
                            this.seriesSelectorTabPanel.hideTabStripItem(this.groupsSelector);
                            this.disableAggregateSelection(true);
                        }
                    }
                }
            })
            
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
                    this.seriesSelectorTabPanel
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
            this.loaderName = 'renderLineChart';
            this.viewGridBtn = new Ext.Button({text: "View Data", handler: this.viewDataGrid, scope: this, disabled: true});
            this.viewChartBtn = new Ext.Button({text: "View Chart(s)", handler: this.renderLineChart, scope: this, hidden: true});

            // setup exportPDF button and menu (items to be added later)
            // the single button will be used for "single" chart layout
            // and the menu button will be used for multi-chart layouts
            this.exportPdfMenu = new Ext.menu.Menu({cls: 'extContainer'});
            this.exportPdfMenuBtn = new Ext.Button({
                text: 'Export PDF',
                menu: this.exportPdfMenu,
                hidden: true,
                scope: this
            });
            this.exportPdfSingleBtn = new Ext.Button({
                text: 'Export PDF',
                disabled: true,
                scope: this
            });

            // checkboxes for displaying individual and/or aggregate lines
            this.displayIndividualCheckbox = new Ext.form.Checkbox({
                boxLabel  : 'Show Individual Lines',
                name      : 'Show Individual Lines',
                disabled  : true,
                checked   : this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
                value     : this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
                listeners : {
                    check : function(cmp, checked){
                        this.displayIndividual = checked;
                        this.getChartData();
                    },
                    beforerender: function(){
                        this.displayIndividual = this.chartInfo.displayIndividual;
                    },
                    scope : this
                }
            });

            this.displayAggregateCheckbox = new Ext.form.Checkbox({
                boxLabel  : 'Show Mean',
                name      : 'Show Mean',
                disabled  : true,
                checked   : this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
                value     : this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
                listeners : {
                    check : function(cmp, checked){
                        this.displayAggregate = checked;

                        // enable/disable the aggregate combo box accordingly
                        this.displayAggregateComboBox.setDisabled(!checked);
                        this.getChartData();
                    },
                    beforerender: function(){
                        this.displayAggregate = this.chartInfo.displayAggregate;
                    },
                    scope : this
                }
            });

            // combobox for selecting which aggregate to display when checkbox is selected
            this.displayAggregateComboBox = new Ext.form.ComboBox({
                triggerAction : 'all',
                mode          : 'local',
                store         : new Ext.data.ArrayStore({
                       fields : ['value'],
                       data   : [['Mean'], ['Count']]
                }),
                disabled      : this.displayAggregate || true,
                hideLabel     : true,
                forceSelection: 'true',
                valueField    : 'value',
                displayField  : 'value',
                value         : 'Mean',
                width         : 75,
                listeners     : {
                    select    : function(cb){
                        this.displayAggregateValue = cb.getValue();
                        //this.fireEvent('chartDefinitionChanged', true);
                    },
                    scope     : this
                }
            });

            this.disableAggregateSelection = function(disable){
                this.displayAggregateCheckbox.setDisabled(disable);
                this.displayIndividualCheckbox.setDisabled(disable);
                if(disable){
                    this.displayAggregateComboBox.setDisabled(true);
                } else {
                    this.displayAggregateComboBox.setDisabled(!this.displayAggregateCheckbox.getValue());
                }
            }

            this.chart = new Ext.Panel({
                id: 'chart-tabpanel',
                region: 'center',
                layout: 'fit',
                frame: false,
                autoScroll: true,
                tbar: [
                        this.viewGridBtn,
                        this.viewChartBtn,
                        '-',
                        this.exportPdfSingleBtn,
                        this.exportPdfMenuBtn,
                        '->',
                        this.displayIndividualCheckbox,
                        {xtype: 'tbspacer', width: 10},
                        this.displayAggregateCheckbox,
                        {xtype: 'tbspacer', width: 5},
//                        this.displayAggregateComboBox, //this needs to be un-commented when we figure out what to do with count.
                        {xtype: 'tbspacer', width: 10}
                ],
                items: [],
                listeners: {
                    scope: this,
                    'resize': function(cmp){
                        // only call loader if the data object is available and the loader equals renderLineChart
                        if((this.individualChartSubjectData || this.aggregateChartSubjectData) && this.loaderName == 'renderLineChart') {
                            this.loader();
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

        Ext.applyIf(this, {
            autoResize: true
        });

        if (this.autoResize)
        {
            Ext.EventManager.onWindowResize(function(w,h){
                this.resizeToViewport(w,h);
            }, this);
            this.on("render", function(){Ext.EventManager.fireWindowResize();}, this);
        }

        this.items = items;

        this.markDirty(false);
        window.onbeforeunload = LABKEY.beforeunload(this.isDirty, this);

        LABKEY.vis.TimeChartPanel.superclass.initComponent.apply(this, arguments);
    },

    resizeToViewport : function(w,h) {
        if (!this.rendered)
            return;

        var padding = [55,0];
        var xy = this.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0])//,
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

    measureSelected: function(measure, userSelectedMeasure) {
        // add any user selected measures to the measure panel object
        if(userSelectedMeasure){
            var measureIndex = this.editorMeasurePanel.addMeasure(measure);
            this.editorMeasurePanel.setDimensionStore(measureIndex);
            this.editorMeasurePanel.setMeasureDateStore(measure, measureIndex);
            this.editorMeasurePanel.setYAxisSide(measureIndex);
        }

        // these method calls should only be made for chart initialization
        // (i.e. showing saved chart or first measure selected for new chart)
        var numMeasures = this.editorMeasurePanel.getNumMeasures();
        if(!userSelectedMeasure || numMeasures == 1){
            this.subjectSelector.getSubjectValues();
            this.editorXAxisPanel.setZeroDateStore(measure.schemaName);

            if(userSelectedMeasure){
                this.editorOverviewPanel.updateOverview(this.saveReportInfo, this.chartInfo.saveThumbnail);
            }
        }

        // these method calls should be made for all measure selections
        this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));
        this.editorChartsPanel.setMainTitle(this.editorMeasurePanel.getDefaultTitle());

        this.enableTabPanels();
    },

    enableTabPanels: function(){
        this.editorOverviewPanel.enable();
        this.editorMeasurePanel.enable();
        this.editorXAxisPanel.enable();
        this.editorChartsPanel.enable();
    },

    disableNonMeasureTabPanels: function(){
        this.editorOverviewPanel.disable();
        this.editorXAxisPanel.disable();
        this.editorYAxisRightPanel.disable();
        this.editorYAxisLeftPanel.disable();
        this.editorChartsPanel.disable();
    },

    isDirty : function() {
        return this.dirty;
    },

    markDirty : function(value) {
        this.dirty = value;
    },

    measureMetadataRequestPending:  function() {
        // mask panel and remove the chart(s)
        this.getEl().mask("loading...");
        this.clearChartPanel();

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
        this.getEl().mask("loading...");
        this.clearChartPanel();
        this.loaderCount = 0; //Used to prevent the loader from running until we have recieved all necessary callbacks.
        if((this.displayIndividualCheckbox.disabled === false && this.displayIndividualCheckbox.getValue() === true) || (this.displayIndividualCheckbox.disabled === true)){
            this.loaderCount++;
        }
        if(this.displayAggregateCheckbox.disabled === false && this.displayAggregate){
            this.loaderCount++;
        }

        if (this.loaderCount == 0)
        {
            this.clearChartPanel("Please select either \"Show Individual Lines\" or \"Show Aggregate\".");
            return;
        }

        // get the updated chart information from the various tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();

        if(this.chartInfo.measures.length == 0){
           this.clearChartPanel("No measure selected. Please click the \"Add Measure\" button to select a measure.");
           return;
        }

        // the subject column is used in the sort, so it needs to be applied to one of the measures
        Ext.apply(this.chartInfo.subject, {
            name: this.viewInfo.subjectColumn,
            schemaName: this.chartInfo.measures[0].measure.schemaName,
            queryName: this.chartInfo.measures[0].measure.queryName
        });


        if((this.displayIndividualCheckbox.disabled === false && this.displayIndividualCheckbox.getValue() === true) || (this.displayIndividualCheckbox.disabled === true)){
            //Get data for individual lines.
            LABKEY.Visualization.getData({
                success: function(data){
                    // store the data in an object by subject for use later when it comes time to render the line chart
                    this.individualData = data;
                    this.individualChartSubjectData = new Object();
                    this.individualChartSubjectData.filterDescription = data.filterDescription;
                    this.markDirty(!this.editorOverviewPanel.isSavedReport()); // only mark when editing unsaved report
                    var gridSortCols = [];

                    // make sure each measure/dimension has at least some data
                    var seriesList = this.getSeriesList();
                    this.individualHasData = {};
                    Ext.each(seriesList, function(s) {
                        this.individualHasData[s.name] = false;
                    }, this);

                    var displayOrder = false;
                    if (this.editorXAxisPanel.getTime() == "visit"){
                        displayOrder = this.hasDisplayOrder(data);
                        if(displayOrder == false){
                            //If there is no explicitly set displayOrder then we must make one.
                            this.generateDisplayOrderAndLabels(data);
                        } else {
                            this.generateDisplayLabels(data);
                        }
                    } else {
                        if(this.displayLabels){
                            this.displayLabels = undefined;
                        }
                    }

                    Ext.each(data.rows, function(row){
                            // get the subject id from the data row
                            var rowSubject = row[this.individualData.measureToColumn[this.viewInfo.subjectColumn]];
                            if(rowSubject.value ) rowSubject = rowSubject.value;

                            // if this is a new subject to the chartSubjectData object, then initialize it
                            if(!this.individualChartSubjectData[rowSubject]) {
                                this.individualChartSubjectData[rowSubject] = new Object();

                                // initialize an array for each meausre
                                Ext.each(seriesList, function(s) {
                                    this.individualChartSubjectData[rowSubject][s.name] = new Array();
                                }, this);
                            }

                            // add the data value and interval value to the appropriate place in the chartSubjectData object for each measure
                            Ext.each(seriesList, function(s) {
                                var dataValue = row[this.individualData.measureToColumn[s.name]];
                                if(typeof dataValue != "object") {
                                    dataValue = {value: dataValue};
                                }

                                // record that this measure has data
                                if (dataValue.value) this.individualHasData[s.name] = true;

                                // if more than one measure, the interval column will be dependent on the measure name
                                if (this.editorXAxisPanel.getTime() == "date")
                                {
                                    var measureIntervalKey = this.individualData.measureToColumn[this.chartInfo.measures[s.measureIndex].dateOptions.dateCol.name]
                                            + "_" + this.chartInfo.measures[s.measureIndex].dateOptions.interval;
                                    if (!row[measureIntervalKey])
                                        measureIntervalKey = this.chartInfo.measures[s.measureIndex].dateOptions.interval;
                                    this.individualChartSubjectData[rowSubject][s.name].push({
                                        interval: row[measureIntervalKey],
                                        dataValue: dataValue
                                    });

                                    // keep track of the interval keys for use later in sorting the grid
                                    if (gridSortCols.indexOf(measureIntervalKey) == -1)
                                        gridSortCols.push(measureIntervalKey);
                                } else {
                                    var intervalValue;
                                    if(displayOrder == false){
                                        intervalValue = this.displayOrder[row[this.individualData.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value];
                                    } else{
                                        intervalValue = row[data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value;
                                    }
                                    this.individualChartSubjectData[rowSubject][s.name].push({
                                        interval: intervalValue,
                                        dataValue: dataValue
                                    });
                                }
                            }, this);
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
                        this.loader();
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

        if(this.displayAggregateCheckbox.disabled === false && this.displayAggregate){
            //Get data for Aggregates.
            var groups = [];
            for(var i = 0; i < this.chartInfo.subject.groups.length; i++){
                groups.push(this.chartInfo.subject.groups[i].id);
            }

            //Set aggregateIntervalsConverted to false so they get converted on generateSeries().
            this.aggregateIntervalsConverted = false;

            LABKEY.Visualization.getData({
                success: function(data){
                    this.aggregateData = data;
                    this.aggregateChartSubjectData = new Object();
                    this.aggregateChartSubjectData.filterDescription = data.filterDescription;

                    // make sure each measure/dimension has at least some data
                    var seriesList = this.getSeriesList();
                    this.aggregateHasData = {};
                    Ext.each(seriesList, function(s) {
                        this.aggregateHasData[s.name] = false;
                    }, this);

                    if(!this.displayIndividualCheckbox.getValue() && this.editorXAxisPanel.getTime() == "visit"){
                        this.aggregateData.hasDisplayOrder = this.hasDisplayOrder(data);
                        if(this.aggregateData.hasDisplayOrder){
                            // If only aggregates are selected, and we have a display order, then we need to generate the
                            // labels with the aggregate rows.
                            this.generateDisplayLabels(this.aggregateData);
                        } else{
                            this.aggregateData.hasDisplayOrder = false;
                            this.generateDisplayOrderAndLabels(this.aggregateData);
                        }
                    }
                    
                    Ext.each(data.rows, function(row){
                        var rowSubject = row.CategoryId.displayValue;
                        if(!this.aggregateChartSubjectData[rowSubject]){
                            this.aggregateChartSubjectData[rowSubject] = new Object();

                            //initialize an array for each measure
                            Ext.each(seriesList, function(s) {
                                this.aggregateChartSubjectData[rowSubject][s.name] = new Array();
                            }, this);
                        }

                        Ext.each(seriesList, function(s) {
                            var dataValue = row[this.aggregateData.measureToColumn[s.name]];
                            var measureIntervalKey;
                            
                            if(typeof dataValue != "object") {
                                dataValue = {value: dataValue};
                            }
                            // record that this measure has data
                            if (dataValue.value) this.aggregateHasData[s.name] = true;

                            if (this.editorXAxisPanel.getTime() == "visit"){
                                measureIntervalKey = this.aggregateData.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"];
                            } else {
                                measureIntervalKey = this.chartInfo.measures[s.measureIndex].dateOptions.interval;
                            }

                            this.aggregateChartSubjectData[rowSubject][s.name].push({
                                interval: row[measureIntervalKey],
                                dataValue: dataValue
                            });
                        }, this);
                    }, this);

                    // now that we have the temp grid info, enable the View Data button
                    // and make sure that the view charts button is hidden
                    this.viewGridBtn.setDisabled(false);
                    this.viewChartBtn.hide();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loader();
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

    hasDisplayOrder: function(data){
        var rows = data.rows;
        for(var i = 0; i < rows.length; i++){
            if(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value != 0){
                return true;
            }
        }

        return false;
    },

    generateDisplayOrderAndLabels: function(data){
        //Generates the labels used for tickmarks, Used when no explicit Display order has been set by the user.
        this.longLabels = false;
        var seqAndLabels = [];
        var sequenceNums = [];
        this.displayOrder = {};
        this.displayLabels = {};
        var rows = data.rows;
        for(var i = 0; i < rows.length; i++){
            if(sequenceNums.indexOf(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/sequencenum"]].value) == -1){
                sequenceNums.push(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/sequencenum"]].value);
                seqAndLabels.push({
                    displayLabel: rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value,
                    sequenceNumber: rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/sequencenum"]].value
                });
            }
            if(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value.length > 4){
                this.longLabels = true;
            }
        }

        //Sort the labels by sequence number.
        seqAndLabels.sort(function (a,b) { return a.sequenceNumber-b.sequenceNumber });
        for(var i = 0; i < seqAndLabels.length; i++){
            this.displayOrder[seqAndLabels[i].displayLabel] = i; //Display Label to Order map.
            this.displayLabels[i] = seqAndLabels[i].displayLabel; //Display Order to Label map.
        }
    },

    generateDisplayLabels: function(data){
        // Generates the labels used for tickmarks, used when an explicit display order has been set.
        // Also generates a new display order form 0 to n-1 so it plays nicely with protovis.
        this.longLabels = false;
        var rows = data.rows;
        var currentDisplayOrder = [];
        var newDisplayOrderMap = {};
        this.displayLabels = {};
        this.displayOrder = {};

        // re-map display order to be from 0 to n-1 so it plays nicely with protovis.
        for(var i = 0; i < rows.length; i++){
            //Store all of the unique display order values.
            if(currentDisplayOrder.indexOf(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value) == -1){
                currentDisplayOrder.push(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value);
            }
            if(rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value.length > 12){
                this.longLabels = true;
            }
        }
        //sort the display order values.
        currentDisplayOrder.sort(function (a,b) { return a-b});
        for(var i = 0; i < currentDisplayOrder.length; i++){
            //renumber the display order.
            newDisplayOrderMap[currentDisplayOrder[i]] = i;
        }
        // generate displayLabels and displayOrder maps.
        for(var i = 0; i < rows.length; i++){
            rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value = newDisplayOrderMap[rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value];
            this.displayLabels[rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value] = rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value;
            this.displayOrder[rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/Label"]].value] = rows[i][data.measureToColumn[this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder"]].value;
        }
        
    },

    renderLineChart: function(force)
    {
        // mask panel and remove the chart(s)
        this.getEl().mask("loading...");
        this.clearChartPanel("loading...");

        // get the updated chart information from the varios tabs of the chartEditor
        this.chartInfo = this.getChartInfoFromEditorTabs();

        // enable/disable the left and right axis tabs
        (this.getAxisIndex(this.chartInfo.axis, "y-axis", "left") > -1 ? enableAndSetRangeOptions(this.editorYAxisLeftPanel) : this.editorYAxisLeftPanel.disable());
        (this.getAxisIndex(this.chartInfo.axis, "y-axis", "right") > -1 ? enableAndSetRangeOptions(this.editorYAxisRightPanel) : this.editorYAxisRightPanel.disable());

        function enableAndSetRangeOptions(yAxisPanel){
            //We have to call setRangeFormOptions AFTER we enable the panel, otherwise all fields get enabled.
            yAxisPanel.enable();
            yAxisPanel.setRangeFormOptions(yAxisPanel.axis.range.type);
        }

        if (!this.editorYAxisLeftPanel.userEditedLabel)
            this.editorYAxisLeftPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("left"));
        if (!this.editorYAxisRightPanel.userEditedLabel) 
            this.editorYAxisRightPanel.setLabel(this.editorMeasurePanel.getDefaultLabel("right"));

        if (this.individualChartSubjectData && this.individualChartSubjectData.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.individualChartSubjectData.filterDescription);

        if(this.chartInfo.measures.length == 0){
           this.clearChartPanel("No measure selected. Please click the \"Add Measure\" button to select a measure.");
           return;
        }

        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");
        if(xAxisIndex == -1){
           Ext.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        // show the viewGrid button and hide the viewCharts button
        this.viewChartBtn.hide();
        this.viewGridBtn.show();
        this.loader = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // check to see if any of the measures don't have data, and display a message accordingly
        if (force !== true) {
            var msg = ""; var sep = "";
            Ext.iterate(this.individualHasData, function(key, value, obj){
                if (!value)
                {
                    msg += sep + key;
                    sep = ", ";
                }
            }, this);
            if (msg.length > 0) {
                this.addWarningText("No data found in: " + msg + " for the selected " + this.viewInfo.subjectNounPlural + ".");
            }
        }

	    // one series per y-axis subject/measure/dimensionvalue combination
	    var seriesList = this.getSeriesList();

        var series = [];

        if((this.displayIndividualCheckbox.disabled === false && this.displayIndividualCheckbox.getValue() === true) || (this.displayIndividualCheckbox.disabled === true)){
            //generate series for individual lines.
            series = this.generateSeries(series, seriesList, false);
        }

        if(this.displayAggregateCheckbox.disabled === false && this.displayAggregate){
            //generate series for aggregate lines.
            series = this.generateSeries(series, seriesList, true);
        }

        var size = {width: (this.chart.getInnerWidth() * .95), height: (this.chart.getInnerHeight() * .97)};

        if (this.chartInfo.chartLayout != "single")
        {
            //ISSUE In multi-chart case, we need to precompute the default axis ranges so that all charts share them.
            //Should have more of this capability in ChartComponent (essentially need to build a single chart with all data)
            //but didn't want to refactor that code substantially..
            var allX = [];
            var allLeft = [];
            var allRight = [];

            Ext.each(series, function (ser) {
                Ext.each(ser.data, function(row) {
                    var xValue = row.interval;
                    var yValue = row.dataValue;
                    if (xValue != null && typeof xValue == "object")
                        xValue = xValue.value;
                    if (yValue != null && typeof yValue == "object")
                        yValue = yValue.value;
                    if (xValue != null && yValue != null) {
                        allX.push(xValue);
                        if(ser.axis == "left"){
                            allLeft.push(yValue);
                        } else {
                            allRight.push(yValue);
                        }
                    }
                })
            });
            this.autoAxisRange = {
                x:LABKEY.vis.getAxisRange(allX, this.chartInfo.axis[xAxisIndex].scale)
            };
            if (leftAxisIndex > -1) {
                this.autoAxisRange.left = LABKEY.vis.getAxisRange(allLeft, this.chartInfo.axis[leftAxisIndex].scale);
            }
            if (rightAxisIndex > -1) {
                this.autoAxisRange.right = LABKEY.vis.getAxisRange(allRight, this.chartInfo.axis[rightAxisIndex].scale);
            }
        }
        else   //Use an undefined min & max so that chart computes it
            this.autoAxisRange = {x:{}, left:{}, right:{}}; //Let the chart compute this

        // remove any existing charts, purge listeners from exportPdfSingleBtn, and remove items from the exportPdfMenu button
        this.chart.removeAll();
        this.exportPdfSingleBtn.purgeListeners();
        this.exportPdfMenu.removeAll();

	    // four options: all series on one chart, one chart per subject, one chart per group, or one chart per measure/dimension
        var charts = [];
        this.firstChartComponent = null;
        if (this.chartInfo.chartLayout == "per_subject")
        {
            // warn if user doesn't have an subjects selected
            if (this.chartInfo.subject.values.length == 0)
            {
                this.addWarningText("Please select at least one subject.");
            }
            else
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.values.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                for (var i = 0; i < (this.chartInfo.subject.values.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.values.length); i++)
                {
                    var subject = this.chartInfo.subject.values[i];
                    charts.push(this.newLineChart(size, series, {parameter: "subject", value: subject}, subject));
                }
            }
        }
        else if (this.chartInfo.chartLayout == "per_group")
        {
            // warn if use doesn't have any groups selected
            if (this.chartInfo.subject.groups.length == 0)
            {
                this.addWarningText("Please select at least one group.");
            }
            else
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.groups.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }
                    //Display individual lines
                for (var i = 0; i < (this.chartInfo.subject.groups.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.groups.length); i++)
                {
                    var group = this.chartInfo.subject.groups[i];
                    charts.push(this.newLineChart(size, series, {parameter: "subject", value: group.participantIds}, group.label));
                }
            }
        }
        else if (this.chartInfo.chartLayout == "per_dimension")
        {
            if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0)
            {
                this.addWarningText("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + '.');
            } else if(this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1){
                this.addWarningText("Please select at least one group.");
            } else {
                for (var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++)
                {
                    var md = seriesList[i].name;
                    charts.push(this.newLineChart(size, series, {parameter: "yAxisSeries", value: md}, md));
                }

                // warn if user doesn't have an dimension values selected or if the max number has been exceeded
                if (seriesList.length == 0)
                {
                    this.addWarningText("Please select at least one dimension value.");
                }
                else if (seriesList.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts");
                }
            }
        }
        else if (this.chartInfo.chartLayout == "single")
        {
            if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0)
            {
                this.addWarningText("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + '.');
            } else if(this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1){
                this.addWarningText("Please select at least one group.");
            } else if(series.length == 0) {
                this.addWarningText("Please select at least one dimension value.")
            } else {
                //Single Line Chart, with all participants or groups.
                charts.push(this.newLineChart(size, series, null, null));
            }
        }

        // if the user has selected more charts than the max allowed, show warning
        if(this.warningText.length > 0){
            this.chart.add(new Ext.form.DisplayField({
                autoHeight: 25,
                autoWidth: true,
                value: this.warningText,
                style: "font-style:italic;width:100%;padding:5px;text-align:center;"
            }));
        };

        this.chart.add(charts);
        this.chart.doLayout();

        // unmask the panel if needed
        if (this.getEl().isMasked())
            this.getEl().unmask();
    },

    generateSeries: function(series, seriesList, aggregate){
        var subjectData = {};
        var type = "";
        if(aggregate){
            subjectData = this.aggregateChartSubjectData;
            type = "groups";
        } else {
            subjectData = this.individualChartSubjectData;
            type = "values";
        }
        for(var j = 0; j < this.chartInfo.subject[type].length; j++)
        {
            for(var i = 0; i < seriesList.length; i++)
            {
                var yAxisSeries = seriesList[i].name;
                var yAxisSide = seriesList[i].yAxisSide;
                if(aggregate){
                    var subject = this.chartInfo.subject.groups[j].label;
                } else {
                    var subject = this.chartInfo.subject.values[j];
                }
                var caption = subject;
                if(aggregate){
                    //Convert intervals from seq num to display order:
                    if ( (this.editorXAxisPanel.getTime() == "visit" && this.displayOrder && subjectData[subject]) && !this.aggregateIntervalsConverted){
                        for(var k = 0; k < subjectData[subject][seriesList[i].name].length; k++){
                            subjectData[subject][seriesList[i].name][k].interval = this.displayOrder[subjectData[subject][seriesList[i].name][k].interval.value];
                        }
                    }
                    //Set caption
                    if(seriesList.length > 1){
                        caption += " " + yAxisSeries;
                    }
                } else {
                    if(seriesList.length > 1 || this.chartInfo.chartLayout != "single"){
                        caption += " " + yAxisSeries;
                    }
                }


                var style = {lineWidth: this.chartInfo.lineWidth};
                if(this.chartInfo.hideDataPoints){
                    style.shape = {name: "square", lineWidth: 1, markSize: 20, hidden: true};
                }

                series.push({
                    subject: subject,
                    yAxisSeries: yAxisSeries,
                    caption: caption,
                    data: subjectData[subject] ? subjectData[subject][yAxisSeries] : [],
                    axis: yAxisSide,
                    xProperty:"interval",
                    yProperty: "dataValue",
                    style: style
                });
            }
        }

        if(aggregate && !this.aggregateIntervalsConverted){
            this.aggregateIntervalsConverted = true;
        }

        return series;
    },

    newLineChart: function(size, series, seriesFilter, title)
    {
        // hold on to the x and y axis index
        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");

    	// if seriesFilter is not null, then create a sub-array for that filter
    	var tempSeries = [];
    	if(seriesFilter) {
            for(var i = 0; i < series.length; i++) {
                if (Ext.isArray(seriesFilter.value) && seriesFilter.value.indexOf(series[i][seriesFilter.parameter]) > -1)
                {
                    tempSeries.push(series[i]);
                }
                else if (series[i][seriesFilter.parameter] == seriesFilter.value)
                {
                    series[i].caption = series[i].caption.replace(seriesFilter.value, "");
                    tempSeries.push(series[i]);
                } else if(series[i][seriesFilter.parameter] == title){
                    tempSeries.push(series[i]);
                }
            }
    	}

    	// set the title for this chart based on the Chart Title entered by the user and the ptid/dimension layout option
    	var mainTitle = this.chartInfo.title + (this.chartInfo.title != "" && title != null ? ": " : "") + (title ? title : "");
        var lineChartConfig = {
            width: size.width,
            height: size.height - 25,
            axes: {
                x: {
                    min: (typeof this.chartInfo.axis[xAxisIndex].range.min == "number"
                                ? this.chartInfo.axis[xAxisIndex].range.min
                                : this.autoAxisRange.x.min),
                    max: (typeof this.chartInfo.axis[xAxisIndex].range.max == "number"
                                ? this.chartInfo.axis[xAxisIndex].range.max
                                : this.autoAxisRange.x.max),
                    caption: this.chartInfo.axis[xAxisIndex].label
                }
            },
            series: tempSeries.length > 0 ? tempSeries : series,
            title: mainTitle
        };

        if (leftAxisIndex > -1)
        {
            lineChartConfig.axes.left = {
                min: (typeof this.chartInfo.axis[leftAxisIndex].range.min == "number"
                        ? this.chartInfo.axis[leftAxisIndex].range.min
                        : this.autoAxisRange.left.min),
                max: (typeof this.chartInfo.axis[leftAxisIndex].range.max == "number"
                        ? this.chartInfo.axis[leftAxisIndex].range.max
                        : this.autoAxisRange.left.max),
                caption: this.chartInfo.axis[leftAxisIndex].label,
                scale: this.chartInfo.axis[leftAxisIndex].scale
            };
        }

        if (rightAxisIndex  > -1)
        {
            lineChartConfig.axes.right = {
                min: (typeof this.chartInfo.axis[rightAxisIndex].range.min == "number"
                        ? this.chartInfo.axis[rightAxisIndex].range.min
                        : this.autoAxisRange.right.min),
                max: (typeof this.chartInfo.axis[rightAxisIndex].range.max == "number"
                        ? this.chartInfo.axis[rightAxisIndex].range.max
                        : this.autoAxisRange.right.max),
                caption: this.chartInfo.axis[rightAxisIndex].label,
                scale: this.chartInfo.axis[rightAxisIndex].scale
            };
        }

        if(this.displayLabels){
            lineChartConfig.labels = this.displayLabels;
            lineChartConfig.longLabels = this.longLabels;
        }

        var chartComponent = new LABKEY.vis.LineChart(lineChartConfig);

        // if the chart component is exportable, either add a listener to the exportPdfSingleBtn or add an item to the exportPdfMenuBtn
        if (chartComponent.canExport()) {
            if(this.chartInfo.chartLayout == "single"){
                // for a single chart, just add a listener to the button
                this.exportPdfSingleBtn.addListener('click', function(){
                    chartComponent.exportImage("pdf");
                }, this);

                this.toggleExportPdfBtns(true);
            }
            else{
                // add an item to the export pdf menu
                this.exportPdfMenu.add({
                    text: mainTitle,
                    handler: function() {chartComponent.exportImage("pdf");},
                    scope: this
                });

                this.toggleExportPdfBtns(false);
            }
        }

        if (!this.firstChartComponent)
            this.firstChartComponent = chartComponent;
        
        return new Ext.Panel({items: chartComponent});
    },

    getDataSortArray: function(){
        var arr = [this.chartInfo.subject]
        Ext.each(this.chartInfo.measures, function(md){
            arr.push({
                schemaName: md.dateOptions? md.dateOptions.dateCol.schemaName : md.measure.schemaName,
                queryName: md.dateOptions ? md.dateOptions.dateCol.queryName : md.measure.queryName,
                name: this.editorXAxisPanel.getTime() == "date" ? md.dateOptions.dateCol.name : this.viewInfo.subjectNounSingular + "Visit/sequencenum"
            });
        }, this);
        return arr;
    },

    getSeriesList: function(){
        var arr = [];
        for (var i = 0; i < this.chartInfo.measures.length; i++)
        {
            md = this.chartInfo.measures[i];

            if(md.dimension && md.dimension.values) {
                Ext.each(md.dimension.values, function(val) {
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
            this.getEl().mask("loading...");
            this.clearChartPanel();
            this.loader = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

            // hide the viewGrid button and show the viewCharts button
            this.viewChartBtn.disable();
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
                sort: this.tempGridInfo.subjectCol + ', ' + this.tempGridInfo.sortCols.join(", "),
                allowChooseQuery: false,
                allowChooseView: false,
                title: "",
                frame: "none"
            });

            // re-enable the View Charts button once the QWP has rendered
            chartQueryWebPart.on('render', function(){
                this.viewChartBtn.enable();
                
                // unmask the panel if needed
                if (this.getEl().isMasked())
                    this.getEl().unmask();
            }, this);

            this.chart.removeAll();
            this.chart.doLayout();
            this.chart.add(dataGridPanel);
            this.chart.doLayout();
        }
    },

    getInitializedChartInfo: function(){
        return {
            measures: [],
            axis: [],
            chartLayout: 'single',
            lineWidth: 4,
            hideDataPoints: false,
            subject: {},
            title: '',
            filterUrl: LABKEY.Visualization.getDataFilterFromURL(),
            filterQuery: this.getFilterQuery()
        }
    },

    getChartInfoFromEditorTabs: function(){
        var config = {
            title: this.editorChartsPanel.getMainTitle(),
            chartLayout: this.editorChartsPanel.getChartLayout(),
            chartSubjectSelection: this.editorChartsPanel.getChartSubjectSelection(),
            lineWidth: this.editorChartsPanel.getLineWidth(),
            hideDataPoints: this.editorChartsPanel.getHideDataPoints(),
            displayIndividual: this.displayIndividualCheckbox.getValue(),
            displayAggregate: this.displayAggregateCheckbox.getValue(),
            measures: [],
            axis: [this.editorXAxisPanel.getAxis()],
            filterUrl: this.editorMeasurePanel.getDataFilterUrl(),
            filterQuery: this.editorMeasurePanel.getDataFilterQuery(),
            saveThumbnail: this.editorOverviewPanel.getSaveThumbnail()
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

    saveChart: function(saveBtnName, replace, reportName, reportDescription, reportShared, canSaveSharedCharts, createdBy) {
        // if queryName and schemaName are set on the URL then save them with the chart info
        var schema = LABKEY.ActionURL.getParameter("schemaName") || null;
        var query = LABKEY.ActionURL.getParameter("queryName") || null;

        var reportSvg = (this.firstChartComponent && this.firstChartComponent.canExport() ? LABKEY.vis.SVGConverter.svgToStr(this.firstChartComponent.rootVisPanel.scene.$g) : null);

        // if the Save button was clicked, save the report using the name and description provided
        if(saveBtnName == 'Save'){
            var config = {
                replace: replace,
                reportName: reportName,
                reportDescription: reportDescription,
                reportShared: reportShared,
                reportSaveThumbnail: this.chartInfo.saveThumbnail,
                reportSvg: this.chartInfo.saveThumbnail ? reportSvg : null,
                createdBy: createdBy,
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
                    id: 'reportNameSaveAs',
                    value: reportName || null,
                    width: 300,
                    allowBlank: false,
                    maxLength: 200
                },
                {
                    xtype: 'textarea',
                    fieldLabel: 'Report Description',
                    name: 'reportDescription',
                    id: 'reportDescriptionSaveAs',
                    value: reportDescription || null,
                    width: 300,
                    height: 70,
                    allowBlank: true
                },
                new Ext.form.RadioGroup({
                    name: 'reportShared',
                    id: 'reportSharedSaveAs',
                    fieldLabel: 'Viewable by',
                    anchor: '100%',
                    items : [
                            { name: 'reportShared', id: 'reportSharedAllSaveAs', boxLabel: 'All readers', inputValue: 'true', checked: reportShared, disabled: !canSaveSharedCharts },
                            { name: 'reportShared', id: 'reportSharedMeSaveAs', boxLabel: 'Only me', inputValue: 'false', checked: !reportShared, disabled: !canSaveSharedCharts }
                        ]
                }),
                new Ext.form.Checkbox({
                    name: 'reportSaveThumbnail',
                    id: 'reportSaveThumbnailSaveAs',
                    fieldLabel: 'Save Thumbnail',
                    anchor: '100%',
                    checked: this.chartInfo.saveThumbnail,
                    value: this.chartInfo.saveThumbnail,
                    hidden: (Ext.isIE6 || Ext.isIE7 || Ext.isIE8)
                })],
                buttons: [{
                    text: 'Save',
                    formBind: true,
                    handler: function(btn, evnt){
                        var formValues = vizSaveForm.getForm().getValues();
                        var shared = typeof formValues.reportShared == "string" ? 'true' == formValues.reportShared : new Boolean(formValues.reportShared).valueOf();
                        this.chartInfo.saveThumbnail = formValues.reportSaveThumbnail == "on";

                        // call fnctn to check if a report of that name already exists
                        this.checkSaveChart({
                            replace: replace,
                            reportName: formValues.reportName,
                            reportDescription: formValues.reportDescription,
                            reportShared: shared,
                            reportSaveThumbnail: this.chartInfo.saveThumbnail, 
                            reportSvg: this.chartInfo.saveThumbnail ? reportSvg : null,
                            query: query,
                            schema: schema
                        });

                        win.close();
                    },
                    scope: this
                },
                {
                    text: 'Cancel',
                    handler: function(){
                        win.close();
                    }
                }]
            });

            // pop-up window for user to enter viz name and description for saving
            var win = new Ext.Window({
                layout:'fit',
                width:475,
                height:255,
                closeAction:'close',
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
                if(this.editorOverviewPanel.canSaveChanges()){
                    Ext.Msg.show({
                        title:'Warning',
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists. Would you like to replace it?',
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
                        msg: 'A report by the name \'' + $h(config.reportName) + '\' already exists.  Please choose a different name.',
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
            saveThumbnail: config.reportSaveThumbnail,
            svg: config.reportSvg,
            replace: config.replace,
            type: LABKEY.Visualization.Type.TimeChart,
            success: this.saveChartSuccess(config.replace,
                                           config.reportName,
                                           config.reportDescription,
                                           config.reportShared,
                                           config.reportShared ? undefined : LABKEY.Security.currentUser.id,
                                           config.createdBy),
            schemaName: config.schema,
            queryName: config.query,
            scope: this
        });
    },

    saveChartSuccess: function (replace, reportName, reportDescription, reportShared, ownerId, createdBy){
        return function(result, request, options) {
            this.markDirty(false);
            Ext.Msg.alert("Success", "The chart has been successfully saved.");

            // if a new chart was created (no replacing), we need to refresh the page with the correct report name on the URL
            if (!replace)
            {
                window.location = LABKEY.ActionURL.buildURL("visualization", "timeChartWizard", LABKEY.ActionURL.getContainer(), {edit: true, name: reportName}); 
            }
            else
            {
                this.editorOverviewPanel.updateOverview({
                    name: reportName,
                    description: reportDescription,
                    shared: reportShared,
                    ownerId: ownerId,
                    createdBy: createdBy
                }, this.chartInfo.saveThumbnail);
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
            this.chart.add(new Ext.Panel({
                padding: 10,
                html : "<table width='100%'><tr><td align='center' style='font-style:italic'>" + message + "</td></tr></table>"
            }));
            if (this.getEl().isMasked)
                this.getEl().unmask();
        }
        this.chart.doLayout();
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
