/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresVisualization();
LABKEY.requiresScript("vis/measuresPanel.js");
LABKEY.requiresScript("vis/ChartPanel.js");

Ext.QuickTips.init();

var panels = {
    TYPES : 'types',
    MEASURES : 'measures',
    MEASURES_DATE : 'measuresDate',
    MEASURES_ZERO_DATE : 'measuresZeroDate',
    CATEGORIES : 'categories',
    DIMENSIONS : 'dimensions',
    PARTICIPANTS : 'participants',
    CHART : 'chart'
};

LABKEY.vis.VisualizationWizard = Ext.extend(Ext.Panel, {

    viewTypes : {},                 // map of view type ids to info object
    panelMap : {},                  // map of types to panel config
    view : {},                      // the current visualization object
    wizardSteps: [],

    constructor : function(config){

        LABKEY.vis.VisualizationWizard.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },

    initComponent : function() {
        this.layout = 'border';
        this.border = false;
        this.frame  = false;

        var steps = [];

        // type panel
        var typePanel = this.createWizardStepPanel({title: 'Step 1 : Report Type',
            step: panels.TYPES,
            nextBtn: {text: 'Next', disabled: true, handler: this.onNextStep, scope: this}});
        steps.push(typePanel);

        this.wizardSteps.push({type: panels.TYPES, panel: typePanel});

        this.viewPanel = new Ext.TabPanel({
            enableTabScroll: true,
            region: 'center',
            activeTab: typePanel.id,
            items: steps
        });

        // make all non-active tabs disabled (force navigation through the buttons)
        this.viewPanel.on('beforetabchange', function(ths, newTab, oldTab){
            if (newTab) newTab.enable();
            if (oldTab) oldTab.disable();
        }, this);

        this.items = [this.viewPanel];

        LABKEY.vis.VisualizationWizard.superclass.initComponent.call(this);
    },

    onNextStep : function()
    {
        var stepNum = this.wizardSteps.length + 1;
        var curStep = this.wizardSteps[this.wizardSteps.length - 1];
        var nextStep;
        var stepHandler;

        switch (curStep.type) {
            case panels.TYPES:
                nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Numeric Data',
                    step: panels.MEASURES,
                    prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                    nextBtn: {text:'Next', disabled:true, handler: this.onNextStep, scope: this}});
                stepHandler = this.getMeasures;
                break;

            case panels.MEASURES:
            case panels.MEASURES_ZERO_DATE:
                if (this.view.hasTimeAxis && curStep.type == panels.MEASURES)
                {
                    // optional date column selection
                    nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Measurement Date',
                        step: panels.MEASURES_DATE,
                        prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                        nextBtn: {text:'Next', disabled:true, handler: this.onNextStep, scope: this}});
                    stepHandler = this.getMeasuresDate;
                }
                else
                {
                    // categories panel
                    nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Series',
                        step: panels.CATEGORIES,
                        prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                        nextBtn: {text:'Next', handler: this.onNextStep, scope: this}});
                    stepHandler = this.getCategories;
                }
                break;

            case panels.MEASURES_DATE:
                // optional zero date column selection
                nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Start Date',
                    step: panels.MEASURES_ZERO_DATE,
                    prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                    nextBtn: {text: 'Next', disabled:true, handler: this.onNextStep, scope: this}});
                stepHandler = this.getZeroDate;
                break;

            case panels.CATEGORIES:
            case panels.DIMENSIONS:
            case panels.PARTICIPANTS:
                var hasDimensions = false;
                var hasPtids = false;

                for (var a in this.view.dimensions)
                {
                    var dims = this.view.dimensions[a];
                    if (dims && dims.length > 0) {
                        hasDimensions = true;
                        break;
                    }
                }

                for (var b in this.view.ptids)
                {
                    if ('object' == typeof this.view.ptids[b]) {
                        hasPtids = true;
                        break;
                    }
                }

                if (hasDimensions && curStep.type == panels.CATEGORIES)
                {
                    // dimensions panel
                    nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Series Filter',
                        step: panels.DIMENSIONS,
                        prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                        nextBtn: {text: 'Next', disabled:true, handler: this.onNextStep, scope: this}});
                    stepHandler = this.getDimensions;
                }
                else if (hasPtids && curStep.type != panels.PARTICIPANTS)
                {
                    // ptid filtering
                    nextStep = this.createWizardStepPanel({title: 'Step ' + stepNum + ' : Subject Filter',
                        step: panels.PARTICIPANTS,
                        prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                        nextBtn: {text: 'Next', disabled:true, handler: this.onNextStep, scope: this}});
                    stepHandler = this.getParticipants;
                }
                else
                {
                    // visualization
                    nextStep = this.createWizardStepPanel({title: 'Visualization',
                        step: panels.CHART,
                        prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this}});
                    stepHandler = this.showVisualization;
                }
                break;
        }

        if (nextStep)
        {
            this.wizardSteps.push({type: nextStep.initialConfig.step, panel: nextStep});
            this.viewPanel.add(nextStep);

            stepHandler.call(this);
        }
    },

    onPreviousStep : function()
    {
        var prevStep = this.wizardSteps.pop();
        var curStep = this.wizardSteps[this.wizardSteps.length - 1];

        if (curStep)
            this.viewPanel.activate(curStep.panel);
        if (prevStep)
            this.viewPanel.remove(prevStep.panel.getId());
    },

    onNewVisualization : function()
    {
        // just delete all but the first panel
        while (this.wizardSteps.length > 1)
        {
            var step = this.wizardSteps.pop();
            this.viewPanel.remove(step.panel.getId());
        }
    },

    onRender : function()
    {
        LABKEY.vis.VisualizationWizard.superclass.onRender.apply(this, arguments);

        if (this.resizable)
        {
            this.resizer = new Ext.Resizable(this.el, {pinned: false});
            this.resizer.on("resize", function(o, width, height){
                this.setWidth(width);
                this.setHeight(height)
            }, this);
        }

        //this.viewPanel.hideTabStripItem(this.panelMap[panels.MEASURES_DATE].panel);

        // get the type information from the server
        LABKEY.Visualization.getTypes({
            successCallback : this.renderTypePanel,
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    /**
     * Creates a generic panel to represent a step in the wizard
     * @param config
     */
    createWizardStepPanel : function(config) {

        var info = {};
        var buttons = [];

        if (config.prevBtn) {
            info.prevBtn = new Ext.Button(config.prevBtn);
            buttons.push(info.prevBtn);
        }

        if (config.nextBtn) {
            info.nextBtn = new Ext.Button(config.nextBtn);
            buttons.push(info.nextBtn);
        }

        // new chart button
        if (this.wizardSteps.length > 0)
            buttons.push({text:'New Visualization', handler: this.onNewVisualization, scope:this});

        info.panel = new Ext.Panel({
            title: config.title,
            bodyStyle:'padding:25px;',
            border:false,
            step:config.step,
            //disabled: true,
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            footerStyle: 'padding:5px;',
            buttonAlign:'right',
            buttons: buttons
        });

        info.id = config.step;
        this.panelMap[config.step] = info;
        return info.panel;
    },

    renderTypePanel : function(types) {
//        this.viewPanel.hideTabStripItem(this.panelMap[panels.MEASURES_DATE].panel);
//        this.viewPanel.hideTabStripItem(this.panelMap[panels.MEASURES_ZERO_DATE].panel);
        this.view.hasTimeAxis = false;

        var info = this.panelMap[panels.TYPES];
        if (info.panel && types && types.length)
        {
            var items = [];
            var tpl = new Ext.XTemplate('{label}<br><img src="{icon}" height="100" width="100"/>').compile();

            this.viewTypes = {};
            for (var i=0; i < types.length; i++)
            {
                var type = types[i];
                this.viewTypes[type.type] = type;
                items.push({boxLabel: tpl.apply(type), name:'type', inputValue: type.type, disabled: !type.enabled, scope: this,
                    handler:function(cmp, checked){
                        if (checked)
                        {
                            this.view.type = cmp.getRawValue();
                            this.view.measures = {};
                            this.view.dateOptions = {};
                            this.view.dimensions = {};
                            this.view.dimensionValues = {};

                            if (this.hasTimeAxis(this.viewTypes[this.view.type]))
                            {
//                                this.viewPanel.unhideTabStripItem(this.panelMap[panels.MEASURES_DATE].panel);
//                                this.viewPanel.unhideTabStripItem(this.panelMap[panels.MEASURES_ZERO_DATE].panel);
                                this.view.hasTimeAxis = true;
                            }
                            else
                            {
//                                this.viewPanel.hideTabStripItem(this.panelMap[panels.MEASURES_DATE].panel);
//                                this.viewPanel.hideTabStripItem(this.panelMap[panels.MEASURES_ZERO_DATE].panel);
                                this.view.hasTimeAxis = false;
                            }
                        }
                        info.nextBtn.enable();}
                });
            }

            var typeGroup = new Ext.form.RadioGroup({
                labelSeparator: '',
                columns:3,
                items: items
            });

            info.panel.add({xtype:'label', text: 'Select the report type.', cls:'labkey-header-large'});
            info.panel.add(typeGroup);
            info.panel.doLayout();
        }
    },

    hasTimeAxis : function(typeInfo) {
        for (var i=0; i < typeInfo.axis.length; i++)
        {
            var axis = typeInfo.axis[i];
            if (axis.timeAxis)
                return true;
        }
        return false;
    },

    getMeasures : function() {
        var info = this.panelMap[panels.MEASURES];

        this.viewPanel.activate(info.panel);
        var viewInfo = this.viewTypes[this.view.type];

        if (viewInfo)
        {
            var panel = new LABKEY.vis.MeasuresPanel({
                axis: viewInfo.axis,
                info: info
            });

            panel.on('measureChanged', function(axisId, data){
                this.view.measures[axisId] = data;
                info.nextBtn.setDisabled(false);
            }, this);
        }
        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Select the report\'s numeric data.', cls:'labkey-header-large', height: 50}, panel]);

        info.panel.doLayout();
    },

    getMeasuresDate : function() {
        var info = this.panelMap[panels.MEASURES_DATE];

        this.viewPanel.activate(info.panel);
        var viewInfo = this.viewTypes[this.view.type];

        if (viewInfo)
        {
            var panel = new LABKEY.vis.MeasuresPanel({
                axis: viewInfo.axis,
                info: info,
                measures : this.view.measures,
                isDateAxis: true
            });

            panel.on('measureChanged', function(axisId, data){
                this.view.measures[axisId] = data;
                info.nextBtn.setDisabled(false);
            }, this);
        }
        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Select the measurement date.', cls:'labkey-header-large', height: 50}, panel]);

        info.panel.doLayout();
    },

    getZeroDate : function(config) {
        var info = this.panelMap[panels.MEASURES_ZERO_DATE];

        this.viewPanel.activate(info.panel);
        this.view.dateOptions.interval = 'days';

        var viewInfo = this.viewTypes[this.view.type];

        if (viewInfo)
        {
            var panel = new LABKEY.vis.ZeroDatePanel({
                viewInfo: this.viewTypes[this.view.type],
                axis: [{name:'zeroDate'}],
                info: info
            });

            panel.on('measureChanged', function(axisId, data){
                this.view.dateOptions['zeroDateCol'] = data;
                info.nextBtn.setDisabled(false);
            }, this);

            panel.on('timeOptionChanged', function(axisId, newVal) {
                this.view.dateOptions.interval = newVal;
            }, this);
        }

        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Choose the start of the time progression.', cls:'labkey-header-large', height: 50}, panel]);

        info.panel.doLayout();
    },

    getCategories : function() {

        var info = this.panelMap[panels.CATEGORIES];
        this.viewPanel.activate(info.panel);

        // used to track participant ID columns for separate filtering
        this.view.ptids = {};

        // get the selected measures and create elements for their categories
        if (this.viewTypes[this.view.type])
        {
            this.view.dimensions = {};   // map of axis name to selected dimension
            var typeInfo = this.viewTypes[this.view.type];
            var items = [];

            for (var i=0; i < typeInfo.axis.length; i++)
            {
                var axis = typeInfo.axis[i];

                // doesn't make sense to group on a time axis
                if (axis.timeAxis)
                    continue;

                var selectedMeasure = this.view.measures[axis.name];
                if (selectedMeasure)
                {
                    // uncomment to include demographic data (will probably need to change from a combo box to a list view
                    // to display all of the measures
                    //selectedMeasure.includeDemographics = true;

                    // define a store to wrap the data dimension
                    var store = new Ext.data.Store({
                        autoLoad: true,
                        axisId: axis.name,
                        reader: new Ext.data.JsonReader({
                            root:'dimensions'},
                            [{name:'name'},{name:'label'},{name:'description'}, {name:'isUserDefined'}, {name:'queryName'}, {name:'schemaName'}, {name:'type'}]
                        ),
                        proxy: new Ext.data.HttpProxy({
                            method: 'GET',
                            url : LABKEY.ActionURL.buildURL("visualization", "getDimensions", null, selectedMeasure)}),
                        remoteSort: false,
                        sortInfo: {
                            field: 'label',
                            direction: 'ASC'
                        }
                    });

                    store.on('load', function(cmp){
                        this.panelMap[panels.CATEGORIES].panel.getEl().unmask();
                        cmp.filter([{
                            fn: function(record){
                                    var tester = new RegExp(Ext.escapeRe(typeInfo.subjectColumn), 'i');

                                    // filter out all participant ID columns, but keep track of them for later use
                                    if (tester.test(record.data.name))
                                    {
                                        var axisId = record.store.axisId;
                                        if (axisId)
                                            this.view.ptids[axisId] = record.data
                                        return false;
                                    }
                                    else
                                        return true;
                            },
                            scope:this
                        }]);
                    }, this);

                    var listView = new Ext.list.ListView({
                        store: store,
                        border: true,
                        axisId: axis.name,
                        singleSelect: true,
                        //multiSelect: true,
                        //simpleSelect: true,
                        columns: [
                            {header:'name', dataIndex:'label'},
                            {header:'category', dataIndex:'queryName'}
                        ]
                    });

                    // stash away the selections
                    listView.on('selectionChange', function(cmp, selections){

                        var axisId = cmp.initialConfig.axisId;
                        var dimensions = [];
                        var records = cmp.getRecords(selections);

                        for (var i=0; i < records.length; i++)
                            dimensions.push(records[i].data);

                        this.view.dimensions[axisId] = dimensions;

                    }, this);

                    items.push(new Ext.Panel({
                        fieldLabel: axis.name + ' : (' + selectedMeasure.queryName + ' : ' + selectedMeasure.label + ')',
                        layout: 'fit',
                        height: 150,
                        items:[listView]}));
                }
            }
        }
        var panel = new Ext.form.FormPanel({
            border: false,
            flex: 1,
            labelWidth: 350,
            items: items
        });

        panel.on('afterrender', function(cmp){
            this.panelMap[panels.CATEGORIES].panel.getEl().mask("loading type data...", "x-mask-loading");
        }, this);

        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Optionally select an identifier to split the results into series.', cls:'labkey-header-large', height: 50}, panel]);
        info.panel.doLayout();
    },

    getDimensions : function() {

        var info = this.panelMap[panels.DIMENSIONS];

        this.viewPanel.activate(info.panel);

        // get the selected measures and create elements for their categories
        if (this.viewTypes[this.view.type])
        {
            this.view.dimensionValues = {};   // map of axis name to selected dimension
            var typeInfo = this.viewTypes[this.view.type];
            var items = [];

            for (var i=0; i < typeInfo.axis.length; i++)
            {
                var axis = typeInfo.axis[i];

                var selectedDimensions = this.view.dimensions[axis.name];
                if (selectedDimensions && selectedDimensions.length)
                {

                    for (var j=0; j < selectedDimensions.length; j++)
                    {
                        var selectedDimension = selectedDimensions[j];

                        // define a store to wrap the data dimension
                        var store = new Ext.data.Store({
                            autoLoad: true,
                            reader: new Ext.data.JsonReader({
                                root:'values'},
                                [{name:'value'}]
                            ),
                            proxy: new Ext.data.HttpProxy({
                                method: 'GET',
                                url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, selectedDimension)})
                        });
                        store.on('load', function(cmp){
                            this.panelMap[panels.DIMENSIONS].panel.getEl().unmask();
                        }, this);

                        var listView = new Ext.list.ListView({
                            store: store,
                            //hideHeaders: true,
                            flex: 1,
                            border: true,
                            axisId: axis.name,
                            multiSelect: true,
                            simpleSelect: true,
                            columns: [
                                {header:selectedDimension.queryName + ' : ' + selectedDimension.label, dataIndex:'value'}
                            ]
                        });

                        // stash away the selections
                        listView.on('selectionChange', function(cmp, selections){

                            var info = this.panelMap[panels.DIMENSIONS];
                            var disabled = selections.length == 0;

                            var axisId = cmp.initialConfig.axisId;

                            info.nextBtn.setDisabled(disabled);
                            this.view.dimensionValues[axisId] = cmp.getRecords(selections);

                        }, this);

                        items.push(new Ext.Panel({
                            title: axis.name,
                            layout: 'fit',
                            flex: 1,
                            //border: false,
                            items: [listView]
                        }));
                    }
                }
            }
        }
        var panel = new Ext.Panel({
            layout: 'hbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            border: false,
            flex: 1,
            items: items
        });
        panel.on('afterrender', function(cmp){
            this.panelMap[panels.DIMENSIONS].panel.getEl().mask("loading type data...", "x-mask-loading");
        }, this);

        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Select the series to include.  Multiple selections are allowed.', cls:'labkey-header-large', height: 50}, panel]);
        info.panel.doLayout();
    },

    getParticipants : function() {

        var info = this.panelMap[panels.PARTICIPANTS];

        this.viewPanel.activate(info.panel);

        // get the selected measures and create elements for their categories
        if (this.viewTypes[this.view.type])
        {
            this.view.participantValues = [];   // array of selected ptids
            var typeInfo = this.viewTypes[this.view.type];
            var items = [];

            var ptidsToQuery = [];
            var ptidStore = new Ext.data.ArrayStore({fields: [{name: 'value'}]});

            for (var i=0; i < typeInfo.axis.length; i++)
            {
                var axis = typeInfo.axis[i];

                var ptidColumn = this.view.ptids[axis.name];
                if (ptidColumn)
                {
                    ptidsToQuery.push(ptidColumn);
                }
            }

            if (ptidsToQuery.length > 0)
            {
                // we only want to show a single list of participants to filter on but we want to build that list
                // by getting values from all participant columns selected and find the intersection of values
                // from each query

                for (i=0; i < ptidsToQuery.length; i++)
                {
                    this.panelMap[panels.PARTICIPANTS].panel.getEl().mask("loading type data...", "x-mask-loading");
                    var ptid = ptidsToQuery[i];

                    Ext.Ajax.request({
                        url : LABKEY.ActionURL.buildURL("visualization", "getDimensionValues", null, ptid),
                        method:'GET',
                        disableCaching:false,
                        success : function(response, e){this.renderParticipants(response, e, ptidStore);},
                        failure: function(){this.panelMap[panels.PARTICIPANTS].panel.getEl().unmask();},
                        scope: this
                    });
                }

                var listView = new Ext.list.ListView({
                    store: ptidStore,
                    flex: 1,
                    border: true,
                    multiSelect: true,
                    simpleSelect: true,
                    columns: [{header: ptidColumn.label, dataIndex:'value'}]
                });

                // stash away the selections
                listView.on('selectionChange', function(cmp, selections){

                    var info = this.panelMap[panels.PARTICIPANTS];
                    var disabled = selections.length == 0;

                    info.nextBtn.setDisabled(disabled);
                    this.view.participantValues = cmp.getRecords(selections);

                }, this);

                items.push(new Ext.Panel({
                    //title: 'select subjects', //axis.name,
                    layout: 'fit',
                    flex: 1,
                    items: [listView]
                }));

            }
        }
        var panel = new Ext.Panel({
            layout: 'hbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            border: false,
            flex: 1,
            items: items
        });

        info.panel.removeAll();
        info.panel.add([{xtype:'label', text: 'Select the subjects to include.  Multiple selections are allowed.', cls:'labkey-header-large', height: 50}, panel]);
        info.panel.doLayout();
    },

    renderParticipants : function(response, e, ptidStore) {

        var reader = new Ext.data.JsonReader({root:'values'}, [{name:'value'}]);
        var o = reader.read(response);

        // use the store to determine the intersection of ptid values, note: this logic only works
        // for either one or two sets of participant values, we would need to change the logic
        // slightly to support a third (or more) sets.

        if (ptidStore.getCount() == 0)
            ptidStore.add(o.records);
        else
        {
            for (var i=0; i < o.records.length; i++)
            {
                var rec = o.records[i];

                var matched = ptidStore.query('value', rec.data.value, false, true);
                matched.each(function(item){
                    item.data.matched = true;
                }, this);
            }
            ptidStore.filter('matched', true);
        }
        this.panelMap[panels.PARTICIPANTS].panel.getEl().unmask();
    },

    showVisualization : function() {

        var info = this.panelMap[panels.CHART];

        this.viewPanel.activate(info.panel);

        if (this.viewTypes[this.view.type])
        {
            var typeInfo = this.viewTypes[this.view.type];

            // for each measurement, construct an object that contains all properties needed to perform the data query
            var measures = [];

            // always add a participant id to the sort (if available)
            var sorts = [];

            var ptidCol = this.view.ptids['y-axis'];
            if (ptidCol)
            {
                var ptidRecs = this.view.participantValues;
                var ptidValues = [];

                // format the participant filter values
                if (ptidRecs) {
                    for (var i=0; i < ptidRecs.length; i++)
                    {
                        ptidValues.push(ptidRecs[i].data.value);
                    }
                }
                if (ptidValues.length > 0)
                    ptidCol.values = ptidValues;

                sorts.push(ptidCol);
            }

            if (this.view.hasTimeAxis)
            {
                var dateCol = this.view.measures['x-axis'];
                if (dateCol)
                    sorts.push(dateCol);
            }

            for (var j=0; j < typeInfo.axis.length; j++)
            {
                var axis = typeInfo.axis[j];
                var props = {};

                props.axis = axis;
                props.measure = this.view.measures[axis.name];

                // temporary, only allow a single dimension per axis for the time being
                if (this.view.dimensions[axis.name])
                    props.dimension = this.view.dimensions[axis.name][0];

                // format the selected dimension values a little better
                var recs = this.view.dimensionValues[axis.name];
                var values = [];
                if (props.dimension && recs) {
                    for (var k=0; k < recs.length; k++)
                    {
                        values.push(recs[k].data.value);
                    }
                    if (values.length > 0)
                        props.dimension.values = values;
                }

                if (axis.timeAxis && this.view.dateOptions)
                    props.dateOptions = this.view.dateOptions;

                measures.push(props);
            }

            LABKEY.Visualization.getData({
                successCallback: function(data){this.renderVisualization(data, measures, typeInfo);},
                failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
                measures: measures,
                viewInfo: typeInfo,
                sorts: sorts,
                scope: this
            });
            // do the queries....
        }
    },

    renderVisualization : function (data, measures, viewInfo) {

	var info = this.panelMap[panels.CHART];

        // create a new chart panel and insert into the wizard step
        var panel = new LABKEY.vis.ChartPanel({
            layout: 'border',
            flex: 1,
            border: false,
            data: data,
            measures: measures,
            viewInfo: viewInfo,
            panelSize: info.panel.getSize()
        });

        info.panel.add(panel);
        info.panel.doLayout();
    }
});