/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4Sandbox(true);
LABKEY.requiresClientAPI();

LABKEY.MeasuresDataViewType = {
    FULL_GRID : 'fullgrid',
    SPLIT_PANELS : 'splitpanels'
};

/**
 * Constructs a new LabKey MeasuresDialog using the supplied configuration (Current usage: LABKEY.ext4.ParticipantReport).
 * @constructor
 * @augments Ext.window.Window
 * @param {string} [dataViewType] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {boolean} [multiSelect] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {string} [closeAction] whether to 'hide' or 'close' the window on select/cancel. Default: close.
 * @param {object} [filter] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {boolean} [allColumns] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {boolean} [canShowHidden] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {boolean} [forceQuery] passed to the LABKEY.ext4.MeasuresPanel definition
**/
Ext4.define('LABKEY.ext4.MeasuresDialog', {

    extend : 'Ext.window.Window',

    constructor : function(config){

        Ext4.QuickTips.init();

        Ext4.apply(this, config, {
            cls: 'extContainer',
            title: 'Add Measure...',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            multiSelect : false,
            dataViewType: LABKEY.MeasuresDataViewType.FULL_GRID
        });

        Ext4.applyIf(this, config, {
            forceQuery : false
        });

        this.callParent([config]);

        this.addEvents('measuresSelected');
    },

    initComponent : function() {

        this.buttons = [];
        this.items = [];
        this.measureSelectionBtnId = Ext4.id();

        this.measurePanel = Ext4.create('LABKEY.ext4.MeasuresPanel', {
            dataViewType: this.dataViewType,
            filter        : this.filter,
            allColumns    : this.allColumns,
            canShowHidden : this.canShowHidden,
            multiSelect : this.multiSelect,
            forceQuery  : this.forceQuery,
            bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded'],
            listeners: {
                scope: this,
                'measureChanged': function (axisId, data) {
                    Ext4.getCmp(this.measureSelectionBtnId).setDisabled(false);
                }
            }
        });
        this.items.push(this.measurePanel);

        this.buttons.push({
            id: this.measureSelectionBtnId,
            text:'Select',
            disabled:true,
            handler: function(){
                var recs = this.measurePanel.getSelectedRecords();
                if (recs && recs.length > 0)
                {
                    this.fireEvent('measuresSelected', recs, true);
                }
                this.closeAction == 'hide' ? this.hide() : this.close();
            },
            scope: this
        });

        this.buttons.push({text : 'Cancel', handler : function(){this.closeAction == 'hide' ? this.hide() : this.close();}, scope : this});

        this.callParent([arguments]);
    }
});

/**
 * Constructs a new LabKey MeasuresPanel using the supplied configuration.
 * @constructor
 * @augments Ext.panel.Panel
 * @param {string} [dataViewType] which data view version to use for the measure panel (either LABKEY.MeasuresDataViewType.FULL_GRID or LABKEY.MeasuresDataViewType.SPLIT_PANELS), defaults to LABKEY.MeasuresDataViewType.FULL_GRID.
 * @param {boolean} [multiSelect] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {object} [filter] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {boolean} [allColumns] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {boolean} [canShowHidden] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {boolean} [forceQuery] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {boolean} [hideDemographicMeasures] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
 * @param {object} [axis] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
**/
Ext4.define('LABKEY.ext4.MeasuresPanel', {

    extend: 'Ext.panel.Panel',

    constructor : function(config){

        this.tbarActions = []; // keep the list of toolbar actions and selections
        this.axisMap = {}; // map of name to axis info

        Ext4.apply(this, config, {
            isDateAxis : false,
            allColumns : false,
            canShowHidden : false,
            dataViewType : LABKEY.MeasuresDataViewType.FULL_GRID
        });

        this.callParent([config]);

        this.addEvents(
            'beforeMeasuresStoreLoad',
            'measuresStoreLoaded',
            'measureChanged'
        );
    },

    initComponent : function() {
        this.layout = 'fit';
        this.border = false;

        if (this.dataViewType == LABKEY.MeasuresDataViewType.SPLIT_PANELS)
        {
            this.dataView = Ext4.create('LABKEY.ext4.MeasuresDataView.SplitPanels', {
                allColumns    : this.allColumns,
                showHidden : this.canShowHidden,
                bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded', 'measureChanged']
            });
        }
        else // default is LABKEY.MeasuresDataViewType.FULL_GRID
        {
            this.dataView = Ext4.create('LABKEY.ext4.MeasuresDataView.FullGrid', {
                axis: [{
                    multiSelect: false,
                    name: "y-axis",
                    label: "Choose a data measure"
                }],
                filter        : this.filter,
                allColumns    : this.allColumns,
                canShowHidden : this.canShowHidden,
                multiSelect : this.multiSelect,
                forceQuery  : this.forceQuery,
                bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded', 'measureChanged']
            });
        }

        this.items = [this.dataView];

        this.callParent([arguments]);
    },

    getSelectionModel : function() {
        return this.dataView.getSelectionModel();
    },

    getSelectedRecords : function() {
        return this.dataView.getSelectedRecords();
    }
});

