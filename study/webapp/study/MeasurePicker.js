/*
 * Copyright (c) 2012-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

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
 * @param {object} [helpText] passed to the LABKEY.ext4.MeasuresPanel definition
 * @param {boolean} [forceQuery] passed to the LABKEY.ext4.MeasuresPanel definition
**/
Ext4.define('LABKEY.ext4.MeasuresDialog', {

    extend : 'Ext.window.Window',

    constructor : function(config){

        Ext4.QuickTips.init();

        Ext4.apply(this, config, {
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
        Ext4.QuickTips.init();

        this.buttons = [];
        this.items = [];

        this.measurePanel = Ext4.create('LABKEY.ext4.MeasuresPanel', {
            dataViewType: this.dataViewType,
            filter        : this.filter,
            allColumns    : this.allColumns,
            canShowHidden : this.canShowHidden,
            helpText      : this.helpText,
            ui: this.ui,
            multiSelect : this.multiSelect,
            forceQuery  : this.forceQuery,
            bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded'],
            listeners: {
                scope: this,
                'selectionchange' : function(cmp, recs) {
                    var btn = this.down('#selectButton');
                    if (btn)
                        btn.setDisabled(!recs.length);
                }
            }
        });
        this.items.push(this.measurePanel);

        this.buttons.push({
            itemId: 'selectButton',
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

        this.callParent();
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
 * @param {object} [helpText] passed to LABKEY.ext4.MeasuresDataView.FullGrid definition
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
            helpText : null,
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
                filter        : this.filter,
                allColumns    : this.allColumns,
                ui : this.ui,
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
                ui: this.ui,
                allColumns    : this.allColumns,
                canShowHidden : this.canShowHidden,
                helpText      : this.helpText,
                multiSelect : this.multiSelect,
                forceQuery  : this.forceQuery,
                bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded', 'measureChanged', 'measuresSelected', 'selectionchange']
            });
        }

        this.items = [this.dataView];

        this.callParent();
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
 * @param {object} [filter] LABKEY.Query.Visualization.Filter object to allow filtering of the measures returned by the LABKEY.Query.Visualization.getMeasures method.
 * @param {boolean} [allColumns] passed to LABKEY.Query.Visualization.getMeasures method
 * @param {boolean} [canShowHidden] if true, add a "Show All" checkbox to the display to tell the LABKEY.Query.Visualization.getMeasures method whether or not the show hidden columns
 * @param (object) [helpText] object with a title and text attribute to be displayed in a tooltip in the grid top toolbar
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
            canShowHidden : false,
            helpText : null
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
            if (!this.loaded) {
                this.getEl().mask("loading measures...");
            }
        }, this);

        this.callParent();
    },

    getMeasures : function(cmp, clearCache) {

        var filter = this.filter || LABKEY.Query.Visualization.Filter.create({schemaName: 'study'});

        if (this.selectedMeasure)
        {
            filter = LABKEY.Query.Visualization.Filter.create({schemaName: this.selectedMeasure.schemaName,
                queryName: this.selectedMeasure.queryName});
        }

        // if the measure store data is not already loaded, get it. otherwise, use the cached data object
        if (!this.measuresStoreData || clearCache)
        {
            if (!this.isLoading) {
                this.isLoading = true;
                LABKEY.Query.Visualization.getMeasures({
                    filters      : [filter],
                    dateMeasures : this.isDateAxis,
                    allColumns   : this.allColumns,
                    showHidden   : this.showHidden,
                    success      : function(measures, response){
                        this.isLoading = false;
                        this.measuresStoreData = Ext4.JSON.decode(response.responseText);
                        if (this.hideDemographicMeasures) {
                            // Remove demographic measures in some cases (i.e. time charts).
                            for(var i = this.measuresStoreData.measures.length; i--;){
                                if(this.measuresStoreData.measures[i].isDemographic === true){
                                    this.measuresStoreData.measures.splice(i, 1);
                                }
                            }
                        }
                        this.fireEvent('beforeMeasuresStoreLoad', this, this.measuresStoreData);
                        this.measuresStore.loadRawData(this.measuresStoreData);
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

                    // Re-select any measures on store reload (i.e. if "Show All" checkbox selected
                    if (this.reloadingStore && this.recordsForReselect)
                    {
                        Ext4.each(this.recordsForReselect, function(record) {
                            var index = this.measuresStore.findBy(function(r) {
                                return (
                                    record.data.schemaName == r.data.schemaName &&
                                    record.data.queryName  == r.data.queryName &&
                                    record.data.name       == r.data.name
                                );
                            }, this);

                            if (index > -1)
                                this.getSelectionModel().select(index, true, true);
                        }, this);
                    }

                    //Prefilter list by queryName on initial load
                    var datasetName = LABKEY.ActionURL.getParameter("queryName");
                    if (!this.reloadingStore && datasetName)
                    {
                        this.searchBox.setValue(LABKEY.ActionURL.getParameter("queryName"));
                        this.searchBox.focus(true, 100);
                    }

                    // filter the list based on the search box value
                    this._lastFilterText = '';
                    this.filterMeasures(this.searchBox.getValue());

                    if (this.rendered) {
                        this.getEl().unmask();
                    }
                    this.fireEvent('measuresStoreLoaded', this);

                    this.reloadingStore = false;
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
            this.view = Ext4.create('Ext.grid.Panel', {
                cls: 'measuresGridPanel iScroll', // for selenium test usage
                store: this.measuresStore,
                flex: 1,
                ui: this.ui,
                border: false,
                stripeRows : true,
                selModel : Ext4.create('Ext.selection.CheckboxModel', {mode: 'SIMPLE'}),
                multiSelect: true,
                bubbleEvents : ['viewready', 'selectionchange'],
                columns: [
                    {header:'Dataset/Query Name', dataIndex:'queryName', flex: 2, hidden: true},
                    {header:'Dataset/Query', dataIndex:'queryLabel', flex: 2},
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
                ui: this.ui,
                border: false,
                multiSelect: false,
                bubbleEvents : ['selectionchange'],
                columns: [
                    {header:'Dataset/Query Name', dataIndex:'queryName', flex: 2, renderer: 'htmlEncode', hidden: true},
                    {header:'Dataset/Query', dataIndex:'queryLabel', flex: 2, renderer: 'htmlEncode'},
                    {header:'Measure', dataIndex:'label', flex: 2, renderer: 'htmlEncode'},
                    {header:'Description', dataIndex:'description', cls : 'normal-wrap', renderer : ttRenderer, flex: 3}
                ],
                listeners: {
                    itemdblclick: function (view, record, item, index, event){
                        this.fireEvent('measuresSelected', [record], true);
                    },
                    scope: this
                }
            });
        }

        // enable disable toolbar actions on selection change
        this.view.getSelectionModel().on('selectionchange', this.onListViewSelectionChanged, this);

        var tbarItems = [{xtype:'tbspacer'}];

        this.searchBox = Ext4.create('Ext.form.TextField', {
            fieldLabel: 'Filter',
            labelWidth: 40,
            width: 225,
            enableKeyEvents: true,
            emptyText : 'Search',
            name : 'filterSearch'
        });
        var taskFilterMeasures = new Ext4.util.DelayedTask(function(){this.filterMeasures(this.searchBox.getValue());}, this);
        this.searchBox.on('change', function(cmp,e){taskFilterMeasures.delay(333);});
        tbarItems.push(this.searchBox);

        this.clearFilterBtn = Ext4.create('Ext.Button', {
            hidden: true,
            iconCls:'iconDelete',
            tooltip: 'Clear filter',
            handler: function(){this.searchBox.setValue('');},
            scope: this
        });
        tbarItems.push(this.clearFilterBtn);

        this.errorField = Ext4.create('Ext.form.DisplayField', {
            width: 250,
            hidden: true,
            value: "<span style='color:red;'>No results found for current filter</span>"
        });
        tbarItems.push(this.errorField);

        if (this.canShowHidden) {

            tbarItems.push('->');
            tbarItems.push({
                xtype   : 'checkbox',
                boxLabel: 'Show all',
                width: 75,
                handler : function(cmp, checked){
                    this.getEl().mask("loading measures...", "x-mask-loading");

                    this.showHidden = checked;
                    this.reloadingStore = true;
                    this.recordsForReselect = this.getSelectionModel().getSelection();

                    // clear the filter, it will be re-applied after reload of store
                    this.measuresStore.clearFilter();
                    this.getMeasures(cmp, true);
                },
                scope   : this
            });
        }

        if (this.helpText)
        {
            tbarItems.push('->');
            var helpCmp = Ext4.create('Ext.form.DisplayField', {
                value: 'Help?',
                style: { 'text-decoration': 'underline' },
                listeners: {
                    scope: this,
                    afterrender: function(cmp) {
                        Ext4.create('Ext.tip.ToolTip', {
                            target: cmp.el,
                            width: 250,
                            title: this.helpText.title,
                            html: this.helpText.text,
                            trackMouse: true,
                            dismissDelay: 30000
                        });
                    }
                }
            });
            tbarItems.push(helpCmp);
            tbarItems.push({xtype:'tbspacer'});
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
            ui: this.ui,
            border : false, 
            items: tbarItems
        }));
        items.push(this.view);

        return Ext4.create('Ext.Panel', {
            region: 'center',
            border: false,
            ui: this.ui,
            layout: {
                type: 'vbox',
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });
    },

    _lastFilterText : '',

    filterMeasures : function (txt)
    {
        txt = (txt || '').trim();
        if (txt == this._lastFilterText)
            return;
        this._lastFilterText = txt;

        if (txt) {
           //Issue 14190: this attempts to balance the need for flexible searching (ie. partial words, random ordering of terms)
            // and the need to get a reasonably small set of results.  the code should:
            //
            // 1) remove/ignore punctuation from search term
            // 2) split term on whitespace
            // 3) return any record where ALL tokens appear at least once in any of the fields.  order does not matter.  the token must begin on a word boundary

            txt = txt.replace(/[^a-z0-9_+\-]+/gi, ' ');
            txt = Ext4.util.Format.trim(txt);
            txt = Ext4.escapeRe(txt);

            var tokens = txt.split(/\s/g);
            var matches = [];
            for(var i=0;i<tokens.length;i++){
                matches.push(new RegExp('\\b' + tokens[i], 'i'));
            }

            //NOTE: if ever split into a standalone component, we would want a config option specifying these fields
            var fields = ['queryName', 'queryLabel', 'label', 'description'];

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
            this.clearFilterBtn.show();
        }
        else
        {
            this.measuresStore.clearFilter();
            this.clearFilterBtn.hide();
        }

        if(this.measuresStore.getCount() == 0){
            this.errorField.show();
        } else {
            this.errorField.hide();
        }
        this.searchBox.focus();
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
                textField.setValue(Ext4.util.Format.htmlEncode((rec.data.queryLabel || rec.data.queryName) + ' : ' + rec.data.label));
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
            ui: this.ui,
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
 * @param {object} [filter] LABKEY.Query.Visualization.Filter object to allow filtering of the measures returned by the LABKEY.Query.Visualization.getMeasures method.
 * @param {boolean} [multiSelect] True to allow multiple measures to be selected at once
 * @param {boolean} [allColumns] passed to LABKEY.Query.Visualization.getMeasures method
 * @param {boolean} [showHidden] passed to LABKEY.Query.Visualization.getMeasures method
**/
Ext4.define('LABKEY.ext4.MeasuresDataView.SplitPanels', {

    extend: 'Ext.panel.Panel',

    layout: {
        type: 'hbox',
        pack: 'start',
        align: 'stretch'
    },

    border: false,

    measurePanelCls: 'measurepanel',

    sourcePanelCls: 'sourcepanel',

    // allows for the measure picker to display the number of matching subjects
    // for the given measure. Currently, depends on the CDS module being available
    // for server action.
    displaySourceCounts: false,

    // only used if displaySourceCounts is 'true'
    updateSourceCountsOnLoad: true,
    sourceCountMemberSet: null,
    sourceCountSourceSet: [],
    sourceCountIdColumn: null,
    sourceCountSchema: null,

    sourceGroupHeader : 'Queries',
    variablePanelHeader : 'Variables',

    // the "select all" column header for the measures grid
    measuresAllHeader : null,

    // group the 'selected' measures as a separate group from which the user can modify the current set
    supportSelectionGroup: false,
    supportSelectionLabel: 'Current columns',
    supportSelectionDescription: 'Currently selected variables grouped by source',

    supportSessionGroup: false,
    supportSessionLabel: 'All variables from this session',
    supportSessionDescription: 'All variables that have been selected from this session grouped by source',

    loadingMsgTxt: 'loading measures...',

    constructor : function(config) {

        Ext4.apply(this, config, {
            allColumns : false,
            showHidden : false,
            trackSelectionCount : false
        });

        this.callParent([config]);

        this.addEvents(
            'beforeMeasuresStoreQuery',
            'beforeMeasureSourceCountsLoad',
            'beforeMeasuresStoreLoad',
            'measureSourceCountsLoad',
            'measuresStoreLoaded',
            'measureChanged'
        );
    },

    initComponent : function() {

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
                this.getEl().mask(this.loadingMsgTxt);
        });

        this.callParent();
    },

    getSourceStore : function() {
        if (!this.sourcesStore) {
            // Using a new MeasureStore, but we will only load data for the list of queryNames (i.e. Sources)
            this.sourcesStore = Ext4.create('LABKEY.ext4.MeasuresStore', {});

            if (this.displaySourceCounts === true && this.updateSourceCountsOnLoad === true) {
                this.sourcesStore.on('load', function(){ this.getSourceCounts(); }, this);
            }
        }
        return this.sourcesStore;
    },

    createSourcePanel : function() {

        this.sourcePanel = Ext4.create('Ext.panel.Panel', {
            flex: 1,
            ui: this.ui,
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            cls: this.sourcePanelCls + ' iScroll',
            title : 'Source',
            border: false,
            items: [ this.getSourcesView() ]
        });

        return this.sourcePanel;
    },

    getSourcesView : function() {
        if (!this.sourcesView)
        {
            this.sourcesView = Ext4.create('Ext.view.View', {
                ui: this.ui,
                border: false,
                cls: 'sourcegrid',
                flex: 1,
                height: '100%',
                autoScroll: true,
                store: this.getSourceStore(),
                itemSelector: 'div.itemrow',
                selectedItemCls: 'itemselected',
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<tpl if="variableType ==null && parent[xindex - 2] && parent[xindex - 2].variableType != null">',
                            '<div class="groupheader groupheaderline" style="padding: 8px 6px 4px 6px; color: #808080">' + this.sourceGroupHeader + '</div>',
                        '</tpl>',
                        '<div class="itemrow {[this.renderDisabled(values)]}" style="padding: 3px 6px 4px 6px; cursor: pointer;">{queryLabel:htmlEncode}{[this.renderCount(values)]}</div>',
                    '</tpl>',
                        {
                            renderCount : function(values) {
                                var val = " <span class='maskit'>";
                                if (values['variableType'] == null && Ext4.isNumber(values['sourceCount'])) {
                                    var count = Ext4.util.Format.number(values['sourceCount'], '0,000');
                                    val += "(" + Ext4.util.Format.htmlEncode(count) + ")";
                                }
                                val += "</span>";
                                return val;
                            },
                            renderDisabled : function(values) {
                                var val = "";
                                if (values['variableType'] == null && values['sourceCount'] === 0) {
                                    val = "itemdisabled";
                                }
                                return val;
                            }
                        }
                )
            });

            this.sourcesView.getSelectionModel().on('select', this.onSourceSelect, this);
        }

        return this.sourcesView;
    },

    getSourceCounts : function(altCountsConfig) {
        if (this.displaySourceCounts) {
            this.fireEvent('beforeMeasureSourceCountsLoad', this);

            var store = this.getSourceStore();
            var sources = store.getRange();

            var json = {
                schema: this.sourceCountSchema,
                colName: this.sourceCountIdColumn,
                members: this.sourceCountMemberSet,
                sources: this.sourceCountSourceSet
            };

            if (this.sourceCountSourceSet.length == 0) {
                var name;
                Ext4.each(sources, function(source) {
                    name = source.get('queryName');
                    if (name) {
                        json.sources.push(name);
                    }
                }, this);
            }


            // config param allows query of specific sources/members/colName different from the defaults
            if (altCountsConfig)
            {
                if (!altCountsConfig.colName && !altCountsConfig.members && !altCountsConfig.sources)
                    return;

                json.colName = altCountsConfig.colName;
                json.members = altCountsConfig.members;
                json.sources = altCountsConfig.sources;
            }

            Ext4.Ajax.request({
                url: LABKEY.ActionURL.buildURL('visualization', 'getSourceCounts.api'),
                method: 'POST',
                jsonData: json,
                success: function(response) {
                    var countResponse = Ext4.decode(response.responseText),
                        counts,
                        key;

                    if (Ext4.isObject(countResponse) && Ext4.isDefined(countResponse.counts)) {
                        counts = countResponse.counts;

                        Ext4.each(sources, function(source) {

                            key = source.get('queryName');

                            if (!Ext4.isEmpty(key) && Ext4.isDefined(counts[key])) {
                                source.set('sourceCount', counts[key]);
                            }

                        }, this);
                    }

                    this.fireEvent('measureSourceCountsLoad', this);
                },
                scope: this
            });
        }
    },

    setCountMemberSet : function(memberSet) {
        // getSourceCounts API action will distinquish between null (no filters) vs empty array (no members that fit filters)
        this.sourceCountMemberSet = Ext4.isArray(memberSet) ? memberSet : null;
        this.getSourceCounts();
    },

    getAltSourceCounts : function(config) {
        this.getSourceCounts(config);
    },

    createMeasurePanel : function() {
        // Using a new MeasureStore, but we will only display filtered sets of measures
        this.measuresStore = Ext4.create('LABKEY.ext4.MeasuresStore', {
            groupField: 'recommendedVariableGrouper',
            listeners : {
                measureStoreSorted : function(store) {
                    this.loaded = true;

                    this.fireEvent('measuresStoreLoaded', this);

                    if (this.rendered) {
                        this.getEl().unmask();
                    }
                },
                exception : function(proxy, type, action, options, resp) {
                    LABKEY.Utils.displayAjaxErrorResponse(resp, options);
                    this.getEl().unmask();
                },
                scope : this
            }
        });

        this.measurePanel = Ext4.create('Ext.panel.Panel', {
            flex: 1,
            ui: this.ui,
            layout: {
                type: 'vbox',
                align: 'stretch'
            },
            cls : this.measurePanelCls + ' iScroll',
            title : this.variablePanelHeader,
            border: false,
            items: [ this.getMeasuresGrid() ]
        });

        return this.measurePanel;        
    },

    getMeasuresGrid : function() {
        if (!this.measuresGrid)
        {
            var measuresGridConfig = {
                border: false,
                flex: 1,
                ui: this.ui,
                cls : 'measuresgrid',
                hidden: true, // starts hidden until a source query is chosen
                listeners : {
                    select : this.onMeasureSelect,
                    deselect : this.onMeasureDeselect,
                    scope : this
                }
            };

            if (this.multiSelect)
            {
                this.measuresGrid = Ext4.create('Ext.grid.Panel', Ext4.apply(measuresGridConfig, {
                    store: this.measuresStore,
                    viewConfig : { stripeRows : false },
                    selType: 'checkboxmodel',
                    selModel: {
                        checkOnly: true,
                        checkSelector: 'td.x-grid-cell-row-checker'
                    },
                    requires: ['Ext.grid.feature.Grouping'],
                    features: [{
                        ftype: 'grouping',
                        id: 'measuresGridGrouping',
                        collapsible: false,
                        groupHeaderTpl: new Ext4.XTemplate(
                            '<div class="groupheader groupheaderline" style="padding: 3px 6px 4px 6px; color: #808080;">',
                                '{groupValue:this.renderHeader}',
                            '</div>',
                            {
                                renderHeader : function(value) {
                                    var hdr = value;
                                    if (value === '0') {
                                        hdr = 'Recommended';
                                    }
                                    else if (value === '1') {
                                        hdr = 'Additional';
                                    }
                                    return hdr;
                                }
                            }
                        )
                    }],
                    enableColumnHide: false,
                    enableColumnResize: false,
                    multiSelect: true,
                    bubbleEvents : ['viewready'],
                    columns: [{
                        header: this.measuresAllHeader || 'Select All',
                        dataIndex: 'label',
                        flex: 1,
                        sortable: false,
                        menuDisabled: true
                    }]
                }));

                this.groupingFeature = this.measuresGrid.view.getFeature('measuresGridGrouping');
            }
            else
            {
                this.measuresGrid = Ext4.create('Ext.view.View', Ext4.apply(measuresGridConfig, {
                    height: '100%',
                    autoScroll: true,
                    store: this.measuresStore,
                    itemSelector: 'div.itemrow',
                    selectedItemCls: 'itemselected',
                    tpl: new Ext4.XTemplate(
                        '<tpl for=".">',
                            '<tpl if="isRecommendedVariable && xindex == 1">',
                                '<div class="groupheader" style="padding: 3px 6px 4px 6px; color: #808080">Recommended</div>',
                            '</tpl>',
                            '<tpl if="!isRecommendedVariable && parent[xindex - 2] && parent[xindex - 2].isRecommendedVariable">',
                                '<div class="groupheader groupheaderline" style="padding: 8px 6px 4px 6px; color: #808080">Additional</div>',
                            '</tpl>',
                            '<div class="itemrow" style="padding: 3px 6px 4px 6px; cursor: pointer;">{label:htmlEncode}</div>',
                        '</tpl>'
                    )
                }));
            }

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
        }

        return this.measuresGrid;
    },

    getMeasures : function() {
        this.fireEvent('beforeMeasuresStoreQuery', this);

        var filter = this.filter || LABKEY.Query.Visualization.Filter.create({schemaName: 'study'});

        if (!this.measuresStoreData) {
            LABKEY.Query.Visualization.getMeasures({
                filters      : [filter],
                allColumns   : this.allColumns,
                showHidden   : this.showHidden,
                success      : function(measures, response) {
                    this.measuresStoreData = measures;
                    this.processMeasuresStoreData();
                },
                failure      : function(info, response, options) {
                    this.isLoading = false;
                    LABKEY.Utils.displayAjaxErrorResponse(response, options);
                },
                scope : this
            });
        }
        else {
            this.processMeasuresStoreData();
        }
    },

    processMeasuresStoreData : function() {

        this.isLoading = false;

        this.sourcesStoreKeys = [];
        this.sourcesStoreData = [];

        if (this.supportSelectionGroup === true && this.multiSelect) {
            this.measuresStoreData.push({
                sortOrder: -100,
                schemaName: '_current',
                name: '',
                queryName: null,
                queryLabel: this.supportSelectionLabel,
                queryDescription: this.supportSelectionDescription,
                variableType: 'SELECTION'
            });
        }

        if (this.supportSessionGroup === true && this.multiSelect) {
            this.measuresStoreData.push({
                sortOrder: -99,
                schemaName: '_session',
                name: '',
                queryName: null,
                queryLabel: this.supportSessionLabel,
                queryDescription: this.supportSessionDescription,
                variableType: 'SESSION'
            });
        }

        Ext4.each(this.getAdditionalMeasuresArray(), function(measure) {
            this.measuresStoreData.push(measure);
        }, this);

        // Apply the user filter here. The userFilter is a function used to further filter down the available
        // measures. Needed in CDS so the color picker only displays categorical measures.
        if (this.hasOwnProperty('userFilter') && Ext4.isFunction(this.userFilter))
        {
            try
            {
                this.measuresStoreData = this.measuresStoreData.filter(this.userFilter);
            }
            catch (error)
            {
                // Fail gracefully and dump error into log.
                console.error('Error applying userFilter to measure data.');
                console.error(error);
            }
        }

        Ext4.each(this.measuresStoreData, function(measure) {
            var key = measure.schemaName + "|" + measure.queryName;

            if (this.sourcesStoreKeys.indexOf(key) == -1)
            {
                this.sourcesStoreKeys.push(key);
                this.sourcesStoreData.push({
                    sortOrder: measure.sortOrder,
                    schemaName : measure.schemaName,
                    queryName : measure.queryName,
                    queryLabel : measure.queryLabel,
                    description : measure.queryDescription,
                    variableType : measure.variableType
                });
            }
        }, this);

        this.fireEvent('beforeMeasuresStoreLoad', this, {measures: this.measuresStoreData});

        // Load the full measures list for the measuresStore, but we will only show filtered sets of measures
        this.measuresStore.loadRawData({measures: this.measuresStoreData});

        // Load only the list of queries (i.e. sources) for the souresStore
        this.getSourceStore().loadRawData({measures: this.sourcesStoreData});
    },

    /**
     * An array of measures provided by an overriding class that will be appended to the sources/measures
     * @returns {Array}
     */
    getAdditionalMeasuresArray : function() {
        return [];
    },

    onSourceSelect : function(rowModel, sourceRecord, index) {
        // filter the measure grid based on the selected source query
        this.getEl().mask("filtering measures...", "x-mask-loading");
        this.getMeasuresGrid().getSelectionModel().deselectAll(true);
        this.measuresStore.clearFilter();

        var columns, grid = this.getMeasuresGrid(),
                usingSelectionSource = this.supportSelectionGroup === true && sourceRecord.get('variableType') === 'SELECTION',
                usingSessionSource = this.supportSessionGroup === true && sourceRecord.get('variableType') === 'SESSION';

        if (grid && this.multiSelect && Ext4.isDefined(grid.columnManager)) {
            columns = grid.columnManager.getColumns();
        }

        //
        // There are three different types of sources to choose from:
        // 1. Selection Source - Show the user the set of measures they currently have selected
        // 2. Session Source - Any measures that have been selected in the app.
        // 3. Data Source - Any measure that comes from a standard query source.
        //
        if (usingSelectionSource) {

            // Update the column header for 'Select All'
            if (Ext4.isArray(columns)) {
                Ext4.each(columns, function(col) {
                    if (col.dataIndex === 'label') {
                        col.setText('Select All');
                        return false;
                    }
                });
            }


            var ids = {};
            Ext4.each(this.selectedMeasures, function(sm) {
                if (Ext4.isDefined(sm.id))
                {
                    ids[sm.id] = true;
                }
            });

            this.measuresStore.filter({
                filterFn: function(measureRecord) {
                    return Ext4.isDefined(measureRecord.id) && ids[measureRecord.id] === true;
                },
                scope: this
            });
        }
        else if (usingSessionSource && Ext4.isFunction(this.getSessionMeasures)) {

            // Update the column header for 'Select All'
            if (Ext4.isArray(columns)) {
                Ext4.each(columns, function(col) {
                    if (col.dataIndex === 'label') {
                        col.setText('Select All');
                        return false;
                    }
                });
            }

            var sessionIDs = {};
            var sessionMeasures = this.getSessionMeasures.call(this);
            Ext4.each(sessionMeasures, function(sm) {
                if (Ext4.isString(sm.alias))
                {
                    sessionIDs[sm.alias] = true;
                }
            });

            this.measuresStore.filter({
                filterFn: function(measureRecord) {
                    return Ext4.isString(measureRecord.data.alias) && sessionIDs[measureRecord.data.alias] === true;
                },
                scope: this
            });
        }
        else
        {
            if (Ext4.isArray(columns)) {
                Ext4.each(columns, function(col) {
                    if (col.dataIndex === 'label') {
                        col.setText(this.measuresAllHeader);
                        return false;
                    }
                }, this);
            }

            this.measuresStore.filter([
                {
                    filterFn: function (measureRecord)
                    {
                        return (sourceRecord.get("schemaName") == measureRecord.get("schemaName")
                                && sourceRecord.get("queryName") == measureRecord.get("queryName"));
                    },
                    scope: this
                }
            ]);
        }

        // since selections aren't remembered after filters are applied, reselect any of the selected measure that are visible for this filter
        Ext4.each(this.selectedMeasures, function(measure) {
            if (this.measuresStore.findExact('id', measure.get('id')) > -1)
                this.getMeasuresGrid().getSelectionModel().select(measure, true, true);
        }, this);

        this.getEl().unmask();

        // apply grouping
        if (this.groupingFeature) {
            if (usingSelectionSource || usingSessionSource) {
                // Always enable grouping if available and change to 'queryLabel' based grouping
                this.groupingFeature.enable();
                this.measuresStore.groupers.first().property = "queryLabel";
                this.measuresStore.group();
            }
            else {
                // enable or disable the measure grid grouping feature based on the presence of a recommended variable
                if (this.measuresStore.find('isRecommendedVariable', true) > -1) {
                    this.groupingFeature.enable();
                    this.measuresStore.groupers.first().property = "recommendedVariableGrouper";
                    this.measuresStore.group();
                    this.measuresStore.sort('recommendedVariableGrouper', 'ASC');
                }
                else {
                    this.groupingFeature.disable();
                }
            }
        }

        // show the grid
        this.getMeasuresGrid().show();
    },

    onMeasureSelect : function(selModel, record, ix) {
        var index = this.getSelectedRecordIndex(record);
        if (index == -1 && ix != -1)
        {
            if (!this.multiSelect && this.selectedMeasures.length > 0) {
                this.selectedMeasures = [];
            }

            // TODO: issue with switching between a source w/ 2 groups (Recommended and Additional) to 1 group (just Additional)
            // JS error message : Uncaught TypeError: Cannot read property 'setDirty' of undefined
            if (record.store.groups && record.store.groups.keys.length > 1)
            {
                record.commit(); // to remove the dirty state
            }

            this.selectedMeasures.push(record);

            this.updateSourcesSelectionEntry(record, 1);
        }
    },

    onMeasureDeselect : function(selModel, record, ix) {
        var index = this.getSelectedRecordIndex(record);
        if (index > -1 && ix != -1)
        {
            // TODO: issue with switching between a source w/ 2 groups (Recommended and Additional) to 1 group (just Additional)
            // JS error message : Uncaught TypeError: Cannot read property 'setDirty' of undefined
            if (record.store.groups && record.store.groups.keys.length > 1)
            {
                record.commit(); // to remove the dirty state
            }

            this.selectedMeasures.splice(index, 1);

            this.updateSourcesSelectionEntry(record, -1);
        }
    },

    updateSourcesSelectionEntry : function(record, sourceCountUpdate) {

        // update the numSelected value for the source entry
        if (this.trackSelectionCount)
        {
            var sourceStore = this.getSourceStore();
            var sourceEntryIndex = sourceStore.findExact('queryName', record.get('queryName'));
            if (sourceEntryIndex > -1)
            {
                var sourceEntry = sourceStore.getAt(sourceEntryIndex);
                if (!sourceEntry.get('numSelected'))
                {
                    sourceEntry.set('numSelected', 0);
                }

                sourceEntry.set('numSelected', sourceEntry.get('numSelected') + sourceCountUpdate);
                sourceEntry.commit(); // to remove the dirty state
            }
        }

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
    },

    setSelectedRecord : function(measure) {
        var sourceStore = this.getSourcesView().getStore();
        var index = sourceStore.findBy(function(rec){
            return rec.get('schemaName') == measure.get('schemaName') && rec.get('queryName') == measure.get('queryName');
        });
        if (index > -1) {
            this.getSourcesView().getSelectionModel().select(sourceStore.getAt(index));
        }

        this.getMeasuresGrid().getSelectionModel().select(measure);
    },

    clearSelection : function() {
        this.getMeasuresGrid().getSelectionModel().deselectAll();
    }
});

Ext4.define('LABKEY.ext4.Measure', {
    extend : 'Ext.data.Model',
    idProperty : 'alias', // default to alias, this can be overridden by the stores proxy/reader
    fields : [
        {name : 'id'},
        {name : 'alias'},
        {name : 'name'},
        {name : 'label'},
        {name : 'description'},
        {name : 'isUserDefined'},
        {name : 'isMeasure', defaultValue: false},
        {name : 'isDimension', defaultValue: false},
        {name : 'isDemographic', defaultValue: false},
        {name : 'phi'},
        {name : 'inNotNullSet', defaultValue: undefined},
        {name : 'hidden', defaultValue: false},
        {name : 'queryLabel'},
        {name : 'queryName'},
        {name : 'schemaName'},
        {name : 'lookup', defaultValue: {}},
        {name : 'type'},
        {name : 'isRecommendedVariable', type: 'boolean', defaultValue: false},
        {name : 'recommendedVariableGrouper', convert: function(val, rec){ return rec.data.isRecommendedVariable ? '0' : '1'; }},
        {name : 'defaultScale'},
        {name : 'sortOrder', defaultValue: 0},
        {name : 'variableType', defaultValue: null}, // i.e. TIME, USER_GROUPS (default to null for query based variables)
        {name : 'queryType', defaultValue: null}, // see LABKEY.Query.Visualization.Filter.QueryType
        {name : 'sourceCount', defaultValue: undefined},
        {name : 'uniqueKeys', defaultValue: null}
    ]
});

Ext4.define('LABKEY.ext4.MeasuresStore', {

    extend: 'Ext.data.Store',

    constructor : function(config) {

        Ext4.apply(this, config, {
            autoLoad: false,
            model: 'LABKEY.ext4.Measure',
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
            var sortArr = [
                {property: 'sortOrder'},
                {property: 'schemaName'},
                {property: 'queryLabel'},
                {property: 'isRecommendedVariable', direction: 'DESC'},
                {property: 'label'}
            ];

            store.sort(sortArr);
            store.fireEvent("measureStoreSorted", store);
        });
    }
});
