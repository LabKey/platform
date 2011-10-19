/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.ChartEditorChartsPanel = Ext.extend(Ext.FormPanel, {
    constructor : function(config){
        Ext.applyIf(config, {
            chartSubjectSelection: 'subjects'
        });
        Ext.apply(config, {
            title: "Chart(s)",
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

    initComponent : function(){
        var colOneItems = [];
        var colTwoItems = [];
        var colThreeItems = [];

        this.subjectRadio = new Ext.form.Radio({
            name: 'subject_selection',
            inputValue: 'subjects',
            fieldLabel: this.subjectNounSingular + ' Selection',
            boxLabel: this.subjectNounPlural,
            checked: this.chartSubjectSelection == 'subjects',
            listeners: {
                scope: this,
                'check': function(cmp, checked){
                    if(checked){
                        this.oneChartPerGroupRadio.setVisible(false);
                        this.fireEvent('groupLayoutSelectionChanged', false);
                        this.oneChartPerSubjectRadio.setVisible(true);
                        if(this.oneChartPerGroupRadio.getValue()){
                            this.oneChartPerSubjectRadio.setValue(true);
                        }
                        this.chartSubjectSelection = "subjects";
                        this.fireEvent('chartDefinitionChanged', true);
                    }
                }
            }
        });
        this.groupsRadio =  new Ext.form.Radio({
            name: 'subject_selection',
            inputValue: 'groups',
            boxLabel: 'Participant Groups',
            checked: this.chartSubjectSelection == 'groups',
            listeners: {
                scope: this,
                'check': function(cmp, checked){
                    if(checked){
                        this.oneChartPerGroupRadio.setVisible(true);
                        this.oneChartPerSubjectRadio.setVisible(false);
                        if(this.oneChartPerSubjectRadio.getValue()){
                            this.oneChartPerGroupRadio.setValue(true);
                        }
                        this.chartSubjectSelection = "groups";
                        this.fireEvent('groupLayoutSelectionChanged', true);
                        this.fireEvent('chartDefinitionChanged', true);
                    }
                }
            }
        });
        this.subjectSelection = new Ext.form.RadioGroup({
            name: 'subject_selection',
            fieldLabel: 'Participant Selection',
            columns: 1,
            items:[
                this.subjectRadio,
                this.groupsRadio
            ]
        });
        colOneItems.push(this.subjectSelection);

        this.oneChartRadio = new Ext.form.Radio({
            name: 'number_of_charts',
            boxLabel: 'One Chart',
            inputValue: 'single',
            checked: this.chartLayout == 'single',
            listeners: {
                scope:this,
                'check': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        this.oneChartPerGroupRadio = new Ext.form.Radio({
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_group',
            inputValue: 'per_group',
            hidden: this.chartSubjectSelection == 'subjects',
            boxLabel: 'One Chart Per Group',
            listeners: {
                scope:this,
                'check': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        this.oneChartPerSubjectRadio = new Ext.form.Radio({
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_subject',
            inputValue: 'per_subject',
            boxLabel: 'One Chart Per ' + this.subjectNounSingular,
            hidden: this.chartSubjectSelection ==  'groups',
            listeners: {
                scope:this,
                'check': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        this.oneChartPerDimensionRadio = new Ext.form.Radio({
            name: 'number_of_charts',
            boxLabel: 'One Chart Per Measure/Dimension',
            checked: this.chartLayout == 'per_dimension',
            inputValue: 'per_dimension',
            listeners: {
                scope:this,
                'check': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        this.numCharts = new Ext.form.RadioGroup({
            name: 'number_of_charts',
            fieldLabel: 'Number of Charts',
            columns: 1,
            items:[
                this.oneChartRadio,
                this.oneChartPerSubjectRadio,
                this.oneChartPerGroupRadio,
                this.oneChartPerDimensionRadio
            ]
        });
        colTwoItems.push(this.numCharts);

        this.chartTitleTextField = new Ext.form.TextField({
            id: 'chart-title-textfield',
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            width: '100%',
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
        colThreeItems.push(this.chartTitleTextField);

        // slider field to set the line width for the chart(s)
        this.lineWidthSliderField = new Ext.form.SliderField({
            fieldLabel: 'Line Width',
            width: '100%',
            value: this.lineWidth || 4, // default to 4 if not specified
            increment: 1,
            minValue: 1,
            maxValue: 10
        });
        this.lineWidthSliderField.slider.on('changecomplete', function(cmp, newVal, thumb) {
            this.lineWidth = newVal;
            this.fireEvent('chartDefinitionChanged', false);
        }, this);
        colThreeItems.push(this.lineWidthSliderField);

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
        colThreeItems.push(this.hideDataPointCheckbox);

        this.items = [{
            border: false,
            layout: 'column',
            items:[
                {
                    columnWidth: (1/3),
                    width: 160,
                    layout: 'form',
                    border: false,
                    bodyStyle: 'padding: 3px',
                    items: [colOneItems]
                },
                {
                    columnWidth: (1/3),
                    width: 260,
                    layout: 'form',
                    border: false,
                    bodyStyle: 'padding: 3px',
                    items: [colTwoItems]
                },
                {
                    columnWidth: (1/3),
                    layout: 'form',
                    border: false,
                    bodyStyle: 'padding: 3px',
                    items: [colThreeItems]
                }
            ]
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

    getChartSubjectSelection: function(){
        return this.chartSubjectSelection;
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

    chartPerRadioChecked: function(field, checked){
        this.chartLayout = field.inputValue;
        this.fireEvent('chartDefinitionChanged', true);
    }

});