/**
 * Constructs a new LabKey MeasuresDataView to display a grid of all measures with columns for dataset, label, and description using the supplied configuration.
 * @constructor
 * @augments Ext.panel.Panel
 * @param {boolean} [multiSelect] if true, display a grid panel with a checkbox column to allow selection of more than one measure. Default: false.
 * @param {object} [filter] LABKEY.Visualization.Filter object to allow filtering of the measures returned by the LABKEY.Visualization.getMeasures method.
 * @param {boolean} [allColumns] passed to LABKEY.Visualization.getMeasures method
 * @param {boolean} [canShowHidden] if true, add a "Show All" checkbox to the display to tell the LABKEY.Visualization.getMeasures method whether or not the show hidden columns
 * @param {boolean} [forceQuery] if true, call the getMeasures method on init
 * @param {boolean} [hideDemographicMeasures] if true, hide the measures from demographic datasets from the display
 * @param {object} [axis]
 * @param {boolean} [isDateAxis]
**/
Ext4.define('LABKEY.ext4.MeasuresDataView.FullGrid', {

    extend: 'Ext.panel.Panel',

    constructor : function(config){

        this.tbarActions = []; // keep the list of toolbar actions and selections
        this.axisMap = {}; // map of name to axis info

        Ext4.apply(this, config, {
            isDateAxis : false,
            allColumns : false,
            canShowHidden : false
        });

        this.callParent([config]);

        this.addEvents(
            'beforeMeasuresStoreLoad',
            'measuresStoreLoaded',
            'measureChanged'
        );
    },

    initComponent : function() {
        this.layout = 'border';
        this.border = false;
        this.flex = 1;

        this.items = [
            this.createMeasuresFormPanel(),
            this.createMeasuresListPanel()
        ];

        this.loaded = false;

        // load the store the first time after this component has rendered
        this.on('afterrender', this.getMeasures, this);
        if (this.forceQuery)
            this.getMeasures(this);

        // Show the mask after the component size has been determined, as long as the
        // data is still loading:
        this.on('afterlayout', function() {
            if (!this.loaded)
                this.getEl().mask("loading measures...", "x-mask-loading");
        });

        this.callParent([arguments]);
    },

    getMeasures : function(cmp, clearCache) {

        var filter = this.filter || LABKEY.Visualization.Filter.create({schemaName: 'study'});

        if (this.selectedMeasure)
        {
            filter = LABKEY.Visualization.Filter.create({schemaName: this.selectedMeasure.schemaName,
                queryName: this.selectedMeasure.queryName});
        }

        // if the measure store data is not already loaded, get it. otherwise, use the cached data object
        if (!this.measuresStoreData || clearCache)
        {
            if (!this.isLoading) {
                this.isLoading = true;
                LABKEY.Visualization.getMeasures({
                    filters      : [filter],
                    dateMeasures : this.isDateAxis,
                    allColumns   : this.allColumns,
                    showHidden   : this.showHidden,
                    success      : function(measures, response){
                        this.isLoading = false;
                        this.measuresStoreData = Ext4.JSON.decode(response.responseText);
                        if(this.hideDemographicMeasures){
                            // Remove demographic measures in some cases (i.e. time charts).
                            for(var i = this.measuresStoreData.measures.length; i--;){
                                if(this.measuresStoreData.measures[i].isDemographic === true){
                                    this.measuresStoreData.measures.splice(i, 1);
                                }
                            }
                        }
                        this.fireEvent('beforeMeasuresStoreLoad', this, this.measuresStoreData);
                        this.measuresStore.loadRawData(this.measuresStoreData);

                        var value = this.searchBox.getValue();
                        if (value && clearCache) {
                            // refilter the grid, if a filter was present
                            this.filterMeasures(value);
                        }
                    },
                    failure      : function(info, response, options) {
                        this.isLoading = false;
                        LABKEY.Utils.displayAjaxErrorResponse(response, options);
                    },
                    scope : this
                });
            }
        }
        else
        {
            if (this.rendered)
                this.measuresStore.loadRawData(this.measuresStoreData);
        }
    },

    createMeasuresListPanel : function() {

        // define a store to wrap the data measures
        this.measuresStore = Ext4.create('LABKEY.ext4.MeasuresStore', {
            listeners : {
                measureStoreSorted : function(store) {

                    this.loaded = true;
                    //Prefilter list
                    var datasetName = LABKEY.ActionURL.getParameter("queryName");
                    if (datasetName)
                    {
                        this.searchBox.setValue(LABKEY.ActionURL.getParameter("queryName"));
                        this.searchBox.focus(true, 100);
                        this.filterMeasures(datasetName);
                    }

                    if (this.rendered)
                        this.getEl().unmask();

                    this.fireEvent('measuresStoreLoaded', this);
                },
                exception : function(proxy, type, action, options, resp) {
                    LABKEY.Utils.displayAjaxErrorResponse(resp, options);
                    this.getEl().unmask();
                },
                scope : this
            }
        });

        // tooltip for description text       
        var ttRenderer = function(value, p, record) {
            var msg = Ext4.util.Format.htmlEncode(value);
            p.tdAttr = 'data-qtip="' + msg + '"';
            return msg;
        };

        if (this.multiSelect)
        {
            this.view = Ext4.create('Ext.grid.GridPanel', {
                cls: 'measuresGridPanel', // for selenium test usage                
                store: this.measuresStore,
                flex: 1,
                border: false,
                stripeRows : true,
                selModel : Ext4.create('Ext.selection.CheckboxModel'),
                multiSelect: true,
                bubbleEvents : ['viewready'],
                columns: [
                    {header:'Dataset', dataIndex:'queryName', flex: 2},
                    {header:'Measure', dataIndex:'label', flex: 2},
                    {header:'Description', dataIndex:'description', cls : 'normal-wrap', renderer : ttRenderer, flex: 3}
                ]
            });
        }
        else
        {
            this.view = Ext4.create('Ext.list.ListView', {
                store: this.measuresStore,
                flex: 1,
                border: false,
                multiSelect: false,
                columns: [
                    {header:'Dataset', dataIndex:'queryName', flex: 2},
                    {header:'Measure', dataIndex:'label', flex: 2},
                    {header:'Description', dataIndex:'description', cls : 'normal-wrap', renderer : ttRenderer, flex: 3}
                ],
                listeners: {
                    itemdblclick: function (view, record, item, index, event){
                        var recs = view.getSelectedRecords();
                        if (recs && recs.length > 0)
                        {
                            this.fireEvent('measuresSelected', recs, true);
                        }
                    },
                    scope: this
                }
            });
        }

        // enable disable toolbar actions on selection change
        this.view.getSelectionModel().on('selectionchange', this.onListViewSelectionChanged, this);

        var tbarItems = [{xtype:'tbspacer'}];

        this.searchDisplayField = Ext4.create('Ext.form.DisplayField', {
            width: 35,
            value: "Filter: "
        });

        tbarItems.push(this.searchDisplayField);
        tbarItems.push({xtype:'tbspacer'});

        this.searchBox = Ext4.create('Ext.form.TextField', {
            width: 200,
            enableKeyEvents: true,
            emptyText : 'Search',
            name : 'filterSearch',
            listeners : {
                // filter the listview using both the label and category names
                keyup : function(cmp, e){ this.filterMeasures(cmp.getValue()); },
                scope : this
            }
        });
        tbarItems.push(this.searchBox);

        this.errorField = Ext4.create('Ext.form.DisplayField', {
            width: 250,
            hidden: true,
            html: "<span style='color:red;'>No results found for current filter</span>"
        });
        tbarItems.push(this.errorField);

        if (this.canShowHidden) {

            tbarItems.push('->');
            tbarItems.push({
                xtype   : 'checkbox',
                boxLabel: 'Show all',
                width: 75,
                handler : function(cmp, checked){
                    this.showHidden = checked;
                    this.getEl().mask("loading measures...", "x-mask-loading");
                    this.getMeasures(cmp, true);
                },
                scope   : this
            });
        }

        // create a toolbar button for each of the axis types
        if (this.hasBtnSelection) {

            for (var i=0; i < this.axis.length; i++)
            {
                var axis = this.axis[i];

                if (this.axisMap[axis.name]) {
                    var action = Ext4.create('Ext.Action', {
                        iconCls: 'iconUp',
                        text: 'Add to ' + axis.name,
                        handler: this.onListViewBtnClicked,
                        scope: this,
                        disabled: true,
                        tooltip: 'Adds the selected measurement into the axis field on the right',
                        labelId: this.axisMap[axis.name].labelId,
                        axisId: axis.name
                    });
                    this.tbarActions.push(action);
                    tbarItems.push(action);
                }
            }
        }

        var items = [];
        items.push(Ext4.create('Ext.Toolbar', {
            style : 'padding: 5px 2px',
            border : false, 
            items: tbarItems
        }));
        items.push(this.view);

        return Ext4.create('Ext.Panel', {
            region: 'center',
            border: false, 
            layout: {
                type: 'vbox',
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });
    },

    filterMeasures : function (txt) {
        if (txt) {
           //Issue 14190: this attempts to balance the need for flexible searching (ie. partial words, random ordering of terms)
            // and the need to get a reasonably small set of results.  the code should:
            //
            // 1) remove/ignore punctuation from search term
            // 2) split term on whitespace
            // 3) return any record where ALL tokens appear at least once in any of the fields.  order does not matter.  the token must begin on a word boundary

            txt = txt.replace(/[^a-z0-9+\-]+/gi, ' ');
            txt = Ext4.util.Format.trim(txt);
            txt = Ext4.escapeRe(txt);

            var tokens = txt.split(/\s/g);
            var matches = [];
            for(var i=0;i<tokens.length;i++){
                matches.push(new RegExp('\\b' + tokens[i], 'i'));
            }

            //NOTE: if ever split into a standalone component, we would want a config option specifying these fields
            var fields = ['queryName', 'label', 'description'];

            this.measuresStore.clearFilter();
            this.measuresStore.filter([{
                filterFn: function(record){
                    // for multi-select don't clear selections on filter
                    if (this.getSelectionModel().isSelected(record))
                        return true;

                    //test presence of term in any field
                    var term = '';
                    for(var i=0; i<fields.length;i++){
                        term += record.get(fields[i]) + ' ';
                    }

                    for(i=0;i<matches.length;i++){
                        if(!term.match(matches[i]))
                            return false;
                    }
                    return true;
                },
                scope : this
            }]);
        }
        else
            this.measuresStore.clearFilter();

        if(this.measuresStore.getCount() == 0){
            this.errorField.show();
        } else {
            this.errorField.hide();
        }
    },

    onListViewSelectionChanged : function(cmp, selections) {
        if (this.hasBtnSelection)
        {
            var disabled = selections.length == 0;

            for (var i=0; i < this.tbarActions.length; i++)
                this.tbarActions[i].setDisabled(disabled);
        }
        else
            this.setTextFieldSelection(this.axisId);
    },

    onListViewBtnClicked : function(btn, e) {

        if (this.view)
            this.setTextFieldSelection(btn.initialConfig.axisId);
    },

    setTextFieldSelection : function(axisId) {

        var selection = this.getSelectedRecords();
        if (selection.length == 1)
        {
            var rec = selection[0];
            var textField = Ext4.getCmp(this.axisMap[axisId].labelId);
            if (textField) {
                textField.setValue(rec.data.queryName + ' : ' + rec.data.label);
                this.fireEvent('measureChanged', axisId, rec.data);
            }
        }
    },

    createMeasuresFormPanel : function() {

        var items = [];

        for (var i=0; i < this.axis.length; i++)
        {
            var axis = this.axis[i];

            if (this.isDateAxis && !axis.timeAxis)
            {
                // if we are picking dates, constrain the date columns to the selected y-axis query name
                if (this.measures)
                    this.selectedMeasure = this.measures[axis.name];

                continue;
            }

            if (!this.isDateAxis && axis.timeAxis)
                continue;

            var field = Ext4.create('Ext.form.DisplayField', {
                width:400,
                hideLabel: true
            });

            var labelField = Ext4.create('Ext.form.DisplayField', {
                width:200,
                value: axis.label + ":",
                hideLabel: true
            });

            // stash the textfield id so we can update it later from the listview
            this.axisMap[axis.name] = {labelId: field.id};
            this.axisId = axis.name;

            items.push(labelField);
            items.push(field);
        }

        // if we have more than one axis, use a tbar button selection model
        this.hasBtnSelection = items.length > 2;

        return Ext4.create('Ext.form.FormPanel', {
            labelWidth: 175,
            border: false,
            bodyStyle:'padding:15px;',
            region: 'north',
            layout: 'hbox',
            width: '100%',
            items: items
        });
    },

    getSelectionModel : function() {
        return this.view.getSelectionModel();
    },

    getSelectedRecords : function() {
        return this.getSelectionModel().getSelection();
    }
});

/**
 * Constructs a new LabKey MeasuresDataView to display a grid of all measures with columns for dataset, label, and description using the supplied configuration.
 * @constructor
 * @augments Ext.panel.Panel
 * @param {boolean} [allColumns] passed to LABKEY.Visualization.getMeasures method
 * @param {boolean} [showHidden] passed to LABKEY.Visualization.getMeasures method
**/
Ext4.define('LABKEY.ext4.MeasuresDataView.SplitPanels', {

    extend: 'Ext.panel.Panel',

    constructor : function(config){

        Ext4.apply(this, config, {
            allColumns : false,
            showHidden : false
        });

        this.callParent([config]);

        this.addEvents(
            'beforeMeasuresStoreLoad',
            'measuresStoreLoaded',
            'measureChanged'
        );
    },

    initComponent : function() {

        this.layout = {
            type: 'hbox',
            pack: 'start',
            align: 'stretch'
        };
        this.border = false;

        this.items = [
            this.createSourcePanel(),
            this.createMeasurePanel()
        ];

        this.loaded = false;

        this.selectedMeasures = [];

        // load the store the first time after this component has rendered
        this.on('afterrender', this.getMeasures, this);

        // Show the mask after the component size has been determined, as long as the data is still loading:
        this.on('afterlayout', function() {
            if (!this.loaded) 
                this.getEl().mask("loading measures...", "x-mask-loading");
        });

        this.callParent([arguments]);
    },

    createSourcePanel : function() {

        // Using a new MeasureStore, but we will only load data for the list of queryNames (i.e. Sources)
        this.sourcesStore = Ext4.create('LABKEY.ext4.MeasuresStore', {})

        this.sourcesGrid = Ext4.create('Ext.grid.GridPanel', {
            store: this.sourcesStore,
            selModel: Ext4.create('Ext.selection.RowModel', {
                singleSelect: true,
                listeners: {
                    select: this.onSourceRowSelection,
                    scope: this
                }
            }),
            flex: 1,
            hideHeaders: true,
            enableColumnHide: false,
            enableColumnResize: false,
            stripeRows: false,
            border: false,
            forceFit: true,
            columns: [{header: 'Source', dataIndex: 'queryName', cls: ''}]
        });

        this.sourcePanel = Ext4.create('Ext.panel.Panel', {
            flex: 1,
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            padding: 5,
            border: false,
            items: [
                {
                    xtype: 'displayfield',
                    html: '<span style="font-weight:bold;font-size:large">Source</span>',
                    padding: 10,
                    height: 40
                },
                this.sourcesGrid
            ]
        });

        return this.sourcePanel;
    },

    createMeasurePanel : function() {
        // Using a new MeasureStore, but we will only display filtered sets of measures
        this.measuresStore = Ext4.create('LABKEY.ext4.MeasuresStore', {
            listeners : {
                measureStoreSorted : function(store) {
                    this.loaded = true;

                    this.fireEvent('measuresStoreLoaded', this);

                    if (this.rendered)
                        this.getEl().unmask();
                },
                exception : function(proxy, type, action, options, resp) {
                    LABKEY.Utils.displayAjaxErrorResponse(resp, options);
                    this.getEl().unmask();
                },
                scope : this
            }
        });

        this.measuresGrid = Ext4.create('Ext.grid.GridPanel', {
            store: this.measuresStore,
            stripeRows : true,
            border: false,
            selModel : Ext4.create('Ext.selection.CheckboxModel', {
                checkOnly: true,
                listeners: {
                    select: this.onMeasureSelect,
                    deselect: this.onMeasureDeselect,
                    scope: this
                }
            }),
            flex: 1,
            hidden: true, // starts hidden until a source query is chosen
            hideHeaders: true,
            enableColumnHide: false,
            enableColumnResize: false,
            multiSelect: true,
            bubbleEvents : ['viewready'],
            columns: [{header: 'Measure', dataIndex: 'label', flex: 1}]
        });

        this.measuresGrid.getSelectionModel().on('selectionchange', function(selModel) {
            this.fireEvent('measureChanged', null, null);
        }, this);

        // workaround for scrollbar issue with adding/removing filters and reloading data
        this.measuresGrid.on('scrollershow', function(scroller) {
            if (scroller && scroller.scrollEl) {
                scroller.clearManagedListeners();
                scroller.mon(scroller.scrollEl, 'scroll', scroller.onElScroll, scroller);
            }
        });        

        this.measurePanel = Ext4.create('Ext.panel.Panel', {
            flex: 1,
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            padding: 5,
            border: false,
            disabled: true, // starts disabled until a source query is chosen
            items: [
                {
                    xtype: 'displayfield',
                    html: '<span style="font-weight:bold;font-size:large">Measure</span>',
                    padding: 10,
                    height: 40
                },
                // TODO: add a Select All checkbox
                this.measuresGrid
            ]
        });

        return this.measurePanel;        
    },

    getMeasures : function(cmp, clearCache) {

        var filter = LABKEY.Visualization.Filter.create({schemaName: 'study'});

        LABKEY.Visualization.getMeasures({
            filters      : [filter],
            allColumns   : this.allColumns,
            showHidden   : this.showHidden,
            success      : function(measures, response){
                this.isLoading = false;
                this.measuresStoreData = Ext4.JSON.decode(response.responseText);

                this.sourcesStoreKeys = [];
                // initialize the sources grid with the first item as a way to view the selected measures
                this.sourcesStoreData = [{id: 'SELECTED', schemaName: null, queryName: '0 Selected Measures', label: null}];
                Ext4.each(this.measuresStoreData.measures, function(measure) {
                    var key = measure.schemaName + "|" + measure.queryName;
                    var query = {schemaName: measure.schemaName, queryName: measure.queryName};
                    if (this.sourcesStoreKeys.indexOf(key) == -1)
                    {
                        this.sourcesStoreKeys.push(key);
                        this.sourcesStoreData.push(query);
                    }
                }, this);

                this.fireEvent('beforeMeasuresStoreLoad', this, this.measuresStoreData);

                // Load the full measures list for the measuresStore, but we will only show filtered sets of measures
                this.measuresStore.loadRawData(this.measuresStoreData);

                // Load only the list of queries (i.e. sources) for the souresStore
                this.sourcesStore.loadRawData({measures: this.sourcesStoreData});
            },
            failure      : function(info, response, options) {
                this.isLoading = false;
                LABKEY.Utils.displayAjaxErrorResponse(response, options);
            },
            scope : this
        });
    },

    onSourceRowSelection : function(rowModel, sourceRecord, index) {
        // filter the measure grid based on the selected source query
        this.getEl().mask("filtering measures...", "x-mask-loading");
        this.measuresStore.clearFilter();
        this.measuresStore.filter([{
            filterFn: function(measureRecord) {
                // if the selected source record is the "# Selected Measures", then only show the selected records
                if (sourceRecord.get("id") == "SELECTED")
                {
                    return measureRecord.get("selected");
                }
                else
                {
                    return (sourceRecord.get("schemaName") == measureRecord.get("schemaName")
                            && sourceRecord.get("queryName") == measureRecord.get("queryName"));
                }
            },
            scope: this
        }]);

        // since selections aren't remembered after filters are applied, reselect any of the selected measure that are visible for this filter
        Ext4.each(this.selectedMeasures, function(measure) {
            this.measuresGrid.getSelectionModel().select(measure, true, true);
        }, this);

        this.getEl().unmask();

        // enable the measure panel and show the grid
        this.measurePanel.setDisabled(false);
        this.measuresGrid.show();
    },

    onMeasureSelect : function(selModel, record, ix) {
        var index = this.getSelectedRecordIndex(record);
        if (index == -1)
        {
            record.set("selected", true);
            record.commit(); // to remove the dirty state
            this.selectedMeasures.push(record);

            this.updateSourcesSelectionEntry();
        }
    },

    onMeasureDeselect : function(selModel, record, ix) {
        var index = this.getSelectedRecordIndex(record);
        if (index > -1)
        {
            record.set("selected", false);
            record.commit(); // to remove the dirty state
            this.selectedMeasures.splice(index, 1);

            this.updateSourcesSelectionEntry();
        }
    },

    updateSourcesSelectionEntry : function() {
        // update entry in the sources grid which indicates how many selected measures there are
        var selectionEntry = this.sourcesStore.getById('SELECTED');
        selectionEntry.set('queryName', this.selectedMeasures.length == 1 ? '1 Selected Measure' : this.selectedMeasures.length + ' Selected Measures');
        selectionEntry.commit(); // to remove the dirty state
    },

    getSelectedRecordIndex : function(record) {
        for (var i = 0; i < this.selectedMeasures.length; i++)
        {
            var tempRec = this.selectedMeasures[i];
            if (tempRec.get("schemaName") == record.get("schemaName")
                    && tempRec.get("queryName") == record.get("queryName")
                    && tempRec.get("name") == record.get("name"))
            {
                return i;
            }
        }
        return -1;
    },

    getSelectedRecords : function() {
        return this.selectedMeasures;
    }
});

Ext4.define('LABKEY.ext4.MeasuresStore', {

    extend: 'Ext.data.Store',

    constructor : function(config){

        Ext4.define('Measure', {
            extend : 'Ext.data.Model',
            fields : [
                {name   : 'id'},
                {name   : 'name'},
                {name   : 'label'},
                {name   : 'description'},
                {name   : 'isUserDefined'},
                {name   : 'isDemographic'},
                {name   : 'queryName'},
                {name   : 'schemaName'},
                {name   : 'type'},
                {name   : 'selected'}
            ]
        });

        Ext4.apply(this, config, {
            autoLoad: false,
            model: 'Measure',
            proxy: {
                type: 'memory',
                reader: {
                    type: 'json',
                    root:'measures',
                    idProperty:'id'
                }
            },
            remoteSort: false
        });

        this.callParent([config]);

        this.addEvents("measureStoreSorted");

        this.on('load', function(store) {
            store.sort([{property: 'queryName', direction: 'ASC'},{property: 'label', direction: 'ASC'}]);
            this.fireEvent("measureStoreSorted", this);
        }, this);        
    },

    initComponent : function() {

        this.callParent([arguments]);
    }
});