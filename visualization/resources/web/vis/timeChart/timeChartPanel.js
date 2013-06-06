/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresCss("_images/icons.css");

LABKEY.requiresScript("study/ParticipantFilterPanel.js");
LABKEY.requiresScript("study/MeasurePicker.js");

Ext4.define('LABKEY.vis.TimeChartPanel', {

    extend : 'Ext.panel.Panel',

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
                    this.toggleOptionButtons(true);
                    this.chart.add(Ext4.create('LABKEY.vis.InitialMeasurePanel', {
                        subjectNounSingular: this.viewInfo.subjectNounSingular,
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
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectColumn: this.viewInfo.subjectColumn,
            collapsed: this.chartInfo.chartSubjectSelection != "subjects",
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged,
                'measureMetadataRequestPending': this.measureMetadataRequestPending,
                'measureMetadataRequestComplete': this.measureMetadataRequestComplete
            }
        });

        this.groupsSelector = Ext4.create('LABKEY.vis.GroupSelector', {
            subject: (this.chartInfo.chartSubjectSelection == "groups" ? this.chartInfo.subject : {}),
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectColumn: this.viewInfo.subjectColumn,
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
            origMeasures: this.chartInfo.measures,
            filterUrl: this.chartInfo.filterUrl ? this.chartInfo.filterUrl : LABKEY.Query.Visualization.getDataFilterFromURL(),
            filterQuery: this.chartInfo.filterQuery ? this.chartInfo.filterQuery : this.getFilterQuery(),
            viewInfo: this.viewInfo,
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
            multipleCharts: this.chartInfo.chartLayout != "single",
            axis: this.chartInfo.axis[xAxisIndex] ? this.chartInfo.axis[xAxisIndex] : {},
            zeroDateCol: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.zeroDateCol : {},
            interval: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].dateOptions ? this.chartInfo.measures[0].dateOptions.interval : "Days",
            time: this.chartInfo.measures.length > 0 && this.chartInfo.measures[0].time ? this.chartInfo.measures[0].time : 'date',
            timepointType: this.viewInfo.TimepointType,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
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
            chartLayout: this.chartInfo.chartLayout,
            chartSubjectSelection: this.chartInfo.chartSubjectSelection,
            subjectNounSingular: this.viewInfo.subjectNounSingular,
            subjectNounPlural: this.viewInfo.subjectNounPlural,
            displayIndividual: this.chartInfo.displayIndividual != undefined ? this.chartInfo.displayIndividual : true,
            displayAggregate: this.chartInfo.displayAggregate != undefined ? this.chartInfo.displayAggregate : false,
            errorBars: this.chartInfo.errorBars != undefined ? this.chartInfo.errorBars : "None",
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': function(requiresDataRefresh){
                    if(this.editorGroupingPanel.groupLayoutChanged == true){
                        this.editorGroupingPanel.groupLayoutChanged = false;
                    }
                    this.chartDefinitionChanged(requiresDataRefresh);
                },
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
            lineWidth: this.chartInfo.lineWidth,
            hideDataPoints: this.chartInfo.hideDataPoints,
            bubbleEvents: ['closeOptionsWindow'],
            listeners: {
                scope: this,
                'chartDefinitionChanged': this.chartDefinitionChanged
            }
        });

        this.editorMainTitlePanel = Ext4.create('LABKEY.vis.MainTitleOptionsPanel', {
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
            this.renderLineChart();
        }, scope: this, hidden: true});
        this.refreshChart = new Ext4.util.DelayedTask(function(){
            this.getChartData();
        }, this);

        // boolean to check if we should allow things like export to PDF and the developer panel 
        this.supportedBrowser = !(Ext4.isIE6 || Ext4.isIE7 || Ext4.isIE8); // issue 15372

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
        this.exportPdfSingleBtn = Ext4.create('Ext.button.Button', {
            text: 'Export PDF',
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
            this.exportPdfSingleBtn.setTooltip("Export to PDF not supported for IE6, IE7, or IE8.");
            this.exportPdfMenuBtn.setTooltip("Export to PDF not supported for IE6, IE7, or IE8.");
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

        // if edit mode, then add the editor panel buttons and save buttons
        this.editMode = (LABKEY.ActionURL.getParameter("edit") == "true" || !this.editorSavePanel.isSavedReport()) && this.allowEditMode;
        var toolbarButtons = [
            this.viewGridBtn,
            this.viewChartBtn,
            this.exportPdfSingleBtn,
            this.exportPdfMenuBtn
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
        else if (this.editModeURL != null)
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
            tbar: toolbarButtons,
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
        if((this.individualData || this.aggregateData) && this.loaderName == 'renderLineChart') {
            this.loaderFn();
        }
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
        this.initialPanelValues = panel.getPanelOptionValues();

        // reset the before close event handler for the given panel
        this.optionWindow.un('beforeclose', this.restorePanelValues, this);
        this.optionWindow.on('beforeclose', this.restorePanelValues, this);

        this.optionWindow.setWidth(width);
        if (positionLeft && positionTop)
            this.optionWindow.setPosition(positionLeft, positionTop, false);

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
            if (!this.exportPdfSingleBtn.isHidden())
                this.buttonsToShow.push(this.exportPdfSingleBtn.hide());
            if (!this.exportPdfMenuBtn.isHidden())
                this.buttonsToShow.push(this.exportPdfMenuBtn.hide());
        }
    },

    disableNonMeasureOptionButtons: function(){
        this.groupingButton.disable();
        this.aestheticsButton.disable();
        this.developerButton.disable();

        this.disablePdfExportButtons();

        this.viewGridBtn.disable();
        this.viewChartBtn.disable();
    },

    disablePdfExportButtons: function() {
        this.exportPdfMenuBtn.disable();
        this.exportPdfSingleBtn.disable();
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

        // Clear previous chart data.
        this.individualData = undefined;
        this.individualHasData = undefined;
        this.aggregateData = undefined;
        this.aggregateHasData = undefined;

        this.hasIntervalData = this.editorXAxisPanel.getTime() != "date"; // only check interval data for date based chart

        // get the updated chart information from the various options panels
        this.chartInfo = this.getChartInfoFromOptionPanels();
        this.numberFormats = {};

        this.loaderCount = 0; //Used to prevent the loader from running until we have recieved all necessary callbacks.
        if (this.chartInfo.displayIndividual)
            this.loaderCount++;
        if (this.chartInfo.displayAggregate)
            this.loaderCount++;

        // fail quickly if some of the key components are not selected for the chart info (ptids, groups, series)
        if (this.warnSelectionsMissing())
            return;

        if (this.chartInfo.displayIndividual)
        {
            //Get data for individual lines.
            LABKEY.Query.Visualization.getData({
                success: function(data){
                    // set the dirty state for non-saved time chart once data is requested
                    if (!this.editorSavePanel.isSavedReport())
                        this.markDirty(true);

                    // store the data in an object by subject for use later when it comes time to render the line chart
                    this.individualData = data;
                    var gridSortCols = [];

                    // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
                    var visitsInData = [];
                    var seriesList = this.getSeriesList();
                    this.individualHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.individualHasData[s.name] = false;
                        for (var i = 0; i < this.individualData.rows.length; i++)
                        {
                            var row = this.individualData.rows[i];
                            var alias = this.getColumnAlias(this.individualData.columnAliases, s.aliasLookupInfo);
                            if (row[alias] && row[alias].value != null)
                                this.individualHasData[s.name] = true;

                            var visitMappedName = this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                            if (this.editorXAxisPanel.getTime() == "visit" && row[visitMappedName])
                            {
                                var visitVal = row[visitMappedName].value;
                                if (visitsInData.indexOf(visitVal) == -1)
                                    visitsInData.push(visitVal.toString());
                            }

                            this.checkForIntervalValues(row);
                        }
                    }, this);

                    this.getNumberFormats(this.individualData.metaData.fields);

                    // trim the visit map domain to just those visits in the response data
                    this.individualData.visitMap = this.trimVisitMapDomain(this.individualData.visitMap, visitsInData);

                    // store the temp schema name, query name, etc. for the data grid
                    this.tempGridInfo = {schema: this.individualData.schemaName, query: data.queryName,
                        subjectCol: this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn),
                        sortCols: this.editorXAxisPanel.getTime() == "date" ? gridSortCols : [this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit/DisplayOrder")]
                    };

                    // now that we have the temp grid info, enable the View Data button
                    this.viewGridBtn.enable();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                sorts: this.getDataSortArray(),
                limit : this.dataLimit,
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }

        if (this.chartInfo.displayAggregate)
        {
            //Get data for Aggregates.
            var groups = [];
            for (var i = 0; i < this.chartInfo.subject.groups.length; i++){
                var group = this.chartInfo.subject.groups[i];

                // encode the group id & type, so we can distinguish between cohort and participant
                // group in the union table
                groups.push(group.id + '-' + group.type);
            }

            LABKEY.Query.Visualization.getData({
                success: function(data){
                    this.aggregateData = data;

                    // make sure each measure/dimension has at least some data, and get a list of which visits are in the data response
                    var visitsInData = [];
                    var seriesList = this.getSeriesList();
                    this.aggregateHasData = {};
                    Ext4.each(seriesList, function(s) {
                        this.aggregateHasData[s.name] = false;
                        for (var i = 0; i < this.aggregateData.rows.length; i++)
                        {
                            var row = this.aggregateData.rows[i];
                            var alias = this.getColumnAlias(this.aggregateData.columnAliases, s.aliasLookupInfo);
                            if (row[alias] && row[alias].value != null)
                                this.aggregateHasData[s.name] = true;

                            var visitMappedName = this.getColumnAlias(this.aggregateData.columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                            if (this.editorXAxisPanel.getTime() == "visit" && row[visitMappedName])
                            {
                                var visitVal = row[visitMappedName].value;
                                if (visitsInData.indexOf(visitVal) == -1)
                                    visitsInData.push(visitVal.toString());
                            }

                            this.checkForIntervalValues(row);
                        }
                    }, this);

                    this.getNumberFormats(this.aggregateData.metaData.fields);

                    // trim the visit map domain to just those visits in the response data
                    this.aggregateData.visitMap = this.trimVisitMapDomain(this.aggregateData.visitMap, visitsInData);

                    // store the temp schema name, query name, etc. for the data grid
                    this.tempGridInfo = {schema: this.aggregateData.schemaName, query: data.queryName};

                    // now that we have the temp grid info, enable the View Data button
                    this.viewGridBtn.enable();

                    // ready to render the chart or grid
                    this.loaderCount--;
                    if(this.loaderCount == 0){
                        this.loaderFn();
                    }
                },
                failure : function(info, response, options) {
                    this.clearChartPanel("Error: " + info.exception);
                },
                measures: this.chartInfo.measures,
                viewInfo: this.viewInfo,
                groupBys: [{schemaName: 'study', queryName: 'ParticipantGroupCohortUnion', name: 'UniqueId', values: groups}],
                sorts: this.getDataSortArray(),
                limit : this.dataLimit,
                filterUrl: this.chartInfo.filterUrl,
                filterQuery: this.chartInfo.filterQuery,
                scope: this
            });
        }
    },

    getNumberFormats: function(fields) {
        for(var i = 0; i < this.chartInfo.axis.length; i++){
            var axis = this.chartInfo.axis[i];
            if(axis.side){
                // Find the first measure with the matching side that has a numberFormat.
                for(var j = 0; j < this.chartInfo.measures.length; j++){
                    var measure = this.chartInfo.measures[j].measure;

                    if(this.numberFormats[axis.side]){
                        break;
                    }

                    if(measure.yAxis == axis.side){
                        var metaDataName = measure.alias;
                        for(var k = 0; k < fields.length; k++){
                            var field = fields[k];
                            if(field.name == metaDataName){
                                if(field.extFormatFn){
                                    this.numberFormats[axis.side] = eval(field.extFormatFn);
                                    break;
                                }
                            }
                        }
                    }
                }

                if(!this.numberFormats[axis.side]){
                    // If after all the searching we still don't have a numberformat use the default number format.
                    this.numberFormats[axis.side] = this.defaultNumberFormat;
                }
            }
        }
    },

    trimVisitMapDomain: function(origVisitMap, visitsInDataArr) {
        // get the visit map info for those visits in the response data
        var trimmedVisits = [];
        for (var v in origVisitMap)
        {
            if (visitsInDataArr.indexOf(v) != -1)
                trimmedVisits.push(Ext4.apply({id: v}, origVisitMap[v]));
        }
        // sort the trimmed visit list by displayOrder and then reset displayOrder starting at 1
        trimmedVisits.sort(function(a,b){return a.displayOrder - b.displayOrder});
        var newVisitMap = {};
        for (var i = 0; i < trimmedVisits.length; i++)
        {
            trimmedVisits[i].displayOrder = i + 1;
            newVisitMap[trimmedVisits[i].id] = trimmedVisits[i];
        }

        return newVisitMap;
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
                simplified.subject.groups.push({label: config.subject.groups[i].label, participantIds: config.subject.groups[i].participantIds});
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

    renderLineChart: function(force)
    {
        this.maskAndRemoveCharts();

        // add a delay to make sure the loading mask shows before next charts start to render
        new Ext4.util.DelayedTask(function(){
            this._renderLineChart(force);
        }, this).delay(100);
    },

    _renderLineChart: function(force)
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

        var xAxisIndex = this.getAxisIndex(this.chartInfo.axis, "x-axis");
        var leftAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "left");
        var rightAxisIndex = this.getAxisIndex(this.chartInfo.axis, "y-axis", "right");
        if(xAxisIndex == -1){
           Ext4.Msg.alert("Error", "Could not find x-axis in chart measure information.");
           return;
        }

        if (this.individualData && this.individualData.filterDescription)
            this.editorMeasurePanel.setFilterWarningText(this.individualData.filterDescription);

        if (this.chartInfo.measures.length == 0)
        {
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
           return;
        }

        this.loaderFn = this.renderLineChart;
        this.loaderName = 'renderLineChart';

        // warn the user if the data limit has been reached
        if ((this.individualData && this.individualData.rows.length == this.dataLimit) || (this.aggregateData && this.aggregateData.rows.length == this.dataLimit))
        {
            this.addWarningText("The data limit for plotting has been reached. Consider filtering your data.");
        }

	    // one series per y-axis subject/measure/dimensionvalue combination
	    var seriesList = this.getSeriesList();

        // check to see if any of the measures don't have data, and display a message accordingly
        if (force !== true) {
            var msg = ""; var sep = "";
            var noDataCounter = 0;
            Ext4.iterate(this.aggregateHasData ? this.aggregateHasData : this.individualHasData, function(key, value, obj){
                if (!value)
                {
                    noDataCounter++;
                    msg += sep + key;
                    sep = ", ";
                }
            }, this);
            if (msg.length > 0)
            {
                msg = "No data found for the following measures/dimensions: " + msg;

                // if there is no data for any series, error out completely
                if (noDataCounter == seriesList.length)
                {
                    this.clearChartPanel(msg);
                    this.disablePdfExportButtons();
                    this.toggleSaveButtons(true);
                    return;
                }
                else
                    this.addWarningText(msg);
            }
        }

        // for date based charts, give error message if there are no calculated interval values
        if (!this.hasIntervalData)
            this.addWarningText("No calculated interval values (i.e. Days, Months, etc.) for the selected 'Measure Date' and 'Interval Start Date'.");

        // issue 17132: only apply clipRect if the user set a custom axis range
        var applyClipRect = (
            xAxisIndex > -1 && (this.chartInfo.axis[xAxisIndex].range.min != null || this.chartInfo.axis[xAxisIndex].range.max != null) ||
            leftAxisIndex > -1 && (this.chartInfo.axis[leftAxisIndex].range.min != null || this.chartInfo.axis[leftAxisIndex].range.max != null) ||
            rightAxisIndex > -1 && (this.chartInfo.axis[rightAxisIndex].range.min != null || this.chartInfo.axis[rightAxisIndex].range.max != null)
        );

        // Use the same max/min for every chart if displaying more than one chart.
        if (this.chartInfo.chartLayout != "single")
        {
            //ISSUE In multi-chart case, we need to precompute the default axis ranges so that all charts share them.
            var leftMeasures = [];
            var rightMeasures = [];
            var xName, xFunc;
            var min, max, tempMin, tempMax, errorBarType;
            var leftAccessor, leftAccessorMax, leftAccessorMin, rightAccessorMax, rightAccessorMin, rightAccessor;
            var columnAliases = this.individualData ? this.individualData.columnAliases : this.aggregateData.columnAliases;

            for(var i = 0; i < seriesList.length; i++){
                var columnName = this.getColumnAlias(columnAliases, seriesList[i].aliasLookupInfo);
                if(seriesList[i].yAxisSide == "left"){
                    leftMeasures.push(columnName);
                } else if(seriesList[i].yAxisSide == "right"){
                    rightMeasures.push(columnName);
                }
            }

            if(this.editorXAxisPanel.getTime() == "date"){
                xName = this.chartInfo.measures[0].dateOptions.interval;
                xFunc = function(row){
                    return row[xName].value;
                };
            } else {
                var visitMap = this.individualData ? this.individualData.visitMap : this.aggregateData.visitMap;
                xName = this.getColumnAlias(columnAliases, this.viewInfo.subjectNounSingular + "Visit/Visit");
                xFunc = function(row){
                    return visitMap[row[xName].value].displayOrder;
                };
            }

            if (!this.chartInfo.axis[xAxisIndex].range.min && this.chartInfo.axis[xAxisIndex].range.type != 'automatic_per_chart')
            {
                this.chartInfo.axis[xAxisIndex].range.min = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows, xFunc);
            }
            if (!this.chartInfo.axis[xAxisIndex].range.max && this.chartInfo.axis[xAxisIndex].range.type != 'automatic_per_chart')
            {
                this.chartInfo.axis[xAxisIndex].range.max = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows, xFunc);
            }

            if(this.chartInfo.errorBars !== 'None'){
                errorBarType = this.chartInfo.errorBars == 'SD' ? '_STDDEV' : '_STDERR';
            }

            if (leftAxisIndex > -1) {
                // If we have a left axis then we need to find the min/max
                min = null, max = null, tempMin = null, tempMax = null;
                leftAccessor = function(row){return (row[leftMeasures[i]] ? row[leftMeasures[i]].value : null);};

                if(errorBarType){
                     // If we have error bars we need to calculate min/max with the error values in mind.
                    leftAccessorMin = function(row){
                        if (row[leftMeasures[i] + errorBarType])
                        {
                            var error = row[leftMeasures[i] + errorBarType].value;
                            return row[leftMeasures[i]].value - error;
                        }
                        else
                            return null;
                    };

                    leftAccessorMax = function(row){
                        if (row[leftMeasures[i] + errorBarType])
                        {
                            var error = row[leftMeasures[i] + errorBarType].value;
                            return row[leftMeasures[i]].value + error;
                        }
                        else
                            return null;
                    };
                }

                if(!this.chartInfo.axis[leftAxisIndex].range.min && this.chartInfo.axis[leftAxisIndex].range.type != 'automatic_per_chart'){
                    for(var i = 0; i < leftMeasures.length; i++){
                        tempMin = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows,
                                leftAccessorMin ? leftAccessorMin : leftAccessor);
                        min = min == null ? tempMin : tempMin < min ? tempMin : min;
                    }
                    this.chartInfo.axis[leftAxisIndex].range.min = min;
                }

                if(!this.chartInfo.axis[leftAxisIndex].range.max && this.chartInfo.axis[leftAxisIndex].range.type != 'automatic_per_chart'){
                    for(var i = 0; i < leftMeasures.length; i++){
                        tempMax = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows,
                                leftAccessorMax ? leftAccessorMax : leftAccessor);
                        max = max == null ? tempMax : tempMax > max ? tempMax : max;
                    }
                    this.chartInfo.axis[leftAxisIndex].range.max = max;
                }
            }

            if (rightAxisIndex > -1) {
                // If we have a right axis then we need to find the min/max
                min = null, max = null, tempMin = null, tempMax = null;
                rightAccessor = function(row){
                    return row[rightMeasures[i]].value
                };

                if(errorBarType){
                    rightAccessorMin = function(row){
                        var error = row[rightMeasures[i] + errorBarType].value;
                        return row[rightMeasures[i]].value - error;
                    };

                    rightAccessorMax = function(row){
                        var error = row[rightMeasures[i] + errorBarType].value;
                        return row[rightMeasures[i]].value + error;
                    };
                }

                if(!this.chartInfo.axis[rightAxisIndex].range.min && this.chartInfo.axis[rightAxisIndex].range.type != 'automatic_per_chart'){
                    for(var i = 0; i < rightMeasures.length; i++){
                        tempMin = d3.min(this.individualData ? this.individualData.rows : this.aggregateData.rows,
                                rightAccessorMin ? rightAccessorMin : rightAccessor);
                        min = min == null ? tempMin : tempMin < min ? tempMin : min;
                    }
                    this.chartInfo.axis[rightAxisIndex].range.min = min;
                }
                if(!this.chartInfo.axis[rightAxisIndex].range.max && this.chartInfo.axis[rightAxisIndex].range.type != 'automatic_per_chart'){
                    for(var i = 0; i < rightMeasures.length; i++){
                        tempMax = d3.max(this.individualData ? this.individualData.rows : this.aggregateData.rows,
                                rightAccessorMax ? rightAccessorMax : rightAccessor);
                        max = max == null ? tempMax : tempMax > max ? tempMax : max;
                    }
                    this.chartInfo.axis[rightAxisIndex].range.max = max;
                }
            }
        }

        // remove any existing charts, purge listeners from exportPdfSingleBtn, and remove items from the exportPdfMenu button
        this.chart.removeAll();
        this.chart.removeListener('resize', this.resizeCharts);
        this.plotConfigInfoArr = [];
        this.exportPdfSingleBtn.removeListener('click', this.exportChartToPdf);
        this.exportPdfMenu.removeAll();

        var charts = [];

        var generateGroupSeries = function(data, groups, subjectColumn){
            // subjectColumn is the aliasColumnName looked up from the getData response columnAliases array
            // groups is this.chartInfo.subject.groups
            var rows = data.rows;
            var dataByGroup = {};

            for(var i = 0; i < rows.length; i++){
                var rowSubject = rows[i][subjectColumn].value;
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

        // warn if user doesn't have an subjects, groups, series, etc. selected
        if (!this.warnSelectionsMissing())
        {
            this.toggleSaveButtons(false);

            // four options: all series on one chart, one chart per subject, one chart per group, or one chart per measure/dimension
            if (this.chartInfo.chartLayout == "per_subject")
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.values.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                var accessor = this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn);
                var dataPerParticipant = LABKEY.vis.groupDataWithSeriesCheck(this.individualData.rows, function(row){return row[accessor].value}, seriesList);
                for (var participant in dataPerParticipant)
                {
                    // skip the group if there is no data for it
                    if (!dataPerParticipant[participant].hasSeriesData)
                        continue;

                    this.plotConfigInfoArr.push({
                        title: this.concatChartTitle(this.chartInfo.title, participant),
                        series: seriesList,
                        individualData: dataPerParticipant[participant].data,
                        style: this.chartInfo.subject.values.length > 1 ? 'border-bottom: solid black 1px;' : null,
                        applyClipRect: applyClipRect
                    });

                    if(this.plotConfigInfoArr.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "per_group")
            {
                // warn if the max number of charts has been exceeded
                if (this.chartInfo.subject.groups.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts.");
                }

                //Display individual lines
                var groupedIndividualData = null;
                if (this.individualData)
                    groupedIndividualData = generateGroupSeries(this.individualData, this.chartInfo.subject.groups, this.getColumnAlias(this.individualData.columnAliases, this.viewInfo.subjectColumn));

                // Display aggregate lines
                var groupedAggregateData = null;
                if (this.aggregateData)
                    groupedAggregateData = LABKEY.vis.groupDataWithSeriesCheck(this.aggregateData.rows, function(row){return row.UniqueId.displayValue}, seriesList);

                for (var i = 0; i < (this.chartInfo.subject.groups.length > this.maxCharts ? this.maxCharts : this.chartInfo.subject.groups.length); i++)
                {
                    var group = this.chartInfo.subject.groups[i];
                    // skip the group if there is no data for it
                    if ((groupedIndividualData != null && !groupedIndividualData[group.label])
                        || (groupedAggregateData != null && (!groupedAggregateData[group.label] || !groupedAggregateData[group.label].hasSeriesData)))
                        continue;

                    this.plotConfigInfoArr.push({
                        title: this.concatChartTitle(this.chartInfo.title, group.label),
                        series: seriesList,
                        individualData: groupedIndividualData && groupedIndividualData[group.label] ? groupedIndividualData[group.label] : null,
                        aggregateData: groupedAggregateData && groupedAggregateData[group.label] ? groupedAggregateData[group.label].data : null,
                        style: this.chartInfo.subject.groups.length > 1 ? 'border-bottom: solid black 1px;' : null,
                        applyClipRect: applyClipRect
                    });

                    if(this.plotConfigInfoArr.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "per_dimension")
            {
                // warn if the max number of charts has been exceeded
                if (seriesList.length > this.maxCharts)
                {
                    this.addWarningText("Only showing the first " + this.maxCharts + " charts");
                }
                for (var i = 0; i < (seriesList.length > this.maxCharts ? this.maxCharts : seriesList.length); i++)
                {
                    this.plotConfigInfoArr.push({
                        title: this.concatChartTitle(this.chartInfo.title, seriesList[i].label),
                        series: [seriesList[i]],
                        style: seriesList.length > 1 ? 'border-bottom: solid black 1px;' : null,
                        applyClipRect: applyClipRect
                    });

                    if(this.plotConfigInfoArr.length > this.maxCharts){
                        break;
                    }
                }
            }
            else if (this.chartInfo.chartLayout == "single")
            {
                //Single Line Chart, with all participants or groups.
                this.plotConfigInfoArr.push({
                    title: this.chartInfo.title,
                    series: seriesList,
                    height: 610,
                    style: null,
                    applyClipRect: applyClipRect
                });
            }

            for (var configIndex = 0; configIndex < this.plotConfigInfoArr.length; configIndex++)
            {
                var newChart = this.generatePlot(
                        configIndex,
                        this.plotConfigInfoArr[configIndex].height || (this.plotConfigInfoArr.length > 1 ? 380 : 600),
                        this.plotConfigInfoArr[configIndex].style,
                        false // forExport param
                    );
                charts.push(newChart);

                if (this.plotConfigInfoArr.length > 1)
                {
                    this.exportPdfMenu.add({
                        text: this.plotConfigInfoArr[configIndex].title,
                        configIndex: configIndex,
                        handler: this.exportChartToPdf,
                        scope: this
                    });
                    this.toggleExportPdfBtns(false);
                }
                else
                {
                    this.exportPdfSingleBtn.configIndex = configIndex;
                    this.exportPdfSingleBtn.addListener('click', this.exportChartToPdf, this);
                    this.toggleExportPdfBtns(true);
                }
            }
        }

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

    concatChartTitle: function(mainTitle, subTitle) {
        return mainTitle + (mainTitle ? ': ' : '') + subTitle;
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

    generatePlot: function(configIndex, chartHeight, chartStyle, forExport){

        // This function generates a plot config and renders a plot for given data.
        // Should be used in per_subject, single, per_measure, and per_group

        var chart = this.chart;
        var studyType = this.editorXAxisPanel.getTime();
        var viewInfo = this.viewInfo;
        var chartInfo = this.chartInfo;
        var mainTitle = this.plotConfigInfoArr[configIndex].title;
        var seriesList = this.plotConfigInfoArr[configIndex].series;
        var individualData = this.plotConfigInfoArr[configIndex].individualData ? this.plotConfigInfoArr[configIndex].individualData : (this.individualData ? this.individualData.rows : null);
        var individualColumnAliases = this.individualData ? this.individualData.columnAliases : null;
        var individualVisitMap = this.individualData ? this.individualData.visitMap : null;
        var aggregateData = this.plotConfigInfoArr[configIndex].aggregateData ? this.plotConfigInfoArr[configIndex].aggregateData : (this.aggregateData ? this.aggregateData.rows : null);
        var aggregateColumnAliases = this.aggregateData ? this.aggregateData.columnAliases : null;
        var aggregateVisitMap = this.aggregateData ? this.aggregateData.visitMap : null;
        var applyClipRect = this.plotConfigInfoArr[configIndex].applyClipRect;

        var generateLayerAes = function(name, yAxisSide, columnName){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return (row[columnName] ? parseFloat(row[columnName].value) : null)}; // Have to parseFlot because for some reason ObsCon from Luminex was returning strings not floats/ints.
            return aes;
        };

        var generateAggregateLayerAes = function(name, yAxisSide, columnName, intervalKey, subjectColumn, errorColumn){
            var yName = yAxisSide == "left" ? "yLeft" : "yRight";
            var aes = {};
            aes[yName] = function(row){return (row[columnName] ? parseFloat(row[columnName].value) : null)}; // Have to parseFloat because for some reason ObsCon from Luminex was returning strings not floats/ints.
            aes.group = aes.color = aes.shape = function(row){return row[subjectColumn].displayValue};
            aes.error = function(row){return (row[errorColumn] ? row[errorColumn].value : null)};
            return aes;
        };

        var hoverTextFn = function(subjectColumn, intervalKey, name, columnName, visitMap, errorColumn, errorType){
            if(visitMap){
                if(errorColumn){
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    }
                } else {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n '+ visitMap[row[intervalKey].value].displayName + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            } else {
                if(errorColumn){
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        var errorVal = row[errorColumn].value ? row[errorColumn].value : 'n/a';
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value +
                                ',\n ' + errorType + ': ' + errorVal;
                    }
                } else {
                    return function(row){
                        var subject = row[subjectColumn].displayValue ? row[subjectColumn].displayValue : row[subjectColumn].value;
                        return ' ' + subject + ',\n ' + intervalKey + ': ' + row[intervalKey].value + ',\n ' + name + ': ' + row[columnName].value;
                    };
                }
            }
        };

        // create a new function from the pointClickFn string provided by the developer
        if (chartInfo.pointClickFn)
        {
            // the developer is expected to return a function, so we encapalate it within the anonymous function
            // (note: the function should have already be validated in a try/catch when applied via the developerOptionsPanel)
            var devPointClickFn = new Function("", "return " + this.editorDeveloperPanel.removeLeadingComments(chartInfo.pointClickFn));
        }

        var pointClickFn = function(columnMap, measureInfo) {
            return function(clickEvent, data) {
                // call the developers function, within the anonymous function, with the params as defined for the developer                 
                devPointClickFn().call(this, data, columnMap, measureInfo, clickEvent);
            }
        };

        var layers = [];
        var xTitle = '', yLeftTitle = '', yRightTitle = '';
        var yLeftMin = null, yLeftMax = null, yLeftTrans = null, yLeftTickFormat;
        var yRightMin = null, yRightMax = null, yRightTrans = null, yRightTickFormat;
        var xMin = null, xMax = null, xTrans = null;
        var intervalKey = null;
        var individualSubjectColumn = individualColumnAliases ? this.getColumnAlias(individualColumnAliases, viewInfo.subjectColumn) : null;
        var aggregateSubjectColumn = "UniqueId";
        var xAes, xTickFormat, tickMap = {};
        var visitMap = individualVisitMap ? individualVisitMap : aggregateVisitMap;

        for(var rowId in visitMap){
            tickMap[visitMap[rowId].displayOrder] = visitMap[rowId].displayName;
        }

        if(studyType == 'date'){
            intervalKey = chartInfo.measures[seriesList[0].measureIndex].dateOptions.interval;
            xAes = function(row){return (row[intervalKey] ? row[intervalKey].value : null)}
        } else {
            intervalKey = individualColumnAliases ?
                    this.getColumnAlias(individualColumnAliases, viewInfo.subjectNounSingular + "Visit/Visit") :
                    this.getColumnAlias(aggregateColumnAliases, viewInfo.subjectNounSingular + "Visit/Visit");
            xAes = function(row){
                return visitMap[row[intervalKey].value].displayOrder;
            };
            xTickFormat = function(value){
                return tickMap[value] ? tickMap[value] : "";
            }
        }

        var newChartDiv = Ext4.create('Ext.container.Container', {
            height: chartHeight,
            style: chartStyle ? chartStyle : 'border: none;',
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
                    yLeftTickFormat = chartInfo.numberFormats.left ? chartInfo.numberFormats.left : null;
                } else {
                    yRightTitle = axis.label;
                    yRightMin = typeof axis.range.min == "number" ? axis.range.min : null;
                    yRightMax = typeof axis.range.max == "number" ? axis.range.max : null;
                    yRightTrans = axis.scale ? axis.scale : "linear";
                    yRightTickFormat = chartInfo.numberFormats.right ? chartInfo.numberFormats.right : null;
                }
            } else {
                xTitle = axis.label;
                xMin = typeof axis.range.min == "number" ? axis.range.min : null;
                xMax = typeof axis.range.max == "number" ? axis.range.max : null;
                xTrans = axis.scale ? axis.scale : "linear";
            }
        }

        // Issue 15369: if two measures have the same name, use the alias for the subsequent series names (which will be unique)
        // Issue 12369: if rendering two measures of the same pivoted value, use measure and pivot name for series names (which will be unique)
        var useUniqueSeriesNames = false;
        var uniqueChartSeriesNames = [];
        for (var i = 0; i < seriesList.length; i++)
        {
            if (uniqueChartSeriesNames.indexOf(seriesList[i].name) > -1)
            {
                useUniqueSeriesNames = true;
                break;
            }
            uniqueChartSeriesNames.push(seriesList[i].name);
        }

        for (var i = seriesList.length -1; i >= 0; i--)
        {
            var chartSeries = seriesList[i];

            var chartSeriesName = chartSeries.label;
            if (useUniqueSeriesNames)
            {
                if (chartSeries.aliasLookupInfo.pivotValue)
                    chartSeriesName = chartSeries.aliasLookupInfo.measureName + " " + chartSeries.aliasLookupInfo.pivotValue;    
                else
                    chartSeriesName = chartSeries.aliasLookupInfo.alias;
            }

            var columnName = individualColumnAliases ? this.getColumnAlias(individualColumnAliases, chartSeries.aliasLookupInfo) : this.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo);
            if(individualData && individualColumnAliases){
                var pathLayerConfig = {
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                };

                if(seriesList.length > 1){
                    pathLayerConfig.name = chartSeriesName;
                }

                layers.push(new LABKEY.vis.Layer(pathLayerConfig));

                if(!chartInfo.hideDataPoints){
                    var pointLayerConfig = {
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName)
                    };

                    if(seriesList.length > 1){
                        pointLayerConfig.name = chartSeriesName;
                    }

                    if(studyType == 'date'){
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, null, null, null);
                    } else {
                        pointLayerConfig.aes.hoverText = hoverTextFn(individualSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, null, null);
                    }

                    if (chartInfo.pointClickFn)
                    {
                        pointLayerConfig.aes.pointClickFn = pointClickFn(
                            {participant: individualSubjectColumn, interval: intervalKey, measure: columnName},
                            {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }

                    layers.push(new LABKEY.vis.Layer(pointLayerConfig));
                }
            }

            if(aggregateData && aggregateColumnAliases){
                var errorBarType = null;
                if(chartInfo.errorBars == 'SD'){
                    errorBarType = '_STDDEV';
                } else if(chartInfo.errorBars == 'SEM'){
                    errorBarType = '_STDERR';
                }
                var errorColumnName = errorBarType ? this.getColumnAlias(aggregateColumnAliases, chartSeries.aliasLookupInfo) + errorBarType : null;

                var aggregatePathLayerConfig = {
                    data: aggregateData,
                    geom: new LABKEY.vis.Geom.Path({size: chartInfo.lineWidth}),
                    aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                };

                if(seriesList.length > 1){
                    aggregatePathLayerConfig.name = chartSeriesName;
                }

                layers.push(new LABKEY.vis.Layer(aggregatePathLayerConfig));

                if(errorColumnName){
                    var aggregateErrorLayerConfig = {
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.ErrorBar(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };

                    if(seriesList.length > 1){
                        aggregateErrorLayerConfig.name = chartSeriesName;
                    }

                    layers.push(new LABKEY.vis.Layer(aggregateErrorLayerConfig));
                }

                if(!chartInfo.hideDataPoints){
                    var aggregatePointLayerConfig = {
                        data: aggregateData,
                        geom: new LABKEY.vis.Geom.Point(),
                        aes: generateAggregateLayerAes(chartSeriesName, chartSeries.yAxisSide, columnName, intervalKey, aggregateSubjectColumn, errorColumnName)
                    };

                    if(seriesList.length > 1){
                        aggregatePointLayerConfig.name = chartSeriesName;
                    }

                    if(studyType == 'date'){
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, null, errorColumnName, chartInfo.errorBars)
                    } else {
                        aggregatePointLayerConfig.aes.hoverText = hoverTextFn(aggregateSubjectColumn, intervalKey, chartSeriesName, columnName, visitMap, errorColumnName, chartInfo.errorBars);
                    }
                    if (chartInfo.pointClickFn)
                    {
                        aggregatePointLayerConfig.aes.pointClickFn = pointClickFn(
                            {group: aggregateSubjectColumn, interval: intervalKey, measure: columnName},
                            {schemaName: chartSeries.schemaName, queryName: chartSeries.queryName, name: chartSeriesName}
                        );
                    }
                    layers.push(new LABKEY.vis.Layer(aggregatePointLayerConfig));
                }
            }
        }

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

        // issue 16602: allow blank titles, but default to "Edit XXXX" for edit mode
        if (this.editMode && !forExport)
        {
            mainTitle = mainTitle == null || Ext4.util.Format.trim(mainTitle) == "" ? "Edit Title" : mainTitle;
            xTitle = xTitle == null || Ext4.util.Format.trim(xTitle) == "" ? "Edit Axis Label" : xTitle;
            yLeftTitle = yLeftTitle == null || Ext4.util.Format.trim(yLeftTitle) == "" ? "Edit Axis Label" : yLeftTitle;
            yRightTitle = yRightTitle == null || Ext4.util.Format.trim(yRightTitle) == "" ? "Edit Axis Label" : yRightTitle;
        }

        var plotConfig = {
            renderTo: newChartDiv.getId(),
            clipRect: applyClipRect,
            labels: {
                main: {
                    value: mainTitle,
                    lookClickable: this.editMode && !forExport,
                    listeners: {
                        click: this.editMode ? mainTitleClickFn(this) : null
                    }
                },
                x: {
                    value: xTitle,
                    lookClickable: this.editMode && !forExport,
                    listeners: {
                        click: this.editMode ? xAxisLabelClickFn(this) : null
                    }
                },
                yLeft: {
                    value: yLeftTitle,
                    lookClickable: this.editMode && !forExport,
                    listeners: {
                        click: this.editMode ? yAxisLeftLabelClickFn(this) : null
                    }
                },
                yRight: {
                    value: yRightTitle,
                    lookClickable: this.editMode && !forExport,
                    listeners: {
                        click: this.editMode ? yAxisRightLabelClickFn(this) : null
                    }
                }
            },
            layers: layers,
            aes: {
                x: xAes,
                color: function(row){return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null)},
                group: function(row){return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null)},
                shape: function(row){
                    return (row[individualSubjectColumn] ? row[individualSubjectColumn].value : null);
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
                    max: yLeftMax,
                    tickFormat: yLeftTickFormat ? yLeftTickFormat : null
                },
                yRight: {
                    scaleType: 'continuous',
                    trans: yRightTrans,
                    min: yRightMin,
                    max: yRightMax,
                    tickFormat: yRightTickFormat ? yRightTickFormat : null
                },
                shape: {
                    scaleType: 'discrete'
                }
            },
            width: newChartDiv.getWidth() - 20, // -20 prevents horizontal scrollbars in cases with multiple charts.
            height: newChartDiv.getHeight() - 20, // -20 prevents vertical scrollbars in cases with one chart.
            data: individualData ? individualData : aggregateData
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
            var md = this.chartInfo.measures[i];

            if(md.dimension && md.dimension.values) {
                Ext4.each(md.dimension.values, function(val) {
                    arr.push({
                        schemaName: md.dimension.schemaName,
                        queryName: md.dimension.queryName,
                        name: val,
                        label: val,
                        measureIndex: i,
                        yAxisSide: md.measure.yAxis,
                        aliasLookupInfo: {measureName: md.measure.name, pivotValue: val}
                    });
                });
            }
            else {
                arr.push({
                    schemaName: md.measure.schemaName,
                    queryName: md.measure.queryName,
                    name: md.measure.name,
                    label: md.measure.label,
                    measureIndex: i,
                    yAxisSide: md.measure.yAxis,
                    aliasLookupInfo: md.measure.alias ? {alias: md.measure.alias} : {measureName: md.measure.name}
                });
            }
        }
        return arr;
    },

    /*
    * Lookup the column alias (from the getData response) by the specified measure information
    * aliasArray: columnAlias array from the getData API response
    * measureInfo: 1. a string with the name of the column to lookup
    *              2. an object with a measure alias OR measureName
     *             3. an object with both measureName AND pivotValue
     */
    getColumnAlias: function(aliasArray, measureInfo) {
        if (typeof measureInfo != "object")
            measureInfo = {measureName: measureInfo};
        for (var i = 0; i < aliasArray.length; i++)
        {
            var arrVal = aliasArray[i];

            if (measureInfo.measureName && measureInfo.pivotValue)
            {
                if (arrVal.measureName == measureInfo.measureName && arrVal.pivotValue == measureInfo.pivotValue)
                    return arrVal.columnName;
            }
            else if (measureInfo.alias)
            {
                if (arrVal.alias == measureInfo.alias)
                    return arrVal.columnName;
            }
            else if (measureInfo.measureName && arrVal.measureName == measureInfo.measureName)
                return arrVal.columnName;
        }
        return null;
    },

    toggleExportPdfBtns: function(showSingle) {
        if(showSingle){
            this.exportPdfSingleBtn.show();
            this.exportPdfSingleBtn.setDisabled(!this.supportedBrowser);
            this.exportPdfMenuBtn.hide();
            this.exportPdfMenuBtn.setDisabled(true);
        }
        else{
            this.exportPdfSingleBtn.hide();
            this.exportPdfSingleBtn.setDisabled(true);
            this.exportPdfMenuBtn.show();
            this.exportPdfMenuBtn.setDisabled(!this.supportedBrowser);
        }
    },

    viewDataGrid: function() {
        // make sure the tempGridInfo is available
        if(typeof this.tempGridInfo == "object") {
            this.maskAndRemoveCharts();
            this.loaderFn = this.viewDataGrid;
            this.loaderName = 'viewDataGrid';

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
                sort: this.tempGridInfo.sortCols ? this.tempGridInfo.subjectCol + ', ' + this.tempGridInfo.sortCols.join(", ") : null,
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

                this.unmaskPanel();
            }, this);

            this.chart.removeAll();
            this.chart.add(dataGridPanel);
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
            config.subject = this.groupsSelector.getSubject();
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
                name: this.viewInfo.subjectColumn,
                schemaName: config.measures[0].measure.schemaName,
                queryName: config.measures[0].measure.queryName
            });
        }

        config.numberFormats = this.numberFormats;

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
            + "   // use LABKEY.ActionURL.buildURL to generate a link to a different controller/action within LabKey server\n"
            + "   var ptidHref = LABKEY.ActionURL.buildURL('study', 'participant', LABKEY.container.path, \n"
            + "                      {participantId: data[columnMap[\"participant\"]].value});\n"
            + "   var queryHref = LABKEY.ActionURL.buildURL('query', 'executeQuery', LABKEY.container.path, \n"
            + "                      {schemaName: measureInfo[\"schemaName\"], \"query.queryName\": measureInfo[\"queryName\"]});\n\n"
            + "   // display an Ext message box with some information from the function parameters\n"
            + "   Ext4.Msg.alert('Data Point Information',\n"
            + "       'Participant: <a href=\"' + ptidHref + '\">' + data[columnMap[\"participant\"]].value + '</a>'\n"
            + "       + '<br/> Interval: ' + data[columnMap[\"interval\"]].value\n"
            + "       + '<br/> Value: ' + data[columnMap[\"measure\"]].value\n"
            + "       + '<br/> Measure Name: ' + measureInfo[\"name\"]\n"
            + "       + '<br/> Schema Name: ' + measureInfo[\"schemaName\"]\n"
            + "       + '<br/> Query Name: <a href=\"' + queryHref + '\">' + measureInfo[\"queryName\"] + '</a>'\n"
            + "   );\n\n"
            + "   // you could also directly navigate away from the chart using window.location\n"
            + "   // window.location = ptidHref;\n"
            + "}";
    },

    getPointClickFnHelp: function() {
        return 'Your code should define a single function to be called when a data point in the chart is clicked. '
            + 'The function will be called with the following parameters:<br/><br/>'
            + '<ul style="margin-left:20px;">'
            + '<li><b>data:</b> the set of data values for the selected data point. Example: </li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 50px;">Days: {value: 10},<br/>study_Dataset1_Measure1: {value: 250}<br/>study_Dataset1_ParticipantId: {value: "123456789"}</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>columnMap:</b> a mapping from participant, interval, and measure to use when looking up values in the data object</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 50px;">participant: "study_Dataset1_ParticipantId",<br/>measure: "study_Dataset1_Measure1"<br/>interval: "Days"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>measureInfo:</b> the schema name, query name, and measure name for the selected series</li>'
            + '<div style="margin-left: 40px;">{</div>'
            + '<div style="margin-left: 50px;">name: "Measure1",<br/>queryName: "Dataset1"<br/>schemaName: "study"</div>'
            + '<div style="margin-left: 40px;">}</div>'
            + '<li><b>clickEvent:</b> information from the browser about the click event (i.e. target, position, etc.)</li></ul>';
    },

    checkForIntervalValues: function(row) {
        // for date based charts, give error message if there are no calculated interval values
        // https://www.labkey.org/issues/ITN/details.view?issueId=16156
        if (this.editorXAxisPanel.getTime() == "date")
        {
            var intervalAlias = this.editorXAxisPanel.getInterval();
            if (row[intervalAlias] && row[intervalAlias].value != null)
                this.hasIntervalData = true;
        }
    },

    warnSelectionsMissing: function() {
        var warningsExist = false;
        var seriesList = this.getSeriesList();

        if (this.chartInfo.chartSubjectSelection == "subjects" && this.chartInfo.subject.values.length == 0)
        {
            this.clearChartPanel("Please select at least one " + this.viewInfo.subjectNounSingular.toLowerCase() + " from the filter panel on the right.");
            this.toggleSaveButtons(true);
            warningsExist = true;
        }
        else if (this.chartInfo.chartSubjectSelection == "groups" && this.chartInfo.subject.groups.length < 1)
        {
            this.clearChartPanel("Please select at least one group from the filter panel on the right.");
            this.toggleSaveButtons(true);
            warningsExist = true;
        }
        else if (this.chartInfo.measures.length == 0)
        {
           this.clearChartPanel("No measure selected. Please click the \"Measures\" button to add a measure.");
            this.toggleSaveButtons(true);
            warningsExist = true;
        }
        else if (seriesList.length == 0)
        {
            this.clearChartPanel("Please select at least one series/dimension value.");
            this.toggleSaveButtons(true);
            warningsExist = true;
        }
        else if (!(this.chartInfo.displayIndividual || this.chartInfo.displayAggregate))
        {
            this.clearChartPanel("Please select either \"Show Individual Lines\" or \"Show Mean\".");
            this.toggleSaveButtons(true);
            warningsExist = true;
        }

        return warningsExist;
    },

    getMeasurePickerHelpText: function() {
        return {title: 'Which measures are included?', text: 'This grid contains dataset columns that have been designated as measures from the dataset definition. '
            + '<br/><br/>It also includes measures from queries in the study schema that contain both the ' + this.viewInfo.subjectNounSingular + 'Id and ' + this.viewInfo.subjectNounSingular + 'Visit columns.'
            + '<br/><br/>You can filter the measures in the grid using the filter textbox to the left. The filtered results will contain measures that have a match in the dataset, measure, or description column. '
            + 'You can get back to the full list of measures at any time by removing the filter.'};
    }
});
