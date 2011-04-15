/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorChartsPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.apply(config, {
            title: 'Chart(s)',
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            labelAlign: 'top',
            items: []
        });

        this.addEvents('chartDefinitionChanged');

        LABKEY.vis.ChartEditorChartsPanel.superclass.constructor.call(this, config);
    },

    initComponent : function() {
        // the x-axis editor panel will be laid out with 2 columns
        var columnOneItems = [];
        var columnTwoItems = [];

        this.chartLayoutSingleRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart',
            inputValue: 'single',
            checked: true,
            listeners: {
                scope: this,
                'check': function(field, checked) {
                    if(checked) {
                        this.chartLayout = 'single';
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });

        this.chartLayoutPerSubjectRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart for Each ' + this.subjectNounSingular,
            inputValue: 'per_subject',
            listeners: {
                scope: this,
                'check': function(field, checked) {
                    if(checked) {
                        this.chartLayout = 'per_subject';
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });

        this.chartLayoutPerDimensionRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart for Each Dimension',
            inputValue: 'per_dimension',
            listeners: {
                scope: this,
                'check': function(field, checked) {
                    if(checked) {
                        this.chartLayout = 'per_dimension';
                        this.fireEvent('chartDefinitionChanged', false);
                    }
                }
            }
        });

        this.chartLayoutRadioGroup = new Ext.form.RadioGroup({
            xtype: 'radiogroup',
            id: 'chart-layout-radiogroup',
            fieldLabel: 'Layout',
            columns: 1,
            items: [
                this.chartLayoutSingleRadio,
                this.chartLayoutPerSubjectRadio,
                this.chartLayoutPerDimensionRadio
            ]
        });
        columnOneItems.push(this.chartLayoutRadioGroup);

        columnTwoItems.push({
            xtype: 'textfield',
            id: 'chart-title-textfield',
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            width: 300,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.mainTitle = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });

        // slider field to set the line width for the chart(s)
        this.lineWidthSliderField = new Ext.form.SliderField({
            fieldLabel: 'Line Width',
            width: 300,
            value: this.lineWidth || 4, // default to 4 if not specified
            increment: 1,
            minValue: 0,
            maxValue: 10
        });
        this.lineWidthSliderField.slider.on('changecomplete', function(cmp, newVal, thumb) {
            this.lineWidth = newVal;
            this.fireEvent('chartDefinitionChanged', false);
        }, this);
        columnTwoItems.push(this.lineWidthSliderField);

        this.items = [{
            border: false,
            layout: 'column',
            items: [{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnOneItems
            },{
                columnWidth: .5,
                layout: 'form',
                border: false,
                bodyStyle: 'padding: 5px',
                items: columnTwoItems
            }]
        }];

        this.on('activate', function(){
           this.doLayout();
        }, this);

        LABKEY.vis.ChartEditorChartsPanel.superclass.initComponent.call(this);
    },

    getMainTitle: function(){
        return this.mainTitle;
    },

    getChartLayout: function(){
        return this.chartLayout;
    },

    getLineWidth: function(){
        return this.lineWidth;
    },

    setMainTitle: function(newMainTitle){
        this.mainTitle = newMainTitle;
        Ext.getCmp('chart-title-textfield').setValue(newMainTitle);
    },

    setChartLayout: function(newChartLayout){
        this.chartLayout = newChartLayout;
        // set the radio group option with the events suspended for the given radio options
        if(this.chartLayout == 'single'){
            this.chartLayoutSingleRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(true);
            this.chartLayoutPerSubjectRadio.setValue(false);
            this.chartLayoutPerDimensionRadio.setValue(false);
            this.chartLayoutSingleRadio.resumeEvents();
        }
        else if(this.chartLayout == 'per_subject'){
            this.chartLayoutPerSubjectRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.setValue(true);
            this.chartLayoutPerDimensionRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.resumeEvents();
        }
        else if(this.chartLayout == 'per_dimension'){
            this.chartLayoutPerDimensionRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.setValue(false);
            this.chartLayoutPerDimensionRadio.setValue(true);
            this.chartLayoutPerDimensionRadio.resumeEvents();
        }
    },

    setLineWidth: function(newLineWidth){
        this.lineWidth = newLineWidth;
        this.lineWidthSliderField.setValue(newLineWidth);
    },

    disableDimensionOption: function(hasDimension){
        // if no dimension has been selected, disable that radio option
        if(hasDimension){
            this.chartLayoutPerDimensionRadio.setDisabled(false);
        }
        else{
            this.chartLayoutPerDimensionRadio.setDisabled(true);
            // if this radio was checked, reset it to the original value of the radio group
            if(this.chartLayoutPerDimensionRadio.getValue()){
                this.setChartLayout('single');
            }
        }
    }
});