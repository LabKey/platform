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

        // track if the axis label is something other than the default
        config.userEditedTitle = (config.mainTitle ? true : false);

        this.addEvents(
                'chartDefinitionChanged',
                'groupLayoutSelectionChanged'
        );

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
            checked: this.chartLayout == 'single',
            height: 10,
            listeners: {
                scope: this,
                'check': this.chartLayoutRadioChecked
            }
        });

        this.chartLayoutPerSubjectRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart for Each ' + this.subjectNounSingular,
            inputValue: 'per_subject',
            checked: this.chartLayout == 'per_subject',
            height: 10,
            listeners: {
                scope: this,
                'check': this.chartLayoutRadioChecked
            }
        });

        this.chartLayoutPerGroupRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart for Each ' + this.subjectNounSingular + ' Group',
            inputValue: 'per_group',
            checked: this.chartLayout == 'per_group',
            height: 10,
            listeners: {
                scope: this,
                'check': function(field, checked) {
                    this.chartLayoutRadioChecked(field, checked);
                    this.fireEvent('groupLayoutSelectionChanged', checked);
                }
            }
        });

        this.chartLayoutPerDimensionRadio = new Ext.form.Radio({
            name: 'chart_layout',
            boxLabel: 'One Chart for Each Measure/Dimension',
            inputValue: 'per_dimension',
            checked: this.chartLayout == 'per_dimension',
            height: 10,
            listeners: {
                scope: this,
                'check': this.chartLayoutRadioChecked
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
                this.chartLayoutPerGroupRadio,
                this.chartLayoutPerDimensionRadio
            ]
        });
        columnOneItems.push(this.chartLayoutRadioGroup);

        this.chartTitleTextField = new Ext.form.TextField({
            id: 'chart-title-textfield',            
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            width: 300,
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.userEditedTitle = true;
                    this.mainTitle = newVal;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });
        this.chartTitleTextField.addListener('keyUp', function(){
                this.userEditedTitle = true;
                this.mainTitle = this.chartTitleTextField.getValue();
                this.fireEvent('chartDefinitionChanged', false);
            }, this, {buffer: 500});
        columnTwoItems.push(this.chartTitleTextField);

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

        // checkbox to hide/show data points
        this.hideDataPointCheckbox = new Ext.form.Checkbox({
            boxLabel: 'Hide Data Points',
            hideLabel: true,
            checked: this.hideDataPoints || false, // default to show data points
            value: this.hideDataPoints || false, // default to show data points
            listeners: {
                scope: this,
                'check': function(cmp, checked){
                    this.hideDataPoints = checked;
                    this.fireEvent('chartDefinitionChanged', false);
                }
            }
        });
        columnTwoItems.push(this.hideDataPointCheckbox);

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

    getHideDataPoints: function(){
        return this.hideDataPoints;
    },

    setMainTitle: function(newMainTitle){
        if (!this.userEditedTitle)
        {
            this.mainTitle = newMainTitle;
            this.chartTitleTextField.setValue(this.mainTitle);
        }
    },

    setChartLayout: function(newChartLayout){
        this.chartLayout = newChartLayout;
        // set the radio group option with the events suspended for the given radio options
        if(this.chartLayout == 'single'){
            this.chartLayoutSingleRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(true);
            this.chartLayoutPerSubjectRadio.setValue(false);
            this.chartLayoutPerGroupRadio.setValue(false);
            this.chartLayoutPerDimensionRadio.setValue(false);
            this.chartLayoutSingleRadio.resumeEvents();
        }
        else if(this.chartLayout == 'per_subject'){
            this.chartLayoutPerSubjectRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.setValue(true);
            this.chartLayoutPerGroupRadio.setValue(false);
            this.chartLayoutPerDimensionRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.resumeEvents();
        }
        else if(this.chartLayout == 'per_group'){
            this.chartLayoutPerGroupRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.setValue(false);
            this.chartLayoutPerGroupRadio.setValue(true);
            this.chartLayoutPerDimensionRadio.setValue(false);
            this.chartLayoutPerGroupRadio.resumeEvents();
        }
        else if(this.chartLayout == 'per_dimension'){
            this.chartLayoutPerDimensionRadio.suspendEvents(false);
            this.chartLayoutSingleRadio.setValue(false);
            this.chartLayoutPerSubjectRadio.setValue(false);
            this.chartLayoutPerGroupRadio.setValue(false);
            this.chartLayoutPerDimensionRadio.setValue(true);
            this.chartLayoutPerDimensionRadio.resumeEvents();
        }
    },

    setLineWidth: function(newLineWidth){
        this.lineWidth = newLineWidth;
        this.lineWidthSliderField.setValue(newLineWidth);
    },

    setHideDataPoints: function(hide){
        this.hideDataPoints = hide;
        this.hideDataPointCheckbox.suspendEvents(false);
        this.hideDataPointCheckbox.setValue(hide);
        this.hideDataPointCheckbox.resumeEvents();
    },

    chartLayoutRadioChecked: function(field, checked) {
        if (checked)
        {
            this.chartLayout = field.inputValue;
            this.fireEvent('chartDefinitionChanged', true);
        }
    }
});