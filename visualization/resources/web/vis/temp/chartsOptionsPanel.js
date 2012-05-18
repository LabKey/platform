/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

Ext4.namespace("LABKEY.vis");

Ext4.tip.QuickTipManager.init();

Ext4.define('LABKEY.vis.ChartsOptionsPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        Ext4.applyIf(config, {
            chartSubjectSelection: 'subjects'
        });
        Ext4.apply(config, {
            header: false,
            autoHeight: true,
            autoWidth: true,
            bodyStyle: 'padding: 5px',
            border: false,
            items: [],
            buttonAlign: 'right',
            buttons: []
        });

        // track if the axis label is something other than the default
        config.userEditedTitle = (config.mainTitle ? true : false);

        this.callParent([config]);

        this.addEvents(
                'chartDefinitionChanged',
                'groupLayoutSelectionChanged',
                'closeOptionsWindow'
        );
    },

    initComponent : function(){
        // track if the panel has changed in a way that would require a chart/data refresh
        this.hasChanges = false;
        this.requireDataRefresh = false;

        var colOneItems = [];
        var colTwoItems = [];
        var colThreeItems = [];

        this.subjectRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'subject_selection',
            inputValue: 'subjects',
            labelAlign: 'top',
            fieldLabel: this.subjectNounSingular + ' Selection',
            boxLabel: this.subjectNounPlural,
            checked: this.chartSubjectSelection == 'subjects',
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    if(checked){
                        this.oneChartPerGroupRadio.setVisible(false);
                        this.oneChartPerSubjectRadio.setVisible(true);
                        if(this.oneChartPerGroupRadio.getValue()){
                            this.oneChartPerSubjectRadio.setValue(true);
                        } else {
                            this.hasChanges = true;
                            this.requireDataRefresh = true;
                        }
                    }
                }
            }
        });

        this.groupsRadio =  Ext4.create('Ext.form.field.Radio', {
            name: 'subject_selection',
            inputValue: 'groups',
            boxLabel: this.subjectNounSingular + ' Groups',
            checked: this.chartSubjectSelection == 'groups',
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    if(checked){
                        this.oneChartPerGroupRadio.setVisible(true);
                        this.oneChartPerSubjectRadio.setVisible(false);
                        if(this.oneChartPerSubjectRadio.getValue()){
                            this.oneChartPerGroupRadio.setValue(true);
                        } else {
                            this.hasChanges = true;
                            this.requireDataRefresh = true;
                        }
                    }
                }
            }
        });
        this.subjectSelectionRadioGroup = new Ext.form.RadioGroup({
            border: false,
            columns: 1,
            items:[
                this.subjectRadio,
                this.groupsRadio
            ]
        });
        colOneItems.push(this.subjectSelectionRadioGroup);

        this.oneChartRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            labelAlign: 'top',
            fieldLabel: 'Number of Charts',
            labelWidth: 160,
            boxLabel: 'One Chart',
            inputValue: 'single',
            checked: this.chartLayout == 'single',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartRadio);

        this.oneChartPerGroupRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_group',
            inputValue: 'per_group',
            hidden: this.chartSubjectSelection == 'subjects',
            boxLabel: 'One Chart Per Group',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerGroupRadio);

        this.oneChartPerSubjectRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            checked: this.chartLayout == 'per_subject',
            inputValue: 'per_subject',
            boxLabel: 'One Chart Per ' + this.subjectNounSingular,
            hidden: this.chartSubjectSelection ==  'groups',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerSubjectRadio);

        this.oneChartPerDimensionRadio = Ext4.create('Ext.form.field.Radio', {
            name: 'number_of_charts',
            boxLabel: 'One Chart Per Measure/Dimension',
            checked: this.chartLayout == 'per_dimension',
            inputValue: 'per_dimension',
            listeners: {
                scope:this,
                'change': function(cmp, checked){
                    if(checked){
                        this.chartPerRadioChecked(cmp, checked);
                    }
                }
            }
        });
        colTwoItems.push(this.oneChartPerDimensionRadio);

        this.chartTitleTextField = Ext4.create('Ext.form.field.Text', {
            name: 'chart-title-textfield',
            labelAlign: 'top',
            fieldLabel: 'Chart Title',
            value: this.mainTitle,
            anchor: '100%',
            enableKeyEvents: true,
            listeners: {
                scope: this,
                'change': function(cmp, newVal, oldVal) {
                    this.userEditedTitle = true;
                    this.mainTitle = newVal;
                    this.hasChanges = true;
                }
            }
        });
        this.chartTitleTextField.addListener('keyUp', function(){
                this.userEditedTitle = true;
                this.mainTitle = this.chartTitleTextField.getValue();
                this.hasChanges = true;
            }, this, {buffer: 500});
        colThreeItems.push(this.chartTitleTextField);

        // slider field to set the line width for the chart(s)
        this.lineWidthSlider = Ext4.create('Ext.slider.Single', {
            anchor: '95%',
            labelAlign: 'top',
            fieldLabel: 'Line Width',
            value: this.lineWidth || 3, // default to 3 if not specified
            increment: 1,
            minValue: 1,
            maxValue: 10
        });
        this.lineWidthSlider.on('changecomplete', function(cmp, newVal, thumb) {
            this.lineWidth = newVal;
            this.hasChanges = true;
        }, this);
        colThreeItems.push(this.lineWidthSlider);

        // checkbox to hide/show data points
        this.hideDataPointCheckbox = Ext4.create('Ext.form.field.Checkbox', {
            boxLabel: 'Hide Data Points',
            hideLabel: true,
            checked: this.hideDataPoints || false, // default to show data points
            value: this.hideDataPoints || false, // default to show data points
            listeners: {
                scope: this,
                'change': function(cmp, checked){
                    this.hideDataPoints = checked;
                    this.hasChanges = true;
                }
            }
        });
        colThreeItems.push(this.hideDataPointCheckbox);

        this.items = [{
            border: false,
            layout: 'column',
            items:[
                {
                    xtype: 'form',
                    columnWidth: (1/3),
                    border: false,
                    padding: 5,
                    items: [colOneItems]
                },
                {
                    xtype: 'form',
                    columnWidth: (1/3),
                    border: false,
                    padding: 5,
                    items: [colTwoItems]
                },
                {
                    xtype: 'form',
                    columnWidth: (1/3),
                    padding: 5,
                    border: false,
                    items: [colThreeItems]
                }
            ]
        }];

        this.buttons = [
            {
                text: 'Apply',
                handler: function(){
                    this.fireEvent('closeOptionsWindow');
                    this.checkForChangesAndFireEvents();
                },
                scope: this
            }
        ];

        this.callParent();
    },

    getMainTitle: function(){
        return this.mainTitle;
    },

    getChartLayout: function(){
        return this.chartLayout;
    },

    getChartSubjectSelection: function(){
        if(this.groupsRadio.getValue()){
            return "groups";
        } else {
            return "subjects";
        }
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
        this.hasChanges = true;
        this.requireDataRefresh = true;
    },

    checkForChangesAndFireEvents : function() {
        if (this.hasChanges)
        {
            this.fireEvent('groupLayoutSelectionChanged', this.getChartSubjectSelection() == "groups");
            this.fireEvent('chartDefinitionChanged', this.requireDataRefresh);
        }

        // reset the changes flags
        this.requireDataRefresh = false;
        this.hasChanges = false;
    }
});
