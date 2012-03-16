/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.MeasuresDialog = Ext.extend(Ext.Window, {

    constructor : function(config){

        this.addEvents(
            'measuresSelected'
        );

        Ext.apply(this, config, {
            cls: 'extContainer',
            title: 'Add Measure...',
            layout:'fit',
            width:800,
            height:550,
            modal: true,
            multiSelect : false
        });

        Ext.applyIf(this, config, {
            forceQuery : false
        });

        LABKEY.vis.MeasuresDialog.superclass.constructor.call(this, config);
    },

    initComponent : function() {

        this.buttons = [];
        this.items = [];
        this.measureSelectionBtnId = Ext.id();

        this.measurePanel = new LABKEY.vis.MeasuresPanel({
            axis: [{
                multiSelect: false,
                name: "y-axis",
                label: "Choose a data measure"
            }],
            filter      : this.filter,
            allColumns  : this.allColumns,
            multiSelect : this.multiSelect,
            forceQuery  : this.forceQuery,
            bubbleEvents: ['beforeMeasuresStoreLoad', 'measuresStoreLoaded'],
            listeners: {
                scope: this,
                'measureChanged': function (axisId, data) {
                    Ext.getCmp(this.measureSelectionBtnId).setDisabled(false);
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

        LABKEY.vis.MeasuresDialog.superclass.initComponent.call(this);
    }
});

LABKEY.vis.MeasuresPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){

        // keep the list of toolbar actions and selections
        this.tbarActions = [];
        this.axisMap = {};              // map of name to axis info
        this.addEvents(
                'beforeMeasuresStoreLoad',
                'measuresStoreLoaded',
                'measureChanged'
        );
        Ext.apply(this, config, {isDateAxis : false, allColumns : false});

        LABKEY.vis.MeasuresPanel.superclass.constructor.call(this, config);

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
        this.on('afterrender', this.onAfterRender, this);
        if (this.forceQuery)
            this.onAfterRender(this);

        // Show the mask after the component size has been determined, as long as the
        // data is still loading:
        this.on('afterlayout', function() {
            if (!this.loaded)
                this.getEl().mask("loading measures...", "x-mask-loading");
        });

        LABKEY.vis.MeasuresPanel.superclass.initComponent.call(this);
    },

    onAfterRender : function(cmp) {
        var filter = this.filter || LABKEY.Visualization.Filter.create({schemaName: 'study'});

        if (this.selectedMeasure)
        {
            filter = LABKEY.Visualization.Filter.create({schemaName: this.selectedMeasure.schemaName,
                queryName: this.selectedMeasure.queryName});
        }

        // if the measure store data is not already loaded, get it. otherwise, use the cached data object
        if (!this.measuresStoreData)
        {
            if (!this.isLoading) {
                this.isLoading = true;
                LABKEY.Visualization.getMeasures({
                    filters      : [filter],
                    dateMeasures : this.isDateAxis,
                    allColumns   : this.allColumns,
                    success      : function(measures, response){
                        this.isLoading = false;
                        this.measuresStoreData = Ext.util.JSON.decode(response.responseText);
                        if(this.hideDemographicMeasures){
                            // Remove demographic measures in some cases (i.e. time charts).
                            for(var i = this.measuresStoreData.measures.length; i--;){
                                if(this.measuresStoreData.measures[i].isDemographic === true){
                                    this.measuresStoreData.measures.splice(i, 1);
                                }
                            }
                        }
                        this.fireEvent('beforeMeasuresStoreLoad', this, this.measuresStoreData);
                        this.measuresStore.loadData(this.measuresStoreData);
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
                this.measuresStore.loadData(this.measuresStoreData);
        }
    },

    createMeasuresListPanel : function() {

        // define a store to wrap the data measures
        this.measuresStore = new Ext.data.Store({
            autoLoad: false,
            reader: new Ext.data.JsonReader({
                root:'measures', idProperty:'id'},[
                    {name   : 'id'},
                    {name   : 'name'},
                    {name   : 'label'},
                    {name   : 'description'},
                    {name   : 'isUserDefined'},
                    {name   : 'isDemographic'}, 
                    {name   : 'queryName'},
                    {name   : 'schemaName'},
                    {name   :'type'}]
            ),
            remoteSort: false,
            listeners : {
                load : function(store) {

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

                    store.sort([{field: 'queryName', direction: 'ASC'},{field: 'label', direction: 'ASC'}]);
                    this.fireEvent('measuresStoreLoaded', this);
                },
                exception : function(proxy, type, action, options, resp) {
                    LABKEY.Utils.displayAjaxErrorResponse(resp, options);
                    this.getEl().unmask();
                },
                scope : this
            }
        });

        if (this.multiSelect) {

            this.selModel = new Ext.grid.CheckboxSelectionModel();
            this.view = new Ext.grid.GridPanel({
                store: this.measuresStore,
                flex: 1,
                stripeRows : true,
                selModel : this.selModel,
                viewConfig : {forceFit: true},
                bubbleEvents : ['viewready'],
                columns: [
                    this.selModel,
                    {header:'Dataset', dataIndex:'queryName'},
                    {header:'Measure', dataIndex:'label'},
                    {header:'Description', dataIndex:'description', cls : 'normal-wrap'}
                ]
            });
        }
        else {

            this.view = new Ext.list.ListView({
                store: this.measuresStore,
                flex: 1,
                singleSelect: true,
                columns: [
                    {header:'Dataset', dataIndex:'queryName', width: .4},
                    {header:'Measure', dataIndex:'label', width:.25},
                    {header:'Description', dataIndex:'description', cls : 'normal-wrap'}
                ]
            });
            this.selModel = this.view;
        }

        // enable disable toolbar actions on selection change
        this.selModel.on('selectionchange', this.onListViewSelectionChanged, this);

        var tbarItems = [{xtype:'tbspacer'}];

        this.searchDisplayField = new Ext.form.DisplayField({
            width: 30,
            value: "Filter: "
        });

        tbarItems.push(this.searchDisplayField);
        tbarItems.push({xtype:'tbspacer'});

        this.searchBox = new Ext.form.TextField({
            width: 200,
            enableKeyEvents: true,
            emptyText : 'Search',
            listeners : {
                // filter the listview using both the label and category names
                keyup : function(cmp, e){ this.filterMeasures(cmp.getValue()); },
                scope : this
            }
        });



        tbarItems.push(this.searchBox);

        this.errorField = new Ext.form.DisplayField({
            hideLabel: true,
            style: "color: red;",
            value: ''
        });
        tbarItems.push({xtype:'tbspacer'});
        tbarItems.push(this.errorField);

        // create a toolbar button for each of the axis types
        if (this.hasBtnSelection) {

            for (var i=0; i < this.axis.length; i++)
            {
                var axis = this.axis[i];

                if (this.axisMap[axis.name]) {
                    var action = new Ext.Action({iconCls: 'iconUp', text: 'Add to ' + axis.name,
                        handler: this.onListViewBtnClicked,
                        scope: this, disabled: true,
                        tooltip: 'Adds the selected measurement into the axis field on the right',
                        labelId: this.axisMap[axis.name].labelId, axisId: axis.name});
                    this.tbarActions.push(action);
                    tbarItems.push(action);
                }
            }
        }

        var items = [];
        items.push(new Ext.Toolbar({
            style : 'padding: 5px 2px',
            items: tbarItems
        }));
        items.push(this.view);

        var panel = new Ext.Panel({
            region: 'center',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        return panel;
    },

    filterMeasures : function (txt) {
        if (txt) {
           //NOTE: this attempts to balance the need for flexible searching (ie. partial words, random ordering of terms)
            // and the need to get a reasonably small set of results.  the code should:
            //
            // 1) remove/ignore punctuation from search term
            // 2) split term on whitespace
            // 3) return any record where ALL tokens appear at least once in any of the fields.  order does not matter.  the token must begin on a word boundary

            txt = txt.replace(/[^a-z0-9 ]+/gi, ' ');
            txt = Ext.util.Format.trim(txt);
            txt = Ext.escapeRe(txt);

            var tokens = txt.split(/\s/g);
            var matches = [];
            for(var i=0;i<tokens.length;i++){
                matches.push(new RegExp('\\b' + tokens[i], 'i'));
            }

            //NOTE: if ever split into a standalone component, we would want a config option specifying these fields
            var fields = ['queryName', 'label', 'description'];

            this.measuresStore.filter([{
                fn: function(record){
                    // for multi-select don't clear selections on filter
                    if (this.selModel.isSelected(record))
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
            this.errorField.setValue("No results found for current filter.");
        } else {
            this.errorField.setValue("");
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
            var textField = Ext.getCmp(this.axisMap[axisId].labelId);
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

            var field = new Ext.form.DisplayField({
                width:400,
                hideLabel: true
            });

            var labelField = new Ext.form.DisplayField({
                width:200,
                value: axis.label + ":",
                hideLabel: true
            });

            var compositeField = new Ext.form.CompositeField({
                width: 555,
                hideLabel: true,
                items: [
                        labelField, field
                ]
            });

            // stash the textfield id so we can update it later from the listview
            this.axisMap[axis.name] = {labelId: field.id};
            this.axisId = axis.name;
            items.push(compositeField);
        }

        // if we have more than one axis, use a tbar button selection model
        this.hasBtnSelection = items.length > 1;

        var panel = new Ext.form.FormPanel({
            labelWidth: 175,
            bodyStyle:'padding:25px;',
            region: 'north',
            items: items
        });

        return panel;
    },

    getSelectionModel : function() {
        return this.selModel;
    },

    getSelectedRecords : function() {
        if (this.multiSelect)
            return this.selModel.getSelections();
        else
            return this.selModel.getSelectedRecords();
    }
});

LABKEY.vis.ZeroDatePanel = Ext.extend(LABKEY.vis.MeasuresPanel, {

    constructor : function(config) {

        Ext.apply(config, {storeUrl: LABKEY.ActionURL.buildURL("visualization", "getZeroDate")});
        LABKEY.vis.ZeroDatePanel.superclass.constructor.call(this, config);

        this.addEvents(
            'timeOptionChanged'
        );
    },

    createMeasuresFormPanel : function() {

        var items = [];

        var field = new Ext.form.TextField({disabled: true,
            emptyText:'select from the list view',
            width:500,
            fieldLabel: 'Measure time from'
        });

        // stash the textfield id so we can update it later from the listview
        this.axisMap['zeroDate'] = {labelId: field.id};
        this.axisId = 'zeroDate';

        items.push(field);

        var dateIntervals = new Ext.data.ArrayStore({
            fields: ['id','value'],
            data: [
                ['days', 'Days'],
                ['weeks', 'Weeks'],
                ['months', 'Months'],
                ['years', 'Years']]
        });

        var dateCombo = new Ext.form.ComboBox({
            fieldLabel: 'Display time progression as:',
            labelSeparator: '',
            store: dateIntervals,
            forceSelection:true,
            triggerAction:'all',
            value: 'days',
            allowBlank: false,
            valueField:'id',
            displayField:'value',
            mode:'local',
            axisId: 'zeroDate',
            emptyText:'Choose a interval value...'
        });
        dateCombo.on('change', function(cmp, newVal, oldVal){
            this.fireEvent('timeOptionChanged', cmp.initialConfig.axisId, newVal);
        }, this);

        items.push(dateCombo);

        var panel = new Ext.form.FormPanel({
            labelWidth: 200,
            bodyStyle:'padding:25px;',
            region: 'north',
            items: items
        });

        return panel;
    }
});

