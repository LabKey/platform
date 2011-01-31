/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

LABKEY.requiresCss("_images/icons.css");
LABKEY.requiresScript("vis/measuresPanel.js");
LABKEY.requiresScript("protovis/protovis-d3.2.js");
LABKEY.requiresScript("vis/ChartComponent.js");
LABKEY.requiresScript("vis/timeChartPanel.js");

Ext.QuickTips.init();

var panels = {
    TYPES : 'types',
    MEASURES : 'measures',
    CHART : 'chart'
};

LABKEY.vis.TimeChartWizard = Ext.extend(Ext.Panel, {

    viewTypes : {},                 // map of view type ids to info object
    panelMap : {},                  // map of types to panel config
    view : {},                      // the current visualization object
    wizardSteps: [],

    constructor : function(config){

        LABKEY.vis.TimeChartWizard.superclass.constructor.call(this, config);
        Ext.apply(this, config);
    },

    initComponent : function() {
        this.layout = 'border';
        this.border = false;
        this.frame  = false;

        var steps = [];

        // type panel
        var typePanel = this.createWizardStepPanel({
            title: 'Step 1 : Report Type',
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

        LABKEY.vis.TimeChartWizard.superclass.initComponent.call(this);
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
                    //prevBtn: {text:'Prev', handler: this.onPreviousStep, scope:this},
                    nextBtn: {text:'Next', disabled:true, handler: this.onNextStep, scope: this}});
                stepHandler = this.getMeasures;
                break;

            case panels.MEASURES:
                if (this.view.hasTimeAxis && curStep.type == panels.MEASURES)
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
        LABKEY.vis.TimeChartWizard.superclass.onRender.apply(this, arguments);

        if (this.resizable)
        {
            this.resizer = new Ext.Resizable(this.el, {pinned: false});
            this.resizer.on("resize", function(o, width, height){
                this.setWidth(width);
                this.setHeight(height)
            }, this);
        }

        // get the type information from the server
        LABKEY.Visualization.getTypes({
            successCallback : this.storeVisuazliationTypes,
            failureCallback : function(info, response, options) {LABKEY.Utils.displayAjaxErrorResponse(response, options);},
            scope: this
        });
    },

    storeVisuazliationTypes : function(types) {
        this.view.hasTimeAxis = false; //todo: ???
        this.view.measures = {};
        this.view.dateOptions = {};
        this.view.dimensions = {};
        this.view.dimensionValues = {};

        this.viewTypes = {};
        for (var i=0; i < types.length; i++)
        {
            var type = types[i];
            this.viewTypes[type.type] = type;
        }

        // this wizard is currently just to support the line/time chart
        this.view.type = 'line';
        this.view.hasTimeAxis = true;

        this.onNextStep();
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

//        // new chart button
//        if (this.wizardSteps.length > 0)
//            buttons.push({text:'New Visualization', handler: this.onNewVisualization, scope:this});

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

            var info = this.panelMap[panels.CHART];

            // create a new chart panel and insert into the wizard step
            var panel = new LABKEY.vis.TimeChartPanel({
                layout: 'border',
                flex: 1,
                border: false,
                //data: data,
                measures: measures,
                viewInfo: typeInfo,
                panelSize: info.panel.getSize()
            });

            info.panel.add(panel);
            info.panel.doLayout();
        }
    }
});